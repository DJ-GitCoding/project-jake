/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.dto.RdapRequestCreate;
import com.jaddar.dto.RdapRequestUpdate;
import com.jaddar.entity.RdapRequestHistory;
import com.jaddar.entity.UserRdapSettings;
import com.jaddar.enums.RequestStatus;
import com.jaddar.dto.UserRdapSettingsRequest;
import com.jaddar.repository.RdapRequestHistoryRepository;
import com.jaddar.repository.UserRdapSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing RDAP request history and user settings.
 *
 * Ported from backend/services/rdap_request_service.py.
 *
 * In the Python service this class was instantiated per-request with a
 * data_holder_url; here it's a singleton and the data holder base URL is passed
 * into the methods that call out to the data holder. Calls to the data holder
 * use the injected dataholderWebClient with an absolute URL (the resolved data
 * holder root may differ per query via TLD matching).
 */
@Slf4j
@Service
public class RdapRequestService {

    private static final Duration DH_TIMEOUT = Duration.ofSeconds(30);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RdapRequestHistoryRepository historyRepository;
    private final UserRdapSettingsRepository settingsRepository;
    private final WebClient dataholderWebClient;
    private final ObjectMapper objectMapper;

    public RdapRequestService(RdapRequestHistoryRepository historyRepository,
                              UserRdapSettingsRepository settingsRepository,
                              @Qualifier("dataholderWebClient") WebClient dataholderWebClient,
                              ObjectMapper objectMapper) {
        this.historyRepository = historyRepository;
        this.settingsRepository = settingsRepository;
        this.dataholderWebClient = dataholderWebClient;
        this.objectMapper = objectMapper;
    }

    // ==================== Request History CRUD ====================

    @Transactional
    public RdapRequestHistory createRequest(String userSub, String userEmail, RdapRequestCreate data) {
        RdapRequestHistory r = new RdapRequestHistory();
        r.setRequestId(data.getRequestId());
        r.setUserSub(userSub);
        r.setUserEmail(userEmail);
        r.setQueryType(data.getQueryType());
        r.setQueryValue(data.getQueryValue());
        r.setStatus(RequestStatus.PENDING);
        r.setAgreementsUsed(data.getAgreementsUsed());
        r.setAccessLevelRequested(data.getAccessLevelRequested());
        r.setDataHolderId(data.getDataHolderId());
        r.setDataHolderName(data.getDataHolderName());
        r.setExpiresAt(data.getExpiresAt());
        RdapRequestHistory saved = historyRepository.save(r);
        log.info("Created request history: {}", saved.getRequestId());
        return saved;
    }

    public RdapRequestHistory getRequestById(String requestId) {
        return historyRepository.findByRequestId(requestId).orElse(null);
    }

    public List<RdapRequestHistory> getRequestsForUser(String userSub, RequestStatus status,
                                                       int limit, int offset) {
        List<RdapRequestHistory> all = (status != null)
                ? historyRepository.findByUserSubAndStatusOrderByCreatedAtDesc(userSub, status)
                : historyRepository.findByUserSubOrderByCreatedAtDesc(userSub);
        int from = Math.min(offset, all.size());
        int to = Math.min(from + limit, all.size());
        return all.subList(from, to);
    }

    public Map<String, Integer> getRequestCountsForUser(String userSub) {
        List<RdapRequestHistory> requests = historyRepository.findByUserSubOrderByCreatedAtDesc(userSub);
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("total", requests.size());
        counts.put("pending", 0);
        counts.put("approved", 0);
        counts.put("denied", 0);
        counts.put("error", 0);
        counts.put("cancelled", 0);
        for (RdapRequestHistory req : requests) {
            switch (req.getStatus()) {
                case PENDING -> counts.merge("pending", 1, Integer::sum);
                case APPROVED -> counts.merge("approved", 1, Integer::sum);
                case DENIED -> counts.merge("denied", 1, Integer::sum);
                case ERROR -> counts.merge("error", 1, Integer::sum);
                case CANCELLED -> counts.merge("cancelled", 1, Integer::sum);
            }
        }
        return counts;
    }

    @Transactional
    public RdapRequestHistory updateRequest(String requestId, RdapRequestUpdate data) {
        RdapRequestHistory r = getRequestById(requestId);
        if (r == null) {
            return null;
        }
        if (data.getStatus() != null) {
            r.setStatus(data.getStatus());
        }
        if (data.getAccessLevelGranted() != null) {
            r.setAccessLevelGranted(data.getAccessLevelGranted());
        }
        if (data.getRdapData() != null) {
            r.setRdapData(data.getRdapData());
        }
        if (data.getErrorMessage() != null) {
            r.setErrorMessage(data.getErrorMessage());
        }
        if (data.getResolvedAt() != null) {
            r.setResolvedAt(data.getResolvedAt());
        }
        RdapRequestHistory saved = historyRepository.save(r);
        log.info("Updated request {} to status {}", requestId, saved.getStatus());
        return saved;
    }

