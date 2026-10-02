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
import { dataHolderGroupsApi } from '../services/api';
import Loading from '../components/Loading';
import Modal from '../components/Modal';
import SubscribeFlow from '../components/SubscribeFlow';
import Pagination, { DEFAULT_PAGE_SIZE } from '../components/Pagination';
import { useT } from '../i18n';

const DataHolderGroups = () => {
  const { isGroupAdmin, isMasterAdmin } = useAuth();
  const { success, error: showError, confirm } = useAlert();
  const { t } = useT();

  const [dataHolderGroupGroups, setDataHolderGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [filterStatus, setFilterStatus] = useState('');

  // Server-side pagination state (1-based page).
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [totalItems, setTotalItems] = useState(0);

  // Modal states
  const [showModal, setShowModal] = useState(false);
  const [editingDataHolder, setEditingDataHolder] = useState(null);
  // The group whose templates are being browsed; also drives the subscribe flow.
  const [subscribeTarget, setSubscribeTarget] = useState(null);

  // Guards the create/edit form below; the subscribe form keeps its own.
  const [submitting, setSubmitting] = useState(false);
  
  const [formData, setFormData] = useState({
    code: '',
    name: '',
    description: '',
    baseUrl: '',
    clientId: '',
    clientSecret: '',
    contactEmail: '',
    notes: '',
    active: true,
  });

  // Debounce the search term into debouncedSearch (300ms).
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(searchTerm), 300);
    return () => clearTimeout(t);
  }, [searchTerm]);

  // Reset to the first page whenever the search / status filter changes.
  useEffect(() => {
    setPage(1);
  }, [debouncedSearch, filterStatus]);

  const loadDataHolderGroups = useCallback(async () => {
    try {
      const response = await dataHolderGroupsApi.getAll({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        /*
         * Only 'active' is expressible server-side; inactive/healthy/unhealthy are
         * narrowed client-side over the fetched page below.
         */
        activeOnly: filterStatus === 'active' ? true : undefined,
        sortBy: 'name',
        sortDir: 'asc',
      });
      const data = response.data.data || {};
      setDataHolderGroups(data.content || []);
      setTotalItems(data.totalElements || 0);
    } catch (err) {
      console.error('Failed to load data holder groups:', err);
      showError(t('dataHolderGroups.errors.loadFailed'));
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, pageSize, debouncedSearch, filterStatus]);

  // Fetch whenever page / size / search / status changes.
  useEffect(() => {
    loadDataHolderGroups();
  }, [loadDataHolderGroups]);


  const handleOpenModal = (dataHolderGroup = null) => {
    if (dataHolderGroup) {
      setEditingDataHolder(dataHolderGroup);
      setFormData({
        code: dataHolderGroup.code || '',
        name: dataHolderGroup.name || '',
        description: dataHolderGroup.description || '',
        baseUrl: dataHolderGroup.baseUrl || '',
        clientId: dataHolderGroup.clientId || '',
        // Never populated from the server; blank means "leave the stored secret alone".
        clientSecret: '',
            contactEmail: dataHolderGroup.contactEmail || '',
        notes: dataHolderGroup.notes || '',
        active: dataHolderGroup.active !== false,
      });
    } else {
      setEditingDataHolder(null);
      setFormData({
        code: '',
        name: '',
        description: '',
        baseUrl: '',
        clientId: '',
        clientSecret: '',
            contactEmail: '',
        notes: '',
        active: true,
      });
    }
    setShowModal(true);
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingDataHolder(null);
    setFormData({
      code: '',
      name: '',
      description: '',
      baseUrl: '',
      clientId: '',
      clientSecret: '',
        contactEmail: '',
      notes: '',
      active: true,
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!formData.code.trim() || !formData.name.trim() || !formData.baseUrl.trim()) {
      showError(t('dataHolderGroups.errors.requiredFields'));
      return;
    }

    setSubmitting(true);

    try {
      if (editingDataHolder) {
        await dataHolderGroupsApi.update(editingDataHolder.id, {
          name: formData.name,
          description: formData.description,
          baseUrl: formData.baseUrl,
          clientId: formData.clientId,
          // A blank secret leaves the stored one untouched.
          ...(formData.clientSecret ? { clientSecret: formData.clientSecret } : {}),
          contactEmail: formData.contactEmail,
          notes: formData.notes,
          active: formData.active,
        });
        success(t('dataHolderGroups.messages.updated'));
      } else {
        await dataHolderGroupsApi.create(formData);
        success(t('dataHolderGroups.messages.created'));
      }
      handleCloseModal();
      loadDataHolderGroups();
    } catch (err) {
      console.error('Failed to save data holder group:', err);
      showError(err.response?.data?.message || t('dataHolderGroups.errors.saveFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (dataHolderGroup) => {
    const confirmed = await confirm(
      t('dataHolderGroups.delete.title'),
      t('dataHolderGroups.delete.message', { name: dataHolderGroup.name, code: dataHolderGroup.code }),
      null,
      null,
      {
        confirmText: t('common.delete'),
        confirmVariant: 'danger',
        icon: 'fa-server',
        iconColor: 'danger',
      }
    );

    if (confirmed) {
      try {
        await dataHolderGroupsApi.delete(dataHolderGroup.id);
        success(t('dataHolderGroups.messages.deleted'));
        loadDataHolderGroups();
      } catch (err) {
        console.error('Failed to delete data holder group:', err);
        showError(err.response?.data?.message || t('dataHolderGroups.errors.deleteFailed'));
      }
    }
  };

  const handleToggleActive = async (dataHolderGroup) => {
    try {
      await dataHolderGroupsApi.toggleActive(dataHolderGroup.id);
      success(dataHolderGroup.active ? t('dataHolderGroups.messages.deactivated') : t('dataHolderGroups.messages.activated'));
      loadDataHolderGroups();
    } catch (err) {
      console.error('Failed to toggle status:', err);
      showError(t('dataHolderGroups.errors.toggleFailed'));
    }
  };

  const handleCheckHealth = async (dataHolderGroup) => {
    try {
      const response = await dataHolderGroupsApi.checkHealth(dataHolderGroup.id);
      const health = response.data.data;
      if (health.healthy) {
        success(t('dataHolderGroups.health.healthyResult', { name: dataHolderGroup.name }));
      } else {
        showError(t('dataHolderGroups.health.unhealthyResult', { name: dataHolderGroup.name, error: health.errorMessage || t('dataHolderGroups.health.unknownError') }));
      }
      loadDataHolderGroups();
    } catch (err) {
      console.error('Health check failed:', err);
      showError(t('dataHolderGroups.health.checkFailed'));
    }
  };


  /*
   * Search + 'active' are handled server-side. The inactive/healthy/unhealthy
   * filters can't be expressed by the API, so narrow the fetched page client-side.
   */
  const displayedDataHolderGroups = dataHolderGroupGroups.filter((dh) => {
    switch (filterStatus) {
      case 'inactive': return !dh.active;
      case 'healthy': return dh.healthStatus === 'HEALTHY';
      case 'unhealthy': return dh.healthStatus === 'UNHEALTHY';
      default: return true; // '' and 'active' already handled server-side
    }
  });

  const getHealthBadge = (status) => {
    switch (status) {
      case 'HEALTHY':
        return <span className="badge bg-success">{t('dataHolderGroups.health.healthy')}</span>;
      case 'UNHEALTHY':
        return <span className="badge bg-danger">{t('dataHolderGroups.health.unhealthy')}</span>;
      default:
        return <span className="badge bg-secondary">{t('common.unknown')}</span>;
    }
  };


  const canManage = isGroupAdmin();

  const canDelete = isMasterAdmin();

  if (loading) {
    return <Loading message={t('dataHolderGroups.loading')} />;
  }

  return (
    <div>
      {/* Page Header */}
      <div className="page-header d-flex justify-content-between align-items-start">
        <div>
          <h1>{t('dataHolderGroups.title')}</h1>
          <p>{t('dataHolderGroups.subtitle')}</p>
        </div>
        {canManage && (
          <button className="btn btn-primary" onClick={() => handleOpenModal()}>
            <i className="fas fa-plus me-2"></i>
            {t('dataHolderGroups.addButton')}
          </button>
        )}
      </div>
      {/* Filters */}
      <div className="card mb-4">
        <div className="card-body">
          <div className="row g-3">
            <div className="col-md-5">
              <div className="input-group">
                <span className="input-group-text">
                  <i className="fas fa-search"></i>
                </span>
                <input
                  type="text"
                  className="form-control"
                  placeholder={t('dataHolderGroups.filters.searchPlaceholder')}
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                />
              </div>
            </div>
            <div className="col-md-3">
              <select
                className="form-select"
                value={filterStatus}
                onChange={(e) => setFilterStatus(e.target.value)}
              >
                <option value="">{t('dataHolderGroups.filters.allStatus')}</option>
                <option value="active">{t('dataHolderGroups.filters.active')}</option>
                <option value="inactive">{t('dataHolderGroups.filters.inactive')}</option>
                <option value="healthy">{t('dataHolderGroups.filters.healthy')}</option>
                <option value="unhealthy">{t('dataHolderGroups.filters.unhealthy')}</option>
              </select>
            </div>
            <div className="col-md-2">
              <button
                className="btn btn-outline-secondary w-100"
                onClick={() => {
                  setSearchTerm('');
                  setFilterStatus('');
                }}
              >
                <i className="fas fa-times me-2"></i>
                {t('common.clear')}
              </button>
            </div>
            <div className="col-md-2">
              <button
                className="btn btn-outline-primary w-100"
                onClick={loadDataHolderGroups}
              >
                <i className="fas fa-sync-alt me-2"></i>
                {t('dataHolderGroups.filters.refresh')}
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Data Holder Groups Grid */}
      {displayedDataHolderGroups.length > 0 ? (
        <div className="row">
          {displayedDataHolderGroups.map((dh) => (
            <div key={dh.id} className="col-md-6 col-lg-4 mb-4">
              <div className={`card h-100 ${!dh.active ? 'opacity-75' : ''}`}>
                <div className="card-header d-flex justify-content-between align-items-center">
                  <div>
                    <h5 className="mb-0">{dh.code}</h5>
                    <small className="text-muted">{dh.name}</small>
                  </div>
                  <div className="d-flex gap-1 flex-wrap justify-content-end">
                    {dh.source === 'REGISTRY' ? (
                      <span className="badge bg-info text-dark" title={t('dataHolderGroups.source.registryNote')}>
                        <i className="fas fa-tower-broadcast me-1"></i>
                        {t('dataHolderGroups.source.registry')}
                      </span>
                    ) : (
                      <span className="badge bg-light text-dark border" title={t('dataHolderGroups.source.manualNote')}>
                        <i className="fas fa-pen me-1"></i>
                        {t('dataHolderGroups.source.manual')}
                      </span>
                    )}
                    {dh.duplicateCode && (
                      <span className="badge bg-warning text-dark" title={t('dataHolderGroups.source.duplicateNote')}>
                        <i className="fas fa-triangle-exclamation me-1"></i>
                        {t('dataHolderGroups.source.duplicate')}
                      </span>
                    )}
                    {dh.active ? (
                      <span className="badge bg-success">{t('dataHolderGroups.filters.active')}</span>
                    ) : (
                      <span className="badge bg-secondary">{t('dataHolderGroups.filters.inactive')}</span>
                    )}
                    {getHealthBadge(dh.healthStatus)}
                  </div>
                </div>
                <div className="card-body">
                  {dh.description && (
                    <p className="card-text text-muted small">{dh.description}</p>
                  )}
                  <div className="mb-2">
                    <small className="text-muted">
                      <i className="fas fa-link me-1"></i>
                      {dh.baseUrl}
                    </small>
                  </div>
                  {dh.contactEmail && (
                    <div className="mb-2">
                      <small className="text-muted">
                        <i className="fas fa-envelope me-1"></i>
                        {dh.contactEmail}
                      </small>
                    </div>
                  )}
                  {dh.lastContactAt && (
                    <div className="mb-2">
                      <small className="text-muted">
                        <i className="fas fa-clock me-1"></i>
                        {t('dataHolderGroups.card.lastContact', { time: new Date(dh.lastContactAt).toLocaleString() })}
                      </small>
                    </div>
                  )}
                  {dh.hasApiKey && (
                    <div>
                      <small className="text-success">
                        <i className="fas fa-key me-1"></i>
                        {t('dataHolderGroups.card.apiKeyConfigured')}
                      </small>
                    </div>
                  )}
                </div>
                <div className="card-footer">
                  <div className="btn-group w-100">
                    <button
                      className="btn btn-sm btn-outline-info"
                      onClick={() => setSubscribeTarget(dh)}
                      title={t('dataHolderGroups.card.viewTemplates')}
                    >
                      <i className="fas fa-file-alt me-1"></i>
                      {t('dataHolderGroups.card.templates')}
                    </button>
                    <button
                      className="btn btn-sm btn-outline-success"
                      onClick={() => handleCheckHealth(dh)}
                      title={t('dataHolderGroups.card.checkHealth')}
                    >
                      <i className="fas fa-heartbeat me-1"></i>
                      {t('dataHolderGroups.card.health')}
                    </button>
                    {canManage && (
                      <>
                        <button
                          className="btn btn-sm btn-outline-primary"
                          onClick={() => handleOpenModal(dh)}
                          disabled={dh.source === 'REGISTRY'}
                          title={dh.source === 'REGISTRY'
                            ? t('dataHolderGroups.source.editDisabled')
                            : t('common.edit')}
                        >
                          <i className="fas fa-edit"></i>
                        </button>
                        <button
                          className={`btn btn-sm btn-outline-${dh.active ? 'warning' : 'success'}`}
                          onClick={() => handleToggleActive(dh)}
                          title={dh.active ? t('dataHolderGroups.card.deactivate') : t('dataHolderGroups.card.activate')}
                        >
                          <i className={`fas fa-${dh.active ? 'pause' : 'play'}`}></i>
                        </button>
                      </>
                    )}
                    {canDelete && (
                      <button
                        className="btn btn-sm btn-outline-danger"
                        onClick={() => handleDelete(dh)}
                        title={t('common.delete')}
                      >
                        <i className="fas fa-trash"></i>
                      </button>
                    )}
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
              itemLabel={t('dataHolderGroups.itemLabel')}
            />
          </div>
        </div>
      ) : (
        <div className="empty-state">
          <div className="empty-icon">
            <i className="fas fa-server"></i>
          </div>
          <h5>{t('dataHolderGroups.empty.title')}</h5>
          <p>
            {searchTerm || filterStatus
              ? t('dataHolderGroups.empty.filtered')
              : t('dataHolderGroups.empty.noData')}
          </p>
          {canManage && !searchTerm && !filterStatus && (
            <button className="btn btn-primary" onClick={() => handleOpenModal()}>
              <i className="fas fa-plus me-2"></i>
              {t('dataHolderGroups.addButton')}
            </button>
          )}
        </div>
      )}

      {/* Create/Edit Modal */}
      <Modal
        show={showModal}
        onHide={handleCloseModal}
        title={editingDataHolder ? t('dataHolderGroups.form.editTitle') : t('dataHolderGroups.form.addTitle')}
        size="lg"
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
                  {editingDataHolder ? t('common.update') : t('common.create')}
                </>
              )}
            </button>
          </>
        }
      >
        <form onSubmit={handleSubmit}>
          <div className="row">
            <div className="col-md-4 mb-3">
              <label htmlFor="code" className="form-label">
                {t('dataHolderGroups.form.code')} <span className="text-danger">*</span>
              </label>
              <input
                type="text"
                className="form-control"
                id="code"
                value={formData.code}
                onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                placeholder={t('dataHolderGroups.form.codePlaceholder')}
                required
                disabled={!!editingDataHolder}
                pattern="^[A-Z0-9_-]+$"
              />
              <small className="text-muted">{t('dataHolderGroups.form.codeHelp')}</small>
            </div>
            <div className="col-md-8 mb-3">
              <label htmlFor="name" className="form-label">
                {t('common.name')} <span className="text-danger">*</span>
              </label>
              <input
                type="text"
                className="form-control"
                id="name"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                placeholder={t('dataHolderGroups.form.namePlaceholder')}
                required
              />
            </div>
          </div>

          <div className="mb-3">
            <label htmlFor="baseUrl" className="form-label">
              {t('dataHolderGroups.form.baseUrl')} <span className="text-danger">*</span>
            </label>
            <input
              type="url"
              className="form-control"
              id="baseUrl"
              value={formData.baseUrl}
              onChange={(e) => setFormData({ ...formData, baseUrl: e.target.value })}
              placeholder="https://dataholder.example.com"
              required
            />
            <small className="text-muted">{t('dataHolderGroups.form.baseUrlHelp')}</small>
          </div>

          {/* Credentials the data holder group issues when it accepts our requestor group. */}
          <div className="card bg-light border mb-3">
            <div className="card-body py-3">
              <h6 className="mb-1">
                <i className="fas fa-key text-warning me-2"></i>
                {t('dataHolderGroups.form.credentials')}
              </h6>
              <p className="text-muted small mb-3">{t('dataHolderGroups.form.credentialsHelp')}</p>
              <div className="row g-3">
                <div className="col-md-6">
                  <label htmlFor="clientId" className="form-label">{t('dataHolderGroups.form.clientId')}</label>
                  <input
                    type="text"
                    className="form-control font-monospace"
                    id="clientId"
                    value={formData.clientId}
                    onChange={(e) => setFormData({ ...formData, clientId: e.target.value })}
                    placeholder="rg-abc123-1a2b3c4d"
                    autoComplete="off"
                  />
                </div>
                <div className="col-md-6">
                  <label htmlFor="clientSecret" className="form-label">{t('dataHolderGroups.form.clientSecret')}</label>
                  <input
                    type="password"
                    className="form-control font-monospace"
                    id="clientSecret"
                    value={formData.clientSecret}
                    onChange={(e) => setFormData({ ...formData, clientSecret: e.target.value })}
                    placeholder={editingDataHolder ? t('dataHolderGroups.form.clientSecretUnchanged') : ''}
                    autoComplete="new-password"
                  />
                  {editingDataHolder && (
                    <small className="text-muted">{t('dataHolderGroups.form.clientSecretUnchanged')}</small>
                  )}
                </div>
              </div>
            </div>
          </div>

          <div className="mb-3">
            <label htmlFor="description" className="form-label">
              {t('dataHolderGroups.form.description')}
            </label>
            <textarea
              className="form-control"
              id="description"
              rows="2"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              placeholder={t('dataHolderGroups.form.descriptionPlaceholder')}
            />
          </div>

          <div className="row">
            <div className="col-md-6 mb-3">
              <label htmlFor="contactEmail" className="form-label">
                {t('dataHolderGroups.form.contactEmail')}
              </label>
              <input
                type="email"
                className="form-control"
                id="contactEmail"
                value={formData.contactEmail}
                onChange={(e) => setFormData({ ...formData, contactEmail: e.target.value })}
                placeholder="admin@dataholder.com"
              />
            </div>
          </div>

          <div className="mb-3">
            <label htmlFor="notes" className="form-label">
              {t('dataHolderGroups.form.notes')}
            </label>
            <textarea
              className="form-control"
              id="notes"
              rows="2"
              value={formData.notes}
              onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
              placeholder={t('dataHolderGroups.form.notesPlaceholder')}
            />
          </div>

          <div className="form-check">
            <input
              type="checkbox"
              className="form-check-input"
              id="active"
              checked={formData.active}
              onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
            />
            <label className="form-check-label" htmlFor="active">
              {t('dataHolderGroups.filters.active')}
            </label>
          </div>
        </form>
      </Modal>

      <SubscribeFlow
        show={!!subscribeTarget}
        dataHolderGroup={subscribeTarget}
        onHide={() => setSubscribeTarget(null)}
      />
    </div>
  );
};

export default DataHolderGroups;