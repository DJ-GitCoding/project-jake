/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.rdrs;

import com.jaddar.config.JaddarProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds each requestor's live RDRS session, keyed by their Keycloak subject.
 *
 * <p>Deliberately in memory and deliberately thin: the user's ICANN password is used
 * once to log in and never stored, and only the resulting session cookie plus its
 * expiry live here. Nothing is persisted, so a restart simply asks users to log in
 * to RDRS again.
 *
 * <p>Entries expire from the Okta token's own {@code exp}, capped by
 * {@code jaddar.rdrs.session-max-seconds}. There is no scheduler: expired entries are
 * swept lazily on read and on write, which is enough because the map only ever holds
 * one entry per signed-in requestor.
 *
 * <p><b>Single-instance only.</b> jaddar-backend runs as one container today. If it is
 * ever scaled out, this must move to Redis or a shared cache, or a user's RDRS session
 * will appear to vanish whenever they are routed to a different replica — the same
 * caveat jaddar-frontend's server-side session map already carries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RdrsSessionStore {

    private final JaddarProperties props;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** A logged-in RDRS session. The cookie never leaves the backend. */
    @Getter
    public static class Session {
        private final String cookie;
        private final String apiBaseUrl;
        private final Instant expiresAt;
        private volatile Map<String, Object> profile;

        Session(String cookie, String apiBaseUrl, Instant expiresAt) {
            this.cookie = cookie;
            this.apiBaseUrl = apiBaseUrl;
            this.expiresAt = expiresAt;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public void setProfile(Map<String, Object> profile) {
            this.profile = profile;
        }
    }

    /**
     * Store a freshly established session.
     *
     * @param tokenExpiresAt epoch seconds from the Okta token, or null if unreadable
     */
    public Session put(String subject, String cookie, String apiBaseUrl, Long tokenExpiresAt) {
        sweep();
        Instant cap = Instant.now().plusSeconds(props.getRdrs().getSessionMaxSeconds());
        Instant expiry = tokenExpiresAt != null ? Instant.ofEpochSecond(tokenExpiresAt) : cap;
        // Never trust a token that claims to outlive the configured cap.
        if (expiry.isAfter(cap)) {
            expiry = cap;
        }
        Session session = new Session(cookie, apiBaseUrl, expiry);
        sessions.put(subject, session);
        log.info("Opened RDRS session for subject {} (expires {})", abbreviate(subject), expiry);
        return session;
    }

    /** The caller's live session, or empty if absent or expired. */
    public Optional<Session> get(String subject) {
        Session session = sessions.get(subject);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired()) {
            sessions.remove(subject, session);
            log.info("RDRS session for subject {} expired", abbreviate(subject));
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void remove(String subject) {
        if (sessions.remove(subject) != null) {
            log.info("Closed RDRS session for subject {}", abbreviate(subject));
        }
    }

    private void sweep() {
        sessions.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    /** Keycloak subjects are UUIDs; log only a prefix so records stay non-identifying. */
    private static String abbreviate(String subject) {
        if (subject == null || subject.length() <= 8) return "unknown";
        return subject.substring(0, 8) + "…";
    }
}
