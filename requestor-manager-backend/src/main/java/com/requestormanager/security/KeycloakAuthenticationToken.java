/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Custom authentication token that holds KeycloakUser as principal
 */
public class KeycloakAuthenticationToken extends AbstractAuthenticationToken {

    private final KeycloakAuthService.KeycloakUser principal;
    private final Object credentials;

    public KeycloakAuthenticationToken(
            KeycloakAuthService.KeycloakUser principal,
            Object credentials,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public KeycloakAuthService.KeycloakUser getPrincipal() {
        return principal;
    }

    /**
     * Get the KeycloakUser directly (convenience method)
     */
    public KeycloakAuthService.KeycloakUser getKeycloakUser() {
        return principal;
    }
}