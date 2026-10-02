/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { useT } from '../i18n';

const Loading = ({ message, fullPage = false }) => {
  const { t } = useT();
  const content = (
    <div className="loading-container">
      <div className="spinner-border text-primary" role="status">
        <span className="visually-hidden">{t('common.loading')}</span>
      </div>
      <p className="text-muted mb-0">{message || t('common.loading')}</p>
    </div>
  );

  if (fullPage) {
    return (
      <div className="d-flex align-items-center justify-content-center min-vh-100">
        {content}
      </div>
    );
  }

  return content;
};

export default Loading;
