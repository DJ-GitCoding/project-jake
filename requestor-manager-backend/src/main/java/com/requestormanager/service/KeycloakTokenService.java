/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Service to validate Keycloak tokens via the introspect endpoint.
 * This enables external services to authenticate with this server using their Keycloak tokens.
 */
@Service
@Slf4j
public class KeycloakTokenService {

    @Value("${keycloak.introspect-url:http://keycloak:8080/realms/master/protocol/openid-connect/token/introspect}")
    private String introspectUrl;

    @Value("${keycloak.client-id:}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public KeycloakTokenService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        log.info("============================================");
        log.info("KeycloakTokenService initialized:");
        log.info("  Introspect URL: {}", introspectUrl);
        log.info("  Client ID: {}", clientId);
        log.info("  Client Secret: {}", clientSecret != null && !clientSecret.isEmpty() ? "[SET]" : "[NOT SET]");
        log.info("============================================");
    }

    /**
     * Introspect a Keycloak token to validate it and extract claims.
     *
     * @param token The Bearer token to validate
     * @return TokenIntrospectionResult containing validation status and claims
     */
    public TokenIntrospectionResult introspectToken(String token) {
        try {
            log.debug("Token introspection requested");

            // Remove "Bearer " prefix if present
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            // Introspect at the token's issuer
            String effectiveIntrospectUrl = introspectUrl; // configured fallback
            try {
                String[] jwtParts = token != null ? token.split("\\.") : new String[0];
                if (jwtParts.length >= 2) {
                    String payload = jwtParts[1];
                    int pad = (4 - payload.length() % 4) % 4;
                    payload = payload + "=".repeat(pad);
                    JsonNode claims = objectMapper.readTree(
                        new String(Base64.getUrlDecoder().decode(payload), java.nio.charset.StandardCharsets.UTF_8));
                    if (claims.hasNonNull("iss")) {
                        String issuer = claims.get("iss").asText().replaceAll("/+$", "");
                        // Use the token's realm PATH, but force the internally-reachable Keycloak
                        // host/port from the configured introspect URL. The issuer host (e.g.
                        // localhost:8080) is the public/browser hostname and is NOT resolvable
                        // container-to-container, which would make introspection fail with
                        // "Connection refused".
                        java.net.URI issuerUri = java.net.URI.create(issuer);
                        java.net.URI configUri = java.net.URI.create(introspectUrl);
                        effectiveIntrospectUrl = configUri.getScheme() + "://" + configUri.getAuthority()
                                + issuerUri.getPath() + "/protocol/openid-connect/token/introspect";
                        log.info("Derived introspect URL (host from config, realm from token): {}", effectiveIntrospectUrl);
                    }
                }
            } catch (Exception e) {
                log.warn("Could not read token issuer; falling back to configured introspect URL ({}): {}",
                    introspectUrl, e.getMessage());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            // Use Basic Auth with client credentials
            if (clientId != null && !clientId.isEmpty() && clientSecret != null && !clientSecret.isEmpty()) {
                String auth = clientId + ":" + clientSecret;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                headers.set("Authorization", "Basic " + encodedAuth);
                log.debug("Introspecting with client: {}", clientId);
            } else {
                log.warn("No Keycloak client credentials configured for token introspection");
            }

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", token);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            log.debug("Calling introspect endpoint: {}", effectiveIntrospectUrl);
            ResponseEntity<String> response = restTemplate.postForEntity(effectiveIntrospectUrl, request, String.class);

            log.debug("Introspect response status: {}", response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());

                boolean active = json.has("active") && json.get("active").asBoolean();

                if (!active) {
                    log.debug("Token is not active");
                    return TokenIntrospectionResult.inactive();
                }

                // Extract claims
                String subject = json.has("sub") ? json.get("sub").asText() : null;
                String email = json.has("email") ? json.get("email").asText() : null;
                String preferredUsername = json.has("preferred_username") ? json.get("preferred_username").asText() : null;
                
                // Extract groups - Keycloak typically puts them in "groups" claim
                List<String> groups = new ArrayList<>();
                if (json.has("groups")) {
                    JsonNode groupsNode = json.get("groups");
                    if (groupsNode.isArray()) {
                        for (JsonNode group : groupsNode) {
                            String groupPath = group.asText();
                            // Keycloak encodes groups as full paths: "/Parent/Child/LeafGroup"
                            // We need both the full path (minus leading slash) and the leaf name,
                            // because the RequestorGroup.name could be stored as either form.
                            // Extract the leaf name (last segment after the final slash).
                            if (groupPath.startsWith("/")) {
                                groupPath = groupPath.substring(1);
                            }
                            // Always add the full path (e.g. "Parent/Child/LeafGroup")
                            groups.add(groupPath);
                            // Also add just the leaf name if the path contains sub-groups
                            int lastSlash = groupPath.lastIndexOf('/');
                            if (lastSlash >= 0) {
                                String leafName = groupPath.substring(lastSlash + 1);
                                if (!leafName.isBlank() && !groups.contains(leafName)) {
                                    groups.add(leafName);
                                }
                            }
                        }
                    }
                }

                log.debug("Token introspection success: sub={}", subject);

                return TokenIntrospectionResult.active(subject, email, preferredUsername, groups);
            }

            log.warn("Token introspection failed: unexpected response status {}", response.getStatusCode());
            return TokenIntrospectionResult.inactive();

        } catch (Exception e) {
            log.error("Token introspection error: {}", e.getMessage());
            return TokenIntrospectionResult.error(e.getMessage());
        }
    }

    /**
     * Result of token introspection
     */
    public static class TokenIntrospectionResult {
        private final boolean active;
        private final String subject;
        private final String email;
        private final String preferredUsername;
        private final List<String> groups;
        private final String error;

        private TokenIntrospectionResult(boolean active, String subject, String email, 
                                         String preferredUsername, List<String> groups, String error) {
            this.active = active;
            this.subject = subject;
            this.email = email;
            this.preferredUsername = preferredUsername;
            this.groups = groups != null ? groups : new ArrayList<>();
            this.error = error;
        }

        public static TokenIntrospectionResult active(String subject, String email, 
                                                      String preferredUsername, List<String> groups) {
            return new TokenIntrospectionResult(true, subject, email, preferredUsername, groups, null);
        }

        public static TokenIntrospectionResult inactive() {
            return new TokenIntrospectionResult(false, null, null, null, null, null);
        }

        public static TokenIntrospectionResult error(String error) {
            return new TokenIntrospectionResult(false, null, null, null, null, error);
        }

        public boolean isActive() { return active; }
        public String getSubject() { return subject; }
        public String getEmail() { return email; }
        public String getPreferredUsername() { return preferredUsername; }
        public List<String> getGroups() { return groups; }
        public String getError() { return error; }
        public boolean hasError() { return error != null; }
    }
}