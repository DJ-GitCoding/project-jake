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
import { useT } from '../i18n';

const ResetPassword = () => {
  const { t } = useT();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [success, setSuccess] = useState(false);

  const validate = () => {
    const next = {};
    if (password.length < 8) {
      next.password = t('resetPassword.validation.passwordTooShort');
    }
    if (confirm !== password) {
      next.confirm = t('resetPassword.validation.passwordsDoNotMatch');
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitError('');
    if (!validate()) return;
    setLoading(true);
    try {
      await resetPassword(token, password);
      setSuccess(true);
      setTimeout(() => navigate('/login'), 2500);
    } catch (err) {
      setSubmitError(err.data?.error || err.message || t('resetPassword.errors.resetFailed'));
    } finally {
      setLoading(false);
    }
  };

  const renderShell = (children, icon, title, subtitle) => (
    <div className="min-vh-100 d-flex align-items-center justify-content-center bg-dark">
      <div className="card shadow" style={{ width: '100%', maxWidth: 420 }}>
        <div className="card-body p-4">
          <div className="text-center mb-4">
            <i className={`fa-solid ${icon} fa-2x text-primary mb-2`}></i>
            <h4 className="fw-bold mb-1">{title}</h4>
            {subtitle && <p className="text-muted small">{subtitle}</p>}
          </div>
          {children}
        </div>
      </div>
    </div>
  );

  if (!token) {
    return renderShell(
      <>
        <div className="alert alert-danger" role="alert">
          <i className="fa-solid fa-triangle-exclamation me-2"></i>
          {t('resetPassword.invalidLink.body')}
        </div>
        <Link to="/forgot-password" className="btn btn-primary w-100">
          {t('resetPassword.invalidLink.requestNew')}
        </Link>
      </>,
      'fa-link-slash',
      t('resetPassword.invalidLink.title'),
    );
  }

  if (success) {
    return renderShell(
      <>
        <div className="alert alert-success" role="alert">
          <i className="fa-solid fa-circle-check me-2"></i>
          {t('resetPassword.success.body')}
        </div>
        <Link to="/login" className="btn btn-outline-secondary w-100">
          <i className="fa-solid fa-arrow-left me-2"></i>{t('resetPassword.backToSignIn')}
        </Link>
      </>,
      'fa-circle-check',
      t('resetPassword.success.title'),
    );
  }

  return renderShell(
    <form onSubmit={handleSubmit}>
      {submitError && (
        <div className="alert alert-danger py-2" role="alert">
          <i className="fa-solid fa-triangle-exclamation me-2"></i>{submitError}
        </div>
      )}

      <div className="mb-3">
        <label className="form-label">{t('resetPassword.newPassword')}</label>
        <div className="input-group">
          <span className="input-group-text"><i className="fa-solid fa-lock"></i></span>
          <input
            type="password"
            className={`form-control ${errors.password ? 'is-invalid' : ''}`}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            autoFocus
            disabled={loading}
          />
        </div>
        {errors.password && <div className="text-danger small mt-1">{errors.password}</div>}
      </div>

      <div className="mb-4">
        <label className="form-label">{t('resetPassword.confirmPassword')}</label>
        <div className="input-group">
          <span className="input-group-text"><i className="fa-solid fa-lock"></i></span>
          <input
            type="password"
            className={`form-control ${errors.confirm ? 'is-invalid' : ''}`}
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            placeholder="••••••••"
            disabled={loading}
          />
        </div>
        {errors.confirm && <div className="text-danger small mt-1">{errors.confirm}</div>}
      </div>

      <button
        type="submit"
        className="btn btn-primary w-100 mb-3"
        disabled={loading}
      >
        {loading ? (
          <><span className="spinner-border spinner-border-sm me-2"></span>{t('resetPassword.resetting')}</>
        ) : (
          <><i className="fa-solid fa-key me-2"></i>{t('resetPassword.resetPassword')}</>
        )}
      </button>

      <div className="text-center">
        <Link to="/login" className="small text-decoration-none">
          <i className="fa-solid fa-arrow-left me-1"></i>{t('resetPassword.backToSignIn')}
        </Link>
      </div>
    </form>,
    'fa-key',
    t('resetPassword.title'),
    t('resetPassword.subtitle'),
  );
};

export default ResetPassword;
