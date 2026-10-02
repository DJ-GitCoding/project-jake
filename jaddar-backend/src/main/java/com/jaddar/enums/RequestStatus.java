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

import java.util.Locale;

/**
 * Status of an RDAP request.
 *
 * <p>Ported from the Python {@code models.database.RequestStatus}. Backed by the Postgres enum
 * type {@code requeststatus}; the enum is stored as the NAME in UPPERCASE (PENDING, APPROVED, ...).
 *
 * <p>Over the wire it is serialized in LOWERCASE ("pending", "approved", ...), matching the
 * {@code str} enum values the Python backend exposed. Clients compare the status verbatim, so the
 * JSON form is part of the API contract and must not follow the Java constant name. Persistence is
 * unaffected: JPA maps via {@code @Enumerated(EnumType.STRING)} on the entity, not via Jackson.
 */
public enum RequestStatus {
    PENDING,
    APPROVED,
    DENIED,
    ERROR,
    CANCELLED;

    /** Wire form: lowercase, as the Python backend emitted. */
    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Accepts either case on the way in, so older uppercase payloads still deserialize. */
    @JsonCreator
    public static RequestStatus fromJson(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
