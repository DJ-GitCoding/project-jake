/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback, useContext, useRef } from 'react';
import {
  getSubscriptions, getSubscription, approveSubscription, denySubscription,
  startTest, activateSubscription, suspendSubscription, reactivateSubscription,
  updateSubscriptionIntrospectionUrl, recordTestResult, runSubscriptionTest,
  revealSubscriptionCredentials, regenerateSubscriptionCredentials, registerSubscriptionPublicKey,
} from '../services/api';
import { GroupContext } from '../context/GroupContext';
import Pagination from '../components/Pagination';
import SubscriptionTerms from '../components/SubscriptionTerms';
import { useT } from '../i18n';
import toast from 'react-hot-toast';

const STATUS_BADGES = {
  PENDING: 'warning', APPROVED: 'info', TESTING: 'primary', ACTIVE: 'success',
  DENIED: 'danger', SUSPENDED: 'danger', EXPIRED: 'secondary', CANCELLED: 'secondary',
};

const StatusBadge = ({ status }) => (
  <span className={`badge bg-${STATUS_BADGES[status] || 'secondary'}`}>{status}</span>
);

const formatDate = (d) => d ? new Date(d).toLocaleString() : '—';

/* ──────────────────────────────────────────────────────────────
   WORKFLOW STEPPER — shows the current position in the lifecycle
   ────────────────────────────────────────────────────────────── */
const WORKFLOW_STEPS = [
  { key: 'PENDING',  label: 'Pending',  icon: 'fa-clock' },
  { key: 'APPROVED', label: 'Approved', icon: 'fa-check' },
  { key: 'TESTING',  label: 'Testing',  icon: 'fa-flask' },
  { key: 'ACTIVE',   label: 'Active',   icon: 'fa-circle-check' },
];

const TERMINAL_STATUSES = ['DENIED', 'SUSPENDED', 'EXPIRED', 'CANCELLED'];

