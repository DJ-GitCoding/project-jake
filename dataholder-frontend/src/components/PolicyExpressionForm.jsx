/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useMemo } from 'react';
import PolicyRedactionRulesEditor from './PolicyRedactionRulesEditor';
import GroupedFieldSelect from './GroupedFieldSelect';
import { useT } from '../i18n';

// ==================== Scope Conditions Builder ====================
/*
 * NOTE: RDAP_FIELDS / OPERATORS below are plain module-level data (not inside a
 * component), so they cannot call the translation hook. Their `label` values are
 * translated at render time via the *_LABEL_KEY maps further down, using each
 * entry's `value` as the lookup key. The `group` values are also translated at
 * render time via GROUP_LABEL_KEYS. The literal English label/group strings here
 * are left in place as fallbacks/source data only and are not rendered directly.
 */

const RDAP_FIELDS = [
  // Domain
  { value: 'name', label: 'Domain Name', group: 'Domain' },
  { value: 'ldhName', label: 'LDH Name', group: 'Domain' },
  { value: 'unicodeName', label: 'Unicode Name', group: 'Domain' },
  { value: 'handle', label: 'Handle', group: 'Identity' },
  { value: 'objectClassName', label: 'Object Class', group: 'Identity' },
  { value: 'type', label: 'Type', group: 'Identity' },
  { value: 'status', label: 'Status', group: 'Status' },
  // Scope (from imported policies)
  { value: 'person', label: 'Person Scope', group: 'Scope' },
  { value: 'protection', label: 'Protection', group: 'Scope' },
  { value: 'nexus', label: 'Nexus', group: 'Scope' },
  { value: 'personal', label: 'Personal', group: 'Scope' },
  { value: 'public_suffix', label: 'Public Suffix', group: 'Scope' },
  // Registrant contact
  { value: 'registrant.name', label: 'Registrant Name', group: 'Registrant' },
  { value: 'registrant.organization', label: 'Registrant Organization', group: 'Registrant' },
  { value: 'registrant.email', label: 'Registrant Email', group: 'Registrant' },
  { value: 'registrant.phone', label: 'Registrant Phone', group: 'Registrant' },
  { value: 'registrant.country', label: 'Registrant Country', group: 'Registrant' },
  { value: 'registrant.city', label: 'Registrant City', group: 'Registrant' },
  { value: 'registrant.state', label: 'Registrant State/Province', group: 'Registrant' },
  { value: 'registrant.postal_code', label: 'Registrant Postal Code', group: 'Registrant' },
  // Admin contact
  { value: 'admin.name', label: 'Admin Contact Name', group: 'Admin' },
  { value: 'admin.email', label: 'Admin Contact Email', group: 'Admin' },
  { value: 'admin.phone', label: 'Admin Contact Phone', group: 'Admin' },
  { value: 'admin.organization', label: 'Admin Organization', group: 'Admin' },
  // Tech contact
  { value: 'tech.name', label: 'Tech Contact Name', group: 'Tech' },
  { value: 'tech.email', label: 'Tech Contact Email', group: 'Tech' },
  { value: 'tech.phone', label: 'Tech Contact Phone', group: 'Tech' },
  { value: 'tech.organization', label: 'Tech Organization', group: 'Tech' },
  // Billing contact
  { value: 'billing.name', label: 'Billing Contact Name', group: 'Billing' },
  { value: 'billing.email', label: 'Billing Contact Email', group: 'Billing' },
  { value: 'billing.organization', label: 'Billing Organization', group: 'Billing' },
  // General contact
  { value: 'organization', label: 'Organization', group: 'Contact' },
  { value: 'country', label: 'Country', group: 'Location' },
  // Network
  { value: 'ipVersion', label: 'IP Version', group: 'Network' },
  { value: 'startAddress', label: 'Start Address', group: 'Network' },
  { value: 'endAddress', label: 'End Address', group: 'Network' },
  { value: 'rir', label: 'RIR', group: 'Network' },
  // Other
  { value: 'port43', label: 'WHOIS Server', group: 'Other' },
  { value: 'secureDNS.delegationSigned', label: 'DNSSEC Signed', group: 'Other' },
];

