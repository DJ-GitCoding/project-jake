/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import SubscriptionStatusBadge, { TestResultBadge } from './SubscriptionStatusBadge';
import AccessLevelBadge from './AccessLevelBadge';
import { SensitivityLevelBadge } from './SensitivityLevelSelect';
import { useT } from '../i18n';

/**
 * Compact subscription card for lists
 */
export const SubscriptionListItem = ({ 
  subscription, 
  onView, 
  onAction,
  actions = [],
  selected = false,
  onSelect
}) => {
  const { t } = useT();
  return (
    <tr className={selected ? 'selected' : ''}>
      {onSelect && (
        <td>
          <input 
            type="checkbox" 
            checked={selected} 
            onChange={onSelect} 
          />
        </td>
      )}
      <td>
        <code className="font-monospace small">
          {subscription.requestId?.slice(0, 12)}...
        </code>
      </td>
      <td>
        <strong>{`${subscription.requestorFirstName || ''} ${subscription.requestorLastName || ''}`.trim() || subscription.requestorGroupName}</strong>
        {subscription.requestorOrganization && (
          <div className="text-muted small">{subscription.requestorOrganization}</div>
        )}
      </td>
      <td>
        <span className="small">{subscription.requestorGroupType || '-'}</span>
      </td>
      <td>
        <span className="small">{subscription.templateName || '-'}</span>
      </td>
      <td>
        <AccessLevelBadge level={subscription.grantedAccessLevel ?? subscription.requestedAccessLevel} />
      </td>
      <td>
        <SubscriptionStatusBadge status={subscription.status} />
      </td>
      <td>
        <TestResultBadge result={subscription.testResult} />
      </td>
      <td>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          {onView && (
            <button 
              className="btn btn-secondary btn-sm" 
              onClick={() => onView(subscription)}
              title={t('subscriptions.viewDetails')}
            >
              <i className="fa-solid fa-eye"></i>
            </button>
          )}
          {actions.map(action => (
            <button
              key={action.key}
              className={`btn ${action.className || 'btn-secondary'} btn-sm`}
              onClick={() => onAction(subscription, action.key)}
              title={action.title}
            >
              {action.label}
            </button>
          ))}
        </div>
      </td>
    </tr>
  );
};

/**
 * Detailed subscription info card
 */
export const SubscriptionDetailCard = ({ subscription }) => {
  const { t } = useT();
  if (!subscription) return null;

  return (
    <div className="subscription-detail-card">
      {/* Header with status */}
      <div style={{ 
        display: 'flex', 
        alignItems: 'center', 
        gap: '16px', 
        marginBottom: '24px' 
      }}>
        <SubscriptionStatusBadge status={subscription.status} size="large" />
        {subscription.testResult && (
          <TestResultBadge result={subscription.testResult} size="large" />
        )}
        {subscription.isCurrentlyEffective && (
          <span className="badge bg-success-subtle text-success">{t('subscriptions.card.currentlyEffective')}</span>
        )}
      </div>

      {/* Key Info Grid */}
      <div className="row g-3" style={{ marginBottom: '24px' }}>
        <InfoItem label={t('subscriptions.card.fields.requestId')} value={subscription.requestId} mono />
        <InfoItem
          label={t('subscriptions.card.fields.accessLevel')}
          value={<AccessLevelBadge level={subscription.grantedAccessLevel ?? subscription.requestedAccessLevel} />}
        />
        <InfoItem
          label={t('subscriptions.card.fields.sensitivityLevel')}
          value={<SensitivityLevelBadge level={subscription.grantedSensitivityLevel ?? 0} />}
        />
        <InfoItem label={t('subscriptions.card.fields.template')} value={subscription.templateName || '-'} />
        <InfoItem
          label={t('subscriptions.card.fields.effectiveFrom')}
          value={subscription.effectiveFrom ? new Date(subscription.effectiveFrom).toLocaleDateString() : t('common.notSet')}
        />
        <InfoItem
          label={t('subscriptions.card.fields.effectiveTo')}
          value={subscription.effectiveTo ? new Date(subscription.effectiveTo).toLocaleDateString() : t('subscriptions.card.noExpiration')}
        />
      </div>
    </div>
  );
};

/**
 * Requestor information section
 */
