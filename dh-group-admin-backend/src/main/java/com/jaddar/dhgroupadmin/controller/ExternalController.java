/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.controller;

import com.jaddar.dhgroupadmin.config.HttpSignatureFilter;
import com.jaddar.dhgroupadmin.entity.*;
import com.jaddar.dhgroupadmin.entity.AgreementSubscription.SubscriptionStatus;
import com.jaddar.dhgroupadmin.repository.*;
import com.jaddar.dhgroupadmin.service.AgreementSubscriptionService;
import com.jaddar.dhgroupadmin.service.AuditService;
import com.jaddar.dhgroupadmin.service.ResponseMapper;
import com.jaddar.dhgroupadmin.service.SubscriptionTestService;
import com.jaddar.dhgroupadmin.service.TemplateVisibilityService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final SubscriptionCredentialRepository subCredentialRepository;
    private final jakarta.servlet.http.HttpServletRequest httpRequest;

    private final RequestorGroupRepository requestorGroupRepository;
    private final DataHolderGroupRepository dataHolderGroupRepository;
    private final DataHolderApplicationRepository dataHolderApplicationRepository;
    private final ResponseMapper mapper;
    private final TemplateVisibilityService visibilityService;
    private final AuditService audit;


    /**
     * Resolves the subscription a caller's credentials authorize, or null when the caller
     * cannot be identified. The credential is scoped to one subscription, so this is an
     * exact match rather than a comparison of group identifiers -- which matters here
     * because requestor group codes are not reliably populated on subscriptions.
     */
    private String authenticatedRequestId(String authHeader) {
        /*
         * A verified request signature is the stronger proof and is checked first: the filter
         * has already confirmed the caller holds the private key for this subscription's
         * registered public key. Basic Auth remains for peers that have not yet moved over.
         */
        Object signed = httpRequest.getAttribute(HttpSignatureFilter.ATTR_SUBSCRIPTION);
        if (signed instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        if (authHeader == null || !authHeader.startsWith("Basic ")) return null;
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(authHeader.substring(6)));
            String[] parts = decoded.split(":", 2);
            if (parts.length != 2) return null;
            var credOpt = subCredentialRepository.findByClientIdAndIsActiveTrue(parts[0]);
            if (credOpt.isEmpty() || !credOpt.get().verifySecret(parts[1])) return null;
            SubscriptionCredential cred = credOpt.get();
            cred.setLastUsedAt(LocalDateTime.now());
            subCredentialRepository.save(cred);
            return cred.getRequestId();
        } catch (Exception e) {
            log.warn("Failed to authenticate subscription caller: {}", e.getMessage());
            audit.logApi("AUTH_FAILED", "SUBSCRIPTION", null, null, "unknown",
                    AuditService.SRC_EXTERNAL, "Authentication failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Ownership check for subscription-scoped calls from a requestor manager.
     *
     * <p>Returns null when the call may proceed. Credentials must authorize the very
     * subscription being acted on: a caller that cannot be identified is refused, and one
     * holding another subscription's credentials is refused too. This is the only thing
     * standing between a reachable port and a subscription, so there is no permissive mode.
     */
    private ResponseEntity<?> denyIfNotOwner(String authHeader, AgreementSubscription subscription) {
        String callerRequestId = authenticatedRequestId(authHeader);
        if (callerRequestId == null) {
            log.warn("Refusing unauthenticated call for subscription {}", subscription.getRequestId());
            audit.logApi("AUTH_FAILED", "SUBSCRIPTION", subscription.getRequestId(), null, "unknown",
                    AuditService.SRC_EXTERNAL, "No valid subscription credentials were presented");
            return ResponseEntity.status(401).body(Map.of(
                    "success", false, "message", "Valid subscription credentials are required"));
        }
        if (!callerRequestId.equals(subscription.getRequestId())) {
            log.warn("Credentials for subscription {} were used against subscription {}",
                    callerRequestId, subscription.getRequestId());
            audit.logApi("ACCESS_DENIED", "SUBSCRIPTION", subscription.getRequestId(), null, callerRequestId,
                    AuditService.SRC_EXTERNAL, "Credentials do not authorize this subscription");
            return ResponseEntity.status(403).body(Map.of(
                    "success", false, "message", "These credentials do not authorize this subscription"));
        }
        return null;
    }



    private String authenticateDataHolder(String authHeader) {
        Object signed = httpRequest.getAttribute(HttpSignatureFilter.ATTR_DATAHOLDER);
        if (signed instanceof String dataholderId && !dataholderId.isBlank()) {
            return dataholderId;
        }
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

    /** One template by its id, unfiltered by visibility since knowing the id is the access condition. */
    @GetMapping("/templates/{templateId}")
    public ResponseEntity<?> getTemplate(@PathVariable String templateId) {
        return templateRepository.findByTemplateId(templateId)
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

            if (!Boolean.TRUE.equals(template.getIsActive())) {
                throw new IllegalStateException("This agreement is no longer accepting new subscriptions. "
                        + "Subscriptions already in place are unaffected.");
            }

            String subscriptionError = validateSubscriptionSubmission(template, request);
            if (subscriptionError != null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", subscriptionError));
            }

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
                    .introspectionClientId(request.getIntrospectionClientId())
                    .introspectionClientSecret(request.getIntrospectionClientSecret())
                    .purpose(request.getPurpose())
                    .additionalTerms(request.getAdditionalTerms())
                    .templateSnapshot(mapper.snapshotTemplate(template))
                    .subscriptionFieldValues(request.getSubscriptionFieldValues())
                    .acceptedTerms(recordAcceptedTerms(template))
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
    public ResponseEntity<?> getSubscriptionStatus(@PathVariable String requestId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        var ownerCheck = subscriptionRepository.findByRequestId(requestId);
        if (ownerCheck.isEmpty()) return ResponseEntity.notFound().build();

        boolean authorized = requestId.equals(authenticatedRequestId(authHeader));
        if (!authorized) {
            log.debug("Unauthenticated status check for subscription {}; returning lifecycle fields only",
                    requestId);
        }

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
                    response.put("agreementId", s.getStatus() == SubscriptionStatus.ACTIVE ? s.getRequestId() : null);
                    response.put("testResult", s.getTestResult() != null ? s.getTestResult() : "");
                    response.put("testCompletedAt", s.getTestCompletedAt() != null ? s.getTestCompletedAt().toString() : "");
                    response.put("pendingChangeStatus", s.getPendingChangeStatus());
                    response.put("pendingProposedAt", s.getPendingProposedAt() != null ? s.getPendingProposedAt().toString() : null);
                    response.put("pendingChangeMode", s.getPendingChangeMode());
                    response.put("pendingChangeDeadline", s.getPendingChangeDeadline() != null ? s.getPendingChangeDeadline().toString() : null);

                    if (authorized) {
                        response.put("pendingTemplateSnapshot", s.getPendingTemplateSnapshot());
                        response.put("templateSnapshot", s.getTemplateSnapshot());
                        response.put("acceptedTerms", s.getAcceptedTerms());
                        // Accepting a change can answer fields the requestor manager has never seen.
                        response.put("subscriptionFieldValues", s.getSubscriptionFieldValues());
                    }
                    response.put("detailsIncluded", authorized);
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Testing Workflow ====================

    @PostMapping("/{requestId}/start-testing")
    @Transactional(noRollbackFor = {IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<?> startTesting(@PathVariable String requestId,
                                           @RequestHeader(value = "Authorization", required = false) String authHeader,
                                           @RequestBody(required = false) WorkflowRequest request) {
        var ownerCheck = subscriptionRepository.findByRequestId(requestId);
        if (ownerCheck.isEmpty()) return ResponseEntity.notFound().build();
        ResponseEntity<?> denied = denyIfNotOwner(authHeader, ownerCheck.get());
        if (denied != null) return denied;
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
                                       @RequestHeader(value = "Authorization", required = false) String authHeader,
                                       @RequestBody(required = false) WorkflowRequest request) {
        var ownerCheck = subscriptionRepository.findByRequestId(requestId);
        if (ownerCheck.isEmpty()) return ResponseEntity.notFound().build();
        ResponseEntity<?> denied = denyIfNotOwner(authHeader, ownerCheck.get());
        if (denied != null) return denied;
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
                                      @RequestHeader(value = "Authorization", required = false) String authHeader,
                                      @RequestBody(required = false) Map<String, Object> request) {
        var ownerCheck = subscriptionRepository.findByRequestId(requestId);
        if (ownerCheck.isEmpty()) return ResponseEntity.notFound().build();
        ResponseEntity<?> denied = denyIfNotOwner(authHeader, ownerCheck.get());
        if (denied != null) return denied;

        SubscriptionTestService.TestReport report;
        try {
            String actor = request != null && request.get("initiatedBy") instanceof String s && !s.isBlank()
                    ? s : "EXTERNAL";
            report = subscriptionTestService.runTest(requestId, actor);
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

    /**
     * Receives the token-introspection credentials the requestor manager provisioned for a
     * subscription, and stores them on that subscription.
     *
     * The requestor manager mints a dedicated client in the requestor's identity provider and
     * delivers it here on activation. Data holders read it back from the subscription (see
     * {@link #getMyByGroupCode}) and authenticate with it when introspecting the bearer tokens
     * that arrive on RDAP queries — RFC 7662 §2.1 requires the introspection endpoint to
     * authenticate its caller, so the URL alone is not usable.
     *
     * <p>This writes the secret the data holder later authenticates with, so the caller has
     * to prove it owns this subscription: an unauthenticated caller could otherwise redirect
     * introspection for any subscription whose request id it could guess.
     */
    @PostMapping("/{requestId}/credentials")
    @Transactional
    public ResponseEntity<?> receiveCredentials(@PathVariable String requestId,
                                                 @RequestBody CredentialDeliveryRequest request,
                                                 @RequestHeader(value = "Authorization", required = false) String authHeader) {
        var subOpt = subscriptionRepository.findByRequestId(requestId);
        if (subOpt.isEmpty()) {
            log.warn("Credential delivery for unknown subscription {}", requestId);
            return ResponseEntity.status(404).body(Map.of(
                    "success", false, "message", "Unknown request ID: " + requestId));
        }
        ResponseEntity<?> denied = denyIfNotOwner(authHeader, subOpt.get());
        if (denied != null) return denied;

        if (request.getClientId() == null || request.getClientId().isBlank()
                || request.getClientSecret() == null || request.getClientSecret().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "clientId and clientSecret are required"));
        }

        AgreementSubscription sub = subOpt.get();
        sub.setIntrospectionClientId(request.getClientId());
        sub.setIntrospectionClientSecret(request.getClientSecret());

        if ((sub.getIntrospectionUrl() == null || sub.getIntrospectionUrl().isBlank())
                && request.getIntrospectionUrl() != null && !request.getIntrospectionUrl().isBlank()) {
            sub.setIntrospectionUrl(request.getIntrospectionUrl());
        } else if (request.getIntrospectionUrl() != null && !request.getIntrospectionUrl().isBlank()
                && !request.getIntrospectionUrl().equals(sub.getIntrospectionUrl())) {
            log.info("Credential delivery for {} carried introspection URL {} but the subscription "
                            + "already registers {} — keeping the registered one",
                    requestId, request.getIntrospectionUrl(), sub.getIntrospectionUrl());
        }
        subscriptionRepository.save(sub);

        audit.logApi("RECEIVE_CREDENTIALS", "SUBSCRIPTION", requestId,
                sub.getRequestorGroupName(), "EXTERNAL", AuditService.SRC_EXTERNAL,
                "Stored introspection credentials (clientId: " + request.getClientId() + ")");

        log.info("Stored introspection credentials for subscription {} (clientId: {})",
                requestId, request.getClientId());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "requestId", requestId,
                "message", "Introspection credentials stored"));
    }

    /**
     * Reports whether a subscription already has introspection credentials, so the requestor
     * manager's reconciliation job can fill gaps without blindly re-sending secrets.
     *
     * Deliberately presence-only: it returns the client ID (not a secret) and never the client
     * secret, so polling it cannot be used to extract credentials.
     */
    @GetMapping("/{requestId}/credentials/status")
    public ResponseEntity<?> getCredentialStatus(@PathVariable String requestId,
                                                 @RequestHeader(value = "Authorization", required = false) String authHeader) {
        var subOpt = subscriptionRepository.findByRequestId(requestId);
        if (subOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false, "message", "Unknown request ID: " + requestId));
        }
        ResponseEntity<?> denied = denyIfNotOwner(authHeader, subOpt.get());
        if (denied != null) return denied;

        AgreementSubscription sub = subOpt.get();
        boolean hasCredentials = sub.getIntrospectionClientId() != null
                && !sub.getIntrospectionClientId().isBlank()
                && sub.getIntrospectionClientSecret() != null
                && !sub.getIntrospectionClientSecret().isBlank();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("requestId", requestId);
        body.put("status", sub.getStatus().name());
        body.put("hasCredentials", hasCredentials);
        body.put("hasIntrospectionUrl", sub.getIntrospectionUrl() != null && !sub.getIntrospectionUrl().isBlank());
        body.put("clientId", hasCredentials ? sub.getIntrospectionClientId() : null);
        return ResponseEntity.ok(body);
    }

    // ==================== Data Holder Queries ====================
    /*
     * The /dataholder/{dataholderId}/... query endpoints were removed: they took the data
     * holder's identity straight from the path with no credential check, so any caller
     * could read any data holder's subscriptions. Data holders use the authenticated
     * /my/... endpoints below, which derive identity from their Basic Auth credentials.
     */

    /** Lookup by group code (used during RDAP query authorization). */
    @GetMapping("/dataholder/{dataholderId}/subscriptions/by-group-code")
    public ResponseEntity<?> getByGroupCode(
            @PathVariable String dataholderId,
            @RequestParam String groupCode) {
        List<AgreementSubscription> subs = subscriptionRepository
                .findActiveAndEffectiveByGroupCode(groupCode, LocalDateTime.now());
        if (subs.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(subs.stream().map(sub -> {
            Map<String, Object> summary = new java.util.HashMap<>();
            summary.put("status", sub.getStatus().name());
            summary.put("statusMessage", sub.getStatusMessage());
            summary.put("effectiveFrom", sub.getEffectiveFrom());
            summary.put("effectiveTo", sub.getEffectiveTo());
            if (sub.getTemplate() != null) {
                summary.put("templateId", sub.getTemplate().getTemplateId());
                summary.put("templateName", sub.getTemplate().getName());
            }
            return summary;
        }).toList());
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
            templates = templateRepository.findByIsActiveTrue();
        } else {
            templates = groupIds.stream()
                    .flatMap(gid -> templateRepository.findByIsActiveTrueAndDataHolderGroupId(gid).stream())
                    .distinct()
                    .toList();
        }
        var groupMap = visibilityService.groupsById();
        return ResponseEntity.ok(templates.stream()
                .map(t -> mapper.toTemplateResponse(t, groupMap)).toList());
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
        Map<Long, DataHolderGroup> groups = visibilityService.groupsById();
        return ResponseEntity.ok(subs.stream().map(s -> mapper.toSubscriptionResponse(s, groups)).toList());
    }
    /** Only currently active+effective subscriptions for the authenticated data holder. */
    @GetMapping("/my/subscriptions/active")
    public ResponseEntity<?> getMyActiveSubscriptions(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String dhId = authenticateDataHolder(authHeader);
        if (dhId == null) return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        List<AgreementSubscription> subs = subscriptionRepository
                .findActiveAndEffectiveByDataholder(dhId, LocalDateTime.now());
        Map<Long, DataHolderGroup> groups = visibilityService.groupsById();
        return ResponseEntity.ok(subs.stream().map(s -> mapper.toSubscriptionResponseForDataHolder(s, groups)).toList());
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
        Map<Long, DataHolderGroup> groups = visibilityService.groupsById();
        return ResponseEntity.ok(subs.stream().map(s -> mapper.toSubscriptionResponseForDataHolder(s, groups)).toList());
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
    /** Public: active templates that are effectively Public or Globally published. */
    @GetMapping("/templates")
    public ResponseEntity<?> getPublicTemplates() {
        var groups = visibilityService.groupsById();
        return ResponseEntity.ok(visibilityService.listedTemplates().stream()
                .map(t -> mapper.toTemplateResponse(t, groups)).toList());
    }
    /** Public: a requestor group self-registers, creating a PENDING registration for admin review. */
    @PostMapping("/requestor-groups/register")
    public ResponseEntity<?> registerRequestorGroup(@RequestBody RequestorGroupRegistrationRequest request) {
        if (isBlank(request.getName()) || isBlank(request.getCode()) || isBlank(request.getContactEmail())) {
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

    /** Public: active data holder groups an applicant may choose from; Private ones are omitted. */
    @GetMapping("/data-holder-groups")
    public ResponseEntity<?> getPublicDataHolderGroups() {
        return ResponseEntity.ok(
            dataHolderGroupRepository.findAll().stream()
                .filter(g -> Boolean.TRUE.equals(g.getIsActive()))
                .filter(g -> g.getVisibility() != Visibility.PRIVATE)
                .map(g -> Map.of(
                    "id", g.getId(),
                    "name", g.getName(),
                    "description", g.getDescription() != null ? g.getDescription() : "",
                    "visibility", g.getVisibility() != null ? g.getVisibility().name() : Visibility.PUBLIC.name()
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
        private String introspectionClientId; private String introspectionClientSecret;
        private Map<String, Object> subscriptionFieldValues;
        private List<Long> acceptedLegalSectionIds;
    }

    /**
     * Reject a submission that misses a required subscription field, overruns a field's character
     * limit, or leaves a legal section unaccepted. Returns null when the submission is complete.
     */
    private String validateSubscriptionSubmission(AgreementTemplate template, InitiationRequest request) {
        Map<String, Object> values = request.getSubscriptionFieldValues() != null
                ? request.getSubscriptionFieldValues() : Map.of();

        for (TemplateSubscriptionField field : template.getSubscriptionFields()) {
            Object v = values.get(field.getName());
            String type = field.getDataType();

            if (Boolean.TRUE.equals(field.getRequired())) {
                // url and checkbox both carry an acknowledgement, which must be ticked rather than filled in.
                boolean acknowledgement = "checkbox".equals(type) || "url".equals(type);
                boolean missing = acknowledgement
                        ? !Boolean.TRUE.equals(v)
                        : v == null || (v instanceof String str && str.isBlank());
                if (missing) {
                    return "\"" + field.getName() + "\" is required to subscribe to this agreement.";
                }
            }

            // Character limit, for the free-text types that offer one.
            Integer max = field.getMaxLength();
            if (max != null && max > 0 && ("string".equals(type) || "text".equals(type))
                    && v instanceof String entered && entered.length() > max) {
                return "\"" + field.getName() + "\" is limited to " + max + " characters.";
            }
        }

        List<Long> accepted = request.getAcceptedLegalSectionIds() != null
                ? request.getAcceptedLegalSectionIds() : List.of();
        for (AgreementLegalSection section : template.getLegalSections()) {
            if (!accepted.contains(section.getId())) {
                return "You must accept \"" + section.getTitle() + "\" to subscribe to this agreement.";
            }
        }
        return null;
    }

    /** Snapshot every legal section as accepted, with the title as it read at acceptance time. */
    private List<Map<String, Object>> recordAcceptedTerms(AgreementTemplate template) {
        String acceptedAt = LocalDateTime.now().toString();
        return template.getLegalSections().stream()
                .map(section -> {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("sectionId", section.getId());
                    m.put("title", section.getTitle());
                    m.put("acceptedAt", acceptedAt);
                    return m;
                })
                .toList();
    }

    /** Adopt the proposed template change, replacing the copy this subscription runs on. */
    @PostMapping("/subscriptions/{requestId}/pending-change/accept")
    public ResponseEntity<?> acceptPendingChange(@PathVariable String requestId,
                                                 @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                 @RequestBody(required = false) PendingChangeResponse body) {
        var ownerCheck = subscriptionRepository.findByRequestId(requestId);
        if (ownerCheck.isEmpty()) return ResponseEntity.notFound().build();
        ResponseEntity<?> denied = denyIfNotOwner(authHeader, ownerCheck.get());
        if (denied != null) return denied;

        return respondToPendingChange(requestId, true,
                body != null ? body.getSubscriptionFieldValues() : null);
    }

    /** Refuse the proposed change; the subscription keeps the copy it already runs on. */
    @PostMapping("/subscriptions/{requestId}/pending-change/decline")
    public ResponseEntity<?> declinePendingChange(@PathVariable String requestId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        var ownerCheck = subscriptionRepository.findByRequestId(requestId);
        if (ownerCheck.isEmpty()) return ResponseEntity.notFound().build();
        ResponseEntity<?> denied = denyIfNotOwner(authHeader, ownerCheck.get());
        if (denied != null) return denied;

        return respondToPendingChange(requestId, false, null);
    }

    private ResponseEntity<?> respondToPendingChange(String requestId, boolean accept,
                                                    Map<String, Object> submittedValues) {
        return subscriptionRepository.findByRequestId(requestId)
                .map(sub -> {
                    if (!"PROPOSED".equals(sub.getPendingChangeStatus())) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "success", false, "message", "There is no change awaiting a response on this subscription."));
                    }
                    if (!accept && "FORCED".equals(sub.getPendingChangeMode())) {
                        return ResponseEntity.badRequest().body(Map.of("success", false,
                                "message", "This change is required. Accept it by "
                                        + sub.getPendingChangeDeadline()
                                        + " or the subscription will be terminated."));
                    }
                    if (accept) {
                        Map<String, Object> promoted = sub.getPendingTemplateSnapshot();
                        /*
                         * A change can introduce fields the requestor has never answered, so the
                         * values are re-checked against the terms being accepted rather than the
                         * ones being replaced. Without this a new required field is agreed to and
                         * left empty.
                         */
                        Map<String, Object> merged = mergedFieldValues(sub.getSubscriptionFieldValues(), submittedValues);
                        String invalid = validateSnapshotFields(promoted, merged);
                        if (invalid != null) {
                            return ResponseEntity.badRequest().body(Map.of("success", false, "message", invalid));
                        }
                        sub.setSubscriptionFieldValues(merged);
                        sub.setTemplateSnapshot(promoted);
                        // The requestor accepted these sections just now; the record has to
                        // follow the terms, or it keeps pointing at sections that no longer exist.
                        sub.setAcceptedTerms(acceptedTermsFromSnapshot(promoted));
                        sub.setPendingTemplateSnapshot(null);
                        sub.setPendingChangeStatus(null);
                        sub.setPendingChangeMode(null);
                        sub.setPendingChangeDeadline(null);
                    } else {
                        sub.setPendingTemplateSnapshot(null);
                        sub.setPendingChangeStatus("DECLINED");
                    }
                    sub.setPendingRespondedAt(LocalDateTime.now());
                    subscriptionRepository.save(sub);
                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "accepted", accept,
                            "subscription", mapper.toSubscriptionResponse(sub)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Data public static class PendingChangeResponse {
        private Map<String, Object> subscriptionFieldValues;
    }

    private Map<String, Object> mergedFieldValues(Map<String, Object> existing, Map<String, Object> submitted) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (existing != null) merged.putAll(existing);
        if (submitted != null) merged.putAll(submitted);
        return merged;
    }

    /**
     * Reject an acceptance that leaves a required field of the accepted terms unanswered or
     * over its character limit. Mirrors the checks made when the subscription was created.
     */
    @SuppressWarnings("unchecked")
    private String validateSnapshotFields(Map<String, Object> snapshot, Map<String, Object> values) {
        if (snapshot == null || !(snapshot.get("subscriptionFields") instanceof List<?> fields)) {
            return null;
        }
        for (Object entry : fields) {
            if (!(entry instanceof Map<?, ?> raw)) continue;
            Map<String, Object> field = (Map<String, Object>) raw;
            String name = String.valueOf(field.get("name"));
            String type = field.get("dataType") != null ? String.valueOf(field.get("dataType")) : "string";
            Object value = values.get(name);

            if (Boolean.TRUE.equals(field.get("required"))) {
                boolean acknowledgement = "checkbox".equals(type) || "url".equals(type);
                boolean missing = acknowledgement
                        ? !Boolean.TRUE.equals(value)
                        : value == null || (value instanceof String str && str.isBlank());
                if (missing) {
                    return "\"" + name + "\" is required to accept this change.";
                }
            }

            Object max = field.get("maxLength");
            if (max instanceof Number limit && limit.intValue() > 0
                    && ("string".equals(type) || "text".equals(type))
                    && value instanceof String entered && entered.length() > limit.intValue()) {
                return "\"" + name + "\" is limited to " + limit.intValue() + " characters.";
            }
        }
        return null;
    }

    /** Acceptance entries for the legal sections carried by a just-accepted snapshot. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> acceptedTermsFromSnapshot(Map<String, Object> snapshot) {
        if (snapshot == null || !(snapshot.get("legalSections") instanceof List<?> sections)) {
            return List.of();
        }
        String acceptedAt = LocalDateTime.now().toString();
        List<Map<String, Object>> accepted = new ArrayList<>();
        for (Object entry : sections) {
            if (!(entry instanceof Map<?, ?> section)) continue;
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("sectionId", ((Map<String, Object>) section).get("id"));
            record.put("title", ((Map<String, Object>) section).get("title"));
            record.put("acceptedAt", acceptedAt);
            accepted.add(record);
        }
        return accepted;
    }

    @Data public static class WorkflowRequest {
        private String initiatedBy; private String notes;
    }

    /** Introspection credentials delivered by the requestor manager for a subscription. */
    @Data public static class CredentialDeliveryRequest {
        private String requestId;
        private String clientId; private String clientSecret;
        private String introspectionUrl; private String tokenUrl;
        private String requestorGroupName; private String requestorGroupCode;
        private String purpose; private String provisionedAt;
    }

    @Data public static class PingRequest {
        private String dataholderId; private String requestorGroupId; private String userId;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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