/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback } from 'react';
import { getPublicDataHolderGroups, submitDataHolderApplication } from '../services/api';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const INITIAL_FORM = {
  dataHolderGroupId: '',
  dataHolderGroupName: '',
  organizationName: '',
  organizationAddress: '',
  organizationPhone: '',
  contactFullName: '',
  contactEmail: '',
  contactPhone: '',
  contactTitle: '',
  rdapServerUrls: '',
  supportedTlds: '',
  additionalNotes: '',
};

const JoinDataHolderGroup = () => {
  const { t } = useT();
  const [groups, setGroups] = useState([]);
  const [loadingGroups, setLoadingGroups] = useState(true);
  const [form, setForm] = useState(INITIAL_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [result, setResult] = useState(null);
  const [manualEntry, setManualEntry] = useState(false);

  // Dynamic RDAP URL list
  const [rdapUrls, setRdapUrls] = useState(['']);
  // Dynamic TLD list
  const [tlds, setTlds] = useState(['']);

  const loadGroups = useCallback(async () => {
    try {
      const res = await getPublicDataHolderGroups();
      setGroups(res || []);
    } catch {
      toast.error(t('joinGroup.errors.loadGroups'));
    } finally {
      setLoadingGroups(false);
    }
  }, []);

  useEffect(() => { loadGroups(); }, [loadGroups]);

  const set = (field) => (e) => setForm(prev => ({ ...prev, [field]: e.target.value }));

  // RDAP URL helpers
  const addRdapUrl = () => setRdapUrls(prev => [...prev, '']);
  const removeRdapUrl = (idx) => setRdapUrls(prev => prev.filter((_, i) => i !== idx));
  const updateRdapUrl = (idx, val) => setRdapUrls(prev => prev.map((u, i) => i === idx ? val : u));

  // TLD helpers
  const addTld = () => setTlds(prev => [...prev, '']);
  const removeTld = (idx) => setTlds(prev => prev.filter((_, i) => i !== idx));
  const updateTld = (idx, val) => setTlds(prev => prev.map((t, i) => i === idx ? val : t));

  const handleSubmit = async (e) => {
    e.preventDefault();

    // Validate required fields
    if (!manualEntry && !form.dataHolderGroupId) {
      toast.error(t('joinGroup.validation.selectGroup'));
      return;
    }
    if (manualEntry && !form.dataHolderGroupName.trim()) {
      toast.error(t('joinGroup.validation.enterGroupName'));
      return;
    }
    if (!form.organizationName.trim()) {
      toast.error(t('joinGroup.validation.organizationNameRequired'));
      return;
    }
    if (!form.contactFullName.trim()) {
      toast.error(t('joinGroup.validation.contactFullNameRequired'));
      return;
    }
    if (!form.contactEmail.trim()) {
      toast.error(t('joinGroup.validation.contactEmailRequired'));
      return;
    }

    // Collect RDAP URLs (filter blanks)
    const cleanRdapUrls = rdapUrls.map(u => u.trim()).filter(Boolean);
    if (cleanRdapUrls.length === 0) {
      toast.error(t('joinGroup.validation.rdapRequired'));
      return;
    }

    // Collect TLDs (filter blanks, normalize)
    const cleanTlds = tlds
      .map(t => t.trim())
      .filter(Boolean)
      .map(t => t.startsWith('.') ? t : '.' + t);
    if (cleanTlds.length === 0) {
      toast.error(t('joinGroup.validation.tldRequired'));
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        ...form,
        dataHolderGroupId: manualEntry ? null : parseInt(form.dataHolderGroupId),
        dataHolderGroupName: manualEntry ? form.dataHolderGroupName.trim() : null,
        rdapServerUrls: cleanRdapUrls.join(', '),
        supportedTlds: cleanTlds.join(', '),
      };
      const res = await submitDataHolderApplication(payload);
      if (res.success) {
        setResult(res);
        setSubmitted(true);
        toast.success(t('joinGroup.toasts.submitSuccess'));
      } else {
        toast.error(res.error || t('joinGroup.toasts.submitFailed'));
      }
    } catch (err) {
      toast.error(err.data?.error || err.message || t('joinGroup.toasts.submitFailedRetry'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleReset = () => {
    setForm(INITIAL_FORM);
    setRdapUrls(['']);
    setTlds(['']);
    setSubmitted(false);
    setResult(null);
    setManualEntry(false);
  };

  // ==================== Success Screen ====================
  if (submitted && result) {
    return (
      <div className="min-vh-100 d-flex align-items-center justify-content-center bg-dark">
        <div className="card shadow" style={{ width: '100%', maxWidth: 540 }}>
          <div className="card-body p-4 text-center">
            <div className="mb-3">
              <i className="fa-solid fa-circle-check fa-3x text-success"></i>
            </div>
            <h4 className="fw-bold mb-2">{t('joinGroup.success.title')}</h4>
            <p className="text-muted">
              {t('joinGroup.success.messagePrefix')} <strong>{form.contactEmail}</strong>.
            </p>
            <div className="alert alert-light text-start mt-3 mb-3">
              <div className="d-flex justify-content-between mb-1">
                <span className="text-muted small">{t('joinGroup.success.applicationId')}</span>
                <strong className="small">{result.applicationId}</strong>
              </div>
              <div className="d-flex justify-content-between">
                <span className="text-muted small">{t('common.status')}</span>
                <span className="badge bg-warning">{result.status}</span>
              </div>
            </div>
            <div className="d-flex gap-2 justify-content-center">
              <button className="btn btn-primary" onClick={handleReset}>
                <i className="fa-solid fa-plus me-1"></i>{t('joinGroup.success.submitAnother')}
              </button>
              <a href="/" className="btn btn-outline-secondary">
                <i className="fa-solid fa-arrow-left me-1"></i>{t('joinGroup.success.home')}
              </a>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // ==================== Main Form ====================
  return (
    <div className="min-vh-100 bg-dark py-5">
      <div className="container">
        <div className="row justify-content-center">
          <div className="col-lg-8 col-xl-7">
            <div className="card shadow">
              <div className="card-body p-4 p-md-5">
                {/* Header */}
                <div className="text-center mb-4">
                  <i className="fa-solid fa-server fa-2x text-primary mb-2"></i>
                  <h3 className="fw-bold mb-1">{t('joinGroup.header.title')}</h3>
                  <p className="text-muted">
                    {t('joinGroup.header.subtitle')}
                  </p>
                </div>

                <form onSubmit={handleSubmit}>
                  {/* ===== Data Holder Group Selection ===== */}
                  <div className="mb-4">
                    <label className="form-label fw-semibold">
                      <i className="fa-solid fa-layer-group text-primary me-1"></i>
                      {t('joinGroup.form.dataHolderGroup')} <span className="text-danger">*</span>
                    </label>
                    {loadingGroups ? (
                      <div className="text-muted small">
                        <span className="spinner-border spinner-border-sm me-1"></span>{t('joinGroup.form.loadingGroups')}
                      </div>
                    ) : (
                      <>
                        {!manualEntry ? (
                          <>
                            <select
                              className="form-select"
                              value={form.dataHolderGroupId}
                              onChange={set('dataHolderGroupId')}
                            >
                              <option value="">{t('joinGroup.form.selectGroup')}</option>
                              {groups.map(g => (
                                <option key={g.id} value={g.id}>{g.name}</option>
                              ))}
                            </select>
                            {form.dataHolderGroupId && groups.find(g => String(g.id) === form.dataHolderGroupId)?.description && (
                              <div className="form-text">
                                {groups.find(g => String(g.id) === form.dataHolderGroupId).description}
                              </div>
                            )}
                          </>
                        ) : (
                          <>
                            <input
                              className="form-control"
                              value={form.dataHolderGroupName}
                              onChange={set('dataHolderGroupName')}
                              placeholder={t('joinGroup.form.groupNamePlaceholder')}
                            />
                            <div className="form-text">
                              {t('joinGroup.form.groupNameHelp')}
                            </div>
                          </>
                        )}
                        <div className="form-text mt-2">
                          <button
                            type="button"
                            className="btn btn-link btn-sm p-0 text-decoration-none"
                            onClick={() => {
                              setManualEntry(!manualEntry);
                              setForm(prev => ({ ...prev, dataHolderGroupId: '', dataHolderGroupName: '' }));
                            }}
                          >
                            <i className={`fa-solid fa-${manualEntry ? 'list' : 'keyboard'} me-1`}></i>
                            {manualEntry ? t('joinGroup.form.selectFromAvailable') : t('joinGroup.form.enterManually')}
                          </button>
                        </div>
                      </>
                    )}
                  </div>

                  <hr className="my-4" />

                  {/* ===== Organization Information ===== */}
                  <h6 className="fw-semibold text-secondary text-uppercase mb-3" style={{ fontSize: 12, letterSpacing: '0.05em' }}>
                    <i className="fa-solid fa-building me-1"></i> {t('joinGroup.organization.sectionTitle')}
                  </h6>
                  <div className="row g-3 mb-4">
                    <div className="col-12">
                      <label className="form-label">{t('joinGroup.organization.name')} <span className="text-danger">*</span></label>
                      <input className="form-control" value={form.organizationName} onChange={set('organizationName')}
                        placeholder={t('joinGroup.organization.namePlaceholder')} required />
                    </div>
                    <div className="col-12">
                      <label className="form-label">{t('joinGroup.organization.address')}</label>
                      <textarea className="form-control" rows={2} value={form.organizationAddress}
                        onChange={set('organizationAddress')}
                        placeholder={t('joinGroup.organization.addressPlaceholder')} />
                    </div>
                    <div className="col-md-6">
                      <label className="form-label">{t('joinGroup.organization.phone')}</label>
                      <input className="form-control" type="tel" value={form.organizationPhone}
                        onChange={set('organizationPhone')} placeholder="+1 (555) 123-4567" />
                    </div>
                  </div>

                  <hr className="my-4" />

                  {/* ===== Admin Contact ===== */}
                  <h6 className="fw-semibold text-secondary text-uppercase mb-3" style={{ fontSize: 12, letterSpacing: '0.05em' }}>
                    <i className="fa-solid fa-user-tie me-1"></i> {t('joinGroup.contact.sectionTitle')}
                  </h6>
                  <div className="row g-3 mb-4">
                    <div className="col-md-6">
                      <label className="form-label">{t('joinGroup.contact.fullName')} <span className="text-danger">*</span></label>
                      <input className="form-control" value={form.contactFullName} onChange={set('contactFullName')}
                        placeholder="Jane Smith" required />
                    </div>
                    <div className="col-md-6">
                      <label className="form-label">{t('joinGroup.contact.title')}</label>
                      <input className="form-control" value={form.contactTitle} onChange={set('contactTitle')}
                        placeholder={t('joinGroup.contact.titlePlaceholder')} />
                    </div>
                    <div className="col-md-6">
                      <label className="form-label">{t('common.email')} <span className="text-danger">*</span></label>
                      <input className="form-control" type="email" value={form.contactEmail}
                        onChange={set('contactEmail')} placeholder="jane@acmeregistry.com" required />
                    </div>
                    <div className="col-md-6">
                      <label className="form-label">{t('joinGroup.contact.phone')}</label>
                      <input className="form-control" type="tel" value={form.contactPhone}
                        onChange={set('contactPhone')} placeholder="+1 (555) 987-6543" />
                    </div>
                  </div>

                  <hr className="my-4" />

                  {/* ===== RDAP Servers ===== */}
                  <h6 className="fw-semibold text-secondary text-uppercase mb-3" style={{ fontSize: 12, letterSpacing: '0.05em' }}>
                    <i className="fa-solid fa-globe me-1"></i> {t('joinGroup.rdap.sectionTitle')}
                  </h6>
                  <p className="text-muted small mb-3">
                    {t('joinGroup.rdap.help')}
                  </p>
                  {rdapUrls.map((url, idx) => (
                    <div className="input-group mb-2" key={idx}>
                      <span className="input-group-text"><i className="fa-solid fa-link"></i></span>
                      <input
                        className="form-control"
                        value={url}
                        onChange={(e) => updateRdapUrl(idx, e.target.value)}
                        placeholder="https://rdap.example.com"
                      />
                      {rdapUrls.length > 1 && (
                        <button type="button" className="btn btn-outline-danger" onClick={() => removeRdapUrl(idx)}
                          title={t('common.remove')}>
                          <i className="fa-solid fa-xmark"></i>
                        </button>
                      )}
                    </div>
                  ))}
                  <button type="button" className="btn btn-outline-secondary btn-sm mb-4"
                    onClick={addRdapUrl}>
                    <i className="fa-solid fa-plus me-1"></i>{t('joinGroup.rdap.addServer')}
                  </button>

                  <hr className="my-4" />

                  {/* ===== Supported TLDs ===== */}
                  <h6 className="fw-semibold text-secondary text-uppercase mb-3" style={{ fontSize: 12, letterSpacing: '0.05em' }}>
                    <i className="fa-solid fa-tags me-1"></i> {t('joinGroup.tld.sectionTitle')}
                  </h6>
                  <p className="text-muted small mb-3">
                    {t('joinGroup.tld.helpBefore')} <code>.com</code>{t('joinGroup.tld.helpAfter')}
                  </p>
                  <div className="d-flex flex-wrap gap-2 mb-2">
                    {tlds.map((tld, idx) => (
                      <div className="input-group" style={{ width: 180 }} key={idx}>
                        <input
                          className="form-control"
                          value={tld}
                          onChange={(e) => updateTld(idx, e.target.value)}
                          placeholder=".com"
                        />
                        {tlds.length > 1 && (
                          <button type="button" className="btn btn-outline-danger btn-sm" onClick={() => removeTld(idx)}
                            title={t('common.remove')}>
                            <i className="fa-solid fa-xmark"></i>
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                  <button type="button" className="btn btn-outline-secondary btn-sm mb-4"
                    onClick={addTld}>
                    <i className="fa-solid fa-plus me-1"></i>{t('joinGroup.tld.addTld')}
                  </button>

                  <hr className="my-4" />

                  {/* ===== Additional Notes ===== */}
                  <div className="mb-4">
                    <label className="form-label fw-semibold">
                      <i className="fa-solid fa-comment me-1 text-secondary"></i> {t('joinGroup.notes.label')}
                    </label>
                    <textarea className="form-control" rows={3} value={form.additionalNotes}
                      onChange={set('additionalNotes')}
                      placeholder={t('joinGroup.notes.placeholder')} />
                  </div>

                  {/* ===== Submit ===== */}
                  <button
                    type="submit"
                    className="btn btn-primary btn-lg w-100"
                    disabled={submitting || loadingGroups}
                  >
                    {submitting ? (
                      <><span className="spinner-border spinner-border-sm me-2"></span>{t('joinGroup.submit.submitting')}</>
                    ) : (
                      <><i className="fa-solid fa-paper-plane me-2"></i>{t('joinGroup.submit.submit')}</>
                    )}
                  </button>
                  <p className="text-muted text-center small mt-2 mb-0">
                    {t('joinGroup.submit.disclaimer')}
                  </p>
                </form>
              </div>
            </div>

            {/* Footer link */}
            <div className="text-center mt-3">
              <a href="/" className="text-white-50 small text-decoration-none">
                <i className="fa-solid fa-arrow-left me-1"></i>{t('joinGroup.backToJareg')}
              </a>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default JoinDataHolderGroup;