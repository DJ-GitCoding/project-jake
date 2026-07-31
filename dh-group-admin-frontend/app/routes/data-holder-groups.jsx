/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { redirect } from "react-router";
import { requireUser } from "../lib/auth.server.js";
import { useGroup } from "../../src/context/GroupContext";
import DataHolderGroups from "../../src/pages/DataHolderGroups";

// Master-only (user.type === 1); non-master users are redirected to the dashboard.
export async function loader({ request }) {
  const user = await requireUser(request);
  if (user.type !== 1) throw redirect("/dashboard");
  return null;
}

/*
 * Wrapper wires the `onGroupsChanged` prop to the group context's refresh so the
 * header's group selector stays in sync after edits.
 */
export default function DataHolderGroupsRoute() {
  const { refreshGroups } = useGroup();
  return <DataHolderGroups onGroupsChanged={refreshGroups} />;
}

export function meta() {
  return [{ title: "DH Groups · Jareg" }];
}
