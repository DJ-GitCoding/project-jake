/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useT } from '../i18n';

const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'touchstart', 'scroll', 'wheel'];
const ACTIVITY_THROTTLE_MS = 5000;
const SHARED_KEY = 'jaddar:lastActivity';
const EXPIRED_URL = '/login?expired=1';

const readShared = () => {
  try {
    const raw = window.localStorage.getItem(SHARED_KEY);
    const parsed = raw ? Number(raw) : 0;
    return Number.isFinite(parsed) ? parsed : 0;
  } catch {
    return 0;
  }
};

const writeShared = (at) => {
  try {
    window.localStorage.setItem(SHARED_KEY, String(at));
  } catch {
    /* private mode */
  }
};

const SessionTimeout = ({ idleMinutes = 15, warnSeconds = 120, heartbeatSeconds = 120 }) => {
  const { t } = useT();
  const idleMs = Math.max(1, idleMinutes) * 60 * 1000;
  const warnMs = Math.min(Math.max(1, warnSeconds) * 1000, idleMs - 1000);

  const lastActivity = useRef(Date.now());
  const lastRecorded = useRef(0);
  const lastHeartbeat = useRef(Date.now());
  const activitySinceHeartbeat = useRef(true);
  const endingSession = useRef(false);
  const [remainingMs, setRemainingMs] = useState(idleMs);

  const endSession = useCallback(() => {
    if (endingSession.current) return;
    endingSession.current = true;
    window.location.href = EXPIRED_URL;
  }, []);

  const sendHeartbeat = useCallback(async () => {
    lastHeartbeat.current = Date.now();
    activitySinceHeartbeat.current = false;
    try {
      const response = await fetch('/session/heartbeat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      });
      if (response.status === 401) endSession();
    } catch {
      /* offline: the next beat retries */
    }
  }, [endSession]);

  const recordActivity = useCallback((at = Date.now(), { share = true } = {}) => {
    if (at <= lastActivity.current) return;
    lastActivity.current = at;
    activitySinceHeartbeat.current = true;
    if (share && at - lastRecorded.current >= ACTIVITY_THROTTLE_MS) {
      lastRecorded.current = at;
      writeShared(at);
    }
  }, []);

  useEffect(() => {
    const shared = readShared();
    if (shared > lastActivity.current) lastActivity.current = shared;

    const onActivity = () => recordActivity();
    ACTIVITY_EVENTS.forEach((name) =>
      window.addEventListener(name, onActivity, { passive: true })
    );

    const onStorage = (event) => {
      if (event.key !== SHARED_KEY || !event.newValue) return;
      recordActivity(Number(event.newValue), { share: false });
    };
    window.addEventListener('storage', onStorage);

    const onVisibility = () => {
      if (document.visibilityState !== 'visible') return;
      const shared = readShared();
      if (shared > lastActivity.current) lastActivity.current = shared;
      sendHeartbeat();
    };
    document.addEventListener('visibilitychange', onVisibility);

    return () => {
      ACTIVITY_EVENTS.forEach((name) => window.removeEventListener(name, onActivity));
      window.removeEventListener('storage', onStorage);
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [recordActivity, sendHeartbeat]);

  useEffect(() => {
    const tick = setInterval(() => {
      const idleFor = Date.now() - lastActivity.current;
      const left = idleMs - idleFor;
      setRemainingMs(left);

      if (left <= 0) {
        endSession();
        return;
      }
      if (
        activitySinceHeartbeat.current &&
        Date.now() - lastHeartbeat.current >= heartbeatSeconds * 1000
      ) {
        sendHeartbeat();
      }
    }, 1000);
    return () => clearInterval(tick);
  }, [idleMs, heartbeatSeconds, endSession, sendHeartbeat]);

  const staySignedIn = () => {
    recordActivity(Date.now());
    setRemainingMs(idleMs);
    sendHeartbeat();
  };

  if (remainingMs > warnMs) return null;

  const seconds = Math.max(0, Math.ceil(remainingMs / 1000));

  return (
    <>
      <div className="modal show d-block" role="dialog" aria-modal="true">
        <div className="modal-dialog modal-dialog-centered modal-sm">
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">
                <i className="fas fa-clock text-warning me-2"></i>
                {t('session.timeout.title')}
              </h5>
            </div>
            <div className="modal-body">
              <p className="mb-2">{t('session.timeout.body')}</p>
              <div className="display-6 text-center fw-semibold" aria-live="polite">
                {Math.floor(seconds / 60)}:{String(seconds % 60).padStart(2, '0')}
              </div>
            </div>
            <div className="modal-footer">
              <a className="btn btn-outline-secondary" href="/logout">
                {t('session.timeout.signOut')}
              </a>
              <button type="button" className="btn btn-primary" onClick={staySignedIn}>
                {t('session.timeout.stay')}
              </button>
            </div>
          </div>
        </div>
      </div>
      <div className="modal-backdrop fade show"></div>
    </>
  );
};

export default SessionTimeout;
