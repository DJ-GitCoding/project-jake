/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { useT } from '../i18n';

const TestDataHolder = () => {
  const { t } = useT();
  const { isAuthenticated } = useAuth();

  // State
  const [queryType, setQueryType] = useState('domain');
  const [queryValue, setQueryValue] = useState('');
  const [selectedAgreements, setSelectedAgreements] = useState([]);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [availableDomains, setAvailableDomains] = useState([]);
  const [pendingRequestId, setPendingRequestId] = useState(null);
  const [polling, setPolling] = useState(false);
  
  // Available agreements (would come from API in real app)
  const agreements = [
    { id: 1, name: t('testDataHolder.agreements.basic'), level: 1 },
    { id: 2, name: t('testDataHolder.agreements.standard'), level: 2 },
    { id: 3, name: t('testDataHolder.agreements.full'), level: 3 },
  ];

  // Fetch available test domains
  useEffect(() => {
    fetchAvailableDomains();
  }, []);

  // Poll for pending request status
  useEffect(() => {
    let interval;
    if (pendingRequestId && polling) {
      interval = setInterval(() => {
        checkRequestStatus(pendingRequestId);
      }, 5000); // Poll every 5 seconds
    }
    return () => clearInterval(interval);
  }, [pendingRequestId, polling]);

  const fetchAvailableDomains = async () => {
    try {
      const response = await fetch(`/dh/api/rdap/domains`);
      if (response.ok) {
        const domains = await response.json();
        setAvailableDomains(domains);
      }
    } catch (err) {
      console.error('Failed to fetch available domains:', err);
    }
  };

  const handleQuery = async (e) => {
    e.preventDefault();
    
    if (!isAuthenticated) {
      setError(t('testDataHolder.errors.loginRequired'));
      return;
    }
    
    setLoading(true);
    setError(null);
    setResult(null);
    setPendingRequestId(null);
    setPolling(false);
    
    try {
      const agreementParam = selectedAgreements.length > 0 
        ? `?agreements=${selectedAgreements.join(',')}` 
        : '';
      
      let endpoint;
      switch (queryType) {
        case 'domain':
          endpoint = `/domain/${queryValue}${agreementParam}`;
          break;
        case 'ip':
          endpoint = `/ip/${queryValue}${agreementParam}`;
          break;
        case 'asn':
          endpoint = `/autnum/${queryValue}${agreementParam}`;
          break;
        default:
          throw new Error(t('testDataHolder.errors.invalidQueryType'));
      }
      
      const response = await fetch(`/dh${endpoint}`, {
        headers: {
          'Accept': 'application/rdap+json, application/json',
        },
      });
      
      const data = await response.json();
      
      if (data.data?.status === 'pending') {
        // Request requires manual verification
        setPendingRequestId(data.data.requestId);
        setPolling(true);
        setResult({
          type: 'pending',
          message: data.data.message,
          requestId: data.data.requestId,
          pollUrl: data.data.pollUrl,
          expiresAt: data.data.expiresAt,
        });
      } else if (data.success) {
        setResult({
          type: 'success',
          accessLevel: data.accessLevel,
          data: data.data,
          source: data.source,
          timestamp: data.timestamp,
        });
      } else {
        setError(data.errorMessage || t('testDataHolder.errors.queryFailed'));
      }
    } catch (err) {
      setError(err.message || t('testDataHolder.errors.executeFailed'));
    } finally {
      setLoading(false);
    }
  };

  const checkRequestStatus = async (requestId) => {
    try {
      const response = await fetch(`/dh/api/rdap/status/${requestId}`, {
        headers: {
          'Accept': 'application/json',
        },
      });
      
      const data = await response.json();
      
      if (data.data?.status === 'pending') {
        // Still pending
        return;
      }
      
      setPolling(false);
      
      if (data.success && data.accessLevel !== undefined) {
        // Approved!
        setResult({
          type: 'success',
          accessLevel: data.accessLevel,
          data: data.data,
          source: data.source,
          timestamp: data.timestamp,
        });
        setPendingRequestId(null);
      } else if (!data.success) {
        // Denied or error
        setError(data.errorMessage || t('testDataHolder.errors.requestDenied'));
        setPendingRequestId(null);
      }
    } catch (err) {
      console.error('Failed to check status:', err);
    }
  };

  const handleAgreementToggle = (agreementId) => {
    setSelectedAgreements(prev => 
      prev.includes(agreementId)
        ? prev.filter(id => id !== agreementId)
        : [...prev, agreementId]
    );
  };

  const handleDomainSelect = (domain) => {
    setQueryValue(domain);
    setQueryType('domain');
  };

  return (
    <div className="test-dataholder-container">
      <h1>{t('testDataHolder.title')}</h1>
      <p className="subtitle">
        {t('testDataHolder.subtitle')}
      </p>

      {!isAuthenticated && (
        <div className="alert alert-warning">
          {t('testDataHolder.loginWarning')}
        </div>
      )}

      <div className="test-dataholder-content">
        {/* Query Form */}
        <div className="query-section">
          <h2>{t('testDataHolder.queryHeading')}</h2>

          <form onSubmit={handleQuery}>
            {/* Query Type */}
            <div className="form-group">
              <label>{t('testDataHolder.queryType')}</label>
              <div className="radio-group">
                {['domain', 'ip', 'asn'].map(type => (
                  <label key={type} className="radio-label">
                    <input
                      type="radio"
                      name="queryType"
                      value={type}
                      checked={queryType === type}
                      onChange={(e) => setQueryType(e.target.value)}
                    />
                    {type.toUpperCase()}
                  </label>
                ))}
              </div>
            </div>
            
            {/* Query Value */}
            <div className="form-group">
              <label htmlFor="queryValue">
                {queryType === 'domain' && t('testDataHolder.labelDomain')}
                {queryType === 'ip' && t('testDataHolder.labelIp')}
                {queryType === 'asn' && t('testDataHolder.labelAsn')}
              </label>
              <input
                type="text"
                id="queryValue"
                value={queryValue}
                onChange={(e) => setQueryValue(e.target.value)}
                placeholder={
                  queryType === 'domain' ? t('testDataHolder.placeholderDomain') :
                  queryType === 'ip' ? t('testDataHolder.placeholderIp') :
                  t('testDataHolder.placeholderAsn')
                }
                required
              />
            </div>
            
            {/* Agreement Selection */}
            <div className="form-group">
              <label>{t('testDataHolder.selectAgreements')}</label>
              <div className="checkbox-group">
                {agreements.map(agreement => (
                  <label key={agreement.id} className="checkbox-label">
                    <input
                      type="checkbox"
                      checked={selectedAgreements.includes(agreement.id)}
                      onChange={() => handleAgreementToggle(agreement.id)}
                    />
                    <span className="agreement-info">
                      <span className="agreement-name">{agreement.name}</span>
                      <span className="agreement-level">{t('testDataHolder.level', { n: agreement.level })}</span>
                    </span>
                  </label>
                ))}
              </div>
              {selectedAgreements.length === 0 && (
                <p className="help-text">
                  {t('testDataHolder.noAgreementsHint')}
                </p>
              )}
            </div>
            
            <button 
              type="submit" 
              className="btn btn-primary"
              disabled={loading || !queryValue}
            >
              {loading ? 'Querying...' : 'Execute Query'}
            </button>
          </form>
        </div>
        
        {/* Available Domains */}
        <div className="domains-section">
          <h3>Available Test Domains</h3>
          <div className="domains-list">
            {availableDomains.map(domain => (
              <button
                key={domain}
                className="domain-chip"
                onClick={() => handleDomainSelect(domain)}
              >
                {domain}
              </button>
            ))}
          </div>
        </div>
        
        {/* Error Display */}
        {error && (
          <div className="alert alert-error">
            <strong>Error:</strong> {error}
          </div>
        )}
        
        {/* Pending Request */}
        {result?.type === 'pending' && (
          <div className="pending-section">
            <div className="alert alert-info">
              <h3>⏳ Manual Verification Required</h3>
              <p>{result.message}</p>
              <p><strong>Request ID:</strong> {result.requestId}</p>
              <p><strong>Expires:</strong> {new Date(result.expiresAt).toLocaleString()}</p>
              {polling && <p className="polling-indicator">Polling for status...</p>}
            </div>
          </div>
        )}
        
        {/* Result Display */}
        {result?.type === 'success' && (
          <div className="result-section">
            <h2>Query Result</h2>
            <div className="result-meta">
              <span className={`access-level level-${result.accessLevel}`}>
                Access Level: {result.accessLevel}
              </span>
              <span className="source">Source: {result.source}</span>
              <span className="timestamp">{result.timestamp}</span>
            </div>
            <pre className="result-data">
              {JSON.stringify(result.data, null, 2)}
            </pre>
          </div>
        )}
      </div>
      
      <style jsx>{`
        .test-dataholder-container {
          max-width: 1200px;
          margin: 0 auto;
          padding: 2rem;
        }
        
        .subtitle {
          color: #666;
          margin-bottom: 2rem;
        }
        
        .alert {
          padding: 1rem;
          border-radius: 8px;
          margin-bottom: 1rem;
        }
        
        .alert-warning {
          background: #fff3cd;
          border: 1px solid #ffc107;
          color: #856404;
        }
        
        .alert-error {
          background: #f8d7da;
          border: 1px solid #f5c6cb;
          color: #721c24;
        }
        
        .alert-info {
          background: #d1ecf1;
          border: 1px solid #bee5eb;
          color: #0c5460;
        }
        
        .test-dataholder-content {
          display: grid;
          gap: 2rem;
        }
        
        .query-section, .result-section, .domains-section {
          background: #fff;
          padding: 1.5rem;
          border-radius: 12px;
          box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        
        .form-group {
          margin-bottom: 1.5rem;
        }
        
        .form-group label {
          display: block;
          font-weight: 600;
          margin-bottom: 0.5rem;
        }
        
        .form-group input[type="text"] {
          width: 100%;
          padding: 0.75rem;
          border: 1px solid #ddd;
          border-radius: 6px;
          font-size: 1rem;
        }
        
        .radio-group, .checkbox-group {
          display: flex;
          flex-wrap: wrap;
          gap: 1rem;
        }
        
        .radio-label, .checkbox-label {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          cursor: pointer;
        }
        
        .checkbox-label {
          background: #f5f5f5;
          padding: 0.75rem 1rem;
          border-radius: 8px;
          border: 2px solid transparent;
          transition: all 0.2s;
        }
        
        .checkbox-label:has(input:checked) {
          background: #e3f2fd;
          border-color: #2196f3;
        }
        
        .agreement-info {
          display: flex;
          flex-direction: column;
        }
        
        .agreement-name {
          font-weight: 500;
        }
        
        .agreement-level {
          font-size: 0.85rem;
          color: #666;
        }
        
        .help-text {
          font-size: 0.9rem;
          color: #666;
          margin-top: 0.5rem;
        }
        
        .btn {
          padding: 0.75rem 1.5rem;
          border: none;
          border-radius: 6px;
          font-size: 1rem;
          cursor: pointer;
          transition: all 0.2s;
        }
        
        .btn-primary {
          background: #2196f3;
          color: white;
        }
        
        .btn-primary:hover:not(:disabled) {
          background: #1976d2;
        }
        
        .btn:disabled {
          opacity: 0.6;
          cursor: not-allowed;
        }
        
        .domains-list {
          display: flex;
          flex-wrap: wrap;
          gap: 0.5rem;
        }
        
        .domain-chip {
          background: #e8f5e9;
          color: #2e7d32;
          border: 1px solid #a5d6a7;
          padding: 0.5rem 1rem;
          border-radius: 20px;
          cursor: pointer;
          font-size: 0.9rem;
          transition: all 0.2s;
        }
        
        .domain-chip:hover {
          background: #c8e6c9;
        }
        
        .result-meta {
          display: flex;
          gap: 1rem;
          flex-wrap: wrap;
          margin-bottom: 1rem;
        }
        
        .access-level {
          padding: 0.25rem 0.75rem;
          border-radius: 4px;
          font-weight: 600;
        }
        
        .level-0 { background: #ffebee; color: #c62828; }
        .level-1 { background: #fff3e0; color: #ef6c00; }
        .level-2 { background: #e8f5e9; color: #2e7d32; }
        .level-3 { background: #e3f2fd; color: #1565c0; }
        
        .source, .timestamp {
          color: #666;
          font-size: 0.9rem;
        }
        
        .result-data {
          background: #f5f5f5;
          padding: 1rem;
          border-radius: 8px;
          overflow-x: auto;
          font-family: 'Monaco', 'Menlo', monospace;
          font-size: 0.9rem;
          line-height: 1.5;
        }
        
        .pending-section {
          margin-top: 1rem;
        }
        
        .polling-indicator {
          font-style: italic;
          animation: pulse 1.5s infinite;
        }
        
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.5; }
        }
      `}</style>
    </div>
  );
};

export default TestDataHolder;
