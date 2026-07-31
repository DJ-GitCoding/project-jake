/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import { Link } from 'react-router';
import { authApi } from '../services/api';
import { useT } from '../i18n';

const ForgotPassword = () => {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const { t } = useT();

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!email) {
      return;
    }

    setLoading(true);

    try {
      await authApi.forgotPassword(email);
    } catch (err) {
      // Endpoint always succeeds; ignore errors to avoid leaking account existence
    } finally {
      setLoading(false);
      setSubmitted(true);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-header">
          <h3>
            <i className="fas fa-key me-2"></i>
            {t('forgotPassword.title')}
          </h3>
          <p>{t('forgotPassword.subtitle')}</p>
        </div>

        <div className="login-body">
          {submitted ? (
            <>
              <div className="alert alert-success" role="alert">
                <i className="fas fa-check-circle me-2"></i>
                {t('forgotPassword.successMessage')}
              </div>
              <div className="text-center mt-4">
                <Link to="/login">
                  <i className="fas fa-arrow-left me-1"></i>
                  {t('forgotPassword.backToSignIn')}
                </Link>
              </div>
            </>
          ) : (
            <>
              <p className="text-muted mb-4">
                {t('forgotPassword.instructions')}
              </p>
              <form onSubmit={handleSubmit}>
                <div className="mb-4">
                  <label htmlFor="email" className="form-label">
                    {t('forgotPassword.emailLabel')}
                  </label>
                  <div className="input-group">
                    <span className="input-group-text">
                      <i className="fas fa-envelope"></i>
                    </span>
                    <input
                      type="email"
                      className="form-control"
                      id="email"
                      placeholder={t('forgotPassword.emailPlaceholder')}
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      disabled={loading}
                      autoComplete="email"
                      autoFocus
                    />
                  </div>
                </div>

                <button
                  type="submit"
                  className="btn btn-primary w-100 py-2"
                  disabled={loading}
                >
                  {loading ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2"></span>
                      {t('forgotPassword.sending')}
                    </>
                  ) : (
                    <>
                      <i className="fas fa-paper-plane me-2"></i>
                      {t('forgotPassword.sendResetLink')}
                    </>
                  )}
                </button>
              </form>

              <div className="text-center mt-4">
                <Link to="/login">
                  <i className="fas fa-arrow-left me-1"></i>
                  {t('forgotPassword.backToSignIn')}
                </Link>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default ForgotPassword;
