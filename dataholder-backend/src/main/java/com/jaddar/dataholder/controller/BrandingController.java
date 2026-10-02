/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import com.jaddar.dataholder.entity.BrandingAsset;
import com.jaddar.dataholder.entity.BrandingNotice;
import com.jaddar.dataholder.entity.DataHolderUser;
import com.jaddar.dataholder.repository.DataHolderUserRepository;
import com.jaddar.dataholder.service.BrandingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deployment branding: the custom logo, and the notices shown under the sign-in form.
 *
 * <p>The GETs are public by design — the login screen has to render both before anyone
 * has authenticated. Writing is restricted to MASTER accounts, since branding is
 * deployment-wide rather than per-user preference.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class BrandingController {

    private final BrandingService brandingService;
    private final DataHolderUserRepository userRepository;

    // ==================== Public read ====================

    /**
     * Serve the uploaded logo, or 404 when none is set so the frontend falls back to
     * the built-in one. The ETag lets browsers revalidate cheaply instead of re-downloading
     * the image on every page load.
     */
    @GetMapping("/api/branding/logo")
    public ResponseEntity<byte[]> getLogo() {
        Optional<BrandingAsset> found = brandingService.find();
        if (found.isEmpty()) return ResponseEntity.notFound().build();

        BrandingAsset asset = found.get();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .eTag(etagOf(asset))
                // no-cache = revalidate every load; the logo must update promptly after a change.
                .cacheControl(CacheControl.noCache())
                .header("Content-Disposition", "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(asset.getData());
    }

    /** Lightweight presence check so the UI can pick its rendering without fetching bytes. */
    @GetMapping("/api/branding/logo/info")
    public ResponseEntity<?> getLogoInfo() {
        Map<String, Object> absent = new LinkedHashMap<>();
        absent.put("present", false);
        return ResponseEntity.ok(brandingService.find().map(this::toInfo).orElse(absent));
    }

    // ==================== Admin write (MASTER only) ====================

    @PostMapping("/api/admin/branding/logo")
    public ResponseEntity<?> uploadLogo(@RequestParam("file") MultipartFile file) {
        DataHolderUser currentUser = currentMasterOrNull();
        if (currentUser == null) {
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "error", "Only MASTER accounts can change the logo"));
        }

        try {
            BrandingAsset saved = brandingService.store(file, currentUser.getUsername());
            Map<String, Object> body = new LinkedHashMap<>(toInfo(saved));
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (BrandingService.InvalidBrandingException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/admin/branding/logo")
    public ResponseEntity<?> removeLogo() {
        DataHolderUser currentUser = currentMasterOrNull();
        if (currentUser == null) {
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "error", "Only MASTER accounts can change the logo"));
        }

        boolean removed = brandingService.remove(currentUser.getUsername());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "removed", removed,
                "present", false));
    }

    // ==================== Login notices ====================

    /** Public: the login screen renders these before anyone has authenticated. */
    @GetMapping("/api/branding/notices")
    public ResponseEntity<?> getNotices() {
        return ResponseEntity.ok(Map.of("notices", toNoticeList(brandingService.listNotices())));
    }

    /** Replace the whole list. MASTER only. An empty list clears the notices. */
    @PutMapping("/api/admin/branding/notices")
    public ResponseEntity<?> putNotices(@RequestBody NoticesRequest request) {
        DataHolderUser currentUser = currentMasterOrNull();
        if (currentUser == null) {
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "error", "Only MASTER accounts can change the login notices"));
        }

        try {
            var saved = brandingService.replaceNotices(request == null ? null : request.getNotices(),
                    currentUser.getUsername());
            return ResponseEntity.ok(Map.of("success", true, "notices", toNoticeList(saved)));
        } catch (BrandingService.InvalidBrandingException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /** Request body for the whole-list replace. */
    @Data
    public static class NoticesRequest {
        private List<BrandingService.NoticeInput> notices;
    }

    /**
     * A file past the container's multipart ceiling never reaches the service, so translate
     * it here rather than letting it surface as a generic 500.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "That image is too large. Logos must be 2 MB or smaller."));
    }

    // ==================== Helpers ====================

    /** Resolve the authenticated principal to a live MASTER user, or null if not one. */
    private DataHolderUser currentMasterOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByUsernameAndIsActiveTrue(auth.getName())
                .filter(DataHolderUser::isMaster)
                .orElse(null);
    }

    private Map<String, Object> toInfo(BrandingAsset asset) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("present", true);
        info.put("filename", asset.getFilename());
        info.put("contentType", asset.getContentType());
        info.put("sizeBytes", asset.getSizeBytes());
        info.put("updatedBy", asset.getUpdatedBy());
        info.put("updatedAt", asset.getUpdatedAt());
        return info;
    }

    private List<Map<String, Object>> toNoticeList(List<BrandingNotice> notices) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (BrandingNotice n : notices) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", n.getText());
            m.put("url", n.getUrl());
            out.add(m);
        }
        return out;
    }

    private String etagOf(BrandingAsset asset) {
        long stamp = asset.getUpdatedAt() == null
                ? 0L
                : asset.getUpdatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
        return "\"" + stamp + "-" + asset.getSizeBytes() + "\"";
    }
}
