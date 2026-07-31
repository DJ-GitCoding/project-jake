/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router';
import api from '../services/api';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const MIN_PASSWORD_LENGTH = 8;

const ResetPassword = () => {
  const { t } = useT();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token');

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  const validate = () => {
    const nextErrors = {};
    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      nextErrors.newPassword = t('resetPassword.passwordTooShort', { min: MIN_PASSWORD_LENGTH });
    }
    if (newPassword !== confirmPassword) {
      nextErrors.confirmPassword = t('resetPassword.passwordsDoNotMatch');
    }
    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (loading) return;
    if (!validate()) return;

    setLoading(true);
    try {
      await api.post('/api/auth/reset-password', { token, newPassword });
      setDone(true);
      toast.success(t('resetPassword.successRedirect'));
      setTimeout(() => {
        navigate('/login');
      }, 2000);
    } catch (error) {
      const detail =
        error.response?.data?.detail ||
        t('resetPassword.resetFailed');
      toast.error(detail);
      setErrors({ form: detail });
    } finally {
      setLoading(false);
    }
  };

  const renderInvalidLink = () => (
    <div className="card-body p-5">
      <div className="text-center mb-4">
        <h2 className="fw-bold mb-2">{t('resetPassword.invalidLinkTitle')}</h2>
      </div>
      <div className="alert alert-danger" role="alert">
        {t('resetPassword.invalidLinkMessage')}
      </div>
      <div className="text-center mt-4">
        <Link to="/forgot-password" className="text-primary text-decoration-none">
          {t('resetPassword.requestNewLink')}
        </Link>
      </div>
    </div>
  );

  const renderForm = () => (
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
        <h2 className="fw-bold mb-2">{t('resetPassword.title')}</h2>
        <p className="text-secondary mb-0">{t('resetPassword.subtitle')}</p>
      </div>

      {done ? (
        <div className="alert alert-success" role="alert">
          {t('resetPassword.successRedirect')}
        </div>
      ) : (
        <form onSubmit={handleSubmit} noValidate>
          <div className="mb-3">
            <label htmlFor="newPassword" className="form-label">{t('resetPassword.newPasswordLabel')}</label>
            <input
              type="password"
              id="newPassword"
              className={`form-control ${errors.newPassword ? 'is-invalid' : ''}`}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
              disabled={loading}
            />
            {errors.newPassword && (
              <div className="invalid-feedback">{errors.newPassword}</div>
            )}
          </div>

          <div className="mb-3">
            <label htmlFor="confirmPassword" className="form-label">{t('resetPassword.confirmPasswordLabel')}</label>
            <input
              type="password"
              id="confirmPassword"
              className={`form-control ${errors.confirmPassword ? 'is-invalid' : ''}`}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              disabled={loading}
            />
            {errors.confirmPassword && (
              <div className="invalid-feedback">{errors.confirmPassword}</div>
            )}
          </div>

          <button
            type="submit"
            className="btn btn-primary w-100 py-3 fw-semibold"
            disabled={loading}
          >
            {loading ? (
              <>
                <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                {t('resetPassword.resetting')}
              </>
            ) : (
              <>
                <i className="bi bi-shield-lock me-2"></i>
                {t('resetPassword.resetPassword')}
              </>
            )}
          </button>
        </form>
      )}

      <div className="text-center mt-4">
        <Link to="/login" className="text-primary text-decoration-none">
          <i className="bi bi-arrow-left me-1"></i>
          {t('resetPassword.backToLogin')}
        </Link>
      </div>
    </div>
  );

  return (
    <div className="container-fluid min-vh-100 d-flex align-items-center justify-content-center bg-light">
      <div className="row w-100 justify-content-center">
        <div className="col-11 col-sm-8 col-md-6 col-lg-4">
          <div className="card shadow-lg border-0">
            {token ? renderForm() : renderInvalidLink()}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ResetPassword;
