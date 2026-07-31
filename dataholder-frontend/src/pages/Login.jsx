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

/*
 * Login posts to the route's server `action` (see app/routes/login.jsx), which
 * exchanges the credentials at the backend and sets the encrypted session cookie.
 * No client-side token handling. Errors come back via useActionData().
 */
const Login = () => {
  const [showPassword, setShowPassword] = useState(false);

  const actionData = useActionData();
  const csrf = useRouteLoaderData('root')?.csrf;
  const navigation = useNavigation();
  const loading = navigation.state !== 'idle';
  const { t } = useT();

  return (
    <div className="d-flex align-items-center justify-content-center min-vh-100 bg-light p-3">
      <div className="card shadow-sm w-100" style={{ maxWidth: '420px' }}>
        <div className="card-body p-4">
          <div className="text-center mb-4">
            <div className="d-inline-flex align-items-center justify-content-center bg-primary text-white rounded mb-3" style={{ width: 64, height: 64 }}>
              <i className="fa-solid fa-scale-balanced fa-2x"></i>
            </div>
            <h1 className="h4 fw-bold mb-1">{t('login.title')}</h1>
            <p className="text-muted mb-0">{t('login.subtitle')}</p>
          </div>

          {actionData?.error && (
            <div className="alert alert-danger d-flex align-items-center" role="alert">
              <i className="fa-solid fa-circle-exclamation me-2"></i>
              <div>{actionData.error}</div>
            </div>
          )}

          <Form method="post">
            <input type="hidden" name="_csrf" value={csrf} />
            <div className="mb-3">
              <label className="form-label" htmlFor="email">{t('login.emailLabel')}</label>
              <div className="input-group">
                <span className="input-group-text"><i className="fa-solid fa-envelope"></i></span>
                <input
                  type="email"
                  className="form-control"
                  id="email"
                  name="email"
                  placeholder={t('login.emailPlaceholder')}
                  autoComplete="email"
                  disabled={loading}
                  autoFocus
                  required
                />
              </div>
            </div>

            <div className="mb-3">
              <label className="form-label" htmlFor="password">{t('login.passwordLabel')}</label>
              <div className="input-group">
                <span className="input-group-text"><i className="fa-solid fa-lock"></i></span>
                <input
                  type={showPassword ? 'text' : 'password'}
                  className="form-control"
                  id="password"
                  name="password"
                  placeholder={t('login.passwordPlaceholder')}
                  autoComplete="current-password"
                  disabled={loading}
                  required
                />
                <button
                  type="button"
                  className="btn btn-outline-secondary"
                  onClick={() => setShowPassword(!showPassword)}
                  tabIndex={-1}
                >
                  <i className={`fa-solid fa-${showPassword ? 'eye-slash' : 'eye'}`}></i>
                </button>
              </div>
              <div className="text-end mt-2">
                <Link to="/forgot-password">{t('login.forgotPassword')}</Link>
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
                  {t('login.signingIn')}
                </>
              ) : (
                t('login.signIn')
              )}
            </button>
          </Form>

          <div className="text-center text-muted small mt-4 pt-3 border-top">
            <p className="font-monospace mb-0">{t('login.credentialsHint')}</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
