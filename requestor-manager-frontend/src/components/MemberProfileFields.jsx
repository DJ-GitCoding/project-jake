/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import CountrySelect from './CountrySelect';
import { useT } from '../i18n';

/** Form state for a profile returned by the API: standard values by key, custom values by field id. */
export const profileFormState = (profile) => ({
  standard: Object.fromEntries((profile?.standardFields || []).map((f) => [f.key, f.value || ''])),
  custom: Object.fromEntries((profile?.groups || []).flatMap((g) => g.fields.map((f) => [f.id, f.value || '']))),
});

/** Which agreements rely on a field. */
const UsedBy = ({ usedBy, filled }) => {
  const { t } = useT();
  if (!usedBy || usedBy.length === 0) return null;
  return (
    <div className="d-flex flex-wrap gap-1 mt-1">
      {usedBy.map((u, i) => (
        <span
          key={i}
          className={`badge ${filled ? 'bg-light text-dark border' : 'bg-warning text-dark'}`}
          style={{ fontSize: '11px', fontWeight: 500 }}
          title={t('profile.usedByTitle', { agreement: u.agreement, dataHolderGroup: u.dataHolderGroup, asField: u.asField })}
        >
          <i className={`fas ${filled ? 'fa-file-signature' : 'fa-exclamation-triangle'} me-1`}></i>
          {u.agreement}
        </span>
      ))}
    </div>
  );
};

/** The input for one custom member field, by its type. */
const CustomInput = ({ field, value, onChange, disabled }) => {
  const id = `custom-${field.id}`;
  if (field.dataType === 'select') {
    const choices = (field.options || '').split(',').map((s) => s.trim()).filter(Boolean);
    return (
      <select id={id} className="form-select" value={value} disabled={disabled} onChange={(e) => onChange(e.target.value)}>
        <option value=""></option>
        {choices.map((c) => <option key={c} value={c}>{c}</option>)}
      </select>
    );
  }
  const type = field.dataType === 'number' ? 'number' : field.dataType === 'date' ? 'date' : 'text';
  return <input id={id} type={type} className="form-control" value={value} disabled={disabled}
                onChange={(e) => onChange(e.target.value)} />;
};

/** The standard member fields; names and email only when includeNames is set. */
export const StandardMemberFields = ({ profile, standard, setStandard, includeNames, disabled }) => {
  const { t } = useT();
  const byKey = Object.fromEntries((profile?.standardFields || []).map((f) => [f.key, f]));

  const input = (key, col) => {
    const field = byKey[key];
    if (!field) return null;
    const readOnly = disabled || field.readOnly;
    return (
      <div className={`${col} mb-3`} key={key}>
        <label htmlFor={`std-${key}`} className="form-label">{field.label}</label>
        {key === 'country' ? (
          <CountrySelect id={`std-${key}`} value={standard.country || ''} disabled={disabled}
                         onChange={(country) => setStandard({ ...standard, country })} />
        ) : (
          <input
            id={`std-${key}`}
            type={key === 'email' ? 'email' : key === 'phone' ? 'tel' : 'text'}
            className="form-control"
            value={standard[key] || ''}
            readOnly={readOnly}
            disabled={readOnly}
            onChange={(e) => setStandard({ ...standard, [key]: e.target.value })}
          />
        )}
        {field.readOnly && includeNames && <div className="form-text">{t('profile.emailHelp')}</div>}
        <UsedBy usedBy={field.usedBy} filled={!!(standard[key] || '').trim()} />
      </div>
    );
  };

  return (
    <>
      {includeNames && (
        <div className="row">
          {input('first_name', 'col-md-4')}
          {input('last_name', 'col-md-4')}
          {input('email', 'col-md-4')}
        </div>
      )}
      <div className="row">
        {input('phone', 'col-md-4')}
        {input('street_address', 'col-md-8')}
      </div>
      <div className="row">
        {input('city', 'col-md-3')}
        {input('state_province', 'col-md-3')}
        {input('postal_code', 'col-md-2')}
        {input('country', 'col-md-4')}
      </div>
    </>
  );
};

/** One section per requestor group with its custom fields, or a note saying why there are none. */
export const GroupMemberFields = ({ profile, custom, setCustom, disabled, renderSection }) => {
  const { t } = useT();
  const groups = profile?.groups || [];
  const withFields = groups.filter((g) => g.fields.length > 0);

  if (groups.length === 0) {
    return <p className="text-muted small mb-0">{t('profile.noGroups')}</p>;
  }
  if (withFields.length === 0) {
    return (
      <p className="text-muted small mb-0">
        {t('profile.noGroupFields', { groups: groups.map((g) => g.name).join(', ') })}
      </p>
    );
  }

  return withFields.map((group) => renderSection(group, (
    <div className="row">
      {group.fields.map((field) => (
        <div className="col-md-6 mb-3" key={field.id}>
          <label htmlFor={`custom-${field.id}`} className="form-label">{field.label}</label>
          <CustomInput field={field} value={custom[field.id] ?? ''} disabled={disabled}
                       onChange={(v) => setCustom({ ...custom, [field.id]: v })} />
          {field.description && <div className="form-text">{field.description}</div>}
          <UsedBy usedBy={field.usedBy} filled={!!String(custom[field.id] ?? '').trim()} />
        </div>
      ))}
    </div>
  )));
};
