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
 * Status configuration for subscription statuses
 */
const STATUS_CONFIG = {
  // Subscription statuses
  PENDING: { className: 'bg-warning-subtle text-warning', color: 'var(--accent-warning)' },
  APPROVED: { className: 'bg-info-subtle text-info', color: 'var(--accent-info)' },
  TESTING: { className: 'bg-warning-subtle text-warning', color: 'var(--accent-warning)' },
  ACTIVE: { className: 'bg-success-subtle text-success', color: 'var(--accent-success)' },
  DENIED: { className: 'bg-danger-subtle text-danger', color: 'var(--accent-error)' },
  SUSPENDED: { className: 'bg-danger-subtle text-danger', color: 'var(--accent-error)' },
  EXPIRED: { className: 'bg-secondary-subtle text-secondary', color: 'var(--text-tertiary)' },
  CANCELLED: { className: 'bg-secondary-subtle text-secondary', color: 'var(--text-tertiary)' },

  // Test results
  PASSED: { className: 'bg-success-subtle text-success', color: 'var(--accent-success)' },
  FAILED: { className: 'bg-danger-subtle text-danger', color: 'var(--accent-error)' },

  // Generic
  UNKNOWN: { className: '', color: 'var(--text-secondary)' },
};

// Maps a status to its i18n key (falls back to common.unknown when unrecognized)
const STATUS_LABEL_KEYS = {
  PENDING: 'subscriptions.status.pending',
  APPROVED: 'subscriptions.status.approved',
  TESTING: 'subscriptions.status.testing',
  ACTIVE: 'common.active',
  DENIED: 'subscriptions.status.denied',
  SUSPENDED: 'subscriptions.status.suspended',
  EXPIRED: 'subscriptions.status.expired',
  CANCELLED: 'subscriptions.status.cancelled',
  PASSED: 'subscriptions.status.passed',
  FAILED: 'subscriptions.status.failed',
};

/**
 * Reusable status badge component
 */
const SubscriptionStatusBadge = ({ 
  status, 
  size = 'default', // 'small', 'default', 'large'
  clickable = false,
  onClick,
  showIcon = false
}) => {
  const { t } = useT();
  const config = STATUS_CONFIG[status] || STATUS_CONFIG.UNKNOWN;
  const label = t(STATUS_LABEL_KEYS[status] || 'common.unknown');

  const sizeStyles = {
    small: { fontSize: '10px', padding: '2px 6px' },
    default: { fontSize: '12px', padding: '4px 10px' },
    large: { fontSize: '14px', padding: '8px 16px' },
  };

  const baseStyle = {
    ...sizeStyles[size],
    cursor: clickable ? 'pointer' : 'default',
    border: 'none',
    display: 'inline-flex',
    alignItems: 'center',
    gap: '4px',
  };

  const icons = {
    PENDING: '⏳',
    APPROVED: '✓',
    TESTING: '🧪',
    ACTIVE: '●',
    DENIED: '✗',
    SUSPENDED: '⏸',
    EXPIRED: '⌛',
    CANCELLED: '✗',
    PASSED: '✓',
    FAILED: '✗',
  };

  const Component = clickable ? 'button' : 'span';

  return (
    <Component
      className={`badge ${config.className}`}
      style={baseStyle}
      onClick={clickable ? onClick : undefined}
      title={clickable ? t('subscriptions.statusBadge.clickToChange') : undefined}
    >
      {showIcon && icons[status] && <span>{icons[status]}</span>}
      {label}
    </Component>
  );
};

/**
 * Test result badge
 */
export const TestResultBadge = ({ result, size = 'default' }) => {
  if (!result) return <span className="text-muted">-</span>;
  return <SubscriptionStatusBadge status={result} size={size} />;
};

/**
 * Get status color for charts/stats
 */
export const getStatusColor = (status) => {
  return STATUS_CONFIG[status]?.color || 'var(--text-secondary)';
};

/**
 * Get all possible statuses
 */
export const SUBSCRIPTION_STATUSES = Object.keys(STATUS_CONFIG).filter(
  s => !['PASSED', 'FAILED', 'UNKNOWN'].includes(s)
);

export const TEST_RESULTS = ['PASSED', 'FAILED'];

export default SubscriptionStatusBadge;