/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import { Link, Form, useActionData, useLoaderData, useNavigation, useRouteLoaderData } from 'react-router';
import BrandingLogo, { useBrandingLogoStatus } from '../components/BrandingLogo';
import LoginNotices from '../components/LoginNotices';
import { useT } from '../i18n';

const Login = () => {
  const [showPassword, setShowPassword] = useState(false);

  const actionData = useActionData();
  const csrf = useRouteLoaderData('root')?.csrf;
  const appVersion = useRouteLoaderData('root')?.config?.appVersion || 'dev';
  const navigation = useNavigation();
  const loading = navigation.state !== 'idle';
  const logoPresent = useBrandingLogoStatus() === 'present';
  const { t } = useT();
  const expired = useLoaderData()?.expired;

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-header">
          {/* Uploaded logo replaces the default icon; see BrandingLogo. */}
          <BrandingLogo
            alt={t('login.title')}
            style={{ maxHeight: 64, maxWidth: 240, objectFit: 'contain', display: 'block', margin: '0 auto 12px' }}
          />
          <h3>
            {!logoPresent && <i className="fas fa-handshake me-2"></i>}
            {t('login.title')}
          </h3>
          <div style={{ fontSize: '0.7rem', marginTop: '-4px', color: 'rgba(255, 255, 255, 0.7)' }}>v{appVersion}</div>
          <p>{t('login.subtitle')}</p>
        </div>

        <div className="login-body">
          {expired && !actionData?.error && (
            <div className="alert alert-warning d-flex align-items-center" role="alert">
              <i className="fas fa-clock me-2"></i>
              <div>{t('login.sessionExpired')}</div>
            </div>
          )}

          {actionData?.error && (
            <div className="alert alert-danger d-flex align-items-center" role="alert">
              <i className="fas fa-exclamation-circle me-2"></i>
              <div>{actionData.error}</div>
            </div>
          )}

          <Form method="post">
            <input type="hidden" name="_csrf" value={csrf} />
            <div className="mb-3">
              <label htmlFor="email" className="form-label">
                {t('login.emailLabel')}
              </label>
              <div className="input-group">
                <span className="input-group-text">
                  <i className="fas fa-envelope"></i>
                </span>
                <input
                  type="email"
                  className="form-control"
                  id="email"
                  name="email"
                  placeholder={t('login.emailPlaceholder')}
                  disabled={loading}
                  autoComplete="email"
                  autoFocus
                  required
                />
              </div>
            </div>

            <div className="mb-4">
              <label htmlFor="password" className="form-label">
                {t('login.passwordLabel')}
              </label>
              <div className="input-group">
                <span className="input-group-text">
                  <i className="fas fa-lock"></i>
                </span>
                <input
                  type={showPassword ? 'text' : 'password'}
                  className="form-control"
                  id="password"
                  name="password"
                  placeholder={t('login.passwordPlaceholder')}
                  disabled={loading}
                  autoComplete="current-password"
                  required
                />
                <button
                  type="button"
                  className="btn btn-outline-secondary"
                  onClick={() => setShowPassword(!showPassword)}
                  tabIndex={-1}
                >
                  <i className={`fas fa-${showPassword ? 'eye-slash' : 'eye'}`}></i>
                </button>
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
                  {t('login.signingIn')}
                </>
              ) : (
                <>
                  <i className="fas fa-sign-in-alt me-2"></i>
                  {t('login.signIn')}
                </>
              )}
            </button>
          </Form>

          <div className="text-center mt-3">
            <Link to="/forgot-password">
              <i className="fas fa-key me-1"></i>
              {t('login.forgotPassword')}
            </Link>
          </div>

          <div className="text-center mt-4">
            <small className="text-muted">
              <i className="fas fa-info-circle me-1"></i>
              {t('login.credentialsHint')}
            </small>
          </div>

          <LoginNotices />
        </div>
      </div>
    </div>
  );
};

export default Login;
