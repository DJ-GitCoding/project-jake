/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useCallback, useMemo } from 'react';
import Modal from './Modal';
import toast from 'react-hot-toast';
import { previewCsvImport, importCsv, importJson, importJsonFile } from '../services/api';
import { useT } from '../i18n';

/**
 * Field definitions with descriptions for import mapping
 */
const FIELD_DEFINITIONS = {
  // Common
  handle: { description: 'Unique handle/ID', required: false },
  status: { description: 'Status values (comma-separated)', required: false },
  port43: { description: 'Port 43 WHOIS server', required: false },
  isTestData: { description: 'Mark as test data', required: false },
  policyExpressionId: { description: 'Policy expression ID', required: false },
  
  // Domain
  ldhName: { description: 'Domain name (LDH format)', required: true },
  unicodeName: { description: 'Unicode/IDN name', required: false },
  secureDnsDelegationSigned: { description: 'DNSSEC delegation signed', required: false },
  secureDnsZoneSigned: { description: 'DNSSEC zone signed', required: false },
  
  // Network/IP
  startAddress: { description: 'Start IP address', required: true },
  endAddress: { description: 'End IP address', required: false },
  ipVersion: { description: 'IP version (v4 or v6)', required: false },
  networkName: { description: 'Network name', required: false },
  networkType: { description: 'Network type', required: false },
  country: { description: 'Country code', required: false },
  parentHandle: { description: 'Parent network handle', required: false },
  
  // ASN
  startAutnum: { description: 'Start ASN', required: true },
  endAutnum: { description: 'End ASN', required: false },
  autnumName: { description: 'AS name', required: false },
  autnumType: { description: 'AS type', required: false },
  
  // Nameservers
  ns1: { description: 'Nameserver 1', required: false },
  ns1Ipv4: { description: 'Nameserver 1 IPv4', required: false },
  ns1Ipv6: { description: 'Nameserver 1 IPv6', required: false },
  ns2: { description: 'Nameserver 2', required: false },
  ns2Ipv4: { description: 'Nameserver 2 IPv4', required: false },
  ns2Ipv6: { description: 'Nameserver 2 IPv6', required: false },
  ns3: { description: 'Nameserver 3', required: false },
  ns3Ipv4: { description: 'Nameserver 3 IPv4', required: false },
  ns3Ipv6: { description: 'Nameserver 3 IPv6', required: false },
  ns4: { description: 'Nameserver 4', required: false },
  ns4Ipv4: { description: 'Nameserver 4 IPv4', required: false },
  ns4Ipv6: { description: 'Nameserver 4 IPv6', required: false },
  
  // Registrant
  registrantHandle: { description: 'Registrant handle', required: false },
  registrantName: { description: 'Registrant name', required: false },
  registrantOrganization: { description: 'Registrant organization', required: false },
  registrantEmail: { description: 'Registrant email', required: false },
  registrantPhone: { description: 'Registrant phone', required: false },
  registrantFax: { description: 'Registrant fax', required: false },
  registrantStreet: { description: 'Registrant street', required: false },
  registrantStreet2: { description: 'Registrant street line 2', required: false },
  registrantCity: { description: 'Registrant city', required: false },
  registrantStateProvince: { description: 'Registrant state/province', required: false },
  registrantPostalCode: { description: 'Registrant postal code', required: false },
  registrantCountry: { description: 'Registrant country', required: false },
  
  // Admin
  adminHandle: { description: 'Admin handle', required: false },
  adminName: { description: 'Admin name', required: false },
  adminOrganization: { description: 'Admin organization', required: false },
  adminEmail: { description: 'Admin email', required: false },
  adminPhone: { description: 'Admin phone', required: false },
  adminFax: { description: 'Admin fax', required: false },
  adminStreet: { description: 'Admin street', required: false },
  adminStreet2: { description: 'Admin street line 2', required: false },
  adminCity: { description: 'Admin city', required: false },
  adminStateProvince: { description: 'Admin state/province', required: false },
  adminPostalCode: { description: 'Admin postal code', required: false },
  adminCountry: { description: 'Admin country', required: false },
  
  // Tech
  techHandle: { description: 'Tech handle', required: false },
  techName: { description: 'Tech name', required: false },
  techOrganization: { description: 'Tech organization', required: false },
  techEmail: { description: 'Tech email', required: false },
  techPhone: { description: 'Tech phone', required: false },
  techFax: { description: 'Tech fax', required: false },
  techStreet: { description: 'Tech street', required: false },
  techStreet2: { description: 'Tech street line 2', required: false },
  techCity: { description: 'Tech city', required: false },
  techStateProvince: { description: 'Tech state/province', required: false },
  techPostalCode: { description: 'Tech postal code', required: false },
  techCountry: { description: 'Tech country', required: false },
  
  // Billing
  billingHandle: { description: 'Billing handle', required: false },
  billingName: { description: 'Billing name', required: false },
  billingOrganization: { description: 'Billing organization', required: false },
  billingEmail: { description: 'Billing email', required: false },
  billingPhone: { description: 'Billing phone', required: false },
  billingFax: { description: 'Billing fax', required: false },
  billingStreet: { description: 'Billing street', required: false },
  billingStreet2: { description: 'Billing street line 2', required: false },
  billingCity: { description: 'Billing city', required: false },
  billingStateProvince: { description: 'Billing state/province', required: false },
  billingPostalCode: { description: 'Billing postal code', required: false },
  billingCountry: { description: 'Billing country', required: false },
  
  // Registrar
  registrarHandle: { description: 'Registrar handle', required: false },
  registrarName: { description: 'Registrar name', required: false },
  registrarEmail: { description: 'Registrar email', required: false },
  registrarPhone: { description: 'Registrar phone', required: false },
  registrarUrl: { description: 'Registrar URL', required: false },
  registrarAbuseEmail: { description: 'Registrar abuse email', required: false },
  registrarAbusePhone: { description: 'Registrar abuse phone', required: false },
  
  // Abuse
  abuseHandle: { description: 'Abuse contact handle', required: false },
  abuseName: { description: 'Abuse contact name', required: false },
  abuseEmail: { description: 'Abuse email', required: false },
  abusePhone: { description: 'Abuse phone', required: false },
  
  // Events
  registrationDate: { description: 'Registration date', required: false },
  expirationDate: { description: 'Expiration date', required: false },
  lastChangedDate: { description: 'Last changed date', required: false },
  lastUpdateOfRdapDb: { description: 'Last RDAP DB update', required: false },
  transferDate: { description: 'Transfer date', required: false },
  
  // Remarks & Links
  remarkTitle: { description: 'Remark title', required: false },
  remarkDescription: { description: 'Remark description', required: false },
  remark2Title: { description: 'Remark 2 title', required: false },
  remark2Description: { description: 'Remark 2 description', required: false },
  selfLink: { description: 'Self link URL', required: false },
  relatedLink: { description: 'Related link URL', required: false },
  relatedLinkTitle: { description: 'Related link title', required: false },
};

