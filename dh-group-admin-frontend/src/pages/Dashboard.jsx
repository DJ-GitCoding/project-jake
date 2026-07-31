/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useContext } from 'react';
import { Link } from 'react-router';
import { getStats, getPendingSubscriptions } from '../services/api';
import { GroupContext } from '../context/GroupContext';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const StatCard = ({ icon, label, value, color = 'primary', to }) => {
  const inner = (
    <div className={`card border-${color} h-100`}>
      <div className="card-body d-flex align-items-center">
        <div className={`rounded-circle bg-${color} bg-opacity-10 d-flex align-items-center justify-content-center me-3`}
          style={{ width: 48, height: 48, flexShrink: 0 }}>
          <i className={`${icon} text-${color}`}></i>
        </div>
        <div>
          <div className="text-muted small">{label}</div>
          <div className="fs-4 fw-bold">{value ?? '—'}</div>
        </div>
      </div>
    </div>
  );
  return to ? <Link to={to} className="text-decoration-none">{inner}</Link> : inner;
};

const Dashboard = () => {
  const { t } = useT();
  const { selectedGroup, isAllMode, isMaster } = useContext(GroupContext);
  const [stats, setStats] = useState(null);
  const [pending, setPending] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [statsRes, pendingRes] = await Promise.all([
        getStats(),
        getPendingSubscriptions(),
      ]);
      setStats(statsRes);
      setPending(pendingRes || []);
    } catch (err) {
      toast.error(t('dashboard.errors.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="d-flex justify-content-center align-items-center" style={{ minHeight: 300 }}>
        <div className="spinner-border text-primary" />
      </div>
    );
  }

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="mb-1">{t('dashboard.title')}</h2>
          <p className="text-muted mb-0">
            {t('dashboard.subtitle')}
            {isAllMode && <span className="badge bg-info ms-2" style={{ fontSize: 10 }}>{t('common.allGroups')}</span>}
            {selectedGroup && <span className="badge bg-primary ms-2" style={{ fontSize: 10 }}>{selectedGroup.name}</span>}
          </p>
        </div>
        <button className="btn btn-outline-secondary btn-sm" onClick={loadData}>
          <i className="fa-solid fa-arrows-rotate me-1"></i> {t('common.refresh')}
        </button>
      </div>

      {/* Stats Cards */}
      <div className="row g-3 mb-4">
        <div className="col-md-3">
          <StatCard icon="fa-solid fa-file-contract" label={t('dashboard.stats.totalTemplates')} value={stats?.totalTemplates} color="primary" to="/templates" />
        </div>
        <div className="col-md-3">
          <StatCard icon="fa-solid fa-eye" label={t('dashboard.stats.published')} value={stats?.publishedTemplates} color="success" to="/templates" />
        </div>
        <div className="col-md-3">
          <StatCard icon="fa-solid fa-handshake" label={t('dashboard.stats.totalSubscriptions')} value={stats?.totalSubscriptions} color="info" to="/subscriptions" />
        </div>
        <div className="col-md-3">
          <StatCard icon="fa-solid fa-database" label={t('dashboard.stats.dataHolders')} value={stats?.totalDataHolders} color="secondary" to="/data-holders" />
        </div>
      </div>

      <div className="row g-3 mb-4">
        <div className="col-md-2">
          <StatCard icon="fa-solid fa-clock" label={t('dashboard.stats.pending')} value={stats?.pendingSubscriptions} color="warning" />
        </div>
        <div className="col-md-2">
          <StatCard icon="fa-solid fa-thumbs-up" label={t('dashboard.stats.approved')} value={stats?.approvedSubscriptions} color="info" />
        </div>
        <div className="col-md-2">
          <StatCard icon="fa-solid fa-flask" label={t('dashboard.stats.testing')} value={stats?.testingSubscriptions} color="warning" />
        </div>
        <div className="col-md-2">
          <StatCard icon="fa-solid fa-check-circle" label={t('common.active')} value={stats?.activeSubscriptions} color="success" />
        </div>
        <div className="col-md-2">
          <StatCard icon="fa-solid fa-pause-circle" label={t('dashboard.stats.suspended')} value={stats?.suspendedSubscriptions} color="danger" />
        </div>
        <div className="col-md-2">
          <StatCard icon="fa-solid fa-ban" label={t('dashboard.stats.denied')} value={stats?.deniedSubscriptions} color="dark" />
        </div>
      </div>

      {/* DH Groups row — visible to master */}
      {isMaster && (
        <div className="row g-3 mb-4">
          <div className="col-md-4">
            <StatCard icon="fa-solid fa-layer-group" label={t('dashboard.stats.dhGroups')} value={stats?.totalDataHolderGroups} color="primary" to="/data-holder-groups" />
          </div>
          <div className="col-md-4">
            <StatCard icon="fa-solid fa-circle-check" label={t('dashboard.stats.activeGroups')} value={stats?.activeDataHolderGroups} color="success" to="/data-holder-groups" />
          </div>
          <div className="col-md-4">
            <StatCard icon="fa-solid fa-users-gear" label={t('dashboard.stats.totalUsers')} value={stats?.totalUsers} color="secondary" to="/users" />
          </div>
        </div>
      )}

      {/* Pending Subscriptions */}
      <div className="card">
        <div className="card-header d-flex justify-content-between align-items-center">
          <h5 className="mb-0">
            <i className="fa-solid fa-clock text-warning me-2"></i>
            {t('dashboard.pendingSubscriptions.title')}
            {pending.length > 0 && <span className="badge bg-warning text-dark ms-2">{pending.length}</span>}
          </h5>
          <Link to="/subscriptions" className="btn btn-sm btn-outline-primary">{t('dashboard.viewAll')}</Link>
        </div>
        <div className="card-body p-0">
          {pending.length === 0 ? (
            <div className="text-center text-muted py-5">
              <i className="fa-solid fa-check-circle fa-2x mb-2 text-success"></i>
              <p className="mb-0">{t('dashboard.pendingSubscriptions.empty')}</p>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="table table-hover mb-0">
                <thead className="table-light">
                  <tr>
                    <th>{t('dashboard.table.requestId')}</th>
                    <th>{t('dashboard.table.requestor')}</th>
                    <th>{t('dashboard.table.template')}</th>
                    <th>{t('dashboard.table.dataHolder')}</th>
                    <th>{t('dashboard.table.created')}</th>
                  </tr>
                </thead>
                <tbody>
                  {pending.slice(0, 10).map(sub => (
                    <tr key={sub.id}>
                      <td><code className="small">{sub.requestId?.slice(0, 16)}...</code></td>
                      <td>
                        <strong>{sub.requestorGroupName}</strong>
                        {sub.requestorOrganization && <div className="text-muted small">{sub.requestorOrganization}</div>}
                      </td>
                      <td>{sub.templateName || '—'}</td>
                      <td>{sub.dataholderName || sub.dataholderId || '—'}</td>
                      <td className="text-muted small">{sub.createdAt ? new Date(sub.createdAt).toLocaleDateString() : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
