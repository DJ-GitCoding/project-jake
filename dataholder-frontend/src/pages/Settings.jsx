/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import {
  getGroupAdminSettings,
  saveGroupAdminSettings,
  updateGroupAdminSettingsById,
  deleteGroupAdminSettings,
  testGroupAdminConnectionById,
  toggleGroupAdminConnection,
  getDataHolderConfig,
  updateDataHolderConfig,
} from '../services/api';
import Loading from '../components/Loading';
import Modal from '../components/Modal';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const EMPTY_FORM = { name: '', baseUrl: '', clientId: '', clientSecret: '' };

const Settings = () => {
  const [connections, setConnections] = useState([]);
  const [loading, setLoading] = useState(true);

  // Add / Edit modal
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  // Per-connection test results { [id]: { connected, error, dataHolder } }
  const [testResults, setTestResults] = useState({});
  const [testingId, setTestingId] = useState(null);

  // Global config state
  const [globalConfig, setGlobalConfig] = useState(null);
  const [savingConfig, setSavingConfig] = useState(false);

  const { t } = useT();

  useEffect(() => {
    loadSettings();
    loadGlobalConfig();
  }, []);

  const loadSettings = async () => {
    try {
      setLoading(true);
      const response = await getGroupAdminSettings();
      const data = response.data;
      setConnections(data.connections || []);
    } catch (error) {
      console.error('Failed to load settings:', error);
      toast.error(t('settings.loadSettingsFailed'));
    } finally {
      setLoading(false);
    }
  };

  const loadGlobalConfig = async () => {
    try {
      const response = await getDataHolderConfig();
      if (response.data.success) {
        setGlobalConfig(response.data.config);
      }
    } catch (error) {
      console.error('Failed to load global config:', error);
    }
  };

  const handleToggleManualReview = async () => {
    const newValue = !globalConfig?.requireManualReviewAll;
    setSavingConfig(true);
    try {
      const response = await updateDataHolderConfig({ requireManualReviewAll: newValue });
      if (response.data.success) {
        setGlobalConfig(response.data.config);
        toast.success(newValue
          ? t('settings.manualReviewEnabled')
          : t('settings.manualReviewDisabled'));
      }
    } catch (error) {
      toast.error(t('settings.updateConfigFailed'));
    } finally {
      setSavingConfig(false);
    }
  };

  // ==================== Add / Edit ====================

  const openAddModal = () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setShowModal(true);
  };

  const openEditModal = (conn) => {
    setEditingId(conn.id);
    setForm({
      name: conn.name || '',
      baseUrl: conn.baseUrl || '',
      clientId: conn.clientId || '',
      clientSecret: '',
    });
    setShowModal(true);
  };

  const handleSave = async () => {
    if (!form.baseUrl.trim() || !form.clientId.trim()) {
      toast.error(t('settings.baseUrlClientIdRequired'));
      return;
    }
    if (!editingId && !form.clientSecret.trim()) {
      toast.error(t('settings.clientSecretRequired'));
      return;
    }

    setSaving(true);
    try {
      if (editingId) {
        const payload = { ...form };
        if (!payload.clientSecret.trim()) delete payload.clientSecret;
        await updateGroupAdminSettingsById(editingId, payload);
        toast.success(t('settings.connectionUpdated'));
      } else {
        await saveGroupAdminSettings(form);
        toast.success(t('settings.connectionAdded'));
      }
      setShowModal(false);
      setTestResults({});
      loadSettings();
    } catch (error) {
      toast.error(t('settings.saveConnectionFailed'));
    } finally {
      setSaving(false);
    }
  };

  // ==================== Delete ====================

  const handleDelete = async (conn) => {
    if (!window.confirm(t('settings.deleteConnectionConfirm', { name: conn.name || conn.baseUrl }))) return;
    try {
      await deleteGroupAdminSettings(conn.id);
      toast.success(t('settings.connectionDeleted'));
      setTestResults(prev => { const copy = { ...prev }; delete copy[conn.id]; return copy; });
      loadSettings();
    } catch (error) {
      toast.error(t('settings.deleteConnectionFailed'));
    }
  };

  // ==================== Toggle Active ====================

  const handleToggle = async (conn) => {
    try {
      const response = await toggleGroupAdminConnection(conn.id);
      if (response.data.success) {
        toast.success(response.data.message);
        loadSettings();
      }
    } catch (error) {
      toast.error(t('settings.toggleConnectionFailed'));
    }
  };

  // ==================== Test ====================

  const handleTest = async (conn) => {
    setTestingId(conn.id);
    setTestResults(prev => ({ ...prev, [conn.id]: null }));
    try {
      const response = await testGroupAdminConnectionById(conn.id);
      setTestResults(prev => ({ ...prev, [conn.id]: response.data }));
      if (response.data.connected) {
        toast.success(t('settings.connectionTestSuccess', { name: conn.name || conn.baseUrl }));
        loadSettings(); // refresh lastConnectedAt
      } else {
        toast.error(t('settings.connectionFailedWithError', { error: response.data.error || t('settings.unknownError') }));
      }
    } catch (error) {
      setTestResults(prev => ({ ...prev, [conn.id]: { connected: false, error: error.message } }));
      toast.error(t('settings.connectionTestFailed'));
    } finally {
      setTestingId(null);
    }
  };

  // ==================== Render ====================

  if (loading) return <Loading message={t('settings.loadingMessage')} />;

  const activeCount = connections.filter(c => c.isActive).length;

  return (
    <div>
      <div className="mb-4">
        <h2 className="h3 fw-bold mb-1">{t('settings.title')}</h2>
        <p className="text-muted mb-0">{t('settings.subtitle')}</p>
      </div>

      {/* Global Request Controls */}
      <div className="card" style={{ marginBottom: '24px' }}>
        <div className="card-header">
          <h3><i className="fa-solid fa-gear"></i> {t('settings.requestControls')}</h3>
        </div>
        <div className="card-body" style={{ padding: '24px' }}>
          <div style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            padding: '16px 20px', borderRadius: '12px',
            background: globalConfig?.requireManualReviewAll
              ? 'rgba(245, 158, 11, 0.08)'
              : 'var(--bg-tertiary)',
            border: `1px solid ${globalConfig?.requireManualReviewAll
              ? 'rgba(245, 158, 11, 0.25)'
              : 'var(--border-primary)'}`,
          }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontWeight: 600, fontSize: 15, marginBottom: 4 }}>
                {t('settings.requireManualReviewTitle')}
              </div>
              <div className="text-muted small" style={{ maxWidth: 500 }}>
                {globalConfig?.requireManualReviewAll
                  ? t('settings.manualReviewAllDescription')
                  : t('settings.manualReviewDefaultDescription')}
              </div>
              {globalConfig?.updatedBy && globalConfig?.updatedAt && (
                <div className="text-muted small" style={{ marginTop: 8, fontSize: 11 }}>
                  {t('settings.lastChangedBy')} <strong>{globalConfig.updatedBy}</strong> {t('settings.lastChangedAt', { date: new Date(globalConfig.updatedAt).toLocaleString() })}
                </div>
              )}
            </div>
            <div style={{ flexShrink: 0, marginLeft: 24 }}>
              <button
                onClick={handleToggleManualReview}
                disabled={savingConfig}
                style={{
                  position: 'relative',
                  width: 52, height: 28, borderRadius: 14,
                  border: 'none', cursor: savingConfig ? 'wait' : 'pointer',
                  background: globalConfig?.requireManualReviewAll
                    ? 'var(--accent-warning)'
                    : 'var(--text-muted)',
                  transition: 'background 0.2s ease',
                }}
              >
                <span style={{
                  position: 'absolute',
                  top: 3, left: globalConfig?.requireManualReviewAll ? 26 : 3,
                  width: 22, height: 22, borderRadius: '50%',
                  background: 'white',
                  transition: 'left 0.2s ease',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
                }} />
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Group Admin Connections */}
      <div className="card" style={{ marginBottom: '24px' }}>
        <div className="card-header d-flex justify-content-between align-items-center">
          <h3><i className="fa-solid fa-gear"></i> {t('settings.groupAdminConnections')}</h3>
          <div className="d-flex align-items-center gap-2">
            <span className="text-muted small">
              {activeCount === 1
                ? t('settings.activeConnectionSingular', { count: activeCount })
                : t('settings.activeConnectionsPlural', { count: activeCount })}
            </span>
            <button className="btn btn-primary btn-sm" onClick={openAddModal}>
              {t('settings.addConnection')}
            </button>
          </div>
        </div>
        <div className="card-body" style={{ padding: '0' }}>
          {connections.length === 0 ? (
            <div style={{ padding: '40px 24px', textAlign: 'center' }}>
              <div className="text-muted" style={{ marginBottom: 16 }}>
                <strong>{t('settings.noConnectionsConfigured')}</strong>
              </div>
              <p className="text-muted small" style={{ maxWidth: 440, margin: '0 auto 16px' }}>
                {t('settings.noConnectionsHint')}
              </p>
              <button className="btn btn-primary" onClick={openAddModal}>
                {t('settings.addFirstConnection')}
              </button>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="table table-hover align-middle">
                <thead>
                  <tr>
                    <th>{t('common.name')}</th>
                    <th>{t('settings.table.baseUrl')}</th>
                    <th>{t('settings.table.clientId')}</th>
                    <th>{t('common.status')}</th>
                    <th>{t('settings.table.lastConnected')}</th>
                    <th>{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {connections.map(conn => {
                    const result = testResults[conn.id];
                    return (
                      <tr key={conn.id} style={{ opacity: conn.isActive ? 1 : 0.5 }}>
                        <td>
                          <strong>{conn.name || t('settings.unnamed')}</strong>
                          {!conn.isActive && (
                            <span className="badge bg-light text-dark border" style={{ marginLeft: 8, fontSize: 10 }}>{t('common.disabled')}</span>
                          )}
                        </td>
                        <td>
                          <code className="small">{conn.baseUrl}</code>
                        </td>
                        <td>
                          <code className="small">{conn.clientId}</code>
                        </td>
                        <td>
                          {result ? (
                            result.connected ? (
                              <span className="badge bg-success-subtle text-success" style={{ fontSize: 11 }}>
                                <i className="fa-solid fa-check"></i> {t('settings.connected')}
                              </span>
                            ) : (
                              <span className="badge bg-danger-subtle text-danger" style={{ fontSize: 11 }} title={result.error}>
                                <i className="fa-solid fa-xmark"></i> {t('settings.failed')}
                              </span>
                            )
                          ) : conn.lastError ? (
                            <span className="badge bg-danger-subtle text-danger" style={{ fontSize: 11 }} title={conn.lastError}>
                              {t('common.error')}
                            </span>
                          ) : conn.lastConnectedAt ? (
                            <span className="badge bg-success-subtle text-success" style={{ fontSize: 11 }}>{t('settings.ok')}</span>
                          ) : (
                            <span className="badge bg-light text-dark border" style={{ fontSize: 11 }}>{t('settings.notTested')}</span>
                          )}
                        </td>
                        <td className="text-muted small">
                          {conn.lastConnectedAt
                            ? new Date(conn.lastConnectedAt).toLocaleString()
                            : t('settings.never')}
                        </td>
                        <td>
                          <div className="d-flex gap-2">
                            <button
                              className="btn btn-secondary btn-sm"
                              onClick={() => handleTest(conn)}
                              disabled={testingId === conn.id || !conn.isActive}
                              title={t('settings.testConnection')}
                            >
                              {testingId === conn.id ? '...' : <i className="fa-solid fa-arrows-rotate"></i>}
                            </button>
                            <button
                              className="btn btn-secondary btn-sm"
                              onClick={() => openEditModal(conn)}
                              title={t('common.edit')}
                            >
                              <i className="fa-solid fa-pen"></i>
                            </button>
                            <button
                              className={`btn btn-sm ${conn.isActive ? 'btn-secondary' : 'btn-success'}`}
                              onClick={() => handleToggle(conn)}
                              title={conn.isActive ? t('settings.disable') : t('settings.enable')}
                              style={{ fontSize: 11, minWidth: 60 }}
                            >
                              {conn.isActive ? t('settings.disable') : t('settings.enable')}
                            </button>
                            <button
                              className="btn btn-outline-secondary btn-sm"
                              onClick={() => handleDelete(conn)}
                              title={t('common.delete')}
                              style={{ color: 'var(--color-danger)' }}
                            >
                              <i className="fa-solid fa-trash"></i>
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {/* Add / Edit Modal */}
      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title={editingId ? t('settings.editConnectionTitle') : t('settings.addConnectionTitle')}
        footer={
          <>
            <button className="btn btn-secondary" onClick={() => setShowModal(false)}>
              {t('common.cancel')}
            </button>
            <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
              {saving ? t('common.saving') : editingId ? t('common.update') : t('settings.addConnection')}
            </button>
          </>
        }
      >
        <div style={{ display: 'grid', gap: '16px' }}>
          <div className="mb-3">
            <label className="form-label">{t('settings.connectionName')}</label>
            <input
              type="text"
              className="form-control"
              value={form.name}
              onChange={(e) => setForm(p => ({ ...p, name: e.target.value }))}
              placeholder={t('settings.connectionNamePlaceholder')}
            />
            <p className="text-muted small" style={{ marginTop: '4px' }}>
              {t('settings.connectionNameHint')}
            </p>
          </div>

          <div className="mb-3">
            <label className="form-label">{t('settings.baseUrlLabel')}</label>
            <input
              type="text"
              className="form-control"
              value={form.baseUrl}
              onChange={(e) => setForm(p => ({ ...p, baseUrl: e.target.value }))}
              placeholder={t('settings.baseUrlPlaceholder')}
            />
          </div>

          <div className="mb-3">
            <label className="form-label">{t('settings.clientIdLabel')}</label>
            <input
              type="text"
              className="form-control"
              value={form.clientId}
              onChange={(e) => setForm(p => ({ ...p, clientId: e.target.value }))}
              placeholder={t('settings.clientIdPlaceholder')}
            />
          </div>

          <div className="mb-3">
            <label className="form-label">
              {t('settings.clientSecretLabel')} {editingId ? t('settings.leaveBlankHint') : '*'}
            </label>
            <input
              type="password"
              className="form-control"
              value={form.clientSecret}
              onChange={(e) => setForm(p => ({ ...p, clientSecret: e.target.value }))}
              placeholder={editingId ? '••••••••' : t('settings.enterClientSecretPlaceholder')}
            />
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default Settings;
