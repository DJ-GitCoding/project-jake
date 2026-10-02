/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

/**
 * ICANN RDRS vocabularies.
 *
 * These are the exact strings ICANN's own client sends on the wire — the API
 * stores the display label, not an id — so they must match character for
 * character. RDRS has no published schema endpoint, so this list is maintained
 * by hand; if ICANN adds a category, add it here.
 */

export const RDRS_CATEGORIES = [
  'Law Enforcement',
  'Security Researcher',
  'Computer Security Incident Response Team (CSIRT)',
  'Cybersecurity Incident Response Team (non-CSIRT)',
  'Consumer Protection',
  'Research (non-security)',
  'Domain Investor',
  'IP Holder',
  'Dispute Resolution Service Provider',
  'Litigation/Dispute Resolution (non-IP)',
  'Other',
];

/** Selecting this category unlocks the law-enforcement deadline and credential upload. */
export const RDRS_CATEGORY_LAW_ENFORCEMENT = 'Law Enforcement';
/** Selecting this category requires a free-text description (5–100 chars). */
export const RDRS_CATEGORY_OTHER = 'Other';

export const RDRS_PRIORITIES = [
  'Standard Request',
  'Expedited Review Request',
];

/** Selecting this priority requires a justification (50–2000 chars). */
export const RDRS_PRIORITY_EXPEDITED = 'Expedited Review Request';

/** Grouped for display; sent to ICANN as a flat array of the labels. */
export const RDRS_DATA_ELEMENT_GROUPS = [
  {
    groupName: 'Registry Information',
    elements: ['Registry Domain ID', 'Registry Registrant ID'],
  },
  {
    groupName: 'Registrant Information',
    elements: [
      'Registrant Name',
      'Registrant Org',
      'Registrant Email',
      'Registrant Street',
      'Registrant City',
      'Registrant Postal Code',
      'Registrant Phone',
    ],
  },
  {
    groupName: 'Tech Information',
    elements: ['Tech ID', 'Tech Name', 'Tech Email', 'Tech Phone'],
  },
];

export const RDRS_ALL_DATA_ELEMENTS = RDRS_DATA_ELEMENT_GROUPS.flatMap(g => g.elements);

export const RDRS_PARTY_REPRESENTATIONS = [
  { id: 'self', label: 'I am submitting this request on my own behalf' },
  { id: 'third-party', label: 'I am authorized to act on behalf of a third party in submitting this request' },
];

/** Choosing this requires a power-of-attorney attachment. */
export const RDRS_PARTY_THIRD_PARTY = RDRS_PARTY_REPRESENTATIONS[1].label;

export const RDRS_LEGAL_BASES = [
  { id: 'legal-basis-6-1-a', label: 'GDPR Art. 6(1)a, data subject consent' },
  { id: 'legal-basis-6-1-b', label: 'GDPR Art. 6(1)b, contractual necessity' },
  { id: 'legal-basis-6-1-c', label: 'GDPR Art. 6(1)c, compliance with a legal obligation to which the controller is subject' },
  { id: 'legal-basis-6-1-d', label: 'GDPR Art. 6(1)d, processing is necessary to protect the vital interests of a data subject or other natural person' },
  { id: 'legal-basis-6-1-e', label: 'GDPR Art. 6(1)e, processing is necessary for a task carried out in the public interest, as set out in EU or EU Member State law' },
  { id: 'legal-basis-6-1-f', label: 'GDPR Art. 6(1)f, legitimate interests' },
  { id: 'legal-basis-other', label: 'Other applicable law legal basis' },
];

/** Choosing this requires free text (50–300 chars). */
export const RDRS_LEGAL_BASIS_OTHER = 'legal-basis-other';

/**
 * Outcomes ICANN's domain lookup can return.
 *
 * These are the literal wire values — lowercase human phrases, not enum-style
 * constants. `third leve not supported` is ICANN's own typo and must match exactly.
 */
export const RDRS_LOOKUP = {
  SUCCESS: 'success',
  CCTLD_NOT_SUPPORTED: 'country code not supported',
  TLD_NOT_SUPPORTED: 'tld not supported',
  THIRD_LEVEL_NOT_SUPPORTED: 'third leve not supported',
  DOMAIN_NOT_FOUND: 'domain not found',
  DOMAIN_NOT_SUPPORTED: 'domain not supported',
  REGISTRAR_NOT_SUPPORTED: 'registrar not supported',
  INVALID_WHOIS_SERVER_RESPONSE: 'Invalid Whois Server Response',
  LOOKUP_SERVICE_NOT_AVAILABLE: 'lookup service not available',
  SERVICE_ERROR: 'service error',
};

/** Compare case-insensitively — the wire value is lowercase, unlike the key. */
export const isLookupSuccess = (result) =>
  typeof result === 'string' && result.toLowerCase() === RDRS_LOOKUP.SUCCESS;

/** i18n key under `rdrs.lookup` explaining a given outcome. */
export const lookupMessageKey = (result) => {
  const match = Object.entries(RDRS_LOOKUP)
    .find(([, value]) => typeof result === 'string' && value.toLowerCase() === result.toLowerCase());
  return match ? match[0] : 'UNKNOWN';
};

/**
 * IANA ids that are not real registrars — registry-operator placeholders, test
 * entries and historic reservations. ICANN refuses submission against any of them.
 */
export const RDRS_RESERVED_IANA_IDS = new Set([
  '1', '3', '8', '119', '365', '376',
  '9994', '9995', '9996', '9997', '9998', '9999',
  '10009', '4000001', '8888888',
]);

export const isReservedIanaId = (ianaId) =>
  ianaId != null && RDRS_RESERVED_IANA_IDS.has(String(ianaId).trim());

/** Field length limits mirrored from ICANN's client-side validation. */
export const RDRS_LIMITS = {
  otherCategory: { min: 5, max: 100 },
  expeditedReason: { min: 50, max: 2000 },
  otherLegalBasis: { min: 50, max: 300 },
  issueDescription: { min: 50, max: 2000 },
  maxAdditionalAttachments: 3,
};

/**
 * Attachment rules.
 */
export const RDRS_FILES = {
  accept: '.pdf',
  maxSizeMb: 5,
};
