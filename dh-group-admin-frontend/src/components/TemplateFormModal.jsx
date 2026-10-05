/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useMemo, useRef } from 'react';
import { useAlert } from './AlertModal';
import { useT } from '../i18n';
import { RDRS_CATEGORIES, RDRS_PRIORITIES, RDRS_DATA_ELEMENT_GROUPS } from '../constants/rdrs';
import {
  STANDARD_LEGAL_SECTIONS,
  buildReferenceIndex,
  buildStandardSection,
  containsReference,
  makeClauseRefKey,
  makeSectionRefKey,
  normalizeSections,
  referenceLabels,
  referenceOptions,
  resolveReferences,
} from '../constants/legalSections';

const STANDARD_USER_FIELDS = [
  { key: 'first_name', label: 'First Name' },
  { key: 'last_name', label: 'Last Name' },
  { key: 'email', label: 'Email' },
  { key: 'phone', label: 'Phone Number' },
  { key: 'street_address', label: 'Street Address' },
  { key: 'city', label: 'City' },
  { key: 'state_province', label: 'State / Province' },
  { key: 'postal_code', label: 'Postal Code' },
  { key: 'country', label: 'Country' },
];
import ReferenceTextEditor from './ReferenceTextEditor';
import CountrySelect from './CountrySelect';

const DEFAULT_RDAP = () => {
  const keys = [
    'domainHandle','domainName','domainStatus','domainPort43','domainPublicIds',
    'nameservers','nameserverHandle','nameserverName','nameserverIpAddresses','nameserverStatus',
    'events','eventRegistration','eventExpiration','eventLastChanged','eventLastUpdateOfRdapDb','eventTransfer',
    'registrantEntity','registrantHandle','registrantName','registrantOrganization','registrantEmail','registrantPhone','registrantFax','registrantAddress','registrantStreet','registrantCity','registrantStateProvince','registrantPostalCode','registrantCountry',
    'adminEntity','adminHandle','adminName','adminOrganization','adminEmail','adminPhone','adminFax','adminAddress','adminStreet','adminCity','adminStateProvince','adminPostalCode','adminCountry',
    'techEntity','techHandle','techName','techOrganization','techEmail','techPhone','techFax','techAddress','techStreet','techCity','techStateProvince','techPostalCode','techCountry',
    'billingEntity','billingHandle','billingName','billingOrganization','billingEmail','billingPhone','billingFax','billingAddress','billingStreet','billingCity','billingStateProvince','billingPostalCode','billingCountry',
    'registrarEntity','registrarHandle','registrarName','registrarEmail','registrarPhone','registrarUrl','registrarAbuseContact',
    'dnssecData','dnssecDelegationSigned','dnssecDsData','dnssecKeyData',
    'networkHandle','networkName','networkType','networkStartAddress','networkEndAddress','networkIpVersion','networkParentHandle','networkCidr','networkCountry',
    'autnumHandle','autnumStart','autnumEnd','autnumName','autnumType','autnumCountry',
    'links','notices','remarks',
  ];
  const obj = {};
  keys.forEach(k => obj[k] = true);
  return obj;
};

const RDAP_GROUPS = {
  'Domain': ['domainHandle','domainName','domainStatus','domainPort43','domainPublicIds'],
  'Nameservers': ['nameservers','nameserverHandle','nameserverName','nameserverIpAddresses','nameserverStatus'],
  'Events': ['events','eventRegistration','eventExpiration','eventLastChanged','eventLastUpdateOfRdapDb','eventTransfer'],
  'Registrant': ['registrantEntity','registrantHandle','registrantName','registrantOrganization','registrantEmail','registrantPhone','registrantFax','registrantAddress','registrantStreet','registrantCity','registrantStateProvince','registrantPostalCode','registrantCountry'],
  'Admin': ['adminEntity','adminHandle','adminName','adminOrganization','adminEmail','adminPhone','adminFax','adminAddress','adminStreet','adminCity','adminStateProvince','adminPostalCode','adminCountry'],
  'Tech': ['techEntity','techHandle','techName','techOrganization','techEmail','techPhone','techFax','techAddress','techStreet','techCity','techStateProvince','techPostalCode','techCountry'],
  'Billing': ['billingEntity','billingHandle','billingName','billingOrganization','billingEmail','billingPhone','billingFax','billingAddress','billingStreet','billingCity','billingStateProvince','billingPostalCode','billingCountry'],
  'Registrar': ['registrarEntity','registrarHandle','registrarName','registrarEmail','registrarPhone','registrarUrl','registrarAbuseContact'],
  'DNSSEC': ['dnssecData','dnssecDelegationSigned','dnssecDsData','dnssecKeyData'],
  'Network': ['networkHandle','networkName','networkType','networkStartAddress','networkEndAddress','networkIpVersion','networkParentHandle','networkCidr','networkCountry'],
  'ASN': ['autnumHandle','autnumStart','autnumEnd','autnumName','autnumType','autnumCountry'],
  'Other': ['links','notices','remarks'],
};

const PARAM_DATA_TYPES = [
  { value: 'string', labelKey: 'templateFormModal.dataTypes.string', icon: 'fa-font', color: 'primary' },
  { value: 'integer', labelKey: 'templateFormModal.dataTypes.integer', icon: 'fa-hashtag', color: 'success' },
  { value: 'float', labelKey: 'templateFormModal.dataTypes.float', icon: 'fa-divide', color: 'success' },
  { value: 'boolean', labelKey: 'templateFormModal.dataTypes.boolean', icon: 'fa-toggle-on', color: 'info' },
  { value: 'date', labelKey: 'templateFormModal.dataTypes.date', icon: 'fa-calendar', color: 'warning' },
  { value: 'datetime', labelKey: 'templateFormModal.dataTypes.datetime', icon: 'fa-clock', color: 'warning' },
  { value: 'file', labelKey: 'templateFormModal.dataTypes.file', icon: 'fa-file-arrow-up', color: 'danger' },
  { value: 'email', labelKey: 'templateFormModal.dataTypes.email', icon: 'fa-envelope', color: 'primary' },
  { value: 'url', labelKey: 'templateFormModal.dataTypes.url', icon: 'fa-link', color: 'primary' },
  { value: 'enum', labelKey: 'templateFormModal.dataTypes.enum', icon: 'fa-list', color: 'secondary' },
  { value: 'text', labelKey: 'templateFormModal.dataTypes.text', icon: 'fa-align-left', color: 'dark' },
  { value: 'json', labelKey: 'templateFormModal.dataTypes.json', icon: 'fa-code', color: 'dark' },
];

const getTypeInfo = (type) => PARAM_DATA_TYPES.find(t => t.value === type) || PARAM_DATA_TYPES[0];

const createEmptyParam = (sortOrder = 0) => ({
  name: '',
  dataType: 'string',
  required: false,
  description: '',
  defaultValue: '',
  placeholder: '',
  enumValues: '',
  validationRegex: '',
  minValue: '',
  maxValue: '',
  maxLength: '',
  allowedFileTypes: '',
  maxFileSizeMb: '',
  fileCriteria: {
    minPages: '', maxPages: '', requireTextLayer: false, allowScanned: true,
    minWordCount: '', requiredTextPatterns: '', forbiddenTextPatterns: '',
    requireEmbeddedImage: false, minEmbeddedImageWidth: '', minEmbeddedImageHeight: '',
    minWidth: '', minHeight: '', minDpi: '',
  },
  sortOrder,
});

/**
 * Strip blank criteria and split the newline-separated pattern textareas into arrays,
 * so the stored JSON only ever contains rules the admin actually set.
 */
const normalizeFileCriteria = (fc) => {
  if (!fc) return null;
  const out = {};
  const num = (v) => (v === '' || v === null || v === undefined ? undefined : Number(v));
  const lines = (v) => (v || '').split('\n').map(s => s.trim()).filter(Boolean);
  ['minPages', 'maxPages', 'minWordCount', 'minEmbeddedImageWidth', 'minEmbeddedImageHeight',
    'minWidth', 'minHeight', 'minDpi'].forEach(k => {
    const n = num(fc[k]);
    if (n !== undefined && !Number.isNaN(n)) out[k] = n;
  });
  if (fc.requireTextLayer) out.requireTextLayer = true;
  if (fc.allowScanned === false) out.allowScanned = false;
  if (fc.requireEmbeddedImage) out.requireEmbeddedImage = true;
  const req = lines(fc.requiredTextPatterns);
  const forb = lines(fc.forbiddenTextPatterns);
  if (req.length) out.requiredTextPatterns = req;
  if (forb.length) out.forbiddenTextPatterns = forb;
  return Object.keys(out).length ? out : null;
};

/** Inverse of {@link normalizeFileCriteria}: stored JSON -> editor field shape. */
const expandFileCriteria = (fc) => {
  const base = createEmptyParam().fileCriteria;
  if (!fc) return base;
  const asText = (v) => (Array.isArray(v) ? v.join('\n') : (v || ''));
  return {
    ...base,
    ...fc,
    allowScanned: fc.allowScanned !== false,
    requiredTextPatterns: asText(fc.requiredTextPatterns),
    forbiddenTextPatterns: asText(fc.forbiddenTextPatterns),
  };
};

