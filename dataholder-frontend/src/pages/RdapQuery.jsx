/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import { queryDomain, queryIp, queryAsn, getAvailableDomains } from '../services/api';
import { useAuth } from '../contexts/AuthContext';
import Loading from '../components/Loading';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const RdapQuery = () => {
  const { accessToken } = useAuth();
  const { t } = useT();
  const [queryType, setQueryType] = useState('domain');
  const [queryValue, setQueryValue] = useState('');
  const [result, setResult] = useState(null);
  const [failed, setFailed] = useState(false);
  const [loading, setLoading] = useState(false);
  const [domains, setDomains] = useState([]);

  useEffect(() => {
    loadDomains();
  }, []);

  const loadDomains = async () => {
    try {
      const res = await getAvailableDomains();
      setDomains(res.data || []);
    } catch (error) {
      console.error('Failed to load domains:', error);
    }
  };

  const handleQuery = async (e) => {
    e.preventDefault();

    if (!queryValue.trim()) {
      toast.error(t('rdapQuery.queryValueMissing'));
      return;
    }

    setLoading(true);
    setResult(null);
    setFailed(false);

    try {
      /*
       * Always query as the signed-in data holder admin so the test returns the
       * full, unredacted record — the point is to confirm the data can be pulled.
       */
      const authHeader = accessToken ? `Bearer ${accessToken}` : null;
      let response;

      switch (queryType) {
        case 'domain':
          response = await queryDomain(queryValue.trim(), [], authHeader);
          break;
        case 'ip':
          response = await queryIp(queryValue.trim(), [], authHeader);
          break;
        case 'asn':
          response = await queryAsn(queryValue.trim(), [], authHeader);
          break;
        default:
          throw new Error(t('rdapQuery.invalidQueryType'));
      }

      setResult(response.data);
      toast.success(t('rdapQuery.querySuccess'));
    } catch (error) {
      console.error('Query failed:', error);
      setFailed(true);
      setResult(error.response?.data || {
        errorMessage: error.message || t('rdapQuery.queryFailedFallback')
      });
      toast.error(t('rdapQuery.queryFailedToast'));
    } finally {
      setLoading(false);
    }
  };

  const getQueryIcon = () => {
    switch (queryType) {
      case 'domain': return <i className="fa-solid fa-globe"></i>;
      case 'ip': return <i className="fa-solid fa-server"></i>;
      case 'asn': return <i className="fa-solid fa-network-wired"></i>;
      default: return <i className="fa-solid fa-magnifying-glass"></i>;
    }
  };

  return (
    <div>
      <div className="mb-4">
        <h2 className="h3 fw-bold mb-1">{t('rdapQuery.heading')}</h2>
        <p className="text-muted mb-0">{t('rdapQuery.subtitle')}</p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: '24px' }}>
        {/* Query Form */}
        <div className="card">
          <div className="card-header">
            <h3>
              <i className="fa-solid fa-magnifying-glass"></i>
              {t('rdapQuery.queryBuilder')}
            </h3>
          </div>
          <form onSubmit={handleQuery}>
            <div className="card-body">
              {/* Query Type Tabs */}
              <ul className="nav nav-pills mb-3">
                {[
                  { value: 'domain', label: t('rdapQuery.tabs.domain') },
                  { value: 'ip', label: t('rdapQuery.tabs.ip') },
                  { value: 'asn', label: t('rdapQuery.tabs.asn') },
                ].map((type) => (
                  <li className="nav-item" key={type.value}>
                    <button
                      type="button"
                      className={`nav-link ${queryType === type.value ? 'active' : ''}`}
                      onClick={() => setQueryType(type.value)}
                    >
                      {type.label}
                    </button>
                  </li>
                ))}
              </ul>

              {/* Query Value */}
              <div className="mb-3">
                <label className="form-label">
                  {queryType === 'domain' ? t('rdapQuery.fieldLabel.domain') :
                   queryType === 'ip' ? t('rdapQuery.fieldLabel.ip') : t('rdapQuery.fieldLabel.asn')}
                </label>
                <input
                  type="text"
                  className="form-control"
                  value={queryValue}
                  onChange={(e) => setQueryValue(e.target.value)}
                  placeholder={
                    queryType === 'domain' ? t('rdapQuery.placeholder.domain') :
                    queryType === 'ip' ? t('rdapQuery.placeholder.ip') : t('rdapQuery.placeholder.asn')
                  }
                />
              </div>

              {/* Quick Select for Domains */}
              {queryType === 'domain' && domains.length > 0 && (
                <div className="mb-3">
                  <label className="form-label">{t('rdapQuery.quickSelect')}</label>
                  <div className="d-flex flex-wrap gap-1">
                    {domains.slice(0, 8).map((domain) => (
                      <button
                        key={domain}
                        type="button"
                        className="badge bg-light text-dark border"
                        style={{ cursor: 'pointer' }}
                        onClick={() => setQueryValue(domain)}
                      >
                        {domain}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              <p className="text-muted small mb-0">
                <i className="fa-solid fa-circle-info" style={{ marginRight: '6px' }}></i>
                {t('rdapQuery.adminNote')}
              </p>
            </div>
            <div className="card-footer">
              <button
                type="submit"
                className="btn btn-primary"
                disabled={loading || !queryValue.trim()}
              >
                {getQueryIcon()}
                {loading ? t('rdapQuery.querying') : t('rdapQuery.executeQuery')}
              </button>
            </div>
          </form>
        </div>

        {/* Results */}
        <div className="card">
          <div className="card-header">
            <h3>{t('rdapQuery.results.title')}</h3>
            {result && (
              <span className={`badge ${failed ? 'bg-danger-subtle text-danger' : 'bg-success-subtle text-success'}`}>
                {failed ? t('rdapQuery.results.failed') : t('common.success')}
              </span>
            )}
          </div>
          <div className="card-body">
            {loading ? (
              <Loading message={t('rdapQuery.results.executing')} />
            ) : result ? (
              <div>
                {result.errorMessage && (
                  <div className="text-danger mb-3">{result.errorMessage}</div>
                )}
                <pre className="bg-light border rounded p-3 mb-0" style={{
                  overflow: 'auto',
                  maxHeight: '600px',
                  fontSize: '12px',
                }}>
                  {JSON.stringify(result.data ?? result, null, 2)}
                </pre>
              </div>
            ) : (
              <div className="text-muted text-center" style={{ padding: '48px' }}>
                <i className="fa-solid fa-magnifying-glass" style={{ fontSize: '48px', opacity: 0.3, margin: '0 auto 16px', display: 'block' }}></i>
                <p>{t('rdapQuery.results.emptyState')}</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default RdapQuery;
