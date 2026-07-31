/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { useAlert } from '../context/AlertContext';
import { requestorGroupsApi, subscriptionsApi } from '../services/api';
import Loading from '../components/Loading';
import Modal from '../components/Modal';
import Pagination, { DEFAULT_PAGE_SIZE } from '../components/Pagination';
import { format } from 'date-fns';
import { useT } from '../i18n';

const RequestorGroups = () => {
  const { isGroupAdmin, canManageGroup } = useAuth();
  const { success, error: showError, confirm } = useAlert();
  const { t } = useT();

  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');

  // Server-side pagination state (1-based page).
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [totalItems, setTotalItems] = useState(0);

  // Modal state
  const [showModal, setShowModal] = useState(false);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [editingGroup, setEditingGroup] = useState(null);
  const [selectedGroup, setSelectedGroup] = useState(null);
  const [groupAgreements, setGroupAgreements] = useState([]);
  const [formData, setFormData] = useState({
    name: '',
    code: '',
    description: '',
    defaultIntrospectionUrl: '',
  });
  const [submitting, setSubmitting] = useState(false);

  const [introspectionDefault, setIntrospectionDefault] = useState('');

  useEffect(() => {
    requestorGroupsApi
      .getIntrospectionDefault()
      .then((response) => setIntrospectionDefault(response.data.data || ''))
      .catch((err) => console.error('Failed to load default introspection URL:', err));
  }, []);

  // Debounce the search term into debouncedSearch (300ms).
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(searchTerm), 300);
    return () => clearTimeout(t);
  }, [searchTerm]);

  // Reset to the first page whenever the search changes.
  useEffect(() => {
    setPage(1);
  }, [debouncedSearch]);

  const loadGroups = useCallback(async () => {
    try {
      const response = await requestorGroupsApi.getAll({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        sortBy: 'name',
        sortDir: 'asc',
      });
      const data = response.data.data || {};
      setGroups(data.content || []);
      setTotalItems(data.totalElements || 0);
    } catch (err) {
      console.error('Failed to load groups:', err);
      showError(t('requestorGroups.errors.loadFailed'));
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, pageSize, debouncedSearch]);

  // Fetch whenever page / size / search changes.
  useEffect(() => {
    loadGroups();
  }, [loadGroups]);

  const handleOpenModal = (group = null) => {
    if (group) {
      setEditingGroup(group);
      setFormData({
        name: group.name,
        code: group.code || '',
        description: group.description || '',
        defaultIntrospectionUrl: group.defaultIntrospectionUrl || '',
      });
    } else {
      setEditingGroup(null);
      // New group: auto-fill the introspection URL from the current environment (editable).
      setFormData({ name: '', code: '', description: '', defaultIntrospectionUrl: introspectionDefault });
    }
    setShowModal(true);
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingGroup(null);
    setFormData({ name: '', code: '', description: '', defaultIntrospectionUrl: '' });
  };

  const handleViewDetails = async (group) => {
    setSelectedGroup(group);
    setShowDetailModal(true);

    try {
      const [subscriptionsRes, agreementsRes] = await Promise.all([
        subscriptionsApi.getByRequestorGroup(group.id).catch(() => ({ data: { data: [] } })),
        subscriptionsApi.getAgreementsByRequestorGroup(group.id).catch(() => ({ data: { data: [] } })),
      ]);
      setGroupAgreements({
        subscriptions: subscriptionsRes.data.data || [],
        agreements: agreementsRes.data.data || [],
      });
    } catch (err) {
      console.error('Failed to load group data:', err);
      setGroupAgreements({ subscriptions: [], agreements: [] });
    }
  };

  const handleCloseDetailModal = () => {
    setShowDetailModal(false);
    setSelectedGroup(null);
    setGroupAgreements([]);
  };

  /**
   * Validate group code: ≤10 chars, alphanumeric + hyphens, no leading/trailing hyphen
   */
  const validateCode = (code) => {
    if (!code || code.trim() === '') return null; // Empty is OK (optional for now)
    const trimmed = code.trim();
    if (trimmed.length > 10) return t('requestorGroups.validation.codeTooLong');
    if (!/^[A-Za-z0-9]([A-Za-z0-9-]{0,8}[A-Za-z0-9])?$/.test(trimmed) && trimmed.length > 1) {
      return t('requestorGroups.validation.codeFormat');
    }
    if (trimmed.length === 1 && !/^[A-Za-z0-9]$/.test(trimmed)) {
      return t('requestorGroups.validation.codeAlphanumeric');
    }
    return null;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!formData.name.trim()) {
      showError(t('requestorGroups.validation.nameRequired'));
      return;
    }

    const codeError = validateCode(formData.code);
    if (codeError) {
      showError(codeError);
      return;
    }

    setSubmitting(true);

    try {
      const payload = {
        name: formData.name,
        description: formData.description,
        code: formData.code.trim() || null,
        // Send the trimmed value (empty string included) so the backend's blank→null logic can
        // both set and CLEAR the introspection URL on update.
        defaultIntrospectionUrl: formData.defaultIntrospectionUrl.trim(),
      };

      if (editingGroup) {
        await requestorGroupsApi.update(editingGroup.id, payload);
        success(t('requestorGroups.success.updated'));
      } else {
        await requestorGroupsApi.create(payload);
        success(t('requestorGroups.success.created'));
      }

      handleCloseModal();
      loadGroups();
    } catch (err) {
      console.error('Failed to save group:', err);
      showError(err.response?.data?.message || t('requestorGroups.errors.saveFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (group) => {
    const confirmed = await confirm(
      t('requestorGroups.deleteConfirm.title'),
      t('requestorGroups.deleteConfirm.message', { name: group.name }),
      null,
      null,
      {
        confirmText: t('common.delete'),
        confirmVariant: 'danger',
        icon: 'fa-trash-alt',
        iconColor: 'danger',
      }
    );

    if (confirmed) {
      try {
        await requestorGroupsApi.delete(group.id);
        success(t('requestorGroups.success.deleted'));
        loadGroups();
      } catch (err) {
        console.error('Failed to delete group:', err);
        showError(err.response?.data?.message || t('requestorGroups.errors.deleteFailed'));
      }
    }
  };

  // Check permissions
  const canCreate = isGroupAdmin();
  const canEdit = (group) => canManageGroup(group.name);
  const canDelete = isGroupAdmin();

  if (loading) {
    return <Loading message={t('requestorGroups.loading')} />;
  }

  return (
    <div>
      {/* Page Header */}
      <div className="page-header d-flex justify-content-between align-items-start">
        <div>
          <h1>{t('requestorGroups.title')}</h1>
          <p>{t('requestorGroups.subtitle')}</p>
        </div>
        {canCreate && (
          <button className="btn btn-primary" onClick={() => handleOpenModal()}>
            <i className="fas fa-plus me-2"></i>
            {t('requestorGroups.newGroup')}
          </button>
        )}
      </div>

      {/* Search */}
      <div className="card mb-4">
        <div className="card-body">
          <div className="row g-3">
            <div className="col-md-8">
              <div className="input-group">
                <span className="input-group-text">
                  <i className="fas fa-search"></i>
                </span>
                <input
                  type="text"
                  className="form-control"
                  placeholder={t('requestorGroups.searchPlaceholder')}
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                />
              </div>
            </div>
            <div className="col-md-4">
              <button
                className="btn btn-outline-secondary"
                onClick={() => setSearchTerm('')}
                disabled={!searchTerm}
              >
                <i className="fas fa-times me-2"></i>
                {t('common.clear')}
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Groups Grid */}
      {groups.length > 0 ? (
        <div className="row g-4">
          {groups.map((group) => (
            <div key={group.id} className="col-md-6 col-lg-4">
              <div className="card h-100">
                <div className="card-body">
                  <div className="d-flex justify-content-between align-items-start mb-3">
                    <div>
                      <h5 className="card-title mb-1">{group.name}</h5>
                      <div className="d-flex align-items-center gap-2">
                        <small className="text-muted">{t('requestorGroups.idLabel')} {group.id}</small>
                        {group.code && (
                          <span className="badge bg-secondary" style={{ fontFamily: 'monospace', fontSize: '11px' }}>
                            {group.code}
                          </span>
                        )}
                      </div>
                    </div>
                    <span className="badge bg-primary">
                      <i className="fas fa-building me-1"></i>
                      {t('requestorGroups.groupBadge')}
                    </span>
                  </div>

                  <p className="card-text text-muted">
                    {group.description || t('common.noDescription')}
                  </p>

                  {group.createdByEmail && (
                    <div className="mb-3">
                      <small className="text-muted">
                        <i className="fas fa-user me-1"></i>
                        {t('requestorGroups.createdByLabel')} {group.createdByName || group.createdByEmail}
                      </small>
                    </div>
                  )}

                  <div className="d-flex justify-content-between align-items-center mt-auto pt-3 border-top">
                    <small className="text-muted">
                      <i className="fas fa-calendar me-1"></i>
                      {group.createdAt
                        ? format(new Date(group.createdAt), 'MMM d, yyyy')
                        : t('common.na')}
                    </small>
                    <div className="btn-group">
                      <button
                        className="btn btn-sm btn-outline-info"
                        onClick={() => handleViewDetails(group)}
                        title={t('common.viewDetails')}
                      >
                        <i className="fas fa-eye"></i>
                      </button>
                      {canEdit(group) && (
                        <button
                          className="btn btn-sm btn-outline-primary"
                          onClick={() => handleOpenModal(group)}
                          title={t('common.edit')}
                        >
                          <i className="fas fa-edit"></i>
                        </button>
                      )}
                      {canDelete && (
                        <button
                          className="btn btn-sm btn-outline-danger"
                          onClick={() => handleDelete(group)}
                          title={t('common.delete')}
                        >
                          <i className="fas fa-trash-alt"></i>
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          ))}
          <div className="col-12">
            <Pagination
              page={page}
              pageSize={pageSize}
              totalItems={totalItems}
              onPageChange={setPage}
              onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
              itemLabel={t('requestorGroups.itemLabel')}
            />
          </div>
        </div>
      ) : (
        <div className="card">
          <div className="card-body">
            <div className="empty-state">
              <div className="empty-icon">
                <i className="fas fa-building"></i>
              </div>
              <h5>{t('requestorGroups.empty.title')}</h5>
              <p>
                {searchTerm
                  ? t('requestorGroups.empty.filtered')
                  : t('requestorGroups.empty.default')}
              </p>
              {canCreate && !searchTerm && (
                <button className="btn btn-primary" onClick={() => handleOpenModal()}>
                  <i className="fas fa-plus me-2"></i>
                  {t('requestorGroups.empty.createButton')}
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Create/Edit Modal */}
      <Modal
        show={showModal}
        onHide={handleCloseModal}
        title={editingGroup ? t('requestorGroups.editTitle') : t('requestorGroups.newTitle')}
        footer={
          <>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={handleCloseModal}
              disabled={submitting}
            >
              {t('common.cancel')}
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleSubmit}
              disabled={submitting}
            >
              {submitting ? (
                <>
                  <span className="spinner-border spinner-border-sm me-2"></span>
                  {t('common.saving')}
                </>
              ) : (
                <>
                  <i className="fas fa-save me-2"></i>
                  {editingGroup ? t('common.update') : t('common.create')}
                </>
              )}
            </button>
          </>
        }
      >
        <form onSubmit={handleSubmit}>
          <div className="mb-3">
            <label htmlFor="name" className="form-label">
              {t('requestorGroups.form.name')} <span className="text-danger">*</span>
            </label>
            <input
              type="text"
              className="form-control"
              id="name"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              placeholder={t('requestorGroups.form.namePlaceholder')}
              required
            />
            <div className="form-text">
              {t('requestorGroups.form.nameHelp')}
            </div>
          </div>

          <div className="mb-3">
            <label htmlFor="code" className="form-label">
              {t('requestorGroups.form.code')}
            </label>
            <input
              type="text"
              className="form-control font-monospace"
              id="code"
              value={formData.code}
              onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
              placeholder={t('requestorGroups.form.codePlaceholder')}
              maxLength={10}
              style={{ letterSpacing: '0.5px' }}
            />
            <div className="form-text">
              {t('requestorGroups.form.codeHelp')}
              {formData.code && (
                <span className="ms-2">
                  {validateCode(formData.code) ? (
                    <span className="text-danger">
                      <i className="fas fa-exclamation-circle me-1"></i>
                      {validateCode(formData.code)}
                    </span>
                  ) : (
                    <span className="text-success">
                      <i className="fas fa-check-circle me-1"></i>
                      {t('requestorGroups.form.codeValid')}
                    </span>
                  )}
                </span>
              )}
            </div>
          </div>

          <div className="mb-3">
            <label htmlFor="description" className="form-label">
              {t('requestorGroups.form.description')}
            </label>
            <textarea
              className="form-control"
              id="description"
              rows="3"
              value={formData.description}
              onChange={(e) =>
                setFormData({ ...formData, description: e.target.value })
              }
              placeholder={t('requestorGroups.form.descriptionPlaceholder')}
            ></textarea>
          </div>

          <div className="mb-3">
            <label htmlFor="defaultIntrospectionUrl" className="form-label">
              {t('requestorGroups.form.introspectionUrl')}
            </label>
            <input
              type="url"
              className="form-control"
              id="defaultIntrospectionUrl"
              value={formData.defaultIntrospectionUrl}
              onChange={(e) =>
                setFormData({ ...formData, defaultIntrospectionUrl: e.target.value })
              }
              placeholder={t('requestorGroups.form.introspectionUrlPlaceholder')}
            />
            <div className="form-text">
              {t('requestorGroups.form.introspectionUrlHelp')}
            </div>
          </div>
        </form>
      </Modal>

      {/* Detail Modal */}
      <Modal
        show={showDetailModal}
        onHide={handleCloseDetailModal}
        title={t('requestorGroups.detail.title', { name: selectedGroup?.name || '' })}
        size="lg"
        footer={
          <button
            type="button"
            className="btn btn-secondary"
            onClick={handleCloseDetailModal}
          >
            {t('common.close')}
          </button>
        }
      >
        {selectedGroup && (
          <div>
            <div className="row mb-4">
              <div className="col-md-4">
                <h6 className="text-muted mb-1">{t('requestorGroups.detail.name')}</h6>
                <p className="fw-semibold">{selectedGroup.name}</p>
              </div>
              <div className="col-md-4">
                <h6 className="text-muted mb-1">{t('requestorGroups.detail.code')}</h6>
                <p className="fw-semibold">
                  {selectedGroup.code ? (
                    <code style={{ fontSize: '14px', padding: '2px 6px', backgroundColor: 'var(--bs-gray-100, #f8f9fa)', borderRadius: '4px' }}>
                      {selectedGroup.code}
                    </code>
                  ) : (
                    <span className="text-muted fst-italic">{t('common.notSet')}</span>
                  )}
                </p>
              </div>
              <div className="col-md-4">
                <h6 className="text-muted mb-1">{t('requestorGroups.detail.id')}</h6>
                <p className="fw-semibold">{selectedGroup.id}</p>
              </div>
            </div>

            <div className="mb-4">
              <h6 className="text-muted mb-1">{t('requestorGroups.detail.description')}</h6>
              <p>{selectedGroup.description || t('common.noDescription')}</p>
            </div>

            <div className="mb-4">
              <h6 className="text-muted mb-1">{t('requestorGroups.detail.introspectionUrl')}</h6>
              <p>
                {selectedGroup.defaultIntrospectionUrl ? (
                  <code style={{ fontSize: '13px', wordBreak: 'break-all' }}>
                    {selectedGroup.defaultIntrospectionUrl}
                  </code>
                ) : (
                  <span className="text-muted fst-italic">{t('requestorGroups.detail.introspectionNotConfigured')}</span>
                )}
              </p>
            </div>

            <div className="row mb-4">
              <div className="col-md-6">
                <h6 className="text-muted mb-1">{t('requestorGroups.detail.createdBy')}</h6>
                <p>
                  {selectedGroup.createdByName || selectedGroup.createdByEmail || t('common.unknown')}
                </p>
              </div>
              <div className="col-md-6">
                <h6 className="text-muted mb-1">{t('requestorGroups.detail.createdAt')}</h6>
                <p>
                  {selectedGroup.createdAt
                    ? format(new Date(selectedGroup.createdAt), 'PPpp')
                    : t('common.na')}
                </p>
              </div>
            </div>

            <hr />

            <h5 className="mb-3">
              <i className="fas fa-file-contract me-2"></i>
              {t('requestorGroups.detail.activeAgreements', { count: groupAgreements.agreements?.length || 0 })}
            </h5>

            {groupAgreements.agreements?.length > 0 ? (
              <div className="table-responsive">
                <table className="table table-sm">
                  <thead>
                    <tr>
                      <th>{t('common.name')}</th>
                      <th>{t('requestorGroups.detail.dataHolder')}</th>
                      <th>{t('requestorGroups.detail.accessLevel')}</th>
                      <th>{t('common.status')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {groupAgreements.agreements.map((agreement) => (
                      <tr key={agreement.id}>
                        <td className="fw-semibold">{agreement.name}</td>
                        <td className="text-muted">{agreement.dataHolderGroupName || agreement.dataHolderGroupCode || '-'}</td>
                        <td>
                          <span className={`badge ${
                            agreement.accessLevel === 3 ? 'bg-success' :
                            agreement.accessLevel === 2 ? 'bg-info' :
                            agreement.accessLevel === 1 ? 'bg-warning text-dark' : 'bg-secondary'
                          }`}>
                            {t('requestorGroups.detail.level', { level: agreement.accessLevel })}
                          </span>
                        </td>
                        <td>
                          <span className={`badge ${agreement.isCurrentlyEffective ? 'bg-success' : 'bg-secondary'}`}>
                            {agreement.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="alert alert-info">
                <i className="fas fa-info-circle me-2"></i>
                {t('requestorGroups.detail.noAgreements')}
              </div>
            )}

            <h5 className="mb-3 mt-4">
              <i className="fas fa-paper-plane me-2"></i>
              {t('requestorGroups.detail.subscriptionRequests', { count: groupAgreements.subscriptions?.length || 0 })}
            </h5>

            {groupAgreements.subscriptions?.length > 0 ? (
              <div className="table-responsive">
                <table className="table table-sm">
                  <thead>
                    <tr>
                      <th>{t('requestorGroups.detail.template')}</th>
                      <th>{t('requestorGroups.detail.dataHolder')}</th>
                      <th>{t('common.status')}</th>
                      <th>{t('requestorGroups.detail.requested')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {groupAgreements.subscriptions.map((sub) => (
                      <tr key={sub.id}>
                        <td className="fw-semibold">{sub.templateName || sub.templateId}</td>
                        <td className="text-muted">{sub.dataHolderGroupName || sub.dataHolderGroupCode || '-'}</td>
                        <td>
                          <span className={`badge ${
                            sub.status === 'ACTIVE' ? 'bg-success' :
                            sub.status === 'APPROVED' || sub.status === 'TESTING' ? 'bg-info' :
                            sub.status === 'SUBMITTED' || sub.status === 'PENDING_REVIEW' ? 'bg-warning text-dark' :
                            sub.status === 'DECLINED' || sub.status === 'CANCELLED' ? 'bg-danger' : 'bg-secondary'
                          }`}>
                            {sub.status}
                          </span>
                        </td>
                        <td className="text-muted small">
                          {sub.createdAt ? format(new Date(sub.createdAt), 'MMM d, yyyy') : '-'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="alert alert-light">
                <i className="fas fa-info-circle me-2"></i>
                {t('requestorGroups.detail.noSubscriptions')}
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default RequestorGroups;