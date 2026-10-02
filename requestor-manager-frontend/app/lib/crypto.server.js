/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";

/*
 * AES-256-GCM encryption for the sensitive parts of the session (the JWTs),
 * so tokens are encrypted at rest inside the httpOnly cookie
 * Key is derived from SESSION_SECRET; ciphertext is iv:tag:data (base64url).
 */

function key() {
  const secret = process.env.SESSION_SECRET;
  if (!secret) throw new Error("SESSION_SECRET must be set");
  // Use the first secret if a rotation list is provided.
  const primary = secret.split(",")[0].trim();
  return createHash("sha256").update(primary).digest(); // 32 bytes
}

export function encrypt(value) {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key(), iv);
  const data = Buffer.concat([cipher.update(JSON.stringify(value), "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return `${iv.toString("base64url")}.${tag.toString("base64url")}.${data.toString("base64url")}`;
}

export function decrypt(token) {
  if (!token || typeof token !== "string") return null;
  try {
    const [ivB64, tagB64, dataB64] = token.split(".");
    const decipher = createDecipheriv("aes-256-gcm", key(), Buffer.from(ivB64, "base64url"));
    decipher.setAuthTag(Buffer.from(tagB64, "base64url"));
    const out = Buffer.concat([
      decipher.update(Buffer.from(dataB64, "base64url")),
      decipher.final(),
    ]);
    return JSON.parse(out.toString("utf8"));
  } catch {
    return null;
  }
}
