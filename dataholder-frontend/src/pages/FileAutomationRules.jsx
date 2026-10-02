/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import {
  getFileAutomationRules,
  createFileAutomationRule,
  updateFileAutomationRule,
  deleteFileAutomationRule,
  toggleFileAutomationRule,
} from '../services/api';
import Loading from '../components/Loading';
import Modal from '../components/Modal';
import Pagination, { DEFAULT_PAGE_SIZE } from '../components/Pagination';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const FILE_TYPES = ['PDF', 'DOCX', 'XLSX', 'TXT', 'JPEG', 'PNG'];
const QUERY_TYPES = ['domain', 'ip', 'asn'];
const ACTIONS = [
  { value: 'AUTO_APPROVE', icon: <i className="fa-solid fa-check" />, color: 'var(--accent-success)' },
  { value: 'AUTO_DENY', icon: <i className="fa-solid fa-ban" />, color: 'var(--accent-danger)' },
  { value: 'FLAG_FOR_REVIEW', icon: <i className="fa-solid fa-eye" />, color: 'var(--accent-warning)' },
  { value: 'REQUIRE_SCAN', icon: <i className="fa-solid fa-shield-halved" />, color: 'var(--accent-primary)' },
  { value: 'QUARANTINE', icon: <i className="fa-solid fa-gavel" />, color: '#8b5cf6' },
];

const emptyRule = {
  name: '',
  description: '',
  enabled: true,
  priority: 100,
  fileTypes: '',
  maxFileSizeBytes: null,
  minFileSizeBytes: null,
  filenamePattern: '',
  queryTypes: '',
  requestorGroups: '',
  action: 'FLAG_FOR_REVIEW',
  actionMessage: '',
};

