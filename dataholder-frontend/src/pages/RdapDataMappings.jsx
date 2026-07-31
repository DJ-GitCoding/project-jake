/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  getRdapDataMappings, createRdapDataMapping, updateRdapDataMapping,
  deleteRdapDataMapping, toggleRdapDataMappingActive, getRdapDataMappingDefaults,
  testMappingConnection, introspectMappingSchema, suggestMappingColumns,
  livePreviewMappingData,
} from '../services/api';
import Loading from '../components/Loading';
import Modal from '../components/Modal';
import DeleteConfirmModal from '../components/DeleteConfirmModal';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

// All supported object types with their table/mapping field keys
const OBJECT_TYPES = [
  { key: 'domain', label: 'Domains', tableField: 'domainsTable', mappingField: 'domainColumnMappings', defaultTable: 'rdap_domains' },
  { key: 'contact', label: 'Contacts', tableField: 'contactsTable', mappingField: 'contactColumnMappings', defaultTable: 'contact' },
  { key: 'host', label: 'Hosts', tableField: 'hostsTable', mappingField: 'hostColumnMappings', defaultTable: 'host' },
  { key: 'ip', label: 'IP Networks', tableField: 'ipsTable', mappingField: 'ipColumnMappings', defaultTable: 'rdap_ips' },
  { key: 'asn', label: 'Autonomous Systems', tableField: 'asnsTable', mappingField: 'asnColumnMappings', defaultTable: 'rdap_asns' },
  { key: 'entity', label: 'Entities', tableField: 'entitiesTable', mappingField: 'entityColumnMappings', defaultTable: 'rdap_entities' },
];

const INITIAL_FORM = {
  name: '', description: '',
  dbType: 'LOCAL',
  externalJdbcUrl: '', externalDbUsername: '', externalDbPassword: '', externalDbDriver: '', externalDbSchema: '',
  columnNameCaseSensitive: false,
  domainsTable: '', ipsTable: '', asnsTable: '', entitiesTable: '', contactsTable: '', hostsTable: '',
  domainColumnMappings: {}, ipColumnMappings: {}, asnColumnMappings: {},
  entityColumnMappings: {}, contactColumnMappings: {}, hostColumnMappings: {},
  domainContactJoinMappings: {}, contactJoinKey: 'id',
  hostJoinConfig: {},
  domainNameSuffixColumn: '', domainNameSeparator: '.',
  customTableMappings: [],
};

