/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState } from 'react';
import api from '../services/api';
import { useT } from '../i18n';

const WhoisSearch = () => {
  const { t } = useT();
  const [searchQuery, setSearchQuery] = useState('');
  const [whoisData, setWhoisData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Placeholder data generator for testing
  const generatePlaceholderData = (query) => {
    const data = [];
    for (let i = 1; i <= 10; i++) {
      data.push({
        id: i,
        domain: `${query || 'example'}${i}.com`,
        registrar: `Registrar Company ${i}`,
        registrant: `Organization ${i}`,
        createdDate: new Date(2020 + Math.floor(Math.random() * 4), Math.floor(Math.random() * 12), Math.floor(Math.random() * 28)).toISOString().split('T')[0],
        expiryDate: new Date(2024 + Math.floor(Math.random() * 3), Math.floor(Math.random() * 12), Math.floor(Math.random() * 28)).toISOString().split('T')[0],
        status: ['Active', 'Pending', 'Expired'][Math.floor(Math.random() * 3)],
        nameServers: `ns${i}.nameserver.com`,
      });
    }
    return data;
  };

  const handleSearch = async () => {
    if (!searchQuery.trim()) {
      setError(t('whoisSearch.errorEmptyQuery'));
      return;
    }

    setLoading(true);
    setError(null);

    try {
      // TODO: Replace with actual API call
      // const response = await axios.get(`/api/whois/search?query=${searchQuery}`);
      // setWhoisData(response.data);
      
      // Using placeholder data for now
      setTimeout(() => {
        const placeholderData = generatePlaceholderData(searchQuery);
        setWhoisData(placeholderData);
        setLoading(false);
      }, 1000); // Simulate network delay
      
    } catch (err) {
      console.error('Search failed:', err);
      setError(t('whoisSearch.errorFetch'));
      setLoading(false);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const clearSearch = () => {
    setSearchQuery('');
    setWhoisData([]);
    setError(null);
  };

  const getStatusBadgeClass = (status) => {
    switch (status.toLowerCase()) {
      case 'active':
        return 'bg-success';
      case 'pending':
        return 'bg-warning';
      case 'expired':
        return 'bg-danger';
      default:
        return 'bg-secondary';
    }
  };

  return (
    <div>
      {/* Search Header */}
      <div className="mb-4">
        <div className="alert alert-secondary" role="alert">
          <i className="bi bi-terminal me-2"></i>
          <strong>{t('whoisSearch.legacyBadge')}</strong> - {t('whoisSearch.legacyDesc')}
        </div>

        {/* Search Controls */}
        <div className="row g-3">
          <div className="col-lg-8">
            <input
              type="text"
              placeholder={t('whoisSearch.searchPlaceholder')}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyPress={handleKeyPress}
              className="form-control form-control-lg"
              disabled={loading}
            />
          </div>
          <div className="col-lg-4">
            <div className="d-flex gap-2">
              <button 
                onClick={handleSearch} 
                className="btn btn-primary btn-lg flex-grow-1"
                disabled={loading}
              >
                {loading ? (
                  <>
                    <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                    {t('whoisSearch.searching')}
                  </>
                ) : (
                  <>
                    <i className="bi bi-search me-2"></i>
                    {t('whoisSearch.search')}
                  </>
                )}
              </button>
              {whoisData.length > 0 && (
                <button
                  onClick={clearSearch}
                  className="btn btn-outline-secondary btn-lg"
                >
                  <i className="bi bi-x-circle me-2"></i>
                  {t('whoisSearch.clear')}
                </button>
              )}
            </div>
          </div>
        </div>

        {/* Error Message */}
        {error && (
          <div className="alert alert-danger alert-dismissible fade show mt-3" role="alert">
            <i className="bi bi-exclamation-triangle-fill me-2"></i>
            {error}
            <button
              type="button"
              className="btn-close"
              onClick={() => setError(null)}
              aria-label={t('whoisSearch.close')}
            ></button>
          </div>
        )}
      </div>

      {/* Results Table */}
      <div className="card shadow-sm">
        <div className="card-header bg-white">
          <h5 className="mb-0 fw-semibold">
            <i className="bi bi-table me-2 text-primary"></i>
            {t('whoisSearch.resultsTitle')}
            {whoisData.length > 0 && (
              <span className="badge bg-primary ms-2">{t('whoisSearch.recordCount', { count: whoisData.length })}</span>
            )}
          </h5>
        </div>
        <div className="card-body p-0">
          <div className="table-responsive">
            <table className="table table-hover table-striped mb-0">
              <thead className="table-light">
                <tr>
                  <th scope="col">{t('whoisSearch.colDomain')}</th>
                  <th scope="col">{t('whoisSearch.colRegistrar')}</th>
                  <th scope="col">{t('whoisSearch.colRegistrant')}</th>
                  <th scope="col">{t('whoisSearch.colCreatedDate')}</th>
                  <th scope="col">{t('whoisSearch.colExpiryDate')}</th>
                  <th scope="col">{t('whoisSearch.colStatus')}</th>
                  <th scope="col">{t('whoisSearch.colNameServers')}</th>
                </tr>
              </thead>
              <tbody>
                {whoisData.length === 0 ? (
                  <tr>
                    <td colSpan="7" className="text-center py-5">
                      <div className="text-muted">
                        <i className="bi bi-inbox display-1 d-block mb-3 text-secondary"></i>
                        <h5 className="fw-normal">{t('whoisSearch.noDataTitle')}</h5>
                        <p className="mb-0">{t('whoisSearch.noDataDesc')}</p>
                      </div>
                    </td>
                  </tr>
                ) : (
                  whoisData.map((row) => (
                    <tr key={row.id}>
                      <td className="fw-semibold text-primary">{row.domain}</td>
                      <td>{row.registrar}</td>
                      <td>{row.registrant}</td>
                      <td>
                        <small className="text-muted">
                          <i className="bi bi-calendar-event me-1"></i>
                          {row.createdDate}
                        </small>
                      </td>
                      <td>
                        <small className="text-muted">
                          <i className="bi bi-calendar-x me-1"></i>
                          {row.expiryDate}
                        </small>
                      </td>
                      <td>
                        <span className={`badge ${getStatusBadgeClass(row.status)}`}>
                          {row.status}
                        </span>
                      </td>
                      <td>
                        <small className="font-monospace text-muted">
                          {row.nameServers}
                        </small>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
        {whoisData.length > 0 && (
          <div className="card-footer bg-light text-muted text-end">
            <small>
              <i className="bi bi-clock me-1"></i>
              {t('whoisSearch.lastSearched', { time: new Date().toLocaleString() })}
            </small>
          </div>
        )}
      </div>
    </div>
  );
};

export default WhoisSearch;