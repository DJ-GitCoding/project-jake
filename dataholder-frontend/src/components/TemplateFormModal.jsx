/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import Modal from './Modal';
import AccessLevelBadge from './AccessLevelBadge';
import RdapParametersEditor, { getDefaultValues, getPublicOnlyValues } from './RdapParametersEditor';
import {
  getTemplateTestData,
  addTemplateTestData,
  updateTemplateTestData,
  deleteTemplateTestData,
  getRdapDomains,
  getRdapIps,
  getRdapAsns,
} from '../services/api';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

// ==================== Shared Constants ====================

const QUERY_TYPE_ICONS = {
  domain: '🌐',
  ip: '🔢',
  asn: '📡',
};

// ==================== Sub-Components ====================

/**
 * Tabs for organizing the form
 */
const FormTabs = ({ activeTab, onTabChange, tabs }) => {
  return (
    <div style={{ 
      display: 'flex', 
      borderBottom: '1px solid var(--border-primary)',
      marginBottom: '16px',
      gap: '4px',
      overflowX: 'auto'
    }}>
      {tabs.map(tab => (
        <button
          key={tab.id}
          onClick={() => onTabChange(tab.id)}
          style={{
            padding: '12px 16px',
            border: 'none',
            backgroundColor: activeTab === tab.id ? 'var(--bg-secondary)' : 'transparent',
            borderBottom: activeTab === tab.id ? '2px solid var(--accent-primary)' : '2px solid transparent',
            cursor: 'pointer',
            fontWeight: activeTab === tab.id ? 600 : 400,
            color: activeTab === tab.id ? 'var(--text-primary)' : 'var(--text-secondary)',
            transition: 'all 0.2s ease',
            whiteSpace: 'nowrap'
          }}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
};

/**
 * Request Type Badge
 */
const RequestTypeBadge = ({ requestType }) => {
  const { t } = useT();
  if (requestType.supportsExigent) {
    return (
      <span className="badge bg-danger-subtle text-danger" style={{ fontSize: '11px' }}>
        ⚡ {t('templateForm.requestTypeBadge.exigent')}
      </span>
    );
  }
  if (requestType.supportsConfidential) {
    return (
      <span className="badge bg-warning-subtle text-warning" style={{ fontSize: '11px' }}>
        🔒 {t('templateForm.requestTypeBadge.confidential')}
      </span>
    );
  }
  return (
    <span className="badge bg-light text-dark border" style={{ fontSize: '11px' }}>
      🌐 {t('templateForm.requestTypeBadge.standard')}
    </span>
  );
};

/**
 * Inline editor for a single request type
 */
const RequestTypeEditor = ({ requestType, index, onChange, onRemove, onEditRdap }) => {
  const { t } = useT();
  const handleChange = (field, value) => {
    onChange(index, { ...requestType, [field]: value });
  };

  return (
    <div
      style={{
        padding: '16px',
        border: '1px solid var(--border-primary)',
        borderRadius: '8px',
        backgroundColor: requestType.isActive === false ? 'var(--bg-tertiary)' : 'var(--bg-secondary)',
        opacity: requestType.isActive === false ? 0.6 : 1,
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
      }}
    >
      {/* Header row */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <RequestTypeBadge requestType={requestType} />
          <strong style={{ fontSize: '14px' }}>{requestType.name || t('templateForm.requestTypeEditor.unnamed')}</strong>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            className="btn btn-outline-secondary btn-sm"
            onClick={() => onEditRdap(index)}
            title={t('templateForm.requestTypeEditor.editRdapParameters')}
            type="button"
          >
            {t('templateForm.requestTypeEditor.rdapButton')}
          </button>
          <button
            className="btn btn-danger btn-sm"
            onClick={() => onRemove(index)}
            title={t('common.remove')}
            type="button"
          >
            <i className="fa-solid fa-xmark"></i>
          </button>
        </div>
      </div>

      {/* Fields row */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: '12px' }}>
        <div className="mb-3" style={{ marginBottom: 0 }}>
          <label className="form-label" style={{ fontSize: '12px' }}>{t('templateForm.requestTypeEditor.nameLabel')}</label>
          <input
            type="text"
            className="form-control"
            value={requestType.name || ''}
            onChange={(e) => handleChange('name', e.target.value)}
            placeholder={t('templateForm.requestTypeEditor.namePlaceholder')}
            style={{ fontSize: '13px' }}
          />
        </div>

        <div className="mb-3" style={{ marginBottom: 0 }}>
          <label className="form-label" style={{ fontSize: '12px' }}>{t('templateForm.requestTypeEditor.typeCodeLabel')}</label>
          <input
            type="number"
            className="form-control"
            value={requestType.typeCode ?? ''}
            onChange={(e) => handleChange('typeCode', e.target.value ? parseInt(e.target.value) : null)}
            placeholder={t('templateForm.requestTypeEditor.typeCodePlaceholder')}
            min="0"
            max="999"
            style={{ fontSize: '13px' }}
          />
          <small style={{ color: 'var(--text-muted)', fontSize: '11px' }}>{t('templateForm.requestTypeEditor.typeCodeHint')}</small>
        </div>

        <div className="mb-3" style={{ marginBottom: 0 }}>
          <label className="form-label" style={{ fontSize: '12px' }}>{t('templateForm.requestTypeEditor.accessLevelLabel')}</label>
          <select
            className="form-select"
            value={requestType.accessLevel ?? 0}
            onChange={(e) => handleChange('accessLevel', parseInt(e.target.value))}
            style={{ fontSize: '13px' }}
          >
            <option value="0">{t('templateForm.requestTypeEditor.accessLevelOptions.level0')}</option>
            <option value="1">{t('templateForm.requestTypeEditor.accessLevelOptions.level1')}</option>
            <option value="2">{t('templateForm.requestTypeEditor.accessLevelOptions.level2')}</option>
            <option value="3">{t('templateForm.requestTypeEditor.accessLevelOptions.level3')}</option>
          </select>
        </div>

        <div className="mb-3" style={{ marginBottom: 0 }}>
          <label className="form-label" style={{ fontSize: '12px' }}>{t('templateForm.requestTypeEditor.sortOrderLabel')}</label>
          <input
            type="number"
            className="form-control"
            value={requestType.sortOrder ?? 0}
            onChange={(e) => handleChange('sortOrder', parseInt(e.target.value) || 0)}
            min="0"
            style={{ fontSize: '13px' }}
          />
        </div>
      </div>

      {/* Description + flags row */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: '12px', alignItems: 'end' }}>
        <div className="mb-3" style={{ marginBottom: 0 }}>
          <label className="form-label" style={{ fontSize: '12px' }}>{t('common.description')}</label>
          <input
            type="text"
            className="form-control"
            value={requestType.description || ''}
            onChange={(e) => handleChange('description', e.target.value)}
            placeholder={t('templateForm.requestTypeEditor.descriptionPlaceholder')}
            style={{ fontSize: '13px' }}
          />
        </div>

        <div style={{ display: 'flex', gap: '16px', paddingBottom: '4px' }}>
          <label className="form-check d-flex align-items-center gap-2 m-0" style={{ fontSize: '12px' }}>
            <input
              type="checkbox"
              className="form-check-input m-0"
              checked={requestType.supportsConfidential || false}
              onChange={(e) => handleChange('supportsConfidential', e.target.checked)}
            />
            <span>{t('templateForm.requestTypeEditor.confidential')}</span>
          </label>
          <label className="form-check d-flex align-items-center gap-2 m-0" style={{ fontSize: '12px' }}>
            <input
              type="checkbox"
              className="form-check-input m-0"
              checked={requestType.supportsExigent || false}
              onChange={(e) => handleChange('supportsExigent', e.target.checked)}
            />
            <span>{t('templateForm.requestTypeEditor.exigent')}</span>
          </label>
          <label className="form-check d-flex align-items-center gap-2 m-0" style={{ fontSize: '12px' }}>
            <input
              type="checkbox"
              className="form-check-input m-0"
              checked={requestType.requiresManualApproval !== false}
              onChange={(e) => handleChange('requiresManualApproval', e.target.checked)}
            />
            <span>{t('templateForm.requestTypeEditor.manualApproval')}</span>
          </label>
          <label className="form-check d-flex align-items-center gap-2 m-0" style={{ fontSize: '12px' }}>
            <input
              type="checkbox"
              className="form-check-input m-0"
              checked={requestType.isActive !== false}
              onChange={(e) => handleChange('isActive', e.target.checked)}
            />
            <span>{t('common.active')}</span>
          </label>
        </div>
      </div>
    </div>
  );
};

/**
 * RDAP Parameters Modal for a specific request type
 */
const RdapForRequestTypeModal = ({ isOpen, onClose, requestType, onSave }) => {
  const { t } = useT();
  const [rdapParams, setRdapParams] = useState(requestType?.rdapParameters || getDefaultValues());

  useEffect(() => {
    if (isOpen && requestType) {
      setRdapParams(requestType.rdapParameters || getDefaultValues());
    }
  }, [isOpen, requestType]);

  if (!requestType) return null;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={
        <div>
          <div>{t('templateForm.rdapModal.title')}</div>
          <div style={{ fontSize: '13px', fontWeight: 400, color: 'var(--text-muted)', marginTop: '4px' }}>
            <RequestTypeBadge requestType={requestType} />
            <span style={{ marginLeft: '8px' }}>{requestType.name || t('templateForm.requestTypeEditor.unnamed')}</span>
            <span style={{ marginLeft: '8px' }}>
              <AccessLevelBadge level={requestType.accessLevel ?? 0} />
            </span>
          </div>
        </div>
      }
      size="large"
      footer={
        <>
          <button className="btn btn-outline-secondary" onClick={onClose}>{t('common.cancel')}</button>
          <button className="btn btn-primary" onClick={() => onSave(rdapParams)}>
            {t('templateForm.rdapModal.saveButton')}
          </button>
        </>
      }
    >
      <RdapParametersEditor
        values={rdapParams}
        onChange={setRdapParams}
        showSummary={true}
        showPresets={true}
      />
    </Modal>
  );
};

// ==================== Test Data Tab Components ====================

/**
 * Test Data Tab — lives inside TemplateFormModal (gear icon / edit mode).
 * Changes are saved immediately via API, not via the parent form's Save button.
 */
const TestDataTab = ({ templateId, requestTypes }) => {
  const { t } = useT();
  const [testData, setTestData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingEntry, setEditingEntry] = useState(null);

  useEffect(() => {
    if (templateId) loadTestData();
  }, [templateId]);

  const loadTestData = async () => {
    try {
      setLoading(true);
      const res = await getTemplateTestData(templateId);
      setTestData(res.data || []);
    } catch (err) {
      console.error('Failed to load test data:', err);
      toast.error(t('templateForm.testData.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (entryId) => {
    try {
      await deleteTemplateTestData(templateId, entryId);
      toast.success(t('templateForm.testData.deleteSuccess'));
      loadTestData();
    } catch (err) {
      toast.error(t('templateForm.testData.deleteFailed'));
    }
  };

  const handleToggleActive = async (entry) => {
    try {
      await updateTemplateTestData(templateId, entry.id, { isActive: !entry.isActive });
      loadTestData();
    } catch (err) {
      toast.error(t('templateForm.testData.updateFailed'));
    }
  };

  if (loading) {
    return <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-muted)' }}>{t('templateForm.testData.loading')}</div>;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{
        padding: '12px 16px',
        backgroundColor: 'var(--bg-secondary)',
        borderRadius: '8px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
      }}>
        <p className="small text-muted" style={{ margin: 0 }}>
          {t('templateForm.testData.description')}
        </p>
        <button
          className="btn btn-primary btn-sm"
          onClick={() => { setEditingEntry(null); setShowAddModal(true); }}
          style={{ flexShrink: 0 }}
        >
          {t('templateForm.testData.addButton')}
        </button>
      </div>

      {testData.length === 0 ? (
        <div style={{
          textAlign: 'center',
          padding: '40px 20px',
          background: 'var(--bg-secondary, #f5f5f5)',
          borderRadius: '8px',
        }}>
          <div style={{ fontSize: '36px', marginBottom: '8px' }}>🧪</div>
          <p className="text-muted">{t('templateForm.testData.emptyTitle')}</p>
          <p className="text-muted small">
            {t('templateForm.testData.emptyDescription')}
          </p>
          <button className="btn btn-primary mt-3" onClick={() => { setEditingEntry(null); setShowAddModal(true); }}>
            {t('templateForm.testData.addFirstButton')}
          </button>
        </div>
      ) : (
        <>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {testData.map((entry) => (
              <TestDataEntryCard
                key={entry.id}
                entry={entry}
                onEdit={() => { setEditingEntry(entry); setShowAddModal(true); }}
                onDelete={() => handleDelete(entry.id)}
                onToggleActive={() => handleToggleActive(entry)}
              />
            ))}
          </div>

          {/* Summary */}
          <div style={{
            padding: '16px',
            backgroundColor: 'var(--bg-secondary)',
            borderRadius: '8px',
            marginTop: '8px',
          }}>
            <h4 style={{ margin: '0 0 12px 0' }}>{t('templateForm.testData.summaryTitle')}</h4>
            <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap' }}>
              <div>
                <div className="text-muted small">{t('templateForm.testData.total')}</div>
                <strong>{testData.length}</strong>
              </div>
              <div>
                <div className="text-muted small">{t('common.active')}</div>
                <strong>{testData.filter(e => e.isActive !== false).length}</strong>
              </div>
              <div>
                <div className="text-muted small">{t('templateForm.testData.domains')}</div>
                <strong>{testData.filter(e => e.queryType === 'domain').length}</strong>
              </div>
              <div>
                <div className="text-muted small">{t('templateForm.testData.ips')}</div>
                <strong>{testData.filter(e => e.queryType === 'ip').length}</strong>
              </div>
              <div>
                <div className="text-muted small">{t('templateForm.testData.asns')}</div>
                <strong>{testData.filter(e => e.queryType === 'asn').length}</strong>
              </div>
            </div>
          </div>
        </>
      )}

      {/* Add/Edit Modal */}
      <AddTestDataModal
        isOpen={showAddModal}
        onClose={() => { setShowAddModal(false); setEditingEntry(null); }}
        templateId={templateId}
        requestTypes={requestTypes || []}
        entry={editingEntry}
        onSaved={() => { setShowAddModal(false); setEditingEntry(null); loadTestData(); }}
      />
    </div>
  );
};

/**
 * Individual test data entry card
 */
const TestDataEntryCard = ({ entry, onEdit, onDelete, onToggleActive }) => {
  const { t } = useT();
  const icon = QUERY_TYPE_ICONS[entry.queryType] || '📋';
  const isActive = entry.isActive !== false;

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: '12px',
      padding: '12px 14px',
      border: '1px solid var(--border-color, #eee)',
      borderRadius: '8px',
      opacity: isActive ? 1 : 0.5,
      background: isActive ? '#fff' : 'var(--bg-tertiary, #f8f9fa)',
    }}>
      {/* Icon */}
      <span style={{ fontSize: '20px', flexShrink: 0 }}>{icon}</span>

      {/* Main info */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontWeight: 600, fontSize: '13px' }}>
          {entry.displayLabel || entry.label || `${entry.queryType}/${entry.queryValue}`}
        </div>
        <div style={{ display: 'flex', gap: '6px', marginTop: '4px', flexWrap: 'wrap', alignItems: 'center' }}>
          <span className="badge bg-light text-dark border" style={{ fontSize: '10px' }}>{entry.queryType?.toUpperCase()}</span>
          <code style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{entry.queryValue}</code>
          {entry.requestTypeName && (
            <span className="badge bg-warning-subtle text-warning" style={{ fontSize: '10px' }}>{entry.requestTypeName}</span>
          )}
          {entry.verifyContactAccess && (
            <span title={t('templateForm.testDataCard.verifyContactAccessTitle')} style={{ fontSize: '11px' }}>👤</span>
          )}
          {entry.verifyRedaction && (
            <span title={t('templateForm.testDataCard.verifyRedactionTitle')} style={{ fontSize: '11px' }}>🔒</span>
          )}
        </div>
        {entry.description && (
          <div className="text-muted" style={{ fontSize: '11px', marginTop: '2px' }}>{entry.description}</div>
        )}
      </div>

      {/* RDAP entity info */}
      {entry.rdapEntityHandle && (
        <div style={{ textAlign: 'right', flexShrink: 0, fontSize: '11px', color: 'var(--text-muted)' }}>
          <div>{entry.rdapEntityObjectType}</div>
          <code>{entry.rdapEntityHandle}</code>
        </div>
      )}

      {/* Actions */}
      <div style={{ display: 'flex', gap: '4px', flexShrink: 0 }}>
        <button
          className="btn btn-outline-secondary btn-sm"
          onClick={onToggleActive}
          title={isActive ? t('templateForm.testDataCard.disable') : t('templateForm.testDataCard.enable')}
          style={{ fontSize: '14px', padding: '4px 6px' }}
        >
          {isActive ? '⏸' : '▶'}
        </button>
        <button
          className="btn btn-outline-secondary btn-sm"
          onClick={onEdit}
          title={t('common.edit')}
          style={{ fontSize: '14px', padding: '4px 6px' }}
        >
          ✏️
        </button>
        <button
          className="btn btn-outline-secondary btn-sm"
          onClick={onDelete}
          title={t('common.remove')}
          style={{ fontSize: '14px', padding: '4px 6px', color: 'var(--accent-error)' }}
        >
          ✕
        </button>
      </div>
    </div>
  );
};

