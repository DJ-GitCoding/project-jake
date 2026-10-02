/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.controller;

import com.requestormanager.dto.ApiResponse;
import com.requestormanager.entity.BrandingAsset;
import com.requestormanager.entity.BrandingNotice;
import com.requestormanager.security.SecurityUtils;
import com.requestormanager.service.BrandingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
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
 * <p>{@code GET /api/v1/branding/logo} is public by design — the login screen has to
 * render the logo before anyone has authenticated. Writes sit under
 * {@code /api/v1/admin/**} (ADMIN or MASTER at the filter chain) and are narrowed to
 * MASTER here, since the logo is deployment-wide branding rather than a group setting.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Branding", description = "Deployment logo and login notices")
public class BrandingController {

    private final BrandingService brandingService;
    private final SecurityUtils securityUtils;

    // ==================== Public read ====================

    /**
     * Serve the uploaded logo, or 404 when none is set so the frontend falls back to
     * the built-in one. The ETag lets browsers revalidate cheaply instead of re-downloading
     * the image on every page load.
     */
    @GetMapping("/api/v1/branding/logo")
    @Operation(summary = "Get the custom logo", description = "Returns 404 when no custom logo is set.")
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
    @GetMapping("/api/v1/branding/logo/info")
    @Operation(summary = "Describe the custom logo", description = "Metadata only; no image bytes.")
    public ResponseEntity<?> getLogoInfo() {
        Map<String, Object> absent = new LinkedHashMap<>();
        absent.put("present", false);
        return ResponseEntity.ok(ApiResponse.success(
                brandingService.find().map(this::toInfo).orElse(absent)));
    }

    // ==================== Admin write (MASTER only) ====================

    @PostMapping("/api/v1/admin/branding/logo")
    @Operation(summary = "Upload a custom logo", description = "Replaces any existing logo. MASTER only.")
    public ResponseEntity<?> uploadLogo(@RequestParam("file") MultipartFile file) {
        if (!securityUtils.isMaster()) return forbidden();

        try {
            BrandingAsset saved = brandingService.store(file, currentUsername());
            return ResponseEntity.ok(ApiResponse.success("Logo updated", toInfo(saved)));
        } catch (BrandingService.InvalidBrandingException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/api/v1/admin/branding/logo")
    @Operation(summary = "Remove the custom logo", description = "Reverts to the built-in logo. MASTER only.")
    public ResponseEntity<?> removeLogo() {
        if (!securityUtils.isMaster()) return forbidden();

        brandingService.remove(currentUsername());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("present", false);
        return ResponseEntity.ok(ApiResponse.success("Logo removed", body));
    }

    // ==================== Login notices ====================

    /** Public: the login screen renders these before anyone has authenticated. */
    @GetMapping("/api/v1/branding/notices")
    @Operation(summary = "List the login notices", description = "Empty list when none are defined.")
    public ResponseEntity<?> getNotices() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("notices", toNoticeList(brandingService.listNotices()));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    @PutMapping("/api/v1/admin/branding/notices")
    @Operation(summary = "Replace the login notices",
               description = "Whole-list replace; an empty list clears them. MASTER only.")
    public ResponseEntity<?> putNotices(@RequestBody NoticesRequest request) {
        if (!securityUtils.isMaster()) return forbiddenNotices();

        try {
            var saved = brandingService.replaceNotices(request == null ? null : request.getNotices(),
                    currentUsername());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("notices", toNoticeList(saved));
            return ResponseEntity.ok(ApiResponse.success("Login notices updated", body));
        } catch (BrandingService.InvalidBrandingException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
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
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("That image is too large. Logos must be 2 MB or smaller."));
    }

    // ==================== Helpers ====================

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only Master Admin accounts can change the logo"));
    }

    private String currentUsername() {
        return securityUtils.getCurrentUsername()
                .or(securityUtils::getCurrentUserEmail)
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

    private ResponseEntity<?> forbiddenNotices() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Only Master Admin accounts can change the login notices"));
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
