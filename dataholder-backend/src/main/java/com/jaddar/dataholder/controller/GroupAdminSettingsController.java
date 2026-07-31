/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import com.jaddar.dataholder.entity.GroupAdminSettings;
import com.jaddar.dataholder.repository.GroupAdminSettingsRepository;
import com.jaddar.dataholder.service.GroupAdminClient;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for configuring Data Holder Group Admin connections.
 * Supports multiple simultaneous connections to different group admins.
 */
@RestController
@RequestMapping("/api/admin/group-admin-settings")
@RequiredArgsConstructor
@Slf4j
public class GroupAdminSettingsController {

    private final GroupAdminSettingsRepository settingsRepository;
    private final GroupAdminClient groupAdminClient;

    // ==================== List / Get ====================

    /**
     * Get all Group Admin connections (active and inactive)
     */
    @GetMapping
    public ResponseEntity<?> getSettings() {
        List<GroupAdminSettings> all = settingsRepository.findAll();
        if (all.isEmpty()) {
            return ResponseEntity.ok(Map.of("configured", false, "connections", List.of()));
        }

        List<Map<String, Object>> connections = all.stream().map(this::toResponse).toList();
        boolean anyActive = all.stream().anyMatch(s -> Boolean.TRUE.equals(s.getIsActive()));

        return ResponseEntity.ok(Map.of(
                "configured", anyActive,
                "connections", connections,
                // Backward compat: "settings" points to the first active connection
                "settings", all.stream()
                        .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                        .findFirst()
                        .map(this::toResponse)
                        .orElse(null)
        ));
    }

