/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  getRdapDataMappings, listMappingTables, browseMappingTable,
} from '../services/api';
import Loading from '../components/Loading';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const ExternalDataBrowser = () => {
  const [mappings, setMappings] = useState([]);
  const [selectedMapping, setSelectedMapping] = useState(null);
  const [tables, setTables] = useState([]);
  const [selectedTable, setSelectedTable] = useState(null);
  const [loading, setLoading] = useState(true);
  const [tablesLoading, setTablesLoading] = useState(false);
  const [dataLoading, setDataLoading] = useState(false);

  // Cell detail modal
  const [cellModal, setCellModal] = useState(null); // { column, value, mappedName }

  // Browse state
  const [data, setData] = useState(null);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [sortColumn, setSortColumn] = useState(null);
  const [sortDir, setSortDir] = useState('asc');
  const { t } = useT();

  // Load mappings on mount
  useEffect(() => {
    (async () => {
      try {
        const res = await getRdapDataMappings();
        if (res.data.success) setMappings(res.data.mappings);
      } catch (e) {
        toast.error(t('dataBrowser.failedToLoadMappings'));
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  // Load tables when mapping selected
  const loadTables = useCallback(async (mapping) => {
    setSelectedMapping(mapping);
    setSelectedTable(null);
    setData(null);
    setTablesLoading(true);
    try {
      const res = await listMappingTables(mapping.id);
      if (res.data.success) {
        setTables(res.data.tables || []);
      } else {
        toast.error(t('dataBrowser.failedToListTables', { error: res.data.error || t('common.unknown') }));
        setTables([]);
      }
    } catch (e) {
      toast.error(t('dataBrowser.failedToListTablesGeneric'));
      setTables([]);
    } finally {
      setTablesLoading(false);
    }
  }, []);

  // Load data when table selected or params change
  const loadData = useCallback(async () => {
    if (!selectedMapping || !selectedTable) return;
    setDataLoading(true);
    try {
      const res = await browseMappingTable(selectedMapping.id, selectedTable, {
        page, pageSize, search: search || undefined,
        sortColumn: sortColumn || undefined, sortDir,
      });
      if (res.data.success) {
        setData(res.data);
      } else {
        toast.error(t('dataBrowser.queryFailed', { error: res.data.error || t('common.unknown') }));
        setData(null);
      }
    } catch (e) {
      toast.error(t('common.loadFailed'));
      setData(null);
    } finally {
      setDataLoading(false);
    }
  }, [selectedMapping, selectedTable, page, pageSize, search, sortColumn, sortDir]);

  useEffect(() => {
    if (selectedTable) loadData();
  }, [selectedTable, page, pageSize, search, sortColumn, sortDir, loadData]);

  const selectTable = (tableName) => {
    setSelectedTable(tableName);
    setPage(0);
    setSearch('');
    setSearchInput('');
    setSortColumn(null);
    setSortDir('asc');
  };

  const handleSort = (col) => {
    if (sortColumn === col) {
      setSortDir(d => d === 'asc' ? 'desc' : 'asc');
    } else {
      setSortColumn(col);
      setSortDir('asc');
    }
    setPage(0);
  };

  const handleSearch = (e) => {
    e.preventDefault();
    setSearch(searchInput);
    setPage(0);
  };

  const clearSearch = () => {
    setSearchInput('');
    setSearch('');
    setPage(0);
  };

  if (loading) return <Loading message={t('dataBrowser.loadingMappings')} />;

  // --- No mappings state ---
  if (mappings.length === 0) {
    return (
      <div>
        <div className="card" style={{ padding: 60, textAlign: 'center' }}>
          <div style={{ fontSize: 48, marginBottom: 16 }}><i className="fa-solid fa-database" /></div>
          <h3 style={{ marginBottom: 8 }}>{t('dataBrowser.noMappingsTitle')}</h3>
          <p className="text-muted">{t('dataBrowser.noMappingsBody')}</p>
        </div>
      </div>
    );
  }

  return (
    <div>
      {/* Mapping selector */}
      <div className="card" style={{ marginBottom: 16, padding: '16px 20px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <label style={{ fontWeight: 600, fontSize: 13, whiteSpace: 'nowrap' }}>{t('dataBrowser.mappingLabel')}</label>
          <select className="form-select" style={{ flex: 1, maxWidth: 400 }}
            value={selectedMapping?.id || ''}
            onChange={(e) => {
              const m = mappings.find(m => m.id === Number(e.target.value));
              if (m) loadTables(m);
            }}>
            <option value="">{t('dataBrowser.selectMappingOption')}</option>
            {mappings.map(m => (
              <option key={m.id} value={m.id}>
                {m.name} ({m.dbType === 'EXTERNAL' ? t('dataBrowser.external') : t('dataBrowser.local')})
                {m.isActive ? ` ✓ ${t('common.active')}` : ''}
              </option>
            ))}
          </select>
          {selectedMapping && (
            <span className="text-muted small">
              {selectedMapping.dbType === 'EXTERNAL' ? '🌐' : '📁'}{' '}
              {selectedMapping.description || selectedMapping.externalJdbcUrl || t('dataBrowser.localDatabaseFallback')}
            </span>
          )}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '220px 1fr', gap: 16, minHeight: 400 }}>
        {/* Left sidebar: table list */}
        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          <div style={{
            padding: '12px 16px', fontWeight: 600, fontSize: 13,
            borderBottom: '1px solid var(--border-primary)',
            background: 'var(--bg-tertiary)',
          }}>
            {t('dataBrowser.tablesHeader')}
            {tables.length > 0 && <span className="text-muted" style={{ fontWeight: 400 }}> ({tables.length})</span>}
          </div>

          {tablesLoading ? (
            <div style={{ padding: 20 }}><Loading message={t('common.loading')} /></div>
          ) : !selectedMapping ? (
            <div style={{ padding: 20 }} className="text-muted small">{t('dataBrowser.selectMappingAbove')}</div>
          ) : tables.length === 0 ? (
            <div style={{ padding: 20 }} className="text-muted small">{t('dataBrowser.noTablesFound')}</div>
          ) : (
            <div style={{ overflowY: 'auto', maxHeight: 'calc(100vh - 320px)' }}>
              {tables.map(tbl => (
                <button key={tbl.name}
                  onClick={() => selectTable(tbl.name)}
                  style={{
                    display: 'block', width: '100%', textAlign: 'left',
                    padding: '10px 16px', border: 'none', cursor: 'pointer',
                    fontSize: 13, borderBottom: '1px solid var(--border-primary)',
                    background: selectedTable === tbl.name ? 'var(--accent-primary)' : 'transparent',
                    color: selectedTable === tbl.name ? 'white' : 'var(--text-primary)',
                    transition: 'all 0.1s ease',
                  }}>
                  <div style={{ fontWeight: 600 }}>{tbl.name}</div>
                  <div style={{
                    fontSize: 11,
                    color: selectedTable === tbl.name ? 'rgba(255,255,255,0.7)' : 'var(--text-tertiary)',
                  }}>
                    {t('dataBrowser.tableStats', { cols: tbl.columnCount, rows: tbl.rowCount >= 0 ? t('dataBrowser.rowsCount', { count: tbl.rowCount }) : '?' })}
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Right: data grid */}
        <div className="card" style={{ padding: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
          {!selectedTable ? (
            <div style={{ padding: 60, textAlign: 'center', flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <div>
                <div style={{ fontSize: 36, marginBottom: 12, opacity: 0.4 }}><i className="fa-solid fa-database" /></div>
                <p className="text-muted">{t('dataBrowser.selectTablePrompt')}</p>
              </div>
            </div>
          ) : (
            <>
              {/* Toolbar */}
              <div style={{
                padding: '10px 16px', borderBottom: '1px solid var(--border-primary)',
                display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap',
                background: 'var(--bg-tertiary)',
              }}>
                <div style={{ fontWeight: 600, fontSize: 14 }}>{selectedTable}</div>
                {data && (
                  <span className="text-muted small">
                    {t('dataBrowser.rowsTotal', { count: data.totalRows.toLocaleString() })}
                  </span>
                )}
                {data?.columnMappings && Object.keys(data.columnMappings).length > 0 && (
                  <span style={{
                    fontSize: 11, padding: '2px 8px', borderRadius: 10,
                    background: 'rgba(99,102,241,0.1)', color: 'var(--accent-primary)',
                    border: '1px solid rgba(99,102,241,0.25)', fontWeight: 500,
                  }}>
                    {Object.keys(data.columnMappings).length !== 1
                      ? t('dataBrowser.columnsMapped', { count: Object.keys(data.columnMappings).length })
                      : t('dataBrowser.columnMapped', { count: Object.keys(data.columnMappings).length })}
                  </span>
                )}
                <div style={{ flex: 1 }} />
                <form onSubmit={handleSearch} style={{ display: 'flex', gap: 6 }}>
                  <input type="text" className="form-control"
                    style={{ padding: '5px 10px', fontSize: 12, width: 200 }}
                    placeholder={t('dataBrowser.searchPlaceholder')}
                    value={searchInput}
                    onChange={e => setSearchInput(e.target.value)} />
                  <button type="submit" className="btn btn-secondary"
                    style={{ padding: '5px 12px', fontSize: 12 }}>{t('common.search')}</button>
                  {search && (
                    <button type="button" className="btn btn-secondary"
                      style={{ padding: '5px 10px', fontSize: 12 }}
                      onClick={clearSearch}>✕</button>
                  )}
                </form>
                <select className="form-select" style={{ width: 80, padding: '5px 6px', fontSize: 12 }}
                  value={pageSize} onChange={e => { setPageSize(Number(e.target.value)); setPage(0); }}>
                  {[10, 25, 50, 100].map(n => <option key={n} value={n}>{t('dataBrowser.pageSizeOption', { n })}</option>)}
                </select>
              </div>

              {/* Data */}
              {dataLoading ? (
                <div style={{ padding: 40 }}><Loading message={t('dataBrowser.loadingData')} /></div>
              ) : data && data.rows ? (
                <>
                  <div style={{ flex: 1, overflowX: 'auto', overflowY: 'auto', maxHeight: 'calc(100vh - 380px)' }}>
                    <table style={{ width: '100%', fontSize: 12, borderCollapse: 'collapse', minWidth: data.columns?.length * 140 }}>
                      <thead>
                        <tr style={{ position: 'sticky', top: 0, background: 'var(--bg-secondary)', zIndex: 1 }}>
                          {data.columns?.map(col => {
                            const hasMapped = col.mappedName != null;
                            return (
                              <th key={col.name}
                                onClick={() => handleSort(col.name)}
                                style={{
                                  padding: '6px 12px', textAlign: 'left', whiteSpace: 'nowrap',
                                  borderBottom: '2px solid var(--border-primary)',
                                  cursor: 'pointer', userSelect: 'none',
                                  borderLeft: hasMapped ? '3px solid var(--accent-primary)' : '3px solid transparent',
                                }}>
                                {hasMapped && (
                                  <div style={{
                                    fontSize: 11, fontWeight: 700, color: 'var(--accent-primary)',
                                    marginBottom: 1, letterSpacing: 0.2,
                                  }}>
                                    {col.mappedName}
                                  </div>
                                )}
                                <div style={{
                                  fontWeight: hasMapped ? 400 : 600,
                                  fontSize: 11,
                                  textTransform: 'uppercase',
                                  letterSpacing: 0.3,
                                  color: sortColumn === col.name
                                    ? 'var(--accent-primary)'
                                    : hasMapped ? 'var(--text-tertiary)' : 'var(--text-secondary)',
                                }}>
                                  {col.name}
                                  {sortColumn === col.name && (
                                    <span style={{ marginLeft: 4 }}>{sortDir === 'asc' ? '▲' : '▼'}</span>
                                  )}
                                </div>
                                <div style={{
                                  fontSize: 9, fontWeight: 400, textTransform: 'none',
                                  letterSpacing: 0, color: 'var(--text-tertiary)', opacity: 0.7,
                                }}>{col.type}</div>
                              </th>
                            );
                          })}
                        </tr>
                      </thead>
                      <tbody>
                        {data.rows.length === 0 ? (
                          <tr>
                            <td colSpan={data.columns?.length || 1}
                              style={{ padding: 40, textAlign: 'center', color: 'var(--text-tertiary)' }}>
                              {search ? t('dataBrowser.noRowsMatchSearch') : t('dataBrowser.tableEmpty')}
                            </td>
                          </tr>
                        ) : (
                          data.rows.map((row, i) => (
                            <tr key={i} style={{
                              borderBottom: '1px solid var(--border-primary)',
                              background: i % 2 === 0 ? 'transparent' : 'var(--bg-tertiary)',
                            }}>
                              {data.columns?.map(col => {
                                const val = row[col.name];
                                const isNull = val === null || val === undefined;
                                const display = isNull ? t('dataBrowser.nullValue') : String(val);
                                const truncated = display.length > 60;
                                return (
                                  <td key={col.name} style={{
                                    padding: '6px 12px', whiteSpace: 'nowrap',
                                    maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis',
                                    color: isNull ? 'var(--text-tertiary)' : 'var(--text-primary)',
                                    fontStyle: isNull ? 'italic' : 'normal',
                                    fontSize: 12,
                                  }}>
                                    {truncated ? (
                                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, maxWidth: '100%' }}>
                                        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                          {display.substring(0, 60)}…
                                        </span>
                                        <button
                                          onClick={() => setCellModal({
                                            column: col.name,
                                            mappedName: col.mappedName || null,
                                            value: display,
                                          })}
                                          style={{
                                            flexShrink: 0, border: '1px solid var(--border-primary)',
                                            background: 'var(--bg-tertiary)', borderRadius: 4,
                                            padding: '1px 5px', fontSize: 10, cursor: 'pointer',
                                            color: 'var(--accent-primary)', fontWeight: 600,
                                            lineHeight: '14px',
                                          }}
                                          title={t('dataBrowser.viewFullValue')}
                                        >⤢</button>
                                      </span>
                                    ) : display}
                                  </td>
                                );
                              })}
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>

                  {/* Pagination */}
                  {data.totalPages > 1 && (
                    <div style={{
                      padding: '10px 16px', borderTop: '1px solid var(--border-primary)',
                      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                      background: 'var(--bg-tertiary)',
                    }}>
                      <span className="text-muted small">
                        {t('dataBrowser.showingRange', {
                          from: (page * pageSize) + 1,
                          to: Math.min((page + 1) * pageSize, data.totalRows),
                          total: data.totalRows.toLocaleString(),
                        })}
                        {search && <span> {t('dataBrowser.filteredSuffix')}</span>}
                      </span>
                      <div style={{ display: 'flex', gap: 4 }}>
                        <button className="btn btn-secondary"
                          style={{ padding: '4px 10px', fontSize: 12 }}
                          disabled={page === 0}
                          onClick={() => setPage(0)}>⟪</button>
                        <button className="btn btn-secondary"
                          style={{ padding: '4px 10px', fontSize: 12 }}
                          disabled={page === 0}
                          onClick={() => setPage(p => p - 1)}>←</button>
                        <span style={{ padding: '4px 12px', fontSize: 12, lineHeight: '24px' }}>
                          {t('dataBrowser.pageOf', { page: page + 1, total: data.totalPages })}
                        </span>
                        <button className="btn btn-secondary"
                          style={{ padding: '4px 10px', fontSize: 12 }}
                          disabled={page >= data.totalPages - 1}
                          onClick={() => setPage(p => p + 1)}>→</button>
                        <button className="btn btn-secondary"
                          style={{ padding: '4px 10px', fontSize: 12 }}
                          disabled={page >= data.totalPages - 1}
                          onClick={() => setPage(data.totalPages - 1)}>⟫</button>
                      </div>
                    </div>
                  )}
                </>
              ) : null}
            </>
          )}
        </div>
      </div>

      {/* Cell value modal */}
      {cellModal && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.5)', zIndex: 9999,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          padding: 24,
        }}
          onClick={() => setCellModal(null)}>
          <div style={{
            background: 'var(--bg-primary)', borderRadius: 12,
            maxWidth: 700, width: '100%', maxHeight: '80vh',
            display: 'flex', flexDirection: 'column',
            boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
          }}
            onClick={e => e.stopPropagation()}>
            {/* Header */}
            <div style={{
              padding: '16px 20px', borderBottom: '1px solid var(--border-primary)',
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            }}>
              <div>
                {cellModal.mappedName && (
                  <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--accent-primary)', marginBottom: 2 }}>
                    {cellModal.mappedName}
                  </div>
                )}
                <div style={{
                  fontSize: cellModal.mappedName ? 12 : 14,
                  fontWeight: cellModal.mappedName ? 400 : 600,
                  color: cellModal.mappedName ? 'var(--text-tertiary)' : 'var(--text-primary)',
                }}>
                  {cellModal.column}
                </div>
              </div>
              <button onClick={() => setCellModal(null)}
                style={{
                  border: 'none', background: 'var(--bg-tertiary)', borderRadius: 6,
                  width: 28, height: 28, cursor: 'pointer', fontSize: 14,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  color: 'var(--text-tertiary)',
                }}>✕</button>
            </div>
            {/* Body */}
            <div style={{
              padding: '16px 20px', overflowY: 'auto', flex: 1,
            }}>
              <pre style={{
                whiteSpace: 'pre-wrap', wordBreak: 'break-all',
                fontSize: 13, lineHeight: 1.6, margin: 0,
                fontFamily: 'ui-monospace, SFMono-Regular, "SF Mono", Menlo, monospace',
                color: 'var(--text-primary)', background: 'var(--bg-tertiary)',
                padding: 16, borderRadius: 8,
              }}>{cellModal.value}</pre>
            </div>
            {/* Footer */}
            <div style={{
              padding: '12px 20px', borderTop: '1px solid var(--border-primary)',
              display: 'flex', justifyContent: 'flex-end', gap: 8,
            }}>
              <button className="btn btn-secondary" style={{ padding: '6px 14px', fontSize: 12 }}
                onClick={() => {
                  navigator.clipboard.writeText(cellModal.value);
                  toast.success(t('dataBrowser.copiedToClipboard'));
                }}>{t('common.copy')}</button>
              <button className="btn btn-primary" style={{ padding: '6px 14px', fontSize: 12 }}
                onClick={() => setCellModal(null)}>{t('common.close')}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ExternalDataBrowser;
