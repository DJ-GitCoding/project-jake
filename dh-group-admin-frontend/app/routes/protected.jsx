/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { useLoaderData } from "react-router";
import { requireUser } from "../lib/auth.server.js";
import { GroupProvider } from "../../src/context/GroupContext";
import Layout from "../../src/components/Layout";

export async function loader({ request }) {
  const user = await requireUser(request);
  return { user };
}

export default function Protected() {
  const { user } = useLoaderData();
  return (
    <GroupProvider user={user}>
      <Layout />
    </GroupProvider>
  );
}
