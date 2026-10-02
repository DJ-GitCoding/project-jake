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
} from "./session.server.js";
import { refreshTokens } from "./auth.server.js";
import { BACKEND_URL } from "./config.server.js";

/*
 * BFF proxy: browser hits same-origin paths, we attach the Bearer token from the
 * encrypted session and forward to the backend so tokens never reach the client.
 * On 401, refresh server-side, update the cookie, retry once. The full path
 * (/api, /domain, /ip, /autnum, ...) is forwarded verbatim — backend has no context path.
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

async function forward(request, accessToken) {
  const url = new URL(request.url);
  const target = BACKEND_URL + url.pathname + url.search;
  const headers = new Headers();
  const ct = request.headers.get("content-type");
  const accept = request.headers.get("accept");
  if (ct) headers.set("content-type", ct);
  if (accept) headers.set("accept", accept);
  if (accessToken) headers.set("authorization", `Bearer ${accessToken}`);
  const init = { method: request.method, headers, redirect: "manual" };
  if (!["GET", "HEAD"].includes(request.method)) {
    init.body = Buffer.from(await request.arrayBuffer());
  }
  return fetch(target, init);
}

export async function proxy(request) {
  const session = await getSession(request);
  const tokens = getTokens(session);

  let res = await forward(request, tokens?.accessToken);

  if (res.status === 401 && tokens?.refreshToken) {
    const refreshed = await refreshTokens(tokens.refreshToken);
    if (refreshed) {
      setAuth(session, { user: getUser(session), ...refreshed });
      res = await forward(request, refreshed.accessToken);
      const body = await res.arrayBuffer();
      const headers = passthroughHeaders(res);
      headers.append("Set-Cookie", await commitSession(session));
      return new Response(body, { status: res.status, headers });
    }
  }

  return new Response(res.body, { status: res.status, headers: passthroughHeaders(res) });
}
