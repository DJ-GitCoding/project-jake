/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { createContext, useContext, useState, useCallback } from 'react';

const AlertContext = createContext(null);

export const AlertTypes = {
  SUCCESS: 'success',
  ERROR: 'error',
  WARNING: 'warning',
  INFO: 'info',
};

export const AlertProvider = ({ children }) => {
  const [alerts, setAlerts] = useState([]);
  const [modal, setModal] = useState(null);
  const [confirmModal, setConfirmModal] = useState(null);

  // Add a toast notification
  const addAlert = useCallback((type, message, duration = 5000) => {
    const id = Date.now() + Math.random();
    const alert = { id, type, message };
    
    setAlerts((prev) => [...prev, alert]);

    if (duration > 0) {
      setTimeout(() => {
        removeAlert(id);
      }, duration);
    }

    return id;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Remove a toast notification
  const removeAlert = useCallback((id) => {
    setAlerts((prev) => prev.filter((alert) => alert.id !== id));
  }, []);

  // Shorthand methods for toast notifications
  const success = useCallback((message, duration) => {
    return addAlert(AlertTypes.SUCCESS, message, duration);
  }, [addAlert]);

  const error = useCallback((message, duration) => {
    return addAlert(AlertTypes.ERROR, message, duration);
  }, [addAlert]);

  const warning = useCallback((message, duration) => {
    return addAlert(AlertTypes.WARNING, message, duration);
  }, [addAlert]);

  const info = useCallback((message, duration) => {
    return addAlert(AlertTypes.INFO, message, duration);
  }, [addAlert]);

  // Show modal alert (centered modal)
  const showModal = useCallback((type, title, message, onClose) => {
    setModal({ type, title, message, onClose });
  }, []);

  // Hide modal alert
  const hideModal = useCallback(() => {
    if (modal?.onClose) {
      modal.onClose();
    }
    setModal(null);
  }, [modal]);

  // Show confirmation modal
  const confirm = useCallback((title, message, onConfirm, onCancel, options = {}) => {
    return new Promise((resolve) => {
      setConfirmModal({
        title,
        message,
        confirmText: options.confirmText || 'Confirm',
        cancelText: options.cancelText || 'Cancel',
        confirmVariant: options.confirmVariant || 'danger',
        icon: options.icon || 'fa-question-circle',
        iconColor: options.iconColor || 'warning',
        onConfirm: () => {
          setConfirmModal(null);
          if (onConfirm) onConfirm();
          resolve(true);
        },
        onCancel: () => {
          setConfirmModal(null);
          if (onCancel) onCancel();
          resolve(false);
        },
      });
    });
  }, []);

  // Hide confirmation modal
  const hideConfirm = useCallback(() => {
    if (confirmModal?.onCancel) {
      confirmModal.onCancel();
    }
    setConfirmModal(null);
  }, [confirmModal]);

  const value = {
    alerts,
    modal,
    confirmModal,
    addAlert,
    removeAlert,
    success,
    error,
    warning,
    info,
    showModal,
    hideModal,
    confirm,
    hideConfirm,
  };

  return (
    <AlertContext.Provider value={value}>
      {children}
    </AlertContext.Provider>
  );
};

export const useAlert = () => {
  const context = useContext(AlertContext);
  if (!context) {
    throw new Error('useAlert must be used within an AlertProvider');
  }
  return context;
};

export default AlertContext;
