/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useMemo } from 'react';
import ReactDOM from 'react-dom';
import GroupedFieldSelect from './GroupedFieldSelect';
import { useT } from '../i18n';

const PolicyRedactionRulesEditor = ({
  rules = [],
  onChange,
  enumValues,
  readOnly = false,
}) => {
  const [expandedObjectType, setExpandedObjectType] = useState('ENTITY');
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingRule, setEditingRule] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const { t } = useT();

  const fieldDefinitions = enumValues?.rdapFieldDefinitions || [];
  const objectTypeOptions = enumValues?.objectTypeOptions || [
    { value: 'DOMAIN', displayName: t('policyRedaction.objectTypes.domain') },
    { value: 'ENTITY', displayName: t('policyRedaction.objectTypes.entity') },
    { value: 'NAMESERVER', displayName: t('policyRedaction.objectTypes.nameserver') },
    { value: 'IP_NETWORK', displayName: t('policyRedaction.objectTypes.ipNetwork') },
    { value: 'AUTNUM', displayName: t('policyRedaction.objectTypes.asNumber') },
    { value: 'ALL', displayName: t('policyRedaction.objectTypes.allObjects') },
  ];

  const SENSITIVITY_LEVELS = [
    { value: 0, label: t('policyRedaction.sensitivityLevels.level0'), color: 'var(--accent-success)' },
    { value: 1, label: t('policyRedaction.sensitivityLevels.level1'), color: 'var(--accent-info, #3b82f6)' },
    { value: 2, label: t('policyRedaction.sensitivityLevels.level2'), color: 'var(--accent-warning)' },
    { value: 3, label: t('policyRedaction.sensitivityLevels.level3'), color: 'var(--accent-error, #ef4444)' },
  ];

  const VALIDATION_LEVELS = [
    { value: 0, label: t('policyRedaction.validationLevels.level0'), shortLabel: 'V0', color: '#9ca3af' },
    { value: 1, label: t('policyRedaction.validationLevels.level1'), shortLabel: 'V1', color: 'var(--accent-info, #3b82f6)' },
    { value: 2, label: t('policyRedaction.validationLevels.level2'), shortLabel: 'V2', color: 'var(--accent-warning)' },
    { value: 3, label: t('policyRedaction.validationLevels.level3'), shortLabel: 'V3', color: '#8b5cf6' },
  ];

  const rulesByObjectType = useMemo(() => {
    const grouped = {};
    objectTypeOptions.forEach(opt => {
      grouped[opt.value] = rules.filter(r => r.objectType === opt.value);
    });
    return grouped;
  }, [rules, objectTypeOptions]);

  const filteredRules = useMemo(() => {
    const currentRules = rulesByObjectType[expandedObjectType] || [];
    if (!searchQuery.trim()) return currentRules;
    const q = searchQuery.toLowerCase();
    return currentRules.filter(r =>
      (r.fieldDisplayName || '').toLowerCase().includes(q) ||
      (r.fieldPath || '').toLowerCase().includes(q) ||
      (r.description || '').toLowerCase().includes(q)
    );
  }, [rulesByObjectType, expandedObjectType, searchQuery]);

  const fieldsForObjectType = useMemo(() => {
    return fieldDefinitions.filter(
      f => f.objectType === expandedObjectType || f.objectType === 'ALL'
    );
  }, [fieldDefinitions, expandedObjectType]);

  const handleAddRule = (rule) => {
    const newRules = [...rules, { ...rule, ruleOrder: rules.length, isEnabled: true }];
    onChange(newRules);
    setShowAddModal(false);
  };

  const handleUpdateRule = (index, updates) => {
    const newRules = [...rules];
    newRules[index] = { ...newRules[index], ...updates };
    onChange(newRules);
  };

  const handleDeleteRule = (index) => {
    onChange(rules.filter((_, i) => i !== index));
  };

  const handleToggleRule = (index) => {
    const newRules = [...rules];
    newRules[index] = { ...newRules[index], isEnabled: !newRules[index].isEnabled };
    onChange(newRules);
  };

  const getSensitivityColor = (level) => {
    return SENSITIVITY_LEVELS[level]?.color || 'var(--text-tertiary)';
  };

  return (
    <div className="redaction-rules-editor">
      <div className="redaction-header">
        <div className="redaction-title">
          <i className="fa-solid fa-shield-halved" />
          <span>{t('policyRedaction.fieldSensitivityLevels')}</span>
          <span className="rule-count">{t('policyRedaction.fieldsCount', { count: rules.length })}</span>
        </div>
        {!readOnly && (
          <button
            type="button"
            className="btn btn-sm btn-primary"
            onClick={(e) => { e.preventDefault(); e.stopPropagation(); setShowAddModal(true); }}
          >
            <i className="fa-solid fa-plus" />
            {t('policyRedaction.addField')}
          </button>
        )}
      </div>

      {/* Object Type Tabs + Search */}
      <div className="d-flex align-items-center justify-content-between gap-2 mb-2 flex-wrap">
        <div className="object-type-tabs" style={{ marginBottom: 0 }}>
          {objectTypeOptions.map(opt => {
            const count = rulesByObjectType[opt.value]?.length || 0;
            return (
              <button
                key={opt.value}
                type="button"
                className={`object-type-tab ${expandedObjectType === opt.value ? 'active' : ''}`}
                onClick={(e) => { e.preventDefault(); e.stopPropagation(); setExpandedObjectType(opt.value); }}
              >
                {opt.displayName}
                {count > 0 && <span className="tab-count">{count}</span>}
              </button>
            );
          })}
        </div>

        {(rulesByObjectType[expandedObjectType]?.length || 0) > 0 && (
          <div className="input-group input-group-sm" style={{ maxWidth: 280 }}>
            <span className="input-group-text bg-transparent border-end-0">
              <i className="fa-solid fa-search text-muted" style={{ fontSize: 12 }} />
            </span>
            <input
              type="text"
              className="form-control border-start-0 ps-0"
              placeholder={t('policyRedaction.filterFieldsPlaceholder')}
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
            />
            {searchQuery && (
              <button
                type="button"
                className="btn btn-outline-secondary border-start-0"
                onClick={() => setSearchQuery('')}
              >
                <i className="fa-solid fa-times" style={{ fontSize: 11 }} />
              </button>
            )}
          </div>
        )}
      </div>

      {searchQuery && (
        <div className="text-muted small mb-2">
          {t('policyRedaction.showingFields', { count: filteredRules.length, total: rulesByObjectType[expandedObjectType]?.length || 0 })}
        </div>
      )}

      {/* Rules List */}
      <div className="rules-list">
        {filteredRules.length === 0 ? (
          <div className="rules-empty">
            <i className={`fa-solid ${searchQuery ? 'fa-search' : 'fa-inbox'}`} />
            <p>{searchQuery
              ? t('policyRedaction.noFieldsMatching', { query: searchQuery })
              : t('policyRedaction.noFieldSensitivityRules', { type: objectTypeOptions.find(o => o.value === expandedObjectType)?.displayName })
            }</p>
            {!readOnly && !searchQuery && (
              <button type="button" className="btn btn-sm btn-secondary"
                onClick={(e) => { e.preventDefault(); e.stopPropagation(); setShowAddModal(true); }}>
                {t('policyRedaction.addField')}
              </button>
            )}
          </div>
        ) : (
          <>
            {/* Column headers */}
            <div className="rule-header">
              <div className="rule-header-field">{t('policyRedaction.columnField')}</div>
              <div className="rule-header-sens">{t('policyRedaction.columnSensitivity')}</div>
              <div className="rule-header-val">{t('policyRedaction.columnValidation')}</div>
              {!readOnly && <div className="rule-header-actions"></div>}
            </div>
          {filteredRules.map((rule) => {
            const globalIndex = rules.findIndex(r =>
              r.objectType === rule.objectType && r.fieldPath === rule.fieldPath
            );
            const sensLevel = SENSITIVITY_LEVELS[rule.sensitivityLevel] || SENSITIVITY_LEVELS[0];
            const valLevel = rule.validationLevel != null ? (VALIDATION_LEVELS[rule.validationLevel] || null) : null;
            return (
              <div
                key={`${rule.objectType}-${rule.fieldPath}`}
                className={`rule-item ${!rule.isEnabled ? 'disabled' : ''}`}
              >
                <div className="rule-col-field">
                  <span className="field-name">{rule.fieldDisplayName || rule.fieldPath}</span>
                  <span className="field-path">{rule.fieldPath}</span>
                </div>
                <div className="rule-col-sens">
                  <span className="sensitivity-badge" style={{ background: sensLevel.color, color: 'white' }}>
                    S{rule.sensitivityLevel}
                  </span>
                  {!readOnly && (
                    <select
                      className="form-select form-select-sm"
                      style={{ width: 'auto', minWidth: 130, fontSize: 12 }}
                      value={rule.sensitivityLevel ?? 0}
                      onChange={(e) => {
                        e.stopPropagation();
                        handleUpdateRule(globalIndex, { sensitivityLevel: parseInt(e.target.value) });
                      }}
                    >
                      {SENSITIVITY_LEVELS.map(l => (
                        <option key={l.value} value={l.value}>{l.label}</option>
                      ))}
                    </select>
                  )}
                </div>
                <div className="rule-col-val">
                  {valLevel ? (
                    <span className="sensitivity-badge" style={{ background: valLevel.color, color: 'white' }}>
                      {valLevel.shortLabel}
                    </span>
                  ) : (
                    <span className="sensitivity-badge" style={{ background: 'var(--bg-tertiary)', color: 'var(--text-tertiary)', border: '1px solid var(--border-primary)' }}>—</span>
                  )}
                  {!readOnly && (
                    <select
                      className="form-select form-select-sm"
                      style={{ width: 'auto', minWidth: 120, fontSize: 12 }}
                      value={rule.validationLevel ?? ''}
                      onChange={(e) => {
                        e.stopPropagation();
                        const val = e.target.value === '' ? null : parseInt(e.target.value);
                        handleUpdateRule(globalIndex, { validationLevel: val });
                      }}
                    >
                      <option value="">—</option>
                      {VALIDATION_LEVELS.map(l => (
                        <option key={l.value} value={l.value}>{l.label}</option>
                      ))}
                    </select>
                  )}
                  </div>
                {!readOnly && (
                  <div className="rule-col-actions">
                    <button type="button" className="btn btn-outline-secondary btn-xs"
                      onClick={(e) => { e.preventDefault(); e.stopPropagation(); setEditingRule({ ...rule, index: globalIndex }); }}
                      title={t('common.edit')}>
                      <i className="fa-solid fa-pen" />
                    </button>
                    <button type="button" className="btn btn-outline-secondary btn-xs"
                      onClick={(e) => { e.preventDefault(); e.stopPropagation(); handleToggleRule(globalIndex); }}
                      title={rule.isEnabled ? t('policyRedaction.disable') : t('policyRedaction.enable')}>
                      <i className={`fa-solid ${rule.isEnabled ? 'fa-toggle-on' : 'fa-toggle-off'}`} />
                    </button>
                    <button type="button" className="btn btn-outline-danger btn-xs"
                      onClick={(e) => { e.preventDefault(); e.stopPropagation(); handleDeleteRule(globalIndex); }}
                      title={t('common.delete')}>
                      <i className="fa-solid fa-trash" />
                    </button>
                  </div>
                )}
              </div>
            );
          })}
          </>
        )}
      </div>

      {/* Quick Add */}
      {!readOnly && fieldsForObjectType.length > 0 && (
        <div className="quick-add-section">
          <div className="quick-add-title">
            <i className="fa-solid fa-bolt" />
            {t('policyRedaction.quickAddCommonFields')}
          </div>
          <div className="quick-add-fields">
            {fieldsForObjectType
              .filter(f => !rules.some(r => r.fieldPath === f.fieldPath && r.objectType === expandedObjectType))
              .slice(0, 6)
              .map(field => (
                <button
                  key={field.fieldPath}
                  type="button"
                  className={`quick-add-btn ${field.sensitive ? 'sensitive' : ''}`}
                  onClick={(e) => {
                    e.preventDefault(); e.stopPropagation();
                    handleAddRule({
                      objectType: expandedObjectType,
                      fieldPath: field.fieldPath,
                      fieldDisplayName: field.displayName,
                      sensitivityLevel: field.sensitive ? 2 : 0,
                    });
                  }}
                  title={field.description}
                >
                  {field.sensitive && <i className="fa-solid fa-lock" />}
                  {field.displayName}
                </button>
              ))}
          </div>
        </div>
      )}

      {/* Add/Edit Modal */}
      {(showAddModal || editingRule) && ReactDOM.createPortal(
        <FieldSensitivityModal
          rule={editingRule}
          objectType={expandedObjectType}
          objectTypeOptions={objectTypeOptions}
          fieldDefinitions={fieldDefinitions}
          sensitivityLevels={SENSITIVITY_LEVELS}
          validationLevels={VALIDATION_LEVELS}
          onSave={(rule) => {
            if (editingRule) {
              handleUpdateRule(editingRule.index, rule);
              setEditingRule(null);
            } else {
              handleAddRule(rule);
            }
          }}
          onClose={() => { setShowAddModal(false); setEditingRule(null); }}
        />,
        document.body
      )}

      <style>{`
        .redaction-rules-editor { border: 1px solid var(--border-primary); border-radius: 8px; overflow: hidden; }
        .redaction-header { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: var(--bg-tertiary); border-bottom: 1px solid var(--border-primary); }
        .redaction-title { display: flex; align-items: center; gap: 8px; font-weight: 600; font-size: 14px; }
        .redaction-title i { color: var(--accent-primary); }
        .rule-count { font-weight: 400; font-size: 12px; color: var(--text-tertiary); background: var(--bg-secondary); padding: 2px 8px; border-radius: 10px; }
        .object-type-tabs { display: flex; gap: 2px; padding: 8px; background: var(--bg-secondary); border-bottom: 1px solid var(--border-primary); overflow-x: auto; }
        .object-type-tab { padding: 6px 12px; border: none; background: none; font-size: 12px; font-weight: 500; color: var(--text-secondary); cursor: pointer; border-radius: 4px; white-space: nowrap; display: flex; align-items: center; gap: 6px; transition: all 0.15s ease; }
        .object-type-tab:hover { background: var(--bg-tertiary); color: var(--text-primary); }
        .object-type-tab.active { background: var(--accent-primary); color: white; }
        .tab-count { font-size: 10px; padding: 1px 5px; background: rgba(255,255,255,0.2); border-radius: 8px; }
        .object-type-tab.active .tab-count { background: rgba(255,255,255,0.3); }
        .rules-list { max-height: 400px; overflow-y: auto; }
        .rules-empty { padding: 32px; text-align: center; color: var(--text-tertiary); }
        .rules-empty i { font-size: 32px; margin-bottom: 12px; opacity: 0.5; }
        .rules-empty p { margin: 0 0 12px 0; }
        .rule-header { display: flex; align-items: center; padding: 6px 16px; border-bottom: 2px solid var(--border-primary); background: var(--bg-tertiary); font-size: 11px; font-weight: 600; color: var(--text-tertiary); text-transform: uppercase; letter-spacing: 0.03em; position: sticky; top: 0; z-index: 1; }
        .rule-header-field { flex: 1; min-width: 0; }
        .rule-header-sens { width: 180px; flex-shrink: 0; text-align: center; }
        .rule-header-val { width: 160px; flex-shrink: 0; text-align: center; }
        .rule-header-actions { width: 90px; flex-shrink: 0; }
        .rule-item { display: flex; align-items: center; padding: 10px 16px; border-bottom: 1px solid var(--border-primary); transition: background 0.15s ease; }
        .rule-item:hover { background: var(--bg-tertiary); }
        .rule-item.disabled { opacity: 0.5; }
        .rule-item:last-child { border-bottom: none; }
        .rule-col-field { flex: 1; min-width: 0; display: flex; flex-direction: column; }
        .rule-col-sens { width: 180px; flex-shrink: 0; display: flex; align-items: center; gap: 6px; justify-content: center; }
        .rule-col-val { width: 160px; flex-shrink: 0; display: flex; align-items: center; gap: 6px; justify-content: center; }
        .rule-col-actions { width: 90px; flex-shrink: 0; display: flex; gap: 4px; justify-content: flex-end; }
        .field-name { font-weight: 500; font-size: 13px; color: var(--text-primary); }
        .field-path { font-size: 11px; color: var(--text-tertiary); font-family: monospace; }
        .sensitivity-badge { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 700; }
        .btn-xs { padding: 4px 6px; font-size: 12px; }
        .quick-add-section { padding: 12px 16px; background: var(--bg-tertiary); border-top: 1px solid var(--border-primary); }
        .quick-add-title { font-size: 11px; font-weight: 600; color: var(--text-tertiary); text-transform: uppercase; margin-bottom: 8px; display: flex; align-items: center; gap: 6px; }
        .quick-add-fields { display: flex; flex-wrap: wrap; gap: 6px; }
        .quick-add-btn { padding: 4px 10px; font-size: 11px; border: 1px solid var(--border-primary); background: var(--bg-secondary); border-radius: 4px; cursor: pointer; display: flex; align-items: center; gap: 4px; transition: all 0.15s ease; }
        .quick-add-btn:hover { border-color: var(--accent-primary); background: var(--bg-primary); }
        .quick-add-btn.sensitive { border-color: var(--accent-warning); }
        .quick-add-btn.sensitive i { color: var(--accent-warning); font-size: 10px; }
      `}</style>
    </div>
  );
};

