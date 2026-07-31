/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback } from 'react';
import Pagination from './Pagination';
import usePagination from '../hooks/usePagination';
import { useT } from '../i18n';

const DataHolderAdmin = () => {
  const { t } = useT();
  // State
  const [pendingRequests, setPendingRequests] = useState([]);
  const [agreementLevels, setAgreementLevels] = useState([]);
  const [policy, setPolicy] = useState(null);
  const [auditLogs, setAuditLogs] = useState([]);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState('pending');
  const [selectedRequest, setSelectedRequest] = useState(null);
  const [reviewNotes, setReviewNotes] = useState('');

  const pendingPager = usePagination(pendingRequests);
  const agreementsPager = usePagination(agreementLevels);

  // Admin credentials (in real app, would be from login)
  const [adminCredentials, setAdminCredentials] = useState({
    username: 'admin',
    password: 'admin123',
  });
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  
  const getAuthHeader = useCallback(() => {
    const encoded = btoa(`${adminCredentials.username}:${adminCredentials.password}`);
    return `Basic ${encoded}`;
  }, [adminCredentials]);
  
  const fetchData = useCallback(async () => {
    if (!isLoggedIn) return;
    
    setLoading(true);
    setError(null);
    
    try {
      // Fetch pending requests
      const pendingRes = await fetch(`/dh/api/admin/pending-requests`, {
        headers: { 'Authorization': getAuthHeader() },
      });
      if (pendingRes.ok) {
        setPendingRequests(await pendingRes.json());
      }
      
      // Fetch stats
      const statsRes = await fetch(`/dh/api/admin/pending-requests/stats`, {
        headers: { 'Authorization': getAuthHeader() },
      });
      if (statsRes.ok) {
        setStats(await statsRes.json());
      }
      
      // Fetch agreement levels
      const levelsRes = await fetch(`/dh/api/admin/agreement-levels`, {
        headers: { 'Authorization': getAuthHeader() },
      });
      if (levelsRes.ok) {
        setAgreementLevels(await levelsRes.json());
      }
      
      // Fetch policy
      const policyRes = await fetch(`/dh/api/admin/policy`, {
        headers: { 'Authorization': getAuthHeader() },
      });
      if (policyRes.ok) {
        setPolicy(await policyRes.json());
      }
      
      // Fetch audit logs
      const logsRes = await fetch(`/dh/api/admin/audit-logs?size=20`, {
        headers: { 'Authorization': getAuthHeader() },
      });
      if (logsRes.ok) {
        const data = await logsRes.json();
        setAuditLogs(data.content || []);
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [isLoggedIn, getAuthHeader]);
  
  useEffect(() => {
    fetchData();
    
    // Auto-refresh every 30 seconds
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, [fetchData]);
  
  const handleLogin = async (e) => {
    e.preventDefault();
    try {
      const res = await fetch(`/dh/api/admin/health`, {
        headers: { 'Authorization': getAuthHeader() },
      });
      if (res.ok) {
        setIsLoggedIn(true);
      } else {
        setError(t('dataHolderAdmin.login.invalidCredentials'));
      }
    } catch (err) {
      setError(t('dataHolderAdmin.login.connectFailed'));
    }
  };
  
  const handleReview = async (requestId, action) => {
    try {
      const res = await fetch(`/dh/api/admin/pending-requests/${requestId}/review`, {
        method: 'POST',
        headers: {
          'Authorization': getAuthHeader(),
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          action,
          adminNotes: reviewNotes,
        }),
      });
      
      if (res.ok) {
        setSelectedRequest(null);
        setReviewNotes('');
        fetchData();
      } else {
        const data = await res.json();
        setError(data.error || t('dataHolderAdmin.review.reviewFailed'));
      }
    } catch (err) {
      setError(err.message);
    }
  };
  
  const handleUpdatePolicy = async (updates) => {
    try {
      const res = await fetch(`/dh/api/admin/policy`, {
        method: 'PUT',
        headers: {
          'Authorization': getAuthHeader(),
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ ...policy, ...updates }),
      });
      
      if (res.ok) {
        setPolicy(await res.json());
      }
    } catch (err) {
      setError(err.message);
    }
  };
  
  // Login form
  if (!isLoggedIn) {
    return (
      <div className="admin-login">
        <h1>{t('dataHolderAdmin.login.heading')}</h1>
        <form onSubmit={handleLogin}>
          <div className="form-group">
            <label>{t('dataHolderAdmin.login.username')}</label>
            <input
              type="text"
              value={adminCredentials.username}
              onChange={e => setAdminCredentials(prev => ({ ...prev, username: e.target.value }))}
            />
          </div>
          <div className="form-group">
            <label>{t('dataHolderAdmin.login.password')}</label>
            <input
              type="password"
              value={adminCredentials.password}
              onChange={e => setAdminCredentials(prev => ({ ...prev, password: e.target.value }))}
            />
          </div>
          {error && <div className="alert alert-error">{error}</div>}
          <button type="submit" className="btn btn-primary">{t('dataHolderAdmin.login.submit')}</button>
        </form>
        <style jsx>{`
          .admin-login {
            max-width: 400px;
            margin: 4rem auto;
            padding: 2rem;
            background: white;
            border-radius: 12px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.1);
          }
          .form-group {
            margin-bottom: 1rem;
          }
          .form-group label {
            display: block;
            margin-bottom: 0.5rem;
            font-weight: 600;
          }
          .form-group input {
            width: 100%;
            padding: 0.75rem;
            border: 1px solid #ddd;
            border-radius: 6px;
          }
          .btn {
            width: 100%;
            padding: 0.75rem;
            background: #2196f3;
            color: white;
            border: none;
            border-radius: 6px;
            cursor: pointer;
            font-size: 1rem;
          }
          .alert-error {
            background: #f8d7da;
            color: #721c24;
            padding: 0.75rem;
            border-radius: 6px;
            margin-bottom: 1rem;
          }
        `}</style>
      </div>
    );
  }
  
  return (
    <div className="admin-container">
      <header className="admin-header">
        <h1>{t('dataHolderAdmin.header.heading')}</h1>
        <div className="header-actions">
          <button onClick={fetchData} className="btn btn-secondary">
            🔄 {t('dataHolderAdmin.header.refresh')}
          </button>
          <button onClick={() => setIsLoggedIn(false)} className="btn btn-outline">
            {t('dataHolderAdmin.header.logout')}
          </button>
        </div>
      </header>
      
      {/* Stats */}
      {stats && (
        <div className="stats-bar">
          <div className="stat">
            <span className="stat-value pending">{stats.pending}</span>
            <span className="stat-label">{t('dataHolderAdmin.stats.pending')}</span>
          </div>
          <div className="stat">
            <span className="stat-value approved">{stats.approved}</span>
            <span className="stat-label">{t('dataHolderAdmin.stats.approved')}</span>
          </div>
          <div className="stat">
            <span className="stat-value denied">{stats.denied}</span>
            <span className="stat-label">{t('dataHolderAdmin.stats.denied')}</span>
          </div>
          <div className="stat">
            <span className="stat-value total">{stats.total}</span>
            <span className="stat-label">{t('dataHolderAdmin.stats.total')}</span>
          </div>
        </div>
      )}
      
      {/* Tabs */}
      <div className="tabs">
        {['pending', 'policy', 'agreements', 'logs'].map(tab => (
          <button
            key={tab}
            className={`tab ${activeTab === tab ? 'active' : ''}`}
            onClick={() => setActiveTab(tab)}
          >
            {t(`dataHolderAdmin.tabs.${tab}`)}
            {tab === 'pending' && pendingRequests.length > 0 && (
              <span className="badge">{pendingRequests.length}</span>
            )}
          </button>
        ))}
      </div>
      
      {error && <div className="alert alert-error">{error}</div>}
      
      <div className="tab-content">
        {/* Pending Requests Tab */}
        {activeTab === 'pending' && (
          <div className="pending-section">
            {pendingRequests.length === 0 ? (
              <p className="empty-state">{t('dataHolderAdmin.pending.empty')}</p>
            ) : (
              <div className="requests-list">
                {pendingPager.pageItems.map(request => (
                  <div key={request.requestId} className="request-card">
                    <div className="request-header">
                      <span className="query-type">{request.queryType.toUpperCase()}</span>
                      <span className="query-value">{request.queryValue}</span>
                      <span className="access-level">{t('dataHolderAdmin.pending.level', { n: request.requestedAccessLevel })}</span>
                    </div>
                    <div className="request-details">
                      <p><strong>{t('dataHolderAdmin.pending.user')}</strong> {request.requestorUsername} ({request.requestorEmail})</p>
                      <p><strong>{t('dataHolderAdmin.pending.agreements')}</strong> {request.agreementIds?.join(', ') || t('dataHolderAdmin.pending.none')}</p>
                      <p><strong>{t('dataHolderAdmin.pending.requested')}</strong> {new Date(request.createdAt).toLocaleString()}</p>
                      <p><strong>{t('dataHolderAdmin.pending.expires')}</strong> {new Date(request.expiresAt).toLocaleString()}</p>
                    </div>
                    <div className="request-actions">
                      <button
                        className="btn btn-approve"
                        onClick={() => setSelectedRequest(request)}
                      >
                        {t('dataHolderAdmin.pending.review')}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
            {pendingRequests.length > 0 && (
              <Pagination
                page={pendingPager.page}
                pageSize={pendingPager.pageSize}
                totalItems={pendingPager.totalItems}
                onPageChange={pendingPager.setPage}
                onPageSizeChange={pendingPager.setPageSize}
                itemLabel={t('dataHolderAdmin.pending.itemLabel')}
              />
            )}
          </div>
        )}

        {/* Policy Tab */}
        {activeTab === 'policy' && policy && (
          <div className="policy-section">
            <h2>{t('dataHolderAdmin.policy.title')}</h2>
            <div className="policy-form">
              <div className="form-group">
                <label>{t('dataHolderAdmin.policy.defaultAccessLevel')}</label>
                <select
                  value={policy.defaultAccessLevel}
                  onChange={e => handleUpdatePolicy({ defaultAccessLevel: parseInt(e.target.value) })}
                >
                  {[0, 1, 2, 3].map(level => (
                    <option key={level} value={level}>{t('dataHolderAdmin.policy.level', { n: level })}</option>
                  ))}
                </select>
              </div>

              <div className="form-group">
                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={policy.requiresAgreement}
                    onChange={e => handleUpdatePolicy({ requiresAgreement: e.target.checked })}
                  />
                  {t('dataHolderAdmin.policy.requireAgreement')}
                </label>
              </div>

              <div className="form-group">
                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={policy.requiresManualVerification}
                    onChange={e => handleUpdatePolicy({ requiresManualVerification: e.target.checked })}
                  />
                  {t('dataHolderAdmin.policy.enableManualVerification')}
                </label>
              </div>

              {policy.requiresManualVerification && (
                <div className="form-group">
                  <label>{t('dataHolderAdmin.policy.manualVerificationThreshold')}</label>
                  <select
                    value={policy.manualVerificationThreshold}
                    onChange={e => handleUpdatePolicy({ manualVerificationThreshold: parseInt(e.target.value) })}
                  >
                    {[0, 1, 2, 3].map(level => (
                      <option key={level} value={level}>{t('dataHolderAdmin.policy.levelPlus', { n: level })}</option>
                    ))}
                  </select>
                  <p className="help-text">
                    {t('dataHolderAdmin.policy.thresholdHelp')}
                  </p>
                </div>
              )}
            </div>
          </div>
        )}
        
        {/* Agreements Tab */}
        {activeTab === 'agreements' && (
          <div className="agreements-section">
            <h2>{t('dataHolderAdmin.agreements.title')}</h2>
            <table className="data-table">
              <thead>
                <tr>
                  <th>{t('dataHolderAdmin.agreements.colId')}</th>
                  <th>{t('dataHolderAdmin.agreements.colName')}</th>
                  <th>{t('dataHolderAdmin.agreements.colAccessLevel')}</th>
                  <th>{t('dataHolderAdmin.agreements.colStatus')}</th>
                </tr>
              </thead>
              <tbody>
                {agreementsPager.pageItems.map(level => (
                  <tr key={level.agreementId}>
                    <td>{level.agreementId}</td>
                    <td>{level.agreementName}</td>
                    <td>
                      <span className={`level-badge level-${level.accessLevel}`}>
                        {t('dataHolderAdmin.agreements.level', { n: level.accessLevel })}
                      </span>
                    </td>
                    <td>
                      <span className={`status-badge ${level.isActive ? 'active' : 'inactive'}`}>
                        {level.isActive ? t('dataHolderAdmin.agreements.active') : t('dataHolderAdmin.agreements.inactive')}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {agreementLevels.length > 0 && (
              <Pagination
                page={agreementsPager.page}
                pageSize={agreementsPager.pageSize}
                totalItems={agreementsPager.totalItems}
                onPageChange={agreementsPager.setPage}
                onPageSizeChange={agreementsPager.setPageSize}
                itemLabel={t('dataHolderAdmin.agreements.itemLabel')}
              />
            )}
          </div>
        )}

        {/* Audit Logs Tab */}
        {activeTab === 'logs' && (
          <div className="logs-section">
            <h2>{t('dataHolderAdmin.logs.title')}</h2>
            <table className="data-table">
              <thead>
                <tr>
                  <th>{t('dataHolderAdmin.logs.colTime')}</th>
                  <th>{t('dataHolderAdmin.logs.colType')}</th>
                  <th>{t('dataHolderAdmin.logs.colQuery')}</th>
                  <th>{t('dataHolderAdmin.logs.colUser')}</th>
                  <th>{t('dataHolderAdmin.logs.colLevel')}</th>
                  <th>{t('dataHolderAdmin.logs.colResult')}</th>
                </tr>
              </thead>
              <tbody>
                {auditLogs.map(log => (
                  <tr key={log.id}>
                    <td>{new Date(log.requestTimestamp).toLocaleString()}</td>
                    <td>{log.queryType}</td>
                    <td>{log.queryValue}</td>
                    <td>{log.requestorUsername || t('dataHolderAdmin.logs.na')}</td>
                    <td>{log.accessLevelGranted}</td>
                    <td>
                      <span className={`result-badge ${log.result}`}>
                        {log.result}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      
      {/* Review Modal */}
      {selectedRequest && (
        <div className="modal-overlay" onClick={() => setSelectedRequest(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h2>{t('dataHolderAdmin.review.title')}</h2>
            <div className="modal-content">
              <p><strong>{t('dataHolderAdmin.review.type')}</strong> {selectedRequest.queryType}</p>
              <p><strong>{t('dataHolderAdmin.review.value')}</strong> {selectedRequest.queryValue}</p>
              <p><strong>{t('dataHolderAdmin.review.requestedLevel')}</strong> {selectedRequest.requestedAccessLevel}</p>
              <p><strong>{t('dataHolderAdmin.review.user')}</strong> {selectedRequest.requestorUsername}</p>

              <div className="form-group">
                <label>{t('dataHolderAdmin.review.adminNotes')}</label>
                <textarea
                  value={reviewNotes}
                  onChange={e => setReviewNotes(e.target.value)}
                  placeholder={t('dataHolderAdmin.review.notesPlaceholder')}
                />
              </div>
            </div>
            <div className="modal-actions">
              <button
                className="btn btn-deny"
                onClick={() => handleReview(selectedRequest.requestId, 'deny')}
              >
                {t('dataHolderAdmin.review.deny')}
              </button>
              <button
                className="btn btn-approve"
                onClick={() => handleReview(selectedRequest.requestId, 'approve')}
              >
                {t('dataHolderAdmin.review.approve')}
              </button>
            </div>
          </div>
        </div>
      )}
      
      <style jsx>{`
        .admin-container {
          max-width: 1400px;
          margin: 0 auto;
          padding: 2rem;
        }
        
        .admin-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 2rem;
        }
        
        .header-actions {
          display: flex;
          gap: 1rem;
        }
        
        .btn {
          padding: 0.5rem 1rem;
          border-radius: 6px;
          cursor: pointer;
          font-size: 0.9rem;
          border: none;
        }
        
        .btn-primary { background: #2196f3; color: white; }
        .btn-secondary { background: #f5f5f5; color: #333; }
        .btn-outline { background: transparent; border: 1px solid #ddd; }
        .btn-approve { background: #4caf50; color: white; }
        .btn-deny { background: #f44336; color: white; }
        
        .stats-bar {
          display: flex;
          gap: 2rem;
          background: white;
          padding: 1.5rem;
          border-radius: 12px;
          margin-bottom: 2rem;
          box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        
        .stat {
          display: flex;
          flex-direction: column;
          align-items: center;
        }
        
        .stat-value {
          font-size: 2rem;
          font-weight: 700;
        }
        
        .stat-value.pending { color: #ff9800; }
        .stat-value.approved { color: #4caf50; }
        .stat-value.denied { color: #f44336; }
        .stat-value.total { color: #2196f3; }
        
        .stat-label {
          color: #666;
          font-size: 0.9rem;
        }
        
        .tabs {
          display: flex;
          gap: 0.5rem;
          margin-bottom: 1rem;
        }
        
        .tab {
          padding: 0.75rem 1.5rem;
          background: #f5f5f5;
          border: none;
          border-radius: 8px 8px 0 0;
          cursor: pointer;
          font-size: 1rem;
          position: relative;
        }
        
        .tab.active {
          background: white;
          font-weight: 600;
        }
        
        .badge {
          background: #f44336;
          color: white;
          font-size: 0.75rem;
          padding: 0.15rem 0.5rem;
          border-radius: 10px;
          margin-left: 0.5rem;
        }
        
        .tab-content {
          background: white;
          padding: 2rem;
          border-radius: 0 12px 12px 12px;
          box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        
        .alert-error {
          background: #f8d7da;
          color: #721c24;
          padding: 1rem;
          border-radius: 8px;
          margin-bottom: 1rem;
        }
        
        .empty-state {
          text-align: center;
          color: #666;
          padding: 3rem;
        }
        
        .requests-list {
          display: grid;
          gap: 1rem;
        }
        
        .request-card {
          border: 1px solid #e0e0e0;
          border-radius: 8px;
          padding: 1rem;
        }
        
        .request-header {
          display: flex;
          gap: 1rem;
          align-items: center;
          margin-bottom: 0.5rem;
        }
        
        .query-type {
          background: #e3f2fd;
          color: #1565c0;
          padding: 0.25rem 0.5rem;
          border-radius: 4px;
          font-weight: 600;
        }
        
        .query-value {
          font-family: monospace;
          font-size: 1.1rem;
        }
        
        .access-level {
          margin-left: auto;
          background: #fff3e0;
          color: #ef6c00;
          padding: 0.25rem 0.5rem;
          border-radius: 4px;
        }
        
        .request-details {
          font-size: 0.9rem;
          color: #666;
        }
        
        .request-details p {
          margin: 0.25rem 0;
        }
        
        .request-actions {
          margin-top: 1rem;
          display: flex;
          justify-content: flex-end;
        }
        
        .data-table {
          width: 100%;
          border-collapse: collapse;
        }
        
        .data-table th, .data-table td {
          padding: 0.75rem;
          text-align: left;
          border-bottom: 1px solid #e0e0e0;
        }
        
        .data-table th {
          background: #f5f5f5;
          font-weight: 600;
        }
        
        .level-badge {
          padding: 0.25rem 0.5rem;
          border-radius: 4px;
          font-size: 0.85rem;
        }
        
        .level-badge.level-0 { background: #ffebee; color: #c62828; }
        .level-badge.level-1 { background: #fff3e0; color: #ef6c00; }
        .level-badge.level-2 { background: #e8f5e9; color: #2e7d32; }
        .level-badge.level-3 { background: #e3f2fd; color: #1565c0; }
        
        .status-badge {
          padding: 0.25rem 0.5rem;
          border-radius: 4px;
          font-size: 0.85rem;
        }
        
        .status-badge.active { background: #e8f5e9; color: #2e7d32; }
        .status-badge.inactive { background: #f5f5f5; color: #666; }
        
        .result-badge {
          padding: 0.25rem 0.5rem;
          border-radius: 4px;
          font-size: 0.85rem;
        }
        
        .result-badge.success { background: #e8f5e9; color: #2e7d32; }
        .result-badge.denied { background: #ffebee; color: #c62828; }
        .result-badge.pending { background: #fff3e0; color: #ef6c00; }
        .result-badge.not_found { background: #f5f5f5; color: #666; }
        
        .policy-form .form-group {
          margin-bottom: 1.5rem;
        }
        
        .policy-form label {
          display: block;
          font-weight: 600;
          margin-bottom: 0.5rem;
        }
        
        .policy-form select {
          padding: 0.5rem;
          border: 1px solid #ddd;
          border-radius: 6px;
          font-size: 1rem;
        }
        
        .checkbox-label {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          cursor: pointer;
        }
        
        .help-text {
          font-size: 0.85rem;
          color: #666;
          margin-top: 0.5rem;
        }
        
        .modal-overlay {
          position: fixed;
          top: 0;
          left: 0;
          right: 0;
          bottom: 0;
          background: rgba(0,0,0,0.5);
          display: flex;
          align-items: center;
          justify-content: center;
          z-index: 1000;
        }
        
        .modal {
          background: white;
          padding: 2rem;
          border-radius: 12px;
          max-width: 500px;
          width: 90%;
        }
        
        .modal h2 {
          margin-top: 0;
        }
        
        .modal-content {
          margin: 1.5rem 0;
        }
        
        .modal-content p {
          margin: 0.5rem 0;
        }
        
        .modal-content textarea {
          width: 100%;
          padding: 0.75rem;
          border: 1px solid #ddd;
          border-radius: 6px;
          min-height: 100px;
          resize: vertical;
        }
        
        .modal-actions {
          display: flex;
          gap: 1rem;
          justify-content: flex-end;
        }
      `}</style>
    </div>
  );
};

export default DataHolderAdmin;
