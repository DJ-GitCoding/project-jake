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

/**
 * DTOs for subscription credential provisioning and delivery.
 */
public class SubscriptionCredentialDto {

    /**
     * Payload sent to the data holder when delivering credentials.
     * The data holder stores this and uses it for token introspection.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "CredentialDeliveryPayload",
            description = "Introspection credentials delivered to a data holder for a subscription")
    public static class DeliveryPayload {

        @Schema(description = "The subscription's external request ID (data holder's reference)")
        private String requestId;

        @Schema(description = "The Keycloak client ID to use for introspection")
        private String clientId;

        @Schema(description = "The Keycloak client secret")
        private String clientSecret;

        @Schema(description = "The token introspection endpoint URL")
        private String introspectionUrl;

        @Schema(description = "The token endpoint URL (for client_credentials grants if needed)")
        private String tokenUrl;

        @Schema(description = "The requestor group name this credential is for")
        private String requestorGroupName;

        @Schema(description = "The requestor group code")
        private String requestorGroupCode;

        @Schema(description = "Purpose description")
        @Builder.Default
        private String purpose = "token-introspection";

        @Schema(description = "When the credential was provisioned")
        private LocalDateTime provisionedAt;
    }

    /**
     * Response from the data holder acknowledging credential receipt.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "CredentialDeliveryResponse",
            description = "Data holder's acknowledgement of received credentials")
    public static class DeliveryResponse {

        @Schema(description = "Whether the credentials were accepted")
        private Boolean success;

        @Schema(description = "Message from the data holder")
        private String message;

        @Schema(description = "Request ID")
        private String requestId;
    }

    /**
     * Internal view of a subscription credential for admin display.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "SubscriptionCredentialResponse",
            description = "Subscription credential details (admin view)")
    public static class CredentialResponse {

        @Schema(description = "Credential ID")
        private Long id;

        @Schema(description = "Subscription request ID")
        private Long subscriptionRequestId;

        @Schema(description = "Subscription internal request ID")
        private String subscriptionInternalRequestId;

        @Schema(description = "Keycloak client ID")
        private String keycloakClientId;

        @Schema(description = "Introspection URL")
        private String introspectionUrl;

        @Schema(description = "Whether delivered to data holder")
        private Boolean deliveredToDataholder;

        @Schema(description = "When delivered")
        private LocalDateTime deliveredAt;

        @Schema(description = "Delivery attempts")
        private Integer deliveryAttempts;

        @Schema(description = "Last delivery error")
        private String lastDeliveryError;

        @Schema(description = "Whether active")
        private Boolean isActive;

        @Schema(description = "Data holder code")
        private String dataHolderGroupCode;

        @Schema(description = "Data holder name")
        private String dataHolderGroupName;

        @Schema(description = "Requestor group name")
        private String requestorGroupName;

        @Schema(description = "Created at")
        private LocalDateTime createdAt;
    }
}