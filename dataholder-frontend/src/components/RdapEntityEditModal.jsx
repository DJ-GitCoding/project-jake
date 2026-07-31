/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import { useT } from '../i18n';

// Icon wrapper component for consistent sizing
const Icon = ({ children, size = 16 }) => (
  <span style={{ display: 'inline-flex', width: size, height: size, flexShrink: 0, alignItems: 'center', justifyContent: 'center' }}>
    {children}
  </span>
);

// Event action types as per RDAP spec
const EVENT_ACTIONS = [
  'REGISTRATION', 'REREGISTRATION', 'LAST_CHANGED', 'EXPIRATION', 'DELETION',
  'REINSTANTIATION', 'TRANSFER', 'LOCKED', 'UNLOCKED', 'LAST_UPDATE_OF_RDAP_DATABASE',
  'REGISTRAR_EXPIRATION', 'ENUM_VALIDATION_EXPIRATION'
];

// Entity roles
const ENTITY_ROLES = [
  'registrant', 'admin', 'tech', 'billing', 'abuse', 'registrar',
  'reseller', 'sponsor', 'proxy', 'notifications', 'noc'
];

// Status values
const STATUS_VALUES = [
  'active', 'inactive', 'validated', 'renew prohibited', 'update prohibited',
  'transfer prohibited', 'delete prohibited', 'client delete prohibited',
  'client hold', 'client renew prohibited', 'client transfer prohibited',
  'client update prohibited', 'server delete prohibited', 'server hold',
  'server renew prohibited', 'server transfer prohibited', 'server update prohibited',
  'locked', 'ok', 'pending create', 'pending delete', 'pending renew',
  'pending restore', 'pending transfer', 'pending update'
];

// Initial form state for entities
const getInitialFormData = (activeTab) => {
  const common = {
    handle: '', status: [], port43: '', isTestData: false,
    events: [], links: [], remarks: [],
  };

  switch (activeTab) {
    case 'domains':
      return { ...common, ldhName: '', unicodeName: '', secureDnsDelegationSigned: false,
        secureDnsZoneSigned: false, nameservers: [], secureDnsRecords: [] };
    case 'ips':
      return { ...common, startAddress: '', endAddress: '', ipVersion: 'v4',
        networkName: '', networkType: '', country: '', parentHandle: '' };
    case 'asns':
      return { ...common, startAutnum: '', endAutnum: '', autnumName: '',
        autnumType: '', country: '' };
    default:
      return common;
  }
};

// Dark theme colors - matching the main component's theme
const darkTheme = {
  bgPrimary: '#111318',
  bgSecondary: '#1a1d24',
  bgTertiary: '#242830',
  bgHover: '#2a2f38',
  border: '#2d333b',
  borderLight: '#373e47',
  text: '#e6edf3',
  textSecondary: '#b1bac4',
  textMuted: '#7d8590',
  primary: '#2f81f7',
  primaryHover: '#388bfd',
  success: '#238636',
  danger: '#da3633',
  dangerHover: '#f85149',
  warning: '#9e6a03',
  warningBg: 'rgba(187, 128, 9, 0.15)',
  warningBorder: 'rgba(187, 128, 9, 0.4)',
  info: '#1f6feb',
};

// Modal styles - Dark theme matching main component
const modalOverlayStyle = {
  position: 'fixed',
  top: 0,
  left: 0,
  right: 0,
  bottom: 0,
  backgroundColor: 'rgba(0, 0, 0, 0.8)',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  zIndex: 1050,
  padding: '20px',
};

const modalContentStyle = {
  backgroundColor: darkTheme.bgPrimary,
  borderRadius: '12px',
  width: '100%',
  maxWidth: '1100px',
  maxHeight: '90vh',
  display: 'flex',
  flexDirection: 'column',
  boxShadow: '0 16px 70px rgba(0, 0, 0, 0.5)',
  border: `1px solid ${darkTheme.border}`,
  color: darkTheme.text,
};

const modalHeaderStyle = {
  padding: '16px 24px',
  borderBottom: `1px solid ${darkTheme.border}`,
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  backgroundColor: darkTheme.bgSecondary,
  borderRadius: '12px 12px 0 0',
};

const modalBodyStyle = {
  padding: '24px',
  overflowY: 'auto',
  flex: 1,
  backgroundColor: darkTheme.bgPrimary,
};

const modalFooterStyle = {
  padding: '16px 24px',
  borderTop: `1px solid ${darkTheme.border}`,
  display: 'flex',
  justifyContent: 'flex-end',
  gap: '12px',
  backgroundColor: darkTheme.bgSecondary,
  borderRadius: '0 0 12px 12px',
};

const contactModalContentStyle = {
  ...modalContentStyle,
  maxWidth: '800px',
  zIndex: 1060,
};

// Dark theme form styles
const darkInputStyle = {
  backgroundColor: darkTheme.bgSecondary,
  border: `1px solid ${darkTheme.border}`,
  color: darkTheme.text,
  borderRadius: '6px',
  padding: '8px 12px',
  width: '100%',
  fontSize: '14px',
  outline: 'none',
  transition: 'border-color 0.2s, box-shadow 0.2s',
};

