/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import api from '../services/api';
import { useT } from '../i18n';

const BASE = '/api/admin/rdrs-mail';

const INDEFINITE = '';

const RETENTION_CHOICES = [
  { value: 7, labelKey: 'rdrsMailSettings.retention.days7' },
  { value: 30, labelKey: 'rdrsMailSettings.retention.days30' },
  { value: 90, labelKey: 'rdrsMailSettings.retention.days90' },
  { value: 180, labelKey: 'rdrsMailSettings.retention.months6' },
  { value: 365, labelKey: 'rdrsMailSettings.retention.year1' },
  { value: INDEFINITE, labelKey: 'rdrsMailSettings.retention.indefinite' },
];

// Retention and capture health for RDRS mail. Nestable: give it its own canManage.
const RdrsMailSettingsPanel = ({ canManage = false }) => {
  const { t } = useT();
  const [settings, setSettings] = useState(null);
  const [retention, setRetention] = useState(INDEFINITE);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [busy, setBusy] = useState(null);

  const applySettings = (data) => {
    setSettings(data);
    setRetention(data?.retention_days == null ? INDEFINITE : data.retention_days);
  };

  useEffect(() => {
    (async () => {
      try {
        const resp = await api.get(`${BASE}/settings`);
        applySettings(resp.data);
      } catch (err) {
        toast.error(err.response?.data?.detail || err.response?.data?.error || err.message);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const save = async () => {
    setSaving(true);
    try {
      const resp = await api.put(`${BASE}/settings`, {
        retention_days: retention === INDEFINITE ? null : Number(retention),
      });
      applySettings(resp.data);
      toast.success(t('rdrsMailSettings.toast.saved'));
    } catch (err) {
      toast.error(err.response?.data?.detail || err.response?.data?.error || err.message);
    } finally {
      setSaving(false);
    }
  };

  const run = async (action, successKey) => {
    setBusy(action);
    try {
      const resp = await api.post(`${BASE}/${action}`);
      applySettings(resp.data);
      toast.success(t(successKey, {
        count: resp.data?.captured ?? resp.data?.removed ?? 0,
      }));
    } catch (err) {
      toast.error(err.response?.data?.detail || err.response?.data?.error || err.message);
    } finally {
      setBusy(null);
    }
  };

  if (loading) {
    return (
      <div className="text-center text-muted py-4">
        <span className="spinner-border spinner-border-sm me-2"></span>{t('common.loading')}
      </div>
    );
  }

  const dirty = String(settings?.retention_days ?? INDEFINITE) !== String(retention);
  const when = (iso) => (iso ? new Date(iso).toLocaleString() : t('rdrsMailSettings.never'));
  const pollFailed = settings?.last_poll_status === 'ERROR';

  return (
    <div className="card">
      <div className="card-body">
        <h5 className="card-title mb-1">
          <i className="bi bi-envelope-paper me-2"></i>{t('rdrsMailSettings.title')}
        </h5>
        <p className="text-muted small">{t('rdrsMailSettings.intro')}</p>

        {!settings?.configured && (
          <div className="alert alert-warning py-2 px-3 small">
            <i className="bi bi-exclamation-triangle me-1"></i>{t('rdrsMailSettings.notConfigured')}
          </div>
        )}

        <div className="row g-3 align-items-end mb-3">
          <div className="col-md-5">
            <label className="form-label small fw-semibold mb-1" htmlFor="rdrs-retention">
              {t('rdrsMailSettings.retentionLabel')}
            </label>
            <select id="rdrs-retention" className="form-select" value={retention}
              onChange={(e) => setRetention(e.target.value === INDEFINITE ? INDEFINITE : Number(e.target.value))}
              disabled={!canManage || saving}>
              {RETENTION_CHOICES.map(c => (
                <option key={String(c.value)} value={c.value}>{t(c.labelKey)}</option>
              ))}
            </select>
          </div>
          <div className="col-md-3">
            <button type="button" className="btn btn-primary w-100"
              onClick={save} disabled={!canManage || saving || !dirty}>
              {saving
                ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('common.saving')}</>
                : t('common.save')}
            </button>
          </div>
        </div>

        <div className="alert alert-info py-2 px-3 small">
          <i className="bi bi-info-circle me-1"></i>
          {retention === INDEFINITE
            ? t('rdrsMailSettings.indefiniteNote')
            : t('rdrsMailSettings.purgeNote', { days: retention })}
        </div>

        <dl className="row small mb-3">
          <dt className="col-sm-4">{t('rdrsMailSettings.storedMessages')}</dt>
          <dd className="col-sm-8">{settings?.stored_messages ?? 0}</dd>
          <dt className="col-sm-4">{t('rdrsMailSettings.lastPolled')}</dt>
          <dd className="col-sm-8">{when(settings?.last_polled_at)}</dd>
          <dt className="col-sm-4">{t('rdrsMailSettings.lastResult')}</dt>
          <dd className="col-sm-8">
            {settings?.last_poll_status
              ? <span className={pollFailed ? 'text-danger' : 'text-success'}>
                  {settings.last_poll_status}
                  {settings.last_poll_message ? ` — ${settings.last_poll_message}` : ''}
                </span>
              : <span className="text-muted">{t('rdrsMailSettings.never')}</span>}
          </dd>
        </dl>

        {canManage && (
          <div className="d-flex gap-2">
            <button type="button" className="btn btn-outline-secondary btn-sm"
              onClick={() => run('poll', 'rdrsMailSettings.toast.polled')}
              disabled={busy !== null || !settings?.configured}>
              {busy === 'poll'
                ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('rdrsMailSettings.polling')}</>
                : <><i className="bi bi-arrow-repeat me-1"></i>{t('rdrsMailSettings.pollNow')}</>}
            </button>
            <button type="button" className="btn btn-outline-danger btn-sm"
              onClick={() => run('purge', 'rdrsMailSettings.toast.purged')}
              disabled={busy !== null || retention === INDEFINITE}
              title={retention === INDEFINITE ? t('rdrsMailSettings.purgeDisabled') : undefined}>
              {busy === 'purge'
                ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('rdrsMailSettings.purging')}</>
                : <><i className="bi bi-trash3 me-1"></i>{t('rdrsMailSettings.purgeNow')}</>}
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default RdrsMailSettingsPanel;
