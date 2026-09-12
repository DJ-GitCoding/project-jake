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

const REFRESH_SKEW_MS = 60 * 1000;

function expiresAt(accessToken) {
  try {
    const [, payload] = accessToken.split(".");
    const claims = JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
    return typeof claims.exp === "number" ? claims.exp * 1000 : null;
  } catch {
    return null;
  }
}

async function heartbeat(request) {
  const session = await getSession(request);
  const user = getUser(session);
  const tokens = getTokens(session);

  if (!user || !tokens?.accessToken) {
    return Response.json(
      { ok: false },
      { status: 401, headers: { "Set-Cookie": await destroySession(session) } }
    );
  }

  const exp = expiresAt(tokens.accessToken);
  if (exp !== null && exp - Date.now() > REFRESH_SKEW_MS) {
    return Response.json(
      { ok: true },
      { headers: { "Set-Cookie": await commitSession(session) } }
    );
  }

  const refreshed = tokens.refreshToken ? await refreshTokens(tokens.refreshToken) : null;
  if (!refreshed) {
    return Response.json(
      { ok: false },
      { status: 401, headers: { "Set-Cookie": await destroySession(session) } }
    );
  }

  setAuth(session, { user, ...refreshed });
  return Response.json(
    { ok: true },
    { headers: { "Set-Cookie": await commitSession(session) } }
  );
}

export const loader = ({ request }) => heartbeat(request);
export const action = ({ request }) => heartbeat(request);
