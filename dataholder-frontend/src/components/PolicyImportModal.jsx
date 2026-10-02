/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useCallback, useRef } from 'react';
import Modal from './Modal';
import { previewPolicyImport, importPolicyExpressions } from '../services/api';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

// ==================== Sub-components ====================

const SensitivityBadge = ({ action, level }) => {
  const styles = {
    SHOW: { bg: 'rgba(16,185,129,0.1)', color: '#10b981', border: 'rgba(16,185,129,0.25)' },
    REDACT: { bg: 'rgba(245,158,11,0.1)', color: '#f59e0b', border: 'rgba(245,158,11,0.25)' },
    HIDE: { bg: 'rgba(239,68,68,0.1)', color: '#ef4444', border: 'rgba(239,68,68,0.25)' },
  };
  const s = styles[action] || styles.SHOW;
  return (
    <span style={{
      fontSize: 11, padding: '2px 8px', borderRadius: 12, fontWeight: 600,
      background: s.bg, color: s.color, border: `1px solid ${s.border}`,
      whiteSpace: 'nowrap',
    }}>
      {action}{level > 0 ? ` (L${level}+)` : ''}
    </span>
  );
};

const StatusChip = ({ label, value, color }) => (
  <span style={{
    fontSize: 11, padding: '2px 10px', borderRadius: 12, fontWeight: 600,
    background: `${color}15`, color, border: `1px solid ${color}30`,
  }}>
    {label}: {value}
  </span>
);

// ==================== Main Component ====================