const darkInputFocusStyle = {
  borderColor: darkTheme.primary,
  boxShadow: `0 0 0 3px rgba(47, 129, 247, 0.3)`,
};

const darkSelectStyle = {
  ...darkInputStyle,
  cursor: 'pointer',
  appearance: 'none',
  backgroundImage: `url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%237d8590' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e")`,
  backgroundPosition: 'right 8px center',
  backgroundRepeat: 'no-repeat',
  backgroundSize: '16px 12px',
  paddingRight: '32px',
};

const darkLabelStyle = {
  color: darkTheme.textSecondary,
  marginBottom: '6px',
  display: 'block',
  fontSize: '13px',
  fontWeight: 500,
};

const darkFormTextStyle = {
  color: darkTheme.textMuted,
  fontSize: '12px',
  marginTop: '4px',
};

// Button styles
const btnBaseStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: '8px 16px',
  fontSize: '14px',
  fontWeight: 500,
  borderRadius: '6px',
  border: 'none',
  cursor: 'pointer',
  transition: 'all 0.2s',
  gap: '6px',
};

const btnPrimaryStyle = {
  ...btnBaseStyle,
  backgroundColor: darkTheme.primary,
  color: '#ffffff',
};

const btnSecondaryStyle = {
  ...btnBaseStyle,
  backgroundColor: darkTheme.bgTertiary,
  color: darkTheme.text,
  border: `1px solid ${darkTheme.border}`,
};

const btnDangerStyle = {
  ...btnBaseStyle,
  backgroundColor: darkTheme.danger,
  color: '#ffffff',
};

const btnSmStyle = {
  padding: '6px 12px',
  fontSize: '12px',
};

const btnOutlineStyle = {
  ...btnBaseStyle,
  backgroundColor: 'transparent',
  color: darkTheme.textSecondary,
  border: `1px solid ${darkTheme.border}`,
};

