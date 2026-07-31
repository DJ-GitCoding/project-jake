/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useRef, useMemo } from 'react';
import api from '../services/api';
import RequestTypeSelector from './RequestTypeSelector';
import { useAlert } from '../contexts/AlertContext';
import ReflectiveRdapRenderer from './ReflectiveRdapRenderer';

const RDAPSearch = () => {
  const [query, setQuery] = useState('');
  const [queryType, setQueryType] = useState('auto');
  const [results, setResults] = useState(null);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('parsed');
  const [selectedAgreement, setSelectedAgreement] = useState(null);
  const [activeDataHolders, setActiveDataHolders] = useState([]);
  const [polling, setPolling] = useState(false);
  const [lastChecked, setLastChecked] = useState(null);
  const [jakeCompliance, setJakeCompliance] = useState(false);
  const [confidential, setConfidential] = useState(false);
  const [exigent, setExigent] = useState(false);
  const [customParamValues, setCustomParamValues] = useState({});
  const [customParamFiles, setCustomParamFiles] = useState({});  // Stores actual File objects for file-type params
  const [isStuck, setIsStuck] = useState(false);

  const pollingRef = useRef(false);
  const stickyRef = useRef(null);
  const sentinelRef = useRef(null);

  const { showError, showAlert, showSuccess } = useAlert();

  // Detect when sticky form is stuck to show shadow
  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel) return;
    const observer = new IntersectionObserver(
      ([entry]) => setIsStuck(!entry.isIntersecting),
      { threshold: 1, rootMargin: '-57px 0px 0px 0px' }
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, []);

  const { hasConfidentialType, hasExigentType } = useMemo(() => {
    const rt = selectedAgreement?.requestType;
    if (!rt) return { hasConfidentialType: false, hasExigentType: false };
    return { hasConfidentialType: !!rt.supportsConfidential, hasExigentType: !!rt.supportsExigent };
  }, [selectedAgreement]);

  const activeCustomParams = useMemo(() => {
    const rt = selectedAgreement?.requestType;
    if (!rt?.customParameters || rt.customParameters.length === 0) return [];
    return [...rt.customParameters].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
  }, [selectedAgreement]);

  useEffect(() => {
    const defaults = {};
    activeCustomParams.forEach(p => {
      if (p.defaultValue !== undefined && p.defaultValue !== null && p.defaultValue !== '') defaults[p.name] = p.defaultValue;
    });
    setCustomParamValues(defaults);
  }, [activeCustomParams]);

  useEffect(() => {
    if (!hasConfidentialType && confidential) setConfidential(false);
    if (!hasExigentType && exigent) setExigent(false);
  }, [hasConfidentialType, hasExigentType]);

  useEffect(() => { fetchActiveDataHolders(); }, []);

  useEffect(() => {
    if (!results?.pending || !results?.requestId) return;
    pollingRef.current = true;
    const poll = async () => {
      if (!pollingRef.current) return;
      setPolling(true);
      try { const resolved = await pollRequestStatus(results.requestId); setLastChecked(new Date()); if (resolved) pollingRef.current = false; }
      finally { setPolling(false); }
    };
    const intervalId = setInterval(poll, 5000);
    return () => { pollingRef.current = false; clearInterval(intervalId); };
  }, [results?.pending, results?.requestId]);

  const fetchActiveDataHolders = async () => {
    try { const response = await api.get('/api/rdap/data-holders/active'); setActiveDataHolders(response.data?.data_holders || []); }
    catch (err) { console.error('Failed to fetch active data holders:', err); }
  };

  const detectQueryType = (q) => {
    if (/^(AS)?\d+$/i.test(q)) return 'asn';
    if (/^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(q) || q.includes(':')) return 'ip';
    if (q.includes('-') && !q.includes('.')) return 'entity';
    return 'domain';
  };

  const resolveRequestType = (agreement, explicitRequestType) => {
    if (!agreement?.requestTypes || agreement.requestTypes.length === 0) return explicitRequestType;
    if (exigent) { const t = agreement.requestTypes.find(rt => rt.supportsExigent); if (t) return t; }
    if (confidential) { const t = agreement.requestTypes.find(rt => rt.supportsConfidential); if (t) return t; }
    if (explicitRequestType) return explicitRequestType;
    return agreement.requestTypes.find(rt => rt.typeCode === 1) || agreement.requestTypes[0];
  };

  const buildQueryParams = () => {
    const params = { jakeCompliance, confidential, exigent };
    if (!selectedAgreement) return params;
    const { agreement, requestType: explicitRequestType } = selectedAgreement;
    const effectiveRequestType = resolveRequestType(agreement, explicitRequestType);
    if (agreement?.requestorGroupCode && effectiveRequestType?.typeCode != null) {
      params.requestorGroup = agreement.requestorGroupCode;
      params.requestType = effectiveRequestType.typeCode;
      params.dataHolderGroup = agreement.dataHolderGroupCode || 'DH-GRP-01';
      return params;
    }
    if (agreement?.name) { params.agreements = agreement.name; return params; }
    if (typeof selectedAgreement === 'string') { params.agreements = selectedAgreement; return params; }
    return params;
  };

  const getCustomParamsPayload = () => {
    if (activeCustomParams.length === 0) return null;
    const payload = {}; let hasValues = false;
    activeCustomParams.forEach(p => { const val = customParamValues[p.name]; if (val !== undefined && val !== null && val !== '') { payload[p.name] = val; hasValues = true; } });
    return hasValues ? payload : null;
  };

  const handleSearch = async (e) => {
    e?.preventDefault();
    if (!query.trim()) { showError('Please enter a domain, IP address, or ASN'); return; }
    const missingRequired = activeCustomParams.filter(p => p.required).filter(p => { const val = customParamValues[p.name]; return val === undefined || val === null || val === ''; });
    if (missingRequired.length > 0) { showError(`Please fill in required fields: ${missingRequired.map(p => p.name).join(', ')}`); return; }
    setLoading(true); setLastChecked(null);
    const detectedType = queryType !== 'auto' ? queryType : detectQueryType(query.trim());
    try {
      const agreementParams = buildQueryParams();
      const customPayload = getCustomParamsPayload();
      const headers = {};

      // Check if any file-type params have actual File objects to upload
      const fileEntries = Object.entries(customParamFiles).filter(([, f]) => f instanceof File);
      let response;

      if (fileEntries.length > 0) {
        // Use multipart POST to upload all file(s) with the query
        const formData = new FormData();
        formData.append('query', query.trim());

        // Append every file and collect their parameter names
        const paramNames = [];
        fileEntries.forEach(([paramName, fileObj]) => {
          formData.append('files', fileObj);
          paramNames.push(paramName);
        });
        formData.append('fileParamNames', paramNames.join(','));

        if (queryType !== 'auto') formData.append('queryType', queryType);
        Object.entries(agreementParams).forEach(([k, v]) => {
          if (v !== undefined && v !== null) formData.append(k, String(v));
        });
        // Include customParams (filenames are kept for display; backend injects __fileId etc.)
        if (customPayload) {
          formData.append('customParams', JSON.stringify(customPayload));
        }
        response = await api.post('/api/rdap/query-with-file', formData, {
          headers: { ...headers, 'Content-Type': 'multipart/form-data' },
        });
      } else {
        // Standard GET query (no file upload)
        const params = { query: query.trim(), ...agreementParams };
        if (queryType !== 'auto') params.query_type = queryType;
        if (customPayload) params.customParams = JSON.stringify(customPayload);
        response = await api.get('/api/rdap/query', { params, headers });
      }

      const data = response.data;
      if (data.success === false || data.error === true) {
        showAlert({ type: data.errorCode || 'error', message: data.errorMessage || 'An error occurred',
          details: { errorCode: data.errorCode, queryType: data.queryType || detectedType, queryValue: data.queryValue || query.trim(), source: data.source || 'RDAP', rdapServer: data.rdapServer, httpStatusCode: data.raw_data?.httpStatusCode || data.httpStatusCode, serverMessage: data.raw_data?.serverMessage || data.raw_data?.server_description, serverSignature: data.raw_data?.serverSignature },
          onRetry: () => handleSearch() });
        setLoading(false); return;
      }
      const rawData = data.raw_data || data.data || data;
      const status = rawData?.status;
      const isPending = status === 'pending' || status === 'PENDING' || (typeof status === 'string' && status.toLowerCase() === 'pending');
      const isDenied = status === 'denied' || status === 'DENIED' || (typeof status === 'string' && status.toLowerCase() === 'denied');
      const agreementName = selectedAgreement?.agreement?.name || (typeof selectedAgreement === 'string' ? selectedAgreement : null);
      if (isDenied) {
        const reason = rawData?.error_message || rawData?.adminNotes || rawData?.description || rawData?.errorMessage || data.error_message || 'No reason provided';
        const reasonStr = Array.isArray(reason) ? reason.join(' ') : String(reason);
        showAlert({ type: 'error', title: 'Request Denied', message: reasonStr, details: { source: 'Data Holder' } });
        setResults({ success: false, query_type: detectedType, raw_data: rawData, timestamp: data.timestamp || new Date().toISOString(), source: data.source || 'Custom Data Holder', requestId: rawData.requestId || data.requestId, pending: false, denied: true, deniedReason: reasonStr });
      } else if (isPending) {
        showAlert({ type: 'warning', toast: true, message: 'Manual verification required — your request has been submitted for review.' });
        setResults({ success: true, query_type: detectedType, raw_data: rawData, timestamp: data.timestamp || new Date().toISOString(), source: data.source || 'Custom Data Holder', rdapServer: data.rdap_server || data.rdapServer, agreement_name: agreementName, pending: true, requestId: rawData.requestId || data.requestId, pollUrl: rawData.pollUrl || data.pollUrl });
      } else {
        const accessLevel = data.accessLevel ?? rawData?.accessLevel;
        const agreementNames = rawData?.agreementNames || data.agreementNames;
        const displayAgreementName = rawData?.agreementName || data.agreementName || (agreementNames && agreementNames[0]) || agreementName;
        setResults({ success: true, query_type: data.query_type || rawData?.objectClassName || detectedType, raw_data: rawData, parsed_data: data.parsed_data, timestamp: data.timestamp || new Date().toISOString(), source: data.source || 'IANA', rdapServer: data.rdap_server || data.rdapServer, agreement_name: displayAgreementName, accessLevel });
      }
      setActiveTab('parsed');
    } catch (err) {
      console.error('RDAP search error:', err); const errorData = err.response?.data;

      const fileFailures = errorData?.fileFailures;
      if (Array.isArray(fileFailures) && fileFailures.length > 0) {
        const details = {};
        fileFailures.forEach((f) => {
          const label = f.paramName ? `${f.filename} (${f.paramName})` : f.filename;
          details[label] = (f.messages || []).join(' ') || f.error;
        });
        showAlert({
          type: 'error',
          title: 'Attachment Does Not Meet Requirements',
          message: errorData?.message || 'An attached file does not meet the required format for this request type.',
          details,
        });
        return;
      }

      showAlert({ type: err.response?.status?.toString() || 'error', message: errorData?.errorMessage || errorData?.detail || errorData?.message || 'Failed to perform RDAP query. Please try again.',
        details: { errorCode: errorData?.errorCode || err.response?.status?.toString(), queryType: detectedType, queryValue: query.trim(), source: errorData?.source || 'RDAP', rdapServer: errorData?.rdapServer }, onRetry: () => handleSearch() });
    } finally { setLoading(false); }
  };

  const pollRequestStatus = async (requestId) => {
    try {
      const response = await api.get(`/api/rdap/requests/${requestId}`, { params: { check_status: true } });
      const data = response.data; const local = data.local; const dh = data.dataHolder; const dhData = dh?.data || dh || {};
      const statusStr = (local?.status || (typeof dhData.status === 'string' ? dhData.status : '')).toLowerCase();
      if (statusStr === 'pending') return false;
      if (statusStr === 'denied') {
        const reason = local?.error_message || dhData.description || dhData.message || dhData.errorMessage || dhData.adminNotes || 'No reason provided';
        const reasonStr = Array.isArray(reason) ? reason.join(' ') : String(reason);
        showAlert({ type: 'error', title: 'Request Denied', message: reasonStr, details: { source: 'Data Holder' } });
        setResults((prev) => (prev ? { ...prev, pending: false, denied: true, deniedReason: reasonStr } : null)); return true;
      }
      if (statusStr === 'approved') {
        showSuccess('Request has been approved!', { toast: true });
        const rdapData = local?.rdap_data || dhData.rdap_data || dhData;
        setResults((prev) => ({ ...prev, pending: false, raw_data: rdapData, accessLevel: local?.access_level_granted ?? dhData.accessLevel, timestamp: dhData.timestamp || new Date().toISOString() })); return true;
      }
      const rdapFromLocal = local?.rdap_data;
      const hasRdapData = rdapFromLocal?.objectClassName || rdapFromLocal?.ldhName || dhData.objectClassName || dhData.ldhName || dhData.handle || dhData.accessLevel !== undefined;
      if (hasRdapData) {
        showSuccess('Request has been approved!', { toast: true });
        setResults((prev) => ({ ...prev, pending: false, raw_data: rdapFromLocal || dhData, accessLevel: local?.access_level_granted ?? dhData.accessLevel, timestamp: dhData.timestamp || new Date().toISOString() })); return true;
      }
      showAlert({ type: 'warning', message: 'Received unexpected response from data holder.', details: { source: 'Data Holder' } }); return false;
    } catch (err) {
      console.error('Failed to poll status:', err); const errorData = err.response?.data;
      if (err.response?.status === 403) { const reason = errorData?.description || errorData?.errorMessage || errorData?.message || 'No reason provided'; const reasonStr = Array.isArray(reason) ? reason.join(' ') : String(reason); showAlert({ type: 'error', title: 'Request Denied', message: reasonStr, details: { source: 'Data Holder' } }); setResults((prev) => (prev ? { ...prev, pending: false, denied: true, deniedReason: reasonStr } : null)); return true; }
      if (err.response?.status === 410) { showAlert({ type: 'warning', title: 'Request Expired', message: 'This request has expired. Please submit a new query.', details: { source: 'Data Holder' } }); setResults(null); return true; }
      showError('Failed to check request status. Please try again.'); return false;
    }
  };

  const renderAccessLevelBadge = (level) => {
    if (level === undefined || level === null) return null;
    const c = { 0: 'bg-danger', 1: 'bg-warning text-dark', 2: 'bg-info text-dark', 3: 'bg-success' };
    const l = { 0: 'L0 Minimal', 1: 'L1 Basic', 2: 'L2 Standard', 3: 'L3 Full' };
    return <span className={`badge ${c[level] || 'bg-secondary'}`} style={{ fontSize: '10px' }}>{l[level] || `L${level}`}</span>;
  };

  const renderJakeCompliance = () => {
    const jake = results?.raw_data?.jakeCompliance;
    if (!jake) return null;

    const groups = jake.dataHolderGroups || [];
    const subs = jake.subscriptions || [];
    const matchedSub = jake.subscription;

    // Group RDAP parameters by category for display
    const paramCategories = [
      { label: 'Domain', prefix: 'domain', fields: ['domainHandle','domainName','domainStatus','domainPort43','domainPublicIds'] },
      { label: 'Nameservers', prefix: 'nameserver', fields: ['nameservers','nameserverHandle','nameserverName','nameserverIpAddresses','nameserverStatus'] },
      { label: 'Events', prefix: 'event', fields: ['events','eventRegistration','eventExpiration','eventLastChanged','eventLastUpdateOfRdapDb','eventTransfer'] },
      { label: 'Registrant', prefix: 'registrant', fields: ['registrantEntity','registrantHandle','registrantName','registrantOrganization','registrantEmail','registrantPhone','registrantFax','registrantAddress','registrantStreet','registrantCity','registrantStateProvince','registrantPostalCode','registrantCountry'] },
      { label: 'Admin Contact', prefix: 'admin', fields: ['adminEntity','adminHandle','adminName','adminOrganization','adminEmail','adminPhone','adminFax','adminAddress','adminStreet','adminCity','adminStateProvince','adminPostalCode','adminCountry'] },
      { label: 'Tech Contact', prefix: 'tech', fields: ['techEntity','techHandle','techName','techOrganization','techEmail','techPhone','techFax','techAddress','techStreet','techCity','techStateProvince','techPostalCode','techCountry'] },
      { label: 'Billing Contact', prefix: 'billing', fields: ['billingEntity','billingHandle','billingName','billingOrganization','billingEmail','billingPhone','billingFax','billingAddress','billingStreet','billingCity','billingStateProvince','billingPostalCode','billingCountry'] },
      { label: 'Registrar', prefix: 'registrar', fields: ['registrarEntity','registrarHandle','registrarName','registrarEmail','registrarPhone','registrarUrl','registrarAbuseContact'] },
      { label: 'DNSSEC', prefix: 'dnssec', fields: ['dnssecData','dnssecDelegationSigned','dnssecDsData','dnssecKeyData'] },
      { label: 'Network', prefix: 'network', fields: ['networkHandle','networkName','networkType','networkStartAddress','networkEndAddress','networkIpVersion','networkParentHandle','networkCidr','networkCountry'] },
      { label: 'ASN', prefix: 'autnum', fields: ['autnumHandle','autnumStart','autnumEnd','autnumName','autnumType','autnumCountry'] },
      { label: 'Other', prefix: '', fields: ['links','notices','remarks'] },
    ];

    const renderRdapParams = (params) => {
      if (!params || typeof params !== 'object') return null;
      const enabledCount = Object.values(params).filter(v => v === true).length;
      const totalCount = Object.keys(params).length;

      return (
        <div className="mt-1">
          <div className="d-flex align-items-center gap-2 mb-1">
            <small className="text-muted fw-semibold">RDAP Parameters</small>
            <small className="text-muted">({enabledCount}/{totalCount} enabled)</small>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: '4px', fontSize: '11px' }}>
            {paramCategories.map(cat => {
              const catFields = cat.fields.filter(f => f in params);
              if (catFields.length === 0) return null;
              const allEnabled = catFields.every(f => params[f] === true);
              const noneEnabled = catFields.every(f => params[f] !== true);
              return (
                <div key={cat.label} className="d-flex align-items-center gap-1 px-2 py-1 rounded" style={{ backgroundColor: allEnabled ? 'rgba(34,197,94,0.08)' : noneEnabled ? 'rgba(239,68,68,0.06)' : 'rgba(245,158,11,0.08)' }}>
                  <i className={`bi ${allEnabled ? 'bi-check-circle-fill text-success' : noneEnabled ? 'bi-x-circle-fill text-danger' : 'bi-dash-circle-fill text-warning'}`} style={{ fontSize: '10px' }}></i>
                  <span className={allEnabled ? 'text-success' : noneEnabled ? 'text-danger' : 'text-warning'}>{cat.label}</span>
                </div>
              );
            })}
          </div>
        </div>
      );
    };

    return (
      <div className="mt-3 text-start">
        <div className="card border-0" style={{ backgroundColor: '#f0f4ff' }}>
          <div className="card-body p-3">
            <h6 className="mb-3 d-flex align-items-center gap-2" style={{ color: '#3b5998' }}>
              <i className="bi bi-shield-check"></i> JAKE Compliance
            </h6>

            {/* Group Memberships with Templates */}
            {groups.length > 0 && (
              <div className="mb-3">
                <div className="fw-semibold small text-muted mb-2">Data Holder Group Memberships</div>
                {groups.map((g, i) => (
                  <div key={i} className="mb-3 p-2 rounded" style={{ backgroundColor: 'rgba(255,255,255,0.7)' }}>
                    <div className="d-flex align-items-start gap-2 mb-2">
                      <i className={`bi bi-circle-fill mt-1 ${g.isActive ? 'text-success' : 'text-secondary'}`} style={{ fontSize: '7px' }}></i>
                      <div style={{ fontSize: '13px' }}>
                        <div><span className="text-muted">Group Name:</span> <strong>{g.groupName}</strong></div>
                        {g.description && <div><span className="text-muted">Description:</span> {g.description}</div>}
                        <div><span className="text-muted">Status:</span> {g.isActive ? <span className="text-success">Active</span> : <span className="text-secondary">Inactive</span>}</div>
                      </div>
                    </div>

                    {/* Templates for this group */}
                    {g.templates && g.templates.length > 0 && (
                      <div className="ms-3 mt-2">
                        <div className="fw-semibold small text-muted mb-1">Templates</div>
                        {g.templates.filter(t => t.isPublished !== false).map((t, ti) => (
                          <div key={ti} className="p-2 mb-2 rounded" style={{ backgroundColor: 'rgba(0,0,0,0.03)', border: '1px solid rgba(0,0,0,0.06)', fontSize: '13px' }}>
                            <div className="d-flex align-items-center flex-wrap gap-2 mb-1">
                              <strong>{t.name}</strong>
                              {t.defaultAccessLevel != null && <span className="badge bg-info" style={{ fontSize: '10px' }}>Default: L{t.defaultAccessLevel}</span>}
                              {t.highestAccessLevel != null && <span className="badge bg-primary" style={{ fontSize: '10px' }}>Max: L{t.highestAccessLevel}</span>}
                              {t.supportsConfidential && <span className="badge bg-warning text-dark" style={{ fontSize: '9px' }}>🔒 Confidential</span>}
                              {t.supportsExigent && <span className="badge bg-danger" style={{ fontSize: '9px' }}>⚡ Exigent</span>}
                            </div>
                            {t.description && <div className="text-muted mb-1" style={{ fontSize: '12px' }}>{t.description}</div>}

                            {/* Request types within template */}
                            {t.requestTypes && t.requestTypes.length > 0 && (
                              <div className="mt-2">
                                <div className="fw-semibold small text-muted mb-1">Request Types</div>
                                {t.requestTypes.map((rt, j) => (
                                  <div key={j} className="p-2 mb-1 rounded" style={{ backgroundColor: 'rgba(255,255,255,0.6)', border: '1px solid rgba(0,0,0,0.04)' }}>
                                    <div className="d-flex align-items-center flex-wrap gap-2 mb-1">
                                      <strong>{rt.name}</strong>
                                      <span className="badge bg-primary" style={{ fontSize: '10px' }}>Level {rt.accessLevel}</span>
                                      {rt.supportsConfidential && <span className="badge bg-warning text-dark" style={{ fontSize: '9px' }}>🔒 Confidential</span>}
                                      {rt.supportsExigent && <span className="badge bg-danger" style={{ fontSize: '9px' }}>⚡ Exigent</span>}
                                      {rt.requiresManualApproval && <span className="badge bg-secondary" style={{ fontSize: '9px' }}>✋ Manual</span>}
                                    </div>
                                    {rt.description && <div className="text-muted" style={{ fontSize: '12px' }}>{rt.description}</div>}
                                    {rt.customParameters && rt.customParameters.length > 0 && (
                                      <div className="mt-1">
                                        <small className="text-muted fw-semibold">Parameters</small>
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
                                    {renderRdapParams(rt.rdapParameters)}
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}

            {/* Active Subscriptions */}
            {subs.length > 0 && (
              <div className="mb-3">
                <div className="fw-semibold small text-muted mb-2">Active Subscriptions</div>
                {subs.map((s, i) => (
                  <div key={i} className="p-2 rounded mb-2" style={{ backgroundColor: 'rgba(255,255,255,0.7)', fontSize: '13px' }}>
                    <div><span className="text-muted">Subscription:</span> <strong>{s.subscriptionName || '—'}</strong></div>
                    {s.templateName && <div><span className="text-muted">Template:</span> {s.templateName}</div>}
                    {s.requestorGroupCode && <div><span className="text-muted">Requestor Group Code:</span> <code>{s.requestorGroupCode}</code></div>}
                    {s.effectiveAccessLevel != null && <div><span className="text-muted">Access Level:</span> <span className="badge bg-primary" style={{ fontSize: '10px' }}>Level {s.effectiveAccessLevel}</span></div>}
                    {s.requestTypes && s.requestTypes.length > 0 && (
                      <div className="mt-2">
                        <span className="text-muted fw-semibold small">Request Types</span>
                        <div className="mt-1" style={{ display: 'grid', gap: '6px' }}>
                          {s.requestTypes.map((rt, j) => (
                            <div key={j} className="p-2 rounded" style={{ backgroundColor: 'rgba(0,0,0,0.03)', border: '1px solid rgba(0,0,0,0.06)' }}>
                              <div className="d-flex align-items-center flex-wrap gap-2 mb-1">
                                <strong>{rt.name}</strong>
                                <span className="badge bg-primary" style={{ fontSize: '10px' }}>Level {rt.accessLevel}</span>
                                {rt.supportsConfidential && <span className="badge bg-warning text-dark" style={{ fontSize: '9px' }}>🔒 Confidential</span>}
                                {rt.supportsExigent && <span className="badge bg-danger" style={{ fontSize: '9px' }}>⚡ Exigent</span>}
                                {rt.requiresManualApproval && <span className="badge bg-secondary" style={{ fontSize: '9px' }}>✋ Manual</span>}
                              </div>
                              {rt.description && <div className="text-muted" style={{ fontSize: '12px' }}>{rt.description}</div>}
                              {rt.customParameters && rt.customParameters.length > 0 && (
                                <div className="mt-1">
                                  <small className="text-muted fw-semibold">Parameters</small>
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
                              {renderRdapParams(rt.rdapParameters)}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}

            {/* Matched subscription for this request */}
            {matchedSub && (
              <div className="p-2 rounded" style={{ backgroundColor: 'rgba(34,197,94,0.08)', border: '1px solid rgba(34,197,94,0.2)', fontSize: '13px' }}>
                <div className="fw-semibold small text-success mb-1"><i className="bi bi-check-circle me-1"></i>Matched Subscription</div>
                <div><span className="text-muted">Agreement:</span> <strong>{matchedSub.agreementName || matchedSub.templateName}</strong></div>
                <div><span className="text-muted">Access Level:</span> <span className="badge bg-success" style={{ fontSize: '10px' }}>Level {matchedSub.accessLevel}</span></div>
              </div>
            )}

            {groups.length === 0 && subs.length === 0 && !matchedSub && (
              <div className="text-muted small">No group memberships or subscriptions found.</div>
            )}
          </div>
        </div>
      </div>
    );
  };

  const renderPendingRequest = () => {
    if (results.denied) {
      return (
        <div className="text-center p-4">
          <i className="bi bi-x-circle display-4 text-danger"></i>
          <h5 className="text-danger mt-2">Request Denied</h5>
          <p className="text-muted small">The data holder administrator has denied your request.</p>
          {results.deniedReason && results.deniedReason !== 'No reason provided' && <div className="alert alert-danger text-start small mb-2"><strong>Reason:</strong> {results.deniedReason}</div>}
          <div className="alert alert-secondary small"><strong>Request ID:</strong> <code>{results.requestId}</code></div>
          {renderJakeCompliance()}
          <button className="btn btn-primary btn-sm" onClick={() => setResults(null)}><i className="bi bi-arrow-left me-1"></i>New Search</button>
        </div>
      );
    }
    return (
      <div className="text-center p-4">
        <i className="bi bi-hourglass-split display-4 text-warning"></i>
        <h5 className="text-warning mt-2">Manual Verification Required</h5>
        <p className="text-muted small">Your request requires manual approval from the data holder administrator.</p>
        <div className="alert alert-info small"><strong>Request ID:</strong> <code>{results.requestId}</code></div>
        <div className="d-flex align-items-center justify-content-center gap-2 mb-2">
          <span className="spinner-border spinner-border-sm text-primary" role="status"></span>
          <span className="text-muted small">{polling ? 'Checking status...' : 'Auto-checking every 5s'}</span>
        </div>
        {lastChecked && <small className="text-muted d-block mb-2"><i className="bi bi-clock me-1"></i>Last checked: {lastChecked.toLocaleTimeString()}</small>}
        {renderJakeCompliance()}
        <button className="btn btn-outline-primary btn-sm mt-2" onClick={async () => { setPolling(true); try { await pollRequestStatus(results.requestId); setLastChecked(new Date()); } finally { setPolling(false); } }} disabled={polling}>
          {polling ? <><span className="spinner-border spinner-border-sm me-1"></span>Checking...</> : <><i className="bi bi-arrow-clockwise me-1"></i>Check Now</>}
        </button>
      </div>
    );
  };

  const getRdapData = () => results ? (results.raw_data || results.data || results) : null;
  const getSourceBadgeClass = (source) => { if (!source) return 'bg-secondary'; const s = source.toLowerCase(); return s === 'iana' ? 'bg-success' : s === 'custom' ? 'bg-info' : 'bg-secondary'; };
  const getSelectedDisplayInfo = () => { if (!selectedAgreement) return null; const { agreement, requestType } = selectedAgreement; if (!agreement) return null; const parts = [agreement.name]; if (requestType) parts.push(requestType.name); return parts.join(' → '); };

  return (
    <div>
      {/* React-managed sentinel for sticky detection */}
      <div ref={sentinelRef} style={{ height: '1px', marginBottom: '-1px' }} />

      {/* Sticky search form */}
      <div
        ref={stickyRef}
        style={{
          position: 'sticky',
          top: 66,
          zIndex: 1020,
          backgroundColor: '#fff',
          margin: '0 -16px',
          padding: '10px 16px',
          borderBottom: isStuck ? '1px solid #e0e0e0' : '1px solid transparent',
          boxShadow: isStuck ? '0 2px 8px rgba(0,0,0,0.06)' : 'none',
          transition: 'box-shadow 0.2s ease, border-color 0.2s ease',
        }}
      >
        <form onSubmit={handleSearch}>
          {/* Row 1: Query + Request Type — independent heights */}
          <div className="row g-2">
            <div className="col-12 col-md-6">
              <label className="form-label fw-semibold mb-0" style={{ fontSize: '12px' }}>Query</label>
              <input
                type="text"
                className="form-control form-control-sm"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Domain, IP, ASN, or entity handle"
                disabled={loading}
                required
              />
            </div>
            <div className="col-12 col-md-6">
              <label className="form-label fw-semibold mb-0" style={{ fontSize: '12px' }}>Request Type</label>
              <RequestTypeSelector
                selectedAgreement={selectedAgreement}
                onChange={setSelectedAgreement}
                placeholder="Select request type..."
              />
            </div>
          </div>
          {/* Row 2: Toggles + Search — always stable */}
          <div className="d-flex align-items-center gap-2 flex-wrap mt-2">
            <div className="form-check form-switch mb-0" style={{ minHeight: 'auto' }}>
              <input className="form-check-input" type="checkbox" role="switch" id="jakeCheck"
                checked={jakeCompliance} onChange={(e) => setJakeCompliance(e.target.checked)} disabled={loading} />
              <label className="form-check-label" htmlFor="jakeCheck" style={{ fontSize: '12px' }}>JAKE</label>
            </div>
            {hasConfidentialType && (
              <div className="form-check form-switch mb-0" style={{ minHeight: 'auto' }}>
                <input className="form-check-input" type="checkbox" role="switch" id="confCheck"
                  checked={confidential} onChange={(e) => setConfidential(e.target.checked)} disabled={loading} />
                <label className="form-check-label" htmlFor="confCheck" style={{ fontSize: '12px' }}>Conf.</label>
              </div>
            )}
            {hasExigentType && (
              <div className="form-check form-switch mb-0" style={{ minHeight: 'auto' }}>
                <input className="form-check-input" type="checkbox" role="switch" id="exigCheck"
                  checked={exigent} onChange={(e) => setExigent(e.target.checked)} disabled={loading} />
                <label className="form-check-label" htmlFor="exigCheck" style={{ fontSize: '12px' }}>Exig.</label>
              </div>
            )}
            {!selectedAgreement && !results && (
              <span className="text-muted" style={{ fontSize: '11px' }}>
                <i className="bi bi-info-circle me-1"></i>No request type selected — public RDAP lookup
              </span>
            )}
            <button type="submit" className="btn btn-primary btn-sm px-3 ms-auto" disabled={loading}>
              {loading ? <><span className="spinner-border spinner-border-sm me-1"></span>Searching...</> : <><i className="bi bi-search me-1"></i>Search</>}
            </button>
          </div>
        </form>
      </div>

      {/* Custom Parameters */}
      {activeCustomParams.length > 0 && (
        <div className="card border mt-2 mb-2">
          <div className="card-header bg-light py-1 px-3">
            <span className="fw-semibold small">
              <i className="bi bi-sliders me-1"></i>Parameters
              <span className="badge bg-secondary ms-1" style={{ fontSize: '10px' }}>{activeCustomParams.length}</span>
              {activeCustomParams.some(p => p.required) && <span className="badge bg-danger ms-1" style={{ fontSize: '10px' }}>{activeCustomParams.filter(p => p.required).length} req</span>}
            </span>
          </div>
          <div className="card-body py-2 px-3">
            <div className="row g-2">
              {activeCustomParams.map((param) => {
                const val = customParamValues[param.name] ?? '';
                const setVal = (v) => setCustomParamValues(prev => ({ ...prev, [param.name]: v }));
                const inputId = `cparam-${param.name}`;
                const colClass = ['text', 'json'].includes(param.dataType) ? 'col-12' : 'col-md-6 col-lg-4';
                return (
                  <div className={colClass} key={param.name}>
                    <label className="form-label small mb-0 fw-medium" htmlFor={inputId}>
                      {param.name.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase())}
                      {param.required && <span className="text-danger ms-1">*</span>}
                    </label>
                    {param.dataType === 'string' && <input type="text" id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} placeholder={param.placeholder || ''} maxLength={param.maxLength || undefined} disabled={loading} required={param.required} />}
                    {param.dataType === 'integer' && <input type="number" id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} placeholder={param.placeholder || ''} min={param.minValue || undefined} max={param.maxValue || undefined} step="1" disabled={loading} required={param.required} />}
                    {param.dataType === 'float' && <input type="number" id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} placeholder={param.placeholder || ''} min={param.minValue || undefined} max={param.maxValue || undefined} step="0.01" disabled={loading} required={param.required} />}
                    {param.dataType === 'boolean' && <div className="form-check form-switch mt-1"><input type="checkbox" id={inputId} className="form-check-input" checked={val === 'true' || val === true} onChange={(e) => setVal(e.target.checked ? 'true' : 'false')} disabled={loading} /><label className="form-check-label small" htmlFor={inputId}>{val === 'true' || val === true ? 'Yes' : 'No'}</label></div>}
                    {param.dataType === 'date' && <input type="date" id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} disabled={loading} required={param.required} />}
                    {param.dataType === 'datetime' && <input type="datetime-local" id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} disabled={loading} required={param.required} />}
                    {param.dataType === 'email' && <input type="email" id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} placeholder={param.placeholder || 'email@example.com'} maxLength={param.maxLength || undefined} disabled={loading} required={param.required} />}
                    {param.dataType === 'url' && <input type="url" id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} placeholder={param.placeholder || 'https://'} maxLength={param.maxLength || undefined} disabled={loading} required={param.required} />}
                    {param.dataType === 'enum' && <select id={inputId} className="form-select form-select-sm" value={val} onChange={(e) => setVal(e.target.value)} disabled={loading} required={param.required}><option value="">{param.placeholder || '— Select —'}</option>{(param.enumValues || '').split(',').map(o => o.trim()).filter(Boolean).map(opt => <option key={opt} value={opt}>{opt}</option>)}</select>}
                    {param.dataType === 'text' && <textarea id={inputId} className="form-control form-control-sm" value={val} onChange={(e) => setVal(e.target.value)} placeholder={param.placeholder || ''} rows={2} maxLength={param.maxLength || undefined} disabled={loading} required={param.required} />}
                    {param.dataType === 'json' && <textarea id={inputId} className="form-control form-control-sm font-monospace" value={val} onChange={(e) => setVal(e.target.value)} placeholder={param.placeholder || '{ }'} rows={2} disabled={loading} required={param.required} style={{ fontSize: '11px' }} />}
                    {param.dataType === 'file' && <input type="file" id={inputId} className="form-control form-control-sm" accept={param.allowedFileTypes || undefined} onChange={(e) => { const file = e.target.files?.[0]; if (file) { if (param.maxFileSizeMb && file.size > param.maxFileSizeMb * 1024 * 1024) { showError(`File exceeds ${param.maxFileSizeMb}MB`); e.target.value = ''; return; } setVal(file.name); setCustomParamFiles(prev => ({ ...prev, [param.name]: file })); } else { setCustomParamFiles(prev => { const n = { ...prev }; delete n[param.name]; return n; }); } }} disabled={loading} required={param.required} />}
                    {param.description && <div className="form-text" style={{ fontSize: '10px' }}>{param.description}</div>}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Results */}
      {results && (
        <div className="mt-2">
          <div className="d-flex flex-wrap align-items-center gap-2 mb-2" style={{ fontSize: '12px' }}>
            <span className="text-muted"><i className="bi bi-clock me-1"></i>{results.timestamp ? new Date(results.timestamp).toLocaleTimeString() : new Date().toLocaleTimeString()}</span>
            {results.source && <span className={`badge ${getSourceBadgeClass(results.source)}`} style={{ fontSize: '10px' }}>{results.source}</span>}
            {results.accessLevel !== undefined && renderAccessLevelBadge(results.accessLevel)}
            {results.agreement_name && <span className="badge bg-info" style={{ fontSize: '10px' }}><i className="bi bi-file-earmark-check me-1"></i>{results.agreement_name}</span>}
            {(results.query_type || results.raw_data?.type || results.raw_data?.objectClassName) && <span className="badge bg-primary" style={{ fontSize: '10px' }}>{(results.query_type || results.raw_data?.type || results.raw_data?.objectClassName || '').toUpperCase()}</span>}
            {results.rdapServer && <span className="text-muted d-none d-md-inline" style={{ fontSize: '11px' }}><i className="bi bi-server me-1"></i>{results.rdapServer.replace(/^https?:\/\//, '').replace(/\/$/, '')}</span>}
            <div className="ms-auto d-flex gap-1">
              <button className={`btn btn-sm ${activeTab === 'parsed' ? 'btn-primary' : 'btn-outline-secondary'}`} onClick={() => setActiveTab('parsed')} style={{ fontSize: '11px', padding: '2px 8px' }}>Parsed</button>
              <button className={`btn btn-sm ${activeTab === 'raw' ? 'btn-primary' : 'btn-outline-secondary'}`} onClick={() => setActiveTab('raw')} style={{ fontSize: '11px', padding: '2px 8px' }}>JSON</button>
            </div>
          </div>
          {activeTab === 'parsed' ? (
            results.pending || results.denied ? renderPendingRequest() : <><ReflectiveRdapRenderer data={getRdapData()} accessLevel={results.accessLevel} />{renderJakeCompliance()}</>
          ) : (
            <pre className="bg-dark text-light p-3 rounded mb-0" style={{ maxHeight: '70vh', overflowY: 'auto', fontSize: '12px' }}><code>{JSON.stringify(results, null, 2)}</code></pre>
          )}
        </div>
      )}
    </div>
  );
};

export default RDAPSearch;