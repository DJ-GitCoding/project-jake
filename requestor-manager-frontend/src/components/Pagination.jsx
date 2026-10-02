/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { useT } from '../i18n';

export const DEFAULT_PAGE_SIZE_OPTIONS = [25, 50, 100, 200];
export const DEFAULT_PAGE_SIZE = 50;

/**
 * Reusable pagination controls (Bootstrap 5 styled).
 *
 * Works for both client-side pagination (pass totalItems = filteredList.length and
 * slice the list yourself) and server-side pagination (pass totalItems = totalElements
 * returned by the API). Pages are 1-based.
 */
const Pagination = ({
  page,
  pageSize,
  totalItems,
  onPageChange,
  onPageSizeChange,
  pageSizeOptions = DEFAULT_PAGE_SIZE_OPTIONS,
  itemLabel,
  className = '',
}) => {
  const { t } = useT();
  const label = itemLabel || t('pagination.items');
  const total = Number(totalItems) || 0;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const current = Math.min(Math.max(1, page), totalPages);

  const start = total === 0 ? 0 : (current - 1) * pageSize + 1;
  const end = Math.min(current * pageSize, total);

  const goTo = (p) => {
    const clamped = Math.min(Math.max(1, p), totalPages);
    if (clamped !== current) onPageChange(clamped);
  };

  // Build a compact window of page numbers around the current page.
  const pageNumbers = [];
  const windowSize = 2; // pages on each side of current
  let from = Math.max(1, current - windowSize);
  let to = Math.min(totalPages, current + windowSize);
  if (current <= windowSize) to = Math.min(totalPages, 1 + windowSize * 2);
  if (current > totalPages - windowSize) from = Math.max(1, totalPages - windowSize * 2);
  for (let i = from; i <= to; i += 1) pageNumbers.push(i);

  return (
    <div
      className={`d-flex flex-wrap justify-content-between align-items-center gap-2 mt-3 ${className}`}
    >
      <div className="d-flex align-items-center gap-2">
        <span className="text-muted small">
          {total === 0
            ? t('pagination.noItems', { itemLabel: label })
            : t('pagination.showing', {
                start: start.toLocaleString(),
                end: end.toLocaleString(),
                total: total.toLocaleString(),
                itemLabel: label,
              })}
        </span>
        {onPageSizeChange && (
          <div className="d-flex align-items-center gap-1">
            <label className="text-muted small mb-0" htmlFor="page-size-select">
              {t('pagination.show')}
            </label>
            <select
              id="page-size-select"
              className="form-select form-select-sm w-auto"
              value={pageSize}
              onChange={(e) => onPageSizeChange(Number(e.target.value))}
              aria-label={t('pagination.itemsPerPage')}
            >
              {pageSizeOptions.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      <nav aria-label={t('pagination.pagination')}>
        <ul className="pagination pagination-sm mb-0">
          <li className={`page-item ${current === 1 ? 'disabled' : ''}`}>
            <button
              type="button"
              className="page-link"
              onClick={() => goTo(1)}
              disabled={current === 1}
              aria-label={t('pagination.firstPage')}
            >
              <span aria-hidden="true">&laquo;</span>
            </button>
          </li>
          <li className={`page-item ${current === 1 ? 'disabled' : ''}`}>
            <button
              type="button"
              className="page-link"
              onClick={() => goTo(current - 1)}
              disabled={current === 1}
              aria-label={t('pagination.previousPage')}
            >
              <span aria-hidden="true">&lsaquo;</span>
            </button>
          </li>
          {pageNumbers.map((p) => (
            <li key={p} className={`page-item ${p === current ? 'active' : ''}`}>
              <button type="button" className="page-link" onClick={() => goTo(p)}>
                {p}
              </button>
            </li>
          ))}
          <li className={`page-item ${current === totalPages ? 'disabled' : ''}`}>
            <button
              type="button"
              className="page-link"
              onClick={() => goTo(current + 1)}
              disabled={current === totalPages}
              aria-label={t('pagination.nextPage')}
            >
              <span aria-hidden="true">&rsaquo;</span>
            </button>
          </li>
          <li className={`page-item ${current === totalPages ? 'disabled' : ''}`}>
            <button
              type="button"
              className="page-link"
              onClick={() => goTo(totalPages)}
              disabled={current === totalPages}
              aria-label={t('pagination.lastPage')}
            >
              <span aria-hidden="true">&raquo;</span>
            </button>
          </li>
        </ul>
      </nav>
    </div>
  );
};

export default Pagination;
