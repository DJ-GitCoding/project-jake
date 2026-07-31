/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.controller;

import com.jaddar.dto.DataHolderCreate;
import com.jaddar.dto.DataHolderListResponse;
import com.jaddar.dto.DataHolderResponse;
import com.jaddar.dto.DataHolderUpdate;
import com.jaddar.dto.RdapResolution;
import com.jaddar.entity.DataHolder;
import com.jaddar.exception.ApiException;
import com.jaddar.security.KeycloakAuthUtil;
import com.jaddar.service.DataHolderService;
import com.jaddar.service.IanaBootstrapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin routes for managing custom RDAP Data Holders.
 *
 * <p>Ported from {@code backend/routers/dataholder_routes.py}. All endpoints
 * require authentication + admin role (mirrors the Python {@code _require_admin}).
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/data-holders")
@RequiredArgsConstructor
public class DataHolderController {

    private final DataHolderService dataHolderService;
    private final IanaBootstrapService ianaBootstrapService;

    /**
     * Mirrors Python {@code _require_admin}. The shared {@link KeycloakAuthUtil#isAdmin}
     * already covers the admin-role / admin-email checks; on failure the Python raised
     * HTTP 403 "Admin access required".
     */
    private void requireAdmin(Jwt jwt) {
        if (!KeycloakAuthUtil.isAdmin(jwt)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    // ============================================================
    // CRUD Endpoints
    // ============================================================

    @GetMapping
    public DataHolderListResponse listDataHolders(
            @RequestParam(name = "active_only", defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal Jwt jwt) {

        requireAdmin(jwt);
        validateLimit(limit);
        validateOffset(offset);

        List<DataHolder> holders = dataHolderService.listAll(activeOnly, limit, offset);
        long total = dataHolderService.count(false);
        long activeCount = dataHolderService.count(true);

        List<DataHolderResponse> responses = new ArrayList<>();
        for (DataHolder h : holders) {
            responses.add(DataHolderResponse.fromEntity(h));
        }
        return new DataHolderListResponse(responses, total, activeCount);
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return dataHolderService.getStats();
    }

    @GetMapping("/{holderId}")
    public DataHolderResponse getDataHolder(
            @PathVariable("holderId") long holderId,
            @AuthenticationPrincipal Jwt jwt) {

        requireAdmin(jwt);
        DataHolder holder = dataHolderService.getById(holderId);
        if (holder == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Data holder not found");
        }
        return DataHolderResponse.fromEntity(holder);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DataHolderResponse createDataHolder(
            @Valid @RequestBody DataHolderCreate data,
            @AuthenticationPrincipal Jwt jwt) {

        requireAdmin(jwt);
        DataHolder holder = dataHolderService.create(data, KeycloakAuthUtil.sub(jwt));
        return DataHolderResponse.fromEntity(holder);
    }

    @PutMapping("/{holderId}")
    public DataHolderResponse updateDataHolder(
            @PathVariable("holderId") long holderId,
            @Valid @RequestBody DataHolderUpdate data,
            @AuthenticationPrincipal Jwt jwt) {

        requireAdmin(jwt);
        DataHolder holder = dataHolderService.update(holderId, data);
        if (holder == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Data holder not found");
        }
        return DataHolderResponse.fromEntity(holder);
    }

    @PatchMapping("/{holderId}/toggle")
    public Map<String, Object> toggleDataHolder(
            @PathVariable("holderId") long holderId,
            @AuthenticationPrincipal Jwt jwt) {

        requireAdmin(jwt);
        DataHolder holder = dataHolderService.toggleActive(holderId);
        if (holder == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Data holder not found");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("id", holder.getId());
        body.put("is_active", holder.getIsActive());
        return body;
    }

    @DeleteMapping("/{holderId}")
    public Map<String, Object> deleteDataHolder(
            @PathVariable("holderId") long holderId,
            @AuthenticationPrincipal Jwt jwt) {

        requireAdmin(jwt);
        if (!dataHolderService.delete(holderId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Data holder not found");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", "Data holder deleted");
        return body;
    }

    // ============================================================
    // IANA Bootstrap Info
    // ============================================================

    @GetMapping("/iana/resolve-test")
    public Map<String, Object> testIanaResolve(
            @RequestParam("query") String query,
            @RequestParam(name = "query_type", defaultValue = "domain") String queryType,
            @AuthenticationPrincipal Jwt jwt) {

        requireAdmin(jwt);

        RdapResolution result;
        switch (queryType) {
            case "domain":
                result = ianaBootstrapService.resolveDomain(query);
                break;
            case "ip":
                result = ianaBootstrapService.resolveIp(query);
                break;
            case "asn":
                result = ianaBootstrapService.resolveAsn(query);
                break;
            default:
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "query_type must be domain, ip, or asn");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("query_type", queryType);
        body.put("resolved", result != null);
        body.put("resolution", result);
        return body;
    }

    // ============================================================
    // Query-param constraint checks (mirror FastAPI Query ge/le → HTTP 422)
    // ============================================================

    /** Query(100, ge=1, le=500). */
    private void validateLimit(int limit) {
        if (limit < 1) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "limit must be greater than or equal to 1");
        }
        if (limit > 500) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "limit must be less than or equal to 500");
        }
    }

    /** Query(0, ge=0). */
    private void validateOffset(int offset) {
        if (offset < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "offset must be greater than or equal to 0");
        }
    }
}
