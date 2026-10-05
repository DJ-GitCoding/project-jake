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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
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

    /**
     * Client role whose presence in a token puts this client in the token's audience, which is what
     * Keycloak requires before it will introspect. See {@link #grantIntrospectionAudience}.
     */
    private static final String INTROSPECTION_ROLE = "introspect";

    /** Introspection-response claim carrying the requestor group's public joining link. */
    public static final String JOINING_URL_CLAIM = "joining_url";
    private static final String JOINING_URL_MAPPER = "joining-url";
    private static final String SELF_AUDIENCE_MAPPER = "self-audience";

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

    public enum AudienceState {
        /** The grant was missing and has now been applied. */
        GRANTED,
        /** The group already carried the role; nothing was written. */
        ALREADY_PRESENT,
        /** No Keycloak client with that clientId; nothing to grant against. */
        CLIENT_MISSING,
        /** No Keycloak group with the requestor group's name; the group sync must run first. */
        GROUP_MISSING,
        /** Keycloak was unreachable or rejected the calls. */
        FAILED
    }

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
                                              String requestorGroupName,
                                              String joiningUrl) {
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
            // Re-assert the audience grant: re-provisioning must converge on a working client.
            grantIntrospectionAudience(adminToken, existingUuid, clientId, requestorGroupName);
            applyJoiningUrlClaim(adminToken, existingUuid, clientId, joiningUrl);
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

            // Without this the client can authenticate but Keycloak still refuses to introspect.
            grantIntrospectionAudience(adminToken, clientUuid, clientId, requestorGroupName);
            applyJoiningUrlClaim(adminToken, clientUuid, clientId, joiningUrl);

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
    // ==================== Introspection audience ====================

    /**
     * Make this client introspectable for the requestor group's tokens.
     *
     * Keycloak refuses to introspect a token unless the calling client is in that token's audience:
     * {@code Introspection denied: client '...' not in audience of token for '...'}. Authenticating
     * successfully is not enough. Rather than adding an audience mapper to the shared login client —
     * which would accumulate one mapper per subscription and mutate a client every requestor uses —
     * this creates a client role and assigns it to the requestor group. Keycloak's default
     * "audience resolve" mapper then adds the client to {@code aud} for exactly those users, because
     * their tokens carry a role for it.
     *
     * That also puts the client under {@code resource_access} in the token, which the data holder's
     * requestor-group binding check reads.
     *
     * Failures are logged rather than thrown: the client and its secret are already usable, and
     * aborting here would leave an orphaned Keycloak client behind. The log says plainly that
     * introspection will be denied until this is repaired, because a silent gap here looks exactly
     * like a working subscription.
     */
    private AudienceState grantIntrospectionAudience(String adminToken, String clientUuid, String clientId,
                                                     String requestorGroupName) {
        try {
            String groupId = findGroupIdByName(adminToken, requestorGroupName);
            if (groupId == null) {
                log.error("No Keycloak group named '{}' for client '{}' — introspection will be DENIED by "
                                + "Keycloak until this client is in the token audience. The requestor group "
                                + "must exist in Keycloak (see the requestor-group sync).",
                        requestorGroupName, clientId);
                return AudienceState.GROUP_MISSING;
            }

            if (groupHasIntrospectionRole(adminToken, groupId, clientUuid)) {
                log.debug("Group '{}' already carries '{}' on client '{}'",
                        requestorGroupName, INTROSPECTION_ROLE, clientId);
                return AudienceState.ALREADY_PRESENT;
            }

            ensureClientRole(adminToken, clientUuid, clientId);

            Map<String, Object> role = getClientRole(adminToken, clientUuid);
            if (role == null) {
                log.error("Could not read the '{}' role on client '{}' — introspection will be DENIED by "
                        + "Keycloak until this client is in the token audience.", INTROSPECTION_ROLE, clientId);
                return AudienceState.FAILED;
            }

            assignClientRoleToGroup(adminToken, groupId, clientUuid, role);

            log.info("Granted '{}' on client '{}' to Keycloak group '{}' — that group's tokens now carry "
                    + "this client in their audience", INTROSPECTION_ROLE, clientId, requestorGroupName);
            return AudienceState.GRANTED;

        } catch (Exception e) {
            log.error("Failed to grant the introspection audience for client '{}' (group '{}'): {}. "
                            + "Introspection will be DENIED by Keycloak until this client is in the token audience.",
                    clientId, requestorGroupName, e.getMessage());
            return AudienceState.FAILED;
        }
    }

    // ==================== Joining link ====================

    /** One introspection client and the joining link its introspection responses should carry. */
    public record JoiningUrlClaim(String clientUuid, String clientId, String joiningUrl) {}

    /**
     * Bring each client's {@value #JOINING_URL_CLAIM} claim in line with its requestor group's
     * joining link, under one admin token.
     *
     * @return how many clients now match; a client Keycloak could not update is left for the next run
     */
    public int syncJoiningUrlClaims(List<JoiningUrlClaim> claims) {
        if (claims.isEmpty()) return 0;
        String adminToken = getAdminToken();
        int ok = 0;
        for (JoiningUrlClaim claim : claims) {
            if (applyJoiningUrlClaim(adminToken, claim.clientUuid(), claim.clientId(), claim.joiningUrl())) ok++;
        }
        return ok;
    }

    /**
     * Make this introspection client report the requestor group's joining link, or stop reporting
     * one when {@code joiningUrl} is null.
     *
     * <p>The claim is a hardcoded claim added to introspection responses only, never to the access
     * token. Keycloak puts a client's mappers into the tokens issued to that client, so the claim is
     * read by introspecting the client's own client-credentials token: a data holder holding these
     * credentials can learn the link without any requestor's token, which is what a public query
     * needs. Keycloak only introspects a token for a client in its audience, so the client is also
     * given an audience mapper naming itself — it then can introspect its own token and, as before,
     * the group members' tokens that carry its role.
     *
     * <p>Failures are logged and reported, never thrown: the client keeps working for token
     * introspection either way, and the sync job retries.
     */
    private boolean applyJoiningUrlClaim(String adminToken, String clientUuid, String clientId, String joiningUrl) {
        try {
            List<Map<String, Object>> mappers = getProtocolMappers(adminToken, clientUuid);
            Map<String, Object> claimMapper = findMapper(mappers, JOINING_URL_MAPPER);
            Map<String, Object> audienceMapper = findMapper(mappers, SELF_AUDIENCE_MAPPER);

            if (joiningUrl == null) {
                if (claimMapper != null) deleteProtocolMapper(adminToken, clientUuid, (String) claimMapper.get("id"));
                if (audienceMapper != null) deleteProtocolMapper(adminToken, clientUuid, (String) audienceMapper.get("id"));
                if (claimMapper != null) log.info("Removed the joining link from introspection client '{}'", clientId);
                return true;
            }

            if (audienceMapper == null) {
                createProtocolMapper(adminToken, clientUuid, selfAudienceMapper(clientId));
            }

            Map<String, Object> wanted = joiningUrlMapper(joiningUrl);
            if (claimMapper == null) {
                createProtocolMapper(adminToken, clientUuid, wanted);
                log.info("Introspection client '{}' now reports joining link {}", clientId, joiningUrl);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) claimMapper.get("config");
                if (config == null || !joiningUrl.equals(config.get("claim.value"))) {
                    wanted.put("id", claimMapper.get("id"));
                    updateProtocolMapper(adminToken, clientUuid, (String) claimMapper.get("id"), wanted);
                    log.info("Introspection client '{}' now reports joining link {}", clientId, joiningUrl);
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("Could not set the joining link on introspection client '{}': {}", clientId, e.getMessage());
            return false;
        }
    }

    private Map<String, Object> joiningUrlMapper(String joiningUrl) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("claim.name", JOINING_URL_CLAIM);
        config.put("claim.value", joiningUrl);
        config.put("jsonType.label", "String");
        config.put("access.token.claim", "false");
        config.put("id.token.claim", "false");
        config.put("userinfo.token.claim", "false");
        config.put("introspection.token.claim", "true");

        Map<String, Object> mapper = new LinkedHashMap<>();
        mapper.put("name", JOINING_URL_MAPPER);
        mapper.put("protocol", "openid-connect");
        mapper.put("protocolMapper", "oidc-hardcoded-claim-mapper");
        mapper.put("config", config);
        return mapper;
    }

    private Map<String, Object> selfAudienceMapper(String clientId) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("included.client.audience", clientId);
        config.put("access.token.claim", "true");
        config.put("id.token.claim", "false");
        config.put("introspection.token.claim", "true");

        Map<String, Object> mapper = new LinkedHashMap<>();
        mapper.put("name", SELF_AUDIENCE_MAPPER);
        mapper.put("protocol", "openid-connect");
        mapper.put("protocolMapper", "oidc-audience-mapper");
        mapper.put("config", config);
        return mapper;
    }

    private static Map<String, Object> findMapper(List<Map<String, Object>> mappers, String name) {
        return mappers.stream().filter(m -> name.equals(m.get("name"))).findFirst().orElse(null);
    }

    private List<Map<String, Object>> getProtocolMappers(String adminToken, String clientUuid) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<String> response = restTemplate.exchange(
                adminUrl + "/clients/" + clientUuid + "/protocol-mappers/models",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        if (response.getBody() == null) return List.of();
        return objectMapper.readValue(response.getBody(), new TypeReference<>() {});
    }

    private void createProtocolMapper(String adminToken, String clientUuid, Map<String, Object> mapper) {
        restTemplate.exchange(adminUrl + "/clients/" + clientUuid + "/protocol-mappers/models",
                HttpMethod.POST, new HttpEntity<>(mapper, jsonHeaders(adminToken)), String.class);
    }

    private void updateProtocolMapper(String adminToken, String clientUuid, String mapperId, Map<String, Object> mapper) {
        restTemplate.exchange(adminUrl + "/clients/" + clientUuid + "/protocol-mappers/models/" + mapperId,
                HttpMethod.PUT, new HttpEntity<>(mapper, jsonHeaders(adminToken)), String.class);
    }

    private void deleteProtocolMapper(String adminToken, String clientUuid, String mapperId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        restTemplate.exchange(adminUrl + "/clients/" + clientUuid + "/protocol-mappers/models/" + mapperId,
                HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
    }

    private static HttpHeaders jsonHeaders(String adminToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Assert the introspection audience grant for an already-provisioned client, by clientId.
     */
    public AudienceState ensureIntrospectionAudience(String clientId, String requestorGroupName) {
        if (clientId == null || clientId.isBlank()) {
            return AudienceState.CLIENT_MISSING;
        }

        String adminToken;
        try {
            adminToken = getAdminToken();
        } catch (Exception e) {
            log.warn("Could not obtain a Keycloak admin token to verify the introspection audience "
                    + "for client '{}': {}", clientId, e.getMessage());
            return AudienceState.FAILED;
        }

        String clientUuid = findClientUuid(adminToken, clientId);
        if (clientUuid == null) {
            log.warn("No Keycloak client '{}' — cannot grant the introspection audience. The "
                    + "subscription's credentials refer to a client that no longer exists.", clientId);
            return AudienceState.CLIENT_MISSING;
        }

        return grantIntrospectionAudience(adminToken, clientUuid, clientId, requestorGroupName);
    }

    /**
     * Whether the group already has the introspection role for this client.
     */
    @SuppressWarnings("unchecked")
    private boolean groupHasIntrospectionRole(String adminToken, String groupId, String clientUuid) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    adminUrl + "/groups/" + groupId + "/role-mappings/clients/" + clientUuid,
                    HttpMethod.GET, new HttpEntity<>(headers), List.class);

            if (response.getBody() == null) return false;
            for (Object item : response.getBody()) {
                if (item instanceof Map<?, ?> role && INTROSPECTION_ROLE.equals(role.get("name"))) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("Could not read role mappings for group {} on client {}: {}",
                    groupId, clientUuid, e.getMessage());
        }
        return false;
    }

    /** Create the introspection role on the client. An existing role (409) is success. */
    private void ensureClientRole(String adminToken, String clientUuid, String clientId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> role = new LinkedHashMap<>();
        role.put("name", INTROSPECTION_ROLE);
        role.put("description", "Marks a bearer as introspectable by " + clientId
                + ". Its presence in the token places this client in the audience.");

        try {
            restTemplate.exchange(adminUrl + "/clients/" + clientUuid + "/roles",
                    HttpMethod.POST, new HttpEntity<>(role, headers), String.class);
        } catch (HttpClientErrorException.Conflict e) {
            log.debug("Client role '{}' already exists on '{}'", INTROSPECTION_ROLE, clientId);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getClientRole(String adminToken, String clientUuid) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    adminUrl + "/clients/" + clientUuid + "/roles/" + INTROSPECTION_ROLE,
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.warn("Could not fetch client role '{}': {}", INTROSPECTION_ROLE, e.getMessage());
            return null;
        }
    }

    /** Resolve a Keycloak group by exact name. The search endpoint matches on substring. */
    @SuppressWarnings("unchecked")
    private String findGroupIdByName(String adminToken, String groupName) {
        if (groupName == null || groupName.isBlank()) return null;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    adminUrl + "/groups?search=" + UriUtils.encodeQueryParam(groupName, StandardCharsets.UTF_8),
                    HttpMethod.GET, new HttpEntity<>(headers), List.class);

            if (response.getBody() == null) return null;
            for (Object item : response.getBody()) {
                if (item instanceof Map<?, ?> group && groupName.equals(group.get("name"))) {
                    return (String) group.get("id");
                }
            }
        } catch (Exception e) {
            log.warn("Could not resolve Keycloak group '{}': {}", groupName, e.getMessage());
        }
        return null;
    }

    /** Assign the client role to the group. Keycloak treats a repeat assignment as a no-op. */
    private void assignClientRoleToGroup(String adminToken, String groupId, String clientUuid,
                                          Map<String, Object> role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(
                adminUrl + "/groups/" + groupId + "/role-mappings/clients/" + clientUuid,
                HttpMethod.POST, new HttpEntity<>(List.of(role), headers), String.class);
    }

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