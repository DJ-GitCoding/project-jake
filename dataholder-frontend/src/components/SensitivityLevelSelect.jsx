/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { useT } from '../i18n';

/**
 * Sensitivity level definitions with descriptions
 */
export const SENSITIVITY_LEVELS = [
  {
    level: 0,
    name: 'Public',
    color: 'var(--accent-success)',
    description: 'Basic public RDAP information only. No contact details or sensitive data.',
    examples: 'Domain name, status, nameservers, registrar info'
  },
  {
    level: 1,
    name: 'Basic',
    color: 'var(--accent-info)',
    description: 'Public data plus basic contact information. Limited PII exposure.',
    examples: 'Public + organization names, country, basic contact handles'
  },
  {
    level: 2,
    name: 'Enhanced',
    color: 'var(--accent-warning)',
    description: 'Extended data including detailed contact information. Contains PII.',
    examples: 'Basic + names, emails, phone numbers, addresses'
  },
  {
    level: 3,
    name: 'Full',
    color: 'var(--accent-error)',
    description: 'Complete access to all RDAP data including sensitive PII.',
    examples: 'All available data without restrictions'
  }
];

/**
 * Badge component for displaying sensitivity level
 */
export const SensitivityLevelBadge = ({ level, showName = true, size = 'normal' }) => {
  const levelInfo = SENSITIVITY_LEVELS[level] || SENSITIVITY_LEVELS[0];
  
  const sizeStyles = {
    small: { padding: '2px 6px', fontSize: '10px' },
    normal: { padding: '4px 8px', fontSize: '12px' },
    large: { padding: '6px 12px', fontSize: '14px' }
  };

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        backgroundColor: levelInfo.color,
        color: 'white',
        borderRadius: '4px',
        fontWeight: 600,
        ...sizeStyles[size]
      }}
      title={levelInfo.description}
    >
      <span>S{level}</span>
      {showName && <span>• {levelInfo.name}</span>}
    </span>
  );
};

/**
 * Dropdown select for sensitivity level
 */
const SensitivityLevelSelect = ({ 
  value, 
  onChange, 
  disabled = false,
  showDescriptions = true,
  id,
  name
}) => {
  const { t } = useT();
  return (
    <div className="sensitivity-level-select">
      <select
        id={id}
        name={name}
        className="form-select"
        value={value ?? 0}
        onChange={(e) => onChange(parseInt(e.target.value))}
        disabled={disabled}
        style={{ marginBottom: showDescriptions ? '8px' : 0 }}
      >
        {SENSITIVITY_LEVELS.map(level => (
          <option key={level.level} value={level.level}>
            {t('sensitivitySelect.levelOption', { level: level.level, name: level.name })}
          </option>
        ))}
      </select>
      
      {showDescriptions && (
        <div 
          style={{ 
            padding: '12px',
            backgroundColor: 'var(--bg-secondary)',
            borderRadius: '6px',
            borderLeft: `4px solid ${SENSITIVITY_LEVELS[value ?? 0].color}`
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
            <SensitivityLevelBadge level={value ?? 0} />
          </div>
          <p className="small" style={{ margin: '4px 0' }}>
            {SENSITIVITY_LEVELS[value ?? 0].description}
          </p>
          <p className="text-muted small" style={{ margin: 0 }}>
            <strong>{t('sensitivitySelect.includes')}</strong> {SENSITIVITY_LEVELS[value ?? 0].examples}
          </p>
        </div>
      )}
    </div>
  );
};

/**
 * Card-based selector for sensitivity level (alternative UI)
 */
export const SensitivityLevelCards = ({ value, onChange, disabled = false }) => {
  return (
    <div style={{ 
      display: 'grid', 
      gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', 
      gap: '12px' 
    }}>
      {SENSITIVITY_LEVELS.map(level => (
        <div
          key={level.level}
          onClick={() => !disabled && onChange(level.level)}
          style={{
            padding: '16px',
            border: `2px solid ${value === level.level ? level.color : 'var(--border-primary)'}`,
            borderRadius: '8px',
            cursor: disabled ? 'not-allowed' : 'pointer',
            backgroundColor: value === level.level ? `${level.color}10` : 'var(--bg-primary)',
            opacity: disabled ? 0.6 : 1,
            transition: 'all 0.2s ease'
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
            <div
              style={{
                width: '24px',
                height: '24px',
                borderRadius: '50%',
                backgroundColor: level.color,
                color: 'white',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '12px',
                fontWeight: 700
              }}
            >
              {level.level}
            </div>
            <strong>{level.name}</strong>
          </div>
          <p className="small text-muted" style={{ margin: 0 }}>
            {level.description}
          </p>
        </div>
      ))}
    </div>
  );
};

export default SensitivityLevelSelect;