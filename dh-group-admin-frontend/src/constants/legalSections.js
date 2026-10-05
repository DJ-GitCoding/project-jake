/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

export const REFERENCE_PATTERN = /\{\{ref:([A-Za-z0-9_.-]+)\}\}/g;

export const STANDARD_LEGAL_SECTIONS = [
  {
    key: 'purpose',
    title: 'Purpose',
    clauses: [
      { text: 'The general purpose of this Agreement is to establish a legally compliant, transparent, and trusted framework under which a Requestor may submit requests for, and the Data Holder may voluntarily disclose, Domain Name Registration Data, including where applicable Non-Public Registrant Data ("Registrant Data").' },
      { text: 'The specific purpose of the Agreement is to enable Requestors, as member of a Requestor Group, represented by the Requestor Agent that is the signatory of the agreement, to invoke this agreement when making a request to the Data Holder when using the Jake Protocols.' },
      { text: 'All requests must comply with applicable due process requirements in the relevant jurisdictions.' },
      { text: 'All disclosures under this Agreement shall be limited to what is strictly necessary, lawful, and proportionate to the stated and validated purpose of each request, and shall be conducted in accordance with:\n\n• Applicable data protection and privacy laws.\n• Due process requirements in the relevant jurisdiction(s); and\n• Recognized best practices for data security and accountability.' },
      { text: 'Nothing in this Agreement shall be construed as creating a general obligation for the Data Holder to disclose Registrant Data, beyond what may be required by law or other applicable obligations.' },
    ],
  },
  {
    key: 'requestor-group-membership',
    title: 'Requestor Group Membership',
    clauses: [
      { text: 'The Requestor Agent will register a Requestor as a member of the Requestor Group whose members are authorized to invoke this agreement when using the Jake Protocols, based on a separate Agreement between Requestor Agent and Requestor that ensures the Requestors compliance with sections {{ref:permitted-uses}} - {{ref:recourse-and-enforcement}} of this agreement.' },
      { text: 'The Requestor Agent will present the RqA-Rq agreement as part of its submission to the DHA to create the Agreement.' },
    ],
  },
  {
    key: 'permitted-uses',
    title: 'Permitted Uses of Disclosed Data',
    clauses: [
      { text: 'Registrant Data disclosed under this Agreement may be used exclusively for the specific, explicit, and legitimate purpose stated in the corresponding request.' },
      { text: 'The Requestor Agent shall provide a binding assurance, on its own behalf and on behalf of the Requestors it represents, that:\n\n• The disclosed data shall not be used for any unrelated, secondary, or incompatible purpose.\n• The data shall not be sold, licensed, shared, or otherwise disclosed to third parties, except where strictly required by law or explicitly authorized by the Data Holder.' },
      { text: 'Bulk, speculative, or exploratory requests lacking a clear legal basis are expressly prohibited.' },
    ],
  },
  {
    key: 'restrictions-and-prohibited-conduct',
    title: 'Restrictions and Prohibited Conduct',
    clauses: [
      { text: 'The Requestor Agent and the Requestor shall not:\n\n• Misrepresent the identity, legal status, or authority of any Requestor.\n• Circumvent jurisdictional, procedural, or legal safeguards.' },
      { text: 'Any use of disclosed data that is unlawful, or outside the stated purpose shall constitute a material breach of this Agreement.' },
    ],
  },
  {
    key: 'recourse-and-enforcement',
    title: 'Recourse and Enforcement',
    clauses: [
      { text: 'Where the Data Holder has reasonable grounds to believe that disclosed data has been misused or that this Agreement has been breached, it may:' },
      { text: 'Inform the RqA, via the means of contact stipulated as the contact for the RqA in this Agreement, of the reasonable grounds it has that disclosed data has been misused or that this Agreement has been breached.' },
      { text: 'RqA and DHA, at their discretion, can require remediation measures from the Requestor.' },
      { text: 'If deemed necessary, the RqA can terminate the RqG membership based on his Agreement with the Requestor and exclude specific Requestors from future requests.' },
      { text: 'These remedies are without prejudice to any additional contractual, statutory, or regulatory remedies available to the Data Holder.' },
    ],
  },
  {
    key: 'payment-and-cost-recovery',
    title: 'Payment and Cost Recovery',
    clauses: [
      { text: 'Unless otherwise agreed in writing, each Party shall bear its own costs arising from this Agreement.' },
      { text: 'The Data Holders and Data Holder Groups reserves the right to introduce reasonable cost-recovery or administrative fees for processing requests, provided such fees are:\n\n• Transparent.\n• Non-discriminatory; and\n• Communicated to the Requestor Agent in advance.' },
      { text: 'No disclosure shall be conditional upon payment unless expressly permitted by applicable law.' },
    ],
  },
  {
    key: 'transparency',
    title: 'Transparency',
    clauses: [
      { text: 'The Data Holder may publish aggregated and anonymized statistics regarding requests and disclosures for transparency purposes.' },
      { text: 'Where legally permissible, affected registrants may be notified of disclosures except where special provisions have been specified in section {{ref:requestor-group-specific-provisions}}, "Requestor Group specific Provisions".' },
    ],
  },
  {
    key: 'handling',
    title: 'Handling',
    clauses: [
      { text: 'Agreement-ID, including version of this Agreement.' },
      { text: 'Short description.' },
      { text: 'Contact information for the DH.' },
      { text: 'Contact information for the RqG.' },
    ],
  },
  {
    key: 'processing',
    title: 'Processing',
    clauses: [
      { text: 'OpenID server.' },
      { text: 'Request parameters — both required and optional.' },
      { text: 'OpenID parameters — both required and optional.' },
      { text: 'Maximum sensitivity level (specific to data requests).' },
      { text: 'Data elements likely to be provided (specific to data requests).' },
    ],
  },
  {
    key: 'testing',
    title: 'Testing',
    clauses: [
      { text: 'Request and response details will be tested to make sure Requestor and Data Holder are communicating correctly. This test is to be executed and checked before this Agreement is made available to Requestors.' },
    ],
  },
  {
    key: 'authentication-quality-standards',
    title: 'Authentication Service Quality Standards',
    clauses: [
      { text: 'The Requestor Agent shall operate, or procure, the authentication service through which Requestors are identified when invoking this Agreement, including the token introspection service the Data Holder relies on to verify each Requestor at the time of a request.' },
      { text: 'The Requestor Agent shall verify the identity of each Requestor before registering that Requestor as a member of the Requestor Group, to a level of assurance appropriate to the data that may be disclosed under this Agreement, and shall keep a record of how each identity was verified.' },
      { text: 'Requestor credentials shall be protected by multi-factor authentication, shall be issued to and used by a single natural person only, and shall be revoked without undue delay when a Requestor leaves the Requestor Group or no longer meets the conditions of membership.' },
      { text: 'The member information the authentication service supplies to the Data Holder shall be accurate and kept up to date by the Requestor Agent, and shall be limited to the information this Agreement requires.' },
      { text: 'Access tokens issued by the authentication service shall be short-lived, and the token introspection service shall be available to the Data Holder with a level of availability that allows requests under this Agreement to be verified when they are made.' },
      { text: 'The Requestor Agent shall notify the Data Holder, via the means of contact stipulated in this Agreement, without undue delay and in any event within [specified period] of becoming aware of any security incident affecting the authentication service or the credentials of any Requestor.' },
      { text: 'The Requestor Agent shall keep logs of authentication and token introspection events for at least [specified retention period] and make them available to the Data Holder on reasonable request in connection with a suspected breach of this Agreement.' },
    ],
  },
  {
    key: 'requestor-group-specific-provisions',
    title: 'Requestor Group specific Provisions',
    clauses: [
      { text: '' },
    ],
  },
  {
    key: 'term-and-termination',
    title: 'Term and Termination, Governing Law and Legal Venue',
    clauses: [
      { text: 'This Agreement shall enter into force on the Effective Date and remain in effect until terminated by either Party upon thirty (30) days’ written notice.' },
      { text: 'Immediate termination may occur in the event of material breach or legal necessity.' },
      { text: 'Governing Law and Legal Venue. This Agreement shall be governed by and construed in accordance with the laws of [specified jurisdiction], unless mandatory law requires otherwise.' },
      { text: 'The Parties shall seek to resolve disputes amicably through negotiation or mediation prior to initiating legal proceedings.' },
      { text: 'Exclusive venue for any unresolved dispute shall lie with the competent courts of the governing jurisdiction, unless otherwise mandated by law.' },
    ],
  },
];

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

