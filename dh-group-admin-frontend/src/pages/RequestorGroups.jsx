/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback, useContext } from 'react';
import { getRequestorGroups, acceptRequestorGroup, denyRequestorGroup, updateRequestorGroup, deleteRequestorGroup, revealRgCredentials, regenerateRgCredentials } from '../services/api';
import { GroupContext } from '../context/GroupContext';
import { useConfirm } from '../components/AlertModal';
import Pagination from '../components/Pagination';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const STATUS_CONFIG = {
  PENDING: { color: 'warning', label: 'Pending' },
  ACCEPTED: { color: 'success', label: 'Accepted' },
  DENIED: { color: 'danger', label: 'Denied' },
  SUSPENDED: { color: 'secondary', label: 'Suspended' },
};

const RequestorGroups = () => {
  const { selectedGroupId, isAllMode, groups: dhGroups } = useContext(GroupContext);
  const { confirm, ConfirmDialog } = useConfirm();
  const { t } = useT();
  const [allGroups, setAllGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('ALL');
  // Server-side pagination state
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [totalItems, setTotalItems] = useState(0);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [detailModal, setDetailModal] = useState(null);
  const [actionModal, setActionModal] = useState(null);
  const [notes, setNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [credentialModal, setCredentialModal] = useState(null);
  const [secretVisible, setSecretVisible] = useState(false);
  const [revealing, setRevealing] = useState(null);

  // Debounce the search box (300ms) before hitting the server.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      // DH-group scoping + status tab are applied server-side.
      const res = await getRequestorGroups({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        dataHolderGroupId: isAllMode ? undefined : selectedGroupId,
        status: filter === 'ALL' ? undefined : filter,
        sortBy: 'createdAt',
        sortDir: 'desc',
      });
      setAllGroups(res.content || []);
      setTotalItems(res.totalElements || 0);
    } catch {
      toast.error(t('requestorGroups.toast.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, debouncedSearch, filter, isAllMode, selectedGroupId]);

  useEffect(() => { load(); }, [load]);

  // Reset to the first page when a filter or the search term changes.
  useEffect(() => { setPage(1); }, [debouncedSearch, filter, isAllMode, selectedGroupId]);

  const handleAction = async () => {
    if (!actionModal) return;
    setSubmitting(true);
    try {
      const payload = { reviewedBy: 'admin', notes };
      let res;
      if (actionModal.action === 'accept') {
        res = await acceptRequestorGroup(actionModal.group.id, payload);
        if (res.success && res.clientId) {
          setCredentialModal({
            success: true,
            clientId: res.clientId,
            clientSecret: res.clientSecret,
            requestorGroupName: actionModal.group.name,
            requestorGroupCode: actionModal.group.code,
          });
          setSecretVisible(true);
        }
      } else {
        res = await denyRequestorGroup(actionModal.group.id, payload);
      }
      if (res.success) {
        toast.success(actionModal.action === 'accept' ? t('requestorGroups.toast.accepted') : t('requestorGroups.toast.denied'));
        setActionModal(null);
        setNotes('');
        load();
      }
    } catch (e) {
      toast.error(e.data?.error || e.message || t('requestorGroups.toast.actionFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleActive = async (rg) => {
    try {
      const res = await updateRequestorGroup(rg.id, { isActive: !rg.isActive });
      if (res.success) {
        toast.success(rg.isActive
          ? t('requestorGroups.toast.disabled', { name: rg.name })
          : t('requestorGroups.toast.enabled', { name: rg.name }));
        load();
      }
    } catch {
      toast.error(t('requestorGroups.toast.statusUpdateFailed'));
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setSubmitting(true);
    try {
      const res = await deleteRequestorGroup(deleteTarget.id);
      if (res.success) {
        toast.success(t('requestorGroups.toast.deleted'));
        setDeleteTarget(null);
        load();
      }
    } catch (e) {
      toast.error(e.data?.error || t('requestorGroups.toast.deleteFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleReveal = async (rg) => {
    setRevealing(rg.id);
    try {
      const res = await revealRgCredentials(rg.id);
      if (res.success) {
        setCredentialModal(res);
        setSecretVisible(false);
      } else {
        toast.error(res.error || t('requestorGroups.toast.noCredentials'));
      }
    } catch (e) {
      toast.error(e.data?.error || t('requestorGroups.toast.revealFailed'));
    } finally {
      setRevealing(null);
    }
  };

  const handleRegenerate = async (rg) => {
    const ok = await confirm(
      t('requestorGroups.regenerateConfirm.body', { name: rg.name }),
      t('requestorGroups.regenerateConfirm.title'),
      { confirmLabel: t('requestorGroups.regenerate'), confirmVariant: 'warning' }
    );
    if (!ok) return;
    try {
      const res = await regenerateRgCredentials(rg.id);
      if (res.success) {
        toast.success(t('requestorGroups.toast.regenerated'));
        setCredentialModal({ success: true, clientId: res.clientId, clientSecret: res.clientSecret, requestorGroupName: rg.name, requestorGroupCode: rg.code });
        setSecretVisible(true);
        load();
      }
    } catch (e) {
      toast.error(e.data?.error || t('requestorGroups.toast.regenerateFailed'));
    }
  };

  const copyToClipboard = (text, label) => {
    navigator.clipboard.writeText(text).then(() => toast.success(t('requestorGroups.toast.copied', { label })), () => toast.error(t('requestorGroups.toast.copyFailed')));
  };

  if (loading && allGroups.length === 0) return <div className="d-flex justify-content-center py-5"><div className="spinner-border text-primary" /></div>;

  return (
    <div>
      {ConfirmDialog}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">{t('requestorGroups.title')}</h2>
          <p className="text-muted mb-0">{t('requestorGroups.registeredCount', { count: totalItems })}</p>
        </div>
        <button className="btn btn-outline-secondary btn-sm" onClick={load}>
          <i className="fa-solid fa-arrows-rotate me-1"></i>{t('common.refresh')}
        </button>
      </div>

      {/* Filter + search */}
      <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
        <div className="d-flex gap-2 flex-wrap">
          {['ALL', 'PENDING', 'ACCEPTED', 'DENIED', 'SUSPENDED'].map(f => (
            <button key={f} className={`btn btn-sm ${filter === f ? 'btn-primary' : 'btn-outline-secondary'}`} onClick={() => setFilter(f)}>
              {f === 'ALL' ? t('common.all') : t(`requestorGroups.status.${f}`)}
            </button>
          ))}
        </div>
        <div className="input-group input-group-sm" style={{ maxWidth: 320 }}>
          <span className="input-group-text"><i className="fa-solid fa-magnifying-glass"></i></span>
          <input
            className="form-control"
            placeholder={t('requestorGroups.searchPlaceholder')}
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

      {/* Table */}
      <div className="card">
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th>{t('requestorGroups.table.code')}</th>
                <th>{t('common.name')}</th>
                {isAllMode && <th>{t('requestorGroups.table.dhGroup')}</th>}
                <th>{t('common.type')}</th>
                <th>{t('requestorGroups.table.contact')}</th>
                <th>{t('common.status')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {allGroups.length === 0 ? (
                <tr><td colSpan={isAllMode ? 7 : 6} className="text-center text-muted py-5"><i className="fa-solid fa-users fa-2x mb-2"></i><br />{t('requestorGroups.empty')}</td></tr>
              ) : allGroups.map(rg => {
                const sc = STATUS_CONFIG[rg.status] || { color: 'secondary', label: rg.status };
                return (
                  <tr key={rg.id} className={!rg.isActive && rg.status === 'ACCEPTED' ? 'table-secondary' : ''}>
                    <td><code className="small">{rg.code}</code></td>
                    <td>
                      <strong>{rg.name}</strong>
                      {rg.organization && <div className="text-muted small">{rg.organization}</div>}
                    </td>
                    {isAllMode && (
                      <td>
                        <span className="badge bg-light text-dark border">
                          {rg.dataHolderGroupId ? (dhGroups.find(g => g.id === rg.dataHolderGroupId)?.name || '—') : <em className="text-muted">{t('common.none')}</em>}
                        </span>
                      </td>
                    )}
                    <td className="small">{rg.groupType || '—'}</td>
                    <td className="small">{rg.contactEmail || '—'}</td>
                    <td>
                      {rg.status === 'ACCEPTED' ? (
                        <button className={`badge border-0 bg-${rg.isActive ? 'success' : 'secondary'}`}
                          onClick={() => handleToggleActive(rg)} title={t('requestorGroups.clickToToggle')}>
                          {rg.isActive ? t('common.active') : t('common.disabled')}
                        </button>
                      ) : (
                        <span className={`badge bg-${sc.color}`}>{STATUS_CONFIG[rg.status] ? t(`requestorGroups.status.${rg.status}`) : rg.status}</span>
                      )}
                    </td>
                    <td>
                      <div className="btn-group btn-group-sm">
                        <button className="btn btn-outline-info" onClick={() => setDetailModal(rg)} title={t('requestorGroups.viewDetails')}>
                          <i className="fa-solid fa-eye"></i>
                        </button>
                        {rg.status === 'PENDING' && (
                          <>
                            <button className="btn btn-outline-success" onClick={() => { setActionModal({ group: rg, action: 'accept' }); setNotes(''); }} title={t('requestorGroups.accept')}>
                              <i className="fa-solid fa-check"></i>
                            </button>
                            <button className="btn btn-outline-danger" onClick={() => { setActionModal({ group: rg, action: 'deny' }); setNotes(''); }} title={t('requestorGroups.deny')}>
                              <i className="fa-solid fa-xmark"></i>
                            </button>
                          </>
                        )}
                        {rg.status === 'ACCEPTED' && (
                          <button className="btn btn-outline-warning" onClick={() => handleReveal(rg)} title={t('requestorGroups.credentialsTooltip')} disabled={revealing === rg.id}>
                            {revealing === rg.id ? <span className="spinner-border spinner-border-sm"></span> : <i className="fa-solid fa-key"></i>}
                          </button>
                        )}
                        <button className="btn btn-outline-danger" onClick={() => setDeleteTarget(rg)} title={t('common.delete')}>
                          <i className="fa-solid fa-trash"></i>
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {totalItems > 0 && (
            <Pagination
              page={page}
              pageSize={pageSize}
              totalItems={totalItems}
              onPageChange={setPage}
              onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
              itemLabel={t('requestorGroups.itemLabel')}
            />
          )}
        </div>
      </div>

      {/* Detail Modal */}
      {detailModal && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-dialog-centered modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title"><i className="fa-solid fa-users me-2"></i>{detailModal.name}</h5>
                <button type="button" className="btn-close" onClick={() => setDetailModal(null)}></button>
              </div>
              <div className="modal-body">
                <div className="row g-3">
                  <div className="col-md-6"><div className="text-muted small">{t('requestorGroups.detail.code')}</div><code>{detailModal.code}</code></div>
                  <div className="col-md-6"><div className="text-muted small">{t('common.type')}</div><strong>{detailModal.groupType || '—'}</strong></div>
                  <div className="col-md-6"><div className="text-muted small">{t('requestorGroups.detail.organization')}</div>{detailModal.organization || '—'}</div>
                  <div className="col-md-6"><div className="text-muted small">{t('requestorGroups.detail.contact')}</div>{detailModal.contactName || '—'}<br />{detailModal.contactEmail || ''}</div>
                  {detailModal.description && <div className="col-12"><div className="text-muted small">{t('common.description')}</div><p>{detailModal.description}</p></div>}
                  {detailModal.reasonForAccess && <div className="col-12"><div className="text-muted small">{t('requestorGroups.detail.reasonForAccess')}</div><p>{detailModal.reasonForAccess}</p></div>}
                  <div className="col-md-6"><div className="text-muted small">{t('common.status')}</div><span className={`badge bg-${STATUS_CONFIG[detailModal.status]?.color || 'secondary'}`}>{STATUS_CONFIG[detailModal.status] ? t(`requestorGroups.status.${detailModal.status}`) : detailModal.status}</span></div>
                  <div className="col-md-6"><div className="text-muted small">{t('requestorGroups.detail.registered')}</div>{new Date(detailModal.createdAt).toLocaleString()}</div>
                  <div className="col-12">
                    <div className="text-muted small mb-1">{t('requestorGroups.detail.dataHolderGroup')}</div>
                    <div className="d-flex align-items-center gap-2">
                      <select
                        className="form-select form-select-sm"
                        style={{ maxWidth: 300 }}
                        value={detailModal.dataHolderGroupId || ''}
                        onChange={async (e) => {
                          const newGroupId = e.target.value ? parseInt(e.target.value) : null;
                          try {
                            const res = await updateRequestorGroup(detailModal.id, { dataHolderGroupId: newGroupId });
                            if (res.success) {
                              toast.success(t('requestorGroups.toast.groupUpdated'));
                              setDetailModal({ ...detailModal, dataHolderGroupId: newGroupId });
                              load();
                            }
                          } catch (err) { toast.error(t('requestorGroups.toast.groupUpdateFailed')); }
                        }}
                      >
                        <option value="">{t('requestorGroups.detail.noGroupAssigned')}</option>
                        {dhGroups.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
                      </select>
                    </div>
                  </div>
                  {detailModal.reviewedBy && <div className="col-12"><div className="text-muted small">{t('requestorGroups.detail.reviewedBy', { name: detailModal.reviewedBy, date: detailModal.reviewedAt ? new Date(detailModal.reviewedAt).toLocaleString() : '—' })}</div>{detailModal.reviewNotes && <p className="small mt-1">{detailModal.reviewNotes}</p>}</div>}
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setDetailModal(null)}>{t('common.close')}</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Accept/Deny Modal */}
      {actionModal && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className={`fa-solid ${actionModal.action === 'accept' ? 'fa-check text-success' : 'fa-xmark text-danger'} me-2`}></i>
                  {actionModal.action === 'accept'
                    ? t('requestorGroups.acceptTitle', { name: actionModal.group.name })
                    : t('requestorGroups.denyTitle', { name: actionModal.group.name })}
                </h5>
                <button type="button" className="btn-close" onClick={() => setActionModal(null)}></button>
              </div>
              <div className="modal-body">
                <p>
                  {actionModal.action === 'accept'
                    ? t('requestorGroups.action.acceptBody')
                    : t('requestorGroups.action.denyBody')}
                </p>
                <div className="mb-3">
                  <label className="form-label">{t('requestorGroups.action.notes')}</label>
                  <textarea className="form-control" rows={3} value={notes} onChange={e => setNotes(e.target.value)} placeholder={actionModal.action === 'deny' ? t('requestorGroups.action.denyPlaceholder') : t('requestorGroups.action.optionalPlaceholder')} />
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setActionModal(null)} disabled={submitting}>{t('common.cancel')}</button>
                <button className={`btn btn-${actionModal.action === 'accept' ? 'success' : 'danger'}`} onClick={handleAction} disabled={submitting}>
                  {submitting ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('common.processing')}</> : actionModal.action === 'accept' ? t('requestorGroups.action.acceptSubmit') : t('requestorGroups.deny')}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Delete Modal */}
      {deleteTarget && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title"><i className="fa-solid fa-triangle-exclamation text-danger me-2"></i>{t('requestorGroups.deleteTitle')}</h5>
                <button type="button" className="btn-close" onClick={() => setDeleteTarget(null)}></button>
              </div>
              <div className="modal-body">
                <p>{t('common.delete')} <strong>{deleteTarget.name}</strong> (<code>{deleteTarget.code}</code>)?</p>
                <div className="alert alert-warning small mb-0">{t('requestorGroups.deleteWarning')}</div>
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

      {/* Credential Modal */}
      {credentialModal && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title"><i className="fa-solid fa-key text-warning me-2"></i>{t('requestorGroups.credentials.title', { name: credentialModal.requestorGroupName || credentialModal.requestorGroupCode })}</h5>
                <button type="button" className="btn-close" onClick={() => { setCredentialModal(null); setSecretVisible(false); }}></button>
              </div>
              <div className="modal-body">
                <div className="mb-3">
                  <label className="form-label fw-semibold small text-muted">{t('requestorGroups.credentials.clientId')}</label>
                  <div className="input-group">
                    <input type="text" className="form-control font-monospace" value={credentialModal.clientId} readOnly />
                    <button className="btn btn-outline-secondary" onClick={() => copyToClipboard(credentialModal.clientId, t('requestorGroups.credentials.clientId'))}><i className="fa-solid fa-copy"></i></button>
                  </div>
                </div>
                <div className="mb-3">
                  <label className="form-label fw-semibold small text-muted">{t('requestorGroups.credentials.clientSecret')}</label>
                  <div className="input-group">
                    <input type={secretVisible ? 'text' : 'password'} className="form-control font-monospace" value={credentialModal.clientSecret} readOnly />
                    <button className="btn btn-outline-secondary" onClick={() => setSecretVisible(!secretVisible)}><i className={`fa-solid ${secretVisible ? 'fa-eye-slash' : 'fa-eye'}`}></i></button>
                    <button className="btn btn-outline-secondary" onClick={() => copyToClipboard(credentialModal.clientSecret, t('requestorGroups.credentials.clientSecret'))}><i className="fa-solid fa-copy"></i></button>
                  </div>
                </div>
                <div className="alert alert-info small mb-0"><i className="fa-solid fa-info-circle me-1"></i>{t('requestorGroups.credentials.info')}</div>
              </div>
              <div className="modal-footer d-flex justify-content-between">
                <button className="btn btn-outline-danger btn-sm" onClick={() => {
                  const rg = allGroups.find(g => g.code === credentialModal.requestorGroupCode);
                  if (rg) { setCredentialModal(null); setSecretVisible(false); handleRegenerate(rg); }
                }}><i className="fa-solid fa-rotate me-1"></i>{t('requestorGroups.regenerate')}</button>
                <button className="btn btn-secondary" onClick={() => { setCredentialModal(null); setSecretVisible(false); }}>{t('common.close')}</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default RequestorGroups;
