/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

/*
 * Server-side backend base URLs, read at request time (real runtime config).
 * The browser never sees these — only the SSR server (loaders/actions/BFF proxy)
 * talks to the backends.
 */

// Jaddar backend (default internal docker service DNS).
export const BACKEND_URL =
  process.env.BACKEND_API_URL || "http://jaddar-backend:8000";

// Data Holder backend, used by the /dh BFF proxy for the test/admin panels.
export const DATAHOLDER_URL =
  process.env.DATAHOLDER_API_URL || "http://dataholder-backend:8082";

// Public Keycloak admin console URL (a browser-facing link surfaced to admins).
export const KEYCLOAK_ADMIN_URL =
  process.env.REACT_APP_KEYCLOAK_ADMIN_URL || "";

// App version string surfaced in the UI.
export const APP_VERSION = process.env.REACT_APP_VERSION || "dev";

/*
 * Public source-code URL, offered to users who interact with this software over a
 * network, per AGPL-3.0 Section 13. Set SOURCE_CODE_URL in the environment; when it
 * is unset the footer link is omitted rather than rendered broken. If you deploy a
 * modified version, point this at the source of YOUR version.
 */
export const SOURCE_CODE_URL = process.env.SOURCE_CODE_URL || "";
