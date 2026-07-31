/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { index, route, layout } from "@react-router/dev/routes";

export default [
  // Public routes
  route("login", "routes/login.jsx"),
  route("logout", "routes/logout.jsx"),
  route("forgot-password", "routes/forgot-password.jsx"),
  route("reset-password", "routes/reset-password.jsx"),

  // Backend-for-frontend proxies (attach the session Bearer server-side)
  route("api/*", "routes/api.$.jsx"),
  route("domain/*", "routes/rdap-domain.$.jsx"),
  route("ip/*", "routes/rdap-ip.$.jsx"),
  route("autnum/*", "routes/rdap-autnum.$.jsx"),

  // Legacy redirects -> data holder groups
  route("subscriptions", "routes/legacy-subscriptions.jsx"),
  route("agreement-templates", "routes/legacy-agreement-templates.jsx"),
  route("agreements", "routes/legacy-agreements.jsx"),
  route("agreement-requests", "routes/legacy-agreement-requests.jsx"),

  // Protected app (requireUser + AuthenticatedLayout)
  layout("routes/protected.jsx", [
    index("routes/dashboard.jsx"),
    route("pending", "routes/pending.jsx"),
    route("data-holder-groups", "routes/data-holder-groups.jsx"),
    route("policy", "routes/policy.jsx"),
    route("logs", "routes/logs.jsx"),
    route("query", "routes/query.jsx"),
    route("rdap-data", "routes/rdap-data.jsx"),
    route("settings", "routes/settings.jsx"),
    route("users", "routes/users.jsx"),
    route("external-database", "routes/external-database.jsx", [
      index("routes/external-database.index.jsx"),
      route("mapping", "routes/external-database.mapping.jsx"),
      route("schema", "routes/external-database.schema.jsx"),
      route("browser", "routes/external-database.browser.jsx"),
    ]),
    route("file-automation", "routes/file-automation.jsx"),
  ]),

  // Catch-all -> dashboard (if authed) or login
  route("*", "routes/catch-all.jsx"),
];
