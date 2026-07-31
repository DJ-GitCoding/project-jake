/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import {
  getCustomRoles,
  createCustomRole,
  updateCustomRole,
  toggleCustomRole,
  deleteCustomRole,
} from '../services/api';
import Loading from '../components/Loading';
import Modal from '../components/Modal';
import Pagination, { DEFAULT_PAGE_SIZE } from '../components/Pagination';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const EMPTY_FORM = { displayName: '', description: '' };

const BUILT_IN_ROLES = ['Registrant', 'Admin', 'Tech', 'Billing', 'Abuse'];

const CustomRoles = () => {
  const { t } = useT();
  const [roles, setRoles] = useState([]);
  const [loading, setLoading] = useState(true);

  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

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
    loadRoles();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, pageSize, debouncedSearch]);

  const loadRoles = async () => {
    try {
      setLoading(true);
      const response = await getCustomRoles({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        sortBy: 'displayName',
        sortDir: 'asc',
      });
      setRoles(response.data.content || []);
      setTotalItems(response.data.totalElements || 0);
    } catch (error) {
      console.error('Failed to load custom roles:', error);
      toast.error(t('customRoles.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  const openAddModal = () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setShowModal(true);
  };

  const openEditModal = (role) => {
    setEditingId(role.id);
    setForm({ displayName: role.displayName || '', description: role.description || '' });
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!form.displayName.trim()) {
      toast.error(t('customRoles.nameRequired'));
      return;
    }
    setSaving(true);
    try {
      const payload = { displayName: form.displayName.trim(), description: form.description.trim() };
      const response = editingId
        ? await updateCustomRole(editingId, payload)
        : await createCustomRole(payload);
      if (response.data.success) {
        toast.success(response.data.message || t('customRoles.saved'));
        setShowModal(false);
        loadRoles();
      } else {
        toast.error(response.data.error || t('customRoles.saveFailed'));
      }
    } catch (error) {
      toast.error(error.response?.data?.error || t('customRoles.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const handleToggle = async (role) => {
    try {
      const response = await toggleCustomRole(role.id);
      if (response.data.success) {
        toast.success(response.data.message);
        loadRoles();
      }
    } catch (error) {
      toast.error(error.response?.data?.error || t('customRoles.toggleFailed'));
    }
  };

  const handleDelete = async (role) => {
    if (!window.confirm(t('customRoles.deleteConfirm', { name: role.displayName }))) {
      return;
    }
    try {
      const response = await deleteCustomRole(role.id);
      if (response.data.success) {
        toast.success(response.data.message);
        loadRoles();
      }
    } catch (error) {
      toast.error(error.response?.data?.error || t('customRoles.deleteFailed'));
    }
  };

  if (loading && roles.length === 0) return <Loading message={t('customRoles.loading')} />;

  const activeCount = roles.filter(r => r.isActive).length;

  return (
    <div>
      <p className="text-muted mb-4">
        {t('customRoles.subtitle')}
      </p>

      {/* Built-in roles reference */}
      <div className="card" style={{ marginBottom: '24px' }}>
        <div className="card-header">
          <h3><i className="fa-solid fa-lock"></i> {t('customRoles.builtInRoles')}</h3>
        </div>
        <div className="card-body" style={{ padding: '16px 24px' }}>
          <p className="text-muted small mb-2">
            {t('customRoles.builtInRolesDesc')}
          </p>
          <div className="d-flex flex-wrap gap-2">
            {BUILT_IN_ROLES.map(r => (
              <span key={r} className="badge bg-light text-dark border" style={{ fontSize: 12 }}>{r}</span>
            ))}
          </div>
        </div>
      </div>

      {/* Custom roles */}
      <div className="card" style={{ marginBottom: '24px' }}>
        <div className="card-header d-flex justify-content-between align-items-center">
          <h3><i className="fa-solid fa-user-tag"></i> {t('customRoles.title')}</h3>
          <div className="d-flex align-items-center gap-2">
            <input
              type="text"
              className="form-control form-control-sm"
              style={{ width: '200px' }}
              placeholder={t('customRoles.searchPlaceholder')}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <span className="text-muted small">
              {t('customRoles.activeRolesOnPage', { count: activeCount, plural: activeCount !== 1 ? 's' : '' })}
            </span>
            <button className="btn btn-primary btn-sm" onClick={openAddModal}>
              + {t('customRoles.addRole')}
            </button>
          </div>
        </div>
        <div className="card-body" style={{ padding: '0' }}>
          {roles.length === 0 ? (
            debouncedSearch ? (
              <div style={{ padding: '40px 24px', textAlign: 'center' }}>
                <div className="text-muted"><strong>{t('customRoles.noSearchResults')}</strong></div>
              </div>
            ) : (
              <div style={{ padding: '40px 24px', textAlign: 'center' }}>
                <div className="text-muted" style={{ marginBottom: 16 }}>
                  <strong>{t('customRoles.noRolesDefined')}</strong>
                </div>
                <p className="text-muted small" style={{ maxWidth: 480, margin: '0 auto 16px' }}>
                  {t('customRoles.noRolesHint')}
                </p>
                <button className="btn btn-primary" onClick={openAddModal}>
                  + {t('customRoles.addFirstRole')}
                </button>
              </div>
            )
          ) : (
            <div className="table-responsive">
              <table className="table table-hover align-middle">
                <thead>
                  <tr>
                    <th>{t('common.name')}</th>
                    <th>{t('customRoles.table.fieldKey')}</th>
                    <th>{t('common.description')}</th>
                    <th>{t('common.status')}</th>
                    <th>{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {roles.map(role => (
                    <tr key={role.id} style={{ opacity: role.isActive ? 1 : 0.5 }}>
                      <td>
                        <strong>{role.displayName}</strong>
                        {!role.isActive && (
                          <span className="badge bg-light text-dark border" style={{ marginLeft: 8, fontSize: 10 }}>{t('customRoles.disabled')}</span>
                        )}
                      </td>
                      <td><code className="small">{role.roleKey}</code></td>
                      <td className="text-muted small">{role.description || '—'}</td>
                      <td>
                        {role.isActive ? (
                          <span className="badge bg-success-subtle text-success" style={{ fontSize: 11 }}>{t('common.active')}</span>
                        ) : (
                          <span className="badge bg-light text-dark border" style={{ fontSize: 11 }}>{t('customRoles.disabled')}</span>
                        )}
                      </td>
                      <td>
                        <div className="d-flex gap-2">
                          <button
                            className="btn btn-secondary btn-sm"
                            onClick={() => openEditModal(role)}
                            title={t('common.edit')}
                          >
                            <i className="fa-solid fa-pen"></i>
                          </button>
                          <button
                            className={`btn btn-sm ${role.isActive ? 'btn-secondary' : 'btn-success'}`}
                            onClick={() => handleToggle(role)}
                            title={role.isActive ? t('customRoles.disable') : t('customRoles.enable')}
                            style={{ fontSize: 11, minWidth: 60 }}
                          >
                            {role.isActive ? t('customRoles.disable') : t('customRoles.enable')}
                          </button>
                          <button
                            className="btn btn-outline-secondary btn-sm"
                            onClick={() => handleDelete(role)}
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
              <div className="p-3">
                <Pagination
                  page={page}
                  pageSize={pageSize}
                  totalItems={totalItems}
                  onPageChange={setPage}
                  onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
                  itemLabel={t('customRoles.itemLabel')}
                />
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Add / Edit Modal */}
      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editingId ? t('customRoles.editModalTitle') : t('customRoles.addModalTitle')}
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setShowModal(false)}>
              {t('common.cancel')}
            </button>
            <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
              {saving ? t('common.saving') : editingId ? t('customRoles.update') : t('customRoles.addRole')}
            </button>
          </>
        }
      >
        <div style={{ display: 'grid', gap: '16px' }}>
          <div className="mb-3">
            <label className="form-label">{t('customRoles.form.roleName')} *</label>
            <input
              type="text"
              className="form-control"
              value={form.displayName}
              onChange={(e) => setForm(p => ({ ...p, displayName: e.target.value }))}
              placeholder={t('customRoles.form.roleNamePlaceholder')}
            />
            <p className="text-muted small" style={{ marginTop: '4px' }}>
              {t('customRoles.form.roleNameHint')} <code>accountHolder</code> {t('customRoles.form.roleNameHintSuffix')}
            </p>
          </div>

          <div className="mb-3">
            <label className="form-label">{t('common.description')}</label>
            <textarea
              className="form-control"
              rows={3}
              value={form.description}
              onChange={(e) => setForm(p => ({ ...p, description: e.target.value }))}
              placeholder={t('customRoles.form.descriptionPlaceholder')}
            />
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default CustomRoles;
