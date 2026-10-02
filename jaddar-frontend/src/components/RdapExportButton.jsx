/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useRef, useEffect } from 'react';
import api from '../services/api';
import RdapExportModal from './RdapExportModal';
import { useT } from '../i18n';

/**
 * RdapExportButton — entry point for exporting RDAP data to Excel or PDF.
 *
 * Usage modes:
 *   1. Single current result (RDAPSearch): pass `currentResult`. There is nothing
 *      to customise for a single record, so this opens a small format dropdown.
 *   2. Bulk export from a list (RdapRequests): pass the list's pagination state.
 *      This opens RdapExportModal, where the user picks the format, how many rows
 *      and which columns to export.
 *
 * Props:
 *   - currentResult: object — the current RDAP search result (single export mode)
 *   - selectedIds: string[] — request_ids ticked in the list
 *   - statusFilter: string — the list's active status filter
 *   - page / pageSize / totalItems: the list's pagination state (bulk mode)
 *   - variant: string — Bootstrap button variant (default: "outline-secondary")
 *   - size: string — "sm" | "md" (default: "sm")
 *   - label: string — button label (default: "Export")
 */
const RdapExportButton = ({
  currentResult = null,
  selectedIds = null,
  statusFilter = '',
  page = 1,
  pageSize = 50,
  totalItems = 0,
  variant = 'outline-secondary',
  size = 'sm',
  label = 'Export',
}) => {
  const { t } = useT();
  const [open, setOpen] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [exporting, setExporting] = useState(null); // 'excel' | 'pdf' | null
  const dropdownRef = useRef(null);

  // Close dropdown on outside click
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const downloadBlob = (data, filename, mimeType) => {
    const blob = new Blob([data], { type: mimeType });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  };

  const extractFilename = (headers, fallback) => {
    const disposition = headers['content-disposition'];
    if (disposition) {
      const match = disposition.match(/filename="?([^";\n]+)"?/);
      if (match) return match[1];
    }
    return fallback;
  };

  // Export current in-view result (from RDAPSearch)
  const exportCurrentResult = async (format) => {
    if (!currentResult) return;
    setExporting(format);
    try {
      const endpoint = `/api/rdap/export/current/${format === 'excel' ? 'excel' : 'pdf'}`;
      const mimeType =
        format === 'excel'
          ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
          : 'application/pdf';

      // Build the payload from the current result
      const payload = {
        query_type: currentResult.query_type,
        query_value: currentResult.raw_data?.ldhName || currentResult.raw_data?.handle || '',
        status: currentResult.denied ? 'denied' : currentResult.pending ? 'pending' : 'approved',
        source: currentResult.source,
        accessLevel: currentResult.accessLevel,
        agreement_name: currentResult.agreement_name,
        timestamp: currentResult.timestamp,
        rdapServer: currentResult.rdapServer,
        raw_data: currentResult.raw_data,
        rdap_data: currentResult.raw_data,
      };

      const response = await api.post(endpoint, payload, {
        responseType: 'arraybuffer',
      });

      const ext = format === 'excel' ? 'xlsx' : 'pdf';
      const fallbackName = `rdap_export.${ext}`;
      const filename = extractFilename(response.headers, fallbackName);
      downloadBlob(response.data, filename, mimeType);
    } catch (err) {
      console.error(`Export to ${format} failed:`, err);
      alert(t('rdapExport.failed', { format: format.toUpperCase() }));
    } finally {
      setExporting(null);
      setOpen(false);
    }
  };

  const isExporting = exporting !== null;

  // Bulk mode: every option lives in the modal, so the button just opens it.
  if (!currentResult) {
    return (
      <>
        <button
          className={`btn btn-${variant} btn-${size}`}
          type="button"
          onClick={() => setShowModal(true)}
        >
          <i className="bi bi-download me-1"></i>
          {label}
        </button>
        <RdapExportModal
          show={showModal}
          onHide={() => setShowModal(false)}
          page={page}
          pageSize={pageSize}
          totalItems={totalItems}
          selectedIds={selectedIds || []}
          statusFilter={statusFilter}
        />
      </>
    );
  }

  return (
    <div className="dropdown" ref={dropdownRef}>
      <button
        className={`btn btn-${variant} btn-${size} dropdown-toggle`}
        type="button"
        onClick={() => setOpen(!open)}
        disabled={isExporting}
      >
        {isExporting ? (
          <>
            <span className="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span>
            {t('rdapExport.exporting')}
          </>
        ) : (
          <>
            <i className="bi bi-download me-1"></i>
            {label}
          </>
        )}
      </button>
      {open && !isExporting && (
        <div className="dropdown-menu show" style={{ minWidth: '200px' }}>
          <div className="dropdown-header text-muted small">{t('rdapExport.descCurrent')}</div>
          <button className="dropdown-item" onClick={() => exportCurrentResult('excel')}>
            <i className="bi bi-file-earmark-excel me-2 text-success"></i>
            {t('rdapExport.toExcel')}
          </button>
          <button className="dropdown-item" onClick={() => exportCurrentResult('pdf')}>
            <i className="bi bi-file-earmark-pdf me-2 text-danger"></i>
            {t('rdapExport.toPdf')}
          </button>
        </div>
      )}
    </div>
  );
};

export default RdapExportButton;
