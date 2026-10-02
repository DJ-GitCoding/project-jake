/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { getUsers, createUser, updateUser, deleteUser } from '../services/api';
import Loading from '../components/Loading';
import Modal from '../components/Modal';
import DeleteConfirmModal from '../components/DeleteConfirmModal';
import Pagination, { DEFAULT_PAGE_SIZE } from '../components/Pagination';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const UserManagement = () => {
  const { user: currentUser } = useAuth();
  const { t } = useT();

  const USER_TYPES = [
    { value: 'MASTER', label: t('userManagement.types.master.label'), desc: t('userManagement.types.master.desc') },
    { value: 'ASSISTANT_ADMIN', label: t('userManagement.types.assistantAdmin.label'), desc: t('userManagement.types.assistantAdmin.desc') },
  ];
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [deletingUser, setDeletingUser] = useState(null);
  const [saving, setSaving] = useState(false);

  const [form, setForm] = useState({
    username: '',
    password: '',
    firstName: '',
    lastName: '',
    email: '',
    type: 'ASSISTANT_ADMIN',
  });

  // Server-side pagination + search state
  const [page, setPage] = useState(1); // 1-based
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [totalItems, setTotalItems] = useState(0);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');

  // Debounce the search box (300ms)
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  // Reset to first page whenever the search term changes
  useEffect(() => { setPage(1); }, [debouncedSearch]);

  // Fetch a page whenever paging or search changes
  useEffect(() => {
    loadUsers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, pageSize, debouncedSearch]);

  const loadUsers = async () => {
    try {
      setLoading(true);
      const response = await getUsers({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        sortBy: 'id',
        sortDir: 'asc',
      });
      setUsers(response.data.content || []);
      setTotalItems(response.data.totalElements || 0);
    } catch (error) {
      console.error('Failed to load users:', error);
      toast.error(error.response?.data?.error || t('userManagement.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  const resetForm = () => {
    setForm({ username: '', password: '', firstName: '', lastName: '', email: '', type: 'ASSISTANT_ADMIN' });
  };

  const openCreate = () => {
    resetForm();
    setShowCreateModal(true);
  };

  const openEdit = (u) => {
    setForm({
      username: u.username,
      password: '',
      firstName: u.firstName || '',
      lastName: u.lastName || '',
      email: u.email || '',
      type: u.type || 'ASSISTANT_ADMIN',
    });
    setEditingUser(u);
  };

  const handleSave = async () => {
    if (showCreateModal) {
      if (!form.username.trim() || !form.password.trim() || !form.email.trim()) {
        toast.error(t('userManagement.validation.required'));
        return;
      }
    }

    setSaving(true);
    try {
      if (editingUser) {
        const payload = { ...form };
        if (!payload.password.trim()) delete payload.password;
        delete payload.username;
        await updateUser(editingUser.id, payload);
        toast.success(t('userManagement.updated'));
        setEditingUser(null);
      } else {
        await createUser(form);
        toast.success(t('userManagement.created'));
        setShowCreateModal(false);
      }
      resetForm();
      loadUsers();
    } catch (error) {
      toast.error(error.response?.data?.error || t('userManagement.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deletingUser) return;
    try {
      await deleteUser(deletingUser.id);
      toast.success(t('userManagement.deleted'));
      setDeletingUser(null);
      loadUsers();
    } catch (error) {
      toast.error(error.response?.data?.error || t('userManagement.deleteFailed'));
    }
  };

  const handleToggleActive = async (u) => {
    try {
      await updateUser(u.id, { isActive: !u.isActive });
      toast.success(u.isActive ? t('userManagement.deactivated') : t('userManagement.activated'));
      loadUsers();
    } catch (error) {
      toast.error(t('userManagement.statusUpdateFailed'));
    }
  };

  if (loading && users.length === 0) return <Loading message={t('userManagement.loading')} />;

  const renderForm = () => (
    <div style={{ display: 'grid', gap: '16px' }}>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
        <div className="mb-3">
          <label className="form-label">{t('userManagement.form.firstName')}</label>
          <input type="text" className="form-control" value={form.firstName}
            onChange={(e) => setForm(p => ({ ...p, firstName: e.target.value }))}
            placeholder={t('userManagement.form.firstNamePlaceholder')} />
        </div>
        <div className="mb-3">
          <label className="form-label">{t('userManagement.form.lastName')}</label>
          <input type="text" className="form-control" value={form.lastName}
            onChange={(e) => setForm(p => ({ ...p, lastName: e.target.value }))}
            placeholder={t('userManagement.form.lastNamePlaceholder')} />
        </div>
      </div>

      {showCreateModal && (
        <div className="mb-3">
          <label className="form-label">{t('userManagement.form.username')} *</label>
          <input type="text" className="form-control" value={form.username}
            onChange={(e) => setForm(p => ({ ...p, username: e.target.value }))}
            placeholder={t('userManagement.form.usernamePlaceholder')} />
        </div>
      )}

      <div className="mb-3">
        <label className="form-label">{t('common.email')} *</label>
        <input type="email" className="form-control" value={form.email}
          onChange={(e) => setForm(p => ({ ...p, email: e.target.value }))}
          placeholder={t('userManagement.form.emailPlaceholder')} />
      </div>

      <div className="mb-3">
        <label className="form-label">
          {t('common.password')} {editingUser ? t('userManagement.form.passwordKeepCurrent') : '*'}
        </label>
        <input type="password" className="form-control" value={form.password}
          onChange={(e) => setForm(p => ({ ...p, password: e.target.value }))}
          placeholder={editingUser ? '••••••••' : t('userManagement.form.passwordPlaceholder')} />
      </div>

      <div className="mb-3">
        <label className="form-label">{t('userManagement.form.accountType')} *</label>
        <select className="form-select" value={form.type}
          onChange={(e) => setForm(p => ({ ...p, type: e.target.value }))}>
          {USER_TYPES.map(ut => (
            <option key={ut.value} value={ut.value}>{ut.label}</option>
          ))}
        </select>
        <p className="text-muted small" style={{ marginTop: '4px' }}>
          {USER_TYPES.find(ut => ut.value === form.type)?.desc}
        </p>
      </div>
    </div>
  );

  return (
    <div>
      <div className="mb-4 d-flex justify-content-between align-items-center">
        <div>
          <h2 className="h3 fw-bold mb-1">{t('userManagement.title')}</h2>
          <p className="text-muted mb-0">{t('userManagement.subtitle')}</p>
        </div>
        <button className="btn btn-primary" onClick={openCreate}>+ {t('userManagement.newUser')}</button>
      </div>

      {/* Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: '16px', marginBottom: '24px' }}>
        {[
          { label: t('userManagement.stats.totalUsers'), val: totalItems, color: 'var(--accent-primary)' },
          { label: t('userManagement.stats.masterAccounts'), val: users.filter(u => u.type === 'MASTER').length, color: 'var(--accent-warning)' },
          { label: t('userManagement.stats.assistantAdmins'), val: users.filter(u => u.type === 'ASSISTANT_ADMIN').length, color: 'var(--accent-tertiary)' },
          { label: t('userManagement.stats.active'), val: users.filter(u => u.isActive).length, color: 'var(--accent-success)' },
        ].map(s => (
          <div key={s.label} className="card" style={{ padding: '20px' }}>
            <div className="text-muted small">{s.label}</div>
            <div style={{ fontSize: 28, fontWeight: 700, color: s.color }}>{s.val}</div>
          </div>
        ))}
      </div>

      {/* Users table */}
      <div className="card">
        <div className="card-header d-flex justify-content-between align-items-center">
          <h3><i className="fa-solid fa-circle-user"></i> {t('userManagement.usersHeading')}</h3>
          <input
            type="text"
            className="form-control"
            style={{ width: '250px' }}
            placeholder={t('userManagement.searchPlaceholder')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="table-responsive">
          <table className="table table-hover align-middle" style={{ width: '100%' }}>
            <thead>
              <tr>
                <th>{t('userManagement.table.user')}</th>
                <th>{t('common.email')}</th>
                <th>{t('common.type')}</th>
                <th>{t('common.status')}</th>
                <th>{t('userManagement.table.lastLogin')}</th>
                <th>{t('userManagement.table.created')}</th>
                <th style={{ textAlign: 'right' }}>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {users.map(u => {
                const isSelf = u.id === currentUser?.userId || u.id === currentUser?.id;
                return (
                  <tr key={u.id}>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <div style={{
                          width: 36, height: 36, borderRadius: '50%', flexShrink: 0,
                          background: u.type === 'MASTER'
                            ? 'linear-gradient(135deg, var(--accent-warning), #f97316)'
                            : 'linear-gradient(135deg, var(--accent-primary), var(--accent-secondary))',
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                          color: 'white', fontWeight: 700, fontSize: 13,
                        }}>
                          {(u.firstName?.[0] || u.username[0]).toUpperCase()}
                        </div>
                        <div>
                          <div style={{ fontWeight: 600, fontSize: 14 }}>
                            {u.fullName || u.username}
                            {isSelf && <span style={{ fontSize: 11, color: 'var(--accent-primary)', marginLeft: 6 }}>{t('userManagement.you')}</span>}
                          </div>
                          <div className="text-muted small">@{u.username}</div>
                        </div>
                      </div>
                    </td>
                    <td>{u.email || '—'}</td>
                    <td>
                      <span style={{
                        fontSize: 11, padding: '3px 10px', borderRadius: 20, fontWeight: 600,
                        background: u.type === 'MASTER' ? 'rgba(245,158,11,0.12)' : 'rgba(99,102,241,0.10)',
                        color: u.type === 'MASTER' ? 'var(--accent-warning)' : 'var(--accent-primary)',
                        border: `1px solid ${u.type === 'MASTER' ? 'rgba(245,158,11,0.25)' : 'rgba(99,102,241,0.2)'}`,
                      }}>
                        {u.type === 'MASTER' ? t('userManagement.badges.master') : t('userManagement.badges.assistantAdmin')}
                      </span>
                    </td>
                    <td>
                      <span style={{
                        display: 'inline-flex', alignItems: 'center', gap: 6,
                        fontSize: 12, fontWeight: 500,
                        color: u.isActive ? 'var(--accent-success)' : 'var(--text-tertiary)',
                      }}>
                        <span style={{
                          width: 8, height: 8, borderRadius: '50%',
                          background: u.isActive ? 'var(--accent-success)' : 'var(--text-muted)',
                        }} />
                        {u.isActive ? t('common.active') : t('common.inactive')}
                      </span>
                    </td>
                    <td className="text-muted small">
                      {u.lastLogin ? new Date(u.lastLogin).toLocaleDateString() : t('userManagement.never')}
                    </td>
                    <td className="text-muted small">
                      {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '—'}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                        <button className="btn btn-secondary" style={{ padding: '4px 12px', fontSize: 12 }}
                          onClick={() => openEdit(u)}>{t('common.edit')}</button>
                        {!isSelf && (
                          <>
                            <button className="btn btn-secondary" style={{ padding: '4px 12px', fontSize: 12 }}
                              onClick={() => handleToggleActive(u)}>
                              {u.isActive ? t('userManagement.deactivate') : t('userManagement.activate')}
                            </button>
                            <button className="btn btn-secondary"
                              style={{ padding: '4px 12px', fontSize: 12, color: 'var(--accent-danger)' }}
                              onClick={() => setDeletingUser(u)}>{t('common.delete')}</button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
              {users.length === 0 && (
                <tr><td colSpan={7} style={{ textAlign: 'center', padding: 40, color: 'var(--text-tertiary)' }}>{t('userManagement.noUsersFound')}</td></tr>
              )}
            </tbody>
          </table>
        </div>
        {totalItems > 0 && (
          <div className="p-3">
            <Pagination
              page={page}
              pageSize={pageSize}
              totalItems={totalItems}
              onPageChange={setPage}
              onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
              itemLabel={t('userManagement.itemLabel')}
            />
          </div>
        )}
      </div>

      {/* Create Modal */}
      <Modal isOpen={showCreateModal} title={t('userManagement.createModalTitle')} onClose={() => setShowCreateModal(false)} size="large">
        {renderForm()}
        <div style={{ display: 'flex', gap: 12, marginTop: 24, justifyContent: 'flex-end' }}>
          <button className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>{t('common.cancel')}</button>
          <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
            {saving ? t('common.creating') : t('userManagement.createUser')}
          </button>
        </div>
      </Modal>

      {/* Edit Modal */}
      <Modal isOpen={!!editingUser} title={t('userManagement.editModalTitle', { username: editingUser?.username || '' })} onClose={() => setEditingUser(null)} size="large">
        {renderForm()}
        <div style={{ display: 'flex', gap: 12, marginTop: 24, justifyContent: 'flex-end' }}>
          <button className="btn btn-secondary" onClick={() => setEditingUser(null)}>{t('common.cancel')}</button>
          <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
            {saving ? t('common.saving') : t('common.saveChanges')}
          </button>
        </div>
      </Modal>

      {/* Delete Confirmation */}
      {deletingUser && (
        <DeleteConfirmModal
          title={t('userManagement.deleteModalTitle')}
          message={t('userManagement.deleteConfirmMessage', { username: deletingUser.username, identifier: deletingUser.fullName || deletingUser.email })}
          onConfirm={handleDelete}
          onCancel={() => setDeletingUser(null)}
        />
      )}
    </div>
  );
};

export default UserManagement;
