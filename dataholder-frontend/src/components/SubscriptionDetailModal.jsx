/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import Modal from './Modal';
import { DetailTabs } from './TabBar';
import SubscriptionStatusBadge, { TestResultBadge } from './SubscriptionStatusBadge';
import AccessLevelBadge from './AccessLevelBadge';
import { getAvailableActions } from './SubscriptionActionModal';
import { useT } from '../i18n';

/**
 * Helper to format ISO date strings for display
 */
const formatDate = (dateStr) => {
  if (!dateStr) return '—';
  try {
    return new Date(dateStr).toLocaleString();
  } catch {
    return dateStr;
  }
};

/**
 * Renders a labeled field row
 */
const Field = ({ label, value, mono = false }) => (
  <div style={{
    display: 'flex',
    padding: '8px 0',
    borderBottom: '1px solid var(--border-color, #eee)',
    gap: '12px',
  }}>
    <div style={{
      flex: '0 0 200px',
      fontWeight: 500,
      color: 'var(--text-muted, #666)',
      fontSize: '13px',
    }}>
      {label}
    </div>
    <div style={{
      flex: 1,
      fontSize: '13px',
      fontFamily: mono ? 'monospace' : 'inherit',
      wordBreak: 'break-word',
    }}>
      {value || '—'}
    </div>
  </div>
);

/**
 * Section wrapper with optional title
 */
const Section = ({ title, children }) => (
  <div style={{ marginBottom: '24px' }}>
    {title && (
      <h4 style={{
        fontSize: '14px',
        fontWeight: 600,
        marginBottom: '12px',
        paddingBottom: '8px',
        borderBottom: '2px solid var(--border-color, #eee)',
      }}>
        {title}
      </h4>
    )}
    {children}
  </div>
);

/**
 * Request Type Badge
 */
const RequestTypeBadge = ({ requestType }) => {
  const { t } = useT();
  if (requestType.supportsExigent) {
    return <span className="badge bg-danger-subtle text-danger" style={{ fontSize: '11px' }}>⚡ {t('subscriptionDetail.badges.exigent')}</span>;
  }
  if (requestType.supportsConfidential) {
    return <span className="badge bg-warning-subtle text-warning" style={{ fontSize: '11px' }}>🔒 {t('subscriptionDetail.badges.confidential')}</span>;
  }
  return <span className="badge bg-secondary-subtle text-secondary" style={{ fontSize: '11px' }}>🌐 {t('subscriptionDetail.badges.standard')}</span>;
};

/**
 * Renders request types as a compact list
 */
const RequestTypesList = ({ requestTypes }) => {
  if (!requestTypes || requestTypes.length === 0) return <span>—</span>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      {requestTypes.map((rt, idx) => (
        <div
          key={idx}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            padding: '6px 10px',
            backgroundColor: 'var(--bg-secondary, #f5f5f5)',
            borderRadius: '6px',
            fontSize: '13px',
          }}
        >
          <RequestTypeBadge requestType={rt} />
          <AccessLevelBadge level={rt.accessLevel} />
          {rt.description && (
            <span style={{ color: 'var(--text-muted, #888)', fontSize: '12px', marginLeft: '4px' }}>
              — {rt.description}
            </span>
          )}
        </div>
      ))}
    </div>
  );
};

/**
 * Details tab
 */
