/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router';
import { format } from 'date-fns';
import { 
  getRequestStats, 
  getPendingRequests, 
  getAuditLogs,
  getAvailableDomains,
  getJakeCompliance
} from '../services/api';
import Loading from '../components/Loading';
import AccessLevelBadge from '../components/AccessLevelBadge';
import { useT } from '../i18n';

const Dashboard = () => {
  const [stats, setStats] = useState(null);
  const [recentRequests, setRecentRequests] = useState([]);
  const [recentLogs, setRecentLogs] = useState([]);
  const [domains, setDomains] = useState([]);
  const [jakeData, setJakeData] = useState(null);
  const [jakeLoading, setJakeLoading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [lastRefreshed, setLastRefreshed] = useState(null);

  const pollingRef = useRef(true);
  const { t } = useT();

  useEffect(() => {
    loadDashboardData();
  }, []);

  // Auto-poll every 5 seconds
  useEffect(() => {
    pollingRef.current = true;

    const intervalId = setInterval(async () => {
      if (!pollingRef.current) return;
      try {
        const [statsRes, pendingRes, logsRes] = await Promise.all([
          getRequestStats(),
          getPendingRequests(),
          getAuditLogs(0, 5),
        ]);

        setStats(statsRes.data);
        setRecentRequests(pendingRes.data.slice(0, 5));
        setRecentLogs(logsRes.data.content || []);
        setLastRefreshed(new Date());
      } catch (error) {
        console.error('Auto-refresh failed:', error);
      }
    }, 5000);

    return () => {
      pollingRef.current = false;
      clearInterval(intervalId);
    };
  }, []);

  const loadDashboardData = async () => {
    try {
      const [statsRes, pendingRes, logsRes, domainsRes] = await Promise.all([
        getRequestStats(),
        getPendingRequests(),
        getAuditLogs(0, 5),
        getAvailableDomains(),
      ]);
      
      setStats(statsRes.data);
      setRecentRequests(pendingRes.data.slice(0, 5));
      setRecentLogs(logsRes.data.content || []);
      setDomains(domainsRes.data || []);
      setLastRefreshed(new Date());
    } catch (error) {
      console.error('Failed to load dashboard data:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadJakeCompliance = async () => {
    setJakeLoading(true);
    try {
      const res = await getJakeCompliance();
      setJakeData(res.data);
    } catch (err) {
      console.error('Failed to load JAKE compliance:', err);
    } finally {
      setJakeLoading(false);
    }
  };

  if (loading) {
    return <Loading message={t('dashboard.loadingMessage')} />;
  }

  return (
    <div>
      <div className="mb-4 d-flex justify-content-between align-items-center">
        <div>
          <h2 className="h3 fw-bold mb-1">{t('dashboard.title')}</h2>
          <p className="text-muted mb-0">{t('dashboard.subtitle')}</p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          {lastRefreshed && (
            <span style={{ fontSize: '12px', color: 'var(--color-text-muted, #6b7280)' }}>
              {t('dashboard.lastUpdated', { time: lastRefreshed.toLocaleTimeString() })}
            </span>
          )}
          <span
            style={{
              display: 'inline-block',
              width: '8px',
              height: '8px',
              borderRadius: '50%',
              backgroundColor: '#22c55e',
              animation: 'pulse 2s infinite',
            }}
            title={t('dashboard.autoRefreshHint')}
          ></span>
          <button className="btn btn-secondary btn-sm" onClick={loadDashboardData}>
            <i className="fa-solid fa-arrows-rotate"></i>
            {t('common.refresh')}
          </button>
        </div>
      </div>

      <style>{`
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.3; }
        }
      `}</style>

      {/* Stats Grid */}
      <div className="row g-3">
        <div className="col-sm-6 col-xl-3">
          <div className="card"><div className="card-body">
            <span className="d-inline-flex align-items-center justify-content-center rounded bg-warning-subtle text-warning" style={{ width: 48, height: 48 }}>
              <i className="fa-solid fa-hourglass-half"></i>
            </span>
            <div className="fs-3 fw-bold">{stats?.pending || 0}</div>
            <div className="text-muted small">{t('dashboard.pendingRequests')}</div>
          </div></div>
        </div>

        <div className="col-sm-6 col-xl-3">
          <div className="card"><div className="card-body">
            <span className="d-inline-flex align-items-center justify-content-center rounded bg-success-subtle text-success" style={{ width: 48, height: 48 }}>
              <i className="fa-solid fa-check"></i>
            </span>
            <div className="fs-3 fw-bold">{stats?.approved || 0}</div>
            <div className="text-muted small">{t('dashboard.approved24h')}</div>
          </div></div>
        </div>

        <div className="col-sm-6 col-xl-3">
          <div className="card"><div className="card-body">
            <span className="d-inline-flex align-items-center justify-content-center rounded bg-danger-subtle text-danger" style={{ width: 48, height: 48 }}>
              <i className="fa-solid fa-xmark"></i>
            </span>
            <div className="fs-3 fw-bold">{stats?.denied || 0}</div>
            <div className="text-muted small">{t('dashboard.denied24h')}</div>
          </div></div>
        </div>

        <div className="col-sm-6 col-xl-3">
          <div className="card"><div className="card-body">
            <span className="d-inline-flex align-items-center justify-content-center rounded bg-info-subtle text-info" style={{ width: 48, height: 48 }}>
              <i className="fa-solid fa-globe"></i>
            </span>
            <div className="fs-3 fw-bold">{domains.length}</div>
            <div className="text-muted small">{t('dashboard.testDomains')}</div>
          </div></div>
        </div>
      </div>

      {/* Two Column Layout */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(500px, 1fr))', gap: '24px' }}>
        {/* Recent Pending Requests */}
        <div className="card">
          <div className="card-header d-flex justify-content-between align-items-center">
            <h3>
              <i className="fa-solid fa-hourglass-half"></i>
              {t('dashboard.recentPendingRequests')}
            </h3>
            <Link to="/pending" className="btn btn-outline-secondary btn-sm">
              {t('dashboard.viewAll')}
            </Link>
          </div>
          <div className="table-responsive">
            {recentRequests.length > 0 ? (
              <table className="table table-hover align-middle">
                <thead>
                  <tr>
                    <th>{t('dashboard.table.query')}</th>
                    <th>{t('dashboard.table.user')}</th>
                    <th>{t('dashboard.table.level')}</th>
                    <th>{t('dashboard.table.created')}</th>
                  </tr>
                </thead>
                <tbody>
                  {recentRequests.map((req) => (
                    <tr key={req.requestId}>
                      <td>
                        <span className="font-monospace small">{req.queryValue}</span>
                        <div className="text-muted small">{req.queryType}</div>
                      </td>
                      <td>{req.requestorUsername || req.requestorEmail || '-'}</td>
                      <td>
                        <span className="badge bg-primary-subtle text-primary">L{req.requestedAccessLevel}</span>
                      </td>
                      <td className="text-muted small">
                        {req.createdAt ? format(new Date(req.createdAt), 'MMM d, HH:mm') : '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div className="table-empty">
                <i className="fa-solid fa-hourglass-half"></i>
                <p>{t('dashboard.noPendingRequests')}</p>
              </div>
            )}
          </div>
        </div>

        {/* Recent Audit Logs */}
        <div className="card">
          <div className="card-header d-flex justify-content-between align-items-center">
            <h3>
              <i className="fa-solid fa-clipboard-list"></i>
              {t('dashboard.recentActivity')}
            </h3>
            <Link to="/logs" className="btn btn-outline-secondary btn-sm">
              {t('dashboard.viewAll')}
            </Link>
          </div>
          <div className="table-responsive">
            {recentLogs.length > 0 ? (
              <table className="table table-hover align-middle">
                <thead>
                  <tr>
                    <th>{t('dashboard.table.query')}</th>
                    <th>{t('dashboard.table.result')}</th>
                    <th>{t('dashboard.table.level')}</th>
                    <th>{t('dashboard.table.time')}</th>
                  </tr>
                </thead>
                <tbody>
                  {recentLogs.map((log) => (
                    <tr key={log.id}>
                      <td>
                        <span className="font-monospace small">{log.queryValue}</span>
                        <div className="text-muted small">{log.queryType}</div>
                      </td>
                      <td>
                        <span className={`badge ${log.result === 'SUCCESS' ? 'bg-success-subtle text-success' : 'bg-danger-subtle text-danger'}`}>
                          {log.result}
                        </span>
                      </td>
                      <td>
                        {log.accessLevelGranted !== null && (
                          <span className="badge bg-primary-subtle text-primary">L{log.accessLevelGranted}</span>
                        )}
                      </td>
                      <td className="text-muted small">
                        {log.requestTimestamp ? format(new Date(log.requestTimestamp), 'HH:mm:ss') : '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div className="table-empty">
                <i className="fa-solid fa-clipboard-list"></i>
                <p>{t('dashboard.noRecentActivity')}</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Available Test Domains */}
      <div className="card" style={{ marginTop: '24px' }}>
        <div className="card-header d-flex justify-content-between align-items-center">
          <h3>
            <i className="fa-solid fa-server"></i>
            {t('dashboard.availableTestDomains')}
          </h3>
          <Link to="/query" className="btn btn-primary btn-sm">
            <i className="fa-solid fa-eye"></i>
            {t('dashboard.testQuery')}
          </Link>
        </div>
        <div className="card-body">
          {domains.length > 0 ? (
            <div className="d-flex flex-wrap gap-1">
              {domains.map((domain) => (
                <span key={domain} className="badge bg-light text-dark border font-monospace">
                  {domain}
                </span>
              ))}
            </div>
          ) : (
            <p className="text-muted">{t('dashboard.noTestDomainsAvailable')}</p>
          )}
        </div>
      </div>

      {/* JAKE Compliance */}
      <div className="card" style={{ marginTop: '24px' }}>
        <div className="card-header d-flex justify-content-between align-items-center">
          <h3>
            <i className="fa-solid fa-shield-halved" style={{ marginRight: '8px' }}></i>
            {t('dashboard.jakeCompliance')}
          </h3>
          <button className="btn btn-primary btn-sm" onClick={loadJakeCompliance} disabled={jakeLoading}>
            {jakeLoading ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('common.loading')}</> : <><i className="fa-solid fa-arrows-rotate"></i> {jakeData ? t('common.refresh') : t('dashboard.load')}</>}
          </button>
        </div>
        <div className="card-body">
          {!jakeData && !jakeLoading && (
            <p className="text-muted">{t('dashboard.clickLoadHint')}</p>
          )}
          {jakeData && (
            <div>
              {/* Groups */}
              {jakeData.dataHolderGroups?.length > 0 && jakeData.dataHolderGroups.map((g, i) => (
                <div key={i} className="mb-3 p-3 rounded" style={{ backgroundColor: '#f8f9fa', border: '1px solid #e9ecef' }}>
                  <div className="d-flex align-items-center gap-2 mb-2">
                    <i className={`fa-solid fa-circle ${g.isActive ? 'text-success' : 'text-secondary'}`} style={{ fontSize: '8px' }}></i>
                    <strong>{g.groupName}</strong>
                    {g.isActive ? <span className="badge bg-success" style={{ fontSize: '10px' }}>{t('common.active')}</span> : <span className="badge bg-secondary" style={{ fontSize: '10px' }}>{t('common.inactive')}</span>}
                  </div>
                  {g.description && <p className="text-muted small mb-2">{g.description}</p>}

                  {g.templates?.length > 0 && g.templates.filter(tpl => tpl.isPublished !== false).map((tpl, ti) => (
                    <div key={ti} className="ms-3 mb-2 p-2 rounded" style={{ backgroundColor: 'white', border: '1px solid #dee2e6' }}>
                      <div className="d-flex align-items-center flex-wrap gap-2 mb-1">
                        <i className="fa-solid fa-file-lines text-primary"></i>
                        <strong>{tpl.name}</strong>
                        {tpl.defaultAccessLevel != null && <span className="badge bg-info" style={{ fontSize: '10px' }}>{t('dashboard.defaultAccessLevel', { level: tpl.defaultAccessLevel })}</span>}
                        {tpl.highestAccessLevel != null && <span className="badge bg-primary" style={{ fontSize: '10px' }}>{t('dashboard.maxAccessLevel', { level: tpl.highestAccessLevel })}</span>}
                        {tpl.supportsConfidential && <span className="badge bg-warning text-dark" style={{ fontSize: '9px' }}>{t('dashboard.confidential')}</span>}
                        {tpl.supportsExigent && <span className="badge bg-danger" style={{ fontSize: '9px' }}>{t('dashboard.exigent')}</span>}
                      </div>
                      {tpl.description && <div className="text-muted small mb-1">{tpl.description}</div>}

                      {tpl.requestTypes?.length > 0 && (
                        <div className="mt-2">
                          <small className="text-muted fw-semibold">{t('dashboard.requestTypes')}</small>
                          {tpl.requestTypes.map((rt, j) => (
                            <div key={j} className="d-flex align-items-center flex-wrap gap-2 mt-1 ms-2">
                              <span>{rt.name}</span>
                              <AccessLevelBadge level={rt.accessLevel} />
                              {rt.supportsConfidential && <span className="badge bg-warning text-dark" style={{ fontSize: '9px' }}>🔒</span>}
                              {rt.supportsExigent && <span className="badge bg-danger" style={{ fontSize: '9px' }}>⚡</span>}
                              {rt.requiresManualApproval && <span className="badge bg-secondary" style={{ fontSize: '9px' }}>{t('dashboard.manual')}</span>}
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              ))}

              {jakeData.subscriptions?.length > 0 && (
                <div className="mt-3">
                  <h6 className="text-muted">{t('dashboard.activeSubscriptions')}</h6>
                  {jakeData.subscriptions.map((s, i) => (
                    <div key={i} className="d-flex align-items-center flex-wrap gap-2 mb-1 p-2 rounded" style={{ backgroundColor: '#f0fff4', border: '1px solid #c6f6d5' }}>
                      <strong>{s.subscriptionName || '—'}</strong>
                      {s.templateName && <span className="text-muted small">({s.templateName})</span>}
                      {s.effectiveAccessLevel != null && <AccessLevelBadge level={s.effectiveAccessLevel} />}
                    </div>
                  ))}
                </div>
              )}

              {(!jakeData.dataHolderGroups || jakeData.dataHolderGroups.length === 0) && (!jakeData.subscriptions || jakeData.subscriptions.length === 0) && (
                <p className="text-muted">{t('dashboard.noGroupsOrSubscriptions')}</p>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;