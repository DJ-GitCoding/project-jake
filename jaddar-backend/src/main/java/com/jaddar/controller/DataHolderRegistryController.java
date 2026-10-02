/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.controller;

import com.jaddar.entity.DataHolderRegistrySettings;
import com.jaddar.exception.ApiException;
import com.jaddar.security.KeycloakAuthUtil;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jaddar.service.DataHolderRegistryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Admin routes for the external Data Holder Registry switch, URL and listing. */
@Slf4j
@RestController
@RequestMapping("/api/admin/data-holder-registry")
@RequiredArgsConstructor
public class DataHolderRegistryController {

    private final DataHolderRegistryService registryService;

    private void requireAdmin(Jwt jwt) {
        if (!KeycloakAuthUtil.isAdmin(jwt)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    /** Current registry settings and last sync outcome. */
    @GetMapping("/settings")
    public Map<String, Object> getSettings(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return toResponse(registryService.getSettings());
    }

    /** Updates the toggle and URL, fetching immediately when enabled. */
    @PutMapping("/settings")
    public Map<String, Object> updateSettings(
            @RequestBody RegistrySettingsUpdate body,
            @AuthenticationPrincipal Jwt jwt) {

        requireAdmin(jwt);
        DataHolderRegistrySettings settings = registryService.updateSettings(
                body.getEnabled(), body.getRegistryUrl(), KeycloakAuthUtil.sub(jwt));

        if (Boolean.TRUE.equals(settings.getEnabled())) {
            settings = registryService.refresh(true);
        }
        return toResponse(settings);
    }

    /** Re-fetches the feed now. */
    @PostMapping("/refresh")
    public Map<String, Object> refresh(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return toResponse(registryService.refresh(true));
    }

    /** What the registry currently publishes; read-only. */
    @GetMapping("/holders")
    public Map<String, Object> listHolders(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        List<Map<String, Object>> holders = registryService.listHoldersForAdmin();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", registryService.isEnabled());
        body.put("holders", holders);
        body.put("total", holders.size());
        return body;
    }

    /** Settings row as the body the admin UI reads. */
    private Map<String, Object> toResponse(DataHolderRegistrySettings settings) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", Boolean.TRUE.equals(settings.getEnabled()));
        body.put("registry_url", settings.getRegistryUrl());
        body.put("last_synced_at", settings.getLastSyncedAt());
        body.put("last_sync_status", settings.getLastSyncStatus());
        body.put("last_sync_message", settings.getLastSyncMessage());
        body.put("holder_count", settings.getHolderCount());
        body.put("feed_publication", settings.getFeedPublication());
        body.put("updated_at", settings.getUpdatedAt());
        return body;
    }

    @Data
    public static class RegistrySettingsUpdate {
        private Boolean enabled;

        @JsonProperty("registry_url")
        @JsonAlias("registryUrl")
        private String registryUrl;
    }
}
