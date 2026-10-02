/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';

/**
 * Renders the admin-authored custom parameters of a request type.
 *
 * Covers all twelve data types the DH Group Admin console can define. Extracted from
 * RDAPSearch so the RDRS request form can render the same admin-defined extras
 * without duplicating the type switch.
 *
 * Props:
 *   params       - custom parameter definitions, already sorted
 *   values       - { [name]: value }
 *   onChange     - (name, value) => void
 *   onFileChange - (name, File|null) => void
 *   disabled     - disables every field
 *   onError      - (message) => void, called when a file breaks its size limit
 *   resetToken   - bump to remount the file inputs and drop their displayed selection
 */
const CustomParameterFields = ({ params, values, onChange, onFileChange, disabled = false, onError, resetToken = 0 }) => {
  if (!params || params.length === 0) return null;

  const requiredCount = params.filter(p => p.required).length;

  const handleFile = (param, event) => {
    const file = event.target.files?.[0];
    if (!file) {
      onChange(param.name, '');
      onFileChange?.(param.name, null);
      return;
    }
    if (param.maxFileSizeMb && file.size > param.maxFileSizeMb * 1024 * 1024) {
      onError?.(`File exceeds ${param.maxFileSizeMb}MB`);
      event.target.value = '';
      onChange(param.name, '');
      onFileChange?.(param.name, null);
      return;
    }
    onChange(param.name, file.name);
    onFileChange?.(param.name, file);
  };

  return (
    <div className="card border mt-2 mb-2">
      <div className="card-header bg-light py-1 px-3">
        <span className="fw-semibold small">
          <i className="bi bi-sliders me-1"></i>Parameters
          <span className="badge bg-secondary ms-1" style={{ fontSize: '10px' }}>{params.length}</span>
          {requiredCount > 0 && <span className="badge bg-danger ms-1" style={{ fontSize: '10px' }}>{requiredCount} req</span>}
        </span>
      </div>
      <div className="card-body py-2 px-3">
        <div className="row g-2">
          {params.map((param) => {
            const val = values[param.name] ?? '';
            const setVal = (v) => onChange(param.name, v);
            const inputId = `cparam-${param.name}`;
            const colClass = ['text', 'json'].includes(param.dataType) ? 'col-12' : 'col-md-6 col-lg-4';
            return (
              <div className={colClass} key={param.name}>
                <label className="form-label small mb-0 fw-medium" htmlFor={inputId}>
                  {param.name.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())}
                  {param.required && <span className="text-danger ms-1">*</span>}
                </label>
                {param.dataType === 'string' && <input type="text" id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} placeholder={param.placeholder || ''} maxLength={param.maxLength || undefined} disabled={disabled} required={param.required} />}
                {param.dataType === 'integer' && <input type="number" id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} placeholder={param.placeholder || ''} min={param.minValue || undefined} max={param.maxValue || undefined} step="1" disabled={disabled} required={param.required} />}
                {param.dataType === 'float' && <input type="number" id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} placeholder={param.placeholder || ''} min={param.minValue || undefined} max={param.maxValue || undefined} step="0.01" disabled={disabled} required={param.required} />}
                {param.dataType === 'boolean' && <div className="form-check form-switch mt-1"><input type="checkbox" id={inputId} className="form-check-input" checked={val === 'true' || val === true} onChange={(e) => setVal(e.target.checked ? 'true' : 'false')} disabled={disabled} /><label className="form-check-label small" htmlFor={inputId}>{val === 'true' || val === true ? 'Yes' : 'No'}</label></div>}
                {param.dataType === 'date' && <input type="date" id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} disabled={disabled} required={param.required} />}
                {param.dataType === 'datetime' && <input type="datetime-local" id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} disabled={disabled} required={param.required} />}
                {param.dataType === 'email' && <input type="email" id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} placeholder={param.placeholder || 'email@example.com'} maxLength={param.maxLength || undefined} disabled={disabled} required={param.required} />}
                {param.dataType === 'url' && <input type="url" id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} placeholder={param.placeholder || 'https://'} maxLength={param.maxLength || undefined} disabled={disabled} required={param.required} />}
                {param.dataType === 'enum' && <select id={inputId} className="form-select form-select-sm" value={val} onChange={(e) => setVal(e.target.value)} disabled={disabled} required={param.required}><option value="">{param.placeholder || '— Select —'}</option>{(param.enumValues || '').split(',').map(o => o.trim()).filter(Boolean).map(opt => <option key={opt} value={opt}>{opt}</option>)}</select>}
                {param.dataType === 'text' && <textarea id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} placeholder={param.placeholder || ''} rows={2} maxLength={param.maxLength || undefined} disabled={disabled} required={param.required} />}
                {param.dataType === 'json' && <textarea id={inputId} className="form-control form-control-sm font-monospace" value={val} onChange={(e) => setVal(e.target.value)} placeholder={param.placeholder || '{ }'} rows={2} disabled={disabled} required={param.required} style={{ fontSize: '11px' }} />}
                {param.dataType === 'file' && <input type="file" key={`${param.name}-${resetToken}`} id={inputId} className="form-control form-control-sm" accept={param.allowedFileTypes || undefined} onChange={(e) => handleFile(param, e)} disabled={disabled} required={param.required} />}
                {param.description && <div className="form-text" style={{ fontSize: '10px' }}>{param.description}</div>}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default CustomParameterFields;
