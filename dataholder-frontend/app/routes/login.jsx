/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { redirect } from "react-router";
import { login, getUserOptional } from "../lib/auth.server.js";
import { getSession } from "../lib/session.server.js";
import { assertCsrf } from "../lib/csrf.server.js";

export async function loader({ request }) {
  if (await getUserOptional(request)) throw redirect("/");
  return null;
}

export async function action({ request }) {
  const session = await getSession(request);
  const form = await request.formData();
  assertCsrf(session, form);
  const email = form.get("email");
  const password = form.get("password");
  return login(request, { email, password });
}

export { default } from "../../src/pages/Login";

export function meta() {
  return [{ title: "Sign In · Data Holder" }];
}
