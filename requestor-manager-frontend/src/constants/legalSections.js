/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

const REFERENCE_PATTERN = /\{\{ref:([A-Za-z0-9_.-]+)\}\}/g;

export const buildReferenceIndex = (sections) => {
  const index = new Map();
  (sections || []).forEach((section, i) => {
    const sectionNumber = String(i + 1);
    if (section.refKey) index.set(section.refKey, sectionNumber);
    (section.clauses || []).forEach((clause, j) => {
      if (clause.refKey) index.set(clause.refKey, `${sectionNumber}.${j + 1}`);
    });
  });
  return index;
};

export const resolveReferences = (text, index) => {
  if (!text) return '';
  return text.replace(REFERENCE_PATTERN, (match, key) => index.get(key) || key);
};

export const sectionClauseLines = (section, sectionNumber, index) => {
  const clauses = section.clauses || [];
  if (clauses.length === 0) {
    return section.body
      ? [{ key: `${sectionNumber}-body`, label: '', text: resolveReferences(section.body, index) }]
      : [];
  }
  return clauses.map((clause, j) => ({
    key: clause.refKey || `${sectionNumber}-${j}`,
    label: `${sectionNumber}.${j + 1}`,
    text: resolveReferences(clause.text, index),
  }));
};
