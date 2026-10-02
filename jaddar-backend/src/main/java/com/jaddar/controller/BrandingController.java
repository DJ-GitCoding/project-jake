/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.controller;

import com.jaddar.entity.BrandingAsset;
import com.jaddar.entity.BrandingNotice;
import com.jaddar.exception.ApiException;
import com.jaddar.security.KeycloakAuthUtil;
import com.jaddar.service.BrandingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
 * <p>{@code GET /api/branding/logo} is public by design — the login screen has to render
 * the logo before anyone has authenticated (see {@code SecurityConfig.PUBLIC_PATHS}).
 * Uploading and removing require the admin role, since the logo is deployment-wide
 * branding rather than a per-user preference.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BrandingController {

    private final BrandingService brandingService;

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
    public Map<String, Object> getLogoInfo() {
        Map<String, Object> absent = new LinkedHashMap<>();
        absent.put("present", false);
        return brandingService.find().map(this::toInfo).orElse(absent);
    }

    // ==================== Admin write ====================

    @PostMapping("/api/admin/branding/logo")
    public Map<String, Object> uploadLogo(@RequestParam("file") MultipartFile file,
                                          @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        try {
            BrandingAsset saved = brandingService.store(file, KeycloakAuthUtil.email(jwt));
            Map<String, Object> body = new LinkedHashMap<>(toInfo(saved));
            body.put("success", true);
            return body;
        } catch (BrandingService.InvalidBrandingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/api/admin/branding/logo")
    public Map<String, Object> removeLogo(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        boolean removed = brandingService.remove(KeycloakAuthUtil.email(jwt));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("removed", removed);
        body.put("present", false);
        return body;
    }

    // ==================== Login notices ====================

    /** Public: the login screen renders these before anyone has authenticated. */
    @GetMapping("/api/branding/notices")
    public Map<String, Object> getNotices() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("notices", toNoticeList(brandingService.listNotices()));
        return body;
    }

    /** Replace the whole list. Admin only. An empty list clears the notices. */
    @PutMapping("/api/admin/branding/notices")
    public Map<String, Object> putNotices(@RequestBody NoticesRequest request,
                                          @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        try {
            var saved = brandingService.replaceNotices(request == null ? null : request.getNotices(),
                    KeycloakAuthUtil.email(jwt));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("notices", toNoticeList(saved));
            return body;
        } catch (BrandingService.InvalidBrandingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, e.getMessage());
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

    /** Same admin gate the other admin routers use. */
    private void requireAdmin(Jwt jwt) {
        if (!KeycloakAuthUtil.isAdmin(jwt)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin access required");
        }
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
