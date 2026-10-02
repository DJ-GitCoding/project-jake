/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useMemo } from 'react';
import api from '../services/api';
import { useAlert } from '../contexts/AlertContext';
import { useT } from '../i18n';
import CustomParameterFields from './CustomParameterFields';
import {
  RDRS_CATEGORIES,
  RDRS_CATEGORY_OTHER,
  RDRS_PRIORITIES,
  RDRS_PRIORITY_EXPEDITED,
  RDRS_DATA_ELEMENT_GROUPS,
  RDRS_PARTY_REPRESENTATIONS,
  RDRS_PARTY_THIRD_PARTY,
  RDRS_LEGAL_BASES,
  RDRS_LEGAL_BASIS_OTHER,
  RDRS_LIMITS,
  RDRS_FILES,
  isLookupSuccess,
  isReservedIanaId,
  lookupMessageKey,
} from '../constants/rdrs';
import CountrySelect from './CountrySelect';
import RdrsRegistrationModal from './RdrsRegistrationModal';

// The RDRS session is gone and the requestor must sign in to ICANN again.
const needsRdrsSignIn = (err) =>
  err?.response?.data?.code === 'RDRS_SIGN_IN_REQUIRED' || err?.response?.status === 401;

// ICANN refused what was typed, as opposed to the service being unavailable.
const isSignInRejected = (err) =>
  err?.response?.data?.code === 'RDRS_SIGN_IN_REJECTED';

const STEP_LOGIN = 1;
const STEP_FORM = 2;
const STEP_REVIEW = 3;

/** ICANN's response-deadline picker only accepts a future date. */
const tomorrow = () => {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return d.toISOString().slice(0, 10);
};

const emptyForm = () => ({
  name: '', email: '', phone: '',
  address1: '', address2: '', city: '', state: '', noState: false,
  zip: '', noZip: false, countryCode: '',
  organization: '',
  category: '', categoryOtherDescription: '', requestedConfidentiality: false,
  domainSubject: '',
  requestPriority: 'Standard Request', expeditedPriorityReason: '',
  requestedDataElements: [],
  processedCountries: [],
  issueDescription: '',
  lawEnforcementRequestForData: null, lawEnforcementRequestDeadLine: '',
  assertingLegalBasis: false, legalBasisReason: '', legalBasisOtherInformation: '',
  partyRepresentation: RDRS_PARTY_REPRESENTATIONS[0].label,
  agreeDataIsCorrect: false, agreeDataProcessTransferCompliance: false,
});

/**
 * ICANN RDRS request flow: sign in to ICANN, look the domain up, fill the form, send.
 *
 * The requestor signs in with their own ICANN credentials. Those credentials go
 * straight to the backend and are never stored — only a short-lived RDRS session is
 * held server-side, and it is never exposed to this component.
 *
 * The domain lookup is not optional: it is the only source of the registrar, the IANA
 * id and the lookup record id, and its result decides whether ICANN will take the
 * request at all.
 */
