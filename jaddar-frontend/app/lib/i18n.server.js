/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

/*
 * Read the language preference from the request cookie (mirrors the client's
 * jaddar_language cookie), so SSR renders in the correct language and hydration matches.
 */
const STORAGE_KEY = "jaddar_language";
const SUPPORTED = new Set(["english"]);

export function getLanguage(request) {
  const cookie = request.headers.get("Cookie") || "";
  const match = cookie.match(new RegExp(`(?:^|;\\s*)${STORAGE_KEY}=([^;]+)`));
  const lang = match ? decodeURIComponent(match[1]) : "english";
  return SUPPORTED.has(lang) ? lang : "english";
}
