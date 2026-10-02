/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import { format } from 'date-fns';
import { getAuditLogs } from '../services/api';
import Loading from '../components/Loading';
import Pagination from '../components/Pagination';
import { useT } from '../i18n';

const EVENT_TYPE_LABEL_KEYS = {
  RDAP_QUERY: 'auditLogs.eventTypes.rdapQuery', LOGIN: 'auditLogs.eventTypes.login', LOGOUT: 'auditLogs.eventTypes.logout', LOGIN_FAILED: 'auditLogs.eventTypes.loginFailed',
  FILE_UPLOAD: 'auditLogs.eventTypes.fileUpload', SECURITY_THREAT: 'auditLogs.eventTypes.securityThreat',
  RULE_CREATED: 'auditLogs.eventTypes.ruleCreated', RULE_UPDATED: 'auditLogs.eventTypes.ruleUpdated', RULE_DELETED: 'auditLogs.eventTypes.ruleDeleted',
  REQUEST_REVIEWED: 'auditLogs.eventTypes.requestReviewed', CONFIG_CHANGED: 'auditLogs.eventTypes.configChanged',
  USER_CREATED: 'auditLogs.eventTypes.userCreated', USER_UPDATED: 'auditLogs.eventTypes.userUpdated', USER_DELETED: 'auditLogs.eventTypes.userDeleted',
};

const EVENT_TYPE_COLORS = {
  RDAP_QUERY: { bg: '#eff6ff', color: '#2563eb', border: '#bfdbfe' },
  LOGIN: { bg: '#f0fdf4', color: '#16a34a', border: '#bbf7d0' },
  LOGOUT: { bg: '#f9fafb', color: '#6b7280', border: '#d1d5db' },
  LOGIN_FAILED: { bg: '#fef2f2', color: '#dc2626', border: '#fecaca' },
  FILE_UPLOAD: { bg: '#faf5ff', color: '#7c3aed', border: '#ddd6fe' },
  SECURITY_THREAT: { bg: '#fef2f2', color: '#dc2626', border: '#fecaca' },
  REQUEST_REVIEWED: { bg: '#eff6ff', color: '#2563eb', border: '#bfdbfe' },
  CONFIG_CHANGED: { bg: '#fffbeb', color: '#d97706', border: '#fde68a' },
};

const SEVERITY_CONFIG = {
  INFO: null, LOW: { bg: '#f0fdf4', color: '#16a34a', border: '#bbf7d0' },
  MEDIUM: { bg: '#fffbeb', color: '#d97706', border: '#fde68a' },
  HIGH: { bg: '#fef2f2', color: '#dc2626', border: '#fecaca' },
  CRITICAL: { bg: '#7f1d1d', color: '#fecaca', border: '#991b1b' },
};

const RESULT_LABEL_KEYS = {
  SUCCESS: 'auditLogs.results.success', DENIED: 'auditLogs.results.denied',
  ERROR: 'common.error', PENDING: 'auditLogs.results.pending',
};

const SEVERITY_LABEL_KEYS = {
  LOW: 'auditLogs.severities.low', MEDIUM: 'auditLogs.severities.medium',
  HIGH: 'auditLogs.severities.high', CRITICAL: 'auditLogs.severities.critical',
};

const THREAT_TYPE_LABEL_KEYS = {
  MALICIOUS_FILE: 'auditLogs.threatTypes.maliciousFile', DISALLOWED_FILE_TYPE: 'auditLogs.threatTypes.disallowedFileType',
  FILE_EXTENSION_MISMATCH: 'auditLogs.threatTypes.mimeMismatch', OVERSIZED_FILE: 'auditLogs.threatTypes.oversizedFile',
  EMBEDDED_SCRIPT: 'auditLogs.threatTypes.embeddedScript', PATH_TRAVERSAL: 'auditLogs.threatTypes.pathTraversal',
  POLYGLOT_FILE: 'auditLogs.threatTypes.polyglotFile', SUSPICIOUS_CONTENT: 'auditLogs.threatTypes.suspiciousContent',
};

