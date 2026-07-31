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
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const ForgotPassword = () => {
  const { t } = useT();
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!email.trim()) {
      toast.error(t('forgotPassword.enterEmail'));
      return;
    }

    setLoading(true);

    try {
      await forgotPassword(email.trim());
    } catch (error) {
      // Endpoint always succeeds; log but still show the generic state.
      console.error('Forgot password error:', error);
    } finally {
      setLoading(false);
      setSubmitted(true);
      toast.success(t('forgotPassword.genericMessage'));
    }
  };

  return (
    <div className="d-flex align-items-center justify-content-center min-vh-100 bg-light p-3">
      <div className="card shadow-sm w-100" style={{ maxWidth: '420px' }}>
        <div className="card-body p-4">
          <div className="text-center mb-4">
            <div className="d-inline-flex align-items-center justify-content-center bg-primary text-white rounded mb-3" style={{ width: 64, height: 64 }}>
              <i className="fa-solid fa-scale-balanced fa-2x"></i>
            </div>
            <h1 className="h4 fw-bold mb-1">{t('forgotPassword.title')}</h1>
            <p className="text-muted mb-0">{t('login.title')}</p>
          </div>

          {submitted ? (
            <div className="d-grid gap-3">
              <p className="text-center text-secondary">{t('forgotPassword.genericMessage')}</p>
              <Link to="/login" className="btn btn-primary w-100">
                {t('forgotPassword.backToSignIn')}
              </Link>
            </div>
          ) : (
            <form onSubmit={handleSubmit}>
              <div className="mb-3">
                <label className="form-label">{t('common.email')}</label>
                <div className="input-group">
                  <span className="input-group-text"><i className="fa-solid fa-envelope"></i></span>
                  <input
                    type="email"
                    className="form-control"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder={t('forgotPassword.emailPlaceholder')}
                    autoComplete="email"
                    autoFocus
                  />
                </div>
              </div>

              <button
                type="submit"
                className="btn btn-primary w-100"
                disabled={loading}
              >
                {loading ? (
                  <>
                    <span className="spinner-border spinner-border-sm me-2"></span>
                    {t('forgotPassword.sending')}
                  </>
                ) : (
                  t('forgotPassword.sendResetLink')
                )}
              </button>
            </form>
          )}

          <div className="text-center text-muted small mt-4 pt-3 border-top">
            <p className="font-monospace mb-0">
              <Link to="/login">
                {t('forgotPassword.backToSignIn')}
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ForgotPassword;
