/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { createContext, useContext, useCallback } from 'react';

const AuthContext = createContext(null);

/**
 * Read-only view of the authenticated user, hydrated from the root loader.
 * JWTs live in an httpOnly cookie JS can't read, so there are no tokens/localStorage/
 * refresh here — the BFF proxy refreshes server-side on 401, logout hits /logout.
 */
export const AuthProvider = ({ children, initialUser = null }) => {
  const user = initialUser;
  const isAuthenticated = !!user;

  // Role helper based on the JWT realm_access.roles copied onto the user profile.
  const hasRole = useCallback(
    (role) => Array.isArray(user?.roles) && user.roles.includes(role),
    [user]
  );

  // Admin check based on role or the admin email.
  const isAdmin = useCallback(
    () => hasRole('admin') || user?.email === 'admin@admin.com',
    [hasRole, user]
  );

  const logout = useCallback(() => {
    window.location.href = '/logout';
  }, []);

  const value = {
    user,
    loading: false,
    isAuthenticated,
    logout,
    hasRole,
    isAdmin,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
};

export default AuthContext;
