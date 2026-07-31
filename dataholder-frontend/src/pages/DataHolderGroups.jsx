/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useRef } from 'react';
import ReactDOM from 'react-dom';
import { getGroupMembers, getGroupAdminTemplates, getGroupAdminSubscriptions } from '../services/api';
import Loading from '../components/Loading';
import AccessLevelBadge from '../components/AccessLevelBadge';
import SubscriptionStatusBadge from '../components/SubscriptionStatusBadge';
import Pagination from '../components/Pagination';
import usePagination from '../hooks/usePagination';
import toast from 'react-hot-toast';
import { useT } from '../i18n';

/**
 * Request type badge — mirrors the badge used on the (removed) templates page.
 */
const RequestTypeBadge = ({ requestType }) => {
  const { t } = useT();
  if (requestType.supportsExigent) {
    return <span className="badge bg-danger-subtle text-danger" style={{ fontSize: '11px' }}>⚡ {t('dataHolderGroups.requestTypeBadge.exigent')}</span>;
  }
  if (requestType.supportsConfidential) {
    return <span className="badge bg-warning-subtle text-warning" style={{ fontSize: '11px' }}>🔒 {t('dataHolderGroups.requestTypeBadge.confidential')}</span>;
  }
  return <span className="badge bg-light text-dark border" style={{ fontSize: '11px' }}>🌐 {t('dataHolderGroups.requestTypeBadge.standard')}</span>;
};

/**
 * A single label/value row inside a hover tooltip.
 */
const InfoDetail = ({ label, children }) => (
  <div style={{ display: 'flex', justifyContent: 'space-between', gap: '12px' }}>
    <span className="text-muted" style={{ whiteSpace: 'nowrap' }}>{label}</span>
    <span style={{ textAlign: 'right', wordBreak: 'break-word' }}>{children}</span>
  </div>
);

/**
 * Reusable hoverable info icon with a tooltip rendered through a portal with
 * viewport-fixed positioning, so it is never clipped by surrounding scrollable
 * tables. `children` is the tooltip content; `label` is the accessible name.
 */
const HoverInfo = ({ label, children }) => {
  const [coords, setCoords] = useState(null);
  const iconRef = useRef(null);

  const open = () => {
    const el = iconRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const half = 150;
    const left = Math.min(Math.max(r.left + r.width / 2, half + 8), window.innerWidth - half - 8);
    setCoords({ top: r.top, left });
  };
  const close = () => setCoords(null);

  return (
    <>
      <i
        ref={iconRef}
        className="fa-solid fa-circle-info text-primary"
        style={{ cursor: 'help', fontSize: '13px' }}
        tabIndex={0}
        onMouseEnter={open}
        onMouseLeave={close}
        onFocus={open}
        onBlur={close}
        aria-label={label}
      />
      {coords && ReactDOM.createPortal(
        <div
          role="tooltip"
          style={{
            position: 'fixed',
            top: coords.top,
            left: coords.left,
            transform: 'translate(-50%, calc(-100% - 8px))',
            zIndex: 2000,
            width: '300px',
            maxWidth: 'calc(100vw - 24px)',
            padding: '12px 14px',
            background: 'var(--bg-primary, #fff)',
            color: 'var(--text-primary)',
            border: '1px solid var(--border-primary)',
            borderRadius: '8px',
            boxShadow: '0 10px 30px rgba(0,0,0,0.2)',
            fontSize: '12px',
            textAlign: 'left',
            pointerEvents: 'none',
            whiteSpace: 'normal',
          }}
        >
          {children}
        </div>,
        document.body
      )}
    </>
  );
};

/**
 * Hoverable info icon showing the full details of a request type in a tooltip.
 */
