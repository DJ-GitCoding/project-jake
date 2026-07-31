/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';
import { useT } from '../i18n';

/*
 * External Database shell: data mapping, schema diagram and data browser render
 * as child routes under one tab bar, so each tab keeps its own URL.
 */
const TABS = [
  {
    to: '/external-database/mapping',
    icon: 'fa-diagram-project',
    labelKey: 'externalDatabase.tabs.mapping',
    descriptionKey: 'rdapMappings.pageSubtitle',
  },
  {
    to: '/external-database/schema',
    icon: 'fa-sitemap',
    labelKey: 'externalDatabase.tabs.schema',
    descriptionKey: 'rdapSchema.subtitle',
  },
  {
    to: '/external-database/browser',
    icon: 'fa-table-list',
    labelKey: 'externalDatabase.tabs.browser',
    descriptionKey: 'dataBrowser.subtitle',
  },
];

const ExternalDatabase = () => {
  const { t } = useT();
  const { pathname } = useLocation();

  const activeTab = TABS.find((tab) => pathname.startsWith(tab.to)) || TABS[0];

  return (
    <div>
      <div className="mb-3">
        <h2 className="h3 fw-bold mb-1">{t('externalDatabase.title')}</h2>
        <p className="text-muted mb-0">{t(activeTab.descriptionKey)}</p>
      </div>

      <ul className="nav nav-tabs mb-4">
        {TABS.map((tab) => (
          <li className="nav-item" key={tab.to}>
            <NavLink
              to={tab.to}
              className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
            >
              <i className={`fa-solid ${tab.icon} me-2`}></i>
              {t(tab.labelKey)}
            </NavLink>
          </li>
        ))}
      </ul>

      <Outlet />
    </div>
  );
};

export default ExternalDatabase;
