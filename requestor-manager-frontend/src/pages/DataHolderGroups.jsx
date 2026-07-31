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
import { dataHolderGroupsApi, subscriptionsApi, requestorGroupsApi } from '../services/api';
import Loading from '../components/Loading';
import Modal from '../components/Modal';
import Pagination, { DEFAULT_PAGE_SIZE } from '../components/Pagination';
import { useT } from '../i18n';

const DataHolderGroups = () => {
  const { isGroupAdmin, isMasterAdmin, user } = useAuth();
  const { success, error: showError, confirm } = useAlert();
  const { t } = useT();

  const [dataHolderGroupGroups, setDataHolderGroups] = useState([]);
  const [templates, setTemplates] = useState([]);
  const [requestorGroups, setRequestorGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [filterStatus, setFilterStatus] = useState('');

  // Server-side pagination state (1-based page).
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [totalItems, setTotalItems] = useState(0);

  // Modal states
  const [showModal, setShowModal] = useState(false);
  const [showTemplatesModal, setShowTemplatesModal] = useState(false);
  const [showSubscribeModal, setShowSubscribeModal] = useState(false);
  const [editingDataHolder, setEditingDataHolder] = useState(null);
  const [selectedDataHolder, setSelectedDataHolder] = useState(null);
  const [selectedTemplate, setSelectedTemplate] = useState(null);
  
  const [formData, setFormData] = useState({
    code: '',
    name: '',
    description: '',
    baseUrl: '',
    contactEmail: '',
    notes: '',
    active: true,
  });

  const [subscriptionFormData, setSubscriptionFormData] = useState({
    requestorGroupId: '',
    requestorFirstName: '',
    requestorLastName: '',
    requestorOrganization: '',
    requestorEmail: '',
    requestorPhone: '',
    requestorAddress: '',
    requestorCity: '',
    requestorStateProvince: '',
    requestorPostalCode: '',
    requestorCountry: '',
    reasonForUse: '',
    requestedAccessLevel: 0,
    additionalNotes: '',
    introspectionUrl: '',
    submitImmediately: true,
  });

  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (user) {
      loadRequestorGroups();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

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

  const loadRequestorGroups = async () => {
    try {
      const response = await requestorGroupsApi.getAll();
      const allGroups = response.data.data || [];
      
      if (isMasterAdmin() || isGroupAdmin()) {
        setRequestorGroups(allGroups);
        return;
      }
      
      const userGroupNames = user?.groups || [];
      
      if (userGroupNames.length === 0) {
        setRequestorGroups([]);
        return;
      }
      
      const userGroups = allGroups.filter(group => {
        const matches = 
          userGroupNames.includes(group.name) || 
          userGroupNames.includes(group.code) ||
          userGroupNames.includes(group.id?.toString()) ||
          userGroupNames.some(ug => 
            ug.toLowerCase() === group.name?.toLowerCase() ||
            ug.toLowerCase() === group.code?.toLowerCase()
          );
        return matches;
      });
      
      setRequestorGroups(userGroups);
    } catch (err) {
      console.error('Failed to load requestor groups:', err);
    }
  };

  const handleOpenModal = (dataHolderGroup = null) => {
    if (dataHolderGroup) {
      setEditingDataHolder(dataHolderGroup);
      setFormData({
        code: dataHolderGroup.code || '',
        name: dataHolderGroup.name || '',
        description: dataHolderGroup.description || '',
        baseUrl: dataHolderGroup.baseUrl || '',
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

  const handleViewTemplates = async (dataHolderGroup) => {
    setSelectedDataHolder(dataHolderGroup);
    setLoadingTemplates(true);
    setShowTemplatesModal(true);

    try {
      const response = await dataHolderGroupsApi.getTemplates(dataHolderGroup.id);
      setTemplates(response.data.data || []);
    } catch (err) {
      console.error('Failed to load templates:', err);
      showError(t('dataHolderGroups.errors.loadTemplatesFailed'));
      setTemplates([]);
    } finally {
      setLoadingTemplates(false);
    }
  };

  const handleOpenSubscribeModal = (template) => {
    setSelectedTemplate(template);
    setShowTemplatesModal(false);
    
    setSubscriptionFormData({
      requestorGroupId: requestorGroups.length > 0 ? requestorGroups[0].id : '',
      requestorFirstName: user?.firstName || '',
      requestorLastName: user?.lastName || '',
      requestorOrganization: '',
      requestorEmail: user?.email || '',
      requestorPhone: '',
      requestorAddress: '',
      requestorCity: '',
      requestorStateProvince: '',
      requestorPostalCode: '',
      requestorCountry: '',
      reasonForUse: '',
      requestedAccessLevel: template.accessLevel || 0,
      additionalNotes: '',
      introspectionUrl: '',
      submitImmediately: true,
    });
    
    setShowSubscribeModal(true);
  };

  const handleCloseSubscribeModal = () => {
    setShowSubscribeModal(false);
    setSelectedTemplate(null);
    setSubscriptionFormData({
      requestorGroupId: '',
      requestorFirstName: '',
      requestorLastName: '',
      requestorOrganization: '',
      requestorEmail: '',
      requestorPhone: '',
      requestorAddress: '',
      requestorCity: '',
      requestorStateProvince: '',
      requestorPostalCode: '',
      requestorCountry: '',
      reasonForUse: '',
      requestedAccessLevel: 0,
      additionalNotes: '',
      introspectionUrl: '',
      submitImmediately: true,
    });
  };

  const isDuplicateSubscriptionError = (message) => {
    if (!message) return false;
    const lower = message.toLowerCase();
    return (lower.includes('already has') && lower.includes('subscription')) ||
           (lower.includes('already') && lower.includes('exists')) ||
           lower.includes('only one subscription per data holder group') ||
           lower.includes('one subscription per requestor group');
  };

  const handleSubscriptionSubmit = async (e) => {
    e.preventDefault();

    if (!subscriptionFormData.requestorGroupId) {
      showError(t('dataHolderGroups.subscribe.validation.selectGroup'));
      return;
    }
    if (!subscriptionFormData.requestorFirstName.trim()) {
      showError(t('dataHolderGroups.subscribe.validation.firstNameRequired'));
      return;
    }
    if (!subscriptionFormData.requestorLastName.trim()) {
      showError(t('dataHolderGroups.subscribe.validation.lastNameRequired'));
      return;
    }
    if (!subscriptionFormData.requestorEmail.trim()) {
      showError(t('dataHolderGroups.subscribe.validation.emailRequired'));
      return;
    }
    if (!subscriptionFormData.reasonForUse.trim()) {
      showError(t('dataHolderGroups.subscribe.validation.reasonRequired'));
      return;
    }

    setSubmitting(true);

    try {
      const payload = {
        dataHolderGroupId: selectedDataHolder.id,
        templateId: selectedTemplate.templateId,
        templateName: selectedTemplate.name,
        ...subscriptionFormData,
        requestorGroupId: parseInt(subscriptionFormData.requestorGroupId),
        requestedAccessLevel: parseInt(subscriptionFormData.requestedAccessLevel) || 0,
      };

      const response = await subscriptionsApi.create(payload);

      const responseData = response.data?.data;
      if (responseData?.status === 'DECLINED' && isDuplicateSubscriptionError(responseData?.statusMessage)) {
        const groupName = requestorGroups.find(
          g => g.id === parseInt(subscriptionFormData.requestorGroupId)
        )?.name || t('dataHolderGroups.subscribe.yourGroup');

        showError(
          t('dataHolderGroups.subscribe.duplicateError', { groupName, dataHolderName: selectedDataHolder.name })
        );
        handleCloseSubscribeModal();
        return;
      }
      
      if (subscriptionFormData.submitImmediately) {
        success(t('dataHolderGroups.subscribe.submittedSuccess'));
      } else {
        success(t('dataHolderGroups.subscribe.draftCreated'));
      }
      
      handleCloseSubscribeModal();
    } catch (err) {
      console.error('Failed to create subscription:', err);

      const errorMessage = err.response?.data?.message || err.response?.data?.error || '';

      if (isDuplicateSubscriptionError(errorMessage)) {
        const groupName = requestorGroups.find(
          g => g.id === parseInt(subscriptionFormData.requestorGroupId)
        )?.name || t('dataHolderGroups.subscribe.yourGroup');

        showError(
          t('dataHolderGroups.subscribe.duplicateError', { groupName, dataHolderName: selectedDataHolder.name })
        );
      } else {
        showError(errorMessage || t('dataHolderGroups.subscribe.createFailed'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleRequestorGroupChange = (groupId) => {
    const group = requestorGroups.find(g => g.id === parseInt(groupId));
    if (group) {
      setSubscriptionFormData(prev => ({
        ...prev,
        requestorGroupId: groupId,
        requestorOrganization: group.defaultOrganization || prev.requestorOrganization,
        requestorEmail: group.defaultContactEmail || prev.requestorEmail,
        requestorPhone: group.defaultContactPhone || prev.requestorPhone,
        requestorAddress: group.defaultAddress || prev.requestorAddress,
        requestorCity: group.defaultCity || prev.requestorCity,
        requestorStateProvince: group.defaultStateProvince || prev.requestorStateProvince,
        requestorPostalCode: group.defaultPostalCode || prev.requestorPostalCode,
        requestorCountry: group.defaultCountry || prev.requestorCountry,
        introspectionUrl: group.defaultIntrospectionUrl || prev.introspectionUrl,
      }));
    } else {
      setSubscriptionFormData(prev => ({ ...prev, requestorGroupId: groupId }));
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

  const getAccessLevelBadge = (level) => {
    const levels = {
      0: { label: t('dataHolderGroups.accessLevels.public'), color: 'secondary' },
      1: { label: t('dataHolderGroups.accessLevels.basic'), color: 'info' },
      2: { label: t('dataHolderGroups.accessLevels.enhanced'), color: 'warning' },
      3: { label: t('dataHolderGroups.accessLevels.full'), color: 'danger' },
    };
    const config = levels[level] || levels[0];
    return <span className={`badge bg-${config.color}`}>{t('dataHolderGroups.accessLevels.badge', { level, label: config.label })}</span>;
  };

  const getRequestTypeBadge = (rt) => {
    if (rt.supportsExigent) {
      return <span className="badge bg-danger"><i className="fas fa-bolt me-1"></i>{t('dataHolderGroups.requestType.exigent')}</span>;
    }
    if (rt.supportsConfidential) {
      return <span className="badge bg-warning text-dark"><i className="fas fa-lock me-1"></i>{t('dataHolderGroups.requestType.confidential')}</span>;
    }
    return <span className="badge bg-secondary"><i className="fas fa-globe me-1"></i>{t('dataHolderGroups.requestType.standard')}</span>;
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
                  <div className="d-flex gap-1">
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
                      onClick={() => handleViewTemplates(dh)}
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
                          title={t('common.edit')}
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

      {/* Templates Modal */}
      <Modal
        show={showTemplatesModal}
        onHide={() => setShowTemplatesModal(false)}
        title={t('dataHolderGroups.templates.modalTitle', { name: selectedDataHolder?.name || t('dataHolderGroups.templates.defaultName') })}
        size="lg"
        footer={
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => setShowTemplatesModal(false)}
          >
            {t('common.close')}
          </button>
        }
      >
        {loadingTemplates ? (
          <div className="text-center py-4">
            <div className="spinner-border text-primary" role="status">
              <span className="visually-hidden">{t('common.loading')}</span>
            </div>
            <p className="mt-2">{t('dataHolderGroups.templates.loading')}</p>
          </div>
        ) : templates.length > 0 ? (
          <div className="list-group">
            {templates.map((template, index) => (
              <div key={index} className="list-group-item">
                <div className="d-flex justify-content-between align-items-start">
                  <div className="flex-grow-1">
                    <div className="d-flex align-items-center gap-2 mb-1">
                      <h6 className="mb-0">{template.name}</h6>
                    </div>
                    <p className="mb-1 text-muted small">{template.description}</p>

                    {/* Request Types */}
                    {template.requestTypes && template.requestTypes.length > 0 ? (
                      <div className="mt-2 mb-2">
                        <small className="text-muted d-block mb-1">
                          <strong>{t('dataHolderGroups.templates.requestTypes')}</strong>
                        </small>
                        <div className="d-flex flex-wrap gap-2">
                          {template.requestTypes.map((rt, rtIndex) => (
                            <div
                              key={rtIndex}
                              className="border rounded px-2 py-1 d-inline-flex align-items-center gap-1"
                              style={{ fontSize: '0.8rem' }}
                            >
                              {getRequestTypeBadge(rt)}
                              {getAccessLevelBadge(rt.accessLevel)}
                              {rt.requiresManualApproval && (
                                <span className="badge bg-warning text-dark" style={{ fontSize: '0.65rem' }}>
                                  <i className="fas fa-user-check me-1"></i>{t('dataHolderGroups.templates.manual')}
                                </span>
                              )}
                              {rt.description && (
                                <span className="text-muted ms-1" title={rt.description}>
                                  <i className="fas fa-info-circle"></i>
                                </span>
                              )}
                            </div>
                          ))}
                        </div>
                      </div>
                    ) : (
                      <div className="mt-2 mb-2">
                        {getAccessLevelBadge(template.accessLevel)}
                      </div>
                    )}

                    <div className="d-flex flex-wrap gap-2 mt-2">
                      <small className="text-muted">
                        <i className="fas fa-fingerprint me-1"></i>
                        {t('dataHolderGroups.templates.id', { id: template.templateId })}
                      </small>
                      {template.requiredGroupTypes && (
                        <small className="text-muted">
                          <i className="fas fa-users me-1"></i>
                          {t('dataHolderGroups.templates.required', { types: template.requiredGroupTypes })}
                        </small>
                      )}
                      {template.maxQueriesPerDay && (
                        <small className="text-muted">
                          <i className="fas fa-tachometer-alt me-1"></i>
                          {t('dataHolderGroups.templates.perDay', { count: template.maxQueriesPerDay })}
                        </small>
                      )}
                    </div>
                  </div>
                  <button
                    className="btn btn-primary btn-sm ms-3"
                    onClick={() => handleOpenSubscribeModal(template)}
                    disabled={requestorGroups.length === 0}
                    title={requestorGroups.length === 0 ? t('dataHolderGroups.templates.subscribeDisabledTitle') : t('dataHolderGroups.templates.subscribeTitle')}
                  >
                    <i className="fas fa-file-signature me-1"></i>
                    {t('dataHolderGroups.templates.subscribe')}
                  </button>
                </div>
                {template.termsAndConditions && (
                  <div className="mt-2 p-2 bg-light rounded">
                    <small className="text-muted">
                      <strong>{t('dataHolderGroups.templates.terms')}</strong> {template.termsAndConditions.substring(0, 150)}
                      {template.termsAndConditions.length > 150 && '...'}
                    </small>
                  </div>
                )}
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center py-4 text-muted">
            <i className="fas fa-file-alt fa-3x mb-3"></i>
            <p>{t('dataHolderGroups.templates.empty')}</p>
          </div>
        )}
        {requestorGroups.length === 0 && templates.length > 0 && (
          <div className="alert alert-warning mt-3 mb-0">
            <i className="fas fa-exclamation-triangle me-2"></i>
            {t('dataHolderGroups.templates.noGroupsWarning')}
          </div>
        )}
      </Modal>

      {/* Subscribe Modal */}
      <Modal
        show={showSubscribeModal}
        onHide={handleCloseSubscribeModal}
        title={t('dataHolderGroups.subscribe.modalTitle', { name: selectedTemplate?.name || t('dataHolderGroups.subscribe.defaultTemplateName') })}
        size="xl"
        footer={
          <>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={handleCloseSubscribeModal}
              disabled={submitting}
            >
              {t('common.cancel')}
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleSubscriptionSubmit}
              disabled={submitting}
            >
              {submitting ? (
                <>
                  <span className="spinner-border spinner-border-sm me-2"></span>
                  {t('dataHolderGroups.subscribe.submitting')}
                </>
              ) : (
                <>
                  <i className="fas fa-paper-plane me-2"></i>
                  {subscriptionFormData.submitImmediately ? t('dataHolderGroups.subscribe.submitRequest') : t('dataHolderGroups.subscribe.saveAsDraft')}
                </>
              )}
            </button>
          </>
        }
      >
        {selectedTemplate && (
          <form onSubmit={handleSubscriptionSubmit}>
            {/* Template Info Banner */}
            <div className="alert alert-info mb-4">
              <div className="d-flex justify-content-between align-items-start">
                <div>
                  <h6 className="alert-heading mb-1">
                    <i className="fas fa-file-contract me-2"></i>
                    {selectedTemplate.name}
                  </h6>
                  <p className="mb-1 small">{selectedTemplate.description}</p>
                  {/* Request Types in banner */}
                  {selectedTemplate.requestTypes && selectedTemplate.requestTypes.length > 0 && (
                    <div className="d-flex flex-wrap gap-2 mt-2">
                      {selectedTemplate.requestTypes.map((rt, rtIndex) => (
                        <div
                          key={rtIndex}
                          className="d-inline-flex align-items-center gap-1"
                        >
                          {getRequestTypeBadge(rt)}
                          <small className="text-muted">
                            {t('dataHolderGroups.subscribe.levelLabel', { level: rt.accessLevel })}
                          </small>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
                <div className="text-end">
                  {(!selectedTemplate.requestTypes || selectedTemplate.requestTypes.length === 0) && (
                    getAccessLevelBadge(selectedTemplate.accessLevel)
                  )}
                  <div className="mt-1">
                    <small className="text-muted">
                      {t('dataHolderGroups.subscribe.fromLabel', { name: selectedDataHolder?.name })}
                    </small>
                  </div>
                </div>
              </div>
            </div>

            {/* One-per-dataholder notice */}
            <div className="alert alert-warning mb-4">
              <i className="fas fa-exclamation-triangle me-2"></i>
              <strong>{t('dataHolderGroups.subscribe.noteLabel')}</strong> {t('dataHolderGroups.subscribe.oneSubscriptionNotice')}
            </div>

            {/* Requestor Group Selection */}
            <div className="mb-4">
              <label htmlFor="requestorGroupId" className="form-label">
                {t('dataHolderGroups.subscribe.requestorGroup')} <span className="text-danger">*</span>
              </label>
              <select
                className="form-select"
                id="requestorGroupId"
                value={subscriptionFormData.requestorGroupId}
                onChange={(e) => handleRequestorGroupChange(e.target.value)}
                required
              >
                <option value="">{t('dataHolderGroups.subscribe.selectGroupOption')}</option>
                {requestorGroups.map((group) => (
                  <option key={group.id} value={group.id}>
                    {group.name}
                  </option>
                ))}
              </select>
              <small className="text-muted">
                {t('dataHolderGroups.subscribe.selectGroupHelp')}
              </small>
            </div>

            {/* Contact Information */}
            <div className="card mb-4">
              <div className="card-header">
                <h6 className="mb-0">
                  <i className="fas fa-user me-2"></i>
                  {t('dataHolderGroups.subscribe.contactInfo')}
                </h6>
              </div>
              <div className="card-body">
                <div className="row">
                  <div className="col-md-6 mb-3">
                    <label htmlFor="requestorFirstName" className="form-label">
                      {t('dataHolderGroups.subscribe.firstName')} <span className="text-danger">*</span>
                    </label>
                    <input
                      type="text"
                      className="form-control"
                      id="requestorFirstName"
                      value={subscriptionFormData.requestorFirstName}
                      onChange={(e) => setSubscriptionFormData({
                        ...subscriptionFormData,
                        requestorFirstName: e.target.value
                      })}
                      placeholder={t('dataHolderGroups.subscribe.firstNamePlaceholder')}
                      required
                    />
                  </div>
                  <div className="col-md-6 mb-3">
                    <label htmlFor="requestorLastName" className="form-label">
                      {t('dataHolderGroups.subscribe.lastName')} <span className="text-danger">*</span>
                    </label>
                    <input
                      type="text"
                      className="form-control"
                      id="requestorLastName"
                      value={subscriptionFormData.requestorLastName}
                      onChange={(e) => setSubscriptionFormData({
                        ...subscriptionFormData,
                        requestorLastName: e.target.value
                      })}
                      placeholder={t('dataHolderGroups.subscribe.lastNamePlaceholder')}
                      required
                    />
                  </div>
                  <div className="col-md-6 mb-3">
                    <label htmlFor="requestorOrganization" className="form-label">
                      {t('dataHolderGroups.subscribe.organization')}
                    </label>
                    <input
                      type="text"
                      className="form-control"
                      id="requestorOrganization"
                      value={subscriptionFormData.requestorOrganization}
                      onChange={(e) => setSubscriptionFormData({
                        ...subscriptionFormData,
                        requestorOrganization: e.target.value
                      })}
                      placeholder={t('dataHolderGroups.subscribe.organizationPlaceholder')}
                    />
                  </div>
                </div>
                <div className="row">
                  <div className="col-md-6 mb-3">
                    <label htmlFor="requestorEmail" className="form-label">
                      {t('dataHolderGroups.subscribe.email')} <span className="text-danger">*</span>
                    </label>
                    <input
                      type="email"
                      className="form-control"
                      id="requestorEmail"
                      value={subscriptionFormData.requestorEmail}
                      onChange={(e) => setSubscriptionFormData({
                        ...subscriptionFormData,
                        requestorEmail: e.target.value
                      })}
                      placeholder="john@example.com"
                      required
                    />
                  </div>
                  <div className="col-md-6 mb-3">
                    <label htmlFor="requestorPhone" className="form-label">
                      {t('dataHolderGroups.subscribe.phone')}
                    </label>
                    <input
                      type="tel"
                      className="form-control"
                      id="requestorPhone"
                      value={subscriptionFormData.requestorPhone}
                      onChange={(e) => setSubscriptionFormData({
                        ...subscriptionFormData,
                        requestorPhone: e.target.value
                      })}
                      placeholder={t('dataHolderGroups.subscribe.phonePlaceholder')}
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* Address Information */}
            <div className="card mb-4">
              <div className="card-header">
                <h6 className="mb-0">
                  <i className="fas fa-map-marker-alt me-2"></i>
                  {t('dataHolderGroups.subscribe.addressInfo')}
                </h6>
              </div>
              <div className="card-body">
                <div className="mb-3">
                  <label htmlFor="requestorAddress" className="form-label">
                    {t('dataHolderGroups.subscribe.streetAddress')}
                  </label>
                  <input
                    type="text"
                    className="form-control"
                    id="requestorAddress"
                    value={subscriptionFormData.requestorAddress}
                    onChange={(e) => setSubscriptionFormData({
                      ...subscriptionFormData,
                      requestorAddress: e.target.value
                    })}
                    placeholder={t('dataHolderGroups.subscribe.streetAddressPlaceholder')}
                  />
                </div>
                <div className="row">
                  <div className="col-md-4 mb-3">
                    <label htmlFor="requestorCity" className="form-label">
                      {t('dataHolderGroups.subscribe.city')}
                    </label>
                    <input
                      type="text"
                      className="form-control"
                      id="requestorCity"
                      value={subscriptionFormData.requestorCity}
                      onChange={(e) => setSubscriptionFormData({
                        ...subscriptionFormData,
                        requestorCity: e.target.value
                      })}
                      placeholder={t('dataHolderGroups.subscribe.cityPlaceholder')}
                    />
                  </div>
                  <div className="col-md-4 mb-3">
                    <label htmlFor="requestorStateProvince" className="form-label">
                      {t('dataHolderGroups.subscribe.stateProvince')}
                    </label>
                    <input
                      type="text"
                      className="form-control"
                      id="requestorStateProvince"
                      value={subscriptionFormData.requestorStateProvince}
                      onChange={(e) => setSubscriptionFormData({
                        ...subscriptionFormData,
                        requestorStateProvince: e.target.value
                      })}
                      placeholder={t('dataHolderGroups.subscribe.stateProvincePlaceholder')}
                    />
                  </div>
                  <div className="col-md-4 mb-3">
                    <label htmlFor="requestorPostalCode" className="form-label">
                      {t('dataHolderGroups.subscribe.postalCode')}
                    </label>
                    <input
                      type="text"
                      className="form-control"
                      id="requestorPostalCode"
                      value={subscriptionFormData.requestorPostalCode}
                      onChange={(e) => setSubscriptionFormData({
                        ...subscriptionFormData,
                        requestorPostalCode: e.target.value
                      })}
                      placeholder={t('dataHolderGroups.subscribe.postalCodePlaceholder')}
                    />
                  </div>
                </div>
                <div className="mb-3">
                  <label htmlFor="requestorCountry" className="form-label">
                    {t('dataHolderGroups.subscribe.country')}
                  </label>
                  <input
                    type="text"
                    className="form-control"
                    id="requestorCountry"
                    value={subscriptionFormData.requestorCountry}
                    onChange={(e) => setSubscriptionFormData({
                      ...subscriptionFormData,
                      requestorCountry: e.target.value
                    })}
                    placeholder={t('dataHolderGroups.subscribe.countryPlaceholder')}
                  />
                </div>
              </div>
            </div>

            {/* Request Details */}
            <div className="card mb-4">
              <div className="card-header">
                <h6 className="mb-0">
                  <i className="fas fa-clipboard-list me-2"></i>
                  {t('dataHolderGroups.subscribe.requestDetails')}
                </h6>
              </div>
              <div className="card-body">
                <div className="mb-3">
                  <label htmlFor="reasonForUse" className="form-label">
                    {t('dataHolderGroups.subscribe.reasonForUse')} <span className="text-danger">*</span>
                  </label>
                  <textarea
                    className="form-control"
                    id="reasonForUse"
                    rows="3"
                    value={subscriptionFormData.reasonForUse}
                    onChange={(e) => setSubscriptionFormData({
                      ...subscriptionFormData,
                      reasonForUse: e.target.value
                    })}
                    placeholder={t('dataHolderGroups.subscribe.reasonForUsePlaceholder')}
                    required
                  />
                  <small className="text-muted">
                    {t('dataHolderGroups.subscribe.reasonForUseHelp')}
                  </small>
                </div>
                <div className="row" style={{ display: 'none' }}>
                  <div className="col-md-6 mb-3">
                    <label htmlFor="requestedAccessLevel" className="form-label">
                      {t('dataHolderGroups.subscribe.requestedAccessLevel')}
                    </label>
                    <select
                      className="form-select"
                      id="requestedAccessLevel"
                      value={subscriptionFormData.requestedAccessLevel}
                      onChange={(e) => setSubscriptionFormData({
                        ...subscriptionFormData,
                        requestedAccessLevel: e.target.value
                      })}
                    >
                      <option value="0">{t('dataHolderGroups.subscribe.accessLevelOptions.level0')}</option>
                      <option value="1">{t('dataHolderGroups.subscribe.accessLevelOptions.level1')}</option>
                      <option value="2">{t('dataHolderGroups.subscribe.accessLevelOptions.level2')}</option>
                      <option value="3">{t('dataHolderGroups.subscribe.accessLevelOptions.level3')}</option>
                    </select>
                    <small className="text-muted">
                      {t('dataHolderGroups.subscribe.accessLevelHelp')}
                    </small>
                  </div>
                </div>
                <div className="mb-3">
                  <label htmlFor="additionalNotes" className="form-label">
                    {t('dataHolderGroups.subscribe.additionalNotes')}
                  </label>
                  <textarea
                    className="form-control"
                    id="additionalNotes"
                    rows="2"
                    value={subscriptionFormData.additionalNotes}
                    onChange={(e) => setSubscriptionFormData({
                      ...subscriptionFormData,
                      additionalNotes: e.target.value
                    })}
                    placeholder={t('dataHolderGroups.subscribe.additionalNotesPlaceholder')}
                  />
                </div>
                <div className="mb-3">
                  <label htmlFor="introspectionUrl" className="form-label">
                    {t('dataHolderGroups.subscribe.introspectionUrl')}
                  </label>
                  <input
                    type="url"
                    className="form-control"
                    id="introspectionUrl"
                    value={subscriptionFormData.introspectionUrl}
                    onChange={(e) => setSubscriptionFormData({
                      ...subscriptionFormData,
                      introspectionUrl: e.target.value
                    })}
                    placeholder="https://auth.example.com/realms/myrealm/protocol/openid-connect/token/introspect"
                  />
                  <div className="form-text">
                    {t('dataHolderGroups.subscribe.introspectionUrlHelp')}
                  </div>
                </div>
              </div>
            </div>

            {/* Submit Options */}
            <div className="form-check mb-3">
              <input
                type="checkbox"
                className="form-check-input"
                id="submitImmediately"
                checked={subscriptionFormData.submitImmediately}
                onChange={(e) => setSubscriptionFormData({
                  ...subscriptionFormData,
                  submitImmediately: e.target.checked
                })}
              />
              <label className="form-check-label" htmlFor="submitImmediately">
                {t('dataHolderGroups.subscribe.submitImmediately')}
              </label>
              <div className="form-text">
                {t('dataHolderGroups.subscribe.submitImmediatelyHelp')}
              </div>
            </div>
          </form>
        )}
      </Modal>
    </div>
  );
};

export default DataHolderGroups;