/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { useT } from '../i18n';

const AccessLevelBadge = ({ level }) => {
  const { t } = useT();
  // Ensure level is a number and default to 0 if undefined/null
  const numLevel = level !== undefined && level !== null ? Number(level) : 0;

  return (
    <div className="access-level" title={t('badges.accessLevel.title', { level: numLevel })}>
      {[0, 1, 2, 3].map((l) => (
        <div 
          key={l} 
          className={`access-level-dot ${l <= numLevel ? 'active' : ''}`}
        />
      ))}
      <span className="badge bg-primary-subtle text-primary" style={{ marginLeft: '8px' }}>
        L{numLevel}
      </span>
    </div>
  );
};

export default AccessLevelBadge;