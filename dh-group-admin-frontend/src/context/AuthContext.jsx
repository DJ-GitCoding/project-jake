/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { createContext, useContext, useCallback } from 'react';

const AuthContext = createContext(null);

/*
 * dh-group-admin uses a numeric `user.type` for authorization:
 *   1 = Master admin (sees everything, manages DH groups + instances)
 *   2 = Group admin  (can manage users within their group)
 *   3 = Regular user
 * Lower numbers are more privileged; the thresholds below gate each route guard.
 */
export const USER_TYPE_LABEL_KEYS = { 1: 'common.master', 2: 'common.admin', 3: 'common.user' };
export const USER_TYPE_COLORS = { 1: 'danger', 2: 'warning', 3: 'info' };

/**
 * Read-only view of the authenticated user, hydrated from the root loader.
 * JWTs live in an httpOnly cookie JS can't read, so there are no tokens/localStorage
 * here; logout navigates to the server /logout action.
 */
export const AuthProvider = ({ children, initialUser = null }) => {
  const user = initialUser;
  const isAuthenticated = !!user;

  // Master admin (type 1).
  const isMaster = user?.type === 1;
  // Admin-or-higher (type <= 2): can access the Users page.
  const isAdmin = user != null && user.type <= 2;

  /*
   * True when the user's type is at least as privileged as `maxType`
   * (lower number = more privileged), i.e. user.type <= maxType.
   */
  const hasTypeAtLeast = useCallback(
    (maxType) => user != null && user.type <= maxType,
    [user]
  );

  const logout = useCallback(() => {
    window.location.href = '/logout';
  }, []);

  const value = {
    user,
    loading: false,
    isAuthenticated,
    isMaster,
    isAdmin,
    hasTypeAtLeast,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export default AuthContext;
