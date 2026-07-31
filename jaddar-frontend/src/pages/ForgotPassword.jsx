/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import { Link } from 'react-router';
import api from '../services/api';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const ForgotPassword = () => {
  const { t } = useT();
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (loading) return;

    setLoading(true);
    try {
      await api.post('/api/auth/forgot-password', { email });
    } catch (error) {
      // Intentionally ignore errors to avoid leaking account existence.
      console.error('Forgot password request failed:', error);
    } finally {
      setLoading(false);
      setSubmitted(true);
      toast.success(t('forgotPassword.genericMessage'));
    }
  };

  return (
    <div className="container-fluid min-vh-100 d-flex align-items-center justify-content-center bg-light">
      <div className="row w-100 justify-content-center">
        <div className="col-11 col-sm-8 col-md-6 col-lg-4">
          <div className="card shadow-lg border-0">
            <div className="card-body p-5">
              <div className="text-center mb-4">
                <div
                  className="bg-primary-light d-inline-flex align-items-center justify-content-center overflow-hidden mb-3"
                  style={{
                    width: '80px',
                    height: '80px',
                    backgroundColor: '#f0f6ff'
                  }}
                >
                  <img
                    src="/media/JADDAR.png"
                    alt="JADDAR Logo"
                    style={{
                      width: '100%',
                      height: '100%',
                      objectFit: 'contain'
                    }}
                  />
                </div>
                <h2 className="fw-bold mb-2">{t('forgotPassword.title')}</h2>
                <p className="text-secondary mb-0">
                  {t('forgotPassword.subtitle')}
                </p>
              </div>

              {submitted ? (
                <div className="alert alert-success" role="alert">
                  {t('forgotPassword.genericMessage')}
                </div>
              ) : (
                <form onSubmit={handleSubmit}>
                  <div className="mb-3">
                    <label htmlFor="email" className="form-label">{t('forgotPassword.emailLabel')}</label>
                    <input
                      type="email"
                      id="email"
                      className="form-control"
                      placeholder={t('forgotPassword.emailPlaceholder')}
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      required
                      disabled={loading}
                    />
                  </div>

                  <button
                    type="submit"
                    className="btn btn-primary w-100 py-3 fw-semibold"
                    disabled={loading || !email}
                  >
                    {loading ? (
                      <>
                        <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                        {t('forgotPassword.sending')}
                      </>
                    ) : (
                      <>
                        <i className="bi bi-envelope me-2"></i>
                        {t('forgotPassword.sendResetLink')}
                      </>
                    )}
                  </button>
                </form>
              )}

              <div className="text-center mt-4">
                <Link to="/login" className="text-primary text-decoration-none">
                  <i className="bi bi-arrow-left me-1"></i>
                  {t('forgotPassword.backToLogin')}
                </Link>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ForgotPassword;
