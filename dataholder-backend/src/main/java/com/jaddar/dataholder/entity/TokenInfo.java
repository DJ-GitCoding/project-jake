/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Holds information extracted from an introspected OAuth2/OIDC token.
 * Used by TokenIntrospectionService to pass user/client info to services.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenInfo {

    /**
     * Whether the token is currently active/valid
     */
    private boolean active;

    /**
     * Subject - the unique identifier for the user (Keycloak user ID)
     */
    private String sub;

    /**
     * Username (preferred_username claim)
     */
    private String username;

    /**
     * User's email address
     */
    private String email;

    /**
     * User's full name
     */
    private String name;

    /**
     * Client ID that requested the token
     */
    private String clientId;

    /**
     * Token scope (space-separated string)
     */
    private String scope;

    /**
     * Token type (e.g., "Bearer")
     */
    private String tokenType;

    /**
     * Token expiration timestamp (Unix epoch seconds)
     */
    private Long exp;

    /**
     * Token issued-at timestamp (Unix epoch seconds)
     */
    private Long iat;

    /**
     * Groups/roles the user belongs to (from Keycloak)
     */
    private List<String> groups;

    /**
     * Realm roles from Keycloak
     */
    private List<String> realmRoles;

    /**
     * Resource/client roles from Keycloak
     */
    private List<String> resourceRoles;

    /**
     * The issuer of the token (iss claim)
     */
    private String issuer;

    /**
     * The audience of the token (aud claim)
     */
    private List<String> audience;

    /**
     * Check if token is expired
     */
    public boolean isExpired() {
        if (exp == null) return false;
        return System.currentTimeMillis() / 1000 > exp;
    }

    /**
     * Check if user has a specific group
     */
    public boolean hasGroup(String group) {
        return groups != null && groups.contains(group);
    }

    /**
     * Check if user has a specific realm role
     */
    public boolean hasRealmRole(String role) {
        return realmRoles != null && realmRoles.contains(role);
    }

    /**
     * Check if user has any of the specified groups
     */
    public boolean hasAnyGroup(List<String> targetGroups) {
        if (groups == null || targetGroups == null) return false;
        return groups.stream().anyMatch(targetGroups::contains);
    }
}