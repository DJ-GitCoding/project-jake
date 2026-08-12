/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs for Requestor Manager API communication
 */
public class AgreementApiDto {

    /**
     * Published agreement template for external consumption
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishedTemplate {
        private String templateId;
        private String name;
        private String shortDescription;
        private String description;
        private Integer accessLevel; // Default/standard access level for backward compatibility
        private List<PublishedRequestType> requestTypes;
        private Boolean supportsConfidential;
        private Boolean supportsExigent;
        private String requiredGroupTypes;
        private String termsAndConditions;
        private String dataUsagePolicy;
        private Integer maxQueriesPerDay;
        private Integer maxQueriesPerMonth;
        private String dataholderName;
        private String dataholderUrl;
    }

    /**
     * Published request type information for external consumption.
     * Describes a type of RDAP request the template supports
     * (e.g., standard, confidential, exigent).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishedRequestType {
        private String name;
        private Integer typeCode; // 3-digit numeric identifier for RDAP query params
        private String description;
        private Integer accessLevel;
        private Boolean supportsConfidential;
        private Boolean supportsExigent;
        private Boolean requiresManualApproval;
    }

    /**
     * Request from Requestor Manager to initiate agreement process
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgreementInitiationRequest {
        // Template being requested
        private String templateId;
        
        // Requestor Group Details
        private String requestorGroupId;
        private String requestorGroupName;
        private String requestorGroupCode; // Short code (≤10 chars) for RDAP query identification
        private String requestorGroupType; // e.g., "law-enforcement", "registrar"
        private String requestorDescription;
        private List<String> requestorGroupMembers; // User IDs/emails in the group
        
        // Requestor Agent Details
        private String requestorAgentId;
        private String requestorAgentUrl;
        private String callbackUrl; // URL to notify status changes
        
        // ==================== Contact Information ====================
        
        private String requestorFirstName;
        private String requestorLastName;
        private String requestorOrganization;
        private String requestorContactEmail;
        private String requestorPhone;
        private String requestorAddress;
        private String requestorCity;
        private String requestorStateProvince;
        private String requestorPostalCode;
        private String requestorCountry;
        
        // ==================== Agreement Details ====================
        
        private Integer requestedAccessLevel;
        private String purpose;
        private String additionalTerms;
        private String introspectionUrl;
        private String introspectionClientId;
        private String introspectionClientSecret;
        private LocalDateTime requestedEffectiveFrom;
        private LocalDateTime requestedEffectiveTo;
    }

    /**
     * Response to agreement initiation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgreementInitiationResponse {
        private boolean success;
        private String requestId;
        private String status;
        private String message;
        private LocalDateTime expiresAt;
        private String dataholderAgentId;
        private String statusCheckUrl;
    }

    /**
     * Agreement status check response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgreementStatusResponse {
        private String requestId;
        private String status;
        private String statusMessage;
        private LocalDateTime statusChangedAt;
        private String agreementId; // Set when active
        private Integer grantedAccessLevel;
        private LocalDateTime effectiveFrom;
        private LocalDateTime effectiveTo;
        private TestResultInfo testResult;
    }

    /**
     * Test result information — includes both validation tests and RDAP data tests.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestResultInfo {
        private String result; // PASSED, FAILED, PENDING
        private LocalDateTime testedAt;
        private String details;

        /**
         * Validation test cases (group info, contact info, template, etc.)
         */
        private List<TestCase> testCases;

        /**
         * Detailed RDAP test case results.
         * One entry per test data item configured on the template.
         * Null/empty if no test data is configured.
         */
        private List<RdapTestCaseResult> rdapTestResults;

        /**
         * Summary counts for quick UI display
         */
        private TestSummary summary;
    }

    /**
     * Individual test case result (validation tests)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCase {
        private String name;
        private String description;
        private boolean passed;
        private String errorMessage;
    }

    // ==================== RDAP Test DTOs ====================

    /**
     * Detailed test result for RDAP data testing.
     * Each entry in the template's test data list produces one of these.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RdapTestCaseResult {
        private Long testDataEntryId;
        private String label;
        private String queryType;
        private String queryValue;
        private String requestTypeName;
        private Integer resolvedAccessLevel;
        private String result; // PASSED, FAILED, SKIPPED, ERROR
        private String message;
        private Long durationMs;
        private List<RdapTestCheck> checks;
    }

    /**
     * An individual check within an RDAP test case.
     * Provides granular detail for each validation step.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RdapTestCheck {
        private String name;
        private String category; // LOOKUP, ACCESS, CONTACT_VISIBILITY, REDACTION, PARAMETER
        private boolean passed;
        private String message;
        private String expected;
        private String actual;
        private String severity; // INFO, WARNING, ERROR
    }

    /**
     * Summary counts for the test execution
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestSummary {
        private int totalValidationTests;
        private int passedValidationTests;
        private int failedValidationTests;
        private int totalRdapTests;
        private int passedRdapTests;
        private int failedRdapTests;
        private int skippedRdapTests;
        private int errorRdapTests;
        private long totalDurationMs;
    }

    // ==================== Test Data Management DTOs ====================

    /**
     * Request to add/update a test data entry on a template
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestDataEntryRequest {
        private Long rdapEntityId;
        private String queryType;
        private String queryValue;
        private String requestTypeName;
        private String label;
        private String description;
        private Boolean verifyContactAccess;
        private Boolean verifyRedaction;
        private Integer sortOrder;
        private Boolean isActive;
    }

    /**
     * Response representing a test data entry on a template
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestDataEntryResponse {
        private Long id;
        private Long rdapEntityId;
        private String queryType;
        private String queryValue;
        private String requestTypeName;
        private String label;
        private String displayLabel;
        private String description;
        private Boolean verifyContactAccess;
        private Boolean verifyRedaction;
        private Integer sortOrder;
        private Boolean isActive;
        private String rdapEntityHandle;
        private String rdapEntityObjectType;
        private String rdapEntityDisplayIdentifier;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ==================== Ping DTOs ====================

    /**
     * Ping request from Requestor Manager to verify agreement status
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgreementPingRequest {
        private String agreementId;
        private String requestorGroupId;
        private String userId; // User logging in
        private LocalDateTime timestamp;
    }

    /**
     * Ping response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgreementPingResponse {
        private boolean active;
        private String status; // ACTIVE, SUSPENDED, EXPIRED, NOT_FOUND
        private String agreementId;
        private Integer currentAccessLevel;
        private String message;
    }

    /**
     * Callback payload sent to Requestor Manager on status changes
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusChangeCallback {
        private String requestId;
        private String previousStatus;
        private String newStatus;
        private String message;
        private LocalDateTime changedAt;
        private String changedBy;
        private String agreementId; // If agreement was created
        private Integer grantedAccessLevel;
        private LocalDateTime effectiveFrom;
        private LocalDateTime effectiveTo;
    }

    // ==================== Workflow Action DTOs ====================

    /**
     * Request to trigger a workflow action (start-testing, activate, etc.)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowActionRequest {
        private String initiatedBy;
        private String notes;
        private String source;
    }

    /**
     * Response from a workflow action
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowActionResponse {
        private boolean success;
        private String requestId;
        private String status;
        private String message;
        private LocalDateTime statusChangedAt;
        private String agreementId;
        private Integer grantedAccessLevel;
        private LocalDateTime effectiveFrom;
        private LocalDateTime effectiveTo;
    }

    /**
     * Response from test execution
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestExecutionResponse {
        private boolean success;
        private String requestId;
        private String message;
        private TestResultInfo testResult;
    }
}