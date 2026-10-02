/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { getSession, getTokens } from "../lib/session.server.js";
import { getUserOptional, isAdminUser } from "../lib/auth.server.js";
import { DATAHOLDER_URL } from "../lib/config.server.js";

/*
 * BFF proxy for the Data Holder backend (test-query + admin panels on /dataholder).
 * Browser hits same-origin /dh/*; strip the prefix and forward.
 */

const USER_PATHS = /^\/(?:domain|ip|autnum)\/|^\/api\/rdap\//;

/** A JSON error response. */
function deny(status, error) {
  return new Response(JSON.stringify({ success: false, error }), {
    status,
    headers: { "content-type": "application/json" },
  });
}

const HOP_BY_HOP = new Set([
  "connection", "keep-alive", "transfer-encoding", "upgrade",
  "content-encoding", "content-length", "set-cookie",
]);

function passthroughHeaders(res) {
  const headers = new Headers();
  for (const [k, v] of res.headers) {
    if (!HOP_BY_HOP.has(k.toLowerCase())) headers.set(k, v);
  }
  return headers;
}

async function proxy(request) {
  const user = await getUserOptional(request);
  if (!user) return deny(401, "Authentication required");
  const admin = isAdminUser(user);

  const url = new URL(request.url);
  const path = url.pathname.replace(/^\/dh/, "") || "/";
  if (!admin && !USER_PATHS.test(path)) return deny(403, "Not authorized");
  const target = DATAHOLDER_URL + path + url.search;

  const headers = new Headers();
  const ct = request.headers.get("content-type");
  const accept = request.headers.get("accept");
  if (ct) headers.set("content-type", ct);
  if (accept) headers.set("accept", accept);

  const clientAuth = admin ? request.headers.get("authorization") : null;
  if (clientAuth) {
    headers.set("authorization", clientAuth);
  } else {
    const session = await getSession(request);
    const tokens = getTokens(session);
    if (tokens?.accessToken) headers.set("authorization", `Bearer ${tokens.accessToken}`);
  }

  const init = { method: request.method, headers, redirect: "manual" };
  if (!["GET", "HEAD"].includes(request.method)) {
    init.body = Buffer.from(await request.arrayBuffer());
  }

  const res = await fetch(target, init);
  return new Response(res.body, { status: res.status, headers: passthroughHeaders(res) });
}

export const loader = ({ request }) => proxy(request);
export const action = ({ request }) => proxy(request);
