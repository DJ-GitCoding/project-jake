/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.config.MtlsEndpoints;
import com.jaddar.dataholder.config.MtlsProperties;
import com.jaddar.dataholder.config.MtlsSslContextFactory;
import com.jaddar.dataholder.entity.GroupAdminSettings;
import com.jaddar.dataholder.repository.GroupAdminSettingsRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Client for the Group Admin external API across multiple concurrent connections. Query methods
 * aggregate results over every active connection, tagging each map with "_groupAdminId" and
 * "_groupAdminName" so callers know its source.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupAdminClient {

    private final GroupAdminSettingsRepository settingsRepository;
    private final MtlsProperties mtlsProperties;
    private final MtlsEndpoints mtlsEndpoints;

    private HttpClient httpClient;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /** Builds the JDK HttpClient, presenting the mTLS client cert when enabled (10s connect timeout). */
    @PostConstruct
    void initHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10));
        if (mtlsProperties.isEnabled()) {
            try {
                builder.sslContext(MtlsSslContextFactory.buildJavaxSslContext(mtlsProperties));
                log.info("mTLS enabled: GroupAdminClient will present a client certificate on outbound calls");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure mTLS for GroupAdminClient", e);
            }
        }
        this.httpClient = builder.build();
    }

    // ==================== Connection ====================

    /**
     * Get all active settings
     */
    public List<GroupAdminSettings> getAllActiveSettings() {
        return settingsRepository.findAllByIsActiveTrueOrderByIdAsc();
    }

    /**
     * Get a specific settings record by ID
     */
    public Optional<GroupAdminSettings> getSettingsById(Long id) {
        return settingsRepository.findById(id);
    }

    /**
     * Check if at least one Group Admin connection is configured
     */
    public boolean isConfigured() {
        return !getAllActiveSettings().isEmpty();
    }

    /**
     * Test the connection for a specific settings record
     */
    public Map<String, Object> testConnection(Long settingsId) {
        Optional<GroupAdminSettings> opt = settingsRepository.findById(settingsId);
        if (opt.isEmpty()) {
            return Map.of("connected", false, "error", "Connection not found");
        }
        GroupAdminSettings settings = opt.get();
        try {
            Map<String, Object> info = doGet(settings, "/api/external/my/info");
            settings.setLastConnectedAt(LocalDateTime.now());
            settings.setLastError(null);
            settingsRepository.save(settings);
            return Map.of("connected", true, "dataHolder", info, "settingsId", settingsId);
        } catch (Exception e) {
            settings.setLastError(e.getMessage());
            settingsRepository.save(settings);
            return Map.of("connected", false, "error", e.getMessage(), "settingsId", settingsId);
        }
    }

    /**
     * Legacy: test the first active connection (backward compat)
     */
    public Map<String, Object> testConnection() {
        List<GroupAdminSettings> all = getAllActiveSettings();
        if (all.isEmpty()) {
            return Map.of("connected", false, "error", "No Group Admin connection configured");
        }
        return testConnection(all.get(0).getId());
    }

    // ==================== Group Memberships ====================

    /** This data holder's group memberships, aggregated across all active Group Admins. */
    public List<Map<String, Object>> getGroupMemberships() {
        List<Map<String, Object>> allGroups = new ArrayList<>();
        for (GroupAdminSettings settings : getAllActiveSettings()) {
            try {
                Map<String, Object> info = doGet(settings, "/api/external/my/info");
                Object memberships = info.get("groupMemberships");
                if (memberships instanceof List) {
                    for (Object item : (List<?>) memberships) {
                        if (item instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> group = new HashMap<>((Map<String, Object>) item);
                            group.put("_groupAdminId", settings.getId());
                            group.put("_groupAdminName", settings.getName() != null ? settings.getName() : settings.getBaseUrl());
                            allGroups.add(group);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch group memberships from '{}': {}", label(settings), e.getMessage());
            }
        }
        return allGroups;
    }

    /** Per group, the fellow data-holder members, aggregated across all active Group Admins. */
    public List<Map<String, Object>> getGroupMembers() {
        return aggregateList("/api/external/my/group-members");
    }

    // ==================== Templates ====================

    /**
     * Fetch published templates from ALL active Group Admins, aggregated
     */
    public List<Map<String, Object>> getTemplates() {
        return aggregateList("/api/external/my/templates");
    }

    // ==================== Subscriptions ====================

    /**
     * Fetch all subscriptions from ALL active Group Admins, aggregated
     */
    public List<Map<String, Object>> getSubscriptions() {
        return aggregateList("/api/external/my/subscriptions");
    }

    /**
     * Fetch only active+effective subscriptions from ALL active Group Admins
     */
    public List<Map<String, Object>> getActiveSubscriptions() {
        return aggregateList("/api/external/my/subscriptions/active");
    }

    /**
     * Check if a requestor group has an active subscription (across all connections)
     */
    public Map<String, Object> checkSubscription(String requestorGroupId) {
        for (GroupAdminSettings settings : getAllActiveSettings()) {
            try {
                Map<String, Object> result = doGet(settings,
                        "/api/external/my/subscriptions/check?requestorGroupId=" + requestorGroupId);
                if (Boolean.TRUE.equals(result.get("active"))) {
                    tagResult(result, settings);
                    return result;
                }
            } catch (Exception e) {
                log.warn("Failed to check subscription on '{}': {}", label(settings), e.getMessage());
            }
        }
        return Map.of("active", false, "message", "No active subscription found across any group admin");
    }

    /**
     * Lookup subscription by requestor group code (across all connections)
     */
    public List<Map<String, Object>> getSubscriptionsByGroupCode(String groupCode) {
        return aggregateList("/api/external/my/subscriptions/by-group-code?groupCode=" + groupCode);
    }

    // ==================== Aggregation ====================

    /** Calls a GET-list endpoint on every active connection and merges the results, tagging each by source. */
    private List<Map<String, Object>> aggregateList(String path) {
        List<Map<String, Object>> merged = new ArrayList<>();
        for (GroupAdminSettings settings : getAllActiveSettings()) {
            try {
                List<Map<String, Object>> results = doGetList(settings, path);
                for (Map<String, Object> item : results) {
                    Map<String, Object> tagged = new HashMap<>(item);
                    tagResult(tagged, settings);
                    merged.add(tagged);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch {} from '{}': {}", path, label(settings), e.getMessage());
            }
        }
        return merged;
    }

    private void tagResult(Map<String, Object> item, GroupAdminSettings settings) {
        item.put("_groupAdminId", settings.getId());
        item.put("_groupAdminName", settings.getName() != null ? settings.getName() : settings.getBaseUrl());
    }

    private String label(GroupAdminSettings s) {
        return s.getName() != null ? s.getName() : ("id=" + s.getId());
    }

    // ==================== HTTP Helpers ====================

    private String buildAuthHeader(GroupAdminSettings settings) {
        String credentials = settings.getClientId() + ":" + settings.getClientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    private Map<String, Object> doGet(GroupAdminSettings settings, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mtlsEndpoints.resolve(settings.getBaseUrl()) + path))
                .header("Authorization", buildAuthHeader(settings))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Group Admin returned " + response.statusCode() + ": " + response.body());
        }

        return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    }

    private List<Map<String, Object>> doGetList(GroupAdminSettings settings, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mtlsEndpoints.resolve(settings.getBaseUrl()) + path))
                .header("Authorization", buildAuthHeader(settings))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Group Admin returned " + response.statusCode() + ": " + response.body());
        }

        return objectMapper.readValue(response.body(), new TypeReference<List<Map<String, Object>>>() {});
    }
}