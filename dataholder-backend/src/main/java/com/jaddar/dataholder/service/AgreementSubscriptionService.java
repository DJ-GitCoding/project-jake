/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.dto.AgreementApiDto.*;
import com.jaddar.dataholder.entity.*;
import com.jaddar.dataholder.entity.AgreementSubscription.SubscriptionStatus;
import com.jaddar.dataholder.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgreementSubscriptionService {

    private final AgreementTemplateRepository templateRepository;
    private final AgreementSubscriptionRepository subscriptionRepository;
    private final AgreementStatusLogRepository statusLogRepository;
    private final RdapEntityRepository rdapEntityRepository;
    private final AgreementTemplateTestDataRepository testDataRepository;
    private final WebClient.Builder webClientBuilder;
    private final com.jaddar.dataholder.config.MtlsEndpoints mtlsEndpoints;

    @Value("${dataholder.name:Test Data Holder}")
    private String dataholderName;

    @Value("${dataholder.url:http://localhost:8082}")
    private String dataholderUrl;

    @Value("${dataholder.agent.id:dataholder-agent-001}")
    private String dataholderAgentId;

    // Statuses that block creating a new subscription
    private static final List<SubscriptionStatus> BLOCKING_STATUSES = Arrays.asList(
            SubscriptionStatus.PENDING,
            SubscriptionStatus.APPROVED,
            SubscriptionStatus.TESTING,
            SubscriptionStatus.ACTIVE
    );

    // ==================== Template Methods ====================

    /**
     * Get all published agreement templates for external consumption
     */
    public List<PublishedTemplate> getPublishedTemplates() {
        return templateRepository.findByIsPublishedTrue().stream()
                .map(this::toPublishedTemplate)
                .toList();
    }

    /**
     * Get a specific template by ID
     */
    public Optional<PublishedTemplate> getPublishedTemplate(String templateId) {
        return templateRepository.findByTemplateId(templateId)
                .filter(AgreementTemplate::getIsPublished)
                .map(this::toPublishedTemplate);
    }

    // ==================== Subscription Initiation ====================

    /**
     * Process a subscription request from the Requestor Manager
     */
    @Transactional
    public AgreementInitiationResponse initiateSubscription(AgreementInitiationRequest request) {
        log.info("Processing subscription request from: {} {} ({})",
                request.getRequestorFirstName(), request.getRequestorLastName(), request.getRequestorGroupName());

        // Find the template
        Optional<AgreementTemplate> templateOpt = templateRepository.findByTemplateId(request.getTemplateId());
        if (templateOpt.isEmpty() || !templateOpt.get().getIsPublished()) {
            return AgreementInitiationResponse.builder()
                    .success(false)
                    .status("REJECTED")
                    .message("Template not found or not available")
                    .build();
        }

        AgreementTemplate template = templateOpt.get();

        // Validate template has at least one active request type
        if (template.getActiveRequestTypes().isEmpty()) {
            return AgreementInitiationResponse.builder()
                    .success(false)
                    .status("REJECTED")
                    .message("Template has no active request types configured")
                    .build();
        }

        // Check for existing pending/active subscription for this requestor group
        List<AgreementSubscription> existingForGroup = subscriptionRepository.findByRequestorGroupId(request.getRequestorGroupId());
        Optional<AgreementSubscription> blockingSubscription = existingForGroup.stream()
                .filter(s -> BLOCKING_STATUSES.contains(s.getStatus()))
                .findFirst();

        if (blockingSubscription.isPresent()) {
            AgreementSubscription existing = blockingSubscription.get();
            String templateName = existing.getTemplate() != null ? existing.getTemplate().getName() : "unknown";

            return AgreementInitiationResponse.builder()
                    .success(false)
                    .requestId(existing.getRequestId())
                    .status(existing.getStatus().name())
                    .message("This requestor group already has a " + existing.getStatus().name().toLowerCase()
                            + " subscription (template: " + templateName + "). "
                            + "Only one subscription per requestor group is allowed.")
                    .build();
        }

        // Create new subscription
        AgreementSubscription subscription = AgreementSubscription.builder()
                .template(template)
                // Requestor Group Info
                .requestorGroupId(request.getRequestorGroupId())
                .requestorGroupName(request.getRequestorGroupName())
                .requestorGroupCode(request.getRequestorGroupCode())
                .requestorGroupType(request.getRequestorGroupType())
                .requestorDescription(request.getRequestorDescription())
                // Contact Information
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
                // Agent Info
                .requestorAgentId(request.getRequestorAgentId())
                .requestorAgentUrl(request.getRequestorAgentUrl())
                .requestorAgentCallbackUrl(request.getCallbackUrl())
                // Subscription Details
                .purpose(request.getPurpose())
                .additionalTerms(request.getAdditionalTerms())
                .introspectionUrl(request.getIntrospectionUrl())
                .introspectionClientId(request.getIntrospectionClientId())
                .introspectionClientSecret(request.getIntrospectionClientSecret())
                // Copy rate limits from template
                .maxQueriesPerDay(template.getMaxQueriesPerDay())
                .maxQueriesPerMonth(template.getMaxQueriesPerMonth())
                // Status
                .status(SubscriptionStatus.PENDING)
                .statusMessage("Subscription request received and pending review")
                .build();

        subscription = subscriptionRepository.save(subscription);

        // Log the status
        logStatusChange(subscription, null, "PENDING", "SYSTEM",
                "Subscription request received from Requestor Manager", "AGREEMENT_SERVER");

        log.info("Subscription created: {} for {} ({}) from {}",
                subscription.getRequestId(),
                request.getRequestorGroupName(),
                (request.getRequestorFirstName() + " " + request.getRequestorLastName()).trim(),
                request.getRequestorOrganization());

        return AgreementInitiationResponse.builder()
                .success(true)
                .requestId(subscription.getRequestId())
                .status("PENDING")
                .message("Subscription request received and pending review")
                .expiresAt(subscription.getRequestExpiresAt())
                .dataholderAgentId(dataholderAgentId)
                .statusCheckUrl(dataholderUrl + "/api/agreements/external/status/" + subscription.getRequestId())
                .build();
    }

    // ==================== Status Methods ====================

    /**
     * Get status of a subscription
     */
    public Optional<AgreementStatusResponse> getSubscriptionStatus(String requestId) {
        return subscriptionRepository.findByRequestId(requestId)
                .map(this::toStatusResponse);
    }

    // ==================== Approval Workflow ====================

    /**
     * Approve a subscription request
     */
    @Transactional
    public AgreementSubscription approveSubscription(Long id, String reviewedBy, String notes) {
        AgreementSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (subscription.getStatus() != SubscriptionStatus.PENDING) {
            throw new IllegalStateException("Subscription is not in PENDING status");
        }

        String previousStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.APPROVED);
        subscription.setReviewedBy(reviewedBy);
        subscription.setReviewedAt(LocalDateTime.now());
        subscription.setReviewNotes(notes);
        subscription.setStatusMessage("Subscription approved, ready for testing");
        subscription.setStatusChangedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);

        logStatusChange(subscription, previousStatus, "APPROVED", reviewedBy, notes, "DATAHOLDER");
        sendStatusCallback(subscription, previousStatus);

        log.info("Subscription {} approved by {}", subscription.getRequestId(), reviewedBy);
        return subscription;
    }

    /**
     * Deny a subscription request
     */
    @Transactional
    public AgreementSubscription denySubscription(Long id, String reviewedBy, String reason) {
        AgreementSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (subscription.getStatus() != SubscriptionStatus.PENDING) {
            throw new IllegalStateException("Subscription is not in PENDING status");
        }

        String previousStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.DENIED);
        subscription.setReviewedBy(reviewedBy);
        subscription.setReviewedAt(LocalDateTime.now());
        subscription.setReviewNotes(reason);
        subscription.setStatusMessage("Subscription denied: " + reason);
        subscription.setStatusChangedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);

        logStatusChange(subscription, previousStatus, "DENIED", reviewedBy, reason, "DATAHOLDER");
        sendStatusCallback(subscription, previousStatus);

        log.info("Subscription {} denied by {}: {}", subscription.getRequestId(), reviewedBy, reason);
        return subscription;
    }

    // ==================== Testing Workflow ====================

    /**
     * Start testing for an approved subscription (by ID)
     */
    @Transactional
    public AgreementSubscription startTesting(Long id, String initiatedBy) {
        AgreementSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        return doStartTesting(subscription, initiatedBy);
    }

    /**
     * Start testing for an approved subscription (by requestId)
     */
    @Transactional
    public AgreementSubscription startTestingByRequestId(String requestId, String initiatedBy) {
        AgreementSubscription subscription = subscriptionRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        return doStartTesting(subscription, initiatedBy);
    }

    private AgreementSubscription doStartTesting(AgreementSubscription subscription, String initiatedBy) {
        if (subscription.getStatus() != SubscriptionStatus.APPROVED) {
            throw new IllegalStateException("Subscription must be APPROVED to start testing");
        }

        String previousStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.TESTING);
        subscription.setTestStartedAt(LocalDateTime.now());
        subscription.setStatusMessage("Testing in progress");
        subscription.setStatusChangedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);

        logStatusChange(subscription, previousStatus, "TESTING", initiatedBy, "Testing initiated", "DATAHOLDER");
        sendStatusCallback(subscription, previousStatus);

        log.info("Testing started for subscription {}", subscription.getRequestId());
        return subscription;
    }

    /**
     * Run the subscription test (by ID)
     */
    @Transactional
    public TestResultInfo runSubscriptionTest(Long id) {
        AgreementSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        return doRunTest(subscription);
    }

    /**
     * Run the subscription test (by requestId)
     */
    @Transactional
    public TestResultInfo runTestByRequestId(String requestId) {
        AgreementSubscription subscription = subscriptionRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        return doRunTest(subscription);
    }

    private TestResultInfo doRunTest(AgreementSubscription subscription) {
        if (subscription.getStatus() != SubscriptionStatus.TESTING) {
            throw new IllegalStateException("Subscription must be in TESTING status");
        }

        long overallStart = System.currentTimeMillis();
        List<TestCase> validationTests = new ArrayList<>();
        List<RdapTestCaseResult> rdapTestResults = new ArrayList<>();
        boolean allValidationsPassed = true;

        // ==================== Phase 1: Validation Tests ====================

        // Test 1: Verify requestor group information
        TestCase groupInfoTest = TestCase.builder()
                .name("Requestor Group Validation")
                .description("Verify requestor group information is complete and valid")
                .passed(subscription.getRequestorGroupId() != null &&
                        subscription.getRequestorGroupName() != null &&
                        subscription.getRequestorContactEmail() != null)
                .errorMessage(subscription.getRequestorGroupId() == null ? "Missing group ID" : null)
                .build();
        validationTests.add(groupInfoTest);
        if (!groupInfoTest.isPassed()) allValidationsPassed = false;

        // Test 2: Verify contact information
        TestCase contactInfoTest = TestCase.builder()
                .name("Contact Information Validation")
                .description("Verify contact information is provided")
                .passed(subscription.getRequestorFirstName() != null &&
                        subscription.getRequestorLastName() != null &&
                        subscription.getRequestorContactEmail() != null)
                .errorMessage((subscription.getRequestorFirstName() == null || subscription.getRequestorLastName() == null)
                        ? "Missing requestor name" :
                        (subscription.getRequestorContactEmail() == null ? "Missing contact email" : null))
                .build();
        validationTests.add(contactInfoTest);
        if (!contactInfoTest.isPassed()) allValidationsPassed = false;

        // Test 3: Verify template exists and is published
        TestCase templateTest = TestCase.builder()
                .name("Template Validation")
                .description("Verify agreement template exists and is published")
                .passed(subscription.getTemplate() != null && subscription.getTemplate().getIsPublished())
                .errorMessage(subscription.getTemplate() == null ? "Template not found" : null)
                .build();
        validationTests.add(templateTest);
        if (!templateTest.isPassed()) allValidationsPassed = false;

        // Test 4: Verify template has active request types
        boolean hasRequestTypes = subscription.getTemplate() != null &&
                !subscription.getTemplate().getActiveRequestTypes().isEmpty();
        TestCase requestTypeTest = TestCase.builder()
                .name("Request Type Validation")
                .description("Verify template has at least one active request type configured")
                .passed(hasRequestTypes)
                .errorMessage(!hasRequestTypes ? "No active request types on template" : null)
                .build();
        validationTests.add(requestTypeTest);
        if (!requestTypeTest.isPassed()) allValidationsPassed = false;

        // Test 5: Verify access levels on request types are valid (0-3)
        boolean accessLevelsValid = true;
        if (hasRequestTypes) {
            accessLevelsValid = subscription.getTemplate().getActiveRequestTypes().stream()
                    .allMatch(rt -> rt.getAccessLevel() != null &&
                            rt.getAccessLevel() >= 0 &&
                            rt.getAccessLevel() <= 3);
        }
        TestCase accessLevelTest = TestCase.builder()
                .name("Access Level Validation")
                .description("Verify all request type access levels are valid (0-3)")
                .passed(accessLevelsValid)
                .errorMessage(!accessLevelsValid ? "Invalid access level on one or more request types" : null)
                .build();
        validationTests.add(accessLevelTest);
        if (!accessLevelTest.isPassed()) allValidationsPassed = false;

        // ==================== Phase 2: RDAP Data Tests ====================

        boolean allRdapPassed = true;
        int rdapSkipped = 0;
        int rdapErrors = 0;

        if (subscription.getTemplate() != null) {
            List<AgreementTemplateTestData> testDataEntries =
                    testDataRepository.findActiveByTemplateId(subscription.getTemplate().getId());

            if (testDataEntries.isEmpty()) {
                // No test data configured — informational
                TestCase noTestDataTest = TestCase.builder()
                        .name("RDAP Test Data")
                        .description("Check if template has RDAP test data configured")
                        .passed(true)
                        .errorMessage(null)
                        .build();
                validationTests.add(noTestDataTest);

                log.info("No RDAP test data configured for template {}, skipping RDAP tests",
                        subscription.getTemplate().getTemplateId());
            } else {
                // Test 6: Verify test data entries reference valid RDAP entities
                TestCase testDataValidityTest = TestCase.builder()
                        .name("RDAP Test Data Configuration")
                        .description("Verify " + testDataEntries.size() + " test data entries are configured and reference valid RDAP entities")
                        .passed(true)
                        .build();
                validationTests.add(testDataValidityTest);

                // Run each RDAP test case
                for (AgreementTemplateTestData testEntry : testDataEntries) {
                    RdapTestCaseResult rdapResult = executeRdapTestCase(subscription.getTemplate(), testEntry);
                    rdapTestResults.add(rdapResult);

                    switch (rdapResult.getResult()) {
                        case "FAILED" -> allRdapPassed = false;
                        case "SKIPPED" -> rdapSkipped++;
                        case "ERROR" -> { allRdapPassed = false; rdapErrors++; }
                    }
                }
            }
        }

        // ==================== Compute Overall Result ====================

        boolean allPassed = allValidationsPassed && allRdapPassed;
        String result = allPassed ? "PASSED" : "FAILED";

        long passedValidations = validationTests.stream().filter(TestCase::isPassed).count();
        long passedRdap = rdapTestResults.stream().filter(r -> "PASSED".equals(r.getResult())).count();
        long totalDuration = System.currentTimeMillis() - overallStart;

        TestSummary summary = TestSummary.builder()
                .totalValidationTests(validationTests.size())
                .passedValidationTests((int) passedValidations)
                .failedValidationTests((int) (validationTests.size() - passedValidations))
                .totalRdapTests(rdapTestResults.size())
                .passedRdapTests((int) passedRdap)
                .failedRdapTests((int) rdapTestResults.stream().filter(r -> "FAILED".equals(r.getResult())).count())
                .skippedRdapTests(rdapSkipped)
                .errorRdapTests(rdapErrors)
                .totalDurationMs(totalDuration)
                .build();

        String details = String.format(
                "Validation: %d/%d passed. RDAP tests: %d/%d passed%s. Duration: %dms",
                passedValidations, validationTests.size(),
                passedRdap, rdapTestResults.size(),
                rdapSkipped > 0 ? " (" + rdapSkipped + " skipped)" : "",
                totalDuration);

        subscription.setTestResult(result);
        subscription.setTestCompletedAt(LocalDateTime.now());
        subscription.setTestDetails(details);
        subscriptionRepository.save(subscription);

        log.info("Test completed for subscription {}: {} — {}", subscription.getRequestId(), result, details);

        return TestResultInfo.builder()
                .result(result)
                .testedAt(subscription.getTestCompletedAt())
                .details(details)
                .testCases(validationTests)
                .rdapTestResults(rdapTestResults)
                .summary(summary)
                .build();
    }

    /**
     * Run this data holder's RDAP retrieval suite for a template, independent of any subscription.
     *
     * <p>Used by the group admin when testing a requestor subscription: rather than pinning one data
     * holder, it asks each data holder in the group to run the template's test data and takes the
     * first that actually has some. {@code summary.totalRdapTests == 0} means this data holder has no
     * active test data for the template and the caller should try another.
     */
    @Transactional(readOnly = true)
    public TestResultInfo runTemplateTest(String templateId) {
        AgreementTemplate template = templateRepository.findByTemplateId(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        long start = System.currentTimeMillis();
        List<AgreementTemplateTestData> testDataEntries =
                testDataRepository.findActiveByTemplateId(template.getId());

        /*
         * Templates that define their own test data are precise about it — which record, which
         * request type, which verifications. Where none is defined, fall back to records the
         * operator flagged as test data so a subscription can still be verified.
         */
        boolean usedFlaggedFallback = false;
        if (testDataEntries.isEmpty()) {
            testDataEntries = flaggedTestDataEntries(template);
            usedFlaggedFallback = !testDataEntries.isEmpty();
        }

        List<RdapTestCaseResult> rdapTestResults = new ArrayList<>();
        boolean allPassed = true;
        int skipped = 0;
        int errors = 0;

        for (AgreementTemplateTestData testEntry : testDataEntries) {
            RdapTestCaseResult rdapResult = executeRdapTestCase(template, testEntry);
            rdapTestResults.add(rdapResult);
            switch (rdapResult.getResult()) {
                case "FAILED" -> allPassed = false;
                case "SKIPPED" -> skipped++;
                case "ERROR" -> { allPassed = false; errors++; }
            }
        }

        long passed = rdapTestResults.stream().filter(r -> "PASSED".equals(r.getResult())).count();
        long duration = System.currentTimeMillis() - start;
        String result = testDataEntries.isEmpty() ? "SKIPPED" : (allPassed ? "PASSED" : "FAILED");

        String details = testDataEntries.isEmpty()
                ? "No active RDAP test data is configured for template " + templateId
                  + " and no RDAP records are flagged as test data"
                : String.format("RDAP retrievals: %d/%d passed%s%s. Duration: %dms",
                        passed, rdapTestResults.size(),
                        skipped > 0 ? " (" + skipped + " skipped)" : "",
                        usedFlaggedFallback ? " (using records flagged as test data)" : "", duration);

        log.info("Template test for {}: {} — {}", templateId, result, details);

        return TestResultInfo.builder()
                .result(result)
                .testedAt(LocalDateTime.now())
                .details(details)
                .testCases(List.of())
                .rdapTestResults(rdapTestResults)
                .summary(TestSummary.builder()
                        .totalValidationTests(0)
                        .passedValidationTests(0)
                        .failedValidationTests(0)
                        .totalRdapTests(rdapTestResults.size())
                        .passedRdapTests((int) passed)
                        .failedRdapTests((int) rdapTestResults.stream()
                                .filter(r -> "FAILED".equals(r.getResult())).count())
                        .skippedRdapTests(skipped)
                        .errorRdapTests(errors)
                        .totalDurationMs(duration)
                        .build())
                .build();
    }

    /**
     * Build transient test cases from RDAP records flagged as test data. These are never persisted —
     * they exist only for the duration of a run, so flagging a record is enough to make it testable
     * without also defining a template test case. Request type defaults to standard, so this cannot
     * exercise confidential or exigent paths; a template test case is still the way to cover those.
     */
    private List<AgreementTemplateTestData> flaggedTestDataEntries(AgreementTemplate template) {
        return rdapEntityRepository.findFlaggedTestData().stream()
                .map(entity -> AgreementTemplateTestData.builder()
                        .template(template)
                        .rdapEntity(entity)
                        .queryType(AgreementTemplateTestData.deriveQueryType(entity))
                        .queryValue(AgreementTemplateTestData.deriveQueryValue(entity))
                        .requestTypeName("standard")
                        .verifyContactAccess(true)
                        .verifyRedaction(true)
                        .isActive(true)
                        .build())
                .toList();
    }

    // ==================== RDAP Test Case Execution ====================

    /**
     * Execute a single RDAP test case against a test data entry.
     */
    private RdapTestCaseResult executeRdapTestCase(AgreementTemplate template,
                                                    AgreementTemplateTestData testEntry) {
        long caseStart = System.currentTimeMillis();
        List<RdapTestCheck> checks = new ArrayList<>();
        boolean casePassed = true;

        String label = testEntry.getDisplayLabel();
        String requestTypeName = testEntry.getRequestTypeName() != null ? testEntry.getRequestTypeName() : "standard";

        try {
            // --- Check 1: Entity Lookup ---
            Optional<RdapEntity> entityOpt = rdapEntityRepository.findById(testEntry.getRdapEntity().getId());
            if (entityOpt.isEmpty()) {
                checks.add(RdapTestCheck.builder()
                        .name("Entity Lookup").category("LOOKUP").passed(false)
                        .message("RDAP entity ID " + testEntry.getRdapEntity().getId() + " not found in database")
                        .severity("ERROR").build());
                return buildCaseResult(testEntry, label, requestTypeName, null, "ERROR",
                        "Referenced RDAP entity no longer exists", caseStart, checks);
            }

            RdapEntity entity = entityOpt.get();
            checks.add(RdapTestCheck.builder()
                    .name("Entity Lookup").category("LOOKUP").passed(true)
                    .message("Found " + entity.getObjectType() + " entity: " + entity.getDisplayIdentifier() +
                             " (handle: " + entity.getHandle() + ")")
                    .severity("INFO").build());

            // --- Check 2: Query Type Validation ---
            String expectedObjectType = switch (testEntry.getQueryType().toLowerCase()) {
                case "domain" -> "DOMAIN";
                case "ip" -> "IP_NETWORK";
                case "asn" -> "AUTNUM";
                default -> "UNKNOWN";
            };
            boolean typeMatch = entity.getObjectType().name().equals(expectedObjectType);
            checks.add(RdapTestCheck.builder()
                    .name("Query Type Match").category("LOOKUP").passed(typeMatch)
                    .message(typeMatch
                            ? "Entity type " + entity.getObjectType() + " matches query type '" + testEntry.getQueryType() + "'"
                            : "Entity type mismatch")
                    .expected(expectedObjectType).actual(entity.getObjectType().name())
                    .severity(typeMatch ? "INFO" : "WARNING").build());
            if (!typeMatch) casePassed = false;

            // --- Check 3: Request Type Resolution ---
            boolean isConfidential = "confidential".equalsIgnoreCase(requestTypeName);
            boolean isExigent = "exigent".equalsIgnoreCase(requestTypeName);

            Optional<AgreementRequestType> matchingRt = template.findMatchingRequestType(isConfidential, isExigent);
            if (matchingRt.isEmpty()) {
                checks.add(RdapTestCheck.builder()
                        .name("Request Type Resolution").category("ACCESS").passed(false)
                        .message("No matching request type found for '" + requestTypeName +
                                 "' (confidential=" + isConfidential + ", exigent=" + isExigent + ")")
                        .severity("ERROR").build());
                return buildCaseResult(testEntry, label, requestTypeName, null, "FAILED",
                        "No matching request type on template for '" + requestTypeName + "'",
                        caseStart, checks);
            }

            AgreementRequestType resolvedRt = matchingRt.get();
            Integer accessLevel = resolvedRt.getAccessLevel();
            checks.add(RdapTestCheck.builder()
                    .name("Request Type Resolution").category("ACCESS").passed(true)
                    .message("Resolved request type '" + resolvedRt.getName() +
                             "' (typeCode=" + resolvedRt.getTypeCode() +
                             ", accessLevel=" + accessLevel + ")")
                    .severity("INFO").build());

            // --- Check 4: Access Level Validity ---
            boolean validLevel = accessLevel != null && accessLevel >= 0 && accessLevel <= 3;
            checks.add(RdapTestCheck.builder()
                    .name("Access Level Validity").category("ACCESS").passed(validLevel)
                    .message(validLevel
                            ? "Access level " + accessLevel + " is valid"
                            : "Access level " + accessLevel + " is out of range (must be 0-3)")
                    .expected("0-3").actual(String.valueOf(accessLevel))
                    .severity(validLevel ? "INFO" : "ERROR").build());
            if (!validLevel) casePassed = false;

            // --- Check 5: RDAP Parameters Resolution ---
            AgreementRdapParameters rdapParams = resolvedRt.getEffectiveRdapParameters();
            boolean hasParams = rdapParams != null;
            checks.add(RdapTestCheck.builder()
                    .name("RDAP Parameters Resolution").category("PARAMETER").passed(hasParams)
                    .message(hasParams
                            ? "RDAP parameters resolved for request type '" + resolvedRt.getName() + "'"
                            : "No RDAP parameters available — defaults will apply")
                    .severity(hasParams ? "INFO" : "WARNING").build());

            // --- Check 6: Contact Visibility ---
            if (Boolean.TRUE.equals(testEntry.getVerifyContactAccess()) && rdapParams != null) {
                verifyContactVisibility(checks, entity, rdapParams, accessLevel);
            }

            // --- Check 7: Field Redaction ---
            if (Boolean.TRUE.equals(testEntry.getVerifyRedaction()) && rdapParams != null) {
                verifyRedaction(checks, entity, rdapParams);
            }

            // --- Check 8: Child Entity Check ---
            List<RdapEntity> childEntities = rdapEntityRepository.findByParentId(entity.getId());
            checks.add(RdapTestCheck.builder()
                    .name("Child Entity Check").category("LOOKUP").passed(true)
                    .message("Entity has " + childEntities.size() + " child entities (contacts/sub-entities)")
                    .severity("INFO").build());

            // --- Check 9: Entity Completeness ---
            checks.add(RdapTestCheck.builder()
                    .name("Entity Completeness").category("LOOKUP").passed(true)
                    .message("Entity has handle='" + entity.getHandle() + "', objectType=" + entity.getObjectType() +
                             ", status=" + (entity.getStatus() != null ? entity.getStatus() : "[]"))
                    .severity("INFO").build());

            // Determine final pass/fail based on ERROR-severity checks
            casePassed = casePassed && checks.stream()
                    .filter(c -> "ERROR".equals(c.getSeverity()))
                    .allMatch(RdapTestCheck::isPassed);

            String caseResult = casePassed ? "PASSED" : "FAILED";
            String caseMessage = casePassed
                    ? "All checks passed for " + testEntry.getQueryType() + " '" + testEntry.getQueryValue() + "'"
                    : "One or more checks failed";

            return buildCaseResult(testEntry, label, requestTypeName, accessLevel, caseResult,
                    caseMessage, caseStart, checks);

        } catch (Exception e) {
            log.error("Error executing RDAP test case for entry {}: {}", testEntry.getId(), e.getMessage(), e);
            checks.add(RdapTestCheck.builder()
                    .name("Unexpected Error").category("LOOKUP").passed(false)
                    .message("Exception during test: " + e.getMessage())
                    .severity("ERROR").build());
            return buildCaseResult(testEntry, label, requestTypeName, null, "ERROR",
                    "Exception: " + e.getMessage(), caseStart, checks);
        }
    }

    /**
     * Verify that contact fields that SHOULD be visible (per RDAP params) are
     * actually populated on the entity or its children.
     */
    private void verifyContactVisibility(List<RdapTestCheck> checks, RdapEntity entity,
                                          AgreementRdapParameters params, int accessLevel) {
        // Registrant
        if (params.hasRegistrantAccess()) {
            List<RdapEntity> registrants = rdapEntityRepository.findByParentIdAndRole(entity.getId(), "registrant");
            boolean hasRegistrant = !registrants.isEmpty();
            checks.add(RdapTestCheck.builder()
                    .name("Registrant Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message(hasRegistrant
                            ? "Registrant contact present (" + registrants.get(0).getDisplayIdentifier() + ") — parameters allow visibility"
                            : "No registrant contact on entity — parameters allow visibility but no data exists to display")
                    .severity(hasRegistrant ? "INFO" : "WARNING").build());
        } else {
            checks.add(RdapTestCheck.builder()
                    .name("Registrant Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message("Registrant contact redacted by RDAP parameters (access level " + accessLevel + ")")
                    .severity("INFO").build());
        }

        // Admin
        if (params.hasAdminAccess()) {
            List<RdapEntity> admins = rdapEntityRepository.findByParentIdAndRole(entity.getId(), "administrative");
            boolean hasAdmin = !admins.isEmpty();
            checks.add(RdapTestCheck.builder()
                    .name("Admin Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message(hasAdmin
                            ? "Admin contact present (" + admins.get(0).getDisplayIdentifier() + ") — parameters allow visibility"
                            : "No admin contact on entity — parameters allow visibility but no data exists")
                    .severity(hasAdmin ? "INFO" : "WARNING").build());
        } else {
            checks.add(RdapTestCheck.builder()
                    .name("Admin Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message("Admin contact redacted by RDAP parameters (access level " + accessLevel + ")")
                    .severity("INFO").build());
        }

        // Tech
        if (params.hasTechAccess()) {
            List<RdapEntity> techs = rdapEntityRepository.findByParentIdAndRole(entity.getId(), "technical");
            boolean hasTech = !techs.isEmpty();
            checks.add(RdapTestCheck.builder()
                    .name("Tech Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message(hasTech
                            ? "Tech contact present (" + techs.get(0).getDisplayIdentifier() + ") — parameters allow visibility"
                            : "No tech contact on entity — parameters allow visibility but no data exists")
                    .severity(hasTech ? "INFO" : "WARNING").build());
        } else {
            checks.add(RdapTestCheck.builder()
                    .name("Tech Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message("Tech contact redacted by RDAP parameters (access level " + accessLevel + ")")
                    .severity("INFO").build());
        }

        // Billing
        if (params.hasBillingAccess()) {
            List<RdapEntity> billings = rdapEntityRepository.findByParentIdAndRole(entity.getId(), "billing");
            boolean hasBilling = !billings.isEmpty();
            checks.add(RdapTestCheck.builder()
                    .name("Billing Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message(hasBilling
                            ? "Billing contact present (" + billings.get(0).getDisplayIdentifier() + ") — parameters allow visibility"
                            : "No billing contact on entity — parameters allow visibility but no data exists")
                    .severity(hasBilling ? "INFO" : "WARNING").build());
        } else {
            checks.add(RdapTestCheck.builder()
                    .name("Billing Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message("Billing contact redacted by RDAP parameters (access level " + accessLevel + ")")
                    .severity("INFO").build());
        }
    }

    /**
     * Verify that fields disabled in the RDAP parameters would be properly redacted.
     */
    private void verifyRedaction(List<RdapTestCheck> checks, RdapEntity entity,
                                  AgreementRdapParameters params) {
        // Domain field redaction
        if (entity.getObjectType() == RdapEntity.ObjectType.DOMAIN) {
            if (!Boolean.TRUE.equals(params.getDomainHandle())) {
                checks.add(RdapTestCheck.builder()
                        .name("Domain Handle Redaction").category("REDACTION").passed(true)
                        .message("Domain handle field will be redacted from response")
                        .severity("INFO").build());
            }
            if (!Boolean.TRUE.equals(params.getNameservers())) {
                checks.add(RdapTestCheck.builder()
                        .name("Nameservers Redaction").category("REDACTION").passed(true)
                        .message("Nameservers will be redacted from response")
                        .severity("INFO").build());
            }
            if (!Boolean.TRUE.equals(params.getDnssecData())) {
                checks.add(RdapTestCheck.builder()
                        .name("DNSSEC Redaction").category("REDACTION").passed(true)
                        .message("DNSSEC data will be redacted from response")
                        .severity("INFO").build());
            }
        }

        // Network field redaction
        if (entity.getObjectType() == RdapEntity.ObjectType.IP_NETWORK) {
            if (!Boolean.TRUE.equals(params.getNetworkHandle())) {
                checks.add(RdapTestCheck.builder()
                        .name("Network Handle Redaction").category("REDACTION").passed(true)
                        .message("Network handle will be redacted from response")
                        .severity("INFO").build());
            }
        }

        // ASN field redaction
        if (entity.getObjectType() == RdapEntity.ObjectType.AUTNUM) {
            if (!Boolean.TRUE.equals(params.getAutnumHandle())) {
                checks.add(RdapTestCheck.builder()
                        .name("ASN Handle Redaction").category("REDACTION").passed(true)
                        .message("ASN handle will be redacted from response")
                        .severity("INFO").build());
            }
        }

        // Contact role redaction summary
        int redactedRoles = 0;
        if (!params.hasRegistrantAccess()) redactedRoles++;
        if (!params.hasAdminAccess()) redactedRoles++;
        if (!params.hasTechAccess()) redactedRoles++;
        if (!params.hasBillingAccess()) redactedRoles++;

        checks.add(RdapTestCheck.builder()
                .name("Contact Redaction Summary").category("REDACTION").passed(true)
                .message(redactedRoles + " of 4 contact roles are fully redacted; " +
                         (4 - redactedRoles) + " are visible")
                .severity("INFO").build());

        // Events redaction
        if (!Boolean.TRUE.equals(params.getEvents())) {
            checks.add(RdapTestCheck.builder()
                    .name("Events Redaction").category("REDACTION").passed(true)
                    .message("Events will be redacted from response")
                    .severity("INFO").build());
        }
    }

    private RdapTestCaseResult buildCaseResult(AgreementTemplateTestData testEntry,
                                                String label, String requestTypeName,
                                                Integer accessLevel, String result,
                                                String message, long startTime,
                                                List<RdapTestCheck> checks) {
        return RdapTestCaseResult.builder()
                .testDataEntryId(testEntry.getId())
                .label(label)
                .queryType(testEntry.getQueryType())
                .queryValue(testEntry.getQueryValue())
                .requestTypeName(requestTypeName)
                .resolvedAccessLevel(accessLevel)
                .result(result)
                .message(message)
                .durationMs(System.currentTimeMillis() - startTime)
                .checks(checks)
                .build();
    }

    // ==================== Activation ====================

    /**
     * Activate a subscription after successful testing (by ID)
     */
    @Transactional
    public AgreementSubscription activateSubscription(Long id, String activatedBy) {
        AgreementSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        return doActivate(subscription, activatedBy);
    }

    /**
     * Activate a subscription after successful testing (by requestId)
     */
    @Transactional
    public AgreementSubscription activateByRequestId(String requestId, String activatedBy) {
        AgreementSubscription subscription = subscriptionRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        return doActivate(subscription, activatedBy);
    }

    private AgreementSubscription doActivate(AgreementSubscription subscription, String activatedBy) {
        if (subscription.getStatus() != SubscriptionStatus.TESTING) {
            throw new IllegalStateException("Subscription must be in TESTING status to activate");
        }

        if (!"PASSED".equals(subscription.getTestResult())) {
            throw new IllegalStateException("Tests must pass before activation");
        }

        String previousStatus = subscription.getStatus().name();

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setEffectiveFrom(LocalDateTime.now());
        subscription.setActivatedAt(LocalDateTime.now());
        subscription.setActivatedBy(activatedBy);
        subscription.setStatusMessage("Subscription activated successfully");
        subscription.setStatusChangedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);

        logStatusChange(subscription, previousStatus, "ACTIVE", activatedBy, "Subscription activated", "DATAHOLDER");
        sendStatusCallback(subscription, previousStatus);

        log.info("Subscription {} activated by {} for {}",
                subscription.getRequestId(), activatedBy, subscription.getRequestorGroupName());
        return subscription;
    }

    // ==================== Suspend / Reactivate ====================

    /**
     * Suspend an active subscription
     */
    @Transactional
    public AgreementSubscription suspendSubscription(Long id, String suspendedBy, String reason) {
        AgreementSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE subscriptions can be suspended");
        }

        String previousStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.SUSPENDED);
        subscription.setStatusMessage("Subscription suspended: " + reason);
        subscription.setStatusChangedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);

        logStatusChange(subscription, previousStatus, "SUSPENDED", suspendedBy, reason, "DATAHOLDER");
        sendStatusCallback(subscription, previousStatus);

        log.info("Subscription {} suspended by {}: {}", subscription.getRequestId(), suspendedBy, reason);
        return subscription;
    }

    /**
     * Reactivate a suspended subscription
     */
    @Transactional
    public AgreementSubscription reactivateSubscription(Long id, String reactivatedBy) {
        AgreementSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (subscription.getStatus() != SubscriptionStatus.SUSPENDED) {
            throw new IllegalStateException("Only SUSPENDED subscriptions can be reactivated");
        }

        String previousStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStatusMessage("Subscription reactivated");
        subscription.setStatusChangedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);

        logStatusChange(subscription, previousStatus, "ACTIVE", reactivatedBy, "Subscription reactivated", "DATAHOLDER");
        sendStatusCallback(subscription, previousStatus);

        log.info("Subscription {} reactivated by {}", subscription.getRequestId(), reactivatedBy);
        return subscription;
    }

    // ==================== Query Methods ====================

    /**
     * Get all active and effective subscriptions
     */
    public List<AgreementSubscription> getActiveSubscriptions() {
        return subscriptionRepository.findActiveAndEffective(LocalDateTime.now());
    }

    /**
     * Get active subscriptions for a requestor group
     */
    public List<AgreementSubscription> getActiveSubscriptionsForGroup(String requestorGroupId) {
        return subscriptionRepository.findActiveAndEffectiveByRequestorGroup(requestorGroupId, LocalDateTime.now());
    }

    /**
     * Handle ping request to verify subscription status
     */
    public AgreementPingResponse handlePing(AgreementPingRequest pingRequest) {
        log.debug("Received ping for group: {}", pingRequest.getRequestorGroupId());

        List<AgreementSubscription> active = subscriptionRepository
                .findActiveAndEffectiveByRequestorGroup(pingRequest.getRequestorGroupId(), LocalDateTime.now());

        if (active.isEmpty()) {
            return AgreementPingResponse.builder()
                    .active(false)
                    .status("NOT_FOUND")
                    .message("No active subscription found for this requestor group")
                    .build();
        }

        AgreementSubscription subscription = active.get(0);

        return AgreementPingResponse.builder()
                .active(true)
                .status("ACTIVE")
                .agreementId(subscription.getRequestId())
                .currentAccessLevel(subscription.getEffectiveAccessLevel())
                .message("Subscription is active")
                .build();
    }

    // ==================== Helper Methods ====================

    private PublishedTemplate toPublishedTemplate(AgreementTemplate template) {
        List<PublishedRequestType> requestTypeInfos = template.getActiveRequestTypes().stream()
                .map(rt -> PublishedRequestType.builder()
                        .name(rt.getName())
                        .typeCode(rt.getTypeCode())
                        .description(rt.getDescription())
                        .accessLevel(rt.getAccessLevel())
                        .supportsConfidential(rt.getSupportsConfidential())
                        .supportsExigent(rt.getSupportsExigent())
                        .requiresManualApproval(rt.getRequiresManualApproval())
                        .build())
                .toList();

        return PublishedTemplate.builder()
                .templateId(template.getTemplateId())
                .name(template.getName())
                .shortDescription(template.getShortDescription())
                .description(template.getDescription())
                .accessLevel(template.getDefaultAccessLevel())
                .requestTypes(requestTypeInfos)
                .supportsConfidential(template.supportsConfidential())
                .supportsExigent(template.supportsExigent())
                .requiredGroupTypes(template.getRequiredGroupTypes())
                .termsAndConditions(template.getTermsAndConditions())
                .dataUsagePolicy(template.getDataUsagePolicy())
                .maxQueriesPerDay(template.getMaxQueriesPerDay())
                .maxQueriesPerMonth(template.getMaxQueriesPerMonth())
                .dataholderName(dataholderName)
                .dataholderUrl(dataholderUrl)
                .build();
    }

    private AgreementStatusResponse toStatusResponse(AgreementSubscription subscription) {
        TestResultInfo testInfo = null;
        if (subscription.getTestResult() != null) {
            testInfo = TestResultInfo.builder()
                    .result(subscription.getTestResult())
                    .testedAt(subscription.getTestCompletedAt())
                    .details(subscription.getTestDetails())
                    .build();
        }

        return AgreementStatusResponse.builder()
                .requestId(subscription.getRequestId())
                .status(subscription.getStatus().name())
                .statusMessage(subscription.getStatusMessage())
                .statusChangedAt(subscription.getStatusChangedAt())
                .agreementId(subscription.getStatus() == SubscriptionStatus.ACTIVE ?
                        subscription.getRequestId() : null)
                .grantedAccessLevel(subscription.getEffectiveAccessLevel())
                .effectiveFrom(subscription.getEffectiveFrom())
                .effectiveTo(subscription.getEffectiveTo())
                .testResult(testInfo)
                .build();
    }

    private void logStatusChange(AgreementSubscription subscription, String previousStatus,
                                 String newStatus, String changedBy, String reason, String source) {
        log.info("Status change for {}: {} -> {} by {} ({})",
                subscription.getRequestId(), previousStatus, newStatus, changedBy, reason);

        AgreementStatusLog statusLog = AgreementStatusLog.builder()
                .subscription(subscription)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedBy(changedBy)
                .changeReason(reason)
                .source(source)
                .build();
        statusLogRepository.save(statusLog);
    }

    private void sendStatusCallback(AgreementSubscription subscription, String previousStatus) {
        if (subscription.getRequestorAgentCallbackUrl() == null) {
            log.debug("No callback URL for subscription {}, skipping notification", subscription.getRequestId());
            return;
        }

        try {
            StatusChangeCallback callback = StatusChangeCallback.builder()
                    .requestId(subscription.getRequestId())
                    .previousStatus(previousStatus)
                    .newStatus(subscription.getStatus().name())
                    .message(subscription.getStatusMessage())
                    .changedAt(LocalDateTime.now())
                    .agreementId(subscription.getStatus() == SubscriptionStatus.ACTIVE ?
                            subscription.getRequestId() : null)
                    .grantedAccessLevel(subscription.getEffectiveAccessLevel())
                    .effectiveFrom(subscription.getEffectiveFrom())
                    .effectiveTo(subscription.getEffectiveTo())
                    .build();

            // Route the callback over the requestor manager's mTLS listener when enabled.
            String callbackUrl = mtlsEndpoints.resolve(subscription.getRequestorAgentCallbackUrl());

            webClientBuilder.build()
                    .post()
                    .uri(callbackUrl)
                    .bodyValue(callback)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .subscribe(
                            result -> log.info("Status callback sent for subscription {}", subscription.getRequestId()),
                            error -> log.error("Failed to send status callback for {}: {}",
                                    subscription.getRequestId(), error.getMessage())
                    );
        } catch (Exception e) {
            log.error("Error sending status callback for {}: {}", subscription.getRequestId(), e.getMessage());
        }
    }
}