    @Transactional
    public boolean deleteRequest(String requestId) {
        RdapRequestHistory r = getRequestById(requestId);
        if (r == null) {
            return false;
        }
        historyRepository.delete(r);
        return true;
    }

    // ==================== User Settings CRUD ====================

    public UserRdapSettings getUserSettings(String userSub) {
        return settingsRepository.findByUserSub(userSub).orElse(null);
    }

    @Transactional
    public UserRdapSettings getOrCreateUserSettings(String userSub, String userEmail) {
        Optional<UserRdapSettings> existing = settingsRepository.findByUserSub(userSub);
        if (existing.isPresent()) {
            return existing.get();
        }
        UserRdapSettings s = new UserRdapSettings();
        s.setUserSub(userSub);
        s.setUserEmail(userEmail);
        s.setDefaultPollIntervalMs(30000);
        s.setAutoPollEnabled(true);
        s.setNotifyOnApproval(true);
        s.setNotifyOnDenial(true);
        UserRdapSettings saved = settingsRepository.save(s);
        log.info("Created default settings for user {}", userSub);
        return saved;
    }

    @Transactional
    public UserRdapSettings updateUserSettings(String userSub, String userEmail,
                                               UserRdapSettingsRequest data) {
        UserRdapSettings s = getOrCreateUserSettings(userSub, userEmail);
        if (data.getDefaultPollIntervalMs() != null) {
            s.setDefaultPollIntervalMs(data.getDefaultPollIntervalMs());
        }
        if (data.getAutoPollEnabled() != null) {
            s.setAutoPollEnabled(data.getAutoPollEnabled());
        }
        if (data.getNotifyOnApproval() != null) {
            s.setNotifyOnApproval(data.getNotifyOnApproval());
        }
        if (data.getNotifyOnDenial() != null) {
            s.setNotifyOnDenial(data.getNotifyOnDenial());
        }
        if (userEmail != null) {
            s.setUserEmail(userEmail);
        }
        UserRdapSettings saved = settingsRepository.save(s);
        log.info("Updated settings for user {}", userSub);
        return saved;
    }

    // ==================== Data Holder Sync ====================

