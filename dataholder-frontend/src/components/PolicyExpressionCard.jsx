/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { DateTime } from 'luxon';
import { useT } from '../i18n';

const formatDate = (dateStr) => {
  if (!dateStr) return null;
  const dt = DateTime.fromISO(dateStr);
  if (!dt.isValid) return null;
  return dt.toLocaleString(DateTime.DATETIME_MED);
};

/*
 * NOTE: These operator labels are used inside a plain (non-component) helper below
 * (ScopeConditionsSummary), which is small enough to remain a function component so it
 * can call the translation hook itself; the label map stays as literal keys here and is
 * translated where it's rendered.
 */
const OPERATOR_LABEL_KEYS = {
  equals: 'policy.card.operators.equals',
  not_equals: 'policy.card.operators.notEquals',
  contains: 'policy.card.operators.contains',
  not_contains: 'policy.card.operators.notContains',
  starts_with: 'policy.card.operators.startsWith',
  ends_with: 'policy.card.operators.endsWith',
  is_empty: 'policy.card.operators.isEmpty',
  is_not_empty: 'policy.card.operators.isNotEmpty',
  in_list: 'policy.card.operators.inList',
  not_in_list: 'policy.card.operators.notInList',
  matches_regex: 'policy.card.operators.matchesRegex',
};

const ScopeConditionsSummary = ({ conditions }) => {
  const { t } = useT();
  let parsed = [];
  try {
    parsed = typeof conditions === 'string' ? JSON.parse(conditions) : (conditions || []);
  } catch { return null; }
  if (!Array.isArray(parsed) || parsed.length === 0) return <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('policy.card.noConditions')}</span>;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
      {parsed.map((c, i) => (
        <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '11px' }}>
          {i > 0 && <span style={{ color: 'var(--accent-primary)', fontWeight: 600, fontSize: '10px' }}>{t('policy.card.and')}</span>}
          <code style={{ fontSize: '11px', background: 'var(--bg-tertiary)', padding: '1px 4px', borderRadius: '3px' }}>{c.field === '__custom__' ? (c.customField || t('policy.card.customField')) : c.field}</code>
          <span style={{ color: 'var(--text-tertiary)' }}>{OPERATOR_LABEL_KEYS[c.operator] ? t(OPERATOR_LABEL_KEYS[c.operator]) : c.operator}</span>
          {c.operator !== 'is_empty' && c.operator !== 'is_not_empty' && c.value && (
            <span style={{ fontWeight: 500 }}>"{c.value}"</span>
          )}
        </div>
      ))}
    </div>
  );
};