/**
 * Field categories for organized display in mapping UI
 */
const FIELD_CATEGORIES = {
  common: {
    label: 'Common Fields',
    description: 'Basic entity fields',
    fields: ['handle', 'status', 'port43', 'isTestData', 'policyExpressionId']
  },
  domain: {
    label: 'Domain Fields',
    description: 'Domain-specific fields',
    fields: ['ldhName', 'unicodeName', 'secureDnsDelegationSigned', 'secureDnsZoneSigned']
  },
  network: {
    label: 'Network/IP Fields',
    description: 'IP network-specific fields',
    fields: ['startAddress', 'endAddress', 'ipVersion', 'networkName', 'networkType', 'country', 'parentHandle']
  },
  autnum: {
    label: 'ASN Fields',
    description: 'Autonomous System Number fields',
    fields: ['startAutnum', 'endAutnum', 'autnumName', 'autnumType', 'country']
  },
  nameservers: {
    label: 'Nameservers',
    description: 'DNS nameserver information',
    fields: ['ns1', 'ns1Ipv4', 'ns1Ipv6', 'ns2', 'ns2Ipv4', 'ns2Ipv6', 'ns3', 'ns3Ipv4', 'ns3Ipv6', 'ns4', 'ns4Ipv4', 'ns4Ipv6']
  },
  registrant: {
    label: 'Registrant Contact',
    description: 'Domain registrant information',
    sensitive: true,
    fields: ['registrantHandle', 'registrantName', 'registrantOrganization', 'registrantEmail', 'registrantPhone', 'registrantFax', 'registrantStreet', 'registrantStreet2', 'registrantCity', 'registrantStateProvince', 'registrantPostalCode', 'registrantCountry']
  },
  admin: {
    label: 'Admin Contact',
    description: 'Administrative contact information',
    sensitive: true,
    fields: ['adminHandle', 'adminName', 'adminOrganization', 'adminEmail', 'adminPhone', 'adminFax', 'adminStreet', 'adminStreet2', 'adminCity', 'adminStateProvince', 'adminPostalCode', 'adminCountry']
  },
  tech: {
    label: 'Tech Contact',
    description: 'Technical contact information',
    sensitive: true,
    fields: ['techHandle', 'techName', 'techOrganization', 'techEmail', 'techPhone', 'techFax', 'techStreet', 'techStreet2', 'techCity', 'techStateProvince', 'techPostalCode', 'techCountry']
  },
  billing: {
    label: 'Billing Contact',
    description: 'Billing contact information',
    sensitive: true,
    fields: ['billingHandle', 'billingName', 'billingOrganization', 'billingEmail', 'billingPhone', 'billingFax', 'billingStreet', 'billingStreet2', 'billingCity', 'billingStateProvince', 'billingPostalCode', 'billingCountry']
  },
  registrar: {
    label: 'Registrar',
    description: 'Sponsoring registrar information',
    fields: ['registrarHandle', 'registrarName', 'registrarEmail', 'registrarPhone', 'registrarUrl', 'registrarAbuseEmail', 'registrarAbusePhone']
  },
  abuse: {
    label: 'Abuse Contact',
    description: 'Abuse reporting contact',
    fields: ['abuseHandle', 'abuseName', 'abuseEmail', 'abusePhone']
  },
  events: {
    label: 'Events',
    description: 'Domain lifecycle events',
    fields: ['registrationDate', 'expirationDate', 'lastChangedDate', 'lastUpdateOfRdapDb', 'transferDate']
  },
  remarks: {
    label: 'Remarks & Links',
    description: 'Additional information',
    fields: ['remarkTitle', 'remarkDescription', 'remark2Title', 'remark2Description', 'selfLink', 'relatedLink', 'relatedLinkTitle']
  }
};

/**
 * Get categories relevant to a specific entity type
 */
const getCategoriesForType = (type) => {
  const common = ['common'];
  const contacts = ['registrant', 'admin', 'tech', 'billing', 'registrar', 'abuse'];
  const other = ['events', 'remarks'];
  
  switch (type?.toLowerCase()) {
    case 'domains':
    case 'domain':
      return [...common, 'domain', 'nameservers', ...contacts, ...other];
    case 'ips':
    case 'ip':
      return [...common, 'network', ...contacts, ...other];
    case 'asns':
    case 'asn':
      return [...common, 'autnum', ...contacts, ...other];
    default:
      return [...common, ...contacts, ...other];
  }
};

/**
 * Build fields array from category definition using local FIELD_DEFINITIONS
 */
const buildFieldsForCategory = (category, t) => {
  return category.fields.map(fieldName => ({
    name: fieldName,
    description: t ? t(`rdapImport.fields.${fieldName}`) : (FIELD_DEFINITIONS[fieldName]?.description || fieldName),
    required: FIELD_DEFINITIONS[fieldName]?.required || false
  }));
};

/**
 * Collapsible category section for field mappings
 */