    /**
     * Fetch user's requests from the data holder and sync the local database.
     * Returns the (possibly updated) request list for the user.
     */
    public List<RdapRequestHistory> syncRequestsFromDataHolder(String dataHolderUrl, String userSub,
                                                               String userEmail, String token) {
        List<String> pendingRdapFetches = new ArrayList<>();
        try {
            String body = dataholderWebClient.get()
                    .uri(dataHolderUrl + "/api/rdap/my-requests?email={email}", userEmail)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("X-Requestor-Email", userEmail)
                    .header("X-Requestor-Sub", userSub)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(DH_TIMEOUT);

            if (body != null) {
                Map<String, Object> data = objectMapper.readValue(body, MAP_TYPE);
                Object success = data.get("success");
                List<?> requests = data.get("requests") instanceof List<?> l ? l : List.of();
                log.info("Data holder response: success={}, requests_count={}", success, requests.size());
                if (Boolean.TRUE.equals(success)) {
                    processDataHolderRequests(userSub, userEmail, requests, pendingRdapFetches);
                }
            }
        } catch (WebClientResponseException e) {
            log.warn("Failed to fetch requests from data holder: {}", e.getStatusCode().value());
        } catch (Exception e) {
            log.error("Error syncing with data holder: {}", e.toString());
        }

        // Fetch RDAP data for approved requests via the redaction-aware status endpoint.
        for (String reqId : pendingRdapFetches) {
            try {
                Map<String, Object> dhResponse = checkRequestStatusOnDataHolder(dataHolderUrl, reqId, token);
                if (dhResponse != null
                        && (dhResponse.containsKey("rdapConformance") || dhResponse.containsKey("objectClassName"))) {
                    RdapRequestUpdate update = new RdapRequestUpdate();
                    update.setRdapData(dhResponse);
                    update.setAccessLevelGranted(asInteger(dhResponse.get("accessLevel")));
                    updateRequest(reqId, update);
                    log.info("Fetched redacted RDAP data for approved request {}", reqId);
                } else {
                    log.info("Status check for {} returned non-RDAP response, skipping", reqId);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch RDAP data for {}: {}", reqId, e.toString());
            }
        }

        return getRequestsForUser(userSub, null, Integer.MAX_VALUE, 0);
    }

    @SuppressWarnings("unchecked")
    private void processDataHolderRequests(String userSub, String userEmail, List<?> dhRequests,
                                           List<String> pendingRdapFetches) {
        for (Object reqObj : dhRequests) {
            if (!(reqObj instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> dhReq = (Map<String, Object>) raw;
            String requestId = asString(dhReq.get("requestId"));
            if (requestId == null) {
                continue;
            }

            RdapRequestHistory localReq = getRequestById(requestId);
            String dhStatus = asString(dhReq.get("status"), "").toLowerCase();
            log.info("Processing request {}: dh_status={}, local_exists={}", requestId, dhStatus, localReq != null);

            if (localReq == null) {
                // Try to re-link an existing "local-" placeholder for the same query.
                String queryValue = asString(dhReq.get("queryValue"), "unknown");
                String queryType = asString(dhReq.get("queryType"), "unknown");
                RdapRequestHistory existing = findLocalPlaceholder(userSub, queryValue, queryType);
                if (existing != null) {
                    log.info("Re-linking local record {} -> {}", existing.getRequestId(), requestId);
                    existing.setRequestId(requestId);
                    List<String> agreementNames = asStringList(dhReq.get("agreementNames"));
                    if (agreementNames != null) {
                        existing.setAgreementsUsed(agreementNames);
                    }
                    Integer accessLevel = asInteger(dhReq.get("requestedAccessLevel"));
                    if (accessLevel != null) {
                        existing.setAccessLevelRequested(accessLevel);
                        existing.setAccessLevelGranted(accessLevel);
                    }
                    localReq = historyRepository.save(existing);
                }
            }

            if (localReq != null) {
                RequestStatus newStatus = mapStatus(dhStatus);
                boolean approvedNeedsData = "approved".equals(dhStatus) && localReq.getRdapData() == null;
                if (localReq.getStatus() != newStatus || approvedNeedsData) {
                    String denialError = null;
                    if ("denied".equals(dhStatus)) {
                        denialError = firstNonEmpty(asString(dhReq.get("denialReason")), asString(dhReq.get("adminNotes")));
                    }
                    RdapRequestUpdate update = new RdapRequestUpdate();
                    update.setStatus(newStatus);
                    update.setAccessLevelGranted(asInteger(dhReq.get("requestedAccessLevel")));
                    // Don't store responseData from sync — fetch via the status endpoint.
                    if (isResolved(dhStatus)) {
                        update.setResolvedAt(Instant.now());
                    }
                    update.setErrorMessage(denialError);
                    updateRequest(requestId, update);
                    log.info("Updated request {} to {}", requestId, newStatus);

                    if (approvedNeedsData) {
                        pendingRdapFetches.add(requestId);
                    }
                }
            } else {
                RdapRequestCreate create = new RdapRequestCreate();
                create.setRequestId(requestId);
                create.setQueryType(asString(dhReq.get("queryType"), "unknown"));
                create.setQueryValue(asString(dhReq.get("queryValue"), "unknown"));
                create.setAgreementsUsed(asStringList(dhReq.get("agreementNames")));
                create.setAccessLevelRequested(asInteger(dhReq.get("requestedAccessLevel")));
                create.setDataHolderId("TDH-001");
                create.setDataHolderName("Test Data Holder");
                create.setExpiresAt(asInstant(dhReq.get("expiresAt")));
                createRequest(userSub, userEmail, create);
                log.info("Created new local request {}", requestId);

                if (isResolved(dhStatus)) {
                    String denialError = null;
                    if ("denied".equals(dhStatus)) {
                        denialError = firstNonEmpty(asString(dhReq.get("denialReason")), asString(dhReq.get("adminNotes")));
                    }
                    RdapRequestUpdate update = new RdapRequestUpdate();
                    update.setStatus(mapStatus(dhStatus));
                    update.setAccessLevelGranted(asInteger(dhReq.get("requestedAccessLevel")));
                    update.setResolvedAt(Instant.now());
                    update.setErrorMessage(denialError);
                    updateRequest(requestId, update);

                    if ("approved".equals(dhStatus)) {
                        pendingRdapFetches.add(requestId);
                    }
                }
            }
        }
    }

    private RdapRequestHistory findLocalPlaceholder(String userSub, String queryValue, String queryType) {
        // Mirror the Python query: user + value + type + request_id LIKE 'local-%', newest first.
        return historyRepository.findByUserSubOrderByCreatedAtDesc(userSub).stream()
                .filter(r -> queryValue.equals(r.getQueryValue())
                        && queryType.equals(r.getQueryType())
                        && r.getRequestId() != null && r.getRequestId().startsWith("local-"))
                .findFirst()
                .orElse(null);
    }

    public static RequestStatus mapStatus(String statusStr) {
        if (statusStr == null) {
            return RequestStatus.ERROR;
        }
        return switch (statusStr.toLowerCase()) {
            case "pending" -> RequestStatus.PENDING;
            case "approved" -> RequestStatus.APPROVED;
            case "denied" -> RequestStatus.DENIED;
            case "error" -> RequestStatus.ERROR;
            case "cancelled" -> RequestStatus.CANCELLED;
            default -> RequestStatus.ERROR;
        };
    }

    private static boolean isResolved(String dhStatus) {
        return "approved".equals(dhStatus) || "denied".equals(dhStatus) || "cancelled".equals(dhStatus);
    }

    /**
     * Cancel a pending request on the data holder. On success, also updates the
     * local request status to CANCELLED. Returns a map with success/error.
     */
    public Map<String, Object> cancelRequestOnDataHolder(String dataHolderUrl, String requestId, String token,
                                                         String userEmail, String userSub, String reason) {
        try {
            String cancelMessage = reason != null
                    ? "Cancelled by requestor: " + reason : "Cancelled by requestor";

            WebClient.RequestBodySpec spec = dataholderWebClient.post()
                    .uri(dataHolderUrl + "/api/rdap/my-requests/{id}/cancel", requestId);
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            if (userEmail != null) {
                spec.header("X-Requestor-Email", userEmail);
            }
            if (userSub != null) {
                spec.header("X-Requestor-Sub", userSub);
            }

            WebClient.RequestHeadersSpec<?> finalSpec = (reason != null)
                    ? spec.bodyValue(Map.of("reason", reason)) : spec;

            String body = finalSpec.retrieve()
                    .bodyToMono(String.class)
                    .block(DH_TIMEOUT);

            Map<String, Object> result = body != null ? objectMapper.readValue(body, MAP_TYPE) : new HashMap<>();
            // Update local status.
            try {
                RdapRequestUpdate update = new RdapRequestUpdate();
                update.setStatus(RequestStatus.CANCELLED);
                update.setResolvedAt(Instant.now());
                update.setErrorMessage(cancelMessage);
                updateRequest(requestId, update);
                log.info("Cancelled request {} on data holder", requestId);
            } catch (Exception dbErr) {
                log.error("Data holder cancelled OK but local DB update failed: {}", dbErr.toString());
                return Map.of("success", false, "error", "Local DB update failed: " + dbErr.getMessage());
            }
            return result;
        } catch (WebClientResponseException e) {
            Map<String, Object> errBody;
            try {
                errBody = objectMapper.readValue(e.getResponseBodyAsString(), MAP_TYPE);
            } catch (Exception ignored) {
                errBody = new HashMap<>();
            }
            Object err = errBody.getOrDefault("error", "HTTP " + e.getStatusCode().value());
            log.warn("Cancel request {} failed: {}", requestId, e.getStatusCode().value());
            Map<String, Object> out = new HashMap<>();
            out.put("success", false);
            out.put("error", err);
            return out;
        } catch (Exception e) {
            log.error("Error cancelling request on data holder: {}", e.toString());
            Map<String, Object> out = new HashMap<>();
            out.put("success", false);
            out.put("error", e.getMessage());
            return out;
        }
    }

    /**
     * Check the status of a request on the data holder.
     * Returns the JSON body for 200/202/403/410, or null for 404 / unexpected.
     */
    public Map<String, Object> checkRequestStatusOnDataHolder(String dataHolderUrl, String requestId, String token) {
        try {
            String body = dataholderWebClient.get()
                    .uri(dataHolderUrl + "/api/rdap/status/{id}", requestId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(DH_TIMEOUT);
            return body != null ? objectMapper.readValue(body, MAP_TYPE) : new HashMap<>();
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 403 || status == 410) {
                try {
                    return objectMapper.readValue(e.getResponseBodyAsString(), MAP_TYPE);
                } catch (Exception ignored) {
                    return null;
                }
            } else if (status == 404) {
                log.info("Request {} not found on data holder (404)", requestId);
                return null;
            }
            log.warn("Status check returned unexpected {} for {}", status, requestId);
            return null;
        } catch (Exception e) {
            log.error("Error checking request status: {}", e.toString());
            return null;
        }
    }

    // ==================== Helpers ====================

    private static String asString(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    private static String asString(Object o, String dflt) {
        return o != null ? String.valueOf(o) : dflt;
    }

    private static Integer asInteger(Object o) {
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

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object o) {
        if (!(o instanceof List<?> list)) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            out.add(String.valueOf(item));
        }
        return out;
    }

    private static Instant asInstant(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(o));
        } catch (Exception e) {
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
}
