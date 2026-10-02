/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import Modal from './Modal';
import SubscriptionStatusBadge, { TestResultBadge } from './SubscriptionStatusBadge';
import AccessLevelBadge from './AccessLevelBadge';
import { useT } from '../i18n';

/**
 * Action configurations for subscription workflow.
 * Display text (titles, button labels, notes labels/placeholders, confirm/warning
 * messages) is resolved via i18n at render time using the action key — see the
 * `subscriptionAction.titles` / `buttonLabels` / etc. translation namespaces.
 */
export const SUBSCRIPTION_ACTIONS = {
  approve: {
    buttonClass: 'btn-primary',
    requiresNotes: false,
    showNotes: true,
  },
  deny: {
    buttonClass: 'btn-danger',
    requiresNotes: true,
    showNotes: true,
  },
  'start-test': {
    buttonClass: 'btn-primary',
    requiresNotes: false,
    hasConfirmMessage: true,
  },
  'run-test': {
    buttonClass: 'btn-primary',
    requiresNotes: false,
    showTestResults: true,
  },
  activate: {
    buttonClass: 'btn-success',
    requiresNotes: false,
    hasConfirmMessage: true,
  },
  suspend: {
    buttonClass: 'btn-danger',
    requiresNotes: true,
    showNotes: true,
    hasWarningMessage: true,
  },
  reactivate: {
    buttonClass: 'btn-primary',
    requiresNotes: false,
    hasConfirmMessage: true,
  },
  cancel: {
    buttonClass: 'btn-danger',
    requiresNotes: true,
    showNotes: true,
    hasWarningMessage: true,
  },
};

/**
 * Get available actions based on subscription status
 */
export const getAvailableActions = (subscription) => {
  const actions = [];
  
  switch (subscription.status) {
    case 'PENDING':
      actions.push({ key: 'approve', ...SUBSCRIPTION_ACTIONS.approve });
      actions.push({ key: 'deny', ...SUBSCRIPTION_ACTIONS.deny });
      break;
    case 'APPROVED':
      actions.push({ key: 'start-test', ...SUBSCRIPTION_ACTIONS['start-test'] });
      actions.push({ key: 'deny', ...SUBSCRIPTION_ACTIONS.deny });
      break;
    case 'TESTING':
      actions.push({ key: 'run-test', ...SUBSCRIPTION_ACTIONS['run-test'] });
      if (subscription.testResult === 'PASSED') {
        actions.push({ key: 'activate', ...SUBSCRIPTION_ACTIONS.activate });
      }
      actions.push({ key: 'deny', ...SUBSCRIPTION_ACTIONS.deny });
      break;
    case 'ACTIVE':
      actions.push({ key: 'suspend', ...SUBSCRIPTION_ACTIONS.suspend });
      break;
    case 'SUSPENDED':
      actions.push({ key: 'reactivate', ...SUBSCRIPTION_ACTIONS.reactivate });
      actions.push({ key: 'cancel', ...SUBSCRIPTION_ACTIONS.cancel });
      break;
    case 'DENIED':
    case 'EXPIRED':
    case 'CANCELLED':
      break;
  }
  
  return actions;
};

/**
 * Request Type Badge (local helper)
 */
const RequestTypeBadge = ({ requestType }) => {
  const { t } = useT();
  if (requestType.supportsExigent) {
    return <span className="badge bg-danger-subtle text-danger" style={{ fontSize: '11px' }}>⚡ {t('subscriptionAction.badges.exigent')}</span>;
  }
  if (requestType.supportsConfidential) {
    return <span className="badge bg-warning-subtle text-warning" style={{ fontSize: '11px' }}>🔒 {t('subscriptionAction.badges.confidential')}</span>;
  }
  return <span className="badge bg-secondary-subtle text-secondary" style={{ fontSize: '11px' }}>🌐 {t('subscriptionAction.badges.standard')}</span>;
};

/**
 * Subscription Action Modal
 */
