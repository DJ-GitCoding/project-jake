/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import BrandingSettings from '../components/BrandingSettings';
import CommonRepositorySettings from '../components/CommonRepositorySettings';
import { useAuth } from '../context/AuthContext';
import { useT } from '../i18n';

/** Deployment-wide settings: branding, and publication to the common repository. */
const TAB_BRANDING = 'branding';
const TAB_REPOSITORY = 'repository';

const TABS = [
  { id: TAB_BRANDING, icon: 'fa-image', labelKey: 'settings.tabs.branding' },
  { id: TAB_REPOSITORY, icon: 'fa-globe', labelKey: 'settings.tabs.commonRepository' },
];

const Settings = () => {
  const [activeTab, setActiveTab] = useState(TAB_BRANDING);
  const { isMaster } = useAuth();
  const { t } = useT();

  return (
    <div>
      <div className="mb-4">
        <h2 className="mb-1">{t('settings.title')}</h2>
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
              <i className={`fa-solid ${tab.icon} me-2`}></i>{t(tab.labelKey)}
            </button>
          </li>
        ))}
      </ul>

      {activeTab === TAB_BRANDING && <BrandingSettings canManage={isMaster} />}
      {activeTab === TAB_REPOSITORY && <CommonRepositorySettings canManage={isMaster} />}
    </div>
  );
};

export default Settings;
