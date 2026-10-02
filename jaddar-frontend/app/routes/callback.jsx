/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { Link, useLoaderData } from "react-router";
import { loginWithCode } from "../lib/auth.server.js";

/*
 * Authorization-code login. The loader exchanges ?code= for tokens server-side,
 * stores them in the encrypted session, and redirects to /dashboard. On success
 * the component never renders (the redirect wins); on failure it shows the error.
 */
export async function loader({ request }) {
  const url = new URL(request.url);
  const error = url.searchParams.get("error");
  if (error) {
    return { error: `Authentication error: ${error}` };
  }
  const code = url.searchParams.get("code");
  if (!code) {
    return { error: "No authorization code was provided." };
  }
  // Returns a redirect (Response) on success, or { error } on failure.
  return loginWithCode(request, code);
}

export default function Callback() {
  const data = useLoaderData();
  const error = data?.error;

  if (error) {
    return (
      <div className="container-fluid min-vh-100 d-flex align-items-center justify-content-center bg-light">
        <div className="row w-100 justify-content-center">
          <div className="col-11 col-sm-8 col-md-6 col-lg-4">
            <div className="card shadow-lg border-0">
              <div className="card-body p-5 text-center">
                <div className="mb-4">
                  <i className="bi bi-x-circle-fill text-danger display-4"></i>
                  <h2 className="fw-bold text-danger mb-3 mt-3">Sign-in failed</h2>
                  <p className="text-muted mb-4">{error}</p>
                </div>
                <Link to="/login" className="btn btn-primary w-100 py-3 fw-semibold">
                  <i className="bi bi-arrow-left me-2"></i>
                  Back to Login
                </Link>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container-fluid min-vh-100 d-flex align-items-center justify-content-center bg-light">
      <div className="text-center">
        <div className="spinner-border text-primary mb-3" role="status" style={{ width: "3rem", height: "3rem" }}>
          <span className="visually-hidden">Loading</span>
        </div>
        <h4 className="fw-semibold text-primary">Signing you in…</h4>
      </div>
    </div>
  );
}

export function meta() {
  return [{ title: "Signing In · Jaddar" }];
}
