/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { redirect } from "react-router";
import { getUserOptional } from "../lib/auth.server.js";

export async function loader({ request }) {
  const user = await getUserOptional(request);
  throw redirect(user ? "/" : "/login");
}

export default function CatchAll() {
  return null;
}
