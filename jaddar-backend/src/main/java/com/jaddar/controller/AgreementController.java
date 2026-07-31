/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.controller;

import com.jaddar.dto.AgreementSummary;
import com.jaddar.dto.UserAgreementsResponse;
import com.jaddar.exception.ApiException;
import com.jaddar.service.AgreementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Agreement management endpoints. Mirrors backend/routers/agreement_routes.py.
 *
 * <p>These routes let the frontend query the user's available agreements based on
 * their Keycloak group memberships. The user's bearer token is forwarded to the
 * Requestor Manager, which validates it and returns the matching agreements.
 *
 * <p>The Python extracted the bearer token from the {@code Authorization} header via
 * an {@code HTTPBearer} security scheme. Here the Keycloak access token is already
 * validated by the resource server; we reconstruct the {@code Bearer <token>} string
 * from the validated {@link Jwt} ({@link Jwt#getTokenValue()}) to forward downstream.
 */
@Slf4j
@RestController
@RequestMapping("/api/agreements")
@RequiredArgsConstructor
public class AgreementController {

    private final AgreementService agreementService;

    /** Reconstruct the "Bearer <raw-token>" header value forwarded to the Requestor Manager. */
    private String bearer(Jwt jwt) {
        return "Bearer " + jwt.getTokenValue();
    }

    /**
     * Get all agreements available to the current user (with group information).
     * Mirrors {@code GET /api/agreements}.
     */
    @GetMapping
    public UserAgreementsResponse getUserAgreements(@AuthenticationPrincipal Jwt jwt) {
        String token = bearer(jwt);
        log.info("Fetching agreements for authenticated user");
        try {
            UserAgreementsResponse result = agreementService.getUserAgreements(token);

            log.info("Returning {} agreements for user", result.getAgreements().size());
            if (result.getAgreements().isEmpty()) {
                log.warn("Agreement server returned 0 agreements. Warnings from agreement server: {}. "
                        + "Keycloak groups: {}", result.getWarnings(), result.getKeycloakGroups());
            }
            return result;
        } catch (Exception e) {
            log.error("Error fetching user agreements: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch agreements.");
        }
    }

    /**
     * Get a flat list of agreements available to the current user (no group info).
     * Mirrors {@code GET /api/agreements/list}.
     */
    @GetMapping("/list")
    public List<AgreementSummary> getAgreementsList(@AuthenticationPrincipal Jwt jwt) {
        String token = bearer(jwt);
        try {
            UserAgreementsResponse result = agreementService.getUserAgreements(token);
            return result.getAgreements();
        } catch (Exception e) {
            log.error("Error fetching agreements list: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch agreements.");
        }
    }

    /**
     * Get agreements for specific group names. Request body is a JSON array of group
     * names (e.g. {@code ["REG-001", "APNIC"]}).
     * Mirrors {@code POST /api/agreements/by-groups}.
     */
    @PostMapping("/by-groups")
    public UserAgreementsResponse getAgreementsByGroups(
            @RequestBody List<String> groupNames,
            @AuthenticationPrincipal Jwt jwt) {
        String token = bearer(jwt);
        log.info("Fetching agreements for groups: {}", groupNames);
        try {
            return agreementService.getAgreementsByGroups(token, groupNames);
        } catch (Exception e) {
            log.error("Error fetching agreements by groups: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch agreements.");
        }
    }

    /**
     * Health check for the agreements integration (connectivity to the Requestor Manager).
     * Mirrors {@code GET /api/agreements/health}.
     */
    @GetMapping("/health")
    public Map<String, Object> agreementsHealthCheck() {
        return agreementService.healthCheck();
    }
}
