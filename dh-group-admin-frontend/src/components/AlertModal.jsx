/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useCallback, useEffect } from 'react';
import { useT } from '../i18n';

// ─── Alert Modal (replaces window.alert) ──────────────────────────────

const ICONS = {
  error: { icon: 'fa-circle-exclamation', color: '#dc3545' },
  warning: { icon: 'fa-triangle-exclamation', color: '#ffc107' },
  info: { icon: 'fa-circle-info', color: '#0d6efd' },
  success: { icon: 'fa-circle-check', color: '#198754' },
};

export const AlertModal = ({ show, onClose, title, message, type = 'error' }) => {
  const { t } = useT();
  useEffect(() => {
    if (!show) return;
    const handler = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [show, onClose]);

  if (!show) return null;

  const { icon, color } = ICONS[type] || ICONS.error;

  return (
    <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)', zIndex: 1080 }} onClick={onClose}>
      <div className="modal-dialog modal-dialog-centered" style={{ maxWidth: 440 }} onClick={e => e.stopPropagation()}>
        <div className="modal-content">
          <div className="modal-body text-center py-4">
            <i className={`fa-solid ${icon} mb-3`} style={{ fontSize: 48, color }} />
            {title && <h5 className="mb-2">{title}</h5>}
            <p className="text-muted mb-0" style={{ whiteSpace: 'pre-line' }}>{message}</p>
          </div>
          <div className="modal-footer border-0 justify-content-center pt-0 pb-3">
            <button className="btn btn-primary px-4" onClick={onClose} autoFocus>{t('common.ok')}</button>
          </div>
        </div>
      </div>
    </div>
  );
};

// ─── Confirm Modal (replaces window.confirm) ──────────────────────────

export const ConfirmModal = ({ show, onConfirm, onCancel, title, message, type = 'warning', confirmLabel, cancelLabel, confirmVariant = 'danger' }) => {
  const { t } = useT();
  const confirmText = confirmLabel ?? t('common.confirm');
  const cancelText = cancelLabel ?? t('common.cancel');
  useEffect(() => {
    if (!show) return;
    const handler = (e) => { if (e.key === 'Escape') onCancel(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [show, onCancel]);

  if (!show) return null;

  const { icon, color } = ICONS[type] || ICONS.warning;

  return (
    <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)', zIndex: 1080 }} onClick={onCancel}>
      <div className="modal-dialog modal-dialog-centered" style={{ maxWidth: 440 }} onClick={e => e.stopPropagation()}>
        <div className="modal-content">
          <div className="modal-body text-center py-4">
            <i className={`fa-solid ${icon} mb-3`} style={{ fontSize: 48, color }} />
            {title && <h5 className="mb-2">{title}</h5>}
            <p className="text-muted mb-0" style={{ whiteSpace: 'pre-line' }}>{message}</p>
          </div>
          <div className="modal-footer border-0 justify-content-center pt-0 pb-3 gap-2">
            <button className="btn btn-secondary px-3" onClick={onCancel}>{cancelText}</button>
            <button className={`btn btn-${confirmVariant} px-3`} onClick={onConfirm} autoFocus>{confirmText}</button>
          </div>
        </div>
      </div>
    </div>
  );
};

// ─── Hook for easy usage ──────────────────────────────────────────────

/**
 * useAlert() — drop-in replacement for window.alert
 * 
 * const { alert, AlertDialog } = useAlert();
 * await alert('Something went wrong', 'Error Title', 'error');
 * return <>{AlertDialog}...</>
 */
export const useAlert = () => {
  const [state, setState] = useState({ show: false, message: '', title: '', type: 'error', resolve: null });

  const alert = useCallback((message, title = '', type = 'error') => {
    return new Promise((resolve) => {
      setState({ show: true, message, title, type, resolve });
    });
  }, []);

  const close = useCallback(() => {
    state.resolve?.();
    setState(s => ({ ...s, show: false }));
  }, [state.resolve]);

  const AlertDialog = (
    <AlertModal show={state.show} onClose={close} title={state.title} message={state.message} type={state.type} />
  );

  return { alert, AlertDialog };
};

/**
 * useConfirm() — drop-in replacement for window.confirm
 * 
 * const { confirm, ConfirmDialog } = useConfirm();
 * const ok = await confirm('Are you sure?', 'Confirm Action');
 * return <>{ConfirmDialog}...</>
 */
export const useConfirm = () => {
  const [state, setState] = useState({ show: false, message: '', title: '', type: 'warning', confirmLabel: undefined, cancelLabel: undefined, confirmVariant: 'danger', resolve: null });

  const confirm = useCallback((message, title = '', options = {}) => {
    return new Promise((resolve) => {
      setState({
        show: true, message, title,
        type: options.type || 'warning',
        confirmLabel: options.confirmLabel,
        cancelLabel: options.cancelLabel,
        confirmVariant: options.confirmVariant || 'danger',
        resolve,
      });
    });
  }, []);

  const handleConfirm = useCallback(() => {
    state.resolve?.(true);
    setState(s => ({ ...s, show: false }));
  }, [state.resolve]);

  const handleCancel = useCallback(() => {
    state.resolve?.(false);
    setState(s => ({ ...s, show: false }));
  }, [state.resolve]);

  const ConfirmDialog = (
    <ConfirmModal show={state.show} onConfirm={handleConfirm} onCancel={handleCancel}
      title={state.title} message={state.message} type={state.type}
      confirmLabel={state.confirmLabel} cancelLabel={state.cancelLabel} confirmVariant={state.confirmVariant} />
  );

  return { confirm, ConfirmDialog };
};