const PolicyExpressionCard = ({ 
  policy, 
  onEdit, 
  onDelete, 
  onSetDefault,
  onToggleActive,
  isSelected = false,
  compact = false
}) => {
  const { t } = useT();

  if (compact) {
    return (
      <div
        className={`policy-card-compact ${isSelected ? 'selected' : ''} ${!policy.isActive ? 'inactive' : ''}`}
        onClick={() => onEdit?.(policy)}
      >
        <div className="policy-card-compact-header">
          <div className="policy-card-compact-title">
            {policy.isDefault && <i className="fa-solid fa-star default-marker" title={t('policy.card.defaultPolicy')} />}
            {policy.name}
          </div>
          <div className="policy-card-compact-badges">
            {policy.noteToRequestor && (
              <span className="note-indicator" title={policy.noteToRequestor}>
                <i className="fa-solid fa-message" style={{ fontSize: '10px' }} />
              </span>
            )}
          </div>
        </div>
        <div className="policy-card-compact-footer">
          <span
            className={`d-inline-block rounded-circle ${policy.isActive ? 'bg-success' : 'bg-secondary'}`}
            style={{ width: 8, height: 8 }}
          />
        </div>

        <style>{`
          .policy-card-compact {
            padding: 12px 16px;
            background: var(--bg-secondary);
            border: 1px solid var(--border-primary);
            border-radius: 8px;
            cursor: pointer;
            transition: all 0.15s ease;
          }

          .policy-card-compact:hover {
            border-color: var(--accent-primary);
            background: var(--bg-tertiary);
          }

          .policy-card-compact.selected {
            border-color: var(--accent-primary);
            box-shadow: 0 0 0 2px var(--accent-primary-alpha);
          }

          .policy-card-compact.inactive {
            opacity: 0.6;
          }

          .policy-card-compact-header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            gap: 12px;
            margin-bottom: 8px;
          }

          .policy-card-compact-title {
            font-weight: 500;
            font-size: 14px;
            color: var(--text-primary);
            display: flex;
            align-items: center;
            gap: 6px;
          }

          .default-marker {
            color: var(--accent-warning);
            font-size: 12px;
          }

          .policy-card-compact-badges {
            display: flex;
            gap: 4px;
            flex-wrap: wrap;
            justify-content: flex-end;
          }

          .note-indicator {
            color: var(--accent-primary);
            padding: 2px 6px;
            background: var(--accent-primary-alpha, rgba(59, 130, 246, 0.1));
            border-radius: 4px;
          }

          .mv-compact-indicator {
            color: var(--accent-warning, #f59e0b);
            padding: 2px 6px;
            background: var(--accent-warning-alpha, rgba(245, 158, 11, 0.1));
            border-radius: 4px;
          }

          .policy-card-compact-footer {
            display: flex;
            justify-content: space-between;
            align-items: center;
          }
        `}</style>
      </div>
    );
  }

  const fieldCount = policy.redactionRules?.length || 0;

  return (
    <div className={`policy-card ${!policy.isActive ? 'inactive' : ''}`}>

      <div className="policy-card-header">
        <div className="policy-card-title-row">
          <i className="fa-solid fa-file-contract policy-icon" />
          <h4 className="policy-card-title">
            {policy.name}
            {policy.isDefault && <span className="default-badge">{t('policy.card.defaultBadge')}</span>}
          </h4>
        </div>
        <div className="policy-card-actions">
          {!policy.isDefault && (
            <button
              className="btn btn-outline-secondary btn-sm"
              onClick={() => onSetDefault?.(policy.id)}
              title={t('policy.card.setAsDefault')}
            >
              <i className="fa-solid fa-star" />
            </button>
          )}
          <button
            className="btn btn-outline-secondary btn-sm"
            onClick={() => onEdit?.(policy)}
            title={t('common.edit')}
          >
            <i className="fa-solid fa-pen-to-square" />
          </button>
          <button
            className="btn btn-outline-danger btn-sm"
            onClick={() => onDelete?.(policy.id)}
            title={t('common.delete')}
          >
            <i className="fa-solid fa-trash" />
          </button>
        </div>
      </div>

      {policy.description && (
        <p className="policy-card-description">{policy.description}</p>
      )}

      <div className="policy-card-grid">
        {/* Defined Fields count - above scope conditions */}
        <div className="policy-card-item" style={{ gridColumn: '1 / -1' }}>
          <span className="policy-card-label">{t('policy.card.definedFields')}</span>
          {fieldCount > 0 ? (
            <span style={{ fontSize: '13px', fontWeight: 500, color: 'var(--accent-primary)' }}>
              <i className="fa-solid fa-shield-halved" style={{ marginRight: '4px', fontSize: '11px' }} />
              {fieldCount === 1
                ? t('policy.card.redactionRulesConfiguredSingular', { count: fieldCount })
                : t('policy.card.redactionRulesConfiguredPlural', { count: fieldCount })}
            </span>
          ) : (
            <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>{t('policy.card.noRedactionRules')}</span>
          )}
        </div>

        {/* Scope Conditions */}
        <div className="policy-card-item" style={{ gridColumn: '1 / -1' }}>
          <span className="policy-card-label">{t('policy.card.scopeConditions')}</span>
          <ScopeConditionsSummary conditions={policy.scopeConditions} />
        </div>

        {policy.noteToRequestor && (
          <div className="policy-card-item" style={{ gridColumn: '1 / -1' }}>
            <span className="policy-card-label">{t('policy.card.noteToRequestor')}</span>
            <span style={{ fontSize: '12px', color: 'var(--text-secondary)', fontStyle: 'italic' }}>
              "{policy.noteToRequestor}"
            </span>
          </div>
        )}

        {/* Dates */}
        {(policy.createdAt || policy.updatedAt) && (
          <>
            <div className="policy-card-item">
              <span className="policy-card-label">{t('policy.card.created')}</span>
              <span className="policy-card-date">
                <i className="fa-regular fa-clock" style={{ fontSize: '10px', marginRight: '4px' }} />
                {formatDate(policy.createdAt) || t('common.na')}
              </span>
            </div>
            <div className="policy-card-item">
              <span className="policy-card-label">{t('policy.card.updated')}</span>
              <span className="policy-card-date">
                <i className="fa-regular fa-pen-to-square" style={{ fontSize: '10px', marginRight: '4px' }} />
                {formatDate(policy.updatedAt) || t('common.na')}
              </span>
            </div>
          </>
        )}
      </div>

      <div className="policy-card-footer">
        <button
          className={`btn btn-sm ${policy.isActive ? 'btn-secondary' : 'btn-primary'}`}
          onClick={() => onToggleActive?.(policy.id)}
        >
          <i className={`fa-solid ${policy.isActive ? 'fa-pause' : 'fa-play'}`} />
          {policy.isActive ? t('policy.card.deactivate') : t('policy.card.activate')}
        </button>
      </div>

      <style>{`
        .policy-card {
          background: var(--bg-secondary);
          border: 2px solid var(--border-primary);
          border-radius: 12px;
          padding: 20px;
          transition: all 0.15s ease;
          position: relative;
          overflow: hidden;
        }

        .policy-card:hover {
          border-color: var(--border-secondary);
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
        }

        .policy-card.inactive {
          opacity: 0.7;
          background: var(--bg-tertiary);
        }

        /* Colored border when manual verification is on */
        .policy-card.mv-enabled {
          border-color: var(--accent-warning, #f59e0b);
        }

        .policy-card.mv-enabled:hover {
          border-color: var(--accent-warning, #f59e0b);
          box-shadow: 0 2px 12px rgba(245, 158, 11, 0.15);
        }

        /* Manual Verification Banner */
        .mv-banner {
          margin: -20px -20px 16px -20px;
          padding: 8px 20px;
          background: var(--accent-warning-alpha, rgba(245, 158, 11, 0.1));
          border-bottom: 1px solid var(--accent-warning, #f59e0b);
        }

        .mv-banner-content {
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 12px;
          font-weight: 600;
          color: var(--accent-warning, #f59e0b);
        }

        .mv-banner-content i {
          font-size: 13px;
        }

        .mv-threshold-badge {
          font-size: 10px;
          font-weight: 500;
          padding: 1px 6px;
          background: var(--accent-warning, #f59e0b);
          color: white;
          border-radius: 4px;
          margin-left: auto;
        }

        .policy-card-header {
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
          margin-bottom: 12px;
        }

        .policy-card-title-row {
          display: flex;
          align-items: center;
          gap: 10px;
        }

        .policy-icon {
          font-size: 18px;
          color: var(--accent-primary);
        }

        .policy-card-title {
          font-size: 16px;
          font-weight: 600;
          color: var(--text-primary);
          margin: 0;
          display: flex;
          align-items: center;
          gap: 8px;
        }

        .default-badge {
          font-size: 10px;
          font-weight: 500;
          padding: 2px 6px;
          background: var(--accent-warning-alpha, rgba(245, 158, 11, 0.1));
          color: var(--accent-warning, #f59e0b);
          border-radius: 4px;
          text-transform: uppercase;
        }

        .policy-card-actions {
          display: flex;
          gap: 4px;
        }

        .policy-card-actions .btn {
          padding: 6px 8px;
        }

        .policy-card-description {
          font-size: 13px;
          color: var(--text-secondary);
          margin: 0 0 16px 0;
          line-height: 1.5;
        }

        .policy-card-grid {
          display: grid;
          grid-template-columns: repeat(2, 1fr);
          gap: 12px;
          margin-bottom: 16px;
        }

        .policy-card-item {
          display: flex;
          flex-direction: column;
          gap: 4px;
        }

        .policy-card-label {
          font-size: 11px;
          font-weight: 500;
          color: var(--text-tertiary);
          text-transform: uppercase;
          letter-spacing: 0.5px;
        }

        .policy-card-footer {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding-top: 16px;
          border-top: 1px solid var(--border-primary);
        }

        .policy-card-footer .btn {
          display: flex;
          align-items: center;
          gap: 6px;
        }

        .policy-card-flags {
          display: flex;
          gap: 8px;
          flex-wrap: wrap;
        }

        .flag {
          font-size: 11px;
          padding: 4px 8px;
          border-radius: 4px;
          background: var(--bg-tertiary);
          color: var(--text-tertiary);
          display: flex;
          align-items: center;
          gap: 4px;
        }

        .flag.active {
          background: var(--accent-primary-alpha, rgba(59, 130, 246, 0.1));
          color: var(--accent-primary, #3b82f6);
        }

        .flag.flag-mv-active {
          background: var(--accent-warning-alpha, rgba(245, 158, 11, 0.1));
          color: var(--accent-warning, #f59e0b);
        }

        .policy-card-date {
          font-size: 12px;
          color: var(--text-secondary);
          display: flex;
          align-items: center;
        }
      `}</style>
    </div>
  );
};

export default PolicyExpressionCard;
