/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import Header from '../components/Header';
import TestDataHolder from '../components/TestDataHolder';
import DataHolderAdmin from '../components/DataHolderAdmin';
import JakeCompliancePanel from '../components/JakeCompliancePanel';
import { useT } from '../i18n';

const DataHolder = () => {
  const { t } = useT();
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState('query');
  
  const isAdmin = user?.roles?.includes('admin') || user?.email === 'admin@admin.com';

  return (
    <div className="min-vh-100 bg-light">
      <Header />
      
      <div className="container-fluid px-4">
        <div className="row mb-4">
          <div className="col">
            <h2 className="mb-3">
              <i className="bi bi-database me-2"></i>
              {t('dataHolder.title')}
            </h2>
            <p className="text-muted">
              {t('dataHolder.subtitle')}
            </p>
          </div>
        </div>

        {/* Tabs */}
        <ul className="nav nav-tabs mb-4">
          <li className="nav-item">
            <button 
              className={`nav-link ${activeTab === 'query' ? 'active' : ''}`}
              onClick={() => setActiveTab('query')}
            >
              <i className="bi bi-search me-2"></i>
              {t('dataHolder.tabQuery')}
            </button>
          </li>
          <li className="nav-item">
            <button 
              className={`nav-link ${activeTab === 'compliance' ? 'active' : ''}`}
              onClick={() => setActiveTab('compliance')}
            >
              <i className="bi bi-shield-check me-2"></i>
              {t('dataHolder.tabCompliance')}
            </button>
          </li>
          {isAdmin && (
            <li className="nav-item">
              <button 
                className={`nav-link ${activeTab === 'admin' ? 'active' : ''}`}
                onClick={() => setActiveTab('admin')}
              >
                <i className="bi bi-gear me-2"></i>
                {t('dataHolder.tabAdmin')}
              </button>
            </li>
          )}
        </ul>

        {/* Tab Content */}
        {activeTab === 'query' && <TestDataHolder />}
        {activeTab === 'compliance' && <JakeCompliancePanel />}
        {activeTab === 'admin' && isAdmin && <DataHolderAdmin />}
      </div>
    </div>
  );
};

export default DataHolder;