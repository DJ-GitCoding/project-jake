/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import api from '../services/api';
import Pagination from './Pagination';
import usePagination from '../hooks/usePagination';
import { useT } from '../i18n';

const PARAM_CATEGORIES = [
  { label: 'Domain', fields: ['domainHandle','domainName','domainStatus','domainPort43','domainPublicIds'] },
  { label: 'Nameservers', fields: ['nameservers','nameserverHandle','nameserverName','nameserverIpAddresses','nameserverStatus'] },
  { label: 'Events', fields: ['events','eventRegistration','eventExpiration','eventLastChanged','eventLastUpdateOfRdapDb','eventTransfer'] },
  { label: 'Registrant', fields: ['registrantEntity','registrantHandle','registrantName','registrantOrganization','registrantEmail','registrantPhone','registrantFax','registrantAddress','registrantStreet','registrantCity','registrantStateProvince','registrantPostalCode','registrantCountry'] },
  { label: 'Admin', fields: ['adminEntity','adminHandle','adminName','adminOrganization','adminEmail','adminPhone','adminFax','adminAddress','adminStreet','adminCity','adminStateProvince','adminPostalCode','adminCountry'] },
  { label: 'Tech', fields: ['techEntity','techHandle','techName','techOrganization','techEmail','techPhone','techFax','techAddress','techStreet','techCity','techStateProvince','techPostalCode','techCountry'] },
  { label: 'Billing', fields: ['billingEntity','billingHandle','billingName','billingOrganization','billingEmail','billingPhone','billingFax','billingAddress','billingStreet','billingCity','billingStateProvince','billingPostalCode','billingCountry'] },
  { label: 'Registrar', fields: ['registrarEntity','registrarHandle','registrarName','registrarEmail','registrarPhone','registrarUrl','registrarAbuseContact'] },
  { label: 'DNSSEC', fields: ['dnssecData','dnssecDelegationSigned','dnssecDsData','dnssecKeyData'] },
  { label: 'Network', fields: ['networkHandle','networkName','networkType','networkStartAddress','networkEndAddress','networkIpVersion','networkParentHandle','networkCidr','networkCountry'] },
  { label: 'ASN', fields: ['autnumHandle','autnumStart','autnumEnd','autnumName','autnumType','autnumCountry'] },
];

const RdapParamBadges = ({ params }) => {
  const { t } = useT();
  if (!params) return null;
  const enabled = Object.values(params).filter(v => v === true).length;
  const total = Object.keys(params).length;
  return (
    <div className="mt-1">
      <small className="text-muted">{t('compliancePanel.rdapParams', { enabled, total })}</small>
      <div className="d-flex flex-wrap gap-1 mt-1">
        {PARAM_CATEGORIES.map(cat => {
          const catFields = cat.fields.filter(f => f in params);
          if (!catFields.length) return null;
          const allOn = catFields.every(f => params[f]);
          const noneOn = catFields.every(f => !params[f]);
          return (
            <span key={cat.label} className={`badge ${allOn ? 'bg-success' : noneOn ? 'bg-danger' : 'bg-warning text-dark'}`} style={{ fontSize: '10px' }}>
              {allOn ? '✓' : noneOn ? '✗' : '~'} {cat.label}
            </span>
          );
        })}
      </div>
    </div>
  );
};

