/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { useState, useMemo, useEffect } from 'react';
import { DEFAULT_PAGE_SIZE } from '../components/Pagination';

/**
 * Client-side pagination helper. Give it the already-filtered/sorted array and it
 * returns the current page slice plus the state/handlers the <Pagination> component needs.
 *
 * The page auto-resets to 1 whenever the total item count changes (e.g. a search filter
 * shrinks the list), so users never get stranded on an empty page.
 */
export default function usePagination(items, initialPageSize = DEFAULT_PAGE_SIZE) {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(initialPageSize);

  const list = Array.isArray(items) ? items : [];
  const totalItems = list.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));

  // Clamp the page if the list shrank beneath the current page.
  useEffect(() => {
    if (page > totalPages) setPage(totalPages);
  }, [page, totalPages]);

  const pageItems = useMemo(() => {
    const startIndex = (Math.min(page, totalPages) - 1) * pageSize;
    return list.slice(startIndex, startIndex + pageSize);
  }, [list, page, pageSize, totalPages]);

  const changePageSize = (size) => {
    setPageSize(size);
    setPage(1);
  };

  // Consumers call this when a search term / filter changes.
  const resetPage = () => setPage(1);

  return {
    page,
    pageSize,
    totalItems,
    totalPages,
    pageItems,
    setPage,
    setPageSize: changePageSize,
    resetPage,
  };
}
