/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.controller;

import com.jaddar.entity.RdapRequestHistory;
import com.jaddar.enums.RequestStatus;
import com.jaddar.exception.ApiException;
import com.jaddar.repository.RdapRequestHistoryRepository;
import com.jaddar.security.KeycloakAuthUtil;
import com.jaddar.service.RdapExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Routes for exporting RDAP request data to Excel and PDF.
 * Ported from backend/routers/rdap_export_routes.py.
 *
 * Mirrors the FastAPI router prefix /api/rdap/export and all of its routes,
 * including the same query/path params, Content-Type, and Content-Disposition
 * (filename) headers. Bytes are returned via ResponseEntity&lt;byte[]&gt;.
 */
@RestController
@RequestMapping("/api/rdap/export")
@Slf4j
@RequiredArgsConstructor
public class RdapExportController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final RdapRequestHistoryRepository historyRepository;
    private final RdapExportService exportService;

    // ============================================================
    // BULK EXPORT ROUTES (from RdapRequests list page)
    // ============================================================

    @GetMapping("/requests/excel")
    public ResponseEntity<byte[]> exportRequestsExcel(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "request_ids", required = false) String requestIds,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset,
            @RequestParam(value = "columns", required = false) String columns,
            @RequestParam(value = "include_rdap_data", defaultValue = "true") boolean includeRdapData) {
        Map<String, String> user = authenticatedUser(jwt);
        List<Map<String, Object>> requestsData =
                slice(getFilteredRequests(user, status, requestIds), offset, limit);

        String title = "RDAP Requests";
        if (status != null && !status.isEmpty()) {
            title += " (" + status.toUpperCase(Locale.ROOT) + ")";
        }

        byte[] bytes = exportService.exportRequestsToExcel(
                requestsData, title, RdapExportService.resolveColumns(csvToList(columns)), includeRdapData);
        String filename = "rdap_requests_" + nowStamp() + ".xlsx";
        return fileResponse(bytes, XLSX_MEDIA_TYPE, filename);
    }

    @GetMapping("/requests/pdf")
    public ResponseEntity<byte[]> exportRequestsPdf(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "request_ids", required = false) String requestIds,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "offset", required = false) Integer offset,
            @RequestParam(value = "columns", required = false) String columns,
            @RequestParam(value = "include_rdap_data", defaultValue = "true") boolean includeRdapData) {
        Map<String, String> user = authenticatedUser(jwt);
        List<Map<String, Object>> requestsData =
                slice(getFilteredRequests(user, status, requestIds), offset, limit);

        String title = "RDAP Requests Report";
        if (status != null && !status.isEmpty()) {
            title += " — " + status.toUpperCase(Locale.ROOT);
        }

        byte[] bytes = exportService.exportRequestsToPdf(
                requestsData, title, RdapExportService.resolveColumns(csvToList(columns)), includeRdapData);
        String filename = "rdap_requests_" + nowStamp() + ".pdf";
        return fileResponse(bytes, MediaType.APPLICATION_PDF_VALUE, filename);
    }

    // ============================================================
    // SINGLE REQUEST EXPORT (by request_id from DB)
    // ============================================================

    @GetMapping("/request/{request_id}/excel")
    public ResponseEntity<byte[]> exportSingleRequestExcel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("request_id") String requestId) {
        Map<String, String> user = authenticatedUser(jwt);
        Map<String, Object> requestData = getSingleRequest(user, requestId);

        String queryValue = strOr(requestData.get("query_value"), "rdap");
        String filename = "rdap_" + queryValue + "_" + nowStamp() + ".xlsx";
        byte[] bytes = exportService.exportSingleRequestToExcel(requestData);
        return fileResponse(bytes, XLSX_MEDIA_TYPE, filename);
    }

    @GetMapping("/request/{request_id}/pdf")
    public ResponseEntity<byte[]> exportSingleRequestPdf(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("request_id") String requestId) {
        Map<String, String> user = authenticatedUser(jwt);
        Map<String, Object> requestData = getSingleRequest(user, requestId);

        String queryValue = strOr(requestData.get("query_value"), "rdap");
        String filename = "rdap_" + queryValue + "_" + nowStamp() + ".pdf";
        byte[] bytes = exportService.exportSingleRequestToPdf(requestData);
        return fileResponse(bytes, MediaType.APPLICATION_PDF_VALUE, filename);
    }

    // ============================================================
    // CURRENT RESULT EXPORT (posted from RDAPSearch frontend)
    // ============================================================

    @PostMapping("/current/excel")
    public ResponseEntity<byte[]> exportCurrentRequestExcel(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) Map<String, Object> requestData) {
        authenticatedUser(jwt);
        Map<String, Object> data = requestData != null ? requestData : new LinkedHashMap<>();

        String queryValue = currentQueryValue(data);
        String filename = "rdap_" + queryValue + "_" + nowStamp() + ".xlsx";
        byte[] bytes = exportService.exportSingleRequestToExcel(data);
        return fileResponse(bytes, XLSX_MEDIA_TYPE, filename);
    }

    @PostMapping("/current/pdf")
    public ResponseEntity<byte[]> exportCurrentRequestPdf(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) Map<String, Object> requestData) {
        authenticatedUser(jwt);
        Map<String, Object> data = requestData != null ? requestData : new LinkedHashMap<>();

        String queryValue = currentQueryValue(data);
        String filename = "rdap_" + queryValue + "_" + nowStamp() + ".pdf";
        byte[] bytes = exportService.exportSingleRequestToPdf(data);
        return fileResponse(bytes, MediaType.APPLICATION_PDF_VALUE, filename);
    }

    // ============================================================
    // Internal helpers
    // ============================================================

    /**
     * Mirror of the Python _get_authenticated_user: requires a subject.
     * Auth is enforced by SecurityConfig (a valid bearer token is required to
     * reach this controller); here we replicate the "User subject not found"
     * guard from the Python helper.
     */
    private Map<String, String> authenticatedUser(Jwt jwt) {
        String sub = KeycloakAuthUtil.sub(jwt);
        if (sub == null || sub.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User subject not found");
        }
        Map<String, String> info = new LinkedHashMap<>();
        info.put("sub", sub);
        info.put("email", KeycloakAuthUtil.email(jwt));
        info.put("name", KeycloakAuthUtil.name(jwt));
        return info;
    }

    /** Fetch RDAP requests for the user, filtered by optional status and optional IDs. */
    private List<Map<String, Object>> getFilteredRequests(
            Map<String, String> user, String status, String requestIdsStr) {
        String userSub = user.get("sub");
        List<RdapRequestHistory> results;

        if (status != null && !status.isEmpty()) {
            RequestStatus statusEnum;
            try {
                statusEnum = RequestStatus.valueOf(status.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
            }
            results = historyRepository.findByUserSubAndStatusOrderByCreatedAtDesc(userSub, statusEnum);
        } else {
            results = historyRepository.findByUserSubOrderByCreatedAtDesc(userSub);
        }

        if (requestIdsStr != null && !requestIdsStr.isEmpty()) {
            List<String> ids = new ArrayList<>();
            for (String rid : requestIdsStr.split(",")) {
                String trimmed = rid.trim();
                if (!trimmed.isEmpty()) {
                    ids.add(trimmed);
                }
            }
            if (!ids.isEmpty()) {
                List<RdapRequestHistory> filtered = new ArrayList<>();
                for (RdapRequestHistory r : results) {
                    if (ids.contains(r.getRequestId())) {
                        filtered.add(r);
                    }
                }
                results = filtered;
            }
        }

        List<Map<String, Object>> out = new ArrayList<>(results.size());
        for (RdapRequestHistory r : results) {
            out.add(recordToMap(r));
        }
        return out;
    }

    /**
     * Narrow the (already newest-first) result set to the window the caller asked
     * for, so an export can mirror the page — or the run of pages — the user is
     * looking at. Both bounds are optional; an offset past the end yields nothing.
     */
    private static List<Map<String, Object>> slice(
            List<Map<String, Object>> requests, Integer offset, Integer limit) {
        if (offset == null && limit == null) {
            return requests;
        }
        int from = offset != null && offset > 0 ? offset : 0;
        if (from >= requests.size()) {
            return List.of();
        }
        int to = requests.size();
        if (limit != null && limit > 0) {
            to = Math.min(to, from + limit);
        }
        return new ArrayList<>(requests.subList(from, to));
    }

    /** Split a comma-separated query param into trimmed, non-empty values. */
    private static List<String> csvToList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /** Fetch a single request, ensuring ownership (404 otherwise). */
    private Map<String, Object> getSingleRequest(Map<String, String> user, String requestId) {
        RdapRequestHistory record = historyRepository.findByRequestId(requestId)
                .filter(r -> user.get("sub") != null && user.get("sub").equals(r.getUserSub()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Request not found"));
        return recordToMap(record);
    }

    /** Convert a DB record to the map shape the export service expects (mirror _db_record_to_dict). */
    private Map<String, Object> recordToMap(RdapRequestHistory r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("request_id", r.getRequestId());
        m.put("status", r.getStatus() != null ? r.getStatus().name().toLowerCase(Locale.ROOT) : null);
        m.put("query_type", r.getQueryType());
        m.put("query_value", r.getQueryValue());
        m.put("agreements_used", r.getAgreementsUsed());
        m.put("access_level_requested", r.getAccessLevelRequested());
        m.put("access_level_granted", r.getAccessLevelGranted());
        m.put("rdap_data", r.getRdapData());
        m.put("error_message", r.getErrorMessage());
        m.put("data_holder_id", r.getDataHolderId());
        m.put("data_holder_name", r.getDataHolderName());
        m.put("created_at", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        m.put("resolved_at", r.getResolvedAt() != null ? r.getResolvedAt().toString() : null);
        m.put("user_email", r.getUserEmail());
        return m;
    }

    /**
     * Mirror of: request_data.get("query_value")
     *            or request_data.get("raw_data", {}).get("ldhName", "rdap")
     */
    @SuppressWarnings("unchecked")
    private String currentQueryValue(Map<String, Object> data) {
        Object qv = data.get("query_value");
        if (qv != null && !String.valueOf(qv).isEmpty()) {
            return String.valueOf(qv);
        }
        Object raw = data.get("raw_data");
        if (raw instanceof Map<?, ?> rawMap) {
            Object ldh = ((Map<String, Object>) rawMap).get("ldhName");
            if (ldh != null && !String.valueOf(ldh).isEmpty()) {
                return String.valueOf(ldh);
            }
        }
        return "rdap";
    }

    private ResponseEntity<byte[]> fileResponse(byte[] bytes, String contentType, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    private static String nowStamp() {
        return LocalDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ENGLISH));
    }

    private static String strOr(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v);
        return s.isEmpty() ? def : s;
    }
}