const SubscriptionActionModal = ({
  isOpen,
  onClose,
  subscription,
  actionType,
  onSubmit,
  submitting = false,
  testResult = null,
}) => {
  const { t } = useT();
  const [notes, setNotes] = useState('');

  const config = SUBSCRIPTION_ACTIONS[actionType] || {};
  const isKnownAction = Boolean(SUBSCRIPTION_ACTIONS[actionType]);

  const title = isKnownAction ? t(`subscriptionAction.titles.${actionType}`) : t('subscriptionAction.defaultTitle');
  const buttonLabel = isKnownAction ? t(`subscriptionAction.buttonLabels.${actionType}`) : undefined;
  const notesLabel = config.showNotes ? t(`subscriptionAction.notesLabels.${actionType}`) : t('subscriptionAction.defaultNotesLabel');
  const notesPlaceholder = config.showNotes ? t(`subscriptionAction.notesPlaceholders.${actionType}`) : t('subscriptionAction.defaultNotesPlaceholder');
  const confirmMessage = config.hasConfirmMessage ? t(`subscriptionAction.confirmMessages.${actionType}`) : undefined;
  const warningMessage = config.hasWarningMessage ? t(`subscriptionAction.warningMessages.${actionType}`) : undefined;

  const handleSubmit = () => {
    onSubmit({ notes });
  };

  const canSubmit = !config.requiresNotes || notes.trim().length > 0;
  const showSubmitButton = !(actionType === 'run-test' && testResult);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={title}
      size={actionType === 'run-test' && testResult ? 'large' : undefined}
      footer={
        <>
          <button className="btn btn-secondary" onClick={onClose}>
            {testResult ? t('common.close') : t('common.cancel')}
          </button>
          {showSubmitButton && (
            <button
              className={`btn ${config.buttonClass || 'btn-primary'}`}
              onClick={handleSubmit}
              disabled={submitting || !canSubmit}
            >
              {submitting ? t('common.processing') : buttonLabel}
            </button>
          )}
        </>
      }
    >
      {subscription && (
        <div>
          {/* Subscription Summary */}
          <div className="row g-3 mb-4">
            <div className="col-md-6">
              <div className="text-muted text-uppercase small fw-semibold mb-1">{t('subscriptionAction.summary.requestor')}</div>
              <div>
                {`${subscription.requestorFirstName || ''} ${subscription.requestorLastName || ''}`.trim() || subscription.requestorGroupName}
                {subscription.requestorOrganization && (
                  <div className="text-muted small">{subscription.requestorOrganization}</div>
                )}
              </div>
            </div>
            <div className="col-md-6">
              <div className="text-muted text-uppercase small fw-semibold mb-1">{t('subscriptionAction.summary.currentStatus')}</div>
              <div>
                <SubscriptionStatusBadge status={subscription.status} />
              </div>
            </div>
          </div>

          {/* Show granted access level for activate action */}
          {actionType === 'activate' && !testResult && (
            <div style={{
              padding: '12px 16px',
              background: 'var(--bg-tertiary, #f8f9fa)',
              borderRadius: '8px',
              marginBottom: '16px',
            }}>
              <div className="text-muted text-uppercase small fw-semibold" style={{ marginBottom: '8px' }}>{t('subscriptionAction.summary.accessLevelFromTemplate')}</div>
              {subscription.requestTypes && subscription.requestTypes.length > 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  {subscription.requestTypes.map((rt, idx) => (
                    <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <RequestTypeBadge requestType={rt} />
                      <AccessLevelBadge level={rt.accessLevel} />
                      {rt.name && <span className="text-muted small">{rt.name}</span>}
                    </div>
                  ))}
                </div>
              ) : (
                <AccessLevelBadge level={subscription.grantedAccessLevel ?? subscription.requestedAccessLevel ?? 0} />
              )}
            </div>
          )}

          {/* Notes Input */}
          {(config.requiresNotes || config.showNotes) && !testResult && (
            <div className="mb-3">
              <label className="form-label">
                {notesLabel}
                {config.requiresNotes && ' *'}
              </label>
              <textarea
                className="form-control"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                rows={3}
                placeholder={notesPlaceholder}
              />
            </div>
          )}

          {/* Test Results */}
          {actionType === 'run-test' && testResult && (
            <TestResultsDisplay testResult={testResult} />
          )}

          {/* Confirmation/Warning Messages */}
          {confirmMessage && !testResult && (
            <div className="alert alert-info" style={{ marginTop: '16px' }}>
              {confirmMessage}
            </div>
          )}
          {warningMessage && !testResult && (
            <div className="alert alert-warning" style={{ marginTop: '16px' }}>
              {warningMessage}
            </div>
          )}
        </div>
      )}
    </Modal>
  );
};

