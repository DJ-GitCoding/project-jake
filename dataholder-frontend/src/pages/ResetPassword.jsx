/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { resetPassword } from '../services/api';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const ResetPassword = () => {
  const { t } = useT();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (newPassword.length < 8) {
      toast.error(t('resetPassword.passwordTooShort'));
      return;
    }

    if (newPassword !== confirmPassword) {
      toast.error(t('resetPassword.passwordsMismatch'));
      return;
    }

    setLoading(true);

    try {
      const response = await resetPassword(token, newPassword);

      if (response.data.success) {
        toast.success(response.data.message || t('resetPassword.resetSuccess'));
        setTimeout(() => navigate('/login'), 1500);
      } else {
        toast.error(response.data.error || t('resetPassword.unableToReset'));
      }
    } catch (error) {
      console.error('Reset password error:', error);
      const errorMessage =
        error.response?.data?.error || t('resetPassword.unableToReset');
      toast.error(errorMessage);
    } finally {
      setLoading(false);
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
            <h1 className="h4 fw-bold mb-1">{t('resetPassword.title')}</h1>
            <p className="text-muted mb-0">{t('login.title')}</p>
          </div>

          {!token ? (
            <div className="d-grid gap-3">
              <p className="text-center text-secondary">
                {t('resetPassword.invalidLink')}
              </p>
              <Link to="/forgot-password" className="btn btn-primary w-100">
                {t('resetPassword.requestNewLink')}
              </Link>
            </div>
          ) : (
            <form onSubmit={handleSubmit}>
              <div className="mb-3">
                <label className="form-label">{t('resetPassword.newPassword')}</label>
                <div className="input-group">
                  <span className="input-group-text"><i className="fa-solid fa-lock"></i></span>
                  <input
                    type="password"
                    className="form-control"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder={t('resetPassword.newPasswordPlaceholder')}
                    autoComplete="new-password"
                    autoFocus
                  />
                </div>
              </div>

              <div className="mb-3">
                <label className="form-label">{t('resetPassword.confirmPassword')}</label>
                <div className="input-group">
                  <span className="input-group-text"><i className="fa-solid fa-lock"></i></span>
                  <input
                    type="password"
                    className="form-control"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder={t('resetPassword.confirmPasswordPlaceholder')}
                    autoComplete="new-password"
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
                    {t('resetPassword.resetting')}
                  </>
                ) : (
                  t('resetPassword.title')
                )}
              </button>
            </form>
          )}

          <div className="text-center text-muted small mt-4 pt-3 border-top">
            <p className="font-monospace mb-0">
              <Link to="/login">
                {t('resetPassword.backToSignIn')}
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ResetPassword;
