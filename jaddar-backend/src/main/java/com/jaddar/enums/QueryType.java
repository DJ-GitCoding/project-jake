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
 * RDAP query type. The wire value is the lowercase string (mirrors the Python
 * str Enum: domain, ip, asn, entity, nameserver).
 */
public enum QueryType {
    DOMAIN("domain"),
    IP("ip"),
    ASN("asn"),
    ENTITY("entity"),
    NAMESERVER("nameserver");

    private final String value;

    QueryType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static QueryType fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        for (QueryType qt : values()) {
            if (qt.value.equalsIgnoreCase(raw) || qt.name().equalsIgnoreCase(raw)) {
                return qt;
            }
        }
        throw new IllegalArgumentException("Invalid query type: " + raw);
    }
}
