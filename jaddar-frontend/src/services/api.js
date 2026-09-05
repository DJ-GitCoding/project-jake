/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import axios from 'axios';

/*
 * Same-origin axios: all calls hit the SSR /api/* BFF proxy, which attaches the Bearer
 * token from the httpOnly session and forwards to the backend. No auth interceptors
 * needed here; instance-scoped (not axios.defaults) so nothing leaks across SSR requests.
 */
const api = axios.create({
  baseURL: '',
});

/*
 * On 401 (session expired / not authenticated) send the user to the login page.
 */
let redirectingToLogin = false;
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (
      error.response?.status === 401 &&
      typeof window !== 'undefined' &&
      !redirectingToLogin &&
      !window.location.pathname.startsWith('/login')
    ) {
      redirectingToLogin = true;
      window.location.href = '/login?expired=1';
    }
    return Promise.reject(error);
  }
);

export default api;
