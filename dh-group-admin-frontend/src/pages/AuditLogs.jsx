/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback } from 'react';
import { getAuditLogs, getAuditLogStats } from '../services/api';
import Pagination from '../components/Pagination';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const CATEGORY_CONFIG = {
  AUTH: { color: 'info', icon: 'fa-solid fa-right-to-bracket' },
  CRUD: { color: 'primary', icon: 'fa-solid fa-pen-to-square' },
  WORKFLOW: { color: 'warning', icon: 'fa-solid fa-arrows-spin' },
  CREDENTIAL: { color: 'danger', icon: 'fa-solid fa-key' },
  API: { color: 'success', icon: 'fa-solid fa-plug' },
  SYSTEM: { color: 'secondary', icon: 'fa-solid fa-gear' },
};

const RESULT_CONFIG = {
  SUCCESS: { color: 'success', icon: 'fa-solid fa-check' },
  FAILURE: { color: 'danger', icon: 'fa-solid fa-xmark' },
  DENIED: { color: 'warning', icon: 'fa-solid fa-ban' },
};

const ENTITY_ICONS = {
  USER: 'fa-solid fa-user',
  TEMPLATE: 'fa-solid fa-file-contract',
  SUBSCRIPTION: 'fa-solid fa-handshake',
  DATA_HOLDER: 'fa-solid fa-database',
  REQUESTOR_GROUP: 'fa-solid fa-users',
  CREDENTIAL: 'fa-solid fa-key',
  REQUEST_TYPE: 'fa-solid fa-layer-group',
  SYSTEM: 'fa-solid fa-gear',
};