// ─── Custom Parameters Editor (sub-modal) ──────────────────────────────
const CustomParamsEditor = ({ parameters = [], onChange, onClose, requestTypeName }) => {
  const { t } = useT();
  const [params, setParams] = useState(parameters.length > 0 ? parameters : []);
  const [expandedIdx, setExpandedIdx] = useState(null);
  const [dragIdx, setDragIdx] = useState(null);

  const addParam = () => {
    const newParam = createEmptyParam(params.length);
    setParams(prev => [...prev, newParam]);
    setExpandedIdx(params.length);
  };

  const updateParam = (idx, field, value) => {
    setParams(prev => {
      const next = [...prev];
      next[idx] = { ...next[idx], [field]: value };
      return next;
    });
  };

  const removeParam = (idx) => {
    setParams(prev => prev.filter((_, i) => i !== idx).map((p, i) => ({ ...p, sortOrder: i })));
    if (expandedIdx === idx) setExpandedIdx(null);
    else if (expandedIdx > idx) setExpandedIdx(expandedIdx - 1);
  };

  const duplicateParam = (idx) => {
    const dup = { ...params[idx], name: params[idx].name + '_copy', sortOrder: params.length };
    setParams(prev => [...prev, dup]);
    setExpandedIdx(params.length);
  };

  const moveParam = (fromIdx, toIdx) => {
    if (toIdx < 0 || toIdx >= params.length) return;
    setParams(prev => {
      const next = [...prev];
      const [moved] = next.splice(fromIdx, 1);
      next.splice(toIdx, 0, moved);
      return next.map((p, i) => ({ ...p, sortOrder: i }));
    });
    setExpandedIdx(toIdx);
  };

  const handleSave = () => {
    onChange(params.map((p, i) => ({ ...p, sortOrder: i })));
    onClose();
  };

  const typeInfo = (t) => getTypeInfo(t);

  return (
    <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)', zIndex: 1060 }}>
      <div className="modal-dialog modal-xl modal-dialog-scrollable">
        <div className="modal-content">
          <div className="modal-header">
            <h5 className="modal-title">
              <i className="fa-solid fa-cube me-2"></i>
              {t('templateFormModal.customParams.title')} — <span className="text-primary">{requestTypeName || t('templateFormModal.customParams.requestTypeFallback')}</span>
            </h5>
            <button type="button" className="btn-close" onClick={onClose}></button>
          </div>
          <div className="modal-body">
            {/* Summary bar */}
            <div className="d-flex justify-content-between align-items-center mb-3">
              <div className="text-muted small">
                {params.length === 0
                  ? t('templateFormModal.customParams.emptyBar')
                  : `${params.length !== 1 ? t('templateFormModal.customParams.summaryParamsPlural', { count: params.length }) : t('templateFormModal.customParams.summaryParams', { count: params.length })} · ${t('templateFormModal.customParams.summaryRequired', { count: params.filter(p => p.required).length })}`}
              </div>
              <button className="btn btn-primary btn-sm" type="button" onClick={addParam}>
                <i className="fa-solid fa-plus me-1"></i> {t('templateFormModal.customParams.addParameter')}
              </button>
            </div>

            {/* Parameter list */}
            {params.map((param, idx) => {
              const ti = typeInfo(param.dataType);
              const isExpanded = expandedIdx === idx;
              const hasName = param.name?.trim();

              return (
                <div
                  key={idx}
                  className={`card mb-2 ${dragIdx === idx ? 'border-primary shadow-sm' : ''} ${!hasName && params.length > 0 ? 'border-danger' : ''}`}
                  draggable
                  onDragStart={() => setDragIdx(idx)}
                  onDragOver={(e) => e.preventDefault()}
                  onDrop={() => { if (dragIdx !== null && dragIdx !== idx) moveParam(dragIdx, idx); setDragIdx(null); }}
                  onDragEnd={() => setDragIdx(null)}
                >
                  {/* Collapsed header row */}
                  <div
                    className="card-header py-2 d-flex align-items-center gap-2"
                    style={{ cursor: 'pointer', backgroundColor: isExpanded ? '#f8f9fa' : 'white' }}
                    onClick={() => setExpandedIdx(isExpanded ? null : idx)}
                  >
                    <i className="fa-solid fa-grip-vertical text-muted" style={{ cursor: 'grab' }} title={t('templateFormModal.customParams.dragToReorder')}></i>
                    <span className={`badge bg-${ti.color}`} style={{ minWidth: 28 }}>
                      <i className={`fa-solid ${ti.icon}`}></i>
                    </span>
                    <strong className={`flex-grow-1 ${!hasName ? 'text-muted fst-italic' : ''}`}>
                      {hasName ? param.name : t('templateFormModal.customParams.unnamed')}
                    </strong>
                    {param.required && <span className="badge bg-danger" style={{ fontSize: 10 }}>{t('common.required')}</span>}
                    <span className="badge bg-light text-dark border" style={{ fontSize: 10 }}>{t(ti.labelKey)}</span>
                    {param.description && <i className="fa-solid fa-comment-dots text-muted small" title={param.description}></i>}
                    <i className={`fa-solid fa-chevron-${isExpanded ? 'up' : 'down'} text-muted small`}></i>
                  </div>

                  {/* Expanded editor */}
                  {isExpanded && (
                    <div className="card-body py-3">
                      <div className="row g-3">
                        {/* Row 1: Name + Type + Required */}
                        <div className="col-md-4">
                          <label className="form-label small mb-1 fw-semibold">{t('templateFormModal.customParams.parameterName')}</label>
                          <input
                            className={`form-control form-control-sm ${!hasName ? 'is-invalid' : ''}`}
                            value={param.name}
                            onChange={e => updateParam(idx, 'name', e.target.value.replace(/[^a-zA-Z0-9_]/g, ''))}
                            placeholder={t('templateFormModal.customParams.parameterNamePlaceholder')}
                          />
                          <div className="form-text" style={{ fontSize: 11 }}>{t('templateFormModal.customParams.parameterNameHint')}</div>
                        </div>
                        <div className="col-md-4">
                          <label className="form-label small mb-1 fw-semibold">{t('templateFormModal.customParams.dataType')}</label>
                          <select
                            className="form-select form-select-sm"
                            value={param.dataType}
                            onChange={e => updateParam(idx, 'dataType', e.target.value)}
                          >
                            {PARAM_DATA_TYPES.map(dt => (
                              <option key={dt.value} value={dt.value}>{t(dt.labelKey)}</option>
                            ))}
                          </select>
                        </div>
                        <div className="col-md-4 d-flex align-items-end gap-3">
                          <div className="form-check form-switch mb-2">
                            <input
                              className="form-check-input"
                              type="checkbox"
                              checked={param.required}
                              onChange={e => updateParam(idx, 'required', e.target.checked)}
                              id={`req-${idx}`}
                            />
                            <label className="form-check-label small" htmlFor={`req-${idx}`}>{t('common.required')}</label>
                          </div>
                        </div>

                        {/* Row 2: Description + Placeholder */}
                        <div className="col-md-6">
                          <label className="form-label small mb-1">{t('common.description')}</label>
                          <input
                            className="form-control form-control-sm"
                            value={param.description}
                            onChange={e => updateParam(idx, 'description', e.target.value)}
                            placeholder={t('templateFormModal.customParams.descriptionPlaceholder')}
                          />
                        </div>
                        <div className="col-md-6">
                          <label className="form-label small mb-1">{t('templateFormModal.customParams.placeholderLabel')}</label>
                          <input
                            className="form-control form-control-sm"
                            value={param.placeholder}
                            onChange={e => updateParam(idx, 'placeholder', e.target.value)}
                            placeholder={t('templateFormModal.customParams.placeholderPlaceholder')}
                          />
                        </div>

                        {/* Row 3: Default value (not for file type) */}
                        {param.dataType !== 'file' && (
                          <div className="col-md-6">
                            <label className="form-label small mb-1">{t('templateFormModal.customParams.defaultValue')}</label>
                            {param.dataType === 'boolean' ? (
                              <select
                                className="form-select form-select-sm"
                                value={param.defaultValue}
                                onChange={e => updateParam(idx, 'defaultValue', e.target.value)}
                              >
                                <option value="">{t('templateFormModal.customParams.noDefault')}</option>
                                <option value="true">true</option>
                                <option value="false">false</option>
                              </select>
                            ) : (
                              <input
                                className="form-control form-control-sm"
                                value={param.defaultValue}
                                onChange={e => updateParam(idx, 'defaultValue', e.target.value)}
                                type={['integer', 'float'].includes(param.dataType) ? 'number' : param.dataType === 'date' ? 'date' : 'text'}
                                step={param.dataType === 'float' ? '0.01' : undefined}
                              />
                            )}
                          </div>
                        )}

                        {/* Enum options */}
                        {param.dataType === 'enum' && (
                          <div className="col-md-6">
                            <label className="form-label small mb-1">{t('templateFormModal.customParams.enumValues')}</label>
                            <input
                              className="form-control form-control-sm"
                              value={param.enumValues}
                              onChange={e => updateParam(idx, 'enumValues', e.target.value)}
                              placeholder="option1, option2, option3"
                            />
                            <div className="form-text" style={{ fontSize: 11 }}>{t('templateFormModal.customParams.enumValuesHint')}</div>
                          </div>
                        )}

                        {/* String/text constraints */}
                        {['string', 'text', 'email', 'url'].includes(param.dataType) && (
                          <>
                            <div className="col-md-3">
                              <label className="form-label small mb-1">{t('templateFormModal.customParams.maxLength')}</label>
                              <input
                                className="form-control form-control-sm"
                                type="number"
                                value={param.maxLength}
                                onChange={e => updateParam(idx, 'maxLength', e.target.value)}
                                min={1}
                                placeholder={t('templateFormModal.customParams.noLimit')}
                              />
                            </div>
                            <div className="col-md-3">
                              <label className="form-label small mb-1">{t('templateFormModal.customParams.validationRegex')}</label>
                              <input
                                className="form-control form-control-sm font-monospace"
                                value={param.validationRegex}
                                onChange={e => updateParam(idx, 'validationRegex', e.target.value)}
                                placeholder="e.g. ^[A-Z]{2}\d+$"
                              />
                            </div>
                          </>
                        )}

                        {/* Number constraints */}
                        {['integer', 'float'].includes(param.dataType) && (
                          <>
                            <div className="col-md-3">
                              <label className="form-label small mb-1">{t('templateFormModal.customParams.minValue')}</label>
                              <input
                                className="form-control form-control-sm"
                                type="number"
                                value={param.minValue}
                                onChange={e => updateParam(idx, 'minValue', e.target.value)}
                                step={param.dataType === 'float' ? '0.01' : '1'}
                              />
                            </div>
                            <div className="col-md-3">
                              <label className="form-label small mb-1">{t('templateFormModal.customParams.maxValue')}</label>
                              <input
                                className="form-control form-control-sm"
                                type="number"
                                value={param.maxValue}
                                onChange={e => updateParam(idx, 'maxValue', e.target.value)}
                                step={param.dataType === 'float' ? '0.01' : '1'}
                              />
                            </div>
                          </>
                        )}

                        {/* File constraints */}
                        {param.dataType === 'file' && (
                          <>
                            <div className="col-md-4">
                              <label className="form-label small mb-1">{t('templateFormModal.customParams.allowedFileTypes')}</label>
                              <input
                                className="form-control form-control-sm"
                                value={param.allowedFileTypes}
                                onChange={e => updateParam(idx, 'allowedFileTypes', e.target.value)}
                                placeholder=".pdf, .jpg, .png"
                              />
                              <div className="form-text" style={{ fontSize: 11 }}>{t('templateFormModal.customParams.allowedFileTypesHint')}</div>
                            </div>
                            <div className="col-md-2">
                              <label className="form-label small mb-1">{t('templateFormModal.customParams.maxSizeMb')}</label>
                              <input
                                className="form-control form-control-sm"
                                type="number"
                                value={param.maxFileSizeMb}
                                onChange={e => updateParam(idx, 'maxFileSizeMb', e.target.value)}
                                min={1}
                                placeholder="10"
                              />
                            </div>

                            {/* Content-format criteria — checked by the document scanner on upload */}
                            <div className="col-12">
                              <div className="border rounded p-2 mt-1" style={{ background: 'var(--bs-tertiary-bg, #f8f9fa)' }}>
                                <div className="small fw-semibold mb-1">
                                  <i className="fa-solid fa-magnifying-glass-chart me-1"></i>
                                  {t('templateFormModal.customParams.formatCriteria')}
                                </div>
                                <div className="form-text mb-2" style={{ fontSize: 11 }}>
                                  {t('templateFormModal.customParams.formatCriteriaHint')}
                                </div>
                                <div className="row g-2">
                                  {[
                                    ['minPages', 'minPages', 1],
                                    ['maxPages', 'maxPages', 1],
                                    ['minWordCount', 'minWordCount', 0],
                                  ].map(([key, label, min]) => (
                                    <div className="col-md-4" key={key}>
                                      <label className="form-label small mb-1">{t(`templateFormModal.customParams.${label}`)}</label>
                                      <input className="form-control form-control-sm" type="number" min={min}
                                        value={param.fileCriteria?.[key] ?? ''}
                                        onChange={e => updateParam(idx, 'fileCriteria', { ...param.fileCriteria, [key]: e.target.value })} />
                                    </div>
                                  ))}

                                  <div className="col-md-6">
                                    <label className="form-label small mb-1">{t('templateFormModal.customParams.requiredTextPatterns')}</label>
                                    <textarea className="form-control form-control-sm" rows={2}
                                      value={param.fileCriteria?.requiredTextPatterns ?? ''}
                                      onChange={e => updateParam(idx, 'fileCriteria', { ...param.fileCriteria, requiredTextPatterns: e.target.value })}
                                      placeholder={'(?i)case\\s*no\\.?\n(?i)signed'} />
                                    <div className="form-text" style={{ fontSize: 11 }}>{t('templateFormModal.customParams.patternsHint')}</div>
                                  </div>
                                  <div className="col-md-6">
                                    <label className="form-label small mb-1">{t('templateFormModal.customParams.forbiddenTextPatterns')}</label>
                                    <textarea className="form-control form-control-sm" rows={2}
                                      value={param.fileCriteria?.forbiddenTextPatterns ?? ''}
                                      onChange={e => updateParam(idx, 'fileCriteria', { ...param.fileCriteria, forbiddenTextPatterns: e.target.value })} />
                                  </div>

                                  {[
                                    ['minWidth', 'minWidth'], ['minHeight', 'minHeight'], ['minDpi', 'minDpi'],
                                    ['minEmbeddedImageWidth', 'minEmbeddedImageWidth'], ['minEmbeddedImageHeight', 'minEmbeddedImageHeight'],
                                  ].map(([key, label]) => (
                                    <div className="col-md-4" key={key}>
                                      <label className="form-label small mb-1">{t(`templateFormModal.customParams.${label}`)}</label>
                                      <input className="form-control form-control-sm" type="number" min={0}
                                        value={param.fileCriteria?.[key] ?? ''}
                                        onChange={e => updateParam(idx, 'fileCriteria', { ...param.fileCriteria, [key]: e.target.value })} />
                                    </div>
                                  ))}

                                  <div className="col-12 d-flex flex-wrap gap-3 mt-1">
                                    {[
                                      ['requireTextLayer', 'requireTextLayer', false],
                                      ['requireEmbeddedImage', 'requireEmbeddedImage', false],
                                    ].map(([key, label]) => (
                                      <div className="form-check" key={key}>
                                        <input className="form-check-input" type="checkbox" id={`fc-${idx}-${key}`}
                                          checked={!!param.fileCriteria?.[key]}
                                          onChange={e => updateParam(idx, 'fileCriteria', { ...param.fileCriteria, [key]: e.target.checked })} />
                                        <label className="form-check-label small" htmlFor={`fc-${idx}-${key}`}>
                                          {t(`templateFormModal.customParams.${label}`)}
                                        </label>
                                      </div>
                                    ))}
                                    <div className="form-check">
                                      <input className="form-check-input" type="checkbox" id={`fc-${idx}-allowScanned`}
                                        checked={param.fileCriteria?.allowScanned !== false}
                                        onChange={e => updateParam(idx, 'fileCriteria', { ...param.fileCriteria, allowScanned: e.target.checked })} />
                                      <label className="form-check-label small" htmlFor={`fc-${idx}-allowScanned`}>
                                        {t('templateFormModal.customParams.allowScanned')}
                                      </label>
                                    </div>
                                  </div>
                                </div>
                              </div>
                            </div>
                          </>
                        )}
                      </div>

                      {/* Action buttons */}
                      <div className="d-flex justify-content-between mt-3 pt-2 border-top">
                        <div className="d-flex gap-1">
                          <button className="btn btn-outline-secondary btn-sm" type="button" onClick={() => moveParam(idx, idx - 1)} disabled={idx === 0} title={t('templateFormModal.customParams.moveUp')}>
                            <i className="fa-solid fa-arrow-up"></i>
                          </button>
                          <button className="btn btn-outline-secondary btn-sm" type="button" onClick={() => moveParam(idx, idx + 1)} disabled={idx === params.length - 1} title={t('templateFormModal.customParams.moveDown')}>
                            <i className="fa-solid fa-arrow-down"></i>
                          </button>
                          <button className="btn btn-outline-secondary btn-sm" type="button" onClick={() => duplicateParam(idx)} title={t('templateFormModal.customParams.duplicate')}>
                            <i className="fa-solid fa-copy"></i>
                          </button>
                        </div>
                        <button className="btn btn-outline-danger btn-sm" type="button" onClick={() => removeParam(idx)}>
                          <i className="fa-solid fa-trash me-1"></i> {t('common.remove')}
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}

            {/* Empty state */}
            {params.length === 0 && (
              <div className="text-center py-5 border rounded bg-light">
                <i className="fa-solid fa-cube fa-3x text-muted mb-3 d-block"></i>
                <p className="text-muted mb-2">{t('templateFormModal.customParams.emptyTitle')}</p>
                <p className="text-muted small mb-3">{t('templateFormModal.customParams.emptyBody')}</p>
                <button className="btn btn-primary btn-sm" type="button" onClick={addParam}>
                  <i className="fa-solid fa-plus me-1"></i> {t('templateFormModal.customParams.addFirstParameter')}
                </button>
              </div>
            )}
          </div>
          <div className="modal-footer d-flex justify-content-between">
            <div className="text-muted small">
              {params.length > 0 && (
                <span>
                  <i className="fa-solid fa-grip-vertical me-1"></i> {t('templateFormModal.customParams.reorderHint')}
                </span>
              )}
            </div>
            <div className="d-flex gap-2">
              <button className="btn btn-secondary" onClick={onClose}>{t('common.cancel')}</button>
              <button className="btn btn-primary" onClick={handleSave}>
                <i className="fa-solid fa-check me-1"></i> {t('templateFormModal.customParams.saveParameters')}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

// ─── RDAP Parameters Editor ────────────────────────────────────────────
const RdapParamsEditor = ({ values, onChange }) => {
  const { t } = useT();
  const [collapsed, setCollapsed] = useState({});
  const toggle = (g) => setCollapsed(p => ({ ...p, [g]: !p[g] }));
  const toggleAll = (group, fields, val) => {
    const next = { ...values };
    fields.forEach(f => next[f] = val);
    onChange(next);
  };

  return (
    <div className="accordion" id="rdapAccordion">
      {Object.entries(RDAP_GROUPS).map(([group, fields]) => {
        const enabled = fields.filter(f => values[f] !== false).length;
        const isOpen = !collapsed[group];
        return (
          <div className="accordion-item" key={group}>
            <h2 className="accordion-header">
              <button className={`accordion-button ${isOpen ? '' : 'collapsed'} py-2`} type="button" onClick={() => toggle(group)}>
                <span className="me-2">{t('templateFormModal.rdap.groups.' + group)}</span>
                <span className={`badge bg-${enabled === fields.length ? 'success' : enabled === 0 ? 'secondary' : 'warning'} ms-auto me-2`}>
                  {enabled}/{fields.length}
                </span>
              </button>
            </h2>
            {isOpen && (
              <div className="accordion-body py-2">
                <div className="mb-2">
                  <button className="btn btn-outline-success btn-sm me-1" type="button" onClick={() => toggleAll(group, fields, true)}>{t('templateFormModal.rdap.allOn')}</button>
                  <button className="btn btn-outline-secondary btn-sm" type="button" onClick={() => toggleAll(group, fields, false)}>{t('templateFormModal.rdap.allOff')}</button>
                </div>
                <div className="row g-1">
                  {fields.map(f => (
                    <div className="col-6 col-md-4" key={f}>
                      <div className="form-check form-switch">
                        <input className="form-check-input" type="checkbox" checked={values[f] !== false}
                          onChange={(e) => onChange({ ...values, [f]: e.target.checked })} id={`rdap-${f}`} />
                        <label className="form-check-label small" htmlFor={`rdap-${f}`}>
                          {f.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase())}
                        </label>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};

// ─── RDRS Defaults Editor ──────────────────────────────────────────────
// An RDRS request type carries presets instead of RDAP parameters: which ICANN
// category/priority to preselect, and which requested data elements the requestor
// is allowed to pick from. Leaving the element list empty permits all of them.
const RdrsDefaultsEditor = ({ rt, onChange }) => {
  const { t } = useT();
  const defaults = rt.rdrsDefaults || {};
  const setDefault = (field, val) => onChange({ ...rt, rdrsDefaults: { ...defaults, [field]: val } });

  const allowed = defaults.allowedDataElements || [];
  const toggleElement = (label) => {
    const next = allowed.includes(label) ? allowed.filter(l => l !== label) : [...allowed, label];
    setDefault('allowedDataElements', next);
  };

  return (
    <div className="border rounded p-2 mt-2 bg-body-tertiary">
      <div className="small fw-semibold mb-2">
        <i className="fa-solid fa-globe me-1" style={{ opacity: 0.6 }}></i>
        {t('templateFormModal.requestType.rdrsDefaults')}
      </div>
      <div className="row g-2">
        <div className="col-md-4">
          <label className="form-label small mb-0">{t('templateFormModal.requestType.rdrsCategory')}</label>
          <select className="form-select form-select-sm" value={defaults.category || ''} onChange={e => setDefault('category', e.target.value || undefined)}>
            <option value="">{t('templateFormModal.requestType.rdrsNoPreset')}</option>
            {RDRS_CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
          </select>
        </div>
        <div className="col-md-4">
          <label className="form-label small mb-0">{t('templateFormModal.requestType.rdrsPriority')}</label>
          <select className="form-select form-select-sm" value={defaults.requestPriority || ''} onChange={e => setDefault('requestPriority', e.target.value || undefined)}>
            <option value="">{t('templateFormModal.requestType.rdrsNoPreset')}</option>
            {RDRS_PRIORITIES.map(p => <option key={p} value={p}>{p}</option>)}
          </select>
        </div>
        <div className="col-md-4">
          <label className="form-label small mb-0">{t('templateFormModal.requestType.rdrsOrganization')}</label>
          <input className="form-control form-control-sm" value={defaults.organization || ''}
            onChange={e => setDefault('organization', e.target.value || undefined)}
            placeholder={t('templateFormModal.requestType.rdrsOrganizationPlaceholder')} />
        </div>
      </div>
      <div className="mt-2">
        <label className="form-label small mb-1">
          {t('templateFormModal.requestType.rdrsAllowedElements')}
          <span className="text-muted ms-1" style={{ fontSize: 10 }}>
            {allowed.length === 0
              ? t('templateFormModal.requestType.rdrsAllElements')
              : t('templateFormModal.requestType.rdrsSomeElements', { count: allowed.length })}
          </span>
        </label>
        <div className="row g-2">
          {RDRS_DATA_ELEMENT_GROUPS.map(group => (
            <div className="col-md-4" key={group.groupName}>
              <div className="small text-muted mb-1">{group.groupName}</div>
              {group.elements.map(label => (
                <div className="form-check form-check-sm small" key={label}>
                  <input className="form-check-input" type="checkbox" id={`rdrs-el-${rt.typeCode ?? 'new'}-${label}`}
                    checked={allowed.includes(label)} onChange={() => toggleElement(label)} />
                  <label className="form-check-label" htmlFor={`rdrs-el-${rt.typeCode ?? 'new'}-${label}`} style={{ fontSize: 11 }}>{label}</label>
                </div>
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

// ─── Request Type Editor Row ───────────────────────────────────────────
const RequestTypeEditor = ({ rt, index, onChange, onRemove, onEditRdap, onEditCustomParams, isDuplicateName }) => {
  const { t } = useT();
  const set = (field, val) => onChange(index, { ...rt, [field]: val });
  const paramCount = (rt.customParameters || []).length;
  const hasRegex = rt.queryValueRegex && rt.queryValueRegex.trim();
  // An RDRS request type goes to ICANN, not a data holder, so access level, RDAP
  // parameters, confidential/exigent and the query regex do not apply to it.
  const isRdrs = rt.kind === 'RDRS';

  return (
    <div className={`card mb-2 ${rt.isActive === false ? 'opacity-50' : ''}`}>
      <div className="card-body py-2">
        <div className="d-flex align-items-center gap-2 mb-2">
          <span className="badge bg-secondary-subtle text-secondary border" style={{ fontSize: 10 }} title={t('templateFormModal.requestType.typeCodeReadonlyHint')}>
            <i className="fa-solid fa-hashtag me-1" style={{ opacity: 0.6 }}></i>
            {t('templateFormModal.requestType.typeCodeLabel')}: {rt.typeCode != null ? rt.typeCode : t('templateFormModal.requestType.typeCodeAuto')}
          </span>
          {isRdrs && (
            <span className="badge bg-info-subtle text-info-emphasis border" style={{ fontSize: 10 }} title={t('templateFormModal.requestType.kindRdrsHint')}>
              <i className="fa-solid fa-globe me-1"></i>ICANN RDRS
            </span>
          )}
        </div>
        <div className="row g-2 align-items-end">
          <div className="col-md-3">
            <label className="form-label small mb-0">{t('templateFormModal.requestType.name')}</label>
            <input className={`form-control form-control-sm ${isDuplicateName ? 'is-invalid' : ''}`} value={rt.name || ''} onChange={e => set('name', e.target.value)} placeholder="standard" />
            {isDuplicateName && <div className="invalid-feedback" style={{ fontSize: 10 }}>{t('templateFormModal.requestType.duplicateName')}</div>}
          </div>
          <div className="col-md-2">
            <label className="form-label small mb-0">{t('templateFormModal.requestType.kind')}</label>
            <select className="form-select form-select-sm" value={rt.kind || 'RDAP'} onChange={e => set('kind', e.target.value)}>
              <option value="RDAP">{t('templateFormModal.requestType.kindRdap')}</option>
              <option value="RDRS">{t('templateFormModal.requestType.kindRdrs')}</option>
            </select>
          </div>
          {!isRdrs && (
            <div className="col-md-2">
              <label className="form-label small mb-0">{t('templateFormModal.requestType.accessLevel')}</label>
              <select className="form-select form-select-sm" value={rt.accessLevel ?? 0} onChange={e => set('accessLevel', parseInt(e.target.value))}>
                <option value={0}>{t('templateFormModal.requestType.accessLevel0')}</option><option value={1}>{t('templateFormModal.requestType.accessLevel1')}</option><option value={2}>{t('templateFormModal.requestType.accessLevel2')}</option><option value={3}>{t('templateFormModal.requestType.accessLevel3')}</option>
              </select>
            </div>
          )}
          <div className={isRdrs ? 'col-md-4' : 'col-md-2'}>
            <label className="form-label small mb-0">{t('common.description')}</label>
            <input className="form-control form-control-sm" value={rt.description || ''} onChange={e => set('description', e.target.value)} />
          </div>
          <div className="col-md-3 d-flex gap-2 align-items-end flex-wrap">
            {!isRdrs && (
              <>
                <div className="form-check form-check-inline small"><input className="form-check-input" type="checkbox" checked={rt.supportsConfidential || false} onChange={e => set('supportsConfidential', e.target.checked)} /><label className="form-check-label">{t('templateFormModal.requestType.conf')}</label></div>
                <div className="form-check form-check-inline small"><input className="form-check-input" type="checkbox" checked={rt.supportsExigent || false} onChange={e => set('supportsExigent', e.target.checked)} /><label className="form-check-label">{t('templateFormModal.requestType.exig')}</label></div>
              </>
            )}
            <div className="form-check form-check-inline small"><input className="form-check-input" type="checkbox" checked={rt.requiresManualApproval !== false} onChange={e => set('requiresManualApproval', e.target.checked)} /><label className="form-check-label">{t('templateFormModal.requestType.manualApproval')}</label></div>
            <div className="form-check form-check-inline small"><input className="form-check-input" type="checkbox" checked={rt.isActive !== false} onChange={e => set('isActive', e.target.checked)} /><label className="form-check-label">{t('common.active')}</label></div>
            {!isRdrs && (
              <button className="btn btn-outline-secondary btn-sm" type="button" onClick={() => onEditRdap(index)} title={t('templateFormModal.requestType.rdapParams')}><i className="fa-solid fa-sliders"></i></button>
            )}
            <button
              className={`btn btn-sm ${paramCount > 0 ? 'btn-outline-primary' : 'btn-outline-secondary'}`}
              type="button"
              onClick={() => onEditCustomParams(index)}
              title={t('templateFormModal.requestType.customParameters')}
            >
              <i className="fa-solid fa-cube me-1"></i>
              {paramCount > 0 && <span className="badge bg-primary rounded-pill" style={{ fontSize: 10 }}>{paramCount}</span>}
            </button>
            <button className="btn btn-outline-danger btn-sm" type="button" onClick={() => onRemove(index)} title={t('common.remove')}><i className="fa-solid fa-xmark"></i></button>
          </div>
        </div>
        {isRdrs ? (
          <RdrsDefaultsEditor rt={rt} onChange={(next) => onChange(index, next)} />
        ) : (
          /* Query Value Regex Validation */
          <div className="row g-2 mt-1">
            <div className="col-md-5">
              <label className="form-label small mb-0">
                <i className="fa-solid fa-filter me-1" style={{ fontSize: 10, opacity: 0.5 }}></i>
                {t('templateFormModal.requestType.queryValueRegex')}
                {hasRegex && <span className="badge bg-warning text-dark ms-1" style={{ fontSize: 9 }}>{t('templateFormModal.requestType.activeBadge')}</span>}
              </label>
              <input className="form-control form-control-sm" value={rt.queryValueRegex || ''} onChange={e => set('queryValueRegex', e.target.value)}
                placeholder={t('templateFormModal.requestType.queryValueRegexPlaceholder')} style={{ fontFamily: 'monospace', fontSize: 12 }} />
            </div>
            <div className="col-md-7">
              <label className="form-label small mb-0">{t('templateFormModal.requestType.rejectionMessage')}</label>
              <input className="form-control form-control-sm" value={rt.queryValueRegexError || ''} onChange={e => set('queryValueRegexError', e.target.value)}
                placeholder={t('templateFormModal.requestType.rejectionMessagePlaceholder')} disabled={!hasRegex} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

// ─── Main Template Form Modal ──────────────────────────────────────────
const TemplateFormModal = ({ show, onHide, onSave, template = null, submitting = false, groups = [],
                             isMaster = false, lockedGroupId = null }) => {
  const { t } = useT();
  const isEditing = !!template;
  const init = (tpl) => ({
    name: tpl?.name || '', shortDescription: tpl?.shortDescription || '', description: tpl?.description || '',
    requiredGroupTypes: tpl?.requiredGroupTypes || '',
    legalSections: normalizeSections(tpl?.legalSections),
    /* Contact shown to requestors: the group's unless this agreement answers for its own.
     * The template's own values are kept either way, so unlinking restores what was typed. */
    useGroupContact: tpl?.useGroupContact ?? true,
    contactFirstName: tpl?.templateContact?.firstName || '',
    contactLastName: tpl?.templateContact?.lastName || '',
    contactEmail: tpl?.templateContact?.email || '',
    contactPhone: tpl?.templateContact?.phone || '',
    contactAddress: tpl?.templateContact?.address || '',
    contactCity: tpl?.templateContact?.city || '',
    contactStateProvince: tpl?.templateContact?.stateProvince || '',
    contactPostalCode: tpl?.templateContact?.postalCode || '',
    contactCountry: tpl?.templateContact?.country || '',
    subscriptionFields: (tpl?.subscriptionFields || []).map(f => ({ ...f })),
    userFields: (tpl?.userFields || []).map(f => ({ ...f })),
    maxQueriesPerDay: tpl?.maxQueriesPerDay ?? '',
    maxQueriesPerMonth: tpl?.maxQueriesPerMonth ?? '',
    isActive: tpl?.isActive ?? false,
    visibility: tpl?.visibility || 'PUBLIC',
    disclosureMode: tpl?.disclosureMode || 'open',
    dataHolderGroupId: tpl?.dataHolderGroupId ?? lockedGroupId ?? (groups.length > 0 ? groups[0].id : ''),
    requestTypes: (tpl?.requestTypes?.length > 0)
      ? tpl.requestTypes.map(rt => ({
          ...rt,
          rdapParameters: rt.rdapParameters || DEFAULT_RDAP(),
          customParameters: (rt.customParameters || []).map(p => ({
            ...p,
            fileCriteria: expandFileCriteria(p.fileCriteria),
          })),
        }))
      : [{ name: 'standard', typeCode: null, kind: 'RDAP', description: 'Standard RDAP request', accessLevel: 0, supportsConfidential: false, supportsExigent: false, requiresManualApproval: true, sortOrder: 0, isActive: true, rdapParameters: DEFAULT_RDAP(), customParameters: [] }],
  });

  const [form, setForm] = useState(() => init(template));
  const [tab, setTab] = useState('basic');
  const [rdapIdx, setRdapIdx] = useState(null);
  const [customParamsIdx, setCustomParamsIdx] = useState(null);
  const { alert: showAlert, AlertDialog } = useAlert();

  useEffect(() => { if (show) { setForm(init(template)); setTab('basic'); setRdapIdx(null); setCustomParamsIdx(null); } }, [show, template]);

  const set = (f, v) => setForm(p => ({ ...p, [f]: v }));
  const clauseRefs = useRef({});
  const setSections = (next) => set('legalSections', next.map((sec, i) => ({ ...sec, sortOrder: i })));
  const updateSection = (i, patch) => setSections(form.legalSections.map((sec, j) => (j === i ? { ...sec, ...patch } : sec)));
  const updateClause = (i, j, patch) => updateSection(i, {
    clauses: (form.legalSections[i].clauses || []).map((clause, k) => (k === j ? { ...clause, ...patch } : clause)),
  });
  const moveSection = (i, delta) => {
    const next = [...form.legalSections];
    [next[i + delta], next[i]] = [next[i], next[i + delta]];
    setSections(next);
  };
  const moveClause = (i, j, delta) => {
    const next = [...(form.legalSections[i].clauses || [])];
    [next[j + delta], next[j]] = [next[j], next[j + delta]];
    updateSection(i, { clauses: next });
  };
  /*
   * The clause editor owns the caret (it is a contenteditable surface, not a
   * textarea), so insertion is handed to it and comes back through onChange.
   */
  const insertReference = (i, j, refKey) => {
    if (!refKey) return;
    const clause = form.legalSections[i].clauses[j];
    const editor = clauseRefs.current[clause.refKey];
    if (editor) editor.insertReference(refKey);
    else updateClause(i, j, { text: `${clause.text || ''}{{ref:${refKey}}}` });
  };
  /* What this agreement inherits while linked: from the chosen group, falling back to the
   * copy the server sent with the template when the group list carries no contact. */
  const groupContact = useMemo(() => {
    const group = groups.find(g => String(g.id) === String(form.dataHolderGroupId));
    return group?.contact || template?.groupContact || null;
  }, [groups, form.dataHolderGroupId, template]);
  const contactInForce = form.useGroupContact ? groupContact : null;

  const refLabels = useMemo(() => referenceLabels(form.legalSections), [form.legalSections]);
  const refIndex = useMemo(() => buildReferenceIndex(form.legalSections), [form.legalSections]);
  const refOptions = useMemo(() => referenceOptions(form.legalSections), [form.legalSections]);
  const refStrings = {
    untitled: t('templateFormModal.main.referenceUntitled'),
    unknown: t('templateFormModal.main.referenceUnknown'),
    tooltip: t('templateFormModal.main.referenceTooltip'),
    unknownTooltip: t('templateFormModal.main.referenceUnknownTooltip'),
  };
  const setRT = (i, val) => setForm(p => { const rts = [...p.requestTypes]; rts[i] = val; return { ...p, requestTypes: rts }; });
  const removeRT = (i) => setForm(p => ({ ...p, requestTypes: p.requestTypes.filter((_, j) => j !== i) }));
  const addRT = (preset) => {
    const presets = {
      standard: { name: 'standard', typeCode: null, kind: 'RDAP', accessLevel: 1, description: 'Standard', supportsConfidential: false, supportsExigent: false, requiresManualApproval: true, isActive: true, rdapParameters: DEFAULT_RDAP(), customParameters: [] },
      confidential: { name: 'confidential', typeCode: null, kind: 'RDAP', accessLevel: 2, description: 'Confidential', supportsConfidential: true, supportsExigent: false, requiresManualApproval: true, isActive: true, rdapParameters: DEFAULT_RDAP(), customParameters: [] },
      exigent: { name: 'exigent', typeCode: null, kind: 'RDAP', accessLevel: 3, description: 'Exigent', supportsConfidential: false, supportsExigent: true, requiresManualApproval: false, isActive: true, rdapParameters: DEFAULT_RDAP(), customParameters: [] },
      // RDRS goes to ICANN rather than a data holder, so it carries no RDAP parameters.
      rdrs: { name: 'rdrs', typeCode: null, kind: 'RDRS', accessLevel: 0, description: 'ICANN RDRS registration data request', supportsConfidential: false, supportsExigent: false, requiresManualApproval: true, isActive: true, rdapParameters: null, rdrsDefaults: {}, customParameters: [] },
    };
    setForm(p => ({ ...p, requestTypes: [...p.requestTypes, { ...presets[preset], sortOrder: p.requestTypes.length }] }));
  };

  const handleCustomParamsChange = (idx, params) => {
    setRT(idx, { ...form.requestTypes[idx], customParameters: params });
  };

  const handleSubmit = () => {
    if (!form.name.trim()) { setTab('basic'); return; }
    /* An agreement on its own contact has to say where to write; one on the group's is
     * the group's to keep filled in, and is only warned about here. */
    if (!form.useGroupContact && !form.contactEmail.trim()) {
      setTab('terms');
      showAlert(
        t('templateFormModal.main.contactEmailRequired'),
        t('templateFormModal.main.contactHeading'),
        'error'
      );
      return;
    }
    if (form.requestTypes.length === 0 || form.requestTypes.some(rt => !rt.name?.trim())) { setTab('requestTypes'); return; }
    // Check for duplicate request type names (case-insensitive)
    const names = form.requestTypes.map(rt => (rt.name || '').trim().toLowerCase());
    const dupNames = names.filter((n, i) => n && names.indexOf(n) !== i);
    if (dupNames.length > 0) {
      setTab('requestTypes');
      const shownNames = [...new Set(
        form.requestTypes
          .map(rt => (rt.name || '').trim())
          .filter(nm => dupNames.includes(nm.toLowerCase()))
      )];
      showAlert(
        t('templateFormModal.main.duplicateNamesMessage', { names: shownNames.join(', ') }),
        t('templateFormModal.main.duplicateNamesTitle'),
        'error'
      );
      return;
    }
    // Validate custom params: ensure all have names
    for (let i = 0; i < form.requestTypes.length; i++) {
      const params = form.requestTypes[i].customParameters || [];
      if (params.some(p => !p.name?.trim())) {
        setTab('requestTypes');
        setCustomParamsIdx(i);
        return;
      }
      // Validate enum params have values
      if (params.some(p => p.dataType === 'enum' && !p.enumValues?.trim())) {
        setTab('requestTypes');
        setCustomParamsIdx(i);
        return;
      }
    }
    onSave({
      ...form,
      legalSections: form.legalSections
        .filter(s => (s.title || '').trim())
        .map((s, i) => ({ ...s, sortOrder: i })),
      subscriptionFields: form.subscriptionFields
        .filter(f => (f.name || '').trim())
        .map((f, i) => ({ ...f, sortOrder: i })),
      userFields: form.userFields
        .filter(f => f.standard || (f.label || '').trim())
        .map((f, i) => ({ ...f, sortOrder: i })),
      dataHolderGroupId: form.dataHolderGroupId ? parseInt(form.dataHolderGroupId) : null,
      maxQueriesPerDay: form.maxQueriesPerDay ? parseInt(form.maxQueriesPerDay) : null,
      maxQueriesPerMonth: form.maxQueriesPerMonth ? parseInt(form.maxQueriesPerMonth) : null,
      requestTypes: form.requestTypes.map(rt => ({
        ...rt,
        customParameters: (rt.customParameters || []).map(p => ({
          ...p,
          fileCriteria: p.dataType === 'file' ? normalizeFileCriteria(p.fileCriteria) : null,
        })),
      })),
    });
  };

  if (!show) return null;

  // Custom Parameters sub-modal
  if (customParamsIdx !== null && form.requestTypes[customParamsIdx]) {
    const rt = form.requestTypes[customParamsIdx];
    return (
      <CustomParamsEditor
        parameters={rt.customParameters || []}
        onChange={(params) => handleCustomParamsChange(customParamsIdx, params)}
        onClose={() => setCustomParamsIdx(null)}
        requestTypeName={rt.name}
      />
    );
  }

  // RDAP sub-modal
  if (rdapIdx !== null && form.requestTypes[rdapIdx]) {
    const rt = form.requestTypes[rdapIdx];
    return (
      <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
        <div className="modal-dialog modal-xl modal-dialog-scrollable">
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title"><i className="fa-solid fa-sliders me-2"></i>{t('templateFormModal.rdap.title')} — {rt.name}</h5>
              <button type="button" className="btn-close" onClick={() => setRdapIdx(null)}></button>
            </div>
            <div className="modal-body">
              <RdapParamsEditor values={rt.rdapParameters || DEFAULT_RDAP()} onChange={(vals) => setRT(rdapIdx, { ...rt, rdapParameters: vals })} />
            </div>
            <div className="modal-footer">
              <button className="btn btn-primary" onClick={() => setRdapIdx(null)}>{t('templateFormModal.rdap.done')}</button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  const tabs = [
    { id: 'basic', label: t('templateFormModal.main.tabBasic'), icon: 'fa-solid fa-info-circle' },
    { id: 'requestTypes', label: t('templateFormModal.main.tabRequestTypes', { count: form.requestTypes.length }), icon: 'fa-solid fa-layer-group' },
    { id: 'limits', label: t('templateFormModal.main.tabLimits'), icon: 'fa-solid fa-gauge-high' },
    { id: 'terms', label: t('templateFormModal.main.tabTerms'), icon: 'fa-solid fa-file-shield' },
  ];

  return (
    <div className="modal show d-block" tabIndex={-1} style={{ backgroundColor: 'rgba(0,0,0,.5)' }}>
      {AlertDialog}
      <div className="modal-dialog modal-xl modal-dialog-scrollable">
        <div className="modal-content">
          <div className="modal-header">
            <h5 className="modal-title">
              <i className={`fa-solid ${isEditing ? 'fa-pen' : 'fa-plus'} me-2`}></i>
              {isEditing ? t('templateFormModal.main.editTemplate') : t('templateFormModal.main.createTemplate')}
            </h5>
            <button type="button" className="btn-close" onClick={onHide}></button>
          </div>
          <div className="modal-body">
            {/* Tabs */}
            <ul className="nav nav-tabs mb-3">
              {tabs.map(t => (
                <li className="nav-item" key={t.id}>
                  <button className={`nav-link ${tab === t.id ? 'active' : ''}`} onClick={() => setTab(t.id)}>
                    <i className={`${t.icon} me-1`}></i> {t.label}
                  </button>
                </li>
              ))}
            </ul>

            {/* Basic */}
            {tab === 'basic' && (
              <div className="row g-3">
                <div className="col-md-8"><label className="form-label">{t('templateFormModal.main.templateName')}</label><input className="form-control" value={form.name} onChange={e => set('name', e.target.value)} placeholder={t('templateFormModal.main.templateNamePlaceholder')} /></div>
                <div className="col-md-4">
                  <label className="form-label">{t('templateFormModal.main.dataHolderGroup')}</label>
                  {!isEditing ? (
                    <>
                      <div className="form-control-plaintext border rounded px-3 py-2 bg-light">
                        <i className="fa-solid fa-layer-group me-2 text-muted"></i>
                        {groups.find(g => String(g.id) === String(form.dataHolderGroupId))?.name
                          || t('templateFormModal.main.selectGroup')}
                      </div>
                      <small className="text-muted">{t('templateFormModal.main.dataHolderGroupFixed')}</small>
                    </>
                  ) : (
                    <>
                      <select className="form-select" value={form.dataHolderGroupId}
                              onChange={e => set('dataHolderGroupId', e.target.value)} disabled={!isMaster}>
                        <option value="">{t('templateFormModal.main.selectGroup')}</option>
                        {groups.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
                      </select>
                      {!isMaster && (
                        <small className="text-muted">{t('templateFormModal.main.dataHolderGroupLocked')}</small>
                      )}
                    </>
                  )}
                </div>
                <div className="col-md-6"><label className="form-label">{t('templateFormModal.main.shortDescription')} <small className="text-muted">({form.shortDescription.length}/25)</small></label><input className="form-control" value={form.shortDescription} onChange={e => set('shortDescription', e.target.value)} maxLength={25} /></div>
                <div className="col-md-6"><label className="form-label">{t('templateFormModal.main.requiredGroupTypes')}</label><input className="form-control" value={form.requiredGroupTypes} onChange={e => set('requiredGroupTypes', e.target.value)} placeholder={t('templateFormModal.main.requiredGroupTypesPlaceholder')} /></div>
                <div className="col-md-6">
                  <label className="form-label">{t('templateFormModal.main.disclosureMode')}</label>
                  <select className="form-select" value={form.disclosureMode} onChange={e => set('disclosureMode', e.target.value)}>
                    <option value="open">{t('templateFormModal.main.disclosureOpen')}</option>
                    <option value="hashed">{t('templateFormModal.main.disclosureHashed')}</option>
                    <option value="omit">{t('templateFormModal.main.disclosureOmit')}</option>
                  </select>
                  <div className="form-text">{t('templateFormModal.main.disclosureHint')}</div>
                </div>
                <div className="col-12"><label className="form-label">{t('common.description')}</label><textarea className="form-control" rows={3} value={form.description} onChange={e => set('description', e.target.value)} /></div>
                <div className="col-md-6">
                  <label className="form-label">{t('templateFormModal.main.visibility')}</label>
                  <select className="form-select" value={form.visibility} onChange={e => set('visibility', e.target.value)}>
                    <option value="PRIVATE">{t('templates.visibility.private')}</option>
                    <option value="PUBLIC">{t('templates.visibility.public')}</option>
                    <option value="GLOBAL">{t('templates.visibility.global')}</option>
                  </select>
                  <div className="form-text">{t(`templates.visibility.${form.visibility.toLowerCase()}Hint`)}</div>
                </div>
                <div className="col-md-6 d-flex align-items-end">
                  <div>
                    <div className="form-check form-switch">
                      <input className="form-check-input" type="checkbox" checked={form.isActive} onChange={e => set('isActive', e.target.checked)} id="templateActive" />
                      <label className="form-check-label" htmlFor="templateActive">{t('templateFormModal.main.active')}</label>
                    </div>
                    <div className="form-text">{t('templateFormModal.main.activeHint')}</div>
                  </div>
                </div>
              </div>
            )}

            {/* Request Types */}
            {tab === 'requestTypes' && (
              <div>
                <div className="d-flex gap-2 mb-3">
                  <button className="btn btn-outline-secondary btn-sm" type="button" onClick={() => addRT('standard')}><i className="fa-solid fa-plus me-1"></i>{t('templateFormModal.main.presetStandard')}</button>
                  <button className="btn btn-outline-warning btn-sm" type="button" onClick={() => addRT('confidential')}><i className="fa-solid fa-lock me-1"></i>{t('templateFormModal.main.presetConfidential')}</button>
                  <button className="btn btn-outline-danger btn-sm" type="button" onClick={() => addRT('exigent')}><i className="fa-solid fa-bolt me-1"></i>{t('templateFormModal.main.presetExigent')}</button>
                  <button className="btn btn-outline-info btn-sm" type="button" onClick={() => addRT('rdrs')}><i className="fa-solid fa-globe me-1"></i>{t('templateFormModal.main.presetRdrs')}</button>
                </div>
                {form.requestTypes.length === 0 ? (
                  <div className="text-center text-muted py-4 border rounded">{t('templateFormModal.main.noRequestTypes')}</div>
                ) : form.requestTypes.map((rt, i) => (
                  <RequestTypeEditor key={i} rt={rt} index={i} onChange={setRT} onRemove={removeRT} onEditRdap={setRdapIdx} onEditCustomParams={setCustomParamsIdx}
                    isDuplicateName={!!rt.name?.trim() && form.requestTypes.some((other, j) => j !== i && (other.name || '').trim().toLowerCase() === (rt.name || '').trim().toLowerCase())} />
                ))}
              </div>
            )}

            {/* Rate Limits */}
            {tab === 'limits' && (
              <div className="row g-3">
                <div className="col-md-6"><label className="form-label">{t('templateFormModal.main.maxQueriesPerDay')}</label><input className="form-control" type="number" value={form.maxQueriesPerDay} onChange={e => set('maxQueriesPerDay', e.target.value)} placeholder={t('templateFormModal.main.unlimited')} min={0} /></div>
                <div className="col-md-6"><label className="form-label">{t('templateFormModal.main.maxQueriesPerMonth')}</label><input className="form-control" type="number" value={form.maxQueriesPerMonth} onChange={e => set('maxQueriesPerMonth', e.target.value)} placeholder={t('templateFormModal.main.unlimited')} min={0} /></div>
              </div>
            )}

            {/* Terms */}
            {tab === 'terms' && (
              <div className="row g-3">
                <div className="col-12">
                  <label className="form-label mb-0">
                    <i className="fa-solid fa-address-card me-2"></i>
                    {t('templateFormModal.main.contactHeading')}
                  </label>
                  <div className="form-text mb-2">{t('templateFormModal.main.contactHint')}</div>

                  <div className="form-check form-switch mb-2">
                    <input
                      className="form-check-input"
                      type="checkbox"
                      role="switch"
                      id="useGroupContact"
                      checked={!!form.useGroupContact}
                      onChange={e => set('useGroupContact', e.target.checked)}
                    />
                    <label className="form-check-label" htmlFor="useGroupContact">
                      {t('templateFormModal.main.useGroupContact')}
                    </label>
                  </div>

                  {form.useGroupContact ? (
                    contactInForce?.email || contactInForce?.formattedAddress ? (
                      <div className="card mb-3">
                        <div className="card-body py-2 small">
                          {contactInForce.name && (
                            <div className="fw-semibold">{contactInForce.name}</div>
                          )}
                          {contactInForce.organization && (
                            <div className={contactInForce.name ? 'text-muted' : 'fw-semibold'}>
                              {contactInForce.organization}
                            </div>
                          )}
                          {contactInForce.email && (
                            <div><i className="fa-solid fa-envelope me-2 text-muted"></i>{contactInForce.email}</div>
                          )}
                          {contactInForce.phone && (
                            <div><i className="fa-solid fa-phone me-2 text-muted"></i>{contactInForce.phone}</div>
                          )}
                          {contactInForce.formattedAddress && (
                            <div><i className="fa-solid fa-location-dot me-2 text-muted"></i>{contactInForce.formattedAddress}</div>
                          )}
                          <div className="text-muted mt-1">{t('templateFormModal.main.contactFromGroup')}</div>
                        </div>
                      </div>
                    ) : (
                      <div className="alert alert-warning py-2 small">
                        <i className="fa-solid fa-triangle-exclamation me-1"></i>
                        {t('templateFormModal.main.contactGroupEmpty')}
                      </div>
                    )
                  ) : (
                    <div className="row g-2 mb-3">
                      <div className="col-md-6">
                        <label className="form-label">{t('templateFormModal.main.contactFirstName')}</label>
                        <input className="form-control form-control-sm"
                               value={form.contactFirstName}
                               onChange={e => set('contactFirstName', e.target.value)} />
                      </div>
                      <div className="col-md-6">
                        <label className="form-label">{t('templateFormModal.main.contactLastName')}</label>
                        <input className="form-control form-control-sm"
                               value={form.contactLastName}
                               onChange={e => set('contactLastName', e.target.value)} />
                      </div>
                      <div className="col-md-6">
                        <label className="form-label">{t('templateFormModal.main.contactEmail')}</label>
                        <input type="email" className="form-control form-control-sm"
                               value={form.contactEmail}
                               onChange={e => set('contactEmail', e.target.value)} />
                      </div>
                      <div className="col-md-6">
                        <label className="form-label">{t('templateFormModal.main.contactPhone')}</label>
                        <input type="tel" className="form-control form-control-sm"
                               value={form.contactPhone}
                               onChange={e => set('contactPhone', e.target.value)} />
                        <div className="form-text">{t('templateFormModal.main.contactPhoneOptional')}</div>
                      </div>
                      <div className="col-md-6">
                        <label className="form-label">{t('templateFormModal.main.contactAddress')}</label>
                        <input className="form-control form-control-sm"
                               value={form.contactAddress}
                               onChange={e => set('contactAddress', e.target.value)} />
                      </div>
                      <div className="col-md-6">
                        <label className="form-label">{t('templateFormModal.main.contactCountry')}</label>
                        <CountrySelect
                          id="templateContactCountry"
                          value={form.contactCountry}
                          onChange={(country) => set('contactCountry', country)}
                        />
                      </div>
                      <div className="col-md-4">
                        <label className="form-label">{t('templateFormModal.main.contactCity')}</label>
                        <input className="form-control form-control-sm"
                               value={form.contactCity}
                               onChange={e => set('contactCity', e.target.value)} />
                      </div>
                      <div className="col-md-4">
                        <label className="form-label">{t('templateFormModal.main.contactStateProvince')}</label>
                        <input className="form-control form-control-sm"
                               value={form.contactStateProvince}
                               onChange={e => set('contactStateProvince', e.target.value)} />
                      </div>
                      <div className="col-md-4">
                        <label className="form-label">{t('templateFormModal.main.contactPostalCode')}</label>
                        <input className="form-control form-control-sm"
                               value={form.contactPostalCode}
                               onChange={e => set('contactPostalCode', e.target.value)} />
                      </div>
                    </div>
                  )}
                </div>

                <div className="col-12">
                  <div className="d-flex align-items-center justify-content-between mb-1 gap-2">
                    <label className="form-label mb-0">{t('templateFormModal.main.legalSections')}</label>
                    <div className="d-flex gap-2">
                      <select className="form-select form-select-sm" style={{ width: 260 }} value=""
                              onChange={e => {
                                const entry = STANDARD_LEGAL_SECTIONS.find(x => x.key === e.target.value);
                                if (entry) setSections([...form.legalSections, buildStandardSection(entry, form.legalSections)]);
                              }}>
                        <option value="">{t('templateFormModal.main.insertStandardSection')}</option>
                        {STANDARD_LEGAL_SECTIONS.map(entry => (
                          <option key={entry.key} value={entry.key}>{entry.title}</option>
                        ))}
                      </select>
                      <button type="button" className="btn btn-sm btn-outline-primary"
                              onClick={() => setSections([...form.legalSections, {
                                refKey: makeSectionRefKey(`section-${form.legalSections.length + 1}`, form.legalSections),
                                title: '',
                                clauses: [],
                              }])}>
                        <i className="fa-solid fa-plus me-1"></i>{t('templateFormModal.main.addLegalSection')}
                      </button>
                    </div>
                  </div>
                  <div className="form-text mb-2">{t('templateFormModal.main.legalSectionsHint')}</div>
                  {form.legalSections.length === 0 && (
                    <div className="text-muted small fst-italic mb-2">{t('templateFormModal.main.noLegalSections')}</div>
                  )}
                  {form.legalSections.map((sec, i) => (
                    <div className="card mb-2" key={sec.refKey || i}>
                      <div className="card-body py-2">
                        <div className="d-flex gap-2 align-items-center mb-2">
                          <span className="badge bg-secondary flex-shrink-0">{i + 1}</span>
                          <input className="form-control form-control-sm" value={sec.title || ''}
                                 placeholder={t('templateFormModal.main.legalSectionTitlePlaceholder')}
                                 onChange={e => updateSection(i, { title: e.target.value })} />
                          <button type="button" className="btn btn-sm btn-outline-secondary" disabled={i === 0}
                                  onClick={() => moveSection(i, -1)}>
                            <i className="fa-solid fa-arrow-up"></i>
                          </button>
                          <button type="button" className="btn btn-sm btn-outline-secondary" disabled={i === form.legalSections.length - 1}
                                  onClick={() => moveSection(i, 1)}>
                            <i className="fa-solid fa-arrow-down"></i>
                          </button>
                          <button type="button" className="btn btn-sm btn-outline-danger"
                                  onClick={() => setSections(form.legalSections.filter((_, j) => j !== i))}>
                            <i className="fa-solid fa-trash"></i>
                          </button>
                        </div>

                        {(sec.clauses || []).map((clause, j) => (
                          <div className="d-flex gap-2 align-items-start mb-2" key={clause.refKey || j}>
                            <span className="badge bg-light text-dark border flex-shrink-0 mt-1" style={{ minWidth: 44 }}>
                              {i + 1}.{j + 1}
                            </span>
                            <div className="flex-grow-1">
                              <ReferenceTextEditor
                                ref={el => { clauseRefs.current[clause.refKey] = el; }}
                                value={clause.text || ''}
                                labels={refLabels}
                                strings={refStrings}
                                minRows={Math.min(8, Math.max(2, String(clause.text || '').split('\n').length))}
                                placeholder={t('templateFormModal.main.legalClausePlaceholder')}
                                ariaLabel={t('templateFormModal.main.legalClauseLabel', { number: `${i + 1}.${j + 1}` })}
                                onChange={text => updateClause(i, j, { text })}
                              />
                              <div className="d-flex gap-2 align-items-center mt-1">
                                <select className="form-select form-select-sm" style={{ maxWidth: 280 }} value=""
                                        onChange={e => { insertReference(i, j, e.target.value); e.target.value = ''; }}>
                                  <option value="">{t('templateFormModal.main.insertReference')}</option>
                                  {refOptions
                                    .filter(option => option.refKey !== clause.refKey)
                                    .map(option => (
                                      <option key={option.refKey} value={option.refKey}>
                                        {option.kind === 'section'
                                          ? `${option.label} — ${option.title || t('templateFormModal.main.referenceUntitled')}`
                                          : `${option.label} — ${option.title || t('templateFormModal.main.referenceUntitled')}${option.text ? `: ${option.text}` : ''}`}
                                      </option>
                                    ))}
                                </select>
                                {containsReference(clause.text) && (
                                  <span className="text-muted small text-truncate">
                                    <span className="fw-semibold me-1">{t('templateFormModal.main.referencePreview')}</span>
                                    {resolveReferences(clause.text, refIndex)}
                                  </span>
                                )}
                              </div>
                            </div>
                            <div className="d-flex flex-column gap-1 flex-shrink-0">
                              <button type="button" className="btn btn-sm btn-outline-secondary py-0" disabled={j === 0}
                                      onClick={() => moveClause(i, j, -1)}>
                                <i className="fa-solid fa-arrow-up"></i>
                              </button>
                              <button type="button" className="btn btn-sm btn-outline-secondary py-0"
                                      disabled={j === (sec.clauses || []).length - 1}
                                      onClick={() => moveClause(i, j, 1)}>
                                <i className="fa-solid fa-arrow-down"></i>
                              </button>
                              <button type="button" className="btn btn-sm btn-outline-danger py-0"
                                      onClick={() => updateSection(i, { clauses: sec.clauses.filter((_, k) => k !== j) })}>
                                <i className="fa-solid fa-trash"></i>
                              </button>
                            </div>
                          </div>
                        ))}

                        <button type="button" className="btn btn-sm btn-outline-secondary"
                                onClick={() => updateSection(i, {
                                  clauses: [...(sec.clauses || []), {
                                    refKey: makeClauseRefKey(sec.refKey, form.legalSections),
                                    text: '',
                                  }],
                                })}>
                          <i className="fa-solid fa-plus me-1"></i>{t('templateFormModal.main.addLegalClause')}
                        </button>
                      </div>
                    </div>
                  ))}
                </div>

                <div className="col-12">
                  <div className="d-flex align-items-center justify-content-between mb-1">
                    <label className="form-label mb-0">{t('templateFormModal.main.subscriptionFields')}</label>
                    <button type="button" className="btn btn-sm btn-outline-primary"
                            onClick={() => set('subscriptionFields', [...form.subscriptionFields, { name: '', dataType: 'string', required: false, sortOrder: form.subscriptionFields.length }])}>
                      <i className="fa-solid fa-plus me-1"></i>{t('templateFormModal.main.addSubscriptionField')}
                    </button>
                  </div>
                  <div className="form-text mb-2">{t('templateFormModal.main.subscriptionFieldsHint')}</div>
                  {form.subscriptionFields.length === 0 && (
                    <div className="text-muted small fst-italic mb-2">{t('templateFormModal.main.noSubscriptionFields')}</div>
                  )}
                  {form.subscriptionFields.map((f, i) => {
                    const upd = (patch) => set('subscriptionFields', form.subscriptionFields.map((x, j) => j === i ? { ...x, ...patch } : x));
                    return (
                      <div className="card mb-2" key={i}>
                        <div className="card-body py-2">
                          <div className="row g-2 align-items-end">
                            <div className="col-md-4">
                              <label className="form-label" style={{ fontSize: 11 }}>{t('templateFormModal.main.fieldName')}</label>
                              <input className="form-control form-control-sm" value={f.name || ''}
                                     onChange={e => upd({ name: e.target.value })} />
                            </div>
                            <div className="col-md-3">
                              <label className="form-label" style={{ fontSize: 11 }}>{t('templateFormModal.main.fieldType')}</label>
                              <select className="form-select form-select-sm" value={f.dataType || 'string'}
                                      onChange={e => {
                                        const dataType = e.target.value;
                                        // The link belongs to the url type — drop it when the field stops being one.
                                        const stale = (f.dataType === 'url' && dataType !== 'url');
                                        upd({ dataType, ...(stale ? { defaultValue: null } : {}) });
                                      }}>
                                <option value="string">{t('templateFormModal.main.fieldTypes.string')}</option>
                                <option value="text">{t('templateFormModal.main.fieldTypes.text')}</option>
                                <option value="url">{t('templateFormModal.main.fieldTypes.url')}</option>
                                <option value="number">{t('templateFormModal.main.fieldTypes.number')}</option>
                                <option value="date">{t('templateFormModal.main.fieldTypes.date')}</option>
                                <option value="select">{t('templateFormModal.main.fieldTypes.select')}</option>
                                <option value="checkbox">{t('templateFormModal.main.fieldTypes.checkbox')}</option>
                              </select>
                            </div>
                            <div className="col-md-3">
                              <div className="form-check mt-3">
                                <input className="form-check-input" type="checkbox" id={`sf-req-${i}`}
                                       checked={!!f.required} onChange={e => upd({ required: e.target.checked })} />
                                <label className="form-check-label" htmlFor={`sf-req-${i}`} style={{ fontSize: 12 }}>
                                  {f.dataType === 'url'
                                    ? t('templateFormModal.main.fieldAcknowledgementRequired')
                                    : t('templateFormModal.main.fieldRequired')}
                                </label>
                              </div>
                            </div>
                            <div className="col-md-2 text-end">
                              <button type="button" className="btn btn-sm btn-outline-danger"
                                      onClick={() => set('subscriptionFields', form.subscriptionFields.filter((_, j) => j !== i).map((x, j) => ({ ...x, sortOrder: j })))}>
                                <i className="fa-solid fa-trash"></i>
                              </button>
                            </div>
                            {f.dataType === 'select' && (
                              <div className="col-12">
                                <label className="form-label" style={{ fontSize: 11 }}>{t('templateFormModal.main.fieldOptions')}</label>
                                <input className="form-control form-control-sm" value={f.enumValues || ''}
                                       placeholder={t('templateFormModal.main.fieldOptionsPlaceholder')}
                                       onChange={e => upd({ enumValues: e.target.value })} />
                              </div>
                            )}
                            {f.dataType === 'url' && (
                              <div className="col-12">
                                <label className="form-label" style={{ fontSize: 11 }}>{t('templateFormModal.main.fieldUrl')}</label>
                                <input className="form-control form-control-sm" type="url" value={f.defaultValue || ''}
                                       placeholder={t('templateFormModal.main.fieldUrlPlaceholder')}
                                       onChange={e => upd({ defaultValue: e.target.value })} />
                                <div className="form-text" style={{ fontSize: 11 }}>{t('templateFormModal.main.fieldUrlHint')}</div>
                              </div>
                            )}
                            {(f.dataType === 'string' || f.dataType === 'text') && (
                              <div className="col-md-4">
                                <label className="form-label" style={{ fontSize: 11 }}>{t('templateFormModal.main.fieldMaxLength')}</label>
                                <input className="form-control form-control-sm" type="number" min={1}
                                       value={f.maxLength ?? ''}
                                       placeholder={t('templateFormModal.main.fieldMaxLengthPlaceholder')}
                                       onChange={e => upd({ maxLength: e.target.value === '' ? null : parseInt(e.target.value, 10) })} />
                              </div>
                            )}
                            <div className="col-12">
                              <label className="form-label" style={{ fontSize: 11 }}>{t('templateFormModal.main.fieldDescription')}</label>
                              <input className="form-control form-control-sm" value={f.description || ''}
                                     onChange={e => upd({ description: e.target.value })} />
                            </div>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>

                <div className="col-12">
                  <div className="d-flex align-items-center justify-content-between mb-1">
                    <label className="form-label mb-0">{t('templateFormModal.main.userFields')}</label>
                    <button type="button" className="btn btn-sm btn-outline-primary"
                            onClick={() => set('userFields', [...form.userFields, { key: '', label: '', description: '', standard: false }])}>
                      <i className="fa-solid fa-plus me-1"></i>{t('templateFormModal.main.addUserField')}
                    </button>
                  </div>
                  <div className="form-text mb-2">{t('templateFormModal.main.userFieldsHint')}</div>
                  <div className="d-flex flex-wrap gap-3 mb-2">
                    {STANDARD_USER_FIELDS.map(({ key, label }) => {
                      const on = form.userFields.some(f => f.standard && f.key === key);
                      return (
                        <div className="form-check" key={key}>
                          <input className="form-check-input" type="checkbox" id={`uf-std-${key}`} checked={on}
                                 onChange={e => set('userFields', e.target.checked
                                   ? [...form.userFields, { key, label, standard: true }]
                                   : form.userFields.filter(f => !(f.standard && f.key === key)))} />
                          <label className="form-check-label" htmlFor={`uf-std-${key}`} style={{ fontSize: 12 }}>{label}</label>
                        </div>
                      );
                    })}
                  </div>
                  {form.userFields.map((f, i) => {
                    if (f.standard) return null;
                    const upd = (patch) => set('userFields', form.userFields.map((x, j) => j === i ? { ...x, ...patch } : x));
                    return (
                      <div className="card mb-2" key={i}>
                        <div className="card-body py-2">
                          <div className="row g-2 align-items-end">
                            <div className="col-md-4">
                              <label className="form-label" style={{ fontSize: 11 }}>{t('templateFormModal.main.userFieldLabel')}</label>
                              <input className="form-control form-control-sm" value={f.label || ''}
                                     placeholder={t('templateFormModal.main.userFieldLabelPlaceholder')}
                                     onChange={e => upd({ label: e.target.value })} />
                            </div>
                            <div className="col-md-7">
                              <label className="form-label" style={{ fontSize: 11 }}>{t('templateFormModal.main.fieldDescription')}</label>
                              <input className="form-control form-control-sm" value={f.description || ''}
                                     onChange={e => upd({ description: e.target.value })} />
                            </div>
                            <div className="col-md-1 text-end">
                              <button type="button" className="btn btn-sm btn-outline-danger"
                                      onClick={() => set('userFields', form.userFields.filter((_, j) => j !== i))}>
                                <i className="fa-solid fa-trash"></i>
                              </button>
                            </div>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={onHide} disabled={submitting}>{t('common.cancel')}</button>
            <button className="btn btn-primary" onClick={handleSubmit} disabled={submitting}>
              {submitting ? <><span className="spinner-border spinner-border-sm me-1"></span>{t('common.saving')}</> : isEditing ? t('templateFormModal.main.updateTemplate') : t('templateFormModal.main.createTemplate')}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default TemplateFormModal;
