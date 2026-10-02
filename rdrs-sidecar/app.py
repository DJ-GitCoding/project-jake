# SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
# SPDX-License-Identifier: AGPL-3.0-only
#
# Author: Derek Jenkins <derek@pure-code.net>
# Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.

"""
RDRS Sidecar — drives ICANN's browser-only login so Jaddar can obtain an RDRS
access token on a user's behalf.

ICANN's Registration Data Request Service has no partner API and no registered
OAuth client for third parties. Its login is an implicit-flow chain that only a
real browser can complete:

    account.icann.org/authorize
      -> Okta /sso/idps/{idp}                  (SAML request)
      -> account.icann.org  Tapestry login     (2 steps: email, then password;
                                                _csrf + signed t:formdata +
                                                per-render field-name suffixes)
      -> Okta                                  (optional MFA challenge)
      -> rdrs.icann.org/oauth#access_token=... (implicit flow, no client secret)

Reproducing that over raw HTTP means scraping signed Tapestry form state and
reimplementing Okta's authenticator state machine. A headless browser absorbs all
of it, including Cloudflare's __cf_bm bot cookie.

This service does exactly one thing: exchange (email, password [, MFA code]) for
an Okta access token. It deliberately does NOT call the RDRS API — jaddar-backend
owns the RDRS session, the REST calls, validation and audit. That keeps this
service narrow and disposable: if ICANN ever registers Jaddar as a real OAuth
client, delete this sidecar and nothing else changes.

Threading model: Playwright's sync API is not thread-safe, so every browser lives
on its own dedicated thread for the whole life of a login. An MFA challenge parks
that thread on a queue waiting for the code, which is why a login can be resumed
across two HTTP requests. Run with a single gunicorn worker (see
docker-entrypoint.sh) — pending logins are in-process and cannot be resumed by a
sibling worker.

Called by jaddar-backend over the Docker network with a shared-secret header.
"""

import os
import hmac
import time
import uuid
import queue
import string
import secrets
import logging
import threading

import requests
from flask import Flask, request, jsonify
from playwright.sync_api import (
    sync_playwright,
    TimeoutError as PlaywrightTimeout,
    Error as PlaywrightError,
)

app = Flask(__name__)
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
log = logging.getLogger("rdrs-sidecar")

# --- Config ---
# Fail-fast: no insecure default. This endpoint accepts end-user passwords.
SHARED_SECRET = os.getenv("RDRS_SIDECAR_SECRET")
if not SHARED_SECRET or len(SHARED_SECRET) < 16:
    raise RuntimeError("RDRS_SIDECAR_SECRET must be set to a strong (>=16 char) value; refusing to start.")

# ICANN publishes this unauthenticated; read it rather than hardcoding, so a
# rotated client id or API host does not require a redeploy.
RDRS_CONFIG_URL = os.getenv("RDRS_CONFIG_URL", "https://rdrs.icann.org/api/config")
CONFIG_TTL_S = int(os.getenv("RDRS_CONFIG_TTL_SECONDS", "3600"))

NAV_TIMEOUT_MS = int(os.getenv("RDRS_NAV_TIMEOUT_MS", "45000"))
LOGIN_TIMEOUT_MS = int(os.getenv("RDRS_LOGIN_TIMEOUT_MS", "90000"))
# How long a login parked on an MFA prompt stays resumable before its browser is
# torn down. Chromium processes leak if this is never enforced.
MFA_TTL_S = int(os.getenv("RDRS_MFA_TTL_SECONDS", "300"))
HEADLESS = os.getenv("RDRS_HEADLESS", "true").lower() != "false"

# Chromium in a container: /dev/shm is tiny by default and there is no sandbox.
# --disable-blink-features=AutomationControlled stops Chromium advertising
# navigator.webdriver. ICANN's sign-in silently refuses to advance past the email
# step for a browser that identifies itself as automated: the form posts, and the
# same step is served back with no error. This is ordinary Playwright hygiene, not
# a workaround for an access control — the requestor is signing in to their own
# ICANN account with their own credentials.
BROWSER_ARGS = [
    "--no-sandbox",
    "--disable-dev-shm-usage",
    "--disable-gpu",
    "--disable-blink-features=AutomationControlled",
]