const PolicyImportModal = ({ isOpen, onClose, onImportComplete }) => {
  const fileInputRef = useRef(null);
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [previews, setPreviews] = useState([]);
  const [previewErrors, setPreviewErrors] = useState([]);
  const [expandedIndex, setExpandedIndex] = useState(null);
  const [loading, setLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importResults, setImportResults] = useState(null);
  const [dragActive, setDragActive] = useState(false);
  const { t } = useT();

  const resetState = useCallback(() => {
    setSelectedFiles([]);
    setPreviews([]);
    setPreviewErrors([]);
    setExpandedIndex(null);
    setImportResults(null);
    setLoading(false);
    setImporting(false);
  }, []);

  const handleClose = () => {
    resetState();
    onClose();
  };

  const handleFilesSelected = useCallback(async (files) => {
    const jsonFiles = Array.from(files).filter(f => f.name.endsWith('.json'));
    if (jsonFiles.length === 0) {
      toast.error(t('policyImport.selectJsonFiles'));
      return;
    }

    setSelectedFiles(jsonFiles);
    setLoading(true);
    setImportResults(null);

    try {
      const response = await previewPolicyImport(jsonFiles);
      if (response.data.success) {
        const successful = response.data.previews.filter(p => p.success);
        const failed = response.data.previews.filter(p => !p.success);
        setPreviews(successful);
        setPreviewErrors(failed);
        if (successful.length > 0) setExpandedIndex(0);
      } else {
        toast.error(response.data.error || t('policyImport.previewFailed'));
      }
    } catch (error) {
      toast.error(error.response?.data?.error || t('policyImport.previewFileError'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  const handleFileChange = (e) => {
    if (e.target.files?.length > 0) handleFilesSelected(e.target.files);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragActive(false);
    if (e.dataTransfer.files?.length > 0) handleFilesSelected(e.dataTransfer.files);
  };

  const handleDragOver = (e) => { e.preventDefault(); setDragActive(true); };
  const handleDragLeave = () => setDragActive(false);

  const removePreview = (index) => {
    setPreviews(prev => prev.filter((_, i) => i !== index));
    // Also remove the corresponding file
    const removedFileName = previews[index]?.fileName;
    if (removedFileName) {
      setSelectedFiles(prev => prev.filter(f => f.name !== removedFileName));
    }
    if (expandedIndex === index) setExpandedIndex(null);
    else if (expandedIndex > index) setExpandedIndex(expandedIndex - 1);
  };

  const handleImport = async () => {
    // Only import files that passed preview
    const filesToImport = selectedFiles.filter(f =>
      previews.some(p => p.fileName === f.name)
    );

    if (filesToImport.length === 0) {
      toast.error(t('policyImport.noValidFiles'));
      return;
    }

    setImporting(true);
    try {
      const response = await importPolicyExpressions(filesToImport);
      if (response.data.success) {
        const results = response.data.results || [];
        const successes = results.filter(r => r.success);
        const failures = results.filter(r => !r.success);

        setImportResults({ success: successes, failed: failures });

        if (successes.length > 0) {
          toast.success(t('policyImport.importedCount', { count: successes.length, plural: successes.length > 1 ? 's' : '' }));
        }
        if (failures.length > 0) {
          toast.error(t('policyImport.importsFailedCount', { count: failures.length, plural: failures.length > 1 ? 's' : '' }));
        }
        if (successes.length > 0 && onImportComplete) {
          onImportComplete();
        }
      } else {
        toast.error(response.data.error || t('policyImport.importFailed'));
      }
    } catch (error) {
      toast.error(error.response?.data?.error || t('policyImport.importPoliciesError'));
    } finally {
      setImporting(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title={t('policyImport.title')} size="xlarge">
      <div style={{ display: 'grid', gap: 20, maxHeight: '70vh', overflowY: 'auto' }}>

        {/* Step 1: File Upload */}
        {!importResults && (
          <>
            <div
              onDrop={handleDrop}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onClick={() => fileInputRef.current?.click()}
              style={{
                border: `2px dashed ${dragActive ? 'var(--accent-primary)' : 'var(--border-primary)'}`,
                borderRadius: 'var(--radius-lg)',
                padding: '32px 24px',
                textAlign: 'center',
                cursor: 'pointer',
                background: dragActive ? 'rgba(99,102,241,0.05)' : 'var(--bg-tertiary)',
                transition: 'all 0.15s ease',
              }}
            >
              <div style={{ fontSize: 32, marginBottom: 8, opacity: 0.5 }}>
                <i className="fa-solid fa-upload" style={{ fontSize: 32 }}></i>
              </div>
              <div style={{ fontWeight: 600, marginBottom: 4 }}>
                {previews.length > 0 ? t('policyImport.dropMoreFiles') : t('policyImport.dropFilesHere')}
              </div>
              <div className="text-muted small">
                {t('policyImport.uploadHint')}
              </div>
              <input
                ref={fileInputRef}
                type="file"
                accept=".json"
                multiple
                onChange={handleFileChange}
                style={{ display: 'none' }}
              />
            </div>

            {/* Loading */}
            {loading && (
              <div style={{ textAlign: 'center', padding: 24 }}>
                <div className="spinner-border text-primary mb-2" role="status" style={{ margin: '0 auto 12px' }}>
                  <span className="visually-hidden">{t('common.loading')}</span>
                </div>
                <div className="text-muted">{t('policyImport.uploadingFiles')}</div>
              </div>
            )}

            {/* Parse Errors */}
            {previewErrors.length > 0 && (
              <div style={{
                padding: '12px 16px', borderRadius: 'var(--radius-md)',
                background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)',
              }}>
                <div style={{ fontWeight: 600, fontSize: 13, color: 'var(--accent-danger)', marginBottom: 6 }}>
                  {t('policyImport.filesCouldNotBeImported', { count: previewErrors.length, plural: previewErrors.length > 1 ? 's' : '' })}
                </div>
                {previewErrors.map((e, i) => (
                  <div key={i} className="small" style={{ color: 'var(--accent-danger)', marginTop: 4 }}>
                    <strong>{e.fileName}:</strong> {e.error}
                  </div>
                ))}
              </div>
            )}

            {/* Preview: Summary */}
            {previews.length > 0 && !loading && (
              <>
                <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
                  <div className="card" style={{ padding: '16px 20px', flex: 1, minWidth: 140 }}>
                    <div className="text-muted small">{t('policyImport.policiesToImport')}</div>
                    <div style={{ fontSize: 24, fontWeight: 700, color: 'var(--accent-primary)' }}>{previews.length}</div>
                  </div>
                </div>

                {/* Preview: Individual policies */}
                <div style={{ display: 'grid', gap: 12 }}>
                  {previews.map((preview, idx) => {
                    const isExpanded = expandedIndex === idx;
                    const policy = preview.policy;
                    if (!policy) return null;

                    return (
                      <div key={idx} className="card" style={{ overflow: 'hidden' }}>
                        {/* Header */}
                        <div
                          onClick={() => setExpandedIndex(isExpanded ? null : idx)}
                          style={{
                            padding: '14px 20px',
                            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                            cursor: 'pointer', gap: 12,
                            background: isExpanded ? 'var(--bg-tertiary)' : 'transparent',
                          }}
                        >
                          <div style={{ flex: 1, minWidth: 0 }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                              <span style={{ fontWeight: 600, fontSize: 14 }}>{policy.name}</span>
                            </div>
                            <div className="text-muted small" style={{ marginTop: 4 }}>
                              {preview.fileName}
                            </div>
                          </div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <button
                              className="btn btn-secondary"
                              style={{ padding: '4px 10px', fontSize: 11 }}
                              onClick={(e) => { e.stopPropagation(); removePreview(idx); }}
                            >
                              {t('common.remove')}
                            </button>
                            <i
                              className="fa-solid fa-chevron-down"
                              style={{ fontSize: 16, transform: isExpanded ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s', flexShrink: 0 }}
                            ></i>
                          </div>
                        </div>

                        {/* Expanded detail */}
                        {isExpanded && (
                          <div style={{ padding: '0 20px 16px' }}>
                            {/* Description */}
                            {policy.description && (
                              <div style={{
                                padding: '10px 14px', borderRadius: 'var(--radius-sm)',
                                background: 'var(--bg-tertiary)', marginBottom: 16,
                                fontSize: 12, whiteSpace: 'pre-line', color: 'var(--text-secondary)',
                              }}>
                                {policy.description}
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </>
            )}
          </>
        )}

        {/* Import Results */}
        {importResults && (
          <div style={{ display: 'grid', gap: 16 }}>
            {importResults.success.length > 0 && (
              <div style={{
                padding: '16px 20px', borderRadius: 'var(--radius-md)',
                background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.2)',
              }}>
                <div style={{ fontWeight: 600, fontSize: 14, color: 'var(--accent-success)', marginBottom: 8 }}>
                  {t('policyImport.successfullyImportedCount', { count: importResults.success.length, plural: importResults.success.length > 1 ? 's' : '' })}
                </div>
                {importResults.success.map((r, i) => (
                  <div key={i} className="small" style={{ marginTop: 4, display: 'flex', alignItems: 'center', gap: 6 }}>
                    <i className="fa-solid fa-check" style={{ fontSize: 14, color: 'var(--accent-success)' }}></i>
                    {r.policyName}
                  </div>
                ))}
              </div>
            )}
            {importResults.failed.length > 0 && (
              <div style={{
                padding: '16px 20px', borderRadius: 'var(--radius-md)',
                background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)',
              }}>
                <div style={{ fontWeight: 600, fontSize: 14, color: 'var(--accent-danger)', marginBottom: 8 }}>
                  {t('policyImport.importsFailedCount', { count: importResults.failed.length, plural: importResults.failed.length > 1 ? 's' : '' })}
                </div>
                {importResults.failed.map((r, i) => (
                  <div key={i} className="small" style={{ marginTop: 4, color: 'var(--accent-danger)' }}>
                    <strong>{r.fileName}:</strong> {r.error}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* Footer */}
      <div style={{ display: 'flex', gap: 12, marginTop: 24, justifyContent: 'flex-end', borderTop: '1px solid var(--border-primary)', paddingTop: 16 }}>
        {importResults ? (
          <button className="btn btn-primary" onClick={handleClose}>{t('policyImport.done')}</button>
        ) : (
          <>
            <button className="btn btn-secondary" onClick={handleClose}>{t('common.cancel')}</button>
            {previews.length > 0 && !loading && (
              <button className="btn btn-primary" onClick={handleImport} disabled={importing}>
                {importing
                  ? t('policyImport.importingCount', { count: previews.length })
                  : t('policyImport.importCountButton', { count: previews.length, plural: previews.length > 1 ? 's' : '' })
                }
              </button>
            )}
          </>
        )}
      </div>
    </Modal>
  );
};

const thStyle = { padding: '8px 10px', textAlign: 'left', fontWeight: 600, fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.4, color: 'var(--text-tertiary)' };
const tdStyle = { padding: '7px 10px', verticalAlign: 'middle' };

export default PolicyImportModal;
