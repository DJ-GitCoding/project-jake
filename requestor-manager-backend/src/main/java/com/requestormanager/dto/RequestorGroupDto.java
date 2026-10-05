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

        @NotBlank(message = "Code is required")
        @Size(max = 10, message = "Code must not exceed 10 characters")
        @Pattern(regexp = "^[A-Za-z0-9]([A-Za-z0-9\\-]{0,8}[A-Za-z0-9])?$",
                message = "Code must be alphanumeric with hyphens, cannot start/end with hyphen")
        @Schema(description = "Short code identifying this group in RDAP queries (≤10 chars, letters/digits/hyphens)",
                example = "ICANN", required = true)
        private String code;
        
        @Schema(description = "Requestor group description", example = "Internet Corporation for Assigned Names and Numbers")
        private String description;

        @NotBlank(message = "A default introspection URL is required")
        @Size(max = 500)
        @Schema(description = "Token introspection endpoint data holders use to validate this group's tokens",
                required = true)
        private String defaultIntrospectionUrl;

        @NotBlank(message = "Street address is required")
        @Size(max = 255)
        @Schema(description = "Default street address, used to prefill subscription requests", required = true)
        private String defaultAddress;

        @NotBlank(message = "City is required")
        @Size(max = 100)
        @Schema(description = "Default city", required = true)
        private String defaultCity;

        @NotBlank(message = "State or province is required")
        @Size(max = 100)
        @Schema(description = "Default state or province", required = true)
        private String defaultStateProvince;

        @NotBlank(message = "Postal code is required")
        @Size(max = 20)
        @Schema(description = "Default postal code", required = true)
        private String defaultPostalCode;

        @NotBlank(message = "Country is required")
        @Size(max = 100)
        @Schema(description = "Default country", required = true)
        private String defaultCountry;

        @Size(max = 500)
        @Schema(description = "Public link where someone can apply to join this group. Optional; data holders "
                + "that offer requestor group links list it with their public RDAP answers.")
        private String joiningUrl;
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
        @Pattern(regexp = "^[A-Za-z0-9]([A-Za-z0-9\\-]{0,8}[A-Za-z0-9])?$",
                message = "Code must be alphanumeric with hyphens, cannot start/end with hyphen")
        @Schema(description = "Short code identifying this group in RDAP queries (≤10 chars, letters/digits/hyphens)",
                example = "ICANN")
        private String code;
        
        @Schema(description = "Requestor group description")
        private String description;

        @Size(max = 500)
        @Schema(description = "Default token introspection URL for this group")
        private String defaultIntrospectionUrl;

        @Size(max = 255)
        @Schema(description = "Default street address, used to prefill subscription requests")
        private String defaultAddress;

        @Size(max = 100)
        @Schema(description = "Default city")
        private String defaultCity;

        @Size(max = 100)
        @Schema(description = "Default state or province")
        private String defaultStateProvince;

        @Size(max = 20)
        @Schema(description = "Default postal code")
        private String defaultPostalCode;

        @Size(max = 100)
        @Schema(description = "Default country")
        private String defaultCountry;

        @Size(max = 500)
        @Schema(description = "Public joining link; send an empty string to remove it")
        private String joiningUrl;
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

        @Schema(description = "Default street address, used to prefill subscription requests")
        private String defaultAddress;

        @Schema(description = "Default city")
        private String defaultCity;

        @Schema(description = "Default state or province")
        private String defaultStateProvince;

        @Schema(description = "Default postal code")
        private String defaultPostalCode;

        @Schema(description = "Default country")
        private String defaultCountry;

        @Schema(description = "Public link where someone can apply to join this group")
        private String joiningUrl;

        @Schema(description = "The public requestor manager endpoint data holders hand out for joining this group; "
                + "null while no joining link is set")
        private String publicJoinUrl;

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

        @Schema(description = "Pending template change state, PROPOSED when the data holder group "
                            + "has proposed terms this subscription has not yet responded to")
        private String pendingChangeStatus;
        
        @Schema(description = "Created timestamp")
        private LocalDateTime createdAt;
        
        @Schema(description = "Last status change")
        private LocalDateTime statusChangedAt;
    }
}