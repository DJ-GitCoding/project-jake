/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import { useT } from '../i18n';

/**
 * Groups RDAP parameters into logical categories for display.
 * `label`/`description` are i18n key suffixes resolved via t() at render time;
 * `key` values are logic identifiers (used to index the values map) and are never translated.
 */
const PARAMETER_GROUPS = {
  domain: {
    fields: [
      { key: 'domainHandle' },
      { key: 'domainName' },
      { key: 'domainStatus' },
      { key: 'domainPort43' },
      { key: 'domainPublicIds' },
    ]
  },
  nameserver: {
    fields: [
      { key: 'nameservers' },
      { key: 'nameserverHandle' },
      { key: 'nameserverName' },
      { key: 'nameserverIpAddresses' },
      { key: 'nameserverStatus' },
    ]
  },
  events: {
    fields: [
      { key: 'events' },
      { key: 'eventRegistration' },
      { key: 'eventExpiration' },
      { key: 'eventLastChanged' },
      { key: 'eventLastUpdateOfRdapDb' },
      { key: 'eventTransfer' },
    ]
  },
  registrant: {
    sensitive: true,
    fields: [
      { key: 'registrantEntity' },
      { key: 'registrantHandle' },
      { key: 'registrantName' },
      { key: 'registrantOrganization' },
      { key: 'registrantEmail' },
      { key: 'registrantPhone' },
      { key: 'registrantFax' },
      { key: 'registrantAddress' },
      { key: 'registrantStreet' },
      { key: 'registrantCity' },
      { key: 'registrantStateProvince' },
      { key: 'registrantPostalCode' },
      { key: 'registrantCountry' },
    ]
  },
  admin: {
    sensitive: true,
    fields: [
      { key: 'adminEntity' },
      { key: 'adminHandle' },
      { key: 'adminName' },
      { key: 'adminOrganization' },
      { key: 'adminEmail' },
      { key: 'adminPhone' },
      { key: 'adminFax' },
      { key: 'adminAddress' },
      { key: 'adminStreet' },
      { key: 'adminCity' },
      { key: 'adminStateProvince' },
      { key: 'adminPostalCode' },
      { key: 'adminCountry' },
    ]
  },
  tech: {
    sensitive: true,
    fields: [
      { key: 'techEntity' },
      { key: 'techHandle' },
      { key: 'techName' },
      { key: 'techOrganization' },
      { key: 'techEmail' },
      { key: 'techPhone' },
      { key: 'techFax' },
      { key: 'techAddress' },
      { key: 'techStreet' },
      { key: 'techCity' },
      { key: 'techStateProvince' },
      { key: 'techPostalCode' },
      { key: 'techCountry' },
    ]
  },
  billing: {
    sensitive: true,
    fields: [
      { key: 'billingEntity' },
      { key: 'billingHandle' },
      { key: 'billingName' },
      { key: 'billingOrganization' },
      { key: 'billingEmail' },
      { key: 'billingPhone' },
      { key: 'billingFax' },
      { key: 'billingAddress' },
      { key: 'billingStreet' },
      { key: 'billingCity' },
      { key: 'billingStateProvince' },
      { key: 'billingPostalCode' },
      { key: 'billingCountry' },
    ]
  },
  registrar: {
    fields: [
      { key: 'registrarEntity' },
      { key: 'registrarHandle' },
      { key: 'registrarName' },
      { key: 'registrarEmail' },
      { key: 'registrarPhone' },
      { key: 'registrarUrl' },
      { key: 'registrarAbuseContact' },
    ]
  },
  dnssec: {
    fields: [
      { key: 'dnssecData' },
      { key: 'dnssecDelegationSigned' },
      { key: 'dnssecDsData' },
      { key: 'dnssecKeyData' },
    ]
  },
  network: {
    fields: [
      { key: 'networkHandle' },
      { key: 'networkName' },
      { key: 'networkType' },
      { key: 'networkStartAddress' },
      { key: 'networkEndAddress' },
      { key: 'networkIpVersion' },
      { key: 'networkParentHandle' },
      { key: 'networkCidr' },
      { key: 'networkCountry' },
    ]
  },
  autnum: {
    fields: [
      { key: 'autnumHandle' },
      { key: 'autnumStart' },
      { key: 'autnumEnd' },
      { key: 'autnumName' },
      { key: 'autnumType' },
      { key: 'autnumCountry' },
    ]
  },
  other: {
    fields: [
      { key: 'links' },
      { key: 'notices' },
      { key: 'remarks' },
    ]
  }
};

