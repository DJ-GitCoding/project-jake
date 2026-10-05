/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, Link, useLocation, useRouteLoaderData } from 'react-router';
import {
  useAuth,
  RoleDisplayNames,
  RoleBadgeClasses,
} from '../context/AuthContext';
import BrandingLogo from './BrandingLogo';
import { useT } from '../i18n';

const Header = () => {
  const { user, logout, getPrimaryRole, isRequestorGroupAdmin, isMasterAdmin } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useT();
  const [menuOpen, setMenuOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const menuRef = useRef(null);
  const [menuHeight, setMenuHeight] = useState(0);

  const appVersion = useRouteLoaderData('root')?.config?.appVersion || 'dev';

  const primaryRole = getPrimaryRole();
  const roleDisplayName = RoleDisplayNames[primaryRole] || t('common.user');
  const roleBadgeClass = RoleBadgeClasses[primaryRole] || '';

  const displayName =
    user?.firstName || user?.lastName
      ? `${user?.firstName || ''} ${user?.lastName || ''}`.trim()
      : user?.email || t('common.user');

  const handleLogout = () => {
    setMenuOpen(false);
    logout();
    navigate('/login');
  };

  const isActive = (path) => location.pathname === path;
  const handleNavClick = () => setMenuOpen(false);

  useEffect(() => { setMenuOpen(false); setUserMenuOpen(false); }, [location.pathname]);

  useEffect(() => {
    if (!userMenuOpen) return;
    const close = (e) => {
      if (!e.target.closest('.header-user-menu')) setUserMenuOpen(false);
    };
    document.addEventListener('click', close);
    return () => document.removeEventListener('click', close);
  }, [userMenuOpen]);

  useEffect(() => {
    if (menuRef.current) {
      setMenuHeight(menuRef.current.scrollHeight);
    }
  }, [menuOpen]);

  useEffect(() => {
    if (!menuOpen) return;
    const handleClick = (e) => {
      if (!e.target.closest('.header-mobile-menu') && !e.target.closest('.header-hamburger')) {
        setMenuOpen(false);
      }
    };
    document.addEventListener('click', handleClick);
    return () => document.removeEventListener('click', handleClick);
  }, [menuOpen]);

  const navItems = [
    { to: '/dashboard', icon: 'fa-tachometer-alt', label: t('header.nav.dashboard'), show: true },
    { to: '/subscriptions', icon: 'fa-file-signature', label: t('header.nav.subscriptions'), show: true },
    { to: '/requestor-groups', icon: 'fa-building', label: t('header.nav.requestorGroups'), show: true },
    { to: '/data-holder-groups', icon: 'fa-server', label: t('header.nav.dataHolderGroups'), show: true },
    { to: '/users', icon: 'fa-users', label: t('header.nav.users'), show: isRequestorGroupAdmin() },
    // Settings is Master-only today because branding is its only tab.
    { to: '/settings', icon: 'fa-gear', label: t('header.nav.settings'), show: isMasterAdmin() },
  ].filter(item => item.show);

  return (
    <>
      <style>{`
        .header-hamburger {
          width: 28px;
          height: 22px;
          position: relative;
          cursor: pointer;
          background: none;
          border: none;
          padding: 0;
        }
        .header-hamburger span {
          display: block;
          position: absolute;
          height: 2px;
          width: 100%;
          background: #333;
          border-radius: 2px;
          left: 0;
          transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        }
        .header-hamburger span:nth-child(1) { top: 0; }
        .header-hamburger span:nth-child(2) { top: 10px; }
        .header-hamburger span:nth-child(3) { top: 20px; }
        .header-hamburger.open span:nth-child(1) {
          top: 10px;
          transform: rotate(45deg);
        }
        .header-hamburger.open span:nth-child(2) {
          opacity: 0;
          transform: translateX(8px);
        }
        .header-hamburger.open span:nth-child(3) {
          top: 10px;
          transform: rotate(-45deg);
        }
        .header-mobile-menu {
          position: absolute;
          left: 0;
          right: 0;
          top: 100%;
          z-index: 1045;
          background: #fff;
          box-shadow: 0 6px 20px rgba(0,0,0,0.1);
          border-top: 1px solid rgba(0,0,0,0.06);
          overflow: hidden;
          transition: max-height 0.35s cubic-bezier(0.4, 0, 0.2, 1),
                      opacity 0.25s ease;
        }
        .header-mobile-menu.closed {
          max-height: 0;
          opacity: 0;
          pointer-events: none;
        }
        .header-mobile-menu.open {
          opacity: 1;
          pointer-events: auto;
        }
        .mobile-nav-link {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 12px 20px;
          text-decoration: none;
          color: #444;
          font-size: 0.95rem;
          border-radius: 8px;
          margin: 2px 12px;
          transition: all 0.15s ease;
        }
        .mobile-nav-link:hover {
          background: #f0f4ff;
          color: #0066cc;
        }
        .mobile-nav-link.active {
          background: #0066cc;
          color: #fff;
          font-weight: 600;
        }
        .mobile-nav-link .nav-icon {
          font-size: 1.1rem;
          width: 22px;
          text-align: center;
        }
        .mobile-menu-backdrop {
          position: fixed;
          inset: 0;
          background: rgba(0,0,0,0.15);
          z-index: 1039;
          opacity: 0;
          transition: opacity 0.3s ease;
          pointer-events: none;
        }
        .mobile-menu-backdrop.show {
          opacity: 1;
          pointer-events: auto;
        }
      `}</style>

      <nav className="navbar navbar-light bg-white shadow-sm mb-3" style={{ position: 'sticky', top: 0, zIndex: 1050 }}>
        <div className="container-fluid px-3 px-md-4 d-flex align-items-center justify-content-between">
          {/* Brand */}
          <Link to="/dashboard" className="navbar-brand d-flex align-items-center gap-2 mb-0">
            {/* Uploaded logo replaces the default icon; see BrandingLogo. */}
            <BrandingLogo
              alt={t('header.brand')}
              style={{ height: 36, maxWidth: 160, objectFit: 'contain' }}
              fallback={<i className="fas fa-handshake text-primary" style={{ fontSize: '1.6rem' }}></i>}
            />
            <div className="d-flex flex-column lh-1">
              <span className="fw-bold text-primary" style={{ fontSize: '1.1rem' }}>{t('header.brand')}</span>
              <span className="text-muted" style={{ fontSize: '0.6rem', opacity: 0.7 }}>v{appVersion}</span>
            </div>
          </Link>

          {/* Desktop nav */}
          <div className="d-none d-lg-flex align-items-center flex-grow-1 ms-4">
            <ul className="navbar-nav me-auto d-flex flex-row gap-1">
              {navItems.map(item => (
                <li className="nav-item" key={item.to}>
                  <Link to={item.to} className={`nav-link ${isActive(item.to) ? 'active fw-bold' : ''}`}>
                    <i className={`fas ${item.icon} me-1`}></i>{item.label}
                  </Link>
                </li>
              ))}
            </ul>
            <div className="d-flex align-items-center gap-2">
              <div className="header-user-menu position-relative me-2">
                <button
                  type="button"
                  className="btn btn-link text-decoration-none d-flex align-items-center p-0"
                  onClick={() => setUserMenuOpen(!userMenuOpen)}
                  aria-haspopup="menu"
                  aria-expanded={userMenuOpen}
                >
                  <i className="fas fa-user-circle me-1 text-muted"></i>
                  <span className="text-muted small">
                    {displayName}
                    {user && (
                      <span className={`badge badge-role ms-2 ${roleBadgeClass}`}>{roleDisplayName}</span>
                    )}
                  </span>
                  <i className="fas fa-caret-down ms-1 text-muted small"></i>
                </button>
                {userMenuOpen && (
                  <div className="dropdown-menu dropdown-menu-end show" role="menu"
                       style={{ position: 'absolute', right: 0, top: '100%', marginTop: 6 }}>
                    <Link to="/profile" className="dropdown-item" role="menuitem">
                      <i className="fas fa-id-card me-2"></i>{t('header.myProfile')}
                    </Link>
                  </div>
                )}
              </div>
              <button onClick={handleLogout} className="btn btn-danger btn-sm">
                <i className="fas fa-sign-out-alt me-1"></i>{t('common.logout')}
              </button>
            </div>
          </div>

          {/* Mobile hamburger */}
          <button
            className={`header-hamburger d-lg-none ${menuOpen ? 'open' : ''}`}
            onClick={() => setMenuOpen(!menuOpen)}
            aria-label={t('header.toggleNav')}
            aria-expanded={menuOpen}
          >
            <span></span>
            <span></span>
            <span></span>
          </button>
        </div>

        {/* Mobile dropdown — absolute, overlays content */}
        <div
          className={`header-mobile-menu d-lg-none ${menuOpen ? 'open' : 'closed'}`}
          style={{ maxHeight: menuOpen ? `${menuHeight}px` : '0' }}
        >
          <div ref={menuRef}>
            <Link to="/profile" onClick={handleNavClick}
                  className="d-flex align-items-center gap-2 px-4 pt-3 pb-2 text-decoration-none text-reset">
              <i className="fas fa-user-circle text-primary" style={{ fontSize: '1.3rem' }}></i>
              <div className="lh-sm">
                <div className="fw-semibold small">{displayName}</div>
                {user?.email && (user?.firstName || user?.lastName) && (
                  <div className="text-muted" style={{ fontSize: '11px' }}>{user.email}</div>
                )}
              </div>
              {user && (
                <span className={`badge badge-role ms-auto ${roleBadgeClass}`} style={{ fontSize: '10px' }}>
                  {roleDisplayName}
                </span>
              )}
            </Link>

            <hr className="my-2 mx-3 opacity-10" />

            <div className="py-1">
              {navItems.map(item => (
                <Link
                  key={item.to}
                  to={item.to}
                  onClick={handleNavClick}
                  className={`mobile-nav-link ${isActive(item.to) ? 'active' : ''}`}
                >
                  <i className={`fas ${item.icon} nav-icon`}></i>
                  {item.label}
                </Link>
              ))}
            </div>

            <hr className="my-2 mx-3 opacity-10" />

            <div className="d-flex gap-2 px-3 pb-3 pt-1">
              <button onClick={handleLogout} className="btn btn-danger btn-sm flex-fill">
                <i className="fas fa-sign-out-alt me-1"></i>{t('common.logout')}
              </button>
            </div>
          </div>
        </div>
      </nav>

      {/* Backdrop */}
      <div
        className={`mobile-menu-backdrop ${menuOpen ? 'show' : ''}`}
        onClick={() => setMenuOpen(false)}
      />
    </>
  );
};

export default Header;