/**
 * Modal to add or edit a test data entry — with RDAP entity search
 */
const AddTestDataModal = ({ isOpen, onClose, templateId, requestTypes, entry, onSaved }) => {
  const { t } = useT();
  const [searchType, setSearchType] = useState('domain');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [selectedEntity, setSelectedEntity] = useState(null);
  const [requestTypeName, setRequestTypeName] = useState('standard');
  const [label, setLabel] = useState('');
  const [description, setDescription] = useState('');
  const [verifyContactAccess, setVerifyContactAccess] = useState(true);
  const [verifyRedaction, setVerifyRedaction] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const isEditing = !!entry;

  // Populate form when editing
  useEffect(() => {
    if (entry) {
      setRequestTypeName(entry.requestTypeName || 'standard');
      setLabel(entry.label || '');
      setDescription(entry.description || '');
      setVerifyContactAccess(entry.verifyContactAccess !== false);
      setVerifyRedaction(entry.verifyRedaction !== false);
      setSearchType(entry.queryType || 'domain');
      setSelectedEntity(entry.rdapEntityId ? {
        id: entry.rdapEntityId,
        handle: entry.rdapEntityHandle,
        objectType: entry.rdapEntityObjectType,
        displayIdentifier: entry.rdapEntityDisplayIdentifier || entry.queryValue,
      } : null);
    } else {
      setRequestTypeName('standard');
      setLabel('');
      setDescription('');
      setVerifyContactAccess(true);
      setVerifyRedaction(true);
      setSelectedEntity(null);
      setSearchResults([]);
      setSearchQuery('');
    }
  }, [entry, isOpen]);

  const handleSearch = async () => {
    setSearching(true);
    try {
      let res;
      switch (searchType) {
        case 'domain':
          res = await getRdapDomains(0, 20, 'ldhName', 'asc', searchQuery);
          break;
        case 'ip':
          res = await getRdapIps(0, 20, 'handle', 'asc', searchQuery);
          break;
        case 'asn':
          res = await getRdapAsns(0, 20, 'handle', 'asc', searchQuery);
          break;
        default:
          res = { data: { content: [] } };
      }
      // Normalize results — API may return paginated or flat array
      const content = res.data?.content || res.data || [];
      setSearchResults(Array.isArray(content) ? content : []);
    } catch (err) {
      console.error('Search failed:', err);
      toast.error(t('templateForm.addTestData.searchFailed'));
      setSearchResults([]);
    } finally {
      setSearching(false);
    }
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      if (isEditing) {
        await updateTemplateTestData(templateId, entry.id, {
          rdapEntityId: selectedEntity?.id || entry.rdapEntityId,
          requestTypeName,
          label: label || null,
          description: description || null,
          verifyContactAccess,
          verifyRedaction,
        });
        toast.success(t('templateForm.addTestData.updateSuccess'));
      } else {
        if (!selectedEntity) {
          toast.error(t('templateForm.addTestData.selectEntityError'));
          setSubmitting(false);
          return;
        }
        await addTemplateTestData(templateId, {
          rdapEntityId: selectedEntity.id,
          requestTypeName,
          label: label || null,
          description: description || null,
          verifyContactAccess,
          verifyRedaction,
        });
        toast.success(t('templateForm.addTestData.addSuccess'));
      }
      onSaved();
    } catch (err) {
      const msg = err.response?.data?.error || t('common.saveFailed');
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  // Derive display name for domain/ip/asn
  const getEntityDisplayName = (item) => {
    return item.ldhName || item.handle || item.startAddress || item.displayIdentifier || `ID: ${item.id}`;
  };

  const getEntitySubtext = (item) => {
    if (item.ldhName) return `Handle: ${item.handle || '—'}`;
    if (item.startAddress && item.endAddress) return `${item.startAddress} — ${item.endAddress}`;
    if (item.startAutnum != null) return `AS${item.startAutnum}${item.endAutnum !== item.startAutnum ? `—${item.endAutnum}` : ''}`;
    return item.handle || '';
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={isEditing ? t('templateForm.addTestData.editTitle') : t('templateForm.addTestData.addTitle')}
      footer={
        <>
          <button className="btn btn-outline-secondary" onClick={onClose}>{t('common.cancel')}</button>
          <button
            className="btn btn-primary"
            onClick={handleSubmit}
            disabled={submitting || (!isEditing && !selectedEntity)}
          >
            {submitting ? t('common.saving') : isEditing ? t('templateForm.addTestData.updateButton') : t('templateForm.addTestData.addEntryButton')}
          </button>
        </>
      }
    >
      {/* Entity Search — only for new entries */}
      {!isEditing && (
        <div style={{ marginBottom: '16px' }}>
          <label className="form-label">{t('templateForm.addTestData.searchLabel')}</label>
          <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
            <select
              className="form-select"
              value={searchType}
              onChange={(e) => { setSearchType(e.target.value); setSearchResults([]); }}
              style={{ width: '120px', flexShrink: 0 }}
            >
              <option value="domain">{t('templateForm.addTestData.searchTypeDomains')}</option>
              <option value="ip">{t('templateForm.addTestData.searchTypeIps')}</option>
              <option value="asn">{t('templateForm.addTestData.searchTypeAsns')}</option>
            </select>
            <input
              className="form-control"
              type="text"
              placeholder={t('templateForm.addTestData.searchPlaceholder', { type: searchType })}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              style={{ flex: 1 }}
            />
            <button className="btn btn-outline-secondary" onClick={handleSearch} disabled={searching}>
              {searching ? '...' : t('common.search')}
            </button>
          </div>

          {/* Selected entity display */}
          {selectedEntity && (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: '8px 12px',
              background: '#f0fdf4',
              border: '1px solid #bbf7d0',
              borderRadius: '6px',
              marginBottom: '8px',
            }}>
              <span style={{ color: '#16a34a', fontWeight: 700 }}>✓</span>
              <span style={{ fontSize: '13px', fontWeight: 500 }}>{selectedEntity.displayIdentifier || selectedEntity.handle}</span>
              <span className="badge bg-light text-dark border" style={{ fontSize: '10px' }}>{selectedEntity.objectType || searchType.toUpperCase()}</span>
              <button
                className="btn btn-outline-secondary btn-sm"
                onClick={() => setSelectedEntity(null)}
                style={{ marginLeft: 'auto', fontSize: '12px', color: 'var(--accent-error)' }}
              >
                ✕
              </button>
            </div>
          )}

          {/* Search results */}
          {searchResults.length > 0 && !selectedEntity && (
            <div style={{
              maxHeight: '200px',
              overflow: 'auto',
              border: '1px solid var(--border-color, #eee)',
              borderRadius: '6px',
            }}>
              {searchResults.map((item) => (
                <div
                  key={item.id}
                  onClick={() => setSelectedEntity({
                    id: item.id,
                    handle: item.handle,
                    objectType: searchType === 'domain' ? 'DOMAIN' : searchType === 'ip' ? 'IP_NETWORK' : 'AUTNUM',
                    displayIdentifier: getEntityDisplayName(item),
                  })}
                  style={{
                    padding: '8px 12px',
                    cursor: 'pointer',
                    borderBottom: '1px solid var(--border-color, #f0f0f0)',
                    fontSize: '13px',
                  }}
                  onMouseEnter={(e) => e.currentTarget.style.background = 'var(--bg-tertiary, #f8f9fa)'}
                  onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                >
                  <div style={{ fontWeight: 500 }}>{getEntityDisplayName(item)}</div>
                  <div className="text-muted" style={{ fontSize: '11px' }}>{getEntitySubtext(item)}</div>
                </div>
              ))}
            </div>
          )}

          {searchResults.length === 0 && searching === false && searchQuery && (
            <div className="text-muted small" style={{ padding: '8px 0' }}>
              {t('templateForm.addTestData.noResults')}
            </div>
          )}
        </div>
      )}

      {/* Editing: show current entity */}
      {isEditing && entry && (
        <div style={{
          padding: '10px 14px',
          background: 'var(--bg-secondary, #f5f5f5)',
          borderRadius: '6px',
          marginBottom: '16px',
          fontSize: '13px',
        }}>
          <div className="text-muted small" style={{ marginBottom: '4px' }}>{t('templateForm.addTestData.entityLabel')}</div>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <span>{QUERY_TYPE_ICONS[entry.queryType] || '📋'}</span>
            <strong>{entry.queryValue}</strong>
            <span className="badge bg-light text-dark border" style={{ fontSize: '10px' }}>{entry.queryType?.toUpperCase()}</span>
            <code className="text-muted" style={{ fontSize: '11px' }}>{entry.rdapEntityHandle}</code>
          </div>
        </div>
      )}

      {/* Configuration Fields */}
      <div style={{ display: 'grid', gap: '12px' }}>
        <div className="mb-3" style={{ margin: 0 }}>
          <label className="form-label">{t('templateForm.addTestData.requestTypeLabel')}</label>
          <select
            className="form-select"
            value={requestTypeName}
            onChange={(e) => setRequestTypeName(e.target.value)}
          >
            {requestTypes.length > 0 ? (
              requestTypes.filter(rt => rt.isActive !== false).map((rt) => (
                <option key={rt.name} value={rt.name}>{rt.name} (Level {rt.accessLevel})</option>
              ))
            ) : (
              <option value="standard">standard</option>
            )}
          </select>
          <div className="text-muted small" style={{ marginTop: '4px' }}>
            {t('templateForm.addTestData.requestTypeHint')}
          </div>
        </div>

        <div className="mb-3" style={{ margin: 0 }}>
          <label className="form-label">{t('templateForm.addTestData.labelField')} ({t('common.optional')})</label>
          <input
            className="form-control"
            type="text"
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            placeholder={t('templateForm.addTestData.labelPlaceholder')}
          />
        </div>

        <div className="mb-3" style={{ margin: 0 }}>
          <label className="form-label">{t('common.description')} ({t('common.optional')})</label>
          <textarea
            className="form-control"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={2}
            placeholder={t('templateForm.addTestData.descriptionPlaceholder')}
          />
        </div>

        <div style={{ display: 'flex', gap: '24px' }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', cursor: 'pointer' }}>
            <input
              type="checkbox"
              checked={verifyContactAccess}
              onChange={(e) => setVerifyContactAccess(e.target.checked)}
            />
            👤 {t('templateForm.addTestData.verifyContactVisibility')}
          </label>
          <label style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '13px', cursor: 'pointer' }}>
            <input
              type="checkbox"
              checked={verifyRedaction}
              onChange={(e) => setVerifyRedaction(e.target.checked)}
            />
            🔒 {t('templateForm.addTestData.verifyFieldRedaction')}
          </label>
        </div>
      </div>
    </Modal>
  );
};

