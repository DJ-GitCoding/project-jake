/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.controller;

import com.jaddar.dhgroupadmin.entity.*;
import com.jaddar.dhgroupadmin.entity.AgreementSubscription.SubscriptionStatus;
import com.jaddar.dhgroupadmin.repository.*;
import com.jaddar.dhgroupadmin.service.AgreementSubscriptionService;
import com.jaddar.dhgroupadmin.service.AuditService;
import com.jaddar.dhgroupadmin.service.ResponseMapper;
import com.jaddar.dhgroupadmin.service.SubscriptionTestService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * External API for data holders (fetch templates/subscriptions) and requestor managers
 * (discover templates, initiate subscriptions, drive the testing/activation workflow).
 */
@RestController
@RequestMapping("/api/external")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ExternalController {

    private final AgreementTemplateRepository templateRepository;
    private final AgreementSubscriptionRepository subscriptionRepository;
    private final AgreementSubscriptionService subscriptionService;
    private final SubscriptionTestService subscriptionTestService;
    private final DataHolderRepository dataHolderRepository;
    private final DataHolderCredentialRepository credentialRepository;
    private final RequestorGroupRepository requestorGroupRepository;
    private final DataHolderGroupRepository dataHolderGroupRepository;
    private final DataHolderApplicationRepository dataHolderApplicationRepository;
    private final ResponseMapper mapper;
    private final AuditService audit;

    private String authenticateDataHolder(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) return null;
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = decoded.split(":", 2);
            if (parts.length != 2) return null;
            var credOpt = credentialRepository.findByClientIdAndIsActiveTrue(parts[0]);
            /* verifySecret() accepts a BCrypt hash or a plaintext secret. */
            if (credOpt.isEmpty() || !credOpt.get().verifySecret(parts[1])) return null;
            DataHolderCredential cred = credOpt.get();
            cred.setLastUsedAt(LocalDateTime.now());
            credentialRepository.save(cred);
            return cred.getDataholderId();
        } catch (Exception e) {
            log.warn("Failed to authenticate data holder: {}", e.getMessage());
            audit.logApi("AUTH_FAILED", "DATA_HOLDER", null, null, "unknown",
                    AuditService.SRC_EXTERNAL, "Authentication failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Resolve the data holder group ID(s) that a data holder belongs to.
     * A data holder may belong to multiple groups via the join table.
     */
    private List<Long> resolveGroupIds(String dataholderId) {
        return dataHolderRepository.findByDataholderId(dataholderId)
                .<List<Long>>map(dh -> new java.util.ArrayList<>(dh.getAllGroupIds()))
                .orElse(List.of());
    }

    // ==================== Template Discovery ====================

    @GetMapping("/templates/{templateId}")
    public ResponseEntity<?> getTemplate(@PathVariable String templateId) {
        return templateRepository.findByTemplateId(templateId)
                .filter(AgreementTemplate::getIsPublished)
                .map(t -> ResponseEntity.ok(mapper.toTemplateResponse(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Subscription Initiation ====================

    @PostMapping("/initiate")
    public ResponseEntity<?> initiateSubscription(@RequestBody InitiationRequest request) {
        log.info("Subscription initiation from: {} for dataholder: {}",
                request.getRequestorGroupName(), request.getDataholderId());

        try {
            AgreementTemplate template = templateRepository.findByTemplateId(request.getTemplateId())
                    .orElseThrow(() -> new IllegalArgumentException("Template not found: " + request.getTemplateId()));

            AgreementSubscription subscription = AgreementSubscription.builder()
                    .template(template)
                    .dataHolderGroupId(template.getDataHolderGroupId())
                    .dataholderId(request.getDataholderId())
                    .dataholderName(request.getDataholderName())
                    .dataholderUrl(request.getDataholderUrl())
                    .requestorGroupId(request.getRequestorGroupId())
                    .requestorGroupName(request.getRequestorGroupName())
                    .requestorGroupCode(request.getRequestorGroupCode())
                    .requestorGroupType(request.getRequestorGroupType())
                    .requestorDescription(request.getRequestorDescription())
                    .requestorFirstName(request.getRequestorFirstName())
                    .requestorLastName(request.getRequestorLastName())
                    .requestorOrganization(request.getRequestorOrganization())
                    .requestorContactEmail(request.getRequestorContactEmail())
                    .requestorPhone(request.getRequestorPhone())
                    .requestorAddress(request.getRequestorAddress())
                    .requestorCity(request.getRequestorCity())
                    .requestorStateProvince(request.getRequestorStateProvince())
                    .requestorPostalCode(request.getRequestorPostalCode())
                    .requestorCountry(request.getRequestorCountry())
                    .requestorAgentId(request.getRequestorAgentId())
                    .requestorAgentUrl(request.getRequestorAgentUrl())
                    .requestorAgentCallbackUrl(request.getCallbackUrl())
                    .introspectionUrl(request.getIntrospectionUrl())
                    .purpose(request.getPurpose())
                    .additionalTerms(request.getAdditionalTerms())
                    .build();

            subscription = subscriptionService.initiateSubscription(subscription);

            audit.logApi("INITIATE_SUBSCRIPTION", "SUBSCRIPTION", subscription.getRequestId(),
                    request.getRequestorGroupName(), request.getRequestorGroupName(),
                    AuditService.SRC_EXTERNAL,
                    "Initiated subscription for dataholder " + request.getDataholderId() + " using template " + request.getTemplateId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "requestId", subscription.getRequestId(),
                    "status", subscription.getStatus().name(),
                    "message", subscription.getStatusMessage(),
                    "expiresAt", subscription.getRequestExpiresAt().toString()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            audit.logEvent(AuditService.CAT_API, "INITIATE_SUBSCRIPTION_FAILED", "SUBSCRIPTION",
                    null, request.getRequestorGroupName(), request.getRequestorGroupName() != null ? request.getRequestorGroupName() : "unknown",
                    AuditService.SRC_EXTERNAL, AuditService.RESULT_FAILURE, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ==================== Status ====================

    @GetMapping("/status/{requestId}")
    public ResponseEntity<?> getSubscriptionStatus(@PathVariable String requestId) {
        return subscriptionRepository.findByRequestId(requestId)
                .map(s -> {
                    var response = new java.util.LinkedHashMap<String, Object>();
                    response.put("requestId", s.getRequestId());
                    response.put("status", s.getStatus().name());
                    response.put("statusMessage", s.getStatusMessage() != null ? s.getStatusMessage() : "");
                    response.put("statusChangedAt", s.getStatusChangedAt() != null ? s.getStatusChangedAt().toString() : "");
                    response.put("effectiveAccessLevel", s.getEffectiveAccessLevel());
                    response.put("effectiveFrom", s.getEffectiveFrom() != null ? s.getEffectiveFrom().toString() : "");
                    response.put("effectiveTo", s.getEffectiveTo() != null ? s.getEffectiveTo().toString() : "");
                    response.put("testResult", s.getTestResult() != null ? s.getTestResult() : "");
                    response.put("testCompletedAt", s.getTestCompletedAt() != null ? s.getTestCompletedAt().toString() : "");
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Testing Workflow ====================

    @PostMapping("/{requestId}/start-testing")
    @Transactional(noRollbackFor = {IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<?> startTesting(@PathVariable String requestId,
                                           @RequestBody(required = false) WorkflowRequest request) {
        try {
            String initiatedBy = request != null && request.getInitiatedBy() != null
                    ? request.getInitiatedBy() : "EXTERNAL";
            AgreementSubscription sub = subscriptionService.startTestingByRequestId(requestId, initiatedBy);

            audit.logApi("START_TESTING", "SUBSCRIPTION", requestId, sub.getRequestorGroupName(),
                    initiatedBy, AuditService.SRC_EXTERNAL, "External testing started");

            return ResponseEntity.ok(Map.of("success", true, "requestId", requestId,
                    "status", sub.getStatus().name(), "message", "Testing started"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{requestId}/activate")
    @Transactional(noRollbackFor = {IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<?> activate(@PathVariable String requestId,
                                       @RequestBody(required = false) WorkflowRequest request) {
        try {
            String activatedBy = request != null && request.getInitiatedBy() != null
                    ? request.getInitiatedBy() : "EXTERNAL";
            AgreementSubscription sub = subscriptionService.activateByRequestId(requestId, activatedBy);

            audit.logApi("ACTIVATE", "SUBSCRIPTION", requestId, sub.getRequestorGroupName(),
                    activatedBy, AuditService.SRC_EXTERNAL, "External activation");

            return ResponseEntity.ok(Map.of("success", true, "requestId", requestId,
                    "status", sub.getStatus().name(), "message", "Subscription activated",
                    "effectiveAccessLevel", sub.getEffectiveAccessLevel(),
                    "effectiveFrom", sub.getEffectiveFrom().toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{requestId}/run-test")
    @Transactional(noRollbackFor = {IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<?> runTest(@PathVariable String requestId,
                                      @RequestBody(required = false) Map<String, Object> request) {
        SubscriptionTestService.TestReport report;
        try {
            report = subscriptionTestService.runTest(requestId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }

        var sub = subscriptionRepository.findByRequestId(requestId).orElse(null);
        // The audit trail is admin-only, so it records the technical cause rather than the
        // sanitized text the requestor sees.
        String auditDetail = report.getInternalDetails() == null || report.getInternalDetails().isBlank()
                ? report.getDetails()
                : report.getInternalDetails();
        audit.logApi("RUN_TEST", "SUBSCRIPTION", requestId,
                sub != null ? sub.getRequestorGroupName() : null,
                "EXTERNAL", AuditService.SRC_EXTERNAL,
                "External test executed — " + report.getResult() + ": " + auditDetail);

        List<Map<String, Object>> testCases = report.getChecks().stream()
                .map(c -> {
                    Map<String, Object> tc = new LinkedHashMap<>();
                    tc.put("name", c.getName());
                    tc.put("description", c.getDescription());
                    tc.put("passed", c.isPassed());
                    tc.put("errorMessage", c.getErrorMessage());
                    return tc;
                })
                .toList();

        Map<String, Object> testResult = new LinkedHashMap<>();
        testResult.put("result", report.getResult());
        testResult.put("testedAt", report.getTestedAt() != null ? report.getTestedAt().toString() : null);
        testResult.put("details", report.getDetails());
        testResult.put("testCases", testCases);
        testResult.put("rdapTestResults", report.getRdapTestResults());
        testResult.put("summary", report.getSummary());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("requestId", requestId);
        body.put("message", "PASSED".equals(report.getResult()) ? "Tests passed" : "Tests failed");
        body.put("dataHolderGroupCode", "DH-GROUP-ADMIN");
        body.put("testResult", testResult);
        return ResponseEntity.ok(body);
    }

    // ==================== Data Holder Queries ====================
    /** A data holder's subscriptions, with full template/request-type/RDAP details. */
    @GetMapping("/dataholder/{dataholderId}/subscriptions")
    public ResponseEntity<List<Map<String, Object>>> getDataholderSubscriptions(
            @PathVariable String dataholderId,
            @RequestParam(required = false) String status) {
        List<AgreementSubscription> subs;
        if (status != null) {
            try {
                SubscriptionStatus s = SubscriptionStatus.valueOf(status.toUpperCase());
                subs = subscriptionRepository.findByDataholderIdAndStatus(dataholderId, s);
            } catch (IllegalArgumentException e) {
                subs = subscriptionRepository.findByDataholderId(dataholderId);
            }
        } else {
            subs = subscriptionRepository.findByDataholderId(dataholderId);
        }
        return ResponseEntity.ok(subs.stream().map(mapper::toSubscriptionResponse).toList());
    }
    /** Only currently active+effective subscriptions. */
    @GetMapping("/dataholder/{dataholderId}/subscriptions/active")
    public ResponseEntity<List<Map<String, Object>>> getActiveDataholderSubscriptions(
            @PathVariable String dataholderId) {
        List<AgreementSubscription> subs = subscriptionRepository
                .findActiveAndEffectiveByDataholder(dataholderId, LocalDateTime.now());
        return ResponseEntity.ok(subs.stream().map(mapper::toSubscriptionResponse).toList());
    }
    /** Checks whether a specific requestor group has an active subscription. */
    @GetMapping("/dataholder/{dataholderId}/subscriptions/check")
    public ResponseEntity<?> checkSubscription(
            @PathVariable String dataholderId,
            @RequestParam String requestorGroupId) {
        List<AgreementSubscription> active = subscriptionRepository
                .findActiveAndEffectiveByDataholderAndGroup(dataholderId, requestorGroupId, LocalDateTime.now());
        if (active.isEmpty()) {
            return ResponseEntity.ok(Map.of("active", false, "message", "No active subscription found"));
        }
        AgreementSubscription sub = active.get(0);
        return ResponseEntity.ok(Map.of(
                "active", true, "requestId", sub.getRequestId(),
                "effectiveAccessLevel", sub.getEffectiveAccessLevel(),
                "supportsConfidential", sub.supportsConfidential(),
                "supportsExigent", sub.supportsExigent(),
                "subscription", mapper.toSubscriptionResponse(sub)
        ));
    }
    /** Lookup by group code (used during RDAP query authorization). */
    @GetMapping("/dataholder/{dataholderId}/subscriptions/by-group-code")
    public ResponseEntity<?> getByGroupCode(
            @PathVariable String dataholderId,
            @RequestParam String groupCode) {
        List<AgreementSubscription> subs = subscriptionRepository
                .findActiveAndEffectiveByGroupCode(groupCode, LocalDateTime.now());
        if (subs.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(subs.stream().map(mapper::toSubscriptionResponse).toList());
    }

    // ==================== Authenticated Data Holder Endpoints ====================
    // Data holders use Basic Auth (clientId:clientSecret) to access these

    /** Published templates for the groups the authenticated data holder belongs to. */
    @GetMapping("/my/templates")
    public ResponseEntity<?> getMyTemplates(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String dhId = authenticateDataHolder(authHeader);
        if (dhId == null) return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));

        List<Long> groupIds = resolveGroupIds(dhId);
        List<AgreementTemplate> templates;
        if (groupIds.isEmpty()) {
            // Fallback: if group membership unknown, return all (backward compat)
            templates = templateRepository.findByIsPublishedTrue();
        } else {
            templates = groupIds.stream()
                    .flatMap(gid -> templateRepository.findByIsPublishedTrueAndDataHolderGroupId(gid).stream())
                    .distinct()
                    .toList();
        }
        return ResponseEntity.ok(templates.stream()
                .map(mapper::toTemplateResponse).toList());
    }
    /** All subscriptions (any status) for the authenticated data holder. */
    @GetMapping("/my/subscriptions")
    public ResponseEntity<?> getMySubscriptions(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) String status) {
        String dhId = authenticateDataHolder(authHeader);
        if (dhId == null) return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));

        /* Subscriptions this DH can serve: those tied directly to it, plus group-level ones for
           its groups (a group subscription applies to every member). Mirrors /my/templates scoping. */
        java.util.LinkedHashMap<Long, AgreementSubscription> byId = new java.util.LinkedHashMap<>();
        for (AgreementSubscription s : subscriptionRepository.findByDataholderId(dhId)) byId.put(s.getId(), s);
        for (Long gid : resolveGroupIds(dhId)) {
            for (AgreementSubscription s : subscriptionRepository.findByDataHolderGroupId(gid)) byId.put(s.getId(), s);
        }
        List<AgreementSubscription> subs = new java.util.ArrayList<>(byId.values());
        if (status != null) {
            try {
                SubscriptionStatus st = SubscriptionStatus.valueOf(status.toUpperCase());
                subs = subs.stream().filter(s -> s.getStatus() == st).toList();
            } catch (IllegalArgumentException ignored) {}
        }
        return ResponseEntity.ok(subs.stream().map(mapper::toSubscriptionResponse).toList());
    }
    /** Only currently active+effective subscriptions for the authenticated data holder. */
    @GetMapping("/my/subscriptions/active")
    public ResponseEntity<?> getMyActiveSubscriptions(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String dhId = authenticateDataHolder(authHeader);
        if (dhId == null) return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        List<AgreementSubscription> subs = subscriptionRepository
                .findActiveAndEffectiveByDataholder(dhId, LocalDateTime.now());
        return ResponseEntity.ok(subs.stream().map(mapper::toSubscriptionResponse).toList());
    }
    /** Checks whether a requestor group has an active subscription. */
    @GetMapping("/my/subscriptions/check")
    public ResponseEntity<?> checkMySubscription(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam String requestorGroupId) {
        String dhId = authenticateDataHolder(authHeader);
        if (dhId == null) return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));

        List<AgreementSubscription> active = subscriptionRepository
                .findActiveAndEffectiveByDataholderAndGroup(dhId, requestorGroupId, LocalDateTime.now());
        if (active.isEmpty()) {
            return ResponseEntity.ok(Map.of("active", false, "message", "No active subscription found"));
        }
        AgreementSubscription sub = active.get(0);
        return ResponseEntity.ok(Map.of(
                "active", true, "requestId", sub.getRequestId(),
                "effectiveAccessLevel", sub.getEffectiveAccessLevel(),
                "supportsConfidential", sub.supportsConfidential(),
                "supportsExigent", sub.supportsExigent(),
                "subscription", mapper.toSubscriptionResponse(sub)
        ));
    }
    /**
     * GET /api/external/my/subscriptions/by-group-code?groupCode=...
     * Lookup by requestor group code (used during RDAP query authorization).
     */
    @GetMapping("/my/subscriptions/by-group-code")
    public ResponseEntity<?> getMyByGroupCode(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam String groupCode) {
        String dhId = authenticateDataHolder(authHeader);
        if (dhId == null) return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        List<AgreementSubscription> subs = subscriptionRepository
                .findActiveAndEffectiveByGroupCode(groupCode, LocalDateTime.now());
        if (subs.isEmpty()) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(subs.stream().map(mapper::toSubscriptionResponse).toList());
    }

    @GetMapping("/my/info")
    public ResponseEntity<?> getMyInfo(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String dhId = authenticateDataHolder(authHeader);
        if (dhId == null) return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        return dataHolderRepository.findByDataholderId(dhId)
                .map(dh -> {
                    var response = new java.util.LinkedHashMap<String, Object>();
                    response.put("dataholderId", dh.getDataholderId());
                    response.put("name", dh.getName());
                    response.put("isActive", dh.getIsActive());
                    response.put("connected", true);

                    // Group memberships
                    var groups = new java.util.ArrayList<Map<String, Object>>();
                    if (dh.getDataHolderGroups() != null) {
                        for (DataHolderGroup g : dh.getDataHolderGroups()) {
                            groups.add(Map.of(
                                "groupId", g.getId(),
                                "groupName", g.getName(),
                                "description", g.getDescription() != null ? g.getDescription() : "",
                                "isActive", g.getIsActive()
                            ));
                        }
                    }
                    if (groups.isEmpty() && dh.getDataHolderGroupId() != null) {
                        dataHolderGroupRepository.findById(dh.getDataHolderGroupId()).ifPresent(g ->
                            groups.add(Map.of(
                                "groupId", g.getId(),
                                "groupName", g.getName(),
                                "description", g.getDescription() != null ? g.getDescription() : "",
                                "isActive", g.getIsActive()
                            ))
                        );
                    }
                    response.put("groupMemberships", groups);

                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * For each group the caller belongs to, the fellow data-holder members, as groups with embedded
     * member lists. The caller itself is included and flagged isSelf=true.
     */
    @GetMapping("/my/group-members")
    public ResponseEntity<?> getMyGroupMembers(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String dhId = authenticateDataHolder(authHeader);
        if (dhId == null) return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));

        return dataHolderRepository.findByDataholderId(dhId)
                .<ResponseEntity<?>>map(self -> {
                    // Resolve the caller's groups (join-table membership, with legacy single-group fallback)
                    java.util.LinkedHashMap<Long, DataHolderGroup> groups = new java.util.LinkedHashMap<>();
                    if (self.getDataHolderGroups() != null) {
                        for (DataHolderGroup g : self.getDataHolderGroups()) groups.put(g.getId(), g);
                    }
                    if (groups.isEmpty() && self.getDataHolderGroupId() != null) {
                        dataHolderGroupRepository.findById(self.getDataHolderGroupId())
                                .ifPresent(g -> groups.put(g.getId(), g));
                    }

                    List<Map<String, Object>> result = new java.util.ArrayList<>();
                    for (DataHolderGroup g : groups.values()) {
                        // Union of join-table members and legacy single-group members, deduped by id
                        java.util.LinkedHashMap<Long, com.jaddar.dhgroupadmin.entity.DataHolder> byId =
                                new java.util.LinkedHashMap<>();
                        dataHolderRepository.findByGroupMembership(g.getId())
                                .forEach(dh -> byId.put(dh.getId(), dh));
                        dataHolderRepository.findByDataHolderGroupId(g.getId())
                                .forEach(dh -> byId.putIfAbsent(dh.getId(), dh));

                        List<Map<String, Object>> members = new java.util.ArrayList<>();
                        for (var dh : byId.values()) {
                            Map<String, Object> m = new java.util.LinkedHashMap<>();
                            m.put("id", dh.getId());
                            m.put("dataholderId", dh.getDataholderId());
                            m.put("name", dh.getName());
                            m.put("url", dh.getUrl());
                            m.put("description", dh.getDescription());
                            m.put("contactEmail", dh.getContactEmail());
                            m.put("isActive", dh.getIsActive());
                            m.put("lastPingAt", dh.getLastPingAt());
                            m.put("isSelf", dhId.equals(dh.getDataholderId()));
                            members.add(m);
                        }

                        Map<String, Object> gm = new java.util.LinkedHashMap<>();
                        gm.put("groupId", g.getId());
                        gm.put("groupName", g.getName());
                        gm.put("description", g.getDescription() != null ? g.getDescription() : "");
                        gm.put("isActive", g.getIsActive());
                        gm.put("memberCount", members.size());
                        gm.put("members", members);
                        result.add(gm);
                    }
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Public Endpoints ====================
    /** Public: all published templates, no auth. */
    @GetMapping("/templates")
    public ResponseEntity<?> getPublicTemplates() {
        return ResponseEntity.ok(templateRepository.findByIsPublishedTrue().stream()
                .map(mapper::toTemplateResponse).toList());
    }
    /** Public: a requestor group self-registers, creating a PENDING registration for admin review. */
    @PostMapping("/requestor-groups/register")
    public ResponseEntity<?> registerRequestorGroup(@RequestBody RequestorGroupRegistrationRequest request) {
        if (request.getName() == null || request.getCode() == null || request.getContactEmail() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "error", "name, code, and contactEmail are required"));
        }
        if (requestorGroupRepository.existsByCode(request.getCode())) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "error", "A requestor group with code '" + request.getCode() + "' already exists"));
        }

        RequestorGroup rg = RequestorGroup.builder()
                .name(request.getName())
                .code(request.getCode().trim())
                .description(request.getDescription())
                .groupType(request.getGroupType())
                .contactName(request.getContactName())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .organization(request.getOrganization())
                .address(request.getAddress())
                .city(request.getCity())
                .stateProvince(request.getStateProvince())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .reasonForAccess(request.getReasonForAccess())
                .dataHolderGroupId(request.getDataHolderGroupId())
                .status(RequestorGroup.RegistrationStatus.PENDING)
                .isActive(false)
                .build();

        rg = requestorGroupRepository.save(rg);
        log.info("Requestor group self-registered: {} ({}) — pending review", rg.getName(), rg.getCode());

        audit.logApi("REGISTER", "REQUESTOR_GROUP", rg.getCode(), rg.getName(),
                request.getContactEmail(), AuditService.SRC_EXTERNAL,
                "Self-registered requestor group — pending review. Organization: " + (request.getOrganization() != null ? request.getOrganization() : "N/A"));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Registration submitted. You will receive credentials once accepted by the group admin.",
                "code", rg.getCode(),
                "status", "PENDING"
        ));
    }
    /** Public: check registration status by code. */
    @GetMapping("/requestor-groups/status")
    public ResponseEntity<?> checkRegistrationStatus(@RequestParam String code) {
        return requestorGroupRepository.findByCode(code)
                .map(rg -> ResponseEntity.ok(Map.of(
                        "code", rg.getCode(), "name", rg.getName(),
                        "status", rg.getStatus().name(), "isActive", rg.getIsActive()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Data Holder Application (Public) ====================

    /** Public: active, non-private data holder groups for applicants to choose from. */
    @GetMapping("/data-holder-groups")
    public ResponseEntity<?> getPublicDataHolderGroups() {
        return ResponseEntity.ok(
            dataHolderGroupRepository.findAll().stream()
                .filter(g -> Boolean.TRUE.equals(g.getIsActive()))
                .filter(g -> !Boolean.TRUE.equals(g.getIsPrivate()))
                .map(g -> Map.of(
                    "id", g.getId(),
                    "name", g.getName(),
                    "description", g.getDescription() != null ? g.getDescription() : ""
                ))
                .toList()
        );
    }

    /** Public: a prospective data holder applies to join a group, creating a PENDING application. */
    @PostMapping("/data-holders/apply")
    public ResponseEntity<?> applyToJoinGroup(@RequestBody DataHolderApplicationRequest request) {
        // Validate required fields
        if (request.getOrganizationName() == null || request.getOrganizationName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Organization name is required"));
        }
        if (request.getContactFullName() == null || request.getContactFullName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Contact full name is required"));
        }
        if (request.getContactEmail() == null || request.getContactEmail().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Contact email is required"));
        }

        // Resolve group: by ID if provided, otherwise by name
        DataHolderGroup group = null;
        if (request.getDataHolderGroupId() != null) {
            group = dataHolderGroupRepository.findById(request.getDataHolderGroupId()).orElse(null);
        } else if (request.getDataHolderGroupName() != null && !request.getDataHolderGroupName().isBlank()) {
            group = dataHolderGroupRepository.findByNameIgnoreCase(request.getDataHolderGroupName().trim()).orElse(null);
        }

        if (group == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Data holder group not found. Please check the name and try again."));
        }
        if (!Boolean.TRUE.equals(group.getIsActive())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Selected data holder group is not currently active"));
        }

        // Check for duplicate pending application
        if (dataHolderApplicationRepository.existsByContactEmailAndDataHolderGroupIdAndStatus(
                request.getContactEmail().trim(),
                group.getId(),
                DataHolderApplication.ApplicationStatus.PENDING)) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "error", "An application with this email is already pending for the selected group"));
        }

        DataHolderApplication application = DataHolderApplication.builder()
                .dataHolderGroupId(group.getId())
                .organizationName(request.getOrganizationName().trim())
                .organizationAddress(request.getOrganizationAddress())
                .organizationPhone(request.getOrganizationPhone())
                .contactFullName(request.getContactFullName().trim())
                .contactEmail(request.getContactEmail().trim())
                .contactPhone(request.getContactPhone())
                .contactTitle(request.getContactTitle())
                .rdapServerUrls(request.getRdapServerUrls())
                .supportedTlds(request.getSupportedTlds())
                .additionalNotes(request.getAdditionalNotes())
                .status(DataHolderApplication.ApplicationStatus.PENDING)
                .build();

        application = dataHolderApplicationRepository.save(application);
        log.info("Data holder application submitted: {} for group {} — pending review",
                application.getOrganizationName(), group.getId());

        audit.logApi("APPLY", "DATA_HOLDER_APPLICATION", String.valueOf(application.getId()),
                application.getOrganizationName(), request.getContactEmail(),
                AuditService.SRC_EXTERNAL,
                "Data holder application submitted for group " + group.getName());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Application submitted successfully. You will be notified once your application has been reviewed.",
                "applicationId", application.getId(),
                "status", "PENDING"
        ));
    }

    /** Public: check application status by email and group. */
    @GetMapping("/data-holders/apply/status")
    public ResponseEntity<?> checkApplicationStatus(
            @RequestParam String email,
            @RequestParam Long groupId) {
        var apps = dataHolderApplicationRepository.findByDataHolderGroupId(groupId).stream()
                .filter(a -> a.getContactEmail().equalsIgnoreCase(email.trim()))
                .map(a -> Map.of(
                        "applicationId", (Object) a.getId(),
                        "organizationName", (Object) a.getOrganizationName(),
                        "status", (Object) a.getStatus().name(),
                        "submittedAt", (Object) a.getCreatedAt().toString()
                ))
                .toList();
        if (apps.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(apps);
    }

    // ==================== Ping / Health ====================

    @PostMapping("/ping")
    public ResponseEntity<?> ping(@RequestBody PingRequest request) {
        List<AgreementSubscription> active = subscriptionRepository
                .findActiveAndEffectiveByDataholderAndGroup(
                        request.getDataholderId(), request.getRequestorGroupId(), LocalDateTime.now());
        if (active.isEmpty()) {
            return ResponseEntity.ok(Map.of("active", false, "status", "NOT_FOUND",
                    "message", "No active subscription found"));
        }
        AgreementSubscription sub = active.get(0);
        return ResponseEntity.ok(Map.of("active", true, "status", "ACTIVE",
                "agreementId", sub.getRequestId(),
                "currentAccessLevel", sub.getEffectiveAccessLevel()));
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "dh-group-admin",
                "timestamp", System.currentTimeMillis()));
    }

    // ==================== Request DTOs ====================

    @Data public static class InitiationRequest {
        private String templateId; private String dataholderId; private String dataholderName; private String dataholderUrl;
        private String requestorGroupId; private String requestorGroupName; private String requestorGroupCode;
        private String requestorGroupType; private String requestorDescription;
        private String requestorFirstName; private String requestorLastName; private String requestorOrganization; private String requestorContactEmail;
        private String requestorPhone; private String requestorAddress; private String requestorCity;
        private String requestorStateProvince; private String requestorPostalCode; private String requestorCountry;
        private String requestorAgentId; private String requestorAgentUrl; private String callbackUrl;
        private String purpose; private String additionalTerms;
        private String introspectionUrl;
    }

    @Data public static class WorkflowRequest {
        private String initiatedBy; private String notes;
    }

    @Data public static class PingRequest {
        private String dataholderId; private String requestorGroupId; private String userId;
    }

    @Data public static class RequestorGroupRegistrationRequest {
        private String name; private String code; private String description;
        private String groupType; private String contactName; private String contactEmail;
        private String contactPhone; private String organization;
        private String address; private String city; private String stateProvince;
        private String postalCode; private String country; private String reasonForAccess;
        private Long dataHolderGroupId;
    }

    @Data public static class DataHolderApplicationRequest {
        private Long dataHolderGroupId;
        private String dataHolderGroupName;
        private String organizationName;
        private String organizationAddress;
        private String organizationPhone;
        private String contactFullName;
        private String contactEmail;
        private String contactPhone;
        private String contactTitle;
        private String rdapServerUrls;
        private String supportedTlds;
        private String additionalNotes;
    }
}