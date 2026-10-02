/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import { Link } from 'react-router';
import { forgotPassword } from '../services/api';
import { useT } from '../i18n';

const ForgotPassword = () => {
  const { t } = useT();
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const GENERIC_MESSAGE = t('forgotPassword.genericMessage');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email.trim()) return;
    setLoading(true);
    try {
      await forgotPassword(email.trim());
    } catch {
      // Endpoint always succeeds; suppress errors to avoid leaking account existence.
    } finally {
      setLoading(false);
      setSubmitted(true);
    }
  };

  return (
    <div className="min-vh-100 d-flex align-items-center justify-content-center bg-dark">
      <div className="card shadow" style={{ width: '100%', maxWidth: 420 }}>
        <div className="card-body p-4">
          <div className="text-center mb-4">
            <i className="fa-solid fa-key fa-2x text-primary mb-2"></i>
            <h4 className="fw-bold mb-1">{t('forgotPassword.title')}</h4>
            <p className="text-muted small">{t('forgotPassword.subtitle')}</p>
          </div>

          {submitted ? (
            <>
              <div className="alert alert-success" role="alert">
                <i className="fa-solid fa-circle-check me-2"></i>{GENERIC_MESSAGE}
              </div>
              <Link to="/login" className="btn btn-outline-secondary w-100">
                <i className="fa-solid fa-arrow-left me-2"></i>{t('forgotPassword.backToSignIn')}
              </Link>
            </>
          ) : (
            <form onSubmit={handleSubmit}>
              <div className="mb-4">
                <label className="form-label">{t('common.email')}</label>
                <div className="input-group">
                  <span className="input-group-text"><i className="fa-solid fa-envelope"></i></span>
                  <input
                    type="email"
                    className="form-control"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder={t('forgotPassword.emailPlaceholder')}
                    autoFocus
                    disabled={loading}
                  />
                </div>
              </div>

              <button
                type="submit"
                className="btn btn-primary w-100 mb-3"
                disabled={loading}
              >
                {loading ? (
                  <><span className="spinner-border spinner-border-sm me-2"></span>{t('forgotPassword.sending')}</>
                ) : (
                  <><i className="fa-solid fa-paper-plane me-2"></i>{t('forgotPassword.sendResetLink')}</>
                )}
              </button>

              <div className="text-center">
                <Link to="/login" className="small text-decoration-none">
                  <i className="fa-solid fa-arrow-left me-1"></i>{t('forgotPassword.backToSignIn')}
                </Link>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};

export default ForgotPassword;
