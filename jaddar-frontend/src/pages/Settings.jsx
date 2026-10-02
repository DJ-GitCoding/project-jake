/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import Header from '../components/Header';
import BrandingSettings from '../components/BrandingSettings';
import DataHolderRegistryPanel from '../components/DataHolderRegistryPanel';
import { useAuth } from '../contexts/AuthContext';
import { useT } from '../i18n';
import RdrsMailSettingsPanel from '../components/RdrsMailSettingsPanel';

/*
 * Deployment-wide settings. Only branding lives here today; the page is built as a tab
 * strip so later settings drop in as additional tabs rather than another nav entry.
 */

const TAB_BRANDING = 'branding';
const TAB_REGISTRY = 'registry';
const TAB_RDRS_MAIL = 'rdrsMail';

const TABS = [
  { id: TAB_BRANDING, icon: 'bi-image', labelKey: 'settings.tabs.branding' },
  { id: TAB_REGISTRY, icon: 'bi-broadcast', labelKey: 'settings.tabs.registry' },
  { id: TAB_RDRS_MAIL, icon: 'bi-envelope-paper', labelKey: 'settings.tabs.rdrsMail' },
];

const Settings = () => {
  const [activeTab, setActiveTab] = useState(TAB_BRANDING);
  const { isAdmin } = useAuth();
  const { t } = useT();

  return (
    <div>
      <Header />
      <div className="container-fluid px-4">
        <div className="mb-4">
          <h2 className="mb-1">
            <i className="bi bi-gear me-2"></i>
            {t('settings.title')}
          </h2>
          <p className="text-muted mb-0">{t('settings.subtitle')}</p>
        </div>

        <ul className="nav nav-tabs mb-4">
          {TABS.map(tab => (
            <li className="nav-item" key={tab.id}>
              <button
                type="button"
                className={`nav-link ${activeTab === tab.id ? 'active' : ''}`}
                onClick={() => setActiveTab(tab.id)}
              >
                <i className={`bi ${tab.icon} me-2`}></i>{t(tab.labelKey)}
              </button>
            </li>
          ))}
        </ul>

        {activeTab === TAB_BRANDING && <BrandingSettings canManage={isAdmin()} />}
        {activeTab === TAB_REGISTRY && <DataHolderRegistryPanel canManage={isAdmin()} />}
        {activeTab === TAB_RDRS_MAIL && <RdrsMailSettingsPanel canManage={isAdmin()} />}
      </div>
    </div>
  );
};

export default Settings;
