/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { redirect } from "react-router";

export function loader() {
  throw redirect("/dashboard");
}

export default function Home() {
  return null;
}

export function meta() {
  return [{ title: "Home · Jaddar" }];
}
