/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useCallback, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import {
  getRegistryPublication,
  updateRegistryPublication,
  announceToRegistry,
  previewRegistryPublication,
} from '../services/api';
import { useT } from '../i18n';

const STATUS_BADGES = {
  NOT_REGISTERED: 'secondary',
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  SUSPENDED: 'danger',
};

const ACCESS_LEVEL_KEYS = ['public', 'basic', 'enhanced', 'full'];
const ACCESS_LEVEL_COLORS = ['secondary', 'info', 'warning', 'danger'];

/** What a template offers, as it will read to someone browsing the repository: the request types, the access level each grants, and what a requestor w... */
const RequestTypeRows = ({ requestTypes }) => {
  const { t } = useT();
  if (!requestTypes || requestTypes.length === 0) {
    return <div className="text-muted small fst-italic">{t('commonRepository.preview.noRequestTypes')}</div>;
  }
  return (
    <div className="d-flex flex-column gap-1 mt-1">
      {requestTypes.map(rt => {
        const params = rt.customParameters || [];
        const required = params.filter(p => p.required);
        return (
          <div key={rt.typeCode || rt.name} className="d-flex flex-wrap align-items-center gap-1">
            <span className="small">{rt.name}</span>
            <span className={`badge bg-${ACCESS_LEVEL_COLORS[rt.accessLevel] || 'secondary'}`} style={{ fontSize: 10 }}>
              L{rt.accessLevel}
              {ACCESS_LEVEL_KEYS[rt.accessLevel]
                ? ` ${t(`templates.accessLevels.${ACCESS_LEVEL_KEYS[rt.accessLevel]}`)}` : ''}
            </span>
            {rt.kind === 'RDRS' && (
              <span className="badge bg-dark" style={{ fontSize: 10 }}>RDRS</span>
            )}
            {rt.supportsConfidential && (
              <span className="badge bg-warning text-dark" style={{ fontSize: 10 }}>
                {t('templates.requestType.confidential')}
              </span>
            )}
            {rt.supportsExigent && (
              <span className="badge bg-danger" style={{ fontSize: 10 }}>
                {t('templates.requestType.exigent')}
              </span>
            )}
            {rt.requiresManualApproval && (
              <span className="badge bg-secondary" style={{ fontSize: 10 }}>
                <i className="fa-solid fa-user-check me-1"></i>
                {t('commonRepository.preview.manualApproval')}
              </span>
            )}
            {params.length > 0 && (
              <span
                className="badge bg-primary"
                style={{ fontSize: 10 }}
                title={params.map(p => p.name + (p.required ? ' *' : '')).join(', ')}
              >
                <i className="fa-solid fa-cube me-1"></i>
                {t('commonRepository.preview.parameters', {
                  count: params.length,
                  required: required.length,
                })}
              </span>
            )}
          </div>
        );
      })}
    </div>
  );
};

const EMPTY = {
  enabled: false,
  registryUrl: '',
  registryCode: '',
  registryName: '',
  registryDescription: '',
  publicBaseUrl: '',
  contactEmail: '',
};

const CommonRepositorySettings = ({ canManage = false }) => {
  const { t } = useT();
  const [form, setForm] = useState(EMPTY);
  const [status, setStatus] = useState(null);
  const [preview, setPreview] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [announcing, setAnnouncing] = useState(false);

  const applySettings = useCallback((data) => {
    setStatus(data);
    setForm({
      enabled: !!data.enabled,
      registryUrl: data.registryUrl || '',
      registryCode: data.registryCode || '',
      registryName: data.registryName || '',
      registryDescription: data.registryDescription || '',
      publicBaseUrl: data.publicBaseUrl || '',
      contactEmail: data.contactEmail || '',
    });
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [settings, previewData] = await Promise.all([
        getRegistryPublication(),
        previewRegistryPublication(),
      ]);
      applySettings(settings);
      setPreview(previewData);
    } catch {
      toast.error(t('commonRepository.errors.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [applySettings, t]);

  useEffect(() => { load(); }, [load]);

  const set = (field, value) => setForm(prev => ({ ...prev, [field]: value }));

  const handleSave = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      const res = await updateRegistryPublication(form);
      if (res && res.success === false) {
        toast.error(res.message || t('commonRepository.errors.saveFailed'));
        return;
      }
      applySettings(res);
      setPreview(await previewRegistryPublication());
      toast.success(t('commonRepository.toasts.saved'));
    } catch {
      toast.error(t('commonRepository.errors.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const handleAnnounce = async () => {
    setAnnouncing(true);
    try {
      const res = await announceToRegistry();
      applySettings(res);
      if (res.lastAnnounceStatus === 'SUCCESS') toast.success(t('commonRepository.toasts.announced'));
      else toast.error(res.lastAnnounceMessage || t('commonRepository.errors.announceFailed'));
    } catch {
      toast.error(t('commonRepository.errors.announceFailed'));
    } finally {
      setAnnouncing(false);
    }
  };

  if (loading) {
    return <div className="d-flex justify-content-center py-5"><div className="spinner-border text-primary" /></div>;
  }

  const registrationStatus = status?.registrationStatus || 'NOT_REGISTERED';
  const previewGroups = preview?.dataHolderGroups || [];
  const templateCount = preview?.templateCount ?? 0;

  return (
    <div>
      <div className="alert alert-light border d-flex gap-3">
        <i className="fa-solid fa-globe fa-lg mt-1 text-primary"></i>
        <div>
          <div className="fw-semibold">{t('commonRepository.intro.title')}</div>
          <div className="small text-muted">{t('commonRepository.intro.body')}</div>
        </div>
      </div>

      <div className="row g-4">
        <div className="col-lg-7">
          <form onSubmit={handleSave}>
            <div className="card">
              <div className="card-header d-flex justify-content-between align-items-center">
                <span className="fw-semibold">{t('commonRepository.settings.title')}</span>
                <span className={`badge bg-${STATUS_BADGES[registrationStatus] || 'secondary'}`}>
                  {t(`commonRepository.status.${registrationStatus.toLowerCase()}`)}
                </span>
              </div>
              <div className="card-body">
                <div className="form-check form-switch mb-3">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    role="switch"
                    id="publicationEnabled"
                    checked={form.enabled}
                    disabled={!canManage}
                    onChange={e => set('enabled', e.target.checked)}
                  />
                  <label className="form-check-label" htmlFor="publicationEnabled">
                    {t('commonRepository.settings.enabled')}
                  </label>
                  <div className="form-text">{t('commonRepository.settings.enabledHint')}</div>
                </div>

                <div className="mb-3">
                  <label className="form-label" htmlFor="registryUrl">{t('commonRepository.settings.url')}</label>
                  <input
                    id="registryUrl"
                    className="form-control"
                    placeholder="https://registry.example.org/dataholder-registry"
                    value={form.registryUrl}
                    disabled={!canManage}
                    onChange={e => set('registryUrl', e.target.value)}
                  />
                  <div className="form-text">{t('commonRepository.settings.urlHint')}</div>
                </div>

                <div className="row g-3">
                  <div className="col-12">
                    <div className="small text-muted border-top pt-3">
                      {t('commonRepository.settings.identityHeading')}
                    </div>
                  </div>
                  <div className="col-md-5">
                    <label className="form-label" htmlFor="registryCode">{t('commonRepository.settings.code')}</label>
                    <input
                      id="registryCode"
                      className="form-control"
                      placeholder="EXAMPLE-REG"
                      value={form.registryCode}
                      disabled={!canManage}
                      onChange={e => set('registryCode', e.target.value.toUpperCase())}
                    />
                    <div className="form-text">{t('commonRepository.settings.codeHint')}</div>
                  </div>
                  <div className="col-md-7">
                    <label className="form-label" htmlFor="registryName">{t('commonRepository.settings.name')}</label>
                    <input
                      id="registryName"
                      className="form-control"
                      value={form.registryName}
                      disabled={!canManage}
                      onChange={e => set('registryName', e.target.value)}
                    />
                    <div className="form-text">{t('commonRepository.settings.nameHint')}</div>
                  </div>
                  <div className="col-12">
                    <label className="form-label" htmlFor="registryDescription">{t('common.description')}</label>
                    <textarea
                      id="registryDescription"
                      className="form-control"
                      rows={2}
                      value={form.registryDescription}
                      disabled={!canManage}
                      onChange={e => set('registryDescription', e.target.value)}
                    />
                  </div>
                  <div className="col-md-7">
                    <label className="form-label" htmlFor="publicBaseUrl">{t('commonRepository.settings.baseUrl')}</label>
                    <input
                      id="publicBaseUrl"
                      className="form-control"
                      placeholder="https://groups.example.org/dh-group-admin"
                      value={form.publicBaseUrl}
                      disabled={!canManage}
                      onChange={e => set('publicBaseUrl', e.target.value)}
                    />
                    <div className="form-text">{t('commonRepository.settings.baseUrlHint')}</div>
                  </div>
                  <div className="col-md-5">
                    <label className="form-label" htmlFor="contactEmail">{t('commonRepository.settings.contact')}</label>
                    <input
                      id="contactEmail"
                      type="email"
                      className="form-control"
                      value={form.contactEmail}
                      disabled={!canManage}
                      onChange={e => set('contactEmail', e.target.value)}
                    />
                  </div>
                </div>
              </div>
              {canManage && (
                <div className="card-footer d-flex gap-2">
                  <button className="btn btn-primary" type="submit" disabled={saving}>
                    {saving ? <span className="spinner-border spinner-border-sm me-2" /> : <i className="fa-solid fa-floppy-disk me-1"></i>}
                    {t('common.save')}
                  </button>
                  <button
                    className="btn btn-outline-secondary"
                    type="button"
                    onClick={handleAnnounce}
                    disabled={announcing || !status?.enabled}
                    title={status?.enabled ? '' : t('commonRepository.settings.enableFirst')}
                  >
                    {announcing ? <span className="spinner-border spinner-border-sm me-2" /> : <i className="fa-solid fa-bullhorn me-1"></i>}
                    {t('commonRepository.settings.announceNow')}
                  </button>
                </div>
              )}
            </div>
          </form>

          {status?.lastAnnounceStatus && (
            <div className={`alert mt-3 ${status.lastAnnounceStatus === 'SUCCESS' ? 'alert-success' : 'alert-danger'}`}>
              <div className="fw-semibold">
                {t(`commonRepository.lastAnnounce.${status.lastAnnounceStatus === 'SUCCESS' ? 'ok' : 'failed'}`)}
              </div>
              <div className="small">{status.lastAnnounceMessage}</div>
              {status.lastAnnouncedAt && (
                <div className="small text-muted mt-1">
                  {new Date(status.lastAnnouncedAt).toLocaleString()}
                </div>
              )}
            </div>
          )}
        </div>

        <div className="col-lg-5">
          <div className="card">
            <div className="card-header fw-semibold">
              {t('commonRepository.preview.title', {
                groups: previewGroups.length,
                templates: templateCount,
              })}
            </div>
            <div className="card-body">
              <p className="small text-muted">{t('commonRepository.preview.body')}</p>
              {previewGroups.length === 0 ? (
                <div className="text-center text-muted py-4">
                  <i className="fa-solid fa-inbox fa-2x mb-2"></i>
                  <div className="small">{t('commonRepository.preview.empty')}</div>
                </div>
              ) : (
                <div className="d-flex flex-column gap-3">
                  {previewGroups.map(group => (
                    <div key={group.groupId ?? group.name}>
                      <div className="d-flex justify-content-between align-items-center">
                        <span className="fw-semibold">
                          <i className="fa-solid fa-sitemap me-2 text-muted"></i>{group.name}
                        </span>
                        <span className="badge bg-light text-dark border">
                          {t('commonRepository.preview.templateCount', {
                            count: (group.templates || []).length,
                          })}
                        </span>
                      </div>
                      {group.description && (
                        <div className="text-muted small">{group.description}</div>
                      )}
                      {(group.templates || []).length === 0 ? (
                        <div className="text-muted small fst-italic mt-1">
                          {t('commonRepository.preview.groupEmpty')}
                        </div>
                      ) : (
                        <ul className="list-group list-group-flush mt-1">
                          {group.templates.map(tpl => (
                            <li key={tpl.templateId} className="list-group-item px-0 py-2 border-0">
                              <div className="small fw-semibold">{tpl.name}</div>
                              <code className="text-muted" style={{ fontSize: 11 }}>{tpl.templateId}</code>
                              <RequestTypeRows requestTypes={tpl.requestTypes} />
                              {(tpl.maxQueriesPerDay || tpl.maxQueriesPerMonth) && (
                                <div className="text-muted mt-1" style={{ fontSize: 11 }}>
                                  <i className="fa-solid fa-gauge me-1"></i>
                                  {t('commonRepository.preview.limits', {
                                    day: tpl.maxQueriesPerDay ?? '—',
                                    month: tpl.maxQueriesPerMonth ?? '—',
                                  })}
                                </div>
                              )}
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CommonRepositorySettings;
