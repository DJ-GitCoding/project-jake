/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { index, route, layout } from "@react-router/dev/routes";

export default [
  route("login", "routes/login.jsx"),
  route("logout", "routes/logout.jsx"),
  route("forgot-password", "routes/forgot-password.jsx"),
  route("reset-password", "routes/reset-password.jsx"),
  route("callback", "routes/callback.jsx"),
  route("api/*", "routes/api.$.jsx"),
  route("session/heartbeat", "routes/session.heartbeat.jsx"),
  route("dh/*", "routes/dh.$.jsx"),
  layout("routes/protected.jsx", [
    index("routes/home.jsx"),
    route("dashboard", "routes/dashboard.jsx"),
    route("rdap-requests", "routes/rdap-requests.jsx"),
    route("dataholder", "routes/dataholder.jsx"),
  ]),
  layout("routes/admin.jsx", [
    route("data-holders", "routes/data-holders.jsx"),
    route("logs", "routes/logs.jsx"),
  ]),
  route("*", "routes/not-found.jsx"),
];
