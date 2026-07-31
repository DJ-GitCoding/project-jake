/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Flyway callback that runs after all migrations complete but before
 * Hibernate validates the schema. Drops legacy columns that earlier
 * migrations should have removed but may still exist due to partial
 * failures or skipped migrations.
 *
 * Each check is idempotent — it queries column metadata before acting.
 */
@Component
@Slf4j
public class SchemaRepairConfig implements Callback {

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        try {
            Connection conn = context.getConnection();
            dropColumnIfExists(conn, "policy_redaction_rules", "min_access_level");
            dropColumnIfExists(conn, "policy_redaction_rules", "redaction_action");
            dropColumnIfExists(conn, "policy_redaction_rules", "replacement_value");
        } catch (Exception e) {
            log.warn("Schema repair check failed (non-fatal): {}", e.getMessage());
        }
    }

    @Override
    public String getCallbackName() {
        return "SchemaRepairCallback";
    }

    private void dropColumnIfExists(Connection conn, String table, String column) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, table, column)) {
                if (rs.next()) {
                    log.warn("Legacy column {}.{} still exists — dropping it now", table, column);
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
                    }
                    log.info("Dropped legacy column {}.{}", table, column);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to drop legacy column {}.{}: {}", table, column, e.getMessage());
        }
    }
}
