/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { useAlert } from '../context/AlertContext';

const ConfirmModal = () => {
  const { confirmModal } = useAlert();

  if (!confirmModal) return null;

  const {
    title,
    message,
    confirmText,
    cancelText,
    confirmVariant,
    icon,
    iconColor,
    onConfirm,
    onCancel,
  } = confirmModal;

  return (
    <>
      <div className="modal fade show d-block" tabIndex="-1" role="dialog">
        <div className="modal-dialog modal-dialog-centered">
          <div className="modal-content confirm-modal">
            <div className="modal-body">
              <div className={`confirm-icon text-${iconColor}`}>
                <i className={`fas ${icon}`}></i>
              </div>
              <h4 className="mb-3">{title}</h4>
              <p className="text-muted mb-4">{message}</p>
              <div className="d-flex justify-content-center gap-2">
                <button
                  type="button"
                  className="btn btn-secondary px-4"
                  onClick={onCancel}
                >
                  {cancelText}
                </button>
                <button
                  type="button"
                  className={`btn btn-${confirmVariant} px-4`}
                  onClick={onConfirm}
                >
                  {confirmText}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div className="modal-backdrop fade show"></div>
    </>
  );
};

export default ConfirmModal;
