/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import com.jaddar.dataholder.dto.AgreementApiDto.*;
import com.jaddar.dataholder.entity.AgreementSubscription;
import com.jaddar.dataholder.entity.IntrospectionCredential;
import com.jaddar.dataholder.repository.AgreementSubscriptionRepository;
import com.jaddar.dataholder.repository.IntrospectionCredentialRepository;
import com.jaddar.dataholder.service.AgreementSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** External API called by the Requestor Manager during subscription/agreement negotiation. */
@RestController
@RequestMapping("/api/agreements/external")
@RequiredArgsConstructor
@Slf4j
public class ExternalAgreementController {

    private final AgreementSubscriptionService subscriptionService;
    private final IntrospectionCredentialRepository introspectionCredentialRepository;
    private final AgreementSubscriptionRepository subscriptionRepository;

    /** All published templates requestors can subscribe to. */
    @GetMapping("/templates")
    public ResponseEntity<List<PublishedTemplate>> getAvailableTemplates() {
        log.info("External request for available agreement templates");
        List<PublishedTemplate> templates = subscriptionService.getPublishedTemplates();
        return ResponseEntity.ok(templates);
    }

    /** Details of a specific template. */
    @GetMapping("/templates/{templateId}")
    public ResponseEntity<?> getTemplate(@PathVariable String templateId) {
        log.info("External request for template: {}", templateId);
        return subscriptionService.getPublishedTemplate(templateId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Entry point for establishing a new subscription/agreement. */
    @PostMapping("/initiate")
    public ResponseEntity<AgreementInitiationResponse> initiateSubscription(
            @RequestBody AgreementInitiationRequest request) {
        log.info("Subscription initiation request from: {} ({})", 
                request.getRequestorGroupName(), request.getRequestorAgentId());
        
        AgreementInitiationResponse response = subscriptionService.initiateSubscription(request);
        
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    /** Status of a subscription request (polled by the Requestor Manager). */
    @GetMapping("/status/{requestId}")
    public ResponseEntity<?> getSubscriptionStatus(@PathVariable String requestId) {
        log.debug("Status check for subscription request: {}", requestId);
        return subscriptionService.getSubscriptionStatus(requestId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Testing Workflow Endpoints ====================

    /** Start the testing phase for an approved subscription. */
    @PostMapping("/{requestId}/start-testing")
    public ResponseEntity<WorkflowActionResponse> startTesting(
            @PathVariable String requestId,
            @RequestBody(required = false) WorkflowActionRequest request) {
        log.info("External request to start testing for subscription: {}", requestId);
        
        try {
            String initiatedBy = request != null && request.getInitiatedBy() != null 
                    ? request.getInitiatedBy() 
                    : "AGREEMENT_SERVER";
            
            AgreementSubscription subscription = subscriptionService.startTestingByRequestId(requestId, initiatedBy);
            
            return ResponseEntity.ok(WorkflowActionResponse.builder()
                    .success(true)
                    .requestId(requestId)
                    .status(subscription.getStatus().name())
                    .message("Testing started successfully")
                    .statusChangedAt(subscription.getStatusChangedAt())
                    .build());
        } catch (IllegalArgumentException e) {
            log.warn("Subscription not found: {}", requestId);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Cannot start testing for {}: {}", requestId, e.getMessage());
            return ResponseEntity.badRequest().body(WorkflowActionResponse.builder()
                    .success(false)
                    .requestId(requestId)
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("Error starting testing for {}", requestId, e);
            return ResponseEntity.internalServerError().body(WorkflowActionResponse.builder()
                    .success(false)
                    .requestId(requestId)
                    .message("An internal error occurred while starting testing. Please try again or contact support.")
                    .build());
        }
    }

    /**
     * Run this data holder's RDAP retrieval suite for a template, with no subscription required.
     * The group admin calls this on each data holder in a group to find one holding test data.
     */
    @PostMapping("/templates/{templateId}/run-test")
    public ResponseEntity<TestExecutionResponse> runTemplateTest(@PathVariable String templateId) {
        log.info("External request to run template tests for: {}", templateId);

        try {
            TestResultInfo testResult = subscriptionService.runTemplateTest(templateId);

            return ResponseEntity.ok(TestExecutionResponse.builder()
                    .success(true)
                    .requestId(templateId)
                    .testResult(testResult)
                    .message("Template test execution completed")
                    .build());
        } catch (IllegalArgumentException e) {
            log.warn("Template not found: {}", templateId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error running template test for {}", templateId, e);
            return ResponseEntity.internalServerError().body(TestExecutionResponse.builder()
                    .success(false)
                    .requestId(templateId)
                    .message("An internal error occurred while running the test. Please try again or contact support.")
                    .build());
        }
    }

    /** Execute the test suite for a subscription in TESTING status. */
    @PostMapping("/{requestId}/run-test")
    public ResponseEntity<TestExecutionResponse> runTest(@PathVariable String requestId) {
        log.info("External request to run tests for subscription: {}", requestId);
        
        try {
            TestResultInfo testResult = subscriptionService.runTestByRequestId(requestId);
            
            return ResponseEntity.ok(TestExecutionResponse.builder()
                    .success(true)
                    .requestId(requestId)
                    .testResult(testResult)
                    .message("Test execution completed")
                    .build());
        } catch (IllegalArgumentException e) {
            log.warn("Subscription not found: {}", requestId);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Cannot run test for {}: {}", requestId, e.getMessage());
            return ResponseEntity.badRequest().body(TestExecutionResponse.builder()
                    .success(false)
                    .requestId(requestId)
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("Error running test for {}", requestId, e);
            return ResponseEntity.internalServerError().body(TestExecutionResponse.builder()
                    .success(false)
                    .requestId(requestId)
                    .message("An internal error occurred while running the test. Please try again or contact support.")
                    .build());
        }
    }

    /** Activate a subscription after successful testing. */
    @PostMapping("/{requestId}/activate")
    public ResponseEntity<WorkflowActionResponse> activateSubscription(
            @PathVariable String requestId,
            @RequestBody(required = false) WorkflowActionRequest request) {
        log.info("External request to activate subscription: {}", requestId);
        
        try {
            String activatedBy = request != null && request.getInitiatedBy() != null 
                    ? request.getInitiatedBy() 
                    : "AGREEMENT_SERVER";
            
            AgreementSubscription subscription = subscriptionService.activateByRequestId(requestId, activatedBy);
            
            return ResponseEntity.ok(WorkflowActionResponse.builder()
                    .success(true)
                    .requestId(requestId)
                    .status(subscription.getStatus().name())
                    .message("Subscription activated successfully")
                    .statusChangedAt(subscription.getStatusChangedAt())
                    .agreementId(subscription.getRequestId())
                    .grantedAccessLevel(subscription.getEffectiveAccessLevel())
                    .effectiveFrom(subscription.getEffectiveFrom())
                    .effectiveTo(subscription.getEffectiveTo())
                    .build());
        } catch (IllegalArgumentException e) {
            log.warn("Subscription not found: {}", requestId);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Cannot activate {}: {}", requestId, e.getMessage());
            return ResponseEntity.badRequest().body(WorkflowActionResponse.builder()
                    .success(false)
                    .requestId(requestId)
                    .message(e.getMessage())
                    .build());
        } catch (Exception e) {
            log.error("Error activating {}", requestId, e);
            return ResponseEntity.internalServerError().body(WorkflowActionResponse.builder()
                    .success(false)
                    .requestId(requestId)
                    .message("An internal error occurred while activating the subscription. Please try again or contact support.")
                    .build());
        }
    }

    /**
     * Receives introspection credentials from the agreement server on activation: a per-subscription
     * Keycloak client_id/client_secret this data holder uses for token introspection on RDAP queries.
     */
    @PostMapping("/{requestId}/credentials")
    public ResponseEntity<Map<String, Object>> receiveCredentials(
            @PathVariable String requestId,
            @RequestBody Map<String, Object> payload) {

        log.info("Received introspection credentials for request: {}", requestId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestId", requestId);

        try {
            // Validate the subscription exists
            Optional<AgreementSubscription> subOpt = subscriptionRepository.findByRequestId(requestId);
            if (subOpt.isEmpty()) {
                log.warn("Received credentials for unknown request: {}", requestId);
                response.put("success", false);
                response.put("message", "Unknown request ID: " + requestId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            AgreementSubscription subscription = subOpt.get();

            // Extract credential fields from payload
            String clientId = (String) payload.get("clientId");
            String clientSecret = (String) payload.get("clientSecret");
            String introspectionUrl = (String) payload.get("introspectionUrl");
            String tokenUrl = (String) payload.get("tokenUrl");
            String requestorGroupName = (String) payload.get("requestorGroupName");
            String requestorGroupCode = (String) payload.get("requestorGroupCode");

            // Validate required fields
            if (clientId == null || clientSecret == null || introspectionUrl == null) {
                response.put("success", false);
                response.put("message", "Missing required fields: clientId, clientSecret, introspectionUrl");
                return ResponseEntity.badRequest().body(response);
            }

            // Check if credentials already exist for this request
            Optional<IntrospectionCredential> existingOpt =
                    introspectionCredentialRepository.findByRequestId(requestId);

            IntrospectionCredential credential;
            if (existingOpt.isPresent()) {
                // Update existing credential
                credential = existingOpt.get();
                credential.setClientId(clientId);
                credential.setClientSecret(clientSecret);
                credential.setIntrospectionUrl(introspectionUrl);
                credential.setTokenUrl(tokenUrl);
                credential.setRequestorGroupName(requestorGroupName);
                credential.setRequestorGroupCode(requestorGroupCode);
                credential.setIsActive(true);
                log.info("Updated existing introspection credential for request {}", requestId);
            } else {
                // Create new credential
                credential = IntrospectionCredential.builder()
                        .requestId(requestId)
                        .subscription(subscription)
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .introspectionUrl(introspectionUrl)
                        .tokenUrl(tokenUrl)
                        .requestorGroupName(requestorGroupName)
                        .requestorGroupCode(requestorGroupCode)
                        .isActive(true)
                        .usageCount(0L)
                        .build();
                log.info("Created new introspection credential for request {} (clientId: {})",
                        requestId, clientId);
            }

            introspectionCredentialRepository.save(credential);

            response.put("success", true);
            response.put("message", "Introspection credentials stored successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error storing introspection credentials for request {}", requestId, e);
            response.put("success", false);
            response.put("message", "An internal error occurred while storing credentials. Please try again or contact support.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Receives subscription status-change notifications from the Group Admin (DHG). Reachable only
     * over the mTLS listener ({@code MtlsOnlyFilter}), so the caller is an authenticated trusted peer.
     * Informational: updates a matching local subscription if present, otherwise acknowledges and logs.
     */
    @PostMapping("/status-callback")
    public ResponseEntity<Map<String, Object>> statusCallback(@RequestBody Map<String, Object> payload) {
        String requestId = (String) payload.get("requestId");
        String previousStatus = (String) payload.get("previousStatus");
        String newStatus = (String) payload.get("newStatus");
        String message = (String) payload.get("message");
        String introspectionUrl = (String) payload.get("introspectionUrl");

        log.info("Received status-change callback from group admin: requestId={}, {} -> {}",
                requestId, previousStatus, newStatus);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("requestId", requestId);

        if (requestId == null || requestId.isBlank()) {
            response.put("received", false);
            response.put("message", "Missing requestId");
            return ResponseEntity.badRequest().body(response);
        }

        Optional<AgreementSubscription> subOpt = subscriptionRepository.findByRequestId(requestId);
        if (subOpt.isEmpty()) {
            log.warn("Status callback for unknown local subscription: {} (acknowledged)", requestId);
            response.put("received", true);
            response.put("matched", false);
            return ResponseEntity.ok(response);
        }

        AgreementSubscription subscription = subOpt.get();
        // Best-effort map onto the local enum; unknown statuses still record message/timestamp.
        if (newStatus != null && !newStatus.isBlank()) {
            try {
                subscription.setStatus(AgreementSubscription.SubscriptionStatus.valueOf(newStatus));
            } catch (IllegalArgumentException ex) {
                log.warn("Group admin status '{}' has no local mapping for {}; recording message only",
                        newStatus, requestId);
            }
        }
        if (message != null) subscription.setStatusMessage(message);
        if (introspectionUrl != null && !introspectionUrl.isBlank()) {
            subscription.setIntrospectionUrl(introspectionUrl);
        }
        subscription.setStatusChangedAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        response.put("received", true);
        response.put("matched", true);
        return ResponseEntity.ok(response);
    }

    // ==================== Ping / Health Endpoints ====================

    /** Verifies a subscription is still active (called on user login to keep systems in sync). */
    @PostMapping("/ping")
    public ResponseEntity<AgreementPingResponse> pingSubscription(
            @RequestBody AgreementPingRequest request) {
        log.debug("Subscription ping from: {}, user: {}", 
                request.getRequestorGroupId(), request.getUserId());
        
        AgreementPingResponse response = subscriptionService.handlePing(request);
        return ResponseEntity.ok(response);
    }

    /** Health check for the Requestor Manager. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "dataholder-subscription-api",
                "timestamp", System.currentTimeMillis()
        ));
    }
}