// ==================== Main Template Form Modal ====================

/**
 * Template Form Modal - handles both create and edit.
 * In edit mode, includes a "Test Data" tab for managing RDAP test entities.
 */
const TemplateFormModal = ({
  isOpen,
  onClose,
  onSave,
  template = null,
  submitting = false
}) => {
  const { t } = useT();
  const isEditing = !!template;

  const getInitialFormData = (tmpl) => {
    if (tmpl) {
      return {
        name: tmpl.name || '',
        shortDescription: tmpl.shortDescription || '',
        description: tmpl.description || '',
        requiredGroupTypes: tmpl.requiredGroupTypes || '',
        termsAndConditions: tmpl.termsAndConditions || '',
        dataUsagePolicy: tmpl.dataUsagePolicy || '',
        maxQueriesPerDay: tmpl.maxQueriesPerDay ?? '',
        maxQueriesPerMonth: tmpl.maxQueriesPerMonth ?? '',
        isPublished: tmpl.isPublished ?? false,
        requestTypes: (tmpl.requestTypes && tmpl.requestTypes.length > 0)
          ? tmpl.requestTypes.map(rt => ({
              id: rt.id,
              name: rt.name || '',
              typeCode: rt.typeCode ?? null,
              description: rt.description || '',
              accessLevel: rt.accessLevel ?? 0,
              supportsConfidential: rt.supportsConfidential || false,
              supportsExigent: rt.supportsExigent || false,
              requiresManualApproval: rt.requiresManualApproval !== false,
              sortOrder: rt.sortOrder ?? 0,
              isActive: rt.isActive !== false,
              rdapParameters: rt.rdapParameters || getDefaultValues(),
            }))
          : [{
              name: 'standard',
              description: 'Standard RDAP request',
              accessLevel: tmpl.accessLevel ?? 0,
              supportsConfidential: false,
              supportsExigent: false,
              requiresManualApproval: true,
              sortOrder: 0,
              isActive: true,
              rdapParameters: tmpl.rdapParameters || getDefaultValues(),
            }],
      };
    }
    return {
      name: '',
      shortDescription: '',
      description: '',
      requiredGroupTypes: '',
      termsAndConditions: '',
      dataUsagePolicy: '',
      maxQueriesPerDay: '',
      maxQueriesPerMonth: '',
      isPublished: false,
      requestTypes: [{
        name: 'standard',
        description: 'Standard RDAP request',
        accessLevel: 0,
        supportsConfidential: false,
        supportsExigent: false,
        requiresManualApproval: true,
        sortOrder: 0,
        isActive: true,
        rdapParameters: getDefaultValues(),
      }],
    };
  };

  const [formData, setFormData] = useState(() => getInitialFormData(template));
  const [activeTab, setActiveTab] = useState('basic');
  const [errors, setErrors] = useState({});

  // RDAP modal state for individual request types
  const [rdapModalOpen, setRdapModalOpen] = useState(false);
  const [rdapEditIndex, setRdapEditIndex] = useState(null);

  useEffect(() => {
    if (isOpen) {
      setFormData(getInitialFormData(template));
      setActiveTab('basic');
      setErrors({});
    }
  }, [isOpen, template]);

  const handleChange = (field, value) => {
    setFormData(prev => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: null }));
    }
  };

  // Request type handlers
  const handleRequestTypeChange = (index, updatedRt) => {
    setFormData(prev => {
      const updated = [...prev.requestTypes];
      updated[index] = updatedRt;
      return { ...prev, requestTypes: updated };
    });
  };

  const handleAddRequestType = (preset) => {
    const presets = {
      standard: {
        name: 'standard',
        typeCode: 1,
        description: 'Standard RDAP request',
        accessLevel: 1,
        supportsConfidential: false,
        supportsExigent: false,
        requiresManualApproval: true,
        sortOrder: formData.requestTypes.length,
        isActive: true,
        rdapParameters: getDefaultValues(),
      },
      confidential: {
        name: 'confidential',
        typeCode: 2,
        description: 'Confidential disclosure request',
        accessLevel: 2,
        supportsConfidential: true,
        supportsExigent: false,
        requiresManualApproval: true,
        sortOrder: formData.requestTypes.length,
        isActive: true,
        rdapParameters: getDefaultValues(),
      },
      exigent: {
        name: 'exigent',
        typeCode: 3,
        description: 'Exigent disclosure request',
        accessLevel: 3,
        supportsConfidential: false,
        supportsExigent: true,
        requiresManualApproval: false,
        sortOrder: formData.requestTypes.length,
        isActive: true,
        rdapParameters: getDefaultValues(),
      },
    };

    const newRt = presets[preset] || presets.standard;
    setFormData(prev => ({
      ...prev,
      requestTypes: [...prev.requestTypes, newRt],
    }));
  };

  const handleRemoveRequestType = (index) => {
    setFormData(prev => ({
      ...prev,
      requestTypes: prev.requestTypes.filter((_, i) => i !== index),
    }));
  };

  const handleEditRdap = (index) => {
    setRdapEditIndex(index);
    setRdapModalOpen(true);
  };

  const handleSaveRdap = (rdapParams) => {
    if (rdapEditIndex !== null) {
      setFormData(prev => {
        const updated = [...prev.requestTypes];
        updated[rdapEditIndex] = { ...updated[rdapEditIndex], rdapParameters: rdapParams };
        return { ...prev, requestTypes: updated };
      });
    }
    setRdapModalOpen(false);
    setRdapEditIndex(null);
  };

  const validate = () => {
    const newErrors = {};
    if (!formData.name.trim()) {
      newErrors.name = t('templateForm.errors.nameRequired');
    }
    if (formData.shortDescription && formData.shortDescription.length > 25) {
      newErrors.shortDescription = t('templateForm.errors.shortDescriptionTooLong');
    }
    if (formData.maxQueriesPerDay && isNaN(parseInt(formData.maxQueriesPerDay))) {
      newErrors.maxQueriesPerDay = t('templateForm.errors.mustBeNumber');
    }
    if (formData.maxQueriesPerMonth && isNaN(parseInt(formData.maxQueriesPerMonth))) {
      newErrors.maxQueriesPerMonth = t('templateForm.errors.mustBeNumber');
    }
    if (formData.requestTypes.length === 0) {
      newErrors.requestTypes = t('templateForm.errors.requestTypesRequired');
    }
    const rtWithoutName = formData.requestTypes.find(rt => !rt.name?.trim());
    if (rtWithoutName) {
      newErrors.requestTypes = t('templateForm.errors.requestTypesNameRequired');
    }
    const rtWithoutCode = formData.requestTypes.find(rt => rt.typeCode == null || rt.typeCode === '');
    if (!newErrors.requestTypes && rtWithoutCode) {
      newErrors.requestTypes = t('templateForm.errors.requestTypesCodeRequired');
    }
    const typeCodes = formData.requestTypes.map(rt => rt.typeCode).filter(tc => tc != null);
    if (!newErrors.requestTypes && new Set(typeCodes).size !== typeCodes.length) {
      newErrors.requestTypes = t('templateForm.errors.requestTypesCodeUnique');
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = () => {
    if (!validate()) {
      if (errors.requestTypes) {
        setActiveTab('requestTypes');
      } else {
        setActiveTab('basic');
      }
      return;
    }

    const data = {
      ...formData,
      maxQueriesPerDay: formData.maxQueriesPerDay ? parseInt(formData.maxQueriesPerDay) : null,
      maxQueriesPerMonth: formData.maxQueriesPerMonth ? parseInt(formData.maxQueriesPerMonth) : null,
    };

    onSave(data);
  };

  // Test data count from the template object (for tab badge)
  const testDataCount = template?.testDataCount || (template?.testData ? template.testData.length : 0);

  const tabs = [
    { id: 'basic', label: t('templateForm.tabs.basicInfo') },
    { id: 'requestTypes', label: t('templateForm.tabs.requestTypes', { count: formData.requestTypes.length }) },
    // Test Data tab only available when editing (template must exist to have an ID)
    ...(isEditing ? [{ id: 'testData', label: testDataCount ? t('templateForm.tabs.testData', { count: testDataCount }) : t('templateForm.tabs.testDataNoCount') }] : []),
    { id: 'limits', label: t('templateForm.tabs.limits') },
    { id: 'terms', label: t('templateForm.tabs.terms') },
  ];

  return (
    <>
      <Modal
        isOpen={isOpen}
        onClose={onClose}
        title={isEditing ? t('templateForm.title.edit') : t('templateForm.title.create')}
        size="large"
        footer={
          <>
            <button className="btn btn-outline-secondary" onClick={onClose} disabled={submitting}>
              {t('common.cancel')}
            </button>
            <button className="btn btn-primary" onClick={handleSubmit} disabled={submitting}>
              {submitting ? t('common.saving') : isEditing ? t('templateForm.updateButton') : t('templateForm.createButton')}
            </button>
          </>
        }
      >
        <FormTabs activeTab={activeTab} onTabChange={setActiveTab} tabs={tabs} />

        {/* Basic Info Tab */}
        {activeTab === 'basic' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div className="mb-3">
              <label className="form-label">{t('templateForm.basic.nameLabel')}</label>
              <input
                type="text"
                className={`form-control ${errors.name ? 'is-invalid' : ''}`}
                value={formData.name}
                onChange={(e) => handleChange('name', e.target.value)}
                placeholder={t('templateForm.basic.namePlaceholder')}
              />
              {errors.name && <p className="text-danger small mt-1">{errors.name}</p>}
            </div>

            <div className="mb-3">
              <label className="form-label">{t('templateForm.basic.shortDescriptionLabel')}</label>
              <input
                type="text"
                className={`form-control ${errors.shortDescription ? 'is-invalid' : ''}`}
                value={formData.shortDescription}
                onChange={(e) => handleChange('shortDescription', e.target.value)}
                placeholder={t('templateForm.basic.shortDescriptionPlaceholder')}
                maxLength={25}
              />
              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '4px' }}>
                {errors.shortDescription ? (
                  <p className="text-danger small">{errors.shortDescription}</p>
                ) : (
                  <p className="text-muted small">{t('templateForm.basic.shortDescriptionHint')}</p>
                )}
                <span className="text-muted small">
                  {t('templateForm.basic.charCount', { count: formData.shortDescription.length })}
                </span>
              </div>
            </div>

            <div className="mb-3">
              <label className="form-label">{t('common.description')}</label>
              <textarea
                className="form-control"
                value={formData.description}
                onChange={(e) => handleChange('description', e.target.value)}
                rows={3}
                placeholder={t('templateForm.basic.descriptionPlaceholder')}
              />
            </div>

            <div className="mb-3">
              <label className="form-label">{t('templateForm.basic.requiredGroupTypesLabel')}</label>
              <input
                type="text"
                className="form-control"
                value={formData.requiredGroupTypes}
                onChange={(e) => handleChange('requiredGroupTypes', e.target.value)}
                placeholder={t('templateForm.basic.requiredGroupTypesPlaceholder')}
              />
              <p className="text-muted small mt-1">
                {t('templateForm.basic.requiredGroupTypesHint')}
              </p>
            </div>

            <div style={{ display: 'flex', gap: '24px' }}>
              <div className="mb-3">
                <label className="form-check d-flex align-items-center gap-2 m-0">
                  <input
                    type="checkbox"
                    className="form-check-input m-0"
                    checked={formData.isPublished}
                    onChange={(e) => handleChange('isPublished', e.target.checked)}
                  />
                  <span>{t('templateForm.basic.publishedLabel')}</span>
                </label>
                <p className="text-muted small mt-1">
                  {t('templateForm.basic.publishedHint')}
                </p>
              </div>
            </div>
          </div>
        )}

        {/* Request Types Tab */}
        {activeTab === 'requestTypes' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div
              style={{
                padding: '12px 16px',
                backgroundColor: 'var(--bg-secondary)',
                borderRadius: '8px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <p className="small text-muted" style={{ margin: 0 }}>
                {t('templateForm.requestTypesTab.description')}
              </p>
              <div style={{ display: 'flex', gap: '8px', flexShrink: 0 }}>
                <button className="btn btn-outline-secondary btn-sm" onClick={() => handleAddRequestType('standard')} type="button">{t('templateForm.requestTypesTab.addStandard')}</button>
                <button className="btn btn-outline-secondary btn-sm" onClick={() => handleAddRequestType('confidential')} type="button">{t('templateForm.requestTypesTab.addConfidential')}</button>
                <button className="btn btn-outline-secondary btn-sm" onClick={() => handleAddRequestType('exigent')} type="button">{t('templateForm.requestTypesTab.addExigent')}</button>
              </div>
            </div>

            {errors.requestTypes && (
              <p className="text-danger small">{errors.requestTypes}</p>
            )}

            {formData.requestTypes.length === 0 ? (
              <div style={{
                textAlign: 'center',
                padding: '40px',
                border: '2px dashed var(--border-primary)',
                borderRadius: '8px',
                color: 'var(--text-muted)',
              }}>
                <p>{t('templateForm.requestTypesTab.emptyTitle')}</p>
                <p className="small">{t('templateForm.requestTypesTab.emptyHint')}</p>
              </div>
            ) : (
              formData.requestTypes.map((rt, index) => (
                <RequestTypeEditor
                  key={index}
                  requestType={rt}
                  index={index}
                  onChange={handleRequestTypeChange}
                  onRemove={handleRemoveRequestType}
                  onEditRdap={handleEditRdap}
                />
              ))
            )}

            {/* Summary */}
            {formData.requestTypes.length > 0 && (
              <div style={{ padding: '16px', backgroundColor: 'var(--bg-secondary)', borderRadius: '8px', marginTop: '8px' }}>
                <h4 style={{ margin: '0 0 12px 0' }}>{t('templateForm.requestTypesTab.summaryTitle')}</h4>
                <div style={{ display: 'flex', gap: '24px', flexWrap: 'wrap' }}>
                  <div>
                    <div className="text-muted small">{t('templateForm.requestTypesTab.totalTypes')}</div>
                    <strong>{formData.requestTypes.length}</strong>
                  </div>
                  <div>
                    <div className="text-muted small">{t('templateForm.requestTypesTab.active')}</div>
                    <strong>{formData.requestTypes.filter(rt => rt.isActive !== false).length}</strong>
                  </div>
                  <div>
                    <div className="text-muted small">{t('templateForm.requestTypesTab.confidential')}</div>
                    <strong>{formData.requestTypes.some(rt => rt.supportsConfidential) ? t('common.yes') : t('common.no')}</strong>
                  </div>
                  <div>
                    <div className="text-muted small">{t('templateForm.requestTypesTab.exigent')}</div>
                    <strong>{formData.requestTypes.some(rt => rt.supportsExigent) ? t('common.yes') : t('common.no')}</strong>
                  </div>
                  <div>
                    <div className="text-muted small">{t('templateForm.requestTypesTab.highestAccessLevel')}</div>
                    <AccessLevelBadge level={Math.max(...formData.requestTypes.map(rt => rt.accessLevel ?? 0))} />
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Test Data Tab — only visible in edit mode */}
        {activeTab === 'testData' && isEditing && template && (
          <TestDataTab
            templateId={template.id}
            requestTypes={formData.requestTypes}
          />
        )}

        {/* Rate Limits Tab */}
        {activeTab === 'limits' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div style={{ padding: '12px 16px', backgroundColor: 'var(--bg-secondary)', borderRadius: '8px', marginBottom: '8px' }}>
              <p className="small text-muted" style={{ margin: 0 }}>
                {t('templateForm.limits.description')}
              </p>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
              <div className="mb-3">
                <label className="form-label">{t('templateForm.limits.maxPerDayLabel')}</label>
                <input
                  type="number"
                  className={`form-control ${errors.maxQueriesPerDay ? 'is-invalid' : ''}`}
                  value={formData.maxQueriesPerDay}
                  onChange={(e) => handleChange('maxQueriesPerDay', e.target.value)}
                  placeholder={t('templateForm.limits.unlimitedPlaceholder')}
                  min="0"
                />
                {errors.maxQueriesPerDay && (
                  <p className="text-danger small mt-1">{errors.maxQueriesPerDay}</p>
                )}
              </div>

              <div className="mb-3">
                <label className="form-label">{t('templateForm.limits.maxPerMonthLabel')}</label>
                <input
                  type="number"
                  className={`form-control ${errors.maxQueriesPerMonth ? 'is-invalid' : ''}`}
                  value={formData.maxQueriesPerMonth}
                  onChange={(e) => handleChange('maxQueriesPerMonth', e.target.value)}
                  placeholder={t('templateForm.limits.unlimitedPlaceholder')}
                  min="0"
                />
                {errors.maxQueriesPerMonth && (
                  <p className="text-danger small mt-1">{errors.maxQueriesPerMonth}</p>
                )}
              </div>
            </div>

            <div style={{ padding: '16px', backgroundColor: 'var(--bg-secondary)', borderRadius: '8px', marginTop: '8px' }}>
              <h4 style={{ margin: '0 0 12px 0' }}>{t('templateForm.limits.summaryTitle')}</h4>
              <div style={{ display: 'flex', gap: '24px' }}>
                <div>
                  <div className="text-muted small">{t('templateForm.limits.dailyLimit')}</div>
                  <strong>{formData.maxQueriesPerDay ? t('templateForm.limits.queriesCount', { count: formData.maxQueriesPerDay }) : t('templateForm.limits.unlimited')}</strong>
                </div>
                <div>
                  <div className="text-muted small">{t('templateForm.limits.monthlyLimit')}</div>
                  <strong>{formData.maxQueriesPerMonth ? t('templateForm.limits.queriesCount', { count: formData.maxQueriesPerMonth }) : t('templateForm.limits.unlimited')}</strong>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Terms & Policy Tab */}
        {activeTab === 'terms' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div className="mb-3">
              <label className="form-label">{t('templateForm.terms.termsLabel')}</label>
              <textarea
                className="form-control"
                value={formData.termsAndConditions}
                onChange={(e) => handleChange('termsAndConditions', e.target.value)}
                rows={6}
                placeholder={t('templateForm.terms.termsPlaceholder')}
              />
            </div>

            <div className="mb-3">
              <label className="form-label">{t('templateForm.terms.policyLabel')}</label>
              <textarea
                className="form-control"
                value={formData.dataUsagePolicy}
                onChange={(e) => handleChange('dataUsagePolicy', e.target.value)}
                rows={6}
                placeholder={t('templateForm.terms.policyPlaceholder')}
              />
            </div>

            {(formData.termsAndConditions || formData.dataUsagePolicy) && (
              <div style={{ padding: '16px', backgroundColor: 'var(--bg-secondary)', borderRadius: '8px', marginTop: '8px' }}>
                <h4 style={{ margin: '0 0 8px 0' }}>{t('templateForm.terms.previewTitle')}</h4>
                <div className="small text-muted">
                  <strong>{t('templateForm.terms.termsShortLabel')}:</strong> {t('templateForm.terms.charactersCount', { count: formData.termsAndConditions?.length || 0 })}
                  <br />
                  <strong>{t('templateForm.terms.policyShortLabel')}:</strong> {t('templateForm.terms.charactersCount', { count: formData.dataUsagePolicy?.length || 0 })}
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* RDAP Parameters Modal for individual request type */}
      <RdapForRequestTypeModal
        isOpen={rdapModalOpen}
        onClose={() => { setRdapModalOpen(false); setRdapEditIndex(null); }}
        requestType={rdapEditIndex !== null ? formData.requestTypes[rdapEditIndex] : null}
        onSave={handleSaveRdap}
      />
    </>
  );
};

export default TemplateFormModal;