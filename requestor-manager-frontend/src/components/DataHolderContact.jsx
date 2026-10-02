/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { useT } from '../i18n';

const DataHolderContact = ({ contact, className = '', compact = false }) => {
  const { t } = useT();

  const hasSomething = contact
    && (contact.name || contact.email || contact.phone || contact.formattedAddress || contact.organization);
  if (!hasSomething) return null;

  const body = (
    <>
      {contact.name && <div className="fw-semibold">{contact.name}</div>}
      {contact.organization && (
        <div className={contact.name ? 'text-muted' : 'fw-semibold'}>{contact.organization}</div>
      )}
      {contact.email && (
        <div>
          <i className="fas fa-envelope me-2 text-muted"></i>
          <a href={`mailto:${contact.email}`} className="text-break">{contact.email}</a>
        </div>
      )}
      {contact.phone && (
        <div>
          <i className="fas fa-phone me-2 text-muted"></i>
          <a href={`tel:${contact.phone.replace(/\s+/g, '')}`}>{contact.phone}</a>
        </div>
      )}
      {contact.formattedAddress && (
        <div>
          <i className="fas fa-location-dot me-2 text-muted"></i>
          {contact.formattedAddress}
        </div>
      )}
    </>
  );

  if (compact) {
    return (
      <div className={`small ${className}`.trim()}>
        <div className="fw-bold mb-1">{t('dataHolderContact.heading')}</div>
        {body}
      </div>
    );
  }

  return (
    <div className={`card ${className}`.trim()}>
      <div className="card-header">
        <h6 className="mb-0">
          <i className="fas fa-address-card me-2"></i>
          {t('dataHolderContact.heading')}
        </h6>
      </div>
      <div className="card-body small">
        {body}
        <div className="text-muted mt-2">{t('dataHolderContact.help')}</div>
      </div>
    </div>
  );
};

export default DataHolderContact;
