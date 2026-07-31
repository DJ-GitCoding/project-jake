/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  getRdapDomains, createRdapDomain, updateRdapDomain, deleteRdapDomain, bulkDeleteRdapDomains,
  getRdapIps, createRdapIp, updateRdapIp, deleteRdapIp, bulkDeleteRdapIps,
  getRdapAsns, createRdapAsn, updateRdapAsn, deleteRdapAsn, bulkDeleteRdapAsns,
  getRdapEntityById,
  getRdapStats, exportRdapData,
  getMappedDataStatus, getMappedDomains
} from '../services/api';
import Loading from '../components/Loading';
import Modal from '../components/Modal';
import toast from 'react-hot-toast';
import RdapEntityEditModal from '../components/RdapEntityEditModal';
import { CsvImportModal, JsonImportModal } from '../components/RdapImportModal';
import { useT } from '../i18n';

const formatMappedDate = (val) => {
  if (!val) return '—';
  try {
    const s = String(val);
    if (s.includes('T')) return s.split('T')[0];
    if (s.length > 10) return s.substring(0, 10);
    return s;
  } catch (e) { return String(val); }
};

const RdapData = () => {
  // Core state
  const [activeTab, setActiveTab] = useState('domains');
  const [entities, setEntities] = useState([]);
  const [stats, setStats] = useState(null);

  // Pagination
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [pageSize, setPageSize] = useState(50);

  // Search
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');

  // Selection
  const [selectedIds, setSelectedIds] = useState([]);

  // Loading states
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  // Modal states
  const [showEditModal, setShowEditModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showCsvImportModal, setShowCsvImportModal] = useState(false);
  const [showJsonImportModal, setShowJsonImportModal] = useState(false);

  // Edit targets
  const [editingItem, setEditingItem] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);

  // Mapped data state
  const [mappedStatus, setMappedStatus] = useState(null); // { active, mappings: [...] }
  const [selectedMappingId, setSelectedMappingId] = useState(null);
  const [mappedDomains, setMappedDomains] = useState([]);
  const [mappedPage, setMappedPage] = useState(0);
  const [mappedPageSize, setMappedPageSize] = useState(50);
  const [mappedTotalPages, setMappedTotalPages] = useState(0);
  const [mappedTotalRows, setMappedTotalRows] = useState(0);
  const [mappedSearch, setMappedSearch] = useState('');
  const [mappedSearchInput, setMappedSearchInput] = useState('');
  const [mappedLoading, setMappedLoading] = useState(false);
  const [mappedCellModal, setMappedCellModal] = useState(null);

  // Schema (not used but kept for compatibility)
  const [schema, setSchema] = useState([]);

  const { t } = useT();

  // Static schema definitions for each entity type
  const getSchemaForType = (type) => {
    const commonFields = [
      { name: 'handle', type: 'string', description: t('rdapData.schema.fields.handle'), required: true, example: 'DOM-123' },
      { name: 'status', type: 'string[]', description: t('rdapData.schema.fields.status'), required: false, example: 'active,ok' },
      { name: 'port43', type: 'string', description: t('rdapData.schema.fields.port43'), required: false, example: 'whois.example.com' },
    ];

    switch (type) {
      case 'domains':
        return [
          { name: 'ldhName', type: 'string', description: t('rdapData.schema.fields.ldhName'), required: true, example: 'example.com' },
          ...commonFields,
          { name: 'unicodeName', type: 'string', description: t('rdapData.schema.fields.unicodeName'), required: false, example: '例え.jp' },
          { name: 'secureDnsDelegationSigned', type: 'boolean', description: t('rdapData.schema.fields.secureDnsDelegationSigned'), required: false, example: 'true' },
          { name: 'secureDnsZoneSigned', type: 'boolean', description: t('rdapData.schema.fields.secureDnsZoneSigned'), required: false, example: 'true' },
        ];
      case 'ips':
        return [
          ...commonFields,
          { name: 'startAddress', type: 'string', description: t('rdapData.schema.fields.startAddress'), required: true, example: '192.0.2.0' },
          { name: 'endAddress', type: 'string', description: t('rdapData.schema.fields.endAddress'), required: false, example: '192.0.2.255' },
          { name: 'ipVersion', type: 'string', description: t('rdapData.schema.fields.ipVersion'), required: false, example: 'v4' },
          { name: 'networkName', type: 'string', description: t('rdapData.schema.fields.networkName'), required: false, example: 'EXAMPLE-NET' },
          { name: 'networkType', type: 'string', description: t('rdapData.schema.fields.networkType'), required: false, example: 'DIRECT ALLOCATION' },
          { name: 'country', type: 'string', description: t('rdapData.schema.fields.country'), required: false, example: 'US' },
          { name: 'parentHandle', type: 'string', description: t('rdapData.schema.fields.parentHandle'), required: false, example: 'NET-192-0-0-0-1' },
        ];
      case 'asns':
        return [
          ...commonFields,
          { name: 'startAutnum', type: 'integer', description: t('rdapData.schema.fields.startAutnum'), required: true, example: '64496' },
          { name: 'endAutnum', type: 'integer', description: t('rdapData.schema.fields.endAutnum'), required: false, example: '64496' },
          { name: 'autnumName', type: 'string', description: t('rdapData.schema.fields.autnumName'), required: false, example: 'EXAMPLE-AS' },
          { name: 'autnumType', type: 'string', description: t('rdapData.schema.fields.autnumType'), required: false, example: 'DIRECT ALLOCATION' },
          { name: 'country', type: 'string', description: t('rdapData.schema.fields.country'), required: false, example: 'US' },
        ];
      default:
        return commonFields;
    }
  };

  // Debounce search
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  // Load data when tab, page, page size, or search changes
  useEffect(() => {
    loadData();
  }, [activeTab, page, pageSize, debouncedSearch]);

  // Load stats and mapped status on mount
  useEffect(() => {
    loadStats();
    loadMappedStatus();
  }, []);

  // Load mapped domains when mapped tab is active
  useEffect(() => {
    if (activeTab === 'mapped' && selectedMappingId) {
      loadMappedDomains();
    }
  }, [activeTab, mappedPage, mappedPageSize, mappedSearch, selectedMappingId]);

  // API calls
  const loadMappedStatus = async () => {
    try {
      const res = await getMappedDataStatus();
      setMappedStatus(res.data);
      // Auto-select first mapping if none selected yet
      if (res.data?.active && res.data.mappings?.length > 0 && !selectedMappingId) {
        setSelectedMappingId(res.data.mappings[0].mappingId);
      }
    } catch (e) {
      console.error('Failed to load mapped status:', e);
    }
  };

  const loadMappedDomains = async () => {
    setMappedLoading(true);
    try {
      const res = await getMappedDomains(mappedPage, mappedPageSize, mappedSearch, selectedMappingId);
      if (res.data.success) {
        setMappedDomains(res.data.domains || []);
        setMappedTotalPages(res.data.totalPages || 0);
        setMappedTotalRows(res.data.totalRows || 0);
      } else {
        toast.error(t('rdapData.mapped.loadFailedWithReason', { reason: res.data.error || t('common.unknown') }));
        setMappedDomains([]);
      }
    } catch (e) {
      toast.error(t('rdapData.mapped.loadFailed'));
      setMappedDomains([]);
    } finally {
      setMappedLoading(false);
    }
  };

  const loadStats = async () => {
    try {
      const response = await getRdapStats();
      setStats(response.data);
    } catch (error) {
      console.error('Failed to load stats:', error);
    }
  };

  const loadData = async () => {
    setLoading(true);
    try {
      let response;
      switch (activeTab) {
        case 'domains':
          response = await getRdapDomains(page, pageSize, 'ldhName', 'asc', debouncedSearch);
          break;
        case 'ips':
          response = await getRdapIps(page, pageSize, 'handle', 'asc', debouncedSearch);
          break;
        case 'asns':
          response = await getRdapAsns(page, pageSize, 'handle', 'asc', debouncedSearch);
          break;
        default:
          return;
      }
      setEntities(response.data.content || []);
      setTotalPages(response.data.totalPages || 0);
      setTotalElements(response.data.totalElements || 0);
      setSelectedIds([]);
    } catch (error) {
      console.error('Failed to load data:', error);
      toast.error(t('common.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  // Import success handler
  const handleImportSuccess = () => {
    loadData();
    loadStats();
  };

  // Tab handling
  const handleTabChange = (tab) => {
    setActiveTab(tab);
    setPage(0);
    setSearch('');
    setSelectedIds([]);
  };

  // Selection handling
  const handleSelectAll = () => {
    if (selectedIds.length === entities.length && entities.length > 0) {
      setSelectedIds([]);
    } else {
      setSelectedIds(entities.map(item => item.id));
    }
  };

  const handleSelectOne = (id) => {
    if (selectedIds.includes(id)) {
      setSelectedIds(selectedIds.filter(i => i !== id));
    } else {
      setSelectedIds([...selectedIds, id]);
    }
  };

  // Create modal
  const openCreateModal = () => {
    setEditingItem(null);
    setShowEditModal(true);
  };

  // Edit modal
  const openEditModal = async (item) => {
    try {
      const response = await getRdapEntityById(item.id);
      const fullData = response.data;

      setEditingItem({
        ...fullData.entity,
        isTestData: fullData.entity.testDataFlag?.isTestData || false,
        events: fullData.events || [],
        links: fullData.links || [],
        nameservers: fullData.nameservers || [],
        remarks: fullData.remarks || [],
        secureDnsRecords: fullData.secureDns || [],
        childEntities: fullData.entity.children || [],
      });
      setShowEditModal(true);
    } catch (error) {
      console.error('Failed to load entity details:', error);
      // Fallback: open modal with basic item data
      setEditingItem({
        ...item,
        isTestData: item.testDataFlag?.isTestData || false,
        events: [],
        links: [],
        nameservers: [],
        remarks: [],
        secureDnsRecords: [],
        childEntities: [],
      });
      setShowEditModal(true);
    }
  };

  // Save entity
  const handleSave = async (payload) => {
    setSubmitting(true);
    try {
      let response;

      if (editingItem) {
        switch (activeTab) {
          case 'domains':
            response = await updateRdapDomain(editingItem.id, payload);
            break;
          case 'ips':
            response = await updateRdapIp(editingItem.id, payload);
            break;
          case 'asns':
            response = await updateRdapAsn(editingItem.id, payload);
            break;
        }
      } else {
        switch (activeTab) {
          case 'domains':
            response = await createRdapDomain(payload);
            break;
          case 'ips':
            response = await createRdapIp(payload);
            break;
          case 'asns':
            response = await createRdapAsn(payload);
            break;
        }
      }

      if (response.data.success) {
        toast.success(editingItem ? t('rdapData.toasts.updatedSuccessfully') : t('rdapData.toasts.createdSuccessfully'));
        setShowEditModal(false);
        loadData();
        loadStats();
      } else {
        toast.error(response.data.error || t('rdapData.toasts.operationFailed'));
      }
    } catch (error) {
      console.error('Save error:', error);
      toast.error(error.response?.data?.error || t('rdapData.toasts.operationFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  // Delete handling
  const openDeleteModal = (item) => {
    setDeleteTarget(item);
    setShowDeleteModal(true);
  };

  const handleDelete = async () => {
    setSubmitting(true);
    try {
      let response;
      switch (activeTab) {
        case 'domains':
          response = await deleteRdapDomain(deleteTarget.id);
          break;
        case 'ips':
          response = await deleteRdapIp(deleteTarget.id);
          break;
        case 'asns':
          response = await deleteRdapAsn(deleteTarget.id);
          break;
      }

      if (response.data.success) {
        toast.success(t('rdapData.toasts.deletedSuccessfully'));
        setShowDeleteModal(false);
        setDeleteTarget(null);
        loadData();
        loadStats();
      } else {
        toast.error(response.data.error || t('rdapData.toasts.deleteFailed'));
      }
    } catch (error) {
      console.error('Delete error:', error);
      toast.error(error.response?.data?.error || t('rdapData.toasts.deleteFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  // Bulk delete
  const handleBulkDelete = async () => {
    if (selectedIds.length === 0) return;
    if (!window.confirm(t('rdapData.bulk.confirmBulkDelete', { count: selectedIds.length }))) return;

    setSubmitting(true);
    try {
      let response;
      switch (activeTab) {
        case 'domains':
          response = await bulkDeleteRdapDomains(selectedIds);
          break;
        case 'ips':
          response = await bulkDeleteRdapIps(selectedIds);
          break;
        case 'asns':
          response = await bulkDeleteRdapAsns(selectedIds);
          break;
      }

      if (response.data.success) {
        toast.success(t('rdapData.bulk.bulkDeleteSuccess', { count: response.data.deletedCount }));
        setSelectedIds([]);
        loadData();
        loadStats();
      } else {
        toast.error(response.data.error || t('rdapData.bulk.bulkDeleteFailed'));
      }
    } catch (error) {
      console.error('Bulk delete error:', error);
      toast.error(t('rdapData.bulk.bulkDeleteFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  // Export
  const handleExport = async () => {
    try {
      const response = await exportRdapData(activeTab);

      if (response.data.success) {
        const blob = new Blob([JSON.stringify(response.data.data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `rdap_${activeTab}_${new Date().toISOString().split('T')[0]}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        toast.success(t('rdapData.toasts.exportSuccess', { count: response.data.count, type: t(`rdapData.entityLabelPlural.${activeTab}`) }));
      }
    } catch (error) {
      console.error('Export error:', error);
      toast.error(t('rdapData.toasts.exportFailed'));
    }
  };

  // Badge helpers
  const getTestBadge = (item) => {
    if (!item.testDataFlag?.isTestData) return null;
    return (
      <span className="badge bg-warning-subtle text-warning" title={t('rdapData.badges.testDataTitle')} style={{ fontSize: '10px', padding: '2px 6px', marginLeft: '4px' }}>
        {t('rdapData.badges.testDataBadge')}
      </span>
    );
  };

  // Render entity table
  const renderEntityTable = () => {
    if (loading) return <Loading message={t('common.loading')} />;

    return (
      <div className="table-responsive">
        {entities.length > 0 ? (
          <table className="table table-hover align-middle">
            <thead>
              <tr>
                <th style={{ width: '40px' }}>
                  <input
                    type="checkbox"
                    checked={selectedIds.length === entities.length && entities.length > 0}
                    onChange={handleSelectAll}
                  />
                </th>
                {activeTab === 'domains' && (
                  <>
                    <th>{t('rdapData.entityTable.domain')}</th>
                    <th>{t('rdapData.entityTable.handle')}</th>
                    <th>{t('common.status')}</th>
                    <th>{t('rdapData.entityTable.dnssec')}</th>
                  </>
                )}
                {activeTab === 'ips' && (
                  <>
                    <th>{t('rdapData.entityTable.handle')}</th>
                    <th>{t('rdapData.entityTable.startAddress')}</th>
                    <th>{t('rdapData.entityTable.endAddress')}</th>
                    <th>{t('rdapData.entityTable.version')}</th>
                    <th>{t('rdapData.entityTable.networkName')}</th>
                  </>
                )}
                {activeTab === 'asns' && (
                  <>
                    <th>{t('rdapData.entityTable.handle')}</th>
                    <th>{t('rdapData.entityTable.startAs')}</th>
                    <th>{t('rdapData.entityTable.endAs')}</th>
                    <th>{t('common.name')}</th>
                    <th>{t('rdapData.entityTable.country')}</th>
                  </>
                )}
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {entities.map((item) => (
                <tr key={item.id} className={selectedIds.includes(item.id) ? 'selected' : ''}>
                  <td>
                    <input
                      type="checkbox"
                      checked={selectedIds.includes(item.id)}
                      onChange={() => handleSelectOne(item.id)}
                    />
                  </td>
                  {activeTab === 'domains' && (
                    <>
                      <td>
                        <span className="font-monospace">{item.ldhName}</span>
                        {getTestBadge(item)}
                      </td>
                      <td className="text-muted small">{item.handle || '-'}</td>
                      <td>
                        {item.status && item.status.length > 0
                          ? <span className="badge bg-info-subtle text-info">{item.status[0]}</span>
                          : '-'
                        }
                      </td>
                      <td>
                        {item.secureDnsDelegationSigned
                          ? <span className="badge bg-success-subtle text-success">{t('common.yes')}</span>
                          : <span className="badge bg-secondary-subtle text-secondary">{t('common.no')}</span>
                        }
                      </td>
                    </>
                  )}
                  {activeTab === 'ips' && (
                    <>
                      <td>
                        <span className="font-monospace">{item.handle}</span>
                        {getTestBadge(item)}
                      </td>
                      <td><span className="font-monospace small">{item.startAddress}</span></td>
                      <td><span className="font-monospace small">{item.endAddress || '-'}</span></td>
                      <td><span className="badge bg-secondary-subtle text-secondary">{item.ipVersion || 'v4'}</span></td>
                      <td className="small">{item.networkName || '-'}</td>
                    </>
                  )}
                  {activeTab === 'asns' && (
                    <>
                      <td>
                        <span className="font-monospace">{item.handle}</span>
                        {getTestBadge(item)}
                      </td>
                      <td><span className="badge bg-primary-subtle text-primary">AS{item.startAutnum}</span></td>
                      <td>
                        {item.endAutnum && item.endAutnum !== item.startAutnum
                          ? <span className="badge bg-primary-subtle text-primary">AS{item.endAutnum}</span>
                          : '-'
                        }
                      </td>
                      <td>{item.autnumName || '-'}</td>
                      <td>{item.country || '-'}</td>
                    </>
                  )}
                  <td>
                    <div className="d-flex gap-2">
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={() => openEditModal(item)}
                        title={t('common.edit')}
                      >
                        <i className="fa-solid fa-pen"></i>
                      </button>
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        onClick={() => openDeleteModal(item)}
                        title={t('common.delete')}
                      >
                        <i className="fa-solid fa-trash"></i>
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="table-empty">
            <i className="fa-solid fa-database" style={{ fontSize: '48px' }}></i>
            <p>{t('rdapData.entityTable.noResultsFound', { type: t(`rdapData.entityLabelPlural.${activeTab}`) })}</p>
            <button type="button" className="btn btn-primary mt-4" onClick={openCreateModal}>
              <i className="fa-solid fa-plus" style={{ marginRight: '4px' }}></i> {t('rdapData.entityTable.addItem', { type: t(`rdapData.entityLabelSingular.${activeTab}`) })}
            </button>
          </div>
        )}
      </div>
    );
  };

  // Render main table
  const renderTable = () => {
    return renderEntityTable();
  };

  // Main render
  if (loading && !entities.length) {
    return <Loading message={t('rdapData.loadingRdapData')} />;
  }

  return (
    <div>
      {/* Page Header */}
      <div className="mb-4">
        <h2 className="h3 fw-bold mb-1">{t('rdapData.pageTitle')}</h2>
        <p className="text-muted mb-0">{t('rdapData.pageSubtitle')}</p>
      </div>

      {/* Stats Cards */}
      {stats && (
        <div className="row g-3">
          {[
            { icon: 'fa-database', tone: 'info', value: stats.totalCount, label: t('rdapData.stats.totalEntities') },
            { icon: 'fa-globe', tone: 'success', value: stats.domainCount, label: t('rdapData.stats.domains') },
            { icon: 'fa-server', tone: 'warning', value: stats.ipCount, label: t('rdapData.stats.ipNetworks') },
            { icon: 'fa-network-wired', tone: 'danger', value: stats.asnCount, label: t('rdapData.stats.asns') },
          ].map((s) => (
            <div className="col-6 col-md-4 col-xl" key={s.label}>
              <div className="card h-100">
                <div className="card-body">
                  <span
                    className={`d-inline-flex align-items-center justify-content-center rounded mb-3 bg-${s.tone}-subtle text-${s.tone}`}
                    style={{ width: 48, height: 48 }}
                  >
                    <i className={`fa-solid ${s.icon}`}></i>
                  </span>
                  <div className="fs-3 fw-bold">{s.value}</div>
                  <div className="text-muted small">{s.label}</div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Tabs */}
      <ul className="nav nav-pills mb-3">
        <li className="nav-item">
          <button type="button" className={`nav-link ${activeTab === 'domains' ? 'active' : ''}`} onClick={() => handleTabChange('domains')}>
            <i className="fa-solid fa-globe" style={{ marginRight: '6px' }}></i>
            {t('rdapData.tabs.domains')}
          </button>
        </li>
        <li className="nav-item">
          <button type="button" className={`nav-link ${activeTab === 'ips' ? 'active' : ''}`} onClick={() => handleTabChange('ips')}>
            <i className="fa-solid fa-server" style={{ marginRight: '6px' }}></i>
            {t('rdapData.tabs.ipNetworks')}
          </button>
        </li>
        <li className="nav-item">
          <button type="button" className={`nav-link ${activeTab === 'asns' ? 'active' : ''}`} onClick={() => handleTabChange('asns')}>
            <i className="fa-solid fa-network-wired" style={{ marginRight: '6px' }}></i>
            {t('rdapData.tabs.asns')}
          </button>
        </li>
        {mappedStatus?.active && mappedStatus.mappings?.length > 0 && (
          <li className="nav-item">
            <button type="button" className={`nav-link ${activeTab === 'mapped' ? 'active' : ''}`}
              onClick={() => handleTabChange('mapped')}>
              <i className="fa-solid fa-database" style={{ marginRight: '6px' }}></i>
              {t('rdapData.tabs.mappedData')}
              <span style={{
                marginLeft: 6, fontSize: 10, padding: '1px 6px', borderRadius: 10,
                background: 'rgba(99,102,241,0.12)', color: 'var(--accent-primary)',
                fontWeight: 600,
              }}>{t('rdapData.tabs.sourceCount', { count: mappedStatus.mappings.length, plural: mappedStatus.mappings.length !== 1 ? 's' : '' })}</span>
            </button>
          </li>
        )}
      </ul>

      {/* Bulk Action Bar */}
      {selectedIds.length > 0 && activeTab !== 'mapped' && (
        <div className="card" style={{ marginBottom: '16px', padding: '12px 16px' }}>
          <div className="d-flex align-items-center gap-3 flex-wrap">
            <span style={{ fontWeight: 600 }}>{t('rdapData.bulk.selected', { count: selectedIds.length })}</span>
            <button type="button" className="btn btn-danger btn-sm" onClick={handleBulkDelete} disabled={submitting}>
              <i className="fa-solid fa-trash" style={{ marginRight: '4px' }}></i> {t('rdapData.bulk.deleteSelected')}
            </button>
            <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setSelectedIds([])}>
              {t('rdapData.bulk.clearSelection')}
            </button>
          </div>
        </div>
      )}

      {/* Main Card - Local Data */}
      {activeTab !== 'mapped' && (
      <div className="card">
        <div className="card-header d-flex justify-content-between align-items-center">
          <div className="d-flex align-items-center gap-3">
            <h3>
              {activeTab === 'domains' && <><i className="fa-solid fa-globe" style={{ marginRight: '8px' }}></i>{t('rdapData.tabs.domains')}</>}
              {activeTab === 'ips' && <><i className="fa-solid fa-server" style={{ marginRight: '8px' }}></i>{t('rdapData.tabs.ipNetworks')}</>}
              {activeTab === 'asns' && <><i className="fa-solid fa-network-wired" style={{ marginRight: '8px' }}></i>{t('rdapData.tabs.asns')}</>}
            </h3>
            <input
              type="text"
              className="form-control"
              style={{ width: '250px' }}
              placeholder={t('rdapData.toolbar.searchPlaceholder')}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <div className="d-flex gap-2">
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowCsvImportModal(true)} title={t('rdapData.toolbar.importCsv')}>
              <i className="fa-solid fa-upload" style={{ marginRight: '4px' }}></i> {t('rdapData.toolbar.csv')}
            </button>
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowJsonImportModal(true)} title={t('rdapData.toolbar.importJson')}>
              <i className="fa-solid fa-code" style={{ marginRight: '4px' }}></i> {t('rdapData.toolbar.json')}
            </button>
            <button type="button" className="btn btn-secondary btn-sm" onClick={handleExport} title={t('rdapData.toolbar.exportJson')}>
              <i className="fa-solid fa-download" style={{ marginRight: '4px' }}></i> {t('common.export')}
            </button>
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => { loadData(); loadStats(); }} title={t('common.refresh')}>
              <i className="fa-solid fa-arrows-rotate"></i>
            </button>
            <button type="button" className="btn btn-primary btn-sm" onClick={openCreateModal}>
              <i className="fa-solid fa-plus" style={{ marginRight: '4px' }}></i> {t('rdapData.entityTable.addItem', { type: t(`rdapData.entityLabelSingular.${activeTab}`) })}
            </button>
          </div>
        </div>

        {/* Table */}
        {renderTable()}

        {/* Pagination */}
        {totalElements > 0 && (
          <div className="pagination">
            <div className="d-flex align-items-center gap-1">
              <label className="text-muted small mb-0">{t('rdapData.pagination.show')}</label>
              <select
                className="form-select form-select-sm w-auto"
                value={pageSize}
                onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0); }}
                aria-label={t('rdapData.pagination.itemsPerPage')}
              >
                {[25, 50, 100, 200].map((o) => (
                  <option key={o} value={o}>{o}</option>
                ))}
              </select>
            </div>
            <button type="button" className="btn btn-secondary btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
              {t('common.previous')}
            </button>
            <span className="text-muted">
              {t('rdapData.pagination.pageOf', { page: page + 1, totalPages, total: totalElements })}
            </span>
            <button type="button" className="btn btn-secondary btn-sm" disabled={page >= totalPages - 1} onClick={() => setPage(page + 1)}>
              {t('common.next')}
            </button>
          </div>
        )}
      </div>
      )}

      {/* Mapped Data Tab Content */}
      {activeTab === 'mapped' && (
        <div className="card">
          <div className="card-header d-flex justify-content-between align-items-center">
            <div className="d-flex align-items-center gap-3 flex-wrap">
              <h3>
                <i className="fa-solid fa-database" style={{ marginRight: '8px' }}></i>
                {t('rdapData.mapped.title')}
              </h3>
              {/* Mapping selector — shown when more than one mapping is active */}
              {mappedStatus?.mappings?.length > 1 ? (
                <select
                  className="form-select"
                  style={{ fontSize: 13, padding: '4px 10px', maxWidth: 220 }}
                  value={selectedMappingId || ''}
                  onChange={(e) => {
                    setSelectedMappingId(Number(e.target.value));
                    setMappedPage(0);
                  }}
                >
                  {mappedStatus.mappings.map(m => (
                    <option key={m.mappingId} value={m.mappingId}>
                      {m.mappingName} ({m.dbType === 'EXTERNAL' ? t('rdapData.mapped.dbTypeExternal') : t('rdapData.mapped.dbTypeLocal')})
                    </option>
                  ))}
                </select>
              ) : (
                <span style={{
                  fontSize: 12, padding: '2px 10px', borderRadius: 10,
                  background: 'rgba(99,102,241,0.1)', color: 'var(--accent-primary)',
                  fontWeight: 500, verticalAlign: 'middle',
                }}>{mappedStatus?.mappings?.[0]?.mappingName}</span>
              )}
              <span className="text-muted small">
                {t('rdapData.mapped.readOnlyInfo', {
                  total: mappedTotalRows.toLocaleString(),
                  source: mappedStatus?.mappings?.find(m => m.mappingId === selectedMappingId)?.dbType === 'EXTERNAL' ? t('rdapData.mapped.sourceExternal') : t('rdapData.mapped.sourceMapped'),
                })}
              </span>
            </div>
            <div className="d-flex gap-2 align-items-center">
              <form onSubmit={(e) => { e.preventDefault(); setMappedSearch(mappedSearchInput); setMappedPage(0); }}
                className="d-flex" style={{ gap: 6 }}>
                <input type="text" className="form-control"
                  style={{ padding: '6px 10px', fontSize: 13, width: 220 }}
                  placeholder={t('rdapData.mapped.searchPlaceholder')}
                  value={mappedSearchInput}
                  onChange={e => setMappedSearchInput(e.target.value)} />
                <button type="submit" className="btn btn-secondary btn-sm">{t('common.search')}</button>
                {mappedSearch && (
                  <button type="button" className="btn btn-secondary btn-sm"
                    onClick={() => { setMappedSearchInput(''); setMappedSearch(''); setMappedPage(0); }}>✕</button>
                )}
              </form>
              <button type="button" className="btn btn-secondary btn-sm" onClick={loadMappedDomains} disabled={mappedLoading}>
                <i className="fa-solid fa-arrows-rotate" style={{ marginRight: '4px' }}></i>
                {t('common.refresh')}
              </button>
            </div>
          </div>

          {mappedLoading ? (
            <div style={{ padding: 40 }}><Loading message={t('rdapData.mapped.loadingMessage')} /></div>
          ) : mappedDomains.length === 0 ? (
            <div style={{ padding: 60, textAlign: 'center' }}>
              <p className="text-muted">{mappedSearch ? t('rdapData.mapped.noSearchResults') : t('rdapData.mapped.noDataFound')}</p>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="table table-hover align-middle" style={{ width: '100%', fontSize: 13 }}>
                <thead>
                  <tr>
                    <th>{t('rdapData.mapped.columns.domainName')}</th>
                    <th>{t('rdapData.mapped.columns.handle')}</th>
                    <th>{t('common.status')}</th>
                    <th>{t('rdapData.mapped.columns.zone')}</th>
                    <th>{t('rdapData.mapped.columns.registration')}</th>
                    <th>{t('rdapData.mapped.columns.expiration')}</th>
                    <th>{t('rdapData.mapped.columns.lastChanged')}</th>
                    <th>{t('rdapData.mapped.columns.nameservers')}</th>
                    <th>{t('rdapData.mapped.columns.contacts')}</th>
                  </tr>
                </thead>
                <tbody>
                  {mappedDomains.map((d, i) => {
                    const contactRoles = ['registrant', 'admin', 'tech', 'billing']
                      .filter(r => d[r + 'Contact'])
                      .map(r => ({ role: r, id: String(d[r + 'Contact']) }));

                    return (
                      <tr key={i}>
                        <td style={{ fontWeight: 600 }}>{d.ldhName || d.d_name || '—'}</td>
                        <td><code style={{ fontSize: 11 }}>{d.handle || d.roid || '—'}</code></td>
                        <td>
                          {(d.status || d.epp_status) ? (
                            <span style={{
                              fontSize: 11, padding: '2px 8px', borderRadius: 10,
                              background: 'rgba(16,185,129,0.1)', color: 'var(--accent-success)',
                              fontWeight: 500,
                            }}>{d.status || d.epp_status}</span>
                          ) : '—'}
                        </td>
                        <td>{d.sld || '—'}</td>
                        <td className="text-muted" style={{ fontSize: 12 }}>
                          {formatMappedDate(d.registrationDate || d.app_date)}
                        </td>
                        <td className="text-muted" style={{ fontSize: 12 }}>
                          {formatMappedDate(d.expirationDate || d.exp_date)}
                        </td>
                        <td className="text-muted" style={{ fontSize: 12 }}>
                          {formatMappedDate(d.lastChangedDate || d.update_date)}
                        </td>
                        <td style={{ maxWidth: 200 }}>
                          {(d.nameservers || d.nameserver) ? (
                            <span style={{ fontSize: 11 }}>
                              {String(d.nameservers || d.nameserver).length > 40 ? (
                                <>
                                  {String(d.nameservers || d.nameserver).substring(0, 40)}…
                                  <button onClick={() => setMappedCellModal({ column: t('rdapData.mapped.columns.nameservers'), value: String(d.nameservers || d.nameserver) })}
                                    style={{
                                      border: '1px solid var(--border-primary)', background: 'var(--bg-tertiary)',
                                      borderRadius: 4, padding: '0 4px', fontSize: 10, cursor: 'pointer',
                                      color: 'var(--accent-primary)', fontWeight: 600, marginLeft: 4,
                                    }}>⤢</button>
                                </>
                              ) : String(d.nameservers || d.nameserver)}
                            </span>
                          ) : '—'}
                        </td>
                        <td>
                          {contactRoles.length > 0 ? (
                            <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                              {contactRoles.map(c => (
                                <span key={c.role} style={{
                                  fontSize: 10, padding: '1px 6px', borderRadius: 8,
                                  background: 'var(--bg-tertiary)', border: '1px solid var(--border-primary)',
                                  whiteSpace: 'nowrap',
                                }} title={`${c.role}: ${c.id}`}>
                                  {c.role.charAt(0).toUpperCase()}: {c.id.length > 12 ? c.id.substring(0, 12) + '…' : c.id}
                                </span>
                              ))}
                            </div>
                          ) : '—'}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {/* Pagination */}
          {mappedTotalRows > 0 && (
            <div style={{
              padding: '12px 16px', borderTop: '1px solid var(--border-primary)',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            }}>
              <div className="d-flex align-items-center gap-2">
                <span className="text-muted small">
                  {t('rdapData.pagination.pageOfMapped', { page: mappedPage + 1, totalPages: mappedTotalPages, total: mappedTotalRows.toLocaleString() })}
                  {mappedSearch && <span>{t('rdapData.pagination.filtered')}</span>}
                </span>
                <div className="d-flex align-items-center gap-1">
                  <label className="text-muted small mb-0">{t('rdapData.pagination.show')}</label>
                  <select
                    className="form-select form-select-sm w-auto"
                    value={mappedPageSize}
                    onChange={(e) => { setMappedPageSize(Number(e.target.value)); setMappedPage(0); }}
                    aria-label={t('rdapData.pagination.rowsPerPage')}
                  >
                    {[25, 50, 100, 200].map((o) => (
                      <option key={o} value={o}>{o}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div style={{ display: 'flex', gap: 4 }}>
                <button className="btn btn-secondary btn-sm" disabled={mappedPage === 0}
                  onClick={() => setMappedPage(0)}>⟪</button>
                <button className="btn btn-secondary btn-sm" disabled={mappedPage === 0}
                  onClick={() => setMappedPage(p => p - 1)}>← {t('rdapData.pagination.prevShort')}</button>
                <button className="btn btn-secondary btn-sm" disabled={mappedPage >= mappedTotalPages - 1}
                  onClick={() => setMappedPage(p => p + 1)}>{t('common.next')} →</button>
                <button className="btn btn-secondary btn-sm" disabled={mappedPage >= mappedTotalPages - 1}
                  onClick={() => setMappedPage(mappedTotalPages - 1)}>⟫</button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Cell value modal for mapped data */}
      {mappedCellModal && (
        <Modal isOpen={true} onClose={() => setMappedCellModal(null)} title={mappedCellModal.column}>
          <pre style={{
            whiteSpace: 'pre-wrap', wordBreak: 'break-all', fontSize: 13, lineHeight: 1.6,
            background: 'var(--bg-tertiary)', padding: 16, borderRadius: 8, margin: 0,
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
          }}>{mappedCellModal.value}</pre>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
            <button className="btn btn-secondary btn-sm" onClick={() => {
              navigator.clipboard.writeText(mappedCellModal.value);
              toast.success(t('rdapData.mapped.copiedToast'));
            }}>{t('common.copy')}</button>
            <button className="btn btn-primary btn-sm" onClick={() => setMappedCellModal(null)}>{t('common.close')}</button>
          </div>
        </Modal>
      )}

      {/* Edit Modal */}
      {showEditModal && (
        <RdapEntityEditModal
          isOpen={showEditModal}
          onClose={() => { setShowEditModal(false); setEditingItem(null); }}
          onSave={handleSave}
          editingItem={editingItem}
          activeTab={activeTab}
          schema={getSchemaForType(activeTab)}
          submitting={submitting}
        />
      )}

      {/* Delete Confirmation Modal */}
      <Modal isOpen={showDeleteModal} onClose={() => setShowDeleteModal(false)} title={t('rdapData.deleteModal.title')}>
        <p>{t('rdapData.deleteModal.confirmText', { type: t(`rdapData.entityLabelSingular.${activeTab}`) })}</p>
        {deleteTarget && (
          <div className="card" style={{ marginTop: '16px', padding: '12px', background: 'var(--color-bg-tertiary)' }}>
            <code>
              {deleteTarget.ldhName || deleteTarget.handle || `ID: ${deleteTarget.id}`}
            </code>
          </div>
        )}
        <p className="text-muted small" style={{ marginTop: '12px' }}>{t('rdapData.deleteModal.cannotBeUndone')}</p>
        <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end', marginTop: '16px' }}>
          <button type="button" className="btn btn-secondary" onClick={() => setShowDeleteModal(false)}>{t('common.cancel')}</button>
          <button type="button" className="btn btn-danger" onClick={handleDelete} disabled={submitting}>
            {submitting ? t('common.deleting') : t('common.delete')}
          </button>
        </div>
      </Modal>

      {/* Import Modals */}
      <CsvImportModal
        show={showCsvImportModal}
        onClose={() => setShowCsvImportModal(false)}
        type={activeTab}
        onSuccess={handleImportSuccess}
      />

      <JsonImportModal
        show={showJsonImportModal}
        onClose={() => setShowJsonImportModal(false)}
        type={activeTab}
        onSuccess={handleImportSuccess}
      />
    </div>
  );
};

export default RdapData;