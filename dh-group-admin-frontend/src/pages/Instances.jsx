/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback, useContext } from 'react';
import { getInstances, provisionInstance, stopInstance, startInstance, teardownInstance, updateInstance, updateAllInstances, getInstanceLiveStatus, getDataHolderGroups } from '../services/api';
import { GroupContext } from '../context/GroupContext';
import { useConfirm } from '../components/AlertModal';
import MultiGroupSelect from '../components/MultiGroupSelect';
import Pagination from '../components/Pagination';
import { useT } from '../i18n';
import toast from 'react-hot-toast';

const STATUS_COLORS = { running: 'success', stopped: 'warning', destroyed: 'secondary' };
const STATUS_ICONS = { running: 'fa-circle-check', stopped: 'fa-circle-pause', destroyed: 'fa-circle-xmark' };
const PARENT_DOMAIN =
  (typeof process !== 'undefined' && process.env?.REACT_APP_PARENT_DOMAIN) || 'localhost';

const Instances = () => {
  const { selectedGroupId, isAllMode, groups } = useContext(GroupContext);
  const { confirm, ConfirmDialog } = useConfirm();
  const { t } = useT();

  const [instances, setInstances] = useState([]);
  const [loading, setLoading] = useState(true);
  // Server-side pagination state
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [totalItems, setTotalItems] = useState(0);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [actionLoading, setActionLoading] = useState(null);
  const [allGroups, setAllGroups] = useState([]);

  const [form, setForm] = useState({
    name: '', subdomain: '', dataholderId: '', dataHolderGroupIds: [], adminPassword: '',
  });
  const [formErrors, setFormErrors] = useState({});

  // Detail panel
  const [detailInstance, setDetailInstance] = useState(null);
  const [liveStatus, setLiveStatus] = useState(null);

  const statusLabel = (s) => (STATUS_COLORS[s] ? t(`instances.status.${s}`) : s);

  // Debounce the search box (300ms) before hitting the server.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      // Group scoping is applied server-side via dataHolderGroupId.
      const [res, grps] = await Promise.all([
        getInstances({
          page: page - 1,
          size: pageSize,
          search: debouncedSearch || undefined,
          dataHolderGroupId: isAllMode ? undefined : selectedGroupId,
        }),
        getDataHolderGroups(),
      ]);
      setInstances(res.content || []);
      setTotalItems(res.totalElements || 0);
      setAllGroups(grps || []);
    } catch {
      toast.error(t('instances.toast.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, debouncedSearch, isAllMode, selectedGroupId]);

  useEffect(() => { load(); }, [load]);

  // Reset to the first page when the search term or group scope changes.
  useEffect(() => { setPage(1); }, [debouncedSearch, isAllMode, selectedGroupId]);

  const groupNameMap = {};
  (allGroups.length > 0 ? allGroups : groups).forEach(g => { groupNameMap[g.id] = g.name; });

  // ─── Form ──────────────────────────────────────────────
  const openCreate = () => {
    setForm({
      name: '', subdomain: '', dataholderId: '', adminPassword: '',
      dataHolderGroupIds: selectedGroupId ? [selectedGroupId] : (groups.length > 0 ? [groups[0].id] : []),
    });
    setFormErrors({});
    setShowForm(true);
  };

  const autoSubdomain = (name) => {
    return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').substring(0, 30);
  };

  const handleNameChange = (val) => {
    setForm(prev => ({
      ...prev,
      name: val,
      subdomain: prev.subdomain || autoSubdomain(val),
    }));
  };

  const validateForm = () => {
    const errs = {};
    if (!form.name.trim()) errs.name = t('common.required');
    if (!form.subdomain.trim()) errs.subdomain = t('common.required');
    else if (!/^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$/.test(form.subdomain))
      errs.subdomain = t('instances.form.subdomainInvalid');
    if (!form.dataHolderGroupIds || form.dataHolderGroupIds.length === 0) errs.dataHolderGroupIds = t('instances.form.groupsRequired');
    setFormErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleCreate = async () => {
    if (!validateForm()) return;
    setSubmitting(true);
    try {
      const res = await provisionInstance({
        ...form,
        dataHolderGroupIds: form.dataHolderGroupIds.map(id => parseInt(id)),
        dataHolderGroupId: parseInt(form.dataHolderGroupIds[0]),
      });
      if (res.success) {
        toast.success(t('instances.toast.created', { name: form.name, url: res.instance?.url }));
        setShowForm(false);
        load();
      } else {
        toast.error(res.error || t('instances.toast.provisioningFailed'));
      }
    } catch (e) {
      toast.error(e.message || t('instances.toast.provisioningFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  // ─── Actions ───────────────────────────────────────────
  const doAction = async (subdomain, action, label, confirmMsg) => {
    if (confirmMsg) {
      const yes = await confirm(confirmMsg);
      if (!yes) return;
    }
    setActionLoading(`${subdomain}-${action}`);
    try {
      let res;
      if (action === 'stop') res = await stopInstance(subdomain);
      else if (action === 'start') res = await startInstance(subdomain);
      else if (action === 'teardown') res = await teardownInstance(subdomain);
      else if (action === 'update') res = await updateInstance(subdomain, {});

      if (res?.success !== false) {
        toast.success(t('instances.toast.actionSuccess', { label }));
        load();
      } else {
        toast.error(res?.error || t('instances.toast.actionFailed', { label }));
      }
    } catch (e) {
      toast.error(e.message || t('instances.toast.actionFailed', { label }));
    } finally {
      setActionLoading(null);
    }
  };

  const handleUpdateAll = async () => {
    const yes = await confirm(t('instances.confirm.updateAll'));
    if (!yes) return;
    setActionLoading('update-all');
    try {
      const res = await updateAllInstances({});
      if (res.success) {
        const ok = (res.results || []).filter(r => r.success).length;
        const fail = (res.results || []).filter(r => !r.success).length;
        toast.success(t('instances.toast.updatedCount', { count: ok }) + (fail > 0 ? t('instances.toast.updatedFailed', { count: fail }) : ''));
        load();
      }
    } catch (e) {
      toast.error(e.message || t('instances.toast.batchUpdateFailed'));
    } finally {
      setActionLoading(null);
    }
  };

  // ─── Detail Panel ──────────────────────────────────────
  const openDetail = async (inst) => {
    setDetailInstance(inst);
    setLiveStatus(null);
    try {
      const status = await getInstanceLiveStatus(inst.subdomain);
      setLiveStatus(status);
    } catch {
      setLiveStatus({ error: t('instances.detail.liveStatusError') });
    }
  };

  // ─── Render ────────────────────────────────────────────
  if (loading && instances.length === 0) {
    return (
      <div className="text-center py-5">
        <div className="spinner-border text-primary" />
        <p className="mt-2 text-muted">{t('instances.loading')}</p>
      </div>
    );
  }

  const runningOnPage = instances.filter(i => i.status === 'running').length;

  return (
    <div>
      {ConfirmDialog}

      {/* Header */}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h4 className="mb-1"><i className="fa-solid fa-server me-2" />{t('instances.title')}</h4>
          <span className="text-muted small">
            {t('instances.header.summary', {
              count: totalItems,
              unit: totalItems !== 1 ? t('instances.header.unitPlural') : t('instances.header.unitSingular'),
              running: runningOnPage,
            })}
          </span>
        </div>
        <div className="d-flex gap-2">
          <div className="input-group input-group-sm" style={{ maxWidth: 260 }}>
            <span className="input-group-text"><i className="fa-solid fa-magnifying-glass"></i></span>
            <input
              className="form-control"
              placeholder={t('instances.searchPlaceholder')}
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
            {search && (
              <button className="btn btn-outline-secondary" onClick={() => setSearch('')} title={t('common.clear')}>
                <i className="fa-solid fa-xmark"></i>
              </button>
            )}
          </div>
          {runningOnPage > 0 && (
            <button className="btn btn-outline-primary btn-sm" onClick={handleUpdateAll}
              disabled={actionLoading === 'update-all'}>
              <i className="fa-solid fa-arrows-rotate me-1" />
              {actionLoading === 'update-all' ? t('instances.updatingAll') : t('instances.updateAll')}
            </button>
          )}
          <button className="btn btn-primary btn-sm" onClick={openCreate}>
            <i className="fa-solid fa-plus me-1" /> {t('instances.newInstance')}
          </button>
        </div>
      </div>

      {/* Instance Cards */}
      {instances.length === 0 ? (
        <div className="card">
          <div className="card-body text-center py-5 text-muted">
            <i className="fa-solid fa-server fa-3x mb-3 opacity-25" />
            <p>{t('instances.emptyState')}</p>
            <button className="btn btn-primary btn-sm" onClick={openCreate}>
              <i className="fa-solid fa-plus me-1" /> {t('instances.createFirst')}
            </button>
          </div>
        </div>
      ) : (
        <div className="row g-3">
          {instances.map(inst => (
            <div key={inst.id} className="col-md-6 col-xl-4">
              <div className="card h-100">
                <div className="card-body">
                  <div className="d-flex justify-content-between align-items-start mb-2">
                    <div>
                      <h6 className="mb-0">{inst.name}</h6>
                      <a href={inst.url} target="_blank" rel="noreferrer" className="small text-primary text-decoration-none">
                        {inst.subdomain}.{PARENT_DOMAIN} <i className="fa-solid fa-arrow-up-right-from-square ms-1" style={{ fontSize: 10 }} />
                      </a>
                    </div>
                    <span className={`badge bg-${STATUS_COLORS[inst.status] || 'secondary'}`}>
                      <i className={`fa-solid ${STATUS_ICONS[inst.status] || 'fa-circle'} me-1`} />
                      {statusLabel(inst.status)}
                    </span>
                  </div>
                  {(() => {
                    const gIds = inst.dataHolderGroupIds || (inst.dataHolderGroupId ? [inst.dataHolderGroupId] : []);
                    return gIds.length > 0 && (
                      <div className="small text-muted mb-2">
                        <i className="fa-solid fa-layer-group me-1" />
                        {gIds.map(gid => groupNameMap[gid]).filter(Boolean).join(', ') || '—'}
                      </div>
                    );
                  })()}
                  <div className="small text-muted mb-3">
                    <span className="me-3"><i className="fa-solid fa-database me-1" />{inst.databaseName}</span>
                    <span><i className="fa-solid fa-network-wired me-1" />:{inst.backendPort} / :{inst.frontendPort}</span>
                  </div>
                  <div className="d-flex gap-1 flex-wrap">
                    <button className="btn btn-outline-secondary btn-sm" onClick={() => openDetail(inst)} title={t('instances.card.detailsTitle')}>
                      <i className="fa-solid fa-info-circle" />
                    </button>
                    {inst.status === 'running' && (
                      <>
                        <button className="btn btn-outline-warning btn-sm"
                          disabled={actionLoading === `${inst.subdomain}-stop`}
                          onClick={() => doAction(inst.subdomain, 'stop', t('instances.actions.stop'), t('instances.confirm.stop', { name: inst.name }))}>
                          <i className="fa-solid fa-stop me-1" />{t('instances.actions.stop')}
                        </button>
                        <button className="btn btn-outline-primary btn-sm"
                          disabled={actionLoading === `${inst.subdomain}-update`}
                          onClick={() => doAction(inst.subdomain, 'update', t('common.update'), t('instances.confirm.update', { name: inst.name }))}>
                          <i className="fa-solid fa-arrows-rotate me-1" />{t('common.update')}
                        </button>
                      </>
                    )}
                    {inst.status === 'stopped' && (
                      <button className="btn btn-outline-success btn-sm"
                        disabled={actionLoading === `${inst.subdomain}-start`}
                        onClick={() => doAction(inst.subdomain, 'start', t('instances.actions.start'))}>
                        <i className="fa-solid fa-play me-1" />{t('instances.actions.start')}
                      </button>
                    )}
                    {inst.status !== 'destroyed' && (
                      <button className="btn btn-outline-danger btn-sm"
                        disabled={actionLoading === `${inst.subdomain}-teardown`}
                        onClick={() => doAction(inst.subdomain, 'teardown', t('instances.actions.teardown'),
                          t('instances.confirm.teardown', { name: inst.name }))}>
                        <i className="fa-solid fa-trash me-1" />{t('instances.actions.teardown')}
                      </button>
                    )}
                  </div>
                </div>
                <div className="card-footer bg-transparent small text-muted">
                  {t('instances.card.created', { date: inst.createdAt ? new Date(inst.createdAt).toLocaleDateString() : '—' })}
                </div>
              </div>
            </div>
          ))}
          <div className="col-12">
            <Pagination
              page={page}
              pageSize={pageSize}
              totalItems={totalItems}
              onPageChange={setPage}
              onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
              itemLabel={t('instances.itemLabel')}
            />
          </div>
        </div>
      )}

      {/* Create Instance Modal */}
      {showForm && (
        <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title"><i className="fa-solid fa-plus me-2" />{t('instances.form.title')}</h5>
                <button className="btn-close" onClick={() => setShowForm(false)} />
              </div>
              <div className="modal-body">
                <div className="row g-3">
                  <div className="col-md-6">
                    <label className="form-label">{t('instances.form.nameLabel')} <span className="text-danger">*</span></label>
                    <input className={`form-control ${formErrors.name ? 'is-invalid' : ''}`}
                      value={form.name} onChange={e => handleNameChange(e.target.value)} placeholder={t('instances.form.namePlaceholder')} />
                    {formErrors.name && <div className="invalid-feedback">{formErrors.name}</div>}
                  </div>
                  <div className="col-md-6">
                    <label className="form-label">{t('instances.form.subdomainLabel')} <span className="text-danger">*</span></label>
                    <div className="input-group">
                      <input className={`form-control ${formErrors.subdomain ? 'is-invalid' : ''}`}
                        value={form.subdomain} onChange={e => setForm(p => ({ ...p, subdomain: e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, '') }))}
                        placeholder={t('instances.form.subdomainPlaceholder')} />
                      <span className="input-group-text">.{PARENT_DOMAIN}</span>
                      {formErrors.subdomain && <div className="invalid-feedback">{formErrors.subdomain}</div>}
                    </div>
                    <div className="form-text">{t('instances.form.subdomainHint', { url: `https://${form.subdomain || '___'}.${PARENT_DOMAIN}` })}</div>
                  </div>
                  <div className="col-md-6">
                    <MultiGroupSelect
                      label={t('instances.form.groupsLabel')}
                      required
                      groups={groups}
                      selectedIds={form.dataHolderGroupIds}
                      onChange={ids => setForm(p => ({ ...p, dataHolderGroupIds: ids }))}
                      isInvalid={!!formErrors.dataHolderGroupIds}
                      errorMessage={formErrors.dataHolderGroupIds}
                    />
                  </div>
                  <div className="col-md-6">
                    <label className="form-label">{t('instances.form.dataHolderIdLabel')}</label>
                    <input className="form-control" value={form.dataholderId}
                      onChange={e => setForm(p => ({ ...p, dataholderId: e.target.value }))}
                      placeholder={t('instances.form.autoGeneratePlaceholder')} />
                    <div className="form-text">{t('instances.form.dataHolderIdHint')}</div>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label">{t('instances.form.adminPasswordLabel')}</label>
                    <input className="form-control" value={form.adminPassword} type="password"
                      onChange={e => setForm(p => ({ ...p, adminPassword: e.target.value }))}
                      placeholder={t('instances.form.autoGeneratePlaceholder')} />
                    <div className="form-text">{t('instances.form.adminPasswordHint')}</div>
                  </div>
                </div>
                <div className="alert alert-info mt-3 small">
                  <i className="fa-solid fa-info-circle me-2" />
                  {t('instances.form.provisioningInfo')}
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setShowForm(false)} disabled={submitting}>{t('common.cancel')}</button>
                <button className="btn btn-primary" onClick={handleCreate} disabled={submitting}>
                  {submitting ? (
                    <><span className="spinner-border spinner-border-sm me-1" /> {t('instances.form.provisioning')}</>
                  ) : (
                    <><i className="fa-solid fa-rocket me-1" /> {t('instances.form.createInstance')}</>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Detail Modal */}
      {detailInstance && (
        <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
          <div className="modal-dialog modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className="fa-solid fa-server me-2" />{detailInstance.name}
                  <span className={`badge bg-${STATUS_COLORS[detailInstance.status]} ms-2`} style={{ fontSize: 11 }}>
                    {statusLabel(detailInstance.status)}
                  </span>
                </h5>
                <button className="btn-close" onClick={() => setDetailInstance(null)} />
              </div>
              <div className="modal-body">
                <table className="table table-sm">
                  <tbody>
                    <tr><td className="text-muted" style={{ width: '35%' }}>{t('instances.detail.url')}</td>
                      <td><a href={detailInstance.url} target="_blank" rel="noreferrer">{detailInstance.url}</a></td></tr>
                    <tr><td className="text-muted">{t('instances.detail.subdomain')}</td><td><code>{detailInstance.subdomain}</code></td></tr>
                    <tr><td className="text-muted">{t('instances.detail.dataHolderId')}</td><td>{detailInstance.dataholderId || '—'}</td></tr>
                    <tr><td className="text-muted">{t('instances.detail.groups')}</td><td>{
                      (() => {
                        const gIds = detailInstance.dataHolderGroupIds || (detailInstance.dataHolderGroupId ? [detailInstance.dataHolderGroupId] : []);
                        return gIds.length > 0
                          ? gIds.map(gid => <span key={gid} className="badge bg-light text-dark border me-1">{groupNameMap[gid] || `#${gid}`}</span>)
                          : '—';
                      })()
                    }</td></tr>
                    <tr><td className="text-muted">{t('instances.detail.database')}</td><td><code>{detailInstance.databaseName}</code></td></tr>
                    <tr><td className="text-muted">{t('instances.detail.backendPort')}</td><td>{detailInstance.backendPort}</td></tr>
                    <tr><td className="text-muted">{t('instances.detail.frontendPort')}</td><td>{detailInstance.frontendPort}</td></tr>
                    <tr><td className="text-muted">{t('instances.detail.backendContainer')}</td><td><code>{detailInstance.backendContainer}</code></td></tr>
                    <tr><td className="text-muted">{t('instances.detail.frontendContainer')}</td><td><code>{detailInstance.frontendContainer}</code></td></tr>
                    <tr><td className="text-muted">{t('instances.detail.adminUsername')}</td><td><code>{detailInstance.adminUsername}</code></td></tr>
                    <tr><td className="text-muted">{t('instances.detail.adminPassword')}</td>
                      <td>
                        <code>{detailInstance._showPw ? detailInstance.adminPassword : '••••••••'}</code>
                        <button className="btn btn-link btn-sm p-0 ms-2" style={{ fontSize: 11 }}
                          onClick={() => setDetailInstance(prev => ({ ...prev, _showPw: !prev._showPw }))}>
                          {detailInstance._showPw ? t('instances.detail.hide') : t('instances.detail.show')}
                        </button>
                      </td>
                    </tr>
                    <tr><td className="text-muted">{t('instances.detail.backendImage')}</td><td><code>{detailInstance.backendImage || '—'}</code></td></tr>
                    <tr><td className="text-muted">{t('instances.detail.frontendImage')}</td><td><code>{detailInstance.frontendImage || '—'}</code></td></tr>
                    <tr><td className="text-muted">{t('instances.detail.created')}</td><td>{detailInstance.createdAt ? new Date(detailInstance.createdAt).toLocaleString() : '—'}</td></tr>
                  </tbody>
                </table>

                {liveStatus && (
                  <div className="mt-3">
                    <h6 className="text-muted small text-uppercase">{t('instances.detail.liveStatusHeading')}</h6>
                    {liveStatus.error ? (
                      <div className="text-danger small">{liveStatus.error}</div>
                    ) : liveStatus.containers ? (
                      <div className="row g-2">
                        {Object.entries(liveStatus.containers).map(([role, info]) => (
                          <div key={role} className="col-md-6">
                            <div className="border rounded p-2 small">
                              <strong>{role}</strong>
                              <span className={`badge bg-${info.status === 'running' ? 'success' : info.status === 'exited' ? 'danger' : 'secondary'} ms-2`}>
                                {info.status}
                              </span>
                              {info.image && <div className="text-muted mt-1">{t('instances.detail.image', { image: info.image })}</div>}
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : null}
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setDetailInstance(null)}>{t('common.close')}</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Instances;