const WorkflowStepper = ({ status, testResult }) => {
  const { t } = useT();
  const isTerminal = TERMINAL_STATUSES.includes(status);
  const stepIndex = WORKFLOW_STEPS.findIndex(s => s.key === status);
  const stepLabels = {
    PENDING: t('subscriptions.workflow.pending'),
    APPROVED: t('subscriptions.workflow.approved'),
    TESTING: t('subscriptions.workflow.testing'),
    ACTIVE: t('common.active'),
  };

  return (
    <div className="d-flex align-items-center mb-4 position-relative" style={{ gap: 0 }}>
      {WORKFLOW_STEPS.map((step, i) => {
        const isCurrent = step.key === status;
        const isPast = !isTerminal && stepIndex > i;

        let color = '#dee2e6';
        let textColor = '#adb5bd';
        let bg = 'transparent';
        if (isPast) { color = '#198754'; textColor = '#198754'; bg = '#d1e7dd'; }
        if (isCurrent && !isTerminal) {
          color = step.key === 'ACTIVE' ? '#198754' : '#0d6efd';
          textColor = color;
          bg = step.key === 'ACTIVE' ? '#d1e7dd' : '#cfe2ff';
        }

        return (
          <React.Fragment key={step.key}>
            {i > 0 && (
              <div style={{
                flex: 1, height: 3, borderRadius: 2,
                background: isPast || (isCurrent && i > 0) ? color : '#dee2e6',
                transition: 'background .3s',
              }} />
            )}
            <div className="d-flex flex-column align-items-center" style={{ minWidth: 80 }}>
              <div style={{
                width: 36, height: 36, borderRadius: '50%',
                border: `2.5px solid ${color}`,
                background: bg,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                transition: 'all .3s',
                boxShadow: isCurrent ? `0 0 0 4px ${color}22` : 'none',
              }}>
                <i className={`fa-solid ${isPast ? 'fa-check' : step.icon}`} style={{ color, fontSize: 14 }} />
              </div>
              <span className="mt-1" style={{
                fontSize: 11, fontWeight: isCurrent ? 600 : 400,
                color: textColor,
              }}>{stepLabels[step.key]}</span>
              {isCurrent && step.key === 'TESTING' && testResult && (
                <span className={`badge bg-${testResult === 'PASSED' ? 'success' : 'danger'} mt-1`} style={{ fontSize: 9 }}>
                  {testResult}
                </span>
              )}
            </div>
          </React.Fragment>
        );
      })}

      {isTerminal && (
        <div className="ms-3 d-flex flex-column align-items-center" style={{ minWidth: 80 }}>
          <div style={{
            width: 36, height: 36, borderRadius: '50%',
            border: '2.5px solid #dc3545', background: '#f8d7da',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <i className="fa-solid fa-xmark" style={{ color: '#dc3545', fontSize: 14 }} />
          </div>
          <span className="mt-1" style={{ fontSize: 11, fontWeight: 600, color: '#dc3545' }}>{status}</span>
        </div>
      )}
    </div>
  );
};

/* ──────────────────────────────────────────────────────────────
   TEST RESULT PANEL — live feedback during testing phase
   ────────────────────────────────────────────────────────────── */
/* One RDAP case the data holder ran: the query it made and how each check scored. */
const RdapCaseRow = ({ entry }) => {
  const { t } = useT();
  const outcome = String(entry.result || '').toUpperCase();
  const tone = outcome === 'PASSED' ? 'success'
    : outcome === 'SKIPPED' ? 'secondary'
    : outcome === 'ERROR' ? 'warning' : 'danger';
  const query = [entry.queryType, entry.queryValue].filter(Boolean).join(' ');

  return (
    <li className="list-group-item bg-transparent px-0 py-2">
      <div className="d-flex align-items-center gap-2 flex-wrap">
        <span className={`badge bg-${tone}`}>{outcome || '—'}</span>
        {query && <code className="small">{query}</code>}
        {entry.requestTypeName && (
          <span className="badge bg-light text-dark border">{entry.requestTypeName}</span>
        )}
        {entry.resolvedAccessLevel != null && (
          <span className="badge bg-light text-dark border">
            {t('subscriptions.testPanel.levelBadge', { level: entry.resolvedAccessLevel })}
          </span>
        )}
        {entry.durationMs != null && (
          <span className="text-muted small">{t('subscriptions.testPanel.durationMs', { ms: entry.durationMs })}</span>
        )}
      </div>
      {entry.label && <div className="small text-muted">{entry.label}</div>}
      {entry.message && <div className="small">{entry.message}</div>}
      {(entry.checks || []).length > 0 && (
        <ul className="list-unstyled mb-0 mt-1 ms-2">
          {entry.checks.map((c, i) => (
            <li key={i} className="small d-flex align-items-start gap-2">
              <i className={`fa-solid ${c.passed ? 'fa-check text-success' : 'fa-xmark text-danger'} mt-1`} />
              <div>
                <span className={c.passed ? '' : 'fw-semibold'}>{c.name}</span>
                {c.category && <span className="text-muted ms-2">{c.category}</span>}
                {!c.passed && c.message && <div>{c.message}</div>}
                {!c.passed && (c.expected != null || c.actual != null) && (
                  <div className="text-muted font-monospace">
                    {t('subscriptions.testPanel.expectedActual', {
                      expected: c.expected ?? '—', actual: c.actual ?? '—',
                    })}
                  </div>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </li>
  );
};

/* What the last run did: who answered, what was queried, and what failed where. */
const TestDiagnostics = ({ diagnostics }) => {
  const { t } = useT();
  const [open, setOpen] = useState(false);
  if (!diagnostics) return null;

  const rdapResults = diagnostics.rdapResults || [];
  const attempts = diagnostics.attempts || [];
  const summary = diagnostics.summary || {};
  const failedChecks = (diagnostics.checks || []).filter(c => !c.passed);

  return (
    <div className="mt-3">
      <button type="button" className="btn btn-link btn-sm px-0" onClick={() => setOpen(o => !o)}>
        <i className={`fa-solid fa-chevron-${open ? 'down' : 'right'} me-1`} />
        {t('subscriptions.testPanel.whatWasTested')}
      </button>

      {open && (
        <div className="mt-1 p-3 rounded bg-white border">
          <div className="small text-muted mb-2">
            {diagnostics.dataHolder?.name || diagnostics.dataHolder?.dataholderId ? (
              <>
                <i className="fa-solid fa-server me-1" />
                {t('subscriptions.testPanel.answeredBy', {
                  name: diagnostics.dataHolder.name || diagnostics.dataHolder.dataholderId,
                })}
              </>
            ) : (
              <>
                <i className="fa-solid fa-triangle-exclamation me-1" />
                {t('subscriptions.testPanel.noDataHolderAnswered')}
              </>
            )}
            {summary.totalRdapTests != null && (
              <span className="ms-2">
                {t('subscriptions.testPanel.rdapSummary', {
                  passed: summary.passedRdapTests ?? 0, total: summary.totalRdapTests,
                })}
              </span>
            )}
          </div>

          {rdapResults.length > 0 ? (
            <ul className="list-group list-group-flush">
              {rdapResults.map((entry, i) => <RdapCaseRow entry={entry} key={i} />)}
            </ul>
          ) : (
            <div className="small fst-italic text-muted">{t('subscriptions.testPanel.noQueriesRun')}</div>
          )}

          {/* Why nobody could answer — the group admin's own record of what it tried. */}
          {rdapResults.length === 0 && attempts.length > 0 && (
            <ul className="small font-monospace text-muted mt-2 mb-0 ps-3">
              {attempts.map((line, i) => <li key={i}>{line}</li>)}
            </ul>
          )}

          {failedChecks.length > 0 && (
            <div className="mt-3">
              <div className="small fw-semibold mb-1">{t('subscriptions.testPanel.failedChecks')}</div>
              <ul className="small mb-0 ps-3">
                {failedChecks.map((c, i) => (
                  <li key={i}>
                    {c.name}
                    {c.detail && <div className="text-muted font-monospace">{c.detail}</div>}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

const TestResultPanel = ({ detail, onRunTest, running, lastRun }) => {
  const { t } = useT();
  const testing = detail.status === 'TESTING';
  if (!testing && !detail.testResult && !detail.testDiagnostics) return null;

  const hasResult = !!detail.testResult;
  const passed = detail.testResult === 'PASSED';
  const failed = detail.testResult === 'FAILED';
  /* Prefer the run just made; otherwise what the last one recorded on the subscription. */
  const diagnostics = lastRun?.diagnostics || detail.testDiagnostics || null;

  return (
    <div className="card border-primary mb-3">
      <div className="card-header bg-primary bg-opacity-10 d-flex align-items-center justify-content-between">
        <div className="d-flex align-items-center gap-2">
          <i className="fa-solid fa-flask text-primary" />
          <strong className="text-primary">{t('subscriptions.testPanel.title')}</strong>
        </div>
        {!hasResult && testing && (
          <span className="badge bg-warning text-dark">
            <i className="fa-solid fa-circle-exclamation me-1" />{t('subscriptions.testPanel.awaiting')}
          </span>
        )}
        {hasResult && passed && (
          <span className="badge bg-success">
            <i className="fa-solid fa-circle-check me-1" />{t('subscriptions.testPanel.testsPassed')}
          </span>
        )}
        {hasResult && failed && (
          <span className="badge bg-danger">
            <i className="fa-solid fa-circle-xmark me-1" />{t('subscriptions.testPanel.testsFailed')}
          </span>
        )}
      </div>
      <div className="card-body">
        {/* Guidance text */}
        <div className="mb-3 p-3 rounded" style={{ background: '#f8f9fa', fontSize: 13 }}>
          <i className="fa-solid fa-info-circle text-primary me-2" />
          {!hasResult ? (
            <>
              {t('subscriptions.testPanel.guidanceAwaiting.p1')} <strong>{t('subscriptions.testPanel.testing')}</strong> {t('subscriptions.testPanel.guidanceAwaiting.p2')} <strong>{t('subscriptions.testPanel.runTest')}</strong> {t('subscriptions.testPanel.guidanceAwaiting.p3')}
            </>
          ) : passed ? (
            <>
              {t('subscriptions.testPanel.guidancePassed.p1')} <strong>{t('subscriptions.testPanel.activated')}</strong>{t('subscriptions.testPanel.guidancePassed.p2')}
            </>
          ) : testing ? (
            <>
              {t('subscriptions.testPanel.guidanceFailed')}
            </>
          ) : (
            <>
              {t('subscriptions.testPanel.guidanceRecorded')}
            </>
          )}
        </div>

        {/* Test execution controls */}
        {testing && <div className="d-flex gap-2 flex-wrap mb-3">
          <button
            className="btn btn-primary"
            onClick={() => onRunTest()}
            disabled={running}
          >
            {running ? (
              <><span className="spinner-border spinner-border-sm me-1" />{t('subscriptions.testPanel.runningTests')}</>
            ) : (
              <><i className="fa-solid fa-play me-1" />{hasResult ? t('subscriptions.testPanel.rerunTests') : t('subscriptions.testPanel.runTest')}</>
            )}
          </button>

          {!hasResult && (
            <>
              <button
                className="btn btn-outline-success btn-sm"
                onClick={() => onRunTest('PASSED')}
                disabled={running}
                title={t('subscriptions.testPanel.markPassedTitle')}
              >
                <i className="fa-solid fa-check me-1" />{t('subscriptions.testPanel.markPassed')}
              </button>
              <button
                className="btn btn-outline-danger btn-sm"
                onClick={() => onRunTest('FAILED')}
                disabled={running}
                title={t('subscriptions.testPanel.markFailedTitle')}
              >
                <i className="fa-solid fa-xmark me-1" />{t('subscriptions.testPanel.markFailed')}
              </button>
            </>
          )}
        </div>}

        {/* Results display */}
        {hasResult && (
          <div className={`alert alert-${passed ? 'success' : 'danger'} d-flex align-items-start gap-3 mb-0`}>
            <i className={`fa-solid ${passed ? 'fa-circle-check' : 'fa-circle-xmark'} mt-1`} style={{ fontSize: 20 }} />
            <div style={{ flex: 1 }}>
              <strong>{passed ? t('subscriptions.testPanel.allPassed') : t('subscriptions.testPanel.failed')}</strong>
              {detail.testDetails && <div className="mt-1 small">{detail.testDetails}</div>}

              {/* Per-check breakdown from the most recent run in this session */}
              {lastRun?.checks?.length > 0 && (
                <ul className="list-group list-group-flush mt-2">
                  {lastRun.checks.map((c, i) => (
                    <li key={i} className="list-group-item bg-transparent px-0 py-1 border-0">
                      <div className="d-flex align-items-start gap-2">
                        <i className={`fa-solid ${c.passed ? 'fa-check text-success' : 'fa-xmark text-danger'} mt-1`} />
                        <div className="small">
                          <div className={c.passed ? '' : 'fw-semibold'}>{c.name}</div>
                          {!c.passed && c.message && <div>{c.message}</div>}
                          {!c.passed && c.detail && <div className="text-muted font-monospace">{c.detail}</div>}
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
              )}

              <TestDiagnostics diagnostics={diagnostics} />

              <div className="mt-2 d-flex gap-3 text-muted small">
                {detail.testStartedAt && <span><i className="fa-regular fa-clock me-1" />{t('subscriptions.detail.started')} {formatDate(detail.testStartedAt)}</span>}
                {detail.testCompletedAt && <span><i className="fa-solid fa-flag-checkered me-1" />{t('subscriptions.detail.completed')} {formatDate(detail.testCompletedAt)}</span>}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

/* ──────────────────────────────────────────────────────────────
   CONTEXT ACTIONS — always visible based on status
   ────────────────────────────────────────────────────────────── */
const getActions = (sub, t) => {
  const a = [];
  switch (sub.status) {
    case 'PENDING':
      a.push(
        { key: 'approve', label: t('subscriptions.actions.approve'), cls: 'success', icon: 'fa-check' },
        { key: 'deny', label: t('subscriptions.actions.deny'), cls: 'danger', icon: 'fa-xmark' },
      );
      break;
    case 'APPROVED':
      a.push({ key: 'start-test', label: t('subscriptions.actions.startTest'), cls: 'primary', icon: 'fa-flask' });
      break;
    case 'TESTING':
      a.push({ key: 'run-test', label: t('subscriptions.actions.runTest'), cls: 'info', icon: 'fa-play' });
      break;
    case 'ACTIVE':
      a.push({ key: 'suspend', label: t('subscriptions.actions.suspend'), cls: 'danger', icon: 'fa-pause' });
      break;
    case 'SUSPENDED':
      a.push({ key: 'reactivate', label: t('subscriptions.actions.reactivate'), cls: 'success', icon: 'fa-play' });
      break;
    default:
      break;
  }
  return a;
};

/* ──────────────────────────────────────────────────────────────
   MAIN COMPONENT
   ────────────────────────────────────────────────────────────── */
const Subscriptions = () => {
  const { t } = useT();
  const { selectedGroupId, isAllMode, isMaster } = useContext(GroupContext);
  const [subs, setSubs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('ALL');
  // Server-side pagination state
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [totalItems, setTotalItems] = useState(0);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [detail, setDetail] = useState(null);
  const [actionModal, setActionModal] = useState(null);
  const [notes, setNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [runningTest, setRunningTest] = useState(false);
  // Per-check breakdown of the most recent run; the subscription itself only stores the summary.
  const [lastRun, setLastRun] = useState(null);

  // Introspection URL editing state
  const [editingIntrospectionUrl, setEditingIntrospectionUrl] = useState(false);
  const [introspectionUrlDraft, setIntrospectionUrlDraft] = useState('');
  const [savingUrl, setSavingUrl] = useState(false);

  // Credentials the requestor manager uses for this subscription; the secret shows once.
  const [credentials, setCredentials] = useState(null);
  const [credentialsBusy, setCredentialsBusy] = useState(false);
  const [secretVisible, setSecretVisible] = useState(false);
  // The requestor manager's signing key, pasted in by an admin.
  const [publicKeyDraft, setPublicKeyDraft] = useState('');
  const [savingKey, setSavingKey] = useState(false);

  // Polling for live updates during TESTING
  const pollingRef = useRef(null);
  // Stable ref to load so polling callback doesn't go stale
  const loadRef = useRef(null);

  // Debounce the search box (300ms) before hitting the server.
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(t);
  }, [search]);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      // DH-group scoping + status tab are applied server-side.
      const res = await getSubscriptions({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        dataHolderGroupId: isAllMode ? undefined : selectedGroupId,
        status: filter === 'ALL' ? undefined : filter,
        sortBy: 'createdAt',
        sortDir: 'desc',
      });
      setSubs(res.content || []);
      setTotalItems(res.totalElements || 0);
    }
    catch { toast.error(t('subscriptions.toast.loadFailed')); }
    finally { setLoading(false); }
  }, [page, pageSize, debouncedSearch, filter, isAllMode, selectedGroupId]);

  // Keep loadRef current
  useEffect(() => { loadRef.current = load; }, [load]);

  useEffect(() => { load(); }, [load]);

  // Reset to the first page when a filter or the search term changes.
  useEffect(() => { setPage(1); }, [debouncedSearch, filter, isAllMode, selectedGroupId]);

  // Live polling when detail is open and in TESTING status
  const detailId = detail?.id;
  const detailStatus = detail?.status;

  useEffect(() => {
    if (pollingRef.current) { clearInterval(pollingRef.current); pollingRef.current = null; }
    if (detailId && detailStatus === 'TESTING') {
      pollingRef.current = setInterval(async () => {
        try {
          const fresh = await getSubscription(detailId);
          setDetail(prev => {
            if (!prev) return prev;
            if (prev.testResult !== fresh.testResult || prev.status !== fresh.status) {
              if (fresh.testResult && !prev.testResult) {
                toast.success(t('subscriptions.toast.testResultReceived', { result: fresh.testResult }));
              }
              if (fresh.status !== prev.status) {
                toast.success(t('subscriptions.toast.statusChanged', { status: fresh.status }));
                if (loadRef.current) loadRef.current();
              }
              return fresh;
            }
            return prev;
          });
        } catch (e) { /* ignore polling failures */ }
      }, 5000);
    }
    return () => { if (pollingRef.current) clearInterval(pollingRef.current); };
  }, [detailId, detailStatus]);

  const copyToClipboard = (text, label) => {
    navigator.clipboard.writeText(text).then(
      () => toast.success(t('subscriptions.credentials.copied', { label })),
      () => toast.error(t('subscriptions.credentials.copyFailed')));
  };

  const loadCredentials = async (sub) => {
    setCredentialsBusy(true);
    try {
      const res = await revealSubscriptionCredentials(sub.id);
      setCredentials(res?.success ? res : null);
    } catch {
      setCredentials(null);
    } finally {
      setCredentialsBusy(false);
    }
  };

  const handleRegisterPublicKey = async () => {
    if (!detail || !publicKeyDraft.trim()) return;
    setSavingKey(true);
    try {
      const res = await registerSubscriptionPublicKey(detail.id, publicKeyDraft.trim());
      if (res?.success) {
        toast.success(t('subscriptions.credentials.keyRegistered'));
        setPublicKeyDraft('');
        loadCredentials(detail);
      } else {
        toast.error(res?.error || t('subscriptions.credentials.keyFailed'));
      }
    } catch (e) {
      toast.error(e?.data?.error || t('subscriptions.credentials.keyFailed'));
    } finally {
      setSavingKey(false);
    }
  };

  const handleRegenerateCredentials = async () => {
    if (!detail) return;
    setCredentialsBusy(true);
    try {
      const res = await regenerateSubscriptionCredentials(detail.id);
      if (res?.success) {
        setCredentials(res);
        setSecretVisible(false);
        toast.success(t('subscriptions.credentials.generated'));
      } else {
        toast.error(res?.error || t('subscriptions.credentials.failed'));
      }
    } catch (e) {
      toast.error(e?.data?.error || t('subscriptions.credentials.failed'));
    } finally {
      setCredentialsBusy(false);
    }
  };

  const openDetail = async (sub) => {
    setEditingIntrospectionUrl(false);
    setLastRun(null);
    setCredentials(null);
    setSecretVisible(false);
    setPublicKeyDraft('');
    loadCredentials(sub);
    try { const res = await getSubscription(sub.id); setDetail(res); }
    catch { setDetail(sub); }
  };

  const refreshDetail = async () => {
    if (!detail) return;
    try {
      const res = await getSubscription(detail.id);
      setDetail(res);
    } catch { /* ignore */ }
  };

  const handleRunTest = async (manualResult) => {
    if (!detail) return;
    setRunningTest(true);
    try {
      if (manualResult) {
        const res = await recordTestResult(detail.id, { result: manualResult, details: 'Manually marked as ' + manualResult + ' by admin' });
        if (res?.success) {
          toast.success(t('subscriptions.toast.testResultRecorded', { result: manualResult }));
          await refreshDetail();
          load();
        } else {
          toast.error(res?.error || t('subscriptions.toast.recordResultFailed'));
        }
      } else {
        const data = await runSubscriptionTest(detail.id);

        if (data?.success) {
          setLastRun(data.testResult || null);
          toast.success(t('subscriptions.toast.testsExecuted', { result: data.testResult?.result || 'PASSED' }));
          await refreshDetail();
          load();
        } else {
          toast.error(data?.error || t('subscriptions.toast.testExecutionFailed'));
        }
      }
    } catch (e) {
      toast.error(e.message || t('subscriptions.toast.testFailed'));
    } finally {
      setRunningTest(false);
    }
  };

  const executeAction = async () => {
    if (!actionModal) return;
    const { sub, action } = actionModal;
    setSubmitting(true);
    try {
      const payload = { reviewedBy: 'admin', initiatedBy: 'admin', notes };
      var res;
      switch (action.key) {
        case 'approve': res = await approveSubscription(sub.id, payload); break;
        case 'deny': res = await denySubscription(sub.id, payload); break;
        case 'start-test': res = await startTest(sub.id, payload); break;
        case 'activate': res = await activateSubscription(sub.id, payload); break;
        case 'suspend': res = await suspendSubscription(sub.id, payload); break;
        case 'reactivate': res = await reactivateSubscription(sub.id, payload); break;
        case 'run-test':
          setActionModal(null);
          setNotes('');
          await openDetail(sub);
          setSubmitting(false);
          return;
        default: break;
      }
      if (res?.success) {
        if (action.key === 'approve' && res.testResult) {
          setLastRun(res.testResult);
          if (res.subscription?.status === 'ACTIVE') toast.success(t('subscriptions.toast.approvedAndActivated'));
          else toast.error(t('subscriptions.toast.approvedTestsFailed'));
        } else {
          toast.success(t('subscriptions.toast.actionSuccessful', { action: action.label }));
        }
        setActionModal(null);
        setNotes('');
        if (detail && detail.id === sub.id) {
          await refreshDetail();
        } else {
          setDetail(null);
        }
        load();
      } else {
        toast.error(res?.error || t('subscriptions.toast.actionFailed'));
      }
    } catch (e) { toast.error(e.data?.error || e.message || t('subscriptions.toast.actionFailed')); }
    finally { setSubmitting(false); }
  };

  const handleStartEditUrl = () => {
    setIntrospectionUrlDraft(detail?.introspectionUrl || '');
    setEditingIntrospectionUrl(true);
  };

  const handleCancelEditUrl = () => {
    setEditingIntrospectionUrl(false);
    setIntrospectionUrlDraft('');
  };

  const handleSaveUrl = async () => {
    if (!detail) return;
    setSavingUrl(true);
    try {
      const res = await updateSubscriptionIntrospectionUrl(detail.id, introspectionUrlDraft.trim() || null);
      if (res?.success) {
        toast.success(t('subscriptions.toast.introspectionUrlUpdated'));
        setDetail(res.subscription || { ...detail, introspectionUrl: introspectionUrlDraft.trim() || null });
        setEditingIntrospectionUrl(false);
        load();
      } else {
        toast.error(res?.error || t('subscriptions.toast.updateFailed'));
      }
    } catch (e) {
      toast.error(e.data?.error || e.message || t('subscriptions.toast.introspectionUrlUpdateFailed'));
    } finally {
      setSavingUrl(false);
    }
  };

  if (loading && subs.length === 0) return <div className="d-flex justify-content-center py-5"><div className="spinner-border text-primary" /></div>;

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div><h2 className="mb-1">{t('subscriptions.title')}</h2><p className="text-muted mb-0">{t('subscriptions.totalCount', { count: totalItems })}</p></div>
        <button className="btn btn-outline-secondary btn-sm" onClick={load}><i className="fa-solid fa-arrows-rotate me-1"></i>{t('common.refresh')}</button>
      </div>

      {/* Filters + search */}
      <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
        <div className="d-flex gap-2 flex-wrap">
          {['ALL', 'PENDING', 'APPROVED', 'TESTING', 'ACTIVE', 'SUSPENDED', 'DENIED'].map(f => (
            <button key={f} className={`btn btn-sm ${filter === f ? 'btn-primary' : 'btn-outline-secondary'}`} onClick={() => setFilter(f)}>
              {f}
            </button>
          ))}
        </div>
        <div className="input-group input-group-sm" style={{ maxWidth: 320 }}>
          <span className="input-group-text"><i className="fa-solid fa-magnifying-glass"></i></span>
          <input
            className="form-control"
            placeholder={t('subscriptions.searchPlaceholder')}
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          {search && (
            <button className="btn btn-outline-secondary" onClick={() => setSearch('')} title={t('subscriptions.clearSearch')}>
              <i className="fa-solid fa-xmark"></i>
            </button>
          )}
        </div>
      </div>

      {/* Table */}
      <div className="card">
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead className="table-light">
              <tr><th>{t('subscriptions.table.requestId')}</th><th>{t('subscriptions.table.requestor')}</th><th>{t('subscriptions.table.template')}</th><th>{t('subscriptions.table.dataHolderGroup')}</th><th>{t('common.status')}</th><th>{t('subscriptions.table.test')}</th><th>{t('subscriptions.table.created')}</th><th style={{ minWidth: 180 }}>{t('common.actions')}</th></tr>
            </thead>
            <tbody>
              {subs.length === 0 ? (
                <tr><td colSpan={8} className="text-center text-muted py-5">{t('subscriptions.empty')}</td></tr>
              ) : subs.map(sub => (
                <tr key={sub.id}>
                  <td><button className="btn btn-link btn-sm p-0 text-decoration-none" onClick={() => openDetail(sub)}><code className="small">{sub.requestId?.slice(0, 16)}...</code></button></td>
                  <td><strong>{sub.requestorGroupName}</strong>{sub.requestorOrganization && <div className="text-muted small">{sub.requestorOrganization}</div>}</td>
                  <td className="small">{sub.templateName || '—'}</td>
                  <td className="small">
                    {sub.dataHolderGroupName || (sub.dataHolderGroupId ? `#${sub.dataHolderGroupId}` : '—')}
                    {(sub.dataholderName || sub.dataholderId) && (
                      <div className="text-muted small">{sub.dataholderName || sub.dataholderId}</div>
                    )}
                  </td>
                  <td><StatusBadge status={sub.status} /></td>
                  <td>{sub.testResult ? <span className={`badge bg-${sub.testResult === 'PASSED' ? 'success' : 'danger'}`}>{sub.testResult}</span> : <span className="text-muted">—</span>}</td>
                  <td className="text-muted small">{sub.createdAt ? new Date(sub.createdAt).toLocaleDateString() : '—'}</td>
                  <td>
                    <div className="btn-group btn-group-sm">
                      <button className="btn btn-outline-primary" onClick={() => openDetail(sub)} title={t('common.view')}><i className="fa-solid fa-eye"></i></button>
                      {getActions(sub, t).map(a => (
                        <button key={a.key} className={`btn btn-outline-${a.cls}`}
                          onClick={() => {
                            if (a.key === 'run-test') {
                              openDetail(sub);
                            } else {
                              setActionModal({ sub, action: a }); setNotes('');
                            }
                          }}
                          title={a.label}
                        >
                          <i className={`fa-solid ${a.icon}`}></i>
                        </button>
                      ))}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {totalItems > 0 && (
            <Pagination
              page={page}
              pageSize={pageSize}
              totalItems={totalItems}
              onPageChange={setPage}
              onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
              itemLabel={t('subscriptions.itemLabel')}
            />
          )}
        </div>
      </div>

      {/* Detail Modal */}
      {detail && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-xl modal-dialog-scrollable">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title me-auto"><i className="fa-solid fa-handshake me-2"></i>{detail.requestorGroupName}</h5>
                <div className="d-flex align-items-center gap-2 me-2">
                  {detail.status === 'TESTING' && (
                    <span className="badge bg-primary bg-opacity-10 text-primary d-flex align-items-center gap-1" style={{ fontSize: 11 }}>
                      <span className="spinner-grow spinner-grow-sm" style={{ width: 8, height: 8 }} />
                      {t('subscriptions.detail.live')}
                    </span>
                  )}
                  <button className="btn btn-outline-secondary btn-sm" onClick={refreshDetail} title={t('common.refresh')}>
                    <i className="fa-solid fa-arrows-rotate"></i>
                  </button>
                </div>
                <button type="button" className="btn-close" onClick={() => setDetail(null)}></button>
              </div>
              <div className="modal-body">
                {/* Workflow Stepper */}
                <WorkflowStepper status={detail.status} testResult={detail.testResult} />

                {/* Testing Panel */}
                <TestResultPanel
                  detail={detail}
                  onRunTest={handleRunTest}
                  running={runningTest}
                  lastRun={lastRun}
                />

                {/* Next step guidance */}
                {detail.status === 'PENDING' && (
                  <div className="alert alert-warning d-flex align-items-center gap-2 mb-3" style={{ fontSize: 13 }}>
                    <i className="fa-solid fa-clock" />
                    <span>{t('subscriptions.detail.pendingGuidance.p1')} <strong>{t('subscriptions.actions.approve')}</strong> {t('subscriptions.detail.pendingGuidance.or')} <strong>{t('subscriptions.actions.deny')}</strong> {t('subscriptions.detail.pendingGuidance.p2')}</span>
                  </div>
                )}
                {detail.status === 'APPROVED' && (
                  <div className="alert alert-info d-flex align-items-center gap-2 mb-3" style={{ fontSize: 13 }}>
                    <i className="fa-solid fa-flask" />
                    <span>{t('subscriptions.detail.approvedGuidance.p1')} <strong>{t('subscriptions.actions.startTest')}</strong> {t('subscriptions.detail.approvedGuidance.p2')}</span>
                  </div>
                )}

                {detail.pendingChangeStatus === 'PROPOSED' && (
                  <div className="alert alert-warning d-flex align-items-start gap-2 mb-3" style={{ fontSize: 13 }}>
                    <i className="fa-solid fa-file-pen mt-1" />
                    <div>
                      <strong>{t('subscriptions.detail.changeProposed')}</strong>
                      <div>
                        {t('subscriptions.terms.proposedSince', { date: formatDate(detail.pendingProposedAt) })}
                        {detail.pendingChangeMode === 'FORCED' && detail.pendingChangeDeadline && (
                          <> · {t('subscriptions.detail.changeDeadline', { date: formatDate(detail.pendingChangeDeadline) })}</>
                        )}
                      </div>
                    </div>
                  </div>
                )}
                {detail.pendingChangeStatus === 'DECLINED' && (
                  <div className="alert alert-secondary d-flex align-items-center gap-2 mb-3" style={{ fontSize: 13 }}>
                    <i className="fa-solid fa-circle-xmark" />
                    <span>{t('subscriptions.detail.changeDeclined')}</span>
                  </div>
                )}
                {detail.pendingChangeStatus === 'EXPIRED' && (
                  <div className="alert alert-danger d-flex align-items-center gap-2 mb-3" style={{ fontSize: 13 }}>
                    <i className="fa-solid fa-hourglass-end" />
                    <span>{t('subscriptions.detail.changeExpired')}</span>
                  </div>
                )}

                <div className="d-flex gap-2 mb-3 flex-wrap">
                  <StatusBadge status={detail.status} />
                  {detail.testResult && (
                    <span className={`badge bg-${detail.testResult === 'PASSED' ? 'success' : 'danger'}`}>
                      {t('subscriptions.detail.testLabel', { result: detail.testResult })}
                    </span>
                  )}
                </div>

                <div className="row g-3">
                  <div className="col-md-6"><h6>{t('subscriptions.detail.subscriptionSection')}</h6>
                    <table className="table table-sm"><tbody>
                      <tr><td className="text-muted">{t('subscriptions.table.requestId')}</td><td><code className="small">{detail.requestId}</code></td></tr>
                      <tr><td className="text-muted">{t('subscriptions.table.template')}</td><td>{detail.templateName}</td></tr>
                      <tr><td className="text-muted">{t('subscriptions.table.dataHolderGroup')}</td><td>{detail.dataHolderGroupName || (detail.dataHolderGroupId ? `#${detail.dataHolderGroupId}` : '—')}</td></tr>
                      {(detail.dataholderName || detail.dataholderId) && <tr><td className="text-muted">{t('subscriptions.table.dataHolder')}</td><td>{detail.dataholderName || detail.dataholderId}</td></tr>}
                      <tr><td className="text-muted">{t('subscriptions.detail.purpose')}</td><td>{detail.purpose || '—'}</td></tr>
                      <tr>
                        <td className="text-muted">{t('subscriptions.detail.introspectionUrl')}</td>
                        <td>
                          {editingIntrospectionUrl ? (
                            <div>
                              <input
                                type="url"
                                className="form-control form-control-sm mb-2"
                                value={introspectionUrlDraft}
                                onChange={e => setIntrospectionUrlDraft(e.target.value)}
                                placeholder={t('subscriptions.detail.introspectionUrlPlaceholder')}
                                autoFocus
                              />
                              <div className="d-flex gap-2">
                                <button className="btn btn-sm btn-primary" onClick={handleSaveUrl} disabled={savingUrl}>
                                  {savingUrl ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('common.saving')}</> : t('common.save')}
                                </button>
                                <button className="btn btn-sm btn-outline-secondary" onClick={handleCancelEditUrl} disabled={savingUrl}>{t('common.cancel')}</button>
                              </div>
                            </div>
                          ) : (
                            <div className="d-flex align-items-start gap-2">
                              <span>
                                {detail.introspectionUrl
                                  ? <code className="small" style={{wordBreak:'break-all'}}>{detail.introspectionUrl}</code>
                                  : <span className="text-muted">{t('subscriptions.detail.notProvided')}</span>}
                              </span>
                              {isMaster && (
                                <button
                                  className="btn btn-outline-primary btn-sm flex-shrink-0"
                                  onClick={handleStartEditUrl}
                                  title={t('subscriptions.detail.editIntrospectionUrl')}
                                  style={{ padding: '1px 8px', fontSize: '11px' }}
                                >
                                  <i className="fa-solid fa-pen"></i>
                                </button>
                              )}
                            </div>
                          )}
                        </td>
                      </tr>
                      <tr><td className="text-muted">{t('subscriptions.detail.effectiveFrom')}</td><td>{formatDate(detail.effectiveFrom)}</td></tr>
                      <tr><td className="text-muted">{t('subscriptions.detail.effectiveTo')}</td><td>{formatDate(detail.effectiveTo) || t('subscriptions.detail.noExpiry')}</td></tr>
                      <tr><td className="text-muted">{t('subscriptions.table.created')}</td><td>{formatDate(detail.createdAt)}</td></tr>
                      {detail.requestExpiresAt && <tr><td className="text-muted">{t('subscriptions.detail.requestExpires')}</td><td>{formatDate(detail.requestExpiresAt)}</td></tr>}
                      {detail.activatedAt && <tr><td className="text-muted">{t('subscriptions.detail.activated')}</td><td>{formatDate(detail.activatedAt)}{detail.activatedBy && <span className="text-muted"> — {detail.activatedBy}</span>}</td></tr>}
                      {(detail.maxQueriesPerDay || detail.maxQueriesPerMonth) && (
                        <tr><td className="text-muted">{t('subscriptions.detail.queryLimits')}</td><td>
                          {t('subscriptions.detail.perDay', { count: detail.maxQueriesPerDay ?? '∞' })}
                          {' · '}
                          {t('subscriptions.detail.perMonth', { count: detail.maxQueriesPerMonth ?? '∞' })}
                        </td></tr>
                      )}
                      {detail.statusMessage && <tr><td className="text-muted">{t('subscriptions.detail.statusMessage')}</td><td>{detail.statusMessage}</td></tr>}
                    </tbody></table>
                  </div>
                  <div className="col-md-6"><h6>{t('subscriptions.detail.requestorSection')}</h6>
                    <table className="table table-sm"><tbody>
                      <tr><td className="text-muted">{t('subscriptions.detail.group')}</td><td>{detail.requestorGroupName}</td></tr>
                      <tr><td className="text-muted">{t('subscriptions.detail.groupId')}</td><td><code className="small">{detail.requestorGroupId}</code></td></tr>
                      <tr><td className="text-muted">{t('common.type')}</td><td>{detail.requestorGroupType || '—'}</td></tr>
                      <tr><td className="text-muted">{t('subscriptions.detail.contact')}</td><td>{`${detail.requestorFirstName || ''} ${detail.requestorLastName || ''}`.trim() || '—'}</td></tr>
                      <tr><td className="text-muted">{t('common.email')}</td><td>{detail.requestorContactEmail || '—'}</td></tr>
                      <tr><td className="text-muted">{t('subscriptions.detail.organization')}</td><td>{detail.requestorOrganization || '—'}</td></tr>
                      <tr><td className="text-muted">{t('subscriptions.detail.address')}</td><td>{detail.formattedAddress || '—'}</td></tr>
                      <tr><td className="text-muted">{t('common.phone')}</td><td>{detail.requestorPhone || '—'}</td></tr>
                      {detail.requestorGroupCode && <tr><td className="text-muted">{t('subscriptions.detail.groupCode')}</td><td><code className="small">{detail.requestorGroupCode}</code></td></tr>}
                      {detail.requestorDescription && <tr><td className="text-muted">{t('common.description')}</td><td>{detail.requestorDescription}</td></tr>}
                      {detail.callbackUrl && <tr><td className="text-muted">{t('subscriptions.detail.callbackUrl')}</td><td><code className="small" style={{wordBreak:'break-all'}}>{detail.callbackUrl}</code></td></tr>}
                    </tbody></table>
                  </div>
                </div>

                {/* Credentials the requestor manager presents for this subscription. */}
                <div className="card mt-4">
                  <div className="card-header bg-transparent d-flex justify-content-between align-items-center">
                    <h6 className="mb-0">
                      <i className="fa-solid fa-key text-warning me-2" />
                      {t('subscriptions.credentials.title')}
                    </h6>
                    <button
                      className="btn btn-outline-warning btn-sm"
                      onClick={handleRegenerateCredentials}
                      disabled={credentialsBusy}
                    >
                      <i className="fa-solid fa-rotate me-1" />
                      {credentials?.clientId
                        ? t('subscriptions.credentials.regenerate')
                        : t('subscriptions.credentials.generate')}
                    </button>
                  </div>
                  <div className="card-body">
                    {credentialsBusy && !credentials && (
                      <div className="text-muted small"><span className="spinner-border spinner-border-sm me-2" />{t('common.loading')}</div>
                    )}

                    {!credentialsBusy && !credentials && (
                      <p className="text-muted small mb-0">{t('subscriptions.credentials.none')}</p>
                    )}

                    {credentials && (
                      <>
                        <div className="mb-3">
                          <label className="form-label small text-muted fw-semibold">{t('subscriptions.credentials.clientId')}</label>
                          <div className="input-group">
                            <input type="text" className="form-control font-monospace" value={credentials.clientId} readOnly />
                            <button className="btn btn-outline-secondary" onClick={() => copyToClipboard(credentials.clientId, t('subscriptions.credentials.clientId'))}>
                              <i className="fa-solid fa-copy" />
                            </button>
                          </div>
                        </div>

                        {/* Only the hash is stored, so a secret is present only right after minting. */}
                        {credentials.clientSecret ? (
                          <div className="mb-3">
                            <label className="form-label small text-muted fw-semibold">{t('subscriptions.credentials.clientSecret')}</label>
                            <div className="input-group">
                              <input type={secretVisible ? 'text' : 'password'} className="form-control font-monospace" value={credentials.clientSecret} readOnly />
                              <button className="btn btn-outline-secondary" onClick={() => setSecretVisible(v => !v)}>
                                <i className={`fa-solid ${secretVisible ? 'fa-eye-slash' : 'fa-eye'}`} />
                              </button>
                              <button className="btn btn-outline-secondary" onClick={() => copyToClipboard(credentials.clientSecret, t('subscriptions.credentials.clientSecret'))}>
                                <i className="fa-solid fa-copy" />
                              </button>
                            </div>
                          </div>
                        ) : (
                          <div className="alert alert-warning small">
                            <i className="fa-solid fa-triangle-exclamation me-1" />
                            {credentials.message || t('subscriptions.credentials.secretNotRetrievable')}
                          </div>
                        )}

                        {credentials.lastUsedAt && (
                          <div className="text-muted small mb-2">
                            {t('subscriptions.credentials.lastUsed', { date: formatDate(credentials.lastUsedAt) })}
                          </div>
                        )}

                        {/* The requestor manager's signing key. Public by construction: pasted, not minted. */}
                        <hr className="my-3" />
                        <label className="form-label small text-muted fw-semibold">
                          {t('subscriptions.credentials.publicKey')}
                        </label>
                        <p className="text-muted small">{t('subscriptions.credentials.publicKeyHelp')}</p>
                        {credentials.publicKeyRegisteredAt ? (
                          <div className="alert alert-success py-2 small">
                            <i className="fa-solid fa-check me-1" />
                            {t('subscriptions.credentials.keyOnFile', {
                              date: formatDate(credentials.publicKeyRegisteredAt),
                            })}
                          </div>
                        ) : (
                          <div className="alert alert-secondary py-2 small">
                            {t('subscriptions.credentials.noKey')}
                          </div>
                        )}
                        <div className="input-group input-group-sm mb-3">
                          <input
                            type="text"
                            className="form-control font-monospace"
                            value={publicKeyDraft}
                            onChange={(e) => setPublicKeyDraft(e.target.value)}
                            placeholder={t('subscriptions.credentials.publicKeyPlaceholder')}
                          />
                          <button
                            className="btn btn-primary"
                            onClick={handleRegisterPublicKey}
                            disabled={savingKey || !publicKeyDraft.trim()}
                          >
                            {savingKey ? t('common.saving') : t('subscriptions.credentials.registerKey')}
                          </button>
                        </div>

                        <div className="alert alert-info small mb-0">
                          <i className="fa-solid fa-info-circle me-1" />
                          {t('subscriptions.credentials.info')}
                        </div>
                      </>
                    )}
                  </div>
                </div>

                <SubscriptionTerms detail={detail} />

                {detail.reviewedBy && (
                  <div className="mt-3"><h6>{t('subscriptions.detail.reviewSection')}</h6>
                    <p className="small">{t('subscriptions.detail.reviewedBy')} <strong>{detail.reviewedBy}</strong> {t('subscriptions.detail.reviewedAt')} {formatDate(detail.reviewedAt)}
                      {detail.reviewNotes && <><br /><em>{detail.reviewNotes}</em></>}
                    </p>
                  </div>
                )}

                {detail.statusHistory && detail.statusHistory.length > 0 && (
                  <div className="mt-3"><h6>{t('subscriptions.detail.statusHistory')}</h6>
                    <div className="list-group list-group-flush">
                      {detail.statusHistory.map((h, i) => (
                        <div key={i} className="list-group-item px-0 py-2 d-flex gap-3 align-items-start small">
                          <span className="text-muted" style={{ width: 140, flexShrink: 0 }}>{formatDate(h.createdAt)}</span>
                          <div>
                            {h.previousStatus && <><StatusBadge status={h.previousStatus} /> <i className="fa-solid fa-arrow-right mx-1 small"></i> </>}
                            <StatusBadge status={h.newStatus} />
                            <div className="text-muted mt-1">{h.changedBy}{h.changeReason && (' — ' + h.changeReason)}</div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
              <div className="modal-footer d-flex justify-content-between">
                <div className="text-muted small">
                  {detail.status === 'TESTING' && !detail.testResult && (
                    <span><i className="fa-solid fa-info-circle me-1" />{t('subscriptions.detail.runBeforeActivate')}</span>
                  )}
                </div>
                <div className="d-flex gap-2">
                  <button className="btn btn-secondary" onClick={() => setDetail(null)}>{t('common.close')}</button>
                  {getActions(detail, t).map(a => {
                    if (a.key === 'run-test') return null;
                    return (
                      <button key={a.key} className={`btn btn-${a.cls}`} onClick={() => {
                        setDetail(null);
                        setActionModal({ sub: detail, action: a });
                        setNotes('');
                      }}>
                        <i className={`fa-solid ${a.icon} me-1`}></i>{a.label}
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Action Modal */}
      {actionModal && (
        <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <h5 className="modal-title"><i className={`fa-solid ${actionModal.action.icon} me-2`}></i>{actionModal.action.label}</h5>
                <button type="button" className="btn-close" onClick={() => setActionModal(null)}></button>
              </div>
              <div className="modal-body">
                <p>{t('subscriptions.actionModal.subscriptionFor')} <strong>{actionModal.sub.requestorGroupName}</strong></p>

                <WorkflowStepper status={actionModal.sub.status} testResult={actionModal.sub.testResult} />

                <div className="d-flex gap-2 mb-3"><StatusBadge status={actionModal.sub.status} /></div>

                {actionModal.action.key === 'start-test' && (
                  <div className="alert alert-info small mb-3">
                    <i className="fa-solid fa-info-circle me-1" />
                    {t('subscriptions.actionModal.startTestInfo')}
                  </div>
                )}
                {actionModal.action.key === 'activate' && (
                  <div className="alert alert-success small mb-3">
                    <i className="fa-solid fa-check-circle me-1" />
                    {t('subscriptions.actionModal.activateInfo')}
                  </div>
                )}

                {(actionModal.action.key === 'deny' || actionModal.action.key === 'suspend') && (
                  <div className="mb-3"><label className="form-label">{t('subscriptions.actionModal.reasonLabel')}</label><textarea className="form-control" rows={3} value={notes} onChange={e => setNotes(e.target.value)} placeholder={t('subscriptions.actionModal.reasonPlaceholder')} /></div>
                )}
                {actionModal.action.key !== 'deny' && actionModal.action.key !== 'suspend' && (
                  <div className="mb-3"><label className="form-label">{t('subscriptions.actionModal.notesLabel')}</label><textarea className="form-control" rows={2} value={notes} onChange={e => setNotes(e.target.value)} /></div>
                )}
              </div>
              <div className="modal-footer">
                <button className="btn btn-secondary" onClick={() => setActionModal(null)} disabled={submitting}>{t('common.cancel')}</button>
                <button className={`btn btn-${actionModal.action.cls}`} onClick={executeAction}
                  disabled={submitting || ((actionModal.action.key === 'deny' || actionModal.action.key === 'suspend') && !notes.trim())}>
                  {submitting ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('common.processing')}</> : actionModal.action.label}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Subscriptions;