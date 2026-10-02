/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.security;

import com.jaddar.config.JaddarProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Spring component wrapper around {@link KeycloakAuthUtil} that injects {@link JaddarProperties} so
 * controllers can call {@code isAdmin(jwt)} / {@code currentUser(jwt)} without threading the admin
 * email through every call.
 *
 * <p>Also seeds {@link KeycloakAuthUtil}'s static admin-email value at startup, so the single-arg
 * static overloads work for all modules.
 */
@Component
public class AuthFacade {

    private final JaddarProperties props;

    public AuthFacade(JaddarProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        KeycloakAuthUtil.setConfiguredAdminEmail(props.getAdminEmail());
    }

    public boolean isAdmin(Jwt jwt) {
        return KeycloakAuthUtil.isAdmin(jwt, props.getAdminEmail());
    }

    public Map<String, Object> currentUser(Jwt jwt) {
        return KeycloakAuthUtil.currentUser(jwt, props.getAdminEmail());
    }
}
