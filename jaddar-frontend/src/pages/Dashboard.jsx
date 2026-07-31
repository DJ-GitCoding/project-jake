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
import Footer from '../components/Footer';
import WhoisSearch from '../components/WhoisSearch';
import RDAPSearch from '../components/RDAPSearch';
import api from '../services/api';
import { useT } from '../i18n';

const Dashboard = () => {
  const { user } = useAuth();
  const { t } = useT();
  const [protectedData, setProtectedData] = useState(null);
  const [activeService, setActiveService] = useState('rdap');

  const getDisplayName = () => {
    if (!user) return t('dashboard.defaultUser');
    if (user.name) return user.name;
    if (user.given_name) return user.given_name;
    if (user.preferred_username) return user.preferred_username;
    if (user.email) return user.email.split('@')[0];
    return t('dashboard.defaultUser');
  };

  const getGreeting = () => {
    const hour = new Date().getHours();
    if (hour < 12) return t('dashboard.goodMorning');
    if (hour < 18) return t('dashboard.goodAfternoon');
    return t('dashboard.goodEvening');
  };
 
  const fetchProtectedData = async () => {
    try {
      const response = await api.get('/api/protected');
      setProtectedData(response.data);
    } catch (error) {
      console.error('Failed to fetch protected data:', error);
    }
  };
 
  return (
    <div className="min-vh-100 bg-light d-flex flex-column">
      <Header />
     
      <div className="container-fluid px-4 pb-5 flex-grow-1">
        <div className="row">
          {/* Compact Welcome Bar */}
          <div className="col-12 mb-3">
            <div className="d-flex flex-column flex-sm-row align-items-sm-center justify-content-sm-between bg-primary text-white rounded px-3 py-2 shadow-sm gap-1">
              <div className="d-flex align-items-center">
                <i className="bi bi-person-circle me-2 fs-5"></i>
                <span className="fw-semibold">{getGreeting()}, {getDisplayName()}</span>
              </div>
              <div className="d-flex align-items-center gap-3 opacity-75 small flex-wrap">
                {user?.email && (
                  <span><i className="bi bi-envelope me-1"></i>{user.email}</span>
                )}
                {user?.groups && user.groups.length > 0 && (
                  <span><i className="bi bi-people me-1"></i>{user.groups.join(', ')}</span>
                )}
              </div>
            </div>
          </div>
         
          {/* Service Selection Tabs */}
          <div className="col-12 mb-4">
            <div className="card shadow-sm">
              <div className="card-header bg-white border-bottom-0 pb-0">
                <ul className="nav nav-tabs card-header-tabs">
                  <li className="nav-item">
                    <button
                      className={`nav-link ${activeService === 'rdap' ? 'active' : ''}`}
                      onClick={() => setActiveService('rdap')}
                    >
                      <i className="bi bi-search me-2"></i>
                      {t('dashboard.rdapWhoisSearch')}
                    </button>
                  </li>
                </ul>
              </div>
              <div className="card-body">
                {activeService === 'rdap' ? (
                  <RDAPSearch />
                ) : (
                  <WhoisSearch />
                )}
              </div>
            </div>
          </div>
        
        </div>
      </div>
    </div>
  );
};

export default Dashboard;