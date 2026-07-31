/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  getPolicyExpressionsPaged,
  getPolicyEnumValues,
  createPolicyExpression,
  updatePolicyExpression,
  deletePolicyExpression,
  setPolicyExpressionDefault,
  togglePolicyExpressionActive
} from '../services/api';
import { DEFAULT_PAGE_SIZE } from '../components/Pagination';
import Loading from '../components/Loading';
import PolicyExpressionList from '../components/PolicyExpressionList';
import PolicyExpressionModal from '../components/PolicyExpressionModal';
import RedactionRules from '../components/RedactionRules';
import CustomRoles from './CustomRoles';
import PolicyImportModal from '../components/PolicyImportModal';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const DEFAULT_FORM_DATA = {
  name: '',
  description: '',
  scopeConditions: [],
  noteToRequestor: '',
  isActive: true,
  isDefault: false,
  redactionRules: [],
};

const Policy = () => {
  const { t } = useT();

  // Data state
  const [policies, setPolicies] = useState([]);
  const [enumValues, setEnumValues] = useState(null);

  // UI state
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [activeTab, setActiveTab] = useState('policy');
  
  // List state
  const [searchQuery, setSearchQuery] = useState('');
  const [filterActive, setFilterActive] = useState('all');

  // Server-side pagination state
  const [page, setPage] = useState(1); // 1-based
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [totalItems, setTotalItems] = useState(0);
  const [debouncedSearch, setDebouncedSearch] = useState('');

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingPolicy, setEditingPolicy] = useState(null);
  const [formData, setFormData] = useState(DEFAULT_FORM_DATA);
  const [showImportModal, setShowImportModal] = useState(false);

  // Load enum values once on mount
  useEffect(() => {
    loadEnums();
  }, []);

  // Debounce the search box (300ms)
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchQuery), 300);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  // Reset to first page whenever search or the active filter changes
  useEffect(() => { setPage(1); }, [debouncedSearch, filterActive]);

  // Fetch a page whenever paging/search/filter change
  useEffect(() => {
    loadPolicies(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, pageSize, debouncedSearch, filterActive]);

  const loadEnums = async () => {
    try {
      const res = await getPolicyEnumValues();
      setEnumValues(res.data.enumValues || null);
    } catch (error) {
      console.error('Failed to load policy enum values:', error);
    }
  };

  const loadPolicies = async (showLoader = true) => {
    if (showLoader) setLoading(true);
    try {
      const res = await getPolicyExpressionsPaged({
        page: page - 1,
        size: pageSize,
        search: debouncedSearch || undefined,
        active: filterActive === 'active' ? true : filterActive === 'inactive' ? false : undefined,
        sortBy: 'id',
        sortDir: 'desc',
      });
      setPolicies(res.data.content || []);
      setTotalItems(res.data.totalElements || 0);
    } catch (error) {
      console.error('Failed to load policy data:', error);
      if (showLoader) toast.error(t('policy.page.loadFailed'));
    } finally {
      if (showLoader) setLoading(false);
    }
  };

  // Refetch the current page without the full-screen loader (used after mutations).
  const loadData = () => loadPolicies(false);

  // Modal handlers
  const openCreateModal = useCallback(() => {
    setEditingPolicy(null);
    setFormData(DEFAULT_FORM_DATA);
    setModalOpen(true);
  }, []);

  const openEditModal = useCallback((policy) => {
    setEditingPolicy(policy);
    setFormData({
      name: policy.name || '',
      description: policy.description || '',
      scopeConditions: (() => {
        const raw = policy.scopeConditions;
        if (!raw) return [];
        if (Array.isArray(raw)) return raw;
        if (typeof raw === 'string' && raw.trim()) {
          try { return JSON.parse(raw); } catch { return []; }
        }
        return [];
      })(),
      noteToRequestor: policy.noteToRequestor || '',
      isActive: policy.isActive ?? true,
      isDefault: policy.isDefault ?? false,
      redactionRules: policy.redactionRules || [],
    });
    setModalOpen(true);
  }, []);

  const closeModal = useCallback(() => {
    setModalOpen(false);
    setEditingPolicy(null);
    setFormData(DEFAULT_FORM_DATA);
  }, []);

  // CRUD handlers
  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    
    try {
      const payload = {
        ...formData,
        scopeConditions: (() => {
          const conditions = Array.isArray(formData.scopeConditions) ? formData.scopeConditions : [];
          // Filter out conditions with no field selected
          const valid = conditions.filter(c => c.field && c.field !== '');
          return valid.length > 0 ? JSON.stringify(valid) : null;
        })(),
      };

      if (editingPolicy?.id) {
        await updatePolicyExpression(editingPolicy.id, payload);
        toast.success(t('policy.page.updateSuccess'));
      } else {
        await createPolicyExpression(payload);
        toast.success(t('policy.page.createSuccess'));
      }

      closeModal();
      await loadData();
    } catch (error) {
      console.error('Failed to save policy:', error);
      const message = error.response?.data?.error || t('policy.page.saveFailed');
      toast.error(message);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm(t('policy.page.deleteConfirm'))) {
      return;
    }

    try {
      await deletePolicyExpression(id);
      toast.success(t('policy.page.deleteSuccess'));
      await loadData();
    } catch (error) {
      console.error('Failed to delete policy:', error);
      toast.error(t('policy.page.deleteFailed'));
    }
  };

  const handleSetDefault = async (id) => {
    try {
      await setPolicyExpressionDefault(id);
      toast.success(t('policy.page.setDefaultSuccess'));
      await loadData();
    } catch (error) {
      console.error('Failed to set default:', error);
      toast.error(t('policy.page.setDefaultFailed'));
    }
  };

  const handleToggleActive = async (id) => {
    try {
      await togglePolicyExpressionActive(id);
      toast.success(t('policy.page.toggleActiveSuccess'));
      await loadData();
    } catch (error) {
      console.error('Failed to toggle active:', error);
      toast.error(t('policy.page.toggleActiveFailed'));
    }
  };

  if (loading && policies.length === 0) {
    return <Loading message={t('policy.page.loadingMessage')} />;
  }

  return (
    <div className="policy-page">
      <div className="d-flex justify-content-between align-items-start mb-4">
        <div>
          <h2 className="h3 fw-bold mb-1 d-flex align-items-center">
            <i className="fa-solid fa-file-shield" style={{ marginRight: '12px', color: 'var(--accent-primary)' }} />
            {t('policy.page.title')}
          </h2>
          <p className="text-muted mb-0">{t('policy.page.subtitle')}</p>
        </div>
        <div className="d-flex gap-2 align-items-center">
          <button className="btn btn-secondary" onClick={() => setShowImportModal(true)}>
            {t('policy.page.importJson')}
          </button>
          <button className="btn btn-outline-secondary d-flex align-items-center gap-2" onClick={loadData} title={t('common.refresh')}>
            <i className="fa-solid fa-rotate" />
          </button>
        </div>
      </div>

      {/* Tab Navigation */}
      <ul className="nav nav-tabs mb-4">
        <li className="nav-item">
          <button
            className={`nav-link${activeTab === 'policy' ? ' active' : ''}`}
            onClick={() => setActiveTab('policy')}
          >
            <i className="fa-solid fa-file-contract me-2" />
            {t('policy.page.tabPolicyExpressions')}
            <span className="badge bg-secondary ms-2">{totalItems}</span>
          </button>
        </li>
        <li className="nav-item">
          <button
            className={`nav-link${activeTab === 'redaction' ? ' active' : ''}`}
            onClick={() => setActiveTab('redaction')}
          >
            <i className="fa-solid fa-shield-halved me-2" />
            {t('policy.page.tabRedactionRules')}
          </button>
        </li>
        <li className="nav-item">
          <button
            className={`nav-link${activeTab === 'roles' ? ' active' : ''}`}
            onClick={() => setActiveTab('roles')}
          >
            <i className="fa-solid fa-user-tag me-2" />
            {t('policy.page.tabCustomRoles')}
          </button>
        </li>
      </ul>

      {/* Policy Expressions Tab */}
      {activeTab === 'policy' && (
        <div className="policy-content">
          <PolicyExpressionList
            policies={policies}
            selectedId={editingPolicy?.id}
            onSelect={openEditModal}
            onEdit={openEditModal}
            onDelete={handleDelete}
            onSetDefault={handleSetDefault}
            onToggleActive={handleToggleActive}
            onCreate={openCreateModal}
            searchQuery={searchQuery}
            onSearchChange={setSearchQuery}
            filterActive={filterActive}
            onFilterChange={setFilterActive}
            page={page}
            pageSize={pageSize}
            totalItems={totalItems}
            onPageChange={setPage}
            onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
          />
        </div>
      )}

      {/* Redaction Rules Tab */}
      {activeTab === 'redaction' && (
        <div className="card">
          <RedactionRules />
        </div>
      )}

      {/* Custom Roles Tab */}
      {activeTab === 'roles' && <CustomRoles />}

      {/* Create/Edit Modal */}
      <PolicyExpressionModal
        isOpen={modalOpen}
        onClose={closeModal}
        policy={editingPolicy}
        formData={formData}
        enumValues={enumValues}
        onChange={setFormData}
        onSubmit={handleSubmit}
        saving={saving}
        existingPolicies={policies}
      />

      <style>{`
        .policy-page {
          max-width: 1400px;
        }

        .policy-tabs {
          display: flex;
          gap: 4px;
          margin-bottom: 24px;
          border-bottom: 1px solid var(--border-primary);
          padding-bottom: 0;
        }

        .tab-btn {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 12px 20px;
          background: none;
          border: none;
          border-bottom: 2px solid transparent;
          margin-bottom: -1px;
          font-size: 14px;
          font-weight: 500;
          color: var(--text-secondary);
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .tab-btn:hover {
          color: var(--text-primary);
        }

        .tab-btn.active {
          color: var(--accent-primary);
          border-bottom-color: var(--accent-primary);
        }

        .tab-btn i {
          font-size: 16px;
        }

        .tab-count {
          font-size: 11px;
          font-weight: 600;
          padding: 2px 6px;
          background: var(--bg-tertiary);
          border-radius: 10px;
          color: var(--text-tertiary);
        }

        .tab-btn.active .tab-count {
          background: var(--accent-primary-alpha, rgba(59, 130, 246, 0.1));
          color: var(--accent-primary);
        }

        .policy-content {
          animation: fadeIn 0.2s ease;
        }

        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(8px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>

      <PolicyImportModal
        isOpen={showImportModal}
        onClose={() => setShowImportModal(false)}
        onImportComplete={loadData}
      />
    </div>
  );
};

export default Policy;