const RdapDataMappings = () => {
  const [mappings, setMappings] = useState([]);
  const [defaults, setDefaults] = useState(null);
  const [loading, setLoading] = useState(true);
  const [showEditor, setShowEditor] = useState(false);
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [saving, setSaving] = useState(false);
  const [activeTab, setActiveTab] = useState('domain');
  const [editorStep, setEditorStep] = useState(0); // 0=basic, 1=connection, 2=tables, 3=columns, 4=joins, 5=preview

  // Connection / introspection state
  const [connTesting, setConnTesting] = useState(false);
  const [connResult, setConnResult] = useState(null);
  const [introspecting, setIntrospecting] = useState(false);
  const [discoveredSchema, setDiscoveredSchema] = useState(null);
  const [previewData, setPreviewData] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  const [form, setForm] = useState({ ...INITIAL_FORM });
  const [expandedCustomTable, setExpandedCustomTable] = useState(null);

  const { t } = useT();

  // Look up the translated label for an RDAP object type, falling back to the raw key.
  const getObjectTypeLabel = (key) => (OBJECT_TYPES.some(ot => ot.key === key) ? t(`rdapMappings.objectTypes.${key}`) : key);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      const [mappingsRes, defaultsRes] = await Promise.all([
        getRdapDataMappings(),
        getRdapDataMappingDefaults(),
      ]);
      if (mappingsRes.data.success) setMappings(mappingsRes.data.mappings);
      if (defaultsRes.data.success) setDefaults(defaultsRes.data.defaults);
    } catch (error) {
      toast.error(t('rdapMappings.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { loadData(); }, [loadData]);

  // Auto-discover schema when the editor modal opens
  const [autoIntrospectPending, setAutoIntrospectPending] = useState(false);

  useEffect(() => {
    if (autoIntrospectPending && showEditor) {
      setAutoIntrospectPending(false);
      // Only auto-introspect if we have connection info (EXTERNAL) or it's LOCAL
      const canIntrospect = form.dbType === 'LOCAL' || (form.dbType === 'EXTERNAL' && form.externalJdbcUrl);
      if (canIntrospect && !discoveredSchema) {
        handleIntrospect(true);
      }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoIntrospectPending, showEditor]);

  const resetForm = () => {
    setForm({ ...INITIAL_FORM });
    setConnResult(null);
    setDiscoveredSchema(null);
    setPreviewData(null);
    setEditorStep(0);
    setExpandedCustomTable(null);
  };

  const openCreate = () => {
    resetForm();
    setEditing(null);
    setActiveTab('domain');
    setShowEditor(true);
    setAutoIntrospectPending(true);
  };

  const openEdit = (mapping) => {
    setForm({
      name: mapping.name || '',
      description: mapping.description || '',
      dbType: mapping.dbType || 'LOCAL',
      externalJdbcUrl: mapping.externalJdbcUrl || '',
      externalDbUsername: mapping.externalDbUsername || '',
      externalDbPassword: mapping.externalDbPassword || '',
      externalDbDriver: mapping.externalDbDriver || '',
      externalDbSchema: mapping.externalDbSchema || '',
      columnNameCaseSensitive: mapping.columnNameCaseSensitive || false,
      domainsTable: mapping.domainsTable || '',
      ipsTable: mapping.ipsTable || '',
      asnsTable: mapping.asnsTable || '',
      entitiesTable: mapping.entitiesTable || '',
      contactsTable: mapping.contactsTable || '',
      hostsTable: mapping.hostsTable || '',
      domainColumnMappings: mapping.domainColumnMappings || {},
      ipColumnMappings: mapping.ipColumnMappings || {},
      asnColumnMappings: mapping.asnColumnMappings || {},
      entityColumnMappings: mapping.entityColumnMappings || {},
      contactColumnMappings: mapping.contactColumnMappings || {},
      hostColumnMappings: mapping.hostColumnMappings || {},
      domainContactJoinMappings: mapping.domainContactJoinMappings || {},
      contactJoinKey: mapping.contactJoinKey || 'id',
      hostJoinConfig: mapping.hostJoinConfig || {},
      domainNameSuffixColumn: mapping.domainNameSuffixColumn || '',
      domainNameSeparator: mapping.domainNameSeparator || '.',
      customTableMappings: mapping.customTableMappings || [],
    });
    if (mapping.discoveredSchema) setDiscoveredSchema(mapping.discoveredSchema);
    else setDiscoveredSchema(null);
    setEditing(mapping);
    setActiveTab('domain');
    setPreviewData(null);
    setEditorStep(0);
    setShowEditor(true);
    // Auto-discover schema if not already cached on the mapping
    if (!mapping.discoveredSchema) {
      setAutoIntrospectPending(true);
    }
  };

  const handleSave = async () => {
    if (!form.name.trim()) { toast.error(t('rdapMappings.nameRequired')); return; }

    setSaving(true);
    try {
      const clean = (m) => {
        const c = {};
        Object.entries(m || {}).forEach(([k, v]) => { if (v && v.toString().trim()) c[k] = v.toString().trim(); });
        return c;
      };

      const payload = {
        ...form,
        domainColumnMappings: clean(form.domainColumnMappings),
        ipColumnMappings: clean(form.ipColumnMappings),
        asnColumnMappings: clean(form.asnColumnMappings),
        entityColumnMappings: clean(form.entityColumnMappings),
        contactColumnMappings: clean(form.contactColumnMappings),
        hostColumnMappings: clean(form.hostColumnMappings),
        domainContactJoinMappings: clean(form.domainContactJoinMappings),
        hostJoinConfig: clean(form.hostJoinConfig),
        customTableMappings: form.customTableMappings || [],
      };

      if (editing) {
        await updateRdapDataMapping(editing.id, payload);
        toast.success(t('rdapMappings.mappingUpdated'));
      } else {
        await createRdapDataMapping(payload);
        toast.success(t('rdapMappings.mappingCreated'));
      }
      setShowEditor(false);
      resetForm();
      loadData();
    } catch (error) {
      toast.error(error.response?.data?.error || t('rdapMappings.saveMappingFailed'));
    } finally {
      setSaving(false);
    }
  };

  const handleToggleActive = async (mapping) => {
    try {
      await toggleRdapDataMappingActive(mapping.id);
      toast.success(mapping.isActive
        ? t('rdapMappings.mappingDeactivated', { name: mapping.name })
        : t('rdapMappings.mappingActivated', { name: mapping.name }));
      loadData();
    } catch (error) {
      toast.error(error.response?.data?.error || t('rdapMappings.toggleFailed'));
    }
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await deleteRdapDataMapping(deleting.id);
      toast.success(t('rdapMappings.mappingDeleted'));
      setDeleting(null);
      loadData();
    } catch (error) {
      toast.error(error.response?.data?.error || t('rdapMappings.deleteFailed'));
    }
  };

  // ==================== Connection Testing ====================

  const handleTestConnection = async () => {
    setConnTesting(true);
    setConnResult(null);
    try {
      const res = await testMappingConnection({
        jdbcUrl: form.externalJdbcUrl,
        username: form.externalDbUsername,
        password: form.externalDbPassword,
        driver: form.externalDbDriver,
      });
      setConnResult(res.data);
      if (res.data.success) toast.success(t('rdapMappings.connectionSuccess'));
      else toast.error(t('rdapMappings.connectionFailed', { error: res.data.error || t('rdapMappings.unknownError') }));
    } catch (error) {
      setConnResult({ success: false, error: error.message });
      toast.error(t('rdapMappings.connectionTestFailed'));
    } finally {
      setConnTesting(false);
    }
  };

  // ==================== Schema Introspection ====================

  const handleIntrospect = async (silent = false) => {
    setIntrospecting(true);
    try {
      const payload = form.dbType === 'EXTERNAL' ? {
        jdbcUrl: form.externalJdbcUrl,
        username: form.externalDbUsername,
        password: form.externalDbPassword,
        driver: form.externalDbDriver,
        schemaFilter: form.externalDbSchema,
      } : {};

      const res = await introspectMappingSchema(payload);
      if (res.data.success) {
        setDiscoveredSchema(res.data.schema);
        if (!silent) toast.success(t('rdapMappings.discoveredTables', { count: res.data.schema.tables?.length || 0 }));
      } else {
        if (!silent) toast.error(t('rdapMappings.introspectionFailed', { error: res.data.schema?.error || t('common.unknown') }));
      }
    } catch (error) {
      if (!silent) toast.error(t('rdapMappings.schemaIntrospectionFailed'));
    } finally {
      setIntrospecting(false);
    }
  };

  // ==================== Auto-Suggest ====================

  const handleAutoSuggest = async (objType) => {
    const tableField = OBJECT_TYPES.find(o => o.key === objType)?.tableField;
    const tableName = form[tableField];
    if (!tableName && !discoveredSchema) { toast.error(t('rdapMappings.selectTableFirst')); return; }

    // Find columns for the selected table in discovered schema
    const table = discoveredSchema?.tables?.find(dt => dt.name === tableName);
    if (!table) { toast.error(t('rdapMappings.tableNotFound', { table: tableName })); return; }

    const externalColumns = table.columns.map(c => c.name);

    try {
      const res = await suggestMappingColumns({ externalColumns, objectType: objType });
      if (res.data.success && res.data.suggestions) {
        const mappingField = OBJECT_TYPES.find(o => o.key === objType)?.mappingField;
        setForm(prev => ({
          ...prev,
          [mappingField]: { ...prev[mappingField], ...res.data.suggestions },
        }));
        const count = Object.keys(res.data.suggestions).length;
        toast.success(t('rdapMappings.autoSuggested', { count, s: count !== 1 ? 's' : '' }));
      }
    } catch (error) {
      toast.error(t('rdapMappings.autoSuggestFailed'));
    }
  };

  // ==================== Preview ====================

  const handlePreview = async (objType) => {
    if (!editing) { toast.error(t('rdapMappings.saveBeforePreview')); return; }
    setPreviewLoading(true);
    try {
      // Clean the mappings the same way handleSave does
      const clean = (m) => {
        const c = {};
        Object.entries(m || {}).forEach(([k, v]) => { if (v && v.toString().trim()) c[k] = v.toString().trim(); });
        return c;
      };

      // Send current form state so preview reflects unsaved changes
      const formState = {
        ...form,
        domainColumnMappings: clean(form.domainColumnMappings),
        ipColumnMappings: clean(form.ipColumnMappings),
        asnColumnMappings: clean(form.asnColumnMappings),
        entityColumnMappings: clean(form.entityColumnMappings),
        contactColumnMappings: clean(form.contactColumnMappings),
        hostColumnMappings: clean(form.hostColumnMappings),
      };

      const res = await livePreviewMappingData(editing.id, objType, 5, formState);
      setPreviewData(res.data);
      if (!res.data.success) toast.error(t('rdapMappings.previewFailedWithError', { error: res.data.error || t('common.unknown') }));
    } catch (error) {
      toast.error(t('rdapMappings.previewFailed'));
    } finally {
      setPreviewLoading(false);
    }
  };

  // ==================== Helpers ====================

  const updateColumnMapping = (objType, field, value) => {
    const mappingField = OBJECT_TYPES.find(o => o.key === objType)?.mappingField;
    if (!mappingField) return;
    setForm(prev => ({ ...prev, [mappingField]: { ...prev[mappingField], [field]: value } }));
  };

  const getColumnsForType = (type) => {
    if (!defaults) return [];
    const key = type + 'Columns';
    return defaults[key] || [];
  };

  const getCurrentMappings = (type) => {
    const mappingField = OBJECT_TYPES.find(o => o.key === type)?.mappingField;
    return form[mappingField] || {};
  };

  const getTableField = (type) => OBJECT_TYPES.find(o => o.key === type)?.tableField;
  const getDefaultTable = (type) => OBJECT_TYPES.find(o => o.key === type)?.defaultTable;

  const getDiscoveredTablesForDropdown = () => {
    if (!discoveredSchema?.tables) return [];
    return discoveredSchema.tables.map(t => t.name);
  };

  const getDiscoveredColumnsForTable = (tableName) => {
    if (!discoveredSchema?.tables) return [];
    const table = discoveredSchema.tables.find(t => t.name === tableName);
    return table?.columns || [];
  };

  const getDiscoveredForeignKeys = (tableName) => {
    if (!discoveredSchema?.tables || !tableName) return { foreignKeys: [], referencedBy: [] };
    const table = discoveredSchema.tables.find(t => t.name === tableName);
    return {
      foreignKeys: table?.foreignKeys || [],
      referencedBy: table?.referencedBy || [],
    };
  };

  const activeMappings = mappings.filter(m => m.isActive);
  const activeMappingExists = activeMappings.length > 0;
  const activeMappingCount = activeMappings.length;

  if (loading) return <Loading message={t('rdapMappings.loadingMappings')} />;

  // ==================== EDITOR STEPS ====================

  const STEPS = [
    { key: 'basic', label: t('rdapMappings.steps.basicInfo') },
    { key: 'connection', label: t('rdapMappings.steps.database') },
    { key: 'tables', label: t('rdapMappings.steps.tables') },
    { key: 'columns', label: t('rdapMappings.steps.columns') },
    { key: 'joins', label: t('rdapMappings.steps.joins') },
    { key: 'customTables', label: t('rdapMappings.steps.customTables') },
    { key: 'preview', label: t('rdapMappings.steps.preview') },
  ];

  const renderStepBasic = () => (
    <div style={{ display: 'grid', gap: 16 }}>
      <div className="mb-3">
        <label className="form-label">{t('rdapMappings.basic.nameLabel')}</label>
        <input type="text" className="form-control" value={form.name}
          onChange={(e) => setForm(p => ({ ...p, name: e.target.value }))}
          placeholder={t('rdapMappings.basic.namePlaceholder')} />
      </div>
      <div className="mb-3">
        <label className="form-label">{t('common.description')}</label>
        <input type="text" className="form-control" value={form.description}
          onChange={(e) => setForm(p => ({ ...p, description: e.target.value }))}
          placeholder="e.g. Maps the registry MariaDB schema (dn/contact/host) to our RDAP fields" />
      </div>
    </div>
  );

  const renderStepConnection = () => (
    <div style={{ display: 'grid', gap: 16 }}>
      <div className="mb-3">
        <label className="form-label">{t('rdapMappings.connection.databaseTypeLabel')}</label>
        <div style={{ display: 'flex', gap: 12 }}>
          {['LOCAL', 'EXTERNAL'].map(dbTypeOpt => (
            <button key={dbTypeOpt} className={`btn ${form.dbType === dbTypeOpt ? 'btn-primary' : 'btn-secondary'}`}
              style={{ flex: 1, padding: '10px 16px' }}
              onClick={() => setForm(p => ({ ...p, dbType: dbTypeOpt }))}>
              {dbTypeOpt === 'LOCAL' ? t('rdapMappings.connection.sameDatabase') : t('rdapMappings.connection.externalDatabase')}
            </button>
          ))}
        </div>
        <p className="text-muted small" style={{ marginTop: 6 }}>
          {form.dbType === 'LOCAL'
            ? t('rdapMappings.connection.localHelp')
            : t('rdapMappings.connection.externalHelp')}
        </p>
      </div>

      {/* Column name case sensitivity */}
      <div className="mb-3">
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
          <input type="checkbox" checked={form.columnNameCaseSensitive || false}
            onChange={(e) => setForm(p => ({ ...p, columnNameCaseSensitive: e.target.checked }))} />
          <span style={{ fontWeight: 500, fontSize: 13 }}>{t('rdapMappings.connection.caseSensitiveLabel')}</span>
        </label>
        <p className="text-muted small" style={{ marginTop: 4, marginLeft: 26 }}>
          {t('rdapMappings.connection.caseSensitiveHelp')}
        </p>
      </div>

      {form.dbType === 'EXTERNAL' && (
        <>
          <div className="mb-3">
            <label className="form-label">{t('rdapMappings.connection.jdbcUrlLabel')}</label>
            <input type="text" className="form-control" value={form.externalJdbcUrl}
              onChange={(e) => setForm(p => ({ ...p, externalJdbcUrl: e.target.value }))}
              placeholder={t('rdapMappings.connection.jdbcUrlPlaceholder')} />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div className="mb-3">
              <label className="form-label">{t('common.username')}</label>
              <input type="text" className="form-control" value={form.externalDbUsername}
                onChange={(e) => setForm(p => ({ ...p, externalDbUsername: e.target.value }))}
                placeholder={t('rdapMappings.connection.usernamePlaceholder')} />
            </div>
            <div className="mb-3">
              <label className="form-label">{t('common.password')}</label>
              <input type="password" className="form-control" value={form.externalDbPassword}
                onChange={(e) => setForm(p => ({ ...p, externalDbPassword: e.target.value }))}
                placeholder="••••••••" />
            </div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div className="mb-3">
              <label className="form-label">{t('rdapMappings.connection.jdbcDriverLabel')}</label>
              <input type="text" className="form-control" value={form.externalDbDriver}
                onChange={(e) => setForm(p => ({ ...p, externalDbDriver: e.target.value }))}
                placeholder={t('rdapMappings.connection.jdbcDriverPlaceholder')} />
            </div>
            <div className="mb-3">
              <label className="form-label">{t('rdapMappings.connection.schemaFilterLabel')}</label>
              <input type="text" className="form-control" value={form.externalDbSchema}
                onChange={(e) => setForm(p => ({ ...p, externalDbSchema: e.target.value }))}
                placeholder={t('rdapMappings.connection.schemaFilterPlaceholder')} />
            </div>
          </div>

          <div style={{ display: 'flex', gap: 12 }}>
            <button className="btn btn-secondary" onClick={handleTestConnection} disabled={connTesting}>
              {connTesting ? t('rdapMappings.connection.testing') : t('rdapMappings.connection.testConnection')}
            </button>
            <button className="btn btn-primary" onClick={handleIntrospect} disabled={introspecting}>
              {introspecting ? t('rdapMappings.connection.discovering') : t('rdapMappings.connection.discoverSchema')}
            </button>
          </div>

          {connResult && (
            <div style={{
              padding: '12px 16px', borderRadius: 8,
              background: connResult.success ? 'rgba(16,185,129,0.08)' : 'rgba(239,68,68,0.08)',
              border: `1px solid ${connResult.success ? 'rgba(16,185,129,0.3)' : 'rgba(239,68,68,0.3)'}`,
              fontSize: 13,
            }}>
              {connResult.success ? (
                <div>
                  <strong style={{ color: 'var(--accent-success)' }}>{t('rdapMappings.connection.connected')}</strong>
                  <span className="text-muted"> — {connResult.productName} {connResult.productVersion}</span>
                </div>
              ) : (
                <div style={{ color: 'var(--accent-danger)' }}>{t('rdapMappings.connection.failedWithError', { error: connResult.error })}</div>
              )}
            </div>
          )}
        </>
      )}

      {form.dbType === 'LOCAL' && (
        <div style={{ display: 'flex', gap: 12 }}>
          <button className="btn btn-primary" onClick={handleIntrospect} disabled={introspecting}>
            {introspecting ? t('rdapMappings.connection.discovering') : t('rdapMappings.connection.discoverLocalSchema')}
          </button>
        </div>
      )}

      {/* Discovered schema summary */}
      {discoveredSchema?.tables && (
        <div style={{
          padding: 16, borderRadius: 8,
          background: 'var(--bg-tertiary)', border: '1px solid var(--border-primary)',
        }}>
          <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 10 }}>
            {t('rdapMappings.connection.discoveredSchemaHeader', { count: discoveredSchema.tables.length })}
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {discoveredSchema.tables.map(dTable => (
              <span key={dTable.name} style={{
                padding: '4px 10px', borderRadius: 6, fontSize: 12, fontWeight: 500,
                background: 'var(--bg-primary)', border: '1px solid var(--border-primary)',
              }}>
                {dTable.name}
                <span className="text-muted"> ({t('rdapMappings.connection.colsCount', { count: dTable.columns?.length || 0 })}{dTable.rowCount >= 0 ? t('rdapMappings.connection.rowsCount', { count: dTable.rowCount }) : ''})</span>
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );

  const renderStepTables = () => (
    <div style={{ display: 'grid', gap: 16 }}>
      <p className="text-muted small">
        {t('rdapMappings.tables.introText')}
        {discoveredSchema?.tables ? t('rdapMappings.tables.selectHint') : t('rdapMappings.tables.typeHint')}
      </p>

      {OBJECT_TYPES.map(ot => {
        const tableField = ot.tableField;
        const discovered = getDiscoveredTablesForDropdown();
        const selectedTable = form[tableField] || ot.defaultTable;
        const { foreignKeys, referencedBy } = getDiscoveredForeignKeys(selectedTable);
        const hasFkInfo = foreignKeys.length > 0 || referencedBy.length > 0;

        return (
          <div key={ot.key} style={{
            border: '1px solid var(--border-primary)', borderRadius: 'var(--radius-md)',
            padding: '14px 16px', background: 'var(--bg-primary)',
          }}>
            <div className="mb-3" style={{ marginBottom: hasFkInfo ? 10 : 0 }}>
              <label className="form-label" style={{ margin: '0 0 6px 0' }}>{t('rdapMappings.tables.tableLabelFor', { type: getObjectTypeLabel(ot.key) })}</label>
              {discovered.length > 0 ? (
                <div style={{ display: 'flex', gap: 8 }}>
                  <select className="form-select" style={{ flex: 1 }}
                    value={form[tableField] || ''}
                    onChange={(e) => setForm(p => ({ ...p, [tableField]: e.target.value }))}>
                    <option value="">{t('rdapMappings.tables.defaultOption', { table: ot.defaultTable })}</option>
                    {discovered.map(tbl => <option key={tbl} value={tbl}>{tbl}</option>)}
                  </select>
                </div>
              ) : (
                <input type="text" className="form-control"
                  value={form[tableField] || ''}
                  onChange={(e) => setForm(p => ({ ...p, [tableField]: e.target.value }))}
                  placeholder={t('rdapMappings.tables.defaultPlaceholder', { table: ot.defaultTable })} />
              )}
            </div>

            {/* Detected FK relationships */}
            {hasFkInfo && (
              <div style={{
                fontSize: 11, color: 'var(--text-tertiary)',
                display: 'flex', flexDirection: 'column', gap: 4,
                padding: '8px 10px', borderRadius: 6,
                background: 'var(--bg-tertiary)', border: '1px solid var(--border-primary)',
              }}>
                {foreignKeys.length > 0 && (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px 12px', alignItems: 'center' }}>
                    <span style={{ fontWeight: 600, color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
                      <i className="fa-solid fa-arrow-right" style={{ fontSize: 9, marginRight: 4, opacity: 0.5 }} />
                      {t('rdapMappings.tables.references')}
                    </span>
                    {foreignKeys.map((fk, i) => (
                      <span key={i} title={fk.inferred ? t('rdapMappings.tables.inferredTitle') : t('rdapMappings.tables.declaredTitle')}
                        style={{
                        display: 'inline-flex', alignItems: 'center', gap: 3,
                        padding: '1px 6px', borderRadius: 4,
                        background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.15)',
                        borderStyle: fk.inferred ? 'dashed' : 'solid',
                      }}>
                        <code style={{ fontSize: 10, color: 'var(--accent-primary)' }}>{fk.fkColumn}</code>
                        <span style={{ opacity: 0.5 }}>→</span>
                        <code style={{ fontSize: 10 }}>{fk.pkTable}</code>
                        <span style={{ opacity: 0.4 }}>.</span>
                        <code style={{ fontSize: 10 }}>{fk.pkColumn}</code>
                        {fk.inferred && <i className="fa-solid fa-wand-magic-sparkles" style={{ fontSize: 8, opacity: 0.4, marginLeft: 2 }} />}
                      </span>
                    ))}
                  </div>
                )}
                {referencedBy.length > 0 && (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px 12px', alignItems: 'center' }}>
                    <span style={{ fontWeight: 600, color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
                      <i className="fa-solid fa-arrow-left" style={{ fontSize: 9, marginRight: 4, opacity: 0.5 }} />
                      {t('rdapMappings.tables.referencedBy')}
                    </span>
                    {referencedBy.map((ref, i) => (
                      <span key={i} title={ref.inferred ? t('rdapMappings.tables.inferredTitle') : t('rdapMappings.tables.declaredTitle')}
                        style={{
                        display: 'inline-flex', alignItems: 'center', gap: 3,
                        padding: '1px 6px', borderRadius: 4,
                        background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.15)',
                        borderStyle: ref.inferred ? 'dashed' : 'solid',
                      }}>
                        <code style={{ fontSize: 10 }}>{ref.fkTable}</code>
                        <span style={{ opacity: 0.4 }}>.</span>
                        <code style={{ fontSize: 10 }}>{ref.fkColumn}</code>
                        <span style={{ opacity: 0.5 }}>→</span>
                        <code style={{ fontSize: 10, color: 'var(--accent-success)' }}>{ref.pkColumn}</code>
                        {ref.inferred && <i className="fa-solid fa-wand-magic-sparkles" style={{ fontSize: 8, opacity: 0.4, marginLeft: 2 }} />}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );

  const renderStepColumns = () => {
    const currentTableField = getTableField(activeTab);
    const currentTable = form[currentTableField];
    const discoveredCols = getDiscoveredColumnsForTable(currentTable);
    const defaultCols = getColumnsForType(activeTab);
    const currentMappings = getCurrentMappings(activeTab);

    return (
      <div style={{ display: 'grid', gap: 16 }}>
        {/* Tab bar */}
        <div style={{ borderBottom: '1px solid var(--border-primary)', display: 'flex', gap: 0, overflowX: 'auto' }}>
          {OBJECT_TYPES.map(ot => (
            <button key={ot.key}
              onClick={() => { setActiveTab(ot.key); setPreviewData(null); }}
              style={{
                padding: '10px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer',
                border: 'none', background: 'none', whiteSpace: 'nowrap',
                borderBottom: activeTab === ot.key ? '2px solid var(--accent-primary)' : '2px solid transparent',
                color: activeTab === ot.key ? 'var(--accent-primary)' : 'var(--text-tertiary)',
                transition: 'all 0.15s ease',
              }}>
              {getObjectTypeLabel(ot.key)}
              {Object.keys(getCurrentMappings(ot.key)).length > 0 && (
                <span style={{
                  marginLeft: 6, fontSize: 10, padding: '1px 6px', borderRadius: 10,
                  background: 'rgba(99,102,241,0.12)', color: 'var(--accent-primary)',
                }}>
                  {Object.keys(getCurrentMappings(ot.key)).length}
                </span>
              )}
            </button>
          ))}
        </div>

        {/* Table info */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div className="small text-muted">
            {t('rdapMappings.columns.tableLabel')} <code style={{ color: 'var(--accent-primary)' }}>{currentTable || getDefaultTable(activeTab)}</code>
            {discoveredCols.length > 0 && <span>{t('rdapMappings.columns.columnsDiscovered', { count: discoveredCols.length })}</span>}
          </div>
          {discoveredCols.length > 0 && (
            <button className="btn btn-secondary" style={{ padding: '4px 12px', fontSize: 12 }}
              onClick={() => handleAutoSuggest(activeTab)}>
              {t('rdapMappings.columns.autoSuggestButton')}
            </button>
          )}
        </div>

        {/* Mapping grid */}
        <div style={{
          border: '1px solid var(--border-primary)', borderRadius: 'var(--radius-md)', overflow: 'hidden',
        }}>
          {/* Header */}
          <div style={{
            display: 'grid', gridTemplateColumns: '1fr 2fr',
            background: 'var(--bg-tertiary)', padding: '10px 16px',
            fontSize: 12, fontWeight: 600, textTransform: 'uppercase',
            letterSpacing: 0.5, color: 'var(--text-tertiary)',
          }}>
            <div>{t('rdapMappings.columns.rdapFieldHeader')}</div>
            <div>{t('rdapMappings.columns.mappedColumnHeader')} {discoveredCols.length > 0 ? t('rdapMappings.columns.fromDiscoveredSchema') : ''} <span style={{ fontWeight: 400, textTransform: 'none', letterSpacing: 0, opacity: 0.7 }}>{t('rdapMappings.columns.unmappedOmitted')}</span></div>
          </div>

          {defaultCols.map((col, i) => {
            const currentOverride = currentMappings[col.field] || '';
            return (
              <div key={col.field} style={{
                display: 'grid', gridTemplateColumns: '1fr 2fr',
                padding: '8px 16px', alignItems: 'center',
                borderTop: '1px solid var(--border-primary)',
                background: i % 2 === 0 ? 'transparent' : 'var(--bg-tertiary)',
              }}>
                <div>
                  <code style={{ fontSize: 12, color: 'var(--accent-primary)' }}>{col.field}</code>
                  <div className="text-muted" style={{ fontSize: 11 }}>{col.description}</div>
                </div>
                <div>
                  {discoveredCols.length > 0 ? (
                    <select className="form-select" style={{ padding: '6px 10px', fontSize: 13 }}
                      value={currentOverride}
                      onChange={(e) => updateColumnMapping(activeTab, col.field, e.target.value)}>
                      <option value="">{t('rdapMappings.columns.notMappedOmitted')}</option>
                      {discoveredCols.map(dc => (
                        <option key={dc.name} value={dc.name}>
                          {dc.name} ({dc.typeDisplay || dc.type})
                        </option>
                      ))}
                    </select>
                  ) : (
                    <input type="text" className="form-control"
                      style={{ padding: '6px 10px', fontSize: 13 }}
                      value={currentOverride}
                      onChange={(e) => updateColumnMapping(activeTab, col.field, e.target.value)}
                      placeholder={t('rdapMappings.notMapped')} />
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    );
  };

  const renderStepJoins = () => {
    const domainTable = form.domainsTable || 'rdap_domains';
    const contactTable = form.contactsTable || 'contact';
    const hostTable = form.hostsTable || 'host';
    const domainCols = getDiscoveredColumnsForTable(form.domainsTable);
    const contactCols = getDiscoveredColumnsForTable(form.contactsTable);

    const CONTACT_ROLES = defaults?.contactRoles || [
      { role: 'registrant', description: t('rdapMappings.joins.roleDescriptions.registrant') },
      { role: 'admin', description: t('rdapMappings.joins.roleDescriptions.admin') },
      { role: 'tech', description: t('rdapMappings.joins.roleDescriptions.tech') },
      { role: 'billing', description: t('rdapMappings.joins.roleDescriptions.billing') },
    ];

    return (
      <div style={{ display: 'grid', gap: 20 }}>
        <div>
          <h4 style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>{t('rdapMappings.joins.domainContactHeading')}</h4>
          <p className="text-muted small" style={{ marginBottom: 12 }}>
            {t('rdapMappings.joins.mapRoleIntro')} <code>{domainTable}</code> {t('rdapMappings.joins.mapRoleMiddle')} <code>{contactTable}</code>.
          </p>

          {/* Contact join key */}
          <div className="mb-3" style={{ marginBottom: 16 }}>
            <label className="form-label">{t('rdapMappings.joins.contactJoinKeyLabel')} <code>{contactTable}</code>)</label>
            {contactCols.length > 0 ? (
              <select className="form-select" value={form.contactJoinKey || 'id'}
                onChange={(e) => setForm(p => ({ ...p, contactJoinKey: e.target.value }))}>
                {contactCols.map(c => <option key={c.name} value={c.name}>{c.name}</option>)}
              </select>
            ) : (
              <input type="text" className="form-control" value={form.contactJoinKey || 'id'}
                onChange={(e) => setForm(p => ({ ...p, contactJoinKey: e.target.value }))}
                placeholder={t('rdapMappings.joins.idPlaceholder')} />
            )}
          </div>

          {/* Role → FK column mapping */}
          <div style={{
            border: '1px solid var(--border-primary)', borderRadius: 'var(--radius-md)', overflow: 'hidden',
          }}>
            <div style={{
              display: 'grid', gridTemplateColumns: '1fr 2fr',
              background: 'var(--bg-tertiary)', padding: '10px 16px',
              fontSize: 12, fontWeight: 600, textTransform: 'uppercase',
              letterSpacing: 0.5, color: 'var(--text-tertiary)',
            }}>
              <div>{t('rdapMappings.joins.contactRoleHeader')}</div>
              <div>{t('rdapMappings.joins.fkColumnIn', { table: domainTable })}</div>
            </div>

            {CONTACT_ROLES.map((cr, i) => (
              <div key={cr.role} style={{
                display: 'grid', gridTemplateColumns: '1fr 2fr',
                padding: '8px 16px', alignItems: 'center',
                borderTop: '1px solid var(--border-primary)',
                background: i % 2 === 0 ? 'transparent' : 'var(--bg-tertiary)',
              }}>
                <div>
                  <span style={{ fontWeight: 600, fontSize: 13 }}>{cr.role}</span>
                  <div className="text-muted" style={{ fontSize: 11 }}>{cr.description}</div>
                </div>
                <div>
                  {domainCols.length > 0 ? (
                    <select className="form-select" style={{ padding: '6px 10px', fontSize: 13 }}
                      value={form.domainContactJoinMappings?.[cr.role] || ''}
                      onChange={(e) => setForm(p => ({
                        ...p,
                        domainContactJoinMappings: { ...p.domainContactJoinMappings, [cr.role]: e.target.value }
                      }))}>
                      <option value="">{t('rdapMappings.notMapped')}</option>
                      {domainCols.map(c => <option key={c.name} value={c.name}>{c.name}</option>)}
                    </select>
                  ) : (
                    <input type="text" className="form-control"
                      style={{ padding: '6px 10px', fontSize: 13 }}
                      value={form.domainContactJoinMappings?.[cr.role] || ''}
                      onChange={(e) => setForm(p => ({
                        ...p,
                        domainContactJoinMappings: { ...p.domainContactJoinMappings, [cr.role]: e.target.value }
                      }))}
                      placeholder={t('rdapMappings.joins.fkPlaceholderExample', { prefix: cr.role.charAt(0) })} />
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Host join config */}
        <div>
          <h4 style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>{t('rdapMappings.joins.domainHostHeading')}</h4>
          <p className="text-muted small" style={{ marginBottom: 12 }}>
            {t('rdapMappings.joins.hostJoinIntroPrefix')} <code>{domainTable}</code> {t('rdapMappings.joins.hostJoinIntroMiddle')} <code>{hostTable}</code> {t('rdapMappings.joins.hostJoinIntroSuffix')}
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12 }}>
            <div className="mb-3">
              <label className="form-label">{t('rdapMappings.joins.domainColumnLabel')}</label>
              {domainCols.length > 0 ? (
                <select className="form-select" value={form.hostJoinConfig?.domainColumn || ''}
                  onChange={(e) => setForm(p => ({
                    ...p, hostJoinConfig: { ...p.hostJoinConfig, domainColumn: e.target.value }
                  }))}>
                  <option value="">{t('rdapMappings.selectPlaceholder')}</option>
                  {domainCols.map(c => <option key={c.name} value={c.name}>{c.name}</option>)}
                </select>
              ) : (
                <input type="text" className="form-control"
                  value={form.hostJoinConfig?.domainColumn || ''}
                  onChange={(e) => setForm(p => ({
                    ...p, hostJoinConfig: { ...p.hostJoinConfig, domainColumn: e.target.value }
                  }))}
                  placeholder={t('rdapMappings.joins.domainColumnPlaceholder')} />
              )}
            </div>
            <div className="mb-3">
              <label className="form-label">{t('rdapMappings.joins.hostJoinColumnLabel')}</label>
              <input type="text" className="form-control"
                value={form.hostJoinConfig?.hostColumn || ''}
                onChange={(e) => setForm(p => ({
                  ...p, hostJoinConfig: { ...p.hostJoinConfig, hostColumn: e.target.value }
                }))}
                placeholder={t('rdapMappings.joins.hostJoinColumnPlaceholder')} />
            </div>
            <div className="mb-3">
              <label className="form-label">{t('rdapMappings.joins.delimiterLabel')}</label>
              <input type="text" className="form-control"
                value={form.hostJoinConfig?.delimiter || ''}
                onChange={(e) => setForm(p => ({
                  ...p, hostJoinConfig: { ...p.hostJoinConfig, delimiter: e.target.value }
                }))}
                placeholder={t('rdapMappings.joins.delimiterPlaceholder')} />
            </div>
          </div>
        </div>

        {/* Domain name composition */}
        <div>
          <h4 style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>{t('rdapMappings.joins.domainNameCompositionHeading')}</h4>
          <p className="text-muted small" style={{ marginBottom: 12 }}>
            {t('rdapMappings.joins.domainNameCompositionIntro1')} <code>d_name</code> {t('rdapMappings.joins.domainNameCompositionIntro2')} <code>sld</code> {t('rdapMappings.joins.domainNameCompositionIntro3')} <strong>name + separator + suffix</strong> {t('rdapMappings.joins.domainNameCompositionIntro4')}
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 12 }}>
            <div className="mb-3">
              <label className="form-label">{t('rdapMappings.joins.suffixColumnLabel')}</label>
              {domainCols.length > 0 ? (
                <select className="form-select" value={form.domainNameSuffixColumn || ''}
                  onChange={(e) => setForm(p => ({ ...p, domainNameSuffixColumn: e.target.value }))}>
                  <option value="">{t('rdapMappings.joins.noneFullDomainOption')}</option>
                  {domainCols.map(c => <option key={c.name} value={c.name}>{c.name}</option>)}
                </select>
              ) : (
                <input type="text" className="form-control"
                  value={form.domainNameSuffixColumn || ''}
                  onChange={(e) => setForm(p => ({ ...p, domainNameSuffixColumn: e.target.value }))}
                  placeholder={t('rdapMappings.joins.suffixColumnPlaceholder')} />
              )}
            </div>
            <div className="mb-3">
              <label className="form-label">{t('rdapMappings.joins.separatorLabel')}</label>
              <input type="text" className="form-control"
                value={form.domainNameSeparator || '.'}
                onChange={(e) => setForm(p => ({ ...p, domainNameSeparator: e.target.value }))}
                placeholder={t('rdapMappings.joins.separatorPlaceholder')} />
              <p className="text-muted" style={{ fontSize: 11, marginTop: 2 }}>{t('rdapMappings.joins.usuallyDot')}</p>
            </div>
          </div>

          {form.domainNameSuffixColumn && (
            <div style={{
              marginTop: 8, padding: '8px 12px', borderRadius: 8,
              background: 'rgba(99,102,241,0.06)', border: '1px solid rgba(99,102,241,0.15)',
              fontSize: 12,
            }}>
              <strong>{t('rdapMappings.joins.previewColonLabel')}</strong>{' '}
              <code>{form.domainColumnMappings?.ldhName || 'd_name'}</code>
              {' '}<span style={{ color: 'var(--accent-primary)', fontWeight: 700 }}>{form.domainNameSeparator || '.'}</span>{' '}
              <code>{form.domainNameSuffixColumn}</code>
              {t('rdapMappings.joins.exampleArrow')}
              <code style={{ color: 'var(--accent-primary)' }}>example{form.domainNameSeparator || '.'}tw</code>
            </div>
          )}
        </div>
      </div>
    );
  };

  // ==================== CUSTOM TABLE HELPERS ====================

  const addCustomTable = () => {
    setForm(prev => ({
      ...prev,
      customTableMappings: [
        ...(prev.customTableMappings || []),
        { tableName: '', label: '', description: '', actsAs: '', columnMappings: [] },
      ],
    }));
  };

  const removeCustomTable = (index) => {
    setForm(prev => ({
      ...prev,
      customTableMappings: prev.customTableMappings.filter((_, i) => i !== index),
    }));
  };

  const updateCustomTable = (index, field, value) => {
    setForm(prev => {
      const updated = [...(prev.customTableMappings || [])];
      updated[index] = { ...updated[index], [field]: value };
      return { ...prev, customTableMappings: updated };
    });
  };

  const addCustomColumnMapping = (tableIndex) => {
    setForm(prev => {
      const updated = [...(prev.customTableMappings || [])];
      const table = { ...updated[tableIndex] };
      table.columnMappings = [
        ...(table.columnMappings || []),
        { sourceColumn: '', mappingType: 'custom', targetType: '', targetField: '', customAlias: '', customDescription: '' },
      ];
      updated[tableIndex] = table;
      return { ...prev, customTableMappings: updated };
    });
  };

  const removeCustomColumnMapping = (tableIndex, colIndex) => {
    setForm(prev => {
      const updated = [...(prev.customTableMappings || [])];
      const table = { ...updated[tableIndex] };
      table.columnMappings = table.columnMappings.filter((_, i) => i !== colIndex);
      updated[tableIndex] = table;
      return { ...prev, customTableMappings: updated };
    });
  };

  const updateCustomColumnMapping = (tableIndex, colIndex, field, value) => {
    setForm(prev => {
      const updated = [...(prev.customTableMappings || [])];
      const table = { ...updated[tableIndex] };
      table.columnMappings = [...table.columnMappings];
      table.columnMappings[colIndex] = { ...table.columnMappings[colIndex], [field]: value };
      // Clear target fields when switching mapping type
      if (field === 'mappingType') {
        if (value === 'custom') {
          table.columnMappings[colIndex].targetType = '';
          table.columnMappings[colIndex].targetField = '';
        } else {
          table.columnMappings[colIndex].customAlias = '';
          table.columnMappings[colIndex].customDescription = '';
        }
      }
      updated[tableIndex] = table;
      return { ...prev, customTableMappings: updated };
    });
  };

  const autoDetectCustomColumns = (tableIndex) => {
    const ct = form.customTableMappings[tableIndex];
    if (!ct?.tableName || !discoveredSchema?.tables) {
      toast.error(t('rdapMappings.selectTableAndDiscover'));
      return;
    }
    const table = discoveredSchema.tables.find(dt => dt.name === ct.tableName);
    if (!table?.columns) {
      toast.error(t('rdapMappings.tableNotFound', { table: ct.tableName }));
      return;
    }
    const existingSourceCols = new Set((ct.columnMappings || []).map(cm => cm.sourceColumn));
    const newMappings = table.columns
      .filter(c => !existingSourceCols.has(c.name))
      .map(c => ({
        sourceColumn: c.name,
        mappingType: 'custom',
        targetType: '',
        targetField: '',
        customAlias: c.name.toLowerCase().replace(/[^a-z0-9]/g, '_'),
        customDescription: `${c.typeDisplay || c.type}`,
      }));

    if (newMappings.length === 0) {
      toast.success(t('rdapMappings.allColumnsMapped'));
      return;
    }

    setForm(prev => {
      const updated = [...(prev.customTableMappings || [])];
      const tbl = { ...updated[tableIndex] };
      tbl.columnMappings = [...(tbl.columnMappings || []), ...newMappings];
      updated[tableIndex] = tbl;
      return { ...prev, customTableMappings: updated };
    });
    toast.success(t('rdapMappings.addedColumnMappings', { count: newMappings.length, s: newMappings.length !== 1 ? 's' : '' }));
  };

  const renderStepCustomTables = () => {
    const customTables = form.customTableMappings || [];
    const discovered = getDiscoveredTablesForDropdown();
    // Tables already used by standard RDAP types
    const usedTables = new Set(OBJECT_TYPES.map(ot => form[ot.tableField]).filter(Boolean));

    return (
      <div style={{ display: 'grid', gap: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
          <div>
            <p className="text-muted small" style={{ margin: 0 }}>
              Add custom tables that don't map to standard RDAP object types. For each column, choose to map it
              to a standard RDAP field (e.g. map <code>CMP_Ename</code> → Contact's <code>organization</code>) or
              define it as a custom named value with your own alias.
            </p>
          </div>
          <button className="btn btn-primary" onClick={addCustomTable}
            style={{ whiteSpace: 'nowrap', padding: '8px 16px', fontSize: 13 }}>
            + Add Table
          </button>
        </div>

        {customTables.length === 0 ? (
          <div style={{
            padding: 40, textAlign: 'center', borderRadius: 'var(--radius-md)',
            border: '2px dashed var(--border-primary)', background: 'var(--bg-tertiary)',
          }}>
            <div style={{ fontSize: 28, marginBottom: 8, opacity: 0.4 }}>📋</div>
            <div className="text-muted small">
              No custom tables configured. Click <strong>+ Add Table</strong> to map additional tables.
            </div>
          </div>
        ) : (
          <div style={{ display: 'grid', gap: 12 }}>
            {customTables.map((ct, tableIndex) => {
              const isExpanded = expandedCustomTable === tableIndex;
              const discoveredCols = getDiscoveredColumnsForTable(ct.tableName);
              const mappingCount = (ct.columnMappings || []).length;
              const standardCount = (ct.columnMappings || []).filter(cm => cm.mappingType === 'standard').length;
              const customCount = mappingCount - standardCount;

              return (
                <div key={tableIndex} style={{
                  border: '1px solid var(--border-primary)', borderRadius: 'var(--radius-md)',
                  overflow: 'hidden',
                  background: isExpanded ? 'var(--bg-primary)' : 'transparent',
                }}>
                  {/* Table header row */}
                  <div style={{
                    padding: '12px 16px',
                    display: 'flex', alignItems: 'center', gap: 12,
                    background: 'var(--bg-tertiary)',
                    cursor: 'pointer',
                  }}
                    onClick={() => setExpandedCustomTable(isExpanded ? null : tableIndex)}>
                    <span style={{
                      display: 'inline-flex', width: 20, height: 20, alignItems: 'center', justifyContent: 'center',
                      fontSize: 12, transition: 'transform 0.15s', transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)',
                    }}>▶</span>
                    <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 10 }}>
                      <strong style={{ fontSize: 14 }}>
                        {ct.label || ct.tableName || `Custom Table #${tableIndex + 1}`}
                      </strong>
                      {ct.tableName && (
                        <code style={{ fontSize: 11, color: 'var(--text-tertiary)' }}>{ct.tableName}</code>
                      )}
                      {ct.actsAs && (
                        <span style={{
                          fontSize: 10, padding: '2px 8px', borderRadius: 10,
                          background: 'rgba(16,185,129,0.12)', color: 'var(--accent-success)', fontWeight: 600,
                        }}>
                          acts as {OBJECT_TYPES.find(ot => ot.key === ct.actsAs)?.label || ct.actsAs}
                        </span>
                      )}
                      {mappingCount > 0 && (
                        <span style={{
                          fontSize: 10, padding: '2px 8px', borderRadius: 10,
                          background: 'rgba(99,102,241,0.12)', color: 'var(--accent-primary)', fontWeight: 600,
                        }}>
                          {mappingCount} col{mappingCount !== 1 ? 's' : ''}
                          {standardCount > 0 && <span> · {standardCount} std</span>}
                          {customCount > 0 && <span> · {customCount} custom</span>}
                        </span>
                      )}
                    </div>
                    <button className="btn btn-secondary"
                      style={{ padding: '4px 10px', fontSize: 12, color: 'var(--accent-danger)' }}
                      onClick={(e) => { e.stopPropagation(); removeCustomTable(tableIndex); }}>
                      Remove
                    </button>
                  </div>

                  {/* Expanded content */}
                  {isExpanded && (
                    <div style={{ padding: 16, display: 'grid', gap: 16 }}>
                      {/* Table identity */}
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                        <div className="mb-3">
                          <label className="form-label">Table Name *</label>
                          {discovered.length > 0 ? (
                            <select className="form-select" value={ct.tableName || ''}
                              onChange={(e) => updateCustomTable(tableIndex, 'tableName', e.target.value)}>
                              <option value="">— Select table —</option>
                              {discovered.filter(t => !usedTables.has(t) || t === ct.tableName).map(t => (
                                <option key={t} value={t}>{t}</option>
                              ))}
                            </select>
                          ) : (
                            <input type="text" className="form-control" value={ct.tableName || ''}
                              onChange={(e) => updateCustomTable(tableIndex, 'tableName', e.target.value)}
                              placeholder="e.g. greendn" />
                          )}
                        </div>
                        <div className="mb-3">
                          <label className="form-label">Display Label</label>
                          <input type="text" className="form-control" value={ct.label || ''}
                            onChange={(e) => updateCustomTable(tableIndex, 'label', e.target.value)}
                            placeholder="e.g. Green Domains" />
                        </div>
                      </div>
                      <div className="mb-3">
                        <label className="form-label">Description</label>
                        <input type="text" className="form-control" value={ct.description || ''}
                          onChange={(e) => updateCustomTable(tableIndex, 'description', e.target.value)}
                          placeholder="e.g. Maps the greendn table for sustainability data" />
                      </div>

                      {/* Data Acting As */}
                      <div className="mb-3">
                        <label className="form-label">Data Acting As</label>
                        <select className="form-select" value={ct.actsAs || ''}
                          onChange={(e) => updateCustomTable(tableIndex, 'actsAs', e.target.value)}
                          style={{ maxWidth: 340 }}>
                          <option value="">Custom Table (no type mapping)</option>
                          {OBJECT_TYPES.map(ot => (
                            <option key={ot.key} value={ot.key}>{ot.label}</option>
                          ))}
                        </select>
                        <p className="text-muted small" style={{ margin: '4px 0 0 0' }}>
                          Tells the system this table's data should be treated as the selected RDAP type.
                          Leave blank to keep it as a standalone custom table.
                        </p>
                      </div>

                      {/* Join Configuration */}
                      {(() => {
                        const joinConfig = ct.joinConfig || {};
                        const joinConditions = joinConfig.joinConditions || [];
                        const primaryType = joinConfig.joinType || 'domain';
                        const primaryTableField = OBJECT_TYPES.find(ot => ot.key === primaryType)?.tableField;
                        const primaryTableName = form[primaryTableField] || OBJECT_TYPES.find(ot => ot.key === primaryType)?.defaultTable || '';
                        const primaryCols = getDiscoveredColumnsForTable(primaryTableName);

                        const updateJoinConfig = (field, value) => {
                          updateCustomTable(tableIndex, 'joinConfig', { ...joinConfig, [field]: value });
                        };

                        const addJoinCondition = () => {
                          updateJoinConfig('joinConditions', [
                            ...joinConditions,
                            { sourceColumn: '', targetColumns: [''], separator: '.' },
                          ]);
                        };

                        const removeJoinCondition = (ci) => {
                          updateJoinConfig('joinConditions', joinConditions.filter((_, i) => i !== ci));
                        };

                        const updateJoinCondition = (ci, field, value) => {
                          const updated = [...joinConditions];
                          updated[ci] = { ...updated[ci], [field]: value };
                          updateJoinConfig('joinConditions', updated);
                        };

                        const addTargetColumn = (ci) => {
                          const updated = [...joinConditions];
                          updated[ci] = { ...updated[ci], targetColumns: [...(updated[ci].targetColumns || []), ''] };
                          updateJoinConfig('joinConditions', updated);
                        };

                        const removeTargetColumn = (ci, ti) => {
                          const updated = [...joinConditions];
                          updated[ci] = { ...updated[ci], targetColumns: updated[ci].targetColumns.filter((_, i) => i !== ti) };
                          updateJoinConfig('joinConditions', updated);
                        };

                        const updateTargetColumn = (ci, ti, value) => {
                          const updated = [...joinConditions];
                          const cols = [...updated[ci].targetColumns];
                          cols[ti] = value;
                          updated[ci] = { ...updated[ci], targetColumns: cols };
                          updateJoinConfig('joinConditions', updated);
                        };

                        return (
                          <div style={{
                            padding: 16, borderRadius: 'var(--radius-md)',
                            border: '1px solid var(--border-primary)',
                            background: joinConfig.enabled ? 'rgba(16,185,129,0.04)' : 'var(--bg-tertiary)',
                          }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                              <h4 style={{ fontSize: 14, fontWeight: 600, margin: 0 }}>
                                🔗 Join Configuration
                              </h4>
                              <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, cursor: 'pointer' }}>
                                <input type="checkbox"
                                  checked={joinConfig.enabled || false}
                                  onChange={(e) => updateJoinConfig('enabled', e.target.checked)} />
                                <span style={{ fontWeight: 500 }}>Enable join</span>
                              </label>
                            </div>
                            <p className="text-muted small" style={{ margin: '0 0 12px 0' }}>
                              Define how this table connects to your primary RDAP data at query time.
                              Each condition pairs a column in this table with one or more columns from the
                              primary table, optionally concatenated with a separator.
                            </p>

                            {joinConfig.enabled && (
                              <div style={{ display: 'grid', gap: 12 }}>
                                {/* Join type */}
                                <div className="mb-3">
                                  <label className="form-label">Join To (RDAP object type)</label>
                                  <select className="form-select" style={{ maxWidth: 300 }}
                                    value={primaryType}
                                    onChange={(e) => updateJoinConfig('joinType', e.target.value)}>
                                    <option value="domain">Domain</option>
                                    <option value="contact">Contact</option>
                                    <option value="host">Host</option>
                                    <option value="ip">IP Network</option>
                                    <option value="asn">Autonomous System</option>
                                  </select>
                                </div>

                                {/* Join conditions list */}
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                  <label className="form-label" style={{ margin: 0 }}>
                                    Join Conditions
                                    <span className="text-muted" style={{ fontWeight: 400 }}> — all must match (AND)</span>
                                  </label>
                                  <button className="btn btn-secondary" style={{ padding: '4px 12px', fontSize: 12 }}
                                    onClick={addJoinCondition}>
                                    + Add Condition
                                  </button>
                                </div>

                                {joinConditions.length === 0 ? (
                                  <div style={{
                                    padding: 20, textAlign: 'center', borderRadius: 'var(--radius-md)',
                                    border: '1px dashed var(--border-primary)', background: 'var(--bg-tertiary)',
                                  }}>
                                    <div className="text-muted small">
                                      No join conditions defined. Add a condition to link this table to {primaryType} data.
                                    </div>
                                  </div>
                                ) : (
                                  <div style={{ display: 'grid', gap: 10 }}>
                                    {joinConditions.map((cond, ci) => (
                                      <div key={ci} style={{
                                        padding: 12, borderRadius: 8,
                                        border: '1px solid var(--border-primary)',
                                        background: 'var(--bg-primary)',
                                      }}>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                                          <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-tertiary)' }}>
                                            {ci > 0 ? 'AND' : 'WHERE'}
                                          </span>
                                          <button onClick={() => removeJoinCondition(ci)}
                                            style={{
                                              background: 'none', border: 'none', cursor: 'pointer', padding: 2,
                                              color: 'var(--accent-danger)', fontSize: 13, opacity: 0.6,
                                            }}
                                            title="Remove condition">✕</button>
                                        </div>
                                        <div style={{ display: 'grid', gridTemplateColumns: '1fr auto 1fr', gap: 8, alignItems: 'start' }}>
                                          {/* Source column (custom table) */}
                                          <div>
                                            <label className="form-label" style={{ fontSize: 11 }}>
                                              <code>{ct.tableName || '?'}</code> column
                                            </label>
                                            {discoveredCols.length > 0 ? (
                                              <select className="form-select" style={{ padding: '6px 8px', fontSize: 12 }}
                                                value={cond.sourceColumn || ''}
                                                onChange={(e) => updateJoinCondition(ci, 'sourceColumn', e.target.value)}>
                                                <option value="">— select —</option>
                                                {discoveredCols.map(dc => (
                                                  <option key={dc.name} value={dc.name}>{dc.name}</option>
                                                ))}
                                              </select>
                                            ) : (
                                              <input type="text" className="form-control"
                                                style={{ padding: '6px 8px', fontSize: 12 }}
                                                value={cond.sourceColumn || ''}
                                                onChange={(e) => updateJoinCondition(ci, 'sourceColumn', e.target.value)}
                                                placeholder="column_name" />
                                            )}
                                          </div>

                                          {/* Equals sign */}
                                          <div style={{ paddingTop: 22, fontSize: 16, fontWeight: 700, color: 'var(--text-tertiary)' }}>=</div>

                                          {/* Target columns (primary table) */}
                                          <div>
                                            <label className="form-label" style={{ fontSize: 11 }}>
                                              <code>{primaryTableName || '?'}</code> column(s)
                                              {(cond.targetColumns || []).length > 1 && (
                                                <span className="text-muted"> — concatenated</span>
                                              )}
                                            </label>
                                            <div style={{ display: 'grid', gap: 4 }}>
                                              {(cond.targetColumns || ['']).map((tc, ti) => (
                                                <div key={ti} style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                                                  {ti > 0 && (
                                                    <input type="text" className="form-control"
                                                      style={{ width: 32, padding: '6px 4px', fontSize: 11, textAlign: 'center', flex: 'none' }}
                                                      value={cond.separator || '.'}
                                                      onChange={(e) => updateJoinCondition(ci, 'separator', e.target.value)}
                                                      title="Separator between columns" />
                                                  )}
                                                  {primaryCols.length > 0 ? (
                                                    <select className="form-select" style={{ padding: '6px 8px', fontSize: 12, flex: 1 }}
                                                      value={tc || ''}
                                                      onChange={(e) => updateTargetColumn(ci, ti, e.target.value)}>
                                                      <option value="">— select —</option>
                                                      {primaryCols.map(dc => (
                                                        <option key={dc.name} value={dc.name}>{dc.name}</option>
                                                      ))}
                                                    </select>
                                                  ) : (
                                                    <input type="text" className="form-control"
                                                      style={{ padding: '6px 8px', fontSize: 12, flex: 1 }}
                                                      value={tc || ''}
                                                      onChange={(e) => updateTargetColumn(ci, ti, e.target.value)}
                                                      placeholder="column_name" />
                                                  )}
                                                  {(cond.targetColumns || []).length > 1 && (
                                                    <button onClick={() => removeTargetColumn(ci, ti)}
                                                      style={{
                                                        background: 'none', border: 'none', cursor: 'pointer', padding: 2,
                                                        color: 'var(--accent-danger)', fontSize: 12, opacity: 0.5, flex: 'none',
                                                      }}
                                                      title="Remove this column">✕</button>
                                                  )}
                                                </div>
                                              ))}
                                              <button className="btn btn-secondary"
                                                style={{ padding: '2px 8px', fontSize: 11, justifySelf: 'start' }}
                                                onClick={() => addTargetColumn(ci)}>
                                                + column
                                              </button>
                                            </div>
                                          </div>
                                        </div>
                                      </div>
                                    ))}
                                  </div>
                                )}

                                {/* Join preview */}
                                {joinConditions.length > 0 && joinConditions.some(c => c.sourceColumn && (c.targetColumns || []).some(Boolean)) && (
                                  <div style={{
                                    padding: '8px 12px', borderRadius: 8,
                                    background: 'rgba(16,185,129,0.06)', border: '1px solid rgba(16,185,129,0.2)',
                                    fontSize: 12,
                                  }}>
                                    <strong>Query Preview:</strong>{' '}
                                    <code>SELECT * FROM {ct.tableName || '?'} WHERE </code>
                                    {joinConditions.map((cond, ci) => {
                                      const targets = (cond.targetColumns || []).filter(Boolean);
                                      const composed = targets.length > 1
                                        ? targets.join(` + '${cond.separator || '.'}' + `)
                                        : targets[0] || '?';
                                      return (
                                        <span key={ci}>
                                          {ci > 0 && <code> AND </code>}
                                          <code>{cond.sourceColumn || '?'}</code>
                                          <code> = </code>
                                          <code style={{ color: 'var(--accent-primary)' }}>
                                            {targets.length > 1 ? `CONCAT(${composed})` : composed}
                                          </code>
                                        </span>
                                      );
                                    })}
                                  </div>
                                )}
                              </div>
                            )}
                          </div>
                        );
                      })()}

                      {/* Column mappings header */}
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <h4 style={{ fontSize: 14, fontWeight: 600, margin: 0 }}>Column Mappings</h4>
                        <div style={{ display: 'flex', gap: 8 }}>
                          {discoveredCols.length > 0 && (
                            <button className="btn btn-secondary" style={{ padding: '4px 12px', fontSize: 12 }}
                              onClick={() => autoDetectCustomColumns(tableIndex)}>
                              ✨ Auto-Detect Columns
                            </button>
                          )}
                          <button className="btn btn-secondary" style={{ padding: '4px 12px', fontSize: 12 }}
                            onClick={() => addCustomColumnMapping(tableIndex)}>
                            + Add Column
                          </button>
                        </div>
                      </div>

                      {/* Column mappings list */}
                      {(!ct.columnMappings || ct.columnMappings.length === 0) ? (
                        <div style={{
                          padding: 24, textAlign: 'center', borderRadius: 'var(--radius-md)',
                          border: '1px dashed var(--border-primary)', background: 'var(--bg-tertiary)',
                        }}>
                          <div className="text-muted small">
                            No column mappings yet.
                            {discoveredCols.length > 0
                              ? ' Click Auto-Detect to populate from discovered schema, or add manually.'
                              : ' Add columns manually or discover the schema first.'}
                          </div>
                        </div>
                      ) : (
                        <div style={{
                          border: '1px solid var(--border-primary)', borderRadius: 'var(--radius-md)', overflow: 'hidden',
                        }}>
                          {/* Column header */}
                          <div style={{
                            display: 'grid',
                            gridTemplateColumns: '180px 120px 1fr 32px',
                            background: 'var(--bg-tertiary)', padding: '8px 12px',
                            fontSize: 11, fontWeight: 600, textTransform: 'uppercase',
                            letterSpacing: 0.5, color: 'var(--text-tertiary)',
                            gap: 8,
                          }}>
                            <div>Source Column</div>
                            <div>Mapping Type</div>
                            <div>Target</div>
                            <div></div>
                          </div>

                          {ct.columnMappings.map((cm, colIndex) => (
                            <div key={colIndex} style={{
                              display: 'grid',
                              gridTemplateColumns: '180px 120px 1fr 32px',
                              padding: '8px 12px', alignItems: 'start',
                              borderTop: '1px solid var(--border-primary)',
                              background: colIndex % 2 === 0 ? 'transparent' : 'var(--bg-tertiary)',
                              gap: 8,
                            }}>
                              {/* Source column */}
                              <div>
                                {discoveredCols.length > 0 ? (
                                  <select className="form-select" style={{ padding: '6px 8px', fontSize: 12 }}
                                    value={cm.sourceColumn || ''}
                                    onChange={(e) => updateCustomColumnMapping(tableIndex, colIndex, 'sourceColumn', e.target.value)}>
                                    <option value="">— select —</option>
                                    {discoveredCols.map(dc => (
                                      <option key={dc.name} value={dc.name}>{dc.name}</option>
                                    ))}
                                  </select>
                                ) : (
                                  <input type="text" className="form-control"
                                    style={{ padding: '6px 8px', fontSize: 12 }}
                                    value={cm.sourceColumn || ''}
                                    onChange={(e) => updateCustomColumnMapping(tableIndex, colIndex, 'sourceColumn', e.target.value)}
                                    placeholder="column_name" />
                                )}
                              </div>

                              {/* Mapping type toggle */}
                              <div>
                                <select className="form-select" style={{ padding: '6px 8px', fontSize: 12 }}
                                  value={cm.mappingType || 'custom'}
                                  onChange={(e) => updateCustomColumnMapping(tableIndex, colIndex, 'mappingType', e.target.value)}>
                                  <option value="standard">Standard</option>
                                  <option value="custom">Custom</option>
                                </select>
                              </div>

                              {/* Target — depends on mapping type */}
                              <div>
                                {cm.mappingType === 'standard' ? (
                                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                                    {/* Target object type */}
                                    <select className="form-select" style={{ padding: '6px 8px', fontSize: 12 }}
                                      value={cm.targetType || ''}
                                      onChange={(e) => updateCustomColumnMapping(tableIndex, colIndex, 'targetType', e.target.value)}>
                                      <option value="">— type —</option>
                                      {OBJECT_TYPES.map(ot => (
                                        <option key={ot.key} value={ot.key}>{ot.label}</option>
                                      ))}
                                    </select>
                                    {/* Target field within that type */}
                                    <select className="form-select" style={{ padding: '6px 8px', fontSize: 12 }}
                                      value={cm.targetField || ''}
                                      onChange={(e) => updateCustomColumnMapping(tableIndex, colIndex, 'targetField', e.target.value)}
                                      disabled={!cm.targetType}>
                                      <option value="">— field —</option>
                                      {(getColumnsForType(cm.targetType) || []).map(col => (
                                        <option key={col.field} value={col.field}>
                                          {col.field} — {col.description}
                                        </option>
                                      ))}
                                    </select>
                                  </div>
                                ) : (
                                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                                    <input type="text" className="form-control"
                                      style={{ padding: '6px 8px', fontSize: 12 }}
                                      value={cm.customAlias || ''}
                                      onChange={(e) => updateCustomColumnMapping(tableIndex, colIndex, 'customAlias', e.target.value)}
                                      placeholder="alias (e.g. green)" />
                                    <input type="text" className="form-control"
                                      style={{ padding: '6px 8px', fontSize: 12 }}
                                      value={cm.customDescription || ''}
                                      onChange={(e) => updateCustomColumnMapping(tableIndex, colIndex, 'customDescription', e.target.value)}
                                      placeholder="description (optional)" />
                                  </div>
                                )}
                              </div>

                              {/* Remove button */}
                              <button
                                onClick={() => removeCustomColumnMapping(tableIndex, colIndex)}
                                style={{
                                  background: 'none', border: 'none', cursor: 'pointer', padding: 4,
                                  color: 'var(--accent-danger)', fontSize: 14, lineHeight: 1,
                                  opacity: 0.6, transition: 'opacity 0.1s',
                                }}
                                onMouseEnter={(e) => e.target.style.opacity = 1}
                                onMouseLeave={(e) => e.target.style.opacity = 0.6}
                                title="Remove column mapping">
                                ✕
                              </button>
                            </div>
                          ))}
                        </div>
                      )}

                      {/* Visual summary of mapped columns */}
                      {ct.columnMappings && ct.columnMappings.length > 0 && (
                        <div style={{
                          padding: '10px 14px', borderRadius: 8,
                          background: 'rgba(99,102,241,0.06)', border: '1px solid rgba(99,102,241,0.15)',
                          fontSize: 12,
                        }}>
                          <strong>Mapping Summary:</strong>{' '}
                          {ct.columnMappings.filter(cm => cm.sourceColumn).map((cm, i) => (
                            <span key={i}>
                              {i > 0 && <span className="text-muted"> · </span>}
                              <code>{cm.sourceColumn}</code>
                              <span className="text-muted"> → </span>
                              {cm.mappingType === 'standard' ? (
                                <span style={{ color: 'var(--accent-primary)', fontWeight: 500 }}>
                                  {cm.targetType}.{cm.targetField}
                                </span>
                              ) : (
                                <span style={{ color: 'var(--accent-success)', fontWeight: 500 }}>
                                  {cm.customAlias || '?'}
                                </span>
                              )}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    );
  };

  const renderStepPreview = () => (
    <div style={{ display: 'grid', gap: 16 }}>
      {!editing ? (
        <div className="card" style={{ padding: 40, textAlign: 'center' }}>
          <p className="text-muted">Save the mapping first, then come back here to preview data.</p>
        </div>
      ) : (
        <>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {OBJECT_TYPES.filter(ot => form[ot.tableField]).map(ot => (
              <button key={ot.key} className="btn btn-secondary" style={{ padding: '6px 14px', fontSize: 13 }}
                onClick={() => handlePreview(ot.key)} disabled={previewLoading}>
                Preview {ot.label}
              </button>
            ))}
          </div>

          {previewLoading && <Loading message="Loading preview..." />}

          {previewData && !previewLoading && (
            <div>
              {previewData.success ? (
                <>
                  <div className="small text-muted" style={{ marginBottom: 8 }}>
                    Table: <code>{previewData.table}</code> — {previewData.rowCount} rows shown
                  </div>

                  {/* Raw data */}
                  <div style={{ marginBottom: 16 }}>
                    <h4 style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>Raw Data (from external DB)</h4>
                    <div style={{ overflowX: 'auto', border: '1px solid var(--border-primary)', borderRadius: 8 }}>
                      <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
                        <thead>
                          <tr style={{ background: 'var(--bg-tertiary)' }}>
                            {previewData.rawColumns?.map(c => (
                              <th key={c} style={{ padding: '8px 12px', textAlign: 'left', whiteSpace: 'nowrap', borderBottom: '1px solid var(--border-primary)' }}>{c}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {previewData.rawRows?.map((row, i) => (
                            <tr key={i} style={{ borderBottom: '1px solid var(--border-primary)' }}>
                              {previewData.rawColumns?.map(c => (
                                <td key={c} style={{ padding: '6px 12px', whiteSpace: 'nowrap', maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                  {row[c] != null ? String(row[c]) : <span className="text-muted">null</span>}
                                </td>
                              ))}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>

                  {/* Mapped data */}
                  <div>
                    <h4 style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>Mapped Data (translated to our fields)</h4>
                    <div style={{ overflowX: 'auto', border: '1px solid var(--border-primary)', borderRadius: 8 }}>
                      <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse' }}>
                        <thead>
                          <tr style={{ background: 'var(--bg-tertiary)' }}>
                            {previewData.mappedRows?.[0] && Object.keys(previewData.mappedRows[0]).map(k => (
                              <th key={k} style={{ padding: '8px 12px', textAlign: 'left', whiteSpace: 'nowrap', borderBottom: '1px solid var(--border-primary)' }}>{k}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {previewData.mappedRows?.map((row, i) => (
                            <tr key={i} style={{ borderBottom: '1px solid var(--border-primary)' }}>
                              {Object.values(row).map((v, j) => (
                                <td key={j} style={{ padding: '6px 12px', whiteSpace: 'nowrap', maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                  {v != null ? String(v) : <span className="text-muted">null</span>}
                                </td>
                              ))}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </>
              ) : (
                <div style={{
                  padding: '12px 16px', borderRadius: 8,
                  background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.3)',
                  color: 'var(--accent-danger)', fontSize: 13,
                }}>
                  Preview failed: {previewData.error}
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );

  // ==================== RENDER ====================

  return (
    <div>
      <div className="mb-4 d-flex justify-content-end">
        <button className="btn btn-primary" onClick={openCreate}>{t('rdapMappings.newMappingButton')}</button>
      </div>

      {/* Current status banner */}
      <div className="card" style={{ marginBottom: 24, padding: '20px 24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{
            width: 40, height: 40, borderRadius: 10,
            background: activeMappingExists
              ? 'linear-gradient(135deg, var(--accent-tertiary), #0891b2)'
              : 'linear-gradient(135deg, var(--accent-primary), var(--accent-secondary))',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <i className="fa-solid fa-database" style={{ color: 'white' }}></i>
          </div>
          <div>
            <div style={{ fontWeight: 600, fontSize: 15 }}>
              {activeMappingExists
                ? `${activeMappingCount} custom mapping${activeMappingCount !== 1 ? 's' : ''} active: ${activeMappings.map(m => m.name).join(', ')}`
                : 'Using default database structure'}
            </div>
            <div className="text-muted small">
              {activeMappingExists
                ? (() => {
                    const extCount = activeMappings.filter(m => m.dbType === 'EXTERNAL').length;
                    const localCount = activeMappingCount - extCount;
                    const parts = [];
                    if (extCount > 0) parts.push(`${extCount} external`);
                    if (localCount > 0) parts.push(`${localCount} local`);
                    return `${parts.join(' + ')} database${activeMappingCount !== 1 ? 's' : ''} — queries will search all active mappings in order, then fall back to defaults.`;
                  })()
                : 'No custom mapping is active. The application reads from the built-in rdap_domains, rdap_ips, and rdap_asns tables.'}
            </div>
          </div>
        </div>
      </div>

      {/* Mapping cards */}
      {mappings.length === 0 ? (
        <div className="card" style={{ padding: 60, textAlign: 'center' }}>
          <div style={{ fontSize: 48, marginBottom: 16 }}><i className="fa-solid fa-database"></i></div>
          <h3 style={{ marginBottom: 8 }}>No Custom Mappings Configured</h3>
          <p className="text-muted" style={{ marginBottom: 24, maxWidth: 460, margin: '0 auto 24px' }}>
            Create a mapping to connect to an external RDAP database with a different schema,
            or override table/column names in the local database.
          </p>
          <button className="btn btn-primary" onClick={openCreate}>Create Mapping</button>
        </div>
      ) : (
        <div style={{ display: 'grid', gap: 16 }}>
          {mappings.map(m => (
            <div key={m.id} className="card" style={{
              border: m.isActive ? '2px solid var(--accent-success)' : undefined,
            }}>
              <div style={{ padding: '20px 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, flexWrap: 'wrap' }}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
                    <h3 style={{ margin: 0, fontSize: 16 }}>{m.name}</h3>
                    {m.isActive && (
                      <span style={{
                        fontSize: 11, padding: '2px 10px', borderRadius: 20, fontWeight: 600,
                        background: 'rgba(16,185,129,0.12)', color: 'var(--accent-success)',
                        border: '1px solid rgba(16,185,129,0.25)',
                      }}>Active</span>
                    )}
                    <span style={{
                      fontSize: 11, padding: '2px 10px', borderRadius: 20, fontWeight: 500,
                      background: m.dbType === 'EXTERNAL' ? 'rgba(99,102,241,0.1)' : 'rgba(156,163,175,0.1)',
                      color: m.dbType === 'EXTERNAL' ? 'var(--accent-primary)' : 'var(--text-tertiary)',
                      border: `1px solid ${m.dbType === 'EXTERNAL' ? 'rgba(99,102,241,0.25)' : 'rgba(156,163,175,0.25)'}`,
                    }}>
                      {m.dbType === 'EXTERNAL' ? '🌐 External' : '📁 Local'}
                    </span>
                  </div>
                  {m.description && <p className="text-muted small" style={{ marginBottom: 12 }}>{m.description}</p>}
                  <div style={{ display: 'flex', gap: 20, flexWrap: 'wrap' }}>
                    {OBJECT_TYPES.map(ot => {
                      const table = m[ot.tableField];
                      const mappingCount = Object.keys(m[ot.mappingField] || {}).length;
                      const hasOverrides = table || mappingCount > 0;
                      return (
                        <div key={ot.key} className="small">
                          <span className="text-muted">{ot.label}:</span>{' '}
                          {hasOverrides ? (
                            <span style={{ color: 'var(--accent-tertiary)', fontWeight: 500 }}>
                              {table || ot.defaultTable}
                              {mappingCount > 0 && ` (${mappingCount} col${mappingCount !== 1 ? 's' : ''})`}
                            </span>
                          ) : (
                            <span className="text-muted">default</span>
                          )}
                        </div>
                      );
                    })}
                  </div>
                  {/* Show join info */}
                  {m.domainContactJoinMappings && Object.keys(m.domainContactJoinMappings).length > 0 && (
                    <div className="small" style={{ marginTop: 6 }}>
                      <span className="text-muted">Contact joins:</span>{' '}
                      <span style={{ color: 'var(--accent-tertiary)', fontWeight: 500 }}>
                        {Object.entries(m.domainContactJoinMappings).map(([role, col]) => `${role}→${col}`).join(', ')}
                      </span>
                    </div>
                  )}
                  {/* Show custom table info */}
                  {m.customTableMappings && m.customTableMappings.length > 0 && (
                    <div className="small" style={{ marginTop: 6, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                      <span className="text-muted">Custom tables:</span>{' '}
                      {m.customTableMappings.map((ct, ci) => {
                        const colCount = (ct.columnMappings || []).length;
                        const stdCount = (ct.columnMappings || []).filter(cm => cm.mappingType === 'standard').length;
                        const custCount = colCount - stdCount;
                        return (
                          <span key={ci} style={{
                            display: 'inline-flex', alignItems: 'center', gap: 4,
                            fontSize: 11, padding: '2px 8px', borderRadius: 6,
                            background: ct.joinConfig?.enabled ? 'rgba(16,185,129,0.1)' : 'rgba(245,158,11,0.1)',
                            border: `1px solid ${ct.joinConfig?.enabled ? 'rgba(16,185,129,0.25)' : 'rgba(245,158,11,0.25)'}`,
                            color: 'var(--text-secondary)',
                          }}>
                            {ct.joinConfig?.enabled && <span title="Join active">🔗</span>}
                            <span style={{ fontWeight: 600 }}>{ct.label || ct.tableName}</span>
                            {ct.actsAs && (
                              <span style={{ fontStyle: 'italic', color: 'var(--accent-success)' }}>
                                as {OBJECT_TYPES.find(ot => ot.key === ct.actsAs)?.label || ct.actsAs}
                              </span>
                            )}
                            {colCount > 0 && (
                              <span className="text-muted">
                                ({colCount} col{colCount !== 1 ? 's' : ''}
                                {stdCount > 0 && `, ${stdCount} std`}
                                {custCount > 0 && `, ${custCount} custom`})
                              </span>
                            )}
                          </span>
                        );
                      })}
                    </div>
                  )}
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn btn-secondary" style={{ padding: '6px 14px', fontSize: 13 }}
                    onClick={() => openEdit(m)}>Edit</button>
                  <button
                    className={`btn ${m.isActive ? 'btn-secondary' : 'btn-primary'}`}
                    style={{ padding: '6px 14px', fontSize: 13 }}
                    onClick={() => handleToggleActive(m)}>
                    {m.isActive ? 'Deactivate' : 'Activate'}
                  </button>
                  {!m.isActive && (
                    <button className="btn btn-secondary"
                      style={{ padding: '6px 14px', fontSize: 13, color: 'var(--accent-danger)' }}
                      onClick={() => setDeleting(m)}>Delete</button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Editor Modal */}
      <Modal
        isOpen={showEditor}
        title={editing ? `Edit Mapping: ${editing.name}` : 'Create New Mapping'}
        onClose={() => setShowEditor(false)}
        size="xlarge"
      >
        {/* Step navigation */}
        <div style={{
          display: 'flex', borderBottom: '1px solid var(--border-primary)', marginBottom: 20,
          gap: 0, overflowX: 'auto',
        }}>
          {STEPS.map((step, i) => (
            <button key={step.key}
              onClick={() => setEditorStep(i)}
              style={{
                padding: '10px 18px', fontSize: 13, fontWeight: 600, cursor: 'pointer',
                border: 'none', background: 'none', whiteSpace: 'nowrap',
                borderBottom: editorStep === i ? '2px solid var(--accent-primary)' : '2px solid transparent',
                color: editorStep === i ? 'var(--accent-primary)' : 'var(--text-tertiary)',
                transition: 'all 0.15s ease',
              }}>
              <span style={{ marginRight: 6, opacity: 0.6, fontSize: 11 }}>{i + 1}</span>
              {step.label}
            </button>
          ))}
        </div>

        {/* Step content */}
        <div style={{ minHeight: 300 }}>
          {editorStep === 0 && renderStepBasic()}
          {editorStep === 1 && renderStepConnection()}
          {editorStep === 2 && renderStepTables()}
          {editorStep === 3 && renderStepColumns()}
          {editorStep === 4 && renderStepJoins()}
          {editorStep === 5 && renderStepCustomTables()}
          {editorStep === 6 && renderStepPreview()}
        </div>

        {/* Footer */}
        <div style={{ display: 'flex', gap: 12, marginTop: 24, justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', gap: 8 }}>
            {editorStep > 0 && (
              <button className="btn btn-secondary" onClick={() => setEditorStep(s => s - 1)}>← Back</button>
            )}
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <button className="btn btn-secondary" onClick={() => setShowEditor(false)}>Cancel</button>
            {editorStep < STEPS.length - 1 ? (
              <button className="btn btn-primary" onClick={() => setEditorStep(s => s + 1)}>
                Next →
              </button>
            ) : (
              <button className="btn btn-primary" onClick={handleSave} disabled={saving}>
                {saving ? 'Saving...' : editing ? 'Save Changes' : 'Create Mapping'}
              </button>
            )}
            {editorStep > 0 && editorStep < STEPS.length - 1 && (
              <button className="btn btn-primary" onClick={handleSave} disabled={saving}
                style={{ opacity: 0.8 }}>
                {saving ? 'Saving...' : 'Save Now'}
              </button>
            )}
          </div>
        </div>
      </Modal>

      {/* Delete confirmation */}
      {deleting && (
        <DeleteConfirmModal
          isOpen={!!deleting}
          onClose={() => setDeleting(null)}
          title="Delete Mapping"
          itemType="mapping"
          itemName={deleting.name}
          warning="This action cannot be undone."
          onConfirm={handleDelete}
        />
      )}
    </div>
  );
};

export default RdapDataMappings;
