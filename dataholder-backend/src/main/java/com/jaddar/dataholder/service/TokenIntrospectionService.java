/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.entity.AgreementSubscription;
import com.jaddar.dataholder.entity.TokenInfo;
import com.jaddar.dataholder.repository.AgreementSubscriptionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Performs token introspection against the introspection URL registered on the
 * requestor group's subscription.
 *
 * The subscription is the single source of truth: it carries both the URL to
 * call and the client credentials to authenticate there. Subscriptions are set
 * up between the requestor manager and the Group Admin; the data holder only
 * consumes them. The requestor manager provisions a dedicated client in the
 * requestor's identity provider and delivers it to the Group Admin, which
 * stores it on the subscription and serves it back here.
 *
 * The Group Admin holds the authoritative record, so its copy is consulted
 * first — the same lookup {@code AccessControlService} already uses to resolve
 * a query's access level. The data holder's local subscription table is a
 * secondary source, covering queries that carry no group code and the case
 * where no Group Admin connection is configured.
 *
 * Resolution order for an RDAP query:
 *   1. Collect the requestor group's active subscriptions, from the Group Admin
 *      first and then the local table.
 *   2. Introspect at each subscription's URL, authenticating as its registered
 *      client, until one reports the token active.
 *   3. Confirm the token belongs to that requestor group.
 *   4. Report a distinct outcome when no subscription exists, when none has an
 *      introspection URL, when no client credentials were delivered, and when
 *      the token belongs to a different group — so the caller can be told what
 *      to do about it rather than getting a bare "invalid token".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenIntrospectionService {

    private final AgreementSubscriptionRepository subscriptionRepository;
    private final GroupAdminClient groupAdminClient;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Result of a single introspection call.
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
     * Why authentication succeeded or failed, with the message shown to the
     * requestor. The messages are deliberately actionable and free of any
     * internal detail: a requestor reading one should know exactly who to
     * contact and what to supply.
     */
    public enum Outcome {
        ACTIVE(null),

        NO_SUBSCRIPTION(
                "No active subscription was found for your requestor group. " +
                "Please contact the data holder group to confirm that your subscription " +
                "is approved and active before querying."),

        NO_INTROSPECTION_URL(
                "Authentication is not configured for your subscription. " +
                "Please contact the data holder group and provide the token introspection URL " +
                "for your identity provider, so that your access tokens can be validated."),

        NO_CLIENT_CREDENTIALS(
                "Authentication is not fully configured for your subscription. " +
                "Your token introspection URL is registered, but the client credentials the " +
                "data holder authenticates with were never received. Please contact the data " +
                "holder group to have them re-issued for your subscription."),

        GROUP_MISMATCH(
                "Your access token is valid but is not associated with the requestor group " +
                "for this subscription. Please sign in with an account belonging to that group, " +
                "or contact the data holder group if you believe your account should have access."),

        TOKEN_REJECTED(
                "Your access token could not be validated. It may have expired or been issued " +
                "for a different service — please obtain a new token and try again.");

        private final String userMessage;

        Outcome(String userMessage) {
            this.userMessage = userMessage;
        }

        /** The message to return to the requestor. Null for {@link #ACTIVE}. */
        public String getUserMessage() {
            return userMessage;
        }
    }

    /**
     * Outcome of introspecting a token on behalf of a subscription, together
     * with the resolved identity when the token is active.
     */
    public record SubscriptionIntrospection(Outcome outcome, TokenInfo tokenInfo) {
        public boolean isActive() {
            return outcome == Outcome.ACTIVE && tokenInfo != null;
        }

        static SubscriptionIntrospection failure(Outcome outcome) {
            return new SubscriptionIntrospection(outcome, null);
        }
    }

    // ==================== Public API ====================

    /**
     * Introspect a bearer token against the introspection URL registered on the
     * requestor group's subscription.
     *
     * @param token              the bearer token presented by the requestor
     * @param requestorGroupCode the requestor group code from the query, or null
     *                           when the query does not carry one (legacy
     *                           agreement-name queries), in which case every
     *                           active subscription's URL is tried
     * @return the outcome, carrying the resolved identity when active
     */
    public SubscriptionIntrospection introspectForSubscription(String token, String requestorGroupCode) {
        if (token == null || token.isBlank()) {
            return SubscriptionIntrospection.failure(Outcome.TOKEN_REJECTED);
        }

        String groupLabel = (requestorGroupCode != null && !requestorGroupCode.isBlank())
                ? requestorGroupCode : "(no group code supplied)";

        Map<String, Endpoint> endpoints = new LinkedHashMap<>();
        Set<String> registeredUrls = new LinkedHashSet<>();
        int subscriptionCount = 0;

        // The Group Admin is authoritative for subscriptions.
        for (Map<String, Object> sub : groupAdminSubscriptions(requestorGroupCode)) {
            subscriptionCount++;
            addEndpoints(endpoints, registeredUrls,
                    str(sub.get("introspectionUrl")),
                    str(sub.get("requestorGroupCode")),
                    str(sub.get("requestorGroupName")),
                    new Credential(str(sub.get("introspectionClientId")),
                                   str(sub.get("introspectionClientSecret"))));
        }

        for (AgreementSubscription sub : localSubscriptions(requestorGroupCode)) {
            subscriptionCount++;
            addEndpoints(endpoints, registeredUrls,
                    sub.getIntrospectionUrl(),
                    sub.getRequestorGroupCode(),
                    sub.getRequestorGroupName(),
                    new Credential(sub.getIntrospectionClientId(), sub.getIntrospectionClientSecret()));
        }

        if (subscriptionCount == 0) {
            log.warn("Introspection: no active subscription found for requestor group {}", groupLabel);
            return SubscriptionIntrospection.failure(Outcome.NO_SUBSCRIPTION);
        }

        if (registeredUrls.isEmpty()) {
            log.warn("Introspection: {} active subscription(s) for requestor group {}, " +
                            "but none has an introspection URL registered",
                    subscriptionCount, groupLabel);
            return SubscriptionIntrospection.failure(Outcome.NO_INTROSPECTION_URL);
        }

        if (endpoints.isEmpty()) {
            log.warn("Introspection: requestor group {} has {} introspection URL(s) registered but no " +
                            "client credentials were provisioned for its subscription(s)",
                    groupLabel, registeredUrls.size());
            return SubscriptionIntrospection.failure(Outcome.NO_CLIENT_CREDENTIALS);
        }

        for (Endpoint endpoint : endpoints.values()) {
            IntrospectionResult result = introspectViaUrl(token, endpoint, groupLabel);
            if (!result.active()) continue;

            if (!matchesRequestorGroup(result, endpoint)) {
                log.warn("Introspection: token for '{}' validated at the endpoint for requestor group {}, " +
                                "but carries none of the expected group identifiers {} — token claims: {}",
                        result.sub(), groupLabel, groupCandidates(endpoint), presentedGroupClaims(result));
                return SubscriptionIntrospection.failure(Outcome.GROUP_MISMATCH);
            }

            log.debug("Introspection succeeded for group {} via client '{}'", groupLabel, endpoint.clientId());
            return new SubscriptionIntrospection(Outcome.ACTIVE, toTokenInfo(result));
        }

        log.warn("Introspection: token was not accepted by any of the {} introspection endpoint(s) " +
                "registered for requestor group {}", endpoints.size(), groupLabel);
        return SubscriptionIntrospection.failure(Outcome.TOKEN_REJECTED);
    }

    /**
     * Extract a bearer token from an Authorization header.
     *
     * @param authHeader the Authorization header value (e.g. "Bearer eyJ...")
     * @return the token string, or null if the header is missing/malformed
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
     * Introspect a token when no requestor group code is available — used by the
     * requestor-facing endpoints such as /my-requests, which authenticate a
     * logged-in requestor rather than a per-subscription RDAP query.
     *
     * @param token the bearer token to introspect
     * @return the TokenInfo, or null if the token is not active
     */
    public TokenInfo introspectToken(String token) {
        SubscriptionIntrospection introspection = introspectForSubscription(token, null);
        return introspection.isActive() ? introspection.tokenInfo() : null;
    }

    // ==================== Subscription resolution ====================

    /**
     * The Group Admin's subscriptions for this group — the authoritative record,
     * fetched with the same lookup used to resolve a query's access level. Both
     * Group Admin endpoints already filter to active, currently-effective
     * subscriptions. Returns empty when no Group Admin connection is configured
     * or the call fails; the local table is then the only source.
     */
    private List<Map<String, Object>> groupAdminSubscriptions(String requestorGroupCode) {
        if (!groupAdminClient.isConfigured()) return List.of();
        try {
            if (requestorGroupCode != null && !requestorGroupCode.isBlank()) {
                return groupAdminClient.getSubscriptionsByGroupCode(requestorGroupCode);
            }
            return groupAdminClient.getActiveSubscriptions();
        } catch (Exception e) {
            log.warn("Introspection: could not fetch subscriptions from the Group Admin: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * The data holder's own subscription rows: the named group's when a code is
     * supplied, otherwise every active one. Only active, currently-effective
     * subscriptions are considered.
     */
    private List<AgreementSubscription> localSubscriptions(String requestorGroupCode) {
        LocalDateTime now = LocalDateTime.now();
        if (requestorGroupCode != null && !requestorGroupCode.isBlank()) {
            return subscriptionRepository.findActiveAndEffectiveByGroupCode(requestorGroupCode, now);
        }
        return subscriptionRepository.findActiveAndEffective(now);
    }

    /**
     * One subscription's introspection endpoint: where to call, which client to
     * authenticate as, and which group identifiers a token must carry to be
     * accepted for it.
     */
    private record Endpoint(String url, String clientId, String clientSecret,
                            String groupCode, String groupName) {

        /** Identity of the endpoint for de-duplication: same URL and same client. */
        String key() {
            return url + "|" + (clientId != null ? clientId : "");
        }

        static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    /** A client identity to authenticate with at an introspection endpoint. */
    private record Credential(String clientId, String clientSecret) {
        boolean isComplete() {
            return Endpoint.notBlank(clientId) && Endpoint.notBlank(clientSecret);
        }
    }

    /**
     * Register this subscription's introspection endpoint, if it has both a URL
     * and the client credentials to authenticate there. The credentials are the
     * ones the requestor manager provisioned for the subscription and delivered
     * to the Group Admin, which serves them back on the subscription record.
     */
    private void addEndpoints(Map<String, Endpoint> endpoints, Set<String> registeredUrls, String url,
                              String groupCode, String groupName, Credential credential) {
        if (!Endpoint.notBlank(url)) return;
        registeredUrls.add(url);

        if (credential.isComplete()) {
            addEndpoint(endpoints, new Endpoint(url, credential.clientId(),
                    credential.clientSecret(), groupCode, groupName));
        }
    }

    /** Register an endpoint, keeping the first entry seen for each URL/client pair. */
    private void addEndpoint(Map<String, Endpoint> endpoints, Endpoint endpoint) {
        endpoints.putIfAbsent(endpoint.key(), endpoint);
    }

    private String str(Object value) {
        return value instanceof String s ? s : null;
    }

    // ==================== Requestor group binding ====================

    /**
     * Verify the token actually belongs to the requestor group this subscription
     * is for. Without this, any valid token from a shared identity provider could
     * be presented under any group's code and receive that group's access level.
     *
     * Both the group code and the group name are accepted: the requestor manager
     * syncs requestor groups into the identity provider by name, so a token's
     * group membership carries the name today; a deployment that also surfaces
     * the code (as a user attribute mapped into a claim, say) matches on that
     * instead, with no change here.
     *
     * A subscription with neither identifier cannot be bound, so it is allowed
     * through — the endpoint and its credentials are then the only assurance.
     */
    private boolean matchesRequestorGroup(IntrospectionResult result, Endpoint endpoint) {
        Set<String> expected = groupCandidates(endpoint);
        if (expected.isEmpty()) return true;

        for (String claim : presentedGroupClaims(result)) {
            if (expected.contains(normalizeGroup(claim))) return true;
        }
        return false;
    }

    /** The normalized group identifiers that satisfy this endpoint's binding. */
    private Set<String> groupCandidates(Endpoint endpoint) {
        Set<String> candidates = new LinkedHashSet<>();
        if (Endpoint.notBlank(endpoint.groupCode())) candidates.add(normalizeGroup(endpoint.groupCode()));
        if (Endpoint.notBlank(endpoint.groupName())) candidates.add(normalizeGroup(endpoint.groupName()));
        return candidates;
    }

    /**
     * Every group-ish value the token presents: the groups claim plus realm and
     * client roles, since deployments differ in which of them carries the group.
     */
    @SuppressWarnings("unchecked")
    private Set<String> presentedGroupClaims(IntrospectionResult result) {
        Set<String> presented = new LinkedHashSet<>();
        Map<String, Object> claims = result.claims();

        addClaimValues(presented, claims.get("groups"));

        if (claims.get("realm_access") instanceof Map<?, ?> realmAccess) {
            addClaimValues(presented, realmAccess.get("roles"));
        }
        if (claims.get("resource_access") instanceof Map<?, ?> resourceAccess) {
            for (Object client : resourceAccess.values()) {
                if (client instanceof Map<?, ?> clientMap) {
                    addClaimValues(presented, clientMap.get("roles"));
                }
            }
        }
        return presented;
    }

    private void addClaimValues(Set<String> target, Object value) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) target.add(String.valueOf(item));
            }
        } else if (value instanceof String s && !s.isBlank()) {
            target.add(s);
        }
    }

    /** Compare group identifiers case-insensitively, ignoring any leading path separator. */
    private String normalizeGroup(String value) {
        String normalized = value.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.toLowerCase();
    }

    // ==================== Core introspection ====================

    /**
     * Introspect a bearer token by calling the subscription's introspection URL,
     * authenticating as the client the subscription registered (RFC 7662 §2.1
     * client authentication, sent as HTTP Basic).
     */
    @SuppressWarnings("unchecked")
    private IntrospectionResult introspectViaUrl(String token, Endpoint endpoint, String groupCode) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(endpoint.clientId(), endpoint.clientSecret());

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", token);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint.url(), HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> introspectionResponse = objectMapper.readValue(
                        response.getBody(), new TypeReference<>() {});

                boolean active = Boolean.TRUE.equals(introspectionResponse.get("active"));

                if (active) {
                    return new IntrospectionResult(
                            true,
                            (String) introspectionResponse.get("sub"),
                            (String) introspectionResponse.get("username"),
                            (String) introspectionResponse.get("email"),
                            (String) introspectionResponse.get("client_id"),
                            introspectionResponse
                    );
                }

                log.warn("Introspection declined the token for group '{}': endpoint={} client={} token[{}] response={}",
                        groupCode, endpoint.url(), endpoint.clientId(), describeToken(token), response.getBody());
            } else {
                log.warn("Introspection endpoint for group '{}' returned {} (endpoint={} client={}): {}",
                        groupCode, response.getStatusCode(), endpoint.url(), endpoint.clientId(), response.getBody());
            }
        } catch (Exception e) {
            log.error("Introspection call failed for group '{}' (endpoint={} client={} token[{}]): {}",
                    groupCode, endpoint.url(), endpoint.clientId(), describeToken(token), e.getMessage());
        }

        return IntrospectionResult.inactive();
    }

    /**
     * Bearer token diagnostics...
     */
    private String describeToken(String token) {
        if (token == null || token.isBlank()) {
            return "absent";
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return "not a JWT, length=" + token.length();
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> claims = objectMapper.readValue(payload, new TypeReference<>() {});

            Object exp = claims.get("exp");
            String expiry = exp instanceof Number n
                    ? n.longValue() + (n.longValue() * 1000 < System.currentTimeMillis() ? " (EXPIRED)" : " (valid)")
                    : "none";

            return String.format("typ=%s iss=%s azp=%s sub=%s sid=%s exp=%s",
                    claims.get("typ"), claims.get("iss"), claims.get("azp"),
                    claims.get("sub"), claims.get("sid"), expiry);
        } catch (Exception e) {
            return "undecodable JWT payload: " + e.getMessage();
        }
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
}