/**
 * Resolves a group's translated label/description via i18n, falling back to
 * common.name for fields whose label text is exactly "Name" etc. Field labels
 * are looked up in agreementParams.fields.<fieldKey>, since the field.key values
 * above are unique identifiers (not display text).
 */
const FIELD_COMMON_KEYS = {
  domainName: 'common.name',
  nameserverName: 'common.name',
  registrantName: 'common.name',
  adminName: 'common.name',
  techName: 'common.name',
  billingName: 'common.name',
  registrarName: 'common.name',
  networkName: 'common.name',
  autnumName: 'common.name',
  domainStatus: 'common.status',
  nameserverStatus: 'common.status',
  networkType: 'common.type',
  autnumType: 'common.type',
};

const getFieldLabel = (t, field) => {
  const commonKey = FIELD_COMMON_KEYS[field.key];
  if (commonKey) return t(commonKey);
  return t(`agreementParams.fields.${field.key}`);
};

/**
 * Collapsible group of parameter toggles
 */
const ParameterGroup = ({
  groupKey,
  group,
  values,
  onChange,
  expanded,
  onToggleExpand,
  disabled
}) => {
  const { t } = useT();
  const enabledCount = group.fields.filter(f => values[f.key]).length;
  const totalCount = group.fields.length;
  const allEnabled = enabledCount === totalCount;
  const noneEnabled = enabledCount === 0;

  const handleToggleAll = () => {
    const newValue = !allEnabled;
    const updates = {};
    group.fields.forEach(field => {
      updates[field.key] = newValue;
    });
    onChange(updates);
  };

  return (
    <div className="rdap-param-group" style={{ 
      border: '1px solid var(--border-primary)', 
      borderRadius: '8px',
      marginBottom: '8px',
      overflow: 'hidden'
    }}>
      <div 
        style={{ 
          display: 'flex', 
          alignItems: 'center', 
          justifyContent: 'space-between',
          padding: '12px 16px',
          backgroundColor: 'var(--bg-secondary)',
          cursor: 'pointer'
        }}
        onClick={onToggleExpand}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          {expanded ? <i className="fa-solid fa-chevron-down"></i> : <i className="fa-solid fa-chevron-right"></i>}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <strong>{t(`agreementParams.groups.${group.label}.label`)}</strong>
              {group.sensitive && (
                <span className="badge bg-warning-subtle text-warning" style={{ fontSize: '10px' }}>{t('agreementParams.sensitiveBadge')}</span>
              )}
            </div>
            <div className="text-muted small">{t(`agreementParams.groups.${group.description}.description`)}</div>
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <span className={`badge ${allEnabled ? 'bg-success-subtle text-success' : noneEnabled ? 'bg-danger-subtle text-danger' : 'bg-warning-subtle text-warning'}`}>
            {enabledCount}/{totalCount}
          </span>
          <button
            className="btn btn-outline-secondary btn-sm"
            onClick={(e) => { e.stopPropagation(); handleToggleAll(); }}
            disabled={disabled}
            title={allEnabled ? t('agreementParams.disableAll') : t('agreementParams.enableAll')}
          >
            {allEnabled ? <i className="fa-solid fa-xmark"></i> : <i className="fa-solid fa-check"></i>}
          </button>
        </div>
      </div>

      {expanded && (
        <div style={{ padding: '12px 16px', backgroundColor: 'var(--bg-primary)' }}>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
            gap: '8px'
          }}>
            {group.fields.map(field => (
              <label
                key={field.key}
                className="form-check d-flex align-items-center gap-2 m-0"
                style={{
                  padding: '8px 12px',
                  backgroundColor: 'var(--bg-secondary)',
                  borderRadius: '4px',
                  cursor: disabled ? 'not-allowed' : 'pointer',
                  opacity: disabled ? 0.6 : 1
                }}
              >
                <input
                  type="checkbox"
                  className="form-check-input m-0"
                  checked={values[field.key] ?? true}
                  onChange={(e) => onChange({ [field.key]: e.target.checked })}
                  disabled={disabled}
                />
                <span className="small">{getFieldLabel(t, field)}</span>
              </label>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

/**
 * Quick preset buttons for common configurations
 */
const PresetButtons = ({ onApply, disabled }) => {
  const { t } = useT();
  const presets = [
    {
      label: t('agreementParams.presets.allEnabled.label'),
      description: t('agreementParams.presets.allEnabled.description'),
      apply: () => {
        const values = {};
        Object.values(PARAMETER_GROUPS).forEach(group => {
          group.fields.forEach(field => {
            values[field.key] = true;
          });
        });
        return values;
      }
    },
    {
      label: t('agreementParams.presets.publicOnly.label'),
      description: t('agreementParams.presets.publicOnly.description'),
      apply: () => {
        const values = {};
        Object.entries(PARAMETER_GROUPS).forEach(([key, group]) => {
          const isSensitive = ['registrant', 'admin', 'tech', 'billing'].includes(key);
          group.fields.forEach(field => {
            values[field.key] = !isSensitive;
          });
        });
        return values;
      }
    },
    {
      label: t('agreementParams.presets.registrantOnly.label'),
      description: t('agreementParams.presets.registrantOnly.description'),
      apply: () => {
        const values = {};
        Object.entries(PARAMETER_GROUPS).forEach(([key, group]) => {
          const isDisabled = ['admin', 'tech', 'billing'].includes(key);
          group.fields.forEach(field => {
            values[field.key] = !isDisabled;
          });
        });
        return values;
      }
    },
    {
      label: t('agreementParams.presets.noContacts.label'),
      description: t('agreementParams.presets.noContacts.description'),
      apply: () => {
        const values = {};
        Object.entries(PARAMETER_GROUPS).forEach(([key, group]) => {
          const isSensitive = ['registrant', 'admin', 'tech', 'billing'].includes(key);
          group.fields.forEach(field => {
            values[field.key] = !isSensitive;
          });
        });
        return values;
      }
    },
  ];

  return (
    <div style={{
      display: 'flex',
      gap: '8px',
      flexWrap: 'wrap',
      padding: '12px',
      backgroundColor: 'var(--bg-secondary)',
      borderRadius: '8px',
      marginBottom: '16px'
    }}>
      <span className="small text-muted" style={{ alignSelf: 'center', marginRight: '8px' }}>
        {t('agreementParams.quickPresets')}
      </span>
      {presets.map(preset => (
        <button
          key={preset.label}
          className="btn btn-outline-secondary btn-sm"
          onClick={() => onApply(preset.apply())}
          disabled={disabled}
          title={preset.description}
        >
          {preset.label}
        </button>
      ))}
    </div>
  );
};

/**
 * Summary view showing enabled/disabled counts by category
 */
const ParametersSummary = ({ values }) => {
  const { t } = useT();
  const summary = Object.entries(PARAMETER_GROUPS).map(([key, group]) => {
    const enabled = group.fields.filter(f => values[f.key]).length;
    const total = group.fields.length;
    return { key, label: t(`agreementParams.groups.${group.label}.label`), enabled, total, sensitive: group.sensitive };
  });

  const totalEnabled = summary.reduce((sum, g) => sum + g.enabled, 0);
  const totalFields = summary.reduce((sum, g) => sum + g.total, 0);

  return (
    <div style={{ 
      display: 'grid', 
      gridTemplateColumns: 'repeat(auto-fill, minmax(120px, 1fr))', 
      gap: '8px',
      marginBottom: '16px'
    }}>
      {summary.map(item => (
        <div 
          key={item.key}
          style={{ 
            padding: '8px 12px', 
            backgroundColor: 'var(--bg-secondary)',
            borderRadius: '6px',
            textAlign: 'center'
          }}
        >
          <div style={{ 
            fontSize: '18px', 
            fontWeight: 600,
            color: item.enabled === item.total 
              ? 'var(--accent-success)' 
              : item.enabled === 0 
                ? 'var(--accent-error)' 
                : 'var(--accent-warning)'
          }}>
            {item.enabled}/{item.total}
          </div>
          <div className="text-muted small">{item.label}</div>
        </div>
      ))}
      <div 
        style={{ 
          padding: '8px 12px', 
          backgroundColor: 'var(--accent-primary)',
          color: 'white',
          borderRadius: '6px',
          textAlign: 'center'
        }}
      >
        <div style={{ fontSize: '18px', fontWeight: 600 }}>
          {totalEnabled}/{totalFields}
        </div>
        <div style={{ opacity: 0.8, fontSize: '12px' }}>{t('agreementParams.total')}</div>
      </div>
    </div>
  );
};

/**
 * Main RDAP Parameters Editor Component
 */
const RdapParametersEditor = ({ 
  values = {}, 
  onChange, 
  disabled = false,
  showSummary = true,
  showPresets = true,
  compact = false
}) => {
  const [expandedGroups, setExpandedGroups] = useState(
    compact ? [] : ['domain', 'registrant']
  );
  const { t } = useT();

  const handleToggleExpand = (groupKey) => {
    setExpandedGroups(prev => 
      prev.includes(groupKey) 
        ? prev.filter(k => k !== groupKey)
        : [...prev, groupKey]
    );
  };

  const handleExpandAll = () => {
    setExpandedGroups(Object.keys(PARAMETER_GROUPS));
  };

  const handleCollapseAll = () => {
    setExpandedGroups([]);
  };

  const handleChange = (updates) => {
    onChange({ ...values, ...updates });
  };

  // Initialize with defaults if empty
  const effectiveValues = { ...getDefaultValues(), ...values };

  return (
    <div className="rdap-parameters-editor">
      {showSummary && <ParametersSummary values={effectiveValues} />}
      
      {showPresets && <PresetButtons onApply={handleChange} disabled={disabled} />}
      
      <div style={{ 
        display: 'flex', 
        justifyContent: 'flex-end', 
        gap: '8px', 
        marginBottom: '12px' 
      }}>
        <button
          className="btn btn-outline-secondary btn-sm"
          onClick={handleExpandAll}
        >
          {t('agreementParams.expandAll')}
        </button>
        <button
          className="btn btn-outline-secondary btn-sm"
          onClick={handleCollapseAll}
        >
          {t('agreementParams.collapseAll')}
        </button>
      </div>

      {Object.entries(PARAMETER_GROUPS).map(([key, group]) => (
        <ParameterGroup
          key={key}
          groupKey={key}
          group={group}
          values={effectiveValues}
          onChange={handleChange}
          expanded={expandedGroups.includes(key)}
          onToggleExpand={() => handleToggleExpand(key)}
          disabled={disabled}
        />
      ))}
    </div>
  );
};

/**
 * Get default values (all true)
 */
export const getDefaultValues = () => {
  const defaults = {};
  Object.values(PARAMETER_GROUPS).forEach(group => {
    group.fields.forEach(field => {
      defaults[field.key] = true;
    });
  });
  return defaults;
};

/**
 * Get public-only values (contacts disabled)
 */
export const getPublicOnlyValues = () => {
  const values = {};
  Object.entries(PARAMETER_GROUPS).forEach(([key, group]) => {
    const isSensitive = ['registrant', 'admin', 'tech', 'billing'].includes(key);
    group.fields.forEach(field => {
      values[field.key] = !isSensitive;
    });
  });
  return values;
};

export { PARAMETER_GROUPS };
export default RdapParametersEditor;