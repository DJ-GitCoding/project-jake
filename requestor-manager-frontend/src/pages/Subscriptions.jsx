/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { useAlert } from '../context/AlertContext';
import { subscriptionsApi, requestorGroupsApi, dataHolderGroupsApi } from '../services/api';
import Loading from '../components/Loading';
import Modal from '../components/Modal';
import Pagination, { DEFAULT_PAGE_SIZE } from '../components/Pagination';
import SubscribeFlow from '../components/SubscribeFlow';
import { buildReferenceIndex, sectionClauseLines } from '../constants/legalSections';
import { initialMemberFieldMapping, MemberFieldSelect } from '../constants/memberFields';
import DataHolderContact from '../components/DataHolderContact';
import { useT } from '../i18n';

// ==================== RDAP Test Result Sub-Components ====================

/**
 * Summary bar showing validation + RDAP test counts at a glance
 */
const TestSummaryBar = ({ summary }) => {
  const { t } = useT();
  if (!summary) return null;
  const { passedValidationTests, failedValidationTests,
          totalRdapTests, passedRdapTests, failedRdapTests, skippedRdapTests,
          errorRdapTests, totalDurationMs } = summary;

  return (
    <div className="card bg-light mb-4">
      <div className="card-body py-2">
        <div className="row text-center">
          <div className="col">
            <div className="small text-muted">{t('subscriptions.testSummary.validationTests')}</div>
            <div>
              <span className="badge bg-success me-1">{t('subscriptions.testSummary.passed', { count: passedValidationTests })}</span>
              {failedValidationTests > 0 && <span className="badge bg-danger">{t('subscriptions.testSummary.failed', { count: failedValidationTests })}</span>}
            </div>
          </div>
          {totalRdapTests > 0 && (
            <div className="col">
              <div className="small text-muted">{t('subscriptions.testSummary.rdapTests')}</div>
              <div>
                <span className="badge bg-success me-1">{t('subscriptions.testSummary.passed', { count: passedRdapTests })}</span>
                {failedRdapTests > 0 && <span className="badge bg-danger me-1">{t('subscriptions.testSummary.failed', { count: failedRdapTests })}</span>}
                {skippedRdapTests > 0 && <span className="badge bg-secondary me-1">{t('subscriptions.testSummary.skipped', { count: skippedRdapTests })}</span>}
                {errorRdapTests > 0 && <span className="badge bg-warning text-dark">{t('subscriptions.testSummary.error', { count: errorRdapTests })}</span>}
              </div>
            </div>
          )}
          {totalDurationMs != null && (
            <div className="col">
              <div className="small text-muted">{t('subscriptions.testSummary.duration')}</div>
              <div><strong>{t('subscriptions.testSummary.ms', { count: totalDurationMs })}</strong></div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

/**
 * Category icon helper
 */
const getCategoryIcon = (category) => {
  switch (category) {
    case 'LOOKUP': return 'fa-search';
    case 'ACCESS': return 'fa-key';
    case 'CONTACT_VISIBILITY': return 'fa-eye';
    case 'REDACTION': return 'fa-eye-slash';
    case 'PARAMETER': return 'fa-sliders-h';
    default: return 'fa-check-circle';
  }
};

const getCategoryLabel = (category, t) => {
  switch (category) {
    case 'LOOKUP': return t('subscriptions.category.lookup');
    case 'ACCESS': return t('subscriptions.category.access');
    case 'CONTACT_VISIBILITY': return t('subscriptions.category.contactVisibility');
    case 'REDACTION': return t('subscriptions.category.redaction');
    case 'PARAMETER': return t('subscriptions.category.parameter');
    default: return category;
  }
};

const getSeverityColor = (severity) => {
  switch (severity) {
    case 'ERROR': return 'danger';
    case 'WARNING': return 'warning';
    case 'INFO': return 'secondary';
    default: return 'secondary';
  }
};

const getQueryTypeIcon = (queryType) => {
  switch (queryType?.toLowerCase()) {
    case 'domain': return 'fa-globe';
    case 'ip': return 'fa-network-wired';
    case 'asn': return 'fa-hashtag';
    default: return 'fa-question';
  }
};

const getResultBadge = (result, t) => {
  switch (result) {
    case 'PASSED': return <span className="badge bg-success"><i className="fas fa-check me-1"></i>{t('subscriptions.result.passed')}</span>;
    case 'FAILED': return <span className="badge bg-danger"><i className="fas fa-times me-1"></i>{t('subscriptions.result.failed')}</span>;
    case 'PENDING': return <span className="badge bg-warning text-dark"><i className="fas fa-hourglass-half me-1"></i>{t('subscriptions.result.pending')}</span>;
    case 'SKIPPED': return <span className="badge bg-secondary"><i className="fas fa-forward me-1"></i>{t('subscriptions.result.skipped')}</span>;
    case 'ERROR': return <span className="badge bg-warning text-dark"><i className="fas fa-exclamation-triangle me-1"></i>{t('subscriptions.result.error')}</span>;
    default: return <span className="badge bg-secondary">{result}</span>;
  }
};

/**
 * Single RDAP check row
 */
const RdapCheckRow = ({ check }) => {
  const { t } = useT();
  const severityColor = getSeverityColor(check.severity);
  return (
    <div className={`d-flex align-items-start gap-2 py-1 px-2 ${!check.passed ? 'bg-danger bg-opacity-10 rounded' : ''}`}
         style={{ fontSize: '0.85rem' }}>
      <i className={`fas ${check.passed ? 'fa-check text-success' : 'fa-times text-danger'} mt-1`}
         style={{ width: '14px', flexShrink: 0 }}></i>
      <div className="flex-grow-1">
        <div className="d-flex align-items-center gap-2">
          <strong>{check.name}</strong>
          <span className={`badge bg-${severityColor} bg-opacity-25 text-${severityColor}`}
                style={{ fontSize: '0.7rem' }}>{check.severity}</span>
        </div>
        <div className="text-muted small">{check.message}</div>
        {check.expected && check.actual && check.expected !== check.actual && (
          <div className="small mt-1">
            <span className="text-muted">{t('subscriptions.rdap.expected')}</span> <code>{check.expected}</code>
            {' → '}
            <span className="text-muted">{t('subscriptions.rdap.actual')}</span> <code>{check.actual}</code>
          </div>
        )}
      </div>
    </div>
  );
};

/**
 * Expandable RDAP test case card — one per test data entry
 */
const RdapTestCaseCard = ({ rdapCase, defaultExpanded }) => {
  const { t } = useT();
  const [expanded, setExpanded] = useState(defaultExpanded || rdapCase.result !== 'PASSED');

  // Group checks by category
  const checksByCategory = {};
  if (rdapCase.checks) {
    rdapCase.checks.forEach(check => {
      const cat = check.category || 'OTHER';
      if (!checksByCategory[cat]) checksByCategory[cat] = [];
      checksByCategory[cat].push(check);
    });
  }
  const categoryOrder = ['LOOKUP', 'ACCESS', 'PARAMETER', 'CONTACT_VISIBILITY', 'REDACTION', 'OTHER'];
  const sortedCategories = Object.keys(checksByCategory)
    .sort((a, b) => categoryOrder.indexOf(a) - categoryOrder.indexOf(b));

  const passedChecks = rdapCase.checks ? rdapCase.checks.filter(c => c.passed).length : 0;
  const totalChecks = rdapCase.checks ? rdapCase.checks.length : 0;

  return (
    <div className={`card mb-2 ${rdapCase.result === 'FAILED' || rdapCase.result === 'ERROR' ? 'border-danger' : 
                                  rdapCase.result === 'PASSED' ? 'border-success' : 'border-secondary'}`}>
      <div className="card-header py-2 d-flex align-items-center justify-content-between"
           style={{ cursor: 'pointer' }}
           onClick={() => setExpanded(!expanded)}>
        <div className="d-flex align-items-center gap-2">
          <i className={`fas ${expanded ? 'fa-chevron-down' : 'fa-chevron-right'} text-muted`}
             style={{ width: '12px' }}></i>
          <i className={`fas ${getQueryTypeIcon(rdapCase.queryType)} text-primary`}></i>
          <strong>{rdapCase.label || t('subscriptions.rdap.lookupLabel', { queryType: rdapCase.queryType, queryValue: rdapCase.queryValue })}</strong>
          {rdapCase.requestTypeName && (
            <span className={`badge ${
              rdapCase.requestTypeName === 'exigent' ? 'bg-danger' :
              rdapCase.requestTypeName === 'confidential' ? 'bg-warning text-dark' : 'bg-secondary'
            }`} style={{ fontSize: '0.7rem' }}>
              {rdapCase.requestTypeName}
            </span>
          )}
          {rdapCase.resolvedAccessLevel != null && (
            <span className="badge bg-info bg-opacity-25 text-info" style={{ fontSize: '0.7rem' }}>
              {t('subscriptions.level', { level: rdapCase.resolvedAccessLevel })}
            </span>
          )}
        </div>
        <div className="d-flex align-items-center gap-2">
          {rdapCase.durationMs != null && (
            <small className="text-muted">{t('subscriptions.testSummary.ms', { count: rdapCase.durationMs })}</small>
          )}
          <small className="text-muted">{t('subscriptions.rdap.checksCount', { passed: passedChecks, total: totalChecks })}</small>
          {getResultBadge(rdapCase.result, t)}
        </div>
      </div>
      {expanded && (
        <div className="card-body p-2">
          {rdapCase.message && (
            <div className="small text-muted mb-2 px-2">{rdapCase.message}</div>
          )}
          {sortedCategories.map(category => (
            <div key={category} className="mb-2">
              <div className="d-flex align-items-center gap-1 px-2 py-1 bg-light rounded-top"
                   style={{ fontSize: '0.8rem' }}>
                <i className={`fas ${getCategoryIcon(category)} text-muted`}></i>
                <strong className="text-muted">{getCategoryLabel(category, t)}</strong>
                <span className="badge bg-light text-muted border ms-auto" style={{ fontSize: '0.7rem' }}>
                  {checksByCategory[category].filter(c => c.passed).length}/{checksByCategory[category].length}
                </span>
              </div>
              <div className="border border-top-0 rounded-bottom">
                {checksByCategory[category].map((check, idx) => (
                  <RdapCheckRow key={idx} check={check} />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

// ==================== Helper: check if activate is available ====================

/**
 * Returns true if the subscription can be activated.
 * Uses the availableActions array from the API if present,
 * otherwise falls back to checking testResult.
 */
const canActivate = (sub) => {
  if (sub.status !== 'TESTING') return false;
  // Prefer the server-computed availableActions
  if (sub.availableActions && Array.isArray(sub.availableActions)) {
    return sub.availableActions.includes('activate');
  }
  // Fallback: check testResult directly
  return sub.testResult === 'PASSED';
};

/**
 * Testing and activation are automated server-side once the data holder approves (see the RM's
 * SubscriptionAutoAdvanceScheduler). The workflow only stops on its own if testing does not pass,
 * in which case the backend flags the record ("halted") and manual controls are offered as a
 * recovery path. This detects that halted state.
 */
const isAutoHalted = (sub) =>
  sub.status === 'TESTING' &&
  typeof sub.statusMessage === 'string' &&
  sub.statusMessage.toLowerCase().includes('halted');

// ==================== Main Component ====================

const Subscriptions = () => {
  const { isGroupAdmin, isMasterAdmin, isRequestorGroupAdmin } = useAuth();
  const { success, error: showError, confirm } = useAlert();
  const { t } = useT();

  const [subscriptions, setSubscriptions] = useState([]);
  const [requestorGroups, setRequestorGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [filterGroup, setFilterGroup] = useState('');
  const [lastPolled, setLastPolled] = useState(null);

  // Server-side pagination state (1-based page) + aggregate stats for badges.
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [totalItems, setTotalItems] = useState(0);
  const [stats, setStats] = useState({ total: 0, byStatus: {} });

  // Modal states
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [showTestResultModal, setShowTestResultModal] = useState(false);
  const [selectedItem, setSelectedItem] = useState(null);
  /* Read from the live template when the details open: contact details are never pinned to
   * the subscription, so a data holder group that moves office reaches its subscribers. */
  const [dataHolderContact, setDataHolderContact] = useState(null);
  const [testResult, setTestResult] = useState(null);
  const [refreshing, setRefreshing] = useState({});
  const [respondingId, setRespondingId] = useState(null);
  const [termsMode, setTermsMode] = useState('current');
  const [showRequestData, setShowRequestData] = useState(false);

  const [memberMapping, setMemberMapping] = useState({});
  const [groupMemberFields, setGroupMemberFields] = useState([]);
  const [savingMapping, setSavingMapping] = useState(false);

  // Credentials this subscription's data holder group issued; the secret is write-only.
  const [credForm, setCredForm] = useState({ clientId: '', clientSecret: '' });
  const [savingCreds, setSavingCreds] = useState(false);
  // The public half of this subscription's signing keys, for registering with the group admin.
  const [signingKey, setSigningKey] = useState('');
  const [generatingKeys, setGeneratingKeys] = useState(false);
  // Re-acceptance of the proposed terms, gathered before the decision is offered.
  const [proposedAccepted, setProposedAccepted] = useState([]);
  const [proposedReviewed, setProposedReviewed] = useState([]);
  const [proposedValues, setProposedValues] = useState({});

  // New subscription: pick a data holder group, then browse its templates and subscribe.
  const [showGroupPicker, setShowGroupPicker] = useState(false);
  const [groupPickerList, setGroupPickerList] = useState([]);
  const [groupPickerLoading, setGroupPickerLoading] = useState(false);
  const [groupPickerSearch, setGroupPickerSearch] = useState('');
  const [subscribeTarget, setSubscribeTarget] = useState(null);
  const [groupPickerPage, setGroupPickerPage] = useState(1);
  const [groupPickerPageSize, setGroupPickerPageSize] = useState(10);
  const [groupPickerTotal, setGroupPickerTotal] = useState(0);
  const [actionLoading, setActionLoading] = useState({});

  const pollingRef = useRef(true);

  const POLLABLE_STATUSES = ['SUBMITTED', 'PENDING_REVIEW', 'APPROVED', 'TESTING'];

  const hasPollableRequests = useCallback(() => {
    return subscriptions.some(s => POLLABLE_STATUSES.includes(s.status));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subscriptions]);

  // Debounce the search term into debouncedSearch (300ms).
  useEffect(() => {
    const t = setTimeout(() => setDebouncedSearch(searchTerm), 300);
    return () => clearTimeout(t);
  }, [searchTerm]);

  // Reset to the first page whenever the search / filters change.
  useEffect(() => {
    setPage(1);
  }, [debouncedSearch, filterStatus, filterGroup]);

  // Load the current page of subscription requests (server-side search/filter/pagination).
  const loadSubscriptions = useCallback(async () => {
    try {
      const response = await subscriptionsApi.getAll({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        status: filterStatus || undefined,
        requestorGroupId: filterGroup || undefined,
        sortBy: 'createdAt',
        sortDir: 'desc',
      });
      const data = response.data.data || {};
      setSubscriptions(data.content || []);
      setTotalItems(data.totalElements || 0);
      setLastPolled(new Date());
    } catch (err) {
      console.error('Failed to load subscriptions:', err);
      showError(t('subscriptions.errors.loadFailed'));
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, pageSize, debouncedSearch, filterStatus, filterGroup]);

  // Aggregate status counts for the summary cards / tab badges (role-scoped server-side).
  const loadStats = useCallback(async () => {
    try {
      const response = await subscriptionsApi.getStats();
      setStats(response.data.data || { total: 0, byStatus: {} });
    } catch (err) {
      console.error('Failed to load subscription stats:', err);
    }
  }, []);

  // Fetch the current page whenever pagination / search / filters change.
  useEffect(() => {
    loadSubscriptions();
  }, [loadSubscriptions]);

  // One-time auxiliary data: stats and the requestor-group filter list.
  useEffect(() => {
    loadStats();
    (async () => {
      try {
        const groupsResponse = await requestorGroupsApi.getAll();
        setRequestorGroups(groupsResponse.data.data || []);
      } catch (err) {
        console.error('Failed to load requestor groups:', err);
      }
    })();
  }, [loadStats]);

  // Manual refresh: reload the current page and stats together.
  const refreshAll = useCallback(() => {
    loadSubscriptions();
    loadStats();
  }, [loadSubscriptions, loadStats]);

  useEffect(() => {
    if (!hasPollableRequests()) return;

    pollingRef.current = true;

    const pollPendingStatuses = async () => {
      if (!pollingRef.current) return;

      const pollable = subscriptions.filter(s => POLLABLE_STATUSES.includes(s.status));
      if (pollable.length === 0) return;

      try {
        const refreshPromises = pollable.map(sub =>
          subscriptionsApi.refreshStatus(sub.id)
            .then(res => res.data.data)
            .catch(err => {
              console.error(`Failed to refresh status for ${sub.internalRequestId}:`, err);
              return null;
            })
        );

        const results = await Promise.all(refreshPromises);
        let hasChanges = false;

        setSubscriptions(prev => {
          const updated = prev.map(sub => {
            const refreshed = results.find(r => r && r.id === sub.id);
            if (refreshed && refreshed.status !== sub.status) {
              hasChanges = true;
              return refreshed;
            }
            return refreshed || sub;
          });
          return updated;
        });

        setLastPolled(new Date());

        if (hasChanges) {
          // Refresh the badge counts since a status transition changed the tallies.
          loadStats();

          results.forEach(r => {
            if (!r) return;
            const original = subscriptions.find(s => s.id === r.id);
            if (!original || original.status === r.status) return;

            if (r.status === 'APPROVED') {
              success(t('subscriptions.toasts.approved', { name: r.templateName }), { toast: true });
            } else if (r.status === 'DECLINED') {
              showError(t('subscriptions.toasts.declined', { name: r.templateName, reason: r.statusMessage || t('subscriptions.toasts.noReason') }));
            } else if (r.status === 'ACTIVE') {
              success(t('subscriptions.toasts.active', { name: r.templateName }), { toast: true });
            } else if (r.status === 'TESTING') {
              success(t('subscriptions.toasts.testing', { name: r.templateName }), { toast: true });
            }
          });
        }
      } catch (err) {
        console.error('Polling failed:', err);
      }
    };

    const intervalId = setInterval(pollPendingStatuses, 5000);

    return () => {
      pollingRef.current = false;
      clearInterval(intervalId);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasPollableRequests, subscriptions, loadStats]);

  const respondToChange = async (subscription, accept, fieldValues) => {
    setRespondingId(subscription.id);
    try {
      await subscriptionsApi.respondToPendingChange(
        subscription.dataHolderGroupId, subscription.externalRequestId, accept, fieldValues);
      const refreshed = await subscriptionsApi.refreshStatus(subscription.id);
      const updated = refreshed.data.data;
      setSubscriptions(prev => prev.map(s => (s.id === updated.id ? updated : s)));
      success(accept
        ? t('subscriptions.pendingChange.accepted')
        : t('subscriptions.pendingChange.declinedToast'));
    } catch (err) {
      console.error('Failed to respond to proposed change:', err);
      showError(t('subscriptions.pendingChange.failed'));
    } finally {
      setRespondingId(null);
    }
  };

  const handleRefreshStatus = async (subscription) => {
    setRefreshing(prev => ({ ...prev, [subscription.id]: true }));
    try {
      const response = await subscriptionsApi.refreshStatus(subscription.id);
      const updated = response.data.data;
      setSubscriptions(prev => prev.map(s => s.id === updated.id ? updated : s)); loadStats();
      success(t('subscriptions.toasts.statusRefreshed'));
    } catch (err) {
      console.error('Failed to refresh status:', err);
      showError(t('subscriptions.errors.refreshFailed'));
    } finally {
      setRefreshing(prev => ({ ...prev, [subscription.id]: false }));
    }
  };

  const handleSubmitDraft = async (subscription) => {
    const confirmed = await confirm(
      t('subscriptions.confirm.submit.title'),
      t('subscriptions.confirm.submit.body', { name: subscription.templateName, group: subscription.dataHolderGroupName }),
      null, null,
      { confirmText: t('subscriptions.actions.submit'), confirmVariant: 'primary', icon: 'fa-paper-plane', iconColor: 'primary' }
    );
    if (confirmed) {
      try {
        const response = await subscriptionsApi.submit(subscription.id);
        const updated = response.data.data;
        setSubscriptions(prev => prev.map(s => s.id === updated.id ? updated : s)); loadStats();
        success(t('subscriptions.toasts.submitted'));
      } catch (err) {
        console.error('Failed to submit:', err);
        showError(err.response?.data?.message || t('subscriptions.errors.submitFailed'));
      }
    }
  };

  const handleCancel = async (subscription) => {
    const confirmed = await confirm(
      t('subscriptions.confirm.cancel.title'),
      t('subscriptions.confirm.cancel.body', { name: subscription.templateName }),
      null, null,
      { confirmText: t('subscriptions.confirm.cancel.confirmText'), confirmVariant: 'danger', icon: 'fa-times-circle', iconColor: 'danger' }
    );
    if (confirmed) {
      try {
        const response = await subscriptionsApi.cancel(subscription.id);
        const updated = response.data.data;
        setSubscriptions(prev => prev.map(s => s.id === updated.id ? updated : s)); loadStats();
        success(t('subscriptions.toasts.cancelled'));
      } catch (err) {
        console.error('Failed to cancel:', err);
        showError(err.response?.data?.message || t('subscriptions.errors.cancelFailed'));
      }
    }
  };

  // ==================== Testing Workflow Handlers ====================

  const handleStartTesting = async (subscription) => {
    const confirmed = await confirm(
      t('subscriptions.confirm.startTesting.title'),
      t('subscriptions.confirm.startTesting.body', { name: subscription.templateName }),
      null, null,
      { confirmText: t('subscriptions.actions.startTesting'), confirmVariant: 'info', icon: 'fa-flask', iconColor: 'info' }
    );
    if (confirmed) {
      setActionLoading(prev => ({ ...prev, [`start-${subscription.id}`]: true }));
      try {
        const response = await subscriptionsApi.startTesting(subscription.id);
        const updated = response.data.data;
        setSubscriptions(prev => prev.map(s => s.id === updated.id ? updated : s)); loadStats();
        success(t('subscriptions.toasts.testingStarted'));
      } catch (err) {
        console.error('Failed to start testing:', err);
        showError(err.response?.data?.message || t('subscriptions.errors.startTestingFailed'));
      } finally {
        setActionLoading(prev => ({ ...prev, [`start-${subscription.id}`]: false }));
      }
    }
  };

  const handleRunTest = async (subscription) => {
    setActionLoading(prev => ({ ...prev, [`test-${subscription.id}`]: true }));
    try {
      const response = await subscriptionsApi.runTest(subscription.id);
      const result = response.data.data;
      setTestResult(result);
      setSelectedItem(subscription);
      setShowTestResultModal(true);
      // Refresh the subscription to get updated availableActions & testResult
      try {
        const refreshResponse = await subscriptionsApi.refreshStatus(subscription.id);
        const updated = refreshResponse.data.data;
        setSubscriptions(prev => prev.map(s => s.id === updated.id ? updated : s)); loadStats();
      } catch (refreshErr) {
        console.error('Failed to refresh after test:', refreshErr);
      }
      if (result.result === 'PASSED') {
        success(t('subscriptions.toasts.testsPassed'));
      } else if (result.result === 'PENDING') {
        success(t('subscriptions.toasts.testsPending'));
      } else {
        showError(t('subscriptions.toasts.testsFailed'));
      }
    } catch (err) {
      console.error('Failed to run tests:', err);
      showError(err.response?.data?.message || t('subscriptions.errors.runTestFailed'));
    } finally {
      setActionLoading(prev => ({ ...prev, [`test-${subscription.id}`]: false }));
    }
  };

  const handleActivate = async (subscription) => {
    const confirmed = await confirm(
      t('subscriptions.confirm.activate.title'),
      t('subscriptions.confirm.activate.body', { name: subscription.templateName }),
      null, null,
      { confirmText: t('subscriptions.actions.activate'), confirmVariant: 'success', icon: 'fa-check-circle', iconColor: 'success' }
    );
    if (confirmed) {
      setActionLoading(prev => ({ ...prev, [`activate-${subscription.id}`]: true }));
      try {
        const response = await subscriptionsApi.activate(subscription.id);
        const updated = response.data.data;
        setSubscriptions(prev => prev.map(s => s.id === updated.id ? updated : s)); loadStats();
        success(t('subscriptions.toasts.activated'));
      } catch (err) {
        console.error('Failed to activate:', err);
        showError(err.response?.data?.message || t('subscriptions.errors.activateFailed'));
      } finally {
        setActionLoading(prev => ({ ...prev, [`activate-${subscription.id}`]: false }));
      }
    }
  };

  /** The contact published with this subscription's template, as it stands right now. */
  const loadDataHolderContact = useCallback(async (item) => {
    setDataHolderContact(null);
    if (!item?.dataHolderGroupId || !item?.templateId) return;
    try {
      const response = await dataHolderGroupsApi.getTemplates(item.dataHolderGroupId);
      const template = (response.data.data || []).find(tpl => tpl.templateId === item.templateId);
      setDataHolderContact(template?.contact || null);
    } catch (err) {
      /* The details are a courtesy here; a group that cannot be reached just shows nothing. */
      console.error('Failed to load the data holder group contact:', err);
    }
  }, []);

  /** Open the details modal. `mode` picks the terms shown: in force, or the proposed change. */
  const handleViewDetails = (item, mode = 'current') => {
    setSelectedItem(item);
    loadDataHolderContact(item);
    setTermsMode(mode);
    setShowRequestData(false);
    // The secret is never returned, so the field always starts blank.
    setCredForm({ clientId: item.dhgClientId || '', clientSecret: '' });
    setSigningKey(item.dhgPublicKey || '');
    setProposedAccepted([]);
    setProposedReviewed([]);
    setProposedValues({ ...(item.subscriptionFieldValues || {}) });
    const requiredMemberFields = item.templateSnapshot?.userFields || [];
    setGroupMemberFields([]);
    setMemberMapping(initialMemberFieldMapping(requiredMemberFields, [], item.userFieldMapping));
    if (requiredMemberFields.length > 0 && item.requestorGroupId) {
      requestorGroupsApi.getUserFields(item.requestorGroupId)
        .then((res) => {
          const fields = res.data.data || [];
          setGroupMemberFields(fields);
          setMemberMapping(initialMemberFieldMapping(requiredMemberFields, fields, item.userFieldMapping));
        })
        .catch((err) => console.error('Failed to load member fields:', err));
    }
    setShowDetailModal(true);
  };

  const saveMemberMapping = async () => {
    if (!selectedItem) return;
    setSavingMapping(true);
    try {
      const res = await subscriptionsApi.updateUserFieldMapping(selectedItem.id, memberMapping);
      const updated = res.data?.data;
      if (updated) setSelectedItem({ ...selectedItem, userFieldMapping: updated.userFieldMapping });
      success(t('subscriptions.memberInformation.saved'));
      loadSubscriptions();
    } catch (err) {
      console.error('Failed to save member field mapping:', err);
      showError(err.response?.data?.message || t('subscriptions.memberInformation.saveFailed'));
    } finally {
      setSavingMapping(false);
    }
  };

  const openGroupPicker = () => {
    setGroupPickerSearch('');
    setGroupPickerPage(1);
    setShowGroupPicker(true);
  };

  // One page at a time from the server, so the list is not capped by what was fetched.
  const loadGroupPicker = useCallback(async () => {
    setGroupPickerLoading(true);
    try {
      const response = await dataHolderGroupsApi.getAll({
        page: groupPickerPage - 1,
        size: groupPickerPageSize,
        search: groupPickerSearch.trim() || undefined,
        activeOnly: true,
        sortBy: 'name',
        sortDir: 'asc',
      });
      const data = response.data.data || {};
      setGroupPickerList(data.content || []);
      setGroupPickerTotal(data.totalElements || 0);
    } catch (err) {
      console.error('Failed to load data holder groups:', err);
      showError(t('subscriptions.newSubscription.loadFailed'));
      setGroupPickerList([]);
      setGroupPickerTotal(0);
    } finally {
      setGroupPickerLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupPickerPage, groupPickerPageSize, groupPickerSearch]);

  useEffect(() => {
    if (!showGroupPicker) return undefined;
    const timer = setTimeout(() => loadGroupPicker(), 250);
    return () => clearTimeout(timer);
  }, [showGroupPicker, loadGroupPicker]);

  const generateSigningKeys = async () => {
    if (!selectedItem) return;
    const ok = await confirm(
      t('subscriptions.signingKeys.confirmTitle'),
      t('subscriptions.signingKeys.confirmBody'),
      null, null,
      { confirmText: t(signingKey ? 'subscriptions.signingKeys.regenerate' : 'subscriptions.signingKeys.generate'),
        confirmVariant: 'warning', icon: 'fa-key', iconColor: 'warning' });
    if (!ok) return;
    setGeneratingKeys(true);
    try {
      const res = await subscriptionsApi.regenerateSigningKeys(selectedItem.id);
      setSigningKey(res.data?.data?.publicKey || '');
      success(t('subscriptions.signingKeys.generated'));
      loadSubscriptions();
    } catch (err) {
      showError(err.response?.data?.message || t('subscriptions.signingKeys.failed'));
    } finally {
      setGeneratingKeys(false);
    }
  };

  const copyText = async (text, label) => {
    try {
      await navigator.clipboard.writeText(text);
      success(t('subscriptions.signingKeys.copied', { label }));
    } catch {
      showError(t('subscriptions.signingKeys.copyFailed'));
    }
  };

  const saveDhgCredentials = async () => {
    if (!selectedItem) return;
    setSavingCreds(true);
    try {
      await subscriptionsApi.updateDhgCredentials(
        selectedItem.id, credForm.clientId, credForm.clientSecret);
      success(t('subscriptions.dhgCredentials.saved'));
      setCredForm(prev => ({ ...prev, clientSecret: '' }));
      loadSubscriptions();
    } catch (err) {
      showError(err.response?.data?.message || t('subscriptions.dhgCredentials.saveFailed'));
    } finally {
      setSavingCreds(false);
    }
  };

  const closeDetail = () => {
    setShowDetailModal(false);
    setTermsMode('current');
    setShowRequestData(false);
    setProposedAccepted([]);
    setProposedReviewed([]);
    setProposedValues({});
  };

  const setProposedValue = (name, value) =>
    setProposedValues(prev => ({ ...prev, [name]: value }));

  const toggleProposedSection = (id) =>
    setProposedAccepted(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]);

  const toggleProposedLink = (name) =>
    setProposedReviewed(prev => prev.includes(name) ? prev.filter(x => x !== name) : [...prev, name]);

  const termsProposed = termsMode === 'proposed' && !!selectedItem?.pendingTemplateSnapshot;
  const termsSnapshot = termsProposed
    ? selectedItem?.pendingTemplateSnapshot
    : selectedItem?.templateSnapshot;

  /*
   * A change replaces the terms the subscription runs under, so accepting it means
   * accepting those terms afresh — every section, and every document the group asks be
   * read. Until all of them are ticked there is nothing to accept.
   */
  const proposedSections = termsProposed ? (termsSnapshot?.legalSections || []) : [];
  const proposedLinks = termsProposed
    ? (termsSnapshot?.subscriptionFields || []).filter(f => f.dataType === 'url' && f.required)
    : [];
  const proposedRequiredFields = termsProposed
    ? (termsSnapshot?.subscriptionFields || []).filter(f => f.dataType !== 'url' && f.required)
    : [];
  const fieldAnswered = (f) => {
    const v = proposedValues[f.name];
    return f.dataType === 'checkbox'
      ? v === true
      : v !== undefined && v !== null && String(v).trim() !== '';
  };
  const proposedFullyReviewed =
    proposedSections.every(sec => proposedAccepted.includes(sec.id))
    && proposedLinks.every(f => proposedReviewed.includes(f.name))
    && proposedRequiredFields.every(fieldAnswered);

  /*
   * The requests table is server-side paginated: `subscriptions` already holds the
   * current page, filtered by search / status / group on the backend. Render it directly.
   */

  const getStatusBadge = (status) => {
    const statusConfig = {
      DRAFT: { color: 'secondary', icon: 'fa-edit', label: t('subscriptions.status.draft') },
      SUBMITTED: { color: 'info', icon: 'fa-paper-plane', label: t('subscriptions.status.submitted') },
      PENDING_REVIEW: { color: 'warning', icon: 'fa-clock', label: t('subscriptions.status.pendingReview') },
      APPROVED: { color: 'primary', icon: 'fa-thumbs-up', label: t('subscriptions.status.approved') },
      TESTING: { color: 'info', icon: 'fa-flask', label: t('subscriptions.status.testing') },
      ACTIVE: { color: 'success', icon: 'fa-check-circle', label: t('subscriptions.status.active') },
      DECLINED: { color: 'danger', icon: 'fa-times-circle', label: t('subscriptions.status.declined') },
      SUSPENDED: { color: 'warning', icon: 'fa-pause-circle', label: t('subscriptions.status.suspended') },
      CANCELLED: { color: 'secondary', icon: 'fa-ban', label: t('subscriptions.status.cancelled') },
      EXPIRED: { color: 'secondary', icon: 'fa-hourglass-end', label: t('subscriptions.status.expired') },
    };
    const config = statusConfig[status] || { color: 'secondary', icon: 'fa-question', label: status };
    return (
      <span className={`badge bg-${config.color}`}>
        <i className={`fas ${config.icon} me-1`}></i>
        {config.label}
      </span>
    );
  };

  const getAccessLevelBadge = (level) => {
    const levels = {
      0: { label: t('subscriptions.accessLevel.public'), color: 'secondary' },
      1: { label: t('subscriptions.accessLevel.basic'), color: 'info' },
      2: { label: t('subscriptions.accessLevel.enhanced'), color: 'warning' },
      3: { label: t('subscriptions.accessLevel.full'), color: 'danger' },
    };
    const config = levels[level] || levels[0];
    return <span className={`badge bg-${config.color}`}>{t('subscriptions.level', { level })}</span>;
  };

  const getRequestTypeBadge = (rt) => {
    if (rt.supportsExigent) {
      return <span className="badge bg-danger"><i className="fas fa-bolt me-1"></i>{t('subscriptions.requestType.exigent')}</span>;
    }
    if (rt.supportsConfidential) {
      return <span className="badge bg-warning text-dark"><i className="fas fa-lock me-1"></i>{t('subscriptions.requestType.confidential')}</span>;
    }
    return <span className="badge bg-secondary"><i className="fas fa-globe me-1"></i>{t('subscriptions.requestType.standard')}</span>;
  };

  const renderRequestTypes = (requestTypes) => {
    if (!requestTypes || requestTypes.length === 0) return null;
    return (
      <div className="d-flex flex-wrap gap-2">
        {requestTypes.map((rt, idx) => (
          <div
            key={idx}
            className="border rounded px-2 py-1 d-inline-flex align-items-center gap-1"
            style={{ fontSize: '0.8rem' }}
          >
            {getRequestTypeBadge(rt)}
            {getAccessLevelBadge(rt.accessLevel)}
          </div>
        ))}
      </div>
    );
  };

  const canManage = isGroupAdmin() || isMasterAdmin();

  if (loading) {
    return <Loading message={t('subscriptions.loadingSubscriptions')} />;
  }

  /*
   * Badge counts come from the role-scoped /stats endpoint so they reflect ALL matching
   * requests, not just the current page. The polling indicator, however, only reflects the
   * in-flight requests visible on the current page (polling operates per-page).
   */
  const byStatus = stats.byStatus || {};
  const countStatuses = (...statuses) =>
    statuses.reduce((sum, st) => sum + (byStatus[st] || 0), 0);
  const totalRequests = stats.total || 0;
  const pendingCount = countStatuses('DRAFT', 'SUBMITTED', 'PENDING_REVIEW');
  const approvedCount = countStatuses('APPROVED');
  const testingCount = countStatuses('TESTING');
  const actionableCount = approvedCount + testingCount;
  const pollableCount = subscriptions.filter(s => POLLABLE_STATUSES.includes(s.status)).length;
  const activeCount = countStatuses('ACTIVE');

  return (
    <div>
      {/* Page Header */}
      <div className="page-header d-flex justify-content-between align-items-start">
        <div>
          <h1>{t('subscriptions.title')}</h1>
          <p>{t('subscriptions.subtitle')}</p>
        </div>
        <button className="btn btn-primary" onClick={openGroupPicker}>
          <i className="fas fa-plus me-2"></i>
          {t('subscriptions.newSubscription.button')}
        </button>
      </div>

      {/* Summary Cards */}
      <div className="row mb-4">
        <div className="col-md-3">
          <div className="card bg-primary text-white">
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-center">
                <div>
                  <h6 className="card-subtitle mb-1 opacity-75">{t('subscriptions.summary.totalRequests')}</h6>
                  <h2 className="card-title mb-0">{totalRequests}</h2>
                </div>
                <i className="fas fa-file-signature fa-2x opacity-50"></i>
              </div>
            </div>
          </div>
        </div>
        <div className="col-md-3">
          <div className="card bg-warning text-dark">
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-center">
                <div>
                  <h6 className="card-subtitle mb-1 opacity-75">{t('subscriptions.summary.pending')}</h6>
                  <h2 className="card-title mb-0">{pendingCount}</h2>
                </div>
                <i className="fas fa-clock fa-2x opacity-50"></i>
              </div>
            </div>
          </div>
        </div>
        <div className="col-md-3">
          <div className="card bg-info text-white">
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-center">
                <div>
                  <h6 className="card-subtitle mb-1 opacity-75">{t('subscriptions.summary.actionRequired')}</h6>
                  <h2 className="card-title mb-0">{actionableCount}</h2>
                </div>
                <i className="fas fa-flask fa-2x opacity-50"></i>
              </div>
            </div>
          </div>
        </div>
        <div className="col-md-3">
          <div className="card bg-success text-white">
            <div className="card-body">
              <div className="d-flex justify-content-between align-items-center">
                <div>
                  <h6 className="card-subtitle mb-1 opacity-75">{t('subscriptions.summary.activeAgreements')}</h6>
                  <h2 className="card-title mb-0">{activeCount}</h2>
                </div>
                <i className="fas fa-check-circle fa-2x opacity-50"></i>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Action Required Alert */}
      {actionableCount > 0 && (
        <div className="alert alert-info mb-4">
          <div className="d-flex align-items-center">
            <i className="fas fa-info-circle fa-lg me-3"></i>
            <div>
              <strong>{t('subscriptions.alert.actionRequiredLabel')}</strong> {t('subscriptions.alert.actionRequiredText', { count: actionableCount })}
              {approvedCount > 0 && (
                <span className="ms-2">
                  <span className="badge bg-primary me-1">
                    {t('subscriptions.alert.readyForTesting', { count: approvedCount })}
                  </span>
                </span>
              )}
              {testingCount > 0 && (
                <span className="ms-2">
                  <span className="badge bg-info">
                    {t('subscriptions.alert.inTesting', { count: testingCount })}
                  </span>
                </span>
              )}
            </div>
          </div>
        </div>
      )}

      {/* One list for the whole lifecycle; status is a filter, not a separate tab. */}
      <div className="d-flex align-items-center gap-2 mb-3">
        {activeCount > 0 && (
          <button className={`btn btn-sm ${filterStatus === 'ACTIVE' ? 'btn-success' : 'btn-outline-success'}`}
                  onClick={() => setFilterStatus(filterStatus === 'ACTIVE' ? '' : 'ACTIVE')}>
            <i className="fas fa-handshake me-1"></i>
            {t('subscriptions.filters.activeOnly')}
            <span className="badge bg-light text-dark ms-2">{activeCount}</span>
          </button>
        )}
        {(pendingCount + actionableCount) > 0 && (
          <span className="badge bg-warning text-dark">
            <i className="fas fa-file-signature me-1"></i>
            {t('subscriptions.filters.needsAttention', { count: pendingCount + actionableCount })}
          </span>
        )}
      </div>

      {/* Filters */}
      <div className="card mb-4">
        <div className="card-body">
          <div className="row g-3">
            <div className="col-md-4">
              <div className="input-group">
                <span className="input-group-text">
                  <i className="fas fa-search"></i>
                </span>
                <input
                  type="text"
                  className="form-control"
                  placeholder={t('subscriptions.filters.searchPlaceholder')}
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                />
              </div>
            </div>
            {(
              <div className="col-md-3">
                <select
                  className="form-select"
                  value={filterStatus}
                  onChange={(e) => setFilterStatus(e.target.value)}
                >
                  <option value="">{t('subscriptions.filters.allStatuses')}</option>
                  <option value="DRAFT">{t('subscriptions.status.draft')}</option>
                  <option value="SUBMITTED">{t('subscriptions.status.submitted')}</option>
                  <option value="PENDING_REVIEW">{t('subscriptions.status.pendingReview')}</option>
                  <option value="APPROVED">{t('subscriptions.filters.approvedOption')}</option>
                  <option value="TESTING">{t('subscriptions.status.testing')}</option>
                  <option value="ACTIVE">{t('subscriptions.status.active')}</option>
                  <option value="DECLINED">{t('subscriptions.status.declined')}</option>
                  <option value="SUSPENDED">{t('subscriptions.status.suspended')}</option>
                  <option value="CANCELLED">{t('subscriptions.status.cancelled')}</option>
                </select>
              </div>
            )}
            <div className="col-md-3">
              <select
                className="form-select"
                value={filterGroup}
                onChange={(e) => setFilterGroup(e.target.value)}
              >
                <option value="">{t('subscriptions.filters.allGroups')}</option>
                {requestorGroups.map((group) => (
                  <option key={group.id} value={group.id}>
                    {group.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="col-md-2">
              <button className="btn btn-outline-primary w-100" onClick={refreshAll}>
                <i className="fas fa-sync-alt me-2"></i>{t('subscriptions.filters.refresh')}
              </button>
            </div>
          </div>
          {pollableCount > 0 && (
            <div className="d-flex align-items-center gap-2 mt-2">
              <span
                style={{
                  display: 'inline-block', width: '8px', height: '8px',
                  borderRadius: '50%', backgroundColor: '#22c55e',
                  animation: 'pulse-dot 2s infinite',
                }}
              ></span>
              <small className="text-muted">
                {t('subscriptions.polling.autoChecking', { count: pollableCount, plural: pollableCount !== 1 ? 's' : '' })}
                {lastPolled && (
                  <span className="ms-1">&middot; {t('subscriptions.polling.lastChecked', { time: lastPolled.toLocaleTimeString() })}</span>
                )}
              </small>
              <style>{`
                @keyframes pulse-dot {
                  0%, 100% { opacity: 1; }
                  50% { opacity: 0.3; }
                }
              `}</style>
            </div>
          )}
        </div>
      </div>

      {/* Content */}
      {(
        subscriptions.length > 0 ? (
          <div className="table-responsive">
            <table className="table table-hover">
              <thead className="table-light">
                <tr>
                  <th>{t('subscriptions.table.requestId')}</th>
                  <th>{t('subscriptions.table.template')}</th>
                  <th>{t('subscriptions.table.dataHolderGroup')}</th>
                  <th>{t('subscriptions.table.requestorGroup')}</th>
                  <th>{t('common.status')}</th>
                  <th>{t('subscriptions.table.requestedBy')}</th>
                  <th>{t('subscriptions.table.created')}</th>
                  <th>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {subscriptions.map((sub) => (
                  <tr key={sub.id} className={POLLABLE_STATUSES.includes(sub.status) ? 'table-info' : ''}>
                    <td>
                      <code className="small">{sub.internalRequestId}</code>
                      {sub.agreementId && (
                        <div className="small text-muted" title={t('subscriptions.table.agreementId')}>
                          <i className="fas fa-handshake me-1"></i><code>{sub.agreementId}</code>
                        </div>
                      )}
                    </td>
                    <td>
                      <strong>{sub.templateName || sub.templateId}</strong>
                    </td>
                    <td>
                      <span className="badge bg-light text-dark">{sub.dataHolderGroupCode}</span>
                      <div className="small text-muted">{sub.dataHolderGroupName}</div>
                    </td>
                    <td>{sub.requestorGroupName}</td>
                    <td>
                      {getStatusBadge(sub.status)}
                      {/*
                        * Whether this subscription can authenticate to its data holder
                        * group. Without credentials its calls are refused, and that is not
                        * visible from the workflow status alone.
                        */}
                      <div className="mt-1">
                        {sub.hasDhgClientSecret && sub.dhgClientId ? (
                          <span className="badge bg-success-subtle text-success-emphasis border border-success-subtle">
                            <i className="fas fa-key me-1"></i>
                            {t('subscriptions.dhgCredentials.configured')}
                          </span>
                        ) : (
                          <span
                            className="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle"
                            title={t('subscriptions.dhgCredentials.missingHelp', { group: sub.dataHolderGroupName })}
                          >
                            <i className="fas fa-triangle-exclamation me-1"></i>
                            {t('subscriptions.dhgCredentials.missing')}
                          </span>
                        )}
                      </div>
                      {POLLABLE_STATUSES.includes(sub.status) && (
                        <div className="d-flex align-items-center gap-1 mt-1">
                          <span
                            className={`spinner-grow spinner-grow-sm ${
                              sub.status === 'APPROVED' ? 'text-primary' :
                              sub.status === 'TESTING' ? 'text-info' : 'text-warning'
                            }`}
                            role="status"
                            style={{ width: '0.5rem', height: '0.5rem' }}
                          ></span>
                          <small className="text-muted">
                            {sub.status === 'SUBMITTED' && t('subscriptions.hints.awaitingReview')}
                            {sub.status === 'PENDING_REVIEW' && t('subscriptions.hints.underReview')}
                            {sub.status === 'APPROVED' && t('subscriptions.hints.autoTestingQueued')}
                            {sub.status === 'TESTING' && (isAutoHalted(sub)
                              ? t('subscriptions.hints.autoHalted')
                              : (canActivate(sub)
                                  ? t('subscriptions.hints.autoActivating')
                                  : t('subscriptions.hints.autoTestingInProgress')))}
                          </small>
                        </div>
                      )}
                      {sub.pendingChangeStatus === 'PROPOSED' && (
                        <div className="alert alert-warning p-2 mt-2 mb-0">
                          <div className="fw-semibold small">
                            <i className="fas fa-triangle-exclamation me-1"></i>
                            {sub.pendingChangeMode === 'FORCED'
                              ? t('subscriptions.pendingChange.titleRequired')
                              : t('subscriptions.pendingChange.title')}
                          </div>
                          <div className="small text-muted mb-2">
                            {sub.pendingChangeMode === 'FORCED'
                              ? t('subscriptions.pendingChange.helpRequired', {
                                  date: sub.pendingChangeDeadline
                                    ? new Date(sub.pendingChangeDeadline).toLocaleDateString()
                                    : '',
                                })
                              : t('subscriptions.pendingChange.help')}
                          </div>
                          {/* Accept and decline live in the review itself: the decision is
                              not offered until the new terms have actually been opened. */}
                          <button className="btn btn-sm btn-outline-dark"
                                  onClick={() => handleViewDetails(sub, 'proposed')}>
                            <i className="fas fa-file-contract me-1"></i>
                            {t('subscriptions.pendingChange.reviewAndRespond')}
                          </button>
                        </div>
                      )}
                      {sub.pendingChangeStatus === 'DECLINED' && (
                        <div className="small text-muted mt-1">
                          <i className="fas fa-ban me-1"></i>{t('subscriptions.pendingChange.declined')}
                        </div>
                      )}
                      {sub.nextStepHint && !POLLABLE_STATUSES.includes(sub.status) && (
                        <div className="small text-muted mt-1" title={sub.nextStepHint}>
                          {sub.nextStepHint.substring(0, 40)}
                          {sub.nextStepHint.length > 40 && '...'}
                        </div>
                      )}
                      {!sub.nextStepHint && sub.statusMessage && !POLLABLE_STATUSES.includes(sub.status) && (
                        <div className="small text-muted mt-1" title={sub.statusMessage}>
                          {sub.statusMessage.substring(0, 30)}
                          {sub.statusMessage.length > 30 && '...'}
                        </div>
                      )}
                    </td>
                    <td>
                      <div>{`${sub.requestorFirstName || ''} ${sub.requestorLastName || ''}`.trim()}</div>
                      <small className="text-muted">{sub.requestorEmail}</small>
                    </td>
                    <td>
                      <small>
                        {new Date(sub.createdAt).toLocaleDateString()}
                        <br />
                        <span className="text-muted">{new Date(sub.createdAt).toLocaleTimeString()}</span>
                      </small>
                    </td>
                    <td>
                      <div className="btn-group btn-group-sm">
                        <button className="btn btn-outline-info" onClick={() => handleViewDetails(sub)} title={t('common.viewDetails')}>
                          <i className="fas fa-eye"></i>
                        </button>
                        {sub.status === 'DRAFT' && canManage && (
                          <button className="btn btn-outline-primary" onClick={() => handleSubmitDraft(sub)} title={t('subscriptions.actions.submit')}>
                            <i className="fas fa-paper-plane"></i>
                          </button>
                        )}
                        {POLLABLE_STATUSES.includes(sub.status) && (
                          <button className="btn btn-outline-secondary" onClick={() => handleRefreshStatus(sub)} disabled={refreshing[sub.id]} title={t('subscriptions.actions.refreshStatus')}>
                            {refreshing[sub.id] ? <span className="spinner-border spinner-border-sm"></span> : <i className="fas fa-sync-alt"></i>}
                          </button>
                        )}
                        {/* Testing & activation are automated after approval. Show a read-only
                            progress indicator; expose manual controls only if automation halts. */}
                        {(sub.status === 'APPROVED' || (sub.status === 'TESTING' && !isAutoHalted(sub))) && (
                          <span className="btn btn-outline-secondary disabled d-inline-flex align-items-center" title={t('subscriptions.actions.automatedInProgress')}>
                            <span className="spinner-border spinner-border-sm me-1"></span>
                            <i className="fas fa-robot"></i>
                          </span>
                        )}
                        {sub.status === 'TESTING' && isAutoHalted(sub) && canManage && (
                          <>
                            <button className="btn btn-outline-info" onClick={() => handleRunTest(sub)} disabled={actionLoading[`test-${sub.id}`]} title={t('subscriptions.actions.runTests')}>
                              {actionLoading[`test-${sub.id}`] ? <span className="spinner-border spinner-border-sm"></span> : <i className="fas fa-play"></i>}
                            </button>
                            {canActivate(sub) && (
                              <button className="btn btn-success" onClick={() => handleActivate(sub)} disabled={actionLoading[`activate-${sub.id}`]} title={t('subscriptions.actions.activate')}>
                                {actionLoading[`activate-${sub.id}`] ? <span className="spinner-border spinner-border-sm"></span> : <i className="fas fa-check-circle"></i>}
                              </button>
                            )}
                          </>
                        )}
                        {['DRAFT', 'SUBMITTED', 'PENDING_REVIEW'].includes(sub.status) && canManage && (
                          <button className="btn btn-outline-danger" onClick={() => handleCancel(sub)} title={t('common.cancel')}>
                            <i className="fas fa-times"></i>
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination
              page={page}
              pageSize={pageSize}
              totalItems={totalItems}
              onPageChange={setPage}
              onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
              itemLabel={t('subscriptions.pagination.requests')}
            />
          </div>
        ) : (
          <div className="empty-state">
            <div className="empty-icon"><i className="fas fa-file-signature"></i></div>
            <h5>{t('subscriptions.empty.requestsTitle')}</h5>
            <p>
              {searchTerm || filterStatus || filterGroup
                ? t('subscriptions.empty.adjustCriteria')
                : t('subscriptions.empty.requestsHint')}
            </p>
          </div>
        )
      )}

      {/* Detail modal: status and actions, the request data in a drawer, then the terms. */}
      <Modal
        show={showDetailModal}
        onHide={closeDetail}
        title={termsProposed
          ? t('subscriptions.terms.proposedTitle', { name: selectedItem?.templateName || selectedItem?.templateId })
          : t('subscriptions.modal.requestDetails')}
        size="xl"
        scrollable
        footer={
          <>
            {termsProposed && selectedItem.pendingChangeStatus === 'PROPOSED' && !proposedFullyReviewed && (
              <span className="text-muted small me-auto">
                <i className="fas fa-info-circle me-1"></i>
                {t('subscriptions.pendingChange.reviewFirst')}
              </span>
            )}
            <button type="button" className="btn btn-secondary" onClick={closeDetail}>{t('common.close')}</button>
            {termsProposed && selectedItem.pendingChangeStatus === 'PROPOSED' && (
              <>
                {selectedItem.pendingChangeMode !== 'FORCED' && (
                  <button className="btn btn-outline-secondary"
                          disabled={respondingId === selectedItem.id}
                          onClick={() => { respondToChange(selectedItem, false); closeDetail(); }}>
                    {t('subscriptions.pendingChange.decline')}
                  </button>
                )}
                <button className="btn btn-success"
                        disabled={respondingId === selectedItem.id || !proposedFullyReviewed}
                        onClick={() => { respondToChange(selectedItem, true, proposedValues); closeDetail(); }}>
                  {t('subscriptions.pendingChange.accept')}
                </button>
              </>
            )}
          </>
        }
      >
        {selectedItem && (
          <div>
            <div className={`alert alert-${
              selectedItem.status === 'ACTIVE' ? 'success' :
              selectedItem.status === 'APPROVED' ? 'primary' :
              selectedItem.status === 'TESTING' ? 'info' :
              selectedItem.status === 'DECLINED' ? 'danger' :
              selectedItem.status === 'CANCELLED' ? 'secondary' : 'warning'
            } mb-4`}>
              <div className="d-flex justify-content-between align-items-center">
                <div><strong>{t('common.status')}: </strong>{getStatusBadge(selectedItem.status)}</div>
                {selectedItem.statusChangedAt && (
                  <small>{t('subscriptions.modal.updated', { time: new Date(selectedItem.statusChangedAt).toLocaleString() })}</small>
                )}
              </div>
              {selectedItem.statusMessage && <div className="mt-2 small">{selectedItem.statusMessage}</div>}
              {selectedItem.nextStepHint && (
                <div className="mt-2 small fst-italic">
                  <i className="fas fa-lightbulb me-1"></i>{selectedItem.nextStepHint}
                </div>
              )}
              {selectedItem.status === 'APPROVED' && canManage && (
                <div className="mt-3">
                  <button className="btn btn-info" onClick={() => { setShowDetailModal(false); handleStartTesting(selectedItem); }}>
                    <i className="fas fa-flask me-2"></i>{t('subscriptions.actions.startTesting')}
                  </button>
                </div>
              )}
              {selectedItem.status === 'TESTING' && canManage && (
                <div className="mt-3">
                  <button className="btn btn-outline-info me-2" onClick={() => { setShowDetailModal(false); handleRunTest(selectedItem); }}>
                    <i className="fas fa-play me-2"></i>{t('subscriptions.actions.runTests')}
                  </button>
                  {canActivate(selectedItem) ? (
                    <button className="btn btn-success" onClick={() => { setShowDetailModal(false); handleActivate(selectedItem); }}>
                      <i className="fas fa-check-circle me-2"></i>{t('subscriptions.actions.activate')}
                    </button>
                  ) : (
                    <span className="text-muted small ms-2">
                      <i className="fas fa-info-circle me-1"></i>{t('subscriptions.modal.runTestsHint')}
                    </span>
                  )}
                </div>
              )}
            </div>

            <div className="card mb-4">
              <div className="card-header p-0">
                <button type="button"
                        className="btn btn-link w-100 text-start text-decoration-none px-3 py-2 fw-semibold"
                        onClick={() => setShowRequestData(v => !v)}
                        aria-expanded={showRequestData}
                        aria-controls="subscription-request-data">
                  <i className={`fas fa-chevron-${showRequestData ? 'down' : 'right'} me-2`}></i>
                  {t('subscriptions.modal.requestData')}
                </button>
              </div>
              <div id="subscription-request-data" className={`collapse ${showRequestData ? 'show' : ''}`}>
                <div className="card-body pt-2">
                  <div className="row">
                    <div className="col-md-6">
                      <h6 className="text-muted mb-3">{t('subscriptions.modal.requestInformation')}</h6>
                      <table className="table table-sm">
                        <tbody>
                          <tr><th className="w-40">{t('subscriptions.modal.requestId')}</th><td><code>{selectedItem.internalRequestId}</code></td></tr>
                          {selectedItem.externalRequestId && <tr><th>{t('subscriptions.modal.externalId')}</th><td><code>{selectedItem.externalRequestId}</code></td></tr>}
                          <tr><th>{t('subscriptions.modal.template')}</th><td>{selectedItem.templateName || selectedItem.templateId}</td></tr>
                          <tr><th>{t('subscriptions.modal.dataHolderGroup')}</th><td>{selectedItem.dataHolderGroupName} ({selectedItem.dataHolderGroupCode})</td></tr>
                          <tr><th>{t('subscriptions.modal.requestorGroup')}</th><td>{selectedItem.requestorGroupName}</td></tr>
                          {selectedItem.requestTypes?.length > 0 && (
                            <tr>
                              <th>{t('subscriptions.modal.requestTypes')}</th>
                              <td>{renderRequestTypes(selectedItem.requestTypes)}</td>
                            </tr>
                          )}
                          <tr><th>{t('subscriptions.modal.created')}</th><td>{new Date(selectedItem.createdAt).toLocaleString()}</td></tr>
                          {selectedItem.submittedAt && <tr><th>{t('subscriptions.modal.submitted')}</th><td>{new Date(selectedItem.submittedAt).toLocaleString()}</td></tr>}
                          {selectedItem.expiresAt && <tr><th>{t('subscriptions.modal.expires')}</th><td>{new Date(selectedItem.expiresAt).toLocaleString()}</td></tr>}
                        </tbody>
                      </table>
                    </div>
                    <div className="col-md-6">
                      <h6 className="text-muted mb-3">{t('subscriptions.modal.contactInformation')}</h6>
                      <table className="table table-sm">
                        <tbody>
                          <tr><th className="w-40">{t('subscriptions.modal.fullName')}</th><td>{`${selectedItem.requestorFirstName || ''} ${selectedItem.requestorLastName || ''}`.trim()}</td></tr>
                          {selectedItem.requestorOrganization && <tr><th>{t('subscriptions.modal.organization')}</th><td>{selectedItem.requestorOrganization}</td></tr>}
                          <tr><th>{t('subscriptions.modal.email')}</th><td>{selectedItem.requestorEmail}</td></tr>
                          {selectedItem.requestorPhone && <tr><th>{t('subscriptions.modal.phone')}</th><td>{selectedItem.requestorPhone}</td></tr>}
                        </tbody>
                      </table>
                      <DataHolderContact contact={dataHolderContact} compact />
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {(selectedItem.templateSnapshot?.userFields || []).length > 0 && (
              <div className="card mb-4">
                <div className="card-header d-flex justify-content-between align-items-center">
                  <h6 className="mb-0"><i className="fas fa-id-card me-2"></i>{t('subscriptions.memberInformation.title')}</h6>
                  {isRequestorGroupAdmin() && (
                    <button className="btn btn-sm btn-primary" onClick={saveMemberMapping} disabled={savingMapping}>
                      {savingMapping
                        ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('common.saving')}</>
                        : <><i className="fas fa-save me-1"></i>{t('subscriptions.memberInformation.save')}</>}
                    </button>
                  )}
                </div>
                <div className="card-body">
                  <p className="small text-muted">{t('subscriptions.memberInformation.help')}</p>
                  {selectedItem.templateSnapshot.userFields.map((f) => (
                    <div className="row g-2 align-items-center mb-2" key={f.key}>
                      <div className="col-md-5">
                        <label htmlFor={`map-${f.key}`} className="form-label mb-0">{f.label}</label>
                        {f.description && <div className="small text-muted">{f.description}</div>}
                      </div>
                      <div className="col-md-7">
                        <MemberFieldSelect
                          id={`map-${f.key}`}
                          value={memberMapping[f.key]}
                          groupFields={groupMemberFields}
                          disabled={!isRequestorGroupAdmin()}
                          placeholder={t('subscriptions.memberInformation.unmapped')}
                          standardLabel={t('dataHolderGroups.subscribe.standardMemberFields')}
                          customLabel={t('dataHolderGroups.subscribe.customMemberFields')}
                          onChange={(v) => setMemberMapping({ ...memberMapping, [f.key]: v })}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {termsProposed ? (
              <div className="alert alert-warning py-2 small">
                {selectedItem.pendingChangeMode === 'FORCED'
                  ? t('subscriptions.terms.proposedNoticeRequired', {
                      date: selectedItem.pendingChangeDeadline
                        ? new Date(selectedItem.pendingChangeDeadline).toLocaleDateString()
                        : '—',
                    })
                  : t('subscriptions.terms.proposedNotice')}
              </div>
            ) : (
              <div className="alert alert-secondary py-2 small">
                {t('subscriptions.terms.inForceSince', {
                  date: termsSnapshot?.capturedAt
                    ? new Date(termsSnapshot.capturedAt).toLocaleString()
                    : '—',
                })}
              </div>
            )}

            <h6 className="fw-bold">{t('subscriptions.terms.dataHolderGroupFields')}</h6>
            {(termsSnapshot?.legalSections || []).length === 0
             && !(termsSnapshot?.subscriptionFields || []).some(f => f.dataType === 'url') ? (
              <p className="text-muted small fst-italic">{t('subscriptions.terms.noSections')}</p>
            ) : (
              (termsSnapshot?.legalSections || []).map((sec, sectionIndex) => {
                const accepted = termsProposed
                  ? null
                  : (selectedItem.acceptedTerms || []).find(a => a.sectionId === sec.id);
                const refIndex = buildReferenceIndex(termsSnapshot?.legalSections);
                const lines = sectionClauseLines(sec, sectionIndex + 1, refIndex);
                return (
                  <div className="card mb-2" key={sec.id}>
                    <div className="card-body py-2">
                      <div className="d-flex justify-content-between align-items-start">
                        <div className="fw-semibold">{sectionIndex + 1}. {sec.title}</div>
                        {accepted && (
                          <span className="badge bg-success-subtle text-success border">
                            <i className="fas fa-check me-1"></i>
                            {t('subscriptions.terms.acceptedOn', {
                              date: new Date(accepted.acceptedAt).toLocaleDateString(),
                            })}
                          </span>
                        )}
                      </div>
                      {termsProposed && (
                        <div className="form-check mt-2">
                          <input className="form-check-input" type="checkbox"
                                 id={`proposed-sec-${sec.id}`}
                                 checked={proposedAccepted.includes(sec.id)}
                                 onChange={() => toggleProposedSection(sec.id)} />
                          <label className="form-check-label" htmlFor={`proposed-sec-${sec.id}`}>
                            {t('subscriptions.pendingChange.acceptSection')} <span className="text-danger">*</span>
                          </label>
                        </div>
                      )}
                      {lines.length > 0 && (
                        <div className="bg-white px-1 pb-2 mt-2"
                             style={{ whiteSpace: 'pre-wrap', fontSize: 13 }}>
                          {lines.map((line) => (
                            <div className="mb-2" key={line.key}>
                              {line.label && <span className="fw-semibold me-1">{line.label}</span>}
                              {line.text}
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                );
              })
            )}

            {/* Links the group published for review sit with its terms, not with what we supplied. */}
            {(termsSnapshot?.subscriptionFields || [])
              .filter(f => f.dataType === 'url')
              .map((f) => (
                <div className="card mb-2" key={f.name}>
                  <div className="card-body py-2">
                    <div className="d-flex justify-content-between align-items-start">
                      <div className="fw-semibold">{f.name}</div>
                      {selectedItem.subscriptionFieldValues?.[f.name] === true && (
                        <span className="badge bg-success-subtle text-success border">
                          <i className="fas fa-check me-1"></i>{t('subscriptions.terms.reviewed')}
                        </span>
                      )}
                    </div>
                    {f.description && <div className="text-muted small">{f.description}</div>}
                    {f.defaultValue && (
                      <a href={f.defaultValue} target="_blank" rel="noopener noreferrer" className="small text-break">
                        <i className="fas fa-up-right-from-square me-1"></i>{f.defaultValue}
                      </a>
                    )}
                    {termsProposed && f.required && (
                      <div className="form-check mt-2">
                        <input className="form-check-input" type="checkbox"
                               id={`proposed-link-${f.name}`}
                               checked={proposedReviewed.includes(f.name)}
                               onChange={() => toggleProposedLink(f.name)} />
                        <label className="form-check-label" htmlFor={`proposed-link-${f.name}`}>
                          {t('subscriptions.pendingChange.acknowledgeLink')} <span className="text-danger">*</span>
                        </label>
                      </div>
                    )}
                  </div>
                </div>
              ))}

            {(termsSnapshot?.subscriptionFields || []).some(f => f.dataType !== 'url') && (
              <>
                <h6 className="fw-bold mt-3">{t('subscriptions.terms.requestorGroupFields')}</h6>
                {termsProposed ? (
                  /* A change can add fields that were never answered, so they are filled in here. */
                  <div className="mb-2">
                    {termsSnapshot.subscriptionFields
                      .filter(f => f.dataType !== 'url')
                      .map((f) => {
                        const value = proposedValues[f.name];
                        const limit = (f.dataType === 'string' || f.dataType === 'text')
                          ? (f.maxLength || undefined) : undefined;
                        const common = {
                          className: 'form-control form-control-sm',
                          id: `proposed-field-${f.name}`,
                          placeholder: f.placeholder || '',
                        };
                        return (
                          <div className="mb-3" key={f.name}>
                            {f.dataType === 'checkbox' ? (
                              <div className="form-check">
                                <input className="form-check-input" type="checkbox"
                                       id={`proposed-field-${f.name}`}
                                       checked={value === true}
                                       onChange={(e) => setProposedValue(f.name, e.target.checked)} />
                                <label className="form-check-label" htmlFor={`proposed-field-${f.name}`}>
                                  {f.name} {f.required && <span className="text-danger">*</span>}
                                </label>
                              </div>
                            ) : (
                              <>
                                <label className="form-label small mb-1" htmlFor={`proposed-field-${f.name}`}>
                                  {f.name} {f.required && <span className="text-danger">*</span>}
                                </label>
                                {f.dataType === 'select' ? (
                                  <select className="form-select form-select-sm"
                                          id={`proposed-field-${f.name}`} value={value ?? ''}
                                          onChange={(e) => setProposedValue(f.name, e.target.value)}>
                                    <option value="">{t('subscriptions.pendingChange.selectPlaceholder')}</option>
                                    {(f.enumValues || '').split(',').map(o => o.trim()).filter(Boolean).map(o => (
                                      <option key={o} value={o}>{o}</option>
                                    ))}
                                  </select>
                                ) : f.dataType === 'text' ? (
                                  <textarea {...common} rows={3} value={value ?? ''} maxLength={limit}
                                            onChange={(e) => setProposedValue(f.name, e.target.value)} />
                                ) : (
                                  <input {...common}
                                         type={f.dataType === 'number' ? 'number' : f.dataType === 'date' ? 'date' : 'text'}
                                         value={value ?? ''} maxLength={limit}
                                         min={f.minValue || undefined} max={f.maxValue || undefined}
                                         onChange={(e) => setProposedValue(f.name, e.target.value)} />
                                )}
                              </>
                            )}
                            {f.description && <small className="text-muted">{f.description}</small>}
                          </div>
                        );
                      })}
                  </div>
                ) : (
                  <table className="table table-sm">
                    <tbody>
                      {termsSnapshot.subscriptionFields
                        .filter(f => f.dataType !== 'url')
                        .map((f) => {
                          const v = selectedItem.subscriptionFieldValues?.[f.name];
                          return (
                            <tr key={f.name}>
                              <td className="text-muted" style={{ width: '40%' }}>{f.name}</td>
                              <td>{v === true ? t('common.yes') : v === false ? t('common.no')
                                    : (v === undefined || v === null || v === '') ? '—' : String(v)}</td>
                            </tr>
                          );
                        })}
                    </tbody>
                  </table>
                )}
              </>
            )}

            {(termsSnapshot?.requestTypes || []).length > 0 && (
              <>
                <h6 className="fw-bold mt-3">{t('subscriptions.terms.requestTypes')}</h6>
                <ul className="small mb-0">
                  {termsSnapshot.requestTypes.map((rt) => (
                    <li key={rt.typeCode || rt.name}>
                      {rt.name}
                      {rt.accessLevel != null && <span className="text-muted ms-2">L{rt.accessLevel}</span>}
                    </li>
                  ))}
                </ul>
              </>
            )}

            {/* Credentials the data holder group issued for this subscription. */}
            <div className="card mt-4">
              <div className="card-header bg-transparent">
                <h6 className="mb-0">
                  <i className="fas fa-key text-warning me-2"></i>
                  {t('subscriptions.dhgCredentials.title')}
                </h6>
              </div>
              <div className="card-body">
                <p className="text-muted small">{t('subscriptions.dhgCredentials.help')}</p>
                {/* State first: whether this subscription can authenticate at all. */}
                <div className={`alert ${selectedItem.hasDhgClientSecret && selectedItem.dhgClientId
                  ? 'alert-success' : 'alert-warning'} py-2 px-3 small d-flex align-items-start gap-2`}>
                  <i className={`fas ${selectedItem.hasDhgClientSecret && selectedItem.dhgClientId
                    ? 'fa-circle-check' : 'fa-triangle-exclamation'} mt-1`}></i>
                  <div>
                    <strong>
                      {selectedItem.hasDhgClientSecret && selectedItem.dhgClientId
                        ? t('subscriptions.dhgCredentials.configured')
                        : t('subscriptions.dhgCredentials.missing')}
                    </strong>
                    <div>
                      {selectedItem.hasDhgClientSecret && selectedItem.dhgClientId
                        ? t('subscriptions.dhgCredentials.configuredHelp', { group: selectedItem.dataHolderGroupName })
                        : t('subscriptions.dhgCredentials.missingHelp', { group: selectedItem.dataHolderGroupName })}
                    </div>
                  </div>
                </div>
                <div className="row g-3">
                  <div className="col-md-6">
                    <label className="form-label small" htmlFor="dhg-client-id">
                      {t('subscriptions.dhgCredentials.clientId')}
                    </label>
                    <input
                      type="text"
                      id="dhg-client-id"
                      className="form-control form-control-sm font-monospace"
                      value={credForm.clientId}
                      onChange={(e) => setCredForm(p => ({ ...p, clientId: e.target.value }))}
                      placeholder="sub-1a2b3c4d-5e6f7a8b"
                      autoComplete="off"
                    />
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small" htmlFor="dhg-client-secret">
                      {t('subscriptions.dhgCredentials.clientSecret')}
                    </label>
                    <input
                      type="password"
                      id="dhg-client-secret"
                      className="form-control form-control-sm font-monospace"
                      value={credForm.clientSecret}
                      onChange={(e) => setCredForm(p => ({ ...p, clientSecret: e.target.value }))}
                      placeholder={selectedItem.hasDhgClientSecret
                        ? t('subscriptions.dhgCredentials.keepStored')
                        : ''}
                      autoComplete="new-password"
                    />
                    {selectedItem.hasDhgClientSecret && (
                      <small className="text-muted">{t('subscriptions.dhgCredentials.keepStored')}</small>
                    )}
                  </div>
                </div>
                {/* Signing keys: the private half never leaves this service. */}
                <hr className="my-3" />
                <div className="d-flex justify-content-between align-items-center mb-2">
                  <label className="form-label small mb-0 fw-semibold">
                    {t('subscriptions.signingKeys.title')}
                  </label>
                  <button
                    type="button"
                    className="btn btn-outline-warning btn-sm"
                    onClick={generateSigningKeys}
                    disabled={generatingKeys}
                  >
                    <i className="fas fa-rotate me-1"></i>
                    {signingKey
                      ? t('subscriptions.signingKeys.regenerate')
                      : t('subscriptions.signingKeys.generate')}
                  </button>
                </div>
                <p className="text-muted small">{t('subscriptions.signingKeys.help')}</p>
                {signingKey ? (
                  <div className="input-group input-group-sm mb-2">
                    <input type="text" className="form-control font-monospace" value={signingKey} readOnly />
                    <button
                      className="btn btn-outline-secondary"
                      onClick={() => copyText(signingKey, t('subscriptions.signingKeys.publicKey'))}
                    >
                      <i className="fas fa-copy"></i>
                    </button>
                  </div>
                ) : (
                  <p className="text-muted small fst-italic">{t('subscriptions.signingKeys.none')}</p>
                )}

                <button
                  type="button"
                  className="btn btn-primary btn-sm mt-3"
                  onClick={saveDhgCredentials}
                  disabled={savingCreds}
                >
                  {savingCreds
                    ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('common.saving')}</>
                    : t('subscriptions.dhgCredentials.save')}
                </button>
              </div>
            </div>
          </div>
        )}
      </Modal>

      {/* ==================== Enhanced Test Result Modal ==================== */}
      <Modal
        show={showTestResultModal}
        onHide={() => setShowTestResultModal(false)}
        title={t('subscriptions.testModal.title')}
        size="xl"
        footer={
          <>
            {testResult?.result === 'PASSED' && selectedItem && canManage && (
              <button type="button" className="btn btn-success me-2" onClick={() => { setShowTestResultModal(false); handleActivate(selectedItem); }}>
                <i className="fas fa-check-circle me-2"></i>{t('subscriptions.testModal.activateSubscription')}
              </button>
            )}
            <button type="button" className="btn btn-secondary" onClick={() => setShowTestResultModal(false)}>{t('common.close')}</button>
          </>
        }
      >
        {testResult && (
          <div>
            {/* Overall Result Banner */}
            <div className={`alert alert-${testResult.result === 'PASSED' ? 'success' : testResult.result === 'PENDING' ? 'warning' : 'danger'} mb-4`}>
              <div className="d-flex align-items-center">
                <i className={`fas fa-${testResult.result === 'PASSED' ? 'check-circle' : testResult.result === 'PENDING' ? 'hourglass-half' : 'times-circle'} fa-2x me-3`}></i>
                <div>
                  <h5 className="mb-1">{testResult.result === 'PASSED' ? t('subscriptions.testModal.allTestsPassed')
                    : testResult.result === 'PENDING' ? t('subscriptions.testModal.testsPending')
                    : t('subscriptions.testModal.someTestsFailed')}</h5>
                  <p className="mb-0 small">{testResult.details}</p>
                </div>
              </div>
            </div>

            {/* Summary Bar */}
            <TestSummaryBar summary={testResult.summary} />

            {/* Request Info */}
            <div className="mb-4">
              <div className="row">
                <div className="col-md-6">
                  <strong>{t('subscriptions.testModal.requestId')}</strong> <code>{testResult.internalRequestId}</code>
                </div>
                <div className="col-md-6">
                  <strong>{t('subscriptions.testModal.testedAt')}</strong> {testResult.testedAt ? new Date(testResult.testedAt).toLocaleString() : t('common.na')}
                </div>
              </div>
            </div>

            {/* Validation Test Cases */}
            {testResult.testCases && testResult.testCases.length > 0 && (
              <div className="mb-4">
                <h6 className="mb-3">
                  <i className="fas fa-clipboard-check me-2 text-primary"></i>
                  {t('subscriptions.testModal.validationTests')}
                </h6>
                <div className="list-group">
                  {testResult.testCases.map((tc, index) => (
                    <div key={index} className={`list-group-item list-group-item-${tc.passed ? 'success' : tc.pending ? 'warning' : 'danger'} py-2`}>
                      <div className="d-flex justify-content-between align-items-start">
                        <div>
                          <div className="d-flex align-items-center gap-2">
                            <i className={`fas fa-${tc.passed ? 'check' : tc.pending ? 'hourglass-half' : 'times'}`}></i>
                            <strong>{tc.name}</strong>
                          </div>
                          <p className="mb-0 small ms-4">{tc.description}</p>
                          {tc.errorMessage && (
                            <p className={`mb-0 small ms-4 ${tc.pending ? 'text-muted' : 'text-danger'}`}>
                              {!tc.pending && <strong>{t('subscriptions.testModal.error')} </strong>}{tc.errorMessage}
                            </p>
                          )}
                        </div>
                        <span className={`badge bg-${tc.passed ? 'success' : tc.pending ? 'warning text-dark' : 'danger'}`}>
                          {tc.passed ? t('subscriptions.testModal.passed')
                            : tc.pending ? t('subscriptions.testModal.pending') : t('subscriptions.testModal.failed')}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {testResult.memberInformation && (
              <div className="mb-4">
                <h6 className="mb-2">
                  <i className="fas fa-id-card me-2 text-primary"></i>
                  {t('subscriptions.testModal.memberInformation')}
                </h6>
                {(testResult.memberInformation.member?.name || testResult.memberInformation.member?.email) && (
                  <p className="small text-muted mb-2">
                    {t('subscriptions.testModal.memberCheckedFor', {
                      member: [testResult.memberInformation.member?.name,
                        testResult.memberInformation.member?.email && `<${testResult.memberInformation.member.email}>`]
                        .filter(Boolean).join(' '),
                    })}
                  </p>
                )}
                <table className="table table-sm small mb-0">
                  <tbody>
                    {(testResult.memberInformation.fields || []).map((f) => (
                      <tr key={f.key}>
                        <th className="fw-normal text-muted" style={{ width: '40%' }}>{f.label}</th>
                        <td>
                          {f.value
                            ? f.value
                            : <span className="text-danger"><i className="fas fa-times-circle me-1"></i>{t('subscriptions.testModal.memberValueMissing')}</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {/* RDAP Test Results */}
            {testResult.rdapTestResults && testResult.rdapTestResults.length > 0 && (
              <div className="mb-4">
                <h6 className="mb-3">
                  <i className="fas fa-database me-2 text-info"></i>
                  {t('subscriptions.testModal.rdapDataTests')}
                  <span className="badge bg-info ms-2">{t('subscriptions.testModal.entities', { count: testResult.rdapTestResults.length })}</span>
                </h6>
                {testResult.rdapTestResults.map((rdapCase, index) => (
                  <RdapTestCaseCard key={index} rdapCase={rdapCase} defaultExpanded={index === 0} />
                ))}
              </div>
            )}

            {/* Footer messages */}
            {testResult.result === 'PASSED' && (
              <div className="alert alert-info mt-4">
                <i className="fas fa-info-circle me-2"></i>
                {t('subscriptions.testModal.passedMessage')}
              </div>
            )}
            {testResult.result === 'PENDING' && (
              <div className="alert alert-info mt-4">
                <i className="fas fa-info-circle me-2"></i>
                {t('subscriptions.testModal.pendingMessage')}
              </div>
            )}
            {testResult.result === 'FAILED' && (
              <div className="alert alert-warning mt-4">
                <i className="fas fa-exclamation-triangle me-2"></i>
                {t('subscriptions.testModal.failedMessage')}
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* New subscription, step one: which data holder group. */}
      <Modal
        show={showGroupPicker}
        onHide={() => setShowGroupPicker(false)}
        title={t('subscriptions.newSubscription.pickGroupTitle')}
        size="lg"
        scrollable
        footer={
          <button type="button" className="btn btn-secondary" onClick={() => setShowGroupPicker(false)}>
            {t('common.close')}
          </button>
        }
      >
        <p className="text-muted small">{t('subscriptions.newSubscription.pickGroupHelp')}</p>
        <input
          type="search"
          className="form-control form-control-sm mb-3"
          value={groupPickerSearch}
          onChange={(e) => { setGroupPickerSearch(e.target.value); setGroupPickerPage(1); }}
          autoComplete="off"
          placeholder={t('subscriptions.newSubscription.searchPlaceholder')}
        />
        {groupPickerLoading ? (
          <div className="text-center py-4">
            <span className="spinner-border text-primary" role="status"></span>
            <p className="mt-2 mb-0 text-muted">{t('subscriptions.newSubscription.loading')}</p>
          </div>
        ) : groupPickerTotal === 0 ? (
          <p className="text-muted fst-italic mb-0">{t('subscriptions.newSubscription.noGroups')}</p>
        ) : (
            <>
            <div className="list-group">
              {groupPickerList.map((group) => (
                <button
                  type="button"
                  className="list-group-item list-group-item-action d-flex justify-content-between align-items-center gap-3 py-3"
                  key={group.id}
                  onClick={() => { setShowGroupPicker(false); setSubscribeTarget(group); }}
                >
                  {/* Always two lines, each truncated, so every row is the same height. */}
                  <span className="flex-grow-1" style={{ minWidth: 0 }}>
                    <span className="d-flex align-items-center gap-2">
                      <span className="fw-semibold text-truncate">{group.name}</span>
                      {group.code && (
                        <span className="badge bg-light text-dark border flex-shrink-0">{group.code}</span>
                      )}
                    </span>
                    <span className="d-block text-muted small text-truncate">
                      {group.description || t('common.noDescription')}
                    </span>
                  </span>
                  <i className="fas fa-chevron-right text-muted flex-shrink-0"></i>
                </button>
              ))}
            </div>
            {groupPickerTotal > groupPickerPageSize && (
              <Pagination
                page={groupPickerPage}
                pageSize={groupPickerPageSize}
                totalItems={groupPickerTotal}
                onPageChange={setGroupPickerPage}
                onPageSizeChange={(size) => { setGroupPickerPageSize(size); setGroupPickerPage(1); }}
                pageSizeOptions={[10, 25, 50]}
              />
            )}
            </>
        )}
      </Modal>

      <SubscribeFlow
        show={!!subscribeTarget}
        dataHolderGroup={subscribeTarget}
        onHide={() => setSubscribeTarget(null)}
        onSubscribed={() => { loadSubscriptions(); loadStats(); }}
      />
    </div>
  );
};

export default Subscriptions;