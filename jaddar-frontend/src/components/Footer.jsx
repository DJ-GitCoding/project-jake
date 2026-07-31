/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import { useT } from '../i18n';
import { useConfig } from '../contexts/ConfigContext';

const Footer = () => {
  const { t } = useT();
  const currentYear = new Date().getFullYear();
  const { sourceCodeUrl } = useConfig();

  return (
    <footer className="bg-white shadow-sm mt-auto py-3">
      <div className="container-fluid px-4">
        <div className="row align-items-center">
          <div className="col-md-4 text-center text-md-start">
            <p className="mb-0 text-muted">
              {t('footer.copyright', { year: currentYear })}
            </p>
          </div>
          <div className="col-md-4 text-center">
            <div style={{ width: '100px', height: '40px', margin: '0 auto' }}>
              <img
                src="/media/Logo3.0.jpg"
                alt={t('footer.logoAlt')}
                style={{
                  width: '100%',
                  height: '100%',
                  objectFit: 'contain'
                }}
              />
            </div>
          </div>
          <div className="col-md-4 text-center text-md-end">
            <p className="mb-0 text-muted">
              {t('footer.poweredBy')}
            </p>
          </div>
        </div>
        <div className="row mt-2">
          <div className="col text-center">
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
        </div>
      </div>
    </footer>
  );
};

export default Footer;