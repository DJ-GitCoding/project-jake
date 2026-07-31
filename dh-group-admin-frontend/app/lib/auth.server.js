/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { redirect } from "react-router";
import {
  getSession,
  setAuth,
  getUser,
  commitSession,
  destroySession,
} from "./session.server.js";
import { BACKEND_URL } from "./config.server.js";

/*
 * dh-group-admin backend returns a flat auth payload (no `data` envelope, access token
 * is `token`): login -> { success, user, token, refreshToken }, refresh -> { success, token, refreshToken }.
 */

/** Exchange credentials at the backend and create an encrypted session cookie. */
export async function login(request, { email, password }) {
  let body;
  try {
    const res = await fetch(`${BACKEND_URL}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    body = await res.json().catch(() => ({}));
    if (!res.ok || !body?.success) {
      return { error: body?.error || body?.message || "Invalid email or password" };
    }
  } catch (e) {
    return { error: "Unable to reach the authentication service. Please try again." };
  }

  const { token, refreshToken, user } = body;
  if (!token) return { error: "Authentication failed" };

  const session = await getSession(request);
  setAuth(session, { user, accessToken: token, refreshToken });
  return redirect("/dashboard", {
    headers: { "Set-Cookie": await commitSession(session) },
  });
}

/** Loader guard: require an authenticated session or redirect to /login. */
export async function requireUser(request) {
  const session = await getSession(request);
  const user = getUser(session);
  if (!user) throw redirect("/login");
  return user;
}

export async function getUserOptional(request) {
  const session = await getSession(request);
  return getUser(session);
}

/** Refresh the access token server-side using the stored refresh token. */
export async function refreshTokens(refreshToken) {
  try {
    const res = await fetch(`${BACKEND_URL}/api/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok || !body?.success || !body?.token) return null;
    return { accessToken: body.token, refreshToken: body.refreshToken };
  } catch {
    return null;
  }
}

export async function logout(request) {
  const session = await getSession(request);
  return redirect("/login", {
    headers: { "Set-Cookie": await destroySession(session) },
  });
}
