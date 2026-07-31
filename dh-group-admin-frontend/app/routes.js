/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { index, route, layout } from "@react-router/dev/routes";

export default [
  // Public routes (accessible whether or not logged in)
  route("join", "routes/join.jsx"),
  route("forgot-password", "routes/forgot-password.jsx"),
  route("reset-password", "routes/reset-password.jsx"),
  route("login", "routes/login.jsx"),
  route("logout", "routes/logout.jsx"),

  // Same-origin BFF proxy to the backend
  route("api/*", "routes/api.$.jsx"),

  // Protected app shell (Header + Outlet), guarded by requireUser
  layout("routes/protected.jsx", [
    index("routes/home.jsx"),
    route("dashboard", "routes/dashboard.jsx"),
    route("templates", "routes/templates.jsx"),
    route("subscriptions", "routes/subscriptions.jsx"),
    route("data-holders", "routes/data-holders.jsx"),
    route("instances", "routes/instances.jsx"),
    route("applications", "routes/applications.jsx"),
    route("requestor-groups", "routes/requestor-groups.jsx"),
    route("data-holder-groups", "routes/data-holder-groups.jsx"),
    route("users", "routes/users.jsx"),
    route("audit-logs", "routes/audit-logs.jsx"),
  ]),

  route("*", "routes/not-found.jsx"),
];