export const RequestorInfoSection = ({ subscription }) => {
  const { t } = useT();
  if (!subscription) return null;

  return (
    <div>
      {/* Group Info */}
      <h4 style={{ marginBottom: '12px' }}>{t('subscriptions.card.requestor.groupHeading')}</h4>
      <div className="row g-3" style={{ marginBottom: '24px' }}>
        <InfoItem label={t('subscriptions.card.requestor.fields.groupName')} value={subscription.requestorGroupName} />
        <InfoItem label={t('subscriptions.card.requestor.fields.groupId')} value={subscription.requestorGroupId} mono />
        <InfoItem label={t('subscriptions.card.requestor.fields.groupType')} value={subscription.requestorGroupType || '-'} />
        <InfoItem label={t('subscriptions.card.requestor.fields.agentId')} value={subscription.requestorAgentId || '-'} mono />
      </div>

      {/* Contact Person */}
      <h4 style={{ marginBottom: '12px' }}>{t('subscriptions.card.requestor.contactHeading')}</h4>
      <div className="row g-3" style={{ marginBottom: '24px' }}>
        <InfoItem label={t('subscriptions.card.requestor.fields.fullName')} value={`${subscription.requestorFirstName || ''} ${subscription.requestorLastName || ''}`.trim() || '-'} />
        <InfoItem label={t('subscriptions.card.requestor.fields.organization')} value={subscription.requestorOrganization || '-'} />
        <InfoItem
          label={t('common.email')}
          value={subscription.requestorEmail ? (
            <a href={`mailto:${subscription.requestorEmail}`}>{subscription.requestorEmail}</a>
          ) : '-'}
        />
        <InfoItem label={t('subscriptions.card.requestor.fields.phone')} value={subscription.requestorPhone || '-'} />
      </div>

      {/* Address */}
      <h4 style={{ marginBottom: '12px' }}>{t('subscriptions.card.requestor.addressHeading')}</h4>
      <AddressDisplay subscription={subscription} />

      {/* Description */}
      {subscription.requestorGroupDescription && (
        <div style={{ marginTop: '20px' }}>
          <h4 style={{ marginBottom: '12px' }}>{t('subscriptions.card.requestor.descriptionHeading')}</h4>
          <TextBlock>{subscription.requestorGroupDescription}</TextBlock>
        </div>
      )}
    </div>
  );
};

/**
 * Review and testing information section
 */
export const ReviewInfoSection = ({ subscription }) => {
  const { t } = useT();
  if (!subscription) return null;

  return (
    <div>
      {/* Review Info */}
      {subscription.reviewedBy ? (
        <div style={{ marginBottom: '24px' }}>
          <h4 style={{ marginBottom: '12px' }}>{t('subscriptions.card.review.heading')}</h4>
          <div className="row g-3">
            <InfoItem label={t('subscriptions.card.review.fields.reviewedBy')} value={subscription.reviewedBy} />
            <InfoItem
              label={t('subscriptions.card.review.fields.reviewedAt')}
              value={subscription.reviewedAt ? new Date(subscription.reviewedAt).toLocaleString() : '-'}
            />
          </div>
          {subscription.reviewNotes && (
            <div style={{ marginTop: '12px' }}>
              <div className="text-muted text-uppercase small fw-semibold mb-1">{t('subscriptions.card.review.notesLabel')}</div>
              <TextBlock>{subscription.reviewNotes}</TextBlock>
            </div>
          )}
        </div>
      ) : (
        <div className="text-muted" style={{ marginBottom: '24px' }}>
          {t('subscriptions.card.review.notReviewed')}
        </div>
      )}

      {/* Test Info */}
      {subscription.testStartedAt && (
        <div>
          <h4 style={{ marginBottom: '12px' }}>{t('subscriptions.card.review.testingHeading')}</h4>
          <div className="row g-3">
            <InfoItem
              label={t('subscriptions.card.review.fields.testStarted')}
              value={new Date(subscription.testStartedAt).toLocaleString()}
            />
            {subscription.testCompletedAt && (
              <InfoItem
                label={t('subscriptions.card.review.fields.testCompleted')}
                value={new Date(subscription.testCompletedAt).toLocaleString()}
              />
            )}
            <InfoItem
              label={t('subscriptions.card.review.fields.result')}
              value={subscription.testResult ? (
                <TestResultBadge result={subscription.testResult} />
              ) : (
                <span className="badge bg-warning-subtle text-warning">{t('subscriptions.status.pending')}</span>
              )}
            />
          </div>
          {subscription.testDetails && (
            <div style={{ marginTop: '12px' }}>
              <div className="text-muted text-uppercase small fw-semibold mb-1">{t('subscriptions.card.review.testDetailsLabel')}</div>
              <TextBlock>{subscription.testDetails}</TextBlock>
            </div>
          )}
        </div>
      )}

      {/* Rate Limits */}
      {(subscription.maxQueriesPerDay || subscription.maxQueriesPerMonth) && (
        <div style={{ marginTop: '24px' }}>
          <h4 style={{ marginBottom: '12px' }}>{t('subscriptions.card.review.rateLimitsHeading')}</h4>
          <div className="row g-3">
            <InfoItem
              label={t('subscriptions.card.review.fields.queriesPerDay')}
              value={subscription.maxQueriesPerDay || t('subscriptions.card.review.unlimited')}
            />
            <InfoItem
              label={t('subscriptions.card.review.fields.queriesPerMonth')}
              value={subscription.maxQueriesPerMonth || t('subscriptions.card.review.unlimited')}
            />
          </div>
        </div>
      )}
    </div>
  );
};