const DetailsTab = ({ subscription }) => {
  const { t } = useT();
  return (
  <div>
    <Section title={t('subscriptionDetail.sections.subscriptionInfo')}>
      <Field label={t('subscriptionDetail.fields.requestId')} value={subscription.requestId} mono />
      <Field label={t('subscriptionDetail.fields.internalId')} value={subscription.id} mono />
      <Field label={t('subscriptionDetail.fields.displayName')} value={subscription.displayName} />
      <Field label={t('subscriptionDetail.fields.template')} value={subscription.templateName} />
      <Field label={t('subscriptionDetail.fields.templateId')} value={subscription.templateId} mono />
      <Field label={t('subscriptionDetail.fields.purpose')} value={subscription.purpose} />
      <Field label={t('subscriptionDetail.fields.additionalTerms')} value={subscription.additionalTerms} />
      <Field label={t('common.status')} value={<SubscriptionStatusBadge status={subscription.status} />} />
      <Field label={t('subscriptionDetail.fields.statusMessage')} value={subscription.statusMessage} />
      <Field label={t('subscriptionDetail.fields.currentlyEffective')} value={subscription.isCurrentlyEffective ? t('common.yes') : t('common.no')} />
    </Section>

    <Section title={t('subscriptionDetail.sections.requestTypesAccess')}>
      {subscription.requestTypes && subscription.requestTypes.length > 0 ? (
        <>
          <Field label={t('subscriptionDetail.fields.requestTypes')} value={<RequestTypesList requestTypes={subscription.requestTypes} />} />
          <Field label={t('subscriptionDetail.fields.supportsConfidential')} value={subscription.requestTypes.some(rt => rt.supportsConfidential) ? t('common.yes') : t('common.no')} />
          <Field label={t('subscriptionDetail.fields.supportsExigent')} value={subscription.requestTypes.some(rt => rt.supportsExigent) ? t('common.yes') : t('common.no')} />
          <Field label={t('subscriptionDetail.fields.highestAccessLevel')} value={<AccessLevelBadge level={Math.max(...subscription.requestTypes.map(rt => rt.accessLevel ?? 0))} />} />
        </>
      ) : (
        <Field label={t('subscriptionDetail.fields.accessLevel')} value={<AccessLevelBadge level={subscription.accessLevel} />} />
      )}
    </Section>

    <Section title={t('subscriptionDetail.sections.rateLimits')}>
      <Field label={t('subscriptionDetail.fields.maxQueriesPerDay')} value={subscription.maxQueriesPerDay ?? t('subscriptionDetail.values.unlimited')} />
      <Field label={t('subscriptionDetail.fields.maxQueriesPerMonth')} value={subscription.maxQueriesPerMonth ?? t('subscriptionDetail.values.unlimited')} />
    </Section>

    <Section title={t('subscriptionDetail.sections.keyDates')}>
      <Field label={t('subscriptionDetail.fields.created')} value={formatDate(subscription.createdAt)} />
      <Field label={t('subscriptionDetail.fields.effectiveFrom')} value={formatDate(subscription.effectiveFrom)} />
      <Field label={t('subscriptionDetail.fields.effectiveTo')} value={formatDate(subscription.effectiveTo) || t('subscriptionDetail.values.noExpiration')} />
      <Field label={t('subscriptionDetail.fields.requestExpires')} value={formatDate(subscription.requestExpiresAt)} />
      <Field label={t('subscriptionDetail.fields.activatedAt')} value={formatDate(subscription.activatedAt)} />
      <Field label={t('subscriptionDetail.fields.statusChangedAt')} value={formatDate(subscription.statusChangedAt)} />
      <Field label={t('subscriptionDetail.fields.updatedAt')} value={formatDate(subscription.updatedAt)} />
    </Section>

    <Section title={t('subscriptionDetail.sections.integration')}>
      <Field label={t('subscriptionDetail.fields.introspectionUrl')} value={subscription.introspectionUrl} mono />
      <Field label={t('subscriptionDetail.fields.callbackUrl')} value={subscription.callbackUrl} mono />
      <Field label={t('subscriptionDetail.fields.requestorAgentId')} value={subscription.requestorAgentId} mono />
    </Section>
  </div>
  );
};

/**
 * Requestor tab
 */
const RequestorTab = ({ subscription }) => {
  const { t } = useT();
  return (
  <div>
    <Section title={t('subscriptionDetail.sections.groupInformation')}>
      <Field label={t('subscriptionDetail.fields.groupName')} value={subscription.requestorGroupName} />
      <Field label={t('subscriptionDetail.fields.groupId')} value={subscription.requestorGroupId} mono />
      <Field label={t('subscriptionDetail.fields.groupType')} value={subscription.requestorGroupType} />
    </Section>

    <Section title={t('subscriptionDetail.sections.contactInformation')}>
      <Field label={t('subscriptionDetail.fields.fullName')} value={`${subscription.requestorFirstName || ''} ${subscription.requestorLastName || ''}`.trim()} />
      <Field label={t('subscriptionDetail.fields.contactEmail')} value={subscription.requestorContactEmail} />
      <Field label={t('subscriptionDetail.fields.phone')} value={subscription.requestorPhone} />
      <Field label={t('subscriptionDetail.fields.organization')} value={subscription.requestorOrganization} />
      <Field label={t('common.description')} value={subscription.requestorDescription} />
    </Section>

    <Section title={t('subscriptionDetail.sections.address')}>
      <Field label={t('subscriptionDetail.fields.formattedAddress')} value={subscription.formattedAddress} />
      <Field label={t('subscriptionDetail.fields.streetAddress')} value={subscription.requestorAddress} />
      <Field label={t('subscriptionDetail.fields.city')} value={subscription.requestorCity} />
      <Field label={t('subscriptionDetail.fields.stateProvince')} value={subscription.requestorStateProvince} />
      <Field label={t('subscriptionDetail.fields.postalCode')} value={subscription.requestorPostalCode} />
      <Field label={t('subscriptionDetail.fields.country')} value={subscription.requestorCountry} />
    </Section>
  </div>
  );
};

