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
import { createStore } from "./session-store.server.js";

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
 * Server-side session store. The cookie holds only a signed httpOnly session id; the
 * JWTs live in the store (AES-encrypted at rest) and never reach the browser. Backed by
 * Postgres when SESSION_DATABASE_URL is set, otherwise an in-process Map -- see
 * session-store.server.js.
 */
const store = createStore({ app: "requestor-manager" });

function absoluteExpiryMs(expires) {
  return expires ? expires.getTime() : Date.now() + MAX_AGE_MS;
}

export const sessionStorage = createSessionStorage({
  cookie: {
    name: "__rm_session",
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
    } while (await store.has(id));
    await store.create(id, data, Date.now() + IDLE_MS, absoluteExpiryMs(expires));
    return id;
  },
  async readData(id) {
    return await store.readAndTouch(id, Date.now() + IDLE_MS);
  },
  async updateData(id, data, expires) {
    await store.update(id, data, Date.now() + IDLE_MS, absoluteExpiryMs(expires));
  },
  async deleteData(id) {
    await store.destroy(id);
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
