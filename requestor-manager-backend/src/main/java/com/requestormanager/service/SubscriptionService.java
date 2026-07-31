/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.service;

import com.requestormanager.dto.DataHolderGroupDto;
import com.requestormanager.dto.SubscriptionDto.*;
import com.requestormanager.entity.*;
import com.requestormanager.entity.SubscriptionRequest.SubscriptionStatus;
import com.requestormanager.enums.UserType;
import com.requestormanager.exception.CustomExceptions.*;
import com.requestormanager.repository.*;
import com.requestormanager.security.KeycloakAuthService.KeycloakUser;
import com.requestormanager.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRequestRepository subscriptionRequestRepository;
    private final DataHolderAgreementRepository dataHolderAgreementRepository;
    private final RequestorGroupRepository requestorGroupRepository;
    private final DataHolderGroupRepository dataHolderGroupRepository;
    private final DataHolderGroupClientService dataHolderGroupClientService;
    private final SubscriptionCredentialService credentialService;
    private final SecurityUtils securityUtils;

    @Value("${requestor-manager.callback.base-url:http://localhost:8081}")
    private String callbackBaseUrl;

    @Value("${requestor-manager.agent.id:requestor-manager-agent-001}")
    private String agentId;

    // ==================== Create Subscription Request ====================

    @Transactional
    public SubscriptionRequestResponse createSubscriptionRequest(CreateRequest request) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        // Validate requestor group exists and user has access
        RequestorGroup requestorGroup = requestorGroupRepository.findById(request.getRequestorGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("RequestorGroup", "id", request.getRequestorGroupId()));

        validateGroupAccess(currentUser, requestorGroup);

        // Validate data holder exists and is active
        DataHolderGroup dataHolderGroup = dataHolderGroupRepository.findById(request.getDataHolderGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("DataHolderGroup", "id", request.getDataHolderGroupId()));

        if (!dataHolderGroup.getActive()) {
            throw new BadRequestException("Data holder is not currently active");
        }

        // Check for existing pending/active subscription with this data holder
        List<SubscriptionStatus> blockingStatuses = Arrays.asList(
                SubscriptionStatus.DRAFT,
                SubscriptionStatus.SUBMITTED,
                SubscriptionStatus.PENDING_REVIEW,
                SubscriptionStatus.APPROVED,
                SubscriptionStatus.TESTING,
                SubscriptionStatus.ACTIVE
        );

        List<SubscriptionRequest> existingForDataHolderGroup = subscriptionRequestRepository
                .findByRequestorGroupIdAndDataHolderGroupId(request.getRequestorGroupId(), request.getDataHolderGroupId());

        Optional<SubscriptionRequest> blockingRequest = existingForDataHolderGroup.stream()
                .filter(s -> blockingStatuses.contains(s.getStatus()))
                .findFirst();

        if (blockingRequest.isPresent()) {
            SubscriptionRequest existing = blockingRequest.get();
            throw new BadRequestException(
                    "Your requestor group already has a " + existing.getStatus().name().toLowerCase().replace('_', ' ')
                    + " subscription with " + dataHolderGroup.getName()
                    + " (template: " + (existing.getTemplateName() != null ? existing.getTemplateName() : existing.getTemplateId()) + "). "
                    + "Only one subscription per data holder is allowed. "
                    + "Please cancel or wait for the existing request to complete before creating a new one.");
        }

        // Create the subscription request
        SubscriptionRequest subscriptionRequest = SubscriptionRequest.builder()
                .requestorGroup(requestorGroup)
                .dataHolderGroup(dataHolderGroup)
                .templateId(request.getTemplateId())
                .templateName(request.getTemplateName())
                // Contact info
                .requestorFirstName(request.getRequestorFirstName())
                .requestorLastName(request.getRequestorLastName())
                .requestorOrganization(request.getRequestorOrganization())
                .requestorEmail(request.getRequestorEmail())
                .requestorPhone(request.getRequestorPhone())
                .requestorAddress(request.getRequestorAddress())
                .requestorCity(request.getRequestorCity())
                .requestorStateProvince(request.getRequestorStateProvince())
                .requestorPostalCode(request.getRequestorPostalCode())
                .requestorCountry(request.getRequestorCountry())
                // Request details
                .reasonForUse(request.getReasonForUse())
                .requestedAccessLevel(request.getRequestedAccessLevel())
                .additionalNotes(request.getAdditionalNotes())
                .introspectionUrl(request.getIntrospectionUrl() != null ? request.getIntrospectionUrl()
                        : requestorGroup.getDefaultIntrospectionUrl())
                // Status
                .status(SubscriptionStatus.DRAFT)
                // Audit
                .createdByKeycloakId(currentUser.getSub())
                .createdByEmail(currentUser.getEmail())
                .createdByName(currentUser.getDisplayName())
                .build();

        subscriptionRequest = subscriptionRequestRepository.save(subscriptionRequest);
        log.info("Subscription request created: {} for group {} to dataholdergroup {}", 
                subscriptionRequest.getInternalRequestId(), requestorGroup.getName(), dataHolderGroup.getCode());

        // If submitImmediately is true, submit right away
        if (Boolean.TRUE.equals(request.getSubmitImmediately())) {
            return submitSubscriptionRequest(subscriptionRequest.getId());
        }

        return SubscriptionRequestResponse.fromEntity(subscriptionRequest);
    }

    // ==================== Submit Subscription Request ====================

    @Transactional
    public SubscriptionRequestResponse submitSubscriptionRequest(Long id) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        SubscriptionRequest subscriptionRequest = subscriptionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionRequest", "id", id));

        validateGroupAccess(currentUser, subscriptionRequest.getRequestorGroup());

        if (subscriptionRequest.getStatus() != SubscriptionStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT subscription requests can be submitted");
        }

        // Build the initiation request for the dataholdergroup
        DataHolderGroupDto.InitiationRequest initiationRequest = buildInitiationRequest(subscriptionRequest);

        // Send to dataholdergroup
        DataHolderGroupDto.InitiationResponse response = dataHolderGroupClientService.initiateAgreement(
                subscriptionRequest.getDataHolderGroup().getId(), initiationRequest);

        if (response.getSuccess()) {
            subscriptionRequest.setStatus(SubscriptionStatus.SUBMITTED);
            subscriptionRequest.setExternalRequestId(response.getRequestId());
            subscriptionRequest.setStatusMessage("Request submitted successfully");
            subscriptionRequest.setDataholderAgentId(response.getDataHolderGroupCode());
            subscriptionRequest.setSubmittedAt(LocalDateTime.now());
            subscriptionRequest.setStatusChangedAt(LocalDateTime.now());
            subscriptionRequest.setExpiresAt(LocalDateTime.now().plusDays(30));

            log.info("Subscription request {} submitted to dataholdergroup, external ID: {}", 
                    subscriptionRequest.getInternalRequestId(), response.getRequestId());
        } else {
            String message = response.getMessage() != null ? response.getMessage() : "Submission failed";
            boolean isExistingSubscription = message.toLowerCase().contains("already")
                    && (message.toLowerCase().contains("subscription") || message.toLowerCase().contains("exists"));

            if (isExistingSubscription) {
                subscriptionRequest.setStatus(SubscriptionStatus.DECLINED);
                subscriptionRequest.setStatusMessage(
                        "The data holder rejected this request: your requestor group already has an existing "
                        + "subscription with this data holder. Only one subscription per data holder is allowed. "
                        + "Dataholder response: " + message);
                subscriptionRequest.setStatusChangedAt(LocalDateTime.now());

                if (response.getRequestId() != null) {
                    subscriptionRequest.setExternalRequestId(response.getRequestId());
                }

                log.warn("Subscription request {} rejected by dataholdergroup — existing subscription for group: {}", 
                        subscriptionRequest.getInternalRequestId(), message);
            } else {
                subscriptionRequest.setStatus(SubscriptionStatus.DECLINED);
                subscriptionRequest.setStatusMessage("Submission failed: " + message);
                subscriptionRequest.setStatusChangedAt(LocalDateTime.now());

                log.warn("Subscription request {} failed to submit: {}", 
                        subscriptionRequest.getInternalRequestId(), message);
            }
        }

        subscriptionRequest = subscriptionRequestRepository.save(subscriptionRequest);
        return SubscriptionRequestResponse.fromEntity(subscriptionRequest);
    }

    // ==================== Update Draft Subscription ====================

    @Transactional
    public SubscriptionRequestResponse updateSubscriptionRequest(Long id, UpdateRequest request) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        SubscriptionRequest subscriptionRequest = subscriptionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionRequest", "id", id));

        validateGroupAccess(currentUser, subscriptionRequest.getRequestorGroup());

        if (subscriptionRequest.getStatus() != SubscriptionStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT subscription requests can be updated");
        }

        if (request.getRequestorFirstName() != null) subscriptionRequest.setRequestorFirstName(request.getRequestorFirstName());
        if (request.getRequestorLastName() != null) subscriptionRequest.setRequestorLastName(request.getRequestorLastName());
        if (request.getRequestorOrganization() != null) subscriptionRequest.setRequestorOrganization(request.getRequestorOrganization());
        if (request.getRequestorEmail() != null) subscriptionRequest.setRequestorEmail(request.getRequestorEmail());
        if (request.getRequestorPhone() != null) subscriptionRequest.setRequestorPhone(request.getRequestorPhone());
        if (request.getRequestorAddress() != null) subscriptionRequest.setRequestorAddress(request.getRequestorAddress());
        if (request.getRequestorCity() != null) subscriptionRequest.setRequestorCity(request.getRequestorCity());
        if (request.getRequestorStateProvince() != null) subscriptionRequest.setRequestorStateProvince(request.getRequestorStateProvince());
        if (request.getRequestorPostalCode() != null) subscriptionRequest.setRequestorPostalCode(request.getRequestorPostalCode());
        if (request.getRequestorCountry() != null) subscriptionRequest.setRequestorCountry(request.getRequestorCountry());
        if (request.getReasonForUse() != null) subscriptionRequest.setReasonForUse(request.getReasonForUse());
        if (request.getRequestedAccessLevel() != null) subscriptionRequest.setRequestedAccessLevel(request.getRequestedAccessLevel());
        if (request.getAdditionalNotes() != null) subscriptionRequest.setAdditionalNotes(request.getAdditionalNotes());
        if (request.getIntrospectionUrl() != null) subscriptionRequest.setIntrospectionUrl(request.getIntrospectionUrl());

        subscriptionRequest = subscriptionRequestRepository.save(subscriptionRequest);
        log.info("Subscription request {} updated", subscriptionRequest.getInternalRequestId());

        return SubscriptionRequestResponse.fromEntity(subscriptionRequest);
    }

    // ==================== Cancel Subscription Request ====================

    @Transactional
    public SubscriptionRequestResponse cancelSubscriptionRequest(Long id) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        SubscriptionRequest subscriptionRequest = subscriptionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionRequest", "id", id));

        validateGroupAccess(currentUser, subscriptionRequest.getRequestorGroup());

        List<SubscriptionStatus> cancellableStatuses = Arrays.asList(
                SubscriptionStatus.DRAFT,
                SubscriptionStatus.SUBMITTED,
                SubscriptionStatus.PENDING_REVIEW
        );

        if (!cancellableStatuses.contains(subscriptionRequest.getStatus())) {
            throw new BadRequestException("Subscription request cannot be cancelled in current status: " 
                    + subscriptionRequest.getStatus());
        }

        subscriptionRequest.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRequest.setStatusMessage("Cancelled by user");
        subscriptionRequest.setStatusChangedAt(LocalDateTime.now());

        subscriptionRequest = subscriptionRequestRepository.save(subscriptionRequest);
        log.info("Subscription request {} cancelled by {}", 
                subscriptionRequest.getInternalRequestId(), currentUser.getEmail());

        return SubscriptionRequestResponse.fromEntity(subscriptionRequest);
    }

    // ==================== Testing Workflow Methods ====================

    @Transactional
    public SubscriptionRequestResponse startTesting(Long id) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        SubscriptionRequest subscriptionRequest = subscriptionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionRequest", "id", id));

        validateGroupAccess(currentUser, subscriptionRequest.getRequestorGroup());

        return startTestingInternal(subscriptionRequest, currentUser.getEmail());
    }

    /**
     * Core APPROVED→TESTING transition, independent of the security context so it can be driven by a
     * user action ({@link #startTesting(Long)}) or by the automated post-approval advancer
     * ({@link #autoAdvance(Long, String)}).
     */
    SubscriptionRequestResponse startTestingInternal(SubscriptionRequest subscriptionRequest, String actorEmail) {
        if (subscriptionRequest.getStatus() != SubscriptionStatus.APPROVED) {
            throw new BadRequestException(
                    "Only APPROVED subscriptions can start testing. Current status: "
                    + subscriptionRequest.getStatus()
                    + ". If status is SUBMITTED or PENDING_REVIEW, wait for the data holder to approve it first "
                    + "(or refresh status via POST /{id}/refresh).");
        }

        if (subscriptionRequest.getExternalRequestId() == null) {
            throw new BadRequestException("Subscription request has no external request ID");
        }

        DataHolderGroupDto.WorkflowActionResponse response = dataHolderGroupClientService.startTesting(
                subscriptionRequest.getDataHolderGroup().getId(),
                subscriptionRequest.getExternalRequestId(),
                actorEmail);

        if (response.getSuccess()) {
            subscriptionRequest.setStatus(SubscriptionStatus.TESTING);
            subscriptionRequest.setStatusMessage("Testing started");
            subscriptionRequest.setStatusChangedAt(LocalDateTime.now());

            log.info("Subscription request {} transitioned to TESTING by {}",
                    subscriptionRequest.getInternalRequestId(), actorEmail);
        } else {
            log.warn("Failed to start testing for subscription {}: {}", 
                    subscriptionRequest.getInternalRequestId(), response.getMessage());
            throw new BadRequestException("Failed to start testing: " + response.getMessage());
        }

        subscriptionRequest = subscriptionRequestRepository.save(subscriptionRequest);
        return SubscriptionRequestResponse.fromEntity(subscriptionRequest);
    }

    /**
     * Run tests for a subscription in TESTING status.
     * Maps the full test result including RDAP test data from the dataholdergroup.
     */
    @Transactional
    public TestExecutionResult runTest(Long id) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        SubscriptionRequest subscriptionRequest = subscriptionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionRequest", "id", id));

        validateGroupAccess(currentUser, subscriptionRequest.getRequestorGroup());

        return runTestInternal(subscriptionRequest);
    }

    /**
     * Core test-execution step for a TESTING subscription, independent of the security context.
     * Invoked by {@link #runTest(Long)} (user action) and by the automated post-approval advancer
     * ({@link #autoAdvance(Long, String)}).
     */
    TestExecutionResult runTestInternal(SubscriptionRequest subscriptionRequest) {
        if (subscriptionRequest.getStatus() != SubscriptionStatus.TESTING) {
            throw new BadRequestException(
                    "Tests can only be run for subscriptions in TESTING status. Current status: "
                    + subscriptionRequest.getStatus()
                    + ". You need to call POST /{id}/start-testing first to enter TESTING status.");
        }

        if (subscriptionRequest.getExternalRequestId() == null) {
            throw new BadRequestException("Subscription request has no external request ID");
        }

        // Call dataholdergroup to run tests
        DataHolderGroupDto.TestExecutionResponse response = dataHolderGroupClientService.runTest(
                subscriptionRequest.getDataHolderGroup().getId(),
                subscriptionRequest.getExternalRequestId());

        if (response.getSuccess() && response.getTestResult() != null) {
            DataHolderGroupDto.TestResultInfo tr = response.getTestResult();

            log.info("Test execution completed for subscription {}: {}", 
                    subscriptionRequest.getInternalRequestId(), tr.getResult());

            // Persist the test result in the status message so list endpoints can
            // determine available actions without re-querying the data holder.
            String resultStr = tr.getResult() != null ? tr.getResult().toUpperCase() : "UNKNOWN";
            subscriptionRequest.setStatusMessage("Testing: " + resultStr
                    + (tr.getDetails() != null ? " — " + tr.getDetails() : ""));
            subscriptionRequestRepository.save(subscriptionRequest);

            // Map validation test cases
            List<TestCaseResult> testCases = null;
            if (tr.getTestCases() != null) {
                testCases = tr.getTestCases().stream()
                        .map(tc -> TestCaseResult.builder()
                                .name(tc.getName())
                                .description(tc.getDescription())
                                .passed(tc.getPassed())
                                .errorMessage(tc.getErrorMessage())
                                .build())
                        .collect(Collectors.toList());
            }

            // Map RDAP test case results (new)
            List<RdapTestCaseResult> rdapTestResults = null;
            if (tr.getRdapTestResults() != null) {
                rdapTestResults = tr.getRdapTestResults().stream()
                        .map(this::mapRdapTestCaseResult)
                        .collect(Collectors.toList());
            }

            // Map summary (new)
            TestSummaryResult summary = null;
            if (tr.getSummary() != null) {
                DataHolderGroupDto.TestSummaryInfo s = tr.getSummary();
                summary = TestSummaryResult.builder()
                        .totalValidationTests(s.getTotalValidationTests())
                        .passedValidationTests(s.getPassedValidationTests())
                        .failedValidationTests(s.getFailedValidationTests())
                        .totalRdapTests(s.getTotalRdapTests())
                        .passedRdapTests(s.getPassedRdapTests())
                        .failedRdapTests(s.getFailedRdapTests())
                        .skippedRdapTests(s.getSkippedRdapTests())
                        .errorRdapTests(s.getErrorRdapTests())
                        .totalDurationMs(s.getTotalDurationMs())
                        .build();
            }

            return TestExecutionResult.builder()
                    .subscriptionRequestId(subscriptionRequest.getId())
                    .internalRequestId(subscriptionRequest.getInternalRequestId())
                    .result(tr.getResult())
                    .testedAt(tr.getTestedAt())
                    .details(tr.getDetails())
                    .testCases(testCases)
                    .rdapTestResults(rdapTestResults)
                    .summary(summary)
                    .build();
        } else {
            log.warn("Failed to run tests for subscription {}: {}", 
                    subscriptionRequest.getInternalRequestId(), response.getMessage());
            throw new BadRequestException("Failed to run tests: " + response.getMessage());
        }
    }

    /**
     * Map a single RDAP test case result from the dataholdergroup DTO to our subscription DTO.
     */
    private RdapTestCaseResult mapRdapTestCaseResult(DataHolderGroupDto.RdapTestCaseInfo src) {
        List<RdapTestCheckResult> checks = null;
        if (src.getChecks() != null) {
            checks = src.getChecks().stream()
                    .map(c -> RdapTestCheckResult.builder()
                            .name(c.getName())
                            .category(c.getCategory())
                            .passed(c.getPassed())
                            .message(c.getMessage())
                            .expected(c.getExpected())
                            .actual(c.getActual())
                            .severity(c.getSeverity())
                            .build())
                    .collect(Collectors.toList());
        }

        return RdapTestCaseResult.builder()
                .testDataEntryId(src.getTestDataEntryId())
                .label(src.getLabel())
                .queryType(src.getQueryType())
                .queryValue(src.getQueryValue())
                .requestTypeName(src.getRequestTypeName())
                .resolvedAccessLevel(src.getResolvedAccessLevel())
                .result(src.getResult())
                .message(src.getMessage())
                .durationMs(src.getDurationMs())
                .checks(checks)
                .build();
    }

    @Transactional
    public SubscriptionRequestResponse activateSubscription(Long id) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        SubscriptionRequest subscriptionRequest = subscriptionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionRequest", "id", id));

        validateGroupAccess(currentUser, subscriptionRequest.getRequestorGroup());

        return activateSubscriptionInternal(subscriptionRequest, currentUser.getEmail());
    }

    /**
     * Core TESTING→ACTIVE transition (including introspection-credential provisioning/delivery),
     * independent of the security context. Invoked by {@link #activateSubscription(Long)} (user
     * action) and by the automated post-approval advancer ({@link #autoAdvance(Long, String)}).
     */
    SubscriptionRequestResponse activateSubscriptionInternal(SubscriptionRequest subscriptionRequest, String actorEmail) {
        if (subscriptionRequest.getStatus() != SubscriptionStatus.TESTING) {
            throw new BadRequestException(
                    "Only TESTING subscriptions can be activated. Current status: "
                    + subscriptionRequest.getStatus()
                    + ". Workflow: APPROVED → start-testing → TESTING → run-test → activate.");
        }

        if (subscriptionRequest.getExternalRequestId() == null) {
            throw new BadRequestException("Subscription request has no external request ID");
        }

        // Pre-flight: refresh status from the data holder to check if tests have passed.
        // This prevents a confusing 500 from the DH side and gives the user a clear error.
        try {
            Optional<DataHolderGroupDto.StatusResponse> statusOpt = dataHolderGroupClientService.getAgreementStatus(
                    subscriptionRequest.getDataHolderGroup().getId(), subscriptionRequest.getExternalRequestId());
            if (statusOpt.isPresent()) {
                DataHolderGroupDto.StatusResponse dhStatus = statusOpt.get();
                log.info("Pre-flight status check for {}: DH status={}", 
                        subscriptionRequest.getInternalRequestId(), dhStatus.getStatus());

                // If the DH still reports TESTING, tests may not have passed yet.
                // The DH's activate endpoint requires testResult=PASSED, so we check here
                // and block early with a helpful message instead of forwarding a 500.
                String dhStatusStr = dhStatus.getStatus() != null ? dhStatus.getStatus().toUpperCase() : "";
                if ("TESTING".equals(dhStatusStr)) {
                    // Check testResult field (added to status response) or fall back to statusMessage
                    String testResult = dhStatus.getTestResult() != null ? dhStatus.getTestResult() : "";
                    if (!"PASSED".equalsIgnoreCase(testResult)) {
                        String detail = testResult.isEmpty() 
                            ? "Tests have not been run yet."
                            : "Test result is: " + testResult + ".";
                        throw new BadRequestException(
                            "Cannot activate yet — " + detail
                            + " Please run tests first and wait for them to pass before activating. "
                            + "Use the run-test endpoint or trigger tests from the DH admin portal.");
                    }
                }
            }
        } catch (BadRequestException e) {
            throw e; // Re-throw our own pre-flight errors
        } catch (Exception e) {
            log.warn("Pre-flight status check failed for {}: {} (proceeding with activation attempt)",
                    subscriptionRequest.getInternalRequestId(), e.getMessage());
        }

        DataHolderGroupDto.WorkflowActionResponse response = dataHolderGroupClientService.activateSubscription(
                subscriptionRequest.getDataHolderGroup().getId(),
                subscriptionRequest.getExternalRequestId(),
                actorEmail);

        if (response.getSuccess()) {
            subscriptionRequest.setStatus(SubscriptionStatus.ACTIVE);
            subscriptionRequest.setStatusMessage("Subscription activated successfully");
            subscriptionRequest.setStatusChangedAt(LocalDateTime.now());
            
            if (response.getGrantedAccessLevel() != null) {
                subscriptionRequest.setGrantedAccessLevel(response.getGrantedAccessLevel());
            }

            if (response.getAgreementId() != null) {
                StatusChangeCallback callback = StatusChangeCallback.builder()
                        .requestId(subscriptionRequest.getExternalRequestId())
                        .newStatus("ACTIVE")
                        .agreementId(response.getAgreementId())
                        .grantedAccessLevel(response.getGrantedAccessLevel())
                        .effectiveFrom(response.getEffectiveFrom())
                        .effectiveTo(response.getEffectiveTo())
                        .build();

                DataHolderAgreement agreement = createDataHolderAgreement(subscriptionRequest, callback);
                subscriptionRequest.setDataHolderAgreement(agreement);
                
                log.info("Created DataHolderAgreement {} for subscription request {}", 
                        agreement.getId(), subscriptionRequest.getInternalRequestId());
            }

            log.info("Subscription request {} activated by {}",
                    subscriptionRequest.getInternalRequestId(), actorEmail);

            // Provision Keycloak introspection credentials and deliver to the data holder
            try {
                credentialService.provisionAndDeliver(subscriptionRequest);
                log.info("Introspection credentials provisioned and delivered for subscription {}", 
                        subscriptionRequest.getInternalRequestId());
            } catch (Exception e) {
                // Non-blocking: credentials can be retried later via admin action or scheduled job
                log.error("Failed to provision/deliver introspection credentials for subscription {}: {}",
                        subscriptionRequest.getInternalRequestId(), e.getMessage(), e);
            }
        } else {
            String errorDetail = response.getMessage() != null ? response.getMessage() : "Unknown error";
            // Provide a helpful hint if the failure is likely because tests haven't passed
            boolean likelyTestIssue = errorDetail.toLowerCase().contains("test")
                    || errorDetail.toLowerCase().contains("pass");
            String hint = likelyTestIssue
                    ? " Make sure you have run tests (POST /{id}/run-test) and they have PASSED before activating."
                    : " You may need to run tests first (POST /{id}/run-test) and ensure they pass.";

            log.warn("Failed to activate subscription {}: {}", 
                    subscriptionRequest.getInternalRequestId(), errorDetail);
            throw new BadRequestException("Activation rejected by data holder: " + errorDetail + hint);
        }

        subscriptionRequest = subscriptionRequestRepository.save(subscriptionRequest);
        return SubscriptionRequestResponse.fromEntity(subscriptionRequest);
    }

    // ==================== Automated post-approval advancement ====================

    /**
     * Server-side auto-advance for the post-approval workflow. Once the data holder has APPROVED a
     * subscription, this drives it APPROVED → TESTING → (run tests) → ACTIVE with no user action.
     *
     * <p>Performs at most one transition per invocation so each intermediate state persists long
     * enough for the frontend's status polling to render it — testing shows as "in progress" rather
     * than being skipped straight to ACTIVE. Called on a fixed interval by
     * {@link com.requestormanager.scheduler.SubscriptionAutoAdvanceScheduler}.
     *
     * <p>Reuses the same internal transition methods as the manual endpoints, so activation still
     * provisions and delivers introspection credentials. If testing ever reports a non-PASSED result
     * (the data holder group test engine is currently a pass-only stub), the subscription is halted
     * in TESTING and flagged for manual review instead of being activated.
     *
     * @param id          the local subscription request id
     * @param actorEmail  system identity recorded as the actor for the automated transitions
     */
    @Transactional
    public void autoAdvance(Long id, String actorEmail) {
        SubscriptionRequest subscriptionRequest = subscriptionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionRequest", "id", id));

        // Not yet handed off to the data holder group — nothing to advance.
        if (subscriptionRequest.getExternalRequestId() == null) {
            return;
        }

        // Sync local status from the data holder group (source of truth) before deciding.
        Optional<DataHolderGroupDto.StatusResponse> statusOpt = dataHolderGroupClientService.getAgreementStatus(
                subscriptionRequest.getDataHolderGroup().getId(), subscriptionRequest.getExternalRequestId());
        if (statusOpt.isEmpty()) {
            log.debug("Auto-advance: no status from data holder group for {}, retrying next tick",
                    subscriptionRequest.getInternalRequestId());
            return;
        }
        DataHolderGroupDto.StatusResponse dhStatus = statusOpt.get();
        updateStatusFromDataholder(subscriptionRequest, dhStatus);
        subscriptionRequest = subscriptionRequestRepository.save(subscriptionRequest);

        switch (subscriptionRequest.getStatus()) {
            case APPROVED -> {
                log.info("Auto-advance: {} APPROVED → starting testing",
                        subscriptionRequest.getInternalRequestId());
                startTestingInternal(subscriptionRequest, actorEmail);
            }
            case TESTING -> {
                String testResult = dhStatus.getTestResult() != null
                        ? dhStatus.getTestResult().trim().toUpperCase() : "";
                if (testResult.isEmpty()) {
                    log.info("Auto-advance: {} TESTING → running tests",
                            subscriptionRequest.getInternalRequestId());
                    runTestInternal(subscriptionRequest);
                } else if ("PASSED".equals(testResult)) {
                    log.info("Auto-advance: {} tests PASSED → activating",
                            subscriptionRequest.getInternalRequestId());
                    activateSubscriptionInternal(subscriptionRequest, actorEmail);
                } else {
                    // Defensive halt: leave in TESTING, flag for manual review, do not activate.
                    log.warn("Auto-advance halted for {}: test result = {}. Manual review required.",
                            subscriptionRequest.getInternalRequestId(), testResult);
                    subscriptionRequest.setStatusMessage(
                            "Automated activation halted — testing returned " + testResult
                            + ". Manual review required.");
                    subscriptionRequest.setStatusChangedAt(LocalDateTime.now());
                    subscriptionRequestRepository.save(subscriptionRequest);
                }
            }
            default -> {
                // SUBMITTED / PENDING_REVIEW (awaiting approval) or a terminal state — nothing to do.
            }
        }
    }

    // ==================== Check Status with Dataholder ====================

    @Transactional
    public SubscriptionRequestResponse refreshStatus(Long id) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        SubscriptionRequest subscriptionRequest = subscriptionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionRequest", "id", id));

        validateGroupAccess(currentUser, subscriptionRequest.getRequestorGroup());

        if (subscriptionRequest.getExternalRequestId() == null) {
            throw new BadRequestException("Subscription request has not been submitted yet");
        }

        Optional<DataHolderGroupDto.StatusResponse> statusOpt = dataHolderGroupClientService.getAgreementStatus(
                subscriptionRequest.getDataHolderGroup().getId(), subscriptionRequest.getExternalRequestId());

        if (statusOpt.isPresent()) {
            DataHolderGroupDto.StatusResponse status = statusOpt.get();
            updateStatusFromDataholder(subscriptionRequest, status);
            subscriptionRequest = subscriptionRequestRepository.save(subscriptionRequest);
            log.info("Subscription request {} status refreshed: {}", 
                    subscriptionRequest.getInternalRequestId(), status.getStatus());
        } else {
            log.warn("Could not get status for subscription request {} from dataholdergroup", 
                    subscriptionRequest.getInternalRequestId());
        }

        return SubscriptionRequestResponse.fromEntity(subscriptionRequest);
    }

    // ==================== Handle Callback from Dataholder ====================

    @Transactional
    public void handleStatusCallback(StatusChangeCallback callback) {
        log.info("Received status callback for request: {}, new status: {}", 
                callback.getRequestId(), callback.getNewStatus());

        Optional<SubscriptionRequest> requestOpt = subscriptionRequestRepository
                .findByExternalRequestId(callback.getRequestId());

        if (requestOpt.isEmpty()) {
            log.warn("Received callback for unknown request: {}", callback.getRequestId());
            return;
        }

        SubscriptionRequest subscriptionRequest = requestOpt.get();
        String previousStatus = subscriptionRequest.getStatus().name();

        SubscriptionStatus newStatus = mapDataholderStatus(callback.getNewStatus());
        subscriptionRequest.setStatus(newStatus);
        subscriptionRequest.setStatusMessage(callback.getMessage());
        subscriptionRequest.setStatusChangedAt(callback.getChangedAt() != null ? 
                callback.getChangedAt() : LocalDateTime.now());

        if (callback.getGrantedAccessLevel() != null) {
            subscriptionRequest.setGrantedAccessLevel(callback.getGrantedAccessLevel());
        }

        if (newStatus == SubscriptionStatus.ACTIVE && callback.getAgreementId() != null) {
            DataHolderAgreement agreement = createDataHolderAgreement(subscriptionRequest, callback);
            subscriptionRequest.setDataHolderAgreement(agreement);
            log.info("Created DataHolderAgreement {} for subscription request {}", 
                    agreement.getId(), subscriptionRequest.getInternalRequestId());

            // Provision Keycloak introspection credentials and deliver to the data holder
            try {
                credentialService.provisionAndDeliver(subscriptionRequest);
                log.info("Introspection credentials provisioned via callback for subscription {}", 
                        subscriptionRequest.getInternalRequestId());
            } catch (Exception e) {
                log.error("Failed to provision/deliver introspection credentials via callback for subscription {}: {}",
                        subscriptionRequest.getInternalRequestId(), e.getMessage(), e);
            }
        }

        subscriptionRequestRepository.save(subscriptionRequest);
        log.info("Subscription request {} status updated: {} -> {}", 
                subscriptionRequest.getInternalRequestId(), previousStatus, newStatus);
    }

    // ==================== Query Methods ====================

    @Transactional(readOnly = true)
    public SubscriptionRequestResponse getSubscriptionRequest(Long id) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        SubscriptionRequest subscriptionRequest = subscriptionRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionRequest", "id", id));

        validateGroupAccess(currentUser, subscriptionRequest.getRequestorGroup());

        return SubscriptionRequestResponse.fromEntity(subscriptionRequest);
    }

    @Transactional(readOnly = true)
    public SubscriptionRequestResponse getSubscriptionRequestByInternalId(String internalRequestId) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        SubscriptionRequest subscriptionRequest = subscriptionRequestRepository
                .findByInternalRequestId(internalRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionRequest", "internalRequestId", internalRequestId));

        validateGroupAccess(currentUser, subscriptionRequest.getRequestorGroup());

        return SubscriptionRequestResponse.fromEntity(subscriptionRequest);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionRequestResponse> getSubscriptionRequestsByGroup(Long requestorGroupId) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        RequestorGroup group = requestorGroupRepository.findById(requestorGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("RequestorGroup", "id", requestorGroupId));

        validateGroupAccess(currentUser, group);

        return subscriptionRequestRepository.findByRequestorGroupId(requestorGroupId).stream()
                .map(SubscriptionRequestResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SubscriptionRequestResponse> getAllSubscriptionRequests() {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        List<SubscriptionRequest> requests;
        if (currentUser.getUserType() == UserType.JADDAR_MASTER_ADMIN || 
            currentUser.getUserType() == UserType.GROUP_ADMIN) {
            requests = subscriptionRequestRepository.findAll();
        } else {
            List<Long> userGroupIds = getUserRequestorGroupIds(currentUser);
            if (userGroupIds.isEmpty()) {
                return List.of();
            }
            requests = subscriptionRequestRepository.findByRequestorGroupIdIn(userGroupIds);
        }

        return requests.stream()
                .map(SubscriptionRequestResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Paginated + searchable variant of {@link #getAllSubscriptionRequests()}.
     * Respects the same role scoping (admins see all; others only their groups).
     */
    @Transactional(readOnly = true)
    public Page<SubscriptionRequestResponse> getAllSubscriptionRequests(
            String search, SubscriptionStatus status, Long requestorGroupId, Pageable pageable) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        String s = (search == null || search.isBlank()) ? null : search.trim();

        Page<SubscriptionRequest> page;
        if (currentUser.getUserType() == UserType.JADDAR_MASTER_ADMIN ||
            currentUser.getUserType() == UserType.GROUP_ADMIN) {
            page = subscriptionRequestRepository.searchAll(s, status, requestorGroupId, pageable);
        } else {
            List<Long> userGroupIds = getUserRequestorGroupIds(currentUser);
            if (userGroupIds.isEmpty()) {
                return Page.empty(pageable);
            }
            page = subscriptionRequestRepository.searchByGroups(userGroupIds, s, status, requestorGroupId, pageable);
        }
        return page.map(SubscriptionRequestResponse::fromEntity);
    }

    /**
     * Aggregate status counts for the current user's accessible subscription requests.
     * Returns {@code total} and a {@code byStatus} map (status name -> count), respecting
     * the same role scoping as {@link #getAllSubscriptionRequests()}. Used by the frontend
     * to keep tab badges accurate when only a single page of requests is loaded.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSubscriptionStats() {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        List<Object[]> rows;
        if (currentUser.getUserType() == UserType.JADDAR_MASTER_ADMIN ||
            currentUser.getUserType() == UserType.GROUP_ADMIN) {
            rows = subscriptionRequestRepository.countByStatusAll();
        } else {
            List<Long> userGroupIds = getUserRequestorGroupIds(currentUser);
            if (userGroupIds.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("total", 0L);
                empty.put("byStatus", new HashMap<String, Long>());
                return empty;
            }
            rows = subscriptionRequestRepository.countByStatusForGroups(userGroupIds);
        }

        Map<String, Long> byStatus = new HashMap<>();
        long total = 0L;
        for (Object[] row : rows) {
            SubscriptionStatus status = (SubscriptionStatus) row[0];
            long count = ((Number) row[1]).longValue();
            byStatus.put(status.name(), count);
            total += count;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("byStatus", byStatus);
        return result;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionSummary> getPendingSubscriptionRequests() {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        List<SubscriptionRequest> requests;
        if (currentUser.getUserType() == UserType.JADDAR_MASTER_ADMIN || 
            currentUser.getUserType() == UserType.GROUP_ADMIN) {
            requests = subscriptionRequestRepository.findPendingRequests();
        } else {
            List<Long> userGroupIds = getUserRequestorGroupIds(currentUser);
            requests = subscriptionRequestRepository.findPendingRequests().stream()
                    .filter(r -> userGroupIds.contains(r.getRequestorGroup().getId()))
                    .collect(Collectors.toList());
        }

        return requests.stream()
                .map(SubscriptionSummary::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SubscriptionRequestResponse> getActionableSubscriptionRequests() {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        List<SubscriptionStatus> actionableStatuses = Arrays.asList(
                SubscriptionStatus.APPROVED,
                SubscriptionStatus.TESTING
        );

        List<SubscriptionRequest> requests;
        if (currentUser.getUserType() == UserType.JADDAR_MASTER_ADMIN || 
            currentUser.getUserType() == UserType.GROUP_ADMIN) {
            requests = subscriptionRequestRepository.findByStatusIn(actionableStatuses);
        } else {
            List<Long> userGroupIds = getUserRequestorGroupIds(currentUser);
            if (userGroupIds.isEmpty()) {
                return List.of();
            }
            requests = subscriptionRequestRepository.findByStatusIn(actionableStatuses).stream()
                    .filter(r -> userGroupIds.contains(r.getRequestorGroup().getId()))
                    .collect(Collectors.toList());
        }

        return requests.stream()
                .map(SubscriptionRequestResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // ==================== DataHolderAgreement Methods ====================

    @Transactional(readOnly = true)
    public DataHolderAgreementResponse getDataHolderAgreement(Long id) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        DataHolderAgreement agreement = dataHolderAgreementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataHolderAgreement", "id", id));

        validateGroupAccess(currentUser, agreement.getRequestorGroup());

        return DataHolderAgreementResponse.fromEntity(agreement);
    }

    @Transactional(readOnly = true)
    public List<DataHolderAgreementResponse> getDataHolderAgreementsByGroup(Long requestorGroupId) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        RequestorGroup group = requestorGroupRepository.findById(requestorGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("RequestorGroup", "id", requestorGroupId));

        validateGroupAccess(currentUser, group);

        return dataHolderAgreementRepository.findByRequestorGroupId(requestorGroupId).stream()
                .map(DataHolderAgreementResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DataHolderAgreementResponse> getActiveDataHolderAgreementsByGroup(Long requestorGroupId) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        RequestorGroup group = requestorGroupRepository.findById(requestorGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("RequestorGroup", "id", requestorGroupId));

        validateGroupAccess(currentUser, group);

        return dataHolderAgreementRepository
                .findActiveAndEffectiveByRequestorGroup(requestorGroupId, LocalDateTime.now())
                .stream()
                .map(DataHolderAgreementResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DataHolderAgreementResponse> getAllDataHolderAgreements() {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();

        List<DataHolderAgreement> agreements;
        if (currentUser.getUserType() == UserType.JADDAR_MASTER_ADMIN || 
            currentUser.getUserType() == UserType.GROUP_ADMIN) {
            agreements = dataHolderAgreementRepository.findAll();
        } else {
            List<Long> userGroupIds = getUserRequestorGroupIds(currentUser);
            if (userGroupIds.isEmpty()) {
                return List.of();
            }
            agreements = dataHolderAgreementRepository.findByRequestorGroupIdIn(userGroupIds);
        }

        return agreements.stream()
                .map(DataHolderAgreementResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // ==================== Helper Methods ====================

    private DataHolderGroupDto.InitiationRequest buildInitiationRequest(SubscriptionRequest subscriptionRequest) {
        DataHolderGroupDto.InitiationRequest request = new DataHolderGroupDto.InitiationRequest();
        request.setTemplateId(subscriptionRequest.getTemplateId());
        request.setRequestorGroupId(subscriptionRequest.getRequestorGroup().getId().toString());
        request.setRequestorGroupName(subscriptionRequest.getRequestorGroup().getName());
        request.setRequestorGroupCode(subscriptionRequest.getRequestorGroup().getCode());
        request.setRequestorGroupType(subscriptionRequest.getRequestorGroup().getGroupType());
        request.setRequestorDescription(subscriptionRequest.getRequestorGroup().getDescription());
        request.setRequestorAgentId(agentId);

        request.setRequestorFirstName(subscriptionRequest.getRequestorFirstName());
        request.setRequestorLastName(subscriptionRequest.getRequestorLastName());
        request.setRequestorOrganization(subscriptionRequest.getRequestorOrganization());
        request.setContactEmail(subscriptionRequest.getRequestorEmail());
        request.setRequestorPhone(subscriptionRequest.getRequestorPhone());
        request.setRequestorAddress(subscriptionRequest.getRequestorAddress());
        request.setRequestorCity(subscriptionRequest.getRequestorCity());
        request.setRequestorStateProvince(subscriptionRequest.getRequestorStateProvince());
        request.setRequestorPostalCode(subscriptionRequest.getRequestorPostalCode());
        request.setRequestorCountry(subscriptionRequest.getRequestorCountry());

        request.setReasonForUse(subscriptionRequest.getReasonForUse());
        request.setRequestedAccessLevel(subscriptionRequest.getRequestedAccessLevel());
        request.setAdditionalNotes(subscriptionRequest.getAdditionalNotes());

        request.setCallbackUrl(callbackBaseUrl + "/api/v1/subscriptions/callback");
        request.setIntrospectionUrl(subscriptionRequest.getIntrospectionUrl());

        return request;
    }

    private void updateStatusFromDataholder(SubscriptionRequest subscriptionRequest, 
                                            DataHolderGroupDto.StatusResponse status) {
        SubscriptionStatus newStatus = mapDataholderStatus(status.getStatus());
        subscriptionRequest.setStatus(newStatus);
        subscriptionRequest.setStatusMessage(status.getStatusMessage());
        subscriptionRequest.setStatusChangedAt(status.getUpdatedAt());

        if (newStatus == SubscriptionStatus.ACTIVE && 
            subscriptionRequest.getDataHolderAgreement() == null &&
            status.getAgreementId() != null) {
            
            StatusChangeCallback callback = StatusChangeCallback.builder()
                    .requestId(status.getRequestId())
                    .newStatus(status.getStatus())
                    .agreementId(status.getAgreementId())
                    .message(status.getMessage())
                    .changedAt(status.getUpdatedAt())
                    .build();

            DataHolderAgreement agreement = createDataHolderAgreement(subscriptionRequest, callback);
            subscriptionRequest.setDataHolderAgreement(agreement);

            // Provision Keycloak introspection credentials and deliver to the data holder
            try {
                credentialService.provisionAndDeliver(subscriptionRequest);
                log.info("Introspection credentials provisioned via status refresh for subscription {}", 
                        subscriptionRequest.getInternalRequestId());
            } catch (Exception e) {
                log.error("Failed to provision/deliver introspection credentials via status refresh for subscription {}: {}",
                        subscriptionRequest.getInternalRequestId(), e.getMessage(), e);
            }
        }
    }

    private SubscriptionStatus mapDataholderStatus(String dataholdergroupStatus) {
        if (dataholdergroupStatus == null) {
            return SubscriptionStatus.SUBMITTED;
        }
        return switch (dataholdergroupStatus.toUpperCase()) {
            case "PENDING" -> SubscriptionStatus.PENDING_REVIEW;
            case "APPROVED" -> SubscriptionStatus.APPROVED;
            case "DECLINED", "REJECTED", "DENIED" -> SubscriptionStatus.DECLINED;
            case "TESTING" -> SubscriptionStatus.TESTING;
            case "ACTIVE" -> SubscriptionStatus.ACTIVE;
            case "SUSPENDED" -> SubscriptionStatus.SUSPENDED;
            case "EXPIRED" -> SubscriptionStatus.EXPIRED;
            default -> SubscriptionStatus.SUBMITTED;
        };
    }

    private DataHolderAgreement createDataHolderAgreement(SubscriptionRequest subscriptionRequest,
                                                          StatusChangeCallback callback) {
        DataHolderAgreement agreement = DataHolderAgreement.builder()
                .externalAgreementId(callback.getAgreementId())
                .requestorGroup(subscriptionRequest.getRequestorGroup())
                .dataHolderGroup(subscriptionRequest.getDataHolderGroup())
                .name(subscriptionRequest.getTemplateName() != null ? 
                        subscriptionRequest.getTemplateName() : "Agreement from " + subscriptionRequest.getDataHolderGroup().getName())
                .description("Agreement obtained through subscription request " + subscriptionRequest.getInternalRequestId())
                .templateId(subscriptionRequest.getTemplateId())
                .accessLevel(callback.getGrantedAccessLevel() != null ? 
                        callback.getGrantedAccessLevel() : subscriptionRequest.getRequestedAccessLevel())
                .isActive(true)
                .status(DataHolderAgreement.AgreementStatus.ACTIVE)
                .effectiveFrom(callback.getEffectiveFrom() != null ? 
                        callback.getEffectiveFrom() : LocalDateTime.now())
                .effectiveTo(callback.getEffectiveTo())
                .lastVerifiedAt(LocalDateTime.now())
                .build();

        return dataHolderAgreementRepository.save(agreement);
    }

    private void validateGroupAccess(KeycloakUser currentUser, RequestorGroup group) {
        if (currentUser.getUserType() == UserType.JADDAR_MASTER_ADMIN || 
            currentUser.getUserType() == UserType.GROUP_ADMIN) {
            return;
        }

        if (!userBelongsToGroup(currentUser, group)) {
            throw new AccessDeniedException("You don't have permission to access this requestor group");
        }
    }

    private boolean userBelongsToGroup(KeycloakUser user, RequestorGroup group) {
        if (user.getGroups() == null) {
            return false;
        }
        return user.getGroups().stream()
                .anyMatch(g -> g.equalsIgnoreCase(group.getName()));
    }

    private List<Long> getUserRequestorGroupIds(KeycloakUser user) {
        List<String> keycloakGroups = user.getGroups();
        if (keycloakGroups == null || keycloakGroups.isEmpty()) {
            return List.of();
        }

        return keycloakGroups.stream()
                .map(groupName -> requestorGroupRepository.findByName(groupName))
                .filter(Optional::isPresent)
                .map(opt -> opt.get().getId())
                .collect(Collectors.toList());
    }
}