/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.jaddar.config.JaddarProperties;
import com.jaddar.dto.AgreementServerApiResponse;
import com.jaddar.dto.AgreementSummary;
import com.jaddar.dto.AgreementsResponse;
import com.jaddar.dto.GroupAgreements;
import com.jaddar.dto.UserAgreementsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for communicating with the Requestor Manager to fetch user agreements.
 * Ported from backend/services/agreement_service.py.
 *
 * <p>The Requestor Manager validates the user's Keycloak token (introspection),
 * extracts the user's groups, and returns the agreements for those groups. We proxy
 * the user's bearer token through verbatim (the Python only forwards
 * {@code Authorization}; we additionally forward the configured requestor agent id
 * via {@code X-Requestor-Agent-Id} — see the assumption note in the migration report).
 *
 * <p>The base URL of {@code requestorManagerWebClient} is {@code requestorManagerUrl}
 * (per the migration contract); the Python equivalent prefixed the same relative paths
 * onto its {@code REQUESTOR_MANAGER_SERVER_URL}.
 */
@Slf4j
@Service
public class AgreementService {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(10);

    private static final String AGREEMENTS_PATH = "/agreements/api/v1/external/agreements";
    private static final String BY_GROUPS_PATH = "/agreements/api/v1/external/agreements/by-groups";
    private static final String HEALTH_PATH = "/api/v1/external/health";

    private final WebClient requestorManagerWebClient;
    private final JaddarProperties properties;

    public AgreementService(
            @Qualifier("requestorManagerWebClient") WebClient requestorManagerWebClient,
            JaddarProperties properties) {
        this.requestorManagerWebClient = requestorManagerWebClient;
        this.properties = properties;
        log.info("AgreementService initialized targeting requestor manager: {}",
                properties.getRequestorManagerUrl());
    }

    /** Ensure the token carries the "Bearer " prefix (mirrors the Python guard). */
    private String ensureBearer(String keycloakToken) {
        if (keycloakToken != null && !keycloakToken.startsWith("Bearer ")) {
            return "Bearer " + keycloakToken;
        }
        return keycloakToken;
    }

    /**
     * Get agreements for the user based on their Keycloak token.
     *
     * <p>On a 401 from the Requestor Manager (invalid/expired token) returns an empty
     * response rather than raising, exactly as the Python did. On any other HTTP error
     * the exception propagates (the controller maps it to a 500).
     */
    public UserAgreementsResponse getUserAgreements(String keycloakToken) {
        String token = ensureBearer(keycloakToken);

        log.info("Fetching agreements from: {}{}", properties.getRequestorManagerUrl(), AGREEMENTS_PATH);

        AgreementServerApiResponse data;
        try {
            data = requestorManagerWebClient.get()
                    .uri(AGREEMENTS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .header("X-Requestor-Agent-Id", properties.getRequestorAgentId())
                    .retrieve()
                    .bodyToMono(AgreementServerApiResponse.class)
                    .block(TIMEOUT);
        } catch (WebClientResponseException.Unauthorized e) {
            log.warn("Requestor Manager returned 401 - token invalid or expired");
            return emptyResponse(new ArrayList<>());
        } catch (WebClientResponseException e) {
            log.error("HTTP error from Requestor Manager: {} - {}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            throw e;
        }

        return toUserResponse(data, null);
    }

    /**
     * Get agreements for specific group names.
     *
     * <p>Posts {@code {"groupNames": [...]}} to the by-groups endpoint. The returned
     * {@code keycloak_groups} echoes the requested group names (matching the Python).
     */
    public UserAgreementsResponse getAgreementsByGroups(String keycloakToken, List<String> groupNames) {
        String token = ensureBearer(keycloakToken);

        log.info("Fetching agreements for groups {} from: {}{}",
                groupNames, properties.getRequestorManagerUrl(), BY_GROUPS_PATH);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupNames", groupNames);

        AgreementServerApiResponse data;
        try {
            data = requestorManagerWebClient.post()
                    .uri(BY_GROUPS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .header("X-Requestor-Agent-Id", properties.getRequestorAgentId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(AgreementServerApiResponse.class)
                    .block(TIMEOUT);
        } catch (WebClientResponseException e) {
            log.error("HTTP error from Requestor Manager: {}", e.getStatusCode().value());
            throw e;
        }

        return toUserResponse(data, groupNames);
    }

    /**
     * Map a Requestor Manager wrapper response into the frontend response.
     *
     * @param keycloakGroupsOverride when non-null, used verbatim for {@code keycloak_groups}
     *                               (the by-groups path); when null, derived from the group names
     *                               in the response (the default path).
     */
    private UserAgreementsResponse toUserResponse(AgreementServerApiResponse data, List<String> keycloakGroupsOverride) {
        if (data != null && data.isSuccess() && data.getData() != null) {
            AgreementsResponse apiData = data.getData();

            List<AgreementSummary> agreements = apiData.getAgreements() != null
                    ? apiData.getAgreements() : new ArrayList<>();
            List<GroupAgreements> groups = apiData.getGroups() != null
                    ? apiData.getGroups() : new ArrayList<>();

            List<String> keycloakGroups;
            if (keycloakGroupsOverride != null) {
                keycloakGroups = keycloakGroupsOverride;
            } else {
                keycloakGroups = new ArrayList<>();
                for (GroupAgreements g : groups) {
                    keycloakGroups.add(g.getGroupName());
                }
            }

            List<String> warnings = apiData.getWarnings();
            if (warnings != null && !warnings.isEmpty()) {
                log.warn("Agreement server warnings: {}", warnings);
            }

            log.info("Found {} agreements in {} groups", agreements.size(), groups.size());

            UserAgreementsResponse response = new UserAgreementsResponse();
            response.setAgreements(agreements);
            response.setGroups(groups);
            response.setKeycloakGroups(keycloakGroups);
            response.setWarnings(warnings);
            return response;
        }

        log.warn("Requestor Manager returned unsuccessful response: {}", data);
        return emptyResponse(keycloakGroupsOverride != null ? keycloakGroupsOverride : new ArrayList<>());
    }

    private UserAgreementsResponse emptyResponse(List<String> keycloakGroups) {
        UserAgreementsResponse response = new UserAgreementsResponse();
        response.setAgreements(new ArrayList<>());
        response.setGroups(new ArrayList<>());
        response.setKeycloakGroups(keycloakGroups);
        return response;
    }

    /** Check if the Requestor Manager is reachable. Never throws. */
    public Map<String, Object> healthCheck() {
        String server = properties.getRequestorManagerUrl();
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Map<String, Object> response = requestorManagerWebClient.get()
                    .uri(HEALTH_PATH)
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .block(HEALTH_TIMEOUT);
            result.put("status", "healthy");
            result.put("agreement_server", server);
            result.put("response", response);
            return result;
        } catch (WebClientResponseException e) {
            result.put("status", "degraded");
            result.put("agreement_server", server);
            result.put("status_code", e.getStatusCode().value());
            return result;
        } catch (Exception e) {
            result.put("status", "unhealthy");
            result.put("agreement_server", server);
            result.put("error", e.getMessage());
            return result;
        }
    }

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new org.springframework.core.ParameterizedTypeReference<>() {
            };
}
