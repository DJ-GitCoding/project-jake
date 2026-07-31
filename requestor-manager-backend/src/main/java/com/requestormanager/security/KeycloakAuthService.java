/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.security;

import com.requestormanager.enums.UserType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
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
 * Service to validate and decode Keycloak tokens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAuthService {

    @Value("${keycloak.introspect-url:http://keycloak:8080/realms/master/protocol/openid-connect/token/introspect}")
    private String introspectUrl;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Validate a token via Keycloak introspection endpoint.
     */
    public KeycloakUser validateToken(String token) {
        try {
            if (token == null || token.isEmpty()) {
                return null;
            }

            // Remove "Bearer " prefix if present
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(clientId, clientSecret);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", token);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(introspectUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                
                boolean active = json.has("active") && json.get("active").asBoolean();
                if (!active) {
                    log.debug("Token is not active");
                    return null;
                }

                return parseKeycloakUser(json, true);
            }

            return null;
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Decode a JWT token without validation (for getting user info after Keycloak validates).
     * This is used after we get the token from Keycloak's token endpoint.
     */
    public KeycloakUser decodeToken(String token) {
        try {
            if (token == null || token.isEmpty()) {
                return null;
            }

            // Remove "Bearer " prefix if present
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            // JWT has 3 parts: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.error("Invalid JWT format");
                return null;
            }

            // Decode the payload (middle part)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode json = objectMapper.readTree(payload);

            return parseKeycloakUser(json, true);

        } catch (Exception e) {
            log.error("Token decode failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse Keycloak user from JSON claims.
     */
    private KeycloakUser parseKeycloakUser(JsonNode json, boolean active) {
        KeycloakUser user = new KeycloakUser();
        user.setActive(active);
        user.setSub(getTextValue(json, "sub"));
        user.setEmail(getTextValue(json, "email"));
        user.setUsername(getTextValue(json, "preferred_username"));
        user.setFirstName(getTextValue(json, "given_name"));
        user.setLastName(getTextValue(json, "family_name"));

        // Parse groups
        List<String> groups = new ArrayList<>();
        if (json.has("groups")) {
            JsonNode groupsNode = json.get("groups");
            if (groupsNode.isArray()) {
                for (JsonNode group : groupsNode) {
                    String groupName = group.asText();
                    // Remove leading slash if present
                    if (groupName.startsWith("/")) {
                        groupName = groupName.substring(1);
                    }
                    groups.add(groupName);
                }
            }
        }
        user.setGroups(groups);

        // Parse realm roles from realm_access.roles
        List<String> realmRoles = new ArrayList<>();
        if (json.has("realm_access")) {
            JsonNode realmAccess = json.get("realm_access");
            if (realmAccess.has("roles")) {
                JsonNode rolesNode = realmAccess.get("roles");
                if (rolesNode.isArray()) {
                    for (JsonNode role : rolesNode) {
                        realmRoles.add(role.asText());
                    }
                }
            }
        }
        user.setRealmRoles(realmRoles);

        // Determine user type from roles using the enum's fromKeycloakRoles method
        user.setUserType(UserType.fromKeycloakRoles(realmRoles));

        log.debug("Parsed Keycloak user: sub={}, email={}, groups={}, roles={}, userType={}",
                user.getSub(), user.getEmail(), user.getGroups(), user.getRealmRoles(), user.getUserType());

        return user;
    }

    private String getTextValue(JsonNode json, String field) {
        return json.has(field) ? json.get(field).asText() : null;
    }

    /**
     * Keycloak user representation.
     */
    @Data
    public static class KeycloakUser {
        private boolean active;
        private String sub;
        private String email;
        private String username;
        private String firstName;
        private String lastName;
        private List<String> groups = new ArrayList<>();
        private List<String> realmRoles = new ArrayList<>();
        private UserType userType = UserType.REQUESTOR_GROUP_USER;

        public String getDisplayName() {
            if (firstName != null && lastName != null) {
                return firstName + " " + lastName;
            }
            if (firstName != null) return firstName;
            if (lastName != null) return lastName;
            if (username != null) return username;
            return email;
        }

        /**
         * Get Spring Security authorities from Keycloak roles.
         */
        public List<org.springframework.security.core.authority.SimpleGrantedAuthority> getAuthorities() {
            List<org.springframework.security.core.authority.SimpleGrantedAuthority> authorities = new ArrayList<>();
            
            // Add userType as authority (both with and without ROLE_ prefix)
            String userTypeName = userType.name().toLowerCase();
            authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(userTypeName));
            authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + userTypeName));
            
            // Add all realm roles as authorities (both with and without ROLE_ prefix)
            for (String role : realmRoles) {
                // Add original role name
                authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(role));
                // Add with ROLE_ prefix
                authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
            }
            
            return authorities;
        }
    }
}