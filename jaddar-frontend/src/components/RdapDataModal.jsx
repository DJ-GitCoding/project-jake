/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import ReflectiveRdapRenderer from './ReflectiveRdapRenderer';
import { useT } from '../i18n';

/**
 * Modal component for displaying RDAP data from a completed request.
 * Uses ReflectiveRdapRenderer for dynamic data display.
 */
const RdapDataModal = ({ show, onHide, request }) => {
  const { t } = useT();
  const [activeTab, setActiveTab] = useState('parsed');

  if (!show || !request) return null;

  const data = request.rdap_data || {};

  const formatDate = (dateString) => {
    if (!dateString) return t('rdapDataModal.notAvailable');
    try {
      return new Date(dateString).toLocaleString();
    } catch {
      return dateString;
    }
  };

  return (
    <div className="modal fade show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }} tabIndex="-1">
      <div className="modal-dialog modal-xl modal-dialog-scrollable">
        <div className="modal-content">
          <div className="modal-header">
            <h5 className="modal-title">
              <i className="bi bi-file-earmark-text me-2 text-primary"></i>
              {t('rdapDataModal.title')} - {request.query_value}
              <span className={`badge ms-2 ${
                request.query_type === 'domain' ? 'bg-primary' :
                request.query_type === 'ip' ? 'bg-info' :
                'bg-secondary'
              }`}>
                {request.query_type?.toUpperCase()}
              </span>
            </h5>
            <button type="button" className="btn-close" onClick={onHide}></button>
          </div>
          
          <ul className="nav nav-tabs px-3 pt-2">
            <li className="nav-item">
              <button className={`nav-link ${activeTab === 'parsed' ? 'active' : ''}`} onClick={() => setActiveTab('parsed')}>
                <i className="bi bi-grid-3x3-gap me-2"></i>{t('rdapDataModal.parsedData')}
              </button>
            </li>
            <li className="nav-item">
              <button className={`nav-link ${activeTab === 'raw' ? 'active' : ''}`} onClick={() => setActiveTab('raw')}>
                <i className="bi bi-code-square me-2"></i>{t('rdapDataModal.rawJson')}
              </button>
            </li>
          </ul>

          <div className="modal-body" style={{ maxHeight: '60vh', overflowY: 'auto' }}>
            {activeTab === 'parsed' ? (
              <ReflectiveRdapRenderer data={data} accessLevel={request.access_level_granted} />
            ) : (
              <pre className="bg-dark text-light p-3 rounded mb-0">
                <code>{JSON.stringify(data, null, 2)}</code>
              </pre>
            )}
          </div>
          
          <div className="modal-footer bg-light">
            <div className="me-auto small text-muted">
              <i className="bi bi-clock me-1"></i>
              {t('rdapDataModal.requested')} {formatDate(request.created_at)}
              {request.resolved_at && (
                <span className="ms-3">
                  <i className="bi bi-check-circle me-1"></i>
                  {t('rdapDataModal.resolved')} {formatDate(request.resolved_at)}
                </span>
              )}
            </div>
            <button type="button" className="btn btn-secondary" onClick={onHide}>{t('rdapDataModal.close')}</button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default RdapDataModal;