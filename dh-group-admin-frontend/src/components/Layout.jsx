/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { Outlet, useRouteLoaderData } from 'react-router';
import Header from './Header';
import SessionTimeout from './SessionTimeout';

const Layout = () => {
  const config = useRouteLoaderData('root')?.config;

  return (
    <div className="App">
      <Header />
      <main className="main-content bg-light">
        <div className="p-4">
          <Outlet />
        </div>
      </main>

      <SessionTimeout
        idleMinutes={config?.sessionIdleMinutes}
        warnSeconds={config?.sessionWarnSeconds}
      />
    </div>
  );
};

export default Layout;
