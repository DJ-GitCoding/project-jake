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

const num = (envVar, fallback) => {
  const parsed = Number(process.env[envVar]);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
};

const IDLE_MS = num("SESSION_IDLE_MINUTES", 15) * 60 * 1000;
const MAX_AGE_MS = num("SESSION_MAX_HOURS", 8) * 60 * 60 * 1000;

/*
 * Server-side session store. The cookie holds only a signed httpOnly session id;
 * backend JWTs live here in this Map (AES-encrypted at rest). Kept on globalThis
 * to survive Vite dev reloads; lost on process restart. For multi-replica prod,
 * back this Map with Redis/Postgres.
 *
 * Records carry two deadlines: a sliding idle window refreshed on every read, and
 * an absolute ceiling that no amount of activity can push out.
 */
const store = (globalThis.__dhgSessionStore ||= new Map());

const isExpired = (rec, now) => rec.idleExpiresAt <= now || rec.absoluteExpiresAt <= now;

function sweep() {
  const now = Date.now();
  for (const [id, rec] of store) {
    if (isExpired(rec, now)) store.delete(id);
  }
}

function absoluteExpiryMs(expires) {
  return expires ? expires.getTime() : Date.now() + MAX_AGE_MS;
}

export const sessionStorage = createSessionStorage({
  cookie: {
    name: "__dhg_session",
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: Math.round(MAX_AGE_MS / 1000),
    secrets,
  },
  async createData(data, expires) {
    let id;
    do {
      id = randomBytes(18).toString("hex");
    } while (store.has(id));
    store.set(id, {
      data,
      idleExpiresAt: Date.now() + IDLE_MS,
      absoluteExpiresAt: absoluteExpiryMs(expires),
    });
    if (store.size % 200 === 0) sweep();
    return id;
  },
  async readData(id) {
    const rec = store.get(id);
    if (!rec) return null;
    if (isExpired(rec, Date.now())) {
      store.delete(id);
      return null;
    }
    rec.idleExpiresAt = Date.now() + IDLE_MS;
    return rec.data;
  },
  async updateData(id, data, expires) {
    const previous = store.get(id);
    store.set(id, {
      data,
      idleExpiresAt: Date.now() + IDLE_MS,
      absoluteExpiresAt: previous?.absoluteExpiresAt ?? absoluteExpiryMs(expires),
    });
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
