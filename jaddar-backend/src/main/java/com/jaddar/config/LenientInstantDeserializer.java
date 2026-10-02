/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Tolerant {@link Instant} deserializer.
 *
 * <p>The other JADDAR services (Requestor Manager, Data Holder, …) are Spring apps that serialize
 * {@code LocalDateTime} as a zone-less ISO string (e.g. {@code 2026-06-08T21:29:31.662254}). The
 * strict Jackson {@code InstantDeserializer} rejects those because they carry no offset. The
 * original FastAPI service parsed them leniently (pydantic), so we match that: accept epoch
 * seconds/millis, full instants, offset date-times, zone-less local date-times (assumed UTC), and
 * bare dates.
 */
public class LenientInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String s = p.getValueAsString();
        if (s == null || s.isBlank()) {
            return null;
        }
        s = s.trim();

        // Numeric epoch (seconds or milliseconds)
        if (s.matches("-?\\d+")) {
            long v = Long.parseLong(s);
            return Math.abs(v) >= 1_000_000_000_000L ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
        }

        try {
            return Instant.parse(s);                                  // 2026-06-08T21:29:31Z / with offset
        } catch (DateTimeParseException ignored) { /* fall through */ }
        try {
            return OffsetDateTime.parse(s).toInstant();               // 2026-06-08T21:29:31+08:00
        } catch (DateTimeParseException ignored) { /* fall through */ }
        try {
            return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC);  // 2026-06-08T21:29:31.662254 (no zone)
        } catch (DateTimeParseException ignored) { /* fall through */ }
        try {
            return LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant(); // 2026-06-08
        } catch (DateTimeParseException ignored) { /* fall through */ }

        throw new IOException("Cannot parse Instant from value: " + s);
    }
}
