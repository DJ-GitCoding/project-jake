/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuth } from '../contexts/AuthContext';
import Loading from './Loading';

/**
 * ProtectedRoute wraps pages that require authentication.
 * Optionally accepts a `requiredFeature` prop to restrict access
 * to certain user types (e.g., 'user-management' for MASTER only).
 */
const ProtectedRoute = ({ children, requiredFeature }) => {
  const { isAuthenticated, loading, hasAccess } = useAuth();
  const location = useLocation();

  if (loading) {
    return <Loading message="Checking authentication..." />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // If a feature restriction is specified, check access
  if (requiredFeature && !hasAccess(requiredFeature)) {
    return <Navigate to="/" replace />;
  }

  return children;
};

export default ProtectedRoute;