// Modal for adding/editing a field sensitivity entry
const FieldSensitivityModal = ({
  rule, objectType, objectTypeOptions, fieldDefinitions, sensitivityLevels, validationLevels, onSave, onClose,
}) => {
  const { t } = useT();
  const [formData, setFormData] = useState({
    objectType: rule?.objectType || objectType || 'ALL',
    fieldPath: rule?.fieldPath || '',
    fieldDisplayName: rule?.fieldDisplayName || '',
    sensitivityLevel: rule?.sensitivityLevel ?? 0,
    validationLevel: rule?.validationLevel ?? '',
    description: rule?.description || '',
    isEnabled: rule?.isEnabled ?? true,
  });

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : type === 'number' ? parseInt(value, 10) : value,
    }));
  };

  const handleFieldSelect = (field) => {
    setFormData(prev => ({
      ...prev,
      fieldPath: field.fieldPath,
      fieldDisplayName: field.displayName,
      sensitivityLevel: field.sensitive ? 2 : 0,
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault(); e.stopPropagation();
    const data = { ...formData, validationLevel: formData.validationLevel === '' ? null : parseInt(formData.validationLevel) };
    onSave(data);
  };

  const relevantFields = fieldDefinitions.filter(
    f => f.objectType === formData.objectType || f.objectType === 'ALL'
  );

  return (
    <div className="redaction-modal-backdrop" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="redaction-modal-container" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title">
            <i className={`fa-solid ${rule ? 'fa-pen' : 'fa-plus'}`} />
            {rule ? t('policyRedaction.editFieldSensitivity') : t('policyRedaction.addFieldSensitivity')}
          </h3>
          <button type="button" className="btn-close" onClick={onClose}></button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            <div className="mb-3">
              <label className="form-label">{t('policyRedaction.objectTypeLabel')}</label>
              <select name="objectType" className="form-select" value={formData.objectType} onChange={handleChange}>
                {objectTypeOptions.map(opt => (
                  <option key={opt.value} value={opt.value}>{opt.displayName}</option>
                ))}
              </select>
            </div>

            {/* Preset field picker — populates path and display name but doesn't lock them */}
            {relevantFields.length > 0 && (
              <div className="mb-3">
                <GroupedFieldSelect
                  label={t('policyRedaction.presetFields')}
                  items={relevantFields.map(f => ({
                    value: f.fieldPath,
                    displayName: f.displayName,
                    category: f.category || t('policyRedaction.otherCategory'),
                    sensitive: f.sensitive,
                    description: f.description,
                  }))}
                  value={relevantFields.some(f => f.fieldPath === formData.fieldPath) ? formData.fieldPath : ''}
                  onChange={(item) => {
                    const field = relevantFields.find(f => f.fieldPath === item.value);
                    if (field) handleFieldSelect(field);
                  }}
                  placeholder={t('policyRedaction.presetPlaceholder')}
                  allowCustom
                />
              </div>
            )}

            <div className="mb-3">
              <label className="form-label">{t('policyRedaction.fieldPathLabel')}</label>
              <input type="text" name="fieldPath" className="form-control"
                value={formData.fieldPath}
                onChange={handleChange} placeholder={t('policyRedaction.fieldPathPlaceholder')}
                required />
              <p className="form-hint">{t('policyRedaction.fieldPathHint')}</p>
            </div>

            <div className="mb-3">
              <label className="form-label">{t('policyRedaction.displayNameLabel')}</label>
              <input type="text" name="fieldDisplayName" className="form-control"
                value={formData.fieldDisplayName} onChange={handleChange}
                placeholder={t('policyRedaction.displayNamePlaceholder')} />
            </div>

            <div className="mb-3">
              <label className="form-label">{t('policyRedaction.sensitivityLevelLabel')}</label>
              <select name="sensitivityLevel" className="form-select"
                value={formData.sensitivityLevel} onChange={(e) => setFormData(prev => ({ ...prev, sensitivityLevel: parseInt(e.target.value) }))}>
                {sensitivityLevels.map(l => (
                  <option key={l.value} value={l.value}>{l.label}</option>
                ))}
              </select>
              <p className="form-hint" style={{ marginTop: 4 }}>
                {t('policyRedaction.sensitivityLevelHint')}
              </p>
            </div>

            <div className="mb-3">
              <label className="form-label">{t('policyRedaction.validationLevelLabel')}</label>
              <select name="validationLevel" className="form-select"
                value={formData.validationLevel} onChange={(e) => setFormData(prev => ({ ...prev, validationLevel: e.target.value }))}>
                <option value="">{t('policyRedaction.notSpecified')}</option>
                {(validationLevels || []).map(l => (
                  <option key={l.value} value={l.value}>{l.label}</option>
                ))}
              </select>
              <p className="form-hint" style={{ marginTop: 4 }}>
                {t('policyRedaction.validationLevelHint')}
              </p>
            </div>

            <div className="mb-3">
              <div className="form-check">
                <input type="checkbox" className="form-check-input" name="isEnabled" checked={formData.isEnabled} onChange={handleChange} />
                <label className="form-check-label">{t('policyRedaction.ruleIsEnabled')}</label>
              </div>
            </div>
          </div>

          <div className="modal-footer">
            <button type="button" className="btn btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
            <button type="submit" className="btn btn-primary">
              <i className="fa-solid fa-check" />
              {rule ? t('common.update') : t('common.add')}
            </button>
          </div>
        </form>
      </div>

      <style>{`
        .redaction-modal-backdrop { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 1100; padding: 24px; }
        .redaction-modal-container { background: var(--bg-primary, #fff); border-radius: 12px; box-shadow: 0 20px 60px rgba(0,0,0,0.4); width: 100%; max-width: 640px; max-height: calc(100vh - 48px); display: flex; flex-direction: column; overflow: hidden; }
        .redaction-modal-container .modal-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid var(--border-primary); flex-shrink: 0; }
        .redaction-modal-container .modal-title { font-size: 16px; font-weight: 600; color: var(--text-primary); margin: 0; display: flex; align-items: center; gap: 8px; }
        .redaction-modal-container .modal-body { padding: 20px; overflow-y: auto; flex: 1; }
        .modal-footer { display: flex; justify-content: flex-end; gap: 12px; padding: 16px 20px; border-top: 1px solid var(--border-primary); }
        .modal-footer .btn { display: flex; align-items: center; gap: 6px; }
        .form-hint { font-size: 12px; color: var(--text-tertiary); }
      `}</style>
    </div>
  );
};

export default PolicyRedactionRulesEditor;
