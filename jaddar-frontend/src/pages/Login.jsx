/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import { Link, Form, useActionData, useNavigation, useRouteLoaderData } from 'react-router';
import { useT } from '../i18n';
import { useConfig } from '../contexts/ConfigContext';

const Login = () => {
  const { t } = useT();
  const { appVersion } = useConfig();

  const actionData = useActionData();
  const csrf = useRouteLoaderData('root')?.csrf;
  const navigation = useNavigation();
  const submitting = navigation.state !== 'idle';
  const [showPassword, setShowPassword] = useState(false);

  const error = actionData?.error;

  return (
    <div className="container-fluid min-vh-100 d-flex align-items-center justify-content-center bg-light">
      <div className="row w-100 justify-content-center">
        <div className="col-11 col-sm-8 col-md-6 col-lg-4">
          <div className="card shadow-lg border-0">
            <div className="card-body p-5">
              <div className="text-center">
                <div
                  className="bg-primary-light d-inline-flex align-items-center justify-content-center overflow-hidden"
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
                <div className="text-center mb-3">
                  <span
                    className="text-muted small"
                    style={{
                      fontSize: '0.7rem',
                      marginTop: '-4px',
                      opacity: 0.7
                    }}
                  >
                    v{appVersion}
                  </span>
                </div>
                <h2 className="fw-bold mb-2">{t('login.welcome')}</h2>
                <p className="text-secondary mb-4">{t('login.subtitle')}</p>
              </div>

              {error && (
                <div className="alert alert-danger py-2" role="alert">
                  {error}
                </div>
              )}

              <Form method="post" noValidate>
                <input type="hidden" name="_csrf" value={csrf} />
                <div className="mb-3">
                  <label htmlFor="email" className="form-label">{t('login.emailLabel')}</label>
                  <div className="input-group">
                    <span className="input-group-text">
                      <i className="bi bi-envelope"></i>
                    </span>
                    <input
                      type="email"
                      id="email"
                      name="email"
                      className="form-control"
                      placeholder={t('login.emailPlaceholder')}
                      disabled={submitting}
                      autoComplete="username"
                      autoFocus
                      required
                    />
                  </div>
                </div>

                <div className="mb-4">
                  <label htmlFor="password" className="form-label">{t('login.passwordLabel')}</label>
                  <div className="input-group">
                    <span className="input-group-text">
                      <i className="bi bi-lock"></i>
                    </span>
                    <input
                      type={showPassword ? 'text' : 'password'}
                      id="password"
                      name="password"
                      className="form-control"
                      placeholder={t('login.passwordPlaceholder')}
                      disabled={submitting}
                      autoComplete="current-password"
                      required
                    />
                    <button
                      type="button"
                      className="btn btn-outline-secondary"
                      onClick={() => setShowPassword((v) => !v)}
                      tabIndex={-1}
                    >
                      <i className={`bi bi-${showPassword ? 'eye-slash' : 'eye'}`}></i>
                    </button>
                  </div>
                </div>

                <button
                  type="submit"
                  className="btn btn-primary w-100 py-3 fw-semibold"
                  disabled={submitting}
                >
                  {submitting ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" role="status"></span>
                      {t('login.signingIn')}
                    </>
                  ) : (
                    <>
                      <i className="bi bi-box-arrow-in-right me-2"></i>
                      {t('login.signIn')}
                    </>
                  )}
                </button>
              </Form>

              <div className="text-center mt-3">
                <Link to="/forgot-password" className="text-primary text-decoration-none small">
                  {t('login.forgotPassword')}
                </Link>
              </div>
            </div>
          </div>

          <div className="text-center mt-3">
            <small className="text-muted">
              {t('login.needHelp')} <a href="#" className="text-primary text-decoration-none">{t('login.contactSupport')}</a>
            </small>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
