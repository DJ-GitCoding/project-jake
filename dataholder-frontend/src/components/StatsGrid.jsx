/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { getStatusColor } from './SubscriptionStatusBadge';

/**
 * Single stat card
 */
export const StatCard = ({ 
  value, 
  label, 
  color,
  onClick,
  active = false,
  size = 'default' // 'small', 'default', 'large'
}) => {
  const sizeStyles = {
    small: { padding: '12px', fontSize: '20px' },
    default: { padding: '16px', fontSize: '24px' },
    large: { padding: '20px', fontSize: '32px' },
  };

  return (
    <div 
      className="card" 
      style={{ 
        ...sizeStyles[size],
        textAlign: 'center',
        cursor: onClick ? 'pointer' : 'default',
        border: active ? `2px solid ${color || 'var(--accent-primary)'}` : undefined,
        transition: 'all 0.2s ease'
      }}
      onClick={onClick}
    >
      <div style={{ 
        fontSize: sizeStyles[size].fontSize, 
        fontWeight: 700, 
        color: color || 'var(--text-primary)' 
      }}>
        {value}
      </div>
      <div className="text-muted small">{label}</div>
    </div>
  );
};

/**
 * Grid of stat cards
 */
export const StatsGrid = ({ 
  stats, 
  columns = 'auto-fit',
  minWidth = '120px',
  onStatClick,
  activeFilter
}) => {
  return (
    <div style={{ 
      display: 'grid', 
      gridTemplateColumns: `repeat(${columns}, minmax(${minWidth}, 1fr))`, 
      gap: '16px', 
      marginBottom: '24px' 
    }}>
      {stats.map((stat, index) => (
        <StatCard
          key={stat.key || index}
          value={stat.value}
          label={stat.label}
          color={stat.color}
          onClick={stat.onClick || (onStatClick ? () => onStatClick(stat.key) : undefined)}
          active={activeFilter === stat.key}
        />
      ))}
    </div>
  );
};

/**
 * Subscription stats configuration helper
 *
 * NOTE (i18n): this is a plain helper function, not a React component/hook,
 * so it cannot call useT() itself (would violate rules-of-hooks). Callers
 * must obtain `t` via `const { t } = useT();` in their own component body
 * and pass it in here. There are currently no call sites for this helper
 * anywhere in the codebase, so adding this parameter is non-breaking.
 */
export const buildSubscriptionStats = (stats, onFilterClick, activeFilter, t) => {
  if (!stats) return [];

  return [
    {
      key: 'all',
      value: stats.totalSubscriptions || 0,
      label: t('statsGrid.total'),
      color: 'var(--accent-primary)',
      onClick: () => onFilterClick('all')
    },
    {
      key: 'PENDING',
      value: stats.pendingCount || 0,
      label: t('statsGrid.pending'),
      color: getStatusColor('PENDING'),
      onClick: () => onFilterClick('PENDING')
    },
    {
      key: 'APPROVED',
      value: stats.approvedCount || 0,
      label: t('statsGrid.approved'),
      color: getStatusColor('APPROVED'),
      onClick: () => onFilterClick('APPROVED')
    },
    {
      key: 'TESTING',
      value: stats.testingCount || 0,
      label: t('statsGrid.testing'),
      color: getStatusColor('TESTING'),
      onClick: () => onFilterClick('TESTING')
    },
    {
      key: 'ACTIVE',
      value: stats.activeCount || 0,
      label: t('statsGrid.active'),
      color: getStatusColor('ACTIVE'),
      onClick: () => onFilterClick('ACTIVE')
    },
    {
      key: 'SUSPENDED',
      value: stats.suspendedCount || 0,
      label: t('statsGrid.suspended'),
      color: getStatusColor('SUSPENDED'),
      onClick: () => onFilterClick('SUSPENDED')
    },
  ].map(stat => ({
    ...stat,
    active: activeFilter === stat.key
  }));
};

/**
 * Template stats configuration helper
 *
 * NOTE (i18n): see buildSubscriptionStats above — plain helper, not a
 * component/hook, so `t` must be supplied by the caller via useT().
 */
export const buildTemplateStats = (templates, t) => {
  const publishedCount = templates.filter(tpl => tpl.isPublished).length;

  return [
    {
      value: templates.length,
      label: t('statsGrid.totalTemplates'),
      color: 'var(--accent-primary)'
    },
    {
      value: publishedCount,
      label: t('statsGrid.published'),
      color: 'var(--accent-success)'
    },
    {
      value: templates.length - publishedCount,
      label: t('statsGrid.draft'),
      color: 'var(--text-tertiary)'
    },
  ];
};

export default StatsGrid;