const formatDate = (d) => d ? new Date(d).toLocaleString() : '—';
const formatDateShort = (d, t) => {
  if (!d) return '—';
  const date = new Date(d);
  const now = new Date();
  const diff = now - date;
  if (diff < 60000) return t('auditLogs.time.justNow');
  if (diff < 3600000) return t('auditLogs.time.minutesAgo', { count: Math.floor(diff / 60000) });
  if (diff < 86400000) return t('auditLogs.time.hoursAgo', { count: Math.floor(diff / 3600000) });
  if (diff < 172800000) return t('auditLogs.time.yesterday');
  return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

const AuditLogs = () => {
  const { t } = useT();
  const [logs, setLogs] = useState([]);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [totalElements, setTotalElements] = useState(0);
  const [detailModal, setDetailModal] = useState(null);

  // Filters
  const [filters, setFilters] = useState({
    category: '', entityType: '', action: '', performedBy: '', result: '', search: '', page: 0, size: 50,
  });

  const load = useCallback(async () => {
    try {
      setLoading(true);
      const [logsRes, statsRes] = await Promise.all([
        getAuditLogs(filters),
        getAuditLogStats(),
      ]);
      setLogs(logsRes.content || []);
      setTotalElements(logsRes.totalElements || 0);
      setStats(statsRes);
    } catch {
      toast.error(t('auditLogs.errors.load'));
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => { load(); }, [load]);

  const setFilter = (key, value) => {
    setFilters(prev => ({ ...prev, [key]: value, page: 0 }));
  };

  const clearFilters = () => {
    setFilters({ category: '', entityType: '', action: '', performedBy: '', result: '', search: '', page: 0, size: 50 });
  };

  const hasFilters = filters.category || filters.entityType || filters.action || filters.performedBy || filters.result || filters.search;

  if (loading && logs.length === 0) {
    return <div className="d-flex justify-content-center py-5"><div className="spinner-border text-primary" /></div>;
  }

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">{t('auditLogs.title')}</h2>
          <p className="text-muted mb-0">
            {t('auditLogs.eventsLogged', { count: totalElements })}
            {stats?.last24h > 0 && <span className="ms-2">· {t('auditLogs.inLast24h', { count: stats.last24h })}</span>}
          </p>
        </div>
        <div className="d-flex gap-2">
          {hasFilters && (
            <button className="btn btn-outline-secondary btn-sm" onClick={clearFilters}>
              <i className="fa-solid fa-filter-circle-xmark me-1"></i>{t('auditLogs.clearFilters')}
            </button>
          )}
          <button className="btn btn-outline-secondary btn-sm" onClick={load} disabled={loading}>
            <i className={`fa-solid fa-arrows-rotate me-1 ${loading ? 'fa-spin' : ''}`}></i>{t('common.refresh')}
          </button>
        </div>
      </div>

      {/* Stats */}
      {stats && (
        <div className="row g-3 mb-4">
          {Object.entries(CATEGORY_CONFIG).map(([cat, cfg]) => {
            const key = cat.toLowerCase() + 'Events';
            const count = stats[key] || 0;
            return (
              <div className="col" key={cat}>
                <div
                  className={`card text-center p-3 ${filters.category === cat ? 'border-' + cfg.color + ' border-2' : ''}`}
                  style={{ cursor: 'pointer' }}
                  onClick={() => setFilter('category', filters.category === cat ? '' : cat)}
                >
                  <div className={`fs-4 fw-bold text-${cfg.color}`}>{count}</div>
                  <small className="text-muted">
                    <i className={`${cfg.icon} me-1`}></i>{cat}
                  </small>
                </div>
              </div>
            );
          })}
          {stats.failureCount > 0 && (
            <div className="col">
              <div
                className={`card text-center p-3 ${filters.result === 'FAILURE' ? 'border-danger border-2' : ''}`}
                style={{ cursor: 'pointer' }}
                onClick={() => setFilter('result', filters.result === 'FAILURE' ? '' : 'FAILURE')}
              >
                <div className="fs-4 fw-bold text-danger">{stats.failureCount}</div>
                <small className="text-muted">
                  <i className="fa-solid fa-triangle-exclamation me-1"></i>{t('auditLogs.failures')}
                </small>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Filters Row */}
      <div className="card mb-3">
        <div className="card-body py-2">
          <div className="row g-2 align-items-end">
            <div className="col-md-2">
              <label className="form-label small mb-0">{t('auditLogs.fields.category')}</label>
              <select className="form-select form-select-sm" value={filters.category}
                onChange={e => setFilter('category', e.target.value)}>
                <option value="">{t('common.all')}</option>
                {Object.keys(CATEGORY_CONFIG).map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small mb-0">{t('auditLogs.fields.entityType')}</label>
              <select className="form-select form-select-sm" value={filters.entityType}
                onChange={e => setFilter('entityType', e.target.value)}>
                <option value="">{t('common.all')}</option>
                {Object.keys(ENTITY_ICONS).map(e => <option key={e} value={e}>{e}</option>)}
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small mb-0">{t('auditLogs.fields.action')}</label>
              <select className="form-select form-select-sm" value={filters.action}
                onChange={e => setFilter('action', e.target.value)}>
                <option value="">{t('common.all')}</option>
                {(stats?.actions || []).map(a => <option key={a} value={a}>{a}</option>)}
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small mb-0">{t('auditLogs.fields.performedBy')}</label>
              <select className="form-select form-select-sm" value={filters.performedBy}
                onChange={e => setFilter('performedBy', e.target.value)}>
                <option value="">{t('common.all')}</option>
                {(stats?.actors || []).map(a => <option key={a} value={a}>{a}</option>)}
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small mb-0">{t('auditLogs.fields.result')}</label>
              <select className="form-select form-select-sm" value={filters.result}
                onChange={e => setFilter('result', e.target.value)}>
                <option value="">{t('common.all')}</option>
                {Object.keys(RESULT_CONFIG).map(r => <option key={r} value={r}>{r}</option>)}
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small mb-0">{t('common.search')}</label>
              <input className="form-control form-control-sm" placeholder={t('auditLogs.searchPlaceholder')}
                value={filters.search} onChange={e => setFilter('search', e.target.value)} />
            </div>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="card">
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th style={{ width: 140 }}>{t('auditLogs.fields.time')}</th>
                <th style={{ width: 100 }}>{t('auditLogs.fields.category')}</th>
                <th>{t('auditLogs.fields.action')}</th>
                <th>{t('auditLogs.fields.entity')}</th>
                <th>{t('auditLogs.fields.performedBy')}</th>
                <th style={{ width: 80 }}>{t('auditLogs.fields.result')}</th>
                <th>{t('auditLogs.fields.details')}</th>
              </tr>
            </thead>
            <tbody>
              {logs.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center text-muted py-5">
                    <i className="fa-solid fa-clipboard-list fa-2x mb-2"></i><br />
                    {hasFilters ? t('auditLogs.empty.noMatch') : t('auditLogs.empty.noLogs')}
                  </td>
                </tr>
              ) : logs.map(entry => {
                const catCfg = CATEGORY_CONFIG[entry.category] || { color: 'secondary', icon: 'fa-solid fa-circle' };
                const resCfg = RESULT_CONFIG[entry.result] || { color: 'secondary', icon: 'fa-solid fa-circle' };
                const entityIcon = ENTITY_ICONS[entry.entityType] || 'fa-solid fa-circle';

                return (
                  <tr
                    key={entry.id}
                    className={entry.result === 'FAILURE' ? 'table-danger' : entry.result === 'DENIED' ? 'table-warning' : ''}
                    style={{ cursor: 'pointer' }}
                    onClick={() => setDetailModal(entry)}
                  >
                    <td className="small text-muted text-nowrap">{formatDateShort(entry.createdAt, t)}</td>
                    <td>
                      <span className={`badge bg-${catCfg.color}`}>
                        <i className={`${catCfg.icon} me-1`}></i>{entry.category}
                      </span>
                    </td>
                    <td>
                      <span className="fw-semibold small">{entry.action}</span>
                    </td>
                    <td>
                      <div className="d-flex align-items-center gap-1">
                        <i className={`${entityIcon} text-muted`} style={{ fontSize: 11 }}></i>
                        <span className="small">{entry.entityType}</span>
                      </div>
                      {entry.entityName && (
                        <div className="text-muted small text-truncate" style={{ maxWidth: 180 }}>{entry.entityName}</div>
                      )}
                      {entry.entityId && !entry.entityName && (
                        <code className="small text-truncate d-block" style={{ maxWidth: 180 }}>{entry.entityId}</code>
                      )}
                    </td>
                    <td className="small">{entry.performedBy || '—'}</td>
                    <td>
                      <span className={`badge bg-${resCfg.color}`}>
                        <i className={`${resCfg.icon} me-1`}></i>{entry.result}
                      </span>
                    </td>
                    <td>
                      <div className="text-muted small text-truncate" style={{ maxWidth: 250 }}>
                        {entry.details || '—'}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalElements > 0 && (
          <div className="card-footer">
            <Pagination
              page={filters.page + 1}
              pageSize={filters.size}
              totalItems={totalElements}
              onPageChange={(p) => setFilters(f => ({ ...f, page: p - 1 }))}
              onPageSizeChange={(s) => setFilters(f => ({ ...f, size: s, page: 0 }))}
              itemLabel={t('auditLogs.itemLabel')}
            />
          </div>
        )}
      </div>

      {/* Detail Modal */}
      {detailModal && (() => {
        const catCfg = CATEGORY_CONFIG[detailModal.category] || { color: 'secondary', icon: 'fa-solid fa-circle' };
        const resCfg = RESULT_CONFIG[detailModal.result] || { color: 'secondary', icon: 'fa-solid fa-circle' };
        return (
          <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
            <div className="modal-dialog modal-dialog-centered modal-lg">
              <div className="modal-content">
                <div className="modal-header">
                  <h5 className="modal-title">
                    <i className={`${catCfg.icon} text-${catCfg.color} me-2`}></i>
                    {t('auditLogs.detail.title', { id: detailModal.id })}
                  </h5>
                  <button type="button" className="btn-close" onClick={() => setDetailModal(null)}></button>
                </div>
                <div className="modal-body">
                  <div className="d-flex gap-2 mb-3">
                    <span className={`badge bg-${catCfg.color}`}>{detailModal.category}</span>
                    <span className="badge bg-dark">{detailModal.action}</span>
                    <span className={`badge bg-${resCfg.color}`}>{detailModal.result}</span>
                    {detailModal.source && <span className="badge bg-secondary">{detailModal.source}</span>}
                  </div>
                  <table className="table table-sm">
                    <tbody>
                      <tr><td className="text-muted" style={{ width: 140 }}>{t('auditLogs.fields.timestamp')}</td><td>{formatDate(detailModal.createdAt)}</td></tr>
                      <tr><td className="text-muted">{t('auditLogs.fields.category')}</td><td><span className={`badge bg-${catCfg.color}`}><i className={`${catCfg.icon} me-1`}></i>{detailModal.category}</span></td></tr>
                      <tr><td className="text-muted">{t('auditLogs.fields.action')}</td><td className="fw-semibold">{detailModal.action}</td></tr>
                      <tr><td className="text-muted">{t('auditLogs.fields.entityType')}</td><td>{detailModal.entityType}</td></tr>
                      <tr><td className="text-muted">{t('auditLogs.fields.entityId')}</td><td>{detailModal.entityId ? <code>{detailModal.entityId}</code> : '—'}</td></tr>
                      <tr><td className="text-muted">{t('auditLogs.fields.entityName')}</td><td>{detailModal.entityName || '—'}</td></tr>
                      <tr><td className="text-muted">{t('auditLogs.fields.performedBy')}</td><td>{detailModal.performedBy || '—'}</td></tr>
                      <tr><td className="text-muted">{t('auditLogs.fields.source')}</td><td>{detailModal.source || '—'}</td></tr>
                      <tr><td className="text-muted">{t('auditLogs.fields.result')}</td><td><span className={`badge bg-${resCfg.color}`}><i className={`${resCfg.icon} me-1`}></i>{detailModal.result}</span></td></tr>
                      {detailModal.ipAddress && <tr><td className="text-muted">{t('auditLogs.fields.ipAddress')}</td><td><code>{detailModal.ipAddress}</code></td></tr>}
                      <tr>
                        <td className="text-muted">{t('auditLogs.fields.details')}</td>
                        <td>
                          {detailModal.details ? (
                            <div className="bg-light rounded p-2 small" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                              {detailModal.details}
                            </div>
                          ) : '—'}
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <div className="modal-footer">
                  <button className="btn btn-secondary" onClick={() => setDetailModal(null)}>{t('common.close')}</button>
                </div>
              </div>
            </div>
          </div>
        );
      })()}
    </div>
  );
};

export default AuditLogs;
