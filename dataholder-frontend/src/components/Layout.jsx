/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { Outlet } from 'react-router';
import Header from './Header';

/*
 * Authenticated shell: the header nav plus the main content region. Rendered by
 * the protected.jsx route layout (which enforces requireUser server-side).
 */
const Layout = () => {
  return (
    <>
      <Header />
      <main className="app-container">
        <Outlet />
      </main>
    </>
  );
};

export default Layout;
