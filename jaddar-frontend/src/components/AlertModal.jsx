/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

/*
 * Global alert display component - renders alerts from AlertContext
 */

import React, { useEffect } from 'react';
import { useAlert } from '../contexts/AlertContext';
import { useT } from '../i18n';

/**
 * Alert Container - Place this once in your app layout
 * Renders all inline alerts and the modal alert
 */
export const AlertContainer = () => {
  const { alerts, modalAlert, dismissAlert, dismissModal } = useAlert();

  return (
    <>
      {/* Inline Alerts Container */}
      {alerts.length > 0 && (
        <div className="alert-container" style={{
          position: 'fixed',
          top: '80px',
          right: '20px',
          zIndex: 1040,
          maxWidth: '450px',
          width: '100%'
        }}>
          {alerts.map(alert => (
            <AlertItem
              key={alert.id}
              alert={alert}
              onClose={() => dismissAlert(alert.id)}
            />
          ))}
        </div>
      )}

      {/* Modal Alert */}
      {modalAlert && (
        <ModalAlert
          alert={modalAlert}
          onClose={dismissModal}
        />
      )}
    </>
  );
};

/**
 * Shared details block for both inline and modal alerts.
 * Shows query info, server URL, HTTP status, and server response details.
 */
const AlertDetails = ({ details }) => {
  const { t } = useT();
  if (!details) return null;

  return (
    <div className="small text-muted mt-1">
      {details.queryValue && (
        <div className="mb-1">
          <span className="fw-semibold">{t('alertModal.details.query')}</span>{' '}
          <code>{details.queryValue}</code>
          {details.queryType && <span className="ms-1">({details.queryType})</span>}
        </div>
      )}
      {details.rdapServer && (
        <div className="mb-1">
          <span className="fw-semibold">{t('alertModal.details.server')}</span>{' '}
          <code className="text-break">{details.rdapServer}</code>
        </div>
      )}
      {details.source && !details.rdapServer && (
        <div className="mb-1">
          <span className="fw-semibold">{t('alertModal.details.source')}</span> {details.source}
        </div>
      )}
      {details.httpStatusCode && (
        <div className="mb-1">
          <span className="fw-semibold">{t('alertModal.details.httpStatus')}</span>{' '}
          <span className="badge bg-secondary">{details.httpStatusCode}</span>
        </div>
      )}
      {details.errorCode && !details.httpStatusCode && (
        <div className="mb-1">
          <span className="fw-semibold">{t('alertModal.details.errorCode')}</span> {details.errorCode}
        </div>
      )}
      {details.serverMessage && (
        <div className="mb-1">
          <span className="fw-semibold">{t('alertModal.details.serverResponse')}</span>{' '}
          <span className="fst-italic">{details.serverMessage}</span>
        </div>
      )}
      {details.serverSignature && (
        <div className="mb-1 text-muted" style={{ fontSize: '0.75rem' }}>
          {details.serverSignature}
        </div>
      )}
    </div>
  );
};

/**
 * Single Alert Item (inline)
 */
const AlertItem = ({ alert, onClose }) => {
  const { t } = useT();
  const config = getAlertConfig(alert.type);

  return (
    <div 
      className={`alert alert-${config.alertClass} alert-dismissible fade show shadow-sm mb-2`} 
      role="alert"
      style={{ animation: 'slideIn 0.3s ease-out' }}
    >
      <div className="d-flex align-items-start">
        <i className={`bi ${config.icon} me-2 fs-5`}></i>
        <div className="flex-grow-1">
          {alert.title && <h6 className="alert-heading mb-1">{alert.title}</h6>}
          <p className="mb-1">{alert.message}</p>
          
          <AlertDetails details={alert.details} />

          {(alert.onRetry || alert.onConfirm) && (
            <div className="mt-2">
              {alert.onRetry && (
                <button 
                  className={`btn btn-sm btn-outline-dark`} 
                  onClick={() => {
                    alert.onRetry();
                    onClose();
                  }}
                >
                  <i className="bi bi-arrow-clockwise me-1"></i>
                  {alert.retryText || t('alertModal.tryAgain')}
                </button>
              )}
              {alert.onConfirm && (
                <button
                  className={`btn btn-sm btn-${config.alertClass}`}
                  onClick={() => {
                    alert.onConfirm();
                    onClose();
                  }}
                >
                  {alert.confirmText || t('alertModal.ok')}
                </button>
              )}
            </div>
          )}
        </div>
        <button type="button" className="btn-close" onClick={onClose} aria-label={t('alertModal.close')}></button>
      </div>
    </div>
  );
};

/**
 * Modal Alert Dialog
 * Supports `dismissable` flag - when false, backdrop click, escape key,
 * close button, and the "Close" footer button are all disabled.
 */