// Maps each RDAP_FIELDS `value` to its translation key.
const FIELD_LABEL_KEYS = {
  name: 'policy.form.fields.name',
  ldhName: 'policy.form.fields.ldhName',
  unicodeName: 'policy.form.fields.unicodeName',
  handle: 'policy.form.fields.handle',
  objectClassName: 'policy.form.fields.objectClassName',
  type: 'policy.form.fields.type',
  status: 'policy.form.fields.status',
  person: 'policy.form.fields.person',
  protection: 'policy.form.fields.protection',
  nexus: 'policy.form.fields.nexus',
  personal: 'policy.form.fields.personal',
  public_suffix: 'policy.form.fields.publicSuffix',
  'registrant.name': 'policy.form.fields.registrantName',
  'registrant.organization': 'policy.form.fields.registrantOrganization',
  'registrant.email': 'policy.form.fields.registrantEmail',
  'registrant.phone': 'policy.form.fields.registrantPhone',
  'registrant.country': 'policy.form.fields.registrantCountry',
  'registrant.city': 'policy.form.fields.registrantCity',
  'registrant.state': 'policy.form.fields.registrantState',
  'registrant.postal_code': 'policy.form.fields.registrantPostalCode',
  'admin.name': 'policy.form.fields.adminName',
  'admin.email': 'policy.form.fields.adminEmail',
  'admin.phone': 'policy.form.fields.adminPhone',
  'admin.organization': 'policy.form.fields.adminOrganization',
  'tech.name': 'policy.form.fields.techName',
  'tech.email': 'policy.form.fields.techEmail',
  'tech.phone': 'policy.form.fields.techPhone',
  'tech.organization': 'policy.form.fields.techOrganization',
  'billing.name': 'policy.form.fields.billingName',
  'billing.email': 'policy.form.fields.billingEmail',
  'billing.organization': 'policy.form.fields.billingOrganization',
  organization: 'policy.form.fields.organization',
  country: 'policy.form.fields.country',
  ipVersion: 'policy.form.fields.ipVersion',
  startAddress: 'policy.form.fields.startAddress',
  endAddress: 'policy.form.fields.endAddress',
  rir: 'policy.form.fields.rir',
  port43: 'policy.form.fields.port43',
  'secureDNS.delegationSigned': 'policy.form.fields.dnssecSigned',
};

// Maps each RDAP_FIELDS `group` to its translation key.
const GROUP_LABEL_KEYS = {
  Domain: 'policy.form.groups.domain',
  Identity: 'policy.form.groups.identity',
  Status: 'policy.form.groups.status',
  Scope: 'policy.form.groups.scope',
  Registrant: 'policy.form.groups.registrant',
  Admin: 'policy.form.groups.admin',
  Tech: 'policy.form.groups.tech',
  Billing: 'policy.form.groups.billing',
  Contact: 'policy.form.groups.contact',
  Location: 'policy.form.groups.location',
  Network: 'policy.form.groups.network',
  Other: 'policy.form.groups.other',
};

const OPERATORS = [
  { value: 'equals', label: 'equals', needsValue: true },
  { value: 'not_equals', label: 'does not equal', needsValue: true },
  { value: 'contains', label: 'contains', needsValue: true },
  { value: 'not_contains', label: 'does not contain', needsValue: true },
  { value: 'starts_with', label: 'starts with', needsValue: true },
  { value: 'ends_with', label: 'ends with', needsValue: true },
  { value: 'is_empty', label: 'is empty', needsValue: false },
  { value: 'is_not_empty', label: 'is not empty', needsValue: false },
  { value: 'in_list', label: 'is one of', needsValue: true },
  { value: 'not_in_list', label: 'is not one of', needsValue: true },
  { value: 'matches_regex', label: 'matches regex', needsValue: true },
];

