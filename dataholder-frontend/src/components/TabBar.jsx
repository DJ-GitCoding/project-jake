/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';

/**
 * Horizontal tab bar component
 */
const TabBar = ({ 
  tabs, 
  activeTab, 
  onTabChange,
  style = {},
  variant = 'default' // 'default', 'pills', 'underline'
}) => {
  const variantStyles = {
    default: {
      container: {
        display: 'flex',
        borderBottom: '1px solid var(--border-primary)',
        gap: '0',
      },
      tab: (isActive) => ({
        padding: '12px 20px',
        border: 'none',
        backgroundColor: isActive ? 'var(--bg-secondary)' : 'transparent',
        borderBottom: isActive ? '2px solid var(--accent-primary)' : '2px solid transparent',
        cursor: 'pointer',
        fontWeight: isActive ? 600 : 400,
        color: isActive ? 'var(--text-primary)' : 'var(--text-secondary)',
        transition: 'all 0.2s ease',
      }),
    },
    pills: {
      container: {
        display: 'flex',
        gap: '8px',
        padding: '4px',
        backgroundColor: 'var(--bg-secondary)',
        borderRadius: '8px',
      },
      tab: (isActive) => ({
        padding: '8px 16px',
        border: 'none',
        backgroundColor: isActive ? 'var(--bg-primary)' : 'transparent',
        borderRadius: '6px',
        cursor: 'pointer',
        fontWeight: isActive ? 600 : 400,
        color: isActive ? 'var(--text-primary)' : 'var(--text-secondary)',
        boxShadow: isActive ? '0 1px 3px rgba(0,0,0,0.1)' : 'none',
        transition: 'all 0.2s ease',
      }),
    },
    underline: {
      container: {
        display: 'flex',
        gap: '24px',
        borderBottom: '1px solid var(--border-primary)',
      },
      tab: (isActive) => ({
        padding: '12px 0',
        border: 'none',
        backgroundColor: 'transparent',
        borderBottom: isActive ? '2px solid var(--accent-primary)' : '2px solid transparent',
        marginBottom: '-1px',
        cursor: 'pointer',
        fontWeight: isActive ? 600 : 400,
        color: isActive ? 'var(--accent-primary)' : 'var(--text-secondary)',
        transition: 'all 0.2s ease',
      }),
    },
  };

  const styles = variantStyles[variant];

  return (
    <ul className="nav nav-pills" style={{ ...styles.container, ...style }}>
      {tabs.map(tab => (
        <li className="nav-item" key={tab.id}>
          <button
            className={`nav-link${activeTab === tab.id ? ' active' : ''}`}
            onClick={() => onTabChange(tab.id)}
            style={styles.tab(activeTab === tab.id)}
          >
            {tab.icon && <span style={{ marginRight: '8px' }}>{tab.icon}</span>}
            {tab.label}
            {tab.count !== undefined && (
              <span
                className={`badge ms-2 ${activeTab === tab.id ? 'bg-primary text-white' : 'bg-secondary-subtle text-secondary'}`}
              >
                {tab.count}
              </span>
            )}
          </button>
        </li>
      ))}
    </ul>
  );
};

/**
 * Filter tabs (commonly used for status filtering)
 */
export const FilterTabs = ({ 
  filters, 
  activeFilter, 
  onFilterChange,
  showCounts = true 
}) => {
  const tabs = filters.map(filter => ({
    id: filter.key,
    label: filter.label,
    count: showCounts ? filter.count : undefined,
  }));

  return (
    <TabBar
      tabs={tabs}
      activeTab={activeFilter}
      onTabChange={onFilterChange}
      variant="default"
      style={{ marginBottom: '16px' }}
    />
  );
};

/**
 * Modal/detail tabs
 */
export const DetailTabs = ({ tabs, activeTab, onTabChange }) => {
  return (
    <TabBar
      tabs={tabs}
      activeTab={activeTab}
      onTabChange={onTabChange}
      variant="underline"
      style={{ marginBottom: '16px' }}
    />
  );
};

export default TabBar;