/**
 * Status history timeline
 */
export const StatusHistorySection = ({ statusHistory = [], getStatusColor }) => {
  const { t } = useT();
  if (!statusHistory.length) {
    return <div className="text-muted">{t('subscriptions.card.history.empty')}</div>;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      {statusHistory.map((log, index) => (
        <div 
          key={index} 
          style={{ 
            display: 'flex', 
            alignItems: 'flex-start', 
            gap: '12px', 
            padding: '12px', 
            background: 'var(--bg-tertiary)', 
            borderRadius: '8px' 
          }}
        >
          <div style={{ minWidth: '150px' }}>
            <div className="small">
              {new Date(log.createdAt).toLocaleDateString()}
            </div>
            <div className="text-muted small">
              {new Date(log.createdAt).toLocaleTimeString()}
            </div>
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
              {log.previousStatus && (
                <>
                  <SubscriptionStatusBadge status={log.previousStatus} size="small" />
                  <span>→</span>
                </>
              )}
              <SubscriptionStatusBadge status={log.newStatus} />
            </div>
            <div className="small">
              <strong>{log.changedBy}</strong>
              {log.source && <span className="text-muted"> ({log.source})</span>}
            </div>
            {log.changeReason && (
              <div className="text-muted small" style={{ marginTop: '4px' }}>
                {log.changeReason}
              </div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
};

// ==================== Helper Components ====================

const InfoItem = ({ label, value, mono = false }) => (
  <div className="col-md-6">
    <div className="text-muted text-uppercase small fw-semibold mb-1">{label}</div>
    <div className={mono ? 'font-monospace' : ''}>{value}</div>
  </div>
);

const TextBlock = ({ children }) => (
  <div style={{ 
    padding: '12px', 
    backgroundColor: 'var(--bg-secondary)', 
    borderRadius: '8px',
    marginTop: '4px'
  }}>
    {children}
  </div>
);

const AddressDisplay = ({ subscription }) => {
  const { t } = useT();
  const hasAddress = subscription.requestorAddressStreet1 ||
                     subscription.requestorAddressCity ||
                     subscription.requestorAddressCountry;

  if (!hasAddress) {
    return <TextBlock><span className="text-muted">{t('subscriptions.card.requestor.noAddress')}</span></TextBlock>;
  }

  return (
    <TextBlock>
      {subscription.requestorAddressStreet1 && (
        <div>{subscription.requestorAddressStreet1}</div>
      )}
      {subscription.requestorAddressStreet2 && (
        <div>{subscription.requestorAddressStreet2}</div>
      )}
      <div>
        {[
          subscription.requestorAddressCity,
          subscription.requestorAddressState,
          subscription.requestorAddressPostalCode
        ].filter(Boolean).join(', ')}
      </div>
      {subscription.requestorAddressCountry && (
        <div>{subscription.requestorAddressCountry}</div>
      )}
    </TextBlock>
  );
};

export default SubscriptionDetailCard;