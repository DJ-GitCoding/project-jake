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

import java.util.List;

/** Drops the old single column unique constraint on data_holder_groups.code. */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(0)
public class DataHolderGroupCodeConstraintFix implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            List<String> stale = jdbcTemplate.queryForList(
                    "SELECT con.conname FROM pg_constraint con "
                    + "JOIN pg_class rel ON rel.oid = con.conrelid "
                    + "WHERE rel.relname = 'data_holder_groups' AND con.contype = 'u' "
                    + "  AND array_length(con.conkey, 1) = 1 "
                    + "  AND con.conkey[1] = (SELECT attnum FROM pg_attribute "
                    + "                       WHERE attrelid = rel.oid AND attname = 'code')",
                    String.class);

            for (String name : stale) {
                jdbcTemplate.execute("ALTER TABLE data_holder_groups DROP CONSTRAINT \"" + name + "\"");
                log.info("Dropped the single column unique constraint {} on data_holder_groups.code; "
                        + "uniqueness is now (code, source)", name);
            }
        } catch (Exception e) {
            log.warn("Could not adjust the data_holder_groups code constraint: {}", e.getMessage());
        }
    }
}
