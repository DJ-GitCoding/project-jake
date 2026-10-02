/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuth } from '../context/AuthContext';
import Loading from './Loading';
import { useT } from '../i18n';

const ProtectedRoute = ({ children, allowedRoles = [] }) => {
  const { isAuthenticated, loading, hasRoleLevel } = useAuth();
  const location = useLocation();
  const { t } = useT();

  if (loading) {
    return <Loading fullPage message={t('common.checkingAuth')} />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // Check role-based access if allowedRoles specified
  if (allowedRoles.length > 0) {
    // User has access if they have at least one of the allowed roles (or higher)
    const hasAccess = allowedRoles.some((role) => hasRoleLevel(role));
    if (!hasAccess) {
      return <Navigate to="/unauthorized" replace />;
    }
  }

  return children;
};

export default ProtectedRoute;
