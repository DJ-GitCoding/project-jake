/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.controller;

import com.jaddar.dhgroupadmin.entity.BrandingAsset;
import com.jaddar.dhgroupadmin.entity.BrandingNotice;
import com.jaddar.dhgroupadmin.service.BrandingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * Custom logo for this deployment.
 *
 * <p>Reads live under {@code /api/public} — the login screen has to render the logo
 * before anyone has authenticated, and {@code /api/public/} is already an unauthenticated
 * prefix in {@code JwtAuthFilter}. Writes live under {@code /api/admin} and are restricted
 * to master administrators (type 1) by that same filter; the check is repeated here so the
 * rule survives any future change to the filter's path matching.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class BrandingController {

    private static final int MASTER_TYPE = 1;

    private final BrandingService brandingService;

    // ==================== Public read ====================

    /**
     * Serve the uploaded logo, or 404 when none is set so the frontend falls back to
     * the built-in one. The ETag lets browsers revalidate cheaply instead of re-downloading
     * the image on every page load.
     */
    @GetMapping("/api/public/branding/logo")
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
    @GetMapping("/api/public/branding/logo/info")
    public ResponseEntity<?> getLogoInfo() {
        Map<String, Object> absent = new LinkedHashMap<>();
        absent.put("present", false);
        return ResponseEntity.ok(brandingService.find().map(this::toInfo).orElse(absent));
    }

    // ==================== Admin write (master only) ====================

    @PostMapping("/api/admin/branding/logo")
    public ResponseEntity<?> uploadLogo(@RequestParam("file") MultipartFile file, HttpServletRequest http) {
        if (!isMaster(http)) return forbidden();

        try {
            BrandingAsset saved = brandingService.store(file, callerEmail(http));
            Map<String, Object> body = new LinkedHashMap<>(toInfo(saved));
            body.put("success", true);
            return ResponseEntity.ok(body);
        } catch (BrandingService.InvalidBrandingException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/admin/branding/logo")
    public ResponseEntity<?> removeLogo(HttpServletRequest http) {
        if (!isMaster(http)) return forbidden();

        boolean removed = brandingService.remove(callerEmail(http));
        return ResponseEntity.ok(Map.of(
                "success", true,
                "removed", removed,
                "present", false));
    }

    // ==================== Login notices ====================

    /** Public: the login screen renders these before anyone has authenticated. */
    @GetMapping("/api/public/branding/notices")
    public ResponseEntity<?> getNotices() {
        return ResponseEntity.ok(Map.of("notices", toNoticeList(brandingService.listNotices())));
    }

    /** Replace the whole list. Master only. An empty list clears the notices. */
    @PutMapping("/api/admin/branding/notices")
    public ResponseEntity<?> putNotices(@RequestBody NoticesRequest request, HttpServletRequest http) {
        if (!isMaster(http)) return forbidden();

        try {
            var saved = brandingService.replaceNotices(request == null ? null : request.getNotices(),
                    callerEmail(http));
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

    private boolean isMaster(HttpServletRequest http) {
        Object type = http.getAttribute("userType");
        return type instanceof Integer t && t == MASTER_TYPE;
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "error", "This action requires master administrator access"));
    }

    private String callerEmail(HttpServletRequest http) {
        Object email = http.getAttribute("userEmail");
        return email == null ? null : String.valueOf(email);
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
