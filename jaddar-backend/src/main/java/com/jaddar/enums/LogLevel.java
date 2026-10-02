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
 * Log severity levels.
 *
 * <p>Wire values mirror the Python {@code models.logging.LogLevel} string-enum
 * (lowercase): debug, info, warning, error, critical.
 */
public enum LogLevel {
    DEBUG("debug"),
    INFO("info"),
    WARNING("warning"),
    ERROR("error"),
    CRITICAL("critical");

    private final String value;

    LogLevel(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static LogLevel fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (LogLevel level : values()) {
            if (level.value.equalsIgnoreCase(value) || level.name().equalsIgnoreCase(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown LogLevel: " + value);
    }
}
