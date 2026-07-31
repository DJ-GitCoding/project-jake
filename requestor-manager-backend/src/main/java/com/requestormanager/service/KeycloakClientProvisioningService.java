/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Provisions per-subscription Keycloak clients. Each is a confidential client_credentials-only client
 * authorized solely for token introspection, with a client_id derived from the subscription request ID,
 * so data holders can introspect RDAP bearer tokens without broader Keycloak access.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakClientProvisioningService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${keycloak.token-url:http://keycloak:8080/realms/master/protocol/openid-connect/token}")
    private String tokenUrl;

    @Value("${keycloak.admin-url:http://keycloak:8080/admin/realms/master}")
    private String adminUrl;

    @Value("${keycloak.introspect-url:http://keycloak:8080/realms/master/protocol/openid-connect/token/introspect}")
    private String introspectUrl;

    @Value("${keycloak.admin-username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin-password:admin}")
    private String adminPassword;

    /**
     * Result of provisioning a new Keycloak client.
     */
    public record ProvisionedClient(
            String clientId,
            String clientUuid,
            String clientSecret,
            String introspectionUrl,
            String tokenUrl
    ) {}

    /**
     * Create a new Keycloak client for a subscription.
     *
     * @param subscriptionInternalId The internal request ID (e.g., "SUB-1234567890-1234")
     * @param dataHolderGroupCode         The data holder code (for description/metadata)
     * @param requestorGroupName     The requestor group name (for description/metadata)
     * @return The provisioned client details
     */
    public ProvisionedClient provisionClient(String subscriptionInternalId,
                                              String dataHolderGroupCode,
                                              String requestorGroupName) {
        String adminToken = getAdminToken();
        String clientId = buildClientId(subscriptionInternalId);

        log.info("Provisioning Keycloak client '{}' for subscription {} (DH: {}, Group: {})",
                clientId, subscriptionInternalId, dataHolderGroupCode, requestorGroupName);

        // Check if client already exists
        String existingUuid = findClientUuid(adminToken, clientId);
        if (existingUuid != null) {
            log.warn("Keycloak client '{}' already exists (uuid: {}), regenerating secret",
                    clientId, existingUuid);
            String secret = regenerateClientSecret(adminToken, existingUuid);
            return new ProvisionedClient(clientId, existingUuid, secret, introspectUrl, tokenUrl);
        }

        // Create the client
        Map<String, Object> clientRepresentation = buildClientRepresentation(
                clientId, subscriptionInternalId, dataHolderGroupCode, requestorGroupName);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(clientRepresentation, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    adminUrl + "/clients",
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to create Keycloak client: HTTP " + response.getStatusCode());
            }

            // Extract UUID from Location header
            String location = response.getHeaders().getFirst("Location");
            String clientUuid;
            if (location != null && location.contains("/")) {
                clientUuid = location.substring(location.lastIndexOf('/') + 1);
            } else {
                // Fallback: look it up
                clientUuid = findClientUuid(adminToken, clientId);
                if (clientUuid == null) {
                    throw new RuntimeException("Failed to find created client UUID for " + clientId);
                }
            }

            // Retrieve the generated secret
            String secret = getClientSecret(adminToken, clientUuid);

            log.info("Successfully provisioned Keycloak client '{}' (uuid: {})", clientId, clientUuid);

            return new ProvisionedClient(clientId, clientUuid, secret, introspectUrl, tokenUrl);

        } catch (Exception e) {
            log.error("Failed to provision Keycloak client '{}': {}", clientId, e.getMessage(), e);
            throw new RuntimeException("Failed to provision Keycloak client: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a Keycloak client when a subscription is terminated.
     *
     * @param clientUuid The Keycloak internal UUID of the client
     */
    public void deleteClient(String clientUuid) {
        String adminToken = getAdminToken();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    adminUrl + "/clients/" + clientUuid,
                    HttpMethod.DELETE,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully deleted Keycloak client (uuid: {})", clientUuid);
            } else {
                log.warn("Failed to delete Keycloak client (uuid: {}): HTTP {}", clientUuid, response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error deleting Keycloak client (uuid: {}): {}", clientUuid, e.getMessage(), e);
        }
    }

    /**
     * Disable a Keycloak client (soft-revoke) without deleting it.
     */
    public void disableClient(String clientUuid) {
        String adminToken = getAdminToken();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> update = Map.of("enabled", false);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(update, headers);

            restTemplate.exchange(
                    adminUrl + "/clients/" + clientUuid,
                    HttpMethod.PUT,
                    request,
                    String.class
            );

            log.info("Disabled Keycloak client (uuid: {})", clientUuid);
        } catch (Exception e) {
            log.error("Error disabling Keycloak client (uuid: {}): {}", clientUuid, e.getMessage(), e);
        }
    }

    /**
     * Regenerate the client secret for an existing client.
     */
    public String regenerateClientSecret(String adminToken, String clientUuid) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    adminUrl + "/clients/" + clientUuid + "/client-secret",
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = objectMapper.readValue(
                        response.getBody(), new TypeReference<>() {});
                return (String) body.get("value");
            }
        } catch (Exception e) {
            log.error("Failed to regenerate secret for client (uuid: {}): {}", clientUuid, e.getMessage());
        }
        throw new RuntimeException("Failed to regenerate client secret for " + clientUuid);
    }

    // ==================== Private Helpers ====================

    /**
     * Build a deterministic client ID from the subscription's internal request ID.
     * Example: "sub-SUB-1708901234567-4321-introspect"
     */
    private String buildClientId(String subscriptionInternalId) {
        // Sanitize: Keycloak client IDs allow alphanumeric, hyphens, underscores
        String sanitized = subscriptionInternalId.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        return "sub-" + sanitized + "-introspect";
    }

    /**
     * Build the Keycloak client representation for creation.
     * Restricted to:
     *   - Confidential client (not public)
     *   - client_credentials grant only (serviceAccountsEnabled)
     *   - No standard/implicit/direct flows
     *   - No redirect URIs (not for user login)
     */
    private Map<String, Object> buildClientRepresentation(String clientId,
                                                           String subscriptionInternalId,
                                                           String dataHolderGroupCode,
                                                           String requestorGroupName) {
        Map<String, Object> client = new LinkedHashMap<>();
        client.put("clientId", clientId);
        client.put("name", "Subscription Introspection: " + subscriptionInternalId);
        client.put("description", String.format(
                "Auto-provisioned introspection client for subscription %s. " +
                "Data Holder: %s, Requestor Group: %s. " +
                "This client may ONLY be used for token introspection.",
                subscriptionInternalId, dataHolderGroupCode, requestorGroupName));
        client.put("enabled", true);
        client.put("protocol", "openid-connect");

        // Confidential client with client_credentials
        client.put("publicClient", false);
        client.put("clientAuthenticatorType", "client-secret");
        client.put("serviceAccountsEnabled", true);

        // Disable all user-facing flows
        client.put("standardFlowEnabled", false);
        client.put("implicitFlowEnabled", false);
        client.put("directAccessGrantsEnabled", false);

        // No redirect URIs (not a login client)
        client.put("redirectUris", List.of());
        client.put("webOrigins", List.of());

        // Attributes to tag this as a subscription introspection client
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("subscription.internal.id", subscriptionInternalId);
        attributes.put("subscription.dataholder.code", dataHolderGroupCode);
        attributes.put("subscription.requestor.group", requestorGroupName);
        attributes.put("subscription.purpose", "introspection-only");
        client.put("attributes", attributes);

        return client;
    }

    /**
     * Get an admin access token using resource owner password credentials.
     */
    private String getAdminToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", "admin-cli");
            body.add("username", adminUsername);
            body.add("password", adminPassword);
            body.add("grant_type", "password");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> tokenResponse = objectMapper.readValue(
                        response.getBody(), new TypeReference<>() {});
                return (String) tokenResponse.get("access_token");
            }

            throw new RuntimeException("Failed to get admin token: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to get Keycloak admin token: {}", e.getMessage());
            throw new RuntimeException("Failed to get Keycloak admin token: " + e.getMessage(), e);
        }
    }

    /**
     * Find the Keycloak internal UUID for a client by its clientId.
     */
    @SuppressWarnings("unchecked")
    private String findClientUuid(String adminToken, String clientId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    adminUrl + "/clients?clientId=" + clientId,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> clients = objectMapper.readValue(
                        response.getBody(), new TypeReference<>() {});
                if (!clients.isEmpty()) {
                    return (String) clients.get(0).get("id");
                }
            }
        } catch (Exception e) {
            log.warn("Error looking up client '{}': {}", clientId, e.getMessage());
        }
        return null;
    }

    /**
     * Retrieve the client secret for a Keycloak client.
     */
    @SuppressWarnings("unchecked")
    private String getClientSecret(String adminToken, String clientUuid) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    adminUrl + "/clients/" + clientUuid + "/client-secret",
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = objectMapper.readValue(
                        response.getBody(), new TypeReference<>() {});
                return (String) body.get("value");
            }
        } catch (Exception e) {
            log.warn("Could not GET client secret for uuid {}, trying POST regenerate", clientUuid);
        }

        // Fallback: regenerate the secret
        return regenerateClientSecret(adminToken, clientUuid);
    }
}