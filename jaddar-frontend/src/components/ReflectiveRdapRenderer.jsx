/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useMemo } from 'react';
import { useT } from '../i18n';

const ReflectiveRdapRenderer = ({ data, accessLevel, className = '' }) => {
  const { t } = useT();
  const [collapsedSections, setCollapsedSections] = useState({});
  const [showPolicyLevels, setShowPolicyLevels] = useState(true);

  const isAnnotatedValue = (value) => {
    if (typeof value !== 'object' || value === null || Array.isArray(value)) return false;
    const keys = Object.keys(value);
    return keys.includes('value') && keys.includes('requiredAccessLevel') && keys.length === 2;
  };

  const deepUnwrap = (input, path = '', levelsMap = {}) => {
    if (input === null || input === undefined) return { clean: input, levels: levelsMap };
    if (isAnnotatedValue(input)) {
      if (path) levelsMap[path] = input.requiredAccessLevel;
      return deepUnwrap(input.value, path, levelsMap);
    }
    if (Array.isArray(input)) {
      const cleanArr = input.map((item, idx) => {
        const childPath = path ? `${path}.${idx}` : `${idx}`;
        return deepUnwrap(item, childPath, levelsMap).clean;
      });
      return { clean: cleanArr, levels: levelsMap };
    }
    if (typeof input === 'object') {
      const cleanObj = {};
      for (const [key, val] of Object.entries(input)) {
        const childPath = path ? `${path}.${key}` : key;
        cleanObj[key] = deepUnwrap(val, childPath, levelsMap).clean;
      }
      return { clean: cleanObj, levels: levelsMap };
    }
    return { clean: input, levels: levelsMap };
  };

  const { cleanData, globalLevels } = useMemo(() => {
    if (!data || (typeof data === 'object' && Object.keys(data).length === 0)) {
      return { cleanData: data, globalLevels: {} };
    }
    const { clean, levels } = deepUnwrap(data);
    return { cleanData: clean, globalLevels: levels };
  }, [data]);

  if (!cleanData || (typeof cleanData === 'object' && Object.keys(cleanData).length === 0)) {
    return <div className="text-center text-muted p-5">{t('rdapRenderer.noData')}</div>;
  }

  /*
   * Per-element policy levels, sent by the data holder alongside the data it
   * describes. `fields` is keyed exactly the way the data holder's redaction
   * code looks elements up: top-level RDAP keys on the object itself, and
   * "vcardArray.<property>" inside each contact so a contact carries its own
   * block, since the levels that apply depend on the contact's role.
   */
  const policyLevels = typeof cleanData === 'object' && !Array.isArray(cleanData)
    ? cleanData.policyLevels : null;
  const topLevelPolicyFields = policyLevels?.fields || null;

  const toggleSection = (key) => {
    setCollapsedSections(prev => ({ ...prev, [key]: !prev[key] }));
  };

  const renderAccessLevelTag = (level) => {
    if (level === null || level === undefined) return null;
    const tagColors = {
      0: { bg: '#e8f5e9', text: '#2e7d32', border: '#a5d6a7' },
      1: { bg: '#fff3e0', text: '#e65100', border: '#ffcc80' },
      2: { bg: '#e3f2fd', text: '#1565c0', border: '#90caf9' },
      3: { bg: '#fce4ec', text: '#c62828', border: '#ef9a9a' },
    };
    const c = tagColors[level] || { bg: '#f5f5f5', text: '#616161', border: '#bdbdbd' };
    return (
      <span className="ms-2" style={{
        display: 'inline-block', fontSize: '0.65rem', fontWeight: 600, padding: '1px 5px',
        borderRadius: '4px', backgroundColor: c.bg, color: c.text, border: `1px solid ${c.border}`,
        verticalAlign: 'middle', whiteSpace: 'nowrap',
      }} title={`Required Access Level: ${level}`}>
        L{level}
      </span>
    );
  };

  const levelTagFor = (path) => renderAccessLevelTag(globalLevels[path] ?? null);

  // ============================================
  // POLICY LEVELS — sensitivity / validation per data element
  // ============================================

  const SENSITIVITY_STYLES = {
    0: { bg: '#e8f5e9', text: '#2e7d32', border: '#a5d6a7' },
    1: { bg: '#e3f2fd', text: '#1565c0', border: '#90caf9' },
    2: { bg: '#fff3e0', text: '#e65100', border: '#ffcc80' },
    3: { bg: '#fce4ec', text: '#c62828', border: '#ef9a9a' },
  };

  const VALIDATION_STYLES = {
    0: { bg: '#f5f5f5', text: '#616161', border: '#bdbdbd' },
    1: { bg: '#e3f2fd', text: '#1565c0', border: '#90caf9' },
    2: { bg: '#fff3e0', text: '#e65100', border: '#ffcc80' },
    3: { bg: '#ede7f6', text: '#5e35b1', border: '#b39ddb' },
  };

  const NEUTRAL_STYLE = { bg: '#f8f9fa', text: '#6c757d', border: '#dee2e6' };

  const levelName = (group, level) => {
    if (level === null || level === undefined) return null;
    const name = t(`rdapRenderer.policyLevels.${group}Names.level${level}`);
    return name === `rdapRenderer.policyLevels.${group}Names.level${level}` ? null : name;
  };

  const policyDetailFor = (fields, key) => (fields ? fields[key] : null);

  const levelPairTitle = (pair, detail, multiple) => {
    const lines = [];
    const sName = levelName('sensitivity', pair.sensitivityLevel);
    lines.push(t('rdapRenderer.policyLevels.sensitivityTitle', {
      level: pair.sensitivityLevel,
      name: sName || t('rdapRenderer.policyLevels.unnamed'),
    }));
    if (pair.validationLevel === null || pair.validationLevel === undefined) {
      lines.push(t('rdapRenderer.policyLevels.validationUnset'));
    } else {
      const vName = levelName('validation', pair.validationLevel);
      lines.push(t('rdapRenderer.policyLevels.validationTitle', {
        level: pair.validationLevel,
        name: vName || t('rdapRenderer.policyLevels.unnamed'),
      }));
    }
    if (pair.policyDefault) {
      lines.push(t('rdapRenderer.policyLevels.policyDefault'));
    }
    const sources = (pair.sources || [])
      .map((src) => src.label || src.fieldPath)
      .filter(Boolean);
    if (sources.length > 0) {
      lines.push(t('rdapRenderer.policyLevels.fromFields', { fields: sources.join(', ') }));
    }
    if (multiple) {
      lines.push(pair.applied
        ? t('rdapRenderer.policyLevels.appliedNote')
        : t('rdapRenderer.policyLevels.supersededNote', { level: detail.effectiveSensitivityLevel }));
    }
    return lines.join('\n');
  };

  const segmentStyle = (palette, dashed) => ({
    display: 'inline-block',
    fontSize: '0.62rem',
    fontWeight: 600,
    lineHeight: 1.4,
    padding: '0 4px',
    backgroundColor: palette.bg,
    color: palette.text,
    border: `1px ${dashed ? 'dashed' : 'solid'} ${palette.border}`,
  });

  const renderLevelPair = (pair, idx, detail, multiple) => {
    /* A pair that lost to a higher sensitivity is dimmed rather than dropped:
    the requestor should still see every level the policy associated with the
    element, not just the one that governed the redaction. */
    const superseded = multiple && !pair.applied;
    const dashed = Boolean(pair.policyDefault);
    const sensPalette = SENSITIVITY_STYLES[pair.sensitivityLevel] || NEUTRAL_STYLE;
    const validationSet = pair.validationLevel !== null && pair.validationLevel !== undefined;
    const valPalette = validationSet
      ? (VALIDATION_STYLES[pair.validationLevel] || NEUTRAL_STYLE)
      : NEUTRAL_STYLE;

    return (
      <span
        key={idx}
        title={levelPairTitle(pair, detail, multiple)}
        style={{ whiteSpace: 'nowrap', opacity: superseded ? 0.5 : 1 }}
      >
        <span style={{ ...segmentStyle(sensPalette, dashed), borderRadius: '4px 0 0 4px', borderRight: 'none' }}>
          S{pair.sensitivityLevel}
        </span>
        <span style={{ ...segmentStyle(valPalette, dashed), borderRadius: '0 4px 4px 0' }}>
          {validationSet ? `V${pair.validationLevel}` : 'V–'}
        </span>
      </span>
    );
  };

  /* Renders every (sensitivity, validation) pair the policy associated with one
  element. Most elements have exactly one. An element whose Int'l and Local
  forms carry different levels — or an address, where every component collapses
  onto a single vCard property — reports one pair per distinct combination. */
  const renderPolicyLevels = (detail) => {
    if (!showPolicyLevels || !detail || !Array.isArray(detail.levels) || detail.levels.length === 0) return null;
    const multiple = detail.levels.length > 1;
    return (
      <span className="ms-1" style={{ verticalAlign: 'middle' }}>
        {detail.levels.map((pair, idx) => (
          <React.Fragment key={idx}>
            {idx > 0 && <span style={{ display: 'inline-block', width: '3px' }} />}
            {renderLevelPair(pair, idx, detail, multiple)}
          </React.Fragment>
        ))}
      </span>
    );
  };

  const getType = (value) => {
    if (value === null) return 'null';
    if (value === undefined) return 'undefined';
    if (Array.isArray(value)) return 'array';
    return typeof value;
  };

  const isEmpty = (value) => {
    if (value === null || value === undefined || value === '') return true;
    if (Array.isArray(value) && value.length === 0) return true;
    if (typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length === 0) return true;
    return false;
  };

  const safeString = (value) => {
    if (value === null || value === undefined) return '';
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return String(value);
    if (Array.isArray(value)) return value.map(safeString).join(', ');
    if (typeof value === 'object') {
      if ('description' in value) {
        return Array.isArray(value.description) ? value.description.join(', ') : String(value.description);
      }
      return JSON.stringify(value);
    }
    return String(value);
  };

  const looksLikeDate = (value) => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}(T|\s)/.test(value);
  const looksLikeUrl = (value) => typeof value === 'string' && /^https?:\/\//i.test(value);
  const looksLikeEmail = (value) => typeof value === 'string' && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  const looksLikePhone = (value) => typeof value === 'string' && /^\+?\d[\d\s\-().]{6,}$/.test(value);

  const isDateKey = (key) => /date|time|created|updated|changed|expires|expiration|registered|registration|timestamp|_at$/i.test(key);
  const isEmailKey = (key) => /email|e-mail|mail/i.test(key);
  const isPhoneKey = (key) => /phone|tel|fax|mobile/i.test(key);
  const isUrlKey = (key) => /url|link|href|uri|website/i.test(key);
  const isStatusKey = (key) => /^status$|^state$/i.test(key);
  const isHandleKey = (key) => /handle|id|uuid|identifier/i.test(key);
  const isAddressKey = (key) => /address|addr|location/i.test(key);

  const isVcardArray = (value) => Array.isArray(value) && value[0] === 'vcard' && Array.isArray(value[1]);

  const isEventsArray = (arr) => {
    if (!Array.isArray(arr) || arr.length === 0) return false;
    return arr.every(item => typeof item === 'object' && item !== null && ('eventAction' in item || 'eventDate' in item));
  };

  const isEntitiesArray = (arr) => {
    if (!Array.isArray(arr) || arr.length === 0) return false;
    return arr.every(item => {
      if (typeof item !== 'object' || item === null) return false;
      return ('roles' in item || 'vcardArray' in item) &&
        item.objectClassName !== 'ip network' && item.objectClassName !== 'autnum';
    });
  };

  const isNetworksArray = (arr) => {
    if (!Array.isArray(arr) || arr.length === 0) return false;
    return arr.every(item => typeof item === 'object' && item !== null &&
      (item.objectClassName === 'ip network' || ('startAddress' in item && 'endAddress' in item)));
  };

  const isAutnumsArray = (arr) => {
    if (!Array.isArray(arr) || arr.length === 0) return false;
    return arr.every(item => typeof item === 'object' && item !== null &&
      (item.objectClassName === 'autnum' || ('startAutnum' in item && 'endAutnum' in item)));
  };

  const isLinksArray = (arr) => {
    if (!Array.isArray(arr) || arr.length === 0) return false;
    return arr.every(item => typeof item === 'object' && item !== null && ('href' in item || 'rel' in item));
  };

  const isNoticesArray = (arr) => {
    if (!Array.isArray(arr) || arr.length === 0) return false;
    return arr.every(item => typeof item === 'object' && item !== null && ('title' in item || 'description' in item));
  };

  const isRedactedArray = (arr) => {
    if (!Array.isArray(arr) || arr.length === 0) return false;
    return arr.every(item => typeof item === 'object' && item !== null && ('prePath' in item || 'method' in item));
  };

  const isNameserversArray = (arr) => {
    if (!Array.isArray(arr) || arr.length === 0) return false;
    return arr.every(item => typeof item === 'string' || (typeof item === 'object' && item !== null &&
      ('ldhName' in item || 'name' in item || item.objectClassName === 'nameserver')));
  };

  const isEntitiesDictionary = (obj) => {
    if (typeof obj !== 'object' || Array.isArray(obj) || obj === null) return false;
    const roleKeys = ['registrar', 'registrant', 'technical', 'administrative', 'abuse', 'billing', 'noc', 'sponsor', 'proxy', 'reseller'];
    return Object.keys(obj).some(k => roleKeys.includes(k.toLowerCase()));
  };

  const isAddressObject = (obj) => {
    if (typeof obj !== 'object' || Array.isArray(obj) || obj === null) return false;
    const addressKeys = ['street_address', 'street', 'locality', 'city', 'region', 'state', 'postal_code', 'postcode', 'zip', 'country', 'po_box', 'ext_address'];
    return addressKeys.some(k => k in obj || k.toLowerCase() in obj);
  };

  const isSecureDnsObject = (obj) => {
    if (typeof obj !== 'object' || Array.isArray(obj) || obj === null) return false;
    return 'delegationSigned' in obj || 'zoneSigned' in obj || 'dsData' in obj || 'keyData' in obj;
  };

  const isContactObject = (obj) => {
    if (typeof obj !== 'object' || Array.isArray(obj) || obj === null) return false;
    const contactKeys = ['name', 'email', 'tel', 'phone', 'address', 'organization', 'org'];
    return contactKeys.filter(k => k in obj).length >= 2;
  };

  const formatKey = (key) => {
    return String(key).replace(/([A-Z])/g, ' $1').replace(/[_-]/g, ' ').replace(/\b\w/g, c => c.toUpperCase()).trim();
  };

  const formatDate = (dateString) => {
    if (!dateString) return 'N/A';
    try {
      const date = new Date(dateString);
      if (isNaN(date.getTime())) return String(dateString);
      return date.toLocaleString();
    } catch { return String(dateString); }
  };

  const getFieldIcon = (key) => {
    const k = String(key).toLowerCase();
    if (isDateKey(k)) return 'bi-calendar';
    if (isEmailKey(k)) return 'bi-envelope';
    if (isPhoneKey(k)) return 'bi-telephone';
    if (isUrlKey(k)) return 'bi-link-45deg';
    if (isStatusKey(k)) return 'bi-shield-check';
    if (isHandleKey(k)) return 'bi-hash';
    if (isAddressKey(k)) return 'bi-geo-alt';
    if (k.includes('name')) return 'bi-tag';
    if (k.includes('server')) return 'bi-server';
    if (k.includes('network')) return 'bi-diagram-3';
    if (k.includes('autnum') || k.includes('asn')) return 'bi-hdd-rack';
    if (k.includes('entity') || k.includes('contact') || k.includes('registr')) return 'bi-person';
    if (k.includes('dns') || k.includes('sec')) return 'bi-shield-lock';
    return 'bi-dot';
  };

  const getDateIcon = (key) => {
    const k = String(key).toLowerCase();
    if (k.includes('registration') || k.includes('created') || k.includes('creation')) return 'bi-calendar-plus text-success';
    if (k.includes('expir') || k.includes('deletion')) return 'bi-calendar-x text-danger';
    if (k.includes('changed') || k.includes('updated') || k.includes('modified')) return 'bi-calendar-check text-primary';
    return 'bi-calendar text-secondary';
  };

  const renderPrimitive = (key, value, reqLevel = null) => {
    if (value === null || value === undefined || value === '') {
      return <span className="text-muted fst-italic">{t('rdapRenderer.notAvailable')}</span>;
    }
    const strValue = String(value);
    const levelTag = renderAccessLevelTag(reqLevel);

    if (typeof value === 'boolean') {
      return <span><span className={`badge ${value ? 'bg-success' : 'bg-warning text-dark'}`}>{value ? t('rdapRenderer.yes') : t('rdapRenderer.no')}</span>{levelTag}</span>;
    }
    if (isDateKey(key) || looksLikeDate(value)) {
      return <span className="text-nowrap"><i className={`bi ${getDateIcon(key)} me-1`}></i>{formatDate(value)}{levelTag}</span>;
    }
    if (isUrlKey(key) || looksLikeUrl(value)) {
      return <span><a href={strValue} target="_blank" rel="noopener noreferrer" className="text-decoration-none text-break"><i className="bi bi-box-arrow-up-right me-1"></i>{strValue}</a>{levelTag}</span>;
    }
    if (isEmailKey(key) || looksLikeEmail(value)) {
      return <span><a href={`mailto:${strValue}`} className="text-decoration-none"><i className="bi bi-envelope me-1"></i>{strValue}</a>{levelTag}</span>;
    }
    if (isPhoneKey(key) || looksLikePhone(value)) {
      return <span><a href={`tel:${strValue}`} className="text-decoration-none"><i className="bi bi-telephone me-1"></i>{strValue}</a>{levelTag}</span>;
    }
    if (isHandleKey(key)) {
      return <span><code className="bg-light px-1 py-0 rounded small">{strValue}</code>{levelTag}</span>;
    }
    return <span>{strValue}{levelTag}</span>;
  };

  const renderAccessLevelBadge = (level) => {
    if (level === undefined || level === null) return null;
    const colors = { 0: 'bg-danger', 1: 'bg-warning text-dark', 2: 'bg-info text-dark', 3: 'bg-success' };
    const labels = { 0: 'L0 Minimal', 1: 'L1 Basic', 2: 'L2 Standard', 3: 'L3 Full' };
    return <span className={`badge ${colors[level] || 'bg-secondary'}`} style={{ fontSize: '10px' }}>{labels[level] || `L${level}`}</span>;
  };

  const renderStatusArray = (statuses, reqLevel = null) => {
    if (!Array.isArray(statuses)) statuses = [statuses];
    return (
      <div className="d-flex flex-wrap gap-1 align-items-center">
        {statuses.filter(s => s).map((status, idx) => (
          <span key={idx} className="badge bg-info text-dark" style={{ fontSize: '11px' }}>{safeString(status)}</span>
        ))}
        {renderAccessLevelTag(reqLevel)}
      </div>
    );
  };

  const renderAddress = (address) => {
    if (!address || typeof address !== 'object') return renderPrimitive('address', address);
    const parts = [];
    const street = address.street_address || address.street || address.streetAddress;
    const ext = address.ext_address || address.extAddress;
    const poBox = address.po_box || address.poBox;
    const city = address.locality || address.city;
    const region = address.region || address.state || address.province;
    const postal = address.postal_code || address.postalCode || address.postcode || address.zip;
    const country = address.country || address.countryName;
    if (street) parts.push(street);
    if (ext) parts.push(ext);
    if (poBox) parts.push(`PO Box ${poBox}`);
    const cityLine = [city, region, postal].filter(Boolean).join(', ');
    if (cityLine) parts.push(cityLine);
    if (country) parts.push(country);
    if (parts.length === 0) return renderGenericObject(address, 1);
    return <div className="small">{parts.map((part, idx) => <div key={idx}>{safeString(part)}</div>)}</div>;
  };

  const renderVcard = (vcardArray, policyFields = null) => {
    if (!vcardArray || !Array.isArray(vcardArray) || !vcardArray[1]) return null;
    const fields = vcardArray[1];
    const labels = { fn: 'Name', org: 'Organization', email: 'Email', tel: 'Phone', adr: 'Address', title: 'Title', role: 'Role', url: 'URL', note: 'Note', kind: 'Kind' };
    return (
      <div>
        {fields.map((field, idx) => {
          if (!Array.isArray(field) || field.length < 4) return null;
          const [fieldType, params, , value] = field;
          if (fieldType === 'version') return null;
          if (isEmpty(value)) return null;
          const label = labels[fieldType] || formatKey(fieldType);
          let displayValue = value;
          if (fieldType === 'adr') {
            if (Array.isArray(value)) {
              const joined = value.filter(Boolean).join(', ');
              if (joined) { displayValue = joined; }
              else if (params && params.label) { displayValue = params.label; }
              else { return null; }
            }
          }
          const typeInfo = params && params.type ? ` (${params.type})` : '';
          const policyDetail = policyDetailFor(policyFields, `vcardArray.${fieldType}`);
          return (
            <div key={idx} className="mb-1">
              <span className="text-muted small">{label}{typeInfo}: </span>
              {fieldType === 'email' ? renderPrimitive('email', safeString(displayValue))
                : fieldType === 'tel' ? renderPrimitive('tel', safeString(displayValue))
                : fieldType === 'url' ? renderPrimitive('url', safeString(displayValue))
                : <span>{safeString(displayValue)}</span>}
              {renderPolicyLevels(policyDetail)}
            </div>
          );
        })}
      </div>
    );
  };

  const renderEvents = (events) => {
    if (!Array.isArray(events)) return null;
    return (
      <div className="d-flex flex-wrap gap-2">
        {events.map((event, idx) => {
          const action = event.eventAction || event.action || 'Event';
          const eventDate = event.eventDate || event.date;
          return (
            <div key={idx} className="p-2 bg-light rounded small" style={{ minWidth: '140px' }}>
              <span className="text-muted d-block" style={{ fontSize: '11px' }}>
                <i className={`bi ${getDateIcon(action)} me-1`}></i>{formatKey(action)}
              </span>
              <span>{formatDate(eventDate)}</span>
              {event.eventActor && <div className="text-muted" style={{ fontSize: '10px' }}>by: {safeString(event.eventActor)}</div>}
            </div>
          );
        })}
      </div>
    );
  };

  const renderNameservers = (nameservers) => {
    if (!Array.isArray(nameservers)) return null;
    return (
      <div className="d-flex flex-wrap gap-2">
        {nameservers.map((ns, idx) => {
          if (typeof ns === 'string') {
            return <div key={idx} className="p-1 px-2 bg-light rounded font-monospace small"><i className="bi bi-server text-primary me-1"></i>{ns}</div>;
          }
          const nsName = ns.ldhName || ns.name || ns.handle || JSON.stringify(ns);
          const nsIps = ns.ipAddresses || null;
          return (
            <div key={idx} className="p-1 px-2 bg-light rounded">
              <div className="font-monospace small"><i className="bi bi-server text-primary me-1"></i>{safeString(nsName)}</div>
              {nsIps && (
                <div className="text-muted ms-3" style={{ fontSize: '11px' }}>
                  {nsIps.v4 && <span>v4: {nsIps.v4.join(', ')} </span>}
                  {nsIps.v6 && <span>v6: {nsIps.v6.join(', ')}</span>}
                </div>
              )}
            </div>
          );
        })}
      </div>
    );
  };

  const renderLinks = (links) => {
    if (!Array.isArray(links)) return null;
    return (
      <div className="d-flex flex-wrap gap-2">
        {links.map((link, idx) => (
          <a key={idx} href={link.href} target="_blank" rel="noopener noreferrer" className="text-decoration-none small p-1 px-2 bg-light rounded text-break">
            <i className="bi bi-box-arrow-up-right me-1"></i>{safeString(link.title || link.rel || 'Link')}
          </a>
        ))}
      </div>
    );
  };

  const renderNotices = (notices) => {
    if (!Array.isArray(notices)) return null;
    return (
      <div>
        {notices.map((notice, idx) => (
          <div key={idx} className="border rounded p-2 mb-1 small">
            {notice.title && <strong className="d-block">{safeString(notice.title)}</strong>}
            {notice.description && (
              Array.isArray(notice.description)
                ? notice.description.map((desc, didx) => <p key={didx} className="mb-0 small">{safeString(desc)}</p>)
                : <p className="mb-0 small">{safeString(notice.description)}</p>
            )}
            {notice.links && Array.isArray(notice.links) && notice.links.length > 0 && (
              <div className="mt-1">
                {notice.links.map((link, lidx) => (
                  <a key={lidx} href={typeof link === 'object' ? link.href : link} target="_blank" rel="noopener noreferrer" className="small text-decoration-none me-2">
                    <i className="bi bi-link-45deg me-1"></i>{safeString(typeof link === 'object' ? (link.title || 'Link') : 'Link')}
                  </a>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    );
  };

  const renderRedacted = (redacted) => {
    if (!Array.isArray(redacted)) return null;
    return (
      <div>
        <div className="small text-muted mb-1"><i className="bi bi-eye-slash me-1"></i>{redacted.length} field{redacted.length !== 1 ? 's' : ''} redacted</div>
        <div className="d-flex flex-wrap gap-1">
          {redacted.slice(0, 10).map((item, idx) => (
            <span key={idx} className="badge bg-light text-dark border" style={{ fontSize: '10px' }}>
              {item.name ? safeString(typeof item.name === 'object' ? item.name.description : item.name) : `#${idx + 1}`}
            </span>
          ))}
          {redacted.length > 10 && <span className="badge bg-light text-muted border" style={{ fontSize: '10px' }}>+{redacted.length - 10} more</span>}
        </div>
      </div>
    );
  };

  const renderNetworkCard = (network, idx) => {
    const name = network.name || network.handle || `Network ${idx + 1}`;
    const range = network.startAddress && network.endAddress
      ? `${safeString(network.startAddress)} — ${safeString(network.endAddress)}` : network.handle;
    return (
      <div className="border rounded p-2 mb-2">
        <div className="d-flex justify-content-between align-items-center mb-1">
          <strong className="small"><i className="bi bi-diagram-3 me-1 text-primary"></i>{safeString(name)}</strong>
          <div className="d-flex gap-1">
            {network.ipVersion && <span className="badge bg-primary" style={{ fontSize: '10px' }}>{safeString(network.ipVersion)}</span>}
            {network.country && <span className="badge bg-info text-dark" style={{ fontSize: '10px' }}>{safeString(network.country)}</span>}
          </div>
        </div>
        {range && <div className="font-monospace small">{safeString(range)}</div>}
        {network.cidr0_cidrs && (
          <div className="small"><span className="text-muted">CIDR:</span> <span className="font-monospace">{network.cidr0_cidrs.map(c => `${c.v4prefix || c.v6prefix || ''}/${c.length}`).join(', ')}</span></div>
        )}
        {network.parentHandle && <div className="small"><span className="text-muted">Parent:</span> <code className="small">{safeString(network.parentHandle)}</code></div>}
        {network.events && isEventsArray(network.events) && <div className="mt-1">{renderEvents(network.events)}</div>}
        {network.entities && isEntitiesArray(network.entities) && <div className="mt-1 pt-1 border-top">{renderEntitiesArray(network.entities)}</div>}
      </div>
    );
  };

  const renderNetworksArray = (networks) => {
    if (!Array.isArray(networks)) return null;
    return <div>{networks.map((net, idx) => <div key={idx}>{renderNetworkCard(net, idx)}</div>)}</div>;
  };

  const renderAutnumCard = (autnum, idx) => {
    const name = autnum.name || `AS${autnum.startAutnum || autnum.handle || idx + 1}`;
    return (
      <div className="border rounded p-2 mb-2">
        <strong className="small"><i className="bi bi-hdd-rack me-1 text-primary"></i>{safeString(autnum.handle || name)}</strong>
        {autnum.name && autnum.handle && <span className="text-muted ms-1 small">({safeString(autnum.name)})</span>}
        {autnum.startAutnum !== undefined && (
          <div className="small font-monospace">
            {autnum.startAutnum === autnum.endAutnum ? safeString(autnum.startAutnum) : `${safeString(autnum.startAutnum)} — ${safeString(autnum.endAutnum)}`}
          </div>
        )}
        {autnum.events && isEventsArray(autnum.events) && <div className="mt-1">{renderEvents(autnum.events)}</div>}
        {autnum.entities && isEntitiesArray(autnum.entities) && <div className="mt-1 pt-1 border-top">{renderEntitiesArray(autnum.entities)}</div>}
      </div>
    );
  };

  const renderAutnumsArray = (autnums) => {
    if (!Array.isArray(autnums)) return null;
    return <div>{autnums.map((an, idx) => <div key={idx}>{renderAutnumCard(an, idx)}</div>)}</div>;
  };

  const renderSecureDns = (secureDNS) => {
    if (typeof secureDNS !== 'object' || secureDNS === null) return null;
    return (
      <div className="d-flex flex-wrap gap-2">
        {Object.entries(secureDNS).map(([key, value]) => {
          if (isEmpty(value)) return null;
          if (key === 'dsData' || key === 'keyData') {
            return (
              <div className="w-100" key={key}>
                <span className="text-muted small">{formatKey(key)}: </span>
                {Array.isArray(value) ? value.map((item, idx) => (
                  <div key={idx} className="font-monospace small">{Object.entries(item).map(([k, v]) => `${k}: ${safeString(v)}`).join(', ')}</div>
                )) : renderValue(key, value, 1)}
              </div>
            );
          }
          return (
            <div key={key} className="p-2 bg-light rounded small">
              <span className="text-muted">{formatKey(key)}: </span>
              {typeof value === 'boolean' ? renderPrimitive(key, value) : <span>{renderValue(key, value, 1)}</span>}
            </div>
          );
        })}
      </div>
    );
  };

  // Compact contact card — tight layout
  const renderContactCard = (contact, title) => {
    const handleStr = typeof contact.handle === 'string' ? contact.handle : safeString(contact.handle);
    const roles = Array.isArray(contact.roles) ? contact.roles : [];
    const displayTitle = title || roles.join(', ') || 'Entity';
    const policyFields = contact.policyLevels?.fields || null;

    return (
      <div className="border rounded h-100" style={{ fontSize: '13px' }}>
        <div className="bg-light px-2 py-1 d-flex align-items-center gap-1 border-bottom">
          <i className={`bi ${getFieldIcon(displayTitle)} text-primary`} style={{ fontSize: '12px' }}></i>
          <strong className="small">{formatKey(displayTitle)}</strong>
          {handleStr && <code className="ms-auto text-muted" style={{ fontSize: '10px' }}>{handleStr}</code>}
          {handleStr && renderPolicyLevels(policyDetailFor(policyFields, 'handle'))}
        </div>
        <div className="px-2 py-1">
          {contact.vcardArray && renderVcard(contact.vcardArray, policyFields)}
          {Object.entries(contact).map(([key, value]) => {
            if (['vcardArray', 'roles', 'handle', 'objectClassName', 'links', 'events', 'entities', 'policyLevels'].includes(key)) return null;
            if (isEmpty(value)) return null;
            return (
              <div key={key} className="mb-1">
                <span className="text-muted small">{formatKey(key)}: </span>
                {renderValue(key, value, 2)}
                {renderPolicyLevels(policyDetailFor(policyFields, key))}
              </div>
            );
          })}
          {contact.entities && Array.isArray(contact.entities) && contact.entities.length > 0 && (
            <div className="mt-1 pt-1 border-top">
              <span className="text-muted small d-block mb-1">Related:</span>
              {renderEntitiesArray(contact.entities)}
            </div>
          )}
        </div>
      </div>
    );
  };

  const renderEntitiesArray = (entities) => {
    if (!Array.isArray(entities)) return null;
    /* Render one card per role. A contact that holds several roles (e.g. both
    Registrant and Billing) is shown as separate cards one per role rather
    than a single combined "Registrant, Billing" card, even though it is the
    same underlying entity. */
    const cards = [];
    entities.forEach((entity, idx) => {
      const roles = Array.isArray(entity.roles) ? entity.roles : [];
      if (roles.length > 1) {
        roles.forEach((role, rIdx) => {
          cards.push(
            <div key={`${idx}-${rIdx}`} className="col-md-6">
              {renderContactCard(entity, role)}
            </div>
          );
        });
      } else {
        const title = roles.length === 1 ? roles[0] : 'Entity';
        cards.push(
          <div key={`${idx}`} className="col-md-6">
            {renderContactCard(entity, title)}
          </div>
        );
      }
    });
    return <div className="row g-2">{cards}</div>;
  };

  const renderEntitiesDictionary = (entities) => {
    const roleOrder = ['registrant', 'registrar', 'administrative', 'admin', 'technical', 'tech', 'billing', 'abuse', 'noc', 'sponsor', 'proxy', 'reseller'];
    const sortedRoles = Object.keys(entities).sort((a, b) => {
      const aIdx = roleOrder.findIndex(r => a.toLowerCase().includes(r));
      const bIdx = roleOrder.findIndex(r => b.toLowerCase().includes(r));
      if (aIdx === -1 && bIdx === -1) return a.localeCompare(b);
      if (aIdx === -1) return 1;
      if (bIdx === -1) return -1;
      return aIdx - bIdx;
    });
    return (
      <div className="row g-2">
        {sortedRoles.map(role => {
          const entityData = entities[role];
          if (isEmpty(entityData)) return null;
          const entityList = Array.isArray(entityData) ? entityData : [entityData];
          return entityList.map((entity, idx) => (
            <div key={`${role}-${idx}`} className="col-md-6">
              {renderContactCard(entity, role)}
            </div>
          ));
        })}
      </div>
    );
  };

  const renderGenericArray = (arr, depth) => {
    if (arr.length === 0) return <span className="text-muted fst-italic">Empty</span>;
    if (arr.every(item => typeof item === 'string' || typeof item === 'number' || typeof item === 'boolean')) {
      return (
        <div className="d-flex flex-wrap gap-1">
          {arr.map((item, idx) => <span key={idx} className="badge bg-secondary" style={{ fontSize: '11px' }}>{String(item)}</span>)}
        </div>
      );
    }
    return (
      <div className={depth > 0 ? 'ms-2' : ''}>
        {arr.map((item, idx) => (
          <div key={idx} className={`mb-1 ${idx < arr.length - 1 ? 'pb-1 border-bottom' : ''}`}>
            {renderValue(`[${idx}]`, item, depth + 1)}
          </div>
        ))}
      </div>
    );
  };

  const renderGenericObject = (obj, depth) => {
    const entries = Object.entries(obj).filter(([, value]) => !isEmpty(value));
    if (entries.length === 0) return <span className="text-muted fst-italic">Empty</span>;
    return (
      <div className={depth > 0 ? 'ms-3 ps-2 border-start' : ''}>
        {entries.map(([key, value]) => (
          <div key={key} className="mb-1">
            <span className="text-muted small"><i className={`bi ${getFieldIcon(key)} me-1`}></i>{formatKey(key)}: </span>
            {renderValue(key, value, depth + 1)}
          </div>
        ))}
      </div>
    );
  };

  const renderValue = (key, value, depth = 0) => {
    if (isEmpty(value)) return <span className="text-muted fst-italic">N/A</span>;
    const type = getType(value);
    const keyLower = String(key).toLowerCase();

    if (type === 'string' || type === 'number' || type === 'boolean') return renderPrimitive(key, value);

    if (type === 'array') {
      if (isVcardArray(value)) return renderVcard(value);
      if (isEventsArray(value)) return renderEvents(value);
      if (keyLower === 'networks' || isNetworksArray(value)) return renderNetworksArray(value);
      if (keyLower === 'autnums' || isAutnumsArray(value)) return renderAutnumsArray(value);
      if (isEntitiesArray(value)) return renderEntitiesArray(value);
      if (isLinksArray(value)) return renderLinks(value);
      if (keyLower === 'redacted' || isRedactedArray(value)) return renderRedacted(value);
      if (isNoticesArray(value)) return renderNotices(value);
      if (keyLower === 'nameservers' || isNameserversArray(value)) return renderNameservers(value);
      if (isStatusKey(key)) return renderStatusArray(value);
      if (keyLower === 'rdapconformance') {
        return <div className="d-flex flex-wrap gap-1">{value.map((item, idx) => <span key={idx} className="badge bg-secondary" style={{ fontSize: '10px' }}>{safeString(item)}</span>)}</div>;
      }
      return renderGenericArray(value, depth);
    }

    if (type === 'object') {
      if (keyLower === 'entities' && isEntitiesDictionary(value)) return renderEntitiesDictionary(value);
      if (isAddressKey(key) || isAddressObject(value)) return renderAddress(value);
      if (keyLower === 'securedns' || isSecureDnsObject(value)) return renderSecureDns(value);
      if (isContactObject(value)) return renderContactCard(value, key);
      return renderGenericObject(value, depth);
    }

    return <span>{safeString(value)}</span>;
  };

  // ============================================
  // TOP-LEVEL LAYOUT — contacts first, compact headers
  // ============================================

  const renderTopLevel = (obj) => {
    const categories = {
      identity: { title: 'Basic Information', icon: 'bi-info-circle', keys: [] },
      dates: { title: 'Important Dates', icon: 'bi-calendar-event', keys: [] },
      status: { title: 'Status', icon: 'bi-shield-check', keys: [] },
      dns: { title: 'DNS Information', icon: 'bi-hdd-network', keys: [] },
      network: { title: 'Network Information', icon: 'bi-diagram-3', keys: [] },
      contacts: { title: 'Contact Information', icon: 'bi-people', keys: [] },
      links: { title: 'Links & References', icon: 'bi-link-45deg', keys: [] },
      notices: { title: 'Notices & Remarks', icon: 'bi-info-circle', keys: [] },
      other: { title: 'Other Information', icon: 'bi-three-dots', keys: [] },
    };

    Object.entries(obj).forEach(([key, value]) => {
      if (isEmpty(value)) return;
      // Not a data element, so it is rendered inline against the elements it describes...
      if (key === 'policyLevels') return;
      const k = key.toLowerCase();
      if (['objectclassname', 'handle', 'parent_handle', 'ldhname', 'name', 'unicodename', 'unicode_name', 'type', 'rir', 'lang'].includes(k)) {
        categories.identity.keys.push(key);
      } else if (isDateKey(key) || k === 'events' || (Array.isArray(value) && isEventsArray(value))) {
        categories.dates.keys.push(key);
      } else if (isStatusKey(key)) {
        categories.status.keys.push(key);
      } else if (['nameservers', 'securedns', 'dnssec'].includes(k)) {
        categories.dns.keys.push(key);
      } else if (['startaddress', 'endaddress', 'ipversion', 'cidr', 'cidr0_cidrs', 'parenthandle', 'networktype', 'country', 'networks', 'autnums', 'startautnum', 'endautnum'].includes(k)) {
        categories.network.keys.push(key);
      } else if (k === 'entities' || k.includes('contact') || k.includes('registr')) {
        categories.contacts.keys.push(key);
      } else if (['links', 'port43', 'url', 'whois_server', 'terms_of_service_url'].includes(k) || isUrlKey(key)) {
        categories.links.keys.push(key);
      } else if (['notices', 'remarks', 'description', 'copyright_notice', 'redacted'].includes(k)) {
        categories.notices.keys.push(key);
      } else if (['rdapconformance'].includes(k)) {
        categories.other.keys.push(key);
      } else {
        categories.other.keys.push(key);
      }
    });

    // Contacts first, then identity so the domain name is visible right after contacts
    const orderedCategories = ['contacts', 'identity', 'dates', 'status', 'dns', 'network', 'links', 'notices', 'other'];
    const nonEmptyCategories = orderedCategories
      .map(catKey => ({ key: catKey, ...categories[catKey] }))
      .filter(cat => cat.keys.length > 0);

    return (
      <div className={className}>
        {accessLevel !== undefined && (
          <div className="d-flex align-items-center gap-2 mb-2 py-1 px-2 bg-light rounded small">
            <strong>Access:</strong>
            {renderAccessLevelBadge(accessLevel)}
            <span className="text-muted ms-auto" style={{ fontSize: '11px' }}>Filtered by agreement</span>
          </div>
        )}

        {policyLevels && (
          <div className="d-flex align-items-center flex-wrap gap-2 mb-2 py-1 px-2 border rounded small">
            <strong>{t('rdapRenderer.policyLevels.title')}</strong>
            {policyLevels.policyName && (
              <span className="text-muted">
                {t('rdapRenderer.policyLevels.policyName', { name: policyLevels.policyName })}
              </span>
            )}
            <span className="text-muted" style={{ fontSize: '11px' }}>
              {t('rdapRenderer.policyLevels.legend')}
            </span>
            <button
              type="button"
              className="btn btn-link btn-sm p-0 ms-auto text-decoration-none"
              style={{ fontSize: '11px' }}
              onClick={() => setShowPolicyLevels((prev) => !prev)}
            >
              {showPolicyLevels
                ? t('rdapRenderer.policyLevels.hide')
                : t('rdapRenderer.policyLevels.show')}
            </button>
          </div>
        )}

        {nonEmptyCategories.map(category => {
          const isCollapsed = collapsedSections[category.key];

          return (
            <div key={category.key} className="mb-3">
              <div
                className={`d-flex align-items-center mb-2 ${category.key === 'other' ? 'text-secondary' : 'text-primary'}`}
                onClick={() => toggleSection(category.key)}
                style={{ cursor: 'pointer', fontSize: '14px', fontWeight: 600 }}
              >
                <i className={`bi ${category.icon} me-2`}></i>
                {category.title}
                <i className={`bi ${isCollapsed ? 'bi-chevron-down' : 'bi-chevron-up'} ms-auto`} style={{ fontSize: '12px' }}></i>
              </div>

              {!isCollapsed && (
                <div className="row g-2">
                  {category.keys.map(key => {
                    const value = obj[key];
                    const rendered = renderValue(key, value, 0);

                    const isFullWidth =
                      Array.isArray(value) && value.length > 0 && typeof value[0] === 'object' ||
                      (typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length > 3);

                    return (
                      <div key={key} className={isFullWidth ? 'col-12' : 'col-md-6'}>
                        {isFullWidth ? (
                          <div>
                            <span className="text-muted small">
                              <i className={`bi ${getFieldIcon(key)} me-1`}></i>{formatKey(key)}:
                              {renderPolicyLevels(policyDetailFor(topLevelPolicyFields, key))}
                            </span>
                            {rendered}
                          </div>
                        ) : (
                          <div className="p-2 bg-light rounded h-100">
                            <span className="d-block text-muted small mb-1">
                              <i className={`bi ${getFieldIcon(key)} me-1`}></i>{formatKey(key)}:
                              {renderPolicyLevels(policyDetailFor(topLevelPolicyFields, key))}
                            </span>
                            {rendered}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </div>
    );
  };

  return renderTopLevel(cleanData);
};

export default ReflectiveRdapRenderer;