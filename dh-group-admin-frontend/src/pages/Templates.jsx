/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback, useContext } from 'react';
import { getTemplates, createTemplate, updateTemplate, deleteTemplate, toggleTemplateActive,
  getCopyableTemplates, copyTemplate,
         setTemplateVisibility, getTemplateSubscribers, publishTemplateChanges } from '../services/api';
import { GroupContext } from '../context/GroupContext';
import TemplateFormModal from '../components/TemplateFormModal';
import Pagination from '../components/Pagination';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const ACCESS_LEVEL_KEYS = ['public', 'basic', 'enhanced', 'full'];
const ACCESS_LEVEL_COLORS = ['secondary', 'info', 'warning', 'danger'];

/** How far a template is advertised; the group setting caps it. */
const VISIBILITY = [
  { value: 'PRIVATE', icon: 'fa-lock', color: 'secondary' },
  { value: 'PUBLIC', icon: 'fa-list', color: 'info' },
  { value: 'GLOBAL', icon: 'fa-globe', color: 'primary' },
];

const VisibilityBadge = ({ template }) => {
  const { t } = useT();
  const own = template.visibility || 'PUBLIC';
  const effective = template.effectiveVisibility || own;
  const clamped = effective !== own;
  const meta = VISIBILITY.find(v => v.value === effective) || VISIBILITY[1];
  return (
    <span
      className={`badge bg-${clamped ? 'warning' : meta.color}${clamped ? ' text-dark' : ''}`}
      style={{ fontSize: 10 }}
      title={template.visibilityNote || t(`templates.visibility.${effective.toLowerCase()}Hint`)}
    >
      <i className={`fa-solid ${meta.icon} me-1`}></i>
      {t(`templates.visibility.${effective.toLowerCase()}`)}
      {clamped && <i className="fa-solid fa-triangle-exclamation ms-1"></i>}
    </span>
  );
};

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
  const [publishTarget, setPublishTarget] = useState(null);
  const [subscribers, setSubscribers] = useState([]);
  const [modes, setModes] = useState({});
  const [publishing, setPublishing] = useState(false);
  const [respondByDays, setRespondByDays] = useState(30);
  const [showCopy, setShowCopy] = useState(false);
  const [copyables, setCopyables] = useState([]);
  const [copyLoading, setCopyLoading] = useState(false);
  const [copySearch, setCopySearch] = useState('');
  const [copyingId, setCopyingId] = useState(null);
  const [chosenGroupId, setChosenGroupId] = useState(null);
  const [pendingAction, setPendingAction] = useState(null);

  const openCopy = async (groupId) => {
    setShowCopy(true);
    setCopySearch('');
    setCopyables([]);
    setCopyLoading(true);
    try {
      const res = await getCopyableTemplates(groupId);
      setCopyables(Array.isArray(res) ? res : []);
    } catch (e) {
      toast.error(e.data?.message || t('templates.copy.loadFailed'));
    } finally {
      setCopyLoading(false);
    }
  };

  /* The copy lands in the group in context, private and inactive, and opens for review. */
  const handleCopy = async (source) => {
    setCopyingId(source.id);
    try {
      const res = await copyTemplate(source.id, { dataHolderGroupId: targetGroupId });
      if (res.success) {
        toast.success(t('templates.copy.copied', { name: source.name }));
        setShowCopy(false);
        await load();
        if (res.template) { setEditing(res.template); setShowForm(true); }
      } else {
        toast.error(res.message || t('templates.copy.failed'));
      }
    } catch (e) {
      toast.error(e.data?.message || t('templates.copy.failed'));
    } finally {
      setCopyingId(null);
    }
  };

  /* Both actions need a group; ask for one when the header is on all groups. */
  const startCreate = () => {
    if (!targetGroupId) { setPendingAction('create'); return; }
    setEditing(null);
    setShowForm(true);
  };

  const startCopy = () => {
    if (!targetGroupId) { setPendingAction('copy'); return; }
    openCopy(targetGroupId);
  };

  const chooseGroup = (groupId) => {
    setChosenGroupId(groupId);
    const action = pendingAction;
    setPendingAction(null);
    if (action === 'create') { setEditing(null); setShowForm(true); }
    if (action === 'copy') openCopy(groupId);
  };

  const openPublish = async (tpl) => {
    setPublishTarget(tpl);
    setSubscribers([]);
    setModes({});
    try {
      const res = await getTemplateSubscribers(tpl.id);
      const rows = res?.subscribers || [];
      setSubscribers(rows);
      setModes(Object.fromEntries(rows.map(r => [r.subscriptionId, 'PROPOSE'])));
    } catch (e) {
      setSubscribers([]);
    }
  };

  const setAllModes = (mode) =>
    setModes(Object.fromEntries(subscribers.map(r => [r.subscriptionId, mode])));

  const applyPublish = async () => {
    setPublishing(true);
    try {
      await publishTemplateChanges(publishTarget.id,
        subscribers.map(r => ({ subscriptionId: r.subscriptionId, mode: modes[r.subscriptionId] || 'PROPOSE' })),
        Number(respondByDays) || 30);
      setPublishTarget(null);
    } finally {
      setPublishing(false);
    }
  };
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

  const handleToggleActive = async (tpl) => {
    try {
      const res = await toggleTemplateActive(tpl.id);
      if (res.success) {
        if (res.isActive) {
          toast.success(t('templates.toasts.activated'));
        } else if (res.activeSubscriptions > 0) {
          toast.success(t('templates.toasts.deactivatedWithSubscriptions', { count: res.activeSubscriptions }));
        } else {
          toast.success(t('templates.toasts.deactivated'));
        }
        load();
      }
    } catch { toast.error(t('templates.errors.toggleActiveFailed')); }
  };

  const handleVisibilityChange = async (tpl, visibility) => {
    try {
      const res = await setTemplateVisibility(tpl.id, visibility);
      if (res.success) {
        if (res.visibilityNote) toast(res.visibilityNote, { icon: '\u26a0\ufe0f' });
        else toast.success(t(`templates.toasts.visibility.${visibility.toLowerCase()}`));
        load();
      }
    } catch { toast.error(t('templates.errors.visibilityFailed')); }
  };

  if (loading && templates.length === 0) {
    return <div className="d-flex justify-content-center py-5"><div className="spinner-border text-primary" /></div>;
  }
  const active = templates.filter(tpl => tpl.isActive).length;
  const global = templates.filter(tpl => (tpl.effectiveVisibility || tpl.visibility) === 'GLOBAL' && tpl.isActive).length;
  /* The group a new agreement belongs to: the one in context, or the one a master picked. */
  const targetGroupId = selectedGroupId ?? chosenGroupId;
  const canCreate = groups.length > 0;

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
          <button
            className="btn btn-outline-primary"
            onClick={startCopy}
            disabled={!canCreate}
            title={canCreate ? t('templates.copy.buttonTitle') : t('templates.noGroups')}
          >
            <i className="fa-solid fa-copy me-1"></i> {t('templates.copy.button')}
          </button>
          <button
            className="btn btn-primary"
            onClick={startCreate}
            disabled={!canCreate}
            title={canCreate ? undefined : t('templates.noGroups')}
          >
            <i className="fa-solid fa-plus me-1"></i> {t('templates.newTemplate')}
          </button>
        </div>
      </div>
      <div className="row g-3 mb-4">
        <div className="col"><div className="card text-center p-3"><div className="fs-3 fw-bold text-primary">{totalItems}</div><small className="text-muted">{t('templates.stats.total')}</small></div></div>
        <div className="col"><div className="card text-center p-3"><div className="fs-3 fw-bold text-success">{active}</div><small className="text-muted">{t('templates.stats.activePage')}</small></div></div>
        <div className="col"><div className="card text-center p-3"><div className="fs-3 fw-bold text-secondary">{templates.length - active}</div><small className="text-muted">{t('templates.stats.inactivePage')}</small></div></div>
        <div className="col"><div className="card text-center p-3"><div className="fs-3 fw-bold text-info">{global}</div><small className="text-muted">{t('templates.stats.globalPage')}</small></div></div>
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
                <th>{t('templates.table.visibility')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {templates.length === 0 ? (
                <tr><td colSpan={isAllMode ? 8 : 7} className="text-center text-muted py-5">
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
                    ) : <span className="text-muted">—</span>}
                  </td>
                  <td><span className="small text-muted">{tpl.requiredGroupTypes || t('templates.any')}</span></td>
                  <td>
                    <button
                      className={`badge border-0 bg-${tpl.isActive ? 'success' : 'secondary'}`}
                      onClick={() => handleToggleActive(tpl)}
                      title={tpl.isActive ? t('templates.clickToDeactivate') : t('templates.clickToActivate')}
                    >
                      {tpl.isActive ? t('templates.active') : t('templates.inactive')}
                    </button>
                  </td>
                  <td>
                    <div className="d-flex align-items-center gap-2">
                      <select
                        className="form-select form-select-sm"
                        style={{ width: 'auto' }}
                        value={tpl.visibility || 'PUBLIC'}
                        onChange={e => handleVisibilityChange(tpl, e.target.value)}
                        aria-label={t('templates.table.visibility')}
                      >
                        {VISIBILITY.map(v => (
                          <option key={v.value} value={v.value}>
                            {t(`templates.visibility.${v.value.toLowerCase()}`)}
                          </option>
                        ))}
                      </select>
                      {(tpl.effectiveVisibility || tpl.visibility) !== (tpl.visibility || 'PUBLIC') && (
                        <VisibilityBadge template={tpl} />
                      )}
                    </div>
                  </td>
                  <td>
                    <div className="btn-group btn-group-sm">
                      <button className="btn btn-outline-secondary" onClick={() => openPublish(tpl)} title={t('templates.publishChanges.action')}>
                        <i className="fa-solid fa-bullhorn"></i>
                      </button>
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
        lockedGroupId={targetGroupId}
      />

      {/* Which group a new agreement belongs to, asked only when the header is on all groups */}
      {pendingAction && (
        <div className="modal show d-block" style={{ background: 'rgba(0,0,0,.5)' }} onClick={() => setPendingAction(null)}>
          <div className="modal-dialog modal-dialog-centered" onClick={e => e.stopPropagation()}>
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className="fa-solid fa-layer-group me-2"></i>{t('templates.chooseGroup.title')}
                </h5>
                <button type="button" className="btn-close" onClick={() => setPendingAction(null)}></button>
              </div>
              <div className="modal-body">
                <p className="form-text mt-0">{t('templates.chooseGroup.help')}</p>
                <div className="list-group">
                  {groups.map(g => (
                    <button
                      type="button"
                      className="list-group-item list-group-item-action"
                      key={g.id}
                      onClick={() => chooseGroup(g.id)}
                    >
                      {g.name}
                    </button>
                  ))}
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setPendingAction(null)}>{t('common.cancel')}</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Copy picker: agreements this group may take a copy of */}
      {showCopy && (
        <div className="modal show d-block" style={{ background: 'rgba(0,0,0,.5)' }} onClick={() => setShowCopy(false)}>
          <div className="modal-dialog modal-lg modal-dialog-scrollable" onClick={e => e.stopPropagation()}>
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className="fa-solid fa-copy me-2"></i>{t('templates.copy.title')}
                </h5>
                <button type="button" className="btn-close" onClick={() => setShowCopy(false)}></button>
              </div>
              <div className="modal-body">
                <p className="form-text mt-0">{t('templates.copy.help')}</p>

                <div className="input-group input-group-sm mb-3">
                  <span className="input-group-text"><i className="fa-solid fa-magnifying-glass"></i></span>
                  <input
                    className="form-control"
                    placeholder={t('templates.copy.searchPlaceholder')}
                    value={copySearch}
                    onChange={e => setCopySearch(e.target.value)}
                  />
                </div>

                {copyLoading ? (
                  <div className="text-center text-muted py-4">
                    <span className="spinner-border spinner-border-sm me-2"></span>{t('common.loading')}
                  </div>
                ) : (() => {
                  const term = copySearch.trim().toLowerCase();
                  const shown = copyables.filter(tpl => !term
                    || (tpl.name || '').toLowerCase().includes(term)
                    || (tpl.shortDescription || '').toLowerCase().includes(term)
                    || (tpl.dataHolderGroupName || '').toLowerCase().includes(term));
                  if (shown.length === 0) {
                    return <div className="text-muted small fst-italic py-3">{t('templates.copy.none')}</div>;
                  }
                  return (
                    <div className="list-group">
                      {shown.map(tpl => (
                        <div className="list-group-item" key={tpl.id}>
                          <div className="d-flex justify-content-between align-items-start gap-3">
                            <div>
                              <div className="fw-semibold">
                                {tpl.name}
                                {tpl.ownGroup ? (
                                  <span className="badge bg-light text-dark border ms-2">{t('templates.copy.ownGroup')}</span>
                                ) : (
                                  <span className="badge bg-light text-dark border ms-2">{tpl.dataHolderGroupName}</span>
                                )}
                                <span className={`badge ms-2 bg-${
                                  (tpl.effectiveVisibility || tpl.visibility) === 'GLOBAL' ? 'primary' : 'secondary'}`}>
                                  {t(`templates.visibility.${(tpl.effectiveVisibility || tpl.visibility || 'PUBLIC').toLowerCase()}`)}
                                </span>
                              </div>
                              {tpl.shortDescription && <div className="small text-muted">{tpl.shortDescription}</div>}
                              <div className="small text-muted">
                                {t('templates.copy.counts', {
                                  requestTypes: tpl.requestTypeCount,
                                  sections: tpl.legalSectionCount,
                                  fields: tpl.subscriptionFieldCount,
                                })}
                              </div>
                            </div>
                            <button
                              className="btn btn-sm btn-primary flex-shrink-0"
                              onClick={() => handleCopy(tpl)}
                              disabled={copyingId != null}
                            >
                              {copyingId === tpl.id
                                ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('templates.copy.copying')}</>
                                : t('templates.copy.action')}
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  );
                })()}
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setShowCopy(false)}>{t('common.close')}</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {publishTarget && (
        <div className="modal show d-block" style={{ background: 'rgba(0,0,0,.5)' }} onClick={() => setPublishTarget(null)}>
          <div className="modal-dialog modal-lg" onClick={e => e.stopPropagation()}>
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className="fa-solid fa-bullhorn me-2"></i>
                  {t('templates.publishChanges.title', { name: publishTarget.name })}
                </h5>
                <button type="button" className="btn-close" onClick={() => setPublishTarget(null)}></button>
              </div>
              <div className="modal-body">
                <p className="text-muted small">{t('templates.publishChanges.help')}</p>
                {subscribers.length === 0 ? (
                  <div className="text-muted fst-italic">{t('templates.publishChanges.noSubscribers')}</div>
                ) : (
                  <>
                    <div className="d-flex align-items-center gap-2 mb-2 flex-wrap">
                      <label className="small mb-0">{t('templates.publishChanges.respondBy')}</label>
                      <input type="number" min="1" max="365" className="form-control form-control-sm"
                             style={{ width: 90 }} value={respondByDays}
                             onChange={e => setRespondByDays(e.target.value)} />
                      <span className="small text-muted">{t('templates.publishChanges.respondByHint')}</span>
                    </div>
                    <div className="d-flex gap-2 mb-2">
                      <button className="btn btn-sm btn-outline-danger" onClick={() => setAllModes('FORCE')}>
                        {t('templates.publishChanges.allForce')}
                      </button>
                      <button className="btn btn-sm btn-outline-primary" onClick={() => setAllModes('PROPOSE')}>
                        {t('templates.publishChanges.allPropose')}
                      </button>
                    </div>
                    <table className="table table-sm align-middle">
                      <thead>
                        <tr>
                          <th>{t('templates.publishChanges.subscriber')}</th>
                          <th>{t('templates.publishChanges.state')}</th>
                          <th style={{ width: 200 }}>{t('templates.publishChanges.mode')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {subscribers.map(r => (
                          <tr key={r.subscriptionId}>
                            <td>
                              {r.requestorGroupName}
                              {r.requestorGroupCode && <span className="badge bg-light text-muted border ms-2">{r.requestorGroupCode}</span>}
                            </td>
                            <td>
                              {r.pendingChangeStatus === 'PROPOSED'
                                ? <span className="badge bg-warning text-dark">{t('templates.publishChanges.awaiting')}</span>
                                : r.outOfDate
                                  ? <span className="badge bg-secondary">{t('templates.publishChanges.outOfDate')}</span>
                                  : <span className="badge bg-success">{t('templates.publishChanges.upToDate')}</span>}
                            </td>
                            <td>
                              <div className="btn-group btn-group-sm w-100">
                                <button className={`btn ${modes[r.subscriptionId] === 'FORCE' ? 'btn-danger' : 'btn-outline-danger'}`}
                                        onClick={() => setModes({ ...modes, [r.subscriptionId]: 'FORCE' })}>
                                  {t('templates.publishChanges.force')}
                                </button>
                                <button className={`btn ${modes[r.subscriptionId] !== 'FORCE' ? 'btn-primary' : 'btn-outline-primary'}`}
                                        onClick={() => setModes({ ...modes, [r.subscriptionId]: 'PROPOSE' })}>
                                  {t('templates.publishChanges.propose')}
                                </button>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </>
                )}
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setPublishTarget(null)}>{t('common.cancel')}</button>
                <button className="btn btn-primary" disabled={publishing || subscribers.length === 0} onClick={applyPublish}>
                  {t('templates.publishChanges.apply')}
                </button>
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
