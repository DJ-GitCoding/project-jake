/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useMemo, useRef, useEffect } from 'react';
import { RDRS_COUNTRIES, countryLabel } from '../constants/rdrsCountries';

/**
 * Searchable country picker, in the two shapes ICANN's RDRS form uses.
 */
const CountrySelect = ({
  mode = 'name',
  multiple = false,
  value,
  onChange,
  id,
  placeholder = 'Select…',
  disabled = false,
  invalid = false,
}) => {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [highlight, setHighlight] = useState(0);
  const boxRef = useRef(null);
  const searchRef = useRef(null);

  const selected = useMemo(
    () => (multiple ? (Array.isArray(value) ? value : []) : (value ? [value] : [])),
    [value, multiple]
  );

  const options = useMemo(() => {
    const seen = new Set();
    return RDRS_COUNTRIES
      .map(c => ({ value: mode === 'code' ? c.code : c.name, label: countryLabel(c), code: c.code }))
      .filter(o => (seen.has(o.value) ? false : seen.add(o.value)))
      .sort((a, b) => a.value.localeCompare(b.value));
  }, [mode]);

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return options;
    return options.filter(o =>
      o.label.toLowerCase().includes(q) || o.value.toLowerCase().includes(q) || o.code.toLowerCase().includes(q)
    );
  }, [options, search]);

  useEffect(() => {
    if (!open) return;
    const onClickAway = (e) => {
      if (boxRef.current && !boxRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', onClickAway);
    return () => document.removeEventListener('mousedown', onClickAway);
  }, [open]);

  useEffect(() => {
    if (open) searchRef.current?.focus();
    else { setSearch(''); setHighlight(0); }
  }, [open]);

  const pick = (option) => {
    if (multiple) {
      const next = selected.includes(option.value)
        ? selected.filter(v => v !== option.value)
        : [...selected, option.value];
      onChange(next);
    } else {
      onChange(option.value);
      setOpen(false);
    }
  };

  const clear = (e) => {
    e.stopPropagation();
    onChange(multiple ? [] : '');
  };

  const onKeyDown = (e) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setHighlight(h => Math.min(h + 1, visible.length - 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setHighlight(h => Math.max(h - 1, 0)); }
    else if (e.key === 'Enter') { e.preventDefault(); if (visible[highlight]) pick(visible[highlight]); }
    else if (e.key === 'Escape') { setOpen(false); }
  };

  const labelFor = (v) => options.find(o => o.value === v)?.label || v;

  return (
    <div className="position-relative" ref={boxRef}>
      <div
        id={id}
        role="combobox"
        aria-expanded={open}
        aria-haspopup="listbox"
        tabIndex={disabled ? -1 : 0}
        className={`form-control form-control-sm d-flex align-items-center flex-wrap gap-1 ${invalid ? 'is-invalid' : ''} ${disabled ? 'bg-body-secondary' : ''}`}
        style={{ minHeight: 31, cursor: disabled ? 'not-allowed' : 'pointer' }}
        onClick={() => !disabled && setOpen(o => !o)}
        onKeyDown={(e) => { if (!disabled && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); setOpen(o => !o); } }}
      >
        {selected.length === 0 && <span className="text-muted">{placeholder}</span>}
        {multiple
          ? selected.map(v => (
              <span key={v} className="badge bg-secondary-subtle text-secondary-emphasis border" style={{ fontSize: 11 }}>
                {labelFor(v)}
                {!disabled && (
                  <span role="button" aria-label={`Remove ${labelFor(v)}`} className="ms-1"
                    onClick={(e) => { e.stopPropagation(); onChange(selected.filter(s => s !== v)); }}>×</span>
                )}
              </span>
            ))
          : selected.length > 0 && <span>{labelFor(selected[0])}</span>}
        {selected.length > 0 && !disabled && (
          <span role="button" aria-label="Clear" className="ms-auto text-muted" onClick={clear}>×</span>
        )}
      </div>

      {open && !disabled && (
        <div className="border rounded bg-body shadow-sm position-absolute w-100"
          style={{ zIndex: 1056, maxHeight: 260, overflowY: 'auto' }}>
          <div className="p-1 border-bottom position-sticky top-0 bg-body">
            <input
              ref={searchRef}
              type="text"
              className="form-control form-control-sm"
              placeholder="Search…"
              value={search}
              onChange={(e) => { setSearch(e.target.value); setHighlight(0); }}
              onKeyDown={onKeyDown}
            />
          </div>
          <ul className="list-unstyled mb-0" role="listbox">
            {visible.length === 0 && <li className="px-2 py-1 text-muted small">No matches</li>}
            {visible.map((o, i) => {
              const isSelected = selected.includes(o.value);
              return (
                <li
                  key={o.value}
                  role="option"
                  aria-selected={isSelected}
                  className={`px-2 py-1 small d-flex align-items-center ${i === highlight ? 'bg-primary-subtle' : ''} ${isSelected ? 'fw-semibold' : ''}`}
                  style={{ cursor: 'pointer' }}
                  onMouseEnter={() => setHighlight(i)}
                  onClick={() => pick(o)}
                >
                  {multiple && (
                    <input type="checkbox" className="form-check-input me-2 mt-0" checked={isSelected} readOnly />
                  )}
                  <span>{o.label}</span>
                  {mode === 'name' && o.code && (
                    <span className="text-muted ms-auto" style={{ fontSize: 10 }}>{o.code}</span>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </div>
  );
};

export default CountrySelect;