const ModalAlert = ({ alert, onClose }) => {
  const { t } = useT();
  const config = getAlertConfig(alert.type);
  const isDismissable = alert.dismissable !== false;

  /*
   * Map alert type to a translated default title key, mirroring getAlertConfig's
   * fallback to 'error' for unknown types.
   */
  const defaultTitleKeys = {
    success: 'success',
    error: 'error',
    warning: 'warning',
    info: 'info',
    '404': 'notFound',
    '401': 'authRequired',
    '403': 'accessDenied',
    '503': 'serviceUnavailable',
    '504': 'requestTimeout',
  };
  const defaultTitle = t(`alertModal.defaultTitle.${defaultTitleKeys[alert.type] || 'error'}`);

  const handleConfirm = () => {
    if (alert.onConfirm) {
      alert.onConfirm();
    } else if (isDismissable) {
      onClose();
    }
  };

  const handleBackdropClick = () => {
    if (isDismissable) {
      onClose();
    }
  };

  // Block escape key when not dismissable
  useEffect(() => {
    if (isDismissable) return;

    const handleKeyDown = (e) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
      }
    };

    document.addEventListener('keydown', handleKeyDown, true);
    return () => document.removeEventListener('keydown', handleKeyDown, true);
  }, [isDismissable]);

  return (
    <>
      <div 
        className="modal-backdrop fade show" 
        style={{ zIndex: 1050 }}
        onClick={handleBackdropClick}
      />
      <div 
        className="modal fade show d-block" 
        tabIndex="-1" 
        role="dialog"
        style={{ zIndex: 1055 }}
      >
        <div className="modal-dialog modal-dialog-centered">
          <div className="modal-content">
            <div className={`modal-header bg-${config.alertClass} ${config.textClass}`}>
              <h5 className="modal-title">
                <i className={`bi ${config.icon} me-2`}></i>
                {alert.title || defaultTitle}
              </h5>
              {isDismissable && (
                <button
                  type="button"
                  className={`btn-close ${config.alertClass !== 'warning' ? 'btn-close-white' : ''}`}
                  onClick={onClose}
                  aria-label={t('alertModal.close')}
                />
              )}
            </div>
            <div className="modal-body">
              <p className="mb-2">{alert.message}</p>
              
              {alert.details && (
                <div className="border-top pt-2 mt-2">
                  <AlertDetails details={alert.details} />
                </div>
              )}
            </div>
            <div className="modal-footer">
              {alert.onRetry && (
                <button 
                  type="button" 
                  className="btn btn-outline-secondary"
                  onClick={() => {
                    alert.onRetry();
                    onClose();
                  }}
                >
                  <i className="bi bi-arrow-clockwise me-1"></i>
                  {alert.retryText || t('alertModal.tryAgain')}
                </button>
              )}
              {isDismissable && (
                <button type="button" className="btn btn-secondary" onClick={onClose}>
                  {alert.cancelText || t('alertModal.close')}
                </button>
              )}
              {alert.onConfirm && (
                <button
                  type="button"
                  className={`btn btn-${config.alertClass}`}
                  onClick={handleConfirm}
                >
                  {alert.confirmText || t('alertModal.ok')}
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

/**
 * Standalone Alert Component (for use outside context)
 */
const AlertModal = ({
  show = true,
  type = 'error',
  title,
  message,
  details,
  modal = false,
  dismissable = true,
  onClose,
  onRetry,
  onConfirm,
  confirmText = 'OK',
  retryText = 'Try Again',
  cancelText = 'Close',
}) => {
  if (!show) return null;

  const alert = { type, title, message, details, dismissable, onRetry, onConfirm, confirmText, retryText, cancelText };

  if (modal) {
    return <ModalAlert alert={alert} onClose={onClose} />;
  }

  return <AlertItem alert={alert} onClose={onClose} />;
};

/**
 * Get Bootstrap config for alert type
 */
function getAlertConfig(type) {
  const configs = {
    success: { alertClass: 'success', icon: 'bi-check-circle', defaultTitle: 'Success', textClass: 'text-white' },
    error: { alertClass: 'danger', icon: 'bi-exclamation-triangle', defaultTitle: 'Error', textClass: 'text-white' },
    warning: { alertClass: 'warning', icon: 'bi-exclamation-circle', defaultTitle: 'Warning', textClass: 'text-dark' },
    info: { alertClass: 'info', icon: 'bi-info-circle', defaultTitle: 'Information', textClass: 'text-white' },
    '404': { alertClass: 'warning', icon: 'bi-search', defaultTitle: 'Not Found', textClass: 'text-dark' },
    '401': { alertClass: 'info', icon: 'bi-lock', defaultTitle: 'Authentication Required', textClass: 'text-white' },
    '403': { alertClass: 'danger', icon: 'bi-x-circle', defaultTitle: 'Access Denied', textClass: 'text-white' },
    '503': { alertClass: 'secondary', icon: 'bi-cloud-slash', defaultTitle: 'Service Unavailable', textClass: 'text-white' },
    '504': { alertClass: 'secondary', icon: 'bi-hourglass', defaultTitle: 'Request Timeout', textClass: 'text-white' },
  };

  return configs[type] || configs.error;
}

export default AlertModal;