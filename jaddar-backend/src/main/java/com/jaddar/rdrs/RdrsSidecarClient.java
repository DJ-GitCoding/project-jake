/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.rdrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.config.JaddarProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * Client for the RDRS sidecar, which drives ICANN's browser-only login.
 *
 * <p>The sidecar exists because ICANN's sign-in cannot be replayed over plain HTTP:
 * it runs through Okta, a SAML round trip and a two-step Apache Tapestry form whose
 * state is server-signed, and it may raise a multi-factor challenge. A real browser
 * absorbs all of that; see rdrs-sidecar/app.py.
 *
 * <p>The user's ICANN password passes through here on its way to the sidecar. It is
 * never logged, never stored, and never written into an exception message.
 */
@Component
@Slf4j
public class RdrsSidecarClient {

    /** Must comfortably exceed the sidecar's own login timeout plus its MFA wait. */
    private static final Duration TIMEOUT = Duration.ofMinutes(6);

    private final WebClient sidecar;
    private final JaddarProperties props;
    private final ObjectMapper objectMapper;

    public RdrsSidecarClient(@Qualifier("rdrsSidecarWebClient") WebClient sidecar,
                             JaddarProperties props,
                             ObjectMapper objectMapper) {
        this.sidecar = sidecar;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        JaddarProperties.Rdrs cfg = props.getRdrs();
        return cfg.isEnabled()
                && cfg.getSidecarUrl() != null && !cfg.getSidecarUrl().isBlank()
                && cfg.getSidecarSecret() != null && !cfg.getSidecarSecret().isBlank();
    }

    /** Begin a login. May come back asking for a multi-factor code. */
    public RdrsDto.SidecarResponse login(String email, String password) {
        return call("/login", Map.of("email", email, "password", password));
    }

    /** Answer a multi-factor challenge raised by an earlier {@link #login}. */
    public RdrsDto.SidecarResponse mfa(String challengeId, String code) {
        return call("/mfa", Map.of("challengeId", challengeId, "code", code));
    }

    /** Abandon a login that is parked on a challenge, so its browser is torn down. */
    public void abandon(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) return;
        try {
            call("/logout", Map.of("challengeId", challengeId));
        } catch (RuntimeException e) {
            // Best effort — the sidecar sweeps abandoned logins on its own.
            log.debug("Could not abandon RDRS login challenge: {}", e.getMessage());
        }
    }

    private RdrsDto.SidecarResponse call(String path, Map<String, String> body) {
        if (!isConfigured()) {
            throw new RdrsException("The ICANN RDRS integration is not configured on this server.", 503);
        }
        try {
            RdrsDto.SidecarResponse response = sidecar.post()
                    .uri(path)
                    .header("X-Rdrs-Secret", props.getRdrs().getSidecarSecret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(RdrsDto.SidecarResponse.class)
                    .block(TIMEOUT);

            if (response == null) {
                throw new RdrsException("The ICANN sign-in service did not respond.", 502);
            }
            if ("error".equals(response.getStatus())) {
                throw fromSidecarError(response);
            }
            return response;

        } catch (WebClientResponseException e) {
            // Deliberately not logging the response body: the request carried a password.
            log.warn("RDRS sidecar {} returned {}", path, e.getStatusCode());
            if (e.getStatusCode().value() == 410) {
                throw new RdrsException("That sign-in attempt is no longer active. Please start again.", 410);
            }
            /*
             * The sidecar reports a rejected password as a non-2xx carrying the reason ICANN
             * gave. Without reading that body every mistyped password would reach the
             * requestor as "temporarily unavailable", sending them to look for an outage
             * instead of at what they typed.
             */
            RdrsDto.SidecarResponse failure = parseError(e.getResponseBodyAsString());
            if (failure != null && "error".equals(failure.getStatus())) {
                throw fromSidecarError(failure);
            }
            throw new RdrsException("ICANN sign-in is temporarily unavailable. Please try again.", 502);
        } catch (WebClientRequestException e) {
            log.error("RDRS sidecar unreachable at {}", props.getRdrs().getSidecarUrl());
            throw new RdrsException("ICANN sign-in is temporarily unavailable. Please try again later.", 503);
        }
    }

    /**
     * Turn a sidecar error into one the requestor can act on. The sidecar's message is
     * ICANN's own wording and is already written for end users, so it passes through.
     *
     * <p>A wrong password is the requestor's to fix, not an outage, so it answers 422
     * rather than 502 and never 401, which the SSR proxy reads as the Jaddar session
     * dying and would sign them out of Jaddar entirely.
     */
    private RdrsException fromSidecarError(RdrsDto.SidecarResponse response) {
        String message = response.getError() != null && !response.getError().isBlank()
                ? response.getError() : "ICANN sign-in failed.";
        if ("invalid_credentials".equals(response.getCode())) {
            return new RdrsException(message, 422, RdrsException.SIGN_IN_REJECTED);
        }
        if ("rate_limited".equals(response.getCode())) {
            return new RdrsException(message, 429, RdrsException.SIGN_IN_REJECTED);
        }
        return new RdrsException(message, 502);
    }

    private RdrsDto.SidecarResponse parseError(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return objectMapper.readValue(body, RdrsDto.SidecarResponse.class);
        } catch (Exception e) {
            return null;
        }
    }
}