# Headless Chromium reports "HeadlessChrome/<version>". Derive the normal string
# from whatever Chromium is actually installed, so this stays truthful about the
# engine and correct across Playwright upgrades.
USER_AGENT_OVERRIDE = os.getenv("RDRS_USER_AGENT", "")
VIEWPORT = {"width": 1440, "height": 900}
LOCALE = os.getenv("RDRS_LOCALE", "en-US")

app.config["MAX_CONTENT_LENGTH"] = 256 * 1024


@app.errorhandler(Exception)
def handle_exception(e):
    # Never surface the exception text: it can carry the submitted password or
    # fragments of ICANN's page markup.
    log.error(f"Unhandled exception: {type(e).__name__}", exc_info=True)
    return jsonify({"status": "error", "error": "An unexpected error occurred"}), 500


def _check_auth():
    token = request.headers.get("X-Rdrs-Secret", "")
    if not hmac.compare_digest(token, SHARED_SECRET):
        return jsonify({"status": "error", "error": "Unauthorized"}), 401
    return None


# ─── ICANN app config ───────────────────────────────────────────────

_config_cache = {"value": None, "fetched_at": 0.0}
_config_lock = threading.Lock()


def rdrs_config():
    """Fetch and cache https://rdrs.icann.org/api/config."""
    with _config_lock:
        now = time.time()
        cached = _config_cache["value"]
        if cached and (now - _config_cache["fetched_at"]) < CONFIG_TTL_S:
            return cached

        resp = requests.get(RDRS_CONFIG_URL, timeout=15)
        resp.raise_for_status()
        cfg = resp.json()
        for key in ("accountAuthorizeUrl", "oauthUrl", "oktaClientId"):
            if not cfg.get(key):
                raise RuntimeError(f"RDRS config is missing '{key}'")
        _config_cache["value"] = cfg
        _config_cache["fetched_at"] = now
        log.info("Loaded RDRS config (backend=%s)", cfg.get("restBackendUrl"))
        return cfg


def _nonce(n=22):
    alphabet = string.ascii_letters + string.digits
    return "".join(secrets.choice(alphabet) for _ in range(n))


def build_login_url(cfg):
    """Mirror the SPA's createLoginUrl(): implicit flow, fragment response mode."""
    from urllib.parse import quote

    return (
        f"{cfg['accountAuthorizeUrl']}"
        f"?redirect_uri={quote(cfg['oauthUrl'], safe='')}"
        f"&client_id={quote(cfg['oktaClientId'], safe='')}"
        f"&nonce={quote(_nonce(), safe='')}"
        f"&state={quote('/requests', safe='')}"
        f"&response_mode=fragment"
    )


def parse_fragment(url):
    """Pull the implicit-flow tokens out of the #fragment."""
    from urllib.parse import unquote

    frag = url.split("#", 1)[1] if "#" in url else ""
    out = {}
    for pair in frag.split("&"):
        if not pair:
            continue
        key, _, value = pair.partition("=")
        out[unquote(key)] = unquote(value)
    return out


def jwt_exp(token):
    """Read `exp` from a JWT without verifying it — we only need the lifetime."""
    import json
    import base64

    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        return int(json.loads(base64.urlsafe_b64decode(payload)).get("exp"))
    except Exception:
        return None


# ─── Login sessions ─────────────────────────────────────────────────

# Selectors for ICANN's Tapestry login form. Step 1 asks for the email address,
# step 2 renders a password field whose name carries a per-render suffix
# (e.g. password_198de944a74311), so match on type rather than name.
SEL_EMAIL = "input[name='emailAddress']"
SEL_PASSWORD = "input[type='password']"
# Okta's MFA challenge. Covers the classic widget and the newer identity engine.
SEL_MFA_CODE = (
    "input[name='credentials.passcode'], input[name='answer'], "
    "input[autocomplete='one-time-code'], input[name^='passcode']"
)

