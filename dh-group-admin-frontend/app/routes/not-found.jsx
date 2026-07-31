/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { redirect } from "react-router";
import { getUserOptional } from "../lib/auth.server.js";

/*
 * Catch-all: authenticated users go to the dashboard, everyone else to the
 * login page.
 */
export async function loader({ request }) {
  const user = await getUserOptional(request);
  throw redirect(user ? "/dashboard" : "/login");
}

export default function NotFound() {
  return null;
}

export function meta() {
  return [{ title: "Page Not Found · Jareg" }];
}