// ==================== Inherited Test Data ====================

const QUERY_TYPE_ICONS = { domain: '🌐', ip: '🔢', asn: '📡' };

/**
 * Shows inherited test data from the subscription's template.
 * Read-only — admin manages test data on the template page.
 */
const InheritedTestDataSection = ({ subscription }) => {
  const { t } = useT();
  // Test data lives on the template; the subscription inherits it
  const testData = subscription.templateTestData || subscription.testData || [];
  const testDataCount = subscription.templateTestDataCount || testData.length;

  return (
    <Section title={testDataCount > 0 ? t('subscriptionDetail.sections.templateTestDataWithCount', { count: testDataCount }) : t('subscriptionDetail.sections.templateTestData')}>
      {testDataCount === 0 && testData.length === 0 ? (
        <div style={{
          padding: '16px',
          textAlign: 'center',
          color: 'var(--text-muted, #999)',
          fontSize: '13px',
        }}>
          {t('subscriptionDetail.noTestDataConfigured')}
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <div style={{
            padding: '8px 12px',
            background: 'var(--bg-secondary, #f5f5f5)',
            borderRadius: '6px',
            fontSize: '12px',
            color: 'var(--text-muted, #888)',
            marginBottom: '4px',
          }}>
            {t('subscriptionDetail.inheritedFromTemplatePrefix')} <strong>{subscription.templateName}</strong>{t('subscriptionDetail.inheritedFromTemplateSuffix')}
          </div>
          {testData.map((entry) => {
            const icon = QUERY_TYPE_ICONS[entry.queryType] || '📋';
            const isActive = entry.isActive !== false;
            return (
              <div key={entry.id} style={{
                display: 'flex',
                alignItems: 'center',
                gap: '10px',
                padding: '8px 12px',
                border: '1px solid var(--border-color, #eee)',
                borderRadius: '6px',
                opacity: isActive ? 1 : 0.5,
                fontSize: '13px',
              }}>
                <span style={{ fontSize: '16px', flexShrink: 0 }}>{icon}</span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 500 }}>
                    {entry.displayLabel || entry.label || `${entry.queryType}/${entry.queryValue}`}
                  </div>
                  <div style={{ display: 'flex', gap: '6px', marginTop: '2px', flexWrap: 'wrap', alignItems: 'center' }}>
                    <span className="badge bg-secondary-subtle text-secondary" style={{ fontSize: '10px' }}>{entry.queryType?.toUpperCase()}</span>
                    <code style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{entry.queryValue}</code>
                    {entry.requestTypeName && (
                      <span className="badge bg-warning-subtle text-warning" style={{ fontSize: '10px' }}>{entry.requestTypeName}</span>
                    )}
                    {entry.verifyContactAccess && <span title={t('subscriptionDetail.verifyContactAccess')} style={{ fontSize: '11px' }}>👤</span>}
                    {entry.verifyRedaction && <span title={t('subscriptionDetail.verifyRedaction')} style={{ fontSize: '11px' }}>🔒</span>}
                    {!isActive && <span className="badge bg-danger-subtle text-danger" style={{ fontSize: '10px' }}>{t('common.inactive')}</span>}
                  </div>
                </div>
                {entry.rdapEntityHandle && (
                  <code className="text-muted" style={{ fontSize: '10px', flexShrink: 0 }}>{entry.rdapEntityHandle}</code>
                )}
              </div>
            );
          })}
        </div>
      )}
    </Section>
  );
};

// ==================== Review & Testing Tab (Enhanced) ====================

/**
 * Inline RDAP test summary for the detail modal
 */
