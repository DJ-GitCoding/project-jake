/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

/*
 * Server-side backend base URL, read at request time. Defaults to internal docker DNS
 * for requestor-manager-backend (context path /agreements). The browser never sees
 * this — only the SSR server (loaders/actions/BFF proxy) talks to the backend.
 */
export const BACKEND_URL =
  process.env.BACKEND_API_URL || "http://requestor-manager-backend:8081/agreements";

/*
 * Public source-code URL, offered to users who interact with this software over a
 * network, per AGPL-3.0 Section 13. Set SOURCE_CODE_URL in the environment; when it
 * is unset the footer link is omitted rather than rendered broken. If you deploy a
 * modified version, point this at the source of YOUR version.
 */
export const SOURCE_CODE_URL = process.env.SOURCE_CODE_URL || "";

const positiveNumber = (envVar, fallback) => {
  const parsed = Number(process.env[envVar]);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
};

export const SESSION_IDLE_MINUTES = positiveNumber("SESSION_IDLE_MINUTES", 15);

export const SESSION_WARN_SECONDS = positiveNumber("SESSION_WARN_SECONDS", 120);
