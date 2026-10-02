/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useRef, useCallback } from 'react';
import api from '../services/api';
import { useT } from '../i18n';

const POLL_MS = 60000;

const RdrsInbox = () => {
  const { t } = useT();
  const [open, setOpen] = useState(false);
  const [mailbox, setMailbox] = useState(null);
  const [messages, setMessages] = useState([]);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const boxRef = useRef(null);

  // Unread count drives the badge, so it is refreshed even while the panel is closed.
  const refreshMailbox = useCallback(async () => {
    try {
      const { data } = await api.get('/api/rdrs/mailbox');
      setMailbox(data);
    } catch {
      setMailbox(null);
    }
  }, []);

  useEffect(() => {
    refreshMailbox();
    const id = setInterval(refreshMailbox, POLL_MS);
    return () => clearInterval(id);
  }, [refreshMailbox]);

  const loadMessages = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.get('/api/rdrs/mailbox/messages', { params: { page: 0, size: 50 } });
      setMessages(data.items || []);
      setMailbox(m => (m ? { ...m, unreadCount: data.unreadCount } : m));
    } catch (e) {
      setError(e?.response?.data?.error || t('rdrsInbox.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (open) { loadMessages(); setSelected(null); }
  }, [open, loadMessages]);

  useEffect(() => {
    if (!open) return;
    const away = (e) => { if (boxRef.current && !boxRef.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', away);
    return () => document.removeEventListener('mousedown', away);
  }, [open]);

  // Opens a message and marks it read, updating the badge without a refetch.
  const openMessage = async (id) => {
    setLoading(true);
    try {
      const { data } = await api.get(`/api/rdrs/mailbox/messages/${id}`);
      setSelected(data);
      setMessages(list => list.map(m => (m.id === id ? { ...m, read: true } : m)));
      setMailbox(m => (m && m.unreadCount > 0 ? { ...m, unreadCount: m.unreadCount - 1 } : m));
    } catch (e) {
      setError(e?.response?.data?.error || t('rdrsInbox.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  const markAllRead = async () => {
    try {
      await api.post('/api/rdrs/mailbox/messages/read-all');
      setMessages(list => list.map(m => ({ ...m, read: true })));
      setMailbox(m => (m ? { ...m, unreadCount: 0 } : m));
    } catch { /* badge refreshes on the next poll */ }
  };

  const unread = mailbox?.unreadCount || 0;
  const when = (iso) => (iso ? new Date(iso).toLocaleString() : '');
  const senderName = (raw) => {
    if (!raw) return t('rdrsInbox.unknownSender');
    const m = raw.match(/^\s*"?([^"<]+?)"?\s*<.*>\s*$/);
    return (m ? m[1] : raw).trim();
  };

  return (
    <div className="position-relative" ref={boxRef}>
      <button type="button" className="btn btn-link nav-link position-relative px-2"
        onClick={() => setOpen(o => !o)} aria-label={t('rdrsInbox.title')} title={t('rdrsInbox.title')}>
        <i className="bi bi-envelope" style={{ fontSize: '1.1rem' }}></i>
        {unread > 0 && (
          <span className="position-absolute translate-middle badge rounded-pill bg-danger"
            style={{ top: 6, left: '80%', fontSize: 10 }}>
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="border rounded shadow bg-body position-absolute end-0 mt-1"
          style={{ zIndex: 1060, width: 420, maxWidth: '90vw' }}>
          <div className="d-flex align-items-center px-3 py-2 border-bottom">
            <span className="fw-semibold small">
              <i className="bi bi-envelope me-1"></i>{t('rdrsInbox.title')}
            </span>
            {unread > 0 && (
              <button type="button" className="btn btn-link btn-sm ms-auto p-0" style={{ fontSize: 12 }}
                onClick={markAllRead}>{t('rdrsInbox.markAllRead')}</button>
            )}
          </div>

          {mailbox?.address && (
            <div className="px-3 py-1 border-bottom text-muted" style={{ fontSize: 11 }}>
              {mailbox.address}
              {!mailbox.registered && (
                <span className="badge bg-warning text-dark ms-2" style={{ fontSize: 9 }}>
                  {t('rdrsInbox.notRegistered')}
                </span>
              )}
            </div>
          )}

          <div style={{ maxHeight: 420, overflowY: 'auto' }}>
            {loading && <div className="p-3 text-center text-muted small">
              <span className="spinner-border spinner-border-sm me-2"></span>{t('rdrsInbox.loading')}
            </div>}
            {error && <div className="p-3 small text-danger">{error}</div>}

            {!loading && !error && selected && (
              <div className="p-3">
                <button type="button" className="btn btn-link btn-sm p-0 mb-2" onClick={() => setSelected(null)}>
                  <i className="bi bi-arrow-left me-1"></i>{t('rdrsInbox.back')}
                </button>
                <div className="fw-semibold small">{selected.subject || t('rdrsInbox.noSubject')}</div>
                <div className="text-muted" style={{ fontSize: 11 }}>
                  {senderName(selected.sender)} · {when(selected.sentAt || selected.receivedAt)}
                </div>
                <hr className="my-2" />
                {selected.bodyPlain
                  ? <pre className="small mb-0" style={{ whiteSpace: 'pre-wrap', fontFamily: 'inherit' }}>{selected.bodyPlain}</pre>
                  : <div className="text-muted small">{t('rdrsInbox.noBody')}</div>}
              </div>
            )}

            {!loading && !error && !selected && messages.length === 0 && (
              <div className="p-4 text-center text-muted small">
                <i className="bi bi-inbox d-block mb-2" style={{ fontSize: '1.5rem' }}></i>
                {t('rdrsInbox.empty')}
              </div>
            )}

            {!loading && !error && !selected && messages.map(m => (
              <button key={m.id} type="button"
                className={`w-100 text-start border-0 border-bottom px-3 py-2 ${m.read ? 'bg-body' : 'bg-primary-subtle'}`}
                onClick={() => openMessage(m.id)}>
                <div className="d-flex align-items-center">
                  <span className={`small text-truncate ${m.read ? '' : 'fw-semibold'}`} style={{ maxWidth: '65%' }}>
                    {senderName(m.sender)}
                  </span>
                  <span className="text-muted ms-auto" style={{ fontSize: 10 }}>
                    {when(m.sentAt || m.receivedAt)}
                  </span>
                </div>
                <div className={`small text-truncate ${m.read ? 'text-muted' : ''}`}>
                  {m.subject || t('rdrsInbox.noSubject')}
                </div>
                {m.preview && <div className="text-muted text-truncate" style={{ fontSize: 11 }}>{m.preview}</div>}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default RdrsInbox;
