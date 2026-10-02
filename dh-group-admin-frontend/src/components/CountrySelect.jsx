/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { RDRS_COUNTRIES, COUNTRY_REGIONS, countryFlag } from '../constants/countries';
import { useT } from '../i18n';

/**
 * Country picker: countries nested under their ICANN region, each region collapsible,
 * with a search that flattens the tree while something is typed.
 *
 * The stored value is the country name rather than its code, matching what the
 * subscription form has always sent to data holders.
 */
const CountrySelect = ({ id, value, onChange, disabled = false, required = false }) => {
  const { t } = useT();
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [expanded, setExpanded] = useState([]);
  const [anchor, setAnchor] = useState(null);
  const boxRef = useRef(null);
  const menuRef = useRef(null);

  const placeMenu = useCallback(() => {
    const trigger = boxRef.current;
    if (!trigger) return;
    const rect = trigger.getBoundingClientRect();
    setAnchor({ top: rect.bottom + 4, left: rect.left, width: rect.width });
  }, []);

  const byRegion = useMemo(() => {
    const groups = new Map(COUNTRY_REGIONS.map(r => [r.code, { ...r, countries: [] }]));
    for (const country of RDRS_COUNTRIES) {
      const group = groups.get(country.region);
      if (group) group.countries.push(country);
    }
    for (const group of groups.values()) {
      group.countries.sort((a, b) => a.name.localeCompare(b.name));
    }
    return [...groups.values()];
  }, []);

  const matches = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return null;
    return RDRS_COUNTRIES
      .filter(c => c.name.toLowerCase().includes(q) || c.code.toLowerCase().includes(q))
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [search]);

  useEffect(() => {
    if (!open) {
      setAnchor(null);
      return undefined;
    }
    placeMenu();

    const reposition = () => placeMenu();
    window.addEventListener('resize', reposition);
    window.addEventListener('scroll', reposition, true);
    const onClickAway = (e) => {
      const insideTrigger = boxRef.current && boxRef.current.contains(e.target);
      const insideMenu = menuRef.current && menuRef.current.contains(e.target);
      if (!insideTrigger && !insideMenu) setOpen(false);
    };
    document.addEventListener('mousedown', onClickAway);
    return () => {
      window.removeEventListener('resize', reposition);
      window.removeEventListener('scroll', reposition, true);
      document.removeEventListener('mousedown', onClickAway);
    };
  }, [open, placeMenu]);

  useEffect(() => {
    if (!open || !value) return;
    const chosen = RDRS_COUNTRIES.find(c => c.name === value);
    if (chosen) setExpanded(prev => (prev.includes(chosen.region) ? prev : [...prev, chosen.region]));
  }, [open, value]);

  const selectedFlag = useMemo(() => {
    const chosen = RDRS_COUNTRIES.find(c => c.name === value);
    return chosen ? countryFlag(chosen.code) : '';
  }, [value]);

  const toggleRegion = (code) =>
    setExpanded(prev => (prev.includes(code) ? prev.filter(c => c !== code) : [...prev, code]));

  const choose = (name) => {
    onChange(name);
    setOpen(false);
    setSearch('');
  };

  const option = (country) => (
    <button
      type="button"
      key={country.code}
      className={`dropdown-item d-flex justify-content-between align-items-center ${country.name === value ? 'active' : ''}`}
      onClick={() => choose(country.name)}
    >
      <span className="text-truncate">
        <span className="me-2" aria-hidden="true">{countryFlag(country.code)}</span>
        {country.name}
      </span>
      <span className={`small ms-2 ${country.name === value ? '' : 'text-muted'}`}>{country.code}</span>
    </button>
  );

  return (
    <div className="position-relative" ref={boxRef}>
      <button
        type="button"
        id={id}
        className="form-control text-start d-flex justify-content-between align-items-center"
        onClick={() => setOpen(v => !v)}
        disabled={disabled}
        aria-expanded={open}
      >
        <span className={`text-truncate ${value ? '' : 'text-muted'}`}>
          {selectedFlag && <span className="me-2" aria-hidden="true">{selectedFlag}</span>}
          {value || t('countrySelect.placeholder')}
          {!value && required && <span className="text-danger ms-1">*</span>}
        </span>
        <span className="d-flex align-items-center gap-2">
          {value && !disabled && (
            <span
              role="button"
              tabIndex={-1}
              className="text-muted"
              aria-label={t('countrySelect.clear')}
              onClick={(e) => { e.stopPropagation(); onChange(''); }}
            >
              <i className="fas fa-times"></i>
            </span>
          )}
          <i className={`fas fa-chevron-${open ? 'up' : 'down'} text-muted`}></i>
        </span>
      </button>

      {open && anchor && createPortal(
        <div
          ref={menuRef}
          className="dropdown-menu show p-0 shadow"
          style={{
            position: 'fixed',
            top: anchor.top,
            left: anchor.left,
            width: anchor.width,
            maxHeight: 320,
            overflowY: 'auto',
            // Above Bootstrap's modal (1055) so it is never painted over.
            zIndex: 1090,
          }}
        >
          <div className="p-2 border-bottom position-sticky top-0 bg-white">
            <input
              type="search"
              className="form-control form-control-sm"
              value={search}
              autoFocus
              placeholder={t('countrySelect.searchPlaceholder')}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          {matches ? (
            matches.length > 0
              ? matches.map(option)
              : <div className="px-3 py-2 text-muted small fst-italic">{t('countrySelect.noMatches')}</div>
          ) : (
            byRegion.map((region) => (
              <div key={region.code}>
                <button
                  type="button"
                  className="dropdown-item fw-semibold d-flex justify-content-between align-items-center"
                  onClick={() => toggleRegion(region.code)}
                  aria-expanded={expanded.includes(region.code)}
                >
                  <span>
                    <i className={`fas fa-chevron-${expanded.includes(region.code) ? 'down' : 'right'} me-2 small`}></i>
                    {region.label}
                  </span>
                  <span className="badge bg-light text-muted border">{region.countries.length}</span>
                </button>
                {expanded.includes(region.code) && (
                  <div className="ps-3">{region.countries.map(option)}</div>
                )}
              </div>
            ))
          )}
        </div>,
        document.body
      )}
    </div>
  );
};

export default CountrySelect;
