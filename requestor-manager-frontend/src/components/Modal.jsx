/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { useT } from '../i18n';

const Modal = ({
  show,
  onHide,
  title,
  children,
  footer,
  size = '',
  centered = true,
  scrollable = false,
}) => {
  const { t } = useT();
  if (!show) return null;

  const dialogClasses = [
    'modal-dialog',
    size && `modal-${size}`,
    centered && 'modal-dialog-centered',
    scrollable && 'modal-dialog-scrollable',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <>
      <div
        className="modal fade show d-block"
        tabIndex="-1"
        role="dialog"
        aria-modal="true"
      >
        <div className={dialogClasses}>
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">{title}</h5>
              <button
                type="button"
                className="btn-close"
                onClick={onHide}
                aria-label={t('common.close')}
              ></button>
            </div>
            <div className="modal-body">{children}</div>
            {footer && <div className="modal-footer">{footer}</div>}
          </div>
        </div>
      </div>
      <div className="modal-backdrop fade show" onClick={onHide}></div>
    </>
  );
};

export default Modal;
