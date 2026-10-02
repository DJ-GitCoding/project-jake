/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time data migration: the requestor's name was split from a single {@code requestor_full_name}
 * column into {@code requestor_first_name} / {@code requestor_last_name}. Hibernate ({@code ddl-auto:
 * update}) adds the new columns but does not migrate data, so this runner backfills existing rows by
 * splitting the legacy value on the first space (first token → first name, remainder → last name).
 *
 * <p>Idempotent and self-disabling: it only runs while the legacy column still exists and only
 * touches rows whose first name is still blank. Once every row is backfilled it becomes a no-op; the
 * legacy column can be dropped manually afterwards (ddl-auto never drops it).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(0)
public class RequestorNameBackfill implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            Integer legacyColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_name = 'subscription_requests' AND column_name = 'requestor_full_name'",
                    Integer.class);
            if (legacyColumn == null || legacyColumn == 0) {
                return; // Legacy column gone → nothing to backfill.
            }

            int updated = jdbcTemplate.update(
                    "UPDATE subscription_requests " +
                    "SET requestor_first_name = split_part(trim(requestor_full_name), ' ', 1), " +
                    "    requestor_last_name = CASE " +
                    "        WHEN position(' ' in trim(requestor_full_name)) > 0 " +
                    "        THEN trim(substring(trim(requestor_full_name) from position(' ' in trim(requestor_full_name)) + 1)) " +
                    "        ELSE '' END " +
                    "WHERE (requestor_first_name IS NULL OR requestor_first_name = '') " +
                    "  AND requestor_full_name IS NOT NULL AND trim(requestor_full_name) <> ''");

            if (updated > 0) {
                log.info("Backfilled requestor first/last name for {} subscription request(s) from the legacy full-name column", updated);
            }

            // Drop the now-redundant legacy column. It must go: its old NOT NULL constraint would
            // otherwise reject every new row (which only sets first/last), and ddl-auto never drops it.
            jdbcTemplate.execute("ALTER TABLE subscription_requests DROP COLUMN IF EXISTS requestor_full_name");
            log.info("Dropped legacy requestor_full_name column from subscription_requests");
        } catch (Exception e) {
            // Never block startup on a best-effort backfill; the next boot retries.
            log.warn("Requestor name backfill skipped: {}", e.getMessage());
        }
    }
}
