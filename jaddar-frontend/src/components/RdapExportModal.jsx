/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useEffect, useMemo, useState } from 'react';
import api from '../services/api';
import { useT } from '../i18n';

/**
 * Column keys understood by the bulk export endpoints. These must stay in sync
 * with RdapExportService.REQUEST_COLUMNS on the backend — the server renders the
 * columns in its own canonical order, so the order here is only what the user sees.
 */
export const EXPORT_COLUMNS = [
  'status',
  'query_type',
  'query_value',
  'agreements',
  'access_level',
  'data_holder',
  'created',
  'resolved',
  'error',
];

/**
 * RdapExportModal — export options for the RDAP requests list.
 *
 * Lets the user pick the format, how many rows to export (the page they are on,
 * their selection, a custom row count, or everything), which columns to include,
 * and whether the full RDAP response data is appended.
 *
 * Props:
 *   - show / onHide: visibility
 *   - page, pageSize, totalItems: the list's current pagination state
 *   - selectedIds: string[] — currently ticked rows (may be empty)
 *   - statusFilter: string — the list's active status filter (may be empty)
 */
const RdapExportModal = ({
  show,
  onHide,
  page = 1,
  pageSize = 50,
  totalItems = 0,
  selectedIds = [],
  statusFilter = '',
}) => {
  const { t } = useT();
  const [format, setFormat] = useState('excel');
  const [scope, setScope] = useState('page');
  const [customRows, setCustomRows] = useState(pageSize);
  const [columns, setColumns] = useState(EXPORT_COLUMNS);
  const [includeRdapData, setIncludeRdapData] = useState(true);
  const [applyStatusFilter, setApplyStatusFilter] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState(null);

  const hasSelection = selectedIds.length > 0;

  useEffect(() => {
    if (!show) return;
    setScope(hasSelection ? 'selected' : 'page');
    setCustomRows(pageSize);
    setApplyStatusFilter(true);
    setError(null);
  }, [show, hasSelection, pageSize]);

  const pageStart = (page - 1) * pageSize;
  const pageRows = Math.max(0, Math.min(pageSize, totalItems - pageStart));

  const rowCount = useMemo(() => {
    if (scope === 'selected') return selectedIds.length;
    if (scope === 'page') return pageRows;
    if (scope === 'custom') return Math.min(customRows || 0, totalItems);
    return totalItems;
  }, [scope, selectedIds.length, pageRows, customRows, totalItems]);

  const toggleColumn = (key) => {
    setColumns((prev) =>
      prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]
    );
  };

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

  const handleExport = async () => {
    setExporting(true);
    setError(null);
    try {
      const params = {};
      // An explicit selection already names its rows, so the list's status
      // filter would only be able to take rows away from it.
      if (statusFilter && applyStatusFilter && scope !== 'selected') {
        params.status = statusFilter;
      }

      if (scope === 'selected') {
        params.request_ids = selectedIds.join(',');
      } else if (scope === 'page') {
        params.offset = pageStart;
        params.limit = pageSize;
      } else if (scope === 'custom') {
        params.limit = customRows;
      }

      if (columns.length !== EXPORT_COLUMNS.length) {
        params.columns = columns.join(',');
      }
      if (!includeRdapData) params.include_rdap_data = false;

      const mimeType =
        format === 'excel'
          ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
          : 'application/pdf';

      const response = await api.get(`/api/rdap/export/requests/${format}`, {
        params,
        responseType: 'arraybuffer',
      });

      const fallbackName = `rdap_requests.${format === 'excel' ? 'xlsx' : 'pdf'}`;
      downloadBlob(response.data, extractFilename(response.headers, fallbackName), mimeType);
      onHide();
    } catch (err) {
      console.error(`Export to ${format} failed:`, err);
      setError(t('rdapExport.failed', { format: format.toUpperCase() }));
    } finally {
      setExporting(false);
    }
  };

  if (!show) return null;

  const scopeOption = (value, label, hint) => (
    <div className="form-check">
      <input
        className="form-check-input"
        type="radio"
        name="exportScope"
        id={`exportScope-${value}`}
        value={value}
        checked={scope === value}
        disabled={exporting}
        onChange={() => setScope(value)}
      />
      <label className="form-check-label" htmlFor={`exportScope-${value}`}>
        {label}
        {hint && <span className="text-muted small ms-2">{hint}</span>}
      </label>
    </div>
  );

  return (
    <>
      <div className="modal-backdrop fade show" onClick={exporting ? undefined : onHide}></div>
      <div
        className="modal fade show d-block"
        tabIndex={-1}
        role="dialog"
        onClick={exporting ? undefined : onHide}
      >
        <div
          className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable"
          role="document"
          onClick={(e) => e.stopPropagation()}
        >
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">
                <i className="bi bi-download me-2"></i>
                {t('rdapExport.modal.title')}
              </h5>
              <button type="button" className="btn-close" onClick={onHide} disabled={exporting}></button>
            </div>

            <div className="modal-body">
              {error && (
                <div className="alert alert-danger" role="alert">
                  <i className="bi bi-exclamation-triangle me-2"></i>
                  {error}
                </div>
              )}

              {/* Format */}
              <div className="mb-4">
                <label className="form-label fw-semibold">{t('rdapExport.modal.format')}</label>
                <div className="d-flex gap-2">
                  <button
                    type="button"
                    className={`btn ${format === 'excel' ? 'btn-success' : 'btn-outline-success'}`}
                    onClick={() => setFormat('excel')}
                    disabled={exporting}
                  >
                    <i className="bi bi-file-earmark-excel me-2"></i>
                    {t('rdapExport.modal.excel')}
                  </button>
                  <button
                    type="button"
                    className={`btn ${format === 'pdf' ? 'btn-danger' : 'btn-outline-danger'}`}
                    onClick={() => setFormat('pdf')}
                    disabled={exporting}
                  >
                    <i className="bi bi-file-earmark-pdf me-2"></i>
                    {t('rdapExport.modal.pdf')}
                  </button>
                </div>
              </div>

              {/* Rows */}
              <div className="mb-4">
                <label className="form-label fw-semibold">{t('rdapExport.modal.rows')}</label>
                {hasSelection &&
                  scopeOption(
                    'selected',
                    t('rdapExport.modal.scopeSelected'),
                    t('rdapExport.modal.rowCount', { count: selectedIds.length })
                  )}
                {scopeOption(
                  'page',
                  t('rdapExport.modal.scopePage', { page }),
                  t('rdapExport.modal.rowCount', { count: pageRows })
                )}
                {scopeOption('custom', t('rdapExport.modal.scopeCustom'))}
                {scope === 'custom' && (
                  <div className="ms-4 mt-2 mb-2" style={{ maxWidth: '260px' }}>
                    <div className="input-group input-group-sm">
                      <input
                        type="number"
                        className="form-control"
                        min="1"
                        max={totalItems || 1}
                        value={customRows}
                        onChange={(e) => setCustomRows(Number(e.target.value))}
                        disabled={exporting}
                        aria-label={t('rdapExport.modal.scopeCustom')}
                      />
                      <span className="input-group-text">
                        {t('rdapExport.modal.ofTotal', { total: totalItems })}
                      </span>
                    </div>
                    <div className="form-text">{t('rdapExport.modal.customHint')}</div>
                  </div>
                )}
                {scopeOption(
                  'all',
                  t('rdapExport.modal.scopeAll'),
                  t('rdapExport.modal.rowCount', { count: totalItems })
                )}
              </div>

              {/* Status filter */}
              {statusFilter && (
                <div className="mb-4">
                  <label className="form-label fw-semibold">{t('rdapExport.modal.filter')}</label>
                  <div className="form-check form-switch">
                    <input
                      className="form-check-input"
                      type="checkbox"
                      id="exportApplyStatus"
                      checked={applyStatusFilter}
                      onChange={(e) => setApplyStatusFilter(e.target.checked)}
                      disabled={exporting || scope === 'selected'}
                    />
                    <label className="form-check-label" htmlFor="exportApplyStatus">
                      {t('rdapExport.modal.applyStatusFilter', {
                        status: statusFilter.toUpperCase(),
                      })}
                    </label>
                  </div>
                </div>
              )}

              {/* Columns */}
              <div className="mb-4">
                <div className="d-flex justify-content-between align-items-center mb-2">
                  <label className="form-label fw-semibold mb-0">
                    {t('rdapExport.modal.columns')}
                  </label>
                  <div className="btn-group btn-group-sm">
                    <button
                      type="button"
                      className="btn btn-outline-secondary"
                      onClick={() => setColumns(EXPORT_COLUMNS)}
                      disabled={exporting}
                    >
                      {t('rdapExport.modal.selectAll')}
                    </button>
                    <button
                      type="button"
                      className="btn btn-outline-secondary"
                      onClick={() => setColumns([])}
                      disabled={exporting}
                    >
                      {t('rdapExport.modal.clearAll')}
                    </button>
                  </div>
                </div>
                <div className="row g-1">
                  {EXPORT_COLUMNS.map((key) => (
                    <div className="col-md-4" key={key}>
                      <div className="form-check">
                        <input
                          className="form-check-input"
                          type="checkbox"
                          id={`exportCol-${key}`}
                          checked={columns.includes(key)}
                          onChange={() => toggleColumn(key)}
                          disabled={exporting}
                        />
                        <label className="form-check-label" htmlFor={`exportCol-${key}`}>
                          {t(`rdapExport.modal.column.${key}`)}
                        </label>
                      </div>
                    </div>
                  ))}
                </div>
                {columns.length === 0 && (
                  <div className="form-text text-warning">
                    <i className="bi bi-exclamation-triangle me-1"></i>
                    {t('rdapExport.modal.noColumns')}
                  </div>
                )}
              </div>

              {/* RDAP response data */}
              <div>
                <label className="form-label fw-semibold">{t('rdapExport.modal.content')}</label>
                <div className="form-check form-switch">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    id="exportIncludeRdap"
                    checked={includeRdapData}
                    onChange={(e) => setIncludeRdapData(e.target.checked)}
                    disabled={exporting}
                  />
                  <label className="form-check-label" htmlFor="exportIncludeRdap">
                    {t('rdapExport.modal.includeRdapData')}
                  </label>
                </div>
                <div className="form-text">
                  {format === 'excel'
                    ? t('rdapExport.modal.includeRdapHintExcel')
                    : t('rdapExport.modal.includeRdapHintPdf')}
                </div>
              </div>
            </div>

            <div className="modal-footer justify-content-between">
              <span className="text-muted small">
                {t('rdapExport.modal.summary', {
                  rows: rowCount,
                  columns: columns.length,
                })}
              </span>
              <div className="d-flex gap-2">
                <button type="button" className="btn btn-secondary" onClick={onHide} disabled={exporting}>
                  {t('rdapExport.modal.cancel')}
                </button>
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={handleExport}
                  disabled={exporting || columns.length === 0 || rowCount === 0}
                >
                  {exporting ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                      {t('rdapExport.exporting')}
                    </>
                  ) : (
                    <>
                      <i className="bi bi-download me-1"></i>
                      {t('rdapExport.modal.confirm')}
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

export default RdapExportModal;
