/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  getDataHolderGroups, createDataHolderGroup, updateDataHolderGroup, deleteDataHolderGroup,
  getGroupMembers, addGroupMember, removeGroupMember, getUsers
} from '../services/api';
import { useConfirm } from '../components/AlertModal';
import Pagination from '../components/Pagination';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const formatDate = (d) => d ? new Date(d).toLocaleString() : '—';

const DataHolderGroups = ({ onGroupsChanged }) => {
  const { confirm, ConfirmDialog } = useConfirm();
  const { t } = useT();
  const [groups, setGroups] = useState([]);
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
  const [form, setForm] = useState({ name: '', description: '', isPrivate: false });
  const [deleteTarget, setDeleteTarget] = useState(null);

  // Members modal
  const [membersGroup, setMembersGroup] = useState(null);
  const [members, setMembers] = useState([]);
  const [membersLoading, setMembersLoading] = useState(false);
  const [allUsers, setAllUsers] = useState([]);
  const [addUserId, setAddUserId] = useState('');

  // Debounce the search box (300ms) before hitting the server.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      const res = await getDataHolderGroups({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        sortBy: 'name',
        sortDir: 'asc',
      });
      setGroups(res.content || []);
      setTotalItems(res.totalElements || 0);
    } catch {
      toast.error(t('dataHolderGroups.toast.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, debouncedSearch]);

  useEffect(() => { load(); }, [load]);

  // Reset to the first page when the search term changes.
  useEffect(() => { setPage(1); }, [debouncedSearch]);

  const openCreate = () => {
    setEditing(null);
    setForm({ name: '', description: '', isPrivate: false });
    setShowForm(true);
  };

  const openEdit = (g) => {
    setEditing(g);
    setForm({ name: g.name || '', description: g.description || '', isPrivate: g.isPrivate || false });
    setShowForm(true);
  };

  const handleSave = async () => {
    if (!form.name.trim()) {
      toast.error(t('dataHolderGroups.validation.nameRequired'));
      return;
    }
    setSubmitting(true);
    try {
      const res = editing
        ? await updateDataHolderGroup(editing.id, form)
        : await createDataHolderGroup(form);
      if (res.success) {
        toast.success(editing ? t('dataHolderGroups.toast.updated') : t('dataHolderGroups.toast.created'));
        setShowForm(false);
        load();
        if (onGroupsChanged) onGroupsChanged();
      } else {
        toast.error(res.error || t('common.failed'));
      }
    } catch (e) {
      toast.error(e.data?.error || e.message || t('dataHolderGroups.toast.saveFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleActive = async (group) => {
    try {
      const res = await updateDataHolderGroup(group.id, { isActive: !group.isActive });
      if (res.success) {
        toast.success(group.isActive
          ? t('dataHolderGroups.toast.disabled', { name: group.name })
          : t('dataHolderGroups.toast.enabled', { name: group.name }));
        load();
        if (onGroupsChanged) onGroupsChanged();
      }
    } catch {
      toast.error(t('dataHolderGroups.toast.statusUpdateFailed'));
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setSubmitting(true);
    try {
      const res = await deleteDataHolderGroup(deleteTarget.id);
      if (res.success) {
        toast.success(t('dataHolderGroups.toast.deleted'));
        setDeleteTarget(null);
        load();
        if (onGroupsChanged) onGroupsChanged();
      } else {
        toast.error(res.error || t('dataHolderGroups.toast.deleteFailed'));
      }
    } catch (e) {
      toast.error(e.data?.error || e.message || t('dataHolderGroups.toast.deleteFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  // Members management
  const openMembers = async (group) => {
    setMembersGroup(group);
    setMembersLoading(true);
    setAddUserId('');
    try {
      const [membersRes, usersRes] = await Promise.all([
        getGroupMembers(group.id),
        getUsers(),
      ]);
      setMembers(membersRes || []);
      setAllUsers(usersRes || []);
    } catch {
      toast.error(t('dataHolderGroups.members.toast.loadFailed'));
    } finally {
      setMembersLoading(false);
    }
  };

  const handleAddMember = async () => {
    if (!addUserId) return;
    try {
      const res = await addGroupMember(membersGroup.id, { userId: parseInt(addUserId) });
      if (res.success) {
        toast.success(t('dataHolderGroups.members.toast.added'));
        setAddUserId('');
        openMembers(membersGroup);
      } else {
        toast.error(res.error || t('common.failed'));
      }
    } catch (e) {
      toast.error(e.data?.error || e.message || t('dataHolderGroups.members.toast.addFailed'));
    }
  };

  const handleRemoveMember = async (userId) => {
    const ok = await confirm(
      t('dataHolderGroups.members.removeConfirm.body'),
      t('dataHolderGroups.members.removeConfirm.title'),
      { confirmLabel: t('common.remove'), confirmVariant: 'danger' }
    );
    if (!ok) return;
    try {
      const res = await removeGroupMember(membersGroup.id, userId);
      if (res.success) {
        toast.success(t('dataHolderGroups.members.toast.removed'));
        openMembers(membersGroup);
      } else {
        toast.error(res.error || t('common.failed'));
      }
    } catch (e) {
      toast.error(e.data?.error || e.message || t('dataHolderGroups.members.toast.removeFailed'));
    }
  };

  if (loading && groups.length === 0) {
    return <div className="d-flex justify-content-center py-5"><div className="spinner-border text-primary" /></div>;
  }

  // Users eligible to be added (non-master, not already members)
  const memberUserIds = new Set(members.map(m => m.userId));
  const eligibleUsers = allUsers.filter(u => u.type !== 1 && !memberUserIds.has(u.id));

  return (
    <div>
      {ConfirmDialog}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">{t('dataHolderGroups.title')}</h2>
          <p className="text-muted mb-0">{t('dataHolderGroups.groupCount', { count: totalItems })}</p>
        </div>
        <div className="d-flex gap-2">
          <button className="btn btn-outline-secondary btn-sm" onClick={load}>
            <i className="fa-solid fa-arrows-rotate me-1"></i>{t('common.refresh')}
          </button>
          <button className="btn btn-primary" onClick={openCreate}>
            <i className="fa-solid fa-plus me-1"></i>{t('dataHolderGroups.createGroup')}
          </button>
        </div>
      </div>

      {/* Search */}
      <div className="mb-3" style={{ maxWidth: 360 }}>
        <div className="input-group input-group-sm">
          <span className="input-group-text"><i className="fa-solid fa-magnifying-glass"></i></span>
          <input
            className="form-control"
            placeholder={t('dataHolderGroups.searchPlaceholder')}
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
                <th>{t('dataHolderGroups.table.id')}</th>
                <th>{t('common.name')}</th>
                <th>{t('common.description')}</th>
                <th>{t('dataHolderGroups.table.visibility')}</th>
                <th>{t('common.status')}</th>
                <th>{t('dataHolderGroups.table.created')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {groups.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center text-muted py-5">
                    <i className="fa-solid fa-layer-group fa-2x mb-2"></i><br />{t('dataHolderGroups.empty')}
                  </td>
                </tr>
              ) : groups.map(g => (
                <tr key={g.id} className={!g.isActive ? 'table-secondary' : ''}>
                  <td><code className="small">{g.id}</code></td>
                  <td><strong>{g.name}</strong></td>
                  <td className="small text-muted">
                    {g.description ? (
                      <span className="text-truncate d-inline-block" style={{ maxWidth: 300 }}>{g.description}</span>
                    ) : '—'}
                  </td>
                  <td>
                    <span className={`badge bg-${g.isPrivate ? 'dark' : 'light'} ${g.isPrivate ? '' : 'text-dark border'}`}>
                      <i className={`fa-solid fa-${g.isPrivate ? 'lock' : 'globe'} me-1`}></i>
                      {g.isPrivate ? t('dataHolderGroups.visibility.private') : t('dataHolderGroups.visibility.public')}
                    </span>
                  </td>
                  <td>
                    <button
                      className={`badge border-0 bg-${g.isActive ? 'success' : 'secondary'}`}
                      onClick={() => handleToggleActive(g)}
                      title={t('dataHolderGroups.clickToToggle')}
                    >
                      {g.isActive ? t('common.active') : t('common.disabled')}
                    </button>
                  </td>
                  <td className="text-muted small">{formatDate(g.createdAt)}</td>
                  <td>
                    <div className="btn-group btn-group-sm">
                      <button className="btn btn-outline-primary" onClick={() => openEdit(g)} title={t('common.edit')}>
                        <i className="fa-solid fa-pen"></i>
                      </button>
                      <button className="btn btn-outline-info" onClick={() => openMembers(g)} title={t('dataHolderGroups.manageMembers')}>
                        <i className="fa-solid fa-users"></i>
                      </button>
                      <button className="btn btn-outline-danger" onClick={() => setDeleteTarget(g)} title={t('common.delete')}>
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
              itemLabel={t('dataHolderGroups.itemLabel')}
            />
          )}
        </div>
      </div>

      {/* Create / Edit Modal */}
      {showForm && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className={`fa-solid ${editing ? 'fa-pen' : 'fa-plus'} me-2`}></i>
                  {editing ? t('dataHolderGroups.editGroup') : t('dataHolderGroups.createGroup')}
                </h5>
                <button type="button" className="btn-close" onClick={() => setShowForm(false)}></button>
              </div>
              <div className="modal-body">
                <div className="mb-3">
                  <label className="form-label">{t('dataHolderGroups.form.nameLabel')}</label>
                  <input
                    className="form-control"
                    value={form.name}
                    onChange={e => setForm(p => ({ ...p, name: e.target.value }))}
                    autoFocus
                  />
                </div>
                <div className="mb-3">
                  <label className="form-label">{t('common.description')}</label>
                  <textarea
                    className="form-control"
                    rows={3}
                    value={form.description}
                    onChange={e => setForm(p => ({ ...p, description: e.target.value }))}
                  />
                </div>
                <div className="mb-3">
                  <div className="form-check form-switch">
                    <input
                      className="form-check-input"
                      type="checkbox"
                      role="switch"
                      id="privateSwitch"
                      checked={form.isPrivate}
                      onChange={e => setForm(p => ({ ...p, isPrivate: e.target.checked }))}
                    />
                    <label className="form-check-label" htmlFor="privateSwitch">
                      <i className={`fa-solid fa-${form.isPrivate ? 'lock' : 'globe'} me-1`}></i>
                      {t('dataHolderGroups.form.privateGroup')}
                    </label>
                  </div>
                  <div className="form-text">
                    {t('dataHolderGroups.form.privateHelp')}
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setShowForm(false)} disabled={submitting}>{t('common.cancel')}</button>
                <button className="btn btn-primary" onClick={handleSave} disabled={submitting}>
                  {submitting ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('common.saving')}</> : editing ? t('common.update') : t('common.create')}
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
                <h5 className="modal-title">
                  <i className="fa-solid fa-triangle-exclamation text-danger me-2"></i>{t('dataHolderGroups.deleteGroup')}
                </h5>
                <button type="button" className="btn-close" onClick={() => setDeleteTarget(null)}></button>
              </div>
              <div className="modal-body">
                <p>{t('dataHolderGroups.deleteConfirm.question')} <strong>{deleteTarget.name}</strong>?</p>
                <div className="alert alert-warning small mb-0">
                  <i className="fa-solid fa-exclamation-triangle me-1"></i>
                  {t('dataHolderGroups.deleteConfirm.warning')}
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

      {/* Members Modal */}
      {membersGroup && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-dialog-centered modal-lg">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className="fa-solid fa-users me-2"></i>
                  {t('dataHolderGroups.members.title', { name: membersGroup.name })}
                </h5>
                <button type="button" className="btn-close" onClick={() => setMembersGroup(null)}></button>
              </div>
              <div className="modal-body">
                <div className="alert alert-info small mb-3">
                  <i className="fa-solid fa-info-circle me-1"></i>
                  {t('dataHolderGroups.members.masterInfo')}
                </div>

                {/* Add member */}
                <div className="d-flex gap-2 mb-3">
                  <select
                    className="form-select form-select-sm"
                    value={addUserId}
                    onChange={e => setAddUserId(e.target.value)}
                  >
                    <option value="">{t('dataHolderGroups.members.selectUser')}</option>
                    {eligibleUsers.map(u => (
                      <option key={u.id} value={u.id}>
                        {u.firstName} {u.lastName} ({u.email}) — {u.typeLabel}
                      </option>
                    ))}
                  </select>
                  <button
                    className="btn btn-primary btn-sm text-nowrap"
                    onClick={handleAddMember}
                    disabled={!addUserId}
                  >
                    <i className="fa-solid fa-plus me-1"></i>{t('common.add')}
                  </button>
                </div>

                {membersLoading ? (
                  <div className="text-center py-3"><div className="spinner-border spinner-border-sm text-primary" /></div>
                ) : members.length === 0 ? (
                  <div className="text-center text-muted py-3">
                    <i className="fa-solid fa-user-slash me-1"></i>{t('dataHolderGroups.members.empty')}
                  </div>
                ) : (
                  <div className="table-responsive">
                    <table className="table table-sm table-hover align-middle mb-0">
                      <thead className="table-light">
                        <tr>
                          <th>{t('common.name')}</th>
                          <th>{t('common.email')}</th>
                          <th>{t('dataHolderGroups.members.table.role')}</th>
                          <th>{t('dataHolderGroups.members.table.joined')}</th>
                          <th></th>
                        </tr>
                      </thead>
                      <tbody>
                        {members.map(m => (
                          <tr key={m.userId}>
                            <td><strong>{m.firstName} {m.lastName}</strong></td>
                            <td className="small">{m.email}</td>
                            <td>
                              <span className={`badge bg-${m.type === 2 ? 'warning' : 'info'}`}>
                                {m.typeLabel}
                              </span>
                            </td>
                            <td className="text-muted small">{formatDate(m.joinedAt)}</td>
                            <td>
                              <button
                                className="btn btn-outline-danger btn-sm"
                                onClick={() => handleRemoveMember(m.userId)}
                                title={t('dataHolderGroups.members.removeFromGroup')}
                              >
                                <i className="fa-solid fa-xmark"></i>
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setMembersGroup(null)}>{t('common.close')}</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DataHolderGroups;