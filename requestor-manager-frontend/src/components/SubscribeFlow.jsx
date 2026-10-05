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
import Modal from './Modal';
import Pagination from './Pagination';
import CountrySelect from './CountrySelect';
import { buildReferenceIndex, sectionClauseLines } from '../constants/legalSections';
import { initialMemberFieldMapping, MemberFieldSelect } from '../constants/memberFields';
import DataHolderContact from './DataHolderContact';
import { useT } from '../i18n';

/**
 * Browse one data holder group's templates and subscribe to one.
 *
 * Lives apart from the pages that open it so the Data Holder Groups list and the
 * Subscriptions page drive the same flow rather than keeping two copies of a form
 * this long in step.
 */
const SubscribeFlow = ({ show, dataHolderGroup, onHide, onSubscribed }) => {
  const { user } = useAuth();
  const { success, error: showError } = useAlert();
  const { t } = useT();

  const selectedDataHolder = dataHolderGroup;

  const [templates, setTemplates] = useState([]);
  const [requestorGroups, setRequestorGroups] = useState([]);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [showTemplatesModal, setShowTemplatesModal] = useState(false);
  const [showSubscribeModal, setShowSubscribeModal] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState(null);
  const [showGroupModal, setShowGroupModal] = useState(false);
  const [selectedRequestorGroup, setSelectedRequestorGroup] = useState(null);
  const [groupPage, setGroupPage] = useState(1);
  const [groupPageSize, setGroupPageSize] = useState(10);
  const [groupSearch, setGroupSearch] = useState('');
  const [groupTotal, setGroupTotal] = useState(0);
  const [groupsLoading, setGroupsLoading] = useState(false);
  const [expandedTemplateId, setExpandedTemplateId] = useState(null);

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
    introspectionUrl: '',
    submitImmediately: true,
  });

  const [submitting, setSubmitting] = useState(false);
  const [groupMemberFields, setGroupMemberFields] = useState([]);

  /*
   * This deployment's token-introspection endpoint. Groups created before the field existed
   * carry no default of their own, so the subscribe form falls back to this rather than
   * leaving the requestor to paste the URL by hand.
   */
  const [introspectionDefault, setIntrospectionDefault] = useState('');

  useEffect(() => {
    requestorGroupsApi
      .getIntrospectionDefault()
      .then((response) => setIntrospectionDefault(response.data.data || ''))
      .catch((err) => console.error('Failed to load default introspection URL:', err));
  }, []);

  /*
   * One page at a time from the server, which also applies the role scoping: admins see
   * every group, everyone else only the ones their Keycloak membership covers.
   */
  const loadRequestorGroups = useCallback(async () => {
    setGroupsLoading(true);
    try {
      const response = await requestorGroupsApi.getAll({
        page: groupPage - 1,
        size: groupPageSize,
        search: groupSearch.trim() || undefined,
        sortBy: 'name',
        sortDir: 'asc',
      });
      const data = response.data.data || {};
      setRequestorGroups(data.content || []);
      setGroupTotal(data.totalElements || 0);
    } catch (err) {
      console.error('Failed to load requestor groups:', err);
      setRequestorGroups([]);
      setGroupTotal(0);
    } finally {
      setGroupsLoading(false);
    }
  }, [groupPage, groupPageSize, groupSearch]);

  useEffect(() => {
    if (!showGroupModal) return undefined;
    const timer = setTimeout(() => loadRequestorGroups(), 250);
    return () => clearTimeout(timer);
  }, [showGroupModal, loadRequestorGroups]);

  const loadTemplates = useCallback(async () => {
    if (!dataHolderGroup) return;
    setLoadingTemplates(true);
    setExpandedTemplateId(null);
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
  }, [dataHolderGroup]);

  useEffect(() => {
    if (!show) return;
    // Which group the subscription is for comes first; the templates follow.
    setShowGroupModal(true);
    setShowTemplatesModal(false);
    setShowSubscribeModal(false);
    setSelectedRequestorGroup(null);
    setGroupPage(1);
    loadTemplates();
  }, [show, loadTemplates]);

  const handleSelectRequestorGroup = (group) => {
    setSelectedRequestorGroup(group);
    setShowGroupModal(false);
    setShowTemplatesModal(true);
  };

  const backToRequestorGroups = () => {
    setShowTemplatesModal(false);
    setShowGroupModal(true);
  };


  const handleOpenSubscribeModal = (template) => {
    setSelectedTemplate(template);
    setShowTemplatesModal(false);

    const group = selectedRequestorGroup;

    setSubscriptionFormData({
      requestorGroupId: group ? group.id : '',
      requestorFirstName: user?.firstName || '',
      requestorLastName: user?.lastName || '',
      requestorOrganization: group?.defaultOrganization || '',
      requestorEmail: group?.defaultContactEmail || user?.email || '',
      requestorPhone: group?.defaultContactPhone || '',
      requestorAddress: group?.defaultAddress || '',
      requestorCity: group?.defaultCity || '',
      requestorStateProvince: group?.defaultStateProvince || '',
      requestorPostalCode: group?.defaultPostalCode || '',
      requestorCountry: group?.defaultCountry || '',
      introspectionUrl: group?.defaultIntrospectionUrl || introspectionDefault || '',
      submitImmediately: true,
      subscriptionFieldValues: Object.fromEntries(
        (template.subscriptionFields || []).map(f => [
          f.name,
          (f.dataType === 'checkbox' || f.dataType === 'url') ? false : (f.defaultValue ?? ''),
        ])
      ),
      acceptedLegalSectionIds: [],
      userFieldMapping: initialMemberFieldMapping(template.userFields, []),
    });

    setShowSubscribeModal(true);
  };

  useEffect(() => {
    const groupId = subscriptionFormData.requestorGroupId;
    if (!showSubscribeModal || !groupId || !(selectedTemplate?.userFields || []).length) {
      setGroupMemberFields([]);
      return;
    }
    let cancelled = false;
    requestorGroupsApi.getUserFields(groupId)
      .then((res) => {
        if (cancelled) return;
        const fields = res.data.data || [];
        setGroupMemberFields(fields);
        setSubscriptionFormData((prev) => {
          const defaults = initialMemberFieldMapping(selectedTemplate.userFields, fields);
          const merged = { ...defaults };
          Object.entries(prev.userFieldMapping || {}).forEach(([k, v]) => { if (v) merged[k] = v; });
          return { ...prev, userFieldMapping: merged };
        });
      })
      .catch((err) => console.error('Failed to load member fields:', err));
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showSubscribeModal, subscriptionFormData.requestorGroupId, selectedTemplate]);

  const closeTemplates = () => {
    setShowTemplatesModal(false);
    setShowGroupModal(false);
    onHide();
  };

  const handleCloseSubscribeModal = () => {
    setShowSubscribeModal(false);
    onHide();
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
      introspectionUrl: '',
      submitImmediately: true,
      subscriptionFieldValues: {},
      acceptedLegalSectionIds: [],
      userFieldMapping: {},
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
    if (!(subscriptionFormData.introspectionUrl || '').trim()) {
      showError(t('dataHolderGroups.subscribe.validation.introspectionUrlRequired'));
      return;
    }

    const values = subscriptionFormData.subscriptionFieldValues || {};
    const missingField = (selectedTemplate?.subscriptionFields || []).find(f => {
      if (!f.required) return false;
      const v = values[f.name];
      return (f.dataType === 'checkbox' || f.dataType === 'url')
        ? v !== true
        : (v === undefined || v === null || String(v).trim() === '');
    });
    if (missingField) {
      showError(t(missingField.dataType === 'url'
        ? 'dataHolderGroups.subscribe.validation.linkReviewRequired'
        : 'dataHolderGroups.subscribe.validation.fieldRequired', { field: missingField.name }));
      return;
    }

    const overlongField = (selectedTemplate?.subscriptionFields || []).find(f => {
      if (!f.maxLength || (f.dataType !== 'string' && f.dataType !== 'text')) return false;
      return String(values[f.name] ?? '').length > f.maxLength;
    });
    if (overlongField) {
      showError(t('dataHolderGroups.subscribe.validation.fieldTooLong', {
        field: overlongField.name, max: overlongField.maxLength,
      }));
      return;
    }

    const accepted = subscriptionFormData.acceptedLegalSectionIds || [];
    const unaccepted = (selectedTemplate?.legalSections || []).find(sec => !accepted.includes(sec.id));
    if (unaccepted) {
      showError(t('dataHolderGroups.subscribe.validation.termsRequired', { section: unaccepted.title }));
      return;
    }

    const unmapped = (selectedTemplate?.userFields || []).find(f => !subscriptionFormData.userFieldMapping?.[f.key]);
    if (unmapped) {
      showError(t('dataHolderGroups.subscribe.validation.memberFieldUnmapped', { field: unmapped.label }));
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
      };

      const response = await subscriptionsApi.create(payload);

      const responseData = response.data?.data;
      if (responseData?.status === 'DECLINED' && isDuplicateSubscriptionError(responseData?.statusMessage)) {
        const groupName = selectedRequestorGroup?.name || t('dataHolderGroups.subscribe.yourGroup');

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

      if (onSubscribed) onSubscribed();
      handleCloseSubscribeModal();
    } catch (err) {
      console.error('Failed to create subscription:', err);

      const errorMessage = err.response?.data?.message || err.response?.data?.error || '';

      if (isDuplicateSubscriptionError(errorMessage)) {
        const groupName = selectedRequestorGroup?.name || t('dataHolderGroups.subscribe.yourGroup');

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

  const legalSections = selectedTemplate?.legalSections || [];
  const legalRefIndex = buildReferenceIndex(legalSections);
  const templateFields = selectedTemplate?.subscriptionFields || [];
  const linkFields = templateFields.filter(f => f.dataType === 'url');
  const inputFields = templateFields.filter(f => f.dataType !== 'url');

  return (
    <>
        {/* Step one: which requestor group the subscription is for. */}
        <Modal
          show={showGroupModal}
          onHide={() => { setShowGroupModal(false); onHide(); }}
          title={t('dataHolderGroups.subscribe.pickRequestorGroupTitle', { name: selectedDataHolder?.name || '' })}
          size="lg"
          scrollable
          footer={
            <button type="button" className="btn btn-secondary"
                    onClick={() => { setShowGroupModal(false); onHide(); }}>
              {t('common.close')}
            </button>
          }
        >
          <p className="text-muted small">{t('dataHolderGroups.subscribe.pickRequestorGroupHelp')}</p>
          <input
            type="search"
            className="form-control form-control-sm mb-3"
            value={groupSearch}
            onChange={(e) => { setGroupSearch(e.target.value); setGroupPage(1); }}
            placeholder={t('dataHolderGroups.subscribe.searchGroupsPlaceholder')}
          />
          {groupsLoading ? (
            <div className="text-center py-4">
              <span className="spinner-border text-primary" role="status"></span>
            </div>
          ) : groupTotal === 0 ? (
            <div className="alert alert-warning mb-0">
              <i className="fas fa-exclamation-triangle me-2"></i>
              {groupSearch.trim()
                ? t('dataHolderGroups.subscribe.noGroupsMatch')
                : t('dataHolderGroups.templates.noGroupsWarning')}
            </div>
          ) : (
            <>
            <div className="list-group">
              {requestorGroups.map((group) => (
                <button
                  type="button"
                  key={group.id}
                  className="list-group-item list-group-item-action d-flex justify-content-between align-items-center gap-3 py-3"
                  onClick={() => handleSelectRequestorGroup(group)}
                >
                  {/* Always two lines, each truncated, so every row is the same height. */}
                  <span className="flex-grow-1" style={{ minWidth: 0 }}>
                    <span className="d-flex align-items-center gap-2">
                      <span className="fw-semibold text-truncate">{group.name}</span>
                      {group.code && (
                        <span className="badge bg-light text-dark border flex-shrink-0">{group.code}</span>
                      )}
                    </span>
                    <span className="d-block text-muted small text-truncate">
                      {group.description || t('common.noDescription')}
                    </span>
                  </span>
                  <i className="fas fa-chevron-right text-muted flex-shrink-0"></i>
                </button>
              ))}
            </div>
            {groupTotal > groupPageSize && (
              <Pagination
                page={groupPage}
                pageSize={groupPageSize}
                totalItems={groupTotal}
                onPageChange={setGroupPage}
                onPageSizeChange={(size) => { setGroupPageSize(size); setGroupPage(1); }}
                pageSizeOptions={[10, 25, 50]}
              />
            )}
            </>
          )}
        </Modal>

        {/* Templates Modal */}
        <Modal
          show={showTemplatesModal}
          onHide={closeTemplates}
          title={t('dataHolderGroups.templates.modalTitle', { name: selectedDataHolder?.name || t('dataHolderGroups.templates.defaultName') })}
          size="xl"
          footer={
            <>
            <button type="button" className="btn btn-outline-secondary me-auto" onClick={backToRequestorGroups}>
              <i className="fas fa-chevron-left me-1"></i>
              {t('dataHolderGroups.subscribe.backToRequestorGroups')}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={closeTemplates}
            >
              {t('common.close')}
            </button>
            </>
          }
        >
          {selectedRequestorGroup && (
            <div className="alert alert-secondary py-2 small d-flex align-items-center">
              <i className="fas fa-users me-2"></i>
              {t('dataHolderGroups.subscribe.subscribingAs', { name: selectedRequestorGroup.name })}
            </div>
          )}
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
                      {template.requestTypes && template.requestTypes.length > 0 && (
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
                        {template.maxQueriesPerMonth && (
                          <small className="text-muted">
                            <i className="fas fa-calendar-days me-1"></i>
                            {t('dataHolderGroups.templates.perMonth', { count: template.maxQueriesPerMonth })}
                          </small>
                        )}
                      </div>
                    </div>
                    <button
                      className="btn btn-primary btn-sm ms-3"
                      onClick={() => handleOpenSubscribeModal(template)}
                      disabled={!selectedRequestorGroup}
                      title={!selectedRequestorGroup ? t('dataHolderGroups.templates.subscribeDisabledTitle') : t('dataHolderGroups.templates.subscribeTitle')}
                    >
                      <i className="fas fa-file-signature me-1"></i>
                      {t('dataHolderGroups.templates.subscribe')}
                    </button>
                  </div>
                  {/* Everything the subscription commits this group to, readable before subscribing. */}
                  <button
                    type="button"
                    className="btn btn-link btn-sm px-0 mt-2"
                    onClick={() => setExpandedTemplateId(
                      expandedTemplateId === template.templateId ? null : template.templateId
                    )}
                  >
                    <i className={`fas fa-chevron-${expandedTemplateId === template.templateId ? 'up' : 'down'} me-1`}></i>
                    {expandedTemplateId === template.templateId
                      ? t('dataHolderGroups.templates.hideDetails')
                      : t('dataHolderGroups.templates.showDetails')}
                  </button>

                  {expandedTemplateId === template.templateId && (
                    <div className="mt-2 p-3 bg-light rounded">
                      {/* Who to ask about this agreement, before deciding to subscribe */}
                      <DataHolderContact contact={template.contact} compact className="mb-3" />

                      {/* Request types, in full */}
                      {template.requestTypes?.length > 0 && (
                        <div className="mb-3">
                          <div className="fw-bold small mb-2">{t('dataHolderGroups.templates.requestTypes')}</div>
                          {template.requestTypes.map((rt, rtIndex) => (
                            <div className="mb-2" key={rtIndex}>
                              <div className="d-flex flex-wrap align-items-center gap-2">
                                <span className="fw-semibold small">{rt.name}</span>
                                {getRequestTypeBadge(rt)}
                                {getAccessLevelBadge(rt.accessLevel)}
                                {rt.requiresManualApproval && (
                                  <span className="badge bg-warning text-dark" style={{ fontSize: '0.65rem' }}>
                                    <i className="fas fa-user-check me-1"></i>{t('dataHolderGroups.templates.manual')}
                                  </span>
                                )}
                              </div>
                              {rt.description && <div className="text-muted small">{rt.description}</div>}
                            </div>
                          ))}
                        </div>
                      )}

                      {/* Data holder group fields: the legal text and any links to review */}
                      <div className="mb-3">
                        <div className="fw-bold small mb-2">{t('dataHolderGroups.subscribe.dataHolderGroupFields')}</div>
                        {(template.legalSections || []).map((sec, sectionIndex) => (
                          <div className="mb-2" key={sec.id}>
                            <div className="fw-semibold small">{sectionIndex + 1}. {sec.title}</div>
                            {sectionClauseLines(sec, sectionIndex + 1, buildReferenceIndex(template.legalSections)).length > 0 && (
                              <div className="border rounded bg-white p-3 mt-1"
                                   style={{ whiteSpace: 'pre-wrap', fontSize: 13 }}>
                                {sectionClauseLines(sec, sectionIndex + 1, buildReferenceIndex(template.legalSections)).map((line) => (
                                  <div className="mb-2" key={line.key}>
                                    {line.label && <span className="fw-semibold me-1">{line.label}</span>}
                                    {line.text}
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>
                        ))}
                        {(template.subscriptionFields || []).filter(f => f.dataType === 'url').map((f) => (
                          <div className="mb-2" key={f.name}>
                            <div className="fw-semibold small">{f.name}</div>
                            {f.description && <div className="text-muted small">{f.description}</div>}
                            {f.defaultValue && (
                              <a href={f.defaultValue} target="_blank" rel="noopener noreferrer" className="small text-break">
                                <i className="fas fa-up-right-from-square me-1"></i>{f.defaultValue}
                              </a>
                            )}
                          </div>
                        ))}
                        {(template.legalSections || []).length === 0
                          && (template.subscriptionFields || []).every(f => f.dataType !== 'url') && (
                          <div className="text-muted small fst-italic">{t('dataHolderGroups.templates.noTerms')}</div>
                        )}
                      </div>

                      {/* Requestor group fields: what this group will have to supply */}
                      <div>
                        <div className="fw-bold small mb-2">{t('dataHolderGroups.subscribe.requestorGroupFields')}</div>
                        {(template.subscriptionFields || []).filter(f => f.dataType !== 'url').length === 0 ? (
                          <div className="text-muted small fst-italic">{t('dataHolderGroups.templates.noFields')}</div>
                        ) : (
                          <ul className="small mb-0 ps-3">
                            {(template.subscriptionFields || []).filter(f => f.dataType !== 'url').map((f) => (
                              <li key={f.name}>
                                {f.name}
                                {f.required && <span className="text-danger ms-1">*</span>}
                                <span className="text-muted ms-2">
                                  {t(`dataHolderGroups.templates.fieldTypes.${f.dataType || 'string'}`)}
                                  {f.maxLength && (f.dataType === 'string' || f.dataType === 'text')
                                    ? t('dataHolderGroups.templates.fieldLimit', { max: f.maxLength })
                                    : ''}
                                </span>
                                {f.description && <div className="text-muted">{f.description}</div>}
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
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
          {!selectedRequestorGroup && templates.length > 0 && (
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
                    <div className="mt-1">
                      <small className="text-muted">
                        {t('dataHolderGroups.subscribe.fromLabel', { name: selectedDataHolder?.name })}
                      </small>
                    </div>
                  </div>
                </div>
              </div>

              {/* Who to contact at the data holder group about this agreement */}
              <DataHolderContact contact={selectedTemplate?.contact} className="mb-4" />

              {/* One-per-dataholder notice */}
              <div className="alert alert-warning mb-4">
                <i className="fas fa-exclamation-triangle me-2"></i>
                <strong>{t('dataHolderGroups.subscribe.noteLabel')}</strong> {t('dataHolderGroups.subscribe.oneSubscriptionNotice')}
              </div>

              {/* Requestor group, as chosen at the start of the flow. */}
              <div className="mb-4">
                <label className="form-label">
                  {t('dataHolderGroups.subscribe.requestorGroup')}
                </label>
                <div className="form-control-plaintext border rounded px-3 py-2 bg-light">
                  <i className="fas fa-users me-2 text-muted"></i>
                  <span className="fw-semibold">{selectedRequestorGroup?.name}</span>
                  {selectedRequestorGroup?.code && (
                    <span className="badge bg-light text-dark border ms-2">{selectedRequestorGroup.code}</span>
                  )}
                </div>
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
                    <CountrySelect
                      id="requestorCountry"
                      value={subscriptionFormData.requestorCountry}
                      onChange={(country) => setSubscriptionFormData({
                        ...subscriptionFormData,
                        requestorCountry: country,
                      })}
                      disabled={submitting}
                    />
                  </div>
                </div>
              </div>

              {/* Data Holder Group Fields, the group's terms and the links it wants reviewed */}
              {(legalSections.length > 0 || linkFields.length > 0) && (
                <div className="card mb-4">
                  <div className="card-header">
                    <h6 className="mb-0">
                      <i className="fas fa-file-shield me-2"></i>
                      {t('dataHolderGroups.subscribe.dataHolderGroupFields')}
                    </h6>
                  </div>
                  <div className="card-body">
                    <small className="text-muted d-block mb-3">
                      {t('dataHolderGroups.subscribe.dataHolderGroupFieldsHelp')}
                    </small>

                    {legalSections.map((sec, sectionIndex) => {
                      const accepted = (subscriptionFormData.acceptedLegalSectionIds || []).includes(sec.id);
                      const toggle = () => {
                        const cur = subscriptionFormData.acceptedLegalSectionIds || [];
                        setSubscriptionFormData({
                          ...subscriptionFormData,
                          acceptedLegalSectionIds: accepted ? cur.filter(id => id !== sec.id) : [...cur, sec.id],
                        });
                      };
                      return (
                        <div className="card mb-2" key={sec.id}>
                          <div className="card-body py-2">
                            <div className="fw-semibold mb-1">{sectionIndex + 1}. {sec.title}</div>
                            {sectionClauseLines(sec, sectionIndex + 1, legalRefIndex).length > 0 && (
                              <div className="bg-white px-1 pb-2"
                                   style={{ whiteSpace: 'pre-wrap', fontSize: 13 }}>
                                {sectionClauseLines(sec, sectionIndex + 1, legalRefIndex).map((line) => (
                                  <div className="mb-2" key={line.key}>
                                    {line.label && <span className="fw-semibold me-1">{line.label}</span>}
                                    {line.text}
                                  </div>
                                ))}
                              </div>
                            )}
                            <div className="form-check">
                              <input className="form-check-input" type="checkbox" id={`ls-${sec.id}`}
                                     checked={accepted} onChange={toggle} />
                              <label className="form-check-label" htmlFor={`ls-${sec.id}`}>
                                {t('dataHolderGroups.subscribe.acceptSection')} <span className="text-danger">*</span>
                              </label>
                            </div>
                          </div>
                        </div>
                      );
                    })}

                    {linkFields.map((f) => {
                      const acknowledged = subscriptionFormData.subscriptionFieldValues?.[f.name] === true;
                      const toggle = (v) => setSubscriptionFormData({
                        ...subscriptionFormData,
                        subscriptionFieldValues: { ...subscriptionFormData.subscriptionFieldValues, [f.name]: v },
                      });
                      return (
                        <div className="card mb-2" key={f.name}>
                          <div className="card-body py-2">
                            <div className="fw-semibold mb-1">{f.name}</div>
                            {f.description && <div className="text-muted small mb-2">{f.description}</div>}
                            {f.defaultValue && (
                              <a href={f.defaultValue} target="_blank" rel="noopener noreferrer"
                                 className="d-inline-block mb-2 text-break">
                                <i className="fas fa-up-right-from-square me-1"></i>{f.defaultValue}
                              </a>
                            )}
                            {f.required && (
                              <div className="form-check">
                                <input className="form-check-input" type="checkbox" id={`sf-${f.name}`}
                                       checked={acknowledged} onChange={(e) => toggle(e.target.checked)} />
                                <label className="form-check-label" htmlFor={`sf-${f.name}`}>
                                  {t('dataHolderGroups.subscribe.acknowledgeLink')} <span className="text-danger">*</span>
                                </label>
                              </div>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {(selectedTemplate?.userFields || []).length > 0 && (
                <div className="card mb-4">
                  <div className="card-header">
                    <h6 className="mb-0">
                      <i className="fas fa-id-card me-2"></i>
                      {t('dataHolderGroups.subscribe.memberInformation')}
                    </h6>
                  </div>
                  <div className="card-body">
                    <p className="small text-muted">{t('dataHolderGroups.subscribe.memberInformationHelp')}</p>
                    {selectedTemplate.userFields.map((f) => (
                      <div className="row g-2 align-items-center mb-2" key={f.key}>
                        <div className="col-md-5">
                          <label htmlFor={`uf-${f.key}`} className="form-label mb-0">
                            {f.label} <span className="text-danger">*</span>
                          </label>
                          {f.description && <div className="small text-muted">{f.description}</div>}
                        </div>
                        <div className="col-md-7">
                          <MemberFieldSelect
                            id={`uf-${f.key}`}
                            value={subscriptionFormData.userFieldMapping?.[f.key]}
                            groupFields={groupMemberFields}
                            placeholder={t('dataHolderGroups.subscribe.memberFieldPlaceholder')}
                            standardLabel={t('dataHolderGroups.subscribe.standardMemberFields')}
                            customLabel={t('dataHolderGroups.subscribe.customMemberFields')}
                            onChange={(v) => setSubscriptionFormData({
                              ...subscriptionFormData,
                              userFieldMapping: { ...subscriptionFormData.userFieldMapping, [f.key]: v },
                            })}
                          />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Requestor Group Fields, what this group has to supply */}
              <div className="card mb-4">
                <div className="card-header">
                  <h6 className="mb-0">
                    <i className="fas fa-clipboard-list me-2"></i>
                    {t('dataHolderGroups.subscribe.requestorGroupFields')}
                  </h6>
                </div>
                <div className="card-body">
                  {inputFields.map((f) => {
                    const val = subscriptionFormData.subscriptionFieldValues?.[f.name];
                    const setVal = (v) => setSubscriptionFormData({
                      ...subscriptionFormData,
                      subscriptionFieldValues: { ...subscriptionFormData.subscriptionFieldValues, [f.name]: v },
                    });
                    const limit = (f.dataType === 'string' || f.dataType === 'text') ? (f.maxLength || undefined) : undefined;
                    const common = { className: 'form-control', id: `sf-${f.name}`, placeholder: f.placeholder || '' };
                    return (
                      <div className="mb-3" key={f.name}>
                        {f.dataType === 'checkbox' ? (
                          <div className="form-check">
                            <input className="form-check-input" type="checkbox" id={`sf-${f.name}`}
                                   checked={val === true} onChange={(e) => setVal(e.target.checked)} />
                            <label className="form-check-label" htmlFor={`sf-${f.name}`}>
                              {f.name} {f.required && <span className="text-danger">*</span>}
                            </label>
                          </div>
                        ) : (
                          <>
                            <label htmlFor={`sf-${f.name}`} className="form-label">
                              {f.name} {f.required && <span className="text-danger">*</span>}
                            </label>
                            {f.dataType === 'select' ? (
                              <select className="form-select" id={`sf-${f.name}`} value={val ?? ''}
                                      onChange={(e) => setVal(e.target.value)}>
                                <option value="">{t('dataHolderGroups.subscribe.selectPlaceholder')}</option>
                                {(f.enumValues || '').split(',').map(o => o.trim()).filter(Boolean).map(o => (
                                  <option key={o} value={o}>{o}</option>
                                ))}
                              </select>
                            ) : f.dataType === 'text' ? (
                              <textarea {...common} rows={3} value={val ?? ''}
                                        maxLength={limit}
                                        onChange={(e) => setVal(e.target.value)} />
                            ) : (
                              <input {...common}
                                     type={f.dataType === 'number' ? 'number' : f.dataType === 'date' ? 'date' : 'text'}
                                     value={val ?? ''}
                                     maxLength={limit}
                                     min={f.minValue || undefined} max={f.maxValue || undefined}
                                     onChange={(e) => setVal(e.target.value)} />
                            )}
                          </>
                        )}
                        <div className="d-flex justify-content-between gap-2">
                          {f.description ? <small className="text-muted">{f.description}</small> : <span />}
                          {limit && (
                            <small className="text-muted flex-shrink-0">
                              {t('dataHolderGroups.subscribe.charactersUsed', {
                                used: String(val ?? '').length, max: limit,
                              })}
                            </small>
                          )}
                        </div>
                      </div>
                    );
                  })}

                  <div className="mb-3">
                    <label htmlFor="introspectionUrl" className="form-label">
                      {t('dataHolderGroups.subscribe.introspectionUrl')} <span className="text-danger">*</span>
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
    </>
  );
};

export default SubscribeFlow;
