/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { requireUser } from "../lib/auth.server.js";
import Layout from "../../src/components/Layout";

export async function loader({ request }) {
  await requireUser(request);
  return null;
}

export default function Protected() {
  return <Layout />;
}
