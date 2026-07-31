/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { useAuth } from '../contexts/AuthContext';
import Header from '../components/Header';
import api from '../services/api';
import { useT } from '../i18n';

// Log level colors and icons
const levelConfig = {
  debug: { color: 'secondary', icon: 'bi-bug' },
  info: { color: 'info', icon: 'bi-info-circle' },
  warning: { color: 'warning', icon: 'bi-exclamation-triangle' },
  error: { color: 'danger', icon: 'bi-x-circle' },
  critical: { color: 'danger', icon: 'bi-exclamation-octagon' }
};

// Category icons
const categoryIcons = {
  auth: 'bi-shield-lock',
  introspection: 'bi-key',
  rdap: 'bi-globe',
  system: 'bi-gear',
  admin: 'bi-person-badge',
  api: 'bi-cloud'
};

// Keycloak event type colors
const keycloakEventConfig = {
  LOGIN: { color: 'success', icon: 'bi-box-arrow-in-right' },
  LOGIN_ERROR: { color: 'danger', icon: 'bi-x-circle' },
  LOGOUT: { color: 'secondary', icon: 'bi-box-arrow-right' },
  INTROSPECT_TOKEN: { color: 'info', icon: 'bi-key' },
  INTROSPECT_TOKEN_ERROR: { color: 'danger', icon: 'bi-key-fill' },
  CODE_TO_TOKEN: { color: 'primary', icon: 'bi-arrow-left-right' },
  CODE_TO_TOKEN_ERROR: { color: 'danger', icon: 'bi-x-octagon' },
  REFRESH_TOKEN: { color: 'info', icon: 'bi-arrow-repeat' },
  REFRESH_TOKEN_ERROR: { color: 'danger', icon: 'bi-arrow-repeat' },
  CLIENT_LOGIN: { color: 'primary', icon: 'bi-building' },
  CLIENT_LOGIN_ERROR: { color: 'danger', icon: 'bi-building' },
  REGISTER: { color: 'success', icon: 'bi-person-plus' },
  TOKEN_EXCHANGE: { color: 'info', icon: 'bi-arrow-left-right' },
  default: { color: 'secondary', icon: 'bi-circle' }
};