const RdapEntityEditModal = ({ isOpen, onClose, editingItem, activeTab, schema, onSave, submitting }) => {
  const { t } = useT();
  const [formData, setFormData] = useState(getInitialFormData(activeTab));
  const [showContactModal, setShowContactModal] = useState(false);
  const [contactFormData, setContactFormData] = useState({});
  const [editingContactIndex, setEditingContactIndex] = useState(null);
  const [expandedSections, setExpandedSections] = useState({
    basic: true, contact: false, events: false, nameservers: false,
    links: false, remarks: false, secureDns: false, childEntities: false,
  });

  useEffect(() => {
    if (isOpen) {
      if (editingItem) {
        setFormData({
          ...editingItem,
          isTestData: editingItem.testDataFlag?.isTestData || false,
          events: editingItem.events || [],
          links: editingItem.links || [],
          nameservers: editingItem.nameservers || [],
          remarks: editingItem.remarks || [],
          secureDnsRecords: editingItem.secureDnsRecords || editingItem.secureDns || [],
          childEntities: editingItem.childEntities || editingItem.children || [],
        });
      } else {
        setFormData(getInitialFormData(activeTab));
      }
      setExpandedSections({ basic: true, contact: false, events: false, nameservers: false,
        links: false, remarks: false, secureDns: false, childEntities: false });
    }
  }, [isOpen, editingItem, activeTab]);

  const toggleSection = (section) => setExpandedSections(prev => ({ ...prev, [section]: !prev[section] }));
  const updateFormField = (field, value) => setFormData(prev => ({ ...prev, [field]: value }));

  // Event handlers
  const addEvent = () => setFormData(prev => ({
    ...prev, events: [...(prev.events || []), { eventAction: 'REGISTRATION', eventDate: '', eventActor: '' }]
  }));
  const updateEvent = (index, field, value) => {
    const events = [...(formData.events || [])];
    events[index] = { ...events[index], [field]: value };
    setFormData(prev => ({ ...prev, events }));
  };
  const removeEvent = (index) => setFormData(prev => ({ ...prev, events: prev.events.filter((_, i) => i !== index) }));

  // Nameserver handlers
  const addNameserver = () => setFormData(prev => ({
    ...prev, nameservers: [...(prev.nameservers || []), { ldhName: '', ipv4Addresses: [], ipv6Addresses: [], status: [] }]
  }));
  const updateNameserver = (index, field, value) => {
    const nameservers = [...(formData.nameservers || [])];
    nameservers[index] = { ...nameservers[index], [field]: value };
    setFormData(prev => ({ ...prev, nameservers }));
  };
  const removeNameserver = (index) => setFormData(prev => ({ ...prev, nameservers: prev.nameservers.filter((_, i) => i !== index) }));

  // Link handlers
  const addLink = () => setFormData(prev => ({
    ...prev, links: [...(prev.links || []), { href: '', rel: 'self', title: '', mediaType: '' }]
  }));
  const updateLink = (index, field, value) => {
    const links = [...(formData.links || [])];
    links[index] = { ...links[index], [field]: value };
    setFormData(prev => ({ ...prev, links }));
  };
  const removeLink = (index) => setFormData(prev => ({ ...prev, links: prev.links.filter((_, i) => i !== index) }));

  // Remark handlers
  const addRemark = () => setFormData(prev => ({
    ...prev, remarks: [...(prev.remarks || []), { remarkType: 'REMARK', title: '', description: [], remarkTypeValue: '' }]
  }));
  const updateRemark = (index, field, value) => {
    const remarks = [...(formData.remarks || [])];
    remarks[index] = { ...remarks[index], [field]: value };
    setFormData(prev => ({ ...prev, remarks }));
  };
  const removeRemark = (index) => setFormData(prev => ({ ...prev, remarks: prev.remarks.filter((_, i) => i !== index) }));

  // Secure DNS handlers
  const addSecureDns = () => setFormData(prev => ({
    ...prev, secureDnsRecords: [...(prev.secureDnsRecords || []), { keyTag: '', algorithm: '', digestType: '', digest: '' }]
  }));
  const updateSecureDns = (index, field, value) => {
    const secureDnsRecords = [...(formData.secureDnsRecords || [])];
    secureDnsRecords[index] = { ...secureDnsRecords[index], [field]: value };
    setFormData(prev => ({ ...prev, secureDnsRecords }));
  };
  const removeSecureDns = (index) => setFormData(prev => ({ ...prev, secureDnsRecords: prev.secureDnsRecords.filter((_, i) => i !== index) }));

  // Contact handlers
  const openContactModal = (contact = null, index = null) => {
    setEditingContactIndex(index);
    setContactFormData(contact ? { ...contact } : {
      handle: '', objectType: 'ENTITY', contactName: '', organization: '', email: '',
      phone: '', fax: '', addressStreet1: '', addressStreet2: '', addressCity: '',
      addressState: '', addressPostalCode: '', addressCountry: '', roles: [],
    });
    setShowContactModal(true);
  };
  const saveContact = () => {
    const childEntities = [...(formData.childEntities || [])];
    if (editingContactIndex !== null) childEntities[editingContactIndex] = contactFormData;
    else childEntities.push(contactFormData);
    setFormData(prev => ({ ...prev, childEntities }));
    setShowContactModal(false);
  };
  const removeContact = (index) => setFormData(prev => ({ ...prev, childEntities: prev.childEntities.filter((_, i) => i !== index) }));

  const renderFormField = (field) => {
    const value = formData[field.name] || '';
    if (field.type === 'boolean') {
      return (
        <select style={darkSelectStyle} value={value === true ? 'true' : value === false ? 'false' : ''}
          onChange={(e) => updateFormField(field.name, e.target.value === '' ? null : e.target.value === 'true')}>
          <option value="">{t('common.notSet')}</option>
          <option value="true">{t('common.yes')}</option>
          <option value="false">{t('common.no')}</option>
        </select>
      );
    }
    if (field.type === 'integer') {
      return <input type="number" style={darkInputStyle} value={value}
        onChange={(e) => updateFormField(field.name, e.target.value ? parseInt(e.target.value) : null)} placeholder={field.example} />;
    }
    if (field.type === 'string[]') {
      return (
        <div>
          <input type="text" style={darkInputStyle} value={Array.isArray(value) ? value.join(', ') : value}
            onChange={(e) => updateFormField(field.name, e.target.value.split(',').map(s => s.trim()).filter(Boolean))} placeholder={field.example} />
          {field.name === 'status' && (
            <div style={{ marginTop: 8, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
              {STATUS_VALUES.slice(0, 8).map(s => (
                <button key={s} type="button" style={{ 
                  ...btnOutlineStyle, 
                  ...btnSmStyle, 
                  fontSize: '10px', 
                  padding: '4px 8px',
                  backgroundColor: Array.isArray(value) && value.includes(s) ? darkTheme.primary : 'transparent',
                  color: Array.isArray(value) && value.includes(s) ? '#ffffff' : darkTheme.textSecondary,
                  borderColor: Array.isArray(value) && value.includes(s) ? darkTheme.primary : darkTheme.border,
                }}
                  onClick={() => {
                    const current = Array.isArray(value) ? value : [];
                    updateFormField(field.name, current.includes(s) ? current.filter(v => v !== s) : [...current, s]);
                  }}>{s}</button>
              ))}
            </div>
          )}
        </div>
      );
    }
    return <input type="text" style={darkInputStyle} value={value}
      onChange={(e) => updateFormField(field.name, e.target.value)} placeholder={field.example} />;
  };

  const CollapsibleSection = ({ title, section, children, count = 0, icon: SectionIcon }) => (
    <div style={{ border: `1px solid ${darkTheme.border}`, borderRadius: 8, marginBottom: 12 }}>
      <div
        onClick={() => toggleSection(section)}
        style={{
          padding: '12px 16px',
          backgroundColor: darkTheme.bgSecondary,
          cursor: 'pointer',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          borderRadius: expandedSections[section] ? '8px 8px 0 0' : 8,
          fontWeight: 600,
          color: darkTheme.text,
        }}
      >
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {SectionIcon && <Icon size={16}><i className={SectionIcon}></i></Icon>}
          {title}
          {count > 0 && <span style={{ 
            marginLeft: 8, 
            backgroundColor: darkTheme.info, 
            color: '#ffffff', 
            padding: '2px 8px', 
            borderRadius: '10px', 
            fontSize: '12px',
            fontWeight: 500,
          }}>{count}</span>}
        </span>
        <Icon size={16}>
          {expandedSections[section] ? <i className="fa-solid fa-chevron-up"></i> : <i className="fa-solid fa-chevron-down"></i>}
        </Icon>
      </div>
      {expandedSections[section] && <div style={{ padding: 16, backgroundColor: darkTheme.bgPrimary }}>{children}</div>}
    </div>
  );

  const handleSave = () => {
    const payload = { ...formData };
    if (payload.events) {
      payload.events = payload.events.map(event => ({
        ...event, eventAction: event.eventAction || 'REGISTRATION',
        eventDate: event.eventDate ? new Date(event.eventDate).toISOString() : null,
      }));
    }
    if (typeof payload.status === 'string') payload.status = payload.status.split(',').map(s => s.trim()).filter(Boolean);
    if (activeTab === 'asns') {
      if (payload.startAutnum) payload.startAutnum = parseInt(payload.startAutnum);
      if (payload.endAutnum) payload.endAutnum = parseInt(payload.endAutnum);
    }
    onSave(payload);
  };

  if (!isOpen) return null;

  const closeButtonStyle = {
    background: 'transparent',
    border: 'none',
    color: darkTheme.textMuted,
    cursor: 'pointer',
    padding: '4px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: '4px',
    transition: 'background-color 0.2s',
  };

  return (
    <>
      {/* Main Modal */}
      <div style={modalOverlayStyle} onClick={onClose}>
        <div style={modalContentStyle} onClick={(e) => e.stopPropagation()}>
          <div style={modalHeaderStyle}>
            <h5 style={{ margin: 0, fontSize: 18, color: darkTheme.text, fontWeight: 600 }}>{editingItem ? t('rdapEntity.editTitle', { type: activeTab.slice(0, -1) }) : t('rdapEntity.addTitle', { type: activeTab.slice(0, -1) })}</h5>
            <button type="button" style={closeButtonStyle} onClick={onClose} aria-label={t('common.close')}>
              <i className="fa-solid fa-xmark" style={{ fontSize: 20 }}></i>
            </button>
          </div>
          <div style={modalBodyStyle}>
            {/* Basic Information */}
            <CollapsibleSection title={t('rdapEntity.sections.basicInformation')} section="basic" icon="fa-solid fa-database">
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16 }}>
                {schema.map((field) => (
                  <div key={field.name}>
                    <label style={darkLabelStyle}>{field.name}{field.required && <span style={{ color: darkTheme.danger, marginLeft: 2 }}>*</span>}</label>
                    {renderFormField(field)}
                    <div style={darkFormTextStyle}>{field.description}</div>
                  </div>
                ))}
              </div>
              <div style={{ marginTop: 16, padding: 12, backgroundColor: darkTheme.warningBg, border: `1px solid ${darkTheme.warningBorder}`, borderRadius: 8 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <input type="checkbox" id="isTestData" style={{ width: 16, height: 16, cursor: 'pointer' }}
                    checked={formData.isTestData || false} onChange={(e) => updateFormField('isTestData', e.target.checked)} />
                  <label style={{ color: darkTheme.text, cursor: 'pointer', fontSize: '14px' }} htmlFor="isTestData">
                    {t('rdapEntity.testData.label')} {formData.isTestData && <span style={{
                      marginLeft: 8,
                      backgroundColor: darkTheme.warning,
                      color: '#ffffff',
                      padding: '2px 8px',
                      borderRadius: '4px',
                      fontSize: '11px',
                      fontWeight: 600,
                    }}>{t('rdapEntity.testData.badge')}</span>}
                  </label>
                </div>
                <div style={{ ...darkFormTextStyle, marginTop: 6, marginLeft: 24 }}>{t('rdapEntity.testData.hint')}</div>
              </div>
            </CollapsibleSection>

            {/* Events */}
            <CollapsibleSection title={t('rdapEntity.sections.events')} section="events" count={(formData.events || []).length} icon="fa-solid fa-clock">
              {(formData.events || []).map((event, idx) => (
                <div key={idx} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr auto', gap: 12, marginBottom: 12, padding: 12, backgroundColor: darkTheme.bgSecondary, borderRadius: 6, border: `1px solid ${darkTheme.border}` }}>
                  <div>
                    <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.events.action')}</label>
                    <select style={{ ...darkSelectStyle, padding: '6px 10px', fontSize: '13px' }} value={event.eventAction || ''} onChange={(e) => updateEvent(idx, 'eventAction', e.target.value)}>
                      {EVENT_ACTIONS.map(a => <option key={a} value={a}>{a.replace(/_/g, ' ')}</option>)}
                    </select>
                  </div>
                  <div>
                    <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.events.date')}</label>
                    <input type="datetime-local" style={{ ...darkInputStyle, padding: '6px 10px', fontSize: '13px' }} value={event.eventDate ? event.eventDate.slice(0, 16) : ''} onChange={(e) => updateEvent(idx, 'eventDate', e.target.value)} />
                  </div>
                  <div>
                    <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.events.actor')}</label>
                    <input type="text" style={{ ...darkInputStyle, padding: '6px 10px', fontSize: '13px' }} placeholder={t('rdapEntity.events.actorPlaceholder')} value={event.eventActor || ''} onChange={(e) => updateEvent(idx, 'eventActor', e.target.value)} />
                  </div>
                  <div style={{ alignSelf: 'end' }}>
                    <button style={{ ...btnDangerStyle, ...btnSmStyle }} onClick={() => removeEvent(idx)}>
                      <Icon size={14}><i className="fa-solid fa-trash"></i></Icon>
                    </button>
                  </div>
                </div>
              ))}
              <button style={btnOutlineStyle} onClick={addEvent}>
                <Icon size={14}><i className="fa-solid fa-plus"></i></Icon> {t('rdapEntity.events.add')}
              </button>
            </CollapsibleSection>

            {/* Nameservers (Domains only) */}
            {activeTab === 'domains' && (
              <CollapsibleSection title={t('rdapEntity.sections.nameservers')} section="nameservers" count={(formData.nameservers || []).length} icon="fa-solid fa-server">
                {(formData.nameservers || []).map((ns, idx) => (
                  <div key={idx} style={{ marginBottom: 12, padding: 12, backgroundColor: darkTheme.bgSecondary, borderRadius: 6, border: `1px solid ${darkTheme.border}` }}>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: 12, marginBottom: 12 }}>
                      <div>
                        <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.nameserver.name')}</label>
                        <input type="text" style={{ ...darkInputStyle, padding: '6px 10px', fontSize: '13px' }} placeholder={t('rdapEntity.nameserver.namePlaceholder')} value={ns.ldhName || ''} onChange={(e) => updateNameserver(idx, 'ldhName', e.target.value)} />
                      </div>
                      <div style={{ alignSelf: 'end' }}>
                        <button style={{ ...btnDangerStyle, ...btnSmStyle }} onClick={() => removeNameserver(idx)}>
                          <Icon size={14}><i className="fa-solid fa-trash"></i></Icon>
                        </button>
                      </div>
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                      <div>
                        <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.nameserver.ipv4')}</label>
                        <input type="text" style={{ ...darkInputStyle, padding: '6px 10px', fontSize: '13px' }} placeholder={t('rdapEntity.nameserver.ipv4Placeholder')} value={Array.isArray(ns.ipv4Addresses) ? ns.ipv4Addresses.join(', ') : ''} onChange={(e) => updateNameserver(idx, 'ipv4Addresses', e.target.value.split(',').map(s => s.trim()).filter(Boolean))} />
                      </div>
                      <div>
                        <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.nameserver.ipv6')}</label>
                        <input type="text" style={{ ...darkInputStyle, padding: '6px 10px', fontSize: '13px' }} placeholder={t('rdapEntity.nameserver.ipv6Placeholder')} value={Array.isArray(ns.ipv6Addresses) ? ns.ipv6Addresses.join(', ') : ''} onChange={(e) => updateNameserver(idx, 'ipv6Addresses', e.target.value.split(',').map(s => s.trim()).filter(Boolean))} />
                      </div>
                    </div>
                  </div>
                ))}
                <button style={btnOutlineStyle} onClick={addNameserver}>
                  <Icon size={14}><i className="fa-solid fa-plus"></i></Icon> {t('rdapEntity.nameserver.add')}
                </button>
              </CollapsibleSection>
            )}

            {/* Links */}
            <CollapsibleSection title={t('rdapEntity.sections.links')} section="links" count={(formData.links || []).length} icon="fa-solid fa-link">
              {(formData.links || []).map((link, idx) => (
                <div key={idx} style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr auto', gap: 12, marginBottom: 12, padding: 12, backgroundColor: darkTheme.bgSecondary, borderRadius: 6, border: `1px solid ${darkTheme.border}` }}>
                  <div>
                    <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.link.url')}</label>
                    <input type="text" style={{ ...darkInputStyle, padding: '6px 10px', fontSize: '13px' }} placeholder={t('rdapEntity.link.urlPlaceholder')} value={link.href || ''} onChange={(e) => updateLink(idx, 'href', e.target.value)} />
                  </div>
                  <div>
                    <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.link.relation')}</label>
                    <select style={{ ...darkSelectStyle, padding: '6px 10px', fontSize: '13px' }} value={link.rel || 'self'} onChange={(e) => updateLink(idx, 'rel', e.target.value)}>
                      <option value="self">self</option><option value="related">related</option><option value="alternate">alternate</option>
                      <option value="copyright">copyright</option><option value="about">about</option><option value="help">help</option>
                    </select>
                  </div>
                  <div>
                    <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.link.title')}</label>
                    <input type="text" style={{ ...darkInputStyle, padding: '6px 10px', fontSize: '13px' }} placeholder={t('rdapEntity.link.titlePlaceholder')} value={link.title || ''} onChange={(e) => updateLink(idx, 'title', e.target.value)} />
                  </div>
                  <div style={{ alignSelf: 'end' }}>
                    <button style={{ ...btnDangerStyle, ...btnSmStyle }} onClick={() => removeLink(idx)}>
                      <Icon size={14}><i className="fa-solid fa-trash"></i></Icon>
                    </button>
                  </div>
                </div>
              ))}
              <button style={btnOutlineStyle} onClick={addLink}>
                <Icon size={14}><i className="fa-solid fa-plus"></i></Icon> {t('rdapEntity.link.add')}
              </button>
            </CollapsibleSection>

            {/* Remarks */}
            <CollapsibleSection title={t('rdapEntity.sections.remarksNotices')} section="remarks" count={(formData.remarks || []).length}>
              {(formData.remarks || []).map((remark, idx) => (
                <div key={idx} style={{ marginBottom: 12, padding: 12, backgroundColor: darkTheme.bgSecondary, borderRadius: 6, border: `1px solid ${darkTheme.border}` }}>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr auto', gap: 12, marginBottom: 12 }}>
                    <div>
                      <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.remark.type')}</label>
                      <select style={{ ...darkSelectStyle, padding: '6px 10px', fontSize: '13px' }} value={remark.remarkType || 'REMARK'} onChange={(e) => updateRemark(idx, 'remarkType', e.target.value)}>
                        <option value="REMARK">{t('rdapEntity.remark.typeRemark')}</option><option value="NOTICE">{t('rdapEntity.remark.typeNotice')}</option>
                      </select>
                    </div>
                    <div>
                      <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.remark.title')}</label>
                      <input type="text" style={{ ...darkInputStyle, padding: '6px 10px', fontSize: '13px' }} placeholder={t('rdapEntity.remark.titlePlaceholder')} value={remark.title || ''} onChange={(e) => updateRemark(idx, 'title', e.target.value)} />
                    </div>
                    <div style={{ alignSelf: 'end' }}>
                      <button style={{ ...btnDangerStyle, ...btnSmStyle }} onClick={() => removeRemark(idx)}>
                        <Icon size={14}><i className="fa-solid fa-trash"></i></Icon>
                      </button>
                    </div>
                  </div>
                  <div>
                    <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.remark.description')}</label>
                    <textarea style={{ ...darkInputStyle, padding: '6px 10px', fontSize: '13px', resize: 'vertical', minHeight: '60px' }} placeholder={t('rdapEntity.remark.descriptionPlaceholder')} rows={3} value={Array.isArray(remark.description) ? remark.description.join('\n') : ''} onChange={(e) => updateRemark(idx, 'description', e.target.value.split('\n').filter(Boolean))} />
                  </div>
                </div>
              ))}
              <button style={btnOutlineStyle} onClick={addRemark}>
                <Icon size={14}><i className="fa-solid fa-plus"></i></Icon> {t('rdapEntity.remark.add')}
              </button>
            </CollapsibleSection>

            {/* Secure DNS (Domains only) */}
            {activeTab === 'domains' && (
              <CollapsibleSection title={t('rdapEntity.sections.secureDns')} section="secureDns" count={(formData.secureDnsRecords || []).length}>
                {(formData.secureDnsRecords || []).map((ds, idx) => (
                  <div key={idx} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 2fr auto', gap: 12, marginBottom: 12, padding: 12, backgroundColor: darkTheme.bgSecondary, borderRadius: 6, border: `1px solid ${darkTheme.border}` }}>
                    <div>
                      <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.secureDnsRecord.keyTag')}</label>
                      <input type="number" style={{ ...darkInputStyle, padding: '6px 10px', fontSize: '13px' }} placeholder="12345" value={ds.keyTag || ''} onChange={(e) => updateSecureDns(idx, 'keyTag', e.target.value ? parseInt(e.target.value) : null)} />
                    </div>
                    <div>
                      <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.secureDnsRecord.algorithm')}</label>
                      <input type="number" style={{ ...darkInputStyle, padding: '6px 10px', fontSize: '13px' }} placeholder="8" value={ds.algorithm || ''} onChange={(e) => updateSecureDns(idx, 'algorithm', e.target.value ? parseInt(e.target.value) : null)} />
                    </div>
                    <div>
                      <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.secureDnsRecord.digestType')}</label>
                      <input type="number" style={{ ...darkInputStyle, padding: '6px 10px', fontSize: '13px' }} placeholder="2" value={ds.digestType || ''} onChange={(e) => updateSecureDns(idx, 'digestType', e.target.value ? parseInt(e.target.value) : null)} />
                    </div>
                    <div>
                      <label style={{ ...darkLabelStyle, fontSize: '12px' }}>{t('rdapEntity.secureDnsRecord.digest')}</label>
                      <input type="text" style={{ ...darkInputStyle, padding: '6px 10px', fontSize: '13px' }} placeholder="ABC123..." value={ds.digest || ''} onChange={(e) => updateSecureDns(idx, 'digest', e.target.value)} />
                    </div>
                    <div style={{ alignSelf: 'end' }}>
                      <button style={{ ...btnDangerStyle, ...btnSmStyle }} onClick={() => removeSecureDns(idx)}>
                        <Icon size={14}><i className="fa-solid fa-trash"></i></Icon>
                      </button>
                    </div>
                  </div>
                ))}
                <button style={btnOutlineStyle} onClick={addSecureDns}>
                  <Icon size={14}><i className="fa-solid fa-plus"></i></Icon> {t('rdapEntity.secureDnsRecord.add')}
                </button>
              </CollapsibleSection>
            )}

            {/* Contacts */}
            <CollapsibleSection title={t('rdapEntity.sections.contacts')} section="childEntities" count={(formData.childEntities || []).length} icon="fa-solid fa-circle-user">
              {(formData.childEntities || []).map((contact, idx) => (
                <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8, padding: 12, backgroundColor: darkTheme.bgSecondary, borderRadius: 6, border: `1px solid ${darkTheme.border}` }}>
                  <div>
                    <div style={{ fontWeight: 600, color: darkTheme.text }}>{contact.contactName || contact.organization || contact.handle || t('rdapEntity.contact.unnamed')}</div>
                    <div style={{ color: darkTheme.textMuted, fontSize: '13px', marginTop: 2 }}>{contact.roles?.length > 0 ? contact.roles.join(', ') : t('rdapEntity.contact.noRoles')}{contact.email && ` • ${contact.email}`}</div>
                  </div>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <button style={{ ...btnSecondaryStyle, ...btnSmStyle }} onClick={() => openContactModal(contact, idx)}>
                      <Icon size={14}><i className="fa-solid fa-pen"></i></Icon>
                    </button>
                    <button style={{ ...btnDangerStyle, ...btnSmStyle }} onClick={() => removeContact(idx)}>
                      <Icon size={14}><i className="fa-solid fa-trash"></i></Icon>
                    </button>
                  </div>
                </div>
              ))}
              <button style={btnOutlineStyle} onClick={() => openContactModal()}>
                <Icon size={14}><i className="fa-solid fa-plus"></i></Icon> {t('rdapEntity.contact.add')}
              </button>
            </CollapsibleSection>
          </div>
          <div style={modalFooterStyle}>
            <button type="button" style={btnSecondaryStyle} onClick={onClose}>{t('common.cancel')}</button>
            <button type="button" style={{ ...btnPrimaryStyle, opacity: submitting ? 0.7 : 1, cursor: submitting ? 'not-allowed' : 'pointer' }} onClick={handleSave} disabled={submitting}>{submitting ? t('common.saving') : t('common.save')}</button>
          </div>
        </div>
      </div>

      {/* Contact Modal */}
      {showContactModal && (
        <div style={{ ...modalOverlayStyle, zIndex: 1060 }} onClick={() => setShowContactModal(false)}>
          <div style={contactModalContentStyle} onClick={(e) => e.stopPropagation()}>
            <div style={modalHeaderStyle}>
              <h5 style={{ margin: 0, fontSize: 18, color: darkTheme.text, fontWeight: 600 }}>{editingContactIndex !== null ? t('rdapEntity.contact.editTitle') : t('rdapEntity.contact.addTitle')}</h5>
              <button type="button" style={closeButtonStyle} onClick={() => setShowContactModal(false)} aria-label={t('common.close')}>
                <i className="fa-solid fa-xmark" style={{ fontSize: 20 }}></i>
              </button>
            </div>
            <div style={modalBodyStyle}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
                <div>
                  <label style={darkLabelStyle}>{t('rdapEntity.contact.handle')} <span style={{ color: darkTheme.danger }}>*</span></label>
                  <input type="text" style={darkInputStyle} value={contactFormData.handle || ''} onChange={(e) => setContactFormData({ ...contactFormData, handle: e.target.value })} placeholder={t('rdapEntity.contact.handlePlaceholder')} />
                </div>
                <div>
                  <label style={darkLabelStyle}>{t('rdapEntity.contact.fullName')}</label>
                  <input type="text" style={darkInputStyle} value={contactFormData.contactName || ''} onChange={(e) => setContactFormData({ ...contactFormData, contactName: e.target.value })} placeholder={t('rdapEntity.contact.fullNamePlaceholder')} />
                </div>
                <div>
                  <label style={darkLabelStyle}>{t('rdapEntity.contact.organization')}</label>
                  <input type="text" style={darkInputStyle} value={contactFormData.organization || ''} onChange={(e) => setContactFormData({ ...contactFormData, organization: e.target.value })} placeholder={t('rdapEntity.contact.organizationPlaceholder')} />
                </div>
                <div>
                  <label style={darkLabelStyle}>{t('common.email')}</label>
                  <input type="email" style={darkInputStyle} value={contactFormData.email || ''} onChange={(e) => setContactFormData({ ...contactFormData, email: e.target.value })} placeholder={t('rdapEntity.contact.emailPlaceholder')} />
                </div>
                <div>
                  <label style={darkLabelStyle}>{t('rdapEntity.contact.phone')}</label>
                  <input type="text" style={darkInputStyle} value={contactFormData.phone || ''} onChange={(e) => setContactFormData({ ...contactFormData, phone: e.target.value })} placeholder={t('rdapEntity.contact.phonePlaceholder')} />
                </div>
                <div>
                  <label style={darkLabelStyle}>{t('rdapEntity.contact.fax')}</label>
                  <input type="text" style={darkInputStyle} value={contactFormData.fax || ''} onChange={(e) => setContactFormData({ ...contactFormData, fax: e.target.value })} placeholder={t('rdapEntity.contact.faxPlaceholder')} />
                </div>
                <div style={{ gridColumn: '1 / -1' }}>
                  <label style={darkLabelStyle}>{t('rdapEntity.contact.roles')}</label>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                    {ENTITY_ROLES.map(role => (
                      <button key={role} type="button" style={{ 
                        ...btnOutlineStyle, 
                        ...btnSmStyle,
                        backgroundColor: (contactFormData.roles || []).includes(role) ? darkTheme.primary : 'transparent',
                        color: (contactFormData.roles || []).includes(role) ? '#ffffff' : darkTheme.textSecondary,
                        borderColor: (contactFormData.roles || []).includes(role) ? darkTheme.primary : darkTheme.border,
                      }}
                        onClick={() => {
                          const currentRoles = contactFormData.roles || [];
                          setContactFormData({ ...contactFormData, roles: currentRoles.includes(role) ? currentRoles.filter(r => r !== role) : [...currentRoles, role] });
                        }}>{role}</button>
                    ))}
                  </div>
                </div>
                <div style={{ gridColumn: '1 / -1' }}>
                  <label style={darkLabelStyle}>{t('rdapEntity.contact.streetAddress')}</label>
                  <input type="text" style={{ ...darkInputStyle, marginBottom: 8 }} value={contactFormData.addressStreet1 || ''} onChange={(e) => setContactFormData({ ...contactFormData, addressStreet1: e.target.value })} placeholder={t('rdapEntity.contact.street1Placeholder')} />
                  <input type="text" style={darkInputStyle} value={contactFormData.addressStreet2 || ''} onChange={(e) => setContactFormData({ ...contactFormData, addressStreet2: e.target.value })} placeholder={t('rdapEntity.contact.street2Placeholder')} />
                </div>
                <div>
                  <label style={darkLabelStyle}>{t('rdapEntity.contact.city')}</label>
                  <input type="text" style={darkInputStyle} value={contactFormData.addressCity || ''} onChange={(e) => setContactFormData({ ...contactFormData, addressCity: e.target.value })} placeholder={t('rdapEntity.contact.cityPlaceholder')} />
                </div>
                <div>
                  <label style={darkLabelStyle}>{t('rdapEntity.contact.state')}</label>
                  <input type="text" style={darkInputStyle} value={contactFormData.addressState || ''} onChange={(e) => setContactFormData({ ...contactFormData, addressState: e.target.value })} placeholder={t('rdapEntity.contact.statePlaceholder')} />
                </div>
                <div>
                  <label style={darkLabelStyle}>{t('rdapEntity.contact.postalCode')}</label>
                  <input type="text" style={darkInputStyle} value={contactFormData.addressPostalCode || ''} onChange={(e) => setContactFormData({ ...contactFormData, addressPostalCode: e.target.value })} placeholder={t('rdapEntity.contact.postalCodePlaceholder')} />
                </div>
                <div>
                  <label style={darkLabelStyle}>{t('rdapEntity.contact.country')}</label>
                  <input type="text" style={darkInputStyle} value={contactFormData.addressCountry || ''} onChange={(e) => setContactFormData({ ...contactFormData, addressCountry: e.target.value })} placeholder={t('rdapEntity.contact.countryPlaceholder')} />
                </div>
              </div>
            </div>
            <div style={modalFooterStyle}>
              <button type="button" style={btnSecondaryStyle} onClick={() => setShowContactModal(false)}>{t('common.cancel')}</button>
              <button type="button" style={btnPrimaryStyle} onClick={saveContact}>{editingContactIndex !== null ? t('common.update') : t('common.add')}</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default RdapEntityEditModal;