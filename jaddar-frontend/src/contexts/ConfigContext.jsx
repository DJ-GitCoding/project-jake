/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { createContext, useContext } from 'react';

/*
 * Public, non-secret runtime config surfaced to the client via the root loader
 * (SSR reads it from server env; the browser can't read process.env under Vite).
 */
const ConfigContext = createContext({ keycloakAdminUrl: '', appVersion: 'dev', sourceCodeUrl: '' });

export const ConfigProvider = ({ children, config }) => (
  <ConfigContext.Provider value={config || { keycloakAdminUrl: '', appVersion: 'dev', sourceCodeUrl: '' }}>
    {children}
  </ConfigContext.Provider>
);

export const useConfig = () => useContext(ConfigContext);

export default ConfigContext;
