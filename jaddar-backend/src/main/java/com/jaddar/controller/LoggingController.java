/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.dto.LogEntry;
import com.jaddar.dto.LogQueryParams;
import com.jaddar.dto.LogStats;
import com.jaddar.dto.LogsResponse;
import com.jaddar.enums.LogCategory;
import com.jaddar.enums.LogLevel;
import com.jaddar.exception.ApiException;
import com.jaddar.security.KeycloakAuthUtil;
import com.jaddar.service.LoggingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin logging endpoints. Mirrors the Python {@code routers/logging_routes.py}.
 *
 * <p>All routes require admin authentication (Keycloak token + admin check).
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class LoggingController {

    private static final DateTimeFormatter EXPORT_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC);

    private final LoggingService loggingService;
    private final ObjectMapper objectMapper;

    private void requireAdmin(Jwt jwt) {
        if (!KeycloakAuthUtil.isAdmin(jwt)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin privileges required");
        }
    }

    /** Replicates dependencies.admin_auth.get_client_ip. */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }

    @GetMapping
    public LogsResponse getLogs(
            HttpServletRequest request,
            @RequestParam(required = false) LogLevel level,
            @RequestParam(required = false) LogCategory category,
            @RequestParam(name = "start_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(name = "end_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(name = "user_sub", required = false) String userSub,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal Jwt jwt) {

        requireAdmin(jwt);
        validateLimit(limit);
        validateOffset(offset);

        Map<String, Object> filters = new HashMap<>();
        filters.put("level", level != null ? level.getValue() : null);
        filters.put("category", category != null ? category.getValue() : null);
        filters.put("search", search);
        filters.put("limit", limit);
        filters.put("offset", offset);
        Map<String, Object> details = new HashMap<>();
        details.put("filters", filters);

        loggingService.logAdminAction("view_logs", KeycloakAuthUtil.sub(jwt), null,
                getClientIp(request), details);

        LogQueryParams params = LogQueryParams.builder()
                .level(level)
                .category(category)
                .startDate(startDate)
                .endDate(endDate)
                .userSub(userSub)
                .clientId(clientId)
                .search(search)
                .limit(limit)
                .offset(offset)
                .build();

        return loggingService.getLogs(params);
    }

    @GetMapping("/stats")
    public LogStats getLogStats(HttpServletRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        loggingService.logAdminAction("view_log_stats", KeycloakAuthUtil.sub(jwt), null,
                getClientIp(request), null);
        return loggingService.getStats();
    }

    @GetMapping("/categories")
    public Map<String, Object> getLogCategories(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        List<String> categories = new ArrayList<>();
        for (LogCategory c : LogCategory.values()) {
            categories.add(c.getValue());
        }
        List<String> levels = new ArrayList<>();
        for (LogLevel l : LogLevel.values()) {
            levels.add(l.getValue());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categories", categories);
        body.put("levels", levels);
        return body;
    }

    @GetMapping("/introspection")
    public LogsResponse getIntrospectionLogs(
            HttpServletRequest request,
            @RequestParam(name = "start_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(name = "end_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal Jwt jwt) {

        requireAdmin(jwt);
        validateLimit(limit);
        validateOffset(offset);

        Map<String, Object> details = new HashMap<>();
        details.put("client_id_filter", clientId);
        loggingService.logAdminAction("view_introspection_logs", KeycloakAuthUtil.sub(jwt), null,
                getClientIp(request), details);

        LogQueryParams params = LogQueryParams.builder()
                .category(LogCategory.INTROSPECTION)
                .startDate(startDate)
                .endDate(endDate)
                .clientId(clientId)
                .limit(limit)
                .offset(offset)
                .build();

        return loggingService.getLogs(params);
    }

    @GetMapping("/auth-events")
    public LogsResponse getAuthEventLogs(
            HttpServletRequest request,
            @RequestParam(name = "start_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(name = "end_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(name = "user_sub", required = false) String userSub,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @AuthenticationPrincipal Jwt jwt) {

        requireAdmin(jwt);
        validateLimit(limit);
        validateOffset(offset);

        loggingService.logAdminAction("view_auth_logs", KeycloakAuthUtil.sub(jwt), null,
                getClientIp(request), null);

        LogQueryParams params = LogQueryParams.builder()
                .category(LogCategory.AUTH)
                .startDate(startDate)
                .endDate(endDate)
                .userSub(userSub)
                .limit(limit)
                .offset(offset)
                .build();

        return loggingService.getLogs(params);
    }

    @DeleteMapping("/clear")
    public Map<String, Object> clearLogs(HttpServletRequest request, @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);

        String adminSub = KeycloakAuthUtil.sub(jwt);
        String ip = getClientIp(request);

        int count = loggingService.clearLogs();

        // Log the clear action (this will be the first entry after clear).
        Map<String, Object> details = new HashMap<>();
        details.put("cleared_count", count);
        loggingService.logAdminAction("clear_logs", adminSub, null, ip, details);

        log.warn("Admin {} cleared {} log entries", adminSub, count);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Logs cleared successfully");
        body.put("cleared_count", count);
        return body;
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportLogs(
            HttpServletRequest request,
            @RequestParam(required = false) LogLevel level,
            @RequestParam(required = false) LogCategory category,
            @RequestParam(name = "start_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(name = "end_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "json") String format,
            @AuthenticationPrincipal Jwt jwt) {

        requireAdmin(jwt);

        if (!"json".equals(format) && !"csv".equals(format)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "format must match ^(json|csv)$");
        }

        Map<String, Object> details = new HashMap<>();
        details.put("format", format);
        loggingService.logAdminAction("export_logs", KeycloakAuthUtil.sub(jwt), null,
                getClientIp(request), details);

        LogQueryParams params = LogQueryParams.builder()
                .level(level)
                .category(category)
                .startDate(startDate)
                .endDate(endDate)
                .limit(10000)
                .build();

        LogsResponse result = loggingService.getLogs(params);
        String filenameTs = EXPORT_TS.format(Instant.now());

        if ("csv".equals(format)) {
            String csv = toCsv(result.getLogs());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=logs_" + filenameTs + ".csv");
            return new ResponseEntity<>(csv, headers, HttpStatus.OK);
        } else {
            String json = toJson(result.getLogs());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=logs_" + filenameTs + ".json");
            return new ResponseEntity<>(json, headers, HttpStatus.OK);
        }
    }

    private String toCsv(List<LogEntry> logs) {
        StringWriter sw = new StringWriter();
        // Header
        writeCsvRow(sw, List.of(
                "id", "timestamp", "level", "category", "message",
                "user_sub", "client_id", "ip_address", "request_path",
                "request_method", "response_status", "duration_ms", "details"));
        for (LogEntry l : logs) {
            List<String> row = new ArrayList<>();
            row.add(nv(l.getId()));
            row.add(l.getTimestamp() != null ? l.getTimestamp().toString() : "");
            row.add(l.getLevel() != null ? l.getLevel().getValue() : "");
            row.add(l.getCategory() != null ? l.getCategory().getValue() : "");
            row.add(nv(l.getMessage()));
            row.add(nv(l.getUserSub()));
            row.add(nv(l.getClientId()));
            row.add(nv(l.getIpAddress()));
            row.add(nv(l.getRequestPath()));
            row.add(nv(l.getRequestMethod()));
            row.add(l.getResponseStatus() != null ? String.valueOf(l.getResponseStatus()) : "");
            row.add(l.getDurationMs() != null ? String.valueOf(l.getDurationMs()) : "");
            String detailsJson = "";
            if (l.getDetails() != null) {
                try {
                    detailsJson = objectMapper.writeValueAsString(l.getDetails());
                } catch (JsonProcessingException e) {
                    detailsJson = "";
                }
            }
            row.add(detailsJson);
            writeCsvRow(sw, row);
        }
        return sw.toString();
    }

    private String toJson(List<LogEntry> logs) {
        try {
            // Python serialized the LogEntry dicts with the timestamp as ISO string;
            // the LogEntry DTO's JSON shape (with @JsonProperty snake_case + ISO Instant) matches.
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(logs);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize logs");
        }
    }

    private void writeCsvRow(StringWriter sw, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sw.append(',');
            }
            sw.append(csvEscape(fields.get(i)));
        }
        sw.append("\r\n");
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        if (!needsQuote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String nv(String v) {
        return v != null ? v : "";
    }

    private void validateLimit(int limit) {
        if (limit > 1000) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "limit must be less than or equal to 1000");
        }
    }

    private void validateOffset(int offset) {
        if (offset < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "offset must be greater than or equal to 0");
        }
    }
}
