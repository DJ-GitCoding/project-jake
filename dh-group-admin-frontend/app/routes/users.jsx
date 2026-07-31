/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { redirect } from "react-router";
import { requireUser } from "../lib/auth.server.js";
import { useAuth } from "../../src/context/AuthContext";
import Users from "../../src/pages/Users";

// Admin-or-higher only (user.type <= 2); non-admins are redirected to the dashboard.
export async function loader({ request }) {
  const user = await requireUser(request);
  if (user.type > 2) throw redirect("/dashboard");
  return null;
}

// Wrapper supplies the `currentUser` prop from the auth context.
export default function UsersRoute() {
  const { user } = useAuth();
  return <Users currentUser={user} />;
}

export function meta() {
  return [{ title: "Users · Jareg" }];
}
