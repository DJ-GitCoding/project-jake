/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import { getFilesForRequest, getFileViewUrl, getFileDownloadUrl, downloadAllFiles } from '../services/api';
import { useT } from '../i18n';

/**
 * FileViewer component - displays files attached to an RDAP request.
 * Used in PendingRequests review modals so manual reviewers can see uploaded documents.
 * 
 * Props:
 *   requestId: UUID string of the request
 *   compact: boolean - if true, shows a compact inline view
 *   onApprove: function(requestId) - optional callback to approve the request
 *   onDeny: function(requestId) - optional callback to deny the request
 *   showActions: boolean - whether to show approve/deny actions in the examine panel
 *   requestStatus: string - current status of the request (PENDING, APPROVED, DENIED, etc.)
 */
const FileViewer = ({ requestId, compact = false, onApprove, onDeny, showActions = false, requestStatus }) => {
  const [files, setFiles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [examineFile, setExamineFile] = useState(null);
  const { t } = useT();

  useEffect(() => {
    if (requestId) {
      loadFiles();
    }
  }, [requestId]);

  const loadFiles = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await getFilesForRequest(requestId);
      setFiles(response.data?.files || []);
    } catch (err) {
      console.error('Failed to load files:', err);
      setError(t('fileViewer.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  const getFileIcon = (fileType, size = '1em') => {
    const s = { fontSize: size };
    switch (fileType) {
      case 'PDF': return <i className="fa-solid fa-file-pdf" style={{ ...s, color: '#ef4444' }} />;
      case 'DOCX': return <i className="fa-solid fa-file-word" style={{ ...s, color: '#2563eb' }} />;
      case 'XLSX': return <i className="fa-solid fa-file-excel" style={{ ...s, color: '#16a34a' }} />;
      case 'JPEG':
      case 'PNG': return <i className="fa-solid fa-file-image" style={{ ...s, color: '#8b5cf6' }} />;
      case 'TXT': return <i className="fa-solid fa-file-lines" style={{ ...s, color: '#6b7280' }} />;
      default: return <i className="fa-solid fa-paperclip" style={s} />;
    }
  };

  const getFileTypeBadgeColor = (fileType) => {
    switch (fileType) {
      case 'PDF': return { bg: '#fef2f2', color: '#dc2626', border: '#fecaca' };
      case 'DOCX': return { bg: '#eff6ff', color: '#2563eb', border: '#bfdbfe' };
      case 'XLSX': return { bg: '#f0fdf4', color: '#16a34a', border: '#bbf7d0' };
      case 'JPEG':
      case 'PNG': return { bg: '#faf5ff', color: '#7c3aed', border: '#ddd6fe' };
      case 'TXT': return { bg: '#f9fafb', color: '#4b5563', border: '#d1d5db' };
      default: return { bg: '#f3f4f6', color: '#6b7280', border: '#e5e7eb' };
    }
  };

  const getScanStatusBadge = (status) => {
    switch (status) {
      case 'CLEAN':
        return <span className="badge bg-success-subtle text-success" style={{ fontSize: '10px', padding: '2px 6px' }}><i className="fa-solid fa-check" style={{ fontSize: '9px' }} /> {t('fileViewer.scanStatus.clean')}</span>;
      case 'MALICIOUS':
        return <span className="badge bg-danger-subtle text-danger" style={{ fontSize: '10px', padding: '2px 6px' }}><i className="fa-solid fa-triangle-exclamation" style={{ fontSize: '9px' }} /> {t('fileViewer.scanStatus.malicious')}</span>;
      case 'SUSPICIOUS':
        return <span className="badge bg-warning-subtle text-warning" style={{ fontSize: '10px', padding: '2px 6px' }}><i className="fa-solid fa-triangle-exclamation" style={{ fontSize: '9px' }} /> {t('fileViewer.scanStatus.suspicious')}</span>;
      case 'PENDING':
        return <span className="badge bg-info-subtle text-info" style={{ fontSize: '10px', padding: '2px 6px' }}>{t('fileViewer.scanStatus.pending')}</span>;
      case 'ERROR':
        return <span className="badge bg-danger-subtle text-danger" style={{ fontSize: '10px', padding: '2px 6px' }}>{t('fileViewer.scanStatus.error')}</span>;
      default:
        return <span className="badge bg-info-subtle text-info" style={{ fontSize: '10px', padding: '2px 6px' }}>{status}</span>;
    }
  };

  const canPreview = (fileType) => {
    return ['PDF', 'JPEG', 'PNG', 'TXT'].includes(fileType);
  };

  const handleDownloadAll = () => {
    const url = downloadAllFiles(requestId);
    window.open(url, '_blank');
  };

  if (loading) {
    return (
      <div style={{ padding: '12px', color: 'var(--text-tertiary)', fontSize: '13px' }}>
        <i className="fa-solid fa-spinner fa-spin" style={{ marginRight: '8px' }} />
        {t('fileViewer.loadingAttachments')}
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: '12px', color: 'var(--accent-danger)', fontSize: '13px' }}>
        {error}
      </div>
    );
  }

  if (files.length === 0) {
    return null;
  }

  return (
    <>
      <div style={{
        border: '1px solid var(--border-primary)',
        borderRadius: 'var(--radius-md, 8px)',
        overflow: 'hidden',
        marginTop: compact ? '8px' : '0'
      }}>
        {/* Header with file count and Download All */}
        <div style={{
          padding: '10px 14px',
          background: 'var(--bg-tertiary)',
          borderBottom: '1px solid var(--border-primary)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '8px',
          fontSize: '13px',
          fontWeight: 600,
          color: 'var(--text-primary)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <i className="fa-solid fa-paperclip" style={{ fontSize: '14px' }} />
            {t('fileViewer.attachedFiles', { count: files.length })}
          </div>
          {files.length > 0 && (
            <button
              className="btn btn-sm"
              onClick={handleDownloadAll}
              title={t('fileViewer.downloadAllTitle')}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                fontSize: '12px',
                padding: '4px 10px',
                background: 'var(--bg-primary)',
                border: '1px solid var(--border-primary)',
                borderRadius: 'var(--radius-sm, 4px)',
                color: 'var(--text-secondary)',
                cursor: 'pointer',
                fontWeight: 500,
              }}
            >
              <i className="fa-solid fa-download" style={{ fontSize: '11px' }} />
              {t('fileViewer.downloadAll')}
            </button>
          )}
        </div>

        {/* Scrollable file list */}
        <div style={{
          maxHeight: files.length > 4 ? '280px' : 'none',
          overflowY: files.length > 4 ? 'auto' : 'visible',
        }}>
          {files.map((file) => {
            const colors = getFileTypeBadgeColor(file.fileType);
            const isMalicious = file.scanStatus === 'MALICIOUS';

            return (
              <div
                key={file.fileId}
                style={{
                  padding: '10px 14px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  borderBottom: '1px solid var(--border-primary)',
                  background: isMalicious ? 'rgba(239, 68, 68, 0.05)' : 'transparent',
                  opacity: isMalicious ? 0.7 : 1,
                  transition: 'background 0.15s ease',
                }}
              >
                {/* File Type Icon */}
                <div style={{ fontSize: '20px', flexShrink: 0 }}>
                  {getFileIcon(file.fileType)}
                </div>

                {/* File Info */}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{
                    fontSize: '13px',
                    fontWeight: 500,
                    color: 'var(--text-primary)',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis'
                  }}>
                    {file.originalFilename}
                  </div>
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    marginTop: '2px',
                    flexWrap: 'wrap'
                  }}>
                    <span style={{
                      fontSize: '10px',
                      fontWeight: 700,
                      padding: '1px 6px',
                      borderRadius: '4px',
                      background: colors.bg,
                      color: colors.color,
                      border: `1px solid ${colors.border}`,
                      letterSpacing: '0.5px'
                    }}>
                      {file.fileType}
                    </span>
                    <span style={{ fontSize: '11px', color: 'var(--text-tertiary)' }}>
                      {file.humanReadableSize}
                    </span>
                    {getScanStatusBadge(file.scanStatus)}
                  </div>
                </div>

                {/* Examine button */}
                <div style={{ flexShrink: 0 }}>
                  {isMalicious ? (
                    <span style={{ fontSize: '11px', color: 'var(--accent-danger)', fontWeight: 600, padding: '4px 8px' }}>
                      <i className="fa-solid fa-triangle-exclamation" /> {t('fileViewer.blocked')}
                    </span>
                  ) : (
                    <button
                      className="btn btn-sm"
                      onClick={() => setExamineFile(file)}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '6px',
                        fontSize: '12px',
                        padding: '5px 12px',
                        background: 'var(--accent-primary, #2563eb)',
                        color: '#fff',
                        border: 'none',
                        borderRadius: 'var(--radius-sm, 4px)',
                        cursor: 'pointer',
                        fontWeight: 500,
                      }}
                    >
                      <i className="fa-solid fa-eye" style={{ fontSize: '11px' }} />
                      {t('fileViewer.examine')}
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* ===== Examine Panel (Full-screen overlay) ===== */}
      {examineFile && (
        <div
          className="file-examine-overlay"
          onClick={() => setExamineFile(null)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.6)',
            backdropFilter: 'blur(4px)',
            zIndex: 2000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            animation: 'fileExamineFadeIn 0.15s ease',
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              width: '95%',
              maxWidth: '960px',
              maxHeight: '92vh',
              background: 'var(--bg-primary, #fff)',
              borderRadius: 'var(--radius-lg, 12px)',
              display: 'flex',
              flexDirection: 'column',
              overflow: 'hidden',
              boxShadow: '0 25px 60px rgba(0,0,0,0.3)',
              animation: 'fileExamineSlideUp 0.2s ease',
            }}
          >
            {/* Examine Header */}
            <div style={{
              padding: '16px 20px',
              borderBottom: '1px solid var(--border-primary)',
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              background: 'var(--bg-secondary, #f8f9fa)',
              flexShrink: 0,
            }}>
              <button
                onClick={() => setExamineFile(null)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px',
                  padding: '6px 10px',
                  fontSize: '13px',
                  fontWeight: 500,
                  color: 'var(--text-secondary)',
                  background: 'var(--bg-primary)',
                  border: '1px solid var(--border-primary)',
                  borderRadius: 'var(--radius-sm, 4px)',
                  cursor: 'pointer',
                }}
              >
                <i className="fa-solid fa-chevron-left" style={{ fontSize: '11px' }} />
                {t('common.back')}
              </button>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                }}>
                  {getFileIcon(examineFile.fileType, '18px')}
                  <span style={{
                    fontSize: '15px',
                    fontWeight: 600,
                    color: 'var(--text-primary)',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}>
                    {examineFile.originalFilename}
                  </span>
                  <span style={{
                    fontSize: '10px',
                    fontWeight: 700,
                    padding: '2px 8px',
                    borderRadius: '4px',
                    background: getFileTypeBadgeColor(examineFile.fileType).bg,
                    color: getFileTypeBadgeColor(examineFile.fileType).color,
                    border: `1px solid ${getFileTypeBadgeColor(examineFile.fileType).border}`,
                  }}>
                    {examineFile.fileType}
                  </span>
                </div>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  marginTop: '4px',
                  fontSize: '12px',
                  color: 'var(--text-tertiary)',
                }}>
                  <span>{examineFile.humanReadableSize}</span>
                  {examineFile.fileHash && (
                    <span>{t('fileViewer.sha256Label')} <code style={{ fontSize: '10px' }}>{examineFile.fileHash.substring(0, 16)}...</code></span>
                  )}
                  {getScanStatusBadge(examineFile.scanStatus)}
                </div>
              </div>
              <button
                onClick={() => setExamineFile(null)}
                style={{
                  width: '32px',
                  height: '32px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  color: 'var(--text-tertiary)',
                  fontSize: '16px',
                  borderRadius: '50%',
                  flexShrink: 0,
                }}
              >
                <i className="fa-solid fa-xmark" />
              </button>
            </div>

            {/* Examine Body — file preview */}
            <div style={{
              flex: 1,
              overflow: 'auto',
              minHeight: 0,
              background: 'var(--bg-tertiary, #f0f0f0)',
            }}>
              {canPreview(examineFile.fileType) ? (
                <>
                  {examineFile.fileType === 'PDF' && (
                    <iframe
                      src={getFileViewUrl(examineFile.fileId)}
                      style={{ width: '100%', height: '100%', border: 'none', minHeight: '500px' }}
                      title={examineFile.originalFilename}
                    />
                  )}
                  {(examineFile.fileType === 'JPEG' || examineFile.fileType === 'PNG') && (
                    <div style={{
                      padding: '24px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      minHeight: '400px',
                    }}>
                      <img
                        src={getFileViewUrl(examineFile.fileId)}
                        alt={examineFile.originalFilename}
                        style={{
                          maxWidth: '100%',
                          maxHeight: '60vh',
                          borderRadius: 'var(--radius-md, 8px)',
                          boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
                        }}
                      />
                    </div>
                  )}
                  {examineFile.fileType === 'TXT' && (
                    <div style={{ padding: '16px' }}>
                      <iframe
                        src={getFileViewUrl(examineFile.fileId)}
                        style={{
                          width: '100%',
                          height: '55vh',
                          border: '1px solid var(--border-primary)',
                          borderRadius: 'var(--radius-sm, 4px)',
                          background: 'var(--bg-primary, #fff)',
                        }}
                        title={examineFile.originalFilename}
                      />
                    </div>
                  )}
                </>
              ) : (
                <div style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: '60px 20px',
                  minHeight: '300px',
                  color: 'var(--text-tertiary)',
                }}>
                  <div style={{ fontSize: '48px', marginBottom: '16px', opacity: 0.5 }}>
                    {getFileIcon(examineFile.fileType, '48px')}
                  </div>
                  <p style={{ fontSize: '14px', marginBottom: '4px' }}>
                    {t('fileViewer.previewUnavailableBefore')} <strong>{examineFile.fileType}</strong> {t('fileViewer.previewUnavailableAfter')}
                  </p>
                  <p style={{ fontSize: '13px', opacity: 0.7 }}>
                    {t('fileViewer.downloadToView')}
                  </p>
                </div>
              )}
            </div>

            {/* Examine Footer — actions */}
            <div style={{
              padding: '14px 20px',
              borderTop: '1px solid var(--border-primary)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '12px',
              background: 'var(--bg-secondary, #f8f9fa)',
              flexShrink: 0,
              flexWrap: 'wrap',
            }}>
              {/* Left side: approve/deny actions */}
              <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                {showActions && requestStatus === 'PENDING' && (
                  <>
                    {onApprove && (
                      <button
                        className="btn btn-success btn-sm"
                        onClick={() => {
                          setExamineFile(null);
                          onApprove(requestId);
                        }}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '6px',
                          fontSize: '13px',
                          padding: '7px 16px',
                          fontWeight: 600,
                        }}
                      >
                        <i className="fa-solid fa-check" style={{ fontSize: '12px' }} />
                        {t('fileViewer.approveRequest')}
                      </button>
                    )}
                    {onDeny && (
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() => {
                          setExamineFile(null);
                          onDeny(requestId);
                        }}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '6px',
                          fontSize: '13px',
                          padding: '7px 16px',
                          fontWeight: 600,
                        }}
                      >
                        <i className="fa-solid fa-xmark" style={{ fontSize: '12px' }} />
                        {t('fileViewer.denyRequest')}
                      </button>
                    )}
                  </>
                )}
                {showActions && requestStatus && requestStatus !== 'PENDING' && (
                  <span style={{ fontSize: '12px', color: 'var(--text-tertiary)', fontStyle: 'italic' }}>
                    {t('fileViewer.alreadyProcessed', { status: requestStatus.toLowerCase() })}
                  </span>
                )}
              </div>

              {/* Right side: download */}
              <a
                href={getFileDownloadUrl(examineFile.fileId)}
                download={examineFile.originalFilename}
                target="_blank"
                rel="noopener noreferrer"
                className="btn btn-primary btn-sm"
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  fontSize: '13px',
                  padding: '7px 16px',
                  textDecoration: 'none',
                  fontWeight: 600,
                }}
              >
                <i className="fa-solid fa-download" style={{ fontSize: '12px' }} />
                {t('common.download')}
              </a>
            </div>
          </div>
        </div>
      )}

      <style>{`
        @keyframes fileExamineFadeIn {
          from { opacity: 0; }
          to { opacity: 1; }
        }
        @keyframes fileExamineSlideUp {
          from { opacity: 0; transform: translateY(12px) scale(0.98); }
          to { opacity: 1; transform: translateY(0) scale(1); }
        }
      `}</style>
    </>
  );
};

export default FileViewer;