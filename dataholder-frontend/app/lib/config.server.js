/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

/*
 * Server-side backend base URL, read at request time. Defaults to internal docker DNS
 * for dataholder-backend, which has no context path — paths like /api, /domain, /ip,
 * /autnum are appended directly. Only the SSR server talks to the backend, not the browser.
 */
export const BACKEND_URL =
  process.env.BACKEND_API_URL || "http://dataholder-backend:8082";

/*
 * Public source-code URL, offered to users who interact with this software over a
 * network, per AGPL-3.0 Section 13. Set SOURCE_CODE_URL in the environment; when it
 * is unset the footer link is omitted rather than rendered broken. If you deploy a
 * modified version, point this at the source of YOUR version.
 */
export const SOURCE_CODE_URL = process.env.SOURCE_CODE_URL || "";
