/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.dto;

import com.requestormanager.entity.DataHolderGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class DataHolderGroupDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "DataHolderGroupCreateRequest", description = "Request to create a new data holder group")
    public static class CreateRequest {

        @NotBlank(message = "Code is required")
        @Size(min = 2, max = 50, message = "Code must be between 2 and 50 characters")
        @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Code must contain only uppercase letters, numbers, underscores, and hyphens")
        @Schema(description = "Unique identifier code", example = "ARIN")
        private String code;

        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must not exceed 200 characters")
        @Schema(description = "Display name", example = "American Registry for Internet Numbers")
        private String name;

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        @Schema(description = "Description of the data holder group")
        private String description;

        @NotBlank(message = "Base URL is required")
        @Pattern(regexp = "^https?://.*", message = "Base URL must be a valid HTTP(S) URL")
        @Schema(description = "Base URL of the data holder group API", example = "https://dataholder.arin.net")
        private String baseUrl;


        @Schema(description = "Contact email")
        private String contactEmail;

        @Size(max = 2000, message = "Notes must not exceed 2000 characters")
        @Schema(description = "Additional notes")
        private String notes;

        @Schema(description = "Whether the data holder group is active", defaultValue = "true")
        private Boolean active = true;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "DataHolderGroupUpdateRequest", description = "Request to update a data holder group")
    public static class UpdateRequest {

        @Size(max = 200, message = "Name must not exceed 200 characters")
        @Schema(description = "Display name")
        private String name;

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        @Schema(description = "Description of the data holder group")
        private String description;

        @Pattern(regexp = "^https?://.*", message = "Base URL must be a valid HTTP(S) URL")
        @Schema(description = "Base URL of the data holder group API")
        private String baseUrl;


        @Schema(description = "Contact email")
        private String contactEmail;

        @Size(max = 2000, message = "Notes must not exceed 2000 characters")
        @Schema(description = "Additional notes")
        private String notes;

        @Schema(description = "Whether the data holder group is active")
        private Boolean active;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "DataHolderGroupResponse", description = "Data holder information")
    public static class Response {

        @Schema(description = "Unique ID")
        private Long id;

        @Schema(description = "Unique code")
        private String code;

        @Schema(description = "Display name")
        private String name;

        @Schema(description = "Description")
        private String description;

        @Schema(description = "Base URL")
        private String baseUrl;


        @Schema(description = "Contact email")
        private String contactEmail;

        @Schema(description = "Additional notes")
        private String notes;

        @Schema(description = "Whether active")
        private Boolean active;

        @Schema(description = "Health status")
        private DataHolderGroup.HealthStatus healthStatus;

        @Schema(description = "Last successful contact")
        private LocalDateTime lastContactAt;

        @Schema(description = "Created timestamp")
        private LocalDateTime createdAt;

        @Schema(description = "Updated timestamp")
        private LocalDateTime updatedAt;

        public static Response fromEntity(DataHolderGroup entity) {
            return Response.builder()
                    .id(entity.getId())
                    .code(entity.getCode())
                    .name(entity.getName())
                    .description(entity.getDescription())
                    .baseUrl(entity.getBaseUrl())
                    .contactEmail(entity.getContactEmail())
                    .notes(entity.getNotes())
                    .active(entity.getActive())
                    .healthStatus(entity.getHealthStatus())
                    .lastContactAt(entity.getLastContactAt())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .build();
        }
    }

    // ========== DTOs for external API responses ==========

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "DataHolderGroupTemplate", description = "Agreement template from a data holder group")
    public static class TemplateInfo {

        @Schema(description = "Template ID")
        private String templateId;

        @Schema(description = "Template name")
        private String name;

        @Schema(description = "Template description")
        private String description;

        @Schema(description = "Default access level (from standard request type, for backward compatibility)")
        private Integer accessLevel;

        @Schema(description = "Request types supported by this template")
        private List<RequestTypeInfo> requestTypes;

        @Schema(description = "Whether this template supports confidential disclosure requests")
        private Boolean supportsConfidential;

        @Schema(description = "Whether this template supports exigent disclosure requests")
        private Boolean supportsExigent;

        @Schema(description = "Template version")
        private String version;

        @Schema(description = "Required group types")
        private String requiredGroupTypes;

        @Schema(description = "Terms and conditions")
        private String termsAndConditions;

        @Schema(description = "Data usage policy")
        private String dataUsagePolicy;

        @Schema(description = "Max queries per day")
        private Integer maxQueriesPerDay;

        @Schema(description = "Max queries per month")
        private Integer maxQueriesPerMonth;

        @Schema(description = "Required fields")
        private List<String> requiredFields;

        @Schema(description = "Data holder code this template belongs to")
        private String dataHolderGroupCode;

        @Schema(description = "Data holder group name")
        private String dataHolderGroupName;

        @Schema(description = "Data holder group URL")
        private String dataHolderGroupUrl;
    }

    /**
     * Describes a type of RDAP request a template supports
     * (e.g., standard, confidential, exigent).
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "RequestTypeInfo", description = "Request type supported by an agreement template")
    public static class RequestTypeInfo {

        @Schema(description = "Request type name (e.g., standard, confidential, exigent)")
        private String name;

        @Schema(description = "Description of this request type")
        private String description;

        @Schema(description = "Access level granted for this request type (0-3)")
        private Integer accessLevel;

        @Schema(description = "Whether this request type supports confidential disclosure")
        private Boolean supportsConfidential;

        @Schema(description = "Whether this request type supports exigent disclosure")
        private Boolean supportsExigent;

        @Schema(description = "Whether this request type requires manual approval")
        private Boolean requiresManualApproval;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "DataHolderGroupHealthResponse", description = "Health check response from data holder group")
    public static class HealthResponse {

        @Schema(description = "Data holder group code")
        private String dataHolderGroupCode;

        @Schema(description = "Data holder group name")
        private String dataHolderGroupName;

        @Schema(description = "Health status")
        private String status;

        @Schema(description = "Service name")
        private String service;

        @Schema(description = "Timestamp")
        private Long timestamp;

        @Schema(description = "Is healthy")
        private Boolean healthy;

        @Schema(description = "Error message if unhealthy")
        private String errorMessage;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "AgreementInitiationRequest", description = "Request to initiate agreement with data holder group")
    public static class InitiationRequest {

        @NotBlank(message = "Template ID is required")
        @Schema(description = "Template ID to subscribe to")
        private String templateId;

        // ==================== Requestor Group Info ====================

        @Schema(description = "Requestor group ID")
        private String requestorGroupId;

        @NotBlank(message = "Requestor group name is required")
        @Schema(description = "Name of the requesting group")
        private String requestorGroupName;

        @Size(max = 10, message = "Requestor group code must not exceed 10 characters")
        @Schema(description = "Short code identifying the requestor group for RDAP query identification (≤10 chars)")
        private String requestorGroupCode;

        @Schema(description = "Requestor group type (e.g., law-enforcement, registrar)")
        private String requestorGroupType;

        @Schema(description = "Description of the requestor group")
        private String requestorDescription;

        @Schema(description = "Requestor agent ID")
        private String requestorAgentId;

        // ==================== Contact Information ====================

        @Schema(description = "First name of the person making the request")
        private String requestorFirstName;

        @Schema(description = "Last name of the person making the request")
        private String requestorLastName;

        @Schema(description = "Organization name")
        private String requestorOrganization;

        @Schema(description = "Contact email for the requestor")
        private String contactEmail;

        @Schema(description = "Contact phone number")
        private String requestorPhone;

        @Schema(description = "Street address")
        private String requestorAddress;

        @Schema(description = "City")
        private String requestorCity;

        @Schema(description = "State or Province")
        private String requestorStateProvince;

        @Schema(description = "Postal/ZIP code")
        private String requestorPostalCode;

        @Schema(description = "Country")
        private String requestorCountry;

        // ==================== Request Details ====================

        @Schema(description = "Reason for requesting this access")
        private String reasonForUse;

        @Schema(description = "Requested access level (0-3)")
        private Integer requestedAccessLevel;

        @Schema(description = "Additional notes or terms")
        private String additionalNotes;

        @Schema(description = "Callback URL for status notifications")
        private String callbackUrl;

        @Schema(description = "Token introspection URL for validating bearer tokens from this requestor group")
        private String introspectionUrl;

        @Schema(description = "Additional metadata")
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "AgreementInitiationResponse", description = "Response from agreement initiation")
    public static class InitiationResponse {

        @Schema(description = "Whether initiation was successful")
        private Boolean success;

        @Schema(description = "Request ID for tracking")
        private String requestId;

        @Schema(description = "Status of the agreement")
        private String status;

        @Schema(description = "Message")
        private String message;

        @Schema(description = "Data holder group code")
        private String dataHolderGroupCode;

        @Schema(description = "Data holder group  agent ID")
        private String dataholderAgentId;

        @Schema(description = "URL to check status")
        private String statusCheckUrl;

        @Schema(description = "When the request expires")
        private LocalDateTime expiresAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "AgreementStatusResponse", description = "Status of an agreement request")
    public static class StatusResponse {

        @Schema(description = "Request ID")
        private String requestId;

        @Schema(description = "Current status")
        private String status;

        @Schema(description = "Status message")
        private String statusMessage;

        @Schema(description = "Agreement ID if approved")
        private String agreementId;

        @Schema(description = "Granted access level")
        private Integer grantedAccessLevel;

        @Schema(description = "Effective from")
        private LocalDateTime effectiveFrom;

        @Schema(description = "Effective to")
        private LocalDateTime effectiveTo;

        @Schema(description = "Message")
        private String message;

        @Schema(description = "Data holder group code")
        private String dataHolderGroupCode;

        @Schema(description = "Last updated")
        private LocalDateTime updatedAt;

        @Schema(description = "Test result: PASSED, FAILED, or empty if not run")
        private String testResult;

        @Schema(description = "When tests were completed")
        private String testCompletedAt;
    }

    // ==================== Testing Workflow DTOs ====================

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "WorkflowActionResponse", description = "Response from a workflow action (start-testing, activate, etc.)")
    public static class WorkflowActionResponse {

        @Schema(description = "Whether the action was successful")
        private Boolean success;

        @Schema(description = "Request ID")
        private String requestId;

        @Schema(description = "New status after the action")
        private String status;

        @Schema(description = "Message")
        private String message;

        @Schema(description = "Data holder group code")
        private String dataHolderGroupCode;

        @Schema(description = "When status changed")
        private LocalDateTime statusChangedAt;

        // Additional fields for activation response
        @Schema(description = "Agreement ID (set on activation)")
        private String agreementId;

        @Schema(description = "Granted access level")
        private Integer grantedAccessLevel;

        @Schema(description = "When agreement becomes effective")
        private LocalDateTime effectiveFrom;

        @Schema(description = "When agreement expires")
        private LocalDateTime effectiveTo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "TestExecutionResponse", description = "Response from test execution")
    public static class TestExecutionResponse {

        @Schema(description = "Whether test execution was successful")
        private Boolean success;

        @Schema(description = "Request ID")
        private String requestId;

        @Schema(description = "Message")
        private String message;

        @Schema(description = "Test result information")
        private TestResultInfo testResult;

        @Schema(description = "Data holder group code")
        private String dataHolderGroupCode;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "TestResultInfo", description = "Test result details — includes both validation tests and RDAP data tests")
    public static class TestResultInfo {

        @Schema(description = "Overall result: PASSED or FAILED")
        private String result;

        @Schema(description = "When the test was executed")
        private LocalDateTime testedAt;

        @Schema(description = "Summary of test results")
        private String details;

        @Schema(description = "Validation test cases (group info, contact, template, etc.)")
        private List<TestCaseInfo> testCases;

        @Schema(description = "Detailed RDAP test case results — one per test data entry on the template")
        private List<RdapTestCaseInfo> rdapTestResults;

        @Schema(description = "Summary counts for quick UI display")
        private TestSummaryInfo summary;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "TestCaseInfo", description = "Individual test case result")
    public static class TestCaseInfo {

        @Schema(description = "Test case name")
        private String name;

        @Schema(description = "Test case description")
        private String description;

        @Schema(description = "Whether the test passed")
        private Boolean passed;

        @Schema(description = "Error message if failed")
        private String errorMessage;
    }

    // ==================== RDAP Test Result DTOs ====================

    /**
     * Detailed test result for an individual RDAP test data entry.
     * Each entry in the template's test data list produces one of these.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "RdapTestCaseInfo", description = "Detailed RDAP test case result for a single test data entry")
    public static class RdapTestCaseInfo {

        @Schema(description = "The test data entry ID this result corresponds to")
        private Long testDataEntryId;

        @Schema(description = "Display label (e.g., 'DOMAIN lookup: example.com')")
        private String label;

        @Schema(description = "Query type used: domain, ip, asn")
        private String queryType;

        @Schema(description = "Query value used: example.com, 192.0.2.0, AS64496")
        private String queryValue;

        @Schema(description = "Request type tested: standard, confidential, exigent")
        private String requestTypeName;

        @Schema(description = "Access level resolved for this request type")
        private Integer resolvedAccessLevel;

        @Schema(description = "Overall result: PASSED, FAILED, SKIPPED, ERROR")
        private String result;

        @Schema(description = "Human-readable summary message")
        private String message;

        @Schema(description = "Time taken for this individual test case in milliseconds")
        private Long durationMs;

        @Schema(description = "Individual checks performed within this test case")
        private List<RdapTestCheckInfo> checks;
    }

    /**
     * An individual check within an RDAP test case.
     * Provides granular detail for each validation step.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "RdapTestCheckInfo", description = "Individual check within an RDAP test case")
    public static class RdapTestCheckInfo {

        @Schema(description = "Name of the check (e.g., 'Entity Lookup', 'Access Level Resolution')")
        private String name;

        @Schema(description = "Category for UI grouping: LOOKUP, ACCESS, CONTACT_VISIBILITY, REDACTION, PARAMETER")
        private String category;

        @Schema(description = "Whether this check passed")
        private Boolean passed;

        @Schema(description = "Detail message (success or failure reason)")
        private String message;

        @Schema(description = "Expected value (for comparison checks)")
        private String expected;

        @Schema(description = "Actual value found (for comparison checks)")
        private String actual;

        @Schema(description = "Severity: INFO, WARNING, ERROR")
        private String severity;
    }

    /**
     * Summary counts for the test execution.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "TestSummaryInfo", description = "Summary counts for test execution")
    public static class TestSummaryInfo {

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