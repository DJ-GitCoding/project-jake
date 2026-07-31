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
  getTokens,
  commitSession,
  destroySession,
} from "./session.server.js";
import { BACKEND_URL } from "./config.server.js";

/*
 * Decode a JWT payload server-side (no verification — the backend already issued
 * and verified it; we only read claims like realm_access.roles / groups / email).
 */
function decodeJwt(token) {
  try {
    const payload = token.split(".")[1];
    const json = Buffer.from(payload, "base64url").toString("utf8");
    return JSON.parse(json);
  } catch {
    return {};
  }
}

// Determine whether a user is an admin.
export function isAdminUser(user) {
  if (!user) return false;
  return (
    (Array.isArray(user.roles) && user.roles.includes("admin")) ||
    user.email === "admin@admin.com"
  );
}

/*
 * Build a user profile from the access token + backend userinfo endpoint.
 * Roles/groups come from the JWT (realm_access.roles); identity is enriched
 * from /api/auth/userinfo when available.
 */
async function buildUser(accessToken) {
  const claims = decodeJwt(accessToken);
  const roles = claims.realm_access?.roles || [];
  const groups = claims.groups || [];

  let profile = {
    email: claims.email,
    name: claims.name,
    given_name: claims.given_name,
    preferred_username: claims.preferred_username,
  };

  try {
    const res = await fetch(`${BACKEND_URL}/api/auth/userinfo`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (res.ok) {
      const info = await res.json().catch(() => ({}));
      profile = { ...profile, ...info };
    }
  } catch {
    // Network error — fall back to the JWT claims we already have.
  }

  return { ...profile, roles, groups };
}

/*
 * Store tokens + freshly built user profile into an encrypted session cookie
 * and redirect to the dashboard.
 */
async function establishSession(request, tokens) {
  const { access_token, id_token, refresh_token } = tokens;
  if (!access_token) return { error: "Authentication failed" };

  const user = await buildUser(access_token);
  const session = await getSession(request);
  setAuth(session, {
    user,
    accessToken: access_token,
    idToken: id_token,
    refreshToken: refresh_token,
  });
  return redirect("/dashboard", {
    headers: { "Set-Cookie": await commitSession(session) },
  });
}

/** Password login: exchange credentials at the backend, create the session. */
export async function login(request, { email, password }) {
  let body;
  try {
    const res = await fetch(`${BACKEND_URL}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      // The backend expects `username`; the UI collects an email address.
      body: JSON.stringify({ username: email, password }),
    });
    body = await res.json().catch(() => ({}));
    if (!res.ok || !body?.access_token) {
      return { error: body?.error || body?.detail || "Incorrect email or password" };
    }
  } catch (e) {
    return { error: "Unable to reach the authentication service. Please try again." };
  }
  return establishSession(request, body);
}

/** Authorization-code login: exchange the ?code= for tokens, create the session. */
export async function loginWithCode(request, code) {
  let body;
  try {
    const res = await fetch(`${BACKEND_URL}/api/auth/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ code }),
    });
    body = await res.json().catch(() => ({}));
    if (!res.ok || !body?.access_token) {
      return { error: body?.error || body?.detail || "Authentication failed" };
    }
  } catch (e) {
    return { error: "Unable to reach the authentication service. Please try again." };
  }
  return establishSession(request, body);
}

/** Loader guard: require an authenticated session or redirect to /login. */
export async function requireUser(request) {
  const session = await getSession(request);
  const user = getUser(session);
  if (!user) throw redirect("/login");
  return user;
}

/** Loader guard: require an admin session, else redirect to /dashboard. */
export async function requireAdmin(request) {
  const user = await requireUser(request);
  if (!isAdminUser(user)) throw redirect("/dashboard");
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
      body: JSON.stringify({ refresh_token: refreshToken }),
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok || !body?.access_token) return null;
    return {
      accessToken: body.access_token,
      idToken: body.id_token,
      refreshToken: body.refresh_token || refreshToken,
    };
  } catch {
    return null;
  }
}

export async function logout(request) {
  const session = await getSession(request);
  // Best-effort backend logout with the current id token.
  const tokens = getTokens(session);
  if (tokens?.idToken || tokens?.accessToken) {
    fetch(`${BACKEND_URL}/api/auth/logout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id_token: tokens?.idToken }),
    }).catch(() => {});
  }
  return redirect("/login", {
    headers: { "Set-Cookie": await destroySession(session) },
  });
}
