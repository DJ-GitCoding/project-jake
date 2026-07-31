/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { createContext, useContext, useState, useCallback } from 'react';
import english from './locales/english.json';

/*
 * Registry of available languages. Add new languages by importing another
 * locale JSON and registering it here (e.g. `french`, `arabic`, ...).
 */
const locales = {
  english,
};

const DEFAULT_LANGUAGE = 'english';
// App-scoped so each frontend keeps its own language preference.
const STORAGE_KEY = 'jaddar_language';

const I18nContext = createContext(null);

// Resolve a dot-path key like "login.title" against a nested object.
const resolveKey = (dict, key) =>
  key.split('.').reduce(
    (acc, part) => (acc != null && acc[part] !== undefined ? acc[part] : undefined),
    dict
  );

// Replace {{name}} placeholders with values from `vars`.
const interpolate = (str, vars) =>
  vars
    ? str.replace(/\{\{\s*(\w+)\s*\}\}/g, (match, name) =>
        vars[name] !== undefined ? String(vars[name]) : match
      )
    : str;

export const I18nProvider = ({ children, initialLanguage }) => {
  const [language, setLanguageState] = useState(() => {
    /*
     * SSR-safe: prefer the language resolved server-side (from the cookie) so the
     * server render and client hydration agree; only touch localStorage in the browser.
     */
    if (initialLanguage && locales[initialLanguage]) return initialLanguage;
    if (typeof window !== 'undefined') {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored && locales[stored]) return stored;
    }
    return DEFAULT_LANGUAGE;
  });

  const setLanguage = useCallback((lang) => {
    if (locales[lang]) {
      setLanguageState(lang);
      if (typeof window !== 'undefined') {
        localStorage.setItem(STORAGE_KEY, lang);
        // Mirror to a cookie so SSR can read it on the next request.
        document.cookie = `${STORAGE_KEY}=${lang};path=/;max-age=31536000;samesite=lax`;
      }
    }
  }, []);

  /*
   * Translate a key; falls back to the default language, then the key itself.
   * `vars` fills {{placeholder}} interpolation.
   */
  const t = useCallback(
    (key, vars) => {
      let value = resolveKey(locales[language] || {}, key);
      if (value === undefined) {
        value = resolveKey(locales[DEFAULT_LANGUAGE], key);
      }
      if (value === undefined) {
        if (process.env.NODE_ENV !== 'production') {
          // eslint-disable-next-line no-console
          console.warn(`[i18n] Missing translation for key: "${key}"`);
        }
        return key;
      }
      return typeof value === 'string' ? interpolate(value, vars) : value;
    },
    [language]
  );

  const value = {
    t,
    language,
    setLanguage,
    languages: Object.keys(locales),
  };

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
};

export const useT = () => {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error('useT must be used within an I18nProvider');
  }
  return context;
};

export default I18nContext;
