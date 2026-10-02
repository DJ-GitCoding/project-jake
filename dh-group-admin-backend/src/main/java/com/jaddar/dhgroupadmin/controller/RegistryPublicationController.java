/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.controller;

import com.jaddar.dhgroupadmin.entity.RegistryPublicationSettings;
import com.jaddar.dhgroupadmin.service.RegistryPublicationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Publication to the common repository: how this deployment names itself as a data holder group registry, and which of its groups and templates go wi... */
@RestController
@RequestMapping("/api/admin/registry-publication")
@RequiredArgsConstructor
@Slf4j
public class RegistryPublicationController {
    private final RegistryPublicationService publicationService;

    /** Current settings and the outcome of the last announcement. */
    @GetMapping
    public ResponseEntity<?> get(HttpServletRequest http) {
        if (!isMaster(http)) return forbidden();
        return ResponseEntity.ok(toResponse(publicationService.getSettings()));
    }

    /** Saves the settings; announces straight away when publication is on. */
    @PutMapping
    public ResponseEntity<?> update(@RequestBody SettingsRequest request, HttpServletRequest http) {
        if (!isMaster(http)) return forbidden();
        RegistryPublicationSettings incoming = new RegistryPublicationSettings();
        incoming.setEnabled(request.getEnabled());
        incoming.setRegistryUrl(request.getRegistryUrl());
        incoming.setRegistryCode(request.getRegistryCode());
        incoming.setRegistryName(request.getRegistryName());
        incoming.setRegistryDescription(request.getRegistryDescription());
        incoming.setPublicBaseUrl(request.getPublicBaseUrl());
        incoming.setContactEmail(request.getContactEmail());

        try {
            RegistryPublicationSettings saved = publicationService.updateSettings(incoming, callerEmail(http));
            if (saved.isReady()) {
                saved = publicationService.announce(true);
            }
            return ResponseEntity.ok(toResponse(saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /** Announces now, without changing the settings. */
    @PostMapping("/announce")
    public ResponseEntity<?> announce(HttpServletRequest http) {
        if (!isMaster(http)) return forbidden();
        return ResponseEntity.ok(toResponse(publicationService.announce(true)));
    }

    /** Exactly what the next announcement would send, so an admin can check before publishing. */
    @GetMapping("/preview")
    public ResponseEntity<?> preview(HttpServletRequest http) {
        if (!isMaster(http)) return forbidden();
        List<Map<String, Object>> groups = publicationService.globalGroupPayload();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("groupCount", groups.size());
        body.put("templateCount", RegistryPublicationService.countTemplates(groups));
        body.put("dataHolderGroups", groups);
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> toResponse(RegistryPublicationSettings settings) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("enabled", Boolean.TRUE.equals(settings.getEnabled()));
        r.put("registryUrl", settings.getRegistryUrl());
        r.put("registryCode", settings.getRegistryCode());
        r.put("registryName", settings.getRegistryName());
        r.put("registryDescription", settings.getRegistryDescription());
        r.put("publicBaseUrl", settings.getPublicBaseUrl());
        r.put("contactEmail", settings.getContactEmail());
        r.put("registrationStatus", settings.getRegistrationStatus());
        r.put("registered", settings.getAnnounceToken() != null && !settings.getAnnounceToken().isBlank());
        r.put("lastAnnouncedAt", settings.getLastAnnouncedAt());
        r.put("lastAnnounceStatus", settings.getLastAnnounceStatus());
        r.put("lastAnnounceMessage", settings.getLastAnnounceMessage());
        r.put("publishedGroupCount", settings.getPublishedGroupCount());
        r.put("publishedTemplateCount", settings.getPublishedTemplateCount());
        r.put("updatedBy", settings.getUpdatedBy());
        r.put("updatedAt", settings.getUpdatedAt());
        return r;
    }

    private String callerEmail(HttpServletRequest http) {
        Object email = http.getAttribute("userEmail");
        return email instanceof String s ? s : "admin";
    }

    /** Whether the caller is a master administrator. */
    private boolean isMaster(HttpServletRequest http) {
        Object type = http.getAttribute("userType");
        return type instanceof Integer t && t == 1;
    }

    /** The 403 for non-master callers. */
    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "error", "This action requires master administrator access"));
    }

    @Data
    public static class SettingsRequest {
        private Boolean enabled;
        private String registryUrl;
        private String registryCode;
        private String registryName;
        private String registryDescription;
        private String publicBaseUrl;
        private String contactEmail;
    }
}
