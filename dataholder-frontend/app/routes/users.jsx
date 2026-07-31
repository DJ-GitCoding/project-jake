/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { redirect } from "react-router";
import { requireUser } from "../lib/auth.server.js";

// User management is MASTER-only (feature: user-management).
export async function loader({ request }) {
  const user = await requireUser(request);
  if (user?.type !== "MASTER") throw redirect("/");
  return null;
}

export { default } from "../../src/pages/UserManagement";

export function meta() {
  return [{ title: "Users · Data Holder" }];
}
