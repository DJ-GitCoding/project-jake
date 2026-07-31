/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useMemo } from 'react';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

/*
 * API functions for redaction rules. Same-origin path so the SSR BFF proxy
 * attaches the session Bearer and forwards to the backend.
 */
const API_BASE = '/api/redaction-rules';

const fetchRules = async () => {
  const response = await fetch(API_BASE);
  if (!response.ok) throw new Error('Failed to fetch rules');
  return response.json();
};

const updateRule = async (id, data) => {
  const response = await fetch(`${API_BASE}/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  if (!response.ok) throw new Error('Failed to update rule');
  return response.json();
};

const initializeRules = async () => {
  const response = await fetch(`${API_BASE}/initialize`, { method: 'POST' });
  if (!response.ok) throw new Error('Failed to initialize rules');
  return response.json();
};

// Behavior badge component
const BehaviorBadge = ({ behavior }) => {
  const { t } = useT();
  const styles = {
    FULL: { bg: 'rgba(16, 185, 129, 0.1)', color: 'var(--accent-success)', label: t('redactionRules.behaviorFull') },
    REDACTED: { bg: 'rgba(245, 158, 11, 0.1)', color: 'var(--accent-warning)', label: t('redactionRules.behaviorRedacted') },
    EMPTY: { bg: 'rgba(239, 68, 68, 0.1)', color: 'var(--accent-danger)', label: t('redactionRules.behaviorEmpty') },
  };

  const style = styles[behavior] || styles.FULL;
  
  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      padding: '4px 10px',
      borderRadius: '12px',
      fontSize: '12px',
      fontWeight: 500,
      background: style.bg,
      color: style.color,
    }}>
      {style.label}
    </span>
  );
};

// Level label (labels supplied by the caller, which has access to translations)
const levelLabel = (level, labels) => {
  return labels[level] ?? `${level}`;
};

// Behavior select dropdown
const BehaviorSelect = ({ value, onChange, disabled }) => {
  const { t } = useT();
  return (
    <select
      className="form-select form-select-sm behavior-select"
      value={value}
      onChange={e => onChange(e.target.value)}
      disabled={disabled}
      style={{
        padding: '4px 8px',
        fontSize: '12px',
        fontWeight: 500,
        minWidth: '130px',
        color: value === 'FULL' ? 'var(--accent-success)' :
               value === 'REDACTED' ? 'var(--accent-warning)' :
               'var(--accent-danger)',
      }}
    >
      <option value="FULL">{t('redactionRules.behaviorFull')}</option>
      <option value="REDACTED">{t('redactionRules.behaviorRedacted')}</option>
      <option value="EMPTY">{t('redactionRules.behaviorEmpty')}</option>
    </select>
  );
};

// Main component
const RedactionRules = () => {
  const [rules, setRules] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState({});
  const [filterAccess, setFilterAccess] = useState('');
  const [filterSensitivity, setFilterSensitivity] = useState('');
  const [filterEmpty, setFilterEmpty] = useState('');
  const { t } = useT();

  const levelLabels = [
    t('redactionRules.level0'),
    t('redactionRules.level1'),
    t('redactionRules.level2'),
    t('redactionRules.level3'),
  ];

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const data = await fetchRules();
      setRules(data.rules || []);
    } catch (error) {
      console.error('Failed to load redaction rules:', error);
      toast.error(t('redactionRules.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  const handleInitialize = async () => {
    try {
      await initializeRules();
      toast.success(t('redactionRules.defaultsInitialized'));
      loadData();
    } catch (error) {
      toast.error(t('redactionRules.initializeFailed'));
    }
  };

  const handleBehaviorChange = async (rule, newBehavior) => {
    const key = rule.id;
    setSaving(prev => ({ ...prev, [key]: true }));
    try {
      await updateRule(rule.id, { redactionBehavior: newBehavior });
      setRules(prev =>
        prev.map(r => r.id === rule.id ? { ...r, redactionBehavior: newBehavior } : r)
      );
    } catch (error) {
      toast.error(t('redactionRules.updateFailed'));
    } finally {
      setSaving(prev => ({ ...prev, [key]: false }));
    }
  };

  // Filter rules
  const filteredRules = useMemo(() => {
    let result = rules;
    if (filterAccess !== '') {
      result = result.filter(r => r.accessLevel === parseInt(filterAccess));
    }
    if (filterSensitivity !== '') {
      result = result.filter(r => r.sensitivityLevel === parseInt(filterSensitivity));
    }
    if (filterEmpty !== '') {
      result = result.filter(r => r.emptyValue === (filterEmpty === 'true'));
    }
    return result;
  }, [rules, filterAccess, filterSensitivity, filterEmpty]);

  // Group by access level for visual grouping
  const groupedByAccess = useMemo(() => {
    const groups = {};
    filteredRules.forEach(r => {
      if (!groups[r.accessLevel]) groups[r.accessLevel] = [];
      groups[r.accessLevel].push(r);
    });
    return groups;
  }, [filteredRules]);

  if (loading) {
    return (
      <div className="loading-container">
        <div className="loading-spinner"></div>
        <p>{t('redactionRules.loadingRules')}</p>
      </div>
    );
  }

  return (
    <div className="redaction-rules">
      <div className="rules-header">
        <div className="rules-title">
          <h4>{t('redactionRules.title')}</h4>
          <p className="text-muted">
            {t('redactionRules.description')}
          </p>
        </div>
        <div className="rules-actions">
          <button className="btn btn-outline-secondary btn-sm" onClick={loadData}>
            <i className="fa-solid fa-arrows-rotate" />
          </button>
          {rules.length === 0 && (
            <button className="btn btn-primary btn-sm" onClick={handleInitialize}>
              {t('redactionRules.initializeDefaults')}
            </button>
          )}
        </div>
      </div>

      {/* Filters */}
      <div className="rules-filters">
        <div className="filter-group">
          <label>{t('redactionRules.accessLevelLabel')}</label>
          <select
            className="form-select form-select-sm"
            value={filterAccess}
            onChange={e => setFilterAccess(e.target.value)}
          >
            <option value="">{t('common.all')}</option>
            <option value="0">{levelLabels[0]}</option>
            <option value="1">{levelLabels[1]}</option>
            <option value="2">{levelLabels[2]}</option>
            <option value="3">{levelLabels[3]}</option>
          </select>
        </div>
        <div className="filter-group">
          <label>{t('redactionRules.sensitivityLabel')}</label>
          <select
            className="form-select form-select-sm"
            value={filterSensitivity}
            onChange={e => setFilterSensitivity(e.target.value)}
          >
            <option value="">{t('common.all')}</option>
            <option value="0">{levelLabels[0]}</option>
            <option value="1">{levelLabels[1]}</option>
            <option value="2">{levelLabels[2]}</option>
            <option value="3">{levelLabels[3]}</option>
          </select>
        </div>
        <div className="filter-group">
          <label>{t('redactionRules.valueLabel')}</label>
          <select
            className="form-select form-select-sm"
            value={filterEmpty}
            onChange={e => setFilterEmpty(e.target.value)}
          >
            <option value="">{t('common.all')}</option>
            <option value="false">{t('redactionRules.hasValue')}</option>
            <option value="true">{t('redactionRules.isEmpty')}</option>
          </select>
        </div>
        <div className="filter-stats">
          {t('redactionRules.showingRules', { count: filteredRules.length, total: rules.length })}
        </div>
      </div>

      {/* Matrix table */}
      <div className="rules-table-wrapper">
        <table className="rules-table">
          <thead>
            <tr>
              <th>{t('redactionRules.colAccessLevel')}</th>
              <th>{t('redactionRules.colSensitivityLevel')}</th>
              <th>{t('redactionRules.colValueState')}</th>
              <th>{t('redactionRules.colBehavior')}</th>
            </tr>
          </thead>
          <tbody>
            {[0, 1, 2, 3].map(accessLevel => {
              const groupRules = groupedByAccess[accessLevel];
              if (!groupRules || groupRules.length === 0) return null;

              return groupRules.map((rule, idx) => (
                <tr
                  key={rule.id}
                  className={`${!rule.isActive ? 'inactive' : ''} ${idx === 0 ? 'group-start' : ''}`}
                >
                  {idx === 0 ? (
                    <td rowSpan={groupRules.length} className="access-level-cell">
                      <div className="level-badge access-badge">
                        {levelLabel(accessLevel, levelLabels)}
                      </div>
                    </td>
                  ) : null}
                  <td>
                    <div className="level-badge sensitivity-badge">
                      {levelLabel(rule.sensitivityLevel, levelLabels)}
                    </div>
                  </td>
                  <td>
                    <span className={`empty-badge ${rule.emptyValue ? 'is-empty' : 'has-value'}`}>
                      {rule.emptyValue ? t('redactionRules.isEmpty') : t('redactionRules.hasValue')}
                    </span>
                  </td>
                  <td>
                    <BehaviorSelect
                      value={rule.redactionBehavior}
                      onChange={(val) => handleBehaviorChange(rule, val)}
                      disabled={!!saving[rule.id]}
                    />
                  </td>
                </tr>
              ));
            })}
          </tbody>
        </table>
      </div>

      {/* Behavior legend */}
      <div className="behavior-legend">
        <h5>{t('redactionRules.behaviorReference')}</h5>
        <div className="legend-items">
          <div className="legend-item">
            <BehaviorBadge behavior="FULL" />
            <span>{t('redactionRules.legendFull')}</span>
          </div>
          <div className="legend-item">
            <BehaviorBadge behavior="REDACTED" />
            <span>{t('redactionRules.legendRedacted')}</span>
          </div>
          <div className="legend-item">
            <BehaviorBadge behavior="EMPTY" />
            <span>{t('redactionRules.legendEmpty')}</span>
          </div>
        </div>
      </div>

      <style>{`
        .redaction-rules {
          margin-top: 24px;
        }

        .rules-header {
          display: flex;
          align-items: flex-start;
          justify-content: space-between;
          margin-bottom: 16px;
        }

        .rules-title h4 {
          margin: 0 0 4px 0;
          font-size: 16px;
        }

        .rules-title .text-muted {
          margin: 0;
          font-size: 13px;
        }

        .rules-actions {
          display: flex;
          gap: 8px;
        }

        .rules-filters {
          display: flex;
          align-items: center;
          gap: 16px;
          padding: 12px 16px;
          background: var(--bg-tertiary);
          border-radius: 8px;
          margin-bottom: 16px;
        }

        .filter-group {
          display: flex;
          align-items: center;
          gap: 8px;
        }

        .filter-group label {
          font-size: 13px;
          color: var(--text-secondary);
          white-space: nowrap;
        }

        .filter-stats {
          margin-left: auto;
          font-size: 13px;
          color: var(--text-tertiary);
        }

        .form-select-sm {
          padding: 6px 12px;
          font-size: 13px;
          min-width: 130px;
        }

        .rules-table-wrapper {
          border: 1px solid var(--border-primary);
          border-radius: 8px;
          overflow: hidden;
        }

        .rules-table {
          width: 100%;
          border-collapse: collapse;
        }

        .rules-table th,
        .rules-table td {
          padding: 10px 16px;
          text-align: left;
          border-bottom: 1px solid var(--border-primary);
        }

        .rules-table th {
          font-size: 12px;
          font-weight: 500;
          color: var(--text-tertiary);
          text-transform: uppercase;
          letter-spacing: 0.5px;
          background: var(--bg-secondary);
        }

        .rules-table tr:last-child td {
          border-bottom: none;
        }

        .rules-table tr.inactive {
          opacity: 0.5;
        }

        .rules-table tr.group-start td {
          border-top: 2px solid var(--border-primary);
        }

        .rules-table thead + tbody tr.group-start:first-child td {
          border-top: none;
        }

        .access-level-cell {
          vertical-align: middle;
          text-align: center;
          background: var(--bg-secondary);
          border-right: 1px solid var(--border-primary);
          font-weight: 600;
        }

        .level-badge {
          display: inline-flex;
          align-items: center;
          padding: 4px 10px;
          border-radius: 6px;
          font-size: 12px;
          font-weight: 500;
        }

        .access-badge {
          background: rgba(59, 130, 246, 0.1);
          color: var(--accent-primary);
          font-weight: 600;
        }

        .sensitivity-badge {
          background: rgba(139, 92, 246, 0.1);
          color: #8b5cf6;
        }

        .empty-badge {
          display: inline-flex;
          align-items: center;
          padding: 3px 8px;
          border-radius: 10px;
          font-size: 11px;
          font-weight: 500;
        }

        .empty-badge.has-value {
          background: rgba(16, 185, 129, 0.1);
          color: var(--accent-success);
        }

        .empty-badge.is-empty {
          background: rgba(156, 163, 175, 0.15);
          color: var(--text-tertiary);
        }

        .behavior-legend {
          margin-top: 24px;
          padding: 16px;
          background: var(--bg-secondary);
          border-radius: 8px;
          border: 1px solid var(--border-primary);
        }

        .behavior-legend h5 {
          margin: 0 0 12px 0;
          font-size: 14px;
        }

        .legend-items {
          display: flex;
          flex-direction: column;
          gap: 8px;
        }

        .legend-item {
          display: flex;
          align-items: center;
          gap: 12px;
          font-size: 13px;
          color: var(--text-secondary);
        }

        .loading-container {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          padding: 48px;
          color: var(--text-tertiary);
        }

        .loading-spinner {
          width: 32px;
          height: 32px;
          border: 3px solid var(--border-primary);
          border-top-color: var(--accent-primary);
          border-radius: 50%;
          animation: spin 1s linear infinite;
          margin-bottom: 12px;
        }

        @keyframes spin {
          to { transform: rotate(360deg); }
        }

        @media (max-width: 768px) {
          .rules-filters {
            flex-direction: column;
            align-items: stretch;
            gap: 12px;
          }

          .filter-stats {
            margin-left: 0;
          }
        }
      `}</style>
    </div>
  );
};

export default RedactionRules;
