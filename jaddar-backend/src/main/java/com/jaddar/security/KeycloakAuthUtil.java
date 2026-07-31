/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static helpers for reading Keycloak access-token claims off a Spring {@link Jwt}.
 *
 * <p>Ported from {@code backend/dependencies/admin_auth.py} (extract_roles / is_admin_user) and
 * {@code main.py}'s userinfo handling.
 *
 * <h2>Config-free vs. config-dependent helpers</h2>
 * Most helpers (sub/email/name/preferredUsername/roles) need no configuration and are pure static
 * functions. {@link #isAdmin(Jwt, String)} requires the configured admin email, which callers must
 * pass in — there is also a config-free {@link #isAdminByRoleOrUsername(Jwt)} for the role/username
 * portion only. For controllers, prefer the {@link AuthFacade} Spring component which injects
 * {@code JaddarProperties} and exposes {@code isAdmin(Jwt)} / {@code currentUser(Jwt)} without
 * threading the admin email through every call site.
 *
 * <p>The single-argument {@link #isAdmin(Jwt)} / {@link #currentUser(Jwt)} overloads (the API other
 * modules reference per the migration contract) read a static admin-email value injected once at
 * startup by {@link AuthFacade}; they are safe to call from any module without wiring.
 */
public final class KeycloakAuthUtil {

    /** Admin roles, case-insensitively matched (mirrors Python ADMIN_ROLES). */
    private static final List<String> ADMIN_ROLES = List.of("admin", "realm-admin", "manage-users", "view-users");

    /** Injected once at startup by {@link AuthFacade} from {@code jaddar.admin-email}. */
    private static volatile String configuredAdminEmail;

    private KeycloakAuthUtil() {
    }

    /** Set the configured admin email (called once by {@link AuthFacade} at startup). */
    static void setConfiguredAdminEmail(String adminEmail) {
        configuredAdminEmail = adminEmail;
    }

    /** {@code sub} claim. */
    public static String sub(Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("sub");
    }

    /** {@code email} claim. */
    public static String email(Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("email");
    }

    /** {@code name}, falling back to {@code preferred_username}. */
    public static String name(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        String name = jwt.getClaimAsString("name");
        return (name != null && !name.isEmpty()) ? name : jwt.getClaimAsString("preferred_username");
    }

    /** {@code preferred_username} claim. */
    public static String preferredUsername(Jwt jwt) {
        return jwt == null ? null : jwt.getClaimAsString("preferred_username");
    }

    public static boolean emailVerified(Jwt jwt) {
        return jwt != null && Boolean.TRUE.equals(jwt.getClaim("email_verified"));
    }

    /**
     * Union of realm roles ({@code realm_access.roles}), all client roles
     * ({@code resource_access.*.roles}), plus "admin" if any {@code groups} entry contains "admin"
     * (case-insensitive). De-duplicated, insertion order preserved.
     */
    @SuppressWarnings("unchecked")
    public static List<String> roles(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();
        if (jwt == null) {
            return new ArrayList<>(roles);
        }

        // Realm roles.
        Object realmAccessObj = jwt.getClaim("realm_access");
        if (realmAccessObj instanceof Map<?, ?> realmAccess) {
            Object realmRoles = realmAccess.get("roles");
            if (realmRoles instanceof List<?> list) {
                for (Object r : list) {
                    if (r != null) {
                        roles.add(String.valueOf(r));
                    }
                }
            }
        }

        // Resource/client roles.
        Object resourceAccessObj = jwt.getClaim("resource_access");
        if (resourceAccessObj instanceof Map<?, ?> resourceAccess) {
            for (Object accessObj : resourceAccess.values()) {
                if (accessObj instanceof Map<?, ?> access) {
                    Object clientRoles = access.get("roles");
                    if (clientRoles instanceof List<?> list) {
                        for (Object r : list) {
                            if (r != null) {
                                roles.add(String.valueOf(r));
                            }
                        }
                    }
                }
            }
        }

        Object groupsObj = jwt.getClaim("groups");
        if (groupsObj instanceof List<?> groups) {
            for (Object g : groups) {
                if (g == null) {
                    continue;
                }
                String group = String.valueOf(g);
                String leaf = group.substring(group.lastIndexOf('/') + 1).trim();
                if (leaf.equalsIgnoreCase("admin")) {
                    roles.add("admin");
                }
            }
        }

        return new ArrayList<>(roles);
    }

    /**
     * Admin check requiring the configured admin email. True if:
     * <ul>
     *   <li>{@code email} equals {@code adminEmail} (case-insensitive), OR</li>
     *   <li>a role is one of {admin, realm-admin, manage-users, view-users} (case-insensitive), OR</li>
     *   <li>{@code preferred_username} equals "admin" (case-insensitive).</li>
     * </ul>
     */
    public static boolean isAdmin(Jwt jwt, String adminEmail) {
        if (jwt == null) {
            return false;
        }
        String email = email(jwt);
        if (email != null && adminEmail != null && email.equalsIgnoreCase(adminEmail) && emailVerified(jwt)) {
            return true;
        }
        return isAdminByRoleOrUsername(jwt);
    }

    /** Config-free portion of the admin check (roles + preferred_username only). */
    public static boolean isAdminByRoleOrUsername(Jwt jwt) {
        if (jwt == null) {
            return false;
        }
        for (String role : roles(jwt)) {
            for (String adminRole : ADMIN_ROLES) {
                if (role.equalsIgnoreCase(adminRole)) {
                    return true;
                }
            }
        }
        String username = preferredUsername(jwt);
        return username != null && username.equalsIgnoreCase("admin");
    }

    /**
     * User summary map: {@code {sub, email, name, preferred_username, roles, is_admin}}.
     * Mirrors Python {@code get_current_user}. {@code is_admin} uses {@code adminEmail}.
     */
    public static Map<String, Object> currentUser(Jwt jwt, String adminEmail) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sub", sub(jwt));
        out.put("email", email(jwt));
        out.put("name", name(jwt));
        out.put("preferred_username", preferredUsername(jwt));
        out.put("roles", roles(jwt));
        out.put("is_admin", isAdmin(jwt, adminEmail));
        return out;
    }

    /**
     * Config-free admin check using the statically-injected admin email (the API referenced by other
     * modules per the migration contract). Equivalent to {@code isAdmin(jwt, configuredAdminEmail)}.
     */
    public static boolean isAdmin(Jwt jwt) {
        return isAdmin(jwt, configuredAdminEmail);
    }

    /** User summary map using the statically-injected admin email. See {@link #currentUser(Jwt, String)}. */
    public static Map<String, Object> currentUser(Jwt jwt) {
        return currentUser(jwt, configuredAdminEmail);
    }
}
