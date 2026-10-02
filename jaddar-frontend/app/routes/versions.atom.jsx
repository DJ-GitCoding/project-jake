/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

/**
 * Public Atom feed of Jaddar releases, served at /versions.atom.
 * 
 * @see https://www.rfc-editor.org/info/rfc4287/ for RFC 4287, the Atom 1.0 specification.
 *
 * Resource route: exports a loader only, no component, so the response is raw XML rather
 * than the SSR HTML shell. Registered outside the protected/admin layouts in routes.js, so
 * it needs no authentication.
 *
 * To publish a release, add an entry to the top of VERSIONS and deploy. Newest first — the
 * feed-level <updated> is taken from VERSIONS[0].
 */

/** Feed metadata. FEED_TITLE and AUTHORS are what feed readers display as the source. */
const FEED_TITLE = "Jaddar Release Notes";
const FEED_SUBTITLE = "Version history for the Jaddar RDRS platform.";
const AUTHORS = [
  { name: "Edgemoor Research Institute" },
  { name: "Derek Jenkins", email: "derek@pure-code.net" },
];

/**
 * Releases, newest first.
 *
 * - version:  used in the entry title and in the stable entry id — never change it once published.
 * - date:     YYYY-MM-DD, treated as UTC midnight.
 * - summary:  one sentence, rendered as the lead paragraph.
 * - changes:  bullet list; omit or leave empty for a summary-only entry.
 * - breaking: adds a "Breaking changes" marker to the rendered entry.
 * - url:      optional; where the entry links to. Defaults to the site root.
 */
const VERSIONS = [
  {
    version: "1.1.0",
    date: "2026-09-26",
    summary:
      "Adds a guided subscription flow, automated subscription testing and activation, credential-based authentication between backends, dashboard charts, and a round of RDRS sign-in fixes.",
    changes: [
      "Replaced the backends' mTLS certificate mesh with peer credentials exchanged through the admin UI. Peer authentication is now mandatory.",
      "Added a guided subscription flow to the Requestor Manager and consolidated subscription details into a single modal. Answers to questions in the legal areas now save correctly.",
      "Requestor groups now have an address, chosen with a region-grouped country picker and prefilled into new subscriptions. The group detail view lists members, and group pickers use server-side pagination.",
      "Group Admin now tests and activates subscriptions automatically on approval. Each test records the queries it ran, per-check results and the technical cause of any failure, and stays reviewable by admins after activation.",
      "Subscription details in Group Admin now show the agreed legal terms, field answers and request types, and the subscription list shows the data holder group.",
      "The data holder group administrator's contact name, email, phone and address are carried through every agreement to requestors. They default from the group and can be overridden per agreement.",
      "An agreement's data holder group is now fixed when the agreement is created, and any data holder group can copy an agreement it is able to see.",
      "Jaddar's selector now filters by agreement rather than request type, and data holders report a hashed agreement code with each query.",
      "Subscriptions show whether they have data holder group credentials, and agreements without them are marked unusable in Jaddar's selector.",
      "The subscription status endpoint now answers before the group admin has issued credentials, while still withholding the requestor's submissions from unauthenticated callers.",
      "Fixed the signing-key confirmation dialog. The introspection URL is now sent and required on submit, and introspection credentials are delivered as soon as data holder group credentials are saved.",
      "Internal request type codes are no longer shown in requestor-facing views in Jaddar and the Requestor Manager.",
      "The terms editor now shows legal term cross-references by section name instead of raw reference markers.",
      "Added charts to the Data Holder, Data Holder Group and Requestor Manager dashboards.",
      "The Data Holder, Data Holder Group Admin and Data Holder Registry apps now expire idle sessions and keep active sessions alive with token heartbeats.",
      "The app version is now shown consistently in the header and on the sign-in page of each admin app.",
      "RDRS: ICANN's actual sign-in and submission rejection reasons are now shown instead of a generic outage or expired-session message.",
      "RDRS: sign-in is locked to the user's assigned Jaddar address, and the password field has a show-password toggle.",
      "RDRS: phone numbers are validated the same way ICANN validates them.",
      "RDRS: a 403 from ICANN no longer signs the user out of Jaddar mid-request; they are returned to the RDRS sign-in step instead. Fixed a race in the RDRS sign-in process.",
      "Security fixes to access control and request authentication across the data holder and group admin backends.",
      "Removed leftovers from the older access-level architecture: template-level and agreement-level default access levels, their badges, the legacy reviewer access-level override, and name-based agreement lookups.",
    ],
    breaking: true,
  },
  {
    version: "1.1.0b",
    date: "2026-07-31",
    summary: "Initial public release candidate.",
    changes: [],
    breaking: false,
  },
];

/** Escape text for XML. Applied to every interpolated value, including generated markup. */
function esc(value) {
  return String(value).replace(
    /[&<>"']/g,
    (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&apos;" })[c],
  );
}

/** YYYY-MM-DD to an RFC 3339 timestamp. Atom rejects bare dates. */
function rfc3339(date) {
  return new Date(`${date}T00:00:00Z`).toISOString();
}

/**
 * Render one release as HTML. The result is escaped again by the caller: Atom's
 * type="html" content is escaped markup, not raw markup, so the double encoding is correct.
 */
function renderContent(v) {
  const parts = [`<p>${v.summary}</p>`];
  if (v.breaking) parts.push("<p><strong>Contains breaking changes.</strong></p>");
  if (v.changes?.length) {
    parts.push(`<ul>${v.changes.map((c) => `<li>${c}</li>`).join("")}</ul>`);
  }
  return parts.join("");
}

function renderEntry(v, base) {
  return `
  <entry>
    <id>tag:jaddar,2026:version:${esc(v.version)}</id>
    <title>Jaddar ${esc(v.version)}</title>
    <updated>${rfc3339(v.date)}</updated>
    <link rel="alternate" type="text/html" href="${esc(v.url ?? base)}"/>
    <content type="html">${esc(renderContent(v))}</content>
  </entry>`;
}

/** One atom:person construct: name first, then the optional email, per RFC 4287. */
function renderAuthor(author) {
  const email = author.email ? `<email>${esc(author.email)}</email>` : "";
  return `<author><name>${esc(author.name)}</name>${email}</author>`;
}

export function loader({ request }) {
  const base = new URL(request.url).origin;

  const body = `<?xml version="1.0" encoding="utf-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <id>${esc(base)}/versions.atom</id>
  <title>${esc(FEED_TITLE)}</title>
  <subtitle>${esc(FEED_SUBTITLE)}</subtitle>
  <updated>${rfc3339(VERSIONS[0].date)}</updated>
  <link rel="self" type="application/atom+xml" href="${esc(base)}/versions.atom"/>
  <link rel="alternate" type="text/html" href="${esc(base)}/"/>
  ${AUTHORS.map(renderAuthor).join("\n  ")}${VERSIONS.map((v) => renderEntry(v, base)).join("")}
</feed>
`;

  return new Response(body, {
    headers: {
      "Content-Type": "application/atom+xml; charset=utf-8",
      // Consumers poll on their own schedule; half an hour keeps the origin quiet!
      "Cache-Control": "public, max-age=1800",
      // Public feed, no credentials, yay! This lets a browser-side consumer read it too.
      "Access-Control-Allow-Origin": "*",
    },
  });
}
