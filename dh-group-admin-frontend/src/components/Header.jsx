/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useRef } from 'react';
import { Link, useLocation } from 'react-router';
import { useAuth, USER_TYPE_LABEL_KEYS, USER_TYPE_COLORS } from '../context/AuthContext';
import { useGroup } from '../context/GroupContext';
import { useT } from '../i18n';

const GroupSelector = ({ user, groups, selectedGroupId, onSelectGroup }) => {
  const { t } = useT();
  const isMaster = user?.type === 1;

  if (groups.length === 0 && !isMaster) return null;

  return (
    <div className="d-flex align-items-center">
      <i className="fa-solid fa-layer-group text-muted me-2" style={{ fontSize: 13 }}></i>
      <select
        className="form-select form-select-sm"
        style={{ minWidth: 150, fontSize: 13 }}
        value={selectedGroupId ?? 'ALL'}
        onChange={e => {
          const val = e.target.value;
          onSelectGroup(val === 'ALL' ? null : parseInt(val));
        }}
      >
        {isMaster && <option value="ALL">{t('header.groupSelector.allGroups')}</option>}
        {groups.map(g => (
          <option key={g.id} value={g.id}>{g.name}</option>
        ))}
        {groups.length === 0 && !isMaster && (
          <option value="" disabled>{t('header.groupSelector.noGroups')}</option>
        )}
      </select>
    </div>
  );
};

