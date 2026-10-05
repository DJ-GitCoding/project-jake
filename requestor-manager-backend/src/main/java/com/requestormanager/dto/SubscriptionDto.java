/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.dto;

import com.requestormanager.entity.DataHolderAgreement;
import com.requestormanager.entity.SubscriptionRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class SubscriptionDto {

    // ==================== Request DTOs ====================

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "CreateSubscriptionRequest", description = "Request to create a new subscription to a dataholder agreement")
    public static class CreateRequest {

        @NotNull(message = "Requestor group ID is required")
        @Schema(description = "ID of the requestor group making the subscription", required = true)
        private Long requestorGroupId;

        @NotNull(message = "Data holder ID is required")
        @Schema(description = "ID of the data holder to subscribe to", required = true)
        private Long dataHolderGroupId;

        @NotBlank(message = "Template ID is required")
        @Schema(description = "Template ID from the data holder", required = true)
        private String templateId;

        @Schema(description = "Template name (for display)")
        private String templateName;

        // Requestor contact information
        @NotBlank(message = "Requestor first name is required")
        @Size(max = 100)
        @Schema(description = "First name of the person making the request", required = true)
        private String requestorFirstName;

        @NotBlank(message = "Requestor last name is required")
        @Size(max = 100)
        @Schema(description = "Last name of the person making the request", required = true)
        private String requestorLastName;

        @Size(max = 200)
        @Schema(description = "Organization name")
        private String requestorOrganization;

        @NotBlank(message = "Contact email is required")
        @Email(message = "Invalid email format")
        @Schema(description = "Contact email address", required = true)
        private String requestorEmail;

        @Size(max = 50)
        @Schema(description = "Contact phone number")
        private String requestorPhone;

        @Size(max = 500)
        @Schema(description = "Street address")
        private String requestorAddress;

        @Size(max = 100)
        @Schema(description = "City")
        private String requestorCity;

        @Size(max = 100)
        @Schema(description = "State or Province")
        private String requestorStateProvince;

        @Size(max = 20)
        @Schema(description = "Postal/ZIP code")
        private String requestorPostalCode;

        @Size(max = 100)
        @Schema(description = "Country")
        private String requestorCountry;

        // Request details
        @Size(max = 500)
        @Schema(description = "Token introspection URL for the requestor group's identity provider")
        private String introspectionUrl;

        @Schema(description = "Values entered for the template's subscription fields")
        private java.util.Map<String, Object> subscriptionFieldValues;

        @Schema(description = "Ids of the legal sections the requestor accepted")
        private java.util.List<Long> acceptedLegalSectionIds;

        @Schema(description = "Which group member field answers each member field the template requires: "
                + "template field key to std:<key> or custom:<field id>")
        private java.util.Map<String, String> userFieldMapping;

        @Schema(description = "Submit immediately after creation (default: false)")
        private Boolean submitImmediately = false;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "DhgCredentialsRequest", description = "Credentials issued by a data holder group for one subscription")
    public static class DhgCredentialsRequest {

        @Schema(description = "Client ID issued by the data holder group")
        private String clientId;

        @Schema(description = "Client secret; blank leaves the stored secret unchanged")
        private String clientSecret;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "UpdateSubscriptionRequest", description = "Request to update a draft subscription")
    public static class UpdateRequest {

        @Size(max = 100)
        @Schema(description = "First name of the person making the request")
        private String requestorFirstName;

        @Size(max = 100)
        @Schema(description = "Last name of the person making the request")
        private String requestorLastName;

        @Size(max = 200)
        @Schema(description = "Organization name")
        private String requestorOrganization;

        @Email(message = "Invalid email format")
        @Schema(description = "Contact email address")
        private String requestorEmail;

        @Size(max = 50)
        @Schema(description = "Contact phone number")
        private String requestorPhone;

        @Size(max = 500)
        @Schema(description = "Street address")
        private String requestorAddress;

        @Size(max = 100)
        @Schema(description = "City")
        private String requestorCity;

        @Size(max = 100)
        @Schema(description = "State or Province")
        private String requestorStateProvince;

        @Size(max = 20)
        @Schema(description = "Postal/ZIP code")
        private String requestorPostalCode;

        @Size(max = 100)
        @Schema(description = "Country")
        private String requestorCountry;

        @Size(max = 500)
        @Schema(description = "Token introspection URL for the requestor group's identity provider")
        private String introspectionUrl;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "SubscriptionRequestResponse", description = "Subscription request details")
    public static class SubscriptionRequestResponse {

        @Schema(description = "Internal ID")
        private Long id;

        @Schema(description = "Internal request ID")
        private String internalRequestId;

        @Schema(description = "External request ID (from dataholder)")
        private String externalRequestId;

        @Schema(description = "Client ID issued by the data holder group for this subscription")
        private String dhgClientId;

        @Schema(description = "Public key this subscription signs with; register it with the group admin")
        private String dhgPublicKey;

        @Schema(description = "Whether a client secret is stored for this subscription; the secret is never returned")
        private Boolean hasDhgClientSecret;

        @Schema(description = "Requestor group ID")
        private Long requestorGroupId;

        @Schema(description = "Requestor group name")
        private String requestorGroupName;

        @Schema(description = "Data holder ID")
        private Long dataHolderGroupId;

        @Schema(description = "Data holder code")
        private String dataHolderGroupCode;

        @Schema(description = "Data holder name")
        private String dataHolderGroupName;

        @Schema(description = "Template ID")
        private String templateId;

        @Schema(description = "Template name")
        private String templateName;

        // Contact info
        @Schema(description = "Requestor first name")
        private String requestorFirstName;

        @Schema(description = "Requestor last name")
        private String requestorLastName;

        @Schema(description = "Requestor organization")
        private String requestorOrganization;

        @Schema(description = "Requestor email")
        private String requestorEmail;

        @Schema(description = "Requestor phone")
        private String requestorPhone;

        @Schema(description = "Full address")
        private AddressResponse requestorAddress;

        // Request details
        @Schema(description = "PROPOSED when a template change awaits this requestor's answer")
        private String pendingChangeStatus;

        @Schema(description = "When the change was proposed")
        private java.time.LocalDateTime pendingProposedAt;

        @Schema(description = "FORCED or OPTIONAL")
        private String pendingChangeMode;

        @Schema(description = "When a forced change must be accepted by")
        private java.time.LocalDateTime pendingChangeDeadline;

        @Schema(description = "The template state being proposed")
        private java.util.Map<String, Object> pendingTemplateSnapshot;

        @Schema(description = "The terms this subscription runs under")
        private java.util.Map<String, Object> templateSnapshot;

        @Schema(description = "Which group member field answers each member field the template requires")
        private java.util.Map<String, String> userFieldMapping;

        @Schema(description = "Agreement id issued by the data holder, once active")
        private String agreementId;

        @Schema(description = "Values the requestor supplied when subscribing")
        private java.util.Map<String, Object> subscriptionFieldValues;

        @Schema(description = "Which legal sections were accepted, and when")
        private java.util.List<java.util.Map<String, Object>> acceptedTerms;

        @Schema(description = "Token introspection URL")
        private String introspectionUrl;

        // Status
        @Schema(description = "Current status")
        private SubscriptionRequest.SubscriptionStatus status;

        @Schema(description = "Status message")
        private String statusMessage;

        @Schema(description = "Status changed at")
        private LocalDateTime statusChangedAt;

        // Timestamps
        @Schema(description = "Created at")
        private LocalDateTime createdAt;

        @Schema(description = "Submitted at")
        private LocalDateTime submittedAt;

        @Schema(description = "Expires at")
        private LocalDateTime expiresAt;

        @Schema(description = "Created by")
        private String createdByName;

        // If approved
        @Schema(description = "Data holder agreement ID (if active)")
        private Long dataHolderAgreementId;

        @Schema(description = "Test result from last test execution: PASSED, FAILED, or null if not run")
        private String testResult;

        @Schema(description = "Actions available for this subscription in its current state. " +
                "UI clients should use this to determine which buttons to show.",
                example = "[\"start-testing\", \"cancel\"]")
        private List<String> availableActions;

        @Schema(description = "Human-readable hint about what to do next")
        private String nextStepHint;

        public static SubscriptionRequestResponse fromEntity(SubscriptionRequest entity) {
            // For TESTING status, try to extract test result from the statusMessage
            // (format: "Testing: PASSED — ..." or "Testing: FAILED — ...")
            String inferredTestResult = null;
            if (entity.getStatus() == SubscriptionRequest.SubscriptionStatus.TESTING
                    && entity.getStatusMessage() != null) {
                String msg = entity.getStatusMessage();
                if (msg.startsWith("Testing: PASSED")) {
                    inferredTestResult = "PASSED";
                } else if (msg.startsWith("Testing: FAILED")) {
                    inferredTestResult = "FAILED";
                } else if (msg.startsWith("Testing: PENDING")) {
                    inferredTestResult = "PENDING";
                }
            }
            return fromEntity(entity, inferredTestResult);
        }

        public static SubscriptionRequestResponse fromEntity(SubscriptionRequest entity, String testResultFromDh) {
            AddressResponse address = AddressResponse.builder()
                    .street(entity.getRequestorAddress())
                    .city(entity.getRequestorCity())
                    .stateProvince(entity.getRequestorStateProvince())
                    .postalCode(entity.getRequestorPostalCode())
                    .country(entity.getRequestorCountry())
                    .build();

            // Compute available actions based on status + test result
            List<String> actions = new java.util.ArrayList<>();
            String hint = null;
            SubscriptionRequest.SubscriptionStatus st = entity.getStatus();

            switch (st) {
                case DRAFT:
                    actions.add("submit");
                    actions.add("cancel");
                    hint = "Submit this request to the data holder for review.";
                    break;
                case SUBMITTED:
                case PENDING_REVIEW:
                    actions.add("refresh");
                    actions.add("cancel");
                    hint = "Waiting for data holder approval. Use refresh to check for updates.";
                    break;
                case APPROVED:
                    actions.add("start-testing");
                    hint = "Approved! Start the testing phase to validate the connection.";
                    break;
                case TESTING:
                    actions.add("run-test");
                    actions.add("refresh");
                    if ("PASSED".equalsIgnoreCase(testResultFromDh)) {
                        actions.add("activate");
                        hint = "Tests passed. Activate the subscription to make it live.";
                    } else if ("PENDING".equalsIgnoreCase(testResultFromDh)) {
                        hint = "Everything else passed. Member information is verified once this subscription's "
                                + "credentials are exchanged and its signing key is registered; the test then runs "
                                + "again automatically.";
                    } else {
                        hint = "Run tests to validate the agreement. Activate becomes available after tests pass.";
                    }
                    break;
                case ACTIVE:
                    hint = "Subscription is active and live.";
                    break;
                case DECLINED:
                    hint = "This request was declined by the data holder.";
                    break;
                case SUSPENDED:
                    hint = "This subscription has been suspended.";
                    break;
                case CANCELLED:
                    hint = "This request was cancelled.";
                    break;
                case EXPIRED:
                    hint = "This request has expired.";
                    break;
            }

            return SubscriptionRequestResponse.builder()
                    .id(entity.getId())
                    .internalRequestId(entity.getInternalRequestId())
                    .externalRequestId(entity.getExternalRequestId())
                    .dhgClientId(entity.getDhgClientId())
                    .dhgPublicKey(entity.getDhgPublicKey())
                    .hasDhgClientSecret(entity.getDhgClientSecret() != null && !entity.getDhgClientSecret().isBlank())
                    .requestorGroupId(entity.getRequestorGroup().getId())
                    .requestorGroupName(entity.getRequestorGroup().getName())
                    .dataHolderGroupId(entity.getDataHolderGroup().getId())
                    .dataHolderGroupCode(entity.getDataHolderGroup().getCode())
                    .dataHolderGroupName(entity.getDataHolderGroup().getName())
                    .templateId(entity.getTemplateId())
                    .templateName(entity.getTemplateName())
                    .requestorFirstName(entity.getRequestorFirstName())
                    .requestorLastName(entity.getRequestorLastName())
                    .requestorOrganization(entity.getRequestorOrganization())
                    .requestorEmail(entity.getRequestorEmail())
                    .requestorPhone(entity.getRequestorPhone())
                    .requestorAddress(address)
                    .introspectionUrl(entity.getIntrospectionUrl())
                    .pendingChangeStatus(entity.getPendingChangeStatus())
                    .pendingProposedAt(entity.getPendingProposedAt())
                    .pendingChangeMode(entity.getPendingChangeMode())
                    .pendingChangeDeadline(entity.getPendingChangeDeadline())
                    .pendingTemplateSnapshot(entity.getPendingTemplateSnapshot())
                    .templateSnapshot(entity.getTemplateSnapshot())
                    .userFieldMapping(entity.getUserFieldMapping())
                    .agreementId(entity.getDataHolderAgreement() != null
                            ? entity.getDataHolderAgreement().getExternalAgreementId() : null)
                    .acceptedTerms(entity.getAcceptedTerms())
                    .subscriptionFieldValues(entity.getSubscriptionFieldValues())
                    .status(entity.getStatus())
                    .statusMessage(entity.getStatusMessage())
                    .statusChangedAt(entity.getStatusChangedAt())
                    .createdAt(entity.getCreatedAt())
                    .submittedAt(entity.getSubmittedAt())
                    .expiresAt(entity.getExpiresAt())
                    .createdByName(entity.getCreatedByName())
                    .dataHolderAgreementId(entity.getDataHolderAgreement() != null ? 
                            entity.getDataHolderAgreement().getId() : null)
                    .testResult(testResultFromDh)
                    .availableActions(actions)
                    .nextStepHint(hint)
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "AddressResponse", description = "Address details")
    public static class AddressResponse {
        private String street;
        private String city;
        private String stateProvince;
        private String postalCode;
        private String country;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "DataHolderAgreementResponse", description = "Active agreement from a data holder")
    public static class DataHolderAgreementResponse {

        @Schema(description = "Internal ID")
        private Long id;

        @Schema(description = "External agreement ID (from dataholder)")
        private String externalAgreementId;

        @Schema(description = "Requestor group ID")
        private Long requestorGroupId;

        @Schema(description = "Requestor group name")
        private String requestorGroupName;

        @Schema(description = "Data holder ID")
        private Long dataHolderGroupId;

        @Schema(description = "Data holder code")
        private String dataHolderGroupCode;

        @Schema(description = "Data holder name")
        private String dataHolderGroupName;

        @Schema(description = "Agreement name")
        private String name;

        @Schema(description = "Agreement description")
        private String description;

        @Schema(description = "Template ID")
        private String templateId;

        @Schema(description = "Terms and conditions")
        private String termsAndConditions;

        @Schema(description = "Data usage policy")
        private String dataUsagePolicy;

        @Schema(description = "Max queries per day")
        private Integer maxQueriesPerDay;

        @Schema(description = "Max queries per month")
        private Integer maxQueriesPerMonth;

        @Schema(description = "Is active")
        private Boolean isActive;

        @Schema(description = "Status")
        private DataHolderAgreement.AgreementStatus status;

        @Schema(description = "Effective from")
        private LocalDateTime effectiveFrom;

        @Schema(description = "Effective to")
        private LocalDateTime effectiveTo;

        @Schema(description = "Last verified with dataholder")
        private LocalDateTime lastVerifiedAt;

        @Schema(description = "Created at")
        private LocalDateTime createdAt;

        @Schema(description = "Is currently effective")
        private Boolean isCurrentlyEffective;

        public static DataHolderAgreementResponse fromEntity(DataHolderAgreement entity) {
            return DataHolderAgreementResponse.builder()
                    .id(entity.getId())
                    .externalAgreementId(entity.getExternalAgreementId())
                    .requestorGroupId(entity.getRequestorGroup().getId())
                    .requestorGroupName(entity.getRequestorGroup().getName())
                    .dataHolderGroupId(entity.getDataHolderGroup().getId())
                    .dataHolderGroupCode(entity.getDataHolderGroup().getCode())
                    .dataHolderGroupName(entity.getDataHolderGroup().getName())
                    .name(entity.getName())
                    .description(entity.getDescription())
                    .templateId(entity.getTemplateId())
                    .termsAndConditions(entity.getTermsAndConditions())
                    .dataUsagePolicy(entity.getDataUsagePolicy())
                    .maxQueriesPerDay(entity.getMaxQueriesPerDay())
                    .maxQueriesPerMonth(entity.getMaxQueriesPerMonth())
                    .isActive(entity.getIsActive())
                    .status(entity.getStatus())
                    .effectiveFrom(entity.getEffectiveFrom())
                    .effectiveTo(entity.getEffectiveTo())
                    .lastVerifiedAt(entity.getLastVerifiedAt())
                    .createdAt(entity.getCreatedAt())
                    .isCurrentlyEffective(entity.isCurrentlyEffective())
                    .build();
        }
    }

    // ==================== Callback DTOs ====================

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "StatusChangeCallback", description = "Callback from dataholder on status change")
    public static class StatusChangeCallback {

        @Schema(description = "Request ID")
        private String requestId;

        @Schema(description = "Previous status")
        private String previousStatus;

        @Schema(description = "New status")
        private String newStatus;

        @Schema(description = "Message")
        private String message;

        @Schema(description = "When the change occurred")
        private LocalDateTime changedAt;

        @Schema(description = "Who made the change")
        private String changedBy;

        @Schema(description = "Agreement ID (if created)")
        private String agreementId;

        @Schema(description = "Effective from")
        private LocalDateTime effectiveFrom;

        @Schema(description = "Effective to")
        private LocalDateTime effectiveTo;
    }

    // ==================== List/Summary DTOs ====================

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "SubscriptionSummary", description = "Summary of subscription request")
    public static class SubscriptionSummary {

        private Long id;
        private String internalRequestId;
        private String requestorGroupName;
        private String dataHolderGroupName;
        private String templateName;
        private SubscriptionRequest.SubscriptionStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime statusChangedAt;

        public static SubscriptionSummary fromEntity(SubscriptionRequest entity) {
            return SubscriptionSummary.builder()
                    .id(entity.getId())
                    .internalRequestId(entity.getInternalRequestId())
                    .requestorGroupName(entity.getRequestorGroup().getName())
                    .dataHolderGroupName(entity.getDataHolderGroup().getName())
                    .templateName(entity.getTemplateName())
                    .status(entity.getStatus())
                    .createdAt(entity.getCreatedAt())
                    .statusChangedAt(entity.getStatusChangedAt())
                    .build();
        }
    }

    // ==================== Testing Workflow DTOs ====================

    /**
     * Result of test execution from data holder.
     * Includes both validation test cases and detailed RDAP test results.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "TestExecutionResult", description = "Result of test execution from data holder")
    public static class TestExecutionResult {

        @Schema(description = "Internal subscription request ID")
        private Long subscriptionRequestId;

        @Schema(description = "Internal request ID")
        private String internalRequestId;

        @Schema(description = "Overall result: PASSED or FAILED")
        private String result;

        @Schema(description = "When the test was executed")
        private LocalDateTime testedAt;

        @Schema(description = "Summary of test results")
        private String details;

        @Schema(description = "Validation test case results")
        private List<TestCaseResult> testCases;

        @Schema(description = "Detailed RDAP test case results — one per test data entry on the template")
        private List<RdapTestCaseResult> rdapTestResults;

        @Schema(description = "Summary counts for quick UI display")
        private TestSummaryResult summary;

        @Schema(description = "The member information the member check received: member, and each required field's value")
        private java.util.Map<String, Object> memberInformation;
    }

    /**
     * Individual validation test case result
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "TestCaseResult", description = "Individual test case result")
    public static class TestCaseResult {

        @Schema(description = "Test case name")
        private String name;

        @Schema(description = "Test case description")
        private String description;

        @Schema(description = "Whether the test passed")
        private Boolean passed;

        @Schema(description = "Not failed, but waiting on the requestor manager")
        private Boolean pending;

        @Schema(description = "Error message if failed")
        private String errorMessage;
    }

    // ==================== RDAP Test Result DTOs ====================

    /**
     * Detailed test result for an individual RDAP test data entry.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "RdapTestCaseResult", description = "Detailed RDAP test case result")
    public static class RdapTestCaseResult {

        @Schema(description = "The test data entry ID this result corresponds to")
        private Long testDataEntryId;

        @Schema(description = "Display label (e.g., 'DOMAIN lookup: example.com')")
        private String label;

        @Schema(description = "Query type used: domain, ip, asn")
        private String queryType;

        @Schema(description = "Query value used")
        private String queryValue;

        @Schema(description = "Request type tested: standard, confidential, exigent")
        private String requestTypeName;

        @Schema(description = "Access level resolved for this request type")
        private Integer resolvedAccessLevel;

        @Schema(description = "Overall result: PASSED, FAILED, SKIPPED, ERROR")
        private String result;

        @Schema(description = "Human-readable summary message")
        private String message;

        @Schema(description = "Time taken in milliseconds")
        private Long durationMs;

        @Schema(description = "Individual checks performed within this test case")
        private List<RdapTestCheckResult> checks;
    }

    /**
     * An individual check within an RDAP test case.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "RdapTestCheckResult", description = "Individual check within an RDAP test case")
    public static class RdapTestCheckResult {

        @Schema(description = "Name of the check")
        private String name;

        @Schema(description = "Category: LOOKUP, ACCESS, CONTACT_VISIBILITY, REDACTION, PARAMETER")
        private String category;

        @Schema(description = "Whether this check passed")
        private Boolean passed;

        @Schema(description = "Detail message")
        private String message;

        @Schema(description = "Expected value (for comparison checks)")
        private String expected;

        @Schema(description = "Actual value found")
        private String actual;

        @Schema(description = "Severity: INFO, WARNING, ERROR")
        private String severity;
    }

    /**
     * Summary counts for test execution.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "TestSummaryResult", description = "Summary counts for test execution")
    public static class TestSummaryResult {

        @Schema(description = "Total validation tests run")
        private Integer totalValidationTests;

        @Schema(description = "Passed validation tests")
        private Integer passedValidationTests;

        @Schema(description = "Failed validation tests")
        private Integer failedValidationTests;

        @Schema(description = "Total RDAP tests run")
        private Integer totalRdapTests;

        @Schema(description = "Passed RDAP tests")
        private Integer passedRdapTests;

        @Schema(description = "Failed RDAP tests")
        private Integer failedRdapTests;

        @Schema(description = "Skipped RDAP tests")
        private Integer skippedRdapTests;

        @Schema(description = "RDAP tests that errored")
        private Integer errorRdapTests;

        @Schema(description = "Total duration in milliseconds")
        private Long totalDurationMs;
    }
}