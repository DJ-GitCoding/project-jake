/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.dto.RdapRequestCreate;
import com.jaddar.dto.RdapRequestListResponse;
import com.jaddar.dto.RdapRequestResponse;
import com.jaddar.dto.RdapRequestUpdate;
import com.jaddar.dto.RdapResolution;
import com.jaddar.dto.RdapResponse;
import com.jaddar.dto.UserRdapSettingsRequest;
import com.jaddar.dto.UserRdapSettingsResponse;
import com.jaddar.entity.DataHolder;
import com.jaddar.entity.RdapRequestHistory;
import com.jaddar.entity.UserRdapSettings;
import com.jaddar.enums.QueryType;
import com.jaddar.enums.RequestStatus;
import com.jaddar.exception.ApiException;
import com.jaddar.repository.DataHolderRepository;
import com.jaddar.service.DataHolderUrlResolver;
import com.jaddar.service.IanaBootstrapService;
import com.jaddar.service.RdapRequestService;
import com.jaddar.service.RdapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * RDAP query + request-management controller.
 *
 * Ports backend/routers/rdap_routes.py. Every route, path, method, query/path
 * param, and response shape mirrors the FastAPI router (prefix /api/rdap).
 *
 * Auth note: the Python router decoded the bearer token itself (unverified) and
 * treated queries as "public" when absent. Here the SecurityConfig validates the
 * token as a resource server, so user info is pulled from the request's
 * Authorization header (still optional — a missing token degrades to a public
 * query where the Python did the same).
 */
