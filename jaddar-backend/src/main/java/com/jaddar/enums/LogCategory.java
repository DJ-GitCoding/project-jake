/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Log categories used for filtering.
 *
 * <p>Wire values mirror the Python {@code models.logging.LogCategory} string-enum
 * (lowercase): auth, introspection, rdap, system, admin, api.
 */
public enum LogCategory {
    AUTH("auth"),
    INTROSPECTION("introspection"),
    RDAP("rdap"),
    SYSTEM("system"),
    ADMIN("admin"),
    API("api");

    private final String value;

    LogCategory(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static LogCategory fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (LogCategory category : values()) {
            if (category.value.equalsIgnoreCase(value) || category.name().equalsIgnoreCase(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown LogCategory: " + value);
    }
}