const RdapTestSummaryInline = ({ rdapTestResults }) => {
  const { t } = useT();
  if (!rdapTestResults || rdapTestResults.length === 0) return null;

  const passed = rdapTestResults.filter(r => r.result === 'PASSED').length;
  const failed = rdapTestResults.filter(r => r.result === 'FAILED').length;
  const errors = rdapTestResults.filter(r => r.result === 'ERROR').length;
  const total = rdapTestResults.length;

  return (
    <div style={{ marginTop: '16px' }}>
      <h4 style={{ fontSize: '14px', fontWeight: 600, marginBottom: '12px' }}>{t('subscriptionDetail.sections.rdapDataTests')}</h4>

      {/* Mini summary */}
      <div style={{
        display: 'flex',
        gap: '16px',
        padding: '10px 14px',
        background: failed === 0 && errors === 0 ? '#f0fdf4' : '#fef2f2',
        borderRadius: '8px',
        marginBottom: '12px',
        fontSize: '13px',
      }}>
        <span><strong>{total}</strong> {t('subscriptionDetail.testsSummary.tests')}</span>
        <span style={{ color: '#16a34a' }}><strong>{passed}</strong> {t('subscriptionDetail.testsSummary.passed')}</span>
        {failed > 0 && <span style={{ color: '#dc2626' }}><strong>{failed}</strong> {t('subscriptionDetail.testsSummary.failed')}</span>}
        {errors > 0 && <span style={{ color: '#dc2626' }}><strong>{errors}</strong> {t('subscriptionDetail.testsSummary.errors')}</span>}
      </div>

      {/* Each test case as a compact row */}
      {rdapTestResults.map((tc, idx) => (
        <RdapTestCaseRow key={idx} testCase={tc} />
      ))}
    </div>
  );
};

/**
 * Compact RDAP test case row for the detail modal (expandable)
 */
