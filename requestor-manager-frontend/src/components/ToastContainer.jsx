/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { useAlert, AlertTypes } from '../context/AlertContext';
import { useT } from '../i18n';

const ToastContainer = () => {
  const { alerts, removeAlert } = useAlert();
  const { t } = useT();

  const getToastClass = (type) => {
    switch (type) {
      case AlertTypes.SUCCESS:
        return 'bg-success';
      case AlertTypes.ERROR:
        return 'bg-danger';
      case AlertTypes.WARNING:
        return 'bg-warning text-dark';
      case AlertTypes.INFO:
        return 'bg-info';
      default:
        return 'bg-secondary';
    }
  };

  const getIcon = (type) => {
    switch (type) {
      case AlertTypes.SUCCESS:
        return 'fa-check-circle';
      case AlertTypes.ERROR:
        return 'fa-times-circle';
      case AlertTypes.WARNING:
        return 'fa-exclamation-triangle';
      case AlertTypes.INFO:
        return 'fa-info-circle';
      default:
        return 'fa-bell';
    }
  };

  if (alerts.length === 0) return null;

  return (
    <div className="toast-container">
      {alerts.map((alert) => (
        <div
          key={alert.id}
          className={`toast show ${getToastClass(alert.type)} text-white`}
          role="alert"
          aria-live="assertive"
          aria-atomic="true"
        >
          <div className="toast-body d-flex align-items-center">
            <i className={`fas ${getIcon(alert.type)} me-2`}></i>
            <span className="flex-grow-1">{alert.message}</span>
            <button
              type="button"
              className="btn-close btn-close-white ms-2"
              onClick={() => removeAlert(alert.id)}
              aria-label={t('common.close')}
            ></button>
          </div>
        </div>
      ))}
    </div>
  );
};

export default ToastContainer;