// ==================== Enhanced Test Results Display ====================

/**
 * Color/icon helpers for check severity and result
 */
const RESULT_STYLES = {
  PASSED: { color: '#16a34a', bg: '#f0fdf4', icon: '✓' },
  FAILED: { color: '#dc2626', bg: '#fef2f2', icon: '✗' },
  SKIPPED: { color: '#9ca3af', bg: '#f9fafb', icon: '—' },
  ERROR: { color: '#dc2626', bg: '#fef2f2', icon: '⚠' },
};

const SEVERITY_STYLES = {
  INFO: { color: '#6b7280', icon: 'ℹ' },
  WARNING: { color: '#d97706', icon: '⚠' },
  ERROR: { color: '#dc2626', icon: '✗' },
};

/**
 * Summary bar showing pass/fail counts
 */
const TestSummaryBar = ({ summary }) => {
  const { t } = useT();
  if (!summary) return null;

  const totalTests = summary.totalValidationTests + summary.totalRdapTests;
  const totalPassed = summary.passedValidationTests + summary.passedRdapTests;
  const totalFailed = summary.failedValidationTests + summary.failedRdapTests;
  const allPassed = totalFailed === 0 && summary.errorRdapTests === 0;

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'repeat(auto-fit, minmax(100px, 1fr))',
      gap: '8px',
      padding: '12px',
      background: allPassed ? '#f0fdf4' : '#fef2f2',
      borderRadius: '8px',
      border: `1px solid ${allPassed ? '#bbf7d0' : '#fecaca'}`,
      marginBottom: '16px',
    }}>
      <SummaryCell label={t('subscriptionAction.testResults.summaryLabels.total')} value={totalTests} />
      <SummaryCell label={t('subscriptionAction.testResults.summaryLabels.passed')} value={totalPassed} color="#16a34a" />
      <SummaryCell label={t('subscriptionAction.testResults.summaryLabels.failed')} value={totalFailed} color="#dc2626" />
      {summary.skippedRdapTests > 0 && (
        <SummaryCell label={t('subscriptionAction.testResults.summaryLabels.skipped')} value={summary.skippedRdapTests} color="#9ca3af" />
      )}
      {summary.errorRdapTests > 0 && (
        <SummaryCell label={t('subscriptionAction.testResults.summaryLabels.errors')} value={summary.errorRdapTests} color="#dc2626" />
      )}
      <SummaryCell label={t('subscriptionAction.testResults.summaryLabels.duration')} value={`${summary.totalDurationMs}ms`} />
    </div>
  );
};

