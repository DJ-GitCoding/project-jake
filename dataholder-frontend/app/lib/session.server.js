/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { createSessionStorage } from "react-router";
import { randomBytes } from "node:crypto";
import { encrypt, decrypt } from "./crypto.server.js";

const secret = process.env.SESSION_SECRET;
if (!secret) {
  throw new Error("SESSION_SECRET must be set");
}
const secrets = secret.split(",").map((s) => s.trim()).filter(Boolean);

const MAX_AGE_MS = 8 * 60 * 60 * 1000; // 8 hours

/*
 * Server-side session store. The cookie holds only a signed httpOnly session id;
 * backend JWTs live here in this Map (AES-encrypted at rest). Kept on globalThis
 * to survive Vite dev reloads; lost on process restart. For multi-replica prod,
 * back this Map with Redis/Postgres.
 */
const store = (globalThis.__dhSessionStore ||= new Map());

function sweep() {
  const now = Date.now();
  for (const [id, rec] of store) {
    if (rec.expiresAt && rec.expiresAt < now) store.delete(id);
  }
}
function expiryMs(expires) {
  return expires ? expires.getTime() : Date.now() + MAX_AGE_MS;
}

export const sessionStorage = createSessionStorage({
  cookie: {
    name: "__dh_session",
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: MAX_AGE_MS / 1000,
    secrets,
  },
  async createData(data, expires) {
    let id;
    do {
      id = randomBytes(18).toString("hex");
    } while (store.has(id));
    store.set(id, { data, expiresAt: expiryMs(expires) });
    if (store.size % 200 === 0) sweep();
    return id;
  },
  async readData(id) {
    const rec = store.get(id);
    if (!rec) return null;
    if (rec.expiresAt && rec.expiresAt < Date.now()) {
      store.delete(id);
      return null;
    }
    return rec.data;
  },
  async updateData(id, data, expires) {
    store.set(id, { data, expiresAt: expiryMs(expires) });
  },
  async deleteData(id) {
    store.delete(id);
  },
});

export function getSession(request) {
  return sessionStorage.getSession(request.headers.get("Cookie"));
}

export function setAuth(session, { user, accessToken, refreshToken }) {
  session.set("user", user);
  session.set("tok", encrypt({ accessToken, refreshToken }));
}

export function getTokens(session) {
  return decrypt(session.get("tok"));
}

export function getUser(session) {
  return session.get("user") || null;
}

export const commitSession = (session) => sessionStorage.commitSession(session);
export const destroySession = (session) => sessionStorage.destroySession(session);
