/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { useRouteLoaderData } from 'react-router';
import { useT } from '../i18n';

const Footer = () => {
  const { t } = useT();
  const currentYear = new Date().getFullYear();
  const sourceCodeUrl = useRouteLoaderData('root')?.config?.sourceCodeUrl;
  return (
    <footer className="mt-auto py-3 border-top bg-white">
      <div className="container-fluid px-4 text-center">
        <p className="mb-1 text-muted" style={{ fontSize: '0.8rem' }}>
          {t('footer.copyright', { year: currentYear })}
        </p>
        <p className="mb-0 text-muted" style={{ fontSize: '0.8rem' }}>
          {t('footer.codeBy')} <a href="https://pure-code.net" target="_blank" rel="noopener noreferrer" className="text-muted">https://pure-code.net</a>
          {sourceCodeUrl && (
            <>
              {' · '}
              <a href={sourceCodeUrl} target="_blank" rel="noopener noreferrer" className="text-muted">{t('footer.sourceCode')}</a>
            </>
          )}
        </p>
      </div>
    </footer>
  );
};

export default Footer;
