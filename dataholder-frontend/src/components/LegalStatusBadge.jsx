/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { useT } from '../i18n';

const STATUS_CONFIG = {
  NO_DECISION: { color: 'bg-warning-subtle text-warning', icon: 'fa-circle' },
  UNKNOWN: { color: 'bg-warning-subtle text-warning', icon: 'fa-question' },
  NATURAL: { color: 'bg-info-subtle text-info', icon: 'fa-user' },
  NATURAL_UNKNOWN: { color: 'bg-info-subtle text-info', icon: 'fa-user-question' },
  LEGAL: { color: 'bg-success-subtle text-success', icon: 'fa-building' },
  LEGAL_UNKNOWN: { color: 'bg-success-subtle text-success', icon: 'fa-building-circle-question' },
  ANY: { color: 'bg-primary-subtle text-primary', icon: 'fa-asterisk' },
};

const LegalStatusBadge = ({ status, showIcon = true, size = 'md' }) => {
  const { t } = useT();
  const statusKey = STATUS_CONFIG[status] ? status : 'NO_DECISION';
  const config = STATUS_CONFIG[statusKey];
  const labelKey = `badges.legalStatus.${statusKey}`;

  return (
    <span className={`badge ${config.color}`}>
      {showIcon && <i className={`fa-solid ${config.icon}`} style={{ marginRight: '6px' }} />}
      {t(labelKey)}
    </span>
  );
};

export default LegalStatusBadge;