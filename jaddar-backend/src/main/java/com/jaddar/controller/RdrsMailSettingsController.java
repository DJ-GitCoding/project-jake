/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.controller;

import com.jaddar.entity.RdrsMailSettings;
import com.jaddar.exception.ApiException;
import com.jaddar.rdrs.RdrsMailIngestService;
import com.jaddar.repository.RdrsMailSettingsRepository;
import com.jaddar.repository.RdrsMessageRepository;
import com.jaddar.security.KeycloakAuthUtil;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Admin routes for RDRS mail capture: retention, poll health and a manual poll. */
@Slf4j
@RestController
@RequestMapping("/api/admin/rdrs-mail")
@RequiredArgsConstructor
public class RdrsMailSettingsController {

    private static final Set<Integer> ALLOWED_RETENTION_DAYS = Set.of(7, 30, 90, 180, 365);

    private final RdrsMailIngestService ingest;
    private final RdrsMailSettingsRepository settingsRepo;
    private final RdrsMessageRepository messages;

    private void requireAdmin(Jwt jwt) {
        if (!KeycloakAuthUtil.isAdmin(jwt)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    /** Current retention setting, capture health and stored message count. */
    @GetMapping("/settings")
    public Map<String, Object> getSettings(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return toResponse(ingest.settings());
    }

    /** Updates how long captured mail is kept. A null retention means indefinitely. */
    @PutMapping("/settings")
    public Map<String, Object> updateSettings(@RequestBody MailSettingsUpdate body,
                                              @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);

        Integer days = body.getRetentionDays();
        if (days != null && !ALLOWED_RETENTION_DAYS.contains(days)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Retention must be one of 7, 30, 90, 180 or 365 days, or unset for indefinite.");
        }

        RdrsMailSettings settings = ingest.settings();
        settings.setRetentionDays(days);
        settings.setUpdatedBy(KeycloakAuthUtil.sub(jwt));
        settings.setUpdatedAt(Instant.now());
        settingsRepo.save(settings);

        log.info("RDRS mail retention set to {}", days == null ? "indefinite" : days + " days");
        return toResponse(settings);
    }

    /** Runs a capture poll now rather than waiting for the schedule. */
    @PostMapping("/poll")
    public Map<String, Object> pollNow(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        if (!ingest.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "RDRS mail capture is not configured on this server.");
        }
        int stored = ingest.poll();
        Map<String, Object> body = new LinkedHashMap<>(toResponse(ingest.settings()));
        body.put("captured", stored);
        return body;
    }

    /** Applies the retention policy immediately. */
    @PostMapping("/purge")
    public Map<String, Object> purgeNow(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        int removed = ingest.purgeExpired();
        Map<String, Object> body = new LinkedHashMap<>(toResponse(ingest.settings()));
        body.put("removed", removed);
        return body;
    }

    private Map<String, Object> toResponse(RdrsMailSettings settings) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("retention_days", settings.getRetentionDays());
        body.put("retention_options", List.of(7, 30, 90, 180, 365));
        body.put("configured", ingest.isConfigured());
        body.put("last_polled_at", settings.getLastPolledAt());
        body.put("last_poll_status", settings.getLastPollStatus());
        body.put("last_poll_message", settings.getLastPollMessage());
        body.put("stored_messages", messages.count());
        body.put("updated_at", settings.getUpdatedAt());
        return body;
    }

    @Data
    public static class MailSettingsUpdate {
        @JsonProperty("retention_days")
        @JsonAlias("retentionDays")
        private Integer retentionDays;
    }
}
