/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback, useContext } from 'react';
import { getDataHolderApplications, approveApplication, denyApplication, deleteApplication } from '../services/api';
import { GroupContext } from '../context/GroupContext';
import Pagination from '../components/Pagination';
import { useT } from '../i18n';
import toast from 'react-hot-toast';

const STATUS_CONFIG = {
  PENDING: { color: 'warning', icon: 'fa-clock' },
  APPROVED: { color: 'success', icon: 'fa-check-circle' },
  DENIED: { color: 'danger', icon: 'fa-times-circle' },
};

const DataHolderApplications = () => {
  const { selectedGroupId, isAllMode, groups } = useContext(GroupContext);
  const { t } = useT();

  const [allApps, setAllApps] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('ALL');
  // Server-side pagination state
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [totalItems, setTotalItems] = useState(0);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [detailModal, setDetailModal] = useState(null);
  const [actionModal, setActionModal] = useState(null); // { app, action: 'approve'|'deny' }
  const [notes, setNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [newCredentials, setNewCredentials] = useState(null);

  const groupNameMap = {};
  groups.forEach(g => { groupNameMap[g.id] = g.name; });

  const statusLabel = (s) => (STATUS_CONFIG[s] ? t(`dataHolderApplications.status.${s}`) : s);

  // Debounce the search box (300ms) before hitting the server.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      // Group scoping + status tab are applied server-side.
      const res = await getDataHolderApplications({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        dataHolderGroupId: isAllMode ? undefined : selectedGroupId,
        status: filter === 'ALL' ? undefined : filter,
        sortBy: 'createdAt',
        sortDir: 'desc',
      });
      setAllApps(res.content || []);
      setTotalItems(res.totalElements || 0);
    } catch {
      toast.error(t('dataHolderApplications.toast.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, debouncedSearch, filter, isAllMode, selectedGroupId]);

  useEffect(() => { load(); }, [load]);

  // Reset to the first page when a filter or the search term changes.
  useEffect(() => { setPage(1); }, [debouncedSearch, filter, isAllMode, selectedGroupId]);

  const openAction = (app, action) => {
    setActionModal({ app, action });
    setNotes('');
    setNewCredentials(null);
  };

  const handleAction = async () => {
    if (!actionModal) return;
    const { app, action } = actionModal;
    setSubmitting(true);
    try {
      const payload = { reviewedBy: 'admin', notes: notes || null };
      const res = action === 'approve'
        ? await approveApplication(app.id, payload)
        : await denyApplication(app.id, payload);
      if (res.success) {
        toast.success(action === 'approve' ? t('dataHolderApplications.toast.approved') : t('dataHolderApplications.toast.denied'));
        if (action === 'approve' && res.dataHolder) {
          setNewCredentials({
            clientId: res.dataHolder.clientId,
            clientSecret: res.dataHolder.clientSecret,
            dataholderId: res.dataHolder.dataholderId,
            name: res.dataHolder.name,
          });
        } else {
          setActionModal(null);
        }
        load();
      } else {
        toast.error(res.error || t('dataHolderApplications.toast.actionFailed'));
      }
    } catch (err) {
      toast.error(err.data?.error || err.message || t('dataHolderApplications.toast.actionFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setSubmitting(true);
    try {
      const res = await deleteApplication(deleteTarget.id);
      if (res.success) {
        toast.success(t('dataHolderApplications.toast.deleted'));
        setDeleteTarget(null);
        load();
      } else {
        toast.error(res.error || t('dataHolderApplications.toast.deleteFailed'));
      }
    } catch (err) {
      toast.error(err.data?.error || err.message || t('dataHolderApplications.toast.deleteFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const copyToClipboard = (text, label) => {
    navigator.clipboard.writeText(text).then(
      () => toast.success(t('dataHolderApplications.toast.copied', { label })),
      () => toast.error(t('dataHolderApplications.toast.copyFailed'))
    );
  };

  const formatDate = (str) => {
    if (!str) return '—';
    try { return new Date(str).toLocaleString(); } catch { return str; }
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <div>
          <h4 className="fw-bold mb-1">
            <i className="fa-solid fa-file-circle-plus me-2 text-primary"></i>
            {t('dataHolderApplications.title')}
          </h4>
          <p className="text-muted mb-0 small">{t('dataHolderApplications.subtitle')}</p>
        </div>
      </div>

      {/* Filter tabs + search */}
      <div className="d-flex justify-content-between align-items-end flex-wrap gap-2 mb-3">
        <ul className="nav nav-tabs mb-0">
          {['ALL', 'PENDING', 'APPROVED', 'DENIED'].map(f => (
            <li className="nav-item" key={f}>
              <button
                className={`nav-link ${filter === f ? 'active' : ''}`}
                onClick={() => setFilter(f)}
              >
                {f === 'ALL' ? t('common.all') : statusLabel(f)}
              </button>
            </li>
          ))}
        </ul>
        <div className="input-group input-group-sm" style={{ maxWidth: 320 }}>
          <span className="input-group-text"><i className="fa-solid fa-magnifying-glass"></i></span>
          <input
            className="form-control"
            placeholder={t('dataHolderApplications.searchPlaceholder')}
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

      {loading && allApps.length === 0 ? (
        <div className="text-center py-5 text-muted">
          <span className="spinner-border"></span>
          <div className="mt-2">{t('dataHolderApplications.loading')}</div>
        </div>
      ) : allApps.length === 0 ? (
        <div className="text-center py-5 text-muted">
          <i className="fa-solid fa-inbox fa-2x mb-2"></i>
          <div>{t('dataHolderApplications.empty', { filter: filter !== 'ALL' ? statusLabel(filter).toLowerCase() : '' })}</div>
        </div>
      ) : (
        <div className="table-responsive">
          <table className="table table-hover align-middle">
            <thead className="table-light">
              <tr>
                <th>{t('dataHolderApplications.table.organization')}</th>
                <th>{t('dataHolderApplications.table.contact')}</th>
                <th>{t('dataHolderApplications.table.group')}</th>
                <th>{t('dataHolderApplications.table.tlds')}</th>
                <th>{t('common.status')}</th>
                <th>{t('dataHolderApplications.table.submitted')}</th>
                <th style={{ width: 160 }}>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {allApps.map(app => {
                const sc = STATUS_CONFIG[app.status] || { color: 'secondary', icon: 'fa-question' };
                return (
                  <tr key={app.id}>
                    <td>
                      <div className="fw-semibold">{app.organizationName}</div>
                      {app.organizationPhone && <div className="text-muted small">{app.organizationPhone}</div>}
                    </td>
                    <td>
                      <div className="small">{app.contactFullName}</div>
                      <div className="text-muted small">{app.contactEmail}</div>
                    </td>
                    <td>
                      <span className="badge bg-light text-dark border">
                        {groupNameMap[app.dataHolderGroupId] || t('dataHolderApplications.groupFallback', { id: app.dataHolderGroupId })}
                      </span>
                    </td>
                    <td>
                      <div className="small" style={{ maxWidth: 160 }}>
                        {app.supportedTlds
                          ? app.supportedTlds.split(',').slice(0, 4).map(t => t.trim()).map((t, i) => (
                              <span key={i} className="badge bg-light text-dark border me-1 mb-1">{t}</span>
                            ))
                          : <span className="text-muted">—</span>
                        }
                        {app.supportedTlds && app.supportedTlds.split(',').length > 4 && (
                          <span className="badge bg-secondary">+{app.supportedTlds.split(',').length - 4}</span>
                        )}
                      </div>
                    </td>
                    <td>
                      <span className={`badge bg-${sc.color}`}>
                        <i className={`fa-solid ${sc.icon} me-1`}></i>{statusLabel(app.status)}
                      </span>
                    </td>
                    <td className="small text-muted">{formatDate(app.createdAt)}</td>
                    <td>
                      <div className="btn-group btn-group-sm">
                        <button className="btn btn-outline-secondary" onClick={() => setDetailModal(app)}
                          title={t('dataHolderApplications.actions.viewDetails')}>
                          <i className="fa-solid fa-eye"></i>
                        </button>
                        {app.status === 'PENDING' && (
                          <>
                            <button className="btn btn-outline-success" onClick={() => openAction(app, 'approve')}
                              title={t('dataHolderApplications.actions.approve')}>
                              <i className="fa-solid fa-check"></i>
                            </button>
                            <button className="btn btn-outline-danger" onClick={() => openAction(app, 'deny')}
                              title={t('dataHolderApplications.actions.deny')}>
                              <i className="fa-solid fa-xmark"></i>
                            </button>
                          </>
                        )}
                        <button className="btn btn-outline-danger" onClick={() => setDeleteTarget(app)}
                          title={t('common.delete')}>
                          <i className="fa-solid fa-trash"></i>
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <Pagination
            page={page}
            pageSize={pageSize}
            totalItems={totalItems}
            onPageChange={setPage}
            onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
            itemLabel={t('dataHolderApplications.itemLabel')}
          />
        </div>
      )}

      {/* ==================== Detail Modal ==================== */}
      {detailModal && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className="fa-solid fa-file-circle-plus text-primary me-2"></i>
                  {t('dataHolderApplications.detail.title', { name: detailModal.organizationName })}
                </h5>
                <button type="button" className="btn-close" onClick={() => setDetailModal(null)}></button>
              </div>
              <div className="modal-body">
                <div className="row g-3">
                  {/* Status */}
                  <div className="col-12">
                    <span className={`badge bg-${(STATUS_CONFIG[detailModal.status] || {}).color || 'secondary'} me-2`}>
                      {statusLabel(detailModal.status)}
                    </span>
                    <span className="badge bg-light text-dark border">
                      {groupNameMap[detailModal.dataHolderGroupId] || t('dataHolderApplications.groupFallback', { id: detailModal.dataHolderGroupId })}
                    </span>
                  </div>

                  {/* Organization */}
                  <div className="col-12">
                    <h6 className="fw-semibold text-secondary text-uppercase" style={{ fontSize: 11, letterSpacing: '0.05em' }}>
                      {t('dataHolderApplications.detail.organization')}
                    </h6>
                  </div>
                  <div className="col-md-6">
                    <div className="text-muted small">{t('common.name')}</div>
                    <div>{detailModal.organizationName}</div>
                  </div>
                  <div className="col-md-6">
                    <div className="text-muted small">{t('common.phone')}</div>
                    <div>{detailModal.organizationPhone || '—'}</div>
                  </div>
                  <div className="col-12">
                    <div className="text-muted small">{t('dataHolderApplications.detail.address')}</div>
                    <div style={{ whiteSpace: 'pre-wrap' }}>{detailModal.organizationAddress || '—'}</div>
                  </div>

                  {/* Contact */}
                  <div className="col-12 mt-3">
                    <h6 className="fw-semibold text-secondary text-uppercase" style={{ fontSize: 11, letterSpacing: '0.05em' }}>
                      {t('dataHolderApplications.detail.adminContact')}
                    </h6>
                  </div>
                  <div className="col-md-6">
                    <div className="text-muted small">{t('dataHolderApplications.detail.fullName')}</div>
                    <div>{detailModal.contactFullName}</div>
                  </div>
                  <div className="col-md-6">
                    <div className="text-muted small">{t('dataHolderApplications.detail.contactTitle')}</div>
                    <div>{detailModal.contactTitle || '—'}</div>
                  </div>
                  <div className="col-md-6">
                    <div className="text-muted small">{t('common.email')}</div>
                    <div><a href={`mailto:${detailModal.contactEmail}`}>{detailModal.contactEmail}</a></div>
                  </div>
                  <div className="col-md-6">
                    <div className="text-muted small">{t('common.phone')}</div>
                    <div>{detailModal.contactPhone || '—'}</div>
                  </div>

                  {/* RDAP */}
                  <div className="col-12 mt-3">
                    <h6 className="fw-semibold text-secondary text-uppercase" style={{ fontSize: 11, letterSpacing: '0.05em' }}>
                      {t('dataHolderApplications.detail.rdapServers')}
                    </h6>
                    {detailModal.rdapServerUrls ? (
                      <div>
                        {detailModal.rdapServerUrls.split(',').map((u, i) => (
                          <div key={i} className="mb-1">
                            <code>{u.trim()}</code>
                          </div>
                        ))}
                      </div>
                    ) : <span className="text-muted">—</span>}
                  </div>

                  {/* TLDs */}
                  <div className="col-12 mt-3">
                    <h6 className="fw-semibold text-secondary text-uppercase" style={{ fontSize: 11, letterSpacing: '0.05em' }}>
                      {t('dataHolderApplications.detail.supportedTlds')}
                    </h6>
                    {detailModal.supportedTlds ? (
                      <div className="d-flex flex-wrap gap-1">
                        {detailModal.supportedTlds.split(',').map((t, i) => (
                          <span key={i} className="badge bg-primary">{t.trim()}</span>
                        ))}
                      </div>
                    ) : <span className="text-muted">—</span>}
                  </div>

                  {/* Notes */}
                  {detailModal.additionalNotes && (
                    <div className="col-12 mt-3">
                      <h6 className="fw-semibold text-secondary text-uppercase" style={{ fontSize: 11, letterSpacing: '0.05em' }}>
                        {t('dataHolderApplications.detail.additionalNotes')}
                      </h6>
                      <div style={{ whiteSpace: 'pre-wrap' }}>{detailModal.additionalNotes}</div>
                    </div>
                  )}

                  {/* Review info */}
                  {detailModal.status !== 'PENDING' && (
                    <div className="col-12 mt-3">
                      <h6 className="fw-semibold text-secondary text-uppercase" style={{ fontSize: 11, letterSpacing: '0.05em' }}>
                        {t('dataHolderApplications.detail.review')}
                      </h6>
                      <div className="text-muted small">
                        {t('dataHolderApplications.detail.reviewLine', {
                          action: detailModal.status === 'APPROVED' ? t('dataHolderApplications.detail.reviewApproved') : t('dataHolderApplications.detail.reviewDenied'),
                          reviewer: detailModal.reviewedBy || '—',
                          date: formatDate(detailModal.reviewedAt),
                        })}
                      </div>
                      {detailModal.reviewNotes && <div className="mt-1">{detailModal.reviewNotes}</div>}
                    </div>
                  )}

                  {/* Timestamps */}
                  <div className="col-12 mt-3">
                    <div className="text-muted small">{t('dataHolderApplications.detail.submitted', { date: formatDate(detailModal.createdAt) })}</div>
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                {detailModal.status === 'PENDING' && (
                  <>
                    <button className="btn btn-success btn-sm" onClick={() => { setDetailModal(null); openAction(detailModal, 'approve'); }}>
                      <i className="fa-solid fa-check me-1"></i>{t('dataHolderApplications.actions.approve')}
                    </button>
                    <button className="btn btn-danger btn-sm" onClick={() => { setDetailModal(null); openAction(detailModal, 'deny'); }}>
                      <i className="fa-solid fa-xmark me-1"></i>{t('dataHolderApplications.actions.deny')}
                    </button>
                  </>
                )}
                <button className="btn btn-secondary btn-sm" onClick={() => setDetailModal(null)}>{t('common.close')}</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ==================== Action Modal (Approve / Deny) ==================== */}
      {actionModal && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  {actionModal.action === 'approve' ? (
                    <><i className="fa-solid fa-check-circle text-success me-2"></i>{t('dataHolderApplications.action.approveTitle')}</>
                  ) : (
                    <><i className="fa-solid fa-times-circle text-danger me-2"></i>{t('dataHolderApplications.action.denyTitle')}</>
                  )}
                </h5>
                <button type="button" className="btn-close" onClick={() => { setActionModal(null); setNewCredentials(null); }}></button>
              </div>
              <div className="modal-body">
                {newCredentials ? (
                  <div>
                    <div className="alert alert-success">
                      <h6 className="alert-heading"><i className="fa-solid fa-check-circle me-1"></i>{t('dataHolderApplications.action.approvedHeading')}</h6>
                      <p className="small mb-0">
                        {t('dataHolderApplications.action.createdPrefix')} <strong>{newCredentials.name}</strong> (<code>{newCredentials.dataholderId}</code>) {t('dataHolderApplications.action.createdSuffix')}
                      </p>
                    </div>
                    <div className="mb-3">
                      <label className="form-label fw-semibold small text-muted">{t('dataHolderApplications.action.clientId')}</label>
                      <div className="input-group">
                        <input type="text" className="form-control font-monospace" value={newCredentials.clientId} readOnly />
                        <button className="btn btn-outline-secondary" onClick={() => copyToClipboard(newCredentials.clientId, t('dataHolderApplications.action.clientId'))}>
                          <i className="fa-solid fa-copy"></i>
                        </button>
                      </div>
                    </div>
                    <div>
                      <label className="form-label fw-semibold small text-muted">{t('dataHolderApplications.action.clientSecret')}</label>
                      <div className="input-group">
                        <input type="text" className="form-control font-monospace" value={newCredentials.clientSecret} readOnly />
                        <button className="btn btn-outline-secondary" onClick={() => copyToClipboard(newCredentials.clientSecret, t('dataHolderApplications.action.clientSecret'))}>
                          <i className="fa-solid fa-copy"></i>
                        </button>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div>
                    <p>
                      {actionModal.action === 'approve'
                        ? <>{t('dataHolderApplications.action.approvePrefix')} <strong>{actionModal.app.organizationName}</strong>{t('dataHolderApplications.action.approveSuffix')}</>
                        : <>{t('dataHolderApplications.action.denyPrefix')} <strong>{actionModal.app.organizationName}</strong>{t('dataHolderApplications.action.denySuffix')}</>
                      }
                    </p>
                    <div className="mb-2">
                      <label className="form-label small text-muted">{t('dataHolderApplications.action.reviewNotesLabel')}</label>
                      <textarea className="form-control" rows={2} value={notes}
                        onChange={e => setNotes(e.target.value)}
                        placeholder={actionModal.action === 'approve'
                          ? t('dataHolderApplications.action.approveNotesPlaceholder')
                          : t('dataHolderApplications.action.denyNotesPlaceholder')}
                      />
                    </div>
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => { setActionModal(null); setNewCredentials(null); }} disabled={submitting}>
                  {newCredentials ? t('dataHolderApplications.action.done') : t('common.cancel')}
                </button>
                {!newCredentials && (
                  <button
                    className={`btn ${actionModal.action === 'approve' ? 'btn-success' : 'btn-danger'}`}
                    onClick={handleAction}
                    disabled={submitting}
                  >
                    {submitting ? (
                      <><span className="spinner-border spinner-border-sm me-1"></span>{t('common.processing')}</>
                    ) : actionModal.action === 'approve' ? (
                      <><i className="fa-solid fa-check me-1"></i>{t('dataHolderApplications.actions.approve')}</>
                    ) : (
                      <><i className="fa-solid fa-xmark me-1"></i>{t('dataHolderApplications.actions.deny')}</>
                    )}
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ==================== Delete Confirm Modal ==================== */}
      {deleteTarget && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className="fa-solid fa-triangle-exclamation text-danger me-2"></i>{t('dataHolderApplications.delete.title')}
                </h5>
                <button type="button" className="btn-close" onClick={() => setDeleteTarget(null)}></button>
              </div>
              <div className="modal-body">
                <p>{t('dataHolderApplications.delete.confirmPrefix')} <strong>{deleteTarget.organizationName}</strong>{t('dataHolderApplications.delete.confirmSuffix')}</p>
                <p className="text-muted small mb-0">{t('dataHolderApplications.delete.cannotUndo')}</p>
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
    </div>
  );
};

export default DataHolderApplications;
