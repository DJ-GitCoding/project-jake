/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useRef, useEffect, useMemo } from 'react';
import { useT } from '../i18n';

/**
 * A searchable dropdown that groups items by category.
 * Each item shows its display name, and optionally an icon/badge.
 *
 * Props:
 *   items          – array of { value, displayName, category, sensitive?, description? }
 *   value          – currently selected value (string)
 *   onChange       – (item) => void   — passes the full item object
 *   placeholder    – text when nothing is selected
 *   label          – optional label above the dropdown
 *   allowCustom    – if true, shows a "Custom…" option at the bottom
 *   className      – optional class on the outer wrapper
 */
const GroupedFieldSelect = ({
  items = [],
  value = '',
  onChange,
  placeholder,
  label,
  allowCustom = false,
  className = '',
}) => {
  const { t } = useT();
  const resolvedPlaceholder = placeholder !== undefined ? placeholder : t('groupedFieldSelect.selectPlaceholder');
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const ref = useRef(null);
  const searchRef = useRef(null);

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  // Focus search on open
  useEffect(() => {
    if (open && searchRef.current) searchRef.current.focus();
  }, [open]);

  // Group items by category
  const grouped = useMemo(() => {
    const groups = {};
    const q = search.toLowerCase();
    items.forEach(item => {
      if (q && !item.displayName.toLowerCase().includes(q) &&
          !item.value.toLowerCase().includes(q) &&
          !(item.category || '').toLowerCase().includes(q)) return;
      const cat = item.category || 'Other';
      if (!groups[cat]) groups[cat] = [];
      groups[cat].push(item);
    });
    return groups;
  }, [items, search]);

  const totalResults = Object.values(grouped).reduce((n, arr) => n + arr.length, 0);

  const selected = items.find(i => i.value === value);

  const handleSelect = (item) => {
    onChange(item);
    setOpen(false);
    setSearch('');
  };

  return (
    <div ref={ref} className={className} style={{ position: 'relative' }}>
      {label && <label className="form-label">{label}</label>}

      {/* Trigger */}
      <div
        className="form-select"
        style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'space-between', minHeight: 38 }}
        onClick={() => setOpen(prev => !prev)}
        tabIndex={0}
        onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setOpen(p => !p); } }}
      >
        {selected ? (
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            {selected.sensitive && <i className="fa-solid fa-lock" style={{ fontSize: 10, color: 'var(--accent-warning, #f59e0b)' }} />}
            <span>{selected.displayName}</span>
            <span style={{ fontSize: 11, color: 'var(--text-tertiary, #999)', marginLeft: 4 }}>({selected.category})</span>
          </span>
        ) : (
          <span style={{ color: 'var(--text-tertiary, #999)' }}>{resolvedPlaceholder}</span>
        )}
      </div>

      {/* Dropdown panel */}
      {open && (
        <div style={{
          position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 1050,
          marginTop: 2, border: '1px solid var(--border-primary, #ddd)',
          borderRadius: 8, backgroundColor: 'var(--bg-primary, #fff)',
          boxShadow: '0 8px 24px rgba(0,0,0,0.12)', maxHeight: 360, display: 'flex', flexDirection: 'column',
          overflow: 'hidden',
        }}>
          {/* Search */}
          <div style={{ padding: '8px 10px', borderBottom: '1px solid var(--border-primary, #eee)' }}>
            <div style={{ position: 'relative' }}>
              <i className="fa-solid fa-search" style={{
                position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)',
                fontSize: 12, color: 'var(--text-tertiary, #999)',
              }} />
              <input
                ref={searchRef}
                type="text"
                className="form-control form-control-sm"
                style={{ paddingLeft: 30, fontSize: 13 }}
                placeholder={t('groupedFieldSelect.searchPlaceholder')}
                value={search}
                onChange={e => setSearch(e.target.value)}
              />
            </div>
          </div>

          {/* Grouped list */}
          <div style={{ overflowY: 'auto', flex: 1 }}>
            {totalResults === 0 ? (
              <div style={{ padding: '16px', textAlign: 'center', color: 'var(--text-tertiary, #999)', fontSize: 13 }}>
                {t('groupedFieldSelect.noMatching')}
              </div>
            ) : (
              Object.entries(grouped).map(([category, catItems]) => (
                <div key={category}>
                  <div style={{
                    padding: '6px 12px', fontSize: 11, fontWeight: 600, textTransform: 'uppercase',
                    letterSpacing: '0.05em', color: 'var(--text-tertiary, #888)',
                    backgroundColor: 'var(--bg-secondary, #f7f7f7)',
                    borderBottom: '1px solid var(--border-primary, #eee)',
                    position: 'sticky', top: 0, zIndex: 1,
                  }}>
                    {category}
                    <span style={{ fontWeight: 400, marginLeft: 6, opacity: 0.6 }}>({catItems.length})</span>
                  </div>
                  {catItems.map(item => {
                    const isSelected = item.value === value;
                    return (
                      <div
                        key={item.value}
                        onClick={() => handleSelect(item)}
                        style={{
                          padding: '7px 12px 7px 20px', cursor: 'pointer', fontSize: 13,
                          display: 'flex', alignItems: 'center', gap: 8,
                          backgroundColor: isSelected ? 'var(--accent-primary-light, #eff6ff)' : undefined,
                          borderLeft: isSelected ? '3px solid var(--accent-primary, #3b82f6)' : '3px solid transparent',
                        }}
                        onMouseEnter={e => { if (!isSelected) e.currentTarget.style.backgroundColor = 'var(--bg-secondary, #f9f9f9)'; }}
                        onMouseLeave={e => { if (!isSelected) e.currentTarget.style.backgroundColor = ''; }}
                      >
                        {item.sensitive && (
                          <i className="fa-solid fa-lock" style={{ fontSize: 10, color: 'var(--accent-warning, #f59e0b)', flexShrink: 0 }} />
                        )}
                        <span style={{ flex: 1 }}>{item.displayName}</span>
                        {item.description && (
                          <span style={{ fontSize: 11, color: 'var(--text-tertiary, #aaa)', maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {item.description}
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              ))
            )}

            {/* Custom option */}
            {allowCustom && (
              <div
                onClick={() => handleSelect({ value: '__custom__', displayName: t('groupedFieldSelect.customFieldPath'), category: 'Custom' })}
                style={{
                  padding: '8px 12px 8px 20px', cursor: 'pointer', fontSize: 13,
                  borderTop: '1px solid var(--border-primary, #eee)', color: 'var(--accent-primary, #3b82f6)',
                  fontStyle: 'italic',
                }}
                onMouseEnter={e => (e.currentTarget.style.backgroundColor = 'var(--bg-secondary, #f9f9f9)')}
                onMouseLeave={e => (e.currentTarget.style.backgroundColor = '')}
              >
                <i className="fa-solid fa-pen" style={{ fontSize: 11, marginRight: 6 }} />
                {t('groupedFieldSelect.enterCustom')}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default GroupedFieldSelect;
