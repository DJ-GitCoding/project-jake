/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

/*
 * Global alert system - supports inline alerts, modals, and toasts
 */

import React, { createContext, useContext, useState, useCallback } from 'react';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

const AlertContext = createContext(null);

/**
 * Alert Provider - Wrap your app with this to enable global alerts
 */
export const AlertProvider = ({ children }) => {
  const { t } = useT();
  const [alerts, setAlerts] = useState([]);
  const [modalAlert, setModalAlert] = useState(null);

  // Generate unique ID for alerts
  const generateId = () => `alert-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

  /**
   * Show an alert
   * @param {Object} options Alert options
   * @param {string} options.type - 'success' | 'error' | 'warning' | 'info'
   * @param {string} options.message - Main message
   * @param {string} options.title - Optional title
   * @param {Object} options.details - Optional details object
   * @param {boolean} options.modal - Show as modal dialog
   * @param {boolean} options.toast - Show as toast notification
   * @param {boolean} options.dismissable - Whether modal can be dismissed without confirm (default: true)
   * @param {number} options.duration - Auto-dismiss duration in ms (0 = no auto-dismiss)
   * @param {Function} options.onConfirm - Callback for confirm button
   * @param {Function} options.onCancel - Callback for cancel/dismiss
   * @param {Function} options.onRetry - Callback for retry button
   * @param {string} options.confirmText - Text for confirm button
   * @param {string} options.cancelText - Text for cancel button
   * @param {string} options.retryText - Text for retry button
   */
  const showAlert = useCallback((options) => {
    const {
      type = 'info',
      message,
      title,
      details,
      modal = false,
      toast: useToast = false,
      dismissable = true,
      duration,
      onConfirm,
      onCancel,
      onRetry,
      confirmText,
      cancelText,
      retryText
    } = options;

    // Toast notification
    if (useToast) {
      const toastOptions = {
        duration: duration || (type === 'error' ? 5000 : 3000),
        position: 'top-right',
      };

      switch (type) {
        case 'success':
          toast.success(message, toastOptions);
          break;
        case 'error':
          toast.error(message, toastOptions);
          break;
        default:
          toast(message, {
            ...toastOptions,
            icon: type === 'warning' ? '⚠️' : 'ℹ️',
          });
      }
      return null;
    }

    // Modal alert
    if (modal) {
      const id = generateId();
      setModalAlert({
        id,
        type,
        message,
        title,
        details,
        dismissable,
        onConfirm,
        onCancel,
        onRetry,
        confirmText,
        cancelText,
        retryText
      });
      return id;
    }

    // Inline alert
    const id = generateId();
    const alert = {
      id,
      type,
      message,
      title,
      details,
      onConfirm,
      onCancel,
      onRetry,
      confirmText,
      cancelText,
      retryText
    };

    setAlerts(prev => [...prev, alert]);

    // Auto-dismiss for success/info
    const autoDismiss = duration !== undefined ? duration : 
                        (type === 'success' ? 5000 : type === 'info' ? 7000 : 0);
    
    if (autoDismiss > 0) {
      setTimeout(() => {
        dismissAlert(id);
      }, autoDismiss);
    }

    return id;
  }, []);

  // Convenience methods
  const showSuccess = useCallback((message, options = {}) => {
    return showAlert({ type: 'success', message, toast: true, ...options });
  }, [showAlert]);

  const showError = useCallback((message, options = {}) => {
    return showAlert({ type: 'error', message, ...options });
  }, [showAlert]);

  const showWarning = useCallback((message, options = {}) => {
    return showAlert({ type: 'warning', message, ...options });
  }, [showAlert]);

  const showInfo = useCallback((message, options = {}) => {
    return showAlert({ type: 'info', message, ...options });
  }, [showAlert]);

  /**
   * Show confirmation modal
   */
  const showConfirm = useCallback((message, options = {}) => {
    return new Promise((resolve) => {
      showAlert({
        type: 'warning',
        message,
        modal: true,
        confirmText: options.confirmText || t('alertContext.confirm'),
        onConfirm: () => {
          setModalAlert(null);
          resolve(true);
        },
        onCancel: () => {
          setModalAlert(null);
          resolve(false);
        },
        ...options
      });
    });
  }, [showAlert, t]);

  /**
   * Dismiss an alert by ID
   */
  const dismissAlert = useCallback((id) => {
    setAlerts(prev => prev.filter(alert => alert.id !== id));
  }, []);

  /**
   * Dismiss modal alert
   * Respects dismissable flag - if not dismissable, does nothing unless force=true
   */
  const dismissModal = useCallback((force = false) => {
    if (!modalAlert) return;

    // If modal is not dismissable and not forced, do nothing
    if (!modalAlert.dismissable && !force) {
      return;
    }

    if (modalAlert.onCancel) {
      modalAlert.onCancel();
    }
    setModalAlert(null);
  }, [modalAlert]);

  /**
   * Clear all alerts
   */
  const clearAlerts = useCallback(() => {
    setAlerts([]);
    // Only clear modal if it's dismissable
    if (modalAlert?.dismissable !== false) {
      if (modalAlert?.onCancel) {
        modalAlert.onCancel();
      }
      setModalAlert(null);
    }
  }, [modalAlert]);

  const value = {
    alerts,
    modalAlert,
    showAlert,
    showSuccess,
    showError,
    showWarning,
    showInfo,
    showConfirm,
    dismissAlert,
    dismissModal,
    clearAlerts
  };

  return (
    <AlertContext.Provider value={value}>
      {children}
    </AlertContext.Provider>
  );
};

/**
 * Hook to use the alert system
 */
export const useAlert = () => {
  const context = useContext(AlertContext);
  if (!context) {
    throw new Error('useAlert must be used within an AlertProvider');
  }
  return context;
};

export default AlertContext;