const RdrsRequest = ({ requestType }) => {
  const { t } = useT();
  const { showError, showSuccess } = useAlert();

  const [step, setStep] = useState(STEP_LOGIN);
  const [busy, setBusy] = useState(false);
  const [session, setSession] = useState(null);

  // Login / MFA
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [challenge, setChallenge] = useState(null); // { challengeId, prompt }
  const [mfaCode, setMfaCode] = useState('');

  // Request
  const [form, setForm] = useState(emptyForm);
  const [lookup, setLookup] = useState(null);
  const [lookupBusy, setLookupBusy] = useState(false);
  const [files, setFiles] = useState({ law_enforcement: null, power_of_attorney: null, additional: [] });
  const [customValues, setCustomValues] = useState({});
  const [customFiles, setCustomFiles] = useState({});
  const [result, setResult] = useState(null);
  const [fromProfile, setFromProfile] = useState({ name: false, email: false });
  const [mailbox, setMailbox] = useState(null);
  const [mailboxLoading, setMailboxLoading] = useState(true);

  const defaults = requestType?.rdrsDefaults || {};

  /*
   * The ICANN account is held under the address Jaddar assigned and the requestor
   * registered with ICANN, so it is shown rather than asked for. Signing in under any
   * other address would land the correspondence in a mailbox Jaddar cannot read.
   */
  const rdrsAddress = mailbox?.address || '';
  const customParams = useMemo(
    () => [...(requestType?.customParameters || [])].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0)),
    [requestType]
  );

  // Only the data elements the admin permitted; empty means all of them.
  const elementGroups = useMemo(() => {
    const allowed = defaults.allowedDataElements;
    if (!allowed || allowed.length === 0) return RDRS_DATA_ELEMENT_GROUPS;
    return RDRS_DATA_ELEMENT_GROUPS
      .map(g => ({ ...g, elements: g.elements.filter(e => allowed.includes(e)) }))
      .filter(g => g.elements.length > 0);
  }, [defaults.allowedDataElements]);

  const selectableElements = useMemo(
    () => elementGroups.flatMap(g => g.elements),
    [elementGroups]
  );

  const set = (field, value) => setForm(prev => ({ ...prev, [field]: value }));

  // Resume an RDRS session that is still alive from an earlier visit.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const { data } = await api.get('/api/rdrs/mailbox');
        if (!cancelled) setMailbox(data);
      } catch {
        if (!cancelled) setMailbox(null);
      } finally {
        if (!cancelled) setMailboxLoading(false);
      }
      try {
        const { data } = await api.get('/api/rdrs/session');
        if (cancelled) return;
        if (data?.status === 'active') applySession(data);
      } catch {
        // No session is a normal starting state, not an error worth showing.
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const applySession = (data) => {
    setSession(data);
    setChallenge(null);
    const prefill = prefillFromProfile(data.profile);
    setFromProfile({ name: !!prefill.name, email: !!prefill.email });
    setForm(prev => ({
      ...prev,
      ...prefillFromProfile(data.profile),
      category: prev.category || defaults.category || '',
      requestPriority: prev.requestPriority || defaults.requestPriority || 'Standard Request',
      organization: prev.organization || defaults.organization || '',
    }));
    setStep(STEP_FORM);
  };

  /**
   * Map an RDRS profile onto the form. ICANN publishes no schema for this, so read
   * defensively and treat every field as optional — the requestor can edit all of it.
   */
  const prefillFromProfile = (profile) => {
    if (!profile || typeof profile !== 'object') return {};
    const contact = profile.contactInformation || {};
    const address = contact.address || profile.address || {};
    const full = profile.name
      || [profile.firstName, profile.lastName].filter(Boolean).join(' ')
      || [profile.givenName, profile.familyName].filter(Boolean).join(' ');
    return {
      ...(full ? { name: full } : {}),
      ...(profile.email ? { email: profile.email } : {}),
      ...(contact.phone || profile.phone ? { phone: contact.phone || profile.phone } : {}),
      ...(address.address1 ? { address1: address.address1 } : {}),
      ...(address.address2 ? { address2: address.address2 } : {}),
      ...(address.city ? { city: address.city } : {}),
      ...(address.state ? { state: address.state } : {}),
      ...(address.zip ? { zip: address.zip } : {}),
      ...(address.countryCode ? { countryCode: address.countryCode } : {}),
      ...(profile.organization ? { organization: profile.organization } : {}),
    };
  };

  const apiError = (e, fallback) => e?.response?.data?.error || fallback;

  /**
   * Accept an attachment only if it matches ICANN's rules — PDF, 5MB.
   * Rejecting here means a clear message instead of an opaque ICANN failure later.
   */
  const validFile = (file) => {
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      showError(t('rdrs.errors.filePdfOnly', { name: file.name }));
      return false;
    }
    if (file.size > RDRS_FILES.maxSizeMb * 1024 * 1024) {
      showError(t('rdrs.errors.fileTooLarge', { name: file.name, max: RDRS_FILES.maxSizeMb }));
      return false;
    }
    return true;
  };

  const pickFile = (event, slot) => {
    const file = event.target.files?.[0] || null;
    if (file && !validFile(file)) {
      event.target.value = '';
      setFiles(f => ({ ...f, [slot]: null }));
      return;
    }
    setFiles(f => ({ ...f, [slot]: file }));
  };

  // ─── Step 1: sign in ──────────────────────────────────────────────

  const handleLogin = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      const { data } = await api.post('/api/rdrs/session', { email: rdrsAddress, password });
      if (data.status === 'mfa_required') {
        setChallenge({ challengeId: data.challengeId, prompt: data.prompt });
        setMfaCode('');
      } else {
        setPassword('');
        applySession(data);
      }
    } catch (err) {
      if (isSignInRejected(err)) {
        setPassword('');
        setShowPassword(false);
      }
      showError(apiError(err, t('rdrs.errors.loginFailed')));
    } finally {
      setBusy(false);
    }
  };

  const handleMfa = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      const { data } = await api.post('/api/rdrs/session/mfa', {
        challengeId: challenge.challengeId,
        code: mfaCode,
      });
      if (data.status === 'mfa_required') {
        setChallenge({ challengeId: data.challengeId, prompt: data.prompt });
        showError(t('rdrs.errors.mfaRetry'));
      } else {
        setPassword('');
        applySession(data);
      }
    } catch (err) {
      showError(apiError(err, t('rdrs.errors.mfaFailed')));
    } finally {
      setBusy(false);
    }
  };

  const handleSignOut = async () => {
    try {
      await api.delete('/api/rdrs/session', {
        params: challenge ? { challengeId: challenge.challengeId } : undefined,
      });
    } catch {
      // Signing out locally is what matters; the server sweeps its own state.
    }
    setSession(null);
    setChallenge(null);
    setForm(emptyForm());
    setLookup(null);
    setResult(null);
    setStep(STEP_LOGIN);
  };

  // ─── Step 2: the request ──────────────────────────────────────────

  const handleLookup = async () => {
    const domain = form.domainSubject.trim().toLowerCase();
    if (!domain) {
      showError(t('rdrs.errors.domainRequired'));
      return;
    }
    setLookupBusy(true);
    setLookup(null);
    try {
      const { data } = await api.get(`/api/rdrs/lookup/${encodeURIComponent(domain)}`);
      setLookup(data);
      if (!isLookupSuccess(data.result)) {
        showError(t(`rdrs.lookup.${lookupMessageKey(data.result)}`, { domain, result: data.result }));
      } else if (isReservedIanaId(data.ianaId)) {
        showError(t('rdrs.lookup.RESERVED_IANA', { domain }));
      }
    } catch (err) {
      if (needsRdrsSignIn(err)) setStep(STEP_LOGIN);
      showError(apiError(err, t('rdrs.errors.lookupFailed')));
    } finally {
      setLookupBusy(false);
    }
  };

  const toggleElement = (label) => {
    setForm(prev => ({
      ...prev,
      requestedDataElements: prev.requestedDataElements.includes(label)
        ? prev.requestedDataElements.filter(l => l !== label)
        : [...prev.requestedDataElements, label],
    }));
  };

  const isOtherCategory = form.category === RDRS_CATEGORY_OTHER;
  const isExpedited = form.requestPriority === RDRS_PRIORITY_EXPEDITED;
  const isThirdParty = form.partyRepresentation === RDRS_PARTY_THIRD_PARTY;
  const isOtherLegalBasis = form.legalBasisReason === RDRS_LEGAL_BASES.find(b => b.id === RDRS_LEGAL_BASIS_OTHER)?.label;
  // ICANN returns "success" in lowercase, so this must not be a strict === 'SUCCESS'.
  const lookupOk = isLookupSuccess(lookup?.result) && !isReservedIanaId(lookup?.ianaId);

  /** Mirrors the server-side rules so problems surface before a round trip. */
  const validate = () => {
    if (!lookupOk) return t('rdrs.errors.lookupRequired');
    if (!form.name.trim()) return t('rdrs.errors.nameRequired');
    if (!form.email.trim()) return t('rdrs.errors.emailRequired');
    if (!form.category) return t('rdrs.errors.categoryRequired');
    if (isOtherCategory) {
      const len = form.categoryOtherDescription.trim().length;
      const { min, max } = RDRS_LIMITS.otherCategory;
      if (len < min || len > max) return t('rdrs.errors.otherCategoryLength', { min, max });
    }
    if (isExpedited) {
      const len = form.expeditedPriorityReason.trim().length;
      const { min, max } = RDRS_LIMITS.expeditedReason;
      if (len < min || len > max) return t('rdrs.errors.expeditedLength', { min, max });
    }
    if (form.requestedDataElements.length === 0) return t('rdrs.errors.elementsRequired');
    if (form.processedCountries.length === 0) return t('rdrs.errors.countriesRequired');
    {
      const len = form.issueDescription.trim().length;
      const { min, max } = RDRS_LIMITS.issueDescription;
      if (len < min || len > max) return t('rdrs.errors.descriptionLength', { min, max });
    }
    // ICANN's API only accepts ASCII here.
    if (!/^[\x20-\x7E\r\n]*$/.test(form.issueDescription)) return t('rdrs.errors.descriptionAscii');
    if (form.lawEnforcementRequestForData === null) return t('rdrs.errors.lawEnforcementRequired');
    if (form.assertingLegalBasis) {
      if (!form.legalBasisReason) return t('rdrs.errors.legalBasisRequired');
      if (isOtherLegalBasis) {
        const len = form.legalBasisOtherInformation.trim().length;
        const { min, max } = RDRS_LIMITS.otherLegalBasis;
        if (len < min || len > max) return t('rdrs.errors.otherLegalBasisLength', { min, max });
      }
    }
    if (isThirdParty && !files.power_of_attorney) return t('rdrs.errors.powerOfAttorneyRequired');
    if (files.additional.length > RDRS_LIMITS.maxAdditionalAttachments) {
      return t('rdrs.errors.tooManyAttachments', { max: RDRS_LIMITS.maxAdditionalAttachments });
    }
    for (const param of customParams) {
      if (param.required && !customValues[param.name]) {
        return t('rdrs.errors.customParamRequired', { name: param.name });
      }
    }
    return null;
  };

  const goToReview = () => {
    const problem = validate();
    if (problem) { showError(problem); return; }
    setStep(STEP_REVIEW);
  };

  // ─── Step 3: send ─────────────────────────────────────────────────

  const buildCommand = () => ({
    user: {
      name: form.name.trim(),
      email: form.email.trim(),
      contactInformation: {
        phone: form.phone.trim(),
        address: {
          address1: form.address1.trim(),
          address2: form.address2.trim(),
          city: form.city.trim(),
          state: form.noState ? '' : form.state.trim(),
          noState: form.noState,
          zip: form.noZip ? '' : form.zip.trim(),
          noZip: form.noZip,
          countryCode: form.countryCode.trim().toUpperCase(),
        },
      },
    },
    organization: form.organization.trim(),
    category: form.category,
    categoryOtherDescription: isOtherCategory ? form.categoryOtherDescription.trim() : null,
    requestedConfidentiality: form.requestedConfidentiality,
    domainSubject: form.domainSubject.trim().toLowerCase(),
    requestPriority: form.requestPriority,
    expeditedPriorityReason: isExpedited ? form.expeditedPriorityReason.trim() : '',
    requestedDataElements: form.requestedDataElements,
    processedCountries: form.processedCountries,
    issueDescription: form.issueDescription.trim(),
    lawEnforcementRequestForData: form.lawEnforcementRequestForData === true,
    lawEnforcementRequestDeadLine: form.lawEnforcementRequestForData && form.lawEnforcementRequestDeadLine
      ? new Date(form.lawEnforcementRequestDeadLine).toISOString()
      : null,
    assertingLegalBasis: form.assertingLegalBasis,
    legalBasisReason: form.assertingLegalBasis ? form.legalBasisReason : null,
    legalBasisOtherInformation: form.assertingLegalBasis && isOtherLegalBasis
      ? form.legalBasisOtherInformation.trim()
      : null,
    partyRepresentation: form.partyRepresentation,
    agreeDataIsCorrect: form.agreeDataIsCorrect,
    agreeDataProcessTransferCompliance: form.agreeDataProcessTransferCompliance,
  });

  const handleSubmit = async () => {
    if (!form.agreeDataIsCorrect || !form.agreeDataProcessTransferCompliance) {
      showError(t('rdrs.errors.acknowledgementsRequired'));
      return;
    }
    setBusy(true);
    try {
      const payload = new FormData();
      payload.append('command', JSON.stringify(buildCommand()));
      if (files.law_enforcement) payload.append('law_enforcement', files.law_enforcement);
      if (files.power_of_attorney) payload.append('power_of_attorney', files.power_of_attorney);
      files.additional.forEach(f => payload.append('additional_attachments', f));
      // Admin-defined extras ride along as additional attachments; RDRS has no
      // concept of custom fields of its own.
      Object.values(customFiles).forEach(f => payload.append('additional_attachments', f));

      const { data } = await api.post('/api/rdrs/submit', payload, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setResult(data);
      showSuccess(t('rdrs.submittedTitle'));
    } catch (err) {
      if (needsRdrsSignIn(err)) setStep(STEP_LOGIN);
      showError(apiError(err, t('rdrs.errors.submitFailed')));
    } finally {
      setBusy(false);
    }
  };

  const startAnother = () => {
    setForm(prev => ({ ...emptyForm(), ...prefillFromProfile(session?.profile), name: prev.name, email: prev.email }));
    setLookup(null);
    setFiles({ law_enforcement: null, power_of_attorney: null, additional: [] });
    setCustomValues({});
    setCustomFiles({});
    setResult(null);
    setStep(STEP_FORM);
  };

  // ─── Render ───────────────────────────────────────────────────────

  const Stepper = () => (
    <div className="d-flex align-items-center gap-2 mb-3 small">
      {[
        { n: STEP_LOGIN, label: t('rdrs.steps.signIn') },
        { n: STEP_FORM, label: t('rdrs.steps.request') },
        { n: STEP_REVIEW, label: t('rdrs.steps.review') },
      ].map((s, i) => (
        <React.Fragment key={s.n}>
          {i > 0 && <span className="text-muted">→</span>}
          <span className={`badge ${step === s.n ? 'bg-primary' : step > s.n ? 'bg-success' : 'bg-secondary'}`}>
            {step > s.n ? <i className="bi bi-check-lg me-1"></i> : `${s.n}. `}{s.label}
          </span>
        </React.Fragment>
      ))}
      {session && (
        <button type="button" className="btn btn-link btn-sm ms-auto p-0" onClick={handleSignOut}>
          {t('rdrs.signOut')}
        </button>
      )}
    </div>
  );

  if (mailboxLoading) {
    return (
      <div className="text-center text-muted py-4 small">
        <span className="spinner-border spinner-border-sm me-2"></span>{t('rdrs.checkingMailbox')}
      </div>
    );
  }

  if (!mailbox?.registered) {
    return (
      <RdrsRegistrationModal
        mailbox={mailbox}
        onRegistered={(updated) => setMailbox(m => ({ ...m, ...updated, registered: true }))}
      />
    );
  }

  return (
    <div>
      <Stepper />

      {step === STEP_LOGIN && (
        <div className="card border">
          <div className="card-header bg-light py-2 px-3">
            <span className="fw-semibold small"><i className="bi bi-shield-lock me-1"></i>{t('rdrs.login.title')}</span>
          </div>
          <div className="card-body">
            <p className="text-muted small">{t('rdrs.login.intro')}</p>

            {!challenge ? (
              <form onSubmit={handleLogin}>
                <div className="row g-2">
                  <div className="col-md-6">
                    <label className="form-label small mb-0" htmlFor="rdrs-email">{t('rdrs.login.email')}</label>
                    <input id="rdrs-email" type="email" className="form-control form-control-sm" autoComplete="username"
                      value={rdrsAddress} readOnly tabIndex={-1} />
                    <div className="form-text" style={{ fontSize: '10px' }}>{t('rdrs.login.assignedAddress')}</div>
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small mb-0" htmlFor="rdrs-password">{t('rdrs.login.password')}</label>
                    <div className="input-group input-group-sm">
                      <input id="rdrs-password" type={showPassword ? 'text' : 'password'}
                        className="form-control form-control-sm" autoComplete="current-password"
                        value={password} onChange={e => setPassword(e.target.value)}
                        disabled={busy} required autoFocus />
                      <button type="button" className="btn btn-outline-secondary"
                        onClick={() => setShowPassword(v => !v)} disabled={busy}
                        aria-pressed={showPassword}
                        aria-label={showPassword ? t('rdrs.login.hidePassword') : t('rdrs.login.showPassword')}
                        title={showPassword ? t('rdrs.login.hidePassword') : t('rdrs.login.showPassword')}>
                        <i className={`bi ${showPassword ? 'bi-eye-slash' : 'bi-eye'}`}></i>
                      </button>
                    </div>
                  </div>
                </div>
                <button type="submit" className="btn btn-primary btn-sm mt-3" disabled={busy}>
                  {busy
                    ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('rdrs.login.signingIn')}</>
                    : <><i className="bi bi-box-arrow-in-right me-1"></i>{t('rdrs.login.signIn')}</>}
                </button>
                <div className="form-text mt-2" style={{ fontSize: '11px' }}>{t('rdrs.login.privacyNote')}</div>
              </form>
            ) : (
              <form onSubmit={handleMfa}>
                <div className="alert alert-info py-2 px-3 small mb-2">
                  <i className="bi bi-phone me-1"></i>{challenge.prompt || t('rdrs.login.mfaPrompt')}
                </div>
                <label className="form-label small mb-0" htmlFor="rdrs-mfa">{t('rdrs.login.mfaCode')}</label>
                <input id="rdrs-mfa" type="text" inputMode="numeric" autoComplete="one-time-code"
                  className="form-control form-control-sm" style={{ maxWidth: 220 }}
                  value={mfaCode} onChange={e => setMfaCode(e.target.value)} disabled={busy} required />
                <div className="d-flex gap-2 mt-3">
                  <button type="submit" className="btn btn-primary btn-sm" disabled={busy}>
                    {busy
                      ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('rdrs.login.verifying')}</>
                      : t('rdrs.login.verify')}
                  </button>
                  <button type="button" className="btn btn-outline-secondary btn-sm" onClick={handleSignOut} disabled={busy}>
                    {t('rdrs.login.cancel')}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}

      {step === STEP_FORM && (
        <div>
          {/* Domain + lookup */}
          <div className="card border mb-2">
            <div className="card-header bg-light py-1 px-3">
              <span className="fw-semibold small"><i className="bi bi-globe me-1"></i>{t('rdrs.form.domainSection')}</span>
            </div>
            <div className="card-body py-2 px-3">
              <div className="row g-2 align-items-end">
                <div className="col-md-6">
                  <label className="form-label small mb-0" htmlFor="rdrs-domain">{t('rdrs.form.domain')} <span className="text-danger">*</span></label>
                  <input id="rdrs-domain" type="text" className="form-control form-control-sm"
                    value={form.domainSubject}
                    onChange={e => { set('domainSubject', e.target.value); setLookup(null); }}
                    placeholder="example.com" disabled={busy} />
                </div>
                <div className="col-md-3">
                  <button type="button" className="btn btn-outline-primary btn-sm w-100"
                    onClick={handleLookup} disabled={lookupBusy || busy || !form.domainSubject.trim()}>
                    {lookupBusy
                      ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('rdrs.form.lookingUp')}</>
                      : <><i className="bi bi-search me-1"></i>{t('rdrs.form.lookUp')}</>}
                  </button>
                </div>
              </div>
              {lookup && (
                <div className={`alert py-2 px-3 mt-2 mb-0 small ${lookupOk ? 'alert-success' : 'alert-warning'}`}>
                  {lookupOk ? (
                    <>
                      <i className="bi bi-check-circle me-1"></i>
                      {t('rdrs.form.lookupOk', { registrar: lookup.registrar, ianaId: lookup.ianaId })}
                    </>
                  ) : (
                    <>
                      <i className="bi bi-exclamation-triangle me-1"></i>
                      {isLookupSuccess(lookup.result) && isReservedIanaId(lookup.ianaId)
                        ? t('rdrs.lookup.RESERVED_IANA', { domain: form.domainSubject })
                        : t(`rdrs.lookup.${lookupMessageKey(lookup.result)}`, {
                            domain: form.domainSubject, result: lookup.result,
                          })}
                    </>
                  )}
                </div>
              )}
              {!lookup && (
                <div className="form-text mt-1" style={{ fontSize: '11px' }}>{t('rdrs.form.lookupHint')}</div>
              )}
            </div>
          </div>

          {/* Contact */}
          <div className="card border mb-2">
            <div className="card-header bg-light py-1 px-3">
              <span className="fw-semibold small"><i className="bi bi-person me-1"></i>{t('rdrs.form.contactSection')}</span>
            </div>
            <div className="card-body py-2 px-3">
              <div className="row g-2">
                <div className="col-md-4">
                  <label className="form-label small mb-0">{t('rdrs.form.name')} <span className="text-danger">*</span></label>
                  <input className="form-control form-control-sm" value={form.name}
                    onChange={e => set('name', e.target.value)}
                    disabled={busy || fromProfile.name} readOnly={fromProfile.name} />
                  {fromProfile.name && <div className="form-text" style={{ fontSize: '10px' }}>{t('rdrs.form.fromIcannAccount')}</div>}
                </div>
                <div className="col-md-4">
                  <label className="form-label small mb-0">{t('rdrs.form.email')} <span className="text-danger">*</span></label>
                  <input type="email" className="form-control form-control-sm" value={form.email}
                    onChange={e => set('email', e.target.value)}
                    disabled={busy || fromProfile.email} readOnly={fromProfile.email} />
                  {fromProfile.email && <div className="form-text" style={{ fontSize: '10px' }}>{t('rdrs.form.fromIcannAccount')}</div>}
                </div>
                <div className="col-md-4">
                  <label className="form-label small mb-0">{t('rdrs.form.phone')}</label>
                  <input className="form-control form-control-sm" value={form.phone} onChange={e => set('phone', e.target.value)} placeholder="+1 555-0100" disabled={busy} />
                </div>
                <div className="col-md-6">
                  <label className="form-label small mb-0">{t('rdrs.form.address1')}</label>
                  <input className="form-control form-control-sm" value={form.address1} onChange={e => set('address1', e.target.value)} disabled={busy} />
                </div>
                <div className="col-md-6">
                  <label className="form-label small mb-0">{t('rdrs.form.address2')}</label>
                  <input className="form-control form-control-sm" value={form.address2} onChange={e => set('address2', e.target.value)} disabled={busy} />
                </div>
                <div className="col-md-3">
                  <label className="form-label small mb-0">{t('rdrs.form.city')}</label>
                  <input className="form-control form-control-sm" value={form.city} onChange={e => set('city', e.target.value)} disabled={busy} />
                </div>
                <div className="col-md-3">
                  <label className="form-label small mb-0">{t('rdrs.form.state')}</label>
                  <input className="form-control form-control-sm" value={form.state} onChange={e => set('state', e.target.value)} disabled={busy || form.noState} />
                  <div className="form-check mt-1">
                    <input className="form-check-input" type="checkbox" id="rdrs-nostate" checked={form.noState} onChange={e => set('noState', e.target.checked)} disabled={busy} />
                    <label className="form-check-label" htmlFor="rdrs-nostate" style={{ fontSize: '11px' }}>{t('rdrs.form.noState')}</label>
                  </div>
                </div>
                <div className="col-md-3">
                  <label className="form-label small mb-0">{t('rdrs.form.zip')}</label>
                  <input className="form-control form-control-sm" value={form.zip} onChange={e => set('zip', e.target.value)} disabled={busy || form.noZip} />
                  <div className="form-check mt-1">
                    <input className="form-check-input" type="checkbox" id="rdrs-nozip" checked={form.noZip} onChange={e => set('noZip', e.target.checked)} disabled={busy} />
                    <label className="form-check-label" htmlFor="rdrs-nozip" style={{ fontSize: '11px' }}>{t('rdrs.form.noZip')}</label>
                  </div>
                </div>
                <div className="col-md-3">
                  <label className="form-label small mb-0" htmlFor="rdrs-country-code">{t('rdrs.form.countryCode')}</label>
                  <CountrySelect
                    id="rdrs-country-code"
                    mode="code"
                    value={form.countryCode}
                    onChange={(v) => set('countryCode', v)}
                    placeholder={t('rdrs.form.selectCountryCode')}
                    disabled={busy}
                  />
                </div>
                <div className="col-md-6">
                  <label className="form-label small mb-0">{t('rdrs.form.organization')}</label>
                  <input className="form-control form-control-sm" value={form.organization} onChange={e => set('organization', e.target.value)} disabled={busy} />
                </div>
              </div>
            </div>
          </div>

          {/* Request details */}
          <div className="card border mb-2">
            <div className="card-header bg-light py-1 px-3">
              <span className="fw-semibold small"><i className="bi bi-file-earmark-text me-1"></i>{t('rdrs.form.requestSection')}</span>
            </div>
            <div className="card-body py-2 px-3">
              <div className="row g-2">
                <div className="col-md-6">
                  <label className="form-label small mb-0">{t('rdrs.form.category')} <span className="text-danger">*</span></label>
                  <select className="form-select form-select-sm" value={form.category} onChange={e => set('category', e.target.value)} disabled={busy}>
                    <option value="">{t('rdrs.form.selectCategory')}</option>
                    {RDRS_CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <div className="col-md-6">
                  <label className="form-label small mb-0">{t('rdrs.form.priority')}</label>
                  <select className="form-select form-select-sm" value={form.requestPriority} onChange={e => set('requestPriority', e.target.value)} disabled={busy}>
                    {RDRS_PRIORITIES.map(p => <option key={p} value={p}>{p}</option>)}
                  </select>
                </div>
                {isOtherCategory && (
                  <div className="col-12">
                    <label className="form-label small mb-0">{t('rdrs.form.otherCategory')} <span className="text-danger">*</span></label>
                    <input className="form-control form-control-sm" value={form.categoryOtherDescription}
                      onChange={e => set('categoryOtherDescription', e.target.value)}
                      minLength={RDRS_LIMITS.otherCategory.min} maxLength={RDRS_LIMITS.otherCategory.max} disabled={busy} />
                    <div className="form-text" style={{ fontSize: '10px' }}>
                      {t('rdrs.form.charCount', { count: form.categoryOtherDescription.trim().length, min: RDRS_LIMITS.otherCategory.min, max: RDRS_LIMITS.otherCategory.max })}
                    </div>
                  </div>
                )}
                {isExpedited && (
                  <div className="col-12">
                    <label className="form-label small mb-0">{t('rdrs.form.expeditedReason')} <span className="text-danger">*</span></label>
                    <textarea className="form-control form-control-sm" rows={3} value={form.expeditedPriorityReason}
                      onChange={e => set('expeditedPriorityReason', e.target.value)}
                      maxLength={RDRS_LIMITS.expeditedReason.max} disabled={busy} />
                    <div className="form-text" style={{ fontSize: '10px' }}>
                      {t('rdrs.form.charCount', { count: form.expeditedPriorityReason.trim().length, min: RDRS_LIMITS.expeditedReason.min, max: RDRS_LIMITS.expeditedReason.max })}
                      {' — '}{t('rdrs.form.expeditedDisclaimer')}
                    </div>
                  </div>
                )}
                <div className="col-12">
                  <div className="form-check">
                    <input className="form-check-input" type="checkbox" id="rdrs-conf"
                      checked={form.requestedConfidentiality} onChange={e => set('requestedConfidentiality', e.target.checked)} disabled={busy} />
                    <label className="form-check-label small" htmlFor="rdrs-conf">{t('rdrs.form.confidentiality')}</label>
                  </div>
                </div>
              </div>

              {/* Data elements */}
              <div className="mt-3">
                <div className="d-flex align-items-center mb-1">
                  <label className="form-label small mb-0">
                    {t('rdrs.form.dataElements')} <span className="text-danger">*</span>
                    <span className="badge bg-secondary ms-1" style={{ fontSize: '10px' }}>{form.requestedDataElements.length}</span>
                  </label>
                  <button type="button" className="btn btn-outline-secondary btn-sm ms-auto py-0"
                    style={{ fontSize: 11 }} disabled={busy}
                    onClick={() => set('requestedDataElements',
                      form.requestedDataElements.length === selectableElements.length ? [] : selectableElements)}>
                    {form.requestedDataElements.length === selectableElements.length
                      ? t('rdrs.form.clearAll') : t('rdrs.form.selectAll')}
                  </button>
                </div>
                <div className="row g-2">
                  {elementGroups.map(group => (
                    <div className="col-md-4" key={group.groupName}>
                      <div className="small text-muted mb-1">{group.groupName}</div>
                      {group.elements.map(label => (
                        <div className="form-check" key={label}>
                          <input className="form-check-input" type="checkbox" id={`rdrs-de-${label}`}
                            checked={form.requestedDataElements.includes(label)}
                            onChange={() => toggleElement(label)} disabled={busy} />
                          <label className="form-check-label" htmlFor={`rdrs-de-${label}`} style={{ fontSize: '12px' }}>{label}</label>
                        </div>
                      ))}
                    </div>
                  ))}
                </div>
              </div>

              <div className="row g-2 mt-2">
                <div className="col-12">
                  <label className="form-label small mb-0" htmlFor="rdrs-countries">
                    {t('rdrs.form.processedCountries')} <span className="text-danger">*</span>
                  </label>
                  <CountrySelect
                    id="rdrs-countries"
                    mode="name"
                    multiple
                    value={form.processedCountries}
                    onChange={(v) => set('processedCountries', v)}
                    placeholder={t('rdrs.form.selectCountries')}
                    disabled={busy}
                  />
                  <div className="form-text" style={{ fontSize: '10px' }}>{t('rdrs.form.processedCountriesHint')}</div>
                </div>
              </div>

              <div className="mt-2">
                <label className="form-label small mb-0">{t('rdrs.form.issueDescription')} <span className="text-danger">*</span></label>
                <textarea className="form-control form-control-sm" rows={4} value={form.issueDescription}
                  onChange={e => set('issueDescription', e.target.value)}
                  maxLength={RDRS_LIMITS.issueDescription.max} disabled={busy} />
                <div className="form-text" style={{ fontSize: '10px' }}>
                  {t('rdrs.form.charCount', {
                    count: form.issueDescription.trim().length,
                    min: RDRS_LIMITS.issueDescription.min,
                    max: RDRS_LIMITS.issueDescription.max,
                  })}
                  {' — '}{t('rdrs.form.asciiOnly')}
                </div>
              </div>
            </div>
          </div>

          {/* Law enforcement */}
          <div className="card border mb-2">
            <div className="card-header bg-light py-1 px-3">
              <span className="fw-semibold small"><i className="bi bi-shield me-1"></i>{t('rdrs.form.lawEnforcementSection')}</span>
            </div>
            <div className="card-body py-2 px-3">
              <div className="small mb-2">{t('rdrs.form.lawEnforcementQuestion')} <span className="text-danger">*</span></div>
              {[true, false].map(answer => (
                <div className="form-check" key={String(answer)}>
                  <input className="form-check-input" type="radio" name="rdrs-le"
                    id={`rdrs-le-${answer}`}
                    checked={form.lawEnforcementRequestForData === answer}
                    onChange={() => set('lawEnforcementRequestForData', answer)} disabled={busy} />
                  <label className="form-check-label small" htmlFor={`rdrs-le-${answer}`}>
                    {answer ? t('common.yes') : t('common.no')}
                  </label>
                </div>
              ))}
              {form.lawEnforcementRequestForData === true && (
                <div className="row g-2 mt-1 ms-1">
                  <div className="col-md-4">
                    <label className="form-label small mb-0">{t('rdrs.form.responseDeadline')}</label>
                    <input type="date" className="form-control form-control-sm" value={form.lawEnforcementRequestDeadLine}
                      min={tomorrow()} onChange={e => set('lawEnforcementRequestDeadLine', e.target.value)} disabled={busy} />
                  </div>
                  <div className="col-md-8">
                    <label className="form-label small mb-0">{t('rdrs.form.lawEnforcementCredential')}</label>
                    <input type="file" className="form-control form-control-sm" accept={RDRS_FILES.accept}
                      onChange={e => pickFile(e, 'law_enforcement')} disabled={busy} />
                    <div className="form-text" style={{ fontSize: '10px' }}>{t('rdrs.form.fileHint', { max: RDRS_FILES.maxSizeMb })}</div>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Legal basis & representation */}
          <div className="card border mb-2">
            <div className="card-header bg-light py-1 px-3">
              <span className="fw-semibold small"><i className="bi bi-journal-text me-1"></i>{t('rdrs.form.legalSection')}</span>
            </div>
            <div className="card-body py-2 px-3">
              <div className="form-check mb-2">
                <input className="form-check-input" type="checkbox" id="rdrs-legal"
                  checked={form.assertingLegalBasis} onChange={e => set('assertingLegalBasis', e.target.checked)} disabled={busy} />
                <label className="form-check-label small" htmlFor="rdrs-legal">{t('rdrs.form.assertLegalBasis')}</label>
              </div>
              {form.assertingLegalBasis && (
                <div className="row g-2 mb-2">
                  <div className="col-12">
                    <label className="form-label small mb-0">{t('rdrs.form.legalBasis')} <span className="text-danger">*</span></label>
                    <select className="form-select form-select-sm" value={form.legalBasisReason}
                      onChange={e => set('legalBasisReason', e.target.value)} disabled={busy}>
                      <option value="">{t('rdrs.form.selectLegalBasis')}</option>
                      {RDRS_LEGAL_BASES.map(b => <option key={b.id} value={b.label}>{b.label}</option>)}
                    </select>
                  </div>
                  {isOtherLegalBasis && (
                    <div className="col-12">
                      <label className="form-label small mb-0">{t('rdrs.form.otherLegalBasis')} <span className="text-danger">*</span></label>
                      <textarea className="form-control form-control-sm" rows={3} value={form.legalBasisOtherInformation}
                        onChange={e => set('legalBasisOtherInformation', e.target.value)}
                        maxLength={RDRS_LIMITS.otherLegalBasis.max} disabled={busy} />
                      <div className="form-text" style={{ fontSize: '10px' }}>
                        {t('rdrs.form.charCount', { count: form.legalBasisOtherInformation.trim().length, min: RDRS_LIMITS.otherLegalBasis.min, max: RDRS_LIMITS.otherLegalBasis.max })}
                      </div>
                    </div>
                  )}
                </div>
              )}

              <label className="form-label small mb-1">{t('rdrs.form.partyRepresentation')} <span className="text-danger">*</span></label>
              {RDRS_PARTY_REPRESENTATIONS.map((p, i) => (
                <div className="form-check" key={p.id}>
                  <input className="form-check-input" type="radio" name="rdrs-party" id={`rdrs-party-${i}`}
                    checked={form.partyRepresentation === p.label}
                    onChange={() => set('partyRepresentation', p.label)} disabled={busy} />
                  <label className="form-check-label" htmlFor={`rdrs-party-${i}`} style={{ fontSize: '12px' }}>{p.label}</label>
                </div>
              ))}
              {isThirdParty && (
                <div className="mt-2">
                  <label className="form-label small mb-0">{t('rdrs.form.powerOfAttorney')} <span className="text-danger">*</span></label>
                  <input type="file" className="form-control form-control-sm" accept={RDRS_FILES.accept}
                    onChange={e => pickFile(e, 'power_of_attorney')} disabled={busy} />
                  <div className="form-text" style={{ fontSize: '10px' }}>
                    {t('rdrs.form.powerOfAttorneyHint')} {t('rdrs.form.fileHint', { max: RDRS_FILES.maxSizeMb })}
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Attachments */}
          <div className="card border mb-2">
            <div className="card-header bg-light py-1 px-3">
              <span className="fw-semibold small"><i className="bi bi-paperclip me-1"></i>{t('rdrs.form.attachmentsSection')}</span>
            </div>
            <div className="card-body py-2 px-3">
              <input type="file" multiple className="form-control form-control-sm" accept={RDRS_FILES.accept}
                onChange={e => {
                  const chosen = Array.from(e.target.files || []);
                  if (chosen.length > RDRS_LIMITS.maxAdditionalAttachments) {
                    showError(t('rdrs.errors.tooManyAttachments', { max: RDRS_LIMITS.maxAdditionalAttachments }));
                    e.target.value = '';
                    return;
                  }
                  if (!chosen.every(validFile)) { e.target.value = ''; return; }
                  setFiles(f => ({ ...f, additional: chosen }));
                }} disabled={busy} />
              <div className="form-text" style={{ fontSize: '10px' }}>
                {t('rdrs.form.attachmentsHint', { max: RDRS_LIMITS.maxAdditionalAttachments })}
                {' '}{t('rdrs.form.fileHint', { max: RDRS_FILES.maxSizeMb })}
              </div>
            </div>
          </div>

          {/* Admin-defined extras */}
          <CustomParameterFields
            params={customParams}
            values={customValues}
            onChange={(name, value) => setCustomValues(prev => ({ ...prev, [name]: value }))}
            onFileChange={(name, file) => setCustomFiles(prev => {
              if (!file) { const next = { ...prev }; delete next[name]; return next; }
              return { ...prev, [name]: file };
            })}
            disabled={busy}
            onError={showError}
          />

          <div className="d-flex gap-2">
            <button type="button" className="btn btn-primary btn-sm" onClick={goToReview} disabled={busy || !lookupOk}>
              {t('rdrs.form.review')}<i className="bi bi-arrow-right ms-1"></i>
            </button>
            {!lookupOk && <span className="text-muted small align-self-center">{t('rdrs.form.lookupFirst')}</span>}
          </div>
        </div>
      )}

      {step === STEP_REVIEW && (
        <div>
          {result ? (
            <div className="card border-success">
              <div className="card-body">
                <h6 className="text-success"><i className="bi bi-check-circle me-1"></i>{t('rdrs.submittedTitle')}</h6>
                <p className="small mb-2">{result.message}</p>
                <dl className="row small mb-3">
                  <dt className="col-sm-3">{t('rdrs.review.domain')}</dt><dd className="col-sm-9">{form.domainSubject}</dd>
                  <dt className="col-sm-3">{t('rdrs.review.registrar')}</dt><dd className="col-sm-9">{result.registrarName} (IANA {result.ianaId})</dd>
                </dl>
                <button type="button" className="btn btn-outline-primary btn-sm" onClick={startAnother}>
                  <i className="bi bi-plus-lg me-1"></i>{t('rdrs.review.another')}
                </button>
              </div>
            </div>
          ) : (
            <div className="card border">
              <div className="card-header bg-light py-2 px-3">
                <span className="fw-semibold small"><i className="bi bi-eye me-1"></i>{t('rdrs.review.title')}</span>
              </div>
              <div className="card-body">
                <dl className="row small">
                  <dt className="col-sm-3">{t('rdrs.review.domain')}</dt><dd className="col-sm-9">{form.domainSubject}</dd>
                  <dt className="col-sm-3">{t('rdrs.review.registrar')}</dt><dd className="col-sm-9">{lookup?.registrar} (IANA {lookup?.ianaId})</dd>
                  <dt className="col-sm-3">{t('rdrs.review.requestor')}</dt><dd className="col-sm-9">{form.name} &lt;{form.email}&gt;</dd>
                  <dt className="col-sm-3">{t('rdrs.review.category')}</dt><dd className="col-sm-9">{form.category}{isOtherCategory ? ` — ${form.categoryOtherDescription}` : ''}</dd>
                  <dt className="col-sm-3">{t('rdrs.review.priority')}</dt><dd className="col-sm-9">{form.requestPriority}</dd>
                  <dt className="col-sm-3">{t('rdrs.review.elements')}</dt><dd className="col-sm-9">{form.requestedDataElements.join(', ')}</dd>
                  <dt className="col-sm-3">{t('rdrs.review.countries')}</dt><dd className="col-sm-9">{form.processedCountries.join(', ')}</dd>
                  <dt className="col-sm-3">{t('rdrs.review.description')}</dt><dd className="col-sm-9" style={{ whiteSpace: 'pre-wrap' }}>{form.issueDescription}</dd>
                  <dt className="col-sm-3">{t('rdrs.review.representation')}</dt><dd className="col-sm-9">{form.partyRepresentation}</dd>
                </dl>

                <div className="form-check mb-1">
                  <input className="form-check-input" type="checkbox" id="rdrs-ack1"
                    checked={form.agreeDataIsCorrect} onChange={e => set('agreeDataIsCorrect', e.target.checked)} disabled={busy} />
                  <label className="form-check-label small" htmlFor="rdrs-ack1">{t('rdrs.review.ack1')}</label>
                </div>
                <div className="form-check mb-3">
                  <input className="form-check-input" type="checkbox" id="rdrs-ack2"
                    checked={form.agreeDataProcessTransferCompliance} onChange={e => set('agreeDataProcessTransferCompliance', e.target.checked)} disabled={busy} />
                  <label className="form-check-label small" htmlFor="rdrs-ack2">{t('rdrs.review.ack2')}</label>
                </div>

                <div className="d-flex gap-2">
                  <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setStep(STEP_FORM)} disabled={busy}>
                    <i className="bi bi-arrow-left me-1"></i>{t('rdrs.review.back')}
                  </button>
                  <button type="button" className="btn btn-success btn-sm" onClick={handleSubmit}
                    disabled={busy || !form.agreeDataIsCorrect || !form.agreeDataProcessTransferCompliance}>
                    {busy
                      ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('rdrs.review.sending')}</>
                      : <><i className="bi bi-send me-1"></i>{t('rdrs.review.send')}</>}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default RdrsRequest;