const Logs = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { t } = useT();
  
  // Active tab
  const [activeTab, setActiveTab] = useState('application');
  
  // Application logs state
  const [logs, setLogs] = useState([]);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [totalLogs, setTotalLogs] = useState(0);
  
  // Keycloak events state
  const [keycloakEvents, setKeycloakEvents] = useState([]);
  const [keycloakLoading, setKeycloakLoading] = useState(false);
  const [keycloakError, setKeycloakError] = useState(null);
  const [keycloakConfig, setKeycloakConfig] = useState(null);
  
  // Application log filters
  const [filters, setFilters] = useState({
    level: '',
    category: '',
    search: '',
    limit: 50,
    offset: 0
  });
  
  // Keycloak event filters
  const [kcFilters, setKcFilters] = useState({
    type: '',
    client: '',
    user: '',
    ip_address: ''
  });

  /*
   * Keycloak events server-side pagination (1-based). Keycloak's Admin API
   * takes first/max and does not return a total count, so we page through blind.
   */
  const [kcPage, setKcPage] = useState(1);
  const [kcPageSize, setKcPageSize] = useState(50);
  
  // Auto-refresh
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [refreshInterval, setRefreshInterval] = useState(10);
  
  // Check admin access
  const isAdmin = user?.roles?.includes('admin') || user?.email === 'admin@admin.com';
  
  // Pagination calculations
  const currentPage = Math.floor(filters.offset / filters.limit) + 1;
  const totalPages = Math.ceil(totalLogs / filters.limit);
  
  useEffect(() => {
    if (!isAdmin) {
      navigate('/dashboard');
    }
  }, [isAdmin, navigate]);
  
  // Fetch application logs - using axios defaults (which has the refreshed token)
  const fetchLogs = useCallback(async () => {
    try {
      const params = new URLSearchParams();
      if (filters.level) params.append('level', filters.level);
      if (filters.category) params.append('category', filters.category);
      if (filters.search) params.append('search', filters.search);
      params.append('limit', filters.limit);
      params.append('offset', filters.offset);
      
      // Use axios without manually setting headers - it uses the default Authorization header
      const response = await api.get(`/api/admin/logs?${params.toString()}`);
      
      setLogs(response.data.logs);
      setTotalLogs(response.data.total);
      setError(null);
    } catch (err) {
      console.error('Failed to fetch logs:', err);
      if (err.response?.status === 401) {
        setError(t('logs.errors.sessionExpired'));
      } else if (err.response?.status === 403) {
        setError(t('logs.errors.accessDenied'));
      } else {
        setError(err.response?.data?.error || err.response?.data?.detail || t('logs.errors.fetchLogs'));
      }
    } finally {
      setLoading(false);
    }
  }, [filters]);
  
  // Fetch stats
  const fetchStats = useCallback(async () => {
    try {
      const response = await api.get('/api/admin/logs/stats');
      setStats(response.data);
    } catch (err) {
      console.error('Failed to fetch stats:', err);
    }
  }, []);
  
  // Fetch Keycloak events
  const fetchKeycloakEvents = useCallback(async () => {
    setKeycloakLoading(true);
    try {
      const params = new URLSearchParams();
      if (kcFilters.type) params.append('type', kcFilters.type);
      if (kcFilters.client) params.append('client', kcFilters.client);
      if (kcFilters.user) params.append('user', kcFilters.user);
      if (kcFilters.ip_address) params.append('ip_address', kcFilters.ip_address);
      params.append('first', (kcPage - 1) * kcPageSize);
      params.append('max', kcPageSize);

      const response = await api.get(`/api/admin/keycloak/events?${params.toString()}`);

      setKeycloakEvents(response.data.events || []);
      setKeycloakError(null);
    } catch (err) {
      console.error('Failed to fetch Keycloak events:', err);
      if (err.response?.status === 401) {
        setKeycloakError(t('logs.errors.sessionExpired'));
      } else if (err.response?.status === 403) {
        setKeycloakError(t('logs.errors.accessDenied'));
      } else if (err.response?.status === 503) {
        setKeycloakError(t('logs.errors.keycloakConnect'));
      } else {
        setKeycloakError(err.response?.data?.error || err.response?.data?.detail || t('logs.errors.fetchKeycloak'));
      }
    } finally {
      setKeycloakLoading(false);
    }
  }, [kcFilters, kcPage, kcPageSize]);
  
  // Fetch Keycloak events config
  const fetchKeycloakConfig = useCallback(async () => {
    try {
      const response = await api.get('/api/admin/keycloak/events/config');
      setKeycloakConfig(response.data);
    } catch (err) {
      console.error('Failed to fetch Keycloak config:', err);
    }
  }, []);
  
  // Initial fetch
  useEffect(() => {
    fetchLogs();
    fetchStats();
  }, [fetchLogs, fetchStats]);
  
  // Fetch Keycloak events when tab changes
  useEffect(() => {
    if (activeTab === 'keycloak') {
      fetchKeycloakEvents();
      fetchKeycloakConfig();
    }
  }, [activeTab, fetchKeycloakEvents, fetchKeycloakConfig]);
  
  // Auto-refresh effect
  useEffect(() => {
    if (!autoRefresh) return;
    
    const interval = setInterval(() => {
      if (activeTab === 'application') {
        fetchLogs();
        fetchStats();
      } else {
        fetchKeycloakEvents();
      }
    }, refreshInterval * 1000);
    
    return () => clearInterval(interval);
  }, [autoRefresh, refreshInterval, activeTab, fetchLogs, fetchStats, fetchKeycloakEvents]);
  
  // Handle filter changes
  const handleFilterChange = (key, value) => {
    setFilters(prev => ({
      ...prev,
      [key]: value,
      offset: 0
    }));
  };
  
  // Handle Keycloak filter changes
  const handleKcFilterChange = (key, value) => {
    setKcFilters(prev => ({
      ...prev,
      [key]: value
    }));
    setKcPage(1);
  };
  
  // Pagination handlers
  const goToPage = (page) => {
    const newOffset = (page - 1) * filters.limit;
    setFilters(prev => ({ ...prev, offset: newOffset }));
  };
  
  const goToFirstPage = () => goToPage(1);
  const goToLastPage = () => goToPage(totalPages);
  const goToPrevPage = () => goToPage(Math.max(1, currentPage - 1));
  const goToNextPage = () => goToPage(Math.min(totalPages, currentPage + 1));
  
  const getPageNumbers = () => {
    const pages = [];
    const maxVisible = 5;
    let start = Math.max(1, currentPage - Math.floor(maxVisible / 2));
    let end = Math.min(totalPages, start + maxVisible - 1);
    if (end - start + 1 < maxVisible) {
      start = Math.max(1, end - maxVisible + 1);
    }
    for (let i = start; i <= end; i++) {
      pages.push(i);
    }
    return pages;
  };
  
  // Clear logs
  const handleClearLogs = async () => {
    if (!window.confirm(t('logs.confirm.clearLogs'))) {
      return;
    }
    try {
      await api.delete('/api/admin/logs/clear');
      fetchLogs();
      fetchStats();
    } catch (err) {
      setError(t('logs.errors.clearLogs'));
    }
  };
  
  // Clear Keycloak events
  const handleClearKeycloakEvents = async () => {
    if (!window.confirm(t('logs.confirm.clearKeycloak'))) {
      return;
    }
    try {
      await api.delete('/api/admin/keycloak/events');
      fetchKeycloakEvents();
    } catch (err) {
      setKeycloakError(t('logs.errors.clearKeycloak'));
    }
  };
  
  // Format timestamp
  const formatTimestamp = (timestamp) => {
    const date = new Date(timestamp);
    return date.toLocaleString();
  };
  
  // Format Keycloak timestamp (epoch ms)
  const formatKeycloakTime = (epochMs) => {
    if (!epochMs) return t('logs.na');
    const date = new Date(epochMs);
    return date.toLocaleString();
  };
  
  // Render log details
  const renderDetails = (details) => {
    if (!details) return null;
    return (
      <pre className="mb-0 small bg-light p-2 rounded" style={{ maxWidth: '400px', overflow: 'auto' }}>
        {JSON.stringify(details, null, 2)}
      </pre>
    );
  };
  
  // Get Keycloak event config
  const getKcEventConfig = (eventType) => {
    return keycloakEventConfig[eventType] || keycloakEventConfig.default;
  };
  
  if (!isAdmin) {
    return null;
  }
  
  return (
    <div className="min-vh-100 bg-light">
      <Header />
      
      <div className="container-fluid px-4 py-3">
        {/* Page Header */}
        <div className="d-flex justify-content-between align-items-center mb-4">
          <h2 className="mb-0">
            <i className="bi bi-journal-text me-2"></i>
            {t('logs.header.title')}
          </h2>
          <div className="d-flex gap-2">
            <div className="form-check form-switch d-flex align-items-center">
              <input
                className="form-check-input me-2"
                type="checkbox"
                id="autoRefresh"
                checked={autoRefresh}
                onChange={(e) => setAutoRefresh(e.target.checked)}
              />
              <label className="form-check-label" htmlFor="autoRefresh">
                {t('logs.header.autoRefresh')}
              </label>
            </div>
            {autoRefresh && (
              <select
                className="form-select form-select-sm"
                style={{ width: '100px' }}
                value={refreshInterval}
                onChange={(e) => setRefreshInterval(Number(e.target.value))}
              >
                <option value={5}>5s</option>
                <option value={10}>10s</option>
                <option value={30}>30s</option>
                <option value={60}>60s</option>
              </select>
            )}
            <button
              className="btn btn-outline-primary btn-sm"
              onClick={() => {
                if (activeTab === 'application') {
                  fetchLogs();
                  fetchStats();
                } else {
                  fetchKeycloakEvents();
                }
              }}
            >
              <i className="bi bi-arrow-clockwise me-1"></i>
              {t('logs.header.refresh')}
            </button>
          </div>
        </div>
        
        {/* Stats Cards */}
        {stats && (
          <div className="row mb-4">
            <div className="col-md-2">
              <div className="card bg-primary text-white">
                <div className="card-body text-center">
                  <h3 className="mb-0">{stats.total_entries}</h3>
                  <small>{t('logs.stats.totalLogs')}</small>
                </div>
              </div>
            </div>
            <div className="col-md-2">
              <div className="card bg-danger text-white">
                <div className="card-body text-center">
                  <h3 className="mb-0">{stats.recent_errors}</h3>
                  <small>{t('logs.stats.errors24h')}</small>
                </div>
              </div>
            </div>
            <div className="col-md-2">
              <div className="card bg-info text-white">
                <div className="card-body text-center">
                  <h3 className="mb-0">{stats.introspection_count}</h3>
                  <small>{t('logs.stats.introspections')}</small>
                </div>
              </div>
            </div>
            <div className="col-md-2">
              <div className="card bg-success text-white">
                <div className="card-body text-center">
                  <h3 className="mb-0">{stats.unique_users}</h3>
                  <small>{t('logs.stats.uniqueUsers')}</small>
                </div>
              </div>
            </div>
            <div className="col-md-2">
              <div className="card bg-warning text-dark">
                <div className="card-body text-center">
                  <h3 className="mb-0">{stats.unique_clients}</h3>
                  <small>{t('logs.stats.uniqueClients')}</small>
                </div>
              </div>
            </div>
            <div className="col-md-2">
              <div className="card">
                <div className="card-body text-center">
                  <h3 className="mb-0">{stats.entries_by_category?.auth || 0}</h3>
                  <small>{t('logs.stats.authEvents')}</small>
                </div>
              </div>
            </div>
          </div>
        )}
        
        {/* Tabs */}
        <ul className="nav nav-tabs mb-4">
          <li className="nav-item">
            <button
              className={`nav-link ${activeTab === 'application' ? 'active' : ''}`}
              onClick={() => setActiveTab('application')}
            >
              <i className="bi bi-server me-2"></i>
              {t('logs.tabs.application')}
            </button>
          </li>
          <li className="nav-item">
            <button
              className={`nav-link ${activeTab === 'keycloak' ? 'active' : ''}`}
              onClick={() => setActiveTab('keycloak')}
            >
              <i className="bi bi-shield-lock me-2"></i>
              {t('logs.tabs.keycloak')}
              {keycloakConfig && !keycloakConfig.eventsEnabled && (
                <span className="badge bg-warning text-dark ms-2">{t('logs.tabs.disabled')}</span>
              )}
            </button>
          </li>
        </ul>
        
        {/* Application Logs Tab */}
        {activeTab === 'application' && (
          <>
            {/* Filters */}
            <div className="card mb-4">
              <div className="card-body">
                <div className="row g-3">
                  <div className="col-md-2">
                    <label className="form-label">{t('logs.filters.level')}</label>
                    <select
                      className="form-select"
                      value={filters.level}
                      onChange={(e) => handleFilterChange('level', e.target.value)}
                    >
                      <option value="">{t('logs.filters.allLevels')}</option>
                      <option value="debug">{t('logs.filters.debug')}</option>
                      <option value="info">{t('logs.filters.info')}</option>
                      <option value="warning">{t('logs.filters.warning')}</option>
                      <option value="error">{t('logs.filters.error')}</option>
                      <option value="critical">{t('logs.filters.critical')}</option>
                    </select>
                  </div>
                  <div className="col-md-2">
                    <label className="form-label">{t('logs.filters.category')}</label>
                    <select
                      className="form-select"
                      value={filters.category}
                      onChange={(e) => handleFilterChange('category', e.target.value)}
                    >
                      <option value="">{t('logs.filters.allCategories')}</option>
                      <option value="auth">{t('logs.filters.auth')}</option>
                      <option value="introspection">{t('logs.filters.introspection')}</option>
                      <option value="rdap">{t('logs.filters.rdap')}</option>
                      <option value="system">{t('logs.filters.system')}</option>
                      <option value="admin">{t('logs.filters.admin')}</option>
                      <option value="api">{t('logs.filters.api')}</option>
                    </select>
                  </div>
                  <div className="col-md-4">
                    <label className="form-label">{t('logs.filters.search')}</label>
                    <input
                      type="text"
                      className="form-control"
                      placeholder={t('logs.filters.searchPlaceholder')}
                      value={filters.search}
                      onChange={(e) => handleFilterChange('search', e.target.value)}
                    />
                  </div>
                  <div className="col-md-2">
                    <label className="form-label">{t('logs.filters.perPage')}</label>
                    <select
                      className="form-select"
                      value={filters.limit}
                      onChange={(e) => handleFilterChange('limit', Number(e.target.value))}
                    >
                      <option value={25}>25</option>
                      <option value={50}>50</option>
                      <option value={100}>100</option>
                      <option value={200}>200</option>
                    </select>
                  </div>
                  <div className="col-md-2 d-flex align-items-end gap-2">
                    <button
                      className="btn btn-outline-danger"
                      onClick={handleClearLogs}
                    >
                      <i className="bi bi-trash me-1"></i>
                      {t('logs.filters.clear')}
                    </button>
                  </div>
                </div>
              </div>
            </div>
            
            {/* Error Alert */}
            {error && (
              <div className="alert alert-danger alert-dismissible fade show" role="alert">
                <i className="bi bi-exclamation-triangle me-2"></i>
                {error}
                <button type="button" className="btn-close" onClick={() => setError(null)}></button>
              </div>
            )}
            
            {/* Logs Table */}
            <div className="card">
              <div className="card-header d-flex justify-content-between align-items-center">
                <span>
                  {t('logs.table.showingCount', { shown: logs.length, total: totalLogs })}
                  {totalPages > 1 && ` ${t('logs.table.pageOf', { current: currentPage, total: totalPages })}`}
                </span>
              </div>
              
              <div className="table-responsive" style={{ maxHeight: '500px', overflowY: 'auto', overflowX: 'auto' }}>
                <table className="table table-hover table-striped mb-0">
                  <thead className="table-light" style={{ position: 'sticky', top: 0, zIndex: 1 }}>
                    <tr>
                      <th style={{ width: '160px', minWidth: '160px' }}>{t('logs.table.timestamp')}</th>
                      <th style={{ width: '90px', minWidth: '90px' }}>{t('logs.table.level')}</th>
                      <th style={{ width: '130px', minWidth: '130px' }}>{t('logs.table.category')}</th>
                      <th style={{ minWidth: '250px' }}>{t('logs.table.message')}</th>
                      <th style={{ width: '150px', minWidth: '150px' }}>{t('logs.table.userClient')}</th>
                      <th style={{ width: '120px', minWidth: '120px' }}>{t('logs.table.ip')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {loading ? (
                      <tr>
                        <td colSpan="6" className="text-center py-4">
                          <div className="spinner-border text-primary" role="status">
                            <span className="visually-hidden">{t('logs.table.loading')}</span>
                          </div>
                        </td>
                      </tr>
                    ) : logs.length === 0 ? (
                      <tr>
                        <td colSpan="6" className="text-center py-4 text-muted">{t('logs.table.noLogs')}</td>
                      </tr>
                    ) : (
                      logs.map((log) => (
                        <tr key={log.id}>
                          <td className="small text-muted text-nowrap">{formatTimestamp(log.timestamp)}</td>
                          <td>
                            <span className={`badge bg-${levelConfig[log.level]?.color || 'secondary'}`}>
                              <i className={`bi ${levelConfig[log.level]?.icon || 'bi-circle'} me-1`}></i>
                              {log.level}
                            </span>
                          </td>
                          <td>
                            <span className="badge bg-light text-dark">
                              <i className={`bi ${categoryIcons[log.category] || 'bi-tag'} me-1`}></i>
                              {log.category}
                            </span>
                          </td>
                          <td>
                            <div className="text-break">{log.message}</div>
                            {log.details && (
                              <details className="mt-1">
                                <summary className="small text-muted" style={{ cursor: 'pointer' }}>{t('logs.table.viewDetails')}</summary>
                                {renderDetails(log.details)}
                              </details>
                            )}
                          </td>
                          <td className="small">
                            {log.user_sub && (
                              <div className="text-truncate" style={{ maxWidth: '140px' }} title={log.user_sub}>
                                <i className="bi bi-person me-1"></i>
                                {log.user_sub.substring(0, 8)}...
                              </div>
                            )}
                            {log.client_id && (
                              <div className="text-truncate text-muted" style={{ maxWidth: '140px' }} title={log.client_id}>
                                <i className="bi bi-key me-1"></i>
                                {log.client_id.substring(0, 12)}...
                              </div>
                            )}
                          </td>
                          <td className="small text-muted text-nowrap">{log.ip_address}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
              
              {/* Pagination */}
              {totalPages > 1 && (
                <div className="card-footer d-flex justify-content-between align-items-center flex-wrap gap-2">
                  <div className="text-muted small">
                    {t('logs.pagination.showingEntries', { from: filters.offset + 1, to: Math.min(filters.offset + filters.limit, totalLogs), total: totalLogs })}
                  </div>
                  <nav aria-label={t('logs.pagination.logPaginationAria')}>
                    <ul className="pagination pagination-sm mb-0">
                      <li className={`page-item ${currentPage === 1 ? 'disabled' : ''}`}>
                        <button className="page-link" onClick={goToFirstPage} disabled={currentPage === 1} title={t('logs.pagination.firstPage')}>
                          <i className="bi bi-chevron-double-left"></i>
                        </button>
                      </li>
                      <li className={`page-item ${currentPage === 1 ? 'disabled' : ''}`}>
                        <button className="page-link" onClick={goToPrevPage} disabled={currentPage === 1} title={t('logs.pagination.previousPage')}>
                          <i className="bi bi-chevron-left"></i>
                        </button>
                      </li>
                      {getPageNumbers()[0] > 1 && (
                        <li className="page-item disabled"><span className="page-link">...</span></li>
                      )}
                      {getPageNumbers().map(page => (
                        <li key={page} className={`page-item ${currentPage === page ? 'active' : ''}`}>
                          <button className="page-link" onClick={() => goToPage(page)}>{page}</button>
                        </li>
                      ))}
                      {getPageNumbers()[getPageNumbers().length - 1] < totalPages && (
                        <li className="page-item disabled"><span className="page-link">...</span></li>
                      )}
                      <li className={`page-item ${currentPage === totalPages ? 'disabled' : ''}`}>
                        <button className="page-link" onClick={goToNextPage} disabled={currentPage === totalPages} title={t('logs.pagination.nextPage')}>
                          <i className="bi bi-chevron-right"></i>
                        </button>
                      </li>
                      <li className={`page-item ${currentPage === totalPages ? 'disabled' : ''}`}>
                        <button className="page-link" onClick={goToLastPage} disabled={currentPage === totalPages} title={t('logs.pagination.lastPage')}>
                          <i className="bi bi-chevron-double-right"></i>
                        </button>
                      </li>
                    </ul>
                  </nav>
                  <div className="d-flex align-items-center gap-2">
                    <span className="small text-muted">{t('logs.pagination.goTo')}</span>
                    <input
                      type="number"
                      className="form-control form-control-sm"
                      style={{ width: '70px' }}
                      min={1}
                      max={totalPages}
                      value={currentPage}
                      onChange={(e) => {
                        const page = parseInt(e.target.value);
                        if (page >= 1 && page <= totalPages) goToPage(page);
                      }}
                    />
                  </div>
                </div>
              )}
            </div>
          </>
        )}
        
        {/* Keycloak Events Tab */}
        {activeTab === 'keycloak' && (
          <>
            {/* Keycloak Config Warning */}
            {keycloakConfig && !keycloakConfig.eventsEnabled && (
              <div className="alert alert-warning">
                <i className="bi bi-exclamation-triangle me-2"></i>
                <strong>{t('logs.keycloak.disabledTitle')}</strong> {t('logs.keycloak.disabledHint')}
              </div>
            )}
            
            {/* Keycloak Filters */}
            <div className="card mb-4">
              <div className="card-body">
                <div className="row g-3">
                  <div className="col-md-2">
                    <label className="form-label">{t('logs.keycloak.eventType')}</label>
                    <select
                      className="form-select"
                      value={kcFilters.type}
                      onChange={(e) => handleKcFilterChange('type', e.target.value)}
                    >
                      <option value="">{t('logs.keycloak.allTypes')}</option>
                      <option value="INTROSPECT_TOKEN">{t('logs.keycloak.introspectToken')}</option>
                      <option value="INTROSPECT_TOKEN_ERROR">{t('logs.keycloak.introspectTokenError')}</option>
                      <option value="LOGIN">{t('logs.keycloak.login')}</option>
                      <option value="LOGIN_ERROR">{t('logs.keycloak.loginError')}</option>
                      <option value="LOGOUT">{t('logs.keycloak.logout')}</option>
                      <option value="CODE_TO_TOKEN">{t('logs.keycloak.codeToToken')}</option>
                      <option value="REFRESH_TOKEN">{t('logs.keycloak.refreshToken')}</option>
                      <option value="CLIENT_LOGIN">{t('logs.keycloak.clientLogin')}</option>
                      <option value="REGISTER">{t('logs.keycloak.register')}</option>
                      <option value="TOKEN_EXCHANGE">{t('logs.keycloak.tokenExchange')}</option>
                    </select>
                  </div>
                  <div className="col-md-2">
                    <label className="form-label">{t('logs.keycloak.clientId')}</label>
                    <input
                      type="text"
                      className="form-control"
                      placeholder={t('logs.keycloak.filterByClient')}
                      value={kcFilters.client}
                      onChange={(e) => handleKcFilterChange('client', e.target.value)}
                    />
                  </div>
                  <div className="col-md-2">
                    <label className="form-label">{t('logs.keycloak.userId')}</label>
                    <input
                      type="text"
                      className="form-control"
                      placeholder={t('logs.keycloak.filterByUser')}
                      value={kcFilters.user}
                      onChange={(e) => handleKcFilterChange('user', e.target.value)}
                    />
                  </div>
                  <div className="col-md-2">
                    <label className="form-label">{t('logs.keycloak.ipAddress')}</label>
                    <input
                      type="text"
                      className="form-control"
                      placeholder={t('logs.keycloak.filterByIp')}
                      value={kcFilters.ip_address}
                      onChange={(e) => handleKcFilterChange('ip_address', e.target.value)}
                    />
                  </div>
                  <div className="col-md-2 d-flex align-items-end gap-2">
                    <button
                      className="btn btn-primary"
                      onClick={fetchKeycloakEvents}
                    >
                      <i className="bi bi-search me-1"></i>
                      {t('logs.keycloak.search')}
                    </button>
                    <button
                      className="btn btn-outline-danger"
                      onClick={handleClearKeycloakEvents}
                    >
                      <i className="bi bi-trash me-1"></i>
                      {t('logs.keycloak.clear')}
                    </button>
                  </div>
                </div>
              </div>
            </div>
            
            {/* Keycloak Error Alert */}
            {keycloakError && (
              <div className="alert alert-danger alert-dismissible fade show" role="alert">
                <i className="bi bi-exclamation-triangle me-2"></i>
                {keycloakError}
                <button type="button" className="btn-close" onClick={() => setKeycloakError(null)}></button>
              </div>
            )}
            
            {/* Keycloak Events Table */}
            <div className="card">
              <div className="card-header">
                <span>{t('logs.keycloak.eventsResults', { n: keycloakEvents.length })}</span>
              </div>
              
              <div className="table-responsive" style={{ maxHeight: '500px', overflowY: 'auto', overflowX: 'auto' }}>
                <table className="table table-hover table-striped mb-0">
                  <thead className="table-light" style={{ position: 'sticky', top: 0, zIndex: 1 }}>
                    <tr>
                      <th style={{ width: '160px', minWidth: '160px' }}>{t('logs.keycloak.colTimestamp')}</th>
                      <th style={{ width: '160px', minWidth: '160px' }}>{t('logs.keycloak.colEventType')}</th>
                      <th style={{ width: '150px', minWidth: '150px' }}>{t('logs.keycloak.colClient')}</th>
                      <th style={{ minWidth: '200px' }}>{t('logs.keycloak.colUser')}</th>
                      <th style={{ width: '120px', minWidth: '120px' }}>{t('logs.keycloak.colIpAddress')}</th>
                      <th style={{ minWidth: '150px' }}>{t('logs.keycloak.colDetails')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {keycloakLoading ? (
                      <tr>
                        <td colSpan="6" className="text-center py-4">
                          <div className="spinner-border text-primary" role="status">
                            <span className="visually-hidden">{t('logs.table.loading')}</span>
                          </div>
                        </td>
                      </tr>
                    ) : keycloakEvents.length === 0 ? (
                      <tr>
                        <td colSpan="6" className="text-center py-4 text-muted">
                          {t('logs.keycloak.noEvents')}
                        </td>
                      </tr>
                    ) : (
                      keycloakEvents.map((event, index) => {
                        const config = getKcEventConfig(event.type);
                        return (
                          <tr key={index}>
                            <td className="small text-muted text-nowrap">
                              {formatKeycloakTime(event.time)}
                            </td>
                            <td>
                              <span className={`badge bg-${config.color}`}>
                                <i className={`bi ${config.icon} me-1`}></i>
                                {event.type}
                              </span>
                            </td>
                            <td className="small">
                              <span className="text-truncate d-inline-block" style={{ maxWidth: '140px' }} title={event.clientId}>
                                {event.clientId || t('logs.na')}
                              </span>
                            </td>
                            <td className="small">
                              {event.userId ? (
                                <div className="text-truncate" style={{ maxWidth: '190px' }} title={event.userId}>
                                  <i className="bi bi-person me-1"></i>
                                  {event.userId.substring(0, 20)}...
                                </div>
                              ) : (
                                <span className="text-muted">{t('logs.na')}</span>
                              )}
                            </td>
                            <td className="small text-muted text-nowrap">
                              {event.ipAddress || t('logs.na')}
                            </td>
                            <td>
                              {event.details && Object.keys(event.details).length > 0 ? (
                                <details>
                                  <summary className="small text-muted" style={{ cursor: 'pointer' }}>
                                    {t('logs.table.viewDetails')}
                                  </summary>
                                  <pre className="mb-0 small bg-light p-2 rounded mt-1" style={{ maxWidth: '300px', overflow: 'auto' }}>
                                    {JSON.stringify(event.details, null, 2)}
                                  </pre>
                                </details>
                              ) : (
                                <span className="text-muted small">-</span>
                              )}
                              {event.error && (
                                <div className="text-danger small mt-1">
                                  <i className="bi bi-exclamation-circle me-1"></i>
                                  {event.error}
                                </div>
                              )}
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>

              {/* Keycloak events pagination (no total available from Keycloak Admin API) */}
              <div className="card-footer d-flex justify-content-between align-items-center flex-wrap gap-2">
                <div className="d-flex align-items-center gap-2">
                  <label className="text-muted small mb-0" htmlFor="kc-page-size">Show</label>
                  <select
                    id="kc-page-size"
                    className="form-select form-select-sm w-auto"
                    value={kcPageSize}
                    onChange={(e) => { setKcPageSize(Number(e.target.value)); setKcPage(1); }}
                    aria-label="Events per page"
                  >
                    <option value={25}>25</option>
                    <option value={50}>50</option>
                    <option value={100}>100</option>
                    <option value={200}>200</option>
                  </select>
                  <span className="text-muted small">per page</span>
                </div>
                <nav aria-label="Keycloak events pagination">
                  <ul className="pagination pagination-sm mb-0">
                    <li className={`page-item ${kcPage === 1 ? 'disabled' : ''}`}>
                      <button
                        className="page-link"
                        onClick={() => setKcPage(p => Math.max(1, p - 1))}
                        disabled={kcPage === 1}
                        title="Previous Page"
                      >
                        <i className="bi bi-chevron-left me-1"></i>Previous
                      </button>
                    </li>
                    <li className="page-item active">
                      <span className="page-link">Page {kcPage}</span>
                    </li>
                    <li className={`page-item ${keycloakEvents.length < kcPageSize ? 'disabled' : ''}`}>
                      <button
                        className="page-link"
                        onClick={() => setKcPage(p => p + 1)}
                        disabled={keycloakEvents.length < kcPageSize}
                        title="Next Page"
                      >
                        Next<i className="bi bi-chevron-right ms-1"></i>
                      </button>
                    </li>
                  </ul>
                </nav>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default Logs;