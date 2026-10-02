/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { useT } from '../i18n';

const SessionExpiredModal = ({ isOpen, onLogin }) => {
  const { t } = useT();
  if (!isOpen) return null;

  return (
    <>
      <div className="modal d-block" tabIndex="-1">
        <div className="modal-dialog modal-dialog-centered">
          <div className="modal-content">
            <div className="modal-body text-center p-4">
              <div className="d-inline-flex align-items-center justify-content-center rounded-circle bg-warning-subtle text-warning mb-3" style={{ width: 56, height: 56, fontSize: 24 }}>
                <i className="fa-solid fa-clock-rotate-left" />
              </div>
              <h3 className="h5 fw-semibold mb-2">{t('sessionExpired.title')}</h3>
              <p className="text-secondary small mb-4">
                {t('sessionExpired.body')}
              </p>
              <button className="btn btn-primary d-inline-flex align-items-center gap-2" onClick={onLogin}>
                <i className="fa-solid fa-right-to-bracket" />
                {t('sessionExpired.loginAgain')}
              </button>
            </div>
          </div>
        </div>
      </div>
      <div className="modal-backdrop fade show"></div>
    </>
  );
};

export default SessionExpiredModal;
