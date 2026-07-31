/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useMemo, useState } from 'react';
import { useT } from '../i18n';

/**
 * RdapCompareModal
 *
 * Displays up to 4 RDAP request results side-by-side in columns,
 * with row-level shading to highlight differences across columns.
 *
 * Props:
 *   show        – boolean, controls modal visibility
 *   onHide      – callback to close the modal
 *   requests    – array of request objects (max 4) each with .rdap_data, .query_value, etc.
 */

// ─── Helpers ──────────────────────────────────────────────────────────────────

const flattenObject = (obj, prefix = '') => {
  const result = {};
  if (obj === null || obj === undefined) return result;

  if (Array.isArray(obj)) {
    obj.forEach((item, idx) => {
      const key = prefix ? `${prefix}.${idx}` : `${idx}`;
      if (typeof item === 'object' && item !== null) {
        Object.assign(result, flattenObject(item, key));
      } else {
        result[key] = item;
      }
    });
  } else if (typeof obj === 'object') {
    Object.entries(obj).forEach(([k, v]) => {
      const key = prefix ? `${prefix}.${k}` : k;
      if (typeof v === 'object' && v !== null) {
        Object.assign(result, flattenObject(v, key));
      } else {
        result[key] = v;
      }
    });
  } else {
    result[prefix] = obj;
  }
  return result;
};

const formatValue = (value) => {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'object') return JSON.stringify(value, null, 2);
  return String(value);
};

const humaniseKey = (key) =>
  key
    .split('.')
    .map((seg) =>
      /^\d+$/.test(seg)
        ? seg
        : seg
            .replace(/([a-z])([A-Z])/g, '$1 $2')
            .replace(/_/g, ' ')
            .replace(/\b\w/g, (c) => c.toUpperCase())
    )
    .join(' › ');

const groupBySection = (keys) => {
  const sections = {};
  keys.forEach((key) => {
    const topLevel = key.split('.')[0];
    if (!sections[topLevel]) sections[topLevel] = [];
    sections[topLevel].push(key);
  });
  return sections;
};

// ─── Sub-components ───────────────────────────────────────────────────────────

const SectionHeader = ({ label, isOpen, onToggle, diffCount }) => {
  const { t } = useT();
  return (
    <tr
      role="button"
      onClick={onToggle}
      className="table-secondary"
      style={{ cursor: 'pointer' }}
    >
      <td
        colSpan={100}
        className="fw-semibold small py-2 px-3"
      >
        <i className={`bi bi-chevron-${isOpen ? 'down' : 'right'} me-2`}></i>
        {humaniseKey(label)}
        {diffCount > 0 && (
          <span className="badge bg-warning text-dark ms-2">{diffCount} {t(diffCount > 1 ? 'rdapCompareModal.diffs' : 'rdapCompareModal.diff')}</span>
        )}
      </td>
    </tr>
  );
};