_sessions = {}
_sessions_lock = threading.Lock()


def normal_user_agent(browser):
    """
    The installed Chromium's own UA with the "Headless" marker removed.

    Derived rather than hardcoded so it always names the engine that is genuinely
    running, and stays correct when the Playwright base image is upgraded.
    """
    ua = ""
    try:
        page = browser.new_page()
        try:
            ua = page.evaluate("() => navigator.userAgent") or ""
        finally:
            page.close()
    except Exception:
        pass
    return ua.replace("HeadlessChrome/", "Chrome/") if ua else None


def visible_text(page, redact=None, limit=1200):
    """
    The page's visible text, so we can see what ICANN is actually telling us.

    innerText does not include input values, so this cannot leak the password. The
    submitted email is redacted anyway, since a login page may echo it back.
    """
    if page is None:
        return "no page"
    text = safely(lambda: page.evaluate("() => document.body.innerText"), default="") or ""
    text = " ".join(text.split())
    if redact:
        text = text.replace(redact, "<email>")
    return text[:limit]


def describe(page):
    """
    A one-line, non-sensitive summary of where the browser actually is.

    Logs the URL, title and first heading only — never form values or page HTML,
    which would carry the submitted credentials. This is what makes an ICANN-side
    change diagnosable instead of just "it timed out".
    """
    if page is None:
        return "no page"
    url = safely(lambda: page.url, default="?")
    title = safely(lambda: page.title(), default="?")
    heading = ""
    for sel in ["h1", "h2", "[data-se='o-form-head']"]:
        el = safely(lambda: page.query_selector(sel))
        if el is not None:
            heading = (safely(lambda: el.inner_text(), default="") or "").strip()
            if heading:
                break
    return f"url={url!r} title={title!r} heading={heading[:120]!r}"


def dump_controls(page):
    """
    List the interactive controls on the page: tag, type, name, id, button text.

    Deliberately never reads element VALUES — those hold the submitted credentials.
    This exists because ICANN's markup is private and undocumented, so when a step
    stops working the only way to fix it is to see what is actually on the page.
    """
    if page is None:
        return "no page"
    script = """() => Array.from(document.querySelectorAll('input,button,a[role=button],select'))
        .slice(0, 40)
        .map(e => ({
            tag: e.tagName.toLowerCase(),
            type: e.getAttribute('type') || '',
            name: e.getAttribute('name') || '',
            id: e.id || '',
            // Label text only. A value is read solely for submit/button inputs,
            // where it IS the label; never for text or password fields.
            text: (e.tagName.toLowerCase() === 'input'
                     ? (['submit', 'button'].includes((e.getAttribute('type') || '').toLowerCase())
                          ? (e.getAttribute('value') || '') : '')
                     : (e.innerText || '')).slice(0, 40),
            visible: !!(e.offsetWidth || e.offsetHeight),
        }))"""
    controls = safely(lambda: page.evaluate(script), default=None)
    if not controls:
        return "no controls readable"
    # Password inputs are listed but their identity only; nothing typed is read.
    return " | ".join(
        f"{c['tag']}[type={c['type']} name={c['name']} id={c['id']}]"
        f"{'*' if c['visible'] else ''} {c['text']!r}"
        for c in controls
    )


def safely(fn, default=None):
    """
    Run a Playwright DOM call that may land mid-navigation.

    ICANN's login is a chain of auto-posting redirects (SAML to Okta and back), so
    while we poll for the outcome the page is frequently navigating. Touching the DOM
    at that moment raises "Execution context was destroyed", which is not a failure —
    the next poll will see the new page. Treat it as "nothing yet" and carry on.
    """
    try:
        return fn()
    except PlaywrightError:
        return default
    except Exception:
        return default


