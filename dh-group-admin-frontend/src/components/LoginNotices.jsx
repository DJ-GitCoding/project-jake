/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useEffect, useState } from 'react';

/*
 * Deployment-defined lines rendered under the sign-in form — privacy wording, an
 * acceptable-use statement, a link to terms, whatever a given deployment needs.
 *
 * Renders nothing at all when no notices are defined (the default), so the login screen
 * is unchanged until an administrator adds some. The endpoint is public because this
 * shows before anyone has authenticated.
 *
 * Entries are plain text by design; a `url` turns the text into a link. The backend
 * restricts those URLs to http/https, so admin-entered wording can never become script.
 */

export const NOTICES_URL = '/api/public/branding/notices';

const LoginNotices = ({ className = '' }) => {
  const [notices, setNotices] = useState([]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch(NOTICES_URL, { headers: { accept: 'application/json' } });
        if (!res.ok) return;
        const data = await res.json();
        if (!cancelled && Array.isArray(data?.notices)) setNotices(data.notices);
      } catch (error) {
        // Branding is decoration — a failure here must never block signing in.
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  if (notices.length === 0) return null;

  return (
    <div className={`text-center small text-muted mt-4 pt-3 border-top ${className}`}>
      {notices.map((notice, index) => (
        <div key={index} className={index > 0 ? 'mt-1' : undefined}>
          {notice.url ? (
            <a href={notice.url} target="_blank" rel="noopener noreferrer">
              {notice.text}
            </a>
          ) : (
            notice.text
          )}
        </div>
      ))}
    </div>
  );
};

export default LoginNotices;
