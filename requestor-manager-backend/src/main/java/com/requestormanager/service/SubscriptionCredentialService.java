/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.service;

import com.requestormanager.dto.SubscriptionCredentialDto;
import com.requestormanager.entity.SubscriptionCredential;
import com.requestormanager.entity.SubscriptionRequest;
import com.requestormanager.repository.SubscriptionCredentialRepository;
import com.requestormanager.service.KeycloakClientProvisioningService.ProvisionedClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Lifecycle of per-subscription introspection credentials: provision (Keycloak client + record),
 * deliver to the data holder, revoke on termination, and retry failed deliveries. Driven from
 * SubscriptionService.activateSubscription() once a subscription goes ACTIVE.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionCredentialService {

    private final KeycloakClientProvisioningService keycloakService;
    private final DataHolderGroupClientService dataHolderGroupClientService;
    private final SubscriptionCredentialRepository credentialRepository;

    /** Provisions a Keycloak client and delivers credentials to the data holder (main activation entry point). */
    @Transactional
    public SubscriptionCredential provisionAndDeliver(SubscriptionRequest subscriptionRequest) {
        // Guard: don't provision twice
        if (credentialRepository.existsBySubscriptionRequestId(subscriptionRequest.getId())) {
            log.warn("Credentials already exist for subscription {}, skipping provisioning",
                    subscriptionRequest.getInternalRequestId());
            return credentialRepository.findBySubscriptionRequestId(subscriptionRequest.getId()).orElse(null);
        }

        log.info("Provisioning introspection credentials for subscription {} (DH: {}, Group: {})",
                subscriptionRequest.getInternalRequestId(),
                subscriptionRequest.getDataHolderGroup().getCode(),
                subscriptionRequest.getRequestorGroup().getName());

        // Step 1: Create the Keycloak client
        ProvisionedClient provisioned = keycloakService.provisionClient(
                subscriptionRequest.getInternalRequestId(),
                subscriptionRequest.getDataHolderGroup().getCode(),
                subscriptionRequest.getRequestorGroup().getName()
        );

        // Step 2: Store the credential record
        SubscriptionCredential credential = SubscriptionCredential.builder()
                .subscriptionRequest(subscriptionRequest)
                .keycloakClientId(provisioned.clientId())
                .keycloakClientUuid(provisioned.clientUuid())
                .clientSecret(provisioned.clientSecret())
                .introspectionUrl(provisioned.introspectionUrl())
                .tokenUrl(provisioned.tokenUrl())
                .isActive(true)
                .deliveredToDataholder(false)
                .deliveryAttempts(0)
                .build();

        credential = credentialRepository.save(credential);
        log.info("Stored credential record (id: {}, clientId: {})", credential.getId(), provisioned.clientId());

        // Step 3: Deliver to the data holder
        deliverToDataHolderGroup(credential, subscriptionRequest);

        return credential;
    }

    /**
     * Deliver (or re-deliver) credentials to the data holder.
     */
    @Transactional
    public void deliverToDataHolderGroup(SubscriptionCredential credential, SubscriptionRequest subscriptionRequest) {
        credential.setDeliveryAttempts(credential.getDeliveryAttempts() + 1);

        try {
            // Build delivery payload
            SubscriptionCredentialDto.DeliveryPayload payload =
                    SubscriptionCredentialDto.DeliveryPayload.builder()
                            .requestId(subscriptionRequest.getExternalRequestId())
                            .clientId(credential.getKeycloakClientId())
                            .clientSecret(credential.getClientSecret())
                            .introspectionUrl(credential.getIntrospectionUrl())
                            .tokenUrl(credential.getTokenUrl())
                            .requestorGroupName(subscriptionRequest.getRequestorGroup().getName())
                            .requestorGroupCode(subscriptionRequest.getRequestorGroup().getCode())
                            .purpose("token-introspection")
                            .provisionedAt(credential.getCreatedAt())
                            .build();

            // POST to the data holder
            SubscriptionCredentialDto.DeliveryResponse response =
                    dataHolderGroupClientService.sendCredentials(
                            subscriptionRequest.getDataHolderGroup().getId(),
                            subscriptionRequest.getExternalRequestId(),
                            payload
                    );

            if (response != null && Boolean.TRUE.equals(response.getSuccess())) {
                credential.setDeliveredToDataholder(true);
                credential.setDeliveredAt(LocalDateTime.now());
                credential.setLastDeliveryError(null);
                log.info("Successfully delivered credentials for subscription {} to data holder {}",
                        subscriptionRequest.getInternalRequestId(),
                        subscriptionRequest.getDataHolderGroup().getCode());
            } else {
                String errorMsg = response != null ? response.getMessage() : "No response from data holder";
                credential.setLastDeliveryError(errorMsg);
                log.warn("Data holder did not accept credentials for subscription {}: {}",
                        subscriptionRequest.getInternalRequestId(), errorMsg);
            }

        } catch (Exception e) {
            credential.setLastDeliveryError(e.getMessage());
            log.error("Failed to deliver credentials for subscription {} (attempt {}): {}",
                    subscriptionRequest.getInternalRequestId(),
                    credential.getDeliveryAttempts(),
                    e.getMessage());
        }

        credentialRepository.save(credential);
    }

    /**
     * Retry delivery for all undelivered credentials.
     * Can be called by a scheduled job or admin action.
     */
    @Transactional
    public int retryUndelivered() {
        List<SubscriptionCredential> undelivered = credentialRepository.findUndelivered();
        int retried = 0;

        for (SubscriptionCredential credential : undelivered) {
            if (credential.getDeliveryAttempts() >= 5) {
                log.warn("Skipping credential {} — exceeded max delivery attempts ({})",
                        credential.getKeycloakClientId(), credential.getDeliveryAttempts());
                continue;
            }

            SubscriptionRequest sub = credential.getSubscriptionRequest();
            deliverToDataHolderGroup(credential, sub);
            retried++;
        }

        if (retried > 0) {
            log.info("Retried delivery for {} undelivered credential(s)", retried);
        }
        return retried;
    }

    /**
     * Revoke credentials for a subscription (on termination/suspension).
     * Disables the Keycloak client and marks the record as revoked.
     */
    @Transactional
    public void revokeCredential(Long subscriptionRequestId, String reason) {
        credentialRepository.findBySubscriptionRequestId(subscriptionRequestId)
                .ifPresent(credential -> {
                    // Disable the Keycloak client
                    keycloakService.disableClient(credential.getKeycloakClientUuid());

                    credential.setIsActive(false);
                    credential.setRevokedAt(LocalDateTime.now());
                    credential.setRevocationReason(reason);
                    credentialRepository.save(credential);

                    log.info("Revoked credential '{}' for subscription request {}: {}",
                            credential.getKeycloakClientId(), subscriptionRequestId, reason);
                });
    }

    /**
     * Permanently delete the Keycloak client and credential record.
     */
    @Transactional
    public void deleteCredential(Long subscriptionRequestId) {
        credentialRepository.findBySubscriptionRequestId(subscriptionRequestId)
                .ifPresent(credential -> {
                    keycloakService.deleteClient(credential.getKeycloakClientUuid());
                    credentialRepository.delete(credential);
                    log.info("Deleted credential '{}' for subscription request {}",
                            credential.getKeycloakClientId(), subscriptionRequestId);
                });
    }

    /**
     * Get credential status for a subscription (admin view).
     */
    @Transactional(readOnly = true)
    public SubscriptionCredentialDto.CredentialResponse getCredentialStatus(Long subscriptionRequestId) {
        return credentialRepository.findBySubscriptionRequestId(subscriptionRequestId)
                .map(this::toResponse)
                .orElse(null);
    }

    /**
     * Get all active credentials (admin view).
     */
    @Transactional(readOnly = true)
    public List<SubscriptionCredentialDto.CredentialResponse> getAllActiveCredentials() {
        return credentialRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private SubscriptionCredentialDto.CredentialResponse toResponse(SubscriptionCredential credential) {
        SubscriptionRequest sub = credential.getSubscriptionRequest();
        return SubscriptionCredentialDto.CredentialResponse.builder()
                .id(credential.getId())
                .subscriptionRequestId(sub.getId())
                .subscriptionInternalRequestId(sub.getInternalRequestId())
                .keycloakClientId(credential.getKeycloakClientId())
                .introspectionUrl(credential.getIntrospectionUrl())
                .deliveredToDataholder(credential.getDeliveredToDataholder())
                .deliveredAt(credential.getDeliveredAt())
                .deliveryAttempts(credential.getDeliveryAttempts())
                .lastDeliveryError(credential.getLastDeliveryError())
                .isActive(credential.getIsActive())
                .dataHolderGroupCode(sub.getDataHolderGroup().getCode())
                .dataHolderGroupName(sub.getDataHolderGroup().getName())
                .requestorGroupName(sub.getRequestorGroup().getName())
                .createdAt(credential.getCreatedAt())
                .build();
    }
}