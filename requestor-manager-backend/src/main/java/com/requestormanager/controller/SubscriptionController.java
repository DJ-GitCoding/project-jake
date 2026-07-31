/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.controller;

import com.requestormanager.dto.ApiResponse;
import com.requestormanager.dto.PagedResponse;
import com.requestormanager.dto.SubscriptionDto.*;
import com.requestormanager.entity.SubscriptionRequest.SubscriptionStatus;
import com.requestormanager.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Subscriptions", description = """
        Subscription lifecycle management for data holder agreements.
        
        WORKFLOW (Requestor side):
        ┌──────────┐    ┌───────────┐    ┌──────────┐    ┌─────────┐    ┌─────────┐    ┌────────┐
        │  CREATE   │───▶│  SUBMIT   │───▶│ PENDING  │───▶│APPROVED │───▶│ TESTING │───▶│ ACTIVE │
        │  (DRAFT)  │    │(SUBMITTED)│    │(REVIEW)  │    │         │    │         │    │        │
        └──────────┘    └───────────┘    └──────────┘    └─────────┘    └─────────┘    └────────┘
                                                                            │              │
         Steps YOU control:                                                 │              │
         1. Create subscription ──▶ POST /                                  ▼              ▼
         2. Submit to DH ──────▶ POST /{id}/submit              POST /{id}/start-testing   │
         3. Wait for DH approval (or poll via /{id}/refresh)    POST /{id}/run-test         │
         4. Start testing ─────▶ POST /{id}/start-testing       POST /{id}/activate ────────┘
         5. Run test suite ────▶ POST /{id}/run-test
         6. Activate ──────────▶ POST /{id}/activate (requires tests PASSED)
        """)
@SecurityRequirement(name = "Bearer Authentication")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    // ==================== Subscription Request Endpoints ====================

    @PostMapping
    @Operation(summary = "Step 1: Create subscription request",
               description = "Create a new subscription request to a data holder agreement template. " +
                           "This creates a DRAFT. Set submitImmediately=true to skip the draft stage.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Subscription request created successfully"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Subscription request already exists for this data holder"
        )
    })
    public ResponseEntity<ApiResponse<SubscriptionRequestResponse>> createSubscriptionRequest(
            @Valid @RequestBody CreateRequest request) {
        SubscriptionRequestResponse response = subscriptionService.createSubscriptionRequest(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Subscription request created successfully", response));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Step 2: Submit subscription request",
               description = "Submit a DRAFT subscription request to the data holder for review. " +
                           "After this, the request moves to SUBMITTED → PENDING_REVIEW as the DH processes it.")
    public ResponseEntity<ApiResponse<SubscriptionRequestResponse>> submitSubscriptionRequest(
            @PathVariable Long id) {
        SubscriptionRequestResponse response = subscriptionService.submitSubscriptionRequest(id);
        return ResponseEntity.ok(ApiResponse.success("Subscription request submitted successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update draft subscription request",
               description = "Update a subscription request that is still in DRAFT status. Cannot update after submission.")
    public ResponseEntity<ApiResponse<SubscriptionRequestResponse>> updateSubscriptionRequest(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRequest request) {
        SubscriptionRequestResponse response = subscriptionService.updateSubscriptionRequest(id, request);
        return ResponseEntity.ok(ApiResponse.success("Subscription request updated successfully", response));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel subscription request",
               description = "Cancel a subscription request. Only allowed in DRAFT, SUBMITTED, or PENDING_REVIEW status.")
    public ResponseEntity<ApiResponse<SubscriptionRequestResponse>> cancelSubscriptionRequest(
            @PathVariable Long id) {
        SubscriptionRequestResponse response = subscriptionService.cancelSubscriptionRequest(id);
        return ResponseEntity.ok(ApiResponse.success("Subscription request cancelled", response));
    }

    @PostMapping("/{id}/refresh")
    @Operation(summary = "Refresh status from data holder",
               description = "Poll the data holder to get the latest status for this subscription. " +
                           "Useful when waiting for approval — the DH may have approved it since your last check.")
    public ResponseEntity<ApiResponse<SubscriptionRequestResponse>> refreshStatus(
            @PathVariable Long id) {
        SubscriptionRequestResponse response = subscriptionService.refreshStatus(id);
        return ResponseEntity.ok(ApiResponse.success("Status refreshed", response));
    }

    // ==================== Testing Workflow Endpoints ====================

    @PostMapping("/{id}/start-testing")
    @Operation(summary = "Step 4: Start testing",
               description = """
                       Initiate the testing phase for an APPROVED subscription.
                       
                       This calls the data holder to transition the subscription to TESTING status.
                       Both the requestor (you) and the DH admin can trigger this once the subscription is APPROVED.
                       
                       Prerequisite: status must be APPROVED.
                       Next step: POST /{id}/run-test to execute the test suite.
                       """)
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Testing started — status is now TESTING"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Cannot start testing — subscription not in APPROVED status"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Subscription request not found"
        )
    })
    public ResponseEntity<ApiResponse<SubscriptionRequestResponse>> startTesting(
            @PathVariable Long id) {
        SubscriptionRequestResponse response = subscriptionService.startTesting(id);
        return ResponseEntity.ok(ApiResponse.success("Testing started successfully", response));
    }

    @PostMapping("/{id}/run-test")
    @Operation(summary = "Step 5: Run test suite",
               description = """
                       Execute the test suite for a subscription in TESTING status.
                       
                       This calls the data holder to run validation and RDAP tests against the agreement.
                       Returns detailed test results including per-test-case pass/fail status.
                       
                       Both the requestor and DH admin can trigger this. You can re-run tests if they fail
                       after correcting issues.
                       
                       Prerequisite: status must be TESTING.
                       On success: tests must PASS before you can POST /{id}/activate.
                       """)
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Tests executed — check result field for PASSED/FAILED"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Cannot run tests — subscription not in TESTING status"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Subscription request not found"
        )
    })
    public ResponseEntity<ApiResponse<TestExecutionResult>> runTest(
            @PathVariable Long id) {
        TestExecutionResult response = subscriptionService.runTest(id);
        return ResponseEntity.ok(ApiResponse.success("Tests executed", response));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Step 6: Activate subscription",
               description = """
                       Activate a subscription after successful testing.
                       
                       This calls the data holder to finalize the agreement and make it live.
                       Upon activation, a DataHolderAgreement record is created and Keycloak introspection
                       credentials are provisioned and delivered to the data holder.
                       
                       Prerequisite: status must be TESTING and tests must have PASSED on the DH side.
                       Both the requestor and DH admin can trigger this.
                       """)
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Subscription activated — agreement is now live"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Cannot activate — tests must pass first, or subscription not in TESTING status"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Subscription request not found"
        )
    })
    public ResponseEntity<ApiResponse<SubscriptionRequestResponse>> activateSubscription(
            @PathVariable Long id) {
        SubscriptionRequestResponse response = subscriptionService.activateSubscription(id);
        return ResponseEntity.ok(ApiResponse.success("Subscription activated successfully", response));
    }

    // ==================== Query Endpoints ====================

    @GetMapping("/{id}")
    @Operation(summary = "Get subscription request",
               description = "Get full details of a subscription request by ID, including current status and test results.")
    public ResponseEntity<ApiResponse<SubscriptionRequestResponse>> getSubscriptionRequest(
            @PathVariable Long id) {
        SubscriptionRequestResponse response = subscriptionService.getSubscriptionRequest(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/by-internal-id/{internalRequestId}")
    @Operation(summary = "Get subscription request by internal ID",
               description = "Get subscription request by its internal request ID (SUB-xxx format).")
    public ResponseEntity<ApiResponse<SubscriptionRequestResponse>> getSubscriptionRequestByInternalId(
            @PathVariable String internalRequestId) {
        SubscriptionRequestResponse response = subscriptionService.getSubscriptionRequestByInternalId(internalRequestId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "Get all subscription requests",
               description = "Get all subscription requests accessible to the current user. " +
                           "Master/Group admins see all; regular users see only their groups' requests. " +
                           "Pass 'page' to receive a paginated PagedResponse (with optional 'search', " +
                           "'status', 'requestorGroupId' filters); omit 'page' for the full list.")
    public ResponseEntity<ApiResponse<?>> getAllSubscriptionRequests(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) Long requestorGroupId,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        if (page == null) {
            // Backward-compatible: no pagination params -> full list (server-to-server callers).
            List<SubscriptionRequestResponse> responses = subscriptionService.getAllSubscriptionRequests();
            return ResponseEntity.ok(ApiResponse.success(responses));
        }
        Sort sort = Sort.by("asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 500), sort);
        PagedResponse<SubscriptionRequestResponse> paged = PagedResponse.of(
                subscriptionService.getAllSubscriptionRequests(search, status, requestorGroupId, pageable));
        return ResponseEntity.ok(ApiResponse.success(paged));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get subscription request stats",
               description = "Aggregate status counts for the current user's accessible subscription requests. " +
                           "Returns 'total' and a 'byStatus' map (status name -> count), respecting the same " +
                           "role scoping as the list endpoint. Lets the UI keep tab badges accurate when only " +
                           "a single page of requests is loaded.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSubscriptionStats() {
        Map<String, Object> stats = subscriptionService.getSubscriptionStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/requestor-group/{requestorGroupId}")
    @Operation(summary = "Get subscription requests by group",
               description = "Get all subscription requests for a specific requestor group.")
    public ResponseEntity<ApiResponse<List<SubscriptionRequestResponse>>> getSubscriptionRequestsByGroup(
            @PathVariable Long requestorGroupId) {
        List<SubscriptionRequestResponse> responses = subscriptionService.getSubscriptionRequestsByGroup(requestorGroupId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending subscription requests",
               description = "Get subscription requests that are waiting for data holder review (SUBMITTED or PENDING_REVIEW).")
    public ResponseEntity<ApiResponse<List<SubscriptionSummary>>> getPendingSubscriptionRequests() {
        List<SubscriptionSummary> responses = subscriptionService.getPendingSubscriptionRequests();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/actionable")
    @Operation(summary = "Get actionable subscription requests",
               description = """
                       Get subscription requests that require YOUR action:
                       - APPROVED → you need to call start-testing
                       - TESTING → you need to run-test then activate
                       
                       This is the best endpoint to check "what do I need to do next?"
                       """)
    public ResponseEntity<ApiResponse<List<SubscriptionRequestResponse>>> getActionableSubscriptionRequests() {
        List<SubscriptionRequestResponse> responses = subscriptionService.getActionableSubscriptionRequests();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    // ==================== Data Holder Agreement Endpoints ====================

    @GetMapping("/agreements")
    @Operation(summary = "Get all active agreements",
               description = "Get all data holder agreements (finalized, active subscriptions). " +
                           "These are the agreements you can actually use for RDAP queries.")
    public ResponseEntity<ApiResponse<List<DataHolderAgreementResponse>>> getAllDataHolderAgreements() {
        List<DataHolderAgreementResponse> responses = subscriptionService.getAllDataHolderAgreements();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/agreements/{id}")
    @Operation(summary = "Get data holder agreement",
               description = "Get a specific data holder agreement by ID.")
    public ResponseEntity<ApiResponse<DataHolderAgreementResponse>> getDataHolderAgreement(
            @PathVariable Long id) {
        DataHolderAgreementResponse response = subscriptionService.getDataHolderAgreement(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/agreements/requestor-group/{requestorGroupId}")
    @Operation(summary = "Get agreements by group",
               description = "Get all data holder agreements for a specific requestor group.")
    public ResponseEntity<ApiResponse<List<DataHolderAgreementResponse>>> getDataHolderAgreementsByGroup(
            @PathVariable Long requestorGroupId) {
        List<DataHolderAgreementResponse> responses = subscriptionService.getDataHolderAgreementsByGroup(requestorGroupId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/agreements/requestor-group/{requestorGroupId}/active")
    @Operation(summary = "Get active agreements by group",
               description = "Get only the currently active and effective agreements for a requestor group. " +
                           "These are the ones valid right now for RDAP queries.")
    public ResponseEntity<ApiResponse<List<DataHolderAgreementResponse>>> getActiveDataHolderAgreementsByGroup(
            @PathVariable Long requestorGroupId) {
        List<DataHolderAgreementResponse> responses = subscriptionService.getActiveDataHolderAgreementsByGroup(requestorGroupId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    // ==================== Callback Endpoint ====================

    @PostMapping("/callback")
    @Operation(summary = "Status change callback (DH → Requestor)",
               description = "Callback endpoint that the data holder calls to notify this server about status changes. " +
                           "You typically don't call this yourself — the DH admin calls it automatically when it " +
                           "approves, denies, or changes the subscription status.")
    public ResponseEntity<Void> handleStatusCallback(@RequestBody StatusChangeCallback callback) {
        log.info("Received status callback: requestId={}, newStatus={}",
                callback.getRequestId(), callback.getNewStatus());
        subscriptionService.handleStatusCallback(callback);
        return ResponseEntity.ok().build();
    }
}