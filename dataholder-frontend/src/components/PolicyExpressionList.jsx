/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import PolicyExpressionCard from './PolicyExpressionCard';
import Pagination from './Pagination';
import { useT } from '../i18n';

/*
 * Server-side paginated list: `policies` is already the current page (filtered/searched
 * server-side by the parent). Pagination state is owned by the parent and passed in.
 */
const PolicyExpressionList = ({
  policies,
  selectedId,
  onSelect,
  onEdit,
  onDelete,
  onSetDefault,
  onToggleActive,
  onCreate,
  searchQuery,
  onSearchChange,
  filterActive,
  onFilterChange,
  page,
  pageSize,
  totalItems,
  onPageChange,
  onPageSizeChange,
  loading = false,
}) => {
  const { t } = useT();

  return (
    <div className="policy-list-container">
      <div className="policy-list-header">
        <div className="policy-list-search">
          <i className="fa-solid fa-search search-icon" />
          <input
            type="text"
            className="form-control search-input"
            placeholder={t('policy.list.searchPlaceholder')}
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
          />
        </div>
        <div className="policy-list-filters">
          <select
            className="form-select filter-select"
            value={filterActive}
            onChange={(e) => onFilterChange(e.target.value)}
          >
            <option value="all">{t('policy.list.filterAll')}</option>
            <option value="active">{t('policy.list.filterActiveOnly')}</option>
            <option value="inactive">{t('policy.list.filterInactiveOnly')}</option>
          </select>
        </div>
      </div>

      <div className="policy-list-actions">
        <button className="btn btn-primary" onClick={onCreate}>
          <i className="fa-solid fa-plus" />
          {t('policy.list.newPolicy')}
        </button>
        <span className="policy-count">
          {totalItems === 1 ? t('policy.list.countSingular', { count: totalItems }) : t('policy.list.countPlural', { count: totalItems })}
        </span>
      </div>

      {loading ? (
        <div className="policy-list-loading">
          <i className="fa-solid fa-spinner fa-spin fa-2x" />
          <span>{t('policy.list.loading')}</span>
        </div>
      ) : policies.length === 0 ? (
        <div className="policy-list-empty">
          <i className="fa-solid fa-folder-open fa-3x" style={{ opacity: 0.3 }} />
          {searchQuery || filterActive !== 'all' ? (
            <>
              <p>{t('policy.list.noMatch')}</p>
              <button
                className="btn btn-secondary btn-sm"
                onClick={() => {
                  onSearchChange('');
                  onFilterChange('all');
                }}
              >
                <i className="fa-solid fa-times" />
                {t('policy.list.clearFilters')}
              </button>
            </>
          ) : (
            <>
              <p>{t('policy.list.emptyState')}</p>
              <button className="btn btn-primary" onClick={onCreate}>
                <i className="fa-solid fa-plus" />
                {t('policy.list.createFirst')}
              </button>
            </>
          )}
        </div>
      ) : (
        <>
          <div className="policy-list-grid">
            {policies.map((policy) => (
              <PolicyExpressionCard
                key={policy.id}
                policy={policy}
                isSelected={policy.id === selectedId}
                onEdit={onEdit}
                onDelete={onDelete}
                onSetDefault={onSetDefault}
                onToggleActive={onToggleActive}
              />
            ))}
          </div>
          <Pagination
            page={page}
            pageSize={pageSize}
            totalItems={totalItems}
            onPageChange={onPageChange}
            onPageSizeChange={onPageSizeChange}
            itemLabel="policies"
          />
        </>
      )}

      <style>{`
        .policy-list-container {
          display: flex;
          flex-direction: column;
          gap: 16px;
        }

        .policy-list-header {
          display: flex;
          gap: 12px;
          flex-wrap: wrap;
        }

        .policy-list-search {
          flex: 1;
          min-width: 200px;
          position: relative;
        }

        .search-icon {
          position: absolute;
          left: 12px;
          top: 50%;
          transform: translateY(-50%);
          color: var(--text-tertiary);
          pointer-events: none;
        }

        .search-input {
          padding-left: 36px;
        }

        .policy-list-filters {
          display: flex;
          gap: 8px;
        }

        .filter-select {
          min-width: 140px;
        }

        .policy-list-actions {
          display: flex;
          justify-content: space-between;
          align-items: center;
        }

        .policy-list-actions .btn {
          display: flex;
          align-items: center;
          gap: 8px;
        }

        .policy-count {
          font-size: 13px;
          color: var(--text-tertiary);
        }

        .policy-list-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
          gap: 16px;
        }

        .policy-list-loading,
        .policy-list-empty {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          padding: 48px 24px;
          text-align: center;
          color: var(--text-secondary);
          background: var(--bg-tertiary);
          border-radius: 12px;
          gap: 16px;
        }

        .policy-list-empty .btn {
          display: flex;
          align-items: center;
          gap: 8px;
        }
      `}</style>
    </div>
  );
};

export default PolicyExpressionList;