const CompareRow = ({ label, values, isDifferent }) => {
  const bgClass = isDifferent ? 'table-warning' : '';
  return (
    <tr className={bgClass}>
      <td
        className="small text-muted text-break px-3 py-1"
        style={{ minWidth: 180, maxWidth: 260 }}
        title={label}
      >
        {humaniseKey(label)}
      </td>
      {values.map((val, idx) => (
        <td
          key={idx}
          className="small text-break px-3 py-1 font-monospace"
          style={{ maxWidth: 320, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}
        >
          {formatValue(val)}
        </td>
      ))}
    </tr>
  );
};

// ─── Sticky header styles ─────────────────────────────────────────────────────

const stickyThStyle = {
  position: 'sticky',
  top: 0,
  zIndex: 2,
  backgroundColor: '#f8f9fa',
  boxShadow: '0 1px 0 rgba(0,0,0,.1)',
};

// ─── Main Component ───────────────────────────────────────────────────────────

const RdapCompareModal = ({ show, onHide, requests = [] }) => {
  const { t } = useT();
  const [collapsedSections, setCollapsedSections] = useState({});
  const [showDiffsOnly, setShowDiffsOnly] = useState(false);

  const { allKeys, flatMaps, sections, diffKeys } = useMemo(() => {
    if (!requests.length) return { allKeys: [], flatMaps: [], sections: {}, diffKeys: new Set() };

    const maps = requests.map((r) => flattenObject(r.rdap_data || {}));

    const keySet = new Set();
    maps.forEach((m) => Object.keys(m).forEach((k) => keySet.add(k)));
    const sorted = Array.from(keySet).sort();

    const diffs = new Set();
    sorted.forEach((key) => {
      const vals = maps.map((m) => formatValue(m[key]));
      if (vals.some((v) => v !== vals[0])) diffs.add(key);
    });

    return {
      allKeys: sorted,
      flatMaps: maps,
      sections: groupBySection(sorted),
      diffKeys: diffs,
    };
  }, [requests]);

  const toggleSection = (section) => {
    setCollapsedSections((prev) => ({ ...prev, [section]: !prev[section] }));
  };

  if (!show) return null;

  const itemCount = requests.length;

  return (
    <>
      {/* Backdrop */}
      <div
        className={`modal-backdrop fade ${show ? 'show' : ''}`}
        onClick={onHide}
      />

      {/* Modal */}
      <div
        className="modal fade show d-block"
        tabIndex={-1}
        role="dialog"
        onClick={onHide}
      >
        <div
          className="modal-dialog modal-xl modal-dialog-scrollable"
          role="document"
          onClick={(e) => e.stopPropagation()}
        >
          <div className="modal-content">
            {/* Header */}
            <div className="modal-header border-bottom">
              <div className="d-flex align-items-center gap-3">
                <div>
                  <h5 className="modal-title mb-0">
                    <i className="bi bi-layout-three-columns me-2"></i>
                    {t('rdapCompareModal.title')}
                  </h5>
                  <small className="text-muted">
                    {itemCount} {t(itemCount !== 1 ? 'rdapCompareModal.items' : 'rdapCompareModal.item')} ·{' '}
                    {diffKeys.size} {t(diffKeys.size !== 1 ? 'rdapCompareModal.differencesFound' : 'rdapCompareModal.differenceFound')}
                  </small>
                </div>
                <div className="form-check form-switch mb-0 ms-3">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    id="showDiffsOnly"
                    checked={showDiffsOnly}
                    onChange={(e) => setShowDiffsOnly(e.target.checked)}
                  />
                  <label className="form-check-label small" htmlFor="showDiffsOnly">
                    {t('rdapCompareModal.differencesOnly')}
                  </label>
                </div>
              </div>
              <button type="button" className="btn-close" onClick={onHide}></button>
            </div>

            {/* Body */}
            <div className="modal-body p-0" style={{ overflowY: 'auto', maxHeight: '70vh' }}>
              {itemCount < 2 ? (
                <div className="text-center p-5">
                  <i className="bi bi-exclamation-circle display-4 text-muted"></i>
                  <p className="mt-3 text-muted">{t('rdapCompareModal.selectAtLeastTwo')}</p>
                </div>
              ) : allKeys.length === 0 ? (
                <div className="text-center p-5">
                  <i className="bi bi-database-x display-4 text-muted"></i>
                  <p className="mt-3 text-muted">{t('rdapCompareModal.noDataForSelected')}</p>
                </div>
              ) : (
                <table className="table table-sm table-bordered mb-0 align-middle" style={{ tableLayout: 'auto' }}>
                  <thead>
                    <tr>
                      <th className="px-3 small" style={{ ...stickyThStyle, minWidth: 180 }}>{t('rdapCompareModal.field')}</th>
                      {requests.map((req, idx) => (
                        <th key={idx} className="px-3 text-center" style={{ ...stickyThStyle, minWidth: 200 }}>
                          <div className="small fw-semibold">{req.query_value}</div>
                          <span className={`badge ${req.status === 'approved' ? 'bg-success' : 'bg-secondary'} mt-1`}>
                            {req.query_type?.toUpperCase()}
                          </span>
                          {req.agreements_used?.length > 0 && (
                            <span className="badge bg-info ms-1 mt-1">
                              {req.agreements_used.length} {t('rdapCompareModal.agmt')}
                            </span>
                          )}
                        </th>
                      ))}
                    </tr>
                  </thead>

                  <tbody>
                    {Object.entries(sections).map(([section, keys]) => {
                      const isOpen = !collapsedSections[section];
                      const visibleKeys = showDiffsOnly
                        ? keys.filter((k) => diffKeys.has(k))
                        : keys;

                      const sectionDiffCount = keys.filter((k) => diffKeys.has(k)).length;

                      if (showDiffsOnly && sectionDiffCount === 0) return null;

                      return (
                        <React.Fragment key={section}>
                          <SectionHeader
                            label={section}
                            isOpen={isOpen}
                            onToggle={() => toggleSection(section)}
                            diffCount={sectionDiffCount}
                          />
                          {isOpen &&
                            visibleKeys.map((key) => {
                              const isDiff = diffKeys.has(key);
                              const shortKey = key.includes('.')
                                ? key.substring(key.indexOf('.') + 1)
                                : key;

                              return (
                                <CompareRow
                                  key={key}
                                  label={shortKey}
                                  values={flatMaps.map((m) => m[key])}
                                  isDifferent={isDiff}
                                />
                              );
                            })}
                        </React.Fragment>
                      );
                    })}
                  </tbody>
                </table>
              )}
            </div>

            {/* Footer legend */}
            <div className="modal-footer border-top d-flex justify-content-between">
              <div className="d-flex align-items-center gap-3 small text-muted">
                <span>
                  <span className="d-inline-block rounded me-1" style={{ width: 14, height: 14, verticalAlign: 'middle', backgroundColor: '#fff3cd' }}></span>
                  {t('rdapCompareModal.legendHighlight')}
                </span>
              </div>
              <button className="btn btn-secondary" onClick={onHide}>{t('rdapCompareModal.close')}</button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

export default RdapCompareModal;