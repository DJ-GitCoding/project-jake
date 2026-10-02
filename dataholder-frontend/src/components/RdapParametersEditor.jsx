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
 * Groups RDAP parameters into logical categories for display
 */
const PARAMETER_GROUPS = {
  domain: {
    label: 'Domain',
    description: 'Basic domain object fields',
    fields: [
      { key: 'domainHandle', label: 'Handle' },
      { key: 'domainName', label: 'Name' },
      { key: 'domainStatus', label: 'Status' },
      { key: 'domainPort43', label: 'Port 43 (Whois)' },
      { key: 'domainPublicIds', label: 'Public IDs' },
    ]
  },
  nameserver: {
    label: 'Nameservers',
    description: 'Nameserver information',
    fields: [
      { key: 'nameservers', label: 'Nameservers (Master)' },
      { key: 'nameserverHandle', label: 'Handle' },
      { key: 'nameserverName', label: 'Name' },
      { key: 'nameserverIpAddresses', label: 'IP Addresses' },
      { key: 'nameserverStatus', label: 'Status' },
    ]
  },
  events: {
    label: 'Events',
    description: 'Domain lifecycle events',
    fields: [
      { key: 'events', label: 'Events (Master)' },
      { key: 'eventRegistration', label: 'Registration Date' },
      { key: 'eventExpiration', label: 'Expiration Date' },
      { key: 'eventLastChanged', label: 'Last Changed' },
      { key: 'eventLastUpdateOfRdapDb', label: 'Last RDAP DB Update' },
      { key: 'eventTransfer', label: 'Transfer Date' },
    ]
  },
  registrant: {
    label: 'Registrant Contact',
    description: 'Domain registrant information',
    sensitive: true,
    fields: [
      { key: 'registrantEntity', label: 'Entity (Master)' },
      { key: 'registrantHandle', label: 'Handle' },
      { key: 'registrantName', label: 'Name' },
      { key: 'registrantOrganization', label: 'Organization' },
      { key: 'registrantEmail', label: 'Email' },
      { key: 'registrantPhone', label: 'Phone' },
      { key: 'registrantFax', label: 'Fax' },
      { key: 'registrantAddress', label: 'Address (Master)' },
      { key: 'registrantStreet', label: 'Street' },
      { key: 'registrantCity', label: 'City' },
      { key: 'registrantStateProvince', label: 'State/Province' },
      { key: 'registrantPostalCode', label: 'Postal Code' },
      { key: 'registrantCountry', label: 'Country' },
    ]
  },
  admin: {
    label: 'Admin Contact',
    description: 'Administrative contact information',
    sensitive: true,
    fields: [
      { key: 'adminEntity', label: 'Entity (Master)' },
      { key: 'adminHandle', label: 'Handle' },
      { key: 'adminName', label: 'Name' },
      { key: 'adminOrganization', label: 'Organization' },
      { key: 'adminEmail', label: 'Email' },
      { key: 'adminPhone', label: 'Phone' },
      { key: 'adminFax', label: 'Fax' },
      { key: 'adminAddress', label: 'Address (Master)' },
      { key: 'adminStreet', label: 'Street' },
      { key: 'adminCity', label: 'City' },
      { key: 'adminStateProvince', label: 'State/Province' },
      { key: 'adminPostalCode', label: 'Postal Code' },
      { key: 'adminCountry', label: 'Country' },
    ]
  },
  tech: {
    label: 'Tech Contact',
    description: 'Technical contact information',
    sensitive: true,
    fields: [
      { key: 'techEntity', label: 'Entity (Master)' },
      { key: 'techHandle', label: 'Handle' },
      { key: 'techName', label: 'Name' },
      { key: 'techOrganization', label: 'Organization' },
      { key: 'techEmail', label: 'Email' },
      { key: 'techPhone', label: 'Phone' },
      { key: 'techFax', label: 'Fax' },
      { key: 'techAddress', label: 'Address (Master)' },
      { key: 'techStreet', label: 'Street' },
      { key: 'techCity', label: 'City' },
      { key: 'techStateProvince', label: 'State/Province' },
      { key: 'techPostalCode', label: 'Postal Code' },
      { key: 'techCountry', label: 'Country' },
    ]
  },
  billing: {
    label: 'Billing Contact',
    description: 'Billing contact information',
    sensitive: true,
    fields: [
      { key: 'billingEntity', label: 'Entity (Master)' },
      { key: 'billingHandle', label: 'Handle' },
      { key: 'billingName', label: 'Name' },
      { key: 'billingOrganization', label: 'Organization' },
      { key: 'billingEmail', label: 'Email' },
      { key: 'billingPhone', label: 'Phone' },
      { key: 'billingFax', label: 'Fax' },
      { key: 'billingAddress', label: 'Address (Master)' },
      { key: 'billingStreet', label: 'Street' },
      { key: 'billingCity', label: 'City' },
      { key: 'billingStateProvince', label: 'State/Province' },
      { key: 'billingPostalCode', label: 'Postal Code' },
      { key: 'billingCountry', label: 'Country' },
    ]
  },
  registrar: {
    label: 'Registrar',
    description: 'Sponsoring registrar information',
    fields: [
      { key: 'registrarEntity', label: 'Entity (Master)' },
      { key: 'registrarHandle', label: 'Handle' },
      { key: 'registrarName', label: 'Name' },
      { key: 'registrarEmail', label: 'Email' },
      { key: 'registrarPhone', label: 'Phone' },
      { key: 'registrarUrl', label: 'URL' },
      { key: 'registrarAbuseContact', label: 'Abuse Contact' },
    ]
  },
  dnssec: {
    label: 'DNSSEC',
    description: 'DNS Security Extensions data',
    fields: [
      { key: 'dnssecData', label: 'DNSSEC Data (Master)' },
      { key: 'dnssecDelegationSigned', label: 'Delegation Signed' },
      { key: 'dnssecDsData', label: 'DS Data' },
      { key: 'dnssecKeyData', label: 'Key Data' },
    ]
  },
  network: {
    label: 'Network/IP',
    description: 'IP network information',
    fields: [
      { key: 'networkHandle', label: 'Handle' },
      { key: 'networkName', label: 'Name' },
      { key: 'networkType', label: 'Type' },
      { key: 'networkStartAddress', label: 'Start Address' },
      { key: 'networkEndAddress', label: 'End Address' },
      { key: 'networkIpVersion', label: 'IP Version' },
      { key: 'networkParentHandle', label: 'Parent Handle' },
      { key: 'networkCidr', label: 'CIDR' },
      { key: 'networkCountry', label: 'Country' },
    ]
  },
  autnum: {
    label: 'ASN',
    description: 'Autonomous System Number data',
    fields: [
      { key: 'autnumHandle', label: 'Handle' },
      { key: 'autnumStart', label: 'Start' },
      { key: 'autnumEnd', label: 'End' },
      { key: 'autnumName', label: 'Name' },
      { key: 'autnumType', label: 'Type' },
      { key: 'autnumCountry', label: 'Country' },
    ]
  },
  other: {
    label: 'Other',
    description: 'Links, notices, and remarks',
    fields: [
      { key: 'links', label: 'Links' },
      { key: 'notices', label: 'Notices' },
      { key: 'remarks', label: 'Remarks' },
    ]
  }
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
              <strong>{t(`rdapParams.groups.${groupKey}.label`)}</strong>
              {group.sensitive && (
                <span className="badge bg-warning-subtle text-warning" style={{ fontSize: '10px' }}>{t('rdapParams.sensitive')}</span>
              )}
            </div>
            <div className="text-muted small">{t(`rdapParams.groups.${groupKey}.description`)}</div>
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
            title={allEnabled ? t('rdapParams.disableAll') : t('rdapParams.enableAll')}
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
                className="form-check"
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
                  className="form-check-input"
                  checked={values[field.key] ?? true}
                  onChange={(e) => onChange({ [field.key]: e.target.checked })}
                  disabled={disabled}
                />
                <span className="form-check-label small">{t(`rdapParams.fields.${field.key}`)}</span>
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
      id: 'allEnabled',
      label: t('rdapParams.presets.allEnabled.label'),
      description: t('rdapParams.presets.allEnabled.description'),
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
      id: 'publicOnly',
      label: t('rdapParams.presets.publicOnly.label'),
      description: t('rdapParams.presets.publicOnly.description'),
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
      id: 'registrantOnly',
      label: t('rdapParams.presets.registrantOnly.label'),
      description: t('rdapParams.presets.registrantOnly.description'),
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
      id: 'noContacts',
      label: t('rdapParams.presets.noContacts.label'),
      description: t('rdapParams.presets.noContacts.description'),
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
        {t('rdapParams.quickPresets')}
      </span>
      {presets.map(preset => (
        <button
          key={preset.id}
          className="btn btn-secondary btn-sm"
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
    return { key, label: t(`rdapParams.groups.${key}.label`), enabled, total, sensitive: group.sensitive };
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
        <div style={{ opacity: 0.8, fontSize: '12px' }}>{t('rdapParams.total')}</div>
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
          {t('rdapParams.expandAll')}
        </button>
        <button
          className="btn btn-outline-secondary btn-sm"
          onClick={handleCollapseAll}
        >
          {t('rdapParams.collapseAll')}
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