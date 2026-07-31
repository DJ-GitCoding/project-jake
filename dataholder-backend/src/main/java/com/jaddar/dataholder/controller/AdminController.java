/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import com.jaddar.dataholder.dto.RdapDto.*;
import com.jaddar.dataholder.dto.PolicyExpressionDto.*;
import com.jaddar.dataholder.entity.DataHolderConfig;
import com.jaddar.dataholder.entity.RequestAuditLog;
import com.jaddar.dataholder.repository.DataHolderConfigRepository;
import com.jaddar.dataholder.repository.RequestAuditLogRepository;
import com.jaddar.dataholder.service.AccessControlService;
import com.jaddar.dataholder.service.PendingRequestService;
import com.jaddar.dataholder.service.PolicyExpressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final PendingRequestService pendingRequestService;
    private final AccessControlService accessControlService;
    private final PolicyExpressionService policyExpressionService;
    private final RequestAuditLogRepository auditLogRepository;
    private final DataHolderConfigRepository configRepository;

    // ==================== Global Configuration ====================

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        DataHolderConfig config = configRepository.findById(DataHolderConfig.SINGLETON_ID)
                .orElseGet(() -> {
                    DataHolderConfig c = DataHolderConfig.builder()
                            .id(DataHolderConfig.SINGLETON_ID)
                            .requireManualReviewAll(false)
                            .build();
                    return configRepository.save(c);
                });
        return ResponseEntity.ok(Map.of(
                "success", true,
                "config", Map.of(
                        "requireManualReviewAll", config.getRequireManualReviewAll(),
                        "updatedAt", config.getUpdatedAt() != null ? config.getUpdatedAt().toString() : "",
                        "updatedBy", config.getUpdatedBy() != null ? config.getUpdatedBy() : ""
                )
        ));
    }

    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody Map<String, Object> body) {
        DataHolderConfig config = configRepository.findById(DataHolderConfig.SINGLETON_ID)
                .orElseGet(() -> DataHolderConfig.builder()
                        .id(DataHolderConfig.SINGLETON_ID)
                        .requireManualReviewAll(false)
                        .build());

        if (body.containsKey("requireManualReviewAll")) {
            config.setRequireManualReviewAll(Boolean.TRUE.equals(body.get("requireManualReviewAll")));
        }
        config.setUpdatedBy((String) body.getOrDefault("updatedBy", "admin"));

        config = configRepository.save(config);
        log.info("Global config updated: requireManualReviewAll={} by {}",
                config.getRequireManualReviewAll(), config.getUpdatedBy());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "config", Map.of(
                        "requireManualReviewAll", config.getRequireManualReviewAll(),
                        "updatedAt", config.getUpdatedAt() != null ? config.getUpdatedAt().toString() : "",
                        "updatedBy", config.getUpdatedBy() != null ? config.getUpdatedBy() : ""
                )
        ));
    }

    // ==================== Pending Requests ====================

    @GetMapping("/pending-requests")
    public ResponseEntity<List<PendingRequestDto>> getPendingRequests() {
        return ResponseEntity.ok(pendingRequestService.getPendingRequests());
    }

    @GetMapping("/pending-requests/{requestId}")
    public ResponseEntity<?> getPendingRequest(@PathVariable String requestId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(requestId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid request ID format"));
        }
        return pendingRequestService.getPendingRequest(uuid)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/pending-requests/{requestId}/review")
    public ResponseEntity<?> reviewRequest(
            @PathVariable String requestId,
            @RequestBody ReviewRequest reviewRequest,
            Authentication authentication) {
        UUID uuid;
        try {
            uuid = UUID.fromString(requestId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid request ID format"));
        }
        try {
            String reviewedBy = authentication != null ? authentication.getName() : "admin";
            PendingRequestDto result = pendingRequestService.reviewRequest(
                    uuid, reviewRequest.getAction(), reviewRequest.getAdminNotes(),
                    reviewRequest.getGrantedAccessLevel(), reviewedBy, reviewRequest.getDenialReason());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/pending-requests/stats")
    public ResponseEntity<Map<String, Object>> getRequestStats() {
        return ResponseEntity.ok(pendingRequestService.getStatistics());
    }

    // ==================== Active Subscriptions (from Group Admin) ====================

    /** Active subscriptions with access levels, fetched from the Group Admin (not local DB). */
    @GetMapping("/agreement-levels")
    public ResponseEntity<List<Map<String, Object>>> getAgreementLevels() {
        return ResponseEntity.ok(accessControlService.getAvailableSubscriptionMaps());
    }

    @GetMapping("/subscriptions/active")
    public ResponseEntity<List<Map<String, Object>>> getActiveSubscriptions() {
        return ResponseEntity.ok(accessControlService.getAvailableSubscriptionMaps());
    }

    // ==================== Policy Expressions ====================

    @GetMapping("/policy")
    public ResponseEntity<PolicyExpressionResponse> getActivePolicy() {
        return policyExpressionService.getDefaultPolicy()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/policy")
    public ResponseEntity<?> updatePolicy(@RequestBody PolicyExpressionRequest request) {
        try {
            Optional<PolicyExpressionResponse> existingOpt = policyExpressionService.getDefaultPolicy();
            PolicyExpressionResponse result;
            if (existingOpt.isPresent()) {
                result = policyExpressionService.updatePolicyExpression(existingOpt.get().getId(), request);
            } else {
                request.setIsDefault(true);
                request.setIsActive(true);
                if (request.getName() == null) request.setName("Default Policy");
                result = policyExpressionService.createPolicyExpression(request);
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update policy", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "An unexpected error occurred. Please try again or contact support."));
        }
    }

    @GetMapping("/policy/enum-values")
    public ResponseEntity<PolicyEnumValues> getPolicyEnumValues() {
        return ResponseEntity.ok(policyExpressionService.getEnumValues());
    }

    // ==================== Audit Logs ====================

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLogDto>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String severity) {
        Page<RequestAuditLog> logs;
        Pageable pageable = PageRequest.of(page, size);

        if (eventType != null && !eventType.isBlank()) {
            try {
                RequestAuditLog.EventType type = RequestAuditLog.EventType.valueOf(eventType);
                logs = auditLogRepository.findByEventTypeOrderByRequestTimestampDesc(type, pageable);
            } catch (IllegalArgumentException e) {
                logs = auditLogRepository.findAllByOrderByRequestTimestampDesc(pageable);
            }
        } else if (severity != null && !severity.isBlank()) {
            try {
                RequestAuditLog.Severity sev = RequestAuditLog.Severity.valueOf(severity);
                logs = auditLogRepository.findBySeverityOrderByRequestTimestampDesc(sev, pageable);
            } catch (IllegalArgumentException e) {
                logs = auditLogRepository.findAllByOrderByRequestTimestampDesc(pageable);
            }
        } else {
            logs = auditLogRepository.findAllByOrderByRequestTimestampDesc(pageable);
        }
        return ResponseEntity.ok(logs.map(this::toDto));
    }

    // ==================== Health / Info ====================

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> adminHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("pendingRequests", pendingRequestService.getStatistics());
        health.put("activeSubscriptions", accessControlService.getAvailableSubscriptionMaps().size());
        policyExpressionService.getDefaultPolicy().ifPresent(policy -> {
            health.put("activePolicy", Map.of(
                    "id", policy.getId(), "name", policy.getName()));
        });
        return ResponseEntity.ok(health);
    }

    // ==================== DTO Converters ====================

    private AuditLogDto toDto(RequestAuditLog entity) {
        return AuditLogDto.builder()
                .id(entity.getId())
                .eventType(entity.getEventType() != null ? entity.getEventType().name() : "RDAP_QUERY")
                .severity(entity.getSeverity() != null ? entity.getSeverity().name() : "INFO")
                .queryType(entity.getQueryType()).queryValue(entity.getQueryValue())
                .requestorUsername(entity.getRequestorUsername()).requestorIp(entity.getRequestorIp())
                .agreementNames(entity.getAgreementNames() != null ? Arrays.asList(entity.getAgreementNames()) : null)
                .accessLevelRequested(entity.getAccessLevelRequested())
                .accessLevelGranted(entity.getAccessLevelGranted())
                .result(entity.getResult()).resultMessage(entity.getResultMessage())
                .confidential(entity.isConfidential()).exigent(entity.isExigent())
                .jakeCompliance(entity.isJakeCompliance())
                .requestTimestamp(entity.getRequestTimestamp()).responseTimeMs(entity.getResponseTimeMs())
                .threatType(entity.getThreatType())
                .originalFilename(entity.getOriginalFilename())
                .fileType(entity.getFileType())
                .detectedMimeType(entity.getDetectedMimeType())
                .fileSizeBytes(entity.getFileSizeBytes())
                .fileHash(entity.getFileHash())
                .userAgent(entity.getUserAgent())
                .build();
    }

    // ==================== Request Management Endpoints ====================

    @GetMapping("/requests")
    public ResponseEntity<?> getAllRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            if (page == null) {
                // Backward-compatible: no pagination params -> full (limited) list.
                List<PendingRequestDto> requests = pendingRequestService.getAllRequests(status);
                if (requests.size() > limit) requests = requests.subList(0, limit);
                return ResponseEntity.ok(Map.of("success", true, "requests", requests,
                        "total", requests.size(), "statusFilter", status != null ? status : "all"));
            }
            Sort sort = Sort.by("asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
            Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 500), sort);
            Page<PendingRequestDto> pageResult = pendingRequestService.getAllRequests(status, search, pageable);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("content", pageResult.getContent());
            body.put("totalElements", pageResult.getTotalElements());
            body.put("totalPages", pageResult.getTotalPages());
            body.put("page", pageResult.getNumber());
            body.put("size", pageResult.getSize());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("Failed to get requests", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "An unexpected error occurred. Please try again or contact support."));
        }
    }

    @GetMapping("/requests/{requestId}")
    public ResponseEntity<?> getRequest(@PathVariable String requestId) {
        try {
            UUID uuid = UUID.fromString(requestId);
            return pendingRequestService.getPendingRequest(uuid)
                    .map(request -> ResponseEntity.ok(Map.of("success", true, "request", request)))
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid request ID format"));
        }
    }

    @PutMapping("/requests/{requestId}")
    public ResponseEntity<?> updateRequest(@PathVariable String requestId, @RequestBody Map<String, Object> body) {
        try {
            UUID uuid = UUID.fromString(requestId);
            String newStatus = (String) body.get("status");
            String adminNotes = (String) body.get("adminNotes");
            Integer accessLevel = body.get("accessLevel") != null ? ((Number) body.get("accessLevel")).intValue() : null;
            String updatedBy = (String) body.getOrDefault("updatedBy", "admin");
            String denialReason = (String) body.get("denialReason");
            PendingRequestDto updated = pendingRequestService.updateRequest(uuid, newStatus, adminNotes, accessLevel, updatedBy, denialReason);
            return ResponseEntity.ok(Map.of("success", true, "request", updated, "message", "Request updated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update request", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "An unexpected error occurred. Please try again or contact support."));
        }
    }

    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<?> deleteRequest(@PathVariable String requestId) {
        try {
            UUID uuid = UUID.fromString(requestId);
            pendingRequestService.deleteRequest(uuid);
            return ResponseEntity.ok(Map.of("success", true, "message", "Request deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete request", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "An unexpected error occurred. Please try again or contact support."));
        }
    }

    @PostMapping("/requests/bulk-update")
    public ResponseEntity<?> bulkUpdateRequests(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> requestIds = (List<String>) body.get("requestIds");
            String action = (String) body.get("action");
            String adminNotes = (String) body.get("adminNotes");
            String updatedBy = (String) body.getOrDefault("updatedBy", "admin");
            if (requestIds == null || requestIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "No request IDs provided"));
            }
            int successCount = 0, failCount = 0;
            List<String> errors = new ArrayList<>();
            for (String reqId : requestIds) {
                try {
                    UUID uuid = UUID.fromString(reqId);
                    if ("delete".equalsIgnoreCase(action)) {
                        pendingRequestService.deleteRequest(uuid);
                    } else {
                        String newStatus = "approve".equalsIgnoreCase(action) ? "APPROVED" : "DENIED";
                        pendingRequestService.updateRequest(uuid, newStatus, adminNotes, null, updatedBy);
                    }
                    successCount++;
                } catch (Exception e) { failCount++; log.error("Bulk update failed for request {}", reqId, e); errors.add(reqId + ": operation failed"); }
            }
            return ResponseEntity.ok(Map.of("success", true, "processed", successCount + failCount,
                    "successCount", successCount, "failCount", failCount, "errors", errors));
        } catch (Exception e) {
            log.error("Failed to bulk update requests", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "An unexpected error occurred. Please try again or contact support."));
        }
    }
}
