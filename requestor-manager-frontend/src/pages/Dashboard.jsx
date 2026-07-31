/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import { Link } from 'react-router';
import { useAuth, RoleDisplayNames, RoleBadgeClasses } from '../context/AuthContext';
import { useAlert } from '../context/AlertContext';
import { requestorGroupsApi, usersApi } from '../services/api';
import Loading from '../components/Loading';
import { useT } from '../i18n';

const Dashboard = () => {
  const { user, getPrimaryRole, isRequestorGroupAdmin } = useAuth();
  const { error: showError } = useAlert();
  const { t } = useT();

  const [stats, setStats] = useState({
    requestorGroups: 0,
    users: 0,
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadDashboardData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const loadDashboardData = async () => {
    try {
      setLoading(true);

      const groupsRes = await requestorGroupsApi.getAll();
      const groups = groupsRes.data.data || [];

      // Try to get users count (may fail for non-admins)
      let usersCount = 0;
      if (isRequestorGroupAdmin()) {
        try {
          const usersRes = await usersApi.getAll();
          usersCount = (usersRes.data.data || []).length;
        } catch (e) {
          // User might not have permission to list all users
        }
      }

      setStats({
        requestorGroups: groups.length,
        users: usersCount,
      });
    } catch (err) {
      console.error('Failed to load dashboard data:', err);
      showError(t('dashboard.loadError'));
    } finally {
      setLoading(false);
    }
  };

  const getGreeting = () => {
    const hour = new Date().getHours();
    if (hour < 12) return t('dashboard.greeting.morning');
    if (hour < 18) return t('dashboard.greeting.afternoon');
    return t('dashboard.greeting.evening');
  };

  const primaryRole = getPrimaryRole();
  const roleDisplayName = RoleDisplayNames[primaryRole] || t('common.user');
  const roleBadgeClass = RoleBadgeClasses[primaryRole] || '';

  if (loading) {
    return <Loading message={t('dashboard.loading')} />;
  }

  return (
    <div>
      {/* Page Header */}
      <div className="page-header">
        <h1>
          {t('dashboard.greetingLine', { greeting: getGreeting(), name: user?.firstName || t('common.user') })}
        </h1>
        <p>
          {t('dashboard.loggedInAs')}{' '}
          <span className={`badge ${roleBadgeClass}`}>{roleDisplayName}</span>
          {user?.groups?.length > 0 && (
            <span className="ms-2">
              {t('dashboard.inGroups')} <strong>{user.groups.join(', ')}</strong>
            </span>
          )}
        </p>
      </div>

      {/* Stats Cards */}
      <div className="row g-4 mb-4">
        <div className="col-md-6">
          <div className="stats-card">
            <div className="d-flex align-items-center">
              <div className="stats-icon bg-success-soft me-3">
                <i className="fas fa-building"></i>
              </div>
              <div>
                <div className="stats-value">{stats.requestorGroups}</div>
                <div className="stats-label">{t('dashboard.stats.requestorGroups')}</div>
              </div>
            </div>
          </div>
        </div>

        {isRequestorGroupAdmin() && (
          <div className="col-md-6">
            <div className="stats-card">
              <div className="d-flex align-items-center">
                <div className="stats-icon bg-info-soft me-3">
                  <i className="fas fa-users"></i>
                </div>
                <div>
                  <div className="stats-value">{stats.users}</div>
                  <div className="stats-label">{t('dashboard.stats.users')}</div>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Quick Actions */}
      <div className="row g-4">
        <div className="col-lg-6">
          <div className="card h-100">
            <div className="card-header">
              <h5>
                <i className="fas fa-bolt me-2 text-warning"></i>
                {t('dashboard.quickActions.title')}
              </h5>
            </div>
            <div className="card-body">
              <div className="d-grid gap-2">
                <Link to="/requestor-groups" className="btn btn-outline-primary">
                  <i className="fas fa-building me-2"></i>
                  {t('dashboard.quickActions.viewGroups')}
                </Link>
                {isRequestorGroupAdmin() && (
                  <Link to="/users" className="btn btn-outline-primary">
                    <i className="fas fa-users me-2"></i>
                    {t('dashboard.quickActions.manageUsers')}
                  </Link>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Role Information Card */}
      <div className="row mt-4">
        <div className="col-12">
          <div className="card">
            <div className="card-header">
              <h5>
                <i className="fas fa-info-circle me-2 text-info"></i>
                {t('dashboard.permissions.title')}
              </h5>
            </div>
            <div className="card-body">
              <div className="row">
                <div className="col-md-6">
                  <h6>{t('dashboard.permissions.currentRole')}</h6>
                  <p>
                    <span className={`badge ${roleBadgeClass} me-2`}>
                      {roleDisplayName}
                    </span>
                    {primaryRole === 'jaddar_master_admin' && (
                      <span className="text-muted">{t('dashboard.roleDescriptions.jaddarMasterAdmin')}</span>
                    )}
                    {primaryRole === 'group_admin' && (
                      <span className="text-muted">{t('dashboard.roleDescriptions.groupAdmin')}</span>
                    )}
                    {primaryRole === 'requestor_group_admin' && (
                      <span className="text-muted">{t('dashboard.roleDescriptions.requestorGroupAdmin')}</span>
                    )}
                    {primaryRole === 'requestor_group_user' && (
                      <span className="text-muted">{t('dashboard.roleDescriptions.requestorGroupUser')}</span>
                    )}
                  </p>
                </div>
                <div className="col-md-6">
                  <h6>{t('dashboard.permissions.groupMembership')}</h6>
                  {user?.groups?.length > 0 ? (
                    <div>
                      {user.groups.map((group, index) => (
                        <span key={index} className="badge bg-secondary me-1 mb-1">
                          {group}
                        </span>
                      ))}
                    </div>
                  ) : (
                    <p className="text-muted mb-0">{t('dashboard.permissions.noGroupMembership')}</p>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;