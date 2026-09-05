/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import { format } from 'date-fns';
import { getPendingRequestsPaged, getRequestStats, reviewRequest, getFileViewUrl, getFileDownloadUrl } from '../services/api';
import Loading from '../components/Loading';
import Modal from '../components/Modal';
import FileViewer from '../components/FileViewer';
import Pagination, { DEFAULT_PAGE_SIZE } from '../components/Pagination';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const PendingRequests = () => {
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selectedRequest, setSelectedRequest] = useState(null);
  const [showReviewModal, setShowReviewModal] = useState(false);
  const [reviewAction, setReviewAction] = useState('');
  const [adminNotes, setAdminNotes] = useState('');
  const [denialReason, setDenialReason] = useState('');
  const [grantedLevel, setGrantedLevel] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [lastRefreshed, setLastRefreshed] = useState(null);
  const [examineCustomFile, setExamineCustomFile] = useState(null);

  // Server-side pagination + search state
  const [page, setPage] = useState(1); // 1-based
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [totalItems, setTotalItems] = useState(0);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  // Status counts come from a dedicated stats endpoint (full-dataset, not the current page).
  const [counts, setCounts] = useState({ ALL: 0, PENDING: 0, APPROVED: 0, DENIED: 0, CANCELLED: 0 });

  const { t } = useT();

  // Helper to get auth headers for admin API calls
  const getAuthHeaders = () => {
    const token = localStorage.getItem('dataholder_access_token');
    const headers = { 'Content-Type': 'application/json' };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    return headers;
  };

  // Debounce the search box (300ms)
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  // Reset to first page whenever the search term or status filter changes
  useEffect(() => { setPage(1); }, [debouncedSearch, statusFilter]);

  /*
   * Fetch the current page (and stats) whenever paging/search/filter change,
   * then keep auto-polling every 5 seconds.
   */
  useEffect(() => {
    loadRequests(true);
    loadStats();
    const intervalId = setInterval(() => {
      loadRequests(false);
      loadStats();
    }, 5000);
    return () => clearInterval(intervalId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, pageSize, debouncedSearch, statusFilter]);

  const loadStats = async () => {
    try {
      const res = await getRequestStats();
      const s = res.data || {};
      setCounts({
        ALL: s.total || 0,
        PENDING: s.pending || 0,
        APPROVED: s.approved || 0,
        DENIED: s.denied || 0,
        CANCELLED: s.cancelled || 0,
      });
    } catch (error) {
      console.error('Failed to load request stats:', error);
    }
  };

  const loadRequests = async (showLoader = true) => {
    if (showLoader) setLoading(true);
    try {
      const response = await getPendingRequestsPaged({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        status: statusFilter !== 'ALL' ? statusFilter : undefined,
        sortBy: 'createdAt',
        sortDir: 'desc',
      });
      setRequests(response.data.content || []);
      setTotalItems(response.data.totalElements || 0);
      setLastRefreshed(new Date());
    } catch (error) {
      console.error('Failed to load requests:', error);
      if (showLoader) toast.error(t('pendingRequests.toast.loadRequestsFailed'));
    } finally {
      if (showLoader) setLoading(false);
    }
  };

  // Refetch current page + counts without the full-screen loader (used after mutations).
  const refresh = () => { loadRequests(false); loadStats(); };

  const openReviewModal = (request, action) => {
    setSelectedRequest(request);
    setReviewAction(action);
    setAdminNotes(request.adminNotes || '');
    setDenialReason(request.denialReason || '');
    setGrantedLevel(request.requestedAccessLevel);
    setShowReviewModal(true);
  };

  const handleReview = async (action) => {
    if (!selectedRequest) return;
    if (action === 'deny' && !denialReason.trim()) {
      toast.error(t('pendingRequests.form.denialReasonRequired'));
      return;
    }

    setSubmitting(true);
    try {
      await reviewRequest(
        selectedRequest.requestId,
        action,
        adminNotes,
        action === 'approve' ? grantedLevel : null,
        action === 'deny' ? denialReason : null
      );

      toast.success(action === 'approve' ? t('pendingRequests.toast.approveSuccess') : t('pendingRequests.toast.denySuccess'));
      setShowReviewModal(false);
      refresh();
    } catch (error) {
      console.error('Failed to review request:', error);
      toast.error(t('pendingRequests.toast.reviewFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (request) => {
    if (!window.confirm(t('pendingRequests.confirmDelete', { id: request.requestId.substring(0, 8) }))) {
      return;
    }

    try {
      const response = await fetch(`/api/admin/requests/${request.requestId}`, {
        method: 'DELETE',
        headers: getAuthHeaders(),
      });

      if (response.ok) {
        toast.success(t('pendingRequests.toast.deleteSuccess'));
        refresh();
      } else {
        const errorData = await response.json().catch(() => ({}));
        toast.error(errorData.error || t('pendingRequests.toast.deleteFailed'));
      }
    } catch (error) {
      console.error('Failed to delete request:', error);
      toast.error(t('pendingRequests.toast.deleteFailed'));
    }
  };

  const getStatusBadge = (status) => {
    const statusClasses = {
      PENDING: 'bg-warning-subtle text-warning',
      APPROVED: 'bg-success-subtle text-success',
      DENIED: 'bg-danger-subtle text-danger',
      CANCELLED: 'bg-info-subtle text-info',
    };
    return <span className={`badge ${statusClasses[status] || 'bg-info-subtle text-info'}`}>{status}</span>;
  };

  if (loading && requests.length === 0) {
    return <Loading message={t('pendingRequests.loadingMessage')} />;
  }

  return (
    <div>
      <div className="mb-4">
        <h2 className="h3 fw-bold mb-1">{t('pendingRequests.pageTitle')}</h2>
        <p className="text-muted mb-0">{t('pendingRequests.pageSubtitle')}</p>
      </div>

      {/* Status Filter Tabs */}
      <div className="card" style={{ marginBottom: '16px' }}>
        <div style={{ display: 'flex', gap: '8px', padding: '12px 16px', flexWrap: 'wrap' }}>
          <button
            className={`btn btn-sm ${statusFilter === 'ALL' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setStatusFilter('ALL')}
          >
            {t('pendingRequests.tabs.all', { count: counts.ALL })}
          </button>
          <button
            className={`btn btn-sm ${statusFilter === 'PENDING' ? 'btn-warning' : 'btn-secondary'}`}
            onClick={() => setStatusFilter('PENDING')}
          >
            {t('pendingRequests.tabs.pending', { count: counts.PENDING })}
          </button>
          <button
            className={`btn btn-sm ${statusFilter === 'APPROVED' ? 'btn-success' : 'btn-secondary'}`}
            onClick={() => setStatusFilter('APPROVED')}
          >
            {t('pendingRequests.tabs.approved', { count: counts.APPROVED })}
          </button>
          <button
            className={`btn btn-sm ${statusFilter === 'DENIED' ? 'btn-danger' : 'btn-secondary'}`}
            onClick={() => setStatusFilter('DENIED')}
          >
            {t('pendingRequests.tabs.denied', { count: counts.DENIED })}
          </button>
          <button
            className={`btn btn-sm ${statusFilter === 'CANCELLED' ? 'btn-info' : 'btn-secondary'}`}
            onClick={() => setStatusFilter('CANCELLED')}
          >
            {t('pendingRequests.tabs.cancelled', { count: counts.CANCELLED })}
          </button>
        </div>
      </div>

      <div className="card">
        <div className="card-header d-flex justify-content-between align-items-center">
          <h3>
            <i className="fa-solid fa-hourglass-half"></i>
            {statusFilter === 'ALL' ? t('pendingRequests.table.titleAll', { count: totalItems }) : t('pendingRequests.table.titleFiltered', { status: statusFilter, count: totalItems })}
          </h3>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <input
              type="text"
              className="form-control form-control-sm"
              style={{ width: '220px' }}
              placeholder={t('pendingRequests.table.searchPlaceholder')}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            {lastRefreshed && (
              <span style={{ fontSize: '12px', color: 'var(--color-text-muted, #6b7280)' }}>
                {t('pendingRequests.table.lastUpdated', { time: lastRefreshed.toLocaleTimeString() })}
              </span>
            )}
            <span
              style={{
                display: 'inline-block',
                width: '8px',
                height: '8px',
                borderRadius: '50%',
                backgroundColor: '#22c55e',
                animation: 'pulse 2s infinite',
              }}
              title={t('pendingRequests.table.autoRefreshTitle')}
            ></span>
            <button className="btn btn-secondary btn-sm" onClick={refresh}>
              <i className="fa-solid fa-arrows-rotate"></i>
              {t('common.refresh')}
            </button>
          </div>
        </div>
        <style>{`
          @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.3; }
          }
        `}</style>
        <div className="table-responsive">
          {requests.length > 0 ? (
            <>
            <table className="table table-hover align-middle">
              <thead>
                <tr>
                  <th>{t('pendingRequests.table.headers.requestId')}</th>
                  <th>{t('pendingRequests.table.headers.query')}</th>
                  <th>{t('pendingRequests.table.headers.requestor')}</th>
                  <th>{t('pendingRequests.table.headers.files')}</th>
                  <th>{t('pendingRequests.table.headers.agreements')}</th>
                  <th>{t('pendingRequests.table.headers.accessLevel')}</th>
                  <th>{t('common.status')}</th>
                  <th>{t('pendingRequests.table.headers.created')}</th>
                  <th>{t('pendingRequests.table.headers.reviewed')}</th>
                  <th>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {requests.map((request) => (
                  <tr key={request.requestId}>
                    <td>
                      <span className="font-monospace small">
                        {request.requestId.substring(0, 8)}...
                      </span>
                    </td>
                    <td>
                      <span className="font-monospace">{request.queryValue}</span>
                      <div className="text-muted small">{request.queryType}</div>
                    </td>
                    <td>
                      <div>{request.requestorUsername || '-'}</div>
                      <div className="text-muted small">{request.requestorEmail}</div>
                    </td>
                    <td>
                      {(() => {
                        // Collect file types from fileAttachments
                        const attachTypes = (request.fileAttachments || []).map(f => f.fileType);
                        // Collect file types from customParams values that look like filenames
                        const cpFileTypes = [];
                        if (request.customParams) {
                          Object.values(request.customParams).forEach(v => {
                            const s = String(v);
                            const m = s.match(/\.(pdf|docx|xlsx|txt|jpeg|jpg|png)$/i);
                            if (m) {
                              let ext = m[1].toUpperCase();
                              if (ext === 'JPG') ext = 'JPEG';
                              cpFileTypes.push(ext);
                            }
                          });
                        }
                        const allTypes = [...new Set([...attachTypes, ...cpFileTypes])];
                        const totalCount = (request.fileAttachments?.length || 0) + cpFileTypes.length;
                        if (allTypes.length === 0) {
                          return <span className="text-muted small">—</span>;
                        }
                        const typeColors = {
                          PDF: { bg: '#fef2f2', color: '#dc2626' },
                          DOCX: { bg: '#eff6ff', color: '#2563eb' },
                          XLSX: { bg: '#f0fdf4', color: '#16a34a' },
                          JPEG: { bg: '#faf5ff', color: '#7c3aed' },
                          PNG: { bg: '#faf5ff', color: '#7c3aed' },
                          TXT: { bg: '#f9fafb', color: '#4b5563' },
                        };
                        return (
                          <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                            <i className="fa-solid fa-paperclip" style={{ fontSize: '12px', color: 'var(--accent-primary)' }} />
                            <span style={{ fontSize: '12px', fontWeight: 500 }}>{totalCount}</span>
                            <div style={{ display: 'flex', gap: '2px', marginLeft: '2px' }}>
                              {allTypes.map(type => {
                                const tc = typeColors[type] || typeColors.TXT;
                                return (
                                  <span key={type} style={{
                                    fontSize: '9px', fontWeight: 700, padding: '1px 4px',
                                    borderRadius: '3px', background: tc.bg, color: tc.color,
                                    letterSpacing: '0.3px'
                                  }}>
                                    {type}
                                  </span>
                                );
                              })}
                            </div>
                          </div>
                        );
                      })()}
                    </td>
                    <td>
                      {request.agreementNames?.length > 0 ? (
                        <div className="d-flex flex-wrap gap-1">
                          {request.agreementNames.map((name) => (
                            <span key={name} className="badge bg-light text-dark border">{name}</span>
                          ))}
                        </div>
                      ) : (
                        <span className="text-muted">{t('common.none')}</span>
                      )}
                    </td>
                    <td>
                      <span className="badge bg-primary-subtle text-primary">{t('pendingRequests.table.accessLevelBadge', { level: request.requestedAccessLevel })}</span>
                    </td>
                    <td>{getStatusBadge(request.status)}</td>
                    <td className="text-muted small">
                      {request.createdAt ? format(new Date(request.createdAt), 'MMM d, HH:mm') : '-'}
                    </td>
                    <td className="text-muted small">
                      {request.reviewedAt ? (
                        <div>
                          {format(new Date(request.reviewedAt), 'MMM d, HH:mm')}
                          {request.reviewedBy && (
                            <div className="text-muted small">{t('pendingRequests.table.reviewedBy', { name: request.reviewedBy })}</div>
                          )}
                        </div>
                      ) : '-'}
                    </td>
                    <td>
                      <div className="d-flex gap-2">
                        {request.status === 'PENDING' && (
                          <button
                            className="btn btn-primary btn-sm"
                            onClick={() => openReviewModal(request, 'review')}
                            title={t('pendingRequests.actions.review')}
                          >
                            {t('pendingRequests.actions.review')}
                          </button>
                        )}
                        <button
                          className="btn btn-outline-secondary btn-sm"
                          onClick={() => {
                            setSelectedRequest(request);
                            setShowReviewModal(true);
                            setReviewAction('view');
                          }}
                          title={t('pendingRequests.actions.viewDetails')}
                        >
                          <i className="fa-solid fa-eye"></i>
                        </button>
                        <button
                          className="btn btn-outline-secondary btn-sm"
                          onClick={() => handleDelete(request)}
                          title={t('common.delete')}
                          style={{ color: 'var(--color-danger)' }}
                        >
                          <i className="fa-solid fa-trash"></i>
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination
              page={page}
              pageSize={pageSize}
              totalItems={totalItems}
              onPageChange={setPage}
              onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
              itemLabel={t('pendingRequests.table.itemLabel')}
            />
            </>
          ) : (
            <div className="table-empty">
              <i className="fa-solid fa-hourglass-half"></i>
              <p>{t('pendingRequests.table.noResults', { status: statusFilter !== 'ALL' ? statusFilter.toLowerCase() : '' })}</p>
            </div>
          )}
        </div>
      </div>

      {/* Review Modal */}
      <Modal
        isOpen={showReviewModal}
        onClose={() => setShowReviewModal(false)}
        title={
          reviewAction === 'review' ? t('pendingRequests.modal.reviewTitle') :
          reviewAction === 'approve' ? t('pendingRequests.modal.approveTitle') :
          reviewAction === 'deny' ? t('pendingRequests.modal.denyTitle') :
          t('pendingRequests.modal.detailsTitle')
        }
        footer={
          reviewAction !== 'view' && (
            <>
              <button
                className="btn btn-secondary"
                onClick={() => setShowReviewModal(false)}
              >
                {t('common.cancel')}
              </button>
              <button
                className="btn btn-danger"
                onClick={() => handleReview('deny')}
                disabled={submitting}
              >
                {submitting ? t('common.processing') : t('pendingRequests.actions.deny')}
              </button>
              <button
                className="btn btn-success"
                onClick={() => handleReview('approve')}
                disabled={submitting}
              >
                {submitting ? t('common.processing') : t('pendingRequests.actions.approve')}
              </button>
            </>
          )
        }
      >
        {selectedRequest && (
          <div>
            <div className="row g-3">
              <div className="col-md-6">
                <div className="text-muted text-uppercase small fw-semibold mb-1">{t('pendingRequests.details.requestId')}</div>
                <div className="font-monospace">{selectedRequest.requestId}</div>
              </div>
              <div className="col-md-6">
                <div className="text-muted text-uppercase small fw-semibold mb-1">{t('common.status')}</div>
                <div>{getStatusBadge(selectedRequest.status)}</div>
              </div>
              <div className="col-md-6">
                <div className="text-muted text-uppercase small fw-semibold mb-1">{t('pendingRequests.details.queryType')}</div>
                <div>{selectedRequest.queryType}</div>
              </div>
              <div className="col-md-6">
                <div className="text-muted text-uppercase small fw-semibold mb-1">{t('pendingRequests.details.queryValue')}</div>
                <div className="font-monospace">{selectedRequest.queryValue}</div>
              </div>
              <div className="col-md-6">
                <div className="text-muted text-uppercase small fw-semibold mb-1">{t('pendingRequests.table.headers.requestor')}</div>
                <div>{selectedRequest.requestorUsername || '-'}</div>
              </div>
              <div className="col-md-6">
                <div className="text-muted text-uppercase small fw-semibold mb-1">{t('common.email')}</div>
                <div>{selectedRequest.requestorEmail || '-'}</div>
              </div>
              <div className="col-12">
                <div className="text-muted text-uppercase small fw-semibold mb-1">{t('pendingRequests.details.groups')}</div>
                <div>
                  {selectedRequest.requestorGroups?.length > 0 ? (
                    <div className="d-flex flex-wrap gap-1">
                      {selectedRequest.requestorGroups.map((g) => (
                        <span key={g} className="badge bg-light text-dark border">{g}</span>
                      ))}
                    </div>
                  ) : (
                    <span className="text-muted">{t('common.none')}</span>
                  )}
                </div>
              </div>
              <div className="col-12">
                <div className="text-muted text-uppercase small fw-semibold mb-1">{t('pendingRequests.details.agreements')}</div>
                <div>
                  {selectedRequest.agreementNames?.length > 0 ? (
                    <div className="d-flex flex-wrap gap-1">
                      {selectedRequest.agreementNames.map((name) => (
                        <span key={name} className="badge bg-light text-dark border">{name}</span>
                      ))}
                    </div>
                  ) : (
                    <span className="text-muted">{t('common.none')}</span>
                  )}
                </div>
              </div>
              {selectedRequest.adminNotes && (
                <div className="col-12">
                  <div className="text-muted text-uppercase small fw-semibold mb-1">{t('pendingRequests.details.adminNotes')}</div>
                  <div>{selectedRequest.adminNotes}</div>
                </div>
              )}

              {/* Custom Parameters (including file references) */}
              {selectedRequest.customParams && Object.keys(selectedRequest.customParams).length > 0 && (
                <div className="col-12">
                  <div className="text-muted text-uppercase small fw-semibold mb-1">{t('pendingRequests.details.customParameters')}</div>
                  <div>
                    {(() => {
                      const cp = selectedRequest.customParams;
                      const fileParamNames = new Set();
                      const metaKeys = new Set();
                      Object.keys(cp).forEach(k => {
                        const m = k.match(/^__(.+?)_(fileId|fileType|fileSize|fileHash)$/);
                        if (m) { fileParamNames.add(m[1]); metaKeys.add(k); }
                      });
                      Object.entries(cp).forEach(([k, v]) => {
                        if (!metaKeys.has(k) && !fileParamNames.has(k)) {
                          if (/\.(pdf|docx|xlsx|txt|jpeg|jpg|png)$/i.test(String(v))) fileParamNames.add(k);
                        }
                      });

                      const fileColors = {
                        PDF: { bg: '#fef2f2', color: '#dc2626', border: '#fecaca' },
                        DOCX: { bg: '#eff6ff', color: '#2563eb', border: '#bfdbfe' },
                        XLSX: { bg: '#f0fdf4', color: '#16a34a', border: '#bbf7d0' },
                        JPEG: { bg: '#faf5ff', color: '#7c3aed', border: '#ddd6fe' },
                        PNG: { bg: '#faf5ff', color: '#7c3aed', border: '#ddd6fe' },
                        TXT: { bg: '#f9fafb', color: '#4b5563', border: '#d1d5db' },
                      };

                      const getFileIcon = (ft) => {
                        switch (ft) {
                          case 'PDF': return <i className="fa-solid fa-file-pdf" style={{ color: '#ef4444' }} />;
                          case 'DOCX': return <i className="fa-solid fa-file-word" style={{ color: '#2563eb' }} />;
                          case 'XLSX': return <i className="fa-solid fa-file-excel" style={{ color: '#16a34a' }} />;
                          case 'JPEG': case 'PNG': return <i className="fa-solid fa-file-image" style={{ color: '#8b5cf6' }} />;
                          default: return <i className="fa-solid fa-file-lines" style={{ color: '#6b7280' }} />;
                        }
                      };

                      // Build file entries
                      const fileEntries = [];
                      fileParamNames.forEach(paramName => {
                        const filename = String(cp[paramName] || 'Unknown file');
                        const fileId = cp[`__${paramName}_fileId`];
                        const fileType = cp[`__${paramName}_fileType`] || (() => {
                          const ext = filename.split('.').pop()?.toUpperCase();
                          return ext === 'JPG' ? 'JPEG' : ext;
                        })();
                        const fileSize = cp[`__${paramName}_fileSize`];
                        const fileHash = cp[`__${paramName}_fileHash`];
                        const hasContent = !!fileId;
                        const fileUrl = hasContent ? getFileViewUrl(fileId) : null;
                        const fileDownloadUrl = hasContent ? getFileDownloadUrl(fileId) : null;
                        fileEntries.push({ paramName, filename, fileId, fileType, fileSize, fileHash, hasContent, fileUrl, fileDownloadUrl });
                      });

                      // Build non-file entries
                      const nonFileItems = [];
                      Object.entries(cp).forEach(([key, value]) => {
                        if (fileParamNames.has(key) || metaKeys.has(key)) return;
                        nonFileItems.push(
                          <div key={key} style={{
                            display: 'flex', alignItems: 'center', gap: '10px',
                            padding: '8px 12px', borderRadius: '8px',
                            background: 'var(--bg-tertiary)', border: '1px solid var(--border-primary)',
                          }}>
                            <div style={{ flex: 1 }}>
                              <div style={{ fontSize: '11px', fontWeight: 600, color: 'var(--text-tertiary)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>{key}</div>
                              <div style={{ fontSize: '13px', fontWeight: 500, color: 'var(--text-primary)', marginTop: '1px' }}>{String(value)}</div>
                            </div>
                          </div>
                        );
                      });

                      const canPreview = (ft) => ['PDF', 'JPEG', 'PNG', 'TXT'].includes(ft);

                      return (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                          {/* File list with examine buttons */}
                          {fileEntries.length > 0 && (
                            <div style={{
                              border: '1px solid var(--border-primary)',
                              borderRadius: '8px',
                              overflow: 'hidden',
                            }}>
                              {/* Header with Download All */}
                              <div style={{
                                padding: '10px 14px',
                                background: 'var(--bg-tertiary)',
                                borderBottom: '1px solid var(--border-primary)',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                fontSize: '13px',
                                fontWeight: 600,
                                color: 'var(--text-primary)',
                              }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                  <i className="fa-solid fa-paperclip" style={{ fontSize: '14px' }} />
                                  {t('pendingRequests.details.filesCount', { count: fileEntries.length })}
                                </div>
                                {fileEntries.filter(f => f.hasContent).length > 1 && (
                                  <button
                                    onClick={() => fileEntries.forEach(f => { if (f.fileUrl) window.open(f.fileUrl, '_blank'); })}
                                    style={{
                                      display: 'flex', alignItems: 'center', gap: '6px',
                                      fontSize: '12px', padding: '4px 10px',
                                      background: 'var(--bg-primary)', border: '1px solid var(--border-primary)',
                                      borderRadius: '4px', color: 'var(--text-secondary)', cursor: 'pointer', fontWeight: 500,
                                    }}
                                  >
                                    <i className="fa-solid fa-download" style={{ fontSize: '11px' }} /> {t('pendingRequests.details.downloadAll')}
                                  </button>
                                )}
                              </div>

                              {/* Scrollable file list */}
                              <div style={{
                                maxHeight: fileEntries.length > 4 ? '280px' : 'none',
                                overflowY: fileEntries.length > 4 ? 'auto' : 'visible',
                              }}>
                                {fileEntries.map((file) => {
                                  const fc = fileColors[file.fileType] || fileColors.TXT;
                                  return (
                                    <div key={file.paramName} style={{
                                      padding: '10px 14px',
                                      display: 'flex',
                                      alignItems: 'center',
                                      gap: '12px',
                                      borderBottom: '1px solid var(--border-primary)',
                                    }}>
                                      <div style={{ fontSize: '20px', flexShrink: 0 }}>{getFileIcon(file.fileType)}</div>
                                      <div style={{ flex: 1, minWidth: 0 }}>
                                        <div style={{ fontSize: '13px', fontWeight: 500, color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                          {file.filename}
                                        </div>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '2px', flexWrap: 'wrap' }}>
                                          <span style={{ fontSize: '10px', fontWeight: 700, padding: '1px 6px', borderRadius: '4px', background: fc.bg, color: fc.color, border: `1px solid ${fc.border}`, letterSpacing: '0.5px' }}>
                                            {file.fileType}
                                          </span>
                                          <span style={{ fontSize: '10px', fontWeight: 600, color: 'var(--text-tertiary)', textTransform: 'uppercase', letterSpacing: '0.3px' }}>
                                            {file.paramName}
                                          </span>
                                          {file.fileSize && (
                                            <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
                                              {file.fileSize < 1024 ? file.fileSize + ' B' : file.fileSize < 1048576 ? (file.fileSize / 1024).toFixed(1) + ' KB' : (file.fileSize / 1048576).toFixed(1) + ' MB'}
                                            </span>
                                          )}
                                        </div>
                                      </div>
                                      <div style={{ flexShrink: 0 }}>
                                        {file.hasContent ? (
                                          <button
                                            onClick={() => setExamineCustomFile(file)}
                                            style={{
                                              display: 'flex', alignItems: 'center', gap: '6px',
                                              fontSize: '12px', padding: '5px 12px',
                                              background: 'var(--accent-primary, #2563eb)', color: '#fff',
                                              border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 500,
                                            }}
                                          >
                                            <i className="fa-solid fa-eye" style={{ fontSize: '11px' }} /> {t('pendingRequests.details.examine')}
                                          </button>
                                        ) : (
                                          <span style={{ fontSize: '11px', color: 'var(--text-tertiary)', fontStyle: 'italic' }}>
                                            {t('pendingRequests.details.referenceOnly')}
                                          </span>
                                        )}
                                      </div>
                                    </div>
                                  );
                                })}
                              </div>
                            </div>
                          )}

                          {/* Non-file params */}
                          {nonFileItems}
                        </div>
                      );
                    })()}
                  </div>
                </div>
              )}

              {/* Attached Files Section */}
              <div className="col-12">
                <div className="text-muted text-uppercase small fw-semibold mb-1">{t('pendingRequests.details.attachedFiles')}</div>
                <div>
                  <FileViewer
                    requestId={selectedRequest.requestId}
                    compact
                  />
                </div>
              </div>
              {selectedRequest.status === 'DENIED' && (
                <div className="col-12">
                  <div className="text-muted text-uppercase small fw-semibold mb-1">{t('pendingRequests.details.denialStatus')}</div>
                  <div>
                    <div style={{
                      background: 'var(--color-danger-bg, #fef2f2)',
                      border: '1px solid var(--color-danger-border, #fecaca)',
                      color: 'var(--color-danger-text, #991b1b)',
                      padding: '12px',
                      borderRadius: '6px',
                    }}>
                      <strong>{t('pendingRequests.details.deniedNotice')}</strong>
                      {selectedRequest.denialReason ? (
                        <div style={{ marginTop: '4px' }}>{t('pendingRequests.details.reason', { reason: selectedRequest.denialReason })}</div>
                      ) : (
                        <div style={{ marginTop: '4px', opacity: 0.7 }}>{t('pendingRequests.details.noReasonProvided')}</div>
                      )}
                    </div>
                  </div>
                </div>
              )}
              {selectedRequest.reviewedAt && (
                <>
                  <div className="col-md-6">
                    <div className="text-muted text-uppercase small fw-semibold mb-1">{t('pendingRequests.details.reviewedAt')}</div>
                    <div>
                      {format(new Date(selectedRequest.reviewedAt), 'MMM d, yyyy HH:mm')}
                    </div>
                  </div>
                  <div className="col-md-6">
                    <div className="text-muted text-uppercase small fw-semibold mb-1">{t('pendingRequests.details.reviewedByLabel')}</div>
                    <div>{selectedRequest.reviewedBy || '-'}</div>
                  </div>
                </>
              )}
              {selectedRequest.status !== 'DENIED' && selectedRequest.responseData && Object.keys(selectedRequest.responseData).length > 0 && (
                <div className="col-12">
                  <div className="text-muted text-uppercase small fw-semibold mb-1">{t('pendingRequests.details.responseData')}</div>
                  <div>
                    <pre style={{ 
                      background: 'var(--color-bg-tertiary)', 
                      padding: '12px', 
                      borderRadius: '4px',
                      fontSize: '12px',
                      maxHeight: '200px',
                      overflow: 'auto'
                    }}>
                      {JSON.stringify(selectedRequest.responseData, null, 2)}
                    </pre>
                  </div>
                </div>
              )}
            </div>

            {reviewAction !== 'view' && (
              <div style={{ marginTop: '24px' }}>
                {(reviewAction === 'approve' || reviewAction === 'review') && (
                  <div className="mb-3">
                    <label className="form-label">{t('pendingRequests.form.grantAccessLevel')}</label>
                    <select
                      className="form-select"
                      value={grantedLevel || ''}
                      onChange={(e) => setGrantedLevel(parseInt(e.target.value))}
                    >
                      <option value="0">{t('pendingRequests.form.accessLevelOptions.level0')}</option>
                      <option value="1">{t('pendingRequests.form.accessLevelOptions.level1')}</option>
                      <option value="2">{t('pendingRequests.form.accessLevelOptions.level2')}</option>
                      <option value="3">{t('pendingRequests.form.accessLevelOptions.level3')}</option>
                    </select>
                    {reviewAction === 'review' && (
                      <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                        {t('pendingRequests.form.grantAccessLevelHint')}
                      </p>
                    )}
                  </div>
                )}

                {(reviewAction === 'deny' || reviewAction === 'review') && (
                  <div className="mb-3">
                    <label className="form-label">{t('pendingRequests.form.denialReason')}</label>
                    <textarea
                      className="form-control"
                      value={denialReason}
                      onChange={(e) => setDenialReason(e.target.value)}
                      placeholder={t('pendingRequests.form.denialReasonPlaceholderRdap')}
                      rows={3}
                    />
                    <p style={{ fontSize: '12px', color: 'var(--text-tertiary)', marginTop: '4px' }}>
                      {reviewAction === 'review'
                        ? t('pendingRequests.form.denialReasonHintReview')
                        : t('pendingRequests.form.denialReasonHint')}
                    </p>
                  </div>
                )}

                <div className="mb-3">
                  <label className="form-label">{t('pendingRequests.form.adminNotesOptional')}</label>
                  <textarea
                    className="form-control"
                    value={adminNotes}
                    onChange={(e) => setAdminNotes(e.target.value)}
                    placeholder={t('pendingRequests.form.adminNotesPlaceholder')}
                  />
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* Examine File Overlay */}
      {examineCustomFile && (() => {
        const file = examineCustomFile;
        const fileColors = {
          PDF: { bg: '#fef2f2', color: '#dc2626', border: '#fecaca' },
          DOCX: { bg: '#eff6ff', color: '#2563eb', border: '#bfdbfe' },
          XLSX: { bg: '#f0fdf4', color: '#16a34a', border: '#bbf7d0' },
          JPEG: { bg: '#faf5ff', color: '#7c3aed', border: '#ddd6fe' },
          PNG: { bg: '#faf5ff', color: '#7c3aed', border: '#ddd6fe' },
          TXT: { bg: '#f9fafb', color: '#4b5563', border: '#d1d5db' },
        };
        const fc = fileColors[file.fileType] || fileColors.TXT;
        const canPreview = ['PDF', 'JPEG', 'PNG', 'TXT'].includes(file.fileType);
        const getFileIcon = (ft) => {
          switch (ft) {
            case 'PDF': return <i className="fa-solid fa-file-pdf" style={{ fontSize: '18px', color: '#ef4444' }} />;
            case 'DOCX': return <i className="fa-solid fa-file-word" style={{ fontSize: '18px', color: '#2563eb' }} />;
            case 'XLSX': return <i className="fa-solid fa-file-excel" style={{ fontSize: '18px', color: '#16a34a' }} />;
            case 'JPEG': case 'PNG': return <i className="fa-solid fa-file-image" style={{ fontSize: '18px', color: '#8b5cf6' }} />;
            default: return <i className="fa-solid fa-file-lines" style={{ fontSize: '18px', color: '#6b7280' }} />;
          }
        };

        return (
          <div
            onClick={() => setExamineCustomFile(null)}
            style={{
              position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
              backdropFilter: 'blur(4px)', zIndex: 2000,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            <div
              onClick={(e) => e.stopPropagation()}
              style={{
                width: '95%', maxWidth: '960px', maxHeight: '92vh',
                background: 'var(--bg-primary, #fff)', borderRadius: '12px',
                display: 'flex', flexDirection: 'column', overflow: 'hidden',
                boxShadow: '0 25px 60px rgba(0,0,0,0.3)',
              }}
            >
              {/* Header */}
              <div style={{
                padding: '16px 20px', borderBottom: '1px solid var(--border-primary)',
                display: 'flex', alignItems: 'center', gap: '12px',
                background: 'var(--bg-secondary, #f8f9fa)', flexShrink: 0,
              }}>
                <button
                  onClick={() => setExamineCustomFile(null)}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '4px', padding: '6px 10px',
                    fontSize: '13px', fontWeight: 500, color: 'var(--text-secondary)',
                    background: 'var(--bg-primary)', border: '1px solid var(--border-primary)',
                    borderRadius: '4px', cursor: 'pointer',
                  }}
                >
                  <i className="fa-solid fa-chevron-left" style={{ fontSize: '11px' }} /> {t('common.back')}
                </button>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    {getFileIcon(file.fileType)}
                    <span style={{ fontSize: '15px', fontWeight: 600, color: 'var(--text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {file.filename}
                    </span>
                    <span style={{ fontSize: '10px', fontWeight: 700, padding: '2px 8px', borderRadius: '4px', background: fc.bg, color: fc.color, border: `1px solid ${fc.border}` }}>
                      {file.fileType}
                    </span>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginTop: '4px', fontSize: '12px', color: 'var(--text-tertiary)' }}>
                    <span style={{ textTransform: 'uppercase', fontWeight: 600, letterSpacing: '0.3px' }}>{file.paramName}</span>
                    {file.fileSize && (
                      <span>{file.fileSize < 1024 ? file.fileSize + ' B' : file.fileSize < 1048576 ? (file.fileSize / 1024).toFixed(1) + ' KB' : (file.fileSize / 1048576).toFixed(1) + ' MB'}</span>
                    )}
                    {file.fileHash && <span>SHA-256: <code style={{ fontSize: '10px' }}>{file.fileHash.substring(0, 16)}...</code></span>}
                  </div>
                </div>
                <button
                  onClick={() => setExamineCustomFile(null)}
                  style={{ width: '32px', height: '32px', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-tertiary)', fontSize: '16px', borderRadius: '50%', flexShrink: 0 }}
                >
                  <i className="fa-solid fa-xmark" />
                </button>
              </div>

              {/* Preview body */}
              <div style={{ flex: 1, overflow: 'auto', minHeight: 0, background: 'var(--bg-tertiary, #f0f0f0)' }}>
                {canPreview && file.fileUrl ? (
                  <>
                    {file.fileType === 'PDF' && (
                      <iframe src={file.fileUrl} style={{ width: '100%', height: '100%', border: 'none', minHeight: '500px' }} title={file.filename} />
                    )}
                    {(file.fileType === 'JPEG' || file.fileType === 'PNG') && (
                      <div style={{ padding: '24px', display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '400px' }}>
                        <img src={file.fileUrl} alt={file.filename} style={{ maxWidth: '100%', maxHeight: '60vh', borderRadius: '8px', boxShadow: '0 4px 20px rgba(0,0,0,0.15)' }} />
                      </div>
                    )}
                    {file.fileType === 'TXT' && (
                      <div style={{ padding: '16px' }}>
                        <iframe src={file.fileUrl} style={{ width: '100%', height: '55vh', border: '1px solid var(--border-primary)', borderRadius: '4px', background: 'var(--bg-primary, #fff)' }} title={file.filename} />
                      </div>
                    )}
                  </>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '60px 20px', minHeight: '300px', color: 'var(--text-tertiary)' }}>
                    <div style={{ fontSize: '48px', marginBottom: '16px', opacity: 0.5 }}>{getFileIcon(file.fileType)}</div>
                    <p style={{ fontSize: '14px', marginBottom: '4px' }}>{t('pendingRequests.preview.notAvailableBefore')} <strong>{file.fileType}</strong> {t('pendingRequests.preview.notAvailableAfter')}</p>
                    <p style={{ fontSize: '13px', opacity: 0.7 }}>{t('pendingRequests.preview.downloadHint')}</p>
                  </div>
                )}
              </div>

              {/* Footer with actions */}
              <div style={{
                padding: '14px 20px', borderTop: '1px solid var(--border-primary)',
                display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: '12px',
                background: 'var(--bg-secondary, #f8f9fa)', flexShrink: 0, flexWrap: 'wrap',
              }}>
                {file.fileUrl && (
                  <a
                    href={file.fileDownloadUrl || file.fileUrl} download={file.filename} target="_blank" rel="noopener noreferrer"
                    className="btn btn-primary btn-sm"
                    style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '13px', padding: '7px 16px', textDecoration: 'none', fontWeight: 600 }}
                  >
                    <i className="fa-solid fa-download" style={{ fontSize: '12px' }} /> {t('common.download')}
                  </a>
                )}
              </div>
            </div>
          </div>
        );
      })()}
    </div>
  );
};

export default PendingRequests;