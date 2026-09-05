/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import {
  getSession,
  getTokens,
  getUser,
  setAuth,
  commitSession,
  destroySession,
} from "../lib/session.server.js";
import { refreshTokens } from "../lib/auth.server.js";
import { BACKEND_URL } from "../lib/config.server.js";

/*
 * BFF proxy: browser hits same-origin /api/*, we attach the Bearer token from the
 * encrypted session and forward to the backend so tokens never reach the client.
 * On 401, refresh server-side, update the session cookie, retry once.
 */

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

async function forward(request, accessToken, body) {
  const url = new URL(request.url);
  const target = BACKEND_URL + url.pathname + url.search; // pathname is /api/...
  const headers = new Headers();
  const ct = request.headers.get("content-type");
  const accept = request.headers.get("accept");
  if (ct) headers.set("content-type", ct);
  if (accept) headers.set("accept", accept);
  if (accessToken) headers.set("authorization", `Bearer ${accessToken}`);
  const init = { method: request.method, headers, redirect: "manual" };
  if (body !== undefined) init.body = body;
  return fetch(target, init);
}

async function proxy(request) {
  const session = await getSession(request);
  const tokens = getTokens(session);

  // Buffer the body once. The retry below re-sends it, and a request stream can
  // only be read a single time ("Body is unusable" on the second read).
  const requestBody = ["GET", "HEAD"].includes(request.method)
    ? undefined
    : Buffer.from(await request.arrayBuffer());

  let res = await forward(request, tokens?.accessToken, requestBody);

  if (res.status === 401) {
    const refreshed = tokens?.refreshToken
      ? await refreshTokens(tokens.refreshToken)
      : null;

    if (refreshed) {
      setAuth(session, { user: getUser(session), ...refreshed });
      res = await forward(request, refreshed.accessToken, requestBody);
      const responseBody = await res.arrayBuffer();
      const headers = passthroughHeaders(res);
      headers.append("Set-Cookie", await commitSession(session));
      return new Response(responseBody, { status: res.status, headers });
    }

    if (getUser(session)) {
      const deadBody = await res.arrayBuffer();
      const headers = passthroughHeaders(res);
      headers.append("Set-Cookie", await destroySession(session));
      return new Response(deadBody, { status: res.status, headers });
    }
  }

  return new Response(res.body, { status: res.status, headers: passthroughHeaders(res) });
}

export const loader = ({ request }) => proxy(request);
export const action = ({ request }) => proxy(request);
