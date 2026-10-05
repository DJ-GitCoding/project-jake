/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */


export const STANDARD_MEMBER_FIELDS = [
  { key: 'first_name', label: 'First Name' },
  { key: 'last_name', label: 'Last Name' },
  { key: 'email', label: 'Email' },
  { key: 'phone', label: 'Phone Number' },
  { key: 'street_address', label: 'Street Address' },
  { key: 'city', label: 'City' },
  { key: 'state_province', label: 'State / Province' },
  { key: 'postal_code', label: 'Postal Code' },
  { key: 'country', label: 'Country' },
];

const STANDARD_KEYS = new Set(STANDARD_MEMBER_FIELDS.map((f) => f.key));

/** The default member field for a required field: the same standard key, else a custom field with the same name. */
export const defaultMemberFieldRef = (templateField, groupFields) => {
  if (templateField.standard || STANDARD_KEYS.has(templateField.key)) return `std:${templateField.key}`;
  const label = (templateField.label || '').trim().toLowerCase();
  const match = (groupFields || []).find((f) => (f.label || '').trim().toLowerCase() === label);
  return match ? `custom:${match.id}` : '';
};

/** A mapping that covers every required field, starting from any mapping already saved. */
export const initialMemberFieldMapping = (templateFields, groupFields, saved = {}) =>
  Object.fromEntries((templateFields || []).map((f) => [f.key, saved?.[f.key] || defaultMemberFieldRef(f, groupFields)]));

/** Dropdown of the group's member fields, standard and custom, for mapping one required field. */
export const MemberFieldSelect = ({ id, value, onChange, groupFields, standardLabel, customLabel, placeholder, disabled }) => (
  <select id={id} className="form-select form-select-sm" value={value || ''} disabled={disabled}
          onChange={(e) => onChange(e.target.value)}>
    <option value="">{placeholder}</option>
    <optgroup label={standardLabel}>
      {STANDARD_MEMBER_FIELDS.map((f) => <option key={f.key} value={`std:${f.key}`}>{f.label}</option>)}
    </optgroup>
    {(groupFields || []).length > 0 && (
      <optgroup label={customLabel}>
        {groupFields.map((f) => <option key={f.id} value={`custom:${f.id}`}>{f.label}</option>)}
      </optgroup>
    )}
  </select>
);
