/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { randomBytes, timingSafeEqual } from "node:crypto";

/*
 * Session-bound CSRF protection for state-changing React Router actions.
 *
 * The BFF /api/* proxy already relies on SameSite=Lax cookies to block
 * cross-site POSTs; this token is defense-in-depth on the RR `action`s
 * (login/logout) which submit through same-origin <Form> posts.
 */

/**
 * Return the session's CSRF token, generating and storing one if absent.
 * Returns { token, created } so the caller (root loader) can decide whether
 * the session needs to be committed (Set-Cookie) to persist a new token.
 */
export function getCsrfToken(session) {
  let token = session.get("csrf");
  if (token) return { token, created: false };
  token = randomBytes(32).toString("base64url");
  session.set("csrf", token);
  return { token, created: true };
}

function safeEqual(a, b) {
  const ab = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  if (ab.length !== bb.length) return false;
  return timingSafeEqual(ab, bb);
}

/**
 * Verify the `_csrf` field of an already-read FormData against the session's
 * token. Pass the FormData in (read it once in the action) so the action can
 * still read its other fields. Throws a 403 Response on mismatch.
 */
export function assertCsrf(session, formData) {
  const expected = session.get("csrf");
  const submitted = formData?.get("_csrf");
  if (
    !expected ||
    typeof submitted !== "string" ||
    !safeEqual(submitted, expected)
  ) {
    throw new Response("Invalid or missing CSRF token", { status: 403 });
  }
}