const JakeCompliancePanel = ({ dataHolderId = null }) => {
  const { t } = useT();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const subscriptionsPager = usePagination(data?.subscriptions || []);

  const loadCompliance = async () => {
    setLoading(true);
    setError(null);
    try {
      const params = dataHolderId ? { data_holder_id: dataHolderId } : {};
      const response = await api.get('/api/rdap/jake-compliance', { params });
      if (response.data?.error) {
        setError(response.data.error);
        setData(null);
      } else {
        setData(response.data);
      }
    } catch (err) {
      setError(err.response?.data?.error || err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadCompliance(); }, [dataHolderId]);

  if (loading && !data) return <div className="text-center p-4"><span className="spinner-border spinner-border-sm me-2"></span>{t('compliancePanel.loading')}</div>;

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <p className="text-muted mb-0">{t('compliancePanel.intro')}</p>
        <button className="btn btn-outline-primary btn-sm" onClick={loadCompliance} disabled={loading}>
          {loading ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('compliancePanel.refreshing')}</> : <><i className="bi bi-arrow-clockwise me-1"></i>{t('compliancePanel.refresh')}</>}
        </button>
      </div>

      {error && <div className="alert alert-danger"><strong>{t('compliancePanel.errorLabel')}</strong> {error}</div>}

      {data && (
        <>
          {data.dataHolderGroups?.map((g, i) => (
            <div key={i} className="card mb-3">
              <div className="card-header d-flex align-items-center gap-2">
                <i className={`bi bi-circle-fill ${g.isActive ? 'text-success' : 'text-secondary'}`} style={{ fontSize: '8px' }}></i>
                <strong>{g.groupName}</strong>
                {g.isActive ? <span className="badge bg-success" style={{ fontSize: '10px' }}>{t('compliancePanel.active')}</span> : <span className="badge bg-secondary" style={{ fontSize: '10px' }}>{t('compliancePanel.inactive')}</span>}
              </div>
              <div className="card-body">
                {g.description && <p className="text-muted small mb-3">{g.description}</p>}

                {g.templates?.filter(tmpl => tmpl.isPublished !== false).map((tmpl, ti) => (
                  <div key={ti} className="mb-3 p-3 rounded" style={{ backgroundColor: '#f8f9fa', border: '1px solid #e9ecef' }}>
                    <div className="d-flex align-items-center flex-wrap gap-2 mb-1">
                      <i className="bi bi-file-earmark-text text-primary"></i>
                      <strong>{tmpl.name}</strong>
                      {tmpl.defaultAccessLevel != null && <span className="badge bg-info" style={{ fontSize: '10px' }}>{t('compliancePanel.defaultLevel', { level: tmpl.defaultAccessLevel })}</span>}
                      {tmpl.highestAccessLevel != null && <span className="badge bg-primary" style={{ fontSize: '10px' }}>{t('compliancePanel.maxLevel', { level: tmpl.highestAccessLevel })}</span>}
                      {tmpl.supportsConfidential && <span className="badge bg-warning text-dark" style={{ fontSize: '9px' }}>🔒 {t('compliancePanel.confidential')}</span>}
                      {tmpl.supportsExigent && <span className="badge bg-danger" style={{ fontSize: '9px' }}>⚡ {t('compliancePanel.exigent')}</span>}
                    </div>
                    {tmpl.description && <div className="text-muted small mb-2">{tmpl.description}</div>}

                    {tmpl.requestTypes?.map((rt, j) => (
                      <div key={j} className="ms-3 mb-2 p-2 rounded" style={{ backgroundColor: 'white', border: '1px solid #dee2e6' }}>
                        <div className="d-flex align-items-center flex-wrap gap-2">
                          <span className="fw-semibold">{rt.name}</span>
                          <span className="badge bg-primary" style={{ fontSize: '10px' }}>{t('compliancePanel.level', { level: rt.accessLevel })}</span>
                          {rt.supportsConfidential && <span className="badge bg-warning text-dark" style={{ fontSize: '9px' }}>🔒 {t('compliancePanel.confidential')}</span>}
                          {rt.supportsExigent && <span className="badge bg-danger" style={{ fontSize: '9px' }}>⚡ {t('compliancePanel.exigent')}</span>}
                          {rt.requiresManualApproval && <span className="badge bg-secondary" style={{ fontSize: '9px' }}>✋ {t('compliancePanel.manualApproval')}</span>}
                        </div>
                        {rt.description && <div className="text-muted small">{rt.description}</div>}
                        {rt.customParameters && rt.customParameters.length > 0 && (
                          <div className="mt-1">
                            <small className="text-muted fw-semibold">{t('compliancePanel.parameters')}</small>
                            <div className="ms-1 mt-1" style={{ fontSize: '12px' }}>
                              {rt.customParameters.map((p, pi) => (
                                <div key={pi} className="d-flex align-items-center gap-2 mb-1">
                                  <code className="small">{p.name}</code>
                                  <span className="badge bg-light text-dark border" style={{ fontSize: '10px' }}>{p.dataType}</span>
                                  {p.required && <span className="badge bg-danger" style={{ fontSize: '9px' }}>Required</span>}
                                  {p.description && <span className="text-muted">— {p.description}</span>}
                                  {p.dataType === 'file' && p.allowedFileTypes && <span className="text-muted small">({p.allowedFileTypes})</span>}
                                  {(p.minValue || p.maxValue) && <span className="text-muted small">[{p.minValue || '…'}–{p.maxValue || '…'}]</span>}
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                        <RdapParamBadges params={rt.rdapParameters} />
                      </div>
                    ))}
                  </div>
                ))}

                {(!g.templates || g.templates.filter(tmpl => tmpl.isPublished !== false).length === 0) && (
                  <p className="text-muted small mb-0">{t('compliancePanel.noPublishedTemplates')}</p>
                )}
              </div>
            </div>
          ))}

          {data.subscriptions?.length > 0 && (
            <div className="card mb-3">
              <div className="card-header"><strong>{t('compliancePanel.activeSubscriptions')}</strong></div>
              <div className="card-body p-0">
                <table className="table table-sm mb-0">
                  <thead><tr><th>{t('compliancePanel.colSubscriber')}</th><th>{t('compliancePanel.colTemplate')}</th><th>{t('compliancePanel.colAccessLevel')}</th></tr></thead>
                  <tbody>
                    {subscriptionsPager.pageItems.map((s, i) => (
                      <tr key={i}>
                        <td>{s.subscriptionName || '—'}</td>
                        <td>{s.templateName || '—'}</td>
                        <td><span className="badge bg-primary" style={{ fontSize: '10px' }}>{t('compliancePanel.level', { level: s.effectiveAccessLevel })}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <div className="px-3 pb-2">
                  <Pagination
                    page={subscriptionsPager.page}
                    pageSize={subscriptionsPager.pageSize}
                    totalItems={subscriptionsPager.totalItems}
                    onPageChange={subscriptionsPager.setPage}
                    onPageSizeChange={subscriptionsPager.setPageSize}
                    itemLabel={t('compliancePanel.subscriptionsLabel')}
                  />
                </div>
              </div>
            </div>
          )}

          {(!data.dataHolderGroups || data.dataHolderGroups.length === 0) && (!data.subscriptions || data.subscriptions.length === 0) && (
            <div className="alert alert-info">{t('compliancePanel.noMemberships')}</div>
          )}
        </>
      )}
    </div>
  );
};

export default JakeCompliancePanel;