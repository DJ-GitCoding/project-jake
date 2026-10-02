/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { Link, Form, useLoaderData, useActionData, useNavigation, useRouteLoaderData } from 'react-router';
import BrandingLogo from '../components/BrandingLogo';
import LoginNotices from '../components/LoginNotices';
import { useT } from '../i18n';

const Login = () => {
  const { t } = useT();
  const actionData = useActionData();
  const csrf = useRouteLoaderData('root')?.csrf;
  const appVersion = useRouteLoaderData('root')?.config?.appVersion || 'dev';
  const navigation = useNavigation();
  const loading = navigation.state !== 'idle';
  const expired = useLoaderData()?.expired;

  return (
    <div className="min-vh-100 d-flex align-items-center justify-content-center bg-dark">
      <div className="card shadow" style={{ width: '100%', maxWidth: 420 }}>
        <div className="card-body p-4">
          <div className="text-center mb-4">
            {/* Uploaded logo replaces the default icon; see BrandingLogo. */}
            <div className="d-flex align-items-center justify-content-center mb-2" style={{ minHeight: 48 }}>
              <BrandingLogo
                alt="Jaremin"
                style={{ maxHeight: 64, maxWidth: 240, objectFit: 'contain' }}
                fallback={<i className="fa-solid fa-server fa-2x text-primary"></i>}
              />
            </div>
            <h4 className="fw-bold mb-1">Jaremin</h4>
            <div className="text-muted" style={{ fontSize: '0.7rem', marginTop: '-4px', opacity: 0.7 }}>v{appVersion}</div>
            <p className="text-muted small">{t('login.subtitle')}</p>
          </div>

          {expired && !actionData?.error && (
            <div className="alert alert-warning d-flex align-items-center py-2" role="alert">
              <i className="fa-solid fa-clock me-2"></i>
              <div>{t('login.sessionExpired')}</div>
            </div>
          )}

          {actionData?.error && (
            <div className="alert alert-danger d-flex align-items-center py-2" role="alert">
              <i className="fa-solid fa-circle-exclamation me-2"></i>
              <div>{actionData.error}</div>
            </div>
          )}

          <Form method="post">
            <input type="hidden" name="_csrf" value={csrf} />
            <div className="mb-3">
              <label className="form-label">{t('common.email')}</label>
              <div className="input-group">
                <span className="input-group-text"><i className="fa-solid fa-envelope"></i></span>
                <input
                  type="email"
                  className="form-control"
                  name="email"
                  placeholder={t('login.emailPlaceholder')}
                  autoComplete="email"
                  autoFocus
                  required
                  disabled={loading}
                />
              </div>
            </div>

            <div className="mb-4">
              <label className="form-label">{t('common.password')}</label>
              <div className="input-group">
                <span className="input-group-text"><i className="fa-solid fa-lock"></i></span>
                <input
                  type="password"
                  className="form-control"
                  name="password"
                  placeholder="••••••••"
                  autoComplete="current-password"
                  required
                  disabled={loading}
                />
              </div>
            </div>

            <button
              type="submit"
              className="btn btn-primary w-100"
              disabled={loading}
            >
              {loading ? (
                <><span className="spinner-border spinner-border-sm me-2"></span>{t('login.signingIn')}</>
              ) : (
                <><i className="fa-solid fa-right-to-bracket me-2"></i>{t('login.signIn')}</>
              )}
            </button>

            <div className="text-center mt-3">
              <Link to="/forgot-password" className="small text-decoration-none">
                {t('login.forgotPassword')}
              </Link>
            </div>
          </Form>

          <LoginNotices />
        </div>
      </div>
    </div>
  );
};

export default Login;