const SummaryCell = ({ label, value, color }) => (
  <div style={{ textAlign: 'center' }}>
    <div style={{ fontSize: '20px', fontWeight: 700, color: color || 'inherit' }}>{value}</div>
    <div style={{ fontSize: '11px', color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.5px' }}>{label}</div>
  </div>
);

/**
 * Main test results display — shows summary, validation tests, and RDAP test cases
 */
const TestResultsDisplay = ({ testResult }) => {
  const { t } = useT();
  if (!testResult) return null;

  return (
    <div>
      {/* Overall result header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
        <TestResultBadge result={testResult.result} size="large" />
        <span className="text-muted" style={{ fontSize: '13px' }}>{testResult.details}</span>
      </div>

      {/* Summary bar */}
      {testResult.summary && <TestSummaryBar summary={testResult.summary} />}

      {/* Validation Tests */}
      {testResult.testCases && testResult.testCases.length > 0 && (
        <div style={{ marginBottom: '20px' }}>
          <h4 style={{ marginBottom: '10px', fontSize: '14px', fontWeight: 600 }}>
            {t('subscriptionAction.testResults.validationTests')}
          </h4>
          {testResult.testCases.map((tc, index) => (
            <ValidationTestRow key={index} testCase={tc} />
          ))}
        </div>
      )}

      {/* RDAP Test Cases */}
      {testResult.rdapTestResults && testResult.rdapTestResults.length > 0 && (
        <div>
          <h4 style={{ marginBottom: '10px', fontSize: '14px', fontWeight: 600 }}>
            {t('subscriptionAction.testResults.rdapDataTests', { count: testResult.rdapTestResults.length })}
          </h4>
          {testResult.rdapTestResults.map((rdapCase, index) => (
            <RdapTestCaseCard key={index} testCase={rdapCase} defaultExpanded={index === 0 || rdapCase.result !== 'PASSED'} />
          ))}
        </div>
      )}
    </div>
  );
};

/**
 * Single validation test row (compact)
 */
const ValidationTestRow = ({ testCase }) => (
  <div style={{
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
    padding: '8px 10px',
    background: 'var(--bg-tertiary, #f8f9fa)',
    borderRadius: '6px',
    marginBottom: '6px',
  }}>
    <span style={{
      color: testCase.passed ? '#16a34a' : '#dc2626',
      fontSize: '16px',
      fontWeight: 700,
      width: '20px',
      textAlign: 'center',
    }}>
      {testCase.passed ? '✓' : '✗'}
    </span>
    <div style={{ flex: 1 }}>
      <strong style={{ fontSize: '13px' }}>{testCase.name}</strong>
      {testCase.description && (
        <div className="text-muted" style={{ fontSize: '12px' }}>{testCase.description}</div>
      )}
      {testCase.errorMessage && (
        <div style={{ fontSize: '12px', color: '#dc2626', marginTop: '2px' }}>{testCase.errorMessage}</div>
      )}
    </div>
  </div>
);

/**
 * Expandable RDAP test case card with detailed checks
 */
const RdapTestCaseCard = ({ testCase, defaultExpanded = false }) => {
  const { t } = useT();
  const [expanded, setExpanded] = useState(defaultExpanded);
  const isKnownResult = Boolean(RESULT_STYLES[testCase.result]);
  const style = RESULT_STYLES[testCase.result] || RESULT_STYLES.ERROR;
  const resultLabel = t(`subscriptionAction.resultLabels.${isKnownResult ? testCase.result : 'ERROR'}`);

  const categoryLabels = {
    LOOKUP: t('subscriptionAction.categories.LOOKUP'),
    ACCESS: t('subscriptionAction.categories.ACCESS'),
    CONTACT_VISIBILITY: t('subscriptionAction.categories.CONTACT_VISIBILITY'),
    REDACTION: t('subscriptionAction.categories.REDACTION'),
    PARAMETER: t('subscriptionAction.categories.PARAMETER'),
  };

  // Group checks by category
  const checksByCategory = {};
  if (testCase.checks) {
    testCase.checks.forEach(check => {
      const cat = check.category || 'OTHER';
      if (!checksByCategory[cat]) checksByCategory[cat] = [];
      checksByCategory[cat].push(check);
    });
  }

  return (
    <div style={{
      border: `1px solid ${style.color}22`,
      borderRadius: '8px',
      marginBottom: '10px',
      overflow: 'hidden',
      background: '#fff',
    }}>
      {/* Header — always visible */}
      <div
        onClick={() => setExpanded(!expanded)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          padding: '10px 14px',
          cursor: 'pointer',
          background: style.bg,
          borderBottom: expanded ? `1px solid ${style.color}22` : 'none',
          userSelect: 'none',
        }}
      >
        {/* Result icon */}
        <span style={{
          width: '24px',
          height: '24px',
          borderRadius: '50%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: style.color,
          color: '#fff',
          fontSize: '13px',
          fontWeight: 700,
          flexShrink: 0,
        }}>
          {style.icon}
        </span>

        {/* Label and meta */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontWeight: 600, fontSize: '13px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {testCase.label}
          </div>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center', marginTop: '2px', flexWrap: 'wrap' }}>
            <span className="badge bg-secondary-subtle text-secondary" style={{ fontSize: '10px' }}>{testCase.queryType?.toUpperCase()}</span>
            {testCase.requestTypeName && testCase.requestTypeName !== 'standard' && (
              <span className="badge bg-warning-subtle text-warning" style={{ fontSize: '10px' }}>{testCase.requestTypeName}</span>
            )}
            {testCase.resolvedAccessLevel != null && (
              <AccessLevelBadge level={testCase.resolvedAccessLevel} />
            )}
            <span className="text-muted" style={{ fontSize: '11px' }}>{testCase.durationMs}ms</span>
          </div>
        </div>

        {/* Result label */}
        <span style={{
          fontSize: '12px',
          fontWeight: 600,
          color: style.color,
          textTransform: 'uppercase',
          letterSpacing: '0.5px',
          flexShrink: 0,
        }}>
          {resultLabel}
        </span>

        {/* Expand chevron */}
        <span style={{
          fontSize: '16px',
          color: '#9ca3af',
          transition: 'transform 0.15s',
          transform: expanded ? 'rotate(180deg)' : 'rotate(0deg)',
          flexShrink: 0,
        }}>
          ▾
        </span>
      </div>

      {/* Expanded detail — individual checks grouped by category */}
      {expanded && testCase.checks && testCase.checks.length > 0 && (
        <div style={{ padding: '10px 14px' }}>
          {/* Overall message */}
          {testCase.message && (
            <div style={{
              fontSize: '12px',
              color: '#6b7280',
              padding: '6px 0 10px',
              borderBottom: '1px solid #f3f4f6',
              marginBottom: '10px',
            }}>
              {testCase.message}
            </div>
          )}

          {/* Checks by category */}
          {Object.entries(checksByCategory).map(([category, checks]) => (
            <div key={category} style={{ marginBottom: '12px' }}>
              <div style={{
                fontSize: '11px',
                fontWeight: 600,
                color: '#9ca3af',
                textTransform: 'uppercase',
                letterSpacing: '0.5px',
                marginBottom: '6px',
              }}>
                {categoryLabels[category] || category}
              </div>
              {checks.map((check, idx) => (
                <CheckRow key={idx} check={check} />
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

/**
 * Single check row within an RDAP test case
 */
const CheckRow = ({ check }) => {
  const { t } = useT();
  const severity = SEVERITY_STYLES[check.severity] || SEVERITY_STYLES.INFO;
  const passedIcon = check.passed ? '✓' : (check.severity === 'WARNING' ? '⚠' : '✗');
  const passedColor = check.passed
    ? (check.severity === 'WARNING' ? '#d97706' : '#16a34a')
    : '#dc2626';

  return (
    <div style={{
      display: 'flex',
      gap: '8px',
      padding: '5px 8px',
      borderRadius: '4px',
      marginBottom: '3px',
      background: check.passed ? 'transparent' : '#fef2f2',
      alignItems: 'flex-start',
    }}>
      <span style={{
        color: passedColor,
        fontSize: '13px',
        fontWeight: 600,
        width: '16px',
        textAlign: 'center',
        flexShrink: 0,
        marginTop: '1px',
      }}>
        {passedIcon}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: '12px', fontWeight: 500 }}>{check.name}</div>
        <div style={{ fontSize: '11px', color: '#6b7280', marginTop: '1px' }}>{check.message}</div>
        {check.expected && check.actual && check.expected !== check.actual && (
          <div style={{ fontSize: '11px', marginTop: '2px', color: '#dc2626' }}>
            {t('subscriptionAction.expected')} <code style={{ fontSize: '11px' }}>{check.expected}</code>
            {' → '}{t('subscriptionAction.got')} <code style={{ fontSize: '11px' }}>{check.actual}</code>
          </div>
        )}
      </div>
    </div>
  );
};

export default SubscriptionActionModal;