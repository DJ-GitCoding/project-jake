/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.controller;

import com.requestormanager.dto.ApiResponse;
import com.requestormanager.dto.ExternalApiDto;
import com.requestormanager.entity.DataHolderGroup;
import com.requestormanager.entity.DataHolderAgreement;
import com.requestormanager.entity.RequestorGroup;
import com.requestormanager.repository.DataHolderAgreementRepository;
import com.requestormanager.repository.DataHolderGroupRepository;
import com.requestormanager.repository.RequestorGroupRepository;
import com.requestormanager.service.KeycloakTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service-to-service API for querying agreements, authenticated via Keycloak token introspection.
 * Request types are fetched live from each dataholder's published template endpoint and cached briefly.
 */
@RestController
@RequestMapping("/api/v1/external")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "External API", description = "External service endpoints for agreement queries")
public class ExternalApiController {

    private final KeycloakTokenService keycloakTokenService;
    private final RequestorGroupRepository requestorGroupRepository;
    private final DataHolderAgreementRepository dataHolderAgreementRepository;
    private final DataHolderGroupRepository dataHolderGroupRepository;
    private final RestTemplate restTemplate;
    private final com.requestormanager.config.MtlsEndpoints mtlsEndpoints;

    /**
     * Simple in-memory cache for template request types.
     * Key: "dataholderBaseUrl::templateId"
     * Value: cached result with timestamp
     */
    private final Map<String, CachedRequestTypes> requestTypesCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000; // 1 minute

    // ==================== Main Endpoints ====================

