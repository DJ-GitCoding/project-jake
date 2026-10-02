/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { Outlet } from "react-router";
import { requireAdmin } from "../lib/auth.server.js";

export async function loader({ request }) {
  // Requires an authenticated session AND an admin role, else redirects to /dashboard.
  await requireAdmin(request);
  return null;
}

export default function Admin() {
  return <Outlet />;
}
