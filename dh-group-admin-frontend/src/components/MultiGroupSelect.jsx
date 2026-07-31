/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useRef, useEffect } from 'react';
import { useT } from '../i18n';

/**
 * A dropdown that shows checkboxes for selecting multiple data holder groups.
 * Closes when the user clicks outside of it.
 *
 * Props:
 *   groups        – array of { id, name } objects
 *   selectedIds   – array of currently selected group IDs
 *   onChange       – (newSelectedIds: number[]) => void
 *   placeholder   – text when nothing is selected (default: "Select groups…")
 *   isInvalid     – boolean, shows red border
 *   errorMessage  – string shown below when isInvalid
 *   label         – optional label text above the dropdown
 *   required      – shows * after label
 *   helpText      – optional small text below
 */
const MultiGroupSelect = ({
  groups = [],
  selectedIds = [],
  onChange,
  placeholder,
  isInvalid = false,
  errorMessage,
  label,
  required = false,
  helpText,
}) => {
  const { t } = useT();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const placeholderText = placeholder ?? t('multiGroupSelect.selectPlaceholder');

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  const toggle = (id) => {
    const next = selectedIds.includes(id)
      ? selectedIds.filter(x => x !== id)
      : [...selectedIds, id];
    onChange(next);
  };

  const selectedNames = groups
    .filter(g => selectedIds.includes(g.id))
    .map(g => g.name);

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      {label && (
        <label className="form-label">
          {label} {required && <span className="text-danger">*</span>}
        </label>
      )}

      {/* Trigger button styled like a form-select */}
      <div
        className={`form-select d-flex align-items-center ${isInvalid ? 'is-invalid' : ''}`}
        style={{ cursor: 'pointer', minHeight: 38, height: 'auto', paddingRight: 32 }}
        onClick={() => setOpen(prev => !prev)}
        tabIndex={0}
        onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setOpen(p => !p); } }}
      >
        {selectedNames.length === 0 ? (
          <span className="text-muted">{placeholderText}</span>
        ) : (
          <div className="d-flex flex-wrap gap-1" style={{ margin: '-2px 0' }}>
            {selectedNames.map(name => (
              <span key={name} className="badge bg-primary fw-normal" style={{ fontSize: 12 }}>
                {name}
              </span>
            ))}
          </div>
        )}
      </div>

      {isInvalid && errorMessage && (
        <div className="invalid-feedback" style={{ display: 'block' }}>{errorMessage}</div>
      )}

      {/* Dropdown panel */}
      {open && (
        <div
          className="border rounded bg-white shadow-sm"
          style={{
            position: 'absolute',
            top: '100%',
            left: 0,
            right: 0,
            zIndex: 1050,
            maxHeight: 220,
            overflowY: 'auto',
            marginTop: 2,
          }}
        >
          {groups.length === 0 ? (
            <div className="text-muted small p-2">{t('multiGroupSelect.noGroupsAvailable')}</div>
          ) : groups.map(g => (
            <label
              key={g.id}
              className="d-flex align-items-center px-3 py-2 m-0"
              style={{ cursor: 'pointer', borderBottom: '1px solid #f0f0f0' }}
              onMouseEnter={e => (e.currentTarget.style.backgroundColor = '#f8f9fa')}
              onMouseLeave={e => (e.currentTarget.style.backgroundColor = '')}
            >
              <input
                type="checkbox"
                className="form-check-input me-2 mt-0"
                checked={selectedIds.includes(g.id)}
                onChange={() => toggle(g.id)}
              />
              {g.name}
            </label>
          ))}
        </div>
      )}

      {helpText && <div className="form-text">{helpText}</div>}
    </div>
  );
};

export default MultiGroupSelect;
