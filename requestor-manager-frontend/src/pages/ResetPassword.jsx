/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { authApi } from '../services/api';
import { useT } from '../i18n';

const ResetPassword = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState('');

  const navigate = useNavigate();
  const { t } = useT();

  const validate = () => {
    const newErrors = {};

    if (newPassword.length < 8) {
      newErrors.newPassword = t('resetPassword.errors.minLength');
    }

    if (newPassword !== confirmPassword) {
      newErrors.confirmPassword = t('resetPassword.errors.mismatch');
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitError('');

    if (!validate()) {
      return;
    }

    setLoading(true);

    try {
      await authApi.resetPassword(token, newPassword);
      setSuccess(true);
      setTimeout(() => {
        navigate('/login', { replace: true });
      }, 2500);
    } catch (err) {
      setSubmitError(
        err.response?.data?.message ||
          t('resetPassword.errors.resetFailed')
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-header">
          <h3>
            <i className="fas fa-lock me-2"></i>
            {t('resetPassword.title')}
          </h3>
          <p>{t('resetPassword.subtitle')}</p>
        </div>

        <div className="login-body">
          {!token ? (
            <>
              <div className="alert alert-danger" role="alert">
                <i className="fas fa-exclamation-triangle me-2"></i>
                {t('resetPassword.invalidLink')}
              </div>
              <div className="text-center mt-4">
                <Link to="/forgot-password">
                  <i className="fas fa-arrow-left me-1"></i>
                  {t('resetPassword.requestNewLink')}
                </Link>
              </div>
            </>
          ) : success ? (
            <>
              <div className="alert alert-success" role="alert">
                <i className="fas fa-check-circle me-2"></i>
                {t('resetPassword.successMessage')}
              </div>
              <div className="text-center mt-4">
                <Link to="/login">
                  <i className="fas fa-sign-in-alt me-1"></i>
                  {t('resetPassword.goToSignIn')}
                </Link>
              </div>
            </>
          ) : (
            <form onSubmit={handleSubmit}>
              {submitError && (
                <div className="alert alert-danger" role="alert">
                  <i className="fas fa-exclamation-circle me-2"></i>
                  {submitError}
                </div>
              )}

              <div className="mb-3">
                <label htmlFor="newPassword" className="form-label">
                  {t('resetPassword.newPasswordLabel')}
                </label>
                <div className="input-group">
                  <span className="input-group-text">
                    <i className="fas fa-lock"></i>
                  </span>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    className={`form-control${
                      errors.newPassword ? ' is-invalid' : ''
                    }`}
                    id="newPassword"
                    placeholder={t('resetPassword.newPasswordPlaceholder')}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    disabled={loading}
                    autoComplete="new-password"
                    autoFocus
                  />
                  <button
                    type="button"
                    className="btn btn-outline-secondary"
                    onClick={() => setShowPassword(!showPassword)}
                    tabIndex={-1}
                  >
                    <i
                      className={`fas fa-${showPassword ? 'eye-slash' : 'eye'}`}
                    ></i>
                  </button>
                  {errors.newPassword && (
                    <div className="invalid-feedback">{errors.newPassword}</div>
                  )}
                </div>
              </div>

              <div className="mb-4">
                <label htmlFor="confirmPassword" className="form-label">
                  {t('resetPassword.confirmPasswordLabel')}
                </label>
                <div className="input-group">
                  <span className="input-group-text">
                    <i className="fas fa-lock"></i>
                  </span>
                  <input
                    type={showPassword ? 'text' : 'password'}
                    className={`form-control${
                      errors.confirmPassword ? ' is-invalid' : ''
                    }`}
                    id="confirmPassword"
                    placeholder={t('resetPassword.confirmPasswordPlaceholder')}
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    disabled={loading}
                    autoComplete="new-password"
                  />
                  {errors.confirmPassword && (
                    <div className="invalid-feedback">
                      {errors.confirmPassword}
                    </div>
                  )}
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
                    {t('resetPassword.resetting')}
                  </>
                ) : (
                  <>
                    <i className="fas fa-check me-2"></i>
                    {t('resetPassword.submit')}
                  </>
                )}
              </button>

              <div className="text-center mt-4">
                <Link to="/login">
                  <i className="fas fa-arrow-left me-1"></i>
                  {t('resetPassword.backToSignIn')}
                </Link>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};

export default ResetPassword;