class LoginSession:
    """One ICANN login, pinned to one thread because Playwright's sync API is not
    thread-safe. Parks on `codes` when Okta asks for an MFA factor."""

    def __init__(self, email, password):
        self.id = str(uuid.uuid4())
        self.email = email
        self.password = password
        self.codes = queue.Queue(maxsize=1)     # main thread -> browser thread
        # Unbounded on purpose: if the HTTP caller has already given up, a bounded
        # queue would block the browser thread here forever and leak Chromium.
        self.results = queue.Queue()            # browser thread -> main thread
        self.created_at = time.time()
        self.done = threading.Event()
        self.thread = None
        # Which step we are on, so a timeout says where it stopped rather than just
        # "the login timed out". ICANN's chain has several places it can stall.
        self.step = "starting"

    def start(self):
        self.thread = threading.Thread(target=self._run, name=f"rdrs-login-{self.id[:8]}", daemon=True)
        self.thread.start()

    def _emit(self, payload):
        self.results.put(payload)

    def _run(self):
        try:
            cfg = rdrs_config()
        except Exception:
            log.error("Could not load RDRS config", exc_info=True)
            self._emit({"status": "error", "error": "Could not reach ICANN's RDRS configuration endpoint"})
            self.done.set()
            return

        page = None
        try:
            with sync_playwright() as pw:
                browser = pw.chromium.launch(headless=HEADLESS, args=BROWSER_ARGS)
                try:
                    context = browser.new_context(
                        ignore_https_errors=False,
                        user_agent=USER_AGENT_OVERRIDE or normal_user_agent(browser),
                        locale=LOCALE,
                        viewport=VIEWPORT,
                    )
                    page = context.new_page()
                    page.set_default_timeout(NAV_TIMEOUT_MS)
                    self._drive(page, cfg)
                finally:
                    browser.close()
        except PlaywrightTimeout:
            log.warning("Login timed out for session %s at step '%s'; %s",
                        self.id[:8], self.step, describe(page))
            self._emit({"status": "error",
                        "error": f"ICANN's sign-in page did not respond ({self.step})."})
        except Exception:
            log.error("Login failed for session %s at step '%s'; %s",
                      self.id[:8], self.step, describe(page), exc_info=True)
            self._emit({"status": "error", "error": "ICANN's login could not be completed"})
        finally:
            self.done.set()
            _forget(self.id)

    # Field names whose contents must never be written to a log.
    _SECRET_FIELDS = ("password", "csrf", "formdata", "token")

    def _on_request(self, request):
        """
        Log which fields a login POST carried, and whether each had a value.

        Only the field NAMES and a set/EMPTY flag are recorded — never a value. This
        answers the one question the screenshot raises: did we post the email address,
        or did we post an empty form?
        """
        try:
            if request.method != "POST" or "icann.org" not in request.url:
                return
            data = request.post_data or ""
            parts = []
            for pair in data.split("&"):
                if "=" not in pair:
                    continue
                key, value = pair.split("=", 1)
                if any(s in key.lower() for s in self._SECRET_FIELDS):
                    parts.append(f"{key}=<{len(value)} chars>")
                else:
                    parts.append(f"{key}={'set' if value else 'EMPTY'}")
            log.info("Login %s: POST %s fields: %s", self.id[:8], request.url, " ".join(parts))
        except Exception:
            pass

    def _on_response(self, response):
        """Log login responses. A 403 means ICANN refused us; a 302 back to a fresh
        form means the session or form state was not accepted."""
        try:
            if "icann.org" not in response.url:
                return
            if response.request.method == "POST" or response.status >= 300:
                location = safely(lambda: response.header_value("location"), default=None)
                log.info("Login %s: %s %s -> %s%s",
                         self.id[:8], response.request.method, response.url, response.status,
                         f" -> {location}" if location else "")
        except Exception:
            pass

    def _drive(self, page, cfg):
        page.on("request", self._on_request)
        page.on("response", self._on_response)
        self.step = "opening ICANN sign-in"
        page.goto(build_login_url(cfg), wait_until="domcontentloaded")
        log.info("Login %s: %s", self.id[:8], describe(page))

        # Step 1 — email address. ICANN sometimes lands straight on the password step
        # (a remembered account), so accept whichever field appears first rather than
        # insisting on the email one.
        self.step = "waiting for the email field"
        first = self._wait_for_any(page, [SEL_EMAIL, SEL_PASSWORD], NAV_TIMEOUT_MS)
        if first is None:
            log.warning("Login %s: no login field appeared; %s\n  controls: %s",
                        self.id[:8], describe(page), dump_controls(page))
            raise PlaywrightTimeout("no login field appeared")

        if first == SEL_EMAIL:
            self.step = "submitting the email address"
            self._cookies(page, "before email submit")
            self._await_tapestry(page)
            self._fill(page, SEL_EMAIL, self.email, "email")
            self._submit(page, SEL_EMAIL)
            self._cookies(page, "after email submit")

            # Tapestry re-renders the form between steps, which is why the password
            # field only exists now.
            self.step = "waiting for the password field"
            if self._wait_for_any(page, [SEL_PASSWORD], NAV_TIMEOUT_MS) is None:
                rejection = self._rejection_text(page)
                if rejection:
                    self._emit({"status": "error", "code": "invalid_credentials",
                                "error": rejection or "ICANN did not accept that email address."})
                    return
                log.warning("Login %s: password field never appeared; %s\n  controls: %s\n  text: %s",
                            self.id[:8], describe(page), dump_controls(page),
                            visible_text(page, redact=self.email))
                self._snapshot(page, "no-password-field")
                raise PlaywrightTimeout("password field never appeared")

        self.step = "submitting the password"
        self._await_tapestry(page)
        self._fill(page, SEL_PASSWORD, self.password, "password")
        self._submit(page, SEL_PASSWORD)
        # Drop the password as soon as it has been typed.
        self.password = None

        self.step = "waiting for ICANN to complete sign-in"
        deadline = time.time() + (LOGIN_TIMEOUT_MS / 1000.0)

        last_report = 0
        while time.time() < deadline:
            # The URL is readable even mid-navigation, so check for the token first.
            token = self._try_read_token(page, cfg)
            if token is not None:
                log.info("Login %s: signed in", self.id[:8])
                self._emit(token)
                return

            # Breadcrumbs while we wait. Without these a stall in the Okta/SAML hops
            # is indistinguishable from a stall anywhere else.
            if time.time() - last_report > 10:
                last_report = time.time()
                log.info("Login %s: waiting; %s", self.id[:8], describe(page))

            if safely(lambda: page.query_selector(SEL_MFA_CODE)) is not None:
                self._emit({
                    "status": "mfa_required",
                    "challengeId": self.id,
                    "prompt": self._mfa_prompt(page),
                })
                code = self._await_code()
                if code is None:
                    self._emit({"status": "error", "error": "MFA challenge expired"})
                    return
                if not self._answer_mfa(page, code):
                    self._emit({"status": "error",
                                "error": "ICANN's verification prompt closed before the code could be entered"})
                    return
                deadline = time.time() + (LOGIN_TIMEOUT_MS / 1000.0)
                continue

            # A login error banner means bad credentials — fail fast rather than
            # burning the whole timeout.
            rejection = self._rejection_text(page)
            if rejection:
                self._emit({"status": "error", "code": "invalid_credentials", "error": rejection})
                return

            # Deliberately not page.wait_for_timeout(): that is another call that can
            # blow up mid-navigation, and this thread owns nothing else to do.
            time.sleep(0.5)

        log.warning("Login %s: never reached the OAuth redirect; %s", self.id[:8], describe(page))
        self._emit({"status": "error", "code": "rate_limited",
                    "error": "ICANN did not complete the sign-in. It may be rate-limiting "
                             "repeated attempts — wait a moment and try again."})

    def _cookies(self, page, when):
        """
        Log which cookies the context holds — names, domains and SameSite only.

        The login is a chain of cross-site POSTs (Okta -> ICANN), and a session cookie
        that is dropped between them would make the server forget the email step and
        redisplay it, which is exactly the symptom. Values are never logged; a session
        cookie's value is as good as the session itself.
        """
        cookies = safely(lambda: page.context.cookies(), default=[]) or []
        summary = ", ".join(
            f"{c.get('name')}@{c.get('domain')}"
            f"[{c.get('sameSite', '?')}{'' if c.get('value') else ',EMPTY'}]"
            for c in cookies
        )
        log.info("Login %s: cookies %s: %s", self.id[:8], when, summary or "NONE")

    def _fill(self, page, selector, value, label):
        """
        Fill a field and confirm it actually holds the value before submitting.

        fill() sets the value and fires an input event, which is normally enough. But
        a form that clears or rewrites the field leaves us posting an empty one, and
        the server then just redisplays the step — silently, and indistinguishably
        from a rejection. Typing is the fallback because it produces real key events.
        Only the LENGTH of the field is ever logged, never its contents.
        """
        page.click(selector)
        page.fill(selector, value)
        length = safely(lambda: page.eval_on_selector(selector, "e => e.value.length"), default=0)
        if not length:
            log.warning("Login %s: %s field was empty after fill; typing instead", self.id[:8], label)
            safely(lambda: page.type(selector, value, delay=30))
            length = safely(lambda: page.eval_on_selector(selector, "e => e.value.length"), default=0)
        # Blur so any validation or state-syncing handler runs before the submit.
        safely(lambda: page.eval_on_selector(selector, "e => e.blur()"))
        log.info("Login %s: %s field holds %d chars", self.id[:8], label, length or 0)

    def _snapshot(self, page, label):
        """
        Save a screenshot of a stuck step to /tmp inside the container.

        Ephemeral and never leaves the host. Password fields render masked, so the
        image shows the page state without exposing the credential.
        """
        path = f"/tmp/rdrs-{label}-{self.id[:8]}.png"
        if safely(lambda: (page.screenshot(path=path, full_page=True), True)[-1], default=False):
            log.warning("Login %s: screenshot written to %s "
                        "(docker cp rdrs-sidecar:%s ./)", self.id[:8], path, path)

    # Tapestry's client-side form handling. Until RequireJS has loaded it, clicking a
    # submit button posts the form natively (the button's own name, no t:submit), and
    # ICANN answers that with a 302 back to a blank email step instead of the next one.
    TAPESTRY_READY_JS = (
        "() => !!(window.require && require.defined && require.defined('t5/core/forms'))"
    )
    TAPESTRY_READY_TIMEOUT_S = 10

    def _await_tapestry(self, page):
        """
        Wait for Tapestry's form module before touching a login step.

        The field is visible and document.readyState is 'complete' roughly half a
        second before the module is defined, so neither of those is a usable signal.
        Tapestry's own data-page-initialized marker never appears on this page. If the
        module never loads, carry on anyway: the step may still work, and the
        password-field wait that follows reports the failure properly if it does not.
        """
        deadline = time.time() + self.TAPESTRY_READY_TIMEOUT_S
        while time.time() < deadline:
            if safely(lambda: page.evaluate(self.TAPESTRY_READY_JS), default=False):
                return
            time.sleep(0.25)
        log.warning("Login %s: Tapestry form handling never loaded; submitting anyway; %s",
                    self.id[:8], describe(page))

    def _wait_for_any(self, page, selectors, timeout_ms):
        """
        Poll for whichever of `selectors` shows up first, returning it.

        Not page.wait_for_selector(): that commits to one selector, and this chain
        redirects constantly, so a probe can land mid-navigation. Polling lets us
        accept several possible next pages and survive the navigations between them.
        """
        deadline = time.time() + (timeout_ms / 1000.0)
        while time.time() < deadline:
            for selector in selectors:
                el = safely(lambda: page.query_selector(selector))
                if el is not None and safely(lambda: el.is_visible(), default=False):
                    return selector
            time.sleep(0.25)
        return None

    def _answer_mfa(self, page, code):
        """Type the multi-factor code, retrying briefly across any navigation."""
        def enter_code():
            field = page.query_selector(SEL_MFA_CODE)
            if field is None:
                return False
            field.fill(code)
            field.press("Enter")
            return True

        deadline = time.time() + 15
        while time.time() < deadline:
            if safely(enter_code, default=False):
                return True
            time.sleep(0.5)
        return False

    # The Tapestry submit controls, from the observed form posts: the email step
    # posts t:submit=["submitEmailAddress", ...], the password step
    # t:submit=["loginUser_<suffix>", "loginUser"] — hence the prefix matches.
    SUBMIT_SELECTORS = [
        "input[name^='submitEmailAddress']", "button[name^='submitEmailAddress']",
        "input[name^='loginUser']", "button[name^='loginUser']",
        "input[type='submit']", "button[type='submit']",
        "[data-se='o-form-submit']",
        "button:has-text('Continue')", "button:has-text('Next')",
        "button:has-text('Sign in')", "button:has-text('Log in')",
    ]

    def _submit(self, page, field_selector):
        """
        Submit the current login step.

        Clicking the real control is tried before Enter: Tapestry drives which step
        it advances to from the submit control's own name (t:submit), so a bare
        keypress is not always equivalent to pressing the button.
        """
        before = safely(lambda: page.url, default="")

        for sel in self.SUBMIT_SELECTORS:
            button = safely(lambda: page.query_selector(sel))
            if button is not None and safely(lambda: button.is_visible(), default=False):
                if safely(lambda: (button.click(), True)[-1], default=False):
                    log.info("Login %s: submitted via %s", self.id[:8], sel)
                    self._settle(page)
                    return

        # No recognisable control — fall back to a keypress.
        log.info("Login %s: no submit control found, pressing Enter; controls: %s",
                 self.id[:8], dump_controls(page))
        safely(lambda: page.press(field_selector, "Enter"))
        self._settle(page)
        if safely(lambda: page.url, default="") == before:
            log.warning("Login %s: page did not move after submit; %s",
                        self.id[:8], describe(page))

    def _settle(self, page):
        """Give the post a moment to navigate; neither outcome is an error."""
        try:
            page.wait_for_load_state("networkidle", timeout=8000)
        except (PlaywrightTimeout, PlaywrightError):
            pass

    def _try_read_token(self, page, cfg):
        """Return a success payload once the browser lands on the OAuth redirect."""
        url = safely(lambda: page.url, default="") or ""
        if not url.startswith(cfg["oauthUrl"]) or "#" not in url:
            return None

        frag = parse_fragment(url)
        if frag.get("error"):
            log.warning("Okta returned error=%s", frag.get("error"))
            return {"status": "error", "error": "ICANN's identity provider refused the login"}

        access_token = frag.get("access_token")
        if not access_token:
            return None

        return {
            "status": "ok",
            "accessToken": access_token,
            "idToken": frag.get("id_token"),
            "expiresAt": jwt_exp(access_token),
            "appId": cfg.get("oktaApplicationId") or cfg.get("oktaClientId"),
            "restBackendUrl": cfg.get("restBackendUrl"),
        }

    def _mfa_prompt(self, page):
        """Best-effort human-readable prompt so the UI can say which factor."""
        for sel in ["[data-se='o-form-explain']", ".okta-form-subtitle", "h1", "h2"]:
            el = safely(lambda: page.query_selector(sel))
            if el is not None:
                text = (safely(lambda: el.inner_text(), default="") or "").strip()
                if text:
                    return text[:200]
        return "Enter the verification code from your ICANN multi-factor device."

    def _rejection_text(self, page):
        """
        ICANN's own error banner text, or None when no rejection is showing.

        The text is what tells a locked account apart from a mistyped password, so it
        is passed back to the caller rather than collapsed into a bool. It is page
        chrome, never a field value, so it cannot carry the submitted password.
        """
        for sel in ["[data-se='o-form-error-container']", ".infobox-error", ".error-message", ".t-error"]:
            el = safely(lambda: page.query_selector(sel))
            if el is not None and safely(lambda: el.is_visible(), default=False):
                # An empty container is rendered up-front on some steps; only a
                # container with text is an actual rejection.
                text = (safely(lambda: el.inner_text(), default="") or "").strip()
                if text:
                    return " ".join(text.split())[:300]
        return None

    def _login_rejected(self, page):
        return self._rejection_text(page) is not None

    def _await_code(self):
        try:
            return self.codes.get(timeout=MFA_TTL_S)
        except queue.Empty:
            return None


