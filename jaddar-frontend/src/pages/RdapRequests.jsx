/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import api from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import Header from '../components/Header';
import Footer from '../components/Footer';
import RdapDataModal from '../components/RdapDataModal';
import RdapExportButton from '../components/RdapExportButton';
import RdapCompareModal from '../components/RdapCompareModal';
import Pagination from '../components/Pagination';
import { useT } from '../i18n';

const RdapRequests = () => {
  const { user } = useAuth();
  const { t } = useT();
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [stats, setStats] = useState({ total: 0, pending: 0, approved: 0, denied: 0, error: 0, cancelled: 0 });
  const [statusFilter, setStatusFilter] = useState('');

  // Server-side pagination (1-based page)
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);

  // Selection state
  const [selectedIds, setSelectedIds] = useState([]);
  
  // Settings
  const [settings, setSettings] = useState({
    default_poll_interval_ms: 30000,
    auto_poll_enabled: true
  });
  const [settingsLoading, setSettingsLoading] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  
  // Modal for viewing RDAP data
  const [selectedRequest, setSelectedRequest] = useState(null);
  const [showDataModal, setShowDataModal] = useState(false);

  // Compare modal
  const [showCompareModal, setShowCompareModal] = useState(false);

  // Denial details modal
  const [showDenialModal, setShowDenialModal] = useState(false);

  // Cancel confirmation modal
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelLoading, setCancelLoading] = useState(false);
  
  // Polling
  const pollIntervalRef = useRef(null);
  const [isPolling, setIsPolling] = useState(false);

  // Load requests
  const loadRequests = useCallback(async (sync = true) => {
    try {
      const params = { sync, limit: pageSize, offset: (page - 1) * pageSize };
      if (statusFilter) params.status = statusFilter;

      const response = await api.get('/api/rdap/requests', { params });
      
      setRequests(response.data.requests || []);
      setStats({
        total: response.data.total || 0,
        pending: response.data.pending_count || 0,
        approved: response.data.approved_count || 0,
        denied: response.data.denied_count || 0,
        error: response.data.error_count || 0,
        cancelled: response.data.cancelled_count || 0
      });
      setError(null);
    } catch (err) {
      console.error('Failed to load requests:', err);
      setError(t('rdapRequests.errors.loadRequests'));
    } finally {
      setLoading(false);
    }
  }, [statusFilter, page, pageSize]);

  // Load settings
  const loadSettings = useCallback(async () => {
    try {
      const response = await api.get('/api/rdap/settings');
      setSettings(response.data);
    } catch (err) {
      console.error('Failed to load settings:', err);
    }
  }, []);

  // Save settings
  const saveSettings = async (newSettings) => {
    setSettingsLoading(true);
    try {
      const response = await api.put('/api/rdap/settings', newSettings);
      setSettings(response.data);
      setError(null);
    } catch (err) {
      console.error('Failed to save settings:', err);
      setError(t('rdapRequests.errors.saveSettings'));
    } finally {
      setSettingsLoading(false);
    }
  };

  // Check status of a specific request
  const checkRequestStatus = async (requestId) => {
    try {
      const response = await api.get(`/api/rdap/requests/${requestId}`, {
        params: { check_status: true }
      });
      await loadRequests(false);
      return response.data;
    } catch (err) {
      console.error('Failed to check request status:', err);
      return null;
    }
  };

  // Delete request from history
  const deleteRequest = async (requestId) => {
    if (!window.confirm(t('rdapRequests.confirm.removeFromHistory'))) return;
    try {
      await api.delete(`/api/rdap/requests/${requestId}`);
      setSelectedIds((prev) => prev.filter((id) => id !== requestId));
      await loadRequests(false);
    } catch (err) {
      console.error('Failed to delete request:', err);
      setError(t('rdapRequests.errors.removeRequest'));
    }
  };

  // Cancel a pending request
  const cancelRequest = async () => {
    if (!selectedRequest) return;
    setCancelLoading(true);
    try {
      await api.post(`/api/rdap/requests/${selectedRequest.request_id}/cancel`,
        { reason: cancelReason || undefined }
      );
      setShowCancelModal(false);
      setCancelReason('');
      setSelectedRequest(null);
      await loadRequests(false);
    } catch (err) {
      console.error('Failed to cancel request:', err);
      setError(err.response?.data?.detail || t('rdapRequests.errors.cancelRequest'));
    } finally {
      setCancelLoading(false);
    }
  };

  // Open cancel confirmation modal
  const openCancelModal = (req) => {
    setSelectedRequest(req);
    setCancelReason('');
    setShowCancelModal(true);
  };

  // Selection handlers
  const toggleSelectAll = () => {
    if (selectedIds.length === requests.length) {
      setSelectedIds([]);
    } else {
      setSelectedIds(requests.map((r) => r.request_id));
    }
  };

  const toggleSelectOne = (requestId) => {
    setSelectedIds((prev) =>
      prev.includes(requestId)
        ? prev.filter((id) => id !== requestId)
        : [...prev, requestId]
    );
  };

  // Clear selection and reset to first page when filter changes
  useEffect(() => {
    setSelectedIds([]);
    setPage(1);
  }, [statusFilter]);

  // Setup polling
  useEffect(() => {
    if (settings.auto_poll_enabled && settings.default_poll_interval_ms > 0 && stats.pending > 0) {
      setIsPolling(true);
      pollIntervalRef.current = setInterval(() => {
        loadRequests(true);
      }, settings.default_poll_interval_ms);
    } else {
      setIsPolling(false);
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current);
      }
    }

    return () => {
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current);
      }
    };
  }, [settings.auto_poll_enabled, settings.default_poll_interval_ms, stats.pending, loadRequests]);

  // Initial load
  useEffect(() => {
    loadRequests();
    loadSettings();
  }, [loadRequests, loadSettings]);

  // Reload when filter changes
  useEffect(() => {
    loadRequests(false);
  }, [statusFilter, loadRequests]);

  // Format date
  const formatDate = (dateString) => {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString();
  };

  // Get status badge
  const getStatusBadge = (status) => {
    const badges = {
      pending: 'bg-warning text-dark',
      approved: 'bg-success',
      denied: 'bg-danger',
      cancelled: 'bg-secondary',
      error: 'bg-secondary'
    };
    return badges[status?.toLowerCase()] || 'bg-secondary';
  };

  // Format poll interval for display
  const formatPollInterval = (ms) => {
    if (ms === 0) return t('rdapRequests.settings.disabled');
    if (ms < 60000) return t('rdapRequests.settings.seconds', { n: ms / 1000 });
    return t('rdapRequests.settings.minutes', { n: ms / 60000 });
  };

  const allSelected = requests.length > 0 && selectedIds.length === requests.length;
  const someSelected = selectedIds.length > 0 && selectedIds.length < requests.length;

  // Compare: count how many selected items actually have RDAP data
  const comparableRequests = requests.filter(
    (r) => selectedIds.includes(r.request_id) && r.status === 'approved' && r.rdap_data
  );
  const canCompare = selectedIds.length >= 2 && selectedIds.length <= 4 && comparableRequests.length >= 2;

  // Total matching the current filter drives server-side pagination.
  const totalItems = statusFilter ? (stats[statusFilter] || 0) : stats.total;

  return (
    <div className="min-vh-100 bg-light d-flex flex-column">
      <Header />
      
      <div className="container-fluid px-4 pb-5 flex-grow-1">
        {/* Page Header */}
        <div className="d-flex justify-content-between align-items-center mb-4">
          <div>
            <h2 className="mb-1">
              <i className="bi bi-clock-history me-2 text-primary"></i>
              {t('rdapRequests.header.title')}
            </h2>
            <p className="text-muted mb-0">
              {t('rdapRequests.header.subtitle')}
            </p>
          </div>
          <div className="d-flex gap-2">
            {selectedIds.length >= 2 && selectedIds.length <= 4 && (
              <button
                className="btn btn-outline-info"
                onClick={() => setShowCompareModal(true)}
                disabled={!canCompare}
                title={
                  canCompare
                    ? t('rdapRequests.compare.titleReady', { n: comparableRequests.length })
                    : t('rdapRequests.compare.titleHint')
                }
              >
                <i className="bi bi-layout-three-columns me-1"></i>
                {t('rdapRequests.compare.button', { n: comparableRequests.length })}
              </button>
            )}
            <RdapExportButton
              selectedIds={selectedIds.length > 0 ? selectedIds : null}
              statusFilter={statusFilter}
              page={page}
              pageSize={pageSize}
              totalItems={totalItems}
              variant="outline-primary"
              size="md"
              label={selectedIds.length > 0 ? t('rdapRequests.actions.exportCount', { length: selectedIds.length }) : t('rdapRequests.actions.export')}
            />
            <button 
              className="btn btn-outline-secondary"
              onClick={() => setShowSettings(!showSettings)}
            >
              <i className="bi bi-gear me-1"></i>
              {t('rdapRequests.actions.settings')}
            </button>
            <button
              className="btn btn-primary"
              onClick={() => loadRequests(true)}
              disabled={loading}
            >
              <i className={`bi bi-arrow-clockwise me-1 ${loading ? 'spin' : ''}`}></i>
              {t('rdapRequests.actions.refresh')}
            </button>
          </div>
        </div>

        {/* Error Alert */}
        {error && (
          <div className="alert alert-danger alert-dismissible fade show" role="alert">
            <i className="bi bi-exclamation-triangle-fill me-2"></i>
            {error}
            <button type="button" className="btn-close" onClick={() => setError(null)}></button>
          </div>
        )}

        {/* Settings Panel */}
        {showSettings && (
          <div className="card shadow-sm mb-4">
            <div className="card-header bg-white">
              <h5 className="mb-0">
                <i className="bi bi-gear me-2"></i>
                {t('rdapRequests.settings.title')}
              </h5>
            </div>
            <div className="card-body">
              <div className="row g-4">
                <div className="col-md-6">
                  <label className="form-label fw-semibold">
                    {t('rdapRequests.settings.autoPollInterval')} <strong>{formatPollInterval(settings.default_poll_interval_ms)}</strong>
                  </label>
                  <input
                    type="range"
                    className="form-range"
                    min="0"
                    max="3600000"
                    step="5000"
                    value={settings.default_poll_interval_ms}
                    onChange={(e) => setSettings({
                      ...settings,
                      default_poll_interval_ms: parseInt(e.target.value)
                    })}
                  />
                  <div className="d-flex justify-content-between small text-muted">
                    <span>{t('rdapRequests.settings.off')}</span>
                    <span>30s</span>
                    <span>5min</span>
                    <span>30min</span>
                    <span>1hr</span>
                  </div>
                  <div className="d-flex gap-2 mt-2 flex-wrap">
                    {[0, 10000, 30000, 60000, 300000, 600000, 1800000, 3600000].map((ms) => (
                      <button
                        key={ms}
                        className={`btn btn-sm ${settings.default_poll_interval_ms === ms ? 'btn-primary' : 'btn-outline-secondary'}`}
                        onClick={() => setSettings({ ...settings, default_poll_interval_ms: ms })}
                      >
                        {ms === 0 ? t('rdapRequests.settings.off') : formatPollInterval(ms)}
                      </button>
                    ))}
                  </div>
                </div>
                <div className="col-md-6">
                  <div className="form-check form-switch mb-3">
                    <input
                      className="form-check-input"
                      type="checkbox"
                      id="autoPollEnabled"
                      checked={settings.auto_poll_enabled}
                      onChange={(e) => setSettings({ ...settings, auto_poll_enabled: e.target.checked })}
                    />
                    <label className="form-check-label" htmlFor="autoPollEnabled">
                      {t('rdapRequests.settings.enableAutoPoll')}
                    </label>
                  </div>
                  <div className="form-check form-switch mb-3">
                    <input
                      className="form-check-input"
                      type="checkbox"
                      id="notifyApproval"
                      checked={settings.notify_on_approval}
                      onChange={(e) => setSettings({ ...settings, notify_on_approval: e.target.checked })}
                    />
                    <label className="form-check-label" htmlFor="notifyApproval">
                      {t('rdapRequests.settings.notifyApproval')}
                    </label>
                  </div>
                  <div className="form-check form-switch">
                    <input
                      className="form-check-input"
                      type="checkbox"
                      id="notifyDenial"
                      checked={settings.notify_on_denial}
                      onChange={(e) => setSettings({ ...settings, notify_on_denial: e.target.checked })}
                    />
                    <label className="form-check-label" htmlFor="notifyDenial">
                      {t('rdapRequests.settings.notifyDenial')}
                    </label>
                  </div>
                </div>
              </div>
              <div className="mt-3">
                <button
                  className="btn btn-primary"
                  onClick={() => saveSettings(settings)}
                  disabled={settingsLoading}
                >
                  {settingsLoading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2"></span>
                      {t('rdapRequests.settings.saving')}
                    </>
                  ) : (
                    <>
                      <i className="bi bi-check-lg me-1"></i>
                      {t('rdapRequests.settings.saveSettings')}
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Stats Cards */}
        <div className="row g-3 mb-4">
          <div className="col col-6">
            <div className="card shadow-sm h-100">
              <div className="card-body text-center">
                <div className="display-6 fw-bold text-primary">{stats.total}</div>
                <div className="text-muted small">{t('rdapRequests.stats.total')}</div>
              </div>
            </div>
          </div>
          <div className="col col-6">
            <div className="card shadow-sm h-100 border-warning">
              <div className="card-body text-center">
                <div className="display-6 fw-bold text-warning">{stats.pending}</div>
                <div className="text-muted small">
                  {t('rdapRequests.stats.pending')}
                  {isPolling && stats.pending > 0 && (
                    <span className="badge bg-info ms-2">
                      <i className="bi bi-broadcast me-1"></i>
                      {t('rdapRequests.stats.polling')}
                    </span>
                  )}
                </div>
              </div>
            </div>
          </div>
          <div className="col col-6">
            <div className="card shadow-sm h-100 border-success">
              <div className="card-body text-center">
                <div className="display-6 fw-bold text-success">{stats.approved}</div>
                <div className="text-muted small">{t('rdapRequests.stats.approved')}</div>
              </div>
            </div>
          </div>
          <div className="col col-6">
            <div className="card shadow-sm h-100 border-danger">
              <div className="card-body text-center">
                <div className="display-6 fw-bold text-danger">{stats.denied}</div>
                <div className="text-muted small">{t('rdapRequests.stats.denied')}</div>
              </div>
            </div>
          </div>
          <div className="col col-6">
            <div className="card shadow-sm h-100 border-secondary">
              <div className="card-body text-center">
                <div className="display-6 fw-bold text-secondary">{stats.cancelled}</div>
                <div className="text-muted small">{t('rdapRequests.stats.cancelled')}</div>
              </div>
            </div>
          </div>
        </div>

        {/* Requests Table */}
        <div className="card shadow-sm">
          <div className="card-header bg-white d-flex justify-content-between align-items-center">
            <h5 className="mb-0">
              <i className="bi bi-list-ul me-2"></i>
              {t('rdapRequests.table.historyTitle')}
              {selectedIds.length > 0 && (
                <span className="badge bg-primary ms-2">{t('rdapRequests.table.selectedCount', { n: selectedIds.length })}</span>
              )}
            </h5>
            <div className="d-flex gap-2 align-items-center">
              {selectedIds.length > 0 && (
                <button
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => setSelectedIds([])}
                >
                  <i className="bi bi-x-lg me-1"></i>{t('rdapRequests.table.clear')}
                </button>
              )}
              <label className="me-2 small text-muted">{t('rdapRequests.table.filter')}</label>
              <select
                className="form-select form-select-sm"
                style={{ width: 'auto' }}
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
              >
                <option value="">{t('rdapRequests.filter.allStatus')}</option>
                <option value="pending">{t('rdapRequests.filter.pending')}</option>
                <option value="approved">{t('rdapRequests.filter.approved')}</option>
                <option value="denied">{t('rdapRequests.filter.denied')}</option>
                <option value="cancelled">{t('rdapRequests.filter.cancelled')}</option>
                <option value="error">{t('rdapRequests.filter.error')}</option>
              </select>
            </div>
          </div>
          <div className="card-body p-0">
            {loading ? (
              <div className="text-center p-5">
                <div className="spinner-border text-primary" role="status">
                  <span className="visually-hidden">{t('rdapRequests.table.loading')}</span>
                </div>
                <p className="mt-3 text-muted">{t('rdapRequests.table.loadingRequests')}</p>
              </div>
            ) : requests.length === 0 ? (
              <div className="text-center p-5">
                <i className="bi bi-inbox display-1 text-muted"></i>
                <p className="mt-3 text-muted">{t('rdapRequests.table.noRequests')}</p>
                <p className="small text-muted">
                  {t('rdapRequests.table.noRequestsHint')}
                </p>
              </div>
            ) : (
              <div className="table-responsive">
                <table className="table table-hover mb-0">
                  <thead className="table-light">
                    <tr>
                      <th style={{ width: '40px' }}>
                        <input
                          type="checkbox"
                          className="form-check-input"
                          checked={allSelected}
                          ref={(el) => { if (el) el.indeterminate = someSelected; }}
                          onChange={toggleSelectAll}
                          title={t('rdapRequests.table.selectAll')}
                        />
                      </th>
                      <th>{t('rdapRequests.table.colStatus')}</th>
                      <th>{t('rdapRequests.table.colType')}</th>
                      <th>{t('rdapRequests.table.colQuery')}</th>
                      <th>{t('rdapRequests.table.colAgreements')}</th>
                      <th>{t('rdapRequests.table.colAccessLevel')}</th>
                      <th>{t('rdapRequests.table.colCreated')}</th>
                      <th>{t('rdapRequests.table.colResolved')}</th>
                      <th>{t('rdapRequests.table.colData')}</th>
                      <th>{t('rdapRequests.table.colActions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {requests.map((req) => (
                      <tr
                        key={req.request_id}
                        className={selectedIds.includes(req.request_id) ? 'table-active' : ''}
                      >
                        <td>
                          <input
                            type="checkbox"
                            className="form-check-input"
                            checked={selectedIds.includes(req.request_id)}
                            onChange={() => toggleSelectOne(req.request_id)}
                          />
                        </td>
                        <td>
                          <span className={`badge ${getStatusBadge(req.status)}`}>
                            {req.status?.toUpperCase()}
                          </span>
                        </td>
                        <td>
                          <span className="badge bg-secondary">
                            {req.query_type?.toUpperCase()}
                          </span>
                        </td>
                        <td>
                          <code className="small">{req.query_value}</code>
                        </td>
                        <td>
                          {req.agreements_used?.length > 0 ? (
                            <span className="badge bg-info" title={req.agreements_used.join(', ')}>
                              {t('rdapRequests.table.agreementsCount', { n: req.agreements_used.length })}
                            </span>
                          ) : (
                            <span className="text-muted small">{t('rdapRequests.table.none')}</span>
                          )}
                        </td>
                        <td>
                          {req.access_level_granted !== null ? (
                            <span className={`badge ${
                              req.access_level_granted === 3 ? 'bg-success' :
                              req.access_level_granted === 2 ? 'bg-info' :
                              req.access_level_granted === 1 ? 'bg-warning text-dark' :
                              'bg-secondary'
                            }`}>
                              {t('rdapRequests.table.level', { n: req.access_level_granted })}
                            </span>
                          ) : req.access_level_requested !== null ? (
                            <span className="text-muted small">
                              {t('rdapRequests.table.requestedLevel', { n: req.access_level_requested })}
                            </span>
                          ) : (
                            <span className="text-muted small">-</span>
                          )}
                        </td>
                        <td className="small">{formatDate(req.created_at)}</td>
                        <td className="small">{formatDate(req.resolved_at)}</td>
                        <td>
                          {req.status === 'approved' && req.rdap_data ? (
                            <button
                              className="btn btn-sm btn-outline-primary"
                              onClick={() => {
                                setSelectedRequest(req);
                                setShowDataModal(true);
                              }}
                            >
                              <i className="bi bi-eye me-1"></i>
                              {t('rdapRequests.table.view')}
                            </button>
                          ) : req.status === 'pending' ? (
                            <span className="text-muted small">
                              <i className="bi bi-hourglass-split me-1"></i>
                              {t('rdapRequests.table.waiting')}
                            </span>
                          ) : req.status === 'denied' ? (
                            <button
                              className="btn btn-sm btn-outline-danger"
                              onClick={() => {
                                setSelectedRequest(req);
                                setShowDenialModal(true);
                              }}
                            >
                              <i className="bi bi-eye me-1"></i>
                              {t('rdapRequests.table.details')}
                            </button>
                          ) : req.status === 'cancelled' ? (
                            <span className="text-muted small">
                              <i className="bi bi-x-circle me-1"></i>
                              {t('rdapRequests.table.cancelled')}
                            </span>
                          ) : (
                            <span className="text-muted small">-</span>
                          )}
                        </td>
                        <td>
                          <div className="btn-group btn-group-sm">
                            {req.status === 'pending' && (
                              <>
                                <button
                                  className="btn btn-outline-primary"
                                  onClick={() => checkRequestStatus(req.request_id)}
                                  title={t('rdapRequests.table.checkStatus')}
                                >
                                  <i className="bi bi-arrow-clockwise"></i>
                                </button>
                                <button
                                  className="btn btn-outline-warning"
                                  onClick={() => openCancelModal(req)}
                                  title={t('rdapRequests.table.cancelRequest')}
                                >
                                  <i className="bi bi-x-lg"></i>
                                </button>
                              </>
                            )}
                            <button
                              className="btn btn-outline-danger"
                              onClick={() => deleteRequest(req.request_id)}
                              title={t('rdapRequests.table.removeFromHistory')}
                            >
                              <i className="bi bi-trash"></i>
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
          {!loading && requests.length > 0 && (
            <div className="card-footer bg-white">
              <Pagination
                page={page}
                pageSize={pageSize}
                totalItems={totalItems}
                onPageChange={setPage}
                onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
                itemLabel={t('rdapRequests.table.itemLabel')}
              />
            </div>
          )}
        </div>
      </div>

      {/* RDAP Data Modal */}
      <RdapDataModal
        show={showDataModal}
        onHide={() => setShowDataModal(false)}
        request={selectedRequest}
      />

      {/* RDAP Compare Modal */}
      <RdapCompareModal
        show={showCompareModal}
        onHide={() => setShowCompareModal(false)}
        requests={comparableRequests}
      />

      {/* Denial Details Modal */}
      {showDenialModal && selectedRequest && (
        <div className="modal fade show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }} tabIndex="-1">
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header bg-danger bg-opacity-10">
                <h5 className="modal-title">
                  <i className="bi bi-x-circle me-2 text-danger"></i>
                  {t('rdapRequests.denialModal.title')}
                </h5>
                <button type="button" className="btn-close" onClick={() => setShowDenialModal(false)}></button>
              </div>
              <div className="modal-body">
                <div className="mb-3">
                  <label className="form-label text-muted small fw-semibold">{t('rdapRequests.denialModal.query')}</label>
                  <div>
                    <span className={`badge me-2 ${
                      selectedRequest.query_type === 'domain' ? 'bg-primary' :
                      selectedRequest.query_type === 'ip' ? 'bg-info' :
                      'bg-secondary'
                    }`}>
                      {selectedRequest.query_type?.toUpperCase()}
                    </span>
                    <code>{selectedRequest.query_value}</code>
                  </div>
                </div>

                {selectedRequest.agreements_used?.length > 0 && (
                  <div className="mb-3">
                    <label className="form-label text-muted small fw-semibold">{t('rdapRequests.denialModal.agreements')}</label>
                    <div>
                      {selectedRequest.agreements_used.map((name) => (
                        <span key={name} className="badge bg-info me-1">{name}</span>
                      ))}
                    </div>
                  </div>
                )}

                {selectedRequest.access_level_requested !== null && selectedRequest.access_level_requested !== undefined && (
                  <div className="mb-3">
                    <label className="form-label text-muted small fw-semibold">{t('rdapRequests.denialModal.accessLevelRequested')}</label>
                    <div><span className="badge bg-secondary">{t('rdapRequests.table.level', { n: selectedRequest.access_level_requested })}</span></div>
                  </div>
                )}

                <div className="mb-3">
                  <label className="form-label text-muted small fw-semibold">{t('rdapRequests.denialModal.denialReason')}</label>
                  <div className="p-3 rounded" style={{ backgroundColor: '#fef2f2', border: '1px solid #fecaca' }}>
                    {selectedRequest.error_message ? (
                      <span className="text-danger">{selectedRequest.error_message}</span>
                    ) : (
                      <span className="text-muted fst-italic">{t('rdapRequests.denialModal.noReason')}</span>
                    )}
                  </div>
                </div>

                <div className="row">
                  <div className="col-6">
                    <label className="form-label text-muted small fw-semibold">{t('rdapRequests.denialModal.requested')}</label>
                    <div className="small">{formatDate(selectedRequest.created_at)}</div>
                  </div>
                  <div className="col-6">
                    <label className="form-label text-muted small fw-semibold">{t('rdapRequests.denialModal.denied')}</label>
                    <div className="small">{formatDate(selectedRequest.resolved_at)}</div>
                  </div>
                </div>

                {selectedRequest.data_holder_name && (
                  <div className="mt-3">
                    <label className="form-label text-muted small fw-semibold">{t('rdapRequests.dataHolder')}</label>
                    <div className="small">{selectedRequest.data_holder_name}</div>
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowDenialModal(false)}>{t('rdapRequests.denialModal.close')}</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Cancel Confirmation Modal */}
      {showCancelModal && selectedRequest && (
        <div className="modal fade show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }} tabIndex="-1">
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header bg-warning bg-opacity-10">
                <h5 className="modal-title">
                  <i className="bi bi-exclamation-triangle me-2 text-warning"></i>
                  {t('rdapRequests.cancelModal.title')}
                </h5>
                <button type="button" className="btn-close" onClick={() => { setShowCancelModal(false); setCancelReason(''); }} disabled={cancelLoading}></button>
              </div>
              <div className="modal-body">
                <p>{t('rdapRequests.cancelModal.confirmText')}</p>

                <div className="mb-3">
                  <label className="form-label text-muted small fw-semibold">{t('rdapRequests.denialModal.query')}</label>
                  <div>
                    <span className={`badge me-2 ${
                      selectedRequest.query_type === 'domain' ? 'bg-primary' :
                      selectedRequest.query_type === 'ip' ? 'bg-info' :
                      'bg-secondary'
                    }`}>
                      {selectedRequest.query_type?.toUpperCase()}
                    </span>
                    <code>{selectedRequest.query_value}</code>
                  </div>
                </div>

                {selectedRequest.data_holder_name && (
                  <div className="mb-3">
                    <label className="form-label text-muted small fw-semibold">{t('rdapRequests.dataHolder')}</label>
                    <div className="small">{selectedRequest.data_holder_name}</div>
                  </div>
                )}

                <div className="mb-3">
                  <label className="form-label text-muted small fw-semibold" htmlFor="cancelReason">
                    {t('rdapRequests.cancelModal.reasonLabel')} <span className="text-muted fw-normal">{t('rdapRequests.cancelModal.optional')}</span>
                  </label>
                  <textarea
                    id="cancelReason"
                    className="form-control"
                    rows="3"
                    placeholder={t('rdapRequests.cancelModal.reasonPlaceholder')}
                    value={cancelReason}
                    onChange={(e) => setCancelReason(e.target.value)}
                    disabled={cancelLoading}
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => { setShowCancelModal(false); setCancelReason(''); }}
                  disabled={cancelLoading}
                >
                  {t('rdapRequests.cancelModal.keep')}
                </button>
                <button
                  type="button"
                  className="btn btn-warning"
                  onClick={cancelRequest}
                  disabled={cancelLoading}
                >
                  {cancelLoading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2"></span>
                      {t('rdapRequests.cancelModal.cancelling')}
                    </>
                  ) : (
                    <>
                      <i className="bi bi-x-lg me-1"></i>
                      {t('rdapRequests.cancelModal.confirmButton')}
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default RdapRequests;