    /**
     * Get agreements for the authenticated user's Keycloak groups.
     */
    @GetMapping("/agreements")
    @Transactional(readOnly = true)
    @Operation(
        summary = "Get agreements by Keycloak token",
        description = "Validates Keycloak token and returns active agreements for the user's groups, enriched with request types from each dataholder"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Agreements retrieved successfully",
            content = @Content(schema = @Schema(implementation = ExternalAgreementsResponseWrapper.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Invalid or expired token",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    public ResponseEntity<ApiResponse<ExternalApiDto.AgreementsResponse>> getAgreementsByToken(
            @Parameter(description = "Bearer token from Keycloak", required = true)
            @RequestHeader("Authorization") String authorizationHeader) {
        
        log.info("External API: agreements request received");

        // Validate token via Keycloak introspection
        KeycloakTokenService.TokenIntrospectionResult introspectionResult = 
            keycloakTokenService.introspectToken(authorizationHeader);

        if (!introspectionResult.isActive()) {
            log.warn("External API: token validation failed");
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid or expired token"));
        }

        List<String> keycloakGroups = introspectionResult.getGroups();
        log.info("External API: user {} has groups: {}", 
            introspectionResult.getSubject(), keycloakGroups);

        if (keycloakGroups.isEmpty()) {
            log.info("External API: user has no groups");
            return ResponseEntity.ok(ApiResponse.success(
                ExternalApiDto.AgreementsResponse.builder()
                    .userSubject(introspectionResult.getSubject())
                    .userEmail(introspectionResult.getEmail())
                    .groups(Collections.emptyList())
                    .agreements(Collections.emptyList())
                    .warnings(Collections.emptyList())
                    .build()
            ));
        }

        // Collect warnings from failed DH Group Admin calls
        List<String> warnings = new ArrayList<>();

        // Find matching RequestorGroups and their agreements
        List<ExternalApiDto.GroupAgreements> groupAgreementsList = new ArrayList<>();
        List<ExternalApiDto.AgreementSummary> allAgreements = new ArrayList<>();

        for (String groupName : keycloakGroups) {
            Optional<RequestorGroup> requestorGroupOpt = requestorGroupRepository.findByName(groupName);
            
            if (requestorGroupOpt.isPresent()) {
                RequestorGroup group = requestorGroupOpt.get();
                List<ExternalApiDto.AgreementSummary> summaries = getAgreementsForGroup(group, warnings);
                
                groupAgreementsList.add(ExternalApiDto.GroupAgreements.builder()
                    .groupId(group.getId())
                    .groupName(group.getName())
                    .agreements(summaries)
                    .build());
                
                allAgreements.addAll(summaries);
                
                log.info("External API: found {} active agreements for group '{}'", 
                    summaries.size(), groupName);
            } else {
                log.info("External API: no RequestorGroup found for Keycloak group '{}'", groupName);
            }
        }

        // ---- FIX: Deduplicate on the FULL natural key including subscription-level
        // differentiators (access level, effective dates) so that distinct subscriptions
        // under the same template are preserved. ----
        List<ExternalApiDto.AgreementSummary> uniqueAgreements = deduplicateAgreements(allAgreements);

        ExternalApiDto.AgreementsResponse response = ExternalApiDto.AgreementsResponse.builder()
            .userSubject(introspectionResult.getSubject())
            .userEmail(introspectionResult.getEmail())
            .groups(groupAgreementsList)
            .agreements(uniqueAgreements)
            .warnings(warnings.isEmpty() ? null : warnings)
            .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get agreements by specific group names (for service accounts).
     */
    @PostMapping("/agreements/by-groups")
    @Transactional(readOnly = true)
    @Operation(
        summary = "Get agreements by group names",
        description = "Returns active agreements for the specified group names after validating the token"
    )
    public ResponseEntity<ApiResponse<ExternalApiDto.AgreementsResponse>> getAgreementsByGroups(
            @Parameter(description = "Bearer token from Keycloak", required = true)
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody ExternalApiDto.GroupsRequest request) {
        
        log.info("External API: agreements by groups request: {}", request.getGroupNames());

        // Validate token
        KeycloakTokenService.TokenIntrospectionResult introspectionResult = 
            keycloakTokenService.introspectToken(authorizationHeader);

        if (!introspectionResult.isActive()) {
            log.warn("External API: token validation failed");
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid or expired token"));
        }

        List<String> requestedGroups = request.getGroupNames();
        if (requestedGroups == null || requestedGroups.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(
                ExternalApiDto.AgreementsResponse.builder()
                    .userSubject(introspectionResult.getSubject())
                    .userEmail(introspectionResult.getEmail())
                    .groups(Collections.emptyList())
                    .agreements(Collections.emptyList())
                    .warnings(Collections.emptyList())
                    .build()
            ));
        }

        // Collect warnings from failed DH Group Admin calls
        List<String> warnings = new ArrayList<>();

        List<ExternalApiDto.GroupAgreements> groupAgreementsList = new ArrayList<>();
        List<ExternalApiDto.AgreementSummary> allAgreements = new ArrayList<>();

        for (String groupName : requestedGroups) {
            Optional<RequestorGroup> requestorGroupOpt = requestorGroupRepository.findByName(groupName);
            
            if (requestorGroupOpt.isPresent()) {
                RequestorGroup group = requestorGroupOpt.get();
                List<ExternalApiDto.AgreementSummary> summaries = getAgreementsForGroup(group, warnings);
                
                groupAgreementsList.add(ExternalApiDto.GroupAgreements.builder()
                    .groupId(group.getId())
                    .groupName(group.getName())
                    .agreements(summaries)
                    .build());
                
                allAgreements.addAll(summaries);
            }
        }

        // ---- FIX: Use corrected deduplication ----
        List<ExternalApiDto.AgreementSummary> uniqueAgreements = deduplicateAgreements(allAgreements);

        return ResponseEntity.ok(ApiResponse.success(
            ExternalApiDto.AgreementsResponse.builder()
                .userSubject(introspectionResult.getSubject())
                .userEmail(introspectionResult.getEmail())
                .groups(groupAgreementsList)
                .agreements(uniqueAgreements)
                .warnings(warnings.isEmpty() ? null : warnings)
                .build()
        ));
    }

    /**
     * Health check for external API
     */
    @GetMapping("/health")
    @Operation(summary = "External API health check")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("service", "Requestor Manager External API");
        return ResponseEntity.ok(health);
    }

    // ==================== Helper Methods ====================

    /**
     * Dedup by a composite key that includes access level and effective dates, so distinct
     * subscriptions under one template are preserved. The earlier group::group::template key
     * collapsed multiple valid subscriptions into one.
     */
    private List<ExternalApiDto.AgreementSummary> deduplicateAgreements(
            List<ExternalApiDto.AgreementSummary> allAgreements) {
        return allAgreements.stream()
            .collect(Collectors.collectingAndThen(
                Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(
                    a -> a.getDataHolderGroupCode()
                         + "::" + a.getRequestorGroupCode()
                         + "::" + a.getTemplateId()
                         + "::" + a.getAccessLevel()
                         + "::" + (a.getEffectiveFrom() != null ? a.getEffectiveFrom().toString() : "null")
                         + "::" + (a.getEffectiveTo() != null ? a.getEffectiveTo().toString() : "null")
                ))),
                ArrayList::new
            ));
    }

    /**
     * Active agreements for a requestor group, queried from each registered DH Group Admin (the
     * source of truth, not the local DB). Falls back to locally cached agreements with a warning
     * if a Group Admin is unreachable.
     */
    private List<ExternalApiDto.AgreementSummary> getAgreementsForGroup(
            RequestorGroup group, List<String> warnings) {
        List<ExternalApiDto.AgreementSummary> summaries = new ArrayList<>();

        if (group.getCode() == null || group.getCode().isBlank()) {
            log.warn("Requestor group '{}' (id={}) has no code set — falling back to group name as code",
                    group.getName(), group.getId());
            // Use the group name as the code so we can still query the DH Group Admin.
            // Record a warning so the admin knows to assign a proper code.
            group.setCode(group.getName());
            String warning = String.format(
                "Requestor group '%s' has no code configured. Using group name as code — "
                + "please set a proper code in the Requestor Manager to avoid issues.",
                group.getName());
            warnings.add(warning);
        }

        // Query every active DataHolderGroup for subscriptions matching this requestor group code
        List<DataHolderGroup> activeGroups = dataHolderGroupRepository.findByActiveTrue();

        for (DataHolderGroup dhGroup : activeGroups) {
            try {
                List<ExternalApiDto.AgreementSummary> fromDhGroup =
                        fetchActiveSubscriptionsFromDhGroupAdmin(dhGroup, group);
                summaries.addAll(fromDhGroup);
            } catch (Exception e) {
                log.warn("Failed to fetch subscriptions from DH Group Admin {}: {}",
                        dhGroup.getCode(), e.getMessage());

                // Fall back to local DataHolderAgreement records
                List<ExternalApiDto.AgreementSummary> fallback =
                        getFallbackAgreementsFromLocal(dhGroup, group);
                summaries.addAll(fallback);

                // Only warn the user if local records exist (meaning we know they
                // should have agreements with this DH but the live call failed).
                // If the fallback also found nothing, the group likely just has no
                // subscriptions with this DH — no need to alarm the user.
                if (!fallback.isEmpty()) {
                    String warning = String.format(
                        "Could not reach Data Holder '%s' (%s) live — showing %d locally cached agreement(s). "
                        + "Request types may be incomplete.",
                        dhGroup.getName(), dhGroup.getCode(), fallback.size());
                    warnings.add(warning);
                    log.info(warning);
                } else {
                    log.info("DH Group Admin '{}' ({}) unreachable and no local agreements found for group '{}' — skipping silently",
                            dhGroup.getName(), dhGroup.getCode(), group.getCode());
                }
            }
        }

        return summaries;
    }

    /**
     * Fall back to local DataHolderAgreement records when the live call to a
     * DH Group Admin fails. Request types are still attempted via the template
     * endpoint (which may also fail, resulting in an empty list).
     */
    private List<ExternalApiDto.AgreementSummary> getFallbackAgreementsFromLocal(
            DataHolderGroup dhGroup, RequestorGroup requestorGroup) {

        List<ExternalApiDto.AgreementSummary> results = new ArrayList<>();

        try {
            List<DataHolderAgreement> localAgreements =
                    dataHolderAgreementRepository
                        .findByDataHolderGroupAndRequestorGroupAndIsActiveTrue(dhGroup, requestorGroup);

            long idCounter = dhGroup.getId() * 10000 + requestorGroup.getId() * 100;

            for (DataHolderAgreement local : localAgreements) {
                if (!local.isCurrentlyEffective()) continue;

                // Attempt to fetch request types (may return empty if DH is down)
                List<ExternalApiDto.RequestTypeSummary> requestTypes =
                        fetchRequestTypesFromDhGroupAdmin(dhGroup, local.getTemplateId());

                results.add(ExternalApiDto.AgreementSummary.builder()
                        .id(idCounter++)
                        .name(local.getName())
                        .description(local.getDescription())
                        .requestorGroupId(requestorGroup.getId())
                        .requestorGroupName(requestorGroup.getName())
                        .requestorGroupCode(requestorGroup.getCode())
                        .dataHolderGroupCode(dhGroup.getCode())
                        .dataHolderGroupName(dhGroup.getName())
                        .templateId(local.getTemplateId())
                        .accessLevel(local.getAccessLevel())
                        .requestTypes(requestTypes)
                        .status("ACTIVE")
                        .sourceType("LOCAL_FALLBACK")
                        .effectiveFrom(local.getEffectiveFrom())
                        .effectiveTo(local.getEffectiveTo())
                        .build());
            }

            log.info("Fallback: found {} locally cached agreements from {} for group {}",
                    results.size(), dhGroup.getCode(), requestorGroup.getCode());

        } catch (Exception e) {
            log.error("Failed to load fallback agreements from local DB for DH {} / group {}: {}",
                    dhGroup.getCode(), requestorGroup.getCode(), e.getMessage());
        }

        return results;
    }

    /**
     * Call a DH Group Admin's API to get active subscriptions for a requestor group.
     * Endpoint: GET {baseUrl}/api/external/dataholder/{dataholderId}/subscriptions/by-group-code?groupCode=...
     */
    @SuppressWarnings("unchecked")
    private List<ExternalApiDto.AgreementSummary> fetchActiveSubscriptionsFromDhGroupAdmin(
            DataHolderGroup dhGroup, RequestorGroup requestorGroup) {

        List<ExternalApiDto.AgreementSummary> results = new ArrayList<>();

        String baseUrl = dhGroup.getBaseUrl();
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        baseUrl = mtlsEndpoints.resolve(baseUrl);

        // Call the public subscriptions-by-group-code endpoint
        String url = baseUrl + "/api/external/dataholder/" + dhGroup.getCode()
                + "/subscriptions/by-group-code?groupCode=" + requestorGroup.getCode();

        log.debug("Fetching active subscriptions from DH Group Admin: {}", url);

        try {
            List<Map<String, Object>> subscriptions = restTemplate.getForObject(url, List.class);

            if (subscriptions == null || subscriptions.isEmpty()) {
                log.debug("No active subscriptions from {} for group {}", dhGroup.getCode(), requestorGroup.getCode());
                return results;
            }

            long idCounter = dhGroup.getId() * 10000 + requestorGroup.getId() * 100;

            for (Map<String, Object> sub : subscriptions) {
                String status = (String) sub.get("status");
                if (!"ACTIVE".equals(status)) continue;

                String templateId = (String) sub.get("templateId");

                // Fetch request types from the template
                List<ExternalApiDto.RequestTypeSummary> requestTypes =
                        fetchRequestTypesFromDhGroupAdmin(dhGroup, templateId);

                results.add(ExternalApiDto.AgreementSummary.builder()
                        .id(idCounter++)
                        .name(sub.get("templateName") != null ? (String) sub.get("templateName") : templateId)
                        .description((String) sub.get("statusMessage"))
                        .requestorGroupId(requestorGroup.getId())
                        .requestorGroupName(requestorGroup.getName())
                        .requestorGroupCode(requestorGroup.getCode())
                        .dataHolderGroupCode(dhGroup.getCode())
                        .dataHolderGroupName(dhGroup.getName())
                        .templateId(templateId)
                        .accessLevel(toInt(sub.get("effectiveAccessLevel")))
                        .requestTypes(requestTypes)
                        .status("ACTIVE")
                        .sourceType("DH_GROUP_ADMIN")
                        .effectiveFrom(sub.get("effectiveFrom") != null ?
                                LocalDateTime.parse((String) sub.get("effectiveFrom")) : null)
                        .effectiveTo(sub.get("effectiveTo") != null ?
                                LocalDateTime.parse((String) sub.get("effectiveTo")) : null)
                        .build());
            }

            log.info("Found {} active subscriptions from {} for group {}",
                    results.size(), dhGroup.getCode(), requestorGroup.getCode());

        } catch (Exception e) {
            log.warn("Error fetching subscriptions from DH Group Admin {} for group {}: {}",
                    dhGroup.getCode(), requestorGroup.getCode(), e.getMessage());
            // Re-throw so the caller can trigger the fallback
            throw new RuntimeException("DH Group Admin unreachable: " + dhGroup.getCode(), e);
        }

        return results;
    }

    /**
     * Fetch request types from a DH Group Admin's published template endpoint.
     * Endpoint: GET {baseUrl}/api/external/templates/{templateId}
     * Results are cached briefly.
     */
    private List<ExternalApiDto.RequestTypeSummary> fetchRequestTypesFromDhGroupAdmin(
            DataHolderGroup dhGroup, String templateId) {

        if (templateId == null || templateId.isBlank()) {
            return Collections.emptyList();
        }

        String cacheKey = dhGroup.getBaseUrl() + "::" + templateId;

        CachedRequestTypes cached = requestTypesCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.requestTypes;
        }

        try {
            String baseUrl = dhGroup.getBaseUrl();
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            baseUrl = mtlsEndpoints.resolve(baseUrl);
            String url = baseUrl + "/api/external/templates/" + templateId;

            log.debug("Fetching template request types from: {}", url);

            @SuppressWarnings("unchecked")
            Map<String, Object> template = restTemplate.getForObject(url, Map.class);

            if (template == null) {
                return cacheAndReturn(cacheKey, Collections.emptyList());
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawTypes = (List<Map<String, Object>>) template.get("requestTypes");

            if (rawTypes == null || rawTypes.isEmpty()) {
                return cacheAndReturn(cacheKey, Collections.emptyList());
            }

            List<ExternalApiDto.RequestTypeSummary> result = rawTypes.stream()
                    .map(rt -> {
                        // Map custom parameters if present
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> rawParams = (List<Map<String, Object>>) rt.get("customParameters");
                        List<ExternalApiDto.CustomParameterSummary> customParams = new ArrayList<>();
                        if (rawParams != null) {
                            customParams = rawParams.stream()
                                    .map(cp -> ExternalApiDto.CustomParameterSummary.builder()
                                            .name((String) cp.get("name"))
                                            .dataType(cp.get("dataType") != null ? (String) cp.get("dataType") : "string")
                                            .required(toBool(cp.get("required")))
                                            .description((String) cp.get("description"))
                                            .defaultValue((String) cp.get("defaultValue"))
                                            .placeholder((String) cp.get("placeholder"))
                                            .enumValues((String) cp.get("enumValues"))
                                            .validationRegex((String) cp.get("validationRegex"))
                                            .minValue(cp.get("minValue") != null ? cp.get("minValue").toString() : null)
                                            .maxValue(cp.get("maxValue") != null ? cp.get("maxValue").toString() : null)
                                            .maxLength(cp.get("maxLength") != null ? toInt(cp.get("maxLength")) : null)
                                            .allowedFileTypes((String) cp.get("allowedFileTypes"))
                                            .maxFileSizeMb(cp.get("maxFileSizeMb") != null ? toInt(cp.get("maxFileSizeMb")) : null)
                                            .sortOrder(toInt(cp.get("sortOrder")))
                                            .build())
                                    .collect(Collectors.toList());
                        }

                        return ExternalApiDto.RequestTypeSummary.builder()
                                .name((String) rt.get("name"))
                                .typeCode(toInt(rt.get("typeCode")))
                                .description((String) rt.get("description"))
                                .accessLevel(toInt(rt.get("accessLevel")))
                                .supportsConfidential(toBool(rt.get("supportsConfidential")))
                                .supportsExigent(toBool(rt.get("supportsExigent")))
                                .customParameters(customParams)
                                .build();
                    })
                    .collect(Collectors.toList());

            return cacheAndReturn(cacheKey, result);

        } catch (Exception e) {
            log.warn("Failed to fetch request types from {} for template {}: {}",
                    dhGroup.getCode(), templateId, e.getMessage());
            return cacheAndReturn(cacheKey, Collections.emptyList());
        }
    }

    private List<ExternalApiDto.RequestTypeSummary> cacheAndReturn(
            String cacheKey, List<ExternalApiDto.RequestTypeSummary> requestTypes) {
        requestTypesCache.put(cacheKey, new CachedRequestTypes(requestTypes));
        return requestTypes;
    }

    private Integer toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return 0; }
    }

    private Boolean toBool(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean) return (Boolean) val;
        return Boolean.parseBoolean(val.toString());
    }

    // ==================== Cache Helper ====================

    private static class CachedRequestTypes {
        final List<ExternalApiDto.RequestTypeSummary> requestTypes;
        final long cachedAt;

        CachedRequestTypes(List<ExternalApiDto.RequestTypeSummary> requestTypes) {
            this.requestTypes = requestTypes;
            this.cachedAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > CACHE_TTL_MS;
        }
    }
    
    // ===== Schema wrapper classes for Swagger documentation =====
    
    @Schema(name = "ExternalAgreementsApiResponse", description = "API response with agreements for external API")
    private static class ExternalAgreementsResponseWrapper extends ApiResponse<ExternalApiDto.AgreementsResponse> {
        @Schema(description = "Agreements response data")
        private ExternalApiDto.AgreementsResponse data;
    }
    
    @Schema(name = "HealthResponse", description = "Health check response")
    private static class HealthResponse {
        @Schema(description = "Service status", example = "healthy")
        private String status;
        
        @Schema(description = "Service name", example = "Requestor Manager External API")
        private String service;
    }
}