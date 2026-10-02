/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

const STORAGE_KEY = 'jaddar.tldAgreementFilters';

const listeners = new Set();

/** Read the store, tolerating a missing, unreadable or malformed sessionStorage. */
const read = () => {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return { tlds: {}, selected: [] };
    const parsed = JSON.parse(raw);
    return {
      tlds: parsed && typeof parsed.tlds === 'object' && parsed.tlds ? parsed.tlds : {},
      selected: Array.isArray(parsed?.selected) ? parsed.selected : [],
    };
  } catch {
    return { tlds: {}, selected: [] };
  }
};

const write = (state) => {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
  }
  listeners.forEach((fn) => fn(state));
};

/** Subscribe to store changes; returns an unsubscribe function. */
export const subscribe = (fn) => {
  listeners.add(fn);
  return () => listeners.delete(fn);
};

export const getState = read;

/** The TLD of a domain query, or null if it is not one. The final label must start with a
 *  letter, so an IP such as 192.0.2.1 does not yield a "TLD" of 1. */
export const tldOf = (query) => {
  if (typeof query !== 'string') return null;
  const trimmed = query.trim().toLowerCase().replace(/\.$/, '');
  if (!trimmed.includes('.') || trimmed.includes(':')) return null;
  const tld = trimmed.split('.').pop();
  return /^[a-z][a-z0-9-]*$/.test(tld) && tld.length >= 2 ? tld : null;
};

/** Record the agreement codes a data holder reported valid for this TLD, replacing any
 *  already held. An empty list still records the TLD, so the UI can say none came back. */
export const recordTldCodes = (tld, codes) => {
  if (!tld) return;
  const state = read();
  /* Blanks are dropped on the way in: an agreement whose code we never learned must not
   * match a data holder that reported nothing for one of its own. */
  const clean = Array.isArray(codes)
    ? [...new Set(codes.filter((code) => typeof code === 'string' && code.trim()))]
    : [];
  state.tlds = { ...state.tlds, [tld]: clean };
  write(state);
};

export const setSelected = (selected) => {
  const state = read();
  state.selected = [...new Set(selected || [])].filter((tld) => tld in state.tlds);
  write(state);
};

export const clearAll = () => write({ tlds: {}, selected: [] });

/** Union of the agreement codes for every selected TLD, or null when none is selected.
 *  Null means "no filter" and is distinct from an empty set. */
export const selectedCodes = (state = read()) => {
  const chosen = (state.selected || []).filter((tld) => Array.isArray(state.tlds?.[tld]));
  if (chosen.length === 0) return null;
  const union = new Set();
  chosen.forEach((tld) => state.tlds[tld].forEach((code) => union.add(code)));
  return union;
};

/** How many of `agreements` a TLD's reported codes cover. The picker shows this against
 *  the total, so "2/5" reads as two of your five agreements being usable there. */
export const matchCount = (agreements, codes) => {
  if (!Array.isArray(agreements) || !Array.isArray(codes)) return 0;
  const set = new Set(codes.filter((code) => typeof code === 'string' && code.trim()));
  return agreements.filter((agreement) => {
    const code = agreement?.agreementCode;
    return typeof code === 'string' && code.trim() && set.has(code);
  }).length;
};
