/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useEffect, useState } from 'react';
import { useAlert } from '../context/AlertContext';
import { profileApi } from '../services/api';
import Loading from '../components/Loading';
import CountrySelect from '../components/CountrySelect';
import { useT } from '../i18n';

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

const CustomInput = ({ field, value, onChange }) => {
  const id = `custom-${field.id}`;
  if (field.dataType === 'select') {
    const choices = (field.options || '').split(',').map((s) => s.trim()).filter(Boolean);
    return (
      <select id={id} className="form-select" value={value} onChange={(e) => onChange(e.target.value)}>
        <option value=""></option>
        {choices.map((c) => <option key={c} value={c}>{c}</option>)}
      </select>
    );
  }
  const type = field.dataType === 'number' ? 'number' : field.dataType === 'date' ? 'date' : 'text';
  return <input id={id} type={type} className="form-control" value={value} onChange={(e) => onChange(e.target.value)} />;
};

const Profile = () => {
  const { success, error: showError } = useAlert();
  const { t } = useT();

  const [profile, setProfile] = useState(null);
  const [standard, setStandard] = useState({});
  const [custom, setCustom] = useState({});
  const [saving, setSaving] = useState(false);

  const load = (data) => {
    setProfile(data);
    setStandard(Object.fromEntries((data.standardFields || []).map((f) => [f.key, f.value || ''])));
    setCustom(Object.fromEntries((data.groups || []).flatMap((g) => g.fields.map((f) => [f.id, f.value || '']))));
  };

  useEffect(() => {
    profileApi.get()
      .then((res) => load(res.data.data))
      .catch((err) => {
        console.error('Failed to load profile:', err);
        showError(t('profile.errors.loadFailed'));
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSave = async (e) => {
    e.preventDefault();
    if (!standard.first_name?.trim() || !standard.last_name?.trim()) {
      showError(t('profile.errors.nameRequired'));
      return;
    }
    setSaving(true);
    try {
      const { email, ...editable } = standard;
      const res = await profileApi.update({ ...editable, customValues: custom });
      load(res.data.data);
      success(t('profile.saved'));
    } catch (err) {
      console.error('Failed to save profile:', err);
      showError(err.response?.data?.message || t('profile.errors.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  if (!profile) return <Loading message={t('profile.loading')} />;

  const standardByKey = Object.fromEntries((profile.standardFields || []).map((f) => [f.key, f]));
  const standardInput = (key, col) => {
    const field = standardByKey[key];
    if (!field) return null;
    return (
      <div className={`${col} mb-3`} key={key}>
        <label htmlFor={`std-${key}`} className="form-label">{field.label}</label>
        {key === 'country' ? (
          <CountrySelect id={`std-${key}`} value={standard.country || ''}
                         onChange={(country) => setStandard({ ...standard, country })} />
        ) : (
          <input
            id={`std-${key}`}
            type={key === 'email' ? 'email' : key === 'phone' ? 'tel' : 'text'}
            className="form-control"
            value={standard[key] || ''}
            readOnly={field.readOnly}
            disabled={field.readOnly}
            onChange={(e) => setStandard({ ...standard, [key]: e.target.value })}
          />
        )}
        {field.readOnly && <div className="form-text">{t('profile.emailHelp')}</div>}
        <UsedBy usedBy={field.usedBy} filled={!!(standard[key] || '').trim()} />
      </div>
    );
  };

  return (
    <div>
      <div className="page-header">
        <h1>{t('profile.title')}</h1>
        <p>{t('profile.subtitle')}</p>
      </div>

      <form onSubmit={handleSave}>
        <div className="card mb-4">
          <div className="card-header"><h5 className="mb-0">{t('profile.personalInformation')}</h5></div>
          <div className="card-body">
            <div className="row">
              {standardInput('first_name', 'col-md-4')}
              {standardInput('last_name', 'col-md-4')}
              {standardInput('email', 'col-md-4')}
            </div>
            <div className="row">
              {standardInput('phone', 'col-md-4')}
              {standardInput('street_address', 'col-md-8')}
            </div>
            <div className="row">
              {standardInput('city', 'col-md-3')}
              {standardInput('state_province', 'col-md-3')}
              {standardInput('postal_code', 'col-md-2')}
              {standardInput('country', 'col-md-4')}
            </div>
          </div>
        </div>

        {(profile.groups || []).filter((g) => g.fields.length > 0).map((group) => (
          <div className="card mb-4" key={group.id}>
            <div className="card-header">
              <h5 className="mb-0">{t('profile.groupFields', { name: group.name })}</h5>
            </div>
            <div className="card-body">
              <div className="row">
                {group.fields.map((field) => (
                  <div className="col-md-6 mb-3" key={field.id}>
                    <label htmlFor={`custom-${field.id}`} className="form-label">{field.label}</label>
                    <CustomInput field={field} value={custom[field.id] ?? ''}
                                 onChange={(v) => setCustom({ ...custom, [field.id]: v })} />
                    {field.description && <div className="form-text">{field.description}</div>}
                    <UsedBy usedBy={field.usedBy} filled={!!String(custom[field.id] ?? '').trim()} />
                  </div>
                ))}
              </div>
            </div>
          </div>
        ))}

        <div className="d-flex align-items-center justify-content-between">
          <small className="text-muted">
            <i className="fas fa-file-signature me-1"></i>{t('profile.usedByLegend')}
          </small>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? (
              <><span className="spinner-border spinner-border-sm me-2"></span>{t('common.saving')}</>
            ) : (
              <><i className="fas fa-save me-2"></i>{t('profile.save')}</>
            )}
          </button>
        </div>
      </form>
    </div>
  );
};

export default Profile;
