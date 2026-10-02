/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useRef } from 'react';
import api from '../services/api';
import { useT } from '../i18n';
import { getState, subscribe, setSelected, selectedCodes, matchCount } from '../services/tldAgreementFilter';

/**
 * RequestTypeSelector - Single-select dropdown for selecting an agreement + request type
 */
const RequestTypeSelector = ({
  selectedAgreement = null,
  onChange,
  onError,
  disabled = false,
  placeholder = "Select a request type..."
}) => {
  const { t } = useT();
  const [agreements, setAgreements] = useState([]);
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [warnings, setWarnings] = useState([]);
  const [isOpen, setIsOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [expandedAgreementId, setExpandedAgreementId] = useState(null);
  const [tldFilter, setTldFilter] = useState(getState);
  const dropdownRef = useRef(null);

  const retryCountRef = useRef(0);
  const maxRetries = 3;

  useEffect(() => {
    fetchAgreements();
  }, []);

  useEffect(() => subscribe(setTldFilter), []);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsOpen(false);
        setExpandedAgreementId(null);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const fetchAgreements = async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await api.get('/api/agreements');

      const data = response.data;
      setAgreements(data.agreements || []);
      setGroups(data.groups || []);
      setWarnings(data.warnings || []);
      retryCountRef.current = 0;

      if (data.agreements?.length === 0) {
        setError(t('requestTypeSelector.noneAvailable'));
      }

    } catch (err) {
      console.error('Error fetching agreements:', err);
      // Auto-retry on server errors (e.g. agreement backend not ready yet)
      if (retryCountRef.current < maxRetries && (!err.response || err.response.status >= 500)) {
        retryCountRef.current += 1;
        const delay = retryCountRef.current * 2000;
        console.log(`Retrying agreement fetch (${retryCountRef.current}/${maxRetries}) in ${delay}ms...`);
        setTimeout(() => fetchAgreements(), delay);
        return; // Don't clear loading state yet
      }
      const errorMessage = err.response?.data?.detail || t('requestTypeSelector.loadFailed');
      setError(errorMessage);
      if (onError) {
        onError(errorMessage);
      }
    } finally {
      // Only stop loading if we're not auto-retrying
      if (retryCountRef.current === 0 || retryCountRef.current >= maxRetries) {
        setLoading(false);
      }
    }
  };

  const knownTlds = Object.keys(tldFilter.tlds || {}).sort();
  const activeCodes = selectedCodes(tldFilter);

  const toggleTld = (tld) => {
    const selected = tldFilter.selected || [];
    setSelected(selected.includes(tld) ? selected.filter(x => x !== tld) : [...selected, tld]);
  };

  /*
   * The data holder answers a query with the agreements it honours, so that is what the
   * filter narrows: an agreement is kept when its code is among them, and it keeps all of
   * its request types. An agreement whose code we never learned cannot be judged against
   * what was reported, so a filter hides it rather than implying it would be accepted.
   */
  const filteredGroups = groups.map(group => ({
    ...group,
    agreements: group.agreements.filter(agreement =>
      agreement.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (agreement.description && agreement.description.toLowerCase().includes(searchTerm.toLowerCase()))
    ).filter(agreement => !activeCodes || activeCodes.has(agreement.agreementCode))
      .map(agreement => ({
        ...agreement,
        requestTypes: (agreement.requestTypes || [])
          .slice()
          .sort((a, b) => (a.accessLevel || 0) - (b.accessLevel || 0))
      }))
  })).filter(group => group.agreements.length > 0);

  const getSelectedId = () => {
    if (!selectedAgreement) return null;
    const agr = selectedAgreement.agreement || selectedAgreement;
    return typeof agr === 'object' ? agr.id : agr;
  };

  const getSelectedRequestTypeName = () => {
    if (!selectedAgreement) return null;
    return selectedAgreement.requestType?.name || null;
  };

  const isSelected = (agreementId, requestTypeName = null) => {
    const selId = getSelectedId();
    if (selId !== agreementId) return false;
    if (requestTypeName) {
      return getSelectedRequestTypeName() === requestTypeName;
    }
    return true;
  };

  const hasRequestTypes = (agreement) => {
    return agreement.requestTypes && agreement.requestTypes.length > 0;
  };

  const hasMultipleRequestTypes = (agreement) => {
    return agreement.requestTypes && agreement.requestTypes.length > 1;
  };

  const isUsable = (agreement) => agreement?.hasCredentials !== false;

  const selectAgreement = (agreement, requestType = null) => {
    if (!isUsable(agreement)) return;

    if (hasMultipleRequestTypes(agreement) && !requestType) {
      setExpandedAgreementId(
        expandedAgreementId === agreement.id ? null : agreement.id
      );
      return;
    }

    if (hasRequestTypes(agreement) && !requestType && agreement.requestTypes.length === 1) {
      requestType = agreement.requestTypes[0];
    }

    onChange({
      agreement,
      requestType: requestType || null,
    });
    setIsOpen(false);
    setSearchTerm('');
    setExpandedAgreementId(null);
  };

  const selectRequestType = (e, agreement, requestType) => {
    e.stopPropagation();
    onChange({
      agreement,
      requestType,
    });
    setIsOpen(false);
    setSearchTerm('');
    setExpandedAgreementId(null);
  };

  const clearSelection = (e) => {
    e.stopPropagation();
    onChange(null);
    setExpandedAgreementId(null);
  };

  const getDisplayText = () => {
    if (!selectedAgreement) return placeholder;

    const agr = selectedAgreement.agreement || selectedAgreement;
    const name = typeof agr === 'object' ? agr.name : (agreements.find(a => a.id === agr)?.name || agr);
    const rt = selectedAgreement.requestType;

    if (rt) {
      return `${name} — ${getRequestTypeLabel(rt)}`;
    }
    return name;
  };

  // Get label for a request type
  const getRequestTypeLabel = (rt) => {
    const name = rt.name ? rt.name.charAt(0).toUpperCase() + rt.name.slice(1) : t('requestTypeSelector.standard');
    const flags = [];
    if (rt.supportsExigent) flags.push(t('requestTypeSelector.exigent'));
    if (rt.supportsConfidential) flags.push(t('requestTypeSelector.confidential'));
    if (flags.length > 0) return `${name} (${flags.join(' + ')})`;
    return name;
  };

  const contactLine = (contact) => {
    if (!contact) return '';
    return [contact.name, contact.email, contact.phone, contact.formattedAddress]
      .filter(part => part && String(part).trim())
      .join(' · ');
  };

  // Get icon/emoji for request type — show both when both are supported
  const getRequestTypeIcon = (rt) => {
    if (rt.supportsExigent && rt.supportsConfidential) return '⚡🔒';
    if (rt.supportsExigent) return '⚡';
    if (rt.supportsConfidential) return '🔒';
    return '🌐';
  };

  // Get badge color class for request type
  const getRequestTypeBadgeClass = (rt) => {
    if (rt.supportsExigent && rt.supportsConfidential) return 'bg-danger';
    if (rt.supportsExigent) return 'bg-danger';
    if (rt.supportsConfidential) return 'bg-warning text-dark';
    return 'bg-secondary';
  };

  const getAccessLevelLabel = (level) => {
    const labels = {
      0: t('requestTypeSelector.accessPublic'),
      1: t('requestTypeSelector.accessBasic'),
      2: t('requestTypeSelector.accessEnhanced'),
      3: t('requestTypeSelector.accessFull'),
    };
    return labels[level] || t('requestTypeSelector.levelN', { level });
  };

  const getAccessLevelBadgeClass = (level) => {
    const classes = { 0: 'bg-secondary', 1: 'bg-info', 2: 'bg-warning text-dark', 3: 'bg-danger' };
    return classes[level] || 'bg-secondary';
  };

  if (loading) {
    return (
      <div className="form-control d-flex align-items-center" style={{ minHeight: '38px' }}>
        <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
        {t('requestTypeSelector.loading')}
      </div>
    );
  }

  return (
    <div className="request-type-selector position-relative" ref={dropdownRef}>
      {/* Main button/display */}
      <div
        className={`form-control d-flex align-items-center justify-content-between ${disabled ? 'disabled' : ''}`}
        onClick={() => !disabled && setIsOpen(!isOpen)}
        style={{ 
          cursor: disabled ? 'not-allowed' : 'pointer',
          minHeight: '38px',
          backgroundColor: disabled ? '#e9ecef' : '#fff'
        }}
      >
        <span className={!selectedAgreement ? 'text-muted' : ''}>
          {getDisplayText()}
        </span>
        <div className="d-flex align-items-center">
          {selectedAgreement && !disabled && (
            <i 
              className="bi bi-x-circle me-2 text-muted" 
              style={{ cursor: 'pointer' }}
              onClick={clearSelection}
              title={t('requestTypeSelector.clearSelection')}
            ></i>
          )}
          <i className={`bi bi-chevron-${isOpen ? 'up' : 'down'}`}></i>
        </div>
      </div>

      {/* Selected request type badge (shown below selector when selected) */}
      {selectedAgreement?.requestType && (
        <div className="d-flex align-items-center gap-2 mt-1">
          <span className={`badge ${getAccessLevelBadgeClass(selectedAgreement.requestType.accessLevel)}`} style={{ fontSize: '11px' }}>
            {t('requestTypeSelector.levelPrefix')} {selectedAgreement.requestType.accessLevel} — {getAccessLevelLabel(selectedAgreement.requestType.accessLevel)}
          </span>
          <span className={`badge ${getRequestTypeBadgeClass(selectedAgreement.requestType)}`} style={{ fontSize: '11px' }}>
            {getRequestTypeIcon(selectedAgreement.requestType)} {getRequestTypeLabel(selectedAgreement.requestType)}
          </span>
          {selectedAgreement.agreement?.requestorGroupCode && (
            <span className="badge bg-outline-secondary border text-muted" style={{ fontSize: '10px' }}>
              {selectedAgreement.agreement.requestorGroupCode}
            </span>
          )}
        </div>
      )}

      {/* Who to contact at the data holder group about the agreement in use */}
      {contactLine(selectedAgreement?.agreement?.dataHolderContact) && (
        <div className="text-muted mt-1" style={{ fontSize: '11px' }}>
          <i className="bi bi-person-lines-fill me-2"></i>
          {t('requestTypeSelector.contact')} {contactLine(selectedAgreement.agreement.dataHolderContact)}
        </div>
      )}

      {/* Error message */}
      {error && (
        <div className="text-danger small mt-1">
          <i className="bi bi-exclamation-circle me-1"></i>
          {error}
          <button 
            className="btn btn-link btn-sm p-0 ms-2" 
            onClick={fetchAgreements}
          >
            {t('requestTypeSelector.retry')}
          </button>
        </div>
      )}

      {/* Warnings from the agreement server */}
      {warnings.length > 0 && (
        <div className="mt-1">
          {warnings.map((w, idx) => (
            <div key={idx} className="text-warning small">
              <i className="bi bi-exclamation-triangle me-1"></i>
              {w}
            </div>
          ))}
        </div>
      )}

      {/* Dropdown menu */}
      {isOpen && !disabled && agreements.length > 0 && (
        <div 
          className="dropdown-menu show w-100 p-0" 
          style={{ 
            maxHeight: '400px', 
            overflowY: 'auto',
            position: 'absolute',
            zIndex: 1050
          }}
        >
          {/* Search input */}
          <div className="p-2 border-bottom sticky-top bg-white">
            <input
              type="text"
              className="form-control form-control-sm"
              placeholder={t('requestTypeSelector.searchPlaceholder')}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onClick={(e) => e.stopPropagation()}
              autoFocus
            />
          </div>

          {knownTlds.length > 0 && (
            <div className="px-2 py-2 border-bottom bg-light" onClick={(e) => e.stopPropagation()}>
              <div className="d-flex align-items-center justify-content-between mb-1">
                <strong style={{ fontSize: 11 }} title={t('requestTypeSelector.tldFilterHint')}>
                  <i className="bi bi-funnel me-1"></i>
                  {t('requestTypeSelector.tldFilterLabel')}
                </strong>
                {(tldFilter.selected || []).length > 0 && (
                  <button type="button" className="btn btn-link p-0" style={{ fontSize: 11 }}
                          onClick={() => setSelected([])}>
                    {t('requestTypeSelector.tldFilterClear')}
                  </button>
                )}
              </div>
              <div className="d-flex flex-wrap gap-2">
                {knownTlds.map((tld) => {
                  const matched = matchCount(agreements, tldFilter.tlds[tld]);
                  return (
                    <div className="form-check form-check-inline m-0" key={tld}>
                      <input className="form-check-input" type="checkbox" id={`tld-filter-${tld}`}
                             checked={(tldFilter.selected || []).includes(tld)}
                             disabled={matched === 0}
                             onChange={() => toggleTld(tld)} />
                      <label className="form-check-label" htmlFor={`tld-filter-${tld}`} style={{ fontSize: 11 }}>
                        {matched === 0
                          ? t('requestTypeSelector.tldFilterNone', { tld })
                          : t('requestTypeSelector.tldFilterOption', {
                              tld, matched, total: agreements.length,
                            })}
                      </label>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Grouped agreements */}
          {filteredGroups.map((group) => (
            <div key={group.groupId} className="border-bottom">
              {/* Group header */}
              <div className="px-3 py-2 bg-light">
                <strong className="text-primary">
                  <i className="bi bi-folder me-1"></i>
                  {group.groupName}
                </strong>
                <span className="badge bg-secondary ms-2">
                  {group.agreements.length}
                </span>
              </div>

              {/* Agreements in group */}
              {group.agreements.map((agreement) => {
                const isExpanded = expandedAgreementId === agreement.id;
                const multipleTypes = hasMultipleRequestTypes(agreement);
                const agrSelected = isSelected(agreement.id);
                const usable = isUsable(agreement);

                return (
                  <div key={agreement.id}>
                    {/* Agreement row */}
                    <div
                      className={`px-3 py-2 d-flex align-items-start ${usable ? 'hover-bg-light' : 'bg-light'} ${agrSelected && !multipleTypes ? 'bg-primary bg-opacity-10' : ''}`}
                      style={{
                        cursor: usable ? 'pointer' : 'not-allowed',
                        paddingLeft: '2rem',
                        opacity: usable ? 1 : 0.65,
                      }}
                      aria-disabled={!usable}
                      title={usable ? undefined : 'Credentials for this subscription have not been received from the data holder group, so it cannot be queried yet.'}
                      onClick={(e) => { e.stopPropagation(); selectAgreement(agreement); }}
                    >
                      <div className="flex-grow-1">
                        <div className="d-flex align-items-center">
                          {agrSelected && !multipleTypes && (
                            <i className="bi bi-check-lg text-primary me-2"></i>
                          )}
                          {multipleTypes && (
                            <i className={`bi bi-chevron-${isExpanded ? 'down' : 'right'} me-2 text-muted`} style={{ fontSize: '12px' }}></i>
                          )}
                          <span className={`fw-medium ${agrSelected ? 'text-primary' : ''}`}>
                            {agreement.name}
                          </span>
                          {agreement.requestorGroupCode && (
                            <span className="badge bg-light text-muted border ms-2" style={{ fontSize: '10px' }}>
                              {agreement.requestorGroupCode}
                            </span>
                          )}
                          {!usable && (
                            <span className="badge bg-warning text-dark ms-2" style={{ fontSize: '10px' }}>
                              <i className="bi bi-key-fill me-1"></i>
                              No credentials
                            </span>
                          )}
                        </div>
                        {agreement.description && (
                          <small className="text-muted d-block" style={{ marginLeft: multipleTypes ? '1.25rem' : agrSelected ? '1.5rem' : '0' }}>
                            {agreement.description}
                          </small>
                        )}
                        {/* Show request type badges inline if not expanded */}
                        {hasRequestTypes(agreement) && !isExpanded && (
                          <div className="d-flex gap-1 mt-1" style={{ marginLeft: multipleTypes ? '1.25rem' : '0' }}>
                            {agreement.requestTypes.map((rt, idx) => (
                              <React.Fragment key={idx}>
                                <span
                                  className={`badge ${getAccessLevelBadgeClass(rt.accessLevel)}`}
                                  style={{ fontSize: '10px' }}
                                >
                                  L{rt.accessLevel}
                                </span>
                                <span
                                  className={`badge ${getRequestTypeBadgeClass(rt)}`}
                                  style={{ fontSize: '10px' }}
                                >
                                  {getRequestTypeIcon(rt)} {getRequestTypeLabel(rt)}
                                </span>
                              </React.Fragment>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Who to contact at the data holder group about this agreement */}
                    {(isExpanded || !multipleTypes) && contactLine(agreement.dataHolderContact) && (
                      <div
                        className="text-muted"
                        style={{ backgroundColor: '#f8f9fa', fontSize: '11px', padding: '6px 12px 6px 3.5rem' }}
                      >
                        <i className="bi bi-person-lines-fill me-2"></i>
                        {t('requestTypeSelector.contact')} {contactLine(agreement.dataHolderContact)}
                      </div>
                    )}

                    {/* Expanded request types sub-menu */}
                    {isExpanded && hasRequestTypes(agreement) && (
                      <div style={{ backgroundColor: '#f8f9fa' }}>
                        {agreement.requestTypes.map((rt, idx) => {
                          const rtSelected = isSelected(agreement.id, rt.name);
                          return (
                            <div
                              key={idx}
                              className={`d-flex align-items-center gap-2 hover-bg-light ${rtSelected ? 'bg-primary bg-opacity-10' : ''}`}
                              style={{
                                cursor: 'pointer',
                                padding: '8px 12px 8px 3.5rem',
                                borderTop: idx === 0 ? '1px solid #dee2e6' : 'none',
                                borderBottom: '1px solid #eee',
                              }}
                              onClick={(e) => selectRequestType(e, agreement, rt)}
                            >
                              {rtSelected && (
                                <i className="bi bi-check-lg text-primary"></i>
                              )}
                              <span className={`badge ${getAccessLevelBadgeClass(rt.accessLevel)}`} style={{ fontSize: '11px' }}>
                                L{rt.accessLevel}
                              </span>
                              <span className={`badge ${getRequestTypeBadgeClass(rt)}`} style={{ fontSize: '11px' }}>
                                {getRequestTypeIcon(rt)} {getRequestTypeLabel(rt)}
                              </span>
                              {rt.description && (
                                <small className="text-muted">{rt.description}</small>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          ))}

          {/* No results */}
          {filteredGroups.length === 0 && !searchTerm && activeCodes && (
            <div className="px-3 py-3 text-center text-muted" style={{ fontSize: 12 }}>
              {t('requestTypeSelector.noneMatchFilter')}
            </div>
          )}

          {filteredGroups.length === 0 && searchTerm && (
            <div className="p-3 text-center text-muted">
              <i className="bi bi-search me-2"></i>
              {t('requestTypeSelector.noMatch', { term: searchTerm })}
            </div>
          )}
        </div>
      )}

      {/* Inline styles for hover effect */}
      <style>{`
        .request-type-selector .hover-bg-light:hover {
          background-color: #e9ecef !important;
        }
        .request-type-selector .dropdown-menu {
          box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.15);
        }
      `}</style>
    </div>
  );
};

export default RequestTypeSelector;