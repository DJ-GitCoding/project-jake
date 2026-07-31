/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useRef } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../contexts/AuthContext';
import { useT } from '../i18n';

const Header = ({ serverInfo }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout, isAuthenticated, isMaster } = useAuth();
  const { t } = useT();

  const [menuOpen, setMenuOpen] = useState(false);
  const [openDropdown, setOpenDropdown] = useState(null); // 'agreements' | 'settings' | null
  const menuRef = useRef(null);
  const [menuHeight, setMenuHeight] = useState(0);

  const appVersion =
    (typeof process !== 'undefined' && process.env?.REACT_APP_VERSION) || 'dev';

  const isActive = (path) => location.pathname === path;
  const isLinkActive = (link) =>
    link.match === 'prefix' ? location.pathname.startsWith(link.to) : isActive(link.to);
  const handleNavClick = () => { setMenuOpen(false); setOpenDropdown(null); };

  const handleLogout = () => {
    setMenuOpen(false);
    // logout() navigates to the server /logout action (destroys session + redirect).
    logout();
  };

  useEffect(() => { setMenuOpen(false); setOpenDropdown(null); }, [location.pathname]);

  useEffect(() => {
    if (menuRef.current) setMenuHeight(menuRef.current.scrollHeight);
  }, [menuOpen, isMaster]);

  useEffect(() => {
    if (!menuOpen && !openDropdown) return;
    const handleClick = (e) => {
      if (!e.target.closest('.header-mobile-menu') && !e.target.closest('.header-hamburger')) {
        setMenuOpen(false);
      }
      if (!e.target.closest('.dh-dropdown')) {
        setOpenDropdown(null);
      }
    };
    document.addEventListener('click', handleClick);
    return () => document.removeEventListener('click', handleClick);
  }, [menuOpen, openDropdown]);

  // Flat nav items shown directly in the bar
  const mainLinks = [
    { to: '/', icon: 'fa-gauge-high', label: t('header.nav.dashboard') },
    { to: '/pending', icon: 'fa-hourglass-half', label: t('header.nav.pendingRequests') },
    { to: '/data-holder-groups', icon: 'fa-users-line', label: t('header.nav.dataHolderGroups') },
  ];
  const dataLinks = [
    { to: '/rdap-data', icon: 'fa-database', label: t('header.nav.rdapData') },
    { to: '/query', icon: 'fa-magnifying-glass', label: t('header.nav.rdapQuery') },
  ];
  const otherLinks = [
    { to: '/policy', icon: 'fa-scale-balanced', label: t('header.nav.policy') },
    { to: '/logs', icon: 'fa-clipboard-list', label: t('header.nav.logs') },
  ];
  const settingsLinks = [
    { to: '/settings', icon: 'fa-gear', label: t('header.nav.groupAdmin') },
    { to: '/external-database', icon: 'fa-server', label: t('header.nav.externalDatabase'), match: 'prefix' },
    { to: '/file-automation', icon: 'fa-robot', label: t('header.nav.fileAutomation') },
    ...(isMaster() ? [{ to: '/users', icon: 'fa-users', label: t('header.nav.users') }] : []),
  ];

  const settingsActive = settingsLinks.some(isLinkActive);

  const NavLink = ({ to, icon, label }) => (
    <li className="nav-item">
      <Link to={to} className={`nav-link ${isActive(to) ? 'active fw-bold' : ''}`} title={label} onClick={handleNavClick}>
        <i className={`fa-solid ${icon}`}></i>
        <span className="d-none d-xl-inline ms-1">{label}</span>
      </Link>
    </li>
  );

  return (
    <>
      <style>{`
        .navbar .nav-link {
          color: #6c757d;
          font-weight: 500;
          padding: 0.5rem 0.75rem;
          border-radius: 8px;
          transition: all 0.15s ease;
        }
        .navbar .nav-link:hover { color: #0066cc; background: #f0f4ff; }
        .navbar .nav-link.active { color: #0066cc; background: rgba(0,102,204,0.1); border-color: transparent; }

        .dh-dropdown { position: relative; }
        .dh-dropdown-menu {
          position: absolute;
          top: 100%;
          left: 0;
          margin-top: 4px;
          background: #fff;
          border: 1px solid #e9ecef;
          border-radius: 12px;
          padding: 8px;
          min-width: 210px;
          box-shadow: 0 10px 40px rgba(0,0,0,0.12);
          z-index: 1051;
        }
        /* transparent bridge over the gap so hover doesn't drop between trigger and menu */
        .dh-dropdown-menu::before {
          content: '';
          position: absolute;
          top: -6px;
          left: 0;
          right: 0;
          height: 6px;
        }
        .dh-dropdown-item {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 10px 12px;
          border-radius: 8px;
          font-size: 0.9rem;
          font-weight: 500;
          color: #444;
          text-decoration: none;
          transition: all 0.15s ease;
        }
        .dh-dropdown-item:hover { color: #0066cc; background: #f0f4ff; }
        .dh-dropdown-item.active { color: #0066cc; background: rgba(0,102,204,0.1); font-weight: 600; }
        .dh-dropdown-item i { width: 18px; text-align: center; }

        .header-hamburger {
          width: 28px; height: 22px; position: relative; cursor: pointer;
          background: none; border: none; padding: 0;
        }
        .header-hamburger span {
          display: block; position: absolute; height: 2px; width: 100%;
          background: #333; border-radius: 2px; left: 0;
          transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        }
        .header-hamburger span:nth-child(1) { top: 0; }
        .header-hamburger span:nth-child(2) { top: 10px; }
        .header-hamburger span:nth-child(3) { top: 20px; }
        .header-hamburger.open span:nth-child(1) { top: 10px; transform: rotate(45deg); }
        .header-hamburger.open span:nth-child(2) { opacity: 0; transform: translateX(8px); }
        .header-hamburger.open span:nth-child(3) { top: 10px; transform: rotate(-45deg); }

        .header-mobile-menu {
          position: absolute; left: 0; right: 0; top: 100%; z-index: 1045;
          background: #fff; box-shadow: 0 6px 20px rgba(0,0,0,0.1);
          border-top: 1px solid rgba(0,0,0,0.06); overflow: hidden;
          transition: max-height 0.35s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.25s ease;
        }
        .header-mobile-menu.closed { max-height: 0; opacity: 0; pointer-events: none; }
        .header-mobile-menu.open { opacity: 1; pointer-events: auto; }
        .mobile-nav-link {
          display: flex; align-items: center; gap: 12px; padding: 12px 20px;
          text-decoration: none; color: #444; font-size: 0.95rem;
          border-radius: 8px; margin: 2px 12px; transition: all 0.15s ease;
        }
        .mobile-nav-link:hover { background: #f0f4ff; color: #0066cc; }
        .mobile-nav-link.active { background: #0066cc; color: #fff; font-weight: 600; }
        .mobile-nav-link .nav-icon { font-size: 1.05rem; width: 22px; text-align: center; }
        .mobile-nav-section {
          padding: 10px 20px 4px; font-size: 11px; text-transform: uppercase;
          letter-spacing: 0.05em; color: #adb5bd; font-weight: 700;
        }
        .mobile-menu-backdrop {
          position: fixed; inset: 0; background: rgba(0,0,0,0.15); z-index: 1039;
          opacity: 0; transition: opacity 0.3s ease; pointer-events: none;
        }
        .mobile-menu-backdrop.show { opacity: 1; pointer-events: auto; }
      `}</style>

      <nav className="navbar navbar-light bg-white shadow-sm mb-3" style={{ position: 'sticky', top: 0, zIndex: 1050 }}>
        <div className="container-fluid px-3 px-md-4 d-flex align-items-center justify-content-between">
          {/* Brand */}
          <Link to="/" className="navbar-brand d-flex align-items-center gap-2 mb-0">
            <span
              className="d-flex align-items-center justify-content-center fw-bold text-white"
              style={{ width: 40, height: 40, borderRadius: 10, fontSize: 15, background: 'linear-gradient(135deg, #0066cc, #00b4d8)' }}
            >
              DH
            </span>
            <div className="d-flex flex-column lh-1">
              <span className="fw-bold text-primary" style={{ fontSize: '1.05rem' }}>{serverInfo?.name || t('header.brandFallback')}</span>
              <span className="text-muted" style={{ fontSize: '0.6rem', opacity: 0.8 }}>{serverInfo?.id || `v${appVersion}`}</span>
            </div>
          </Link>

          {/* Desktop nav */}
          <div className="d-none d-lg-flex align-items-center flex-grow-1 ms-4">
            <ul className="navbar-nav me-auto d-flex flex-row align-items-center gap-1">
              {mainLinks.map(l => <NavLink key={l.to} {...l} />)}

              {dataLinks.map(l => <NavLink key={l.to} {...l} />)}
              {otherLinks.map(l => <NavLink key={l.to} {...l} />)}

              {/* Settings dropdown */}
              <li
                className="nav-item dh-dropdown"
                onMouseEnter={() => setOpenDropdown('settings')}
                onMouseLeave={() => setOpenDropdown(null)}
              >
                <button
                  type="button"
                  className={`nav-link border-0 bg-transparent ${settingsActive ? 'active fw-bold' : ''}`}
                  onClick={() => setOpenDropdown('settings')}
                >
                  <i className="fa-solid fa-gear"></i>
                  <span className="d-none d-xl-inline ms-1">{t('header.menus.settings')}</span>
                  <i className="fa-solid fa-chevron-down ms-1" style={{ fontSize: 10 }}></i>
                </button>
                {openDropdown === 'settings' && (
                  <div className="dh-dropdown-menu">
                    {settingsLinks.map(l => (
                      <Link key={l.to} to={l.to} className={`dh-dropdown-item ${isLinkActive(l) ? 'active' : ''}`} onClick={handleNavClick}>
                        <i className={`fa-solid ${l.icon}`}></i>{l.label}
                      </Link>
                    ))}
                  </div>
                )}
              </li>
            </ul>

            {/* Actions */}
            <div className="d-flex align-items-center gap-2">
              <span className="badge bg-success-subtle text-success d-flex align-items-center gap-1" style={{ fontWeight: 500 }}>
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#10b981', display: 'inline-block' }}></span>
                {t('common.online')}
              </span>
              {isAuthenticated && (
                <>
                  <div className="d-flex align-items-center mx-1">
                    <i className="fa-solid fa-circle-user me-1 text-muted"></i>
                    <span className="text-muted small">
                      {user?.username || t('header.userFallback')}
                      {isMaster() && <i className="fa-solid fa-shield-halved text-warning ms-2" title={t('common.master')}></i>}
                    </span>
                  </div>
                  <button onClick={handleLogout} className="btn btn-danger btn-sm" title={t('common.logout')}>
                    <i className="fa-solid fa-right-from-bracket me-1"></i>{t('common.logout')}
                  </button>
                </>
              )}
            </div>
          </div>

          {/* Mobile hamburger */}
          <button
            className={`header-hamburger d-lg-none ${menuOpen ? 'open' : ''}`}
            onClick={() => setMenuOpen(!menuOpen)}
            aria-label={t('header.toggleNav')}
            aria-expanded={menuOpen}
          >
            <span></span><span></span><span></span>
          </button>
        </div>

        {/* Mobile dropdown */}
        <div
          className={`header-mobile-menu d-lg-none ${menuOpen ? 'open' : 'closed'}`}
          style={{ maxHeight: menuOpen ? `${menuHeight}px` : '0' }}
        >
          <div ref={menuRef}>
            {isAuthenticated && (
              <div className="d-flex align-items-center gap-2 px-4 pt-3 pb-2">
                <i className="fa-solid fa-circle-user text-primary" style={{ fontSize: '1.3rem' }}></i>
                <div className="lh-sm">
                  <div className="fw-semibold small">{user?.username || t('header.userFallback')}</div>
                  {user?.type && <div className="text-muted" style={{ fontSize: 11 }}>{user.type}</div>}
                </div>
                {isMaster() && <i className="fa-solid fa-shield-halved text-warning ms-auto" title={t('common.master')}></i>}
              </div>
            )}

            <hr className="my-2 mx-3 opacity-10" />

            <div className="py-1">
              {[...mainLinks].map(l => (
                <Link key={l.to} to={l.to} onClick={handleNavClick} className={`mobile-nav-link ${isActive(l.to) ? 'active' : ''}`}>
                  <i className={`fa-solid ${l.icon} nav-icon`}></i>{l.label}
                </Link>
              ))}

              <div className="mobile-nav-section">{t('header.menus.data')}</div>
              {dataLinks.map(l => (
                <Link key={l.to} to={l.to} onClick={handleNavClick} className={`mobile-nav-link ${isActive(l.to) ? 'active' : ''}`}>
                  <i className={`fa-solid ${l.icon} nav-icon`}></i>{l.label}
                </Link>
              ))}
              {otherLinks.map(l => (
                <Link key={l.to} to={l.to} onClick={handleNavClick} className={`mobile-nav-link ${isActive(l.to) ? 'active' : ''}`}>
                  <i className={`fa-solid ${l.icon} nav-icon`}></i>{l.label}
                </Link>
              ))}

              <div className="mobile-nav-section">{t('header.menus.settings')}</div>
              {settingsLinks.map(l => (
                <Link key={l.to} to={l.to} onClick={handleNavClick} className={`mobile-nav-link ${isLinkActive(l) ? 'active' : ''}`}>
                  <i className={`fa-solid ${l.icon} nav-icon`}></i>{l.label}
                </Link>
              ))}
            </div>

            {isAuthenticated && (
              <>
                <hr className="my-2 mx-3 opacity-10" />
                <div className="d-flex gap-2 px-3 pb-3 pt-1">
                  <button onClick={handleLogout} className="btn btn-danger btn-sm flex-fill">
                    <i className="fa-solid fa-right-from-bracket me-1"></i>{t('common.logout')}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      </nav>

      {/* Backdrop */}
      <div className={`mobile-menu-backdrop ${menuOpen ? 'show' : ''}`} onClick={() => setMenuOpen(false)} />
    </>
  );
};

export default Header;
