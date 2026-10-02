/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.entity;

/** How far a data holder group, or one of its agreement templates, is advertised. */
public enum Visibility {
    PRIVATE,
    PUBLIC,
    GLOBAL;

    /** Whether this level is at least as open as other. */
    public boolean atLeast(Visibility other) {
        return this.ordinal() >= other.ordinal();
    }

    /** The more restrictive of two levels; used to clamp a template to its group. */
    public static Visibility min(Visibility a, Visibility b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    /** Parses a client-supplied value, falling back to fallback when unusable. */
    public static Visibility parse(String value, Visibility fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