@Slf4j
@RestController
@RequestMapping("/api/rdap")
public class RdapController {

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9\\-]{0,8}[A-Za-z0-9])?$");
    private static final Pattern ASN_PATTERN = Pattern.compile("^(?:AS)?(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS = Pattern.compile("^\\d+$");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RdapService rdapService;
    private final RdapRequestService rdapRequestService;
    private final IanaBootstrapService ianaBootstrapService;
    private final DataHolderUrlResolver dataHolderUrlResolver;
    private final DataHolderRepository dataHolderRepository;
    private final ObjectMapper objectMapper;

    public RdapController(RdapService rdapService,
                          RdapRequestService rdapRequestService,
                          IanaBootstrapService ianaBootstrapService,
                          DataHolderUrlResolver dataHolderUrlResolver,
                          DataHolderRepository dataHolderRepository,
                          ObjectMapper objectMapper) {
        this.rdapService = rdapService;
        this.rdapRequestService = rdapRequestService;
        this.ianaBootstrapService = ianaBootstrapService;
        this.dataHolderUrlResolver = dataHolderUrlResolver;
        this.dataHolderRepository = dataHolderRepository;
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // HELPERS
    // ============================================================

    /** Extract the bearer token; throws 401 if missing/malformed (mirrors _extract_token). */
    private String extractToken(String authHeader) {
        if (authHeader == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authorization header required");
        }
        if (!authHeader.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid authorization header format");
        }
        return authHeader.substring(7);
    }

    /** Decode a JWT (no signature verification) into user info — mirrors _get_user_info_from_token. */
    private Map<String, Object> getUserInfoFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("not a jwt");
            }
            byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> decoded = objectMapper.readValue(payload, MAP_TYPE);

            String email = firstNonEmpty(asStr(decoded.get("email")),
                    asStr(decoded.get("preferred_username")), asStr(decoded.get("upn")));
            String preferredUsername = asStr(decoded.get("preferred_username"));
            if (email == null && preferredUsername != null && preferredUsername.contains("@")) {
                email = preferredUsername;
            }

            String name = firstNonEmpty(asStr(decoded.get("name")), asStr(decoded.get("preferred_username")));
            if (name == null) {
                String given = asStr(decoded.get("given_name"));
                String family = asStr(decoded.get("family_name"));
                String combined = ((given != null ? given : "") + " " + (family != null ? family : "")).trim();
                name = combined.isEmpty() ? null : combined;
            }

            Object groups = decoded.get("groups");
            if (groups == null) {
                groups = decoded.get("roles");
            }
            if (groups == null) {
                groups = new ArrayList<>();
            }
            Object realmAccess = decoded.get("realm_access");
            if (realmAccess instanceof Map<?, ?> ra) {
                Object realmRoles = ((Map<?, ?>) ra).get("roles");
                if (realmRoles instanceof List<?> rr && !rr.isEmpty()
                        && (groups instanceof List<?> gl && gl.isEmpty())) {
                    groups = realmRoles;
                }
            }

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("sub", decoded.get("sub"));
            info.put("email", email);
            info.put("name", name);
            info.put("groups", groups);
            info.put("preferred_username", preferredUsername);
            return info;
        } catch (Exception e) {
            log.error("Failed to decode token: {}", e.toString());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }

    private void validateGroupCode(String code, String fieldName) {
        if (code == null || code.isEmpty()) {
            return;
        }
        if (code.length() > 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must be ≤10 characters");
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName
                    + " must contain only letters, digits, and hyphens, and cannot begin or end with a hyphen");
        }
    }

    private void validateRequestTypeCode(Integer code) {
        if (code == null) {
            return;
        }
        if (code < 0 || code > 999) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "requestType must be between 0 and 999");
        }
    }

    /** Auto-detect the query type from input — mirrors _detect_query_type. */
    private QueryType detectQueryType(String queryRaw) {
        String query = queryRaw.trim();

        // ASN
        if (query.toUpperCase().startsWith("AS") || DIGITS.matcher(query).matches()) {
            var m = ASN_PATTERN.matcher(query);
            if (m.matches()) {
                try {
                    long asnNum = Long.parseLong(m.group(1));
                    if (asnNum >= 0 && asnNum <= 4294967295L) {
                        return QueryType.ASN;
                    }
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }

        // IP (address or network)
        if (isIpLiteral(query)) {
            return QueryType.IP;
        }

        // Entity handle (has a hyphen and no dots)
        if (query.contains("-") && !query.contains(".")) {
            return QueryType.ENTITY;
        }

        return QueryType.DOMAIN;
    }

    private boolean isIpLiteral(String query) {
        String hostPart = query.contains("/") ? query.substring(0, query.indexOf('/')) : query;
        if (!hostPart.matches("[0-9a-fA-F:.]+")) {
            return false;
        }
        try {
            java.net.InetAddress.getByName(hostPart);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ============================================================
    // RDAP QUERY ROUTES
    // ============================================================

    @GetMapping("/query")
    public Object rdapQuery(
            @RequestParam("query") String query,
            @RequestParam(value = "query_type", required = false) QueryType queryType,
            @RequestParam(value = "dataHolderGroup", required = false) String dataHolderGroup,
            @RequestParam(value = "requestorGroup", required = false) String requestorGroup,
            @RequestParam(value = "requestType", required = false) Integer requestType,
            @RequestParam(value = "jakeCompliance", defaultValue = "false") boolean jakeCompliance,
            @RequestParam(value = "confidential", defaultValue = "false") boolean confidential,
            @RequestParam(value = "exigent", defaultValue = "false") boolean exigent,
            @RequestParam(value = "customParams", required = false) String customParams,
            @RequestParam(value = "agreements", required = false) String agreements,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        try {
            if (requestorGroup != null) {
                validateGroupCode(requestorGroup, "requestorGroup");
            }
            if (dataHolderGroup != null) {
                validateGroupCode(dataHolderGroup, "dataHolderGroup");
            }
            if (requestType != null) {
                validateRequestTypeCode(requestType);
            }

            QueryType qt = queryType != null ? queryType : detectQueryType(query);

            String token = null;
            Map<String, Object> userInfo = null;
            try {
                token = extractToken(authHeader);
                userInfo = getUserInfoFromToken(token);
                log.info("RDAP query: token extracted");
            } catch (ApiException e) {
                log.info("RDAP query: no token in request (public query)");
            } catch (Exception e) {
                log.warn("RDAP query: token extraction failed: {}", e.toString());
            }

            boolean useNewParams = (requestorGroup != null && requestType != null);

            Map<String, Object> parsedCustomParams = null;
            if (customParams != null && !customParams.isEmpty()) {
                try {
                    Object parsed = objectMapper.readValue(customParams, Object.class);
                    if (!(parsed instanceof Map)) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "customParams must be a JSON object");
                    }
                    parsedCustomParams = objectMapper.convertValue(parsed, MAP_TYPE);
                } catch (ApiException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "customParams must be valid JSON");
                }
            }

            RdapService.QueryOptions opt = new RdapService.QueryOptions();
            opt.token = token;
            opt.agreements = useNewParams ? null : agreements;
            opt.userInfo = userInfo;
            opt.dataHolderGroup = useNewParams ? dataHolderGroup : null;
            opt.requestorGroup = useNewParams ? requestorGroup : null;
            opt.requestType = useNewParams ? requestType : null;
            opt.jakeCompliance = jakeCompliance;
            opt.confidential = confidential;
            opt.exigent = exigent;
            opt.customParams = parsedCustomParams;

            RdapResponse result = runQuery(qt, query, opt);
            if (result == null) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("success", false);
                r.put("error", true);
                r.put("errorCode", "400");
                r.put("errorMessage", "Unsupported query type: " + qt);
                r.put("queryType", qt != null ? qt.getValue() : "unknown");
                r.put("queryValue", query);
                r.put("source", "Jaddar");
                return r;
            }

            if (!result.isSuccess()) {
                saveQueryToHistory(userInfo, query, qt, agreements, result, jakeCompliance, confidential, exigent);

                Object upstreamStatus = result.getRawData() != null
                        ? result.getRawData().get("httpStatusCode") : null;
                String errorCode = upstreamStatus != null ? String.valueOf(upstreamStatus) : "error";

                Map<String, Object> r = new LinkedHashMap<>();
                r.put("success", false);
                r.put("error", true);
                r.put("errorCode", errorCode);
                r.put("errorMessage", result.getErrorMessage() != null
                        ? result.getErrorMessage() : "No RDAP data found for " + query);
                r.put("queryType", qt != null ? qt.getValue() : "unknown");
                r.put("queryValue", query);
                r.put("source", result.getSource() != null ? result.getSource() : "RDAP");
                r.put("rdapServer", result.getRdapServer());
                r.put("raw_data", result.getRawData() != null && !result.getRawData().isEmpty()
                        ? result.getRawData() : null);
                return r;
            }

            saveQueryToHistory(userInfo, query, qt, agreements, result, jakeCompliance, confidential, exigent);
            return result;

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("RDAP query failed: {}", e.toString(), e);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("error", true);
            r.put("errorCode", "500");
            r.put("errorMessage", "Internal error processing RDAP query");
            r.put("queryValue", query);
            r.put("source", "Jaddar");
            return r;
        }
    }

    private RdapResponse runQuery(QueryType qt, String query, RdapService.QueryOptions opt) {
        if (qt == null) {
            return null;
        }
        return switch (qt) {
            case DOMAIN -> rdapService.queryDomain(query, opt);
            case IP -> rdapService.queryIp(query, opt);
            case ASN -> rdapService.queryAsn(query, opt);
            case ENTITY -> rdapService.queryEntity(query, opt);
            default -> null;
        };
    }

    /** Save an RDAP query result to history — mirrors _save_query_to_history. */
    private void saveQueryToHistory(Map<String, Object> userInfo, String query, QueryType queryType,
                                    String agreements, RdapResponse result, boolean jakeCompliance,
                                    boolean confidential, boolean exigent) {
        if (userInfo == null) {
            return;
        }
        try {
            String dataHolderUrl = dataHolderUrlResolver.getDataHolderUrl(query);
            Map<String, Object> raw = result.getRawData() != null ? result.getRawData() : new HashMap<>();
            boolean isPending = "pending".equals(raw.get("status")) && raw.get("requestId") != null;
            String qtValue = queryType != null ? queryType.getValue() : "unknown";

            if (isPending) {
                String requestId = String.valueOf(raw.get("requestId"));
                if (rdapRequestService.getRequestById(requestId) != null) {
                    log.info("Request {} already tracked locally, skipping", requestId);
                    return;
                }
                RdapRequestCreate create = new RdapRequestCreate();
                create.setRequestId(requestId);
                create.setQueryType(qtValue);
                create.setQueryValue(query);
                create.setAgreementsUsed(splitAgreements(agreements));
                create.setDataHolderId(result.getSource() != null ? result.getSource() : "custom");
                create.setDataHolderName(result.getRdapServer() != null ? result.getRdapServer() : "Data Holder");
                create.setExpiresAt(parseInstant(raw.get("expiresAt")));
                create.setJakeCompliance(jakeCompliance);
                create.setConfidential(confidential);
                create.setExigent(exigent);
                rdapRequestService.createRequest(strOr(userInfo.get("sub"), "unknown"),
                        asStr(userInfo.get("email")), create);
                log.info("Created local tracking for pending request {}", requestId);
            } else {
                String userSub = strOr(userInfo.get("sub"), "unknown");
                // Duplicate guard: same user/query/type within 10 seconds.
                RdapRequestHistory existing = rdapRequestService.getRequestsForUser(userSub, null, Integer.MAX_VALUE, 0)
                        .stream()
                        .filter(r -> query.equals(r.getQueryValue()) && qtValue.equals(r.getQueryType()))
                        .findFirst().orElse(null);
                if (existing != null && existing.getCreatedAt() != null
                        && java.time.Duration.between(existing.getCreatedAt(), Instant.now()).getSeconds() < 10) {
                    log.info("Skipping duplicate save for {} (recent entry exists)", query);
                    return;
                }

                String generatedId = "local-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                RequestStatus initialStatus = result.isSuccess() ? RequestStatus.APPROVED : RequestStatus.ERROR;

                RdapRequestCreate create = new RdapRequestCreate();
                create.setRequestId(generatedId);
                create.setQueryType(qtValue);
                create.setQueryValue(query);
                create.setAgreementsUsed(splitAgreements(agreements));
                create.setDataHolderId(result.getSource() != null ? result.getSource() : "public");
                create.setDataHolderName(result.getRdapServer() != null ? result.getRdapServer()
                        : (result.getSource() != null ? result.getSource() : "RDAP"));
                create.setJakeCompliance(jakeCompliance);
                create.setConfidential(confidential);
                create.setExigent(exigent);
                rdapRequestService.createRequest(userSub, asStr(userInfo.get("email")), create);

                RdapRequestUpdate update = new RdapRequestUpdate();
                update.setStatus(initialStatus);
                update.setRdapData(result.isSuccess() ? raw : null);
                update.setErrorMessage(result.isSuccess() ? null : result.getErrorMessage());
                update.setResolvedAt(Instant.now());
                rdapRequestService.updateRequest(generatedId, update);
                log.info("Saved RDAP query to history: {} (status={})", generatedId, initialStatus);
            }
        } catch (Exception e) {
            log.warn("Failed to save RDAP query to history: {}", e.toString());
        }
    }

    // ----- Simple query routes (backward compatible) -----

    @GetMapping("/domain/{domain}")
    public RdapResponse queryDomain(@PathVariable String domain) {
        RdapResponse result = rdapService.queryDomain(domain, new RdapService.QueryOptions());
        if (!result.isSuccess()) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    result.getErrorMessage() != null ? result.getErrorMessage() : "Domain " + domain + " not found");
        }
        return result;
    }

    @GetMapping("/ip/{ipAddress}")
    public RdapResponse queryIp(@PathVariable("ipAddress") String ipAddress) {
        RdapResponse result = rdapService.queryIp(ipAddress, new RdapService.QueryOptions());
        if (!result.isSuccess()) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    result.getErrorMessage() != null ? result.getErrorMessage() : "IP " + ipAddress + " not found");
        }
        return result;
    }

    @GetMapping("/asn/{asn}")
    public RdapResponse queryAsn(@PathVariable String asn) {
        RdapResponse result = rdapService.queryAsn(asn, new RdapService.QueryOptions());
        if (!result.isSuccess()) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    result.getErrorMessage() != null ? result.getErrorMessage() : "ASN " + asn + " not found");
        }
        return result;
    }

    // ============================================================
    // DATA HOLDER DISCOVERY
    // ============================================================

    @GetMapping("/resolve")
    public Map<String, Object> resolveRdapServer(
            @RequestParam("query") String query,
            @RequestParam(value = "query_type", required = false) String queryType) {
        String qt = queryType;
        if (qt == null) {
            qt = detectQueryType(query).getValue();
        }
        RdapResolution resolution;
        switch (qt) {
            case "domain" -> resolution = ianaBootstrapService.resolveDomain(query);
            case "ip" -> resolution = ianaBootstrapService.resolveIp(query);
            case "asn" -> resolution = ianaBootstrapService.resolveAsn(query);
            case "entity" -> resolution = ianaBootstrapService.resolveEntity(query);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid query_type");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("query_type", qt);
        out.put("resolved", resolution != null);
        out.put("resolution", resolution != null ? resolutionToMap(resolution) : null);
        return out;
    }

    /** Mirror the Python dict shape returned by resolve_* (snake_case keys). */
    private Map<String, Object> resolutionToMap(RdapResolution r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("base_urls", r.getBaseUrls());
        m.put("source", r.getSource());
        m.put("data_holder_id", r.getDataHolderId());
        if (r.getDataHolderName() != null) {
            m.put("data_holder_name", r.getDataHolderName());
        }
        m.put("requires_auth", r.isRequiresAuth());
        m.put("auth_type", r.getAuthType());
        return m;
    }

    @GetMapping("/data-holders/active")
    public Map<String, Object> listActiveDataHolders() {
        List<Map<String, Object>> holders = new ArrayList<>();
        for (DataHolder h : dataHolderRepository.findByIsActiveTrue()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("name", h.getName());
            m.put("tlds", h.getTlds() != null ? h.getTlds() : List.of());
            m.put("ip_ranges", h.getIpRanges() != null ? h.getIpRanges() : List.of());
            m.put("asn_ranges", h.getAsnRanges() != null ? h.getAsnRanges() : List.of());
            holders.add(m);
        }
        return Map.of("data_holders", holders);
    }

    // ============================================================
    // USER REQUEST MANAGEMENT ROUTES
    // ============================================================

    /** Detect the logical status from a data holder response — mirrors _detect_dh_response_status. */
    @SuppressWarnings("unchecked")
    private String detectDhResponseStatus(Map<String, Object> dhResponse) {
        if (dhResponse == null || dhResponse.isEmpty()) {
            return "";
        }
        Object statusVal = dhResponse.get("status");
        if ("pending".equals(statusVal)) {
            return "pending";
        }
        if (dhResponse.containsKey("errorCode")) {
            Object errorCode = dhResponse.get("errorCode");
            int code = errorCode instanceof Number n ? n.intValue() : -1;
            if (code == 403) {
                return "denied";
            }
            if (code == 410) {
                Object desc = dhResponse.get("description");
                String title = asStr(dhResponse.get("title"));
                String msg = desc instanceof List ? String.join(" ",
                        ((List<?>) desc).stream().map(String::valueOf).toList()) : String.valueOf(desc);
                String low = (msg + " " + (title != null ? title : "")).toLowerCase();
                if (low.contains("cancel")) {
                    return "cancelled";
                }
                return "expired";
            }
            return "error";
        }
        if (dhResponse.containsKey("rdapConformance") || dhResponse.containsKey("objectClassName")) {
            return "approved";
        }
        if (statusVal instanceof String s) {
            return s.toLowerCase();
        }
        return "";
    }

    @GetMapping("/requests")
    public RdapRequestListResponse getMyRequests(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "sync", defaultValue = "true") boolean sync,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String token = extractToken(authHeader);
        Map<String, Object> userInfo = getUserInfoFromToken(token);
        String userSub = asStr(userInfo.get("sub"));
        String userEmail = asStr(userInfo.get("email"));
        if (userSub == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User subject not found");
        }

        String dataHolderUrl = dataHolderUrlResolver.getDataHolderUrl(null);

        if (sync && userEmail != null) {
            try {
                rdapRequestService.syncRequestsFromDataHolder(dataHolderUrl, userSub, userEmail, token);
            } catch (Exception e) {
                log.warn("Failed to sync with data holder: {}", e.toString());
            }
        }

        RequestStatus statusFilter = null;
        if (status != null) {
            try {
                statusFilter = RequestStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
            }
        }

        List<RdapRequestHistory> requests = rdapRequestService.getRequestsForUser(userSub, statusFilter, limit, offset);
        Map<String, Integer> counts = rdapRequestService.getRequestCountsForUser(userSub);

        RdapRequestListResponse resp = new RdapRequestListResponse();
        resp.setRequests(requests.stream().map(RdapRequestResponse::fromEntity).toList());
        resp.setTotal(counts.get("total"));
        resp.setPendingCount(counts.get("pending"));
        resp.setApprovedCount(counts.get("approved"));
        resp.setDeniedCount(counts.get("denied"));
        resp.setErrorCount(counts.get("error"));
        resp.setCancelledCount(counts.get("cancelled"));
        return resp;
    }

    @GetMapping("/requests/{requestId}")
    public Map<String, Object> getRequestDetail(
            @PathVariable String requestId,
            @RequestParam(value = "check_status", defaultValue = "true") boolean checkStatus,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String token = extractToken(authHeader);
        Map<String, Object> userInfo = getUserInfoFromToken(token);
        String userSub = asStr(userInfo.get("sub"));

        String dataHolderUrl = dataHolderUrlResolver.getDataHolderUrl(null);
        RdapRequestHistory localRequest = rdapRequestService.getRequestById(requestId);

        if (localRequest != null && localRequest.getQueryValue() != null) {
            dataHolderUrl = dataHolderUrlResolver.getDataHolderUrl(localRequest.getQueryValue());
        }

        if (localRequest != null && !java.util.Objects.equals(localRequest.getUserSub(), userSub)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not authorized");
        }

        if (checkStatus) {
            Map<String, Object> dhStatus = rdapRequestService.checkRequestStatusOnDataHolder(dataHolderUrl, requestId, token);

            if (dhStatus != null && localRequest != null) {
                String dhStatusStr = detectDhResponseStatus(dhStatus);
                if (!dhStatusStr.isEmpty()) {
                    RequestStatus newStatus = RdapRequestService.mapStatus(dhStatusStr);
                    boolean needsUpdate = localRequest.getStatus() != newStatus;
                    if (!needsUpdate && "approved".equals(dhStatusStr) && localRequest.getRdapData() == null) {
                        needsUpdate = true;
                    }
                    if (needsUpdate) {
                        Map<String, Object> rdapData = "approved".equals(dhStatusStr) ? dhStatus : null;
                        String errorMsg = null;
                        if ("denied".equals(dhStatusStr)) {
                            errorMsg = denialMessage(dhStatus);
                        }
                        RdapRequestUpdate update = new RdapRequestUpdate();
                        update.setStatus(newStatus);
                        update.setAccessLevelGranted(asInt(dhStatus.get("accessLevel")));
                        update.setRdapData(rdapData);
                        update.setErrorMessage(errorMsg);
                        if (isResolvedStatus(dhStatusStr)) {
                            update.setResolvedAt(Instant.now());
                        }
                        localRequest = rdapRequestService.updateRequest(requestId, update);
                        log.info("Updated request {} to {}", requestId, dhStatusStr);
                    }
                }
            } else if (dhStatus != null) {
                try {
                    String dhStatusStr = detectDhResponseStatus(dhStatus);
                    String queryTypeStr = asStr(dhStatus.getOrDefault("queryType", "unknown"));
                    String queryValueStr = asStr(dhStatus.getOrDefault("queryValue", "unknown"));
                    String userEmail = asStr(userInfo.get("email"));

                    RdapRequestCreate create = new RdapRequestCreate();
                    create.setRequestId(requestId);
                    create.setQueryType(queryTypeStr);
                    create.setQueryValue(queryValueStr);
                    create.setDataHolderId("custom");
                    create.setDataHolderName(dataHolderUrl);
                    create.setExpiresAt(parseInstant(dhStatus.get("expiresAt")));
                    create.setJakeCompliance(Boolean.TRUE.equals(dhStatus.get("jakeCompliance")));
                    localRequest = rdapRequestService.createRequest(userSub, userEmail, create);

                    if (isResolvedOrExpired(dhStatusStr)) {
                        Map<String, Object> rdapData = "approved".equals(dhStatusStr) ? dhStatus : null;
                        String errorMsg = null;
                        if ("denied".equals(dhStatusStr)) {
                            errorMsg = denialMessage(dhStatus);
                        }
                        RdapRequestUpdate update = new RdapRequestUpdate();
                        update.setStatus(RdapRequestService.mapStatus(dhStatusStr));
                        update.setAccessLevelGranted(asInt(dhStatus.get("accessLevel")));
                        update.setRdapData(rdapData);
                        update.setErrorMessage(errorMsg);
                        update.setResolvedAt(Instant.now());
                        localRequest = rdapRequestService.updateRequest(requestId, update);
                    }
                    log.info("Created local record for request {}", requestId);
                } catch (Exception e) {
                    log.warn("Failed to create local record: {}", e.toString());
                }
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("local", localRequest != null ? RdapRequestResponse.fromEntity(localRequest) : null);
            out.put("dataHolder", dhStatus);
            return out;
        }

        if (localRequest == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Request not found");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("local", RdapRequestResponse.fromEntity(localRequest));
        out.put("dataHolder", null);
        return out;
    }

    @PostMapping("/requests/{requestId}/cancel")
    public Map<String, Object> cancelRequest(
            @PathVariable String requestId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String token = extractToken(authHeader);
        Map<String, Object> userInfo = getUserInfoFromToken(token);
        String userSub = asStr(userInfo.get("sub"));
        String userEmail = asStr(userInfo.get("email"));
        if (userSub == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User subject not found");
        }

        String cancelReason = body != null ? asStr(body.get("reason")) : null;

        String dataHolderUrl = dataHolderUrlResolver.getDataHolderUrl(null);

        RdapRequestHistory localRequest = rdapRequestService.getRequestById(requestId);
        if (localRequest == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Request not found");
        }
        if (!java.util.Objects.equals(localRequest.getUserSub(), userSub)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not authorized");
        }
        if (localRequest.getStatus() != RequestStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only pending requests can be cancelled");
        }

        if (localRequest.getQueryValue() != null) {
            dataHolderUrl = dataHolderUrlResolver.getDataHolderUrl(localRequest.getQueryValue());
        }

        String cancelMessage = cancelReason != null
                ? "Cancelled by requestor: " + cancelReason : "Cancelled by requestor";

        Map<String, Object> result = rdapRequestService.cancelRequestOnDataHolder(
                dataHolderUrl, requestId, token, userEmail, userSub, cancelReason);

        if (result != null && Boolean.TRUE.equals(result.get("success"))) {
            return Map.of("success", true, "message", "Request cancelled successfully");
        }

        // Fallback: cancel locally.
        try {
            RdapRequestUpdate update = new RdapRequestUpdate();
            update.setStatus(RequestStatus.CANCELLED);
            update.setResolvedAt(Instant.now());
            update.setErrorMessage(cancelMessage);
            rdapRequestService.updateRequest(requestId, update);
            String errorDetail = result != null
                    ? asStr(result.getOrDefault("error", "Unknown error")) : "Data holder unreachable";
            log.warn("Cancelled request {} locally; data holder returned: {}", requestId, errorDetail);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("message", "Request cancelled locally");
            out.put("warning", "Data holder notification may have failed: " + errorDetail);
            return out;
        } catch (Exception fallbackErr) {
            log.error("Failed to cancel request {} locally", requestId, fallbackErr);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cancellation sent to data holder but failed to update locally.");
        }
    }

    @DeleteMapping("/requests/{requestId}")
    public Map<String, Object> deleteRequestFromHistory(
            @PathVariable String requestId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String token = extractToken(authHeader);
        Map<String, Object> userInfo = getUserInfoFromToken(token);
        String userSub = asStr(userInfo.get("sub"));

        RdapRequestHistory localRequest = rdapRequestService.getRequestById(requestId);
        if (localRequest == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Request not found");
        }
        if (!java.util.Objects.equals(localRequest.getUserSub(), userSub)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not authorized");
        }
        boolean success = rdapRequestService.deleteRequest(requestId);
        return Map.of("success", success, "message", "Request removed from history");
    }

    // ============================================================
    // USER SETTINGS ROUTES
    // ============================================================

    @GetMapping("/settings")
    public UserRdapSettingsResponse getMySettings(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String token = extractToken(authHeader);
        Map<String, Object> userInfo = getUserInfoFromToken(token);
        String userSub = asStr(userInfo.get("sub"));
        String userEmail = asStr(userInfo.get("email"));
        if (userSub == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User subject not found");
        }
        UserRdapSettings settings = rdapRequestService.getOrCreateUserSettings(userSub, userEmail);
        return UserRdapSettingsResponse.fromEntity(settings);
    }

    @PutMapping("/settings")
    public UserRdapSettingsResponse updateMySettings(
            @Valid @RequestBody UserRdapSettingsRequest settingsData,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        String token = extractToken(authHeader);
        Map<String, Object> userInfo = getUserInfoFromToken(token);
        String userSub = asStr(userInfo.get("sub"));
        String userEmail = asStr(userInfo.get("email"));
        if (userSub == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User subject not found");
        }
        UserRdapSettings settings = rdapRequestService.updateUserSettings(userSub, userEmail, settingsData);
        return UserRdapSettingsResponse.fromEntity(settings);
    }

    // ============================================================
    // HEALTH CHECK
    // ============================================================

    @GetMapping("/health")
    public Map<String, Object> rdapHealthCheck() {
        try {
            String ianaStatus;
            try {
                RdapResolution resolution = ianaBootstrapService.resolveDomain("example.com");
                ianaStatus = resolution != null ? "healthy" : "degraded";
            } catch (Exception e) {
                ianaStatus = "unhealthy";
            }
            long activeHolders = dataHolderRepository.findByIsActiveTrue().size();

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "healthy".equals(ianaStatus) ? "healthy" : "degraded");
            out.put("service", "RDAP");
            out.put("requestor_id", rdapService.getRequestorAgentId());
            out.put("iana_bootstrap", ianaStatus);
            out.put("custom_data_holders", activeHolders);
            return out;
        } catch (Exception e) {
            log.error("RDAP health check failed", e);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "unhealthy");
            out.put("service", "RDAP");
            out.put("error", "RDAP service health check failed");
            return out;
        }
    }

    // ============================================================
    // helpers
    // ============================================================

    private boolean isResolvedStatus(String s) {
        return "approved".equals(s) || "denied".equals(s) || "expired".equals(s) || "cancelled".equals(s);
    }

    private boolean isResolvedOrExpired(String s) {
        return isResolvedStatus(s);
    }

    @SuppressWarnings("unchecked")
    private String denialMessage(Map<String, Object> dhStatus) {
        Object desc = dhStatus.get("description");
        if (desc instanceof List<?> list) {
            List<String> parts = list.stream().map(String::valueOf).toList();
            return parts.isEmpty() ? "Request denied" : String.join("; ", parts);
        }
        return desc != null ? String.valueOf(desc) : "Request denied";
    }

    private List<String> splitAgreements(String agreements) {
        if (agreements == null || agreements.isEmpty()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (String a : agreements.split(",")) {
            out.add(a);
        }
        return out;
    }

    private static String asStr(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    private static String strOr(Object o, String dflt) {
        return o != null ? String.valueOf(o) : dflt;
    }

    private static Integer asInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    private Instant parseInstant(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }
}