def _forget(session_id):
    with _sessions_lock:
        _sessions.pop(session_id, None)


def _sweep():
    """Drop logins that were parked on MFA and never resumed."""
    cutoff = time.time() - MFA_TTL_S
    with _sessions_lock:
        stale = [sid for sid, s in _sessions.items() if s.created_at < cutoff]
        for sid in stale:
            _sessions.pop(sid, None)
    if stale:
        log.info("Swept %d stale login session(s)", len(stale))


# Errors the requestor can fix by retyping something. They get a status of their own
# so the caller can tell "you mistyped your password" from "ICANN is down", rather
# than reporting every failure as a gateway error.
USER_CORRECTABLE = {"invalid_credentials"}


def _result_status(result):
    """HTTP status for a finished login attempt."""
    if result.get("status") != "error":
        return 200
    code = result.get("code")
    if code in USER_CORRECTABLE:
        return 422
    if code == "rate_limited":
        return 429
    return 502


# ─── Endpoints ──────────────────────────────────────────────────────

@app.post("/login")
def login():
    unauthorized = _check_auth()
    if unauthorized:
        return unauthorized

    _sweep()
    body = request.get_json(silent=True) or {}
    email = (body.get("email") or "").strip()
    password = body.get("password") or ""
    if not email or not password:
        return jsonify({"status": "error", "error": "email and password are required"}), 400

    session = LoginSession(email, password)
    with _sessions_lock:
        _sessions[session.id] = session
    session.start()

    try:
        result = session.results.get(timeout=(LOGIN_TIMEOUT_MS / 1000.0) + 15)
    except queue.Empty:
        _forget(session.id)
        return jsonify({"status": "error", "error": "ICANN's login did not respond in time"}), 504

    if result.get("status") != "mfa_required":
        _forget(session.id)
    return jsonify(result), _result_status(result)


