/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time data migration: the requestor's name was split from a single {@code requestor_full_name}
 * column into {@code requestor_first_name} / {@code requestor_last_name}. This column is
 * Hibernate-managed (not owned by Flyway), and Flyway runs before Hibernate here, so the split is
 * done in a {@link CommandLineRunner} — which runs after the context (and Hibernate's ddl-auto) is
 * fully initialized and the new columns exist — rather than in a Flyway migration.
 *
 * <p>Splits the legacy value on the first space (first token → first name, remainder → last name).
 * Idempotent and self-disabling: only runs while the legacy column exists and only touches rows
 * whose first name is still blank.
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
                    "WHERE table_name = 'agreement_subscriptions' AND column_name = 'requestor_full_name'",
                    Integer.class);
            if (legacyColumn == null || legacyColumn == 0) {
                return; // Legacy column gone → nothing to backfill.
            }

            int updated = jdbcTemplate.update(
                    "UPDATE agreement_subscriptions " +
                    "SET requestor_first_name = split_part(trim(requestor_full_name), ' ', 1), " +
                    "    requestor_last_name = CASE " +
                    "        WHEN position(' ' in trim(requestor_full_name)) > 0 " +
                    "        THEN trim(substring(trim(requestor_full_name) from position(' ' in trim(requestor_full_name)) + 1)) " +
                    "        ELSE '' END " +
                    "WHERE (requestor_first_name IS NULL OR requestor_first_name = '') " +
                    "  AND requestor_full_name IS NOT NULL AND trim(requestor_full_name) <> ''");

            if (updated > 0) {
                log.info("Backfilled requestor first/last name for {} agreement subscription(s) from the legacy full-name column", updated);
            }

            // Drop the now-redundant legacy column so it can't shadow inserts (and to complete the
            // full replacement). ddl-auto never drops it.
            jdbcTemplate.execute("ALTER TABLE agreement_subscriptions DROP COLUMN IF EXISTS requestor_full_name");
            log.info("Dropped legacy requestor_full_name column from agreement_subscriptions");
        } catch (Exception e) {
            // Never block startup on a best-effort backfill; the next boot retries.
            log.warn("Requestor name backfill skipped: {}", e.getMessage());
        }
    }
}