const Header = () => {
  const { t } = useT();
  const location = useLocation();
  const { user, logout } = useAuth();
  const { groups, selectedGroupId, selectGroup } = useGroup();
  const [menuOpen, setMenuOpen] = useState(false);
  const [openDropdown, setOpenDropdown] = useState(null);
  const menuRef = useRef(null);
  const [menuHeight, setMenuHeight] = useState(0);

  const appVersion =
    (typeof process !== 'undefined' && process.env?.REACT_APP_VERSION) || 'dev';

  const isActive = (path) => location.pathname === path;
  const handleNavClick = () => { setMenuOpen(false); setOpenDropdown(null); };

  const handleLogout = () => {
    setMenuOpen(false);
    logout();
  };

  useEffect(() => { setMenuOpen(false); setOpenDropdown(null); }, [location.pathname]);

  useEffect(() => {
    if (menuRef.current) {
      setMenuHeight(menuRef.current.scrollHeight);
    }
  }, [menuOpen, groups, selectedGroupId]);

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

  // Primary item stays flat in the bar; the rest are grouped into dropdowns to save space.
  const flatPrimary = [
    { to: '/dashboard', icon: 'fa-gauge-high', label: t('header.nav.dashboard') },
  ];
  const dropdownGroups = [
    {
      id: 'agreements', label: t('header.menus.agreements'), icon: 'fa-file-signature',
      items: [
        { to: '/subscriptions', icon: 'fa-handshake', label: t('header.nav.subscriptions') },
        { to: '/templates', icon: 'fa-file-contract', label: t('header.nav.templates') },
      ],
    },
    {
      id: 'directory', label: t('header.menus.directory'), icon: 'fa-sitemap',
      items: [
        { to: '/data-holders', icon: 'fa-database', label: t('header.nav.dataHolders') },
        { to: '/applications', icon: 'fa-file-circle-plus', label: t('header.nav.applications') },
        { to: '/requestor-groups', icon: 'fa-users', label: t('header.nav.requestorGroups') },
        ...(user?.type === 1 ? [{ to: '/data-holder-groups', icon: 'fa-layer-group', label: t('header.nav.dhGroups') }] : []),
        ...(user?.type === 1 ? [{ to: '/instances', icon: 'fa-server', label: t('header.nav.instances') }] : []),
      ],
    },
    {
      id: 'admin', label: t('header.menus.admin'), icon: 'fa-user-shield',
      items: [
        ...(user?.type <= 2 ? [{ to: '/users', icon: 'fa-users-gear', label: t('header.nav.users') }] : []),
        { to: '/audit-logs', icon: 'fa-clipboard-list', label: t('header.nav.auditLogs') },
      ],
    },
  ].filter(group => group.items.length > 0);

  const NavItem = ({ item }) => (
    <li className="nav-item">
      <Link to={item.to} className={`nav-link ${isActive(item.to) ? 'active fw-bold' : ''}`} title={item.label} onClick={handleNavClick}>
        <i className={`fa-solid ${item.icon}`}></i>
        <span className="d-none d-xl-inline ms-1">{item.label}</span>
      </Link>
    </li>
  );

  const NavDropdown = ({ group }) => {
    const active = group.items.some(i => isActive(i.to));
    return (
      <li
        className="nav-item dh-dropdown"
        onMouseEnter={() => setOpenDropdown(group.id)}
        onMouseLeave={() => setOpenDropdown(null)}
      >
        <button
          type="button"
          className={`nav-link border-0 bg-transparent ${active ? 'active fw-bold' : ''}`}
          onClick={() => setOpenDropdown(group.id)}
          title={group.label}
        >
          <i className={`fa-solid ${group.icon}`}></i>
          <span className="d-none d-xl-inline ms-1">{group.label}</span>
          <i className="fa-solid fa-chevron-down ms-1" style={{ fontSize: 10 }}></i>
        </button>
        {openDropdown === group.id && (
          <div className="dh-dropdown-menu">
            {group.items.map(i => (
              <Link key={i.to} to={i.to} className={`dh-dropdown-item ${isActive(i.to) ? 'active' : ''}`} onClick={handleNavClick}>
                <i className={`fa-solid ${i.icon}`}></i>{i.label}
              </Link>
            ))}
          </div>
        )}
      </li>
    );
  };

  const userTypeBadge = (
    <span className={`badge bg-${USER_TYPE_COLORS[user?.type] || 'secondary'}`} style={{ fontSize: '10px' }}>
      {t(USER_TYPE_LABEL_KEYS[user?.type] || 'common.user')}
    </span>
  );

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
        .mobile-nav-section {
          padding: 10px 20px 4px;
          font-size: 11px;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          color: #adb5bd;
          font-weight: 700;
        }
      `}</style>

      <nav className="navbar navbar-light bg-white shadow-sm mb-3" style={{ position: 'sticky', top: 0, zIndex: 1050 }}>
        <div className="container-fluid px-3 px-md-4 d-flex align-items-center justify-content-between">
          {/* Brand */}
          <Link to="/dashboard" className="navbar-brand d-flex align-items-center gap-2 mb-0">
            <i className="fa-solid fa-server text-primary" style={{ fontSize: '1.5rem' }}></i>
            <div className="d-flex flex-column lh-1">
              <span className="fw-bold text-primary" style={{ fontSize: '1.1rem' }}>Jareg</span>
              <span className="text-muted" style={{ fontSize: '0.6rem', opacity: 0.7 }}>v{appVersion}</span>
            </div>
          </Link>

          {/* Desktop nav */}
          <div className="d-none d-lg-flex align-items-center flex-grow-1 ms-4">
            <ul className="navbar-nav me-auto d-flex flex-row align-items-center gap-1">
              {flatPrimary.map(item => <NavItem key={item.to} item={item} />)}
              {dropdownGroups.map(group => <NavDropdown key={group.id} group={group} />)}
            </ul>
            <div className="d-flex align-items-center gap-2">
              <GroupSelector
                user={user}
                groups={groups}
                selectedGroupId={selectedGroupId}
                onSelectGroup={selectGroup}
              />
              <div className="d-flex align-items-center mx-2">
                <i className="fa-solid fa-circle-user me-1 text-muted"></i>
                <span className="text-muted small">
                  {user?.firstName} {user?.lastName}
                  <span className="ms-2">{userTypeBadge}</span>
                </span>
              </div>
              <button onClick={handleLogout} className="btn btn-danger btn-sm" title={t('header.signOut')}>
                <i className="fa-solid fa-right-from-bracket me-1"></i>{t('common.logout')}
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
            <div className="d-flex align-items-center gap-2 px-4 pt-3 pb-2">
              <i className="fa-solid fa-circle-user text-primary" style={{ fontSize: '1.3rem' }}></i>
              <div className="lh-sm">
                <div className="fw-semibold small">{user?.firstName} {user?.lastName}</div>
              </div>
              <span className="ms-auto">{userTypeBadge}</span>
            </div>

            {(groups.length > 0 || user?.type === 1) && (
              <div className="px-4 pb-2">
                <div className="text-muted text-uppercase mb-1" style={{ fontSize: 10, letterSpacing: '0.05em' }}>{t('header.actingAs')}</div>
                <GroupSelector
                  user={user}
                  groups={groups}
                  selectedGroupId={selectedGroupId}
                  onSelectGroup={selectGroup}
                />
              </div>
            )}

            <hr className="my-2 mx-3 opacity-10" />

            <div className="py-1">
              {flatPrimary.map(item => (
                <Link
                  key={item.to}
                  to={item.to}
                  onClick={handleNavClick}
                  className={`mobile-nav-link ${isActive(item.to) ? 'active' : ''}`}
                >
                  <i className={`fa-solid ${item.icon} nav-icon`}></i>
                  {item.label}
                </Link>
              ))}
              {dropdownGroups.map(group => (
                <React.Fragment key={group.id}>
                  <div className="mobile-nav-section">{group.label}</div>
                  {group.items.map(item => (
                    <Link
                      key={item.to}
                      to={item.to}
                      onClick={handleNavClick}
                      className={`mobile-nav-link ${isActive(item.to) ? 'active' : ''}`}
                    >
                      <i className={`fa-solid ${item.icon} nav-icon`}></i>
                      {item.label}
                    </Link>
                  ))}
                </React.Fragment>
              ))}
            </div>

            <hr className="my-2 mx-3 opacity-10" />

            <div className="d-flex gap-2 px-3 pb-3 pt-1">
              <button onClick={handleLogout} className="btn btn-danger btn-sm flex-fill">
                <i className="fa-solid fa-right-from-bracket me-1"></i>{t('common.logout')}
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
