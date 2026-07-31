/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../contexts/AuthContext';
import Header from '../components/Header';
import JakeCompliancePanel from '../components/JakeCompliancePanel';
import Pagination from '../components/Pagination';
import api from '../services/api';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

// ============================================================
// Data Holder Form Modal
// ============================================================
const DataHolderModal = ({ show, onClose, onSave, editingHolder }) => {
  const { t } = useT();
  const [form, setForm] = useState({
    name: '',
    description: '',
    base_urls: [''],
    tlds: [''],
    ip_ranges: [''],
    asn_ranges: [['', '']],
    requires_auth: false,
    auth_type: 'none',
    is_active: true,
  });
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState({});

  useEffect(() => {
    if (editingHolder) {
      setForm({
        name: editingHolder.name || '',
        description: editingHolder.description || '',
        base_urls: editingHolder.base_urls?.length ? editingHolder.base_urls : [''],
        tlds: editingHolder.tlds?.length ? editingHolder.tlds : [''],
        ip_ranges: editingHolder.ip_ranges?.length ? editingHolder.ip_ranges : [''],
        asn_ranges: editingHolder.asn_ranges?.length
          ? editingHolder.asn_ranges.map(r => [String(r[0]), String(r[1])])
          : [['', '']],
        requires_auth: editingHolder.requires_auth || false,
        auth_type: editingHolder.auth_type || 'none',
        is_active: editingHolder.is_active !== false,
      });
    } else {
      setForm({
        name: '',
        description: '',
        base_urls: [''],
        tlds: [''],
        ip_ranges: [''],
        asn_ranges: [['', '']],
        requires_auth: false,
        auth_type: 'none',
        is_active: true,
      });
    }
    setErrors({});
  }, [editingHolder, show]);

  const updateField = (field, value) => {
    setForm(prev => ({ ...prev, [field]: value }));
    setErrors(prev => ({ ...prev, [field]: null }));
  };

  // Array field helpers
  const addArrayItem = (field, defaultValue = '') => {
    setForm(prev => ({ ...prev, [field]: [...prev[field], defaultValue] }));
  };
  const removeArrayItem = (field, index) => {
    setForm(prev => ({
      ...prev,
      [field]: prev[field].filter((_, i) => i !== index),
    }));
  };
  const updateArrayItem = (field, index, value) => {
    setForm(prev => ({
      ...prev,
      [field]: prev[field].map((item, i) => (i === index ? value : item)),
    }));
  };

  // ASN range helpers
  const addAsnRange = () => {
    setForm(prev => ({ ...prev, asn_ranges: [...prev.asn_ranges, ['', '']] }));
  };
  const removeAsnRange = (index) => {
    setForm(prev => ({
      ...prev,
      asn_ranges: prev.asn_ranges.filter((_, i) => i !== index),
    }));
  };
  const updateAsnRange = (index, pos, value) => {
    setForm(prev => ({
      ...prev,
      asn_ranges: prev.asn_ranges.map((pair, i) =>
        i === index ? (pos === 0 ? [value, pair[1]] : [pair[0], value]) : pair
      ),
    }));
  };

  const validate = () => {
    const errs = {};
    if (!form.name.trim()) errs.name = t('dataHolders.modal.errNameRequired');
    const urls = form.base_urls.filter(u => u.trim());
    if (urls.length === 0) errs.base_urls = t('dataHolders.modal.errBaseUrlRequired');
    urls.forEach((u, i) => {
      if (!u.startsWith('http://') && !u.startsWith('https://')) {
        errs.base_urls = t('dataHolders.modal.errUrlProtocol', { n: i + 1 });
      }
    });
    // Must have at least one of: tlds, ip_ranges, asn_ranges
    const tlds = form.tlds.filter(t => t.trim());
    const ips = form.ip_ranges.filter(r => r.trim());
    const asns = form.asn_ranges.filter(r => r[0] && r[1]);
    if (tlds.length === 0 && ips.length === 0 && asns.length === 0) {
      errs.coverage = t('dataHolders.modal.errCoverageRequired');
    }
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setSaving(true);
    try {
      const payload = {
        name: form.name.trim(),
        description: form.description.trim() || null,
        base_urls: form.base_urls.filter(u => u.trim()),
        tlds: form.tlds.filter(t => t.trim()).map(t => t.toLowerCase()),
        ip_ranges: form.ip_ranges.filter(r => r.trim()),
        asn_ranges: form.asn_ranges
          .filter(r => r[0] && r[1])
          .map(r => [parseInt(r[0]), parseInt(r[1])]),
        requires_auth: form.requires_auth,
        auth_type: form.requires_auth ? form.auth_type : 'none',
        is_active: form.is_active,
      };
      await onSave(payload);
    } finally {
      setSaving(false);
    }
  };

  if (!show) return null;

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog modal-lg modal-dialog-scrollable">
        <div className="modal-content">
          <div className="modal-header">
            <h5 className="modal-title">
              <i className={`bi ${editingHolder ? 'bi-pencil' : 'bi-plus-circle'} me-2`}></i>
              {editingHolder ? t('dataHolders.modal.editTitle') : t('dataHolders.modal.addTitle')}
            </h5>
            <button type="button" className="btn-close" onClick={onClose} disabled={saving}></button>
          </div>
          <div className="modal-body">
            {/* Name */}
            <div className="mb-3">
              <label className="form-label fw-semibold">{t('dataHolders.modal.nameLabel')}</label>
              <input
                type="text"
                className={`form-control ${errors.name ? 'is-invalid' : ''}`}
                value={form.name}
                onChange={e => updateField('name', e.target.value)}
                placeholder={t('dataHolders.modal.namePlaceholder')}
              />
              {errors.name && <div className="invalid-feedback">{errors.name}</div>}
            </div>

            {/* Description */}
            <div className="mb-3">
              <label className="form-label fw-semibold">{t('dataHolders.modal.descriptionLabel')}</label>
              <textarea
                className="form-control"
                rows="2"
                value={form.description}
                onChange={e => updateField('description', e.target.value)}
                placeholder={t('dataHolders.modal.descriptionPlaceholder')}
              />
            </div>

            {/* Base URLs */}
            <div className="mb-3">
              <label className="form-label fw-semibold">{t('dataHolders.modal.baseUrlsLabel')}</label>
              <small className="text-muted d-block mb-1">
                {t('dataHolders.modal.baseUrlsHint')}
              </small>
              {form.base_urls.map((url, i) => (
                <div key={i} className="input-group mb-1">
                  <input
                    type="text"
                    className={`form-control ${errors.base_urls ? 'is-invalid' : ''}`}
                    value={url}
                    onChange={e => updateArrayItem('base_urls', i, e.target.value)}
                    placeholder="https://rdap.example.com/"
                  />
                  {form.base_urls.length > 1 && (
                    <button className="btn btn-outline-danger btn-sm" onClick={() => removeArrayItem('base_urls', i)}>
                      <i className="bi bi-x"></i>
                    </button>
                  )}
                </div>
              ))}
              {errors.base_urls && <div className="text-danger small">{errors.base_urls}</div>}
              <button className="btn btn-outline-secondary btn-sm mt-1" onClick={() => addArrayItem('base_urls')}>
                <i className="bi bi-plus me-1"></i>{t('dataHolders.modal.addUrl')}
              </button>
            </div>

            <hr />
            <h6 className="text-muted mb-3">
              <i className="bi bi-diagram-3 me-1"></i>
              {t('dataHolders.modal.coverageHeading')}
            </h6>
            {errors.coverage && (
              <div className="alert alert-warning py-2 small">{errors.coverage}</div>
            )}

            {/* TLDs */}
            <div className="mb-3">
              <label className="form-label fw-semibold">{t('dataHolders.modal.tldsLabel')}</label>
              <small className="text-muted d-block mb-1">
                {t('dataHolders.modal.tldsHint')}
              </small>
              <div className="d-flex flex-wrap gap-1 mb-1">
                {form.tlds.map((tld, i) => (
                  <div key={i} className="input-group" style={{ width: '180px' }}>
                    <input
                      type="text"
                      className="form-control form-control-sm"
                      value={tld}
                      onChange={e => updateArrayItem('tlds', i, e.target.value)}
                      placeholder={t('dataHolders.modal.tldPlaceholder')}
                    />
                    {form.tlds.length > 1 && (
                      <button className="btn btn-outline-danger btn-sm" onClick={() => removeArrayItem('tlds', i)}>
                        <i className="bi bi-x"></i>
                      </button>
                    )}
                  </div>
                ))}
              </div>
              <button className="btn btn-outline-secondary btn-sm" onClick={() => addArrayItem('tlds')}>
                <i className="bi bi-plus me-1"></i>{t('dataHolders.modal.addTld')}
              </button>
            </div>

            {/* IP Ranges */}
            <div className="mb-3">
              <label className="form-label fw-semibold">{t('dataHolders.modal.ipRangesLabel')}</label>
              <small className="text-muted d-block mb-1">
                {t('dataHolders.modal.ipRangesHint')}
              </small>
              {form.ip_ranges.map((range, i) => (
                <div key={i} className="input-group mb-1">
                  <input
                    type="text"
                    className="form-control form-control-sm"
                    value={range}
                    onChange={e => updateArrayItem('ip_ranges', i, e.target.value)}
                    placeholder="192.0.2.0/24"
                  />
                  {form.ip_ranges.length > 1 && (
                    <button className="btn btn-outline-danger btn-sm" onClick={() => removeArrayItem('ip_ranges', i)}>
                      <i className="bi bi-x"></i>
                    </button>
                  )}
                </div>
              ))}
              <button className="btn btn-outline-secondary btn-sm" onClick={() => addArrayItem('ip_ranges')}>
                <i className="bi bi-plus me-1"></i>{t('dataHolders.modal.addIpRange')}
              </button>
            </div>

            {/* ASN Ranges */}
            <div className="mb-3">
              <label className="form-label fw-semibold">{t('dataHolders.modal.asnRangesLabel')}</label>
              <small className="text-muted d-block mb-1">
                {t('dataHolders.modal.asnRangesHint')}
              </small>
              {form.asn_ranges.map((pair, i) => (
                <div key={i} className="input-group mb-1">
                  <input
                    type="number"
                    className="form-control form-control-sm"
                    value={pair[0]}
                    onChange={e => updateAsnRange(i, 0, e.target.value)}
                    placeholder={t('dataHolders.modal.asnStart')}
                  />
                  <span className="input-group-text">–</span>
                  <input
                    type="number"
                    className="form-control form-control-sm"
                    value={pair[1]}
                    onChange={e => updateAsnRange(i, 1, e.target.value)}
                    placeholder={t('dataHolders.modal.asnEnd')}
                  />
                  {form.asn_ranges.length > 1 && (
                    <button className="btn btn-outline-danger btn-sm" onClick={() => removeAsnRange(i)}>
                      <i className="bi bi-x"></i>
                    </button>
                  )}
                </div>
              ))}
              <button className="btn btn-outline-secondary btn-sm" onClick={() => addAsnRange()}>
                <i className="bi bi-plus me-1"></i>{t('dataHolders.modal.addAsnRange')}
              </button>
            </div>

            <hr />

            {/* Authentication */}
            <div className="mb-3">
              <div className="form-check form-switch">
                <input
                  className="form-check-input"
                  type="checkbox"
                  id="requiresAuth"
                  checked={form.requires_auth}
                  onChange={e => {
                    updateField('requires_auth', e.target.checked);
                    if (e.target.checked) updateField('auth_type', 'bearer');
                    else updateField('auth_type', 'none');
                  }}
                />
                <label className="form-check-label fw-semibold" htmlFor="requiresAuth">
                  {t('dataHolders.modal.requiresAuth')}
                </label>
              </div>
              {form.requires_auth && (
                <div className="mt-2 ms-4">
                  <select
                    className="form-select form-select-sm w-auto"
                    value={form.auth_type}
                    onChange={e => updateField('auth_type', e.target.value)}
                  >
                    <option value="bearer">{t('dataHolders.modal.forwardBearerToken')}</option>
                  </select>
                  <small className="text-muted d-block mt-1">
                    {t('dataHolders.modal.bearerTokenHint')}
                  </small>
                </div>
              )}
            </div>

            {/* Active */}
            <div className="mb-3">
              <div className="form-check form-switch">
                <input
                  className="form-check-input"
                  type="checkbox"
                  id="isActive"
                  checked={form.is_active}
                  onChange={e => updateField('is_active', e.target.checked)}
                />
                <label className="form-check-label fw-semibold" htmlFor="isActive">
                  {t('dataHolders.modal.active')}
                </label>
              </div>
            </div>
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={onClose} disabled={saving}>
              {t('dataHolders.modal.cancel')}
            </button>
            <button className="btn btn-primary" onClick={handleSubmit} disabled={saving}>
              {saving ? (
                <>
                  <span className="spinner-border spinner-border-sm me-1"></span>
                  {t('dataHolders.modal.saving')}
                </>
              ) : (
                <>
                  <i className="bi bi-check-lg me-1"></i>
                  {editingHolder ? t('dataHolders.modal.update') : t('dataHolders.modal.create')}
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};


// ============================================================
// Resolution Test Panel
// ============================================================
const ResolutionTester = () => {
  const { t } = useT();
  const [query, setQuery] = useState('');
  const [queryType, setQueryType] = useState('domain');
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);

  const testResolve = async () => {
    if (!query.trim()) return;
    setLoading(true);
    setResult(null);
    try {
      const resp = await api.get('/api/admin/data-holders/iana/resolve-test', {
        params: { query: query.trim(), query_type: queryType },
      });
      setResult(resp.data);
    } catch (err) {
      setResult({ error: err.response?.data?.detail || err.message });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card mb-4">
      <div className="card-header bg-light">
        <h6 className="mb-0">
          <i className="bi bi-search me-2"></i>
          {t('dataHolders.resolutionTester.title')}
        </h6>
      </div>
      <div className="card-body">
        <p className="text-muted small mb-2">
          {t('dataHolders.resolutionTester.description')}
        </p>
        <div className="row g-2 align-items-end">
          <div className="col-auto">
            <select
              className="form-select form-select-sm"
              value={queryType}
              onChange={e => setQueryType(e.target.value)}
            >
              <option value="domain">{t('dataHolders.resolutionTester.optDomain')}</option>
              <option value="ip">{t('dataHolders.resolutionTester.optIp')}</option>
              <option value="asn">{t('dataHolders.resolutionTester.optAsn')}</option>
            </select>
          </div>
          <div className="col">
            <input
              type="text"
              className="form-control form-control-sm"
              placeholder={
                queryType === 'domain' ? 'example.com' :
                queryType === 'ip' ? '8.8.8.8' : 'AS15169'
              }
              value={query}
              onChange={e => setQuery(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && testResolve()}
            />
          </div>
          <div className="col-auto">
            <button
              className="btn btn-primary btn-sm"
              onClick={testResolve}
              disabled={loading || !query.trim()}
            >
              {loading ? <span className="spinner-border spinner-border-sm"></span> : t('dataHolders.resolutionTester.test')}
            </button>
          </div>
        </div>
        {result && (
          <div className="mt-3">
            {result.error ? (
              <div className="alert alert-danger py-2 small mb-0">{result.error}</div>
            ) : result.resolved ? (
              <div className="alert alert-success py-2 small mb-0">
                <strong>{t('dataHolders.resolutionTester.resolved')}</strong> {t('dataHolders.resolutionTester.source')} <code>{result.resolution?.source}</code>
                {result.resolution?.data_holder_name && (
                  <span> — {result.resolution.data_holder_name}</span>
                )}
                <br />
                {t('dataHolders.resolutionTester.urls')} {result.resolution?.base_urls?.map((u, i) => (
                  <code key={i} className="me-1">{u}</code>
                ))}
                {result.resolution?.requires_auth && (
                  <span className="badge bg-warning text-dark ms-2">{t('dataHolders.resolutionTester.authRequired')}</span>
                )}
              </div>
            ) : (
              <div className="alert alert-warning py-2 small mb-0">
                {t('dataHolders.resolutionTester.noServer')} <code>{result.query}</code>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};


// ============================================================
// Main Page Component
// ============================================================
const DataHolders = () => {
  const { t } = useT();
  const { user } = useAuth();
  const [holders, setHolders] = useState([]);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editingHolder, setEditingHolder] = useState(null);
  const [deleteConfirm, setDeleteConfirm] = useState(null);
  const [complianceHolder, setComplianceHolder] = useState(null);

  // Server-side pagination (1-based page)
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [totalItems, setTotalItems] = useState(0);

  const fetchHolders = useCallback(async () => {
    try {
      setLoading(true);
      const config = {
        params: { limit: pageSize, offset: (page - 1) * pageSize },
      };
      const [holdersRes, statsRes] = await Promise.all([
        api.get('/api/admin/data-holders', config),
        api.get('/api/admin/data-holders/stats'),
      ]);
      setHolders(holdersRes.data.data_holders || []);
      setTotalItems(holdersRes.data.total || 0);
      setStats(statsRes.data);
    } catch (err) {
      toast.error(t('dataHolders.toast.loadFailed', { error: err.response?.data?.detail || err.message }));
      console.error('Fetch data holders error:', err.response?.data || err);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => {
    fetchHolders();
  }, [fetchHolders]);

  const handleCreate = () => {
    setEditingHolder(null);
    setShowModal(true);
  };

  const handleEdit = (holder) => {
    setEditingHolder(holder);
    setShowModal(true);
  };

  const handleSave = async (payload) => {
    try {
      if (editingHolder) {
        await api.put(`/api/admin/data-holders/${editingHolder.id}`, payload);
        toast.success(t('dataHolders.toast.updated'));
      } else {
        await api.post('/api/admin/data-holders', payload);
        toast.success(t('dataHolders.toast.created'));
      }
      setShowModal(false);
      fetchHolders();
    } catch (err) {
      const msg = err.response?.data?.detail || t('dataHolders.toast.saveFailed');
      toast.error(typeof msg === 'string' ? msg : JSON.stringify(msg));
    }
  };

  const handleToggle = async (holder) => {
    try {
      await api.patch(`/api/admin/data-holders/${holder.id}/toggle`, {});
      toast.success(holder.is_active
        ? t('dataHolders.toast.deactivated', { name: holder.name })
        : t('dataHolders.toast.activated', { name: holder.name }));
      fetchHolders();
    } catch (err) {
      toast.error(t('dataHolders.toast.toggleFailed'));
    }
  };

  const handleDelete = async (holderId) => {
    try {
      await api.delete(`/api/admin/data-holders/${holderId}`);
      toast.success(t('dataHolders.toast.deleted'));
      setDeleteConfirm(null);
      fetchHolders();
    } catch (err) {
      toast.error(t('dataHolders.toast.deleteFailed'));
    }
  };

  const formatCoverage = (holder) => {
    const parts = [];
    if (holder.tlds?.length) parts.push(holder.tlds.length > 1
      ? t('dataHolders.coverage.tlds', { n: holder.tlds.length })
      : t('dataHolders.coverage.tld', { n: holder.tlds.length }));
    if (holder.ip_ranges?.length) parts.push(holder.ip_ranges.length > 1
      ? t('dataHolders.coverage.ipRanges', { n: holder.ip_ranges.length })
      : t('dataHolders.coverage.ipRange', { n: holder.ip_ranges.length }));
    if (holder.asn_ranges?.length) parts.push(holder.asn_ranges.length > 1
      ? t('dataHolders.coverage.asnRanges', { n: holder.asn_ranges.length })
      : t('dataHolders.coverage.asnRange', { n: holder.asn_ranges.length }));
    return parts.join(', ') || t('dataHolders.coverage.none');
  };

  return (
    <div>
      <Header />
      <div className="container-fluid px-4">
        {/* Page Header */}
        <div className="d-flex justify-content-between align-items-center mb-4">
          <div>
            <h2 className="mb-1">
              <i className="bi bi-database-gear me-2"></i>
              {t('dataHolders.page.title')}
            </h2>
            <p className="text-muted mb-0">
              {t('dataHolders.page.subtitle')}
            </p>
          </div>
          <button className="btn btn-primary" onClick={handleCreate}>
            <i className="bi bi-plus-lg me-1"></i>
            {t('dataHolders.page.addButton')}
          </button>
        </div>

        {/* Stats Cards */}
        {stats && (
          <div className="row g-3 mb-4">
            <div className="col-sm-6 col-md-3">
              <div className="card border-0 shadow-sm">
                <div className="card-body text-center">
                  <div className="fs-3 fw-bold text-primary">{stats.total}</div>
                  <div className="text-muted small">{t('dataHolders.stats.totalHolders')}</div>
                </div>
              </div>
            </div>
            <div className="col-sm-6 col-md-3">
              <div className="card border-0 shadow-sm">
                <div className="card-body text-center">
                  <div className="fs-3 fw-bold text-success">{stats.active_count}</div>
                  <div className="text-muted small">{t('dataHolders.stats.active')}</div>
                </div>
              </div>
            </div>
            <div className="col-sm-6 col-md-3">
              <div className="card border-0 shadow-sm">
                <div className="card-body text-center">
                  <div className="fs-3 fw-bold text-info">{stats.total_tlds}</div>
                  <div className="text-muted small">{t('dataHolders.stats.tldsCovered')}</div>
                </div>
              </div>
            </div>
            <div className="col-sm-6 col-md-3">
              <div className="card border-0 shadow-sm">
                <div className="card-body text-center">
                  <div className="fs-3 fw-bold text-secondary">
                    {stats.total_ip_ranges + stats.total_asn_ranges}
                  </div>
                  <div className="text-muted small">{t('dataHolders.stats.ipAsnRanges')}</div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Resolution Tester */}
        <ResolutionTester />

        {/* Data Holders Table */}
        <div className="card shadow-sm">
          <div className="card-body p-0">
            {loading ? (
              <div className="text-center py-5">
                <div className="spinner-border text-primary"></div>
                <div className="mt-2 text-muted">{t('dataHolders.table.loading')}</div>
              </div>
            ) : holders.length === 0 ? (
              <div className="text-center py-5">
                <i className="bi bi-database display-4 text-muted"></i>
                <p className="text-muted mt-2">{t('dataHolders.table.empty')}</p>
                <button className="btn btn-primary btn-sm" onClick={handleCreate}>
                  <i className="bi bi-plus me-1"></i>{t('dataHolders.table.addFirst')}
                </button>
              </div>
            ) : (
              <>
              <div className="table-responsive">
                <table className="table table-hover mb-0 align-middle">
                  <thead className="table-light">
                    <tr>
                      <th>{t('dataHolders.table.colName')}</th>
                      <th>{t('dataHolders.table.colBaseUrl')}</th>
                      <th>{t('dataHolders.table.colCoverage')}</th>
                      <th>{t('dataHolders.table.colAuth')}</th>
                      <th>{t('dataHolders.table.colStatus')}</th>
                      <th className="text-end">{t('dataHolders.table.colActions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {holders.map(holder => (
                      <tr key={holder.id} className={!holder.is_active ? 'table-secondary' : ''}>
                        <td>
                          <div className="fw-semibold">{holder.name}</div>
                          {holder.description && (
                            <small className="text-muted">{holder.description}</small>
                          )}
                        </td>
                        <td>
                          <code className="small">
                            {holder.base_urls?.[0]?.replace(/\/$/, '') || '–'}
                          </code>
                          {holder.base_urls?.length > 1 && (
                            <span className="badge bg-light text-dark ms-1">
                              +{holder.base_urls.length - 1}
                            </span>
                          )}
                        </td>
                        <td>
                          <small>{formatCoverage(holder)}</small>
                          {holder.tlds?.length > 0 && (
                            <div className="d-flex flex-wrap gap-1 mt-1">
                              {holder.tlds.slice(0, 5).map(tld => (
                                <span key={tld} className="badge bg-secondary bg-opacity-10 text-primary border border-primary border-opacity-25" style={{ fontSize: '0.75em' }}>
                                  .{tld}
                                </span>
                              ))}
                              {holder.tlds.length > 5 && (
                                <span className="badge bg-light text-muted">
                                  {t('dataHolders.table.moreTlds', { n: holder.tlds.length - 5 })}
                                </span>
                              )}
                            </div>
                          )}
                        </td>
                        <td>
                          {holder.requires_auth ? (
                            <span className="badge bg-warning text-dark">
                              <i className="bi bi-lock-fill me-1"></i>
                              {holder.auth_type}
                            </span>
                          ) : (
                            <span className="badge bg-light text-muted">{t('dataHolders.table.authNone')}</span>
                          )}
                        </td>
                        <td>
                          <div
                            className="form-check form-switch mb-0"
                            title={holder.is_active ? t('dataHolders.table.statusActiveTitle') : t('dataHolders.table.statusInactiveTitle')}
                          >
                            <input
                              className="form-check-input"
                              type="checkbox"
                              checked={holder.is_active}
                              onChange={() => handleToggle(holder)}
                            />
                          </div>
                        </td>
                        <td className="text-end">
                          <div className="btn-group btn-group-sm">
                            <button
                              className="btn btn-outline-info"
                              onClick={() => setComplianceHolder(holder)}
                              title={t('dataHolders.table.complianceTitle')}
                            >
                              <i className="bi bi-shield-check"></i>
                            </button>
                            <button
                              className="btn btn-outline-primary"
                              onClick={() => handleEdit(holder)}
                              title={t('dataHolders.table.editTitle')}
                            >
                              <i className="bi bi-pencil"></i>
                            </button>
                            <button
                              className="btn btn-outline-danger"
                              onClick={() => setDeleteConfirm(holder)}
                              title={t('dataHolders.table.deleteTitle')}
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
              <div className="px-3 pb-3">
                <Pagination
                  page={page}
                  pageSize={pageSize}
                  totalItems={totalItems}
                  onPageChange={setPage}
                  onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
                  itemLabel={t('dataHolders.table.itemLabel')}
                />
              </div>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Create/Edit Modal */}
      <DataHolderModal
        show={showModal}
        onClose={() => setShowModal(false)}
        onSave={handleSave}
        editingHolder={editingHolder}
      />

      {/* Delete Confirmation Modal */}
      {deleteConfirm && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-sm">
            <div className="modal-content">
              <div className="modal-header bg-danger text-white">
                <h6 className="modal-title">{t('dataHolders.delete.title')}</h6>
                <button className="btn-close btn-close-white" onClick={() => setDeleteConfirm(null)}></button>
              </div>
              <div className="modal-body">
                <p>
                  {t('dataHolders.delete.confirmPre')} <strong>{deleteConfirm.name}</strong>{t('dataHolders.delete.confirmPost')}
                </p>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary btn-sm" onClick={() => setDeleteConfirm(null)}>
                  {t('dataHolders.delete.cancel')}
                </button>
                <button className="btn btn-danger btn-sm" onClick={() => handleDelete(deleteConfirm.id)}>
                  <i className="bi bi-trash me-1"></i>{t('dataHolders.delete.confirm')}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* JAKE Compliance Modal */}
      {complianceHolder && (
        <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-xl modal-dialog-scrollable">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className="bi bi-shield-check me-2 text-info"></i>
                  {t('dataHolders.compliance.title', { name: complianceHolder.name })}
                </h5>
                <button className="btn-close" onClick={() => setComplianceHolder(null)}></button>
              </div>
              <div className="modal-body">
                <JakeCompliancePanel dataHolderId={complianceHolder.id} />
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DataHolders;