/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { createContext, useContext, useCallback } from 'react';

const AuthContext = createContext(null);

// User roles hierarchy (highest to lowest)
export const UserRoles = {
  JADDAR_MASTER_ADMIN: 'jaddar_master_admin',
  GROUP_ADMIN: 'group_admin',
  REQUESTOR_GROUP_ADMIN: 'requestor_group_admin',
  REQUESTOR_GROUP_USER: 'requestor_group_user',
};

export const RoleHierarchy = {
  [UserRoles.JADDAR_MASTER_ADMIN]: 1,
  [UserRoles.GROUP_ADMIN]: 2,
  [UserRoles.REQUESTOR_GROUP_ADMIN]: 3,
  [UserRoles.REQUESTOR_GROUP_USER]: 4,
};

export const RoleDisplayNames = {
  [UserRoles.JADDAR_MASTER_ADMIN]: 'Master Admin',
  [UserRoles.GROUP_ADMIN]: 'Group Admin',
  [UserRoles.REQUESTOR_GROUP_ADMIN]: 'Requestor Admin',
  [UserRoles.REQUESTOR_GROUP_USER]: 'Requestor User',
};

export const RoleBadgeClasses = {
  [UserRoles.JADDAR_MASTER_ADMIN]: 'role-master',
  [UserRoles.GROUP_ADMIN]: 'role-admin',
  [UserRoles.REQUESTOR_GROUP_ADMIN]: 'role-group-admin',
  [UserRoles.REQUESTOR_GROUP_USER]: 'role-group-user',
};

/**
 * Read-only view of the authenticated user, hydrated from the root loader.
 * JWTs live in an httpOnly cookie JS can't read, so there are no tokens/localStorage
 * here; logout navigates to the server /logout action.
 */
export const AuthProvider = ({ children, initialUser = null }) => {
  const user = initialUser;
  const isAuthenticated = !!user;

  const hasRole = useCallback((role) => {
    if (!user) return false;
    return user.type === role || user.roles?.includes(role);
  }, [user]);

  const getHighestRole = useCallback(() => {
    if (!user) return null;
    const userRoles = [...(user.roles || [])];
    if (user.type) userRoles.push(user.type);
    let highestRole = null;
    let highestLevel = Infinity;
    for (const role of userRoles) {
      const level = RoleHierarchy[role];
      if (level && level < highestLevel) {
        highestLevel = level;
        highestRole = role;
      }
    }
    return highestRole || UserRoles.REQUESTOR_GROUP_USER;
  }, [user]);

  const hasRoleLevel = useCallback((requiredRole) => {
    if (!user) return false;
    const highestRole = getHighestRole();
    const userLevel = RoleHierarchy[highestRole] || Infinity;
    const requiredLevel = RoleHierarchy[requiredRole] || 0;
    return userLevel <= requiredLevel;
  }, [user, getHighestRole]);

  const isMasterAdmin = useCallback(() => hasRoleLevel(UserRoles.JADDAR_MASTER_ADMIN), [hasRoleLevel]);
  const isGroupAdmin = useCallback(() => hasRoleLevel(UserRoles.GROUP_ADMIN), [hasRoleLevel]);
  const isRequestorGroupAdmin = useCallback(() => hasRoleLevel(UserRoles.REQUESTOR_GROUP_ADMIN), [hasRoleLevel]);

  const canManageGroup = useCallback((groupName) => {
    if (!user) return false;
    if (hasRoleLevel(UserRoles.GROUP_ADMIN)) return true;
    if (hasRoleLevel(UserRoles.REQUESTOR_GROUP_ADMIN)) {
      return user.groups?.includes(groupName);
    }
    return false;
  }, [user, hasRoleLevel]);

  const getPrimaryRole = useCallback(() => getHighestRole(), [getHighestRole]);

  const logout = useCallback(() => {
    window.location.href = '/logout';
  }, []);

  const value = {
    user,
    loading: false,
    isAuthenticated,
    logout,
    hasRole,
    hasRoleLevel,
    getHighestRole,
    isMasterAdmin,
    isGroupAdmin,
    isRequestorGroupAdmin,
    canManageGroup,
    getPrimaryRole,
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
