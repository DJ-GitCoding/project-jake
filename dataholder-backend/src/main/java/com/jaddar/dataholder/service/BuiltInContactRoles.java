/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The fixed set of built-in RDAP contact roles and the keyword matching used to
 * resolve an import group name onto one of them. Custom roles (managed by admins)
 * extend this set but may not shadow a built-in.
 */
public final class BuiltInContactRoles {

    private BuiltInContactRoles() {}

    /** Ordered [group-name keyword, canonical role key] pairs. Order matters for matching. */
    private static final List<String[]> KEYWORD_TO_KEY = List.of(
            new String[]{"registrant", "registrant"},
            new String[]{"admin",      "administrative"},
            new String[]{"tech",       "technical"},
            new String[]{"billing",    "billing"},
            new String[]{"abuse",      "abuse"}
    );

    /** Canonical built-in role keys used as RDAP field-path prefixes. */
    public static final Set<String> ROLE_KEYS = Set.of(
            "registrant", "administrative", "technical", "billing", "abuse");

    /**
     * Resolves a lower-cased group name to a built-in role key by keyword match,
     * e.g. "admin contact" → "administrative". Empty if it matches no built-in.
     */
    public static Optional<String> resolve(String groupNameLower) {
        for (String[] kv : KEYWORD_TO_KEY) {
            if (groupNameLower.contains(kv[0])) return Optional.of(kv[1]);
        }
        return Optional.empty();
    }

    /** True if the given role key is one of the built-ins (case-insensitive). */
    public static boolean isBuiltInKey(String roleKey) {
        return roleKey != null && ROLE_KEYS.contains(roleKey.trim().toLowerCase());
    }

    /**
     * True if the given display name would resolve to (or collide with) a built-in role,
     * e.g. "Administrator" contains "admin". Used to reject shadowing custom roles.
     */
    public static boolean collidesWithBuiltIn(String displayName) {
        if (displayName == null) return false;
        String lower = displayName.trim().toLowerCase();
        return ROLE_KEYS.contains(lower) || resolve(lower).isPresent();
    }
}
