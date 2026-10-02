/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import { buildReferenceIndex, sectionClauseLines } from '../constants/legalSections';
import { useT } from '../i18n';

const formatDate = (d) => (d ? new Date(d).toLocaleString() : '—');

const answerText = (value, t) => {
  if (value === true) return t('common.yes');
  if (value === false) return t('common.no');
  if (value === undefined || value === null || value === '') return '—';
  return String(value);
};

/*
 * What the requestor actually signed up to: the pinned copy of the terms, which
 * sections they accepted and when, the answers they gave, and the request types the
 * subscription carries. Falls back to the live template for subscriptions that
 * predate snapshotting.
 */
const SubscriptionTerms = ({ detail }) => {
  const { t } = useT();
  const [showProposed, setShowProposed] = useState(false);

  const hasProposed = !!detail.pendingTemplateSnapshot && detail.pendingChangeStatus === 'PROPOSED';
  const viewingProposed = hasProposed && showProposed;

  const snapshot = viewingProposed
    ? detail.pendingTemplateSnapshot
    : (detail.templateSnapshot || {
        legalSections: detail.legalSections,
        subscriptionFields: detail.subscriptionFields,
        requestTypes: detail.requestTypes,
      });

  const legalSections = snapshot?.legalSections || [];
  const allFields = snapshot?.subscriptionFields || [];
  const linkFields = allFields.filter(f => f.dataType === 'url');
  const answerFields = allFields.filter(f => f.dataType !== 'url');
  const requestTypes = snapshot?.requestTypes || [];
  const acceptedTerms = viewingProposed ? [] : (detail.acceptedTerms || []);
  /* Subscriptions predating acceptance recording have no entries at all; flagging every
   * section "not accepted" there would misrepresent them. */
  const acceptanceRecorded = acceptedTerms.length > 0;
  const referenceIndex = buildReferenceIndex(legalSections);

  return (
    <div className="mt-4">
      <div className="d-flex justify-content-between align-items-center mb-2">
        <h6 className="mb-0">
          <i className="fa-solid fa-file-contract me-2" />
          {viewingProposed
            ? t('subscriptions.terms.proposedTitle')
            : t('subscriptions.terms.title')}
          {snapshot?.name && <span className="text-muted fw-normal ms-2">{snapshot.name}</span>}
        </h6>
        {hasProposed && (
          <div className="btn-group btn-group-sm">
            <button
              type="button"
              className={`btn btn-outline-secondary ${viewingProposed ? '' : 'active'}`}
              onClick={() => setShowProposed(false)}
            >
              {t('subscriptions.terms.viewInForce')}
            </button>
            <button
              type="button"
              className={`btn btn-outline-warning ${viewingProposed ? 'active' : ''}`}
              onClick={() => setShowProposed(true)}
            >
              {t('subscriptions.terms.viewProposed')}
            </button>
          </div>
        )}
      </div>

      {viewingProposed ? (
        <div className="alert alert-warning py-2 small">
          {detail.pendingChangeMode === 'FORCED'
            ? t('subscriptions.terms.proposedNoticeRequired', {
                date: detail.pendingChangeDeadline
                  ? new Date(detail.pendingChangeDeadline).toLocaleDateString()
                  : '—',
              })
            : t('subscriptions.terms.proposedNotice')}
          <div className="mt-1">
            {t('subscriptions.terms.proposedSince', { date: formatDate(detail.pendingProposedAt) })}
          </div>
        </div>
      ) : (
        <div className="alert alert-secondary py-2 small mb-3">
          {t('subscriptions.terms.inForceSince', { date: formatDate(snapshot?.capturedAt) })}
        </div>
      )}

      <h6 className="fw-bold small text-uppercase text-muted">
        {t('subscriptions.terms.dataHolderGroupFields')}
      </h6>
      {!viewingProposed && !acceptanceRecorded && legalSections.length > 0 && (
        <p className="text-muted small fst-italic">{t('subscriptions.terms.noAcceptanceRecord')}</p>
      )}
      {legalSections.length === 0 ? (
        <p className="text-muted small fst-italic">{t('subscriptions.terms.noSections')}</p>
      ) : (
        legalSections.map((sec, i) => {
          const accepted = acceptedTerms.find(a => a.sectionId === sec.id);
          const lines = sectionClauseLines(sec, i + 1, referenceIndex);
          return (
            <div className="card mb-2" key={sec.id ?? i}>
              <div className="card-body py-2">
                <div className="d-flex justify-content-between align-items-start gap-2">
                  <div className="fw-semibold">{i + 1}. {sec.title}</div>
                  {accepted && (
                    <span className="badge bg-success-subtle text-success border flex-shrink-0">
                      <i className="fa-solid fa-check me-1" />
                      {t('subscriptions.terms.acceptedOn', {
                        date: new Date(accepted.acceptedAt).toLocaleDateString(),
                      })}
                    </span>
                  )}
                  {!accepted && acceptanceRecorded && (
                    <span className="badge bg-light text-muted border flex-shrink-0">
                      {t('subscriptions.terms.notAccepted')}
                    </span>
                  )}
                </div>
                {lines.length > 0 && (
                  <div className="bg-white px-1 pb-2 mt-2" style={{ whiteSpace: 'pre-wrap', fontSize: 13 }}>
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

      {linkFields.length > 0 && (
        <>
          <h6 className="fw-bold small text-uppercase text-muted mt-3">
            {t('subscriptions.terms.documents')}
          </h6>
          {linkFields.map((f) => (
            <div className="card mb-2" key={f.name}>
              <div className="card-body py-2">
                <div className="d-flex justify-content-between align-items-start gap-2">
                  <div className="fw-semibold">{f.name}</div>
                  {detail.subscriptionFieldValues?.[f.name] === true ? (
                    <span className="badge bg-success-subtle text-success border flex-shrink-0">
                      <i className="fa-solid fa-check me-1" />{t('subscriptions.terms.reviewed')}
                    </span>
                  ) : (
                    <span className="badge bg-light text-muted border flex-shrink-0">
                      {t('subscriptions.terms.notReviewed')}
                    </span>
                  )}
                </div>
                {f.description && <div className="text-muted small">{f.description}</div>}
                {f.defaultValue && (
                  <a href={f.defaultValue} target="_blank" rel="noopener noreferrer" className="small text-break">
                    <i className="fa-solid fa-up-right-from-square me-1" />{f.defaultValue}
                  </a>
                )}
              </div>
            </div>
          ))}
        </>
      )}

      {answerFields.length > 0 && (
        <>
          <h6 className="fw-bold small text-uppercase text-muted mt-3">
            {t('subscriptions.terms.requestorGroupFields')}
          </h6>
          <table className="table table-sm">
            <tbody>
              {answerFields.map((f) => (
                <tr key={f.name}>
                  <td className="text-muted" style={{ width: '40%' }}>
                    {f.name}
                    {f.required && <span className="text-danger ms-1">*</span>}
                    {f.description && <div className="small fst-italic">{f.description}</div>}
                  </td>
                  <td style={{ whiteSpace: 'pre-wrap' }}>
                    {answerText(detail.subscriptionFieldValues?.[f.name], t)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {requestTypes.length > 0 && (
        <>
          <h6 className="fw-bold small text-uppercase text-muted mt-3">
            {t('subscriptions.terms.requestTypes')}
          </h6>
          <table className="table table-sm">
            <tbody>
              {requestTypes.map((rt, i) => (
                <tr key={rt.typeCode || rt.name || i}>
                  <td>
                    {rt.name}
                    {rt.typeCode && <code className="ms-2 small">{rt.typeCode}</code>}
                    {rt.description && <div className="text-muted small">{rt.description}</div>}
                  </td>
                  <td className="text-end" style={{ width: 120 }}>
                    {rt.accessLevel != null && (
                      <span className="badge bg-secondary">
                        {t('subscriptions.terms.accessLevel', { level: rt.accessLevel })}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
};

export default SubscriptionTerms;
