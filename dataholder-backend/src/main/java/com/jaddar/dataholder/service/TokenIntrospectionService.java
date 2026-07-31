/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.entity.AgreementSubscription;
import com.jaddar.dataholder.entity.IntrospectionCredential;
import com.jaddar.dataholder.entity.TokenInfo;
import com.jaddar.dataholder.repository.AgreementSubscriptionRepository;
import com.jaddar.dataholder.repository.IntrospectionCredentialRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Performs token introspection using per-subscription credentials received
 * from the agreement server, or using the introspection URL provided by the
 * requestor group during subscription.
 *
 * When an RDAP query arrives with a bearer token and a requestor group
 * identifier, this service:
 *   1. Looks up the introspection credential for that requestor group
 *   2. If found, calls the introspection endpoint using the subscription's
 *      dedicated client_id/client_secret
 *   3. If no credential exists, falls back to the subscription's
 *      introspection URL (provided by the requestor group at subscription time)
 *   4. Returns the introspection result (active, sub, groups, roles, etc.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenIntrospectionService {

    private final IntrospectionCredentialRepository credentialRepository;
    private final AgreementSubscriptionRepository subscriptionRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Result of a token introspection call.
     */
    public record IntrospectionResult(
            boolean active,
            String sub,
            String username,
            String email,
            String clientId,
            Map<String, Object> claims
    ) {
        public static IntrospectionResult inactive() {
            return new IntrospectionResult(false, null, null, null, null, Map.of());
        }
    }

    /**
     * Introspect a bearer token using the credential associated with the
     * given requestor group code.
     *
     * @param token              The bearer token to introspect
     * @param requestorGroupCode The requestor group code from the RDAP query
     * @return The introspection result
     */
    public IntrospectionResult introspectByRequestorGroup(String token, String requestorGroupCode) {
        // First try: use dedicated IntrospectionCredential (provisioned by agreement-server)
        Optional<IntrospectionCredential> credOpt =
                credentialRepository.findByRequestorGroupCodeAndIsActiveTrue(requestorGroupCode);

        if (credOpt.isPresent()) {
            return introspect(token, credOpt.get());
        }

        // Fallback: use the introspection URL from the subscription itself
        // (provided by requestor group at subscription time)
        log.debug("No dedicated introspection credential for group '{}', checking subscription introspection URL",
                requestorGroupCode);
        return introspectViaSubscriptionUrl(token, requestorGroupCode);
    }

    /**
     * Introspect a bearer token using the credential associated with the
     * given subscription request ID.
     *
     * @param token     The bearer token to introspect
     * @param requestId The subscription request ID
     * @return The introspection result
     */
    public IntrospectionResult introspectByRequestId(String token, String requestId) {
        // First try: use dedicated IntrospectionCredential
        Optional<IntrospectionCredential> credOpt =
                credentialRepository.findByRequestId(requestId);

        if (credOpt.isPresent() && credOpt.get().getIsActive()) {
            return introspect(token, credOpt.get());
        }

        // Fallback: use the introspection URL from the subscription
        log.debug("No dedicated introspection credential for request '{}', checking subscription introspection URL",
                requestId);
        Optional<AgreementSubscription> subOpt = subscriptionRepository.findByRequestId(requestId);
        if (subOpt.isPresent() && subOpt.get().getIntrospectionUrl() != null
                && !subOpt.get().getIntrospectionUrl().isBlank()) {
            return introspectViaUrl(token, subOpt.get().getIntrospectionUrl(),
                    subOpt.get().getRequestorGroupCode());
        }

        log.warn("No introspection method available for request ID: {}", requestId);
        return IntrospectionResult.inactive();
    }

    /**
     * Try all active credentials to introspect a token (fallback when
     * requestor group is unknown).
     *
     * @param token The bearer token to introspect
     * @return The introspection result, or inactive if no credential validates it
     */
    public IntrospectionResult introspectWithAnyCredential(String token) {
        for (IntrospectionCredential cred : credentialRepository.findByIsActiveTrue()) {
            IntrospectionResult result = introspect(token, cred);
            if (result.active()) {
                return result;
            }
        }
        log.warn("Token could not be validated by any active introspection credential");
        return IntrospectionResult.inactive();
    }

    // ==================== Backward-compatible convenience methods ====================

    /**
     * Extract a bearer token from an Authorization header.
     *
     * @param authHeader The Authorization header value (e.g. "Bearer eyJ...")
     * @return The token string, or null if the header is missing/malformed
     */
    public String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }
        if (authHeader.toLowerCase().startsWith("bearer ")) {
            String token = authHeader.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    /**
     * Introspect a token without knowing the requestor group.
     * Tries all active credentials. Returns the entity TokenInfo for
     * backward compatibility with endpoints like /my-requests that
     * authenticate a logged-in user rather than a per-subscription RDAP query.
     *
     * @param token The bearer token to introspect
     * @return TokenInfo, or null if no active credential can validate the token
     */
    public TokenInfo introspectToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        IntrospectionResult result = introspectWithAnyCredential(token);
        if (!result.active()) {
            return null;
        }
        return toTokenInfo(result);
    }

    /**
     * Convert an IntrospectionResult to the entity TokenInfo,
     * populating all available fields from the Keycloak claims.
     */
    @SuppressWarnings("unchecked")
    private TokenInfo toTokenInfo(IntrospectionResult result) {
        Map<String, Object> claims = result.claims();

        // Extract groups from Keycloak's groups claim
        List<String> groups = claims.get("groups") instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : null;

        // Extract realm roles from realm_access.roles
        List<String> realmRoles = null;
        if (claims.get("realm_access") instanceof Map<?, ?> realmAccess) {
            if (realmAccess.get("roles") instanceof List<?> roles) {
                realmRoles = roles.stream().map(Object::toString).toList();
            }
        }

        // Extract resource/client roles from resource_access
        List<String> resourceRoles = null;
        if (claims.get("resource_access") instanceof Map<?, ?> resourceAccess) {
            resourceRoles = resourceAccess.values().stream()
                    .filter(v -> v instanceof Map)
                    .flatMap(v -> {
                        Object roles = ((Map<?, ?>) v).get("roles");
                        if (roles instanceof List<?> roleList) {
                            return roleList.stream().map(Object::toString);
                        }
                        return java.util.stream.Stream.empty();
                    })
                    .toList();
        }

        // Extract audience
        List<String> audience = null;
        Object aud = claims.get("aud");
        if (aud instanceof List<?> audList) {
            audience = audList.stream().map(Object::toString).toList();
        } else if (aud instanceof String audStr) {
            audience = List.of(audStr);
        }

        return TokenInfo.builder()
                .active(result.active())
                .sub(result.sub())
                .username(result.username())
                .email(result.email())
                .name((String) claims.get("name"))
                .clientId(result.clientId())
                .scope((String) claims.get("scope"))
                .tokenType("Bearer")
                .exp(claims.get("exp") instanceof Number n ? n.longValue() : null)
                .iat(claims.get("iat") instanceof Number n ? n.longValue() : null)
                .groups(groups)
                .realmRoles(realmRoles)
                .resourceRoles(resourceRoles)
                .issuer((String) claims.get("iss"))
                .audience(audience)
                .build();
    }

    // ==================== Core introspection ====================

    /**
     * Perform the actual introspection call to Keycloak.
     */
    @SuppressWarnings("unchecked")
    private IntrospectionResult introspect(String token, IntrospectionCredential credential) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(credential.getClientId(), credential.getClientSecret());

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", token);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    credential.getIntrospectionUrl(),
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> introspectionResponse = objectMapper.readValue(
                        response.getBody(), new TypeReference<>() {});

                boolean active = Boolean.TRUE.equals(introspectionResponse.get("active"));

                // Record usage
                credential.recordUsage();
                credentialRepository.save(credential);

                if (active) {
                    log.debug("Token introspection successful via credential '{}' for requestor group '{}'",
                            credential.getClientId(), credential.getRequestorGroupCode());

                    return new IntrospectionResult(
                            true,
                            (String) introspectionResponse.get("sub"),
                            (String) introspectionResponse.get("username"),
                            (String) introspectionResponse.get("email"),
                            (String) introspectionResponse.get("client_id"),
                            introspectionResponse
                    );
                } else {
                    log.debug("Token is not active (introspected via credential '{}')",
                            credential.getClientId());
                    return IntrospectionResult.inactive();
                }
            }

        } catch (Exception e) {
            log.error("Introspection failed using credential '{}': {}",
                    credential.getClientId(), e.getMessage());
        }

        return IntrospectionResult.inactive();
    }

    // ==================== Subscription URL Fallback ====================

    /**
     * Look up the subscription by requestor group code and try its introspection URL.
     */
    private IntrospectionResult introspectViaSubscriptionUrl(String token, String requestorGroupCode) {
        List<AgreementSubscription> subs = subscriptionRepository
                .findByRequestorGroupCodeAndStatus(requestorGroupCode, "ACTIVE");

        for (AgreementSubscription sub : subs) {
            if (sub.getIntrospectionUrl() != null && !sub.getIntrospectionUrl().isBlank()) {
                IntrospectionResult result = introspectViaUrl(token, sub.getIntrospectionUrl(), requestorGroupCode);
                if (result.active()) {
                    return result;
                }
            }
        }

        log.warn("No introspection method available for requestor group code: {}", requestorGroupCode);
        return IntrospectionResult.inactive();
    }

    /**
     * Introspect a bearer token by calling the given introspection URL directly.
     * This is the fallback path when no dedicated client credentials exist —
     * the token is sent as a standard RFC 7662 introspection request using
     * the token itself for authentication (Bearer token in Authorization header).
     */
    @SuppressWarnings("unchecked")
    private IntrospectionResult introspectViaUrl(String token, String introspectionUrl, String groupCode) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBearerAuth(token);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", token);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    introspectionUrl, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> introspectionResponse = objectMapper.readValue(
                        response.getBody(), new TypeReference<>() {});

                boolean active = Boolean.TRUE.equals(introspectionResponse.get("active"));

                if (active) {
                    log.debug("Token introspection successful via subscription URL for group '{}'", groupCode);
                    return new IntrospectionResult(
                            true,
                            (String) introspectionResponse.get("sub"),
                            (String) introspectionResponse.get("username"),
                            (String) introspectionResponse.get("email"),
                            (String) introspectionResponse.get("client_id"),
                            introspectionResponse
                    );
                } else {
                    log.debug("Token not active (introspected via subscription URL for group '{}')", groupCode);
                }
            }
        } catch (Exception e) {
            log.error("Introspection via subscription URL failed for group '{}': {}", groupCode, e.getMessage());
        }

        return IntrospectionResult.inactive();
    }
}