const FileAutomationRules = () => {
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editingRule, setEditingRule] = useState(null);
  const [form, setForm] = useState({ ...emptyRule });
  const [submitting, setSubmitting] = useState(false);
  const [selectedFileTypes, setSelectedFileTypes] = useState([]);
  const [selectedQueryTypes, setSelectedQueryTypes] = useState([]);

  // Server-side pagination + search state
  const [page, setPage] = useState(1); // 1-based
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [totalItems, setTotalItems] = useState(0);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const { t } = useT();

  const ACTION_LABELS = {
    AUTO_APPROVE: t('fileAutomation.actions.autoApprove.label'),
    AUTO_DENY: t('fileAutomation.actions.autoDeny.label'),
    FLAG_FOR_REVIEW: t('fileAutomation.actions.flagForReview.label'),
    REQUIRE_SCAN: t('fileAutomation.actions.requireScan.label'),
    QUARANTINE: t('fileAutomation.actions.quarantine.label'),
  };
  const ACTION_DESCRIPTIONS = {
    AUTO_APPROVE: t('fileAutomation.actions.autoApprove.desc'),
    AUTO_DENY: t('fileAutomation.actions.autoDeny.desc'),
    FLAG_FOR_REVIEW: t('fileAutomation.actions.flagForReview.desc'),
    REQUIRE_SCAN: t('fileAutomation.actions.requireScan.desc'),
    QUARANTINE: t('fileAutomation.actions.quarantine.desc'),
  };

  // Debounce the search box (300ms)
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  // Reset to first page whenever the search term changes
  useEffect(() => { setPage(1); }, [debouncedSearch]);

  // Fetch a page whenever paging or search changes
  useEffect(() => {
    loadRules();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, pageSize, debouncedSearch]);

  const loadRules = async () => {
    setLoading(true);
    try {
      const response = await getFileAutomationRules({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        sortBy: 'priority',
        sortDir: 'asc',
      });
      setRules(response.data?.content || []);
      setTotalItems(response.data?.totalElements || 0);
    } catch (error) {
      console.error('Failed to load automation rules:', error);
      toast.error(t('fileAutomation.toasts.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  const openCreateModal = () => {
    setEditingRule(null);
    setForm({ ...emptyRule });
    setSelectedFileTypes([]);
    setSelectedQueryTypes([]);
    setShowModal(true);
  };

  const openEditModal = (rule) => {
    setEditingRule(rule);
    setForm({
      name: rule.name || '',
      description: rule.description || '',
      enabled: rule.enabled !== false,
      priority: rule.priority || 100,
      fileTypes: rule.fileTypes || '',
      maxFileSizeBytes: rule.maxFileSizeBytes || null,
      minFileSizeBytes: rule.minFileSizeBytes || null,
      filenamePattern: rule.filenamePattern || '',
      queryTypes: rule.queryTypes || '',
      requestorGroups: rule.requestorGroups || '',
      action: rule.action || 'FLAG_FOR_REVIEW',
      actionMessage: rule.actionMessage || '',
    });
    setSelectedFileTypes(rule.fileTypes ? rule.fileTypes.split(',').map(t => t.trim()) : []);
    setSelectedQueryTypes(rule.queryTypes ? rule.queryTypes.split(',').map(t => t.trim()) : []);
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!form.name.trim()) {
      toast.error(t('fileAutomation.toasts.nameRequired'));
      return;
    }
    setSubmitting(true);
    try {
      const payload = {
        ...form,
        fileTypes: selectedFileTypes.length > 0 ? selectedFileTypes.join(',') : null,
        queryTypes: selectedQueryTypes.length > 0 ? selectedQueryTypes.join(',') : null,
        maxFileSizeBytes: form.maxFileSizeBytes ? parseInt(form.maxFileSizeBytes) : null,
        minFileSizeBytes: form.minFileSizeBytes ? parseInt(form.minFileSizeBytes) : null,
      };
      if (editingRule) {
        await updateFileAutomationRule(editingRule.id, payload);
        toast.success(t('fileAutomation.toasts.updated'));
      } else {
        await createFileAutomationRule(payload);
        toast.success(t('fileAutomation.toasts.created'));
      }
      setShowModal(false);
      loadRules();
    } catch (error) {
      console.error('Failed to save rule:', error);
      toast.error(t('fileAutomation.toasts.saveFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (rule) => {
    if (!window.confirm(t('fileAutomation.confirmDelete', { name: rule.name }))) return;
    try {
      await deleteFileAutomationRule(rule.id);
      toast.success(t('fileAutomation.toasts.deleted'));
      loadRules();
    } catch (error) {
      toast.error(t('fileAutomation.toasts.deleteFailed'));
    }
  };

  const handleToggle = async (rule) => {
    try {
      await toggleFileAutomationRule(rule.id);
      toast.success(rule.enabled ? t('fileAutomation.toasts.disabled') : t('fileAutomation.toasts.enabled'));
      loadRules();
    } catch (error) {
      toast.error(t('fileAutomation.toasts.toggleFailed'));
    }
  };

  const toggleFileType = (type) => {
    setSelectedFileTypes(prev =>
      prev.includes(type) ? prev.filter(t => t !== type) : [...prev, type]
    );
  };

  const toggleQueryType = (type) => {
    setSelectedQueryTypes(prev =>
      prev.includes(type) ? prev.filter(t => t !== type) : [...prev, type]
    );
  };

  const getActionInfo = (action) => {
    const found = ACTIONS.find(a => a.value === action) || ACTIONS[2];
    return { ...found, label: ACTION_LABELS[found.value], desc: ACTION_DESCRIPTIONS[found.value] };
  };

  const getFileTypeIcon = (type) => {
    switch (type) {
      case 'PDF': return <i className="fa-solid fa-file-pdf" style={{ color: '#ef4444' }} />;
      case 'DOCX': return <i className="fa-solid fa-file-word" style={{ color: '#2563eb' }} />;
      case 'XLSX': return <i className="fa-solid fa-file-excel" style={{ color: '#16a34a' }} />;
      case 'JPEG': case 'PNG': return <i className="fa-solid fa-file-image" style={{ color: '#8b5cf6' }} />;
      case 'TXT': return <i className="fa-solid fa-file-lines" style={{ color: '#6b7280' }} />;
      default: return null;
    }
  };

  const formatBytes = (bytes) => {
    if (!bytes) return t('common.na');
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  if (loading && rules.length === 0) {
    return <Loading message={t('fileAutomation.loadingRules')} />;
  }

  return (
    <div>
      <div className="mb-4">
        <h2 className="h3 fw-bold mb-1">{t('fileAutomation.pageTitle')}</h2>
        <p className="text-muted mb-0">{t('fileAutomation.pageSubtitle')}</p>
      </div>

      {/* Info Banner */}
      <div className="card" style={{ marginBottom: '16px', background: 'linear-gradient(135deg, rgba(99,102,241,0.08), rgba(139,92,246,0.08))' }}>
        <div style={{ padding: '16px 20px', display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
          <i className="fa-solid fa-robot" style={{ fontSize: '20px', color: 'var(--accent-primary)', flexShrink: 0, marginTop: '2px' }} />
          <div style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: '1.6' }}>
            <strong style={{ color: 'var(--text-primary)' }}>{t('fileAutomation.infoBanner.title')}</strong> {t('fileAutomation.infoBanner.body')} <strong>{t('fileAutomation.infoBanner.supportedTypes')}</strong>.
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header d-flex justify-content-between align-items-center">
          <h3>
            <i className="fa-solid fa-robot" />
            {t('fileAutomation.rulesHeading', { count: totalItems })}
          </h3>
          <div className="d-flex gap-2 align-items-center">
            <input
              type="text"
              className="form-control form-control-sm"
              style={{ width: '200px' }}
              placeholder={t('fileAutomation.searchPlaceholder')}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <button className="btn btn-secondary btn-sm" onClick={loadRules}>
              <i className="fa-solid fa-arrows-rotate" /> {t('common.refresh')}
            </button>
            <button className="btn btn-primary btn-sm" onClick={openCreateModal}>
              <i className="fa-solid fa-plus" /> {t('fileAutomation.newRule')}
            </button>
          </div>
        </div>

        {rules.length > 0 ? (
          <div className="table-responsive">
            <table className="table table-hover align-middle">
              <thead>
                <tr>
                  <th style={{ width: '50px' }}>{t('fileAutomation.table.priority')}</th>
                  <th>{t('fileAutomation.table.ruleName')}</th>
                  <th>{t('fileAutomation.table.fileTypes')}</th>
                  <th>{t('fileAutomation.table.conditions')}</th>
                  <th>{t('fileAutomation.table.action')}</th>
                  <th>{t('fileAutomation.table.stats')}</th>
                  <th style={{ width: '60px' }}>{t('common.active')}</th>
                  <th style={{ width: '120px' }}>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {rules.map((rule) => {
                  const actionInfo = getActionInfo(rule.action);
                  return (
                    <tr key={rule.id} style={{ opacity: rule.enabled ? 1 : 0.5 }}>
                      <td>
                        <span style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          width: '32px',
                          height: '32px',
                          borderRadius: '8px',
                          background: 'var(--bg-tertiary)',
                          fontWeight: 700,
                          fontSize: '13px',
                          color: 'var(--text-primary)'
                        }}>
                          {rule.priority}
                        </span>
                      </td>
                      <td>
                        <div style={{ fontWeight: 500 }}>{rule.name}</div>
                        {rule.description && (
                          <div className="text-muted small" style={{ maxWidth: '250px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {rule.description}
                          </div>
                        )}
                      </td>
                      <td>
                        {rule.fileTypes ? (
                          <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap' }}>
                            {rule.fileTypes.split(',').map(t => t.trim()).map(type => (
                              <span key={type} style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '3px',
                                fontSize: '11px',
                                padding: '2px 6px',
                                borderRadius: '4px',
                                background: 'var(--bg-tertiary)',
                                fontWeight: 600
                              }}>
                                {getFileTypeIcon(type)} {type}
                              </span>
                            ))}
                          </div>
                        ) : (
                          <span className="text-muted small">{t('fileAutomation.allTypes')}</span>
                        )}
                      </td>
                      <td>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', fontSize: '11px', color: 'var(--text-tertiary)' }}>
                          {rule.maxFileSizeBytes && <span>{t('fileAutomation.maxLabel', { size: formatBytes(rule.maxFileSizeBytes) })}</span>}
                          {rule.filenamePattern && <span>{t('fileAutomation.patternLabel', { pattern: rule.filenamePattern })}</span>}
                          {rule.queryTypes && <span>{t('fileAutomation.queriesLabel', { types: rule.queryTypes })}</span>}
                          {!rule.maxFileSizeBytes && !rule.filenamePattern && !rule.queryTypes && (
                            <span className="text-muted">{t('fileAutomation.noConditions')}</span>
                          )}
                        </div>
                      </td>
                      <td>
                        <span style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '5px',
                          padding: '3px 10px',
                          borderRadius: '6px',
                          fontSize: '12px',
                          fontWeight: 600,
                          color: actionInfo.color,
                          background: `${actionInfo.color}15`,
                          border: `1px solid ${actionInfo.color}30`
                        }}>
                          {actionInfo.icon} {actionInfo.label}
                        </span>
                      </td>
                      <td>
                        <div style={{ fontSize: '12px' }}>
                          <span style={{ fontWeight: 600 }}>{rule.triggerCount || 0}</span>
                          <span className="text-muted"> {t('fileAutomation.triggers')}</span>
                        </div>
                      </td>
                      <td style={{ textAlign: 'center' }}>
                        <button
                          className="btn btn-outline-secondary btn-sm"
                          onClick={() => handleToggle(rule)}
                          title={rule.enabled ? t('fileAutomation.disableToggle') : t('fileAutomation.enableToggle')}
                          style={{ color: rule.enabled ? 'var(--accent-success)' : 'var(--text-muted)' }}
                        >
                          {rule.enabled ? <i className="fa-solid fa-toggle-on" /> : <i className="fa-solid fa-toggle-off" />}
                        </button>
                      </td>
                      <td>
                        <div className="d-flex gap-2">
                          <button className="btn btn-outline-secondary btn-sm" onClick={() => openEditModal(rule)} title={t('common.edit')}>
                            <i className="fa-solid fa-pen" />
                          </button>
                          <button
                            className="btn btn-outline-secondary btn-sm"
                            onClick={() => handleDelete(rule)}
                            title={t('common.delete')}
                            style={{ color: 'var(--accent-danger)' }}
                          >
                            <i className="fa-solid fa-trash" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            <div className="px-3 pb-3">
              <Pagination
                page={page}
                pageSize={pageSize}
                totalItems={totalItems}
                onPageChange={setPage}
                onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
                itemLabel={t('fileAutomation.itemLabel')}
              />
            </div>
          </div>
        ) : (
          <div className="table-empty">
            <i className="fa-solid fa-robot" style={{ fontSize: '32px', color: 'var(--text-muted)' }} />
            <p>{t('fileAutomation.emptyState')}</p>
            <button className="btn btn-primary btn-sm" onClick={openCreateModal} style={{ marginTop: '8px' }}>
              <i className="fa-solid fa-plus" /> {t('fileAutomation.createFirstRule')}
            </button>
          </div>
        )}
      </div>

      {/* Create/Edit Modal */}
      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editingRule ? t('fileAutomation.modal.editTitle') : t('fileAutomation.modal.createTitle')}
        size="large"
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setShowModal(false)}>{t('common.cancel')}</button>
            <button className="btn btn-primary" onClick={handleSave} disabled={submitting}>
              {submitting ? t('common.saving') : editingRule ? t('common.saveChanges') : t('fileAutomation.modal.createRule')}
            </button>
          </>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Basic Info */}
          <div>
            <h4 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '12px', color: 'var(--text-primary)' }}>{t('fileAutomation.modal.basicInfo')}</h4>
            <div className="mb-3">
              <label className="form-label">{t('fileAutomation.modal.ruleNameLabel')}</label>
              <input
                type="text"
                className="form-control"
                value={form.name}
                onChange={e => setForm({ ...form, name: e.target.value })}
                placeholder={t('fileAutomation.modal.ruleNamePlaceholder')}
              />
            </div>
            <div className="mb-3">
              <label className="form-label">{t('common.description')}</label>
              <textarea
                className="form-control"
                value={form.description}
                onChange={e => setForm({ ...form, description: e.target.value })}
                placeholder={t('fileAutomation.modal.descriptionPlaceholder')}
                rows={2}
              />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div className="mb-3">
                <label className="form-label">{t('fileAutomation.modal.priorityLabel')}</label>
                <input
                  type="number"
                  className="form-control"
                  value={form.priority}
                  onChange={e => setForm({ ...form, priority: parseInt(e.target.value) || 100 })}
                  min="1"
                  max="9999"
                />
              </div>
              <div className="mb-3">
                <label className="form-label">{t('common.status')}</label>
                <select
                  className="form-select"
                  value={form.enabled ? 'true' : 'false'}
                  onChange={e => setForm({ ...form, enabled: e.target.value === 'true' })}
                >
                  <option value="true">{t('common.enabled')}</option>
                  <option value="false">{t('common.disabled')}</option>
                </select>
              </div>
            </div>
          </div>

          {/* Conditions */}
          <div>
            <h4 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '12px', color: 'var(--text-primary)' }}>
              {t('fileAutomation.modal.conditionsHeading')}
              <span style={{ fontWeight: 400, color: 'var(--text-tertiary)', fontSize: '12px', marginLeft: '8px' }}>
                {t('fileAutomation.modal.conditionsHint')}
              </span>
            </h4>

            {/* File Types */}
            <div className="mb-3">
              <label className="form-label">{t('fileAutomation.table.fileTypes')}</label>
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                {FILE_TYPES.map(type => (
                  <button
                    key={type}
                    type="button"
                    onClick={() => toggleFileType(type)}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '6px',
                      padding: '6px 12px',
                      borderRadius: '8px',
                      fontSize: '12px',
                      fontWeight: 600,
                      cursor: 'pointer',
                      border: `1px solid ${selectedFileTypes.includes(type) ? 'var(--accent-primary)' : 'var(--border-primary)'}`,
                      background: selectedFileTypes.includes(type) ? 'rgba(99,102,241,0.1)' : 'transparent',
                      color: selectedFileTypes.includes(type) ? 'var(--accent-primary)' : 'var(--text-secondary)',
                      transition: 'all 0.15s ease'
                    }}
                  >
                    {getFileTypeIcon(type)} {type}
                  </button>
                ))}
              </div>
            </div>

            {/* Size Limits */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div className="mb-3">
                <label className="form-label">{t('fileAutomation.modal.maxFileSizeLabel')}</label>
                <input
                  type="number"
                  className="form-control"
                  value={form.maxFileSizeBytes ? (form.maxFileSizeBytes / (1024 * 1024)).toFixed(1) : ''}
                  onChange={e => setForm({
                    ...form,
                    maxFileSizeBytes: e.target.value ? Math.round(parseFloat(e.target.value) * 1024 * 1024) : null
                  })}
                  placeholder={t('fileAutomation.modal.maxFileSizePlaceholder')}
                  step="0.1"
                  min="0"
                />
              </div>
              <div className="mb-3">
                <label className="form-label">{t('fileAutomation.modal.filenamePatternLabel')}</label>
                <input
                  type="text"
                  className="form-control"
                  value={form.filenamePattern}
                  onChange={e => setForm({ ...form, filenamePattern: e.target.value })}
                  placeholder={t('fileAutomation.modal.filenamePatternPlaceholder')}
                />
              </div>
            </div>

            {/* Query Types */}
            <div className="mb-3">
              <label className="form-label">{t('fileAutomation.modal.queryTypesLabel')}</label>
              <div style={{ display: 'flex', gap: '8px' }}>
                {QUERY_TYPES.map(type => (
                  <button
                    key={type}
                    type="button"
                    onClick={() => toggleQueryType(type)}
                    style={{
                      padding: '6px 14px',
                      borderRadius: '8px',
                      fontSize: '12px',
                      fontWeight: 600,
                      cursor: 'pointer',
                      textTransform: 'uppercase',
                      border: `1px solid ${selectedQueryTypes.includes(type) ? 'var(--accent-primary)' : 'var(--border-primary)'}`,
                      background: selectedQueryTypes.includes(type) ? 'rgba(99,102,241,0.1)' : 'transparent',
                      color: selectedQueryTypes.includes(type) ? 'var(--accent-primary)' : 'var(--text-secondary)',
                      transition: 'all 0.15s ease'
                    }}
                  >
                    {type}
                  </button>
                ))}
              </div>
            </div>

            <div className="mb-3">
              <label className="form-label">{t('fileAutomation.modal.requestorGroupsLabel')}</label>
              <input
                type="text"
                className="form-control"
                value={form.requestorGroups}
                onChange={e => setForm({ ...form, requestorGroups: e.target.value })}
                placeholder={t('fileAutomation.modal.requestorGroupsPlaceholder')}
              />
            </div>
          </div>

          {/* Action */}
          <div>
            <h4 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '12px', color: 'var(--text-primary)' }}>{t('fileAutomation.modal.actionHeading')}</h4>
            <div className="mb-3">
              <label className="form-label">{t('fileAutomation.modal.actionWhenLabel')}</label>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                {ACTIONS.map(action => (
                  <label
                    key={action.value}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '10px',
                      padding: '10px 14px',
                      borderRadius: '8px',
                      cursor: 'pointer',
                      border: `1px solid ${form.action === action.value ? action.color + '50' : 'var(--border-primary)'}`,
                      background: form.action === action.value ? action.color + '08' : 'transparent',
                      transition: 'all 0.15s ease'
                    }}
                  >
                    <input
                      type="radio"
                      name="action"
                      value={action.value}
                      checked={form.action === action.value}
                      onChange={e => setForm({ ...form, action: e.target.value })}
                      style={{ accentColor: action.color }}
                    />
                    <span style={{ color: action.color, fontSize: '16px' }}>{action.icon}</span>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: '13px', color: 'var(--text-primary)' }}>{ACTION_LABELS[action.value]}</div>
                      <div style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{ACTION_DESCRIPTIONS[action.value]}</div>
                    </div>
                  </label>
                ))}
              </div>
            </div>
            <div className="mb-3">
              <label className="form-label">
                {form.action === 'AUTO_DENY' ? t('fileAutomation.modal.denialReasonLabel') : t('fileAutomation.modal.actionMessageLabel')}
              </label>
              <textarea
                className="form-control"
                value={form.actionMessage}
                onChange={e => setForm({ ...form, actionMessage: e.target.value })}
                placeholder={
                  form.action === 'AUTO_DENY'
                    ? t('fileAutomation.modal.denialReasonPlaceholder')
                    : t('fileAutomation.modal.actionMessagePlaceholder')
                }
                rows={2}
              />
            </div>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default FileAutomationRules;
