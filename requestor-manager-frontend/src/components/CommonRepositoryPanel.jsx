/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useCallback, useEffect, useState } from 'react';
import { dataHolderGroupsApi } from '../services/api';
import { useAlert } from '../context/AlertContext';
import { useT } from '../i18n';

/** Deployment-wide switch and URL for the central data holder group repository. */
const CommonRepositoryPanel = ({ canManage = false }) => {
  const { t } = useT();
  const { success, error: showError } = useAlert();

  const [settings, setSettings] = useState(null);
  const [url, setUrl] = useState('');
  const [registries, setRegistries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const applySettings = (data) => {
    setSettings(data);
    setUrl(data.registryUrl || '');
  };

  /** Reads the settings row and, when the repository is on, what it currently lists. */
  const load = useCallback(async () => {
    try {
      const response = await dataHolderGroupsApi.getDirectory();
      const data = response.data.data || {};
      applySettings(data.settings || {});
      setRegistries(data.registries || []);
    } catch {
      showError(t('settings.commonRepository.errors.loadFailed'));
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { load(); }, [load]);

  const save = async (enabled) => {
    setSaving(true);
    try {
      const response = await dataHolderGroupsApi.updateDirectorySettings({
        enabled,
        registryUrl: url.trim(),
      });
      applySettings(response.data.data || {});
      success(t('settings.commonRepository.toasts.saved'));
      await load();
    } catch (err) {
      showError(err.response?.data?.message || t('settings.commonRepository.errors.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const refresh = async () => {
    setRefreshing(true);
    try {
      await dataHolderGroupsApi.refreshDirectory();
      await load();
    } catch {
      showError(t('settings.commonRepository.errors.refreshFailed'));
    } finally {
      setRefreshing(false);
    }
  };

  if (loading) {
    return <div className="text-center py-5"><span className="spinner-border spinner-border-sm me-2"></span>{t('common.loading')}</div>;
  }

  const enabled = !!settings?.enabled;
  const syncFailed = settings?.lastSyncStatus === 'FAILURE';
  const groupTotal = registries.reduce((sum, r) => sum + (r.groupCount || 0), 0);

  return (
    <div>
      <div className="alert alert-light border d-flex gap-3">
        <i className="fas fa-globe fa-lg mt-1 text-primary"></i>
        <div>
          <div className="fw-semibold">{t('settings.commonRepository.title')}</div>
          <div className="small text-muted">{t('settings.commonRepository.body')}</div>
        </div>
      </div>

      <div className="card">
        <div className="card-body">
          <div className="form-check form-switch mb-3">
            <input
              className="form-check-input"
              type="checkbox"
              role="switch"
              id="commonRepositoryEnabled"
              checked={enabled}
              disabled={!canManage || saving}
              onChange={(e) => save(e.target.checked)}
            />
            <label className="form-check-label" htmlFor="commonRepositoryEnabled">
              {t('settings.commonRepository.enabled')}
            </label>
            <div className="form-text">{t('settings.commonRepository.enabledHint')}</div>
          </div>

          <div className="mb-3">
            <label className="form-label" htmlFor="commonRepositoryUrl">
              {t('settings.commonRepository.urlLabel')}
            </label>
            <div className="input-group">
              <input
                id="commonRepositoryUrl"
                className="form-control"
                placeholder="https://registry.example.org/dataholder-registry/registry"
                value={url}
                disabled={!canManage}
                onChange={(e) => setUrl(e.target.value)}
              />
              {canManage && (
                <button className="btn btn-primary" onClick={() => save(enabled)} disabled={saving}>
                  {saving ? <span className="spinner-border spinner-border-sm"></span> : t('common.save')}
                </button>
              )}
              {canManage && (
                <button
                  className="btn btn-outline-secondary"
                  onClick={refresh}
                  disabled={refreshing || !enabled}
                  title={t('settings.commonRepository.refresh')}
                >
                  <i className={`fas fa-arrows-rotate ${refreshing ? 'fa-spin' : ''}`}></i>
                </button>
              )}
            </div>
            <div className="form-text">{t('settings.commonRepository.urlHint')}</div>
          </div>

          {settings?.lastSyncMessage && (
            <div className={`alert ${syncFailed ? 'alert-danger' : 'alert-light border'} py-2 small mb-0`}>
              <div>{settings.lastSyncMessage}</div>
              {settings.lastSyncedAt && (
                <div className="text-muted">
                  {t('settings.commonRepository.lastSynced', {
                    when: new Date(settings.lastSyncedAt).toLocaleString(),
                  })}
                </div>
              )}
            </div>
          )}
        </div>

        {enabled && (
          <div className="card-footer small text-muted">
            <div>
              <i className="fas fa-circle-info me-1"></i>
              {t('settings.commonRepository.summary', {
                registries: registries.length,
                groups: groupTotal,
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default CommonRepositoryPanel;