const MappingCategory = ({ 
  categoryKey, 
  category, 
  headers, 
  mappings, 
  onMappingChange,
  expanded,
  onToggleExpand
}) => {
  const { t } = useT();
  const categoryFields = buildFieldsForCategory(category, t);
  if (categoryFields.length === 0) return null;

  const mappedCount = categoryFields.filter(f => mappings[f.name] !== undefined && mappings[f.name] !== '').length;

  return (
    <div className="mapping-category">
      <div className="mapping-category-header" onClick={onToggleExpand}>
        <div className="mapping-category-title">
          <i className={`fas fa-chevron-${expanded ? 'down' : 'right'}`} style={{ width: '16px' }}></i>
          <div>
            <span className="mapping-category-label">{t(`rdapImport.categories.${categoryKey}`)}</span>
            {category.sensitive && (
              <span className="badge bg-warning-subtle text-warning" style={{ fontSize: '10px', marginLeft: '8px' }}>{t('rdapImport.mapping.sensitiveBadge')}</span>
            )}
          </div>
        </div>
        <span className={`badge ${mappedCount > 0 ? 'bg-success-subtle text-success' : 'bg-secondary-subtle text-secondary'}`}>
          {t('rdapImport.mapping.mappedCount', { mapped: mappedCount, total: categoryFields.length })}
        </span>
      </div>

      {expanded && (
        <div className="mapping-category-body">
          <div className="mapping-fields-grid">
            {categoryFields.map(field => (
              <div key={field.name} className="mapping-field-row">
                <div className="mapping-field-info">
                  <span className="mapping-field-name">
                    {field.name}
                    {field.required && <span className="required-marker"> *</span>}
                  </span>
                  <span className="mapping-field-description">{field.description}</span>
                </div>
                <select
                  className="form-select mapping-field-select"
                  value={mappings[field.name] ?? ''}
                  onChange={(e) => onMappingChange(field.name, e.target.value === '' ? undefined : parseInt(e.target.value))}
                >
                  <option value="">{t('rdapImport.mapping.skipOption')}</option>
                  {headers?.map((header, index) => (
                    <option key={index} value={index}>{header}</option>
                  ))}
                </select>
              </div>
            ))}
          </div>
        </div>
      )}

      <style>{`
        .mapping-category {
          border: 1px solid var(--border-primary, #e2e8f0);
          border-radius: 10px;
          margin-bottom: 12px;
          overflow: hidden;
          background: var(--bg-primary, #fff);
        }
        
        .mapping-category-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 14px 18px;
          background: var(--bg-secondary, #f8fafc);
          cursor: pointer;
          transition: background 0.15s ease;
        }
        
        .mapping-category-header:hover {
          background: var(--bg-tertiary, #f1f5f9);
        }
        
        .mapping-category-title {
          display: flex;
          align-items: center;
          gap: 10px;
        }
        
        .mapping-category-label {
          font-weight: 600;
          font-size: 15px;
        }
        
        .mapping-category-body {
          padding: 16px 18px;
          background: var(--bg-primary, #fff);
        }
        
        .mapping-fields-grid {
          display: flex;
          flex-direction: column;
          gap: 12px;
        }
        
        .mapping-field-row {
          display: grid;
          grid-template-columns: 1fr 280px;
          gap: 20px;
          align-items: center;
          padding: 10px 14px;
          background: var(--bg-secondary, #f8fafc);
          border-radius: 8px;
        }
        
        .mapping-field-info {
          display: flex;
          flex-direction: column;
          gap: 2px;
        }
        
        .mapping-field-name {
          font-weight: 600;
          font-size: 14px;
          color: var(--text-primary, #1e293b);
        }
        
        .required-marker {
          color: var(--accent-error, #ef4444);
        }
        
        .mapping-field-description {
          font-size: 13px;
          color: var(--text-muted, #64748b);
        }
        
        .mapping-field-select {
          padding: 10px 14px;
          font-size: 14px;
          border-radius: 6px;
        }
      `}</style>
    </div>
  );
};

/**
 * Auto-mapping helper
 */
const autoMapFields = (headers, type) => {
  const mappings = {};
  const headerLower = headers.map(h => h.toLowerCase().replace(/[_\-\s]/g, ''));
  
  const relevantCategories = getCategoriesForType(type);
  const allFields = [];
  relevantCategories.forEach(catKey => {
    const cat = FIELD_CATEGORIES[catKey];
    if (cat) {
      allFields.push(...cat.fields);
    }
  });
  
  allFields.forEach(fieldName => {
    const fieldLower = fieldName.toLowerCase().replace(/[_\-\s]/g, '');
    
    let matchIndex = headerLower.findIndex(h => h === fieldLower);
    
    if (matchIndex === -1) {
      matchIndex = headerLower.findIndex(h => h === fieldLower || h.includes(fieldLower));
    }
    
    if (matchIndex === -1) {
      matchIndex = headerLower.findIndex(h => fieldLower.includes(h) && h.length > 3);
    }
    
    if (matchIndex === -1) {
      const aliases = getFieldAliases(fieldName);
      for (const alias of aliases) {
        matchIndex = headerLower.findIndex(h => h === alias || h.includes(alias));
        if (matchIndex !== -1) break;
      }
    }
    
    if (matchIndex !== -1 && !Object.values(mappings).includes(matchIndex)) {
      mappings[fieldName] = matchIndex;
    }
  });
  
  return mappings;
};

const getFieldAliases = (fieldName) => {
  const aliases = {
    'ldhName': ['domain', 'domainname', 'name', 'fqdn', 'hostname'],
    'unicodeName': ['unicode', 'idn', 'idnname'],
    'secureDnsDelegationSigned': ['dnssec', 'dnssecenabled', 'signed', 'delegationsigned'],
    'startAddress': ['ip', 'ipaddress', 'start', 'startip', 'ipstart', 'fromip', 'beginip'],
    'endAddress': ['endip', 'end', 'ipend', 'toip', 'lastip'],
    'ipVersion': ['version', 'ipv', 'protocol'],
    'networkName': ['netname', 'name', 'network'],
    'networkType': ['nettype', 'type', 'alloctype'],
    'startAutnum': ['asn', 'asnumber', 'as', 'asnum', 'autonomoussystem'],
    'endAutnum': ['endasn', 'endasnum', 'toasn'],
    'autnumName': ['asname', 'autonomousname'],
    'registrantHandle': ['reghandle', 'registrantid'],
    'registrantName': ['registrant', 'owner', 'ownername', 'regname', 'registrantcontact'],
    'registrantEmail': ['owneremail', 'regemail', 'registrantemail'],
    'registrantOrganization': ['regorg', 'ownerorg', 'registrantorg', 'organization', 'org'],
    'registrantPhone': ['regphone', 'ownerphone', 'registranttel', 'phone', 'tel'],
    'registrantFax': ['regfax', 'ownerfax', 'fax'],
    'registrantStreet': ['regstreet', 'regaddress', 'address', 'street', 'street1', 'addressstreet1'],
    'registrantStreet2': ['regstreet2', 'address2', 'street2', 'addressstreet2'],
    'registrantCity': ['regcity', 'city', 'addresscity'],
    'registrantStateProvince': ['regstate', 'state', 'province', 'stateprovince', 'addressstate'],
    'registrantPostalCode': ['regpostal', 'postalcode', 'zipcode', 'zip', 'postcode'],
    'registrantCountry': ['regcountry', 'country', 'addresscountry', 'countrycode'],
    'adminHandle': ['adminid', 'administrativehandle'],
    'adminName': ['admin', 'administrator', 'administrativename', 'admincontact', 'administrative'],
    'adminEmail': ['adminemail', 'administratoremail', 'administrativeemail'],
    'adminOrganization': ['adminorg', 'administrativeorg'],
    'adminPhone': ['adminphone', 'admintel', 'administrativephone'],
    'techHandle': ['techid', 'technicalhandle'],
    'techName': ['tech', 'technical', 'technicalname', 'techcontact', 'technicalcontact'],
    'techEmail': ['techemail', 'technicalemail'],
    'techOrganization': ['techorg', 'technicalorg'],
    'techPhone': ['techphone', 'techtel', 'technicalphone'],
    'billingHandle': ['billhandle', 'billingid'],
    'billingName': ['billing', 'billingcontact'],
    'billingEmail': ['billingemail', 'billemail'],
    'billingOrganization': ['billingorg', 'billorg'],
    'billingPhone': ['billingphone', 'billphone'],
    'registrationDate': ['created', 'createddate', 'createdon', 'registered', 'creationdate', 'createdat', 'registration'],
    'expirationDate': ['expires', 'expiry', 'expirydate', 'expireson', 'expiredate', 'expirationdate', 'expiration'],
    'lastChangedDate': ['updated', 'modified', 'lastupdated', 'lastmodified', 'changed', 'updatedat', 'modifiedat'],
    'lastUpdateOfRdapDb': ['rdapupdate', 'dbupdated', 'lastupdateofrdap'],
    'transferDate': ['transferred', 'transfer', 'transferredat'],
    'ns1': ['nameserver1', 'primaryns', 'dns1', 'nameserver', 'ns', 'primarydns'],
    'ns2': ['nameserver2', 'secondaryns', 'dns2', 'secondarydns'],
    'ns3': ['nameserver3', 'dns3', 'tertiarydns'],
    'ns4': ['nameserver4', 'dns4'],
    'ns1Ipv4': ['ns1ip', 'nameserver1ip', 'ns1ipv4address'],
    'ns2Ipv4': ['ns2ip', 'nameserver2ip', 'ns2ipv4address'],
    'remarkTitle': ['remarktitle', 'notetitle', 'commenttitle'],
    'remarkDescription': ['remark', 'remarkdesc', 'note', 'comment', 'description'],
    'selfLink': ['self', 'selfurl', 'rdapurl'],
    'relatedLink': ['related', 'relatedurl', 'website', 'url', 'homepage'],
  };
  return aliases[fieldName] || [];
};

