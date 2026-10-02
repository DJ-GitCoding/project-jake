/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useRef } from 'react';
import api from '../services/api';
import { useT } from '../i18n';

const ICANN_REGISTER_URL = 'https://account.icann.org/registeraccount';

const RdrsRegistrationModal = ({ mailbox, onRegistered }) => {
  const { t } = useT();
  const [copyState, setCopyState] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState(null);
  const addressRef = useRef(null);

  const address = mailbox?.address || '';

  // Selects the address in place so the copy is something the user can see and do.
  const selectAddress = () => {
    const el = addressRef.current;
    if (!el) return false;
    el.focus();
    el.setSelectionRange(0, address.length);
    return true;
  };

  const copy = () => {
    if (!selectAddress()) return;
    let ok = false;
    try {
      ok = document.execCommand('copy');
    } catch {
      ok = false;
    }
    setCopyState(ok ? 'copied' : 'selected');
    setTimeout(() => setCopyState(null), 4000);
  };

  // Records the user's confirmation so RDRS requests unlock.
  const confirm = async () => {
    setConfirming(true);
    setError(null);
    try {
      const { data } = await api.post('/api/rdrs/mailbox/registered');
      onRegistered?.(data);
    } catch (e) {
      setError(e?.response?.data?.error || t('rdrsRegistration.confirmFailed'));
    } finally {
      setConfirming(false);
    }
  };

  return (
    <>
      <div className="modal fade show d-block" tabIndex="-1" role="dialog"
        style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
        <div className="modal-dialog modal-dialog-centered modal-lg">
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">
                <i className="bi bi-shield-lock me-2"></i>{t('rdrsRegistration.title')}
              </h5>
            </div>
            <div className="modal-body">
              <p className="mb-3">{t('rdrsRegistration.intro')}</p>

              <label className="form-label small fw-semibold mb-1">{t('rdrsRegistration.yourAddress')}</label>
              <div className="input-group mb-1">
                <input ref={addressRef} type="text" className="form-control font-monospace"
                  value={address} readOnly onClick={selectAddress} onFocus={selectAddress} />
                <button type="button" className="btn btn-outline-secondary" onClick={copy}>
                  <i className={`bi ${copyState === 'copied' ? 'bi-check2' : 'bi-clipboard'} me-1`}></i>
                  {copyState === 'copied' ? t('rdrsRegistration.copied') : t('rdrsRegistration.copy')}
                </button>
              </div>
              <div className="form-text mb-3">
                {copyState === 'selected'
                  ? t('rdrsRegistration.selectedHint')
                  : t('rdrsRegistration.addressHint')}
              </div>

              <ol className="small mb-3">
                <li className="mb-1">{t('rdrsRegistration.step1')}</li>
                <li className="mb-1">{t('rdrsRegistration.step2')}</li>
                <li className="mb-1">{t('rdrsRegistration.step3')}</li>
              </ol>

              <div className="alert alert-info py-2 px-3 small mb-3">
                <i className="bi bi-info-circle me-1"></i>{t('rdrsRegistration.mailNote')}
              </div>

              {error && <div className="alert alert-danger py-2 px-3 small mb-3">{error}</div>}
            </div>
            <div className="modal-footer">
              <a className="btn btn-primary" href={ICANN_REGISTER_URL} target="_blank" rel="noopener noreferrer">
                <i className="bi bi-box-arrow-up-right me-1"></i>{t('rdrsRegistration.registerAtIcann')}
              </a>
              <button type="button" className="btn btn-success" onClick={confirm} disabled={confirming || !address}>
                {confirming
                  ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('rdrsRegistration.confirming')}</>
                  : <><i className="bi bi-check2-circle me-1"></i>{t('rdrsRegistration.confirm')}</>}
              </button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

export default RdrsRegistrationModal;
