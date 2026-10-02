/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';

const StatusBadge = ({ status }) => {
  const getStatusClass = () => {
    switch (status?.toUpperCase()) {
      case 'PENDING':
        return 'bg-warning-subtle text-warning';
      case 'APPROVED':
        return 'bg-info-subtle text-info';
      case 'TESTING':
        return 'bg-warning-subtle text-warning';
      case 'ACTIVE':
        return 'bg-success-subtle text-success';
      case 'DECLINED':
        return 'bg-danger-subtle text-danger';
      case 'SUSPENDED':
        return 'bg-danger-subtle text-danger';
      case 'PASSED':
        return 'bg-success-subtle text-success';
      case 'FAILED':
        return 'bg-danger-subtle text-danger';
      default:
        return '';
    }
  };

  return (
    <span className={`badge ${getStatusClass()}`}>
      {status}
    </span>
  );
};

export default StatusBadge;