    /**
     * Get a specific connection by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getSettingsById(@PathVariable Long id) {
        return settingsRepository.findById(id)
                .map(s -> ResponseEntity.ok(toResponse(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Create ====================

    /**
     * Add a new Group Admin connection.
     * Does NOT deactivate existing connections — multiple connections can coexist.
     */
    @PostMapping
    public ResponseEntity<?> saveSettings(@RequestBody SettingsRequest request) {
        if (request.getBaseUrl() == null || request.getClientId() == null || request.getClientSecret() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "error", "baseUrl, clientId, and clientSecret are required"));
        }

        GroupAdminSettings settings = GroupAdminSettings.builder()
                .name(request.getName() != null ? request.getName().trim() : null)
                .baseUrl(request.getBaseUrl().replaceAll("/$", ""))
                .clientId(request.getClientId().trim())
                .clientSecret(request.getClientSecret().trim())
                .isActive(true)
                .build();

        settings = settingsRepository.save(settings);
        log.info("Group Admin connection added: '{}' at {} (clientId: {})",
                settings.getName(), settings.getBaseUrl(), settings.getClientId());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Connection added",
                "connection", toResponse(settings)));
    }

    // ==================== Update ====================

    /**
     * Update a specific connection by ID
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateSettingsById(@PathVariable Long id, @RequestBody SettingsRequest request) {
        return settingsRepository.findById(id)
                .map(settings -> {
                    if (request.getName() != null) settings.setName(request.getName().trim());
                    if (request.getBaseUrl() != null) settings.setBaseUrl(request.getBaseUrl().replaceAll("/$", ""));
                    if (request.getClientId() != null) settings.setClientId(request.getClientId().trim());
                    if (request.getClientSecret() != null && !request.getClientSecret().isBlank()) {
                        settings.setClientSecret(request.getClientSecret().trim());
                    }
                    if (request.getIsActive() != null) settings.setIsActive(request.getIsActive());
                    settingsRepository.save(settings);
                    log.info("Group Admin connection updated: id={}", id);
                    return ResponseEntity.ok(Map.of(
                            "success", true, "message", "Connection updated",
                            "connection", toResponse(settings)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Legacy PUT (no ID): updates the first active connection
     */
    @PutMapping
    public ResponseEntity<?> updateSettings(@RequestBody SettingsRequest request) {
        return settingsRepository.findFirstByIsActiveTrueOrderByIdDesc()
                .map(settings -> {
                    if (request.getName() != null) settings.setName(request.getName().trim());
                    if (request.getBaseUrl() != null) settings.setBaseUrl(request.getBaseUrl().replaceAll("/$", ""));
                    if (request.getClientId() != null) settings.setClientId(request.getClientId().trim());
                    if (request.getClientSecret() != null && !request.getClientSecret().isBlank()) {
                        settings.setClientSecret(request.getClientSecret().trim());
                    }
                    settingsRepository.save(settings);
                    return ResponseEntity.ok(Map.of("success", true, "message", "Settings updated"));
                })
                .orElse(ResponseEntity.badRequest().body(Map.of(
                        "success", false, "error", "No settings configured. Use POST to create.")));
    }

    // ==================== Delete ====================

    /**
     * Delete a connection by ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSettings(@PathVariable Long id) {
        return settingsRepository.findById(id)
                .map(settings -> {
                    settingsRepository.delete(settings);
                    log.info("Group Admin connection deleted: id={}, name='{}'", id, settings.getName());
                    return ResponseEntity.ok(Map.of("success", true, "message", "Connection deleted"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Toggle Active ====================

    /**
     * Toggle a connection's active status
     */
    @PostMapping("/{id}/toggle")
    public ResponseEntity<?> toggleActive(@PathVariable Long id) {
        return settingsRepository.findById(id)
                .map(settings -> {
                    settings.setIsActive(!Boolean.TRUE.equals(settings.getIsActive()));
                    settingsRepository.save(settings);
                    log.info("Group Admin connection {} toggled to active={}",
                            id, settings.getIsActive());
                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "isActive", settings.getIsActive(),
                            "message", settings.getIsActive() ? "Connection enabled" : "Connection disabled"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Test ====================

    /**
     * Test a specific connection by ID
     */
    @PostMapping("/{id}/test")
    public ResponseEntity<?> testConnectionById(@PathVariable Long id) {
        Map<String, Object> result = groupAdminClient.testConnection(id);
        return ResponseEntity.ok(result);
    }

    /**
     * Legacy: test the first active connection
     */
    @PostMapping("/test")
    public ResponseEntity<?> testConnection() {
        Map<String, Object> result = groupAdminClient.testConnection();
        return ResponseEntity.ok(result);
    }

    // ==================== Proxied Data (aggregated across all connections) ====================

    /**
     * Fetch templates from all Group Admins (aggregated)
     */
    @GetMapping("/templates")
    public ResponseEntity<?> getTemplates() {
        if (!groupAdminClient.isConfigured()) {
            return ResponseEntity.ok(Map.of("configured", false, "templates", List.of()));
        }
        return ResponseEntity.ok(Map.of("configured", true, "templates", groupAdminClient.getTemplates()));
    }

    /**
     * Fetch, per data holder group, the fellow member data holders (aggregated
     * across all Group Admins).
     */
    @GetMapping("/group-members")
    public ResponseEntity<?> getGroupMembers() {
        if (!groupAdminClient.isConfigured()) {
            return ResponseEntity.ok(Map.of("configured", false, "groups", List.of()));
        }
        return ResponseEntity.ok(Map.of("configured", true, "groups", groupAdminClient.getGroupMembers()));
    }

    /**
     * Fetch subscriptions from all Group Admins (aggregated)
     */
    @GetMapping("/subscriptions")
    public ResponseEntity<?> getSubscriptions() {
        if (!groupAdminClient.isConfigured()) {
            return ResponseEntity.ok(Map.of("configured", false, "subscriptions", List.of()));
        }
        return ResponseEntity.ok(Map.of("configured", true, "subscriptions", groupAdminClient.getSubscriptions()));
    }

    /**
     * Fetch active subscriptions from all Group Admins (aggregated)
     */
    @GetMapping("/subscriptions/active")
    public ResponseEntity<?> getActiveSubscriptions() {
        if (!groupAdminClient.isConfigured()) {
            return ResponseEntity.ok(Map.of("configured", false, "subscriptions", List.of()));
        }
        return ResponseEntity.ok(Map.of("configured", true, "subscriptions", groupAdminClient.getActiveSubscriptions()));
    }

    // ==================== Helpers ====================

    private Map<String, Object> toResponse(GroupAdminSettings settings) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", settings.getId());
        response.put("name", settings.getName());
        response.put("baseUrl", settings.getBaseUrl());
        response.put("clientId", settings.getClientId());
        response.put("hasSecret", settings.getClientSecret() != null && !settings.getClientSecret().isBlank());
        response.put("isActive", settings.getIsActive());
        response.put("lastConnectedAt", settings.getLastConnectedAt());
        response.put("lastError", settings.getLastError());
        response.put("createdAt", settings.getCreatedAt());
        response.put("updatedAt", settings.getUpdatedAt());
        return response;
    }

    @Data
    public static class SettingsRequest {
        private String name;
        private String baseUrl;
        private String clientId;
        private String clientSecret;
        private Boolean isActive;
    }
}