// Maps each OPERATORS `value` to its translation key.
const OPERATOR_LABEL_KEYS = {
  equals: 'policy.form.operators.equals',
  not_equals: 'policy.form.operators.notEquals',
  contains: 'policy.form.operators.contains',
  not_contains: 'policy.form.operators.notContains',
  starts_with: 'policy.form.operators.startsWith',
  ends_with: 'policy.form.operators.endsWith',
  is_empty: 'policy.form.operators.isEmpty',
  is_not_empty: 'policy.form.operators.isNotEmpty',
  in_list: 'policy.form.operators.inList',
  not_in_list: 'policy.form.operators.notInList',
  matches_regex: 'policy.form.operators.matchesRegex',
};

const EMPTY_CONDITION = { field: '', operator: 'equals', value: '' };

const ScopeConditionsBuilder = ({ conditions = [], onChange }) => {
  const { t } = useT();
  const addCondition = () => onChange([...conditions, { ...EMPTY_CONDITION }]);

  const updateCondition = (index, updates) => {
    const next = conditions.map((c, i) => i === index ? { ...c, ...updates } : c);
    // Clear value when switching to an operator that doesn't need one
    if (updates.operator) {
      const op = OPERATORS.find(o => o.value === updates.operator);
      if (op && !op.needsValue) next[index].value = '';
    }
    onChange(next);
  };

  const removeCondition = (index) => onChange(conditions.filter((_, i) => i !== index));

  const duplicateCondition = (index) => {
    const copy = [...conditions];
    copy.splice(index + 1, 0, { ...conditions[index] });
    onChange(copy);
  };

  // Group fields for the dropdown
  const groupedFields = useMemo(() => {
    const groups = {};
    RDAP_FIELDS.forEach(f => {
      if (!groups[f.group]) groups[f.group] = [];
      groups[f.group].push(f);
    });
    return groups;
  }, []);

  return (
    <div>
      {conditions.length === 0 ? (
        <div className="scope-empty">
          <i className="fa-solid fa-filter" />
          <div>
            <strong>{t('policy.form.noScopeConditions')}</strong>
            <p>{t('policy.form.noScopeConditionsHint')}</p>
          </div>
          <button type="button" className="btn btn-primary btn-sm" onClick={addCondition}>
            <i className="fa-solid fa-plus" /> {t('policy.form.addCondition')}
          </button>
        </div>
      ) : (
        <div className="scope-conditions-list">
          {conditions.map((condition, index) => {
            const selectedOp = OPERATORS.find(o => o.value === condition.operator);
            const needsValue = selectedOp ? selectedOp.needsValue : true;
            const isListOp = condition.operator === 'in_list' || condition.operator === 'not_in_list';

            return (
              <div key={index} className="scope-condition-row">
                {index > 0 && (
                  <div className="scope-condition-connector">
                    <span className="connector-label">{t('policy.form.and')}</span>
                    <div className="connector-line" />
                  </div>
                )}
                <div className="scope-condition-card">
                  <div className="scope-condition-fields">
                    {/* Field selector */}
                    <div className="scope-field">
                      <label className="scope-label">{t('policy.form.when')}</label>
                      <GroupedFieldSelect
                        items={RDAP_FIELDS.map(f => ({
                          value: f.value,
                          displayName: FIELD_LABEL_KEYS[f.value] ? t(FIELD_LABEL_KEYS[f.value]) : f.label,
                          category: GROUP_LABEL_KEYS[f.group] ? t(GROUP_LABEL_KEYS[f.group]) : f.group,
                        }))}
                        value={condition.field}
                        onChange={(item) => updateCondition(index, { field: item.value })}
                        placeholder={t('policy.form.selectFieldPlaceholder')}
                        allowCustom
                      />
                    </div>

                    {/* Custom field input */}
                    {condition.field === '__custom__' && (
                      <div className="scope-field scope-field-custom">
                        <label className="scope-label">{t('policy.form.fieldPath')}</label>
                        <input
                          type="text"
                          className="form-control form-control-sm"
                          value={condition.customField || ''}
                          onChange={e => updateCondition(index, { customField: e.target.value })}
                          placeholder={t('policy.form.fieldPathPlaceholder')}
                        />
                      </div>
                    )}

                    {/* Operator selector */}
                    <div className="scope-field">
                      <label className="scope-label">{t('policy.form.condition')}</label>
                      <select
                        className="form-select form-select-sm"
                        value={condition.operator}
                        onChange={e => updateCondition(index, { operator: e.target.value })}
                      >
                        {OPERATORS.map(op => (
                          <option key={op.value} value={op.value}>{t(OPERATOR_LABEL_KEYS[op.value]) || op.label}</option>
                        ))}
                      </select>
                    </div>

                    {/* Value input */}
                    {needsValue && (
                      <div className="scope-field scope-field-value">
                        <label className="scope-label">{isListOp ? t('policy.form.valuesCommaSeparated') : t('policy.form.value')}</label>
                        <input
                          type="text"
                          className="form-control form-control-sm"
                          value={condition.value}
                          onChange={e => updateCondition(index, { value: e.target.value })}
                          placeholder={isListOp ? t('policy.form.valueListPlaceholder') : t('policy.form.valuePlaceholder')}
                        />
                      </div>
                    )}
                  </div>

                  {/* Row actions */}
                  <div className="scope-condition-actions">
                    <button type="button" className="btn-icon" onClick={() => duplicateCondition(index)} title={t('policy.form.duplicate')}>
                      <i className="fa-solid fa-copy" />
                    </button>
                    <button type="button" className="btn-icon btn-icon-danger" onClick={() => removeCondition(index)} title={t('policy.form.remove')}>
                      <i className="fa-solid fa-trash" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
          <button type="button" className="btn btn-secondary btn-sm scope-add-btn" onClick={addCondition}>
            <i className="fa-solid fa-plus" /> {t('policy.form.addCondition')}
          </button>
        </div>
      )}

      <style>{`
        .scope-empty {
          display: flex;
          align-items: center;
          gap: 16px;
          padding: 20px;
          background: var(--bg-tertiary, #f8f9fa);
          border: 1px dashed var(--border-primary, #dee2e6);
          border-radius: 10px;
        }
        .scope-empty i { font-size: 24px; color: var(--text-tertiary); flex-shrink: 0; }
        .scope-empty strong { display: block; font-size: 13px; margin-bottom: 2px; }
        .scope-empty p { margin: 0; font-size: 12px; color: var(--text-tertiary); }
        .scope-empty .btn { flex-shrink: 0; margin-left: auto; }

        .scope-conditions-list { display: flex; flex-direction: column; gap: 0; }

        .scope-condition-row { position: relative; }

        .scope-condition-connector {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 6px 0 6px 12px;
        }
        .connector-label {
          font-size: 10px;
          font-weight: 700;
          color: var(--accent-primary, #3b82f6);
          background: var(--accent-primary-alpha, rgba(59, 130, 246, 0.1));
          padding: 2px 8px;
          border-radius: 4px;
          letter-spacing: 0.5px;
          flex-shrink: 0;
        }
        .connector-line {
          flex: 1;
          height: 1px;
          background: var(--border-primary, #dee2e6);
        }

        .scope-condition-card {
          display: flex;
          align-items: flex-start;
          gap: 8px;
          padding: 12px;
          background: var(--bg-secondary, #fff);
          border: 1px solid var(--border-primary, #dee2e6);
          border-radius: 10px;
          transition: border-color 0.15s ease;
        }
        .scope-condition-card:hover { border-color: var(--accent-primary, #3b82f6); }

        .scope-condition-fields {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;
          flex: 1;
          min-width: 0;
        }

        .scope-field { min-width: 140px; flex: 1; }
        .scope-field-custom { flex: 1.5; }
        .scope-field-value { flex: 1.5; }

        .scope-label {
          display: block;
          font-size: 10px;
          font-weight: 600;
          color: var(--text-tertiary);
          text-transform: uppercase;
          letter-spacing: 0.5px;
          margin-bottom: 3px;
        }

        .scope-condition-actions {
          display: flex;
          gap: 4px;
          flex-shrink: 0;
          padding-top: 18px;
        }

        .btn-icon {
          width: 28px;
          height: 28px;
          border: none;
          background: transparent;
          color: var(--text-tertiary);
          border-radius: 6px;
          cursor: pointer;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 12px;
          transition: all 0.15s ease;
        }
        .btn-icon:hover { background: var(--bg-tertiary, #f0f0f0); color: var(--text-primary); }
        .btn-icon-danger:hover { background: rgba(239, 68, 68, 0.1); color: var(--accent-error, #ef4444); }

        .scope-add-btn {
          align-self: flex-start;
          margin-top: 8px;
          display: flex;
          align-items: center;
          gap: 6px;
        }

        @media (max-width: 640px) {
          .scope-condition-fields { flex-direction: column; }
          .scope-field { min-width: 100%; }
        }
      `}</style>
    </div>
  );
};

// ==================== Main Form ====================

const PolicyExpressionForm = ({ 
  formData, 
  enumValues, 
  onChange, 
  onSubmit, 
  saving,
  isNew = false,
  existingPolicies = [],
  editingPolicyId = null,
}) => {
  const { t } = useT();

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    onChange({
      ...formData,
      [name]: type === 'checkbox' ? checked :
              type === 'number' ? parseInt(value, 10) : value,
    });
  };

  // Parse scope conditions for the builder
  const scopeConditions = useMemo(() => {
    if (!formData.scopeConditions) return [];
    if (Array.isArray(formData.scopeConditions)) return formData.scopeConditions;
    try { return JSON.parse(formData.scopeConditions); } catch { return []; }
  }, [formData.scopeConditions]);

  const handleScopeChange = (conditions) => {
    onChange({ ...formData, scopeConditions: conditions });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit(e);
  };

  const noteLength = (formData.noteToRequestor || '').length;

  return (
    <form onSubmit={handleSubmit}>
      <div className="form-grid">
        {/* Basic Info */}
        <div className="form-section">
          <h4 className="form-section-title">
            <i className="fa-solid fa-info-circle" />
            {t('policy.form.basicInformation')}
          </h4>

          <div className="mb-3">
            <label className="form-label">{t('policy.form.policyNameRequired')}</label>
            <input
              type="text"
              name="name"
              className="form-control"
              value={formData.name}
              onChange={handleChange}
              placeholder={t('policy.form.policyNamePlaceholder')}
              required
            />
          </div>

          <div className="mb-3">
            <label className="form-label">{t('common.description')}</label>
            <textarea
              name="description"
              className="form-control"
              value={formData.description}
              onChange={handleChange}
              placeholder={t('policy.form.descriptionPlaceholder')}
              rows={3}
            />
          </div>
        </div>

        {/* Scope Conditions */}
        <div className="form-section">
          <h4 className="form-section-title">
            <i className="fa-solid fa-crosshairs" />
            {t('policy.form.scopeConditionsTitle')}
          </h4>
          <p className="form-hint" style={{ marginBottom: '16px' }}>
            {t('policy.form.scopeConditionsHint')}
          </p>

          <ScopeConditionsBuilder
            conditions={scopeConditions}
            onChange={handleScopeChange}
          />
        </div>

        {/* Note to Requestor */}
        <div className="form-section">
          <h4 className="form-section-title">
            <i className="fa-solid fa-message" />
            {t('policy.form.noteToRequestorTitle')}
          </h4>
          <p className="form-hint" style={{ marginBottom: '16px' }}>
            {t('policy.form.noteToRequestorHint')}
          </p>

          <div className="mb-3">
            <textarea
              name="noteToRequestor"
              className="form-control"
              value={formData.noteToRequestor || ''}
              onChange={handleChange}
              placeholder={t('policy.form.noteToRequestorPlaceholder')}
              rows={3}
              maxLength={255}
            />
            <div className="form-hint" style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span>{t('policy.form.noteToRequestorAppearsHint')}</span>
              <span style={{ color: noteLength > 240 ? 'var(--accent-error)' : 'var(--text-tertiary)' }}>
                {noteLength}/255
              </span>
            </div>
          </div>
        </div>

        {/* Status Flags */}
        <div className="form-section">
          <h4 className="form-section-title">
            <i className="fa-solid fa-toggle-on" />
            {t('policy.form.policyStatusTitle')}
          </h4>

          <div className="form-row">
            <div className="mb-3">
              <div className="form-check">
                <input
                  type="checkbox"
                  className="form-check-input"
                  name="isActive"
                  checked={formData.isActive}
                  onChange={handleChange}
                />
                <label className="form-check-label">{t('common.active')}</label>
              </div>
              <p className="form-hint" style={{ marginLeft: '24px' }}>
                {t('policy.form.activeHint')}
              </p>
            </div>

            <div className="mb-3">
              <div className="form-check">
                <input
                  type="checkbox"
                  className="form-check-input"
                  name="isDefault"
                  checked={formData.isDefault}
                  onChange={handleChange}
                />
                <label className="form-check-label">{t('policy.form.defaultPolicy')}</label>
              </div>
              <p className="form-hint" style={{ marginLeft: '24px' }}>
                {t('policy.form.defaultPolicyHint')}
              </p>
            </div>
          </div>
        </div>

        {/* Per-Field Sensitivity Levels */}
        <div className="form-section">
          <h4 className="form-section-title">
            <i className="fa-solid fa-shield-halved" />
            {t('policy.form.fieldSensitivityLevels')}
          </h4>
          <p className="form-hint" style={{ marginBottom: '16px' }}>
            {t('policy.form.fieldSensitivityLevelsHint')}
          </p>

          <PolicyRedactionRulesEditor
            rules={formData.redactionRules || []}
            onChange={(rules) => onChange({ ...formData, redactionRules: rules })}
            enumValues={enumValues}
          />
        </div>
      </div>

      <div className="form-actions">
        <button
          type="submit"
          className="btn btn-primary"
          disabled={saving}
        >
          <i className={`fa-solid ${saving ? 'fa-spinner fa-spin' : 'fa-check'}`} />
          {saving ? t('common.saving') : isNew ? t('policy.form.createPolicy') : t('common.saveChanges')}
        </button>
      </div>

      <style>{`
        .form-grid { display: flex; flex-direction: column; gap: 24px; }
        .form-section { padding-bottom: 20px; border-bottom: 1px solid var(--border-primary); }
        .form-section:last-of-type { border-bottom: none; padding-bottom: 0; }
        .form-section-title {
          font-size: 14px; font-weight: 600; color: var(--text-primary);
          margin: 0 0 16px 0; display: flex; align-items: center; gap: 8px;
        }
        .form-section-title i { color: var(--accent-primary); }
        .form-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; }
        .form-hint { font-size: 12px; color: var(--text-tertiary); margin-top: 4px; }
        .form-actions {
          margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--border-primary);
          display: flex; justify-content: flex-end; gap: 12px;
        }
        .form-actions .btn { display: flex; align-items: center; gap: 8px; }
      `}</style>

    </form>
  );
};

export default PolicyExpressionForm;