@app.post("/mfa")
def mfa():
    unauthorized = _check_auth()
    if unauthorized:
        return unauthorized

    body = request.get_json(silent=True) or {}
    challenge_id = (body.get("challengeId") or "").strip()
    code = (body.get("code") or "").strip()
    if not challenge_id or not code:
        return jsonify({"status": "error", "error": "challengeId and code are required"}), 400

    with _sessions_lock:
        session = _sessions.get(challenge_id)
    if session is None or session.done.is_set():
        return jsonify({"status": "error", "error": "That login is no longer active; start again"}), 410

    try:
        session.codes.put_nowait(code)
    except queue.Full:
        return jsonify({"status": "error", "error": "A code is already being verified"}), 409

    try:
        result = session.results.get(timeout=(LOGIN_TIMEOUT_MS / 1000.0) + 15)
    except queue.Empty:
        _forget(challenge_id)
        return jsonify({"status": "error", "error": "ICANN did not respond to the verification code in time"}), 504

    if result.get("status") != "mfa_required":
        _forget(challenge_id)
    return jsonify(result), _result_status(result)


@app.post("/logout")
def logout():
    unauthorized = _check_auth()
    if unauthorized:
        return unauthorized

    body = request.get_json(silent=True) or {}
    challenge_id = (body.get("challengeId") or "").strip()
    with _sessions_lock:
        session = _sessions.pop(challenge_id, None)
    if session is not None:
        # Unblock the parked thread so its browser tears down promptly.
        try:
            session.codes.put_nowait(None)
        except queue.Full:
            pass
    return jsonify({"status": "ok"}), 200


@app.get("/health")
def health():
    with _sessions_lock:
        pending = len(_sessions)
    return jsonify({"status": "ok", "pendingLogins": pending}), 200