/**
 * Import Result Display Component
 */
const ImportResult = ({ result }) => {
  const { t } = useT();
  const isSuccess = result.failedCount === 0;

  return (
    <div className="import-result">
      <div className={`import-result-icon ${isSuccess ? 'success' : 'warning'}`}>
        <i className={`fas fa-${isSuccess ? 'check' : 'times'}`} style={{ fontSize: '36px', color: 'white' }}></i>
      </div>

      <h3 className="import-result-title">
        {isSuccess ? t('rdapImport.result.completeTitle') : t('rdapImport.result.completedWithErrorsTitle')}
      </h3>

      <div className="import-result-stats">
        <div className="import-stat">
          <div className="import-stat-value">{result.totalRows || (result.successCount + result.failedCount)}</div>
          <div className="import-stat-label">{t('rdapImport.result.total')}</div>
        </div>
        <div className="import-stat success">
          <div className="import-stat-value">{result.successCount}</div>
          <div className="import-stat-label">{t('rdapImport.result.imported')}</div>
        </div>
        <div className="import-stat error">
          <div className="import-stat-value">{result.failedCount}</div>
          <div className="import-stat-label">{t('rdapImport.result.failed')}</div>
        </div>
      </div>

      {result.errors && result.errors.length > 0 && (
        <details className="import-errors">
          <summary>{t('rdapImport.result.viewErrors', { count: result.errors.length })}</summary>
          <div className="import-errors-list">
            {result.errors.map((error, i) => (
              <div key={i} className="import-error-item">
                <span className="import-error-row">{t('rdapImport.result.row', { number: error.rowNumber })}</span>
                <span className="import-error-message">{error.error}</span>
              </div>
            ))}
          </div>
        </details>
      )}

      <style>{`
        .import-result {
          text-align: center;
          padding: 40px 20px;
        }
        
        .import-result-icon {
          width: 80px;
          height: 80px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          margin: 0 auto 24px;
        }
        
        .import-result-icon.success {
          background: linear-gradient(135deg, #10b981 0%, #059669 100%);
        }
        
        .import-result-icon.warning {
          background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
        }
        
        .import-result-title {
          font-size: 22px;
          font-weight: 600;
          margin-bottom: 32px;
          color: var(--text-primary, #1e293b);
        }
        
        .import-result-stats {
          display: flex;
          justify-content: center;
          gap: 48px;
          margin-bottom: 32px;
        }
        
        .import-stat {
          text-align: center;
        }
        
        .import-stat-value {
          font-size: 42px;
          font-weight: 700;
          line-height: 1;
          margin-bottom: 8px;
          color: var(--text-primary, #1e293b);
        }
        
        .import-stat.success .import-stat-value {
          color: var(--accent-success, #10b981);
        }
        
        .import-stat.error .import-stat-value {
          color: var(--accent-error, #ef4444);
        }
        
        .import-stat-label {
          font-size: 14px;
          color: var(--text-muted, #64748b);
          text-transform: uppercase;
          letter-spacing: 0.05em;
        }
        
        .import-errors {
          text-align: left;
          margin-top: 24px;
          background: var(--bg-secondary, #f8fafc);
          border-radius: 10px;
          padding: 16px;
        }
        
        .import-errors summary {
          cursor: pointer;
          font-weight: 600;
          font-size: 15px;
          padding: 8px 0;
        }
        
        .import-errors-list {
          max-height: 200px;
          overflow-y: auto;
          margin-top: 12px;
        }
        
        .import-error-item {
          padding: 10px 12px;
          background: var(--bg-primary, #fff);
          border-radius: 6px;
          margin-bottom: 8px;
          font-size: 14px;
        }
        
        .import-error-row {
          color: var(--accent-error, #ef4444);
          font-weight: 600;
          margin-right: 8px;
        }
        
        .import-error-message {
          color: var(--text-secondary, #475569);
        }
      `}</style>
    </div>
  );
};

/**
 * CSV Import Modal Component
 */
