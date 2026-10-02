/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { Link } from 'react-router';
import { useT } from '../i18n';

const Unauthorized = () => {
  const { t } = useT();
  return (
    <div className="d-flex align-items-center justify-content-center min-vh-100 bg-light">
      <div className="text-center">
        <div className="mb-4">
          <i className="fas fa-lock text-danger" style={{ fontSize: '5rem' }}></i>
        </div>
        <h1 className="h2 mb-3">{t('unauthorized.title')}</h1>
        <p className="text-muted mb-4">
          {t('unauthorized.message')}
          <br />
          {t('unauthorized.contactAdmin')}
        </p>
        <div className="d-flex gap-2 justify-content-center">
          <Link to="/dashboard" className="btn btn-primary">
            <i className="fas fa-home me-2"></i>
            {t('unauthorized.goToDashboard')}
          </Link>
          <Link to="/login" className="btn btn-outline-secondary">
            <i className="fas fa-sign-in-alt me-2"></i>
            {t('unauthorized.signIn')}
          </Link>
        </div>
      </div>
    </div>
  );
};

export default Unauthorized;