const RequestTypeInfo = ({ requestType: rt }) => {
  const { t } = useT();
  const yesNo = (v) => (v ? t('common.yes') : t('common.no'));
  return (
    <HoverInfo label={t('dataHolderGroups.requestTypeInfo.title', { name: rt.name })}>
      <div className="d-flex align-items-center gap-2 mb-1">
        <RequestTypeBadge requestType={rt} />
        <strong style={{ fontSize: '13px' }}>{rt.name}</strong>
      </div>
      {rt.description && <div className="text-muted mb-2">{rt.description}</div>}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
        {rt.typeCode != null && <InfoDetail label={t('dataHolderGroups.requestTypeInfo.typeCode')}><code style={{ fontSize: '11px' }}>{rt.typeCode}</code></InfoDetail>}
        <InfoDetail label={t('dataHolderGroups.requestTypeInfo.accessLevel')}><AccessLevelBadge level={rt.accessLevel} /></InfoDetail>
        <InfoDetail label={t('dataHolderGroups.requestTypeInfo.confidential')}>{yesNo(rt.supportsConfidential)}</InfoDetail>
        <InfoDetail label={t('dataHolderGroups.requestTypeInfo.exigent')}>{yesNo(rt.supportsExigent)}</InfoDetail>
        <InfoDetail label={t('dataHolderGroups.requestTypeInfo.manualApproval')}>{yesNo(rt.requiresManualApproval !== false)}</InfoDetail>
        {rt.queryValueRegex && (
          <InfoDetail label={t('dataHolderGroups.requestTypeInfo.queryRegex')}><code style={{ fontSize: '11px' }}>{rt.queryValueRegex}</code></InfoDetail>
        )}
        {rt.customParameters && rt.customParameters.length > 0 && (
          <InfoDetail label={t('dataHolderGroups.requestTypeInfo.customParams')}>
            {rt.customParameters.map((p) => p.name).join(', ')}
          </InfoDetail>
        )}
      </div>
    </HoverInfo>
  );
};

/**
 * Hoverable info icon showing the details of the requestor group that holds a
 * subscription (name, code, type, organization, contact, address).
 */
const RequestorGroupInfo = ({ subscription: s }) => {
  const { t } = useT();
  const address = s.formattedAddress || [s.requestorAddress, s.requestorCity, s.requestorStateProvince, s.requestorPostalCode, s.requestorCountry].filter(Boolean).join(', ');
  return (
    <HoverInfo label={t('dataHolderGroups.requestorInfo.title', { name: s.requestorGroupName || '' })}>
      <div className="mb-1">
        <strong style={{ fontSize: '13px' }}>{s.requestorGroupName || '—'}</strong>
        {s.requestorGroupCode && <code className="ms-2" style={{ fontSize: '11px' }}>{s.requestorGroupCode}</code>}
      </div>
      {s.requestorDescription && <div className="text-muted mb-2">{s.requestorDescription}</div>}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
        {s.requestorGroupType && <InfoDetail label={t('dataHolderGroups.requestorInfo.type')}>{s.requestorGroupType}</InfoDetail>}
        {s.requestorOrganization && <InfoDetail label={t('dataHolderGroups.requestorInfo.organization')}>{s.requestorOrganization}</InfoDetail>}
        {(s.requestorFirstName || s.requestorLastName) && <InfoDetail label={t('dataHolderGroups.requestorInfo.contact')}>{`${s.requestorFirstName || ''} ${s.requestorLastName || ''}`.trim()}</InfoDetail>}
        {s.requestorContactEmail && <InfoDetail label={t('dataHolderGroups.requestorInfo.email')}>{s.requestorContactEmail}</InfoDetail>}
        {s.requestorPhone && <InfoDetail label={t('dataHolderGroups.requestorInfo.phone')}>{s.requestorPhone}</InfoDetail>}
        {address && <InfoDetail label={t('dataHolderGroups.requestorInfo.address')}>{address}</InfoDetail>}
        {s.requestorAgentId && <InfoDetail label={t('dataHolderGroups.requestorInfo.agentId')}><code style={{ fontSize: '11px' }}>{s.requestorAgentId}</code></InfoDetail>}
        {s.purpose && <InfoDetail label={t('dataHolderGroups.requestorInfo.purpose')}>{s.purpose}</InfoDetail>}
      </div>
    </HoverInfo>
  );
};

/**
 * A group's member data holders.
 */
