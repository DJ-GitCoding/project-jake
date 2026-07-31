/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.entity.RdapDataMapping;
import com.jaddar.dataholder.repository.RdapDataMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service that handles connecting to external databases, introspecting their
 * schema (tables + columns), running test queries, and executing dynamic
 * mapped queries at runtime.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalDatabaseService {

    private final DataSource localDataSource;
    private final RdapDataMappingRepository mappingRepository;

    // ==================== CONNECTION TESTING ====================

    private static final java.util.List<String> ALLOWED_JDBC_SCHEMES =
        java.util.List.of("jdbc:postgresql:", "jdbc:mysql:", "jdbc:mariadb:");
    private static final java.util.List<String> ALLOWED_DRIVERS =
        java.util.List.of("org.postgresql.Driver", "com.mysql.cj.jdbc.Driver", "org.mariadb.jdbc.Driver");
    /* JDBC params that enable RCE/SSRF (H2 RUNSCRIPT/INIT, PG socketFactory, MySQL local-infile, JDBC deserialization). */
    private static final java.util.List<String> FORBIDDEN_JDBC_TOKENS =
        java.util.List.of("runscript", "init=", "socketfactory", "autodeserialize",
            "allowloadlocalinfile", "allowurlinlocalinfile", "queryinterceptors", "statementinterceptors");

    /*
     * Validate a caller-supplied JDBC target before opening it. Allowlist the driver and URL scheme
     * and reject dangerous JDBC params (H2 RUNSCRIPT/INIT, PG socketFactory, MySQL local-infile,
     * JDBC deserialization).
     */
    private void validateExternalDbTarget(String jdbcUrl, String driver) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("A database URL is required.");
        }
        String lower = jdbcUrl.toLowerCase();
        if (ALLOWED_JDBC_SCHEMES.stream().noneMatch(lower::startsWith)) {
            throw new IllegalArgumentException("Only PostgreSQL, MySQL, and MariaDB database URLs are allowed.");
        }
        for (String bad : FORBIDDEN_JDBC_TOKENS) {
            if (lower.contains(bad)) {
                throw new IllegalArgumentException("The database URL contains a disallowed parameter.");
            }
        }
        if (driver != null && !driver.isBlank() && !ALLOWED_DRIVERS.contains(driver)) {
            throw new IllegalArgumentException("The specified database driver is not allowed.");
        }
    }

    /**
     * Test connectivity to an external database given raw connection details.
     * Does NOT require a saved mapping — used by the UI before the user saves.
     */
    public Map<String, Object> testConnection(String jdbcUrl, String username, String password, String driver) {
        validateExternalDbTarget(jdbcUrl, driver);
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if (driver != null && !driver.isBlank()) {
                Class.forName(driver);
            }
            try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                DatabaseMetaData meta = conn.getMetaData();
                result.put("success", true);
                result.put("productName", meta.getDatabaseProductName());
                result.put("productVersion", meta.getDatabaseProductVersion());
                result.put("driverName", meta.getDriverName());
                result.put("catalog", conn.getCatalog());
                result.put("schema", conn.getSchema());
            }
        } catch (Exception e) {
            log.warn("External DB connection test failed", e);
            result.put("success", false);
            result.put("error", "Could not connect to the external database — please verify the connection details");
        }
        return result;
    }

    // ==================== SCHEMA INTROSPECTION ====================

    /**
     * Introspect an external database, returning {"tables": [{name, columns:[{name,type,nullable}], ...}]}
     * plus primary keys, indexes, row counts, sample rows, and FK relationships.
     */
    public Map<String, Object> introspectSchema(String jdbcUrl, String username, String password,
                                                 String driver, String schemaFilter) {
        validateExternalDbTarget(jdbcUrl, driver);
        Map<String, Object> schema = new LinkedHashMap<>();
        List<Map<String, Object>> tables = new ArrayList<>();

        try {
            if (driver != null && !driver.isBlank()) {
                Class.forName(driver);
            }
            try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                DatabaseMetaData meta = conn.getMetaData();
                String catalog = conn.getCatalog();
                String schemaName = (schemaFilter != null && !schemaFilter.isBlank()) ? schemaFilter : conn.getSchema();

                // Get all tables
                try (ResultSet rs = meta.getTables(catalog, schemaName, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        // Skip system/internal tables
                        if (tableName.startsWith("pg_") || tableName.startsWith("sql_") ||
                            tableName.equals("information_schema") || tableName.startsWith("flyway_")) {
                            continue;
                        }

                        Map<String, Object> table = new LinkedHashMap<>();
                        table.put("name", tableName);

                        // Primary key columns (real DB metadata, ordered by key sequence)
                        java.util.TreeMap<Short, String> pkOrdered = new java.util.TreeMap<>();
                        try (ResultSet pkRs = meta.getPrimaryKeys(catalog, schemaName, tableName)) {
                            while (pkRs.next()) {
                                pkOrdered.put(pkRs.getShort("KEY_SEQ"), pkRs.getString("COLUMN_NAME"));
                            }
                        } catch (Exception e) {
                            log.debug("PK discovery failed for table {}: {}", tableName, e.getMessage());
                        }
                        java.util.Set<String> pkColumns = new java.util.LinkedHashSet<>(pkOrdered.values());

                        // Get columns for this table
                        List<Map<String, Object>> columns = new ArrayList<>();
                        try (ResultSet colRs = meta.getColumns(catalog, schemaName, tableName, "%")) {
                            while (colRs.next()) {
                                Map<String, Object> col = new LinkedHashMap<>();
                                String colName = colRs.getString("COLUMN_NAME");
                                col.put("name", colName);
                                col.put("primaryKey", pkColumns.contains(colName));
                                col.put("type", colRs.getString("TYPE_NAME"));
                                int size = colRs.getInt("COLUMN_SIZE");
                                if (size > 0) {
                                    col.put("size", size);
                                    col.put("typeDisplay", colRs.getString("TYPE_NAME") + "(" + size + ")");
                                } else {
                                    col.put("typeDisplay", colRs.getString("TYPE_NAME"));
                                }
                                col.put("nullable", colRs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                                col.put("ordinal", colRs.getInt("ORDINAL_POSITION"));
                                columns.add(col);
                            }
                        }
                        table.put("columns", columns);
                        table.put("primaryKeys", new ArrayList<>(pkColumns));

                        // Indexes (name, unique, columns) — part of the architectural scan
                        List<Map<String, Object>> indexes = new ArrayList<>();
                        try (ResultSet ixRs = meta.getIndexInfo(catalog, schemaName, tableName, false, true)) {
                            Map<String, Map<String, Object>> ixByName = new LinkedHashMap<>();
                            while (ixRs.next()) {
                                String ixName = ixRs.getString("INDEX_NAME");
                                if (ixName == null) continue; // tableIndexStatistic rows
                                Map<String, Object> ix = ixByName.get(ixName);
                                if (ix == null) {
                                    ix = new LinkedHashMap<>();
                                    ix.put("name", ixName);
                                    ix.put("unique", !ixRs.getBoolean("NON_UNIQUE"));
                                    ix.put("columns", new ArrayList<String>());
                                    ixByName.put(ixName, ix);
                                }
                                @SuppressWarnings("unchecked")
                                List<String> ixCols = (List<String>) ix.get("columns");
                                String ixCol = ixRs.getString("COLUMN_NAME");
                                if (ixCol != null) ixCols.add(ixCol);
                            }
                            indexes.addAll(ixByName.values());
                        } catch (Exception e) {
                            log.debug("Index discovery failed for table {}: {}", tableName, e.getMessage());
                        }
                        if (!indexes.isEmpty()) {
                            table.put("indexes", indexes);
                        }

                        // Get row count estimate
                        try (Statement stmt = conn.createStatement();
                             ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + quoteIdentifier(conn, tableName))) {
                            if (countRs.next()) {
                                table.put("rowCount", countRs.getLong(1));
                            }
                        } catch (Exception e) {
                            table.put("rowCount", -1);
                        }

                        // Get sample data (first 3 rows)
                        try (Statement stmt = conn.createStatement();
                             ResultSet sampleRs = stmt.executeQuery(
                                 "SELECT * FROM " + quoteIdentifier(conn, tableName) + " LIMIT 3")) {
                            ResultSetMetaData rsMeta = sampleRs.getMetaData();
                            List<List<String>> sampleRows = new ArrayList<>();
                            while (sampleRs.next()) {
                                List<String> row = new ArrayList<>();
                                for (int i = 1; i <= rsMeta.getColumnCount(); i++) {
                                    String val = sampleRs.getString(i);
                                    row.add(val != null ? (val.length() > 80 ? val.substring(0, 80) + "..." : val) : null);
                                }
                                sampleRows.add(row);
                            }
                            table.put("sampleData", sampleRows);
                        } catch (Exception e) {
                            table.put("sampleData", List.of());
                        }

                        // Discover foreign key relationships
                        List<Map<String, Object>> foreignKeys = new ArrayList<>();
                        try (ResultSet fkRs = meta.getImportedKeys(catalog, schemaName, tableName)) {
                            while (fkRs.next()) {
                                Map<String, Object> fk = new LinkedHashMap<>();
                                fk.put("fkColumn", fkRs.getString("FKCOLUMN_NAME"));
                                fk.put("pkTable", fkRs.getString("PKTABLE_NAME"));
                                fk.put("pkColumn", fkRs.getString("PKCOLUMN_NAME"));
                                fk.put("fkName", fkRs.getString("FK_NAME"));
                                foreignKeys.add(fk);
                            }
                        } catch (Exception e) {
                            // FK discovery not supported or failed — not critical
                            log.debug("FK discovery failed for table {}: {}", tableName, e.getMessage());
                        }
                        if (!foreignKeys.isEmpty()) {
                            table.put("foreignKeys", foreignKeys);
                        }

                        // Discover tables that reference this table (exported/reverse FKs)
                        List<Map<String, Object>> referencedBy = new ArrayList<>();
                        try (ResultSet ekRs = meta.getExportedKeys(catalog, schemaName, tableName)) {
                            while (ekRs.next()) {
                                Map<String, Object> ref = new LinkedHashMap<>();
                                ref.put("fkTable", ekRs.getString("FKTABLE_NAME"));
                                ref.put("fkColumn", ekRs.getString("FKCOLUMN_NAME"));
                                ref.put("pkColumn", ekRs.getString("PKCOLUMN_NAME"));
                                ref.put("fkName", ekRs.getString("FK_NAME"));
                                referencedBy.add(ref);
                            }
                        } catch (Exception e) {
                            log.debug("Exported key discovery failed for table {}: {}", tableName, e.getMessage());
                        }
                        if (!referencedBy.isEmpty()) {
                            table.put("referencedBy", referencedBy);
                        }

                        tables.add(table);
                    }
                }

                schema.put("tables", tables);
                schema.put("productName", meta.getDatabaseProductName());
                schema.put("productVersion", meta.getDatabaseProductVersion());
                schema.put("catalog", catalog);
                schema.put("schema", schemaName);
            }
        } catch (Exception e) {
            log.error("Schema introspection failed", e);
            schema.put("error", "Could not introspect the external database — please verify the connection details");
            schema.put("tables", List.of());
        }

        // Post-process: infer FK relationships from column names and indexed columns
        inferForeignKeys(tables);

        return schema;
    }

    /**
     * Introspect the LOCAL database (same datasource as the app).
     */
    public Map<String, Object> introspectLocalSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        List<Map<String, Object>> tables = new ArrayList<>();

        try (Connection conn = localDataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schemaName = conn.getSchema();
            if (schemaName == null) schemaName = "public";

            try (ResultSet rs = meta.getTables(catalog, schemaName, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (tableName.startsWith("flyway_")) continue;

                    Map<String, Object> table = new LinkedHashMap<>();
                    table.put("name", tableName);

                    // Primary key columns (real DB metadata, ordered by key sequence)
                    java.util.TreeMap<Short, String> pkOrdered = new java.util.TreeMap<>();
                    try (ResultSet pkRs = meta.getPrimaryKeys(catalog, schemaName, tableName)) {
                        while (pkRs.next()) {
                            pkOrdered.put(pkRs.getShort("KEY_SEQ"), pkRs.getString("COLUMN_NAME"));
                        }
                    } catch (Exception e) {
                        log.debug("PK discovery failed for table {}: {}", tableName, e.getMessage());
                    }
                    java.util.Set<String> pkColumns = new java.util.LinkedHashSet<>(pkOrdered.values());

                    List<Map<String, Object>> columns = new ArrayList<>();
                    try (ResultSet colRs = meta.getColumns(catalog, schemaName, tableName, "%")) {
                        while (colRs.next()) {
                            Map<String, Object> col = new LinkedHashMap<>();
                            String colName = colRs.getString("COLUMN_NAME");
                            col.put("name", colName);
                            col.put("primaryKey", pkColumns.contains(colName));
                            col.put("type", colRs.getString("TYPE_NAME"));
                            int size = colRs.getInt("COLUMN_SIZE");
                            col.put("typeDisplay", size > 0
                                ? colRs.getString("TYPE_NAME") + "(" + size + ")"
                                : colRs.getString("TYPE_NAME"));
                            col.put("nullable", colRs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                            columns.add(col);
                        }
                    }
                    table.put("columns", columns);
                    table.put("primaryKeys", new ArrayList<>(pkColumns));

                    // Indexes (name, unique, columns) — part of the architectural scan
                    List<Map<String, Object>> indexes = new ArrayList<>();
                    try (ResultSet ixRs = meta.getIndexInfo(catalog, schemaName, tableName, false, true)) {
                        Map<String, Map<String, Object>> ixByName = new LinkedHashMap<>();
                        while (ixRs.next()) {
                            String ixName = ixRs.getString("INDEX_NAME");
                            if (ixName == null) continue;
                            Map<String, Object> ix = ixByName.get(ixName);
                            if (ix == null) {
                                ix = new LinkedHashMap<>();
                                ix.put("name", ixName);
                                ix.put("unique", !ixRs.getBoolean("NON_UNIQUE"));
                                ix.put("columns", new ArrayList<String>());
                                ixByName.put(ixName, ix);
                            }
                            @SuppressWarnings("unchecked")
                            List<String> ixCols = (List<String>) ix.get("columns");
                            String ixCol = ixRs.getString("COLUMN_NAME");
                            if (ixCol != null) ixCols.add(ixCol);
                        }
                        indexes.addAll(ixByName.values());
                    } catch (Exception e) {
                        log.debug("Index discovery failed for table {}: {}", tableName, e.getMessage());
                    }
                    if (!indexes.isEmpty()) {
                        table.put("indexes", indexes);
                    }

                    // Discover foreign key relationships
                    List<Map<String, Object>> foreignKeys = new ArrayList<>();
                    try (ResultSet fkRs = meta.getImportedKeys(catalog, schemaName, tableName)) {
                        while (fkRs.next()) {
                            Map<String, Object> fk = new LinkedHashMap<>();
                            fk.put("fkColumn", fkRs.getString("FKCOLUMN_NAME"));
                            fk.put("pkTable", fkRs.getString("PKTABLE_NAME"));
                            fk.put("pkColumn", fkRs.getString("PKCOLUMN_NAME"));
                            fk.put("fkName", fkRs.getString("FK_NAME"));
                            foreignKeys.add(fk);
                        }
                    } catch (Exception e) {
                        log.debug("FK discovery failed for table {}: {}", tableName, e.getMessage());
                    }
                    if (!foreignKeys.isEmpty()) {
                        table.put("foreignKeys", foreignKeys);
                    }

                    // Discover tables that reference this table
                    List<Map<String, Object>> referencedBy = new ArrayList<>();
                    try (ResultSet ekRs = meta.getExportedKeys(catalog, schemaName, tableName)) {
                        while (ekRs.next()) {
                            Map<String, Object> ref = new LinkedHashMap<>();
                            ref.put("fkTable", ekRs.getString("FKTABLE_NAME"));
                            ref.put("fkColumn", ekRs.getString("FKCOLUMN_NAME"));
                            ref.put("pkColumn", ekRs.getString("PKCOLUMN_NAME"));
                            ref.put("fkName", ekRs.getString("FK_NAME"));
                            referencedBy.add(ref);
                        }
                    } catch (Exception e) {
                        log.debug("Exported key discovery failed for table {}: {}", tableName, e.getMessage());
                    }
                    if (!referencedBy.isEmpty()) {
                        table.put("referencedBy", referencedBy);
                    }

                    tables.add(table);
                }
            }

            schema.put("tables", tables);
            schema.put("productName", meta.getDatabaseProductName());
        } catch (Exception e) {
            log.error("Local schema introspection failed", e);
            schema.put("error", "Could not introspect the local database schema");
            schema.put("tables", List.of());
        }

        // Post-process: infer FK relationships from column names and indexed columns
        inferForeignKeys(tables);

        return schema;
    }

    /**
     * Introspect and save the schema into the mapping's discoveredSchema field.
     */
    public Map<String, Object> introspectAndSave(Long mappingId) {
        RdapDataMapping mapping = mappingRepository.findById(mappingId)
                .orElseThrow(() -> new IllegalArgumentException("Mapping not found: " + mappingId));

        Map<String, Object> schema;
        if (mapping.isExternal()) {
            schema = introspectSchema(
                mapping.getExternalJdbcUrl(),
                mapping.getExternalDbUsername(),
                mapping.getExternalDbPassword(),
                mapping.getExternalDbDriver(),
                mapping.getExternalDbSchema()
            );
        } else {
            schema = introspectLocalSchema();
        }

        mapping.setDiscoveredSchema(schema);
        mapping.setLastIntrospectedAt(LocalDateTime.now());
        mappingRepository.save(mapping);

        return schema;
    }

    // ==================== DYNAMIC QUERYING ====================

    /**
     * Execute a preview query against the mapped database to verify the mapping works.
     * Returns a few sample rows translated through the column mappings.
     */
    public Map<String, Object> previewMappedData(RdapDataMapping mapping, String objectType, int limit) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            Connection conn = getConnection(mapping);
            try {
                String tableName;
                Map<String, String> columnMappings;

                switch (objectType.toLowerCase()) {
                    case "domain" -> {
                        tableName = mapping.getEffectiveDomainsTable();
                        columnMappings = mapping.getDomainColumnMappings();
                    }
                    case "contact" -> {
                        tableName = mapping.getEffectiveContactsTable();
                        columnMappings = mapping.getContactColumnMappings();
                    }
                    case "host" -> {
                        tableName = mapping.getEffectiveHostsTable();
                        columnMappings = mapping.getHostColumnMappings();
                    }
                    case "entity" -> {
                        tableName = mapping.getEntitiesTable() != null && !mapping.getEntitiesTable().isBlank()
                                ? mapping.getEntitiesTable() : "rdap_entities";
                        columnMappings = mapping.getEntityColumnMappings();
                    }
                    case "ip" -> {
                        tableName = mapping.getIpsTable() != null && !mapping.getIpsTable().isBlank()
                                ? mapping.getIpsTable() : "rdap_ips";
                        columnMappings = mapping.getIpColumnMappings();
                    }
                    case "asn" -> {
                        tableName = mapping.getAsnsTable() != null && !mapping.getAsnsTable().isBlank()
                                ? mapping.getAsnsTable() : "rdap_asns";
                        columnMappings = mapping.getAsnColumnMappings();
                    }
                    default -> throw new IllegalArgumentException("Unknown object type: " + objectType);
                }

                // Build SELECT with mapped columns
                StringBuilder sql = new StringBuilder("SELECT * FROM ");
                sql.append(quoteIdentifier(conn, tableName));
                sql.append(" LIMIT ").append(Math.min(limit, 20));

                List<Map<String, Object>> rows = new ArrayList<>();
                List<String> rawColumns = new ArrayList<>();

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql.toString())) {
                    ResultSetMetaData meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        rawColumns.add(meta.getColumnName(i));
                    }

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        // Include both raw and mapped values
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            String colName = meta.getColumnName(i);
                            row.put(colName, rs.getObject(i));
                        }
                        rows.add(row);
                    }
                }

                // Build reverse mapping (external column -> internal field)
                Map<String, String> reverseMap = new LinkedHashMap<>();
                if (columnMappings != null) {
                    columnMappings.forEach((internalField, externalCol) ->
                        reverseMap.put(externalCol, internalField));
                }

                // Translate rows using mappings
                List<Map<String, Object>> mappedRows = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    Map<String, Object> mapped = new LinkedHashMap<>();
                    row.forEach((col, val) -> {
                        String internalField = reverseMap.getOrDefault(col, col);
                        mapped.put(internalField, val);
                    });
                    mappedRows.add(mapped);
                }

                result.put("success", true);
                result.put("table", tableName);
                result.put("rawColumns", rawColumns);
                result.put("rawRows", rows);
                result.put("mappedRows", mappedRows);
                result.put("rowCount", rows.size());
                result.put("columnMappings", columnMappings);

            } finally {
                if (mapping.isExternal()) {
                    conn.close();
                }
            }
        } catch (Exception e) {
            log.error("Preview query failed", e);
            result.put("success", false);
            result.put("error", "Could not preview data from the mapped database — please verify the mapping and connection details");
        }

        return result;
    }

    // ==================== AUTO-SUGGEST MAPPINGS ====================

    /**
     * Given discovered external columns and our internal field definitions,
     * suggests likely mappings using name similarity heuristics.
     */
    public Map<String, String> suggestColumnMappings(List<String> externalColumns, String objectType) {
        Map<String, String> suggestions = new LinkedHashMap<>();

        // Define known mapping hints per object type
        Map<String, List<String>> hints = getHintsForType(objectType);

        for (Map.Entry<String, List<String>> entry : hints.entrySet()) {
            String internalField = entry.getKey();
            List<String> patterns = entry.getValue();

            for (String extCol : externalColumns) {
                String normalized = extCol.toLowerCase().replaceAll("[_\\-\\s]", "");
                for (String pattern : patterns) {
                    if (normalized.equals(pattern) || normalized.contains(pattern) || pattern.contains(normalized)) {
                        suggestions.put(internalField, extCol);
                        break;
                    }
                }
                if (suggestions.containsKey(internalField)) break;
            }
        }

        return suggestions;
    }

    private Map<String, List<String>> getHintsForType(String objectType) {
        Map<String, List<String>> hints = new LinkedHashMap<>();

        switch (objectType.toLowerCase()) {
            case "domain" -> {
                hints.put("ldhName", List.of("dname", "domainname", "domain", "name", "ldh", "ldhname"));
                hints.put("handle", List.of("roid", "handle", "registryid", "domainid"));
                hints.put("status", List.of("eppstatus", "status", "state"));
                hints.put("registrationDate", List.of("appdate", "createdate", "registrationdate", "regdate", "created"));
                hints.put("expirationDate", List.of("expdate", "expirationdate", "expires", "expiry"));
                hints.put("lastChangedDate", List.of("updatedate", "lastchanged", "modified", "updated", "lastupdated"));
                hints.put("nameservers", List.of("nameserver", "ns", "nameservers", "dns"));
                hints.put("dnssecEnabled", List.of("dnssec", "dnssecenabled", "secureddns"));
                hints.put("registrantContact", List.of("rcontact", "registrant"));
                hints.put("adminContact", List.of("acontact", "admincontact", "admin"));
                hints.put("techContact", List.of("tcontact", "techcontact", "tech"));
                hints.put("billingContact", List.of("bcontact", "billingcontact", "billing"));
                hints.put("registrarId", List.of("registrarid", "registrar", "registrarno"));
                hints.put("sld", List.of("sld", "tld", "zone"));
            }
            case "contact" -> {
                hints.put("handle", List.of("id", "contactid", "handle"));
                hints.put("name", List.of("ename", "name", "contactname", "fullname"));
                hints.put("localName", List.of("cname", "localname", "nativename"));
                hints.put("organization", List.of("cmpename", "organization", "org", "company"));
                hints.put("localOrganization", List.of("cmpcname", "localorg"));
                hints.put("email", List.of("email", "emailaddr", "emailaddress"));
                hints.put("phone", List.of("tel", "phone", "telephone"));
                hints.put("fax", List.of("fax", "facsimile"));
                hints.put("street1", List.of("eaddr1", "street", "address", "addr1", "street1"));
                hints.put("street2", List.of("eaddr2", "addr2", "street2", "addressline2"));
                hints.put("street3", List.of("eaddr3", "addr3", "street3"));
                hints.put("city", List.of("ecity", "city"));
                hints.put("state", List.of("estate", "state", "province", "region"));
                hints.put("postalCode", List.of("postal", "postalcode", "zip", "zipcode"));
                hints.put("country", List.of("country", "countrycode", "cc"));
                hints.put("localStreet1", List.of("caddr", "localaddr", "localstreet"));
                hints.put("localStreet2", List.of("caddr2"));
                hints.put("localCity", List.of("ccity", "localcity"));
                hints.put("localState", List.of("cstate", "localstate"));
                hints.put("localPostalCode", List.of("cpostal", "localpostal"));
                hints.put("localCountry", List.of("ccountry", "localcountry"));
                hints.put("disclose", List.of("disclose", "disclosure", "privacy"));
                hints.put("createdDate", List.of("createdate", "created"));
                hints.put("updatedDate", List.of("updatedate", "updated", "modified"));
            }
            case "host" -> {
                hints.put("handle", List.of("roid", "handle", "hostid"));
                hints.put("hostName", List.of("hostname", "host", "name", "fqdn"));
                hints.put("ipv4", List.of("ipaddr4", "ipv4", "ip4", "ipaddrv4"));
                hints.put("ipv6", List.of("ipaddr6", "ipv6", "ip6", "ipaddrv6"));
                hints.put("createdDate", List.of("createdate", "created"));
                hints.put("updatedDate", List.of("updatedate", "updated", "modified"));
            }
        }

        return hints;
    }

    // ==================== DATA BROWSING ====================

    /**
     * Browse data from any table in the mapped database with pagination, search, and sorting.
     * Returns both raw column data and the mapped-field translation.
     */
    public Map<String, Object> browseTable(RdapDataMapping mapping, String tableName,
                                            int page, int pageSize, String search,
                                            String sortColumn, String sortDir) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            Connection conn = getConnection(mapping);
            try {
                String quoted = quoteIdentifier(conn, tableName);

                // --- total count ---
                long totalRows;
                if (search != null && !search.isBlank()) {
                    // Get all text-type columns for the WHERE clause
                    List<String> textCols = new ArrayList<>();
                    try (ResultSet colRs = conn.getMetaData().getColumns(
                            conn.getCatalog(), conn.getSchema(), tableName, "%")) {
                        while (colRs.next()) {
                            String typeName = colRs.getString("TYPE_NAME").toLowerCase();
                            if (typeName.contains("char") || typeName.contains("text") || typeName.contains("clob")) {
                                textCols.add(colRs.getString("COLUMN_NAME"));
                            }
                        }
                    }
                    if (textCols.isEmpty()) {
                        // No text columns — fall back to unfiltered count
                        try (Statement s = conn.createStatement();
                             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + quoted)) {
                            rs.next();
                            totalRows = rs.getLong(1);
                        }
                    } else {
                        StringBuilder where = buildSearchWhere(conn, textCols, search);
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT COUNT(*) FROM " + quoted + " WHERE " + where)) {
                            for (int i = 1; i <= textCols.size(); i++) {
                                ps.setString(i, "%" + search + "%");
                            }
                            try (ResultSet rs = ps.executeQuery()) {
                                rs.next();
                                totalRows = rs.getLong(1);
                            }
                        }
                    }
                } else {
                    try (Statement s = conn.createStatement();
                         ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + quoted)) {
                        rs.next();
                        totalRows = rs.getLong(1);
                    }
                }

                // --- data query ---
                StringBuilder sql = new StringBuilder("SELECT * FROM ").append(quoted);

                List<String> textCols = new ArrayList<>();
                if (search != null && !search.isBlank()) {
                    try (ResultSet colRs = conn.getMetaData().getColumns(
                            conn.getCatalog(), conn.getSchema(), tableName, "%")) {
                        while (colRs.next()) {
                            String typeName = colRs.getString("TYPE_NAME").toLowerCase();
                            if (typeName.contains("char") || typeName.contains("text") || typeName.contains("clob")) {
                                textCols.add(colRs.getString("COLUMN_NAME"));
                            }
                        }
                    }
                    if (!textCols.isEmpty()) {
                        sql.append(" WHERE ").append(buildSearchWhere(conn, textCols, search));
                    }
                }

                // Sort
                if (sortColumn != null && !sortColumn.isBlank()) {
                    String dir = "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
                    sql.append(" ORDER BY ").append(quoteIdentifier(conn, sortColumn)).append(" ").append(dir);
                }

                // Pagination
                int safePage = Math.max(0, page);
                int safeSize = Math.max(1, Math.min(pageSize, 100));
                int offset = safePage * safeSize;
                sql.append(" LIMIT ").append(safeSize).append(" OFFSET ").append(offset);

                List<Map<String, Object>> rows = new ArrayList<>();
                List<Map<String, Object>> columns = new ArrayList<>();

                if (textCols.isEmpty() || search == null || search.isBlank()) {
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(sql.toString())) {
                        columns = extractColumns(rs);
                        rows = extractRows(rs);
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                        for (int i = 1; i <= textCols.size(); i++) {
                            ps.setString(i, "%" + search + "%");
                        }
                        try (ResultSet rs = ps.executeQuery()) {
                            columns = extractColumns(rs);
                            rows = extractRows(rs);
                        }
                    }
                }

                result.put("success", true);
                result.put("table", tableName);
                result.put("columns", columns);
                result.put("rows", rows);
                result.put("page", safePage);
                result.put("pageSize", safeSize);
                result.put("totalRows", totalRows);
                result.put("totalPages", (int) Math.ceil((double) totalRows / safeSize));

                // Resolve column mappings: find which mapping config applies to this table
                Map<String, String> columnMappings = resolveColumnMappingsForTable(mapping, tableName);
                if (columnMappings != null && !columnMappings.isEmpty()) {
                    // Build reverse map: externalCol -> internalField
                    Map<String, String> reverseMap = new LinkedHashMap<>();
                    columnMappings.forEach((internalField, externalCol) ->
                        reverseMap.put(externalCol, internalField));
                    result.put("columnMappings", columnMappings);
                    result.put("reverseColumnMap", reverseMap);

                    // Enrich column metadata with mapped names
                    for (Map<String, Object> col : columns) {
                        String colName = (String) col.get("name");
                        String mappedName = reverseMap.get(colName);
                        col.put("mappedName", mappedName); // null if no mapping
                    }
                }

            } finally {
                if (mapping.isExternal()) conn.close();
            }
        } catch (Exception e) {
            log.error("Browse query failed for table {}", tableName, e);
            result.put("success", false);
            result.put("error", "Could not browse data from the mapped database — please verify the mapping and connection details");
        }

        return result;
    }

    /**
     * List all tables available in the mapped database with their row counts.
     */
    public Map<String, Object> listTables(RdapDataMapping mapping) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            Connection conn = getConnection(mapping);
            try {
                DatabaseMetaData meta = conn.getMetaData();
                String catalog = conn.getCatalog();
                String schema = conn.getSchema();

                List<Map<String, Object>> tables = new ArrayList<>();
                try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        String name = rs.getString("TABLE_NAME");
                        if (name.startsWith("pg_") || name.startsWith("sql_") ||
                            name.equals("information_schema") || name.startsWith("flyway_")) {
                            continue;
                        }

                        Map<String, Object> table = new LinkedHashMap<>();
                        table.put("name", name);

                        // Column count
                        int colCount = 0;
                        try (ResultSet colRs = meta.getColumns(catalog, schema, name, "%")) {
                            while (colRs.next()) colCount++;
                        }
                        table.put("columnCount", colCount);

                        // Row count
                        try (Statement s = conn.createStatement();
                             ResultSet countRs = s.executeQuery("SELECT COUNT(*) FROM " + quoteIdentifier(conn, name))) {
                            if (countRs.next()) table.put("rowCount", countRs.getLong(1));
                        } catch (Exception e) {
                            table.put("rowCount", -1);
                        }

                        tables.add(table);
                    }
                }

                result.put("success", true);
                result.put("tables", tables);
                result.put("dbProduct", meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion());

            } finally {
                if (mapping.isExternal()) conn.close();
            }
        } catch (Exception e) {
            log.error("List tables failed", e);
            result.put("success", false);
            result.put("error", "Could not list tables from the mapped database — please verify the connection details");
        }

        return result;
    }

    /**
     * Given a table name, find which column mapping config from the mapping applies to it.
     * Matches against all configured table names (domains, contacts, hosts, ips, asns, entities).
     */
    private Map<String, String> resolveColumnMappingsForTable(RdapDataMapping mapping, String tableName) {
        if (tableName == null) return null;

        // Check each table mapping to see which one matches this table name
        if (tableName.equals(mapping.getDomainsTable()) || tableName.equals(mapping.getEffectiveDomainsTable())) {
            return mapping.getDomainColumnMappings();
        }
        if (tableName.equals(mapping.getContactsTable()) || tableName.equals(mapping.getEffectiveContactsTable())) {
            return mapping.getContactColumnMappings();
        }
        if (tableName.equals(mapping.getHostsTable()) || tableName.equals(mapping.getEffectiveHostsTable())) {
            return mapping.getHostColumnMappings();
        }
        if (tableName.equals(mapping.getIpsTable())) {
            return mapping.getIpColumnMappings();
        }
        if (tableName.equals(mapping.getAsnsTable())) {
            return mapping.getAsnColumnMappings();
        }
        if (tableName.equals(mapping.getEntitiesTable())) {
            return mapping.getEntityColumnMappings();
        }

        return null;
    }

    private StringBuilder buildSearchWhere(Connection conn, List<String> textCols, String search) throws SQLException {
        StringBuilder where = new StringBuilder("(");
        for (int i = 0; i < textCols.size(); i++) {
            if (i > 0) where.append(" OR ");
            where.append(quoteIdentifier(conn, textCols.get(i))).append(" LIKE ?");
        }
        where.append(")");
        return where;
    }

    private List<Map<String, Object>> extractColumns(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        List<Map<String, Object>> columns = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("name", meta.getColumnName(i));
            col.put("type", meta.getColumnTypeName(i));
            col.put("displaySize", meta.getColumnDisplaySize(i));
            columns.add(col);
        }
        return columns;
    }

    private List<Map<String, Object>> extractRows(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                Object val = rs.getObject(i);
                // Convert types that don't serialize well to JSON
                if (val instanceof java.sql.Timestamp ts) {
                    row.put(meta.getColumnName(i), ts.toLocalDateTime().toString());
                } else if (val instanceof java.sql.Date d) {
                    row.put(meta.getColumnName(i), d.toLocalDate().toString());
                } else if (val instanceof byte[]) {
                    row.put(meta.getColumnName(i), "[binary " + ((byte[]) val).length + " bytes]");
                } else {
                    row.put(meta.getColumnName(i), val);
                }
            }
            rows.add(row);
        }
        return rows;
    }

    // ==================== HELPERS ====================

    private Connection getConnection(RdapDataMapping mapping) throws SQLException {
        if (mapping.isExternal()) {
            try {
                if (mapping.getExternalDbDriver() != null && !mapping.getExternalDbDriver().isBlank()) {
                    Class.forName(mapping.getExternalDbDriver());
                }
            } catch (ClassNotFoundException e) {
                throw new SQLException("JDBC driver not found: " + mapping.getExternalDbDriver(), e);
            }
            return DriverManager.getConnection(
                mapping.getExternalJdbcUrl(),
                mapping.getExternalDbUsername(),
                mapping.getExternalDbPassword()
            );
        } else {
            return localDataSource.getConnection();
        }
    }

    /**
     * Infer likely FK relationships from column-name patterns, for legacy schemas that express
     * relationships by convention rather than formal FOREIGN KEY constraints (e.g. dn.r_contact →
     * contact, order.customer_id → customer). Inferred entries are marked inferred=true so the UI
     * can distinguish them from formal FKs.
     */
    @SuppressWarnings("unchecked")
    private void inferForeignKeys(List<Map<String, Object>> tables) {
        if (tables == null || tables.isEmpty()) return;

        // Build a lookup: tableName → key column name (from indexes or first column named "id")
        Map<String, String> tableKeyColumns = new LinkedHashMap<>();
        Set<String> tableNames = new LinkedHashSet<>();
        for (Map<String, Object> table : tables) {
            String name = (String) table.get("name");
            if (name == null) continue;
            tableNames.add(name.toLowerCase());

            List<Map<String, Object>> columns = (List<Map<String, Object>>) table.get("columns");
            if (columns == null) continue;

            // Find the most likely key column: prefer "id", then first column
            String keyCol = null;
            for (Map<String, Object> col : columns) {
                String colName = (String) col.get("name");
                if (colName == null) continue;
                if ("id".equalsIgnoreCase(colName)) { keyCol = colName; break; }
            }
            if (keyCol == null && !columns.isEmpty()) {
                keyCol = (String) columns.get(0).get("name");
            }
            if (keyCol != null) {
                tableKeyColumns.put(name.toLowerCase(), keyCol);
            }
        }

        // For each table, examine each column and check if it references another table
        for (Map<String, Object> table : tables) {
            String tableName = (String) table.get("name");
            List<Map<String, Object>> columns = (List<Map<String, Object>>) table.get("columns");
            if (tableName == null || columns == null) continue;

            List<Map<String, Object>> existingFks = (List<Map<String, Object>>) table.get("foreignKeys");
            Set<String> alreadyMapped = new HashSet<>();
            if (existingFks != null) {
                for (Map<String, Object> fk : existingFks) {
                    alreadyMapped.add(((String) fk.get("fkColumn")).toLowerCase());
                }
            }

            List<Map<String, Object>> inferred = new ArrayList<>();

            for (Map<String, Object> col : columns) {
                String colName = (String) col.get("name");
                if (colName == null) continue;
                String colLower = colName.toLowerCase();

                // Skip if already a formal FK
                if (alreadyMapped.contains(colLower)) continue;

                // Skip self-references to the table's own key
                if (colLower.equals(tableKeyColumns.getOrDefault(tableName.toLowerCase(), ""))) continue;

                /* Strategy 1: column name contains a table name
                 * e.g. "r_contact" contains "contact", "a_contact" contains "contact" */
                for (String otherTable : tableNames) {
                    if (otherTable.equals(tableName.toLowerCase())) continue;
                    /* Only match if the table name appears as a whole segment
                     * (separated by underscores, at start/end, or is the entire name) */
                    if (columnReferencesTable(colLower, otherTable)) {
                        String targetKey = tableKeyColumns.getOrDefault(otherTable, "id");
                        Map<String, Object> fk = new LinkedHashMap<>();
                        fk.put("fkColumn", colName);
                        fk.put("pkTable", findOriginalCaseTableName(tables, otherTable));
                        fk.put("pkColumn", targetKey);
                        fk.put("inferred", true);
                        inferred.add(fk);
                        alreadyMapped.add(colLower);
                        break; // one match per column
                    }
                }

                // Strategy 2: column named "{something}_id" where {something} matches a table
                if (!alreadyMapped.contains(colLower) && colLower.endsWith("_id")) {
                    String prefix = colLower.substring(0, colLower.length() - 3);
                    if (tableNames.contains(prefix)) {
                        String targetKey = tableKeyColumns.getOrDefault(prefix, "id");
                        Map<String, Object> fk = new LinkedHashMap<>();
                        fk.put("fkColumn", colName);
                        fk.put("pkTable", findOriginalCaseTableName(tables, prefix));
                        fk.put("pkColumn", targetKey);
                        fk.put("inferred", true);
                        inferred.add(fk);
                        alreadyMapped.add(colLower);
                    }
                }
            }

            if (!inferred.isEmpty()) {
                List<Map<String, Object>> allFks = existingFks != null ? new ArrayList<>(existingFks) : new ArrayList<>();
                allFks.addAll(inferred);
                table.put("foreignKeys", allFks);

                // Also add inferred referencedBy entries on the target tables
                for (Map<String, Object> fk : inferred) {
                    String pkTableName = ((String) fk.get("pkTable")).toLowerCase();
                    Map<String, Object> targetTable = tables.stream()
                            .filter(t -> pkTableName.equalsIgnoreCase((String) t.get("name")))
                            .findFirst().orElse(null);
                    if (targetTable != null) {
                        List<Map<String, Object>> refs = (List<Map<String, Object>>) targetTable.get("referencedBy");
                        if (refs == null) { refs = new ArrayList<>(); targetTable.put("referencedBy", refs); }
                        Map<String, Object> ref = new LinkedHashMap<>();
                        ref.put("fkTable", tableName);
                        ref.put("fkColumn", fk.get("fkColumn"));
                        ref.put("pkColumn", fk.get("pkColumn"));
                        ref.put("inferred", true);
                        refs.add(ref);
                    }
                }
            }
        }
    }

    /**
     * True if a column name references a table name, matching only whole underscore-delimited
     * segments (e.g. "r_contact"/"contact_id" match "contact").
     */
    private boolean columnReferencesTable(String columnLower, String tableLower) {
        if (columnLower.equals(tableLower)) return false; // exact match = same name, not a reference
        // Check as underscore-delimited segment
        String[] parts = columnLower.split("_");
        for (String part : parts) {
            if (part.equals(tableLower)) return true;
        }
        // Also check for multi-word table names as suffix/prefix
        if (columnLower.endsWith("_" + tableLower) || columnLower.startsWith(tableLower + "_")) return true;
        return false;
    }

    /**
     * Find the original-case table name from the tables list.
     */
    private String findOriginalCaseTableName(List<Map<String, Object>> tables, String lowerName) {
        for (Map<String, Object> t : tables) {
            String name = (String) t.get("name");
            if (name != null && name.equalsIgnoreCase(lowerName)) return name;
        }
        return lowerName;
    }

    private String quoteIdentifier(Connection conn, String identifier) throws SQLException {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier cannot be null or blank");
        }
        // Reject identifiers with dangerous characters
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_.]*$")) {
            throw new IllegalArgumentException("Invalid identifier: " + identifier);
        }
        String quote = conn.getMetaData().getIdentifierQuoteString();
        if (quote == null || quote.isBlank() || quote.equals(" ")) quote = "\"";
        // Escape any embedded quote characters
        String escaped = identifier.replace(quote, quote + quote);
        return quote + escaped + quote;
    }
}