export const CsvImportModal = ({ show, onClose, type, onSuccess }) => {
  const [file, setFile] = useState(null);
  const [delimiter, setDelimiter] = useState(',');
  const [hasHeader, setHasHeader] = useState(true);
  const [step, setStep] = useState(1);
  const [preview, setPreview] = useState(null);
  const [columnMappings, setColumnMappings] = useState({});
  const [result, setResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [expandedCategories, setExpandedCategories] = useState(['common', 'domain', 'network', 'autnum']);
  const { t } = useT();

  const relevantCategories = useMemo(() => getCategoriesForType(type), [type]);

  const resetState = useCallback(() => {
    setFile(null);
    setDelimiter(',');
    setHasHeader(true);
    setStep(1);
    setPreview(null);
    setColumnMappings({});
    setResult(null);
    setSubmitting(false);
    setExpandedCategories(['common', 'domain', 'network', 'autnum']);
  }, []);

  const handleClose = useCallback(() => {
    resetState();
    onClose();
  }, [onClose, resetState]);

  const handleFileSelect = (e) => {
    const selectedFile = e.target.files[0];
    if (selectedFile) {
      setFile(selectedFile);
      const reader = new FileReader();
      reader.onload = (event) => {
        const firstLine = event.target.result.split('\n')[0];
        let bestDelimiter = ',';
        let maxCount = 0;
        [',', ';', '\t', '|'].forEach((d) => {
          const count = (firstLine.match(new RegExp(d === '|' ? '\\|' : d, 'g')) || []).length;
          if (count > maxCount) {
            maxCount = count;
            bestDelimiter = d;
          }
        });
        setDelimiter(bestDelimiter);
      };
      reader.readAsText(selectedFile.slice(0, 1024));
    }
  };

  const handlePreview = async () => {
    if (!file) {
      toast.error(t('rdapImport.csv.selectFileRequired'));
      return;
    }
    setSubmitting(true);
    try {
      const response = await previewCsvImport(file, type, delimiter, hasHeader);
      if (response.data.success) {
        setPreview(response.data.preview);

        const headers = response.data.preview.detectedHeaders || [];
        const autoMappings = autoMapFields(headers, type);
        const serverMappings = response.data.preview.suggestedMappings || {};
        setColumnMappings({ ...autoMappings, ...serverMappings });

        setStep(2);

        const mappedCount = Object.keys({ ...autoMappings, ...serverMappings }).length;
        if (mappedCount > 0) {
          toast.success(t('rdapImport.csv.autoMappedFromHeaders', { count: mappedCount }));
        }
      } else {
        toast.error(response.data.error || t('rdapImport.csv.previewFailed'));
      }
    } catch (error) {
      console.error('CSV preview error:', error);
      toast.error(error.response?.data?.error || t('rdapImport.csv.previewError'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleImport = async () => {
    setSubmitting(true);
    try {
      const response = await importCsv(file, type, delimiter, hasHeader, columnMappings);
      if (response.data.success) {
        setResult(response.data.result);
        setStep(3);
        toast.success(t('rdapImport.importedRecords', { count: response.data.result.successCount }));
        if (onSuccess) onSuccess();
      } else {
        toast.error(response.data.error || t('rdapImport.importFailed'));
      }
    } catch (error) {
      console.error('CSV import error:', error);
      toast.error(error.response?.data?.error || t('rdapImport.csv.importError'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleMappingChange = (fieldName, columnIndex) => {
    setColumnMappings(prev => {
      const updated = { ...prev };
      if (columnIndex === undefined) {
        delete updated[fieldName];
      } else {
        updated[fieldName] = columnIndex;
      }
      return updated;
    });
  };

  const toggleCategory = (categoryKey) => {
    setExpandedCategories(prev => 
      prev.includes(categoryKey) 
        ? prev.filter(k => k !== categoryKey)
        : [...prev, categoryKey]
    );
  };

  const expandAllCategories = () => setExpandedCategories(relevantCategories);
  const collapseAllCategories = () => setExpandedCategories([]);
  
  const clearAllMappings = () => {
    setColumnMappings({});
    toast.success(t('rdapImport.csv.mappingsCleared'));
  };

  const autoMapAllFields = () => {
    if (preview?.detectedHeaders) {
      const autoMappings = autoMapFields(preview.detectedHeaders, type);
      setColumnMappings(autoMappings);
      toast.success(t('rdapImport.csv.autoMappedFields', { count: Object.keys(autoMappings).length }));
    }
  };

  const mappedFieldCount = useMemo(() => {
    return Object.keys(columnMappings).filter(k => columnMappings[k] !== undefined && columnMappings[k] !== '').length;
  }, [columnMappings]);

  const getFooter = () => {
    if (step === 1) {
      return (
        <>
          <button type="button" className="btn btn-secondary" onClick={handleClose}>{t('common.cancel')}</button>
          <button type="button" className="btn btn-primary" onClick={handlePreview} disabled={submitting || !file}>
            {submitting ? t('common.loading') : t('rdapImport.csv.previewAndMap')}
          </button>
        </>
      );
    }
    if (step === 2) {
      return (
        <>
          <button type="button" className="btn btn-secondary" onClick={() => setStep(1)}>{t('common.back')}</button>
          <button type="button" className="btn btn-primary" onClick={handleImport} disabled={submitting}>
            {submitting ? t('rdapImport.importing') : t('rdapImport.csv.importFieldsCount', { count: mappedFieldCount })}
          </button>
        </>
      );
    }
    return <button type="button" className="btn btn-primary" onClick={handleClose}>{t('rdapImport.done')}</button>;
  };

  return (
    <Modal
      isOpen={show}
      onClose={handleClose}
      title={t('rdapImport.csv.modalTitle', { type })}
      footer={getFooter()}
      size="xlarge"
    >
      <div className="csv-import-modal">
        {step === 1 && (
          <div className="csv-step-one">
            <div className="csv-file-upload">
              <label className="form-label">{t('rdapImport.csv.selectFileLabel')}</label>
              <div className="file-drop-zone">
                <input type="file" accept=".csv,.txt" onChange={handleFileSelect} className="file-input" />
                <div className="file-drop-content">
                  <i className="fas fa-file-csv" style={{ fontSize: '48px', color: '#64748b', marginBottom: '16px' }}></i>
                  <p>{t('rdapImport.csv.dropZoneText')}</p>
                  {file && (
                    <div className="file-selected">
                      <i className="fas fa-check-circle" style={{ marginRight: '8px' }}></i>
                      <strong>{file.name}</strong> ({t('rdapImport.csv.fileSizeKb', { size: (file.size / 1024).toFixed(1) })})
                    </div>
                  )}
                </div>
              </div>
            </div>

            <div className="csv-options">
              <div className="mb-3">
                <label className="form-label">{t('rdapImport.csv.delimiterLabel')}</label>
                <select className="form-select" value={delimiter} onChange={(e) => setDelimiter(e.target.value)}>
                  <option value=",">{t('rdapImport.csv.delimiterComma')}</option>
                  <option value=";">{t('rdapImport.csv.delimiterSemicolon')}</option>
                  <option value="	">{t('rdapImport.csv.delimiterTab')}</option>
                  <option value="|">{t('rdapImport.csv.delimiterPipe')}</option>
                </select>
              </div>
              <div className="mb-3">
                <label className="form-label">{t('rdapImport.csv.firstRowHeaderLabel')}</label>
                <select className="form-select" value={hasHeader ? 'true' : 'false'} onChange={(e) => setHasHeader(e.target.value === 'true')}>
                  <option value="true">{t('common.yes')}</option>
                  <option value="false">{t('common.no')}</option>
                </select>
              </div>
            </div>

            <div className="csv-info-box">
              <h4><i className="fas fa-info-circle" style={{ marginRight: '8px' }}></i>{t('rdapImport.csv.supportedCategories')}</h4>
              <div className="csv-categories-preview">
                {relevantCategories.map(catKey => (
                  <span key={catKey} className="badge bg-info-subtle text-info">
                    {FIELD_CATEGORIES[catKey] ? t(`rdapImport.categories.${catKey}`) : ''}
                  </span>
                ))}
              </div>
              <p className="csv-required-note">
                <strong>{t('rdapImport.csv.requiredFieldsLabel')}</strong>{' '}
                {type === 'domains' ? 'ldhName' : type === 'ips' ? 'handle, startAddress' : 'handle, startAutnum'}
              </p>
            </div>
          </div>
        )}

        {step === 2 && preview && (
          <div className="csv-step-two">
            <div className="csv-mapping-toolbar">
              <div className="csv-mapping-stats">
                <span className="stat-item"><i className="fas fa-table" style={{ marginRight: '6px' }}></i><strong>{preview.totalRows}</strong> {t('rdapImport.csv.rowsLabel')}</span>
                <span className="stat-item"><i className="fas fa-columns" style={{ marginRight: '6px' }}></i><strong>{preview.detectedHeaders?.length || 0}</strong> {t('rdapImport.csv.columnsLabel')}</span>
                <span className={`badge badge-lg ${mappedFieldCount > 0 ? 'bg-success-subtle text-success' : 'bg-warning-subtle text-warning'}`}>
                  {t('rdapImport.csv.fieldsMappedLabel', { count: mappedFieldCount })}
                </span>
              </div>
              <div className="csv-mapping-actions">
                <button className="btn btn-outline-secondary" onClick={autoMapAllFields}><i className="fas fa-magic" style={{ marginRight: '6px' }}></i>{t('rdapImport.csv.autoMap')}</button>
                <button className="btn btn-outline-secondary" onClick={expandAllCategories}><i className="fas fa-expand-alt" style={{ marginRight: '6px' }}></i>{t('rdapImport.csv.expandAll')}</button>
                <button className="btn btn-outline-secondary" onClick={collapseAllCategories}><i className="fas fa-compress-alt" style={{ marginRight: '6px' }}></i>{t('rdapImport.csv.collapseAll')}</button>
                <button className="btn btn-outline-secondary" onClick={clearAllMappings}><i className="fas fa-eraser" style={{ marginRight: '6px' }}></i>{t('rdapImport.csv.clearAll')}</button>
              </div>
            </div>

            {preview.sampleRows && preview.sampleRows.length > 0 && (
              <details className="csv-preview-table" open>
                <summary>
                  <i className="fas fa-eye" style={{ marginRight: '8px' }}></i>
                  {t('rdapImport.csv.previewDataSummary', { total: preview.totalRows, shown: preview.sampleRows.length })}
                </summary>
                <div className="table-wrapper">
                  <table>
                    <thead>
                      <tr>
                        <th style={{ width: '50px', textAlign: 'center' }}>#</th>
                        {preview.detectedHeaders?.map((h, i) => (
                          <th key={i}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {preview.sampleRows.map((row, rowIdx) => (
                        <tr key={rowIdx}>
                          <td style={{ textAlign: 'center', color: '#94a3b8', fontWeight: 500 }}>{rowIdx + 1}</td>
                          {row.map((cell, cellIdx) => (
                            <td key={cellIdx}>{cell || <span className="text-muted">—</span>}</td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </details>
            )}

            <div className="csv-mapping-categories">
              {relevantCategories.map(categoryKey => {
                const category = FIELD_CATEGORIES[categoryKey];
                if (!category) return null;
                
                return (
                  <MappingCategory
                    key={categoryKey}
                    categoryKey={categoryKey}
                    category={category}
                    headers={preview.detectedHeaders || []}
                    mappings={columnMappings}
                    onMappingChange={handleMappingChange}
                    expanded={expandedCategories.includes(categoryKey)}
                    onToggleExpand={() => toggleCategory(categoryKey)}
                  />
                );
              })}
            </div>
          </div>
        )}

        {step === 3 && result && <ImportResult result={result} />}
      </div>

      <style>{`
        .csv-import-modal {
          min-height: 400px;
        }
        
        .csv-step-one {
          display: flex;
          flex-direction: column;
          gap: 28px;
          padding: 8px 0;
        }
        
        .csv-file-upload .form-label {
          font-weight: 600;
          font-size: 15px;
          margin-bottom: 12px;
          display: block;
        }
        
        .file-drop-zone {
          position: relative;
          border: 2px dashed var(--border-primary, #e2e8f0);
          border-radius: 12px;
          padding: 48px 24px;
          text-align: center;
          transition: all 0.2s ease;
          background: var(--bg-secondary, #f8fafc);
        }
        
        .file-drop-zone:hover {
          border-color: var(--accent-primary, #6366f1);
          background: var(--bg-tertiary, #f1f5f9);
        }
        
        .file-input {
          position: absolute;
          inset: 0;
          opacity: 0;
          cursor: pointer;
        }
        
        .file-drop-content p {
          color: var(--text-muted, #64748b);
          font-size: 15px;
          margin: 0;
        }
        
        .file-selected {
          margin-top: 16px;
          padding: 12px 16px;
          background: var(--bg-primary, #fff);
          border-radius: 8px;
          display: inline-block;
          color: var(--accent-success, #10b981);
        }
        
        .csv-options {
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 24px;
        }
        
        .csv-options .form-label {
          font-weight: 600;
          font-size: 14px;
          margin-bottom: 8px;
          display: block;
        }
        
        .csv-options .form-select {
          padding: 12px 16px;
          font-size: 15px;
          border-radius: 8px;
        }
        
        .csv-info-box {
          padding: 20px 24px;
          background: var(--bg-secondary, #f8fafc);
          border-radius: 12px;
          border: 1px solid var(--border-primary, #e2e8f0);
        }
        
        .csv-info-box h4 {
          font-size: 15px;
          font-weight: 600;
          margin-bottom: 12px;
        }
        
        .csv-categories-preview {
          display: flex;
          flex-wrap: wrap;
          gap: 8px;
          margin-bottom: 16px;
        }
        
        .csv-required-note {
          font-size: 14px;
          color: var(--text-muted, #64748b);
          margin: 0;
        }
        
        .csv-step-two {
          display: flex;
          flex-direction: column;
          gap: 20px;
        }
        
        .csv-mapping-toolbar {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 16px 20px;
          background: var(--bg-secondary, #f8fafc);
          border-radius: 12px;
          flex-wrap: wrap;
          gap: 16px;
        }
        
        .csv-mapping-stats {
          display: flex;
          align-items: center;
          gap: 20px;
        }
        
        .stat-item {
          font-size: 14px;
          color: var(--text-secondary, #475569);
        }
        
        .badge-lg {
          padding: 8px 14px;
          font-size: 13px;
        }
        
        .csv-mapping-actions {
          display: flex;
          gap: 8px;
        }
        
        .csv-preview-table {
          background: var(--bg-secondary, #f8fafc);
          border-radius: 10px;
          padding: 16px;
        }
        
        .csv-preview-table summary {
          cursor: pointer;
          font-weight: 600;
          font-size: 14px;
          padding: 8px 0;
        }
        
        .csv-preview-table .table-wrapper {
          overflow: auto;
          margin-top: 12px;
          border: 1px solid var(--border-primary, #e2e8f0);
          border-radius: 8px;
          max-height: 400px;
        }
        
        .csv-preview-table table {
          width: 100%;
          font-size: 13px;
          border-collapse: collapse;
        }
        
        .csv-preview-table th {
          background: var(--bg-tertiary, #f1f5f9);
          padding: 10px 14px;
          text-align: left;
          white-space: nowrap;
          font-weight: 600;
          border-bottom: 1px solid var(--border-primary, #e2e8f0);
          position: sticky;
          top: 0;
          z-index: 1;
        }
        
        .csv-preview-table td {
          padding: 8px 14px;
          border-bottom: 1px solid var(--border-secondary, #f1f5f9);
          max-width: 200px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        
        .csv-preview-table tr:hover td {
          background: var(--bg-tertiary, #f1f5f9);
        }
        
        .csv-mapping-categories {
          max-height: 500px;
          overflow-y: auto;
          padding-right: 8px;
        }
      `}</style>
    </Modal>
  );
};

/**
 * JSON Import Modal Component
 */
export const JsonImportModal = ({ show, onClose, type, onSuccess }) => {
  const [jsonInput, setJsonInput] = useState('');
  const [file, setFile] = useState(null);
  const [result, setResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const { t } = useT();

  const resetState = useCallback(() => {
    setJsonInput('');
    setFile(null);
    setResult(null);
    setSubmitting(false);
  }, []);

  const handleClose = useCallback(() => {
    resetState();
    onClose();
  }, [onClose, resetState]);

  const handleFileSelect = (e) => {
    const selectedFile = e.target.files[0];
    if (selectedFile) {
      setFile(selectedFile);
      const reader = new FileReader();
      reader.onload = (event) => setJsonInput(event.target.result);
      reader.readAsText(selectedFile);
    }
  };

  const handleImport = async () => {
    if (!jsonInput.trim() && !file) {
      toast.error(t('rdapImport.json.provideDataRequired'));
      return;
    }
    setSubmitting(true);
    try {
      let response;
      if (file) {
        response = await importJsonFile(file, type);
      } else {
        const data = JSON.parse(jsonInput);
        response = await importJson(type, Array.isArray(data) ? data : [data]);
      }
      if (response.data.success) {
        setResult(response.data.result);
        toast.success(t('rdapImport.importedRecords', { count: response.data.result.successCount }));
        if (onSuccess) onSuccess();
      } else {
        toast.error(response.data.error || t('rdapImport.importFailed'));
      }
    } catch (error) {
      if (error instanceof SyntaxError) {
        toast.error(t('rdapImport.json.invalidFormat'));
      } else {
        console.error('JSON import error:', error);
        toast.error(error.response?.data?.error || t('rdapImport.json.importError'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const formatJson = () => {
    try {
      const parsed = JSON.parse(jsonInput);
      setJsonInput(JSON.stringify(parsed, null, 2));
    } catch (e) {
      toast.error(t('rdapImport.json.invalidCannotFormat'));
    }
  };

  const loadTemplate = () => {
    const templates = {
      domains: [
        {
          ldhName: "example.com",
          handle: "DOM-EXAMPLE-COM",
          status: ["active"],
          secureDnsDelegationSigned: true,
          childEntities: [
            {
              handle: "CONT-REG-1",
              roles: ["registrant"],
              contactName: "John Smith",
              organization: "Example Corp",
              email: "registrant@example.com",
              phone: "+1.5551234567"
            }
          ],
          nameservers: [
            { ldhName: "ns1.example.com", ipv4Addresses: ["192.0.2.1"] }
          ],
          events: [
            { eventAction: "registration", eventDate: "2020-01-15T00:00:00Z" },
            { eventAction: "expiration", eventDate: "2025-01-15T00:00:00Z" }
          ],
          remarks: [
            { title: "Terms of Use", description: ["For informational purposes only."] }
          ],
          isTestData: true
        }
      ],
      ips: [
        {
          handle: "NET-192-0-2-0",
          startAddress: "192.0.2.0",
          endAddress: "192.0.2.255",
          ipVersion: "v4",
          networkName: "EXAMPLE-NET",
          country: "US",
          childEntities: [
            {
              handle: "CONT-ADMIN-1",
              roles: ["administrative"],
              contactName: "Network Admin",
              email: "admin@example.com"
            }
          ],
          isTestData: true
        }
      ],
      asns: [
        {
          handle: "AS64496",
          startAutnum: 64496,
          endAutnum: 64496,
          autnumName: "EXAMPLE-AS",
          country: "US",
          childEntities: [
            {
              handle: "CONT-TECH-1",
              roles: ["technical"],
              contactName: "NOC Team",
              email: "noc@example.com"
            }
          ],
          isTestData: true
        }
      ]
    };
    setJsonInput(JSON.stringify(templates[type] || templates.domains, null, 2));
    setFile(null);
  };

  const getFooter = () => {
    if (result) {
      return <button type="button" className="btn btn-primary" onClick={handleClose}>{t('rdapImport.done')}</button>;
    }
    return (
      <>
        <button type="button" className="btn btn-secondary" onClick={handleClose}>{t('common.cancel')}</button>
        <button type="button" className="btn btn-primary" onClick={handleImport} disabled={submitting || (!jsonInput.trim() && !file)}>
          {submitting ? t('rdapImport.importing') : t('rdapImport.json.importJsonButton')}
        </button>
      </>
    );
  };

  return (
    <Modal isOpen={show} onClose={handleClose} title={t('rdapImport.json.modalTitle', { type })} footer={getFooter()} size="xlarge">
      <div className="json-import-modal">
        {!result ? (
          <div className="json-import-content">
            <div className="json-file-section">
              <label className="form-label">{t('rdapImport.json.uploadFileLabel')}</label>
              <div className="file-drop-zone compact">
                <input type="file" accept=".json" onChange={handleFileSelect} className="file-input" />
                <div className="file-drop-content">
                  <i className="fas fa-file-code" style={{ fontSize: '24px', color: '#64748b', marginRight: '12px' }}></i>
                  <span>{t('rdapImport.json.dropZoneText')}</span>
                  {file && <span className="file-selected"><i className="fas fa-check-circle" style={{ marginRight: '6px' }}></i>{file.name}</span>}
                </div>
              </div>
            </div>

            <div className="json-divider">
              <span>{t('rdapImport.json.orDivider')}</span>
            </div>

            <div className="json-editor-section">
              <div className="json-editor-header">
                <label className="form-label">{t('rdapImport.json.pasteLabel')}</label>
                <div className="json-editor-actions">
                  <button type="button" className="btn btn-outline-secondary btn-sm" onClick={loadTemplate}>
                    <i className="fas fa-file-alt" style={{ marginRight: '6px' }}></i>{t('rdapImport.json.loadTemplateButton')}
                  </button>
                  <button type="button" className="btn btn-outline-secondary btn-sm" onClick={formatJson}>
                    <i className="fas fa-indent" style={{ marginRight: '6px' }}></i>{t('rdapImport.json.formatButton')}
                  </button>
                </div>
              </div>
              <textarea
                className="json-editor"
                value={jsonInput}
                onChange={(e) => { setJsonInput(e.target.value); setFile(null); }}
                placeholder={t('rdapImport.json.textareaPlaceholder')}
              />
            </div>

            <div className="json-info-box">
              <h4><i className="fas fa-info-circle" style={{ marginRight: '8px' }}></i>{t('rdapImport.json.formatSupportsTitle')}</h4>
              <ul>
                <li><strong>childEntities:</strong> {t('rdapImport.json.childEntitiesDesc')}</li>
                <li><strong>events:</strong> {t('rdapImport.json.arrayOf')} {`{eventAction, eventDate, eventActor}`}</li>
                <li><strong>nameservers:</strong> {t('rdapImport.json.arrayOf')} {`{ldhName, ipv4Addresses, ipv6Addresses}`} ({t('rdapImport.json.domainsOnly')})</li>
                <li><strong>remarks:</strong> {t('rdapImport.json.arrayOf')} {`{title, description[]}`}</li>
                <li><strong>links:</strong> {t('rdapImport.json.arrayOf')} {`{href, rel, title}`}</li>
              </ul>
            </div>
          </div>
        ) : (
          <ImportResult result={result} />
        )}
      </div>

      <style>{`
        .json-import-modal {
          min-height: 400px;
        }
        
        .json-import-content {
          display: flex;
          flex-direction: column;
          gap: 24px;
          padding: 8px 0;
        }
        
        .json-file-section .form-label {
          font-weight: 600;
          font-size: 15px;
          margin-bottom: 12px;
          display: block;
        }
        
        .file-drop-zone.compact {
          position: relative;
          border: 2px dashed var(--border-primary, #e2e8f0);
          border-radius: 10px;
          padding: 24px;
          text-align: center;
          transition: all 0.2s ease;
          background: var(--bg-secondary, #f8fafc);
        }
        
        .file-drop-zone.compact:hover {
          border-color: var(--accent-primary, #6366f1);
        }
        
        .file-drop-zone.compact .file-input {
          position: absolute;
          inset: 0;
          opacity: 0;
          cursor: pointer;
        }
        
        .file-drop-zone.compact .file-drop-content {
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 12px;
          color: var(--text-muted, #64748b);
        }
        
        .file-drop-zone.compact .file-selected {
          color: var(--accent-success, #10b981);
          font-weight: 600;
        }
        
        .json-divider {
          display: flex;
          align-items: center;
          gap: 16px;
          color: var(--text-muted, #64748b);
        }
        
        .json-divider::before,
        .json-divider::after {
          content: '';
          flex: 1;
          height: 1px;
          background: var(--border-primary, #e2e8f0);
        }
        
        .json-editor-section {
          display: flex;
          flex-direction: column;
          gap: 12px;
        }
        
        .json-editor-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
        }
        
        .json-editor-header .form-label {
          font-weight: 600;
          font-size: 15px;
          margin: 0;
        }
        
        .json-editor-actions {
          display: flex;
          gap: 8px;
        }
        
        .json-editor {
          font-family: 'SF Mono', 'Monaco', 'Menlo', monospace;
          font-size: 13px;
          line-height: 1.6;
          min-height: 320px;
          padding: 16px;
          border: 1px solid var(--border-primary, #e2e8f0);
          border-radius: 10px;
          resize: vertical;
          background: var(--bg-primary, #fff);
        }
        
        .json-editor:focus {
          outline: none;
          border-color: var(--accent-primary, #6366f1);
          box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
        }
        
        .json-info-box {
          padding: 20px 24px;
          background: var(--bg-secondary, #f8fafc);
          border-radius: 12px;
          border: 1px solid var(--border-primary, #e2e8f0);
        }
        
        .json-info-box h4 {
          font-size: 15px;
          font-weight: 600;
          margin-bottom: 14px;
        }
        
        .json-info-box ul {
          margin: 0;
          padding-left: 20px;
          display: flex;
          flex-direction: column;
          gap: 8px;
        }
        
        .json-info-box li {
          font-size: 14px;
          color: var(--text-secondary, #475569);
          line-height: 1.5;
        }
        
        .json-info-box strong {
          color: var(--text-primary, #1e293b);
        }
      `}</style>
    </Modal>
  );
};

export default { CsvImportModal, JsonImportModal };