const getFileTypeIcon = (ft) => {
  if (!ft) return null;
  const s = { fontSize: '12px' };
  switch (ft.toUpperCase()) {
    case 'PDF': return <i className="fa-solid fa-file-pdf" style={{ ...s, color: '#ef4444' }} />;
    case 'DOCX': return <i className="fa-solid fa-file-word" style={{ ...s, color: '#2563eb' }} />;
    case 'XLSX': return <i className="fa-solid fa-file-excel" style={{ ...s, color: '#16a34a' }} />;
    case 'JPEG': case 'JPG': case 'PNG': return <i className="fa-solid fa-file-image" style={{ ...s, color: '#8b5cf6' }} />;
    case 'TXT': return <i className="fa-solid fa-file-lines" style={{ ...s, color: '#6b7280' }} />;
    default: return <i className="fa-solid fa-paperclip" style={s} />;
  }
};

const AuditLogs = () => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(50);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [expandedLog, setExpandedLog] = useState(null);
  const [eventTypeFilter, setEventTypeFilter] = useState('');
  const [severityFilter, setSeverityFilter] = useState('');
  const { t } = useT();

  useEffect(() => { loadLogs(); }, [page, pageSize, eventTypeFilter, severityFilter]);

  const loadLogs = async () => {
    setLoading(true);
    try {
      const response = await getAuditLogs(page, pageSize, eventTypeFilter || null, severityFilter || null);
      setLogs(response.data.content || []);
      setTotalPages(response.data.totalPages || 0);
      setTotalElements(response.data.totalElements || 0);
    } catch (error) {
      console.error('Failed to load audit logs:', error);
    } finally {
      setLoading(false);
    }
  };

  const badge = (result) => {
    const cls = { SUCCESS: 'bg-success-subtle text-success', DENIED: 'bg-danger-subtle text-danger', ERROR: 'bg-danger-subtle text-danger', PENDING: 'bg-warning-subtle text-warning' };
    return <span className={`badge ${cls[result] || 'bg-info-subtle text-info'}`}>{RESULT_LABEL_KEYS[result] ? t(RESULT_LABEL_KEYS[result]) : result}</span>;
  };

  const evtBadge = (et) => {
    const c = EVENT_TYPE_COLORS[et] || EVENT_TYPE_COLORS.RDAP_QUERY;
    return (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', padding: '2px 8px', borderRadius: '6px', fontSize: '10px', fontWeight: 700, background: c.bg, color: c.color, border: `1px solid ${c.border}`, letterSpacing: '0.3px', whiteSpace: 'nowrap' }}>
        {et === 'SECURITY_THREAT' && <i className="fa-solid fa-triangle-exclamation" style={{ fontSize: '9px' }} />}
        {et === 'FILE_UPLOAD' && <i className="fa-solid fa-paperclip" style={{ fontSize: '9px' }} />}
        {(EVENT_TYPE_LABEL_KEYS[et] && t(EVENT_TYPE_LABEL_KEYS[et])) || et || t('auditLogs.eventTypes.rdapQuery')}
      </span>
    );
  };

  const sevBadge = (sev) => {
    if (!sev || sev === 'INFO') return null;
    const c = SEVERITY_CONFIG[sev];
    if (!c) return null;
    return (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px', padding: '1px 6px', borderRadius: '4px', fontSize: '9px', fontWeight: 700, background: c.bg, color: c.color, border: `1px solid ${c.border}` }}>
        {sev === 'CRITICAL' && <i className="fa-solid fa-triangle-exclamation" style={{ fontSize: '8px' }} />}{SEVERITY_LABEL_KEYS[sev] ? t(SEVERITY_LABEL_KEYS[sev]) : sev}
      </span>
    );
  };

  const quickFilters = [
    { label: t('common.all'), et: '', sev: '' },
    { label: t('auditLogs.quickFilters.rdapQueries'), et: 'RDAP_QUERY', sev: '' },
    { label: t('auditLogs.quickFilters.securityThreats'), et: 'SECURITY_THREAT', sev: '' },
    { label: t('auditLogs.quickFilters.fileUploads'), et: 'FILE_UPLOAD', sev: '' },
    { label: t('auditLogs.quickFilters.logins'), et: 'LOGIN', sev: '' },
    { label: t('auditLogs.quickFilters.failedLogins'), et: 'LOGIN_FAILED', sev: '' },
  ];

  return (
    <div>
      <div className="mb-4">
        <h2 className="h3 fw-bold mb-1">{t('auditLogs.title')}</h2>
        <p className="text-muted mb-0">{t('auditLogs.subtitle')}</p>
      </div>

      <div className="card" style={{ marginBottom: '16px' }}>
        <div style={{ display: 'flex', gap: '8px', padding: '12px 16px', flexWrap: 'wrap', alignItems: 'center' }}>
          <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-tertiary)', marginRight: '4px' }}>{t('auditLogs.filter')}:</span>
          {quickFilters.map((f) => (
            <button key={f.label} className={`btn btn-sm ${eventTypeFilter === f.et && severityFilter === f.sev ? 'btn btn-primary' : 'btn btn-secondary'}`}
              onClick={() => { setEventTypeFilter(f.et); setSeverityFilter(f.sev); setPage(0); }}>
              {f.label}
            </button>
          ))}
          <div style={{ borderLeft: '1px solid var(--border-primary)', height: '20px', margin: '0 4px' }} />
          <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-tertiary)' }}>{t('auditLogs.severity')}:</span>
          {['HIGH', 'CRITICAL'].map((s) => (
            <button key={s} className={`btn btn-sm ${severityFilter === s ? 'btn btn-danger' : 'btn btn-secondary'}`}
              onClick={() => { setSeverityFilter(severityFilter === s ? '' : s); setEventTypeFilter(''); setPage(0); }}>
              {s === 'CRITICAL' && <i className="fa-solid fa-triangle-exclamation" style={{ fontSize: '10px' }} />} {SEVERITY_LABEL_KEYS[s] ? t(SEVERITY_LABEL_KEYS[s]) : s}
            </button>
          ))}
        </div>
      </div>

      <div className="card">
        <div className="card-header d-flex justify-content-between align-items-center">
          <h3><i className="fa-solid fa-clipboard-list" /> {t('auditLogs.activityLog')}</h3>
          <button className="btn btn-secondary btn-sm" onClick={loadLogs}><i className="fa-solid fa-arrows-rotate" /> {t('common.refresh')}</button>
        </div>
        {loading ? <Loading message={t('auditLogs.loading')} /> : (
          <>
            <div className="table-responsive">
              {logs.length > 0 ? (
                <table className="table table-hover align-middle">
                  <thead>
                    <tr>
                      <th>{t('auditLogs.table.id')}</th><th>{t('auditLogs.table.event')}</th><th>{t('auditLogs.table.queryDetails')}</th><th>{t('auditLogs.table.requestor')}</th>
                      <th>{t('auditLogs.table.result')}</th><th>{t('auditLogs.table.accessLevel')}</th><th>{t('auditLogs.table.responseTime')}</th><th>{t('auditLogs.table.timestamp')}</th><th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {logs.map((log) => {
                      const isThreat = log.eventType === 'SECURITY_THREAT';
                      const isFile = log.eventType === 'FILE_UPLOAD' || isThreat;
                      return (
                        <React.Fragment key={log.id}>
                          <tr onClick={() => setExpandedLog(expandedLog === log.id ? null : log.id)}
                            style={{ cursor: 'pointer', background: isThreat ? 'rgba(239,68,68,0.04)' : 'transparent' }}>
                            <td><span className="font-monospace text-muted">#{log.id}</span></td>
                            <td>
                              <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
                                {evtBadge(log.eventType)}
                                {sevBadge(log.severity)}
                              </div>
                            </td>
                            <td>
                              <span className="font-monospace">{log.queryValue}</span>
                              <div className="text-muted small">
                                {log.queryType}
                                {log.originalFilename && (
                                  <span style={{ marginLeft: '6px' }}>
                                    {getFileTypeIcon(log.fileType)} {log.originalFilename}
                                  </span>
                                )}
                              </div>
                            </td>
                            <td>
                              <div>{log.requestorUsername || t('auditLogs.anonymous')}</div>
                              <div className="text-muted small">{log.requestorIp}</div>
                            </td>
                            <td>{badge(log.result)}</td>
                            <td>
                              {log.accessLevelGranted != null ? (
                                <span className="badge bg-primary-subtle text-primary">L{log.accessLevelGranted}</span>
                              ) : <span className="text-muted">-</span>}
                            </td>
                            <td>
                              {log.responseTimeMs != null ? (
                                <span className="font-monospace small">{log.responseTimeMs}ms</span>
                              ) : <span className="text-muted">-</span>}
                            </td>
                            <td className="text-muted small">
                              {log.requestTimestamp ? format(new Date(log.requestTimestamp), 'MMM d, yyyy HH:mm:ss') : '-'}
                            </td>
                            <td>
                              <button className="btn btn-outline-secondary btn-sm" style={{ transform: expandedLog === log.id ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }}>
                                <i className="fa-solid fa-chevron-down" />
                              </button>
                            </td>
                          </tr>
                          {expandedLog === log.id && (
                            <tr>
                              <td colSpan="9" style={{ padding: 0 }}>
                                <div style={{ padding: '20px', background: isThreat ? 'rgba(239,68,68,0.03)' : 'var(--bg-tertiary)', borderTop: '1px solid var(--border-primary)', borderBottom: '1px solid var(--border-primary)' }}>
                                  <div className="row g-3">
                                    <div className="col-md-6">
                                      <div className="text-muted text-uppercase small fw-semibold mb-1">{t('auditLogs.detail.eventType')}</div>
                                      <div>{evtBadge(log.eventType)}</div>
                                    </div>
                                    {sevBadge(log.severity) && (
                                      <div className="col-md-6">
                                        <div className="text-muted text-uppercase small fw-semibold mb-1">{t('auditLogs.severity')}</div>
                                        <div>{sevBadge(log.severity)}</div>
                                      </div>
                                    )}
                                    <div className="col-md-6">
                                      <div className="text-muted text-uppercase small fw-semibold mb-1">{t('auditLogs.detail.queryType')}</div>
                                      <div>{log.queryType}</div>
                                    </div>
                                    <div className="col-md-6">
                                      <div className="text-muted text-uppercase small fw-semibold mb-1">{t('auditLogs.detail.queryValue')}</div>
                                      <div className="font-monospace">{log.queryValue}</div>
                                    </div>
                                    <div className="col-md-6">
                                      <div className="text-muted text-uppercase small fw-semibold mb-1">{t('auditLogs.table.requestor')}</div>
                                      <div>{log.requestorUsername || t('auditLogs.anonymous')}</div>
                                    </div>
                                    <div className="col-md-6">
                                      <div className="text-muted text-uppercase small fw-semibold mb-1">{t('auditLogs.detail.ipAddress')}</div>
                                      <div className="font-monospace">{log.requestorIp || '-'}</div>
                                    </div>
                                    {isThreat && (
                                      <div className="col-12" style={{ background: 'rgba(239,68,68,0.06)', padding: '12px', borderRadius: '8px', border: '1px solid rgba(239,68,68,0.15)' }}>
                                        <div className="text-muted text-uppercase small fw-semibold mb-1" style={{ color: '#dc2626' }}>
                                          <i className="fa-solid fa-triangle-exclamation" style={{ fontSize: '12px' }} /> {t('auditLogs.detail.threatDetails')}
                                        </div>
                                        <div style={{ marginTop: '4px' }}>
                                          <div style={{ fontWeight: 600, marginBottom: '4px' }}>
                                            {(THREAT_TYPE_LABEL_KEYS[log.threatType] && t(THREAT_TYPE_LABEL_KEYS[log.threatType])) || log.threatType}
                                          </div>
                                          <div style={{ fontSize: '13px', color: 'var(--text-secondary)' }}>{log.resultMessage}</div>
                                        </div>
                                      </div>
                                    )}
                                    {isFile && log.originalFilename && (
                                      <>
                                        <div className="col-md-6">
                                          <div className="text-muted text-uppercase small fw-semibold mb-1">{t('auditLogs.detail.filename')}</div>
                                          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                            {getFileTypeIcon(log.fileType)} {log.originalFilename}
                                            {log.fileType && <span style={{ fontSize: '9px', fontWeight: 700, padding: '1px 5px', borderRadius: '3px', background: 'var(--bg-tertiary)', border: '1px solid var(--border-primary)' }}>{log.fileType}</span>}
                                          </div>
                                        </div>
                                        <div className="col-md-6">
                                          <div className="text-muted text-uppercase small fw-semibold mb-1">{t('auditLogs.detail.fileSize')}</div>
                                          <div>{log.fileSizeBytes != null ? log.fileSizeBytes < 1048576 ? (log.fileSizeBytes / 1024).toFixed(1) + ' KB' : (log.fileSizeBytes / 1048576).toFixed(1) + ' MB' : '-'}</div>
                                        </div>
                                        {log.detectedMimeType && <div className="col-md-6"><div className="text-muted text-uppercase small fw-semibold mb-1">{t('auditLogs.detail.mimeType')}</div><div className="font-monospace" style={{ fontSize: '12px' }}>{log.detectedMimeType}</div></div>}
                                        {log.fileHash && <div className="col-md-6"><div className="text-muted text-uppercase small fw-semibold mb-1">{t('auditLogs.detail.sha256')}</div><div><code style={{ fontSize: '10px', wordBreak: 'break-all' }}>{log.fileHash}</code></div></div>}
                                      </>
                                    )}
                                    {log.userAgent && <div className="col-12"><div className="text-muted text-uppercase small fw-semibold mb-1">{t('auditLogs.detail.userAgent')}</div><div style={{ fontSize: '11px', wordBreak: 'break-all' }}>{log.userAgent}</div></div>}
                                    <div className="col-12">
                                      <div className="text-muted text-uppercase small fw-semibold mb-1">{t('auditLogs.detail.agreements')}</div>
                                      <div>
                                        {log.agreementNames?.length > 0 ? (
                                          <div className="d-flex flex-wrap gap-1">{log.agreementNames.map((n) => <span key={n} className="badge bg-light text-dark border">{n}</span>)}</div>
                                        ) : <span className="text-muted">{t('common.none')}</span>}
                                      </div>
                                    </div>
                                    {log.resultMessage && !isThreat && <div className="col-12"><div className="text-muted text-uppercase small fw-semibold mb-1">{t('auditLogs.detail.resultMessage')}</div><div>{log.resultMessage}</div></div>}
                                  </div>
                                </div>
                              </td>
                            </tr>
                          )}
                        </React.Fragment>
                      );
                    })}
                  </tbody>
                </table>
              ) : (
                <div className="table-empty"><i className="fa-solid fa-clipboard-list" /><p>{eventTypeFilter ? t('auditLogs.noLogsFoundFor', { type: (EVENT_TYPE_LABEL_KEYS[eventTypeFilter] && t(EVENT_TYPE_LABEL_KEYS[eventTypeFilter])) || eventTypeFilter }) : t('auditLogs.noLogsFound')}</p></div>
              )}
            </div>
            {logs.length > 0 && (
              <div className="card-footer">
                <Pagination
                  page={page + 1}
                  pageSize={pageSize}
                  totalItems={totalElements}
                  onPageChange={(p) => setPage(p - 1)}
                  onPageSizeChange={(size) => { setPageSize(size); setPage(0); }}
                  itemLabel={t('auditLogs.itemLabel')}
                />
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default AuditLogs;