export const containsReference = (text) => /\{\{ref:[A-Za-z0-9_.-]+\}\}/.test(text || '');

export const normalizeSections = (sections) => {
  const used = new Set();
  const take = (base) => {
    let key = base;
    let n = 2;
    while (used.has(key)) {
      key = `${base}-${n}`;
      n += 1;
    }
    used.add(key);
    return key;
  };
  return (sections || []).map((section, i) => {
    const refKey = take(section.refKey || `section-${i + 1}`);
    const source = (section.clauses && section.clauses.length > 0)
      ? section.clauses
      : (section.body ? [{ text: section.body }] : []);
    return {
      ...section,
      refKey,
      clauses: source.map((clause, j) => ({
        refKey: take(clause.refKey || `${refKey}-c${j + 1}`),
        text: clause.text || '',
      })),
    };
  });
};

export const resolveReferences = (text, index) => {
  if (!text) return text || '';
  return text.replace(REFERENCE_PATTERN, (match, key) => index.get(key) || key);
};

export const referenceOptions = (sections) => {
  const index = buildReferenceIndex(sections);
  const options = [];
  (sections || []).forEach((section, i) => {
    const sectionNumber = String(i + 1);
    const title = section.title || '';
    if (section.refKey) {
      options.push({
        refKey: section.refKey, label: sectionNumber, kind: 'section', title, text: title,
      });
    }
    (section.clauses || []).forEach((clause, j) => {
      if (clause.refKey) {
        options.push({
          refKey: clause.refKey,
          label: `${sectionNumber}.${j + 1}`,
          kind: 'clause',
          title,
          text: resolveReferences(clause.text, index).trim().slice(0, 60),
        });
      }
    });
  });
  return options;
};

