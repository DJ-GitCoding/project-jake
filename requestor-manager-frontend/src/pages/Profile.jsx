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
import { profileFormState, StandardMemberFields, GroupMemberFields } from '../components/MemberProfileFields';
import { useT } from '../i18n';

const Profile = () => {
  const { success, error: showError } = useAlert();
  const { t } = useT();

  const [profile, setProfile] = useState(null);
  const [standard, setStandard] = useState({});
  const [custom, setCustom] = useState({});
  const [saving, setSaving] = useState(false);

  const load = (data) => {
    const state = profileFormState(data);
    setProfile(data);
    setStandard(state.standard);
    setCustom(state.custom);
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

  const hasGroupFields = (profile.groups || []).some((g) => g.fields.length > 0);

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
            <StandardMemberFields profile={profile} standard={standard} setStandard={setStandard} includeNames />
          </div>
        </div>

        {hasGroupFields ? (
          <GroupMemberFields
            profile={profile}
            custom={custom}
            setCustom={setCustom}
            renderSection={(group, fields) => (
              <div className="card mb-4" key={group.id}>
                <div className="card-header">
                  <h5 className="mb-0">{t('profile.groupFields', { name: group.name })}</h5>
                </div>
                <div className="card-body">{fields}</div>
              </div>
            )}
          />
        ) : (
          <div className="card mb-4">
            <div className="card-header"><h5 className="mb-0">{t('profile.groupFieldsTitle')}</h5></div>
            <div className="card-body">
              <GroupMemberFields profile={profile} custom={custom} setCustom={setCustom} renderSection={() => null} />
            </div>
          </div>
        )}

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
