/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback, useContext } from 'react';
import { getUsers, createUser, updateUser, deleteUser } from '../services/api';
import { GroupContext } from '../context/GroupContext';
import Pagination from '../components/Pagination';
import { useT } from '../i18n';
import toast from 'react-hot-toast';

const USER_TYPES = [
  { value: 1, key: 'master', icon: 'fa-solid fa-crown', color: 'danger' },
  { value: 2, key: 'admin', icon: 'fa-solid fa-user-shield', color: 'warning' },
  { value: 3, key: 'user', icon: 'fa-solid fa-user', color: 'info' },
];

const getTypeConfig = (type) => USER_TYPES.find(t => t.value === type) || USER_TYPES[2];

const formatDate = (d) => d ? new Date(d).toLocaleString() : '—';

const Users = ({ currentUser }) => {
  const { t } = useT();
  const { groups: dhGroups } = useContext(GroupContext);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '', type: 3, groupIds: [] });
  // Server-side pagination state
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [totalItems, setTotalItems] = useState(0);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');

  const isMaster = currentUser?.type === 1;
  const isAdmin = currentUser?.type === 2;

  // Groups the current caller has access to (for Admins, only their own groups)
  const callerGroupIds = currentUser?.groupIds || currentUser?.groups?.map(g => g.id) || [];
  const availableGroupsForAssignment = isMaster
    ? dhGroups
    : dhGroups.filter(g => callerGroupIds.includes(g.id));

  // User types the caller can create/assign (only types below their own)
  const allowedTypes = USER_TYPES.filter(t => t.value > currentUser?.type);

  // Debounce the search box (300ms) before hitting the server.
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(t);
  }, [search]);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      const res = await getUsers({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        sortBy: 'createdAt',
        sortDir: 'desc',
      });
      setUsers(res.content || []);
      setTotalItems(res.totalElements || 0);
    } catch {
      toast.error(t('users.toast.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, debouncedSearch]);

  useEffect(() => { load(); }, [load]);

  // Reset to the first page when the search term changes.
  useEffect(() => { setPage(1); }, [debouncedSearch]);

  const openCreate = () => {
    setEditing(null);
    setForm({
      firstName: '', lastName: '', email: '', password: '',
      type: isAdmin ? 3 : 3, // Admins default to User, Master can pick
      groupIds: [],
    });
    setShowForm(true);
  };

  const openEdit = (u) => {
    setEditing(u);
    setForm({
      firstName: u.firstName || '',
      lastName: u.lastName || '',
      email: u.email || '',
      password: '',
      type: u.type || 3,
      groupIds: u.groupIds || [],
    });
    setShowForm(true);
  };

  /*
   * Determine if the current user can edit a target user.
   * Master can edit anyone. Non-master can edit their own account (self-service: email +
   * password only) or users strictly below their own level.
   */
  const canEdit = (targetUser) => {
    if (isMaster) return true;
    if (targetUser.id === currentUser?.id) return true;
    return targetUser.type > currentUser?.type;
  };

  // Editing your own account, as a non-master → self-service scope (email + password only).
  const editingSelf = !!editing && editing.id === currentUser?.id;
  const selfServiceOnly = editingSelf && !isMaster;
  // An email may only be changed by its owner or by a master.
  const canEditEmail = !editing || isMaster || editingSelf;

  // Determine if group checkboxes should be disabled
  const isGroupEditDisabled = (targetUser) => {
    if (!targetUser) return false;
    if (isMaster) return false;
    // Non-master cannot edit groups of anyone at or above their level (including self)
    return targetUser.type <= currentUser?.type;
  };

  const handleSave = async () => {
    if (!form.firstName.trim() || !form.lastName.trim() || !form.email.trim()) {
      toast.error(t('users.validation.nameEmailRequired'));
      return;
    }
    if (!editing && !form.password.trim()) {
      toast.error(t('users.validation.passwordRequired'));
      return;
    }
    if (form.type !== 1 && form.groupIds.length === 0) {
      toast.error(t('users.validation.groupRequired'));
      return;
    }
    setSubmitting(true);
    try {
      const payload = { ...form, callerUserId: currentUser?.id };
      if (editing && !payload.password.trim()) {
        delete payload.password;
      }
      if (payload.type === 1) {
        delete payload.groupIds;
      }
      const res = editing ? await updateUser(editing.id, payload) : await createUser(payload);
      if (res.success) {
        toast.success(editing ? t('users.toast.updated') : t('users.toast.created'));
        setShowForm(false);
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

  const handleDelete = async () => {
    if (!deleteTarget) return;
    if (deleteTarget.id === currentUser?.id) {
      toast.error(t('users.toast.cannotDeleteSelf'));
      return;
    }
    setSubmitting(true);
    try {
      const res = await deleteUser(deleteTarget.id);
      if (res.success) {
        toast.success(t('users.toast.deleted'));
        setDeleteTarget(null);
        load();
      } else {
        toast.error(res.error || t('common.deleteFailed'));
      }
    } catch {
      toast.error(t('common.deleteFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleActive = async (user) => {
    if (user.id === currentUser?.id) {
      toast.error(t('users.toast.cannotDeactivateSelf'));
      return;
    }
    try {
      const res = await updateUser(user.id, { isActive: !user.isActive, callerUserId: currentUser?.id });
      if (res.success) {
        toast.success(user.isActive ? t('users.toast.deactivated') : t('users.toast.activated'));
        load();
      }
    } catch (e) {
      toast.error(e.data?.error || e.message || t('users.toast.updateFailed'));
    }
  };

  const toggleGroupId = (groupId) => {
    setForm(p => {
      const ids = p.groupIds.includes(groupId)
        ? p.groupIds.filter(id => id !== groupId)
        : [...p.groupIds, groupId];
      return { ...p, groupIds: ids };
    });
  };

  if (loading && users.length === 0) {
    return (
      <div className="d-flex justify-content-center py-5">
        <div className="spinner-border text-primary" />
      </div>
    );
  }

  // Active/per-type counts reflect the current page only (server-side pagination).
  const active = users.filter(u => u.isActive).length;

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">{t('users.title')}</h2>
          <p className="text-muted mb-0">{t('users.count', { count: totalItems })}</p>
        </div>
        <div className="d-flex gap-2">
          <div className="input-group input-group-sm" style={{ maxWidth: 260 }}>
            <span className="input-group-text"><i className="fa-solid fa-magnifying-glass"></i></span>
            <input
              className="form-control"
              placeholder={t('users.searchPlaceholder')}
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
            <i className="fa-solid fa-arrows-rotate me-1"></i>{t('common.refresh')}
          </button>
          <button className="btn btn-primary" onClick={openCreate}>
            <i className="fa-solid fa-user-plus me-1"></i>{t('users.addUser')}
          </button>
        </div>
      </div>

      {/* Stats (reflect the current page) */}
      <div className="row g-3 mb-4">
        {USER_TYPES.map(ut => {
          const count = users.filter(u => u.type === ut.value).length;
          return (
            <div className="col" key={ut.value}>
              <div className="card text-center p-3">
                <div className={`fs-3 fw-bold text-${ut.color}`}>{count}</div>
                <small className="text-muted">
                  <i className={`${ut.icon} me-1`}></i>{t(`users.typePlural.${ut.key}`)}
                </small>
              </div>
            </div>
          );
        })}
        <div className="col">
          <div className="card text-center p-3">
            <div className="fs-3 fw-bold text-success">{active}</div>
            <small className="text-muted">
              <i className="fa-solid fa-check-circle me-1"></i>{t('common.active')}
            </small>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="card">
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr>
                <th>{t('common.name')}</th>
                <th>{t('common.email')}</th>
                <th>{t('common.type')}</th>
                <th>{t('users.groups')}</th>
                <th>{t('common.status')}</th>
                <th>{t('users.created')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {users.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center text-muted py-5">
                    <i className="fa-solid fa-users fa-2x mb-2"></i><br />{t('users.empty')}
                  </td>
                </tr>
              ) : users.map(user => {
                const tc = getTypeConfig(user.type);
                const isSelf = user.id === currentUser?.id;
                const editable = canEdit(user);
                return (
                  <tr key={user.id} className={!user.isActive ? 'table-secondary' : ''}>
                    <td>
                      <strong>{user.firstName} {user.lastName}</strong>
                      {isSelf && <span className="badge bg-primary ms-2" style={{ fontSize: '9px' }}>{t('users.you')}</span>}
                    </td>
                    <td className="small">{user.email}</td>
                    <td>
                      <span className={`badge bg-${tc.color}`}>
                        <i className={`${tc.icon} me-1`}></i>{t(`common.${tc.key}`)}
                      </span>
                    </td>
                    <td>
                      {user.type === 1 ? (
                        <span className="badge bg-dark" style={{ fontSize: 10 }}>{t('users.allGroups')}</span>
                      ) : user.groups && user.groups.length > 0 ? (
                        <div className="d-flex flex-wrap gap-1">
                          {user.groups.map(g => (
                            <span key={g.id} className="badge bg-light text-dark border" style={{ fontSize: 10 }}>{g.name}</span>
                          ))}
                        </div>
                      ) : (
                        <span className="text-muted small"><em>{t('common.none')}</em></span>
                      )}
                    </td>
                    <td>
                      <button
                        className={`badge border-0 bg-${user.isActive ? 'success' : 'secondary'}`}
                        onClick={() => handleToggleActive(user)}
                        title={!editable ? t('users.insufficientPermissions') : t('common.clickToToggle')}
                        disabled={!editable}
                      >
                        {user.isActive ? t('common.active') : t('common.inactive')}
                      </button>
                    </td>
                    <td className="text-muted small">{formatDate(user.createdAt)}</td>
                    <td>
                      <div className="btn-group btn-group-sm">
                        <button
                          className="btn btn-outline-primary"
                          onClick={() => openEdit(user)}
                          title={!editable ? t('users.insufficientPermissions') : t('common.edit')}
                          disabled={!editable}
                        >
                          <i className="fa-solid fa-pen"></i>
                        </button>
                        <button
                          className="btn btn-outline-danger"
                          onClick={() => setDeleteTarget(user)}
                          title={!editable ? t('users.insufficientPermissions') : t('common.delete')}
                          disabled={!editable}
                        >
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
              itemLabel={t('users.itemLabel')}
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
                  <i className={`fa-solid ${editing ? 'fa-user-pen' : 'fa-user-plus'} me-2`}></i>
                  {editing ? t('users.editUser') : t('users.addUser')}
                </h5>
                <button type="button" className="btn-close" onClick={() => setShowForm(false)}></button>
              </div>
              <div className="modal-body">
                <div className="row g-3">
                  <div className="col-md-6">
                    <label className="form-label">{t('users.form.firstName')}</label>
                    <input
                      className="form-control"
                      value={form.firstName}
                      onChange={e => setForm(p => ({ ...p, firstName: e.target.value }))}
                      disabled={selfServiceOnly}
                    />
                  </div>
                  <div className="col-md-6">
                    <label className="form-label">{t('users.form.lastName')}</label>
                    <input
                      className="form-control"
                      value={form.lastName}
                      onChange={e => setForm(p => ({ ...p, lastName: e.target.value }))}
                      disabled={selfServiceOnly}
                    />
                  </div>
                  <div className="col-12">
                    <label className="form-label">{t('users.form.email')}</label>
                    <input
                      className="form-control"
                      type="email"
                      value={form.email}
                      onChange={e => setForm(p => ({ ...p, email: e.target.value }))}
                      disabled={!canEditEmail}
                    />
                    {editing && !canEditEmail && (
                      <div className="form-text">{t('users.form.emailLocked')}</div>
                    )}
                  </div>
                  <div className="col-md-6">
                    <label className="form-label">
                      {editing ? t('users.form.newPassword') : t('users.form.password')}
                    </label>
                    <input
                      className="form-control"
                      type="password"
                      value={form.password}
                      onChange={e => setForm(p => ({ ...p, password: e.target.value }))}
                      placeholder={editing ? t('users.form.passwordPlaceholder') : ''}
                    />
                    {editing && (
                      <div className="form-text">{t('users.form.passwordHint')}</div>
                    )}
                  </div>
                  <div className="col-md-6">
                    <label className="form-label">{t('common.type')}</label>
                    <select
                      className="form-select"
                      value={form.type}
                      onChange={e => setForm(p => ({ ...p, type: parseInt(e.target.value) }))}
                      disabled={selfServiceOnly}
                    >
                      {/* Self-service: the type is fixed, so show only the user's own type
                          (allowedTypes excludes it and the select would otherwise render blank). */}
                      {(selfServiceOnly ? USER_TYPES.filter(ut => ut.value === form.type) : allowedTypes).map(ut => (
                        <option key={ut.value} value={ut.value}>{t(`common.${ut.key}`)}</option>
                      ))}
                      {/* Show current type even if not in allowedTypes (shouldn't happen since you can't open the form) */}
                      {editing && !allowedTypes.find(at => at.value === form.type) && (
                        <option value={form.type}>{t(`common.${getTypeConfig(form.type).key}`)}</option>
                      )}
                    </select>
                    {!isMaster && (
                      <div className="form-text">{t('users.form.typeHint')}</div>
                    )}
                  </div>

                  {/* Group Memberships */}
                  {form.type !== 1 && (
                    <div className="col-12">
                      <label className="form-label">
                        {t('users.form.groups')}
                        <span className="text-muted small ms-2">
                          {t('users.form.selectedCount', { count: form.groupIds.length })}
                        </span>
                      </label>

                      {isGroupEditDisabled(editing) ? (
                        <div className="alert alert-secondary small mb-0">
                          <i className="fa-solid fa-lock me-1"></i>
                          {t('users.form.noGroupPermission')}
                        </div>
                      ) : availableGroupsForAssignment.length === 0 ? (
                        <div className="alert alert-warning small mb-0">
                          <i className="fa-solid fa-exclamation-triangle me-1"></i>
                          {isAdmin
                            ? t('users.form.adminNoGroups')
                            : t('users.form.noGroupsExist')}
                        </div>
                      ) : (
                        <>
                          <div className="border rounded p-2" style={{ maxHeight: 180, overflowY: 'auto' }}>
                            {availableGroupsForAssignment.map(g => {
                              const checked = form.groupIds.includes(g.id);
                              return (
                                <div className="form-check" key={g.id}>
                                  <input
                                    className="form-check-input"
                                    type="checkbox"
                                    id={`grp-${g.id}`}
                                    checked={checked}
                                    onChange={() => toggleGroupId(g.id)}
                                  />
                                  <label className="form-check-label" htmlFor={`grp-${g.id}`}>
                                    {g.name}
                                    {!g.isActive && <span className="badge bg-secondary ms-1" style={{ fontSize: 9 }}>{t('common.disabled')}</span>}
                                  </label>
                                </div>
                              );
                            })}
                          </div>
                          {isAdmin && (
                            <div className="form-text">
                              {t('users.form.adminGroupHint')}
                            </div>
                          )}
                          {isMaster && (
                            <div className="form-text">
                              {t('users.form.masterGroupHint')}
                            </div>
                          )}
                          {/* Show warning if editing user has groups the caller can't see */}
                          {isAdmin && editing && editing.groupIds && (() => {
                            const outsideGroups = editing.groupIds.filter(id => !callerGroupIds.includes(id));
                            if (outsideGroups.length > 0) {
                              return (
                                <div className="alert alert-info small mt-2 mb-0">
                                  <i className="fa-solid fa-info-circle me-1"></i>
                                  {t('users.form.outsideGroups', { count: outsideGroups.length })}
                                </div>
                              );
                            }
                            return null;
                          })()}
                        </>
                      )}
                    </div>
                  )}
                  {form.type === 1 && (
                    <div className="col-12">
                      <div className="alert alert-info small mb-0">
                        <i className="fa-solid fa-crown me-1"></i>
                        {t('users.form.masterInfo')}
                      </div>
                    </div>
                  )}
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setShowForm(false)} disabled={submitting}>
                  {t('common.cancel')}
                </button>
                <button className="btn btn-primary" onClick={handleSave} disabled={submitting}>
                  {submitting ? (
                    <><span className="spinner-border spinner-border-sm me-1"></span>{t('common.saving')}</>
                  ) : editing ? t('common.update') : t('users.createUser')}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirm */}
      {deleteTarget && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title">
                  <i className="fa-solid fa-triangle-exclamation text-danger me-2"></i>{t('users.deleteUser')}
                </h5>
                <button type="button" className="btn-close" onClick={() => setDeleteTarget(null)}></button>
              </div>
              <div className="modal-body">
                <p>{t('common.deleteConfirmPrefix')} <strong>{deleteTarget.firstName} {deleteTarget.lastName}</strong>?</p>
                <p className="text-muted small">
                  {deleteTarget.email}
                  {deleteTarget.groups && deleteTarget.groups.length > 0 && deleteTarget.type !== 1 && (
                    <span className="d-block mt-1">{t('users.memberOf', { groups: deleteTarget.groups.map(g => g.name).join(', ') })}</span>
                  )}
                </p>
                <div className="alert alert-warning small mb-0">
                  <i className="fa-solid fa-exclamation-triangle me-1"></i>
                  {t('users.deleteWarning')}
                </div>
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setDeleteTarget(null)} disabled={submitting}>
                  {t('common.cancel')}
                </button>
                <button className="btn btn-danger" onClick={handleDelete} disabled={submitting}>
                  {submitting ? (
                    <><span className="spinner-border spinner-border-sm me-1"></span>{t('common.deleting')}</>
                  ) : t('common.delete')}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Users;