export const referenceLabels = (sections) => {
  const index = buildReferenceIndex(sections);
  const labels = new Map();
  (sections || []).forEach((section, i) => {
    const sectionNumber = String(i + 1);
    const title = section.title || '';
    if (section.refKey) {
      labels.set(section.refKey, { number: sectionNumber, name: title, kind: 'section', detail: '' });
    }
    (section.clauses || []).forEach((clause, j) => {
      if (clause.refKey) {
        labels.set(clause.refKey, {
          number: `${sectionNumber}.${j + 1}`,
          name: title,
          kind: 'clause',
          detail: resolveReferences(clause.text, index).trim().slice(0, 160),
        });
      }
    });
  });
  return labels;
};

const uniqueKey = (base, used) => {
  let key = base;
  let suffix = 2;
  while (used.has(key)) {
    key = `${base}-${suffix}`;
    suffix += 1;
  }
  used.add(key);
  return key;
};

export const collectUsedKeys = (sections) => {
  const used = new Set();
  (sections || []).forEach((section) => {
    if (section.refKey) used.add(section.refKey);
    (section.clauses || []).forEach((clause) => {
      if (clause.refKey) used.add(clause.refKey);
    });
  });
  return used;
};

export const makeSectionRefKey = (base, sections) => uniqueKey(base, collectUsedKeys(sections));

export const makeClauseRefKey = (sectionRefKey, sections) => {
  const used = collectUsedKeys(sections);
  let n = 1;
  while (used.has(`${sectionRefKey}-c${n}`)) n += 1;
  return `${sectionRefKey}-c${n}`;
};

export const buildStandardSection = (entry, sections) => {
  const used = collectUsedKeys(sections);
  const refKey = uniqueKey(entry.key, used);
  const remap = new Map();
  const clauses = entry.clauses.map((clause, i) => {
    const clauseKey = uniqueKey(`${refKey}-c${i + 1}`, used);
    remap.set(`${entry.key}-c${i + 1}`, clauseKey);
    return { refKey: clauseKey, text: clause.text };
  });
  if (refKey !== entry.key) remap.set(entry.key, refKey);
  return {
    refKey,
    title: entry.title,
    clauses: clauses.map((clause) => ({
      ...clause,
      text: clause.text.replace(REFERENCE_PATTERN, (match, key) => `{{ref:${remap.get(key) || key}}}`),
    })),
  };
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