const RdapTestCaseRow = ({ testCase }) => {
  const { t } = useT();
  const [expanded, setExpanded] = useState(false);

  const categoryLabels = {
    LOOKUP: t('subscriptionDetail.categories.LOOKUP'),
    ACCESS: t('subscriptionDetail.categories.ACCESS'),
    CONTACT_VISIBILITY: t('subscriptionDetail.categories.CONTACT_VISIBILITY'),
    REDACTION: t('subscriptionDetail.categories.REDACTION'),
    PARAMETER: t('subscriptionDetail.categories.PARAMETER'),
  };

  const resultColors = {
    PASSED: '#16a34a',
    FAILED: '#dc2626',
    ERROR: '#dc2626',
    SKIPPED: '#9ca3af',
  };
  const color = resultColors[testCase.result] || '#6b7280';

  const checksByCategory = {};
  if (testCase.checks) {
    testCase.checks.forEach(c => {
      const cat = c.category || 'OTHER';
      if (!checksByCategory[cat]) checksByCategory[cat] = [];
      checksByCategory[cat].push(c);
    });
  }

  return (
    <div style={{
      border: '1px solid var(--border-color, #eee)',
      borderRadius: '6px',
      marginBottom: '6px',
      overflow: 'hidden',
    }}>
      <div
        onClick={() => setExpanded(!expanded)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          padding: '8px 12px',
          cursor: 'pointer',
          background: expanded ? 'var(--bg-tertiary, #f8f9fa)' : 'transparent',
          userSelect: 'none',
        }}
      >
        <span style={{ color, fontWeight: 700, fontSize: '14px', width: '18px', textAlign: 'center' }}>
          {testCase.result === 'PASSED' ? '✓' : testCase.result === 'SKIPPED' ? '—' : '✗'}
        </span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <span style={{ fontSize: '13px', fontWeight: 500 }}>{testCase.label}</span>
          <div style={{ display: 'flex', gap: '6px', marginTop: '2px', flexWrap: 'wrap' }}>
            <span className="badge bg-secondary-subtle text-secondary" style={{ fontSize: '10px' }}>{testCase.queryType?.toUpperCase()}</span>
            {testCase.requestTypeName && testCase.requestTypeName !== 'standard' && (
              <span className="badge bg-warning-subtle text-warning" style={{ fontSize: '10px' }}>{testCase.requestTypeName}</span>
            )}
            {testCase.resolvedAccessLevel != null && (
              <AccessLevelBadge level={testCase.resolvedAccessLevel} />
            )}
          </div>
        </div>
        <span style={{ fontSize: '11px', color: '#9ca3af' }}>{testCase.durationMs}ms</span>
        <span style={{
          fontSize: '14px',
          color: '#9ca3af',
          transition: 'transform 0.15s',
          transform: expanded ? 'rotate(180deg)' : 'rotate(0deg)',
        }}>▾</span>
      </div>

      {expanded && testCase.checks && (
        <div style={{ padding: '8px 12px', borderTop: '1px solid var(--border-color, #eee)' }}>
          {testCase.message && (
            <div style={{ fontSize: '12px', color: '#6b7280', marginBottom: '8px' }}>{testCase.message}</div>
          )}
          {Object.entries(checksByCategory).map(([cat, checks]) => (
            <div key={cat} style={{ marginBottom: '8px' }}>
              <div style={{ fontSize: '11px', fontWeight: 600, color: '#9ca3af', marginBottom: '4px' }}>
                {categoryLabels[cat] || cat}
              </div>
              {checks.map((check, ci) => (
                <div key={ci} style={{
                  display: 'flex',
                  gap: '6px',
                  padding: '3px 6px',
                  fontSize: '12px',
                  alignItems: 'flex-start',
                  background: !check.passed ? '#fef2f2' : 'transparent',
                  borderRadius: '3px',
                  marginBottom: '2px',
                }}>
                  <span style={{
                    color: check.passed ? (check.severity === 'WARNING' ? '#d97706' : '#16a34a') : '#dc2626',
                    fontWeight: 600,
                    width: '14px',
                    textAlign: 'center',
                    flexShrink: 0,
                  }}>
                    {check.passed ? (check.severity === 'WARNING' ? '⚠' : '✓') : '✗'}
                  </span>
                  <div style={{ flex: 1 }}>
                    <span style={{ fontWeight: 500 }}>{check.name}: </span>
                    <span style={{ color: '#6b7280' }}>{check.message}</span>
                  </div>
                </div>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

/**
 * Review & Testing tab — enhanced with RDAP test results
 */
const ReviewTab = ({ subscription }) => {
  const { t } = useT();
  return (
  <div>
    <Section title={t('subscriptionDetail.sections.review')}>
      <Field label={t('subscriptionDetail.fields.reviewedBy')} value={subscription.reviewedBy} />
      <Field label={t('subscriptionDetail.fields.reviewedAt')} value={formatDate(subscription.reviewedAt)} />
      <Field label={t('subscriptionDetail.fields.reviewNotes')} value={subscription.reviewNotes} />
    </Section>

    <Section title={t('subscriptionDetail.sections.activation')}>
      <Field label={t('subscriptionDetail.fields.activatedBy')} value={subscription.activatedBy} />
      <Field label={t('subscriptionDetail.fields.activatedAt')} value={formatDate(subscription.activatedAt)} />
    </Section>

    {/* Inherited Test Data from Template */}
    <InheritedTestDataSection subscription={subscription} />

    <Section title={t('subscriptionDetail.sections.testing')}>
      <Field label={t('subscriptionDetail.fields.testResult')} value={<TestResultBadge result={subscription.testResult} />} />
      <Field label={t('subscriptionDetail.fields.testStarted')} value={formatDate(subscription.testStartedAt)} />
      <Field label={t('subscriptionDetail.fields.testCompleted')} value={formatDate(subscription.testCompletedAt)} />
      <Field label={t('subscriptionDetail.fields.testDetails')} value={subscription.testDetails} />

      {/* RDAP Test Results (if available from last test run stored on subscription) */}
      {subscription.lastTestResult && subscription.lastTestResult.rdapTestResults && (
        <RdapTestSummaryInline rdapTestResults={subscription.lastTestResult.rdapTestResults} />
      )}
    </Section>
  </div>
  );
};

/**
 * History tab
 */
const HistoryTab = ({ subscription }) => {
  const { t } = useT();
  const statusHistory = subscription.statusHistory || [];
  const unknown = t('common.unknown');

  const inferredTimeline = [];
  if (subscription.createdAt) {
    inferredTimeline.push({ status: 'PENDING', timestamp: subscription.createdAt, note: t('subscriptionDetail.timelineNotes.requestCreated') });
  }
  if (subscription.reviewedAt) {
    inferredTimeline.push({ status: 'REVIEWED', timestamp: subscription.reviewedAt, note: t('subscriptionDetail.timelineNotes.reviewedBy', { reviewer: subscription.reviewedBy || unknown }) });
  }
  if (subscription.testStartedAt) {
    inferredTimeline.push({ status: 'TESTING', timestamp: subscription.testStartedAt, note: t('subscriptionDetail.timelineNotes.testingStarted') });
  }
  if (subscription.testCompletedAt) {
    inferredTimeline.push({ status: 'TEST_COMPLETE', timestamp: subscription.testCompletedAt, note: t('subscriptionDetail.timelineNotes.testResult', { result: subscription.testResult || unknown }) });
  }
  if (subscription.activatedAt) {
    inferredTimeline.push({ status: 'ACTIVE', timestamp: subscription.activatedAt, note: t('subscriptionDetail.timelineNotes.activatedBy', { activator: subscription.activatedBy || unknown }) });
  }
  if (subscription.status === 'SUSPENDED' && subscription.statusChangedAt) {
    inferredTimeline.push({ status: 'SUSPENDED', timestamp: subscription.statusChangedAt, note: subscription.statusMessage || t('subscriptionDetail.timelineNotes.suspended') });
  }

  const timeline = statusHistory.length > 0 ? statusHistory : inferredTimeline;
  timeline.sort((a, b) => new Date(a.timestamp || a.changedAt) - new Date(b.timestamp || b.changedAt));

  if (timeline.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: '40px', color: 'var(--text-muted, #999)' }}>
        {t('subscriptionDetail.noHistoryAvailable')}
      </div>
    );
  }

  return (
    <div>
      <Section title={t('subscriptionDetail.sections.statusTimeline')}>
        {timeline.map((entry, index) => (
          <div
            key={index}
            style={{
              display: 'flex',
              gap: '16px',
              padding: '12px 0',
              borderBottom: '1px solid var(--border-color, #eee)',
              alignItems: 'flex-start',
            }}
          >
            <div style={{
              flex: '0 0 180px',
              fontSize: '12px',
              color: 'var(--text-muted, #666)',
              fontFamily: 'monospace',
            }}>
              {formatDate(entry.timestamp || entry.changedAt)}
            </div>
            <div>
              <SubscriptionStatusBadge status={entry.status} />
              {(entry.note || entry.reason) && (
                <div style={{ fontSize: '13px', color: 'var(--text-muted, #666)', marginTop: '4px' }}>
                  {entry.note || entry.reason}
                </div>
              )}
            </div>
          </div>
        ))}
      </Section>
    </div>
  );
};

/**
 * Full subscription detail modal with tabs
 */
const SubscriptionDetailModal = ({
  isOpen,
  onClose,
  subscription,
  onAction,
  loading = false,
}) => {
  const { t } = useT();
  const [activeTab, setActiveTab] = useState('details');

  if (!subscription) return null;

  const tabs = [
    { id: 'details', label: t('subscriptionDetail.tabs.details') },
    { id: 'requestor', label: t('subscriptionDetail.tabs.requestor') },
    { id: 'review', label: t('subscriptionDetail.tabs.review') },
    { id: 'history', label: t('subscriptionDetail.tabs.history') },
  ];

  const availableActions = getAvailableActions(subscription);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={
        <div>
          <div style={{ fontSize: '20px', fontWeight: 700 }}>
            {subscription.requestorGroupName || t('subscriptionDetail.unknownGroup')}
          </div>
          <div style={{
            fontSize: '13px',
            fontWeight: 400,
            color: 'var(--text-muted, #888)',
            marginTop: '4px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            flexWrap: 'wrap',
          }}>
            <span>{subscription.templateName}</span>
            <span style={{ opacity: 0.4 }}>·</span>
            <SubscriptionStatusBadge status={subscription.status} />
            <span style={{ opacity: 0.4 }}>·</span>
            <code style={{ fontSize: '12px' }}>{subscription.requestId}</code>
          </div>
        </div>
      }
      size="large"
      footer={
        <div style={{ display: 'flex', gap: '8px', justifyContent: 'space-between', width: '100%' }}>
          <button className="btn btn-secondary" onClick={onClose}>{t('common.close')}</button>
          <div style={{ display: 'flex', gap: '8px' }}>
            {availableActions.map(action => (
              <button
                key={action.key}
                className={`btn ${action.buttonClass}`}
                onClick={() => onAction(subscription, action.key)}
              >
                {t(`subscriptionAction.buttonLabels.${action.key}`)}
              </button>
            ))}
          </div>
        </div>
      }
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: '40px' }}>{t('common.loading')}</div>
      ) : (
        <div>
          <DetailTabs tabs={tabs} activeTab={activeTab} onTabChange={setActiveTab} />
          <div style={{ minHeight: '300px', padding: '16px 0' }}>
            {activeTab === 'details' && <DetailsTab subscription={subscription} />}
            {activeTab === 'requestor' && <RequestorTab subscription={subscription} />}
            {activeTab === 'review' && <ReviewTab subscription={subscription} />}
            {activeTab === 'history' && <HistoryTab subscription={subscription} />}
          </div>
        </div>
      )}
    </Modal>
  );
};

export default SubscriptionDetailModal;