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

const AlertModal = () => {
  const { modal, hideModal } = useAlert();
  const { t } = useT();

  if (!modal) return null;

  const getIconClass = (type) => {
    switch (type) {
      case AlertTypes.SUCCESS:
        return 'fa-check-circle text-success';
      case AlertTypes.ERROR:
        return 'fa-times-circle text-danger';
      case AlertTypes.WARNING:
        return 'fa-exclamation-triangle text-warning';
      case AlertTypes.INFO:
        return 'fa-info-circle text-info';
      default:
        return 'fa-bell text-secondary';
    }
  };

  return (
    <>
      <div className="modal fade show d-block" tabIndex="-1" role="dialog">
        <div className="modal-dialog modal-dialog-centered">
          <div className="modal-content alert-modal">
            <div className="modal-body text-center py-5">
              <div className={`alert-icon ${modal.type}`}>
                <i className={`fas ${getIconClass(modal.type)}`}></i>
              </div>
              {modal.title && <h4 className="mb-3">{modal.title}</h4>}
              <p className="text-muted mb-4">{modal.message}</p>
              <button
                type="button"
                className="btn btn-primary px-4"
                onClick={hideModal}
              >
                {t('common.ok')}
              </button>
            </div>
          </div>
        </div>
      </div>
      <div className="modal-backdrop fade show"></div>
    </>
  );
};

export default AlertModal;