const MembersSection = ({ members }) => {
  const { t } = useT();
  if (!members || members.length === 0) {
    return <p className="text-muted small mb-0">{t('dataHolderGroups.detail.noMembers')}</p>;
  }
  return (
    <div className="table-responsive">
      <table className="table table-sm align-middle mb-0">
        <thead>
          <tr>
            <th>{t('dataHolderGroups.members.name')}</th>
            <th>{t('dataHolderGroups.members.id')}</th>
            <th>{t('dataHolderGroups.members.contact')}</th>
            <th>{t('dataHolderGroups.members.url')}</th>
            <th>{t('common.status')}</th>
          </tr>
        </thead>
        <tbody>
          {members.map((m) => (
            <tr key={m.id ?? m.dataholderId}>
              <td>
                <strong>{m.name || '—'}</strong>
                {m.isSelf && (
                  <span className="badge bg-primary-subtle text-primary ms-2" style={{ fontSize: '10px' }}>
                    {t('dataHolderGroups.members.you')}
                  </span>
                )}
              </td>
              <td><code className="font-monospace small">{m.dataholderId}</code></td>
              <td className="small">{m.contactEmail || '—'}</td>
              <td className="small text-truncate" style={{ maxWidth: '220px' }}>
                {m.url ? <a href={m.url} target="_blank" rel="noreferrer">{m.url}</a> : '—'}
              </td>
              <td>
                {m.isActive
                  ? <span className="badge bg-success-subtle text-success">{t('dataHolderGroups.status.active')}</span>
                  : <span className="badge bg-secondary-subtle text-secondary">{t('dataHolderGroups.status.inactive')}</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

/**
 * A group's agreement templates, each with its request types.
 */
const TemplatesSection = ({ templates }) => {
  const { t } = useT();
  if (!templates || templates.length === 0) {
    return <p className="text-muted small mb-0">{t('dataHolderGroups.detail.noTemplates')}</p>;
  }
  return (
    <div className="table-responsive">
      <table className="table table-sm align-middle mb-0">
        <thead>
          <tr>
            <th>{t('dataHolderGroups.templates.name')}</th>
            <th>{t('dataHolderGroups.templates.templateId')}</th>
            <th>{t('dataHolderGroups.templates.requestTypes')}</th>
            <th>{t('common.status')}</th>
          </tr>
        </thead>
        <tbody>
          {templates.map((tpl, idx) => (
            <tr key={tpl.templateId || idx}>
              <td>
                <strong>{tpl.name}</strong>
                {tpl.shortDescription && <div className="text-muted small">{tpl.shortDescription}</div>}
              </td>
              <td><code className="font-monospace small">{tpl.templateId}</code></td>
              <td>
                {tpl.requestTypes && tpl.requestTypes.length > 0 ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    {tpl.requestTypes.map((rt, i) => (
                      <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
                        <span className="small">{rt.name}</span>
                        <AccessLevelBadge level={rt.accessLevel} />
                        {rt.requiresManualApproval !== false
                          ? <span className="badge bg-warning-subtle text-warning" style={{ fontSize: '10px' }}>{t('dataHolderGroups.templates.manual')}</span>
                          : <span className="badge bg-success-subtle text-success" style={{ fontSize: '10px' }}>{t('dataHolderGroups.templates.auto')}</span>}
                        <RequestTypeInfo requestType={rt} />
                      </div>
                    ))}
                  </div>
                ) : <span className="text-muted">—</span>}
              </td>
              <td>
                <span className={`badge ${tpl.isPublished ? 'bg-success-subtle text-success' : 'bg-secondary-subtle text-secondary'}`}>
                  {tpl.isPublished ? t('dataHolderGroups.published') : t('dataHolderGroups.draft')}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

/**
 * A group's requestor-group subscriptions.
 */
const SubscriptionsSection = ({ subscriptions }) => {
  const { t } = useT();
  if (!subscriptions || subscriptions.length === 0) {
    return <p className="text-muted small mb-0">{t('dataHolderGroups.detail.noSubscriptions')}</p>;
  }
  return (
    <div className="table-responsive">
      <table className="table table-sm align-middle mb-0">
        <thead>
          <tr>
            <th>{t('dataHolderGroups.subscriptions.requestorGroup')}</th>
            <th>{t('dataHolderGroups.subscriptions.template')}</th>
            <th>{t('common.status')}</th>
          </tr>
        </thead>
        <tbody>
          {subscriptions.map((s, idx) => (
            <tr key={s.id ?? s.requestId ?? idx}>
              <td>
                <div className="d-flex align-items-center gap-2">
                  <strong>{s.requestorGroupName || '—'}</strong>
                  <RequestorGroupInfo subscription={s} />
                </div>
                {s.requestorGroupCode && <div className="text-muted small"><code>{s.requestorGroupCode}</code></div>}
              </td>
              <td className="small">{s.templateName || '—'}</td>
              <td><SubscriptionStatusBadge status={s.status} size="small" /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

/**
 * Reusable group accordion: a table of data holder groups, each row expandable
 * to reveal tab-specific detail. `countColumns` render the centered count
 * badge columns; `renderExpanded(group)` renders the expanded panel.
 */
const GroupsAccordion = ({ groups, countColumns, renderExpanded, itemLabel }) => {
  const { t } = useT();
  const [expanded, setExpanded] = useState(null);
  const pager = usePagination(groups);
  const keyOf = (g) => `${g._groupAdminId ?? 'x'}:${g.groupId}`;
  const colCount = 4 + countColumns.length;

  if (!groups || groups.length === 0) {
    return <div className="table-empty"><p>{t('dataHolderGroups.emptyState')}</p></div>;
  }

  return (
    <>
      <table className="table table-hover align-middle">
        <thead>
          <tr>
            <th style={{ width: '40px' }}></th>
            <th>{t('dataHolderGroups.table.group')}</th>
            <th>{t('dataHolderGroups.table.groupAdmin')}</th>
            {countColumns.map((c, i) => <th key={i} className="text-center">{c.header}</th>)}
            <th>{t('common.status')}</th>
          </tr>
        </thead>
        <tbody>
          {pager.pageItems.map((g) => {
            const key = keyOf(g);
            const isOpen = expanded === key;
            return (
              <React.Fragment key={key}>
                <tr onClick={() => setExpanded(isOpen ? null : key)} style={{ cursor: 'pointer' }}>
                  <td className="text-center">
                    <i className={`fa-solid ${isOpen ? 'fa-chevron-down' : 'fa-chevron-right'} text-muted`}></i>
                  </td>
                  <td>
                    <strong>{g.groupName}</strong>
                    {g.description && <div className="text-muted small">{g.description}</div>}
                  </td>
                  <td className="small text-muted">{g._groupAdminName || '—'}</td>
                  {countColumns.map((c, i) => <td key={i} className="text-center">{c.render(g)}</td>)}
                  <td>
                    {g.isActive
                      ? <span className="badge bg-success-subtle text-success">{t('dataHolderGroups.status.active')}</span>
                      : <span className="badge bg-secondary-subtle text-secondary">{t('dataHolderGroups.status.inactive')}</span>}
                  </td>
                </tr>
                {isOpen && (
                  <tr>
                    <td colSpan={colCount} style={{ backgroundColor: 'var(--bg-secondary)' }}>
                      <div style={{ padding: '8px 8px 16px' }}>{renderExpanded(g)}</div>
                    </td>
                  </tr>
                )}
              </React.Fragment>
            );
          })}
        </tbody>
      </table>
      <Pagination
        page={pager.page}
        pageSize={pager.pageSize}
        totalItems={pager.totalItems}
        onPageChange={pager.setPage}
        onPageSizeChange={pager.setPageSize}
        itemLabel={itemLabel}
      />
    </>
  );
};

/**
 * Data Holder Groups — read-only overview of the groups this data holder
 * belongs to. Two tabs: group membership (members + agreement templates) and
 * requestor-group subscriptions, both organized by data holder group. Data is
 * aggregated from the Data Holder Group Admin(s) this data holder is connected to.
 */
const DataHolderGroups = () => {
  const { t } = useT();
  const [groups, setGroups] = useState([]);
  const [templates, setTemplates] = useState([]);
  const [subscriptions, setSubscriptions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [configured, setConfigured] = useState(true);
  const [activeTab, setActiveTab] = useState('membership'); // 'membership' | 'subscriptions'

  const loadData = async () => {
    try {
      setLoading(true);
      const [groupsRes, templatesRes, subsRes] = await Promise.all([
        getGroupMembers(),
        getGroupAdminTemplates(),
        getGroupAdminSubscriptions(),
      ]);
      setConfigured(groupsRes.data.configured !== false);
      setGroups(groupsRes.data.groups || []);
      setTemplates(templatesRes.data.templates || []);
      setSubscriptions(subsRes.data.subscriptions || []);
    } catch (error) {
      console.error('Failed to load data holder groups:', error);
      toast.error(t('dataHolderGroups.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  /*
   * Correlate a related record (template/subscription) to a group by data holder
   * group id and, when both are tagged, the same Group Admin connection.
   */
  const relatesToGroup = (rec, g) => {
    if (rec.dataHolderGroupId == null) return false;
    const sameGroup = String(rec.dataHolderGroupId) === String(g.groupId);
    const sameAdmin = rec._groupAdminId == null || g._groupAdminId == null
      || String(rec._groupAdminId) === String(g._groupAdminId);
    return sameGroup && sameAdmin;
  };
  const templatesForGroup = (g) => templates.filter((tpl) => relatesToGroup(tpl, g));
  const subscriptionsForGroup = (g) => subscriptions.filter((s) => relatesToGroup(s, g));

  if (loading) return <Loading message={t('dataHolderGroups.loadingMessage')} />;

  if (!configured) {
    return (
      <div>
        <div className="mb-4">
          <h2 className="h3 fw-bold mb-1">{t('dataHolderGroups.title')}</h2>
        </div>
        <div className="card" style={{ padding: '40px', textAlign: 'center' }}>
          <div style={{ fontSize: '48px', marginBottom: '12px' }}>🔗</div>
          <h3>{t('dataHolderGroups.notConnected.heading')}</h3>
          <p className="text-muted">
            {t('dataHolderGroups.notConnected.descriptionBefore')} <a href="/settings">{t('dataHolderGroups.notConnected.settingsLink')}</a> {t('dataHolderGroups.notConnected.descriptionAfter')}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-4 d-flex justify-content-between align-items-center">
        <div>
          <h2 className="h3 fw-bold mb-1">{t('dataHolderGroups.title')}</h2>
          <p className="text-muted mb-0">{t('dataHolderGroups.subtitle')}</p>
        </div>
        <button className="btn btn-outline-secondary" onClick={loadData}>
          <i className="fa-solid fa-arrows-rotate"></i> {t('common.refresh')}
        </button>
      </div>

      <ul className="nav nav-tabs mb-3">
        <li className="nav-item">
          <button type="button" className={`nav-link ${activeTab === 'membership' ? 'active' : ''}`} onClick={() => setActiveTab('membership')}>
            <i className="fa-solid fa-users-line me-1"></i>{t('dataHolderGroups.tabs.membership')}
          </button>
        </li>
        <li className="nav-item">
          <button type="button" className={`nav-link ${activeTab === 'subscriptions' ? 'active' : ''}`} onClick={() => setActiveTab('subscriptions')}>
            <i className="fa-solid fa-file-signature me-1"></i>{t('dataHolderGroups.tabs.subscriptions')}
          </button>
        </li>
      </ul>

      <div className="card">
        <div className="table-responsive">
          {activeTab === 'membership' ? (
            <GroupsAccordion
              groups={groups}
              itemLabel={t('dataHolderGroups.itemLabel')}
              countColumns={[
                { header: t('dataHolderGroups.table.members'), render: (g) => <span className="badge bg-info-subtle text-info">{g.members?.length ?? g.memberCount ?? 0}</span> },
                { header: t('dataHolderGroups.table.templates'), render: (g) => <span className="badge bg-secondary-subtle text-secondary">{templatesForGroup(g).length}</span> },
              ]}
              renderExpanded={(g) => (
                <>
                  <div className="fw-semibold text-uppercase small text-muted mb-2">
                    <i className="fa-solid fa-building me-1"></i>{t('dataHolderGroups.detail.membersHeading')}
                  </div>
                  <MembersSection members={g.members} />

                  <div className="fw-semibold text-uppercase small text-muted mt-4 mb-2">
                    <i className="fa-solid fa-file-lines me-1"></i>{t('dataHolderGroups.detail.templatesHeading')}
                  </div>
                  <TemplatesSection templates={templatesForGroup(g)} />
                </>
              )}
            />
          ) : (
            <GroupsAccordion
              groups={groups}
              itemLabel={t('dataHolderGroups.itemLabel')}
              countColumns={[
                { header: t('dataHolderGroups.table.subscriptions'), render: (g) => <span className="badge bg-primary-subtle text-primary">{subscriptionsForGroup(g).length}</span> },
              ]}
              renderExpanded={(g) => (
                <>
                  <div className="fw-semibold text-uppercase small text-muted mb-2">
                    <i className="fa-solid fa-file-signature me-1"></i>{t('dataHolderGroups.detail.subscriptionsHeading')}
                  </div>
                  <SubscriptionsSection subscriptions={subscriptionsForGroup(g)} />
                </>
              )}
            />
          )}
        </div>
      </div>
    </div>
  );
};

export default DataHolderGroups;
