/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs for external API endpoints.
 * These are specifically designed for service-to-service communication.
 * 
 * Agreements returned by this API are derived from DataHolderAgreements.
 * Request types are fetched live from the dataholder's published template
 * to ensure they always reflect the current configuration.
 */
public class ExternalApiDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ExternalGroupsRequest", description = "Request to get agreements by group names")
    public static class GroupsRequest {
        @Schema(description = "List of group names to query agreements for", 
                example = "[\"REG-001\", \"APNIC\"]")
        private List<String> groupNames;
    }

    /**
     * Summary of a custom parameter defined on a request type.
     * These define additional data fields requestors must provide when making requests.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ExternalCustomParameterSummary", description = "Custom parameter defined on a request type")
    public static class CustomParameterSummary {

        @Schema(description = "Parameter name (field key)", example = "case_number")
        private String name;

        @Schema(description = "Data type", example = "string")
        private String dataType;

        @Schema(description = "Whether this parameter is required", example = "true")
        private Boolean required;

        @Schema(description = "Human-readable description")
        private String description;

        @Schema(description = "Default value")
        private String defaultValue;

        @Schema(description = "Placeholder/hint text")
        private String placeholder;

        @Schema(description = "Comma-separated allowed values for enum type")
        private String enumValues;

        @Schema(description = "Regex validation pattern for string types")
        private String validationRegex;

        @Schema(description = "Minimum value for numeric types")
        private String minValue;

        @Schema(description = "Maximum value for numeric types")
        private String maxValue;

        @Schema(description = "Maximum character length for string/text types")
        private Integer maxLength;

        @Schema(description = "Comma-separated allowed file extensions", example = ".pdf,.jpg")
        private String allowedFileTypes;

        @Schema(description = "Maximum file size in MB")
        private Integer maxFileSizeMb;

        @Schema(description = "Display order")
        private Integer sortOrder;
    }

    /**
     * Summary of a request type available under an agreement.
     * Fetched from the dataholder's published template at query time.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ExternalRequestTypeSummary", description = "Request type available under an agreement")
    public static class RequestTypeSummary {

        @Schema(description = "Request type name", example = "standard")
        private String name;

        @Schema(description = "Numeric type code for RDAP query parameters (3 digits)", example = "1")
        private Integer typeCode;

        @Schema(description = "Description of this request type")
        private String description;

        @Schema(description = "Access level granted for this request type (0-3)", example = "2")
        private Integer accessLevel;

        @Schema(description = "Whether this supports confidential disclosure", example = "false")
        private Boolean supportsConfidential;

        @Schema(description = "Whether this supports exigent/emergency disclosure", example = "false")
        private Boolean supportsExigent;

        @Schema(description = "Custom parameters defined for this request type")
        @Builder.Default
        private List<CustomParameterSummary> customParameters = new java.util.ArrayList<>();
    }

    /**
     * Summary of an agreement for external API responses.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ExternalAgreementSummary", description = "Agreement summary for external API")
    public static class AgreementSummary {
        
        @Schema(description = "Agreement ID", example = "1")
        private Long id;
        
        @Schema(description = "Agreement name", example = "Example Domain Access Agreement")
        private String name;
        
        @Schema(description = "Agreement description")
        private String description;
        
        @Schema(description = "Requestor group ID", example = "1")
        private Long requestorGroupId;
        
        @Schema(description = "Requestor group name", example = "REG-001")
        private String requestorGroupName;

        @Schema(description = "Requestor group code for RDAP query parameters", example = "REG-001")
        private String requestorGroupCode;
        
        @Schema(description = "Data holder code", example = "ARIN")
        private String dataHolderGroupCode;
        
        @Schema(description = "Data holder name", example = "American Registry for Internet Numbers")
        private String dataHolderGroupName;
        
        @Schema(description = "Template ID from the data holder", example = "law-enforcement-basic")
        private String templateId;
        
        @Schema(description = "Access level (0-3)", example = "1")
        private Integer accessLevel;

        @Schema(description = "Request types available under this agreement")
        private List<RequestTypeSummary> requestTypes;
        
        @Schema(description = "Current status", example = "ACTIVE")
        private String status;
        
        @Schema(description = "Source type", example = "DATA_HOLDER_AGREEMENT")
        private String sourceType;
        
        @Schema(description = "When the agreement becomes effective")
        private LocalDateTime effectiveFrom;
        
        @Schema(description = "When the agreement expires (null = no expiration)")
        private LocalDateTime effectiveTo;
        
        @Schema(description = "Creation timestamp")
        private LocalDateTime createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ExternalGroupAgreements", description = "Agreements grouped by RequestorGroup")
    public static class GroupAgreements {
        
        @Schema(description = "Group ID", example = "1")
        private Long groupId;
        
        @Schema(description = "Group name", example = "REG-001")
        private String groupName;
        
        @Schema(description = "List of agreements in this group")
        private List<AgreementSummary> agreements;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "ExternalAgreementsResponse", description = "Agreements response for external API")
    public static class AgreementsResponse {
        
        @Schema(description = "User subject (from token)")
        private String userSubject;
        
        @Schema(description = "User email (from token)")
        private String userEmail;
        
        @Schema(description = "Agreements grouped by RequestorGroup")
        private List<GroupAgreements> groups;
        
        @Schema(description = "All agreements (flattened, unique)")
        private List<AgreementSummary> agreements;

        @Schema(description = "Warnings about incomplete results (e.g. unreachable data holders)")
        private List<String> warnings;
    }
}