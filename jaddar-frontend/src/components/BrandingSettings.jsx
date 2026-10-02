/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useEffect, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import api from '../services/api';
import BrandingLogo, { notifyLogoChanged } from './BrandingLogo';
import { useT } from '../i18n';

/*
 * Upload / preview / remove for the deployment logo shown on the login screen and in
 * the header. Uploading replaces whatever is there; removing reverts to the bundled
 * JADDAR mark. Reads are open to anyone who can reach Settings, but the controls are
 * gated on `canManage` (admin) to match the backend.
 */

// Kept in step with BrandingService on the backend so we can reject early with a
// clearer message than a round-trip would give.
const MAX_BYTES = 2 * 1024 * 1024;
const ACCEPTED_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'];
const ACCEPT_ATTR = ACCEPTED_TYPES.join(',');

// Mirrors BrandingService on the backend.
const MAX_NOTICES = 10;
const MAX_NOTICE_LENGTH = 500;

const formatBytes = (bytes) => {
  if (!bytes && bytes !== 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

// GlobalExceptionHandler renders errors as {"error": "..."}; `detail` is the older
// FastAPI shape that some backend paths still return.
const errorMessage = (err, fallback) =>
  err?.response?.data?.error || err?.response?.data?.detail || fallback;

const BrandingSettings = ({ canManage = false }) => {
  const [info, setInfo] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const fileInputRef = useRef(null);
  const { t } = useT();

  // Login notices. `rows` is the working copy the editor mutates; it is only sent to
  // the backend on Save, which replaces the stored list wholesale.
  const [rows, setRows] = useState([]);
  const [savingNotices, setSavingNotices] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await api.get('/api/branding/notices');
        if (!cancelled) setRows(res.data?.notices?.map(n => ({ text: n.text || '', url: n.url || '' })) || []);
      } catch (error) {
        // Leave the editor empty; the admin can still define notices from scratch.
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await api.get('/api/branding/logo/info');
        if (!cancelled) setInfo(res.data);
      } catch (err) {
        // Treat an unreadable info call as "no custom logo" — the preview below still
        // reflects reality, since it loads the image endpoint directly.
        if (!cancelled) setInfo({ present: false });
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const handleFileSelected = async (event) => {
    const file = event.target.files?.[0];
    // Clear the input so picking the same file again after an error still fires onChange.
    event.target.value = '';
    if (!file) return;

    if (!ACCEPTED_TYPES.includes(file.type)) {
      toast.error(t('branding.errors.unsupportedType'));
      return;
    }
    if (file.size > MAX_BYTES) {
      toast.error(t('branding.errors.tooLarge'));
      return;
    }

    setBusy(true);
    try {
      const form = new FormData();
      form.append('file', file);
      const res = await api.post('/api/admin/branding/logo', form);
      setInfo(res.data);
      notifyLogoChanged(true);
      toast.success(t('branding.toast.uploaded'));
    } catch (err) {
      toast.error(errorMessage(err, t('branding.toast.uploadFailed')));
    } finally {
      setBusy(false);
    }
  };

  const handleRemove = async () => {
    setBusy(true);
    try {
      await api.delete('/api/admin/branding/logo');
      setInfo({ present: false });
      notifyLogoChanged(false);
      toast.success(t('branding.toast.removed'));
    } catch (err) {
      toast.error(errorMessage(err, t('branding.toast.removeFailed')));
    } finally {
      setBusy(false);
    }
  };

  // ==================== Login notices ====================

  const updateRow = (index, field, value) =>
    setRows(prev => prev.map((row, i) => (i === index ? { ...row, [field]: value } : row)));

  const addRow = () => setRows(prev => [...prev, { text: '', url: '' }]);

  const removeRow = (index) => setRows(prev => prev.filter((_, i) => i !== index));

  const moveRow = (index, delta) =>
    setRows(prev => {
      const target = index + delta;
      if (target < 0 || target >= prev.length) return prev;
      const next = [...prev];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });

  const handleSaveNotices = async () => {
    setSavingNotices(true);
    try {
      // Drop rows the admin left entirely blank rather than making them delete each one.
      const payload = rows
        .map(r => ({ text: r.text.trim(), url: r.url.trim() }))
        .filter(r => r.text || r.url);
      const res = await api.put('/api/admin/branding/notices', { notices: payload });
      setRows(res.data?.notices?.map(n => ({ text: n.text || '', url: n.url || '' })) || []);
      toast.success(t('branding.notices.toast.saved'));
    } catch (err) {
      toast.error(errorMessage(err, t('branding.notices.toast.saveFailed')));
    } finally {
      setSavingNotices(false);
    }
  };

  const hasLogo = Boolean(info?.present);

  const noticesCard = (
    <div className="card border-0 shadow-sm" style={{ marginTop: '24px' }}>
      <div className="card-header bg-white">
        <h5 className="mb-0"><i className="bi bi-info-circle"></i> {t('branding.notices.title')}</h5>
      </div>
      <div className="card-body">
        <p className="text-muted" style={{ maxWidth: 640 }}>{t('branding.notices.description')}</p>

        {!canManage ? (
          rows.length === 0 ? (
            <p className="text-muted mb-0">{t('branding.notices.none')}</p>
          ) : (
            <ul className="mb-0">
              {rows.map((row, i) => (
                <li key={i}>{row.text}{row.url ? ` — ${row.url}` : ''}</li>
              ))}
            </ul>
          )
        ) : (
          <>
            {rows.length === 0 && (
              <p className="text-muted small">{t('branding.notices.empty')}</p>
            )}

            {rows.map((row, i) => (
              <div key={i} className="border rounded p-3 mb-2">
                <div className="row g-2">
                  <div className="col-md-7">
                    <label className="form-label small mb-1">{t('branding.notices.textLabel')}</label>
                    <input
                      type="text"
                      className="form-control form-control-sm"
                      maxLength={MAX_NOTICE_LENGTH}
                      value={row.text}
                      onChange={(e) => updateRow(i, 'text', e.target.value)}
                      placeholder={t('branding.notices.textPlaceholder')}
                    />
                  </div>
                  <div className="col-md-5">
                    <label className="form-label small mb-1">{t('branding.notices.urlLabel')}</label>
                    <input
                      type="url"
                      className="form-control form-control-sm"
                      maxLength={MAX_NOTICE_LENGTH}
                      value={row.url}
                      onChange={(e) => updateRow(i, 'url', e.target.value)}
                      placeholder="https://example.com/privacy"
                    />
                  </div>
                </div>
                <div className="d-flex justify-content-end gap-1 mt-2">
                  <button
                    type="button"
                    className="btn btn-outline-secondary btn-sm"
                    onClick={() => moveRow(i, -1)}
                    disabled={i === 0}
                    title={t('branding.notices.moveUp')}
                  >
                    <i className="bi bi-arrow-up"></i>
                  </button>
                  <button
                    type="button"
                    className="btn btn-outline-secondary btn-sm"
                    onClick={() => moveRow(i, 1)}
                    disabled={i === rows.length - 1}
                    title={t('branding.notices.moveDown')}
                  >
                    <i className="bi bi-arrow-down"></i>
                  </button>
                  <button
                    type="button"
                    className="btn btn-outline-danger btn-sm"
                    onClick={() => removeRow(i)}
                    title={t('branding.notices.removeItem')}
                  >
                    <i className="bi bi-trash"></i>
                  </button>
                </div>
              </div>
            ))}

            <div className="d-flex flex-wrap gap-2 mt-3">
              <button
                type="button"
                className="btn btn-outline-secondary"
                onClick={addRow}
                disabled={rows.length >= MAX_NOTICES}
              >
                <i className="bi bi-plus-lg me-2"></i>{t('branding.notices.add')}
              </button>
              <button
                type="button"
                className="btn btn-primary"
                onClick={handleSaveNotices}
                disabled={savingNotices}
              >
                {savingNotices ? t('branding.notices.saving') : t('branding.notices.save')}
              </button>
            </div>
            <div className="text-muted small" style={{ marginTop: 12 }}>
              {t('branding.notices.hint', { max: MAX_NOTICES })}
            </div>
          </>
        )}
      </div>
    </div>
  );

  return (
    <>
    <div className="card border-0 shadow-sm">
      <div className="card-header bg-white">
        <h5 className="mb-0"><i className="bi bi-image me-2"></i>{t('branding.title')}</h5>
      </div>
      <div className="card-body">
        <p className="text-muted" style={{ maxWidth: 640 }}>{t('branding.description')}</p>

        <div className="d-flex flex-wrap align-items-start gap-4">
          {/* Preview */}
          <div>
            <div
              className="d-flex align-items-center justify-content-center bg-light border rounded"
              style={{ width: 140, height: 140, overflow: 'hidden', padding: 12 }}
            >
              <BrandingLogo
                alt={t('branding.previewAlt')}
                style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
              />
            </div>
            <div className="text-muted small text-center mt-2" style={{ width: 140 }}>
              {loading ? t('branding.loading') : hasLogo ? t('branding.customLogo') : t('branding.defaultLogo')}
            </div>
          </div>

          {/* Controls */}
          <div style={{ flex: '1 1 320px', minWidth: 280 }}>
            {hasLogo && (
              <dl className="row mb-3 small">
                <dt className="col-sm-4 text-muted fw-normal">{t('branding.meta.file')}</dt>
                <dd className="col-sm-8 mb-1">{info.filename || '—'}</dd>
                <dt className="col-sm-4 text-muted fw-normal">{t('branding.meta.size')}</dt>
                <dd className="col-sm-8 mb-1">{formatBytes(info.sizeBytes)}</dd>
                {info.updatedBy && (
                  <>
                    <dt className="col-sm-4 text-muted fw-normal">{t('branding.meta.updatedBy')}</dt>
                    <dd className="col-sm-8 mb-1">{info.updatedBy}</dd>
                  </>
                )}
                {info.updatedAt && (
                  <>
                    <dt className="col-sm-4 text-muted fw-normal">{t('branding.meta.updatedAt')}</dt>
                    <dd className="col-sm-8 mb-1">{new Date(info.updatedAt).toLocaleString()}</dd>
                  </>
                )}
              </dl>
            )}

            {canManage ? (
              <>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept={ACCEPT_ATTR}
                  onChange={handleFileSelected}
                  className="d-none"
                />
                <div className="d-flex flex-wrap gap-2">
                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={busy}
                    onClick={() => fileInputRef.current?.click()}
                  >
                    <i className="bi bi-upload me-2"></i>
                    {hasLogo ? t('branding.actions.replace') : t('branding.actions.upload')}
                  </button>
                  {hasLogo && (
                    <button
                      type="button"
                      className="btn btn-outline-danger"
                      disabled={busy}
                      onClick={handleRemove}
                    >
                      <i className="bi bi-trash me-2"></i>
                      {t('branding.actions.remove')}
                    </button>
                  )}
                </div>
                <div className="text-muted small mt-3">{t('branding.requirements')}</div>
              </>
            ) : (
              <div className="alert alert-info mb-0 d-flex align-items-center">
                <i className="bi bi-info-circle me-2"></i>
                <div>{t('branding.adminOnly')}</div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>

    {noticesCard}
    </>
  );
};

export default BrandingSettings;
