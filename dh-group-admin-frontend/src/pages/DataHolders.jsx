/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback, useContext } from 'react';
import { getDataHolders, createDataHolder, updateDataHolder, deleteDataHolder, revealCredentials, regenerateCredentials } from '../services/api';
import { GroupContext } from '../context/GroupContext';
import { useConfirm } from '../components/AlertModal';
import MultiGroupSelect from '../components/MultiGroupSelect';
import Pagination from '../components/Pagination';
import { useT } from '../i18n';
import toast from 'react-hot-toast';

const DataHolders = () => {
  const { t } = useT();
  const { selectedGroupId, isAllMode, groups } = useContext(GroupContext);
  const { confirm, ConfirmDialog } = useConfirm();

  const [holders, setHolders] = useState([]);
  const [loading, setLoading] = useState(true);
  // Server-side pagination state
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [totalItems, setTotalItems] = useState(0);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({ dataholderId: '', name: '', url: '', description: '', contactEmail: '', callbackUrl: '', dataHolderGroupIds: [] });

  const [credentialModal, setCredentialModal] = useState(null);
  const [secretVisible, setSecretVisible] = useState(false);
  const [revealing, setRevealing] = useState(null);
  const [regenerating, setRegenerating] = useState(null);
  const [newCredentials, setNewCredentials] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);

  // Debounce the search box (300ms) before hitting the server.
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(t);
  }, [search]);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      // Group scoping is applied server-side via dataHolderGroupId.
      const res = await getDataHolders({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        dataHolderGroupId: isAllMode ? undefined : selectedGroupId,
        sortBy: 'name',
        sortDir: 'asc',
      });
      setHolders(res.content || []);
      setTotalItems(res.totalElements || 0);
    } catch {
      toast.error(t('dataHolders.toast.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, debouncedSearch, isAllMode, selectedGroupId]);

  useEffect(() => { load(); }, [load]);

  // Reset to the first page when the search term or group scope changes.
  useEffect(() => { setPage(1); }, [debouncedSearch, isAllMode, selectedGroupId]);

  // Build a group name lookup
  const groupNameMap = {};
  groups.forEach(g => { groupNameMap[g.id] = g.name; });

  const openCreate = () => {
    setEditing(null);
    setForm({
      dataholderId: '', name: '', url: '', description: '', contactEmail: '', callbackUrl: '',
      dataHolderGroupIds: selectedGroupId ? [selectedGroupId] : (groups.length > 0 ? [groups[0].id] : []),
    });
    setNewCredentials(null);
    setShowForm(true);
  };

  const openEdit = (dh) => {
    setEditing(dh);
    // Prefer new multi-group list, fall back to legacy single field
    const gIds = (dh.dataHolderGroupIds && dh.dataHolderGroupIds.length > 0)
      ? dh.dataHolderGroupIds
      : (dh.dataHolderGroupId ? [dh.dataHolderGroupId] : []);
    setForm({
      dataholderId: dh.dataholderId,
      name: dh.name,
      url: dh.url || '',
      description: dh.description || '',
      contactEmail: dh.contactEmail || '',
      callbackUrl: dh.callbackUrl || '',
      dataHolderGroupIds: gIds,
    });
    setNewCredentials(null);
    setShowForm(true);
  };

  const handleSave = async () => {
    if (!form.name.trim() || (!editing && !form.dataholderId.trim())) {
      toast.error(t('dataHolders.validation.nameIdRequired'));
      return;
    }
    if (!form.dataHolderGroupIds || form.dataHolderGroupIds.length === 0) {
      toast.error(t('dataHolders.validation.groupRequired'));
      return;
    }
    setSubmitting(true);
    try {
      const payload = {
        ...form,
        dataHolderGroupIds: form.dataHolderGroupIds.map(id => parseInt(id)),
        // Legacy compat: set single field to the first selected group
        dataHolderGroupId: parseInt(form.dataHolderGroupIds[0]),
      };
      const res = editing ? await updateDataHolder(editing.id, payload) : await createDataHolder(payload);
      if (res.success) {
        toast.success(editing ? t('dataHolders.toast.updated') : t('dataHolders.toast.registered'));
        if (!editing && res.dataHolder?.clientId) {
          setNewCredentials({
            clientId: res.dataHolder.clientId,
            clientSecret: res.dataHolder.clientSecret,
          });
        } else {
          setShowForm(false);
        }
        load();
      } else {
        toast.error(res.error || t('common.failed'));
      }
    } catch (e) {
      toast.error(e.data?.error || e.message || t('common.saveFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleActive = async (dh) => {
    try {
      const res = await updateDataHolder(dh.id, { isActive: !dh.isActive });
      if (res.success) {
        toast.success(dh.isActive ? t('dataHolders.toast.disabled', { name: dh.name }) : t('dataHolders.toast.enabled', { name: dh.name }));
        load();
      }
    } catch {
      toast.error(t('dataHolders.toast.statusFailed'));
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setSubmitting(true);
    try {
      const res = await deleteDataHolder(deleteTarget.id);
      if (res.success) {
        toast.success(t('dataHolders.toast.deleted'));
        setDeleteTarget(null);
        load();
      } else {
        toast.error(res.error || t('common.deleteFailed'));
      }
    } catch (e) {
      toast.error(e.data?.error || e.message || t('common.deleteFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleReveal = async (dh) => {
    setRevealing(dh.id);
    try {
      const res = await revealCredentials(dh.id);
      if (res.success) {
        setCredentialModal(res);
        setSecretVisible(false);
      } else {
        toast.error(res.error || t('dataHolders.toast.noCredentials'));
      }
    } catch (e) {
      toast.error(e.data?.error || e.message || t('dataHolders.toast.revealFailed'));
    } finally {
      setRevealing(null);
    }
  };

  const handleRegenerate = async (dh) => {
    const ok = await confirm(
      t('dataHolders.regenerate.message', { name: dh.name }),
      t('dataHolders.regenerate.title'),
      { confirmLabel: t('dataHolders.regenerate.action'), confirmVariant: 'warning' }
    );
    if (!ok) return;
    setRegenerating(dh.id);
    try {
      const res = await regenerateCredentials(dh.id);
      if (res.success) {
        toast.success(t('dataHolders.toast.regenerated'));
        setCredentialModal({
          success: true,
          clientId: res.clientId,
          clientSecret: res.clientSecret,
          dataholderName: dh.name,
          dataholderId: dh.dataholderId,
        });
        setSecretVisible(true);
        load();
      } else {
        toast.error(res.error || t('common.failed'));
      }
    } catch (e) {
      toast.error(e.data?.error || e.message || t('dataHolders.toast.regenerateFailed'));
    } finally {
      setRegenerating(null);
    }
  };

  const copyToClipboard = (text, label) => {
    navigator.clipboard.writeText(text).then(
      () => toast.success(t('dataHolders.toast.copied', { label })),
      () => toast.error(t('dataHolders.toast.copyFailed'))
    );
  };

  if (loading && holders.length === 0) {
    return <div className="d-flex justify-content-center py-5"><div className="spinner-border text-primary" /></div>;
  }

  return (
    <div>
      {ConfirmDialog}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">{t('dataHolders.title')}</h2>
          <p className="text-muted mb-0">
            {t('dataHolders.count', { count: totalItems })}
            {isAllMode && <span className="badge bg-info ms-2" style={{ fontSize: 10 }}>{t('dataHolders.allGroups')}</span>}
          </p>
        </div>
        <div className="d-flex gap-2">
          <button className="btn btn-outline-secondary btn-sm" onClick={load}>
            <i className="fa-solid fa-arrows-rotate me-1"></i>{t('common.refresh')}
          </button>
          <button className="btn btn-primary" onClick={openCreate}>
            <i className="fa-solid fa-plus me-1"></i>{t('dataHolders.register')}
          </button>
        </div>
      </div>

      {/* Search */}
      <div className="mb-3" style={{ maxWidth: 360 }}>
        <div className="input-group input-group-sm">
          <span className="input-group-text"><i className="fa-solid fa-magnifying-glass"></i></span>
          <input
            className="form-control"
            placeholder={t('dataHolders.searchPlaceholder')}
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          {search && (
            <button className="btn btn-outline-secondary" onClick={() => setSearch('')} title={t('common.clear')}>
              <i className="fa-solid fa-xmark"></i>
            </button>
          )}
        </div>
      </div>

      <div className="card">
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th>{t('dataHolders.id')}</th>
                <th>{t('common.name')}</th>
                {isAllMode && <th>{t('dataHolders.groups')}</th>}
                <th>{t('dataHolders.url')}</th>
                <th>{t('dataHolders.contact')}</th>
                <th>{t('common.status')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {holders.length === 0 ? (
                <tr>
                  <td colSpan={isAllMode ? 7 : 6} className="text-center text-muted py-5">
                    <i className="fa-solid fa-database fa-2x mb-2"></i><br />{t('dataHolders.empty')}
                    {!isAllMode && selectedGroupId && <div className="small mt-1">{t('dataHolders.emptyInGroup')}</div>}
                  </td>
                </tr>
              ) : holders.map(dh => (
                <tr key={dh.id} className={!dh.isActive ? 'table-secondary' : ''}>
                  <td><code className="small">{dh.dataholderId}</code></td>
                  <td>
                    <strong>{dh.name}</strong>
                    {dh.description && (
                      <div className="text-muted small text-truncate" style={{ maxWidth: 200 }}>{dh.description}</div>
                    )}
                  </td>
                  {isAllMode && (
                    <td>
                      {(() => {
                        const gIds = dh.dataHolderGroupIds || (dh.dataHolderGroupId ? [dh.dataHolderGroupId] : []);
                        return gIds.length > 0 ? gIds.map(gid => (
                          <span key={gid} className="badge bg-light text-dark border me-1 mb-1">
                            {groupNameMap[gid] || '—'}
                          </span>
                        )) : <span className="text-muted">—</span>;
                      })()}
                    </td>
                  )}
                  <td className="small">
                    {dh.url ? <a href={dh.url} target="_blank" rel="noreferrer">{dh.url}</a> : '—'}
                  </td>
                  <td className="small">{dh.contactEmail || '—'}</td>
                  <td>
                    <button
                      className={`badge border-0 bg-${dh.isActive ? 'success' : 'secondary'}`}
                      onClick={() => handleToggleActive(dh)}
                      title={t('common.clickToToggle')}
                    >
                      {dh.isActive ? t('common.active') : t('common.disabled')}
                    </button>
                  </td>
                  <td>
                    <div className="btn-group btn-group-sm">
                      <button className="btn btn-outline-primary" onClick={() => openEdit(dh)} title={t('common.edit')}>
                        <i className="fa-solid fa-pen"></i>
                      </button>
                      <button
                        className="btn btn-outline-warning"
                        onClick={() => handleReveal(dh)}
                        title={t('dataHolders.viewCredentials')}
                        disabled={revealing === dh.id}
                      >
                        {revealing === dh.id
                          ? <span className="spinner-border spinner-border-sm"></span>
                          : <i className="fa-solid fa-key"></i>
                        }
                      </button>
                      <button
                        className="btn btn-outline-danger"
                        onClick={() => setDeleteTarget(dh)}
                        title={t('common.delete')}
                      >
                        <i className="fa-solid fa-trash"></i>
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {totalItems > 0 && (
            <Pagination
              page={page}
              pageSize={pageSize}
              totalItems={totalItems}
              onPageChange={setPage}
              onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
              itemLabel={t('dataHolders.itemLabel')}
            />
          )}
        </div>
      </div>

      {/* Create / Edit Modal */}
      {showForm && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-dialog-centered modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className={`fa-solid ${editing ? 'fa-pen' : 'fa-plus'} me-2`}></i>
                  {editing ? t('dataHolders.editTitle') : t('dataHolders.register')}
                </h5>
                <button type="button" className="btn-close" onClick={() => setShowForm(false)}></button>
              </div>
              <div className="modal-body">
                {newCredentials && (
                  <div className="alert alert-success mb-3">
                    <h6 className="alert-heading"><i className="fa-solid fa-key me-2"></i>{t('dataHolders.credentials.generated')}</h6>
                    <p className="small mb-2">{t('dataHolders.credentials.copyNow')}</p>
                    <div className="d-flex align-items-center gap-2 mb-2">
                      <strong className="small" style={{ width: 90 }}>{t('dataHolders.clientId')}:</strong>
                      <code className="flex-grow-1 user-select-all">{newCredentials.clientId}</code>
                      <button className="btn btn-outline-success btn-sm" onClick={() => copyToClipboard(newCredentials.clientId, t('dataHolders.clientId'))}>
                        <i className="fa-solid fa-copy"></i>
                      </button>
                    </div>
                    <div className="d-flex align-items-center gap-2">
                      <strong className="small" style={{ width: 90 }}>{t('dataHolders.secret')}:</strong>
                      <code className="flex-grow-1 user-select-all">{newCredentials.clientSecret}</code>
                      <button className="btn btn-outline-success btn-sm" onClick={() => copyToClipboard(newCredentials.clientSecret, t('dataHolders.clientSecret'))}>
                        <i className="fa-solid fa-copy"></i>
                      </button>
                    </div>
                  </div>
                )}
                {!newCredentials && (
                  <div className="row g-3">
                    <div className="col-md-6">
                      <label className="form-label">{t('dataHolders.form.id')}</label>
                      <input className="form-control" value={form.dataholderId}
                        onChange={e => setForm(p => ({ ...p, dataholderId: e.target.value }))} disabled={!!editing} />
                    </div>
                    <div className="col-md-6">
                      <label className="form-label">{t('dataHolders.form.name')}</label>
                      <input className="form-control" value={form.name}
                        onChange={e => setForm(p => ({ ...p, name: e.target.value }))} />
                    </div>
                    <div className="col-md-6">
                      <MultiGroupSelect
                        label={t('dataHolders.form.groups')}
                        required
                        groups={groups}
                        selectedIds={form.dataHolderGroupIds}
                        onChange={ids => setForm(p => ({ ...p, dataHolderGroupIds: ids }))}
                        helpText={t('dataHolders.form.groupsHelp')}
                      />
                    </div>
                    <div className="col-md-6">
                      <label className="form-label">{t('dataHolders.url')}</label>
                      <input className="form-control" value={form.url}
                        onChange={e => setForm(p => ({ ...p, url: e.target.value }))} placeholder="http://..." />
                    </div>
                    <div className="col-md-6">
                      <label className="form-label">{t('dataHolders.form.contactEmail')}</label>
                      <input className="form-control" type="email" value={form.contactEmail}
                        onChange={e => setForm(p => ({ ...p, contactEmail: e.target.value }))} />
                    </div>
                    <div className="col-md-6">
                      <label className="form-label">{t('dataHolders.form.callbackUrl')}</label>
                      <input className="form-control" value={form.callbackUrl}
                        onChange={e => setForm(p => ({ ...p, callbackUrl: e.target.value }))} />
                    </div>
                    <div className="col-12">
                      <label className="form-label">{t('common.description')}</label>
                      <textarea className="form-control" rows={2} value={form.description}
                        onChange={e => setForm(p => ({ ...p, description: e.target.value }))} />
                    </div>
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setShowForm(false)} disabled={submitting}>
                  {newCredentials ? t('common.done') : t('common.cancel')}
                </button>
                {!newCredentials && (
                  <button className="btn btn-primary" onClick={handleSave} disabled={submitting}>
                    {submitting ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('common.saving')}</> : editing ? t('common.update') : t('dataHolders.registerButton')}
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirm Modal */}
      {deleteTarget && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className="fa-solid fa-triangle-exclamation text-danger me-2"></i>{t('dataHolders.deleteTitle')}
                </h5>
                <button type="button" className="btn-close" onClick={() => setDeleteTarget(null)}></button>
              </div>
              <div className="modal-body">
                <p>{t('common.deleteConfirmPrefix')} <strong>{deleteTarget.name}</strong>?</p>
                <p className="text-muted small">
                  {t('dataHolders.id')}: <code>{deleteTarget.dataholderId}</code>
                </p>
                <div className="alert alert-warning small mb-0">
                  <i className="fa-solid fa-exclamation-triangle me-1"></i>
                  {t('dataHolders.deleteWarning')}
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setDeleteTarget(null)} disabled={submitting}>{t('common.cancel')}</button>
                <button className="btn btn-danger" onClick={handleDelete} disabled={submitting}>
                  {submitting ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('common.deleting')}</> : t('common.delete')}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Credential Reveal Modal */}
      {credentialModal && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className="fa-solid fa-key text-warning me-2"></i>
                  {t('dataHolders.credentials.title', { name: credentialModal.dataholderName || credentialModal.dataholderId })}
                </h5>
                <button type="button" className="btn-close" onClick={() => { setCredentialModal(null); setSecretVisible(false); }}></button>
              </div>
              <div className="modal-body">
                <div className="mb-3">
                  <label className="form-label fw-semibold small text-muted">{t('dataHolders.clientId')}</label>
                  <div className="input-group">
                    <input type="text" className="form-control font-monospace" value={credentialModal.clientId} readOnly />
                    <button className="btn btn-outline-secondary" onClick={() => copyToClipboard(credentialModal.clientId, t('dataHolders.clientId'))}>
                      <i className="fa-solid fa-copy"></i>
                    </button>
                  </div>
                </div>
                {credentialModal.clientSecret ? (
                  <div className="mb-3">
                    <label className="form-label fw-semibold small text-muted">{t('dataHolders.clientSecret')}</label>
                    <div className="input-group">
                      <input
                        type={secretVisible ? 'text' : 'password'}
                        className="form-control font-monospace"
                        value={credentialModal.clientSecret}
                        readOnly
                      />
                      <button className="btn btn-outline-secondary" onClick={() => setSecretVisible(!secretVisible)}
                        title={secretVisible ? t('dataHolders.hide') : t('dataHolders.reveal')}>
                        <i className={`fa-solid ${secretVisible ? 'fa-eye-slash' : 'fa-eye'}`}></i>
                      </button>
                      <button className="btn btn-outline-secondary" onClick={() => copyToClipboard(credentialModal.clientSecret, t('dataHolders.clientSecret'))}>
                        <i className="fa-solid fa-copy"></i>
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="mb-3">
                    <label className="form-label fw-semibold small text-muted">{t('dataHolders.clientSecret')}</label>
                    <div className="alert alert-warning small mb-0">
                      <i className="fa-solid fa-lock me-1"></i>
                      {credentialModal.message || t('dataHolders.credentials.secretHidden')}
                    </div>
                  </div>
                )}
                <div className="alert alert-info small mb-0">
                  <i className="fa-solid fa-info-circle me-1"></i>
                  {t('dataHolders.credentials.info')}
                </div>
              </div>
              <div className="modal-footer d-flex justify-content-between">
                <button
                  className="btn btn-outline-danger btn-sm"
                  onClick={() => {
                    const dh = holders.find(h => h.dataholderId === credentialModal.dataholderId);
                    if (dh) { setCredentialModal(null); setSecretVisible(false); handleRegenerate(dh); }
                  }}
                  disabled={regenerating != null}
                >
                  <i className="fa-solid fa-rotate me-1"></i>{t('dataHolders.regenerate.action')}
                </button>
                <button className="btn btn-secondary" onClick={() => { setCredentialModal(null); setSecretVisible(false); }}>{t('common.close')}</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DataHolders;
