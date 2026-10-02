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
import { requestorGroupsApi, usersApi, subscriptionsApi } from '../services/api';
import Loading from '../components/Loading';
import { SubscriptionStatusChart, GroupActivityChart, isAwaitingResponse, isActiveSubscription } from '../components/DashboardCharts';
import { useT } from '../i18n';

const Dashboard = () => {
  const { user, getPrimaryRole, isRequestorGroupAdmin } = useAuth();
  const { error: showError } = useAlert();
  const { t } = useT();

  const [stats, setStats] = useState({
    requestorGroups: 0,
    groupsWithAgreements: 0,
    groups: [],
    users: 0,
    subscriptions: 0,
    byStatus: {},
    actionable: 0,
    awaitingResponse: 0,
    needsAction: 0,
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

      /* Status counts come from the server already scoped to this user's groups, so the
       * charts never need the subscription list itself. */
      let subscriptionStats = { total: 0, byStatus: {} };
      try {
        const statsRes = await subscriptionsApi.getStats();
        subscriptionStats = statsRes.data.data || subscriptionStats;
      } catch (e) {
        // Leave the charts empty rather than failing the whole dashboard.
      }

      let actionable = 0;
      try {
        const actionableRes = await subscriptionsApi.getActionable();
        actionable = (actionableRes.data.data || []).length;
      } catch (e) {
        // Same: an unavailable count should not blank the page.
      }

      /* A change the data holder group proposed is waiting on this side to accept or
       * decline, so it counts as work to do alongside the testing/activation steps. */
      const summaries = groups.flatMap(g => g.subscriptionRequests || []);
      const awaitingResponse = summaries.filter(isAwaitingResponse).length;

      setStats({
        requestorGroups: groups.length,
        groupsWithAgreements: groups.filter(g => (g.subscriptionRequests || [])
          .some(isActiveSubscription)).length,
        groups,
        users: usersCount,
        subscriptions: Number(subscriptionStats.total ?? 0),
        byStatus: subscriptionStats.byStatus || {},
        actionable,
        awaitingResponse,
        needsAction: actionable + awaitingResponse,
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
        <div className="col-6 col-xl-3">
          <Link to="/subscriptions" className="text-decoration-none">
            <div className="stats-card h-100">
              <div className="d-flex align-items-center">
                <div className="stats-icon bg-info-soft me-3">
                  <i className="fas fa-file-signature"></i>
                </div>
                <div style={{ minWidth: 0 }}>
                  <div className="stats-value">{stats.subscriptions}</div>
                  <div className="stats-label">{t('dashboard.stats.subscriptions')}</div>
                  <div className="text-muted small">
                    {t('dashboard.stats.activeCount', { count: stats.byStatus?.ACTIVE ?? 0 })}
                  </div>
                </div>
              </div>
            </div>
          </Link>
        </div>

        <div className="col-6 col-xl-3">
          <Link to="/subscriptions" className="text-decoration-none">
            <div className="stats-card h-100">
              <div className="d-flex align-items-center">
                <div className={`stats-icon me-3 ${stats.needsAction > 0 ? 'bg-warning-soft' : 'bg-success-soft'}`}>
                  <i className={`fas ${stats.needsAction > 0 ? 'fa-triangle-exclamation' : 'fa-check'}`}></i>
                </div>
                <div style={{ minWidth: 0 }}>
                  <div className="stats-value">{stats.needsAction}</div>
                  <div className="stats-label">{t('dashboard.stats.needsAction')}</div>
                  <div className="text-muted small">
                    {stats.needsAction === 0
                      ? t('dashboard.stats.allCaughtUp')
                      : stats.awaitingResponse > 0
                        ? t('dashboard.stats.changesProposed', { count: stats.awaitingResponse })
                        : t('dashboard.stats.needsActionHint')}
                  </div>
                </div>
              </div>
            </div>
          </Link>
        </div>

        <div className="col-6 col-xl-3">
          <Link to="/requestor-groups" className="text-decoration-none">
            <div className="stats-card h-100">
              <div className="d-flex align-items-center">
                <div className="stats-icon bg-success-soft me-3">
                  <i className="fas fa-building"></i>
                </div>
                <div style={{ minWidth: 0 }}>
                  <div className="stats-value">{stats.requestorGroups}</div>
                  <div className="stats-label">{t('dashboard.stats.requestorGroups')}</div>
                  <div className="text-muted small">
                    {t('dashboard.stats.withAgreements', { count: stats.groupsWithAgreements })}
                  </div>
                </div>
              </div>
            </div>
          </Link>
        </div>

        {isRequestorGroupAdmin() && (
          <div className="col-6 col-xl-3">
            <Link to="/users" className="text-decoration-none">
              <div className="stats-card h-100">
                <div className="d-flex align-items-center">
                  <div className="stats-icon bg-info-soft me-3">
                    <i className="fas fa-users"></i>
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <div className="stats-value">{stats.users}</div>
                    <div className="stats-label">{t('dashboard.stats.users')}</div>
                  </div>
                </div>
              </div>
            </Link>
          </div>
        )}
      </div>

      {/* Subscription charts */}
      <div className="row g-4 mb-4">
        <div className="col-12 col-xl-5">
          <SubscriptionStatusChart byStatus={stats.byStatus} />
        </div>
        <div className="col-12 col-xl-7">
          <GroupActivityChart groups={stats.groups} />
        </div>
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