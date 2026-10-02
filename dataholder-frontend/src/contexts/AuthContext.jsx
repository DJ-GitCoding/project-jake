/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { createContext, useContext, useCallback } from 'react';

const AuthContext = createContext(null);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

/**
 * Read-only view of the authenticated user, hydrated from the root loader.
 * JWTs live in an httpOnly cookie JS can't read, so there are no tokens/localStorage
 * here; logout hits the server /logout action.
 * Interface: user, isAuthenticated, isMaster, isAssistantAdmin, hasAccess, logout.
 */
export const AuthProvider = ({ children, initialUser = null }) => {
  const user = initialUser;
  const isAuthenticated = !!user;

  /** MASTER account: access to everything, including user management. */
  const isMaster = useCallback(() => user?.type === 'MASTER', [user]);

  /** ASSISTANT_ADMIN account: all features except user management. */
  const isAssistantAdmin = useCallback(() => user?.type === 'ASSISTANT_ADMIN', [user]);

  /** Feature access check; currently only user management is restricted to MASTER. */
  const hasAccess = useCallback(
    (feature) => {
      if (!user) return false;
      switch (feature) {
        case 'user-management':
          return user.type === 'MASTER';
        default:
          return true;
      }
    },
    [user]
  );

  /*
   * Auth is handled entirely by the BFF proxy (Bearer attached server-side
   * from the encrypted session cookie), so client code never needs a header.
   */
  const getAuthHeader = useCallback(() => ({}), []);

  const logout = useCallback(() => {
    window.location.href = '/logout';
  }, []);

  const value = {
    user,
    accessToken: null,
    loading: false,
    isAuthenticated,
    logout,
    getAuthHeader,
    isMaster,
    isAssistantAdmin,
    hasAccess,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export default AuthContext;
