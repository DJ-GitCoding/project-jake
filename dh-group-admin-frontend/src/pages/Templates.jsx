/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback, useContext } from 'react';
import { getTemplates, createTemplate, updateTemplate, deleteTemplate, toggleTemplatePublish } from '../services/api';
import { GroupContext } from '../context/GroupContext';
import TemplateFormModal from '../components/TemplateFormModal';
import Pagination from '../components/Pagination';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const ACCESS_LEVEL_KEYS = ['public', 'basic', 'enhanced', 'full'];
const ACCESS_LEVEL_COLORS = ['secondary', 'info', 'warning', 'danger'];

const AccessBadge = ({ level }) => {
  const { t } = useT();
  return (
    <span className={`badge bg-${ACCESS_LEVEL_COLORS[level] || 'secondary'}`}>
      L{level} {ACCESS_LEVEL_KEYS[level] ? t(`templates.accessLevels.${ACCESS_LEVEL_KEYS[level]}`) : ''}
    </span>
  );
};

const Templates = () => {
  const { t } = useT();
  const { selectedGroupId, isAllMode, groups, isMaster } = useContext(GroupContext);
  const [templates, setTemplates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  // Server-side pagination state
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [totalItems, setTotalItems] = useState(0);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');

  // Debounce the search box (300ms) before hitting the server.
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(t);
  }, [search]);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      // Group scoping is applied server-side via dataHolderGroupId.
      const res = await getTemplates({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        dataHolderGroupId: isAllMode ? undefined : selectedGroupId,
        sortBy: 'name',
        sortDir: 'asc',
      });
      setTemplates(res.content || []);
      setTotalItems(res.totalElements || 0);
    } catch { toast.error(t('templates.errors.loadFailed')); }
    finally { setLoading(false); }
  }, [page, pageSize, debouncedSearch, isAllMode, selectedGroupId]);

  useEffect(() => { load(); }, [load]);

  // Reset to the first page when the search term or group scope changes.
  useEffect(() => { setPage(1); }, [debouncedSearch, isAllMode, selectedGroupId]);

  // Build group name lookup
  const groupNameMap = {};
  groups.forEach(g => { groupNameMap[g.id] = g.name; });

  const handleSave = async (data) => {
    setSubmitting(true);
    try {
      const res = editing ? await updateTemplate(editing.id, data) : await createTemplate(data);
      if (res.success) {
        toast.success(editing ? t('templates.toasts.updated') : t('templates.toasts.created'));
        setShowForm(false);
        setEditing(null);
        load();
      } else { toast.error(res.error || t('templates.errors.failed')); }
    } catch { toast.error(t('templates.errors.saveFailed')); }
    finally { setSubmitting(false); }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setSubmitting(true);
    try {
      const res = await deleteTemplate(deleteTarget.id);
      if (res.success) { toast.success(t('templates.toasts.deleted')); setDeleteTarget(null); load(); }
      else { toast.error(res.error || t('templates.errors.deleteFailed')); }
    } catch { toast.error(t('templates.errors.deleteFailed')); }
    finally { setSubmitting(false); }
  };

  const handleTogglePublish = async (tpl) => {
    try {
      const res = await toggleTemplatePublish(tpl.id);
      if (res.success) {
        toast.success(res.isPublished ? t('templates.toasts.published') : t('templates.toasts.unpublished'));
        load();
      }
    } catch { toast.error(t('templates.errors.togglePublishFailed')); }
  };

  if (loading && templates.length === 0) {
    return <div className="d-flex justify-content-center py-5"><div className="spinner-border text-primary" /></div>;
  }

  // Published/draft counts reflect the current page only (server-side pagination).
  const published = templates.filter(t => t.isPublished).length;

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">{t('templates.title')}</h2>
          <p className="text-muted mb-0">
            {t('templates.count', { count: totalItems })}
            {isAllMode && <span className="badge bg-info ms-2" style={{ fontSize: 10 }}>{t('common.allGroups')}</span>}
          </p>
        </div>
        <div className="d-flex gap-2">
          <div className="input-group input-group-sm" style={{ maxWidth: 260 }}>
            <span className="input-group-text"><i className="fa-solid fa-magnifying-glass"></i></span>
            <input
              className="form-control"
              placeholder={t('templates.searchPlaceholder')}
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
            {search && (
              <button className="btn btn-outline-secondary" onClick={() => setSearch('')} title={t('common.clear')}>
                <i className="fa-solid fa-xmark"></i>
              </button>
            )}
          </div>
          <button className="btn btn-outline-secondary btn-sm" onClick={load}>
            <i className="fa-solid fa-arrows-rotate me-1"></i> {t('common.refresh')}
          </button>
          <button className="btn btn-primary" onClick={() => { setEditing(null); setShowForm(true); }}>
            <i className="fa-solid fa-plus me-1"></i> {t('templates.newTemplate')}
          </button>
        </div>
      </div>

      {/* Stats (published/draft reflect the current page) */}
      <div className="row g-3 mb-4">
        <div className="col"><div className="card text-center p-3"><div className="fs-3 fw-bold text-primary">{totalItems}</div><small className="text-muted">{t('templates.stats.total')}</small></div></div>
        <div className="col"><div className="card text-center p-3"><div className="fs-3 fw-bold text-success">{published}</div><small className="text-muted">{t('templates.stats.publishedPage')}</small></div></div>
        <div className="col"><div className="card text-center p-3"><div className="fs-3 fw-bold text-secondary">{templates.length - published}</div><small className="text-muted">{t('templates.stats.draftPage')}</small></div></div>
      </div>

      {/* Table */}
      <div className="card">
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th>{t('templates.table.templateId')}</th>
                <th>{t('common.name')}</th>
                {isAllMode && <th>{t('templates.table.group')}</th>}
                <th>{t('templates.table.requestTypes')}</th>
                <th>{t('templates.table.requiredGroups')}</th>
                <th>{t('common.status')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {templates.length === 0 ? (
                <tr><td colSpan={isAllMode ? 7 : 6} className="text-center text-muted py-5">
                  <i className="fa-solid fa-file-circle-plus fa-2x mb-2"></i><br />
                  {t('templates.empty')}
                </td></tr>
              ) : templates.map(tpl => (
                <tr key={tpl.id}>
                  <td><code className="small">{tpl.templateId}</code></td>
                  <td>
                    <strong>{tpl.name}</strong>
                    {tpl.shortDescription && <div className="text-muted small">{tpl.shortDescription}</div>}
                  </td>
                  {isAllMode && (
                    <td>
                      <span className="badge bg-light text-dark border">
                        {groupNameMap[tpl.dataHolderGroupId] || '—'}
                      </span>
                    </td>
                  )}
                  <td>
                    {tpl.requestTypes && tpl.requestTypes.length > 0 ? (
                      <div className="d-flex flex-column gap-1">
                        {tpl.requestTypes.map((rt, i) => {
                          const paramCount = (rt.customParameters || []).length;
                          return (
                            <div key={i} className="d-flex align-items-center gap-1">
                              {rt.supportsExigent ? <span className="badge bg-danger" style={{ fontSize: 10 }}>{t('templates.requestType.exigent')}</span>
                                : rt.supportsConfidential ? <span className="badge bg-warning text-dark" style={{ fontSize: 10 }}>{t('templates.requestType.confidential')}</span>
                                : <span className="badge bg-secondary" style={{ fontSize: 10 }}>{t('templates.requestType.standard')}</span>}
                              <AccessBadge level={rt.accessLevel} />
                              {paramCount > 0 && (
                                <span className="badge bg-primary" style={{ fontSize: 10 }} title={paramCount !== 1 ? t('templates.customParameters.plural', { count: paramCount }) : t('templates.customParameters.singular', { count: paramCount })}>
                                  <i className="fa-solid fa-cube me-1"></i>{paramCount}
                                </span>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    ) : <AccessBadge level={tpl.defaultAccessLevel || 0} />}
                  </td>
                  <td><span className="small text-muted">{tpl.requiredGroupTypes || t('templates.any')}</span></td>
                  <td>
                    <button
                      className={`badge border-0 bg-${tpl.isPublished ? 'success' : 'secondary'}`}
                      onClick={() => handleTogglePublish(tpl)}
                      title={t('templates.clickToToggle')}
                    >
                      {tpl.isPublished ? t('templates.published') : t('templates.draft')}
                    </button>
                  </td>
                  <td>
                    <div className="btn-group btn-group-sm">
                      <button className="btn btn-outline-primary" onClick={() => { setEditing(tpl); setShowForm(true); }} title={t('common.edit')}>
                        <i className="fa-solid fa-pen"></i>
                      </button>
                      <button className="btn btn-outline-danger" onClick={() => setDeleteTarget(tpl)} title={t('common.delete')}>
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
              itemLabel={t('templates.itemLabel')}
            />
          )}
        </div>
      </div>

      {/* Template Form Modal */}
      <TemplateFormModal
        show={showForm}
        onHide={() => { setShowForm(false); setEditing(null); }}
        onSave={handleSave}
        template={editing}
        submitting={submitting}
        groups={groups}
        isMaster={isMaster}
      />

      {/* Delete Confirm Modal */}
      {deleteTarget && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title"><i className="fa-solid fa-triangle-exclamation text-danger me-2"></i>{t('templates.deleteModal.title')}</h5>
                <button type="button" className="btn-close" onClick={() => setDeleteTarget(null)}></button>
              </div>
              <div className="modal-body">
                <p>{t('templates.deleteModal.confirmPrefix')} <strong>{deleteTarget.name}</strong>?</p>
                <p className="text-muted small mb-0">{t('templates.deleteModal.templateIdLabel')} <code>{deleteTarget.templateId}</code></p>
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

export default Templates;
