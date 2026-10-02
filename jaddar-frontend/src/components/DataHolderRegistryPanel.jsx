/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback } from 'react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { useT } from '../i18n';

const BASE = '/api/admin/data-holder-registry';

// Deployment-wide switch and URL for the external Data Holder Registry.
const DataHolderRegistryPanel = ({ canManage = false }) => {
  const { t } = useT();
  const [settings, setSettings] = useState(null);
  const [url, setUrl] = useState('');
  const [holders, setHolders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [expanded, setExpanded] = useState(false);

  const applySettings = (data) => {
    setSettings(data);
    setUrl(data.registry_url || '');
  };

  const loadHolders = useCallback(async () => {
    try {
      const resp = await api.get(`${BASE}/holders`);
      setHolders(resp.data?.holders || []);
    } catch {
      setHolders([]);
    }
  }, []);

  useEffect(() => {
    (async () => {
      try {
        const resp = await api.get(`${BASE}/settings`);
        applySettings(resp.data);
        if (resp.data?.enabled) await loadHolders();
      } catch (err) {
        toast.error(err.response?.data?.detail || err.message);
      } finally {
        setLoading(false);
      }
    })();
  }, [loadHolders]);

  const save = async (enabled) => {
    setSaving(true);
    try {
      const resp = await api.put(`${BASE}/settings`, { enabled, registry_url: url.trim() });
      applySettings(resp.data);
      if (resp.data?.enabled) {
        await loadHolders();
        if (resp.data.last_sync_status === 'SUCCESS') {
          toast.success(t('dataHolderRegistry.toast.saved'));
        } else {
          toast.error(resp.data.last_sync_message || t('dataHolderRegistry.toast.syncFailed'));
        }
      } else {
        setHolders([]);
        toast.success(t('dataHolderRegistry.toast.disabled'));
      }
    } catch (err) {
      toast.error(err.response?.data?.detail || err.message);
    } finally {
      setSaving(false);
    }
  };

  const refresh = async () => {
    setRefreshing(true);
    try {
      const resp = await api.post(`${BASE}/refresh`);
      applySettings(resp.data);
      await loadHolders();
      if (resp.data.last_sync_status === 'SUCCESS') {
        toast.success(resp.data.last_sync_message);
      } else {
        toast.error(resp.data.last_sync_message);
      }
    } catch (err) {
      toast.error(err.response?.data?.detail || err.message);
    } finally {
      setRefreshing(false);
    }
  };

  const formatDate = (value) => (value ? new Date(value).toLocaleString() : '—');

  const formatCoverage = (holder) => {
    const parts = [];
    if (holder.tlds?.length) parts.push(t('dataHolderRegistry.coverage.tlds', { n: holder.tlds.length }));
    if (holder.ip_ranges?.length) parts.push(t('dataHolderRegistry.coverage.ipRanges', { n: holder.ip_ranges.length }));
    if (holder.asn_ranges?.length) parts.push(t('dataHolderRegistry.coverage.asnRanges', { n: holder.asn_ranges.length }));
    return parts.join(', ') || t('dataHolderRegistry.coverage.none');
  };

  if (loading) return null;

  const enabled = !!settings?.enabled;
  const failed = settings?.last_sync_status === 'FAILURE';

  return (
    <div className="card border-0 shadow-sm">
      <div className="card-header bg-white d-flex justify-content-between align-items-center flex-wrap gap-2">
        <h6 className="mb-0">
          <i className="bi bi-broadcast me-2"></i>
          {t('dataHolderRegistry.title')}
        </h6>
        <div className="form-check form-switch mb-0">
          <input
            className="form-check-input"
            type="checkbox"
            id="registryEnabled"
            checked={enabled}
            disabled={saving || !canManage}
            onChange={e => save(e.target.checked)}
          />
          <label className="form-check-label small" htmlFor="registryEnabled">
            {enabled ? t('dataHolderRegistry.enabled') : t('dataHolderRegistry.disabled')}
          </label>
        </div>
      </div>

      <div className="card-body">
        <p className="text-muted small">{t('dataHolderRegistry.description')}</p>
        {!canManage && (
          <p className="text-muted small fst-italic">{t('dataHolderRegistry.adminOnly')}</p>
        )}

        <div className="row g-2 align-items-end mb-3">
          <div className="col">
            <label className="form-label small mb-1">{t('dataHolderRegistry.urlLabel')}</label>
            <input
              type="text"
              className="form-control form-control-sm"
              value={url}
              onChange={e => setUrl(e.target.value)}
              disabled={!canManage}
              placeholder="https://registry.example.com/dataholder-registry/registry"
            />
            <small className="text-muted">{t('dataHolderRegistry.urlHint')}</small>
          </div>
          <div className="col-auto">
            <button className="btn btn-primary btn-sm" onClick={() => save(enabled)} disabled={saving || !canManage}>
              {saving
                ? <span className="spinner-border spinner-border-sm"></span>
                : <><i className="bi bi-check-lg me-1"></i>{t('dataHolderRegistry.save')}</>}
            </button>
          </div>
          <div className="col-auto">
            <button
              className="btn btn-outline-secondary btn-sm"
              onClick={refresh}
              disabled={!enabled || refreshing || !canManage}
            >
              {refreshing
                ? <span className="spinner-border spinner-border-sm"></span>
                : <><i className="bi bi-arrow-clockwise me-1"></i>{t('dataHolderRegistry.refresh')}</>}
            </button>
          </div>
        </div>

        {enabled && settings?.last_synced_at && (
          <div className={`alert py-2 small mb-3 ${failed ? 'alert-danger' : 'alert-success'}`}>
            <i className={`bi ${failed ? 'bi-exclamation-triangle' : 'bi-check-circle'} me-1`}></i>
            {settings.last_sync_message}
            <span className="ms-2 text-muted">
              {t('dataHolderRegistry.lastSynced', { when: formatDate(settings.last_synced_at) })}
            </span>
            {settings.feed_publication && (
              <span className="ms-2 text-muted">
                {t('dataHolderRegistry.published', { when: formatDate(settings.feed_publication) })}
              </span>
            )}
          </div>
        )}

        {enabled && (
          <>
            <button
              className="btn btn-link btn-sm p-0 text-decoration-none"
              onClick={() => setExpanded(v => !v)}
            >
              <i className={`bi ${expanded ? 'bi-chevron-down' : 'bi-chevron-right'} me-1`}></i>
              {t('dataHolderRegistry.holdersHeading', { count: holders.length })}
            </button>

            {expanded && (
              holders.length === 0 ? (
                <p className="text-muted small mt-2 mb-0">{t('dataHolderRegistry.holdersEmpty')}</p>
              ) : (
                <div className="table-responsive mt-2">
                  <table className="table table-sm table-hover align-middle mb-0">
                    <thead className="table-light">
                      <tr>
                        <th>{t('dataHolderRegistry.table.name')}</th>
                        <th>{t('dataHolderRegistry.table.baseUrls')}</th>
                        <th>{t('dataHolderRegistry.table.coverage')}</th>
                        <th>{t('dataHolderRegistry.table.auth')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {holders.map((holder, idx) => (
                        <tr key={`${holder.name}-${idx}`}>
                          <td>
                            <div className="fw-semibold">{holder.name}</div>
                            {holder.description && (
                              <div className="text-muted small">{holder.description}</div>
                            )}
                          </td>
                          <td className="small font-monospace text-break" style={{ maxWidth: 240 }}>
                            {(holder.base_urls || []).join(', ')}
                          </td>
                          <td className="small text-muted">{formatCoverage(holder)}</td>
                          <td>
                            {holder.requires_auth
                              ? <span className="badge bg-warning text-dark">{t('dataHolderRegistry.table.authRequired')}</span>
                              : <span className="badge bg-light text-muted border">{t('dataHolderRegistry.table.authNone')}</span>}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <p className="text-muted small mt-2 mb-0">
                    <i className="bi bi-info-circle me-1"></i>{t('dataHolderRegistry.readOnlyNote')}
                  </p>
                </div>
              )
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default DataHolderRegistryPanel;
