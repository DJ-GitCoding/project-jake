/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class RequestorGroupDto {
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "RequestorGroupCreateRequest", description = "Create requestor group request")
    public static class CreateRequest {
        
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        @Schema(description = "Requestor group name - should match Keycloak group name", example = "ICANN")
        private String name;

        @Size(max = 10, message = "Code must not exceed 10 characters")
        @Pattern(regexp = "^$|^[A-Za-z0-9]([A-Za-z0-9\\-]{0,8}[A-Za-z0-9])?$",
                message = "Code must be alphanumeric with hyphens, cannot start/end with hyphen")
        @Schema(description = "Short code for RDAP query parameters (≤10 chars, letters/digits/hyphens)",
                example = "ICANN")
        private String code;
        
        @Schema(description = "Requestor group description", example = "Internet Corporation for Assigned Names and Numbers")
        private String description;

        @Size(max = 500)
        @Schema(description = "Default token introspection URL for this group")
        private String defaultIntrospectionUrl;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "RequestorGroupUpdateRequest", description = "Update requestor group request")
    public static class UpdateRequest {
        
        @Size(max = 255, message = "Name must not exceed 255 characters")
        @Schema(description = "Requestor group name", example = "ICANN")
        private String name;

        @Size(max = 10, message = "Code must not exceed 10 characters")
        @Pattern(regexp = "^$|^[A-Za-z0-9]([A-Za-z0-9\\-]{0,8}[A-Za-z0-9])?$",
                message = "Code must be alphanumeric with hyphens, cannot start/end with hyphen")
        @Schema(description = "Short code for RDAP query parameters (≤10 chars, letters/digits/hyphens)",
                example = "ICANN")
        private String code;
        
        @Schema(description = "Requestor group description")
        private String description;

        @Size(max = 500)
        @Schema(description = "Default token introspection URL for this group")
        private String defaultIntrospectionUrl;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "RequestorGroupResponse", description = "Requestor group response")
    public static class RequestorGroupResponse {
        
        @Schema(description = "Requestor group ID")
        private Long id;
        
        @Schema(description = "Requestor group name")
        private String name;

        @Schema(description = "Short code for RDAP query parameters")
        private String code;
        
        @Schema(description = "Requestor group description")
        private String description;

        @Schema(description = "Default token introspection URL for this group")
        private String defaultIntrospectionUrl;

        @Schema(description = "Number of active agreements for this group")
        private Integer agreementCount;
        
        @Schema(description = "Number of subscription requests for this group")
        private Integer subscriptionRequestCount;
        
        @Schema(description = "Active agreements summary")
        private List<AgreementSummary> agreements;
        
        @Schema(description = "Recent subscription requests summary")
        private List<SubscriptionRequestSummary> subscriptionRequests;
        
        @Schema(description = "Keycloak ID of user who created this group")
        private String createdByKeycloakId;
        
        @Schema(description = "Name of user who created this group")
        private String createdByName;
        
        @Schema(description = "Email of user who created this group")
        private String createdByEmail;
        
        @Schema(description = "Creation timestamp")
        private LocalDateTime createdAt;
        
        @Schema(description = "Last update timestamp")
        private LocalDateTime updatedAt;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "AgreementSummary", description = "Summary of an active agreement")
    public static class AgreementSummary {
        
        @Schema(description = "Agreement ID")
        private Long id;
        
        @Schema(description = "External agreement ID from the data holder")
        private String externalAgreementId;
        
        @Schema(description = "Agreement name")
        private String name;
        
        @Schema(description = "Data holder code")
        private String dataHolderGroupCode;
        
        @Schema(description = "Data holder name")
        private String dataHolderGroupName;
        
        @Schema(description = "Granted access level")
        private Integer accessLevel;
        
        @Schema(description = "Agreement status")
        private String status;
        
        @Schema(description = "Whether the agreement is currently effective")
        private Boolean isCurrentlyEffective;
        
        @Schema(description = "Effective from date")
        private LocalDateTime effectiveFrom;
        
        @Schema(description = "Effective to date")
        private LocalDateTime effectiveTo;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "SubscriptionRequestSummary", description = "Summary of a subscription request")
    public static class SubscriptionRequestSummary {
        
        @Schema(description = "Subscription request ID")
        private Long id;
        
        @Schema(description = "Internal request ID")
        private String internalRequestId;
        
        @Schema(description = "External request ID from data holder")
        private String externalRequestId;
        
        @Schema(description = "Template name")
        private String templateName;
        
        @Schema(description = "Data holder code")
        private String dataHolderGroupCode;
        
        @Schema(description = "Data holder name")
        private String dataHolderGroupName;
        
        @Schema(description = "Request status")
        private String status;
        
        @Schema(description = "Requested access level")
        private Integer requestedAccessLevel;
        
        @Schema(description = "Granted access level (if approved)")
        private Integer grantedAccessLevel;
        
        @Schema(description = "Created timestamp")
        private LocalDateTime createdAt;
        
        @Schema(description = "Last status change")
        private LocalDateTime statusChangedAt;
    }
}