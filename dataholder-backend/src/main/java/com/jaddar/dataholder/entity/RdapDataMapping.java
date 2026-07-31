/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Stores RDAP data mapping configuration that allows the application
 * to work with any database structure, including external databases.
 *
 * Supports two modes:
 * - LOCAL: queries run against the same database as the application
 * - EXTERNAL: queries run against a separate JDBC-connected database
 *
 * The mapping supports relational schemas where domain data, contact data,
 * and host data live in separate tables linked by foreign keys (like a registry's
 * dn/contact/host schema), not just flat single-table layouts.
 */
@Entity
@Table(name = "rdap_data_mappings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RdapDataMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    // ==================== Database Connection ====================

    @Column(name = "db_type", length = 30)
    @Builder.Default
    private String dbType = "LOCAL";

    @Column(name = "external_jdbc_url", length = 500)
    private String externalJdbcUrl;

    @Column(name = "external_db_username", length = 512)
    @Convert(converter = com.jaddar.dataholder.config.EncryptedStringConverter.class)
    private String externalDbUsername;

    @Column(name = "external_db_password", length = 512)
    @Convert(converter = com.jaddar.dataholder.config.EncryptedStringConverter.class)
    private String externalDbPassword;

    @Column(name = "external_db_driver", length = 100)
    private String externalDbDriver;

    @Column(name = "external_db_schema", length = 100)
    private String externalDbSchema;

    /**
     * Whether column name lookups (for join conditions, etc.) should be case-sensitive.
     * Defaults to false (case-insensitive) which works for MariaDB/MySQL.
     * Set to true for databases like PostgreSQL where column names are case-sensitive.
     */
    @Column(name = "column_name_case_sensitive")
    @Builder.Default
    private Boolean columnNameCaseSensitive = false;

    // ==================== Table Name Overrides ====================

    @Column(name = "domains_table", length = 255)
    private String domainsTable;

    @Column(name = "ips_table", length = 255)
    private String ipsTable;

    @Column(name = "asns_table", length = 255)
    private String asnsTable;

    @Column(name = "entities_table", length = 255)
    private String entitiesTable;

    @Column(name = "contacts_table", length = 255)
    private String contactsTable;

    @Column(name = "hosts_table", length = 255)
    private String hostsTable;

    // ==================== Column Mapping Overrides ====================

    @Column(name = "domain_column_mappings", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> domainColumnMappings;

    @Column(name = "ip_column_mappings", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> ipColumnMappings;

    @Column(name = "asn_column_mappings", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> asnColumnMappings;

    @Column(name = "entity_column_mappings", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> entityColumnMappings;

    @Column(name = "contact_column_mappings", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> contactColumnMappings;

    @Column(name = "host_column_mappings", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> hostColumnMappings;

    // ==================== Join Configuration ====================

    /**
     * Maps contact roles to domain table foreign key columns.
     * Example: {"registrant": "r_contact", "admin": "a_contact", "tech": "t_contact", "billing": "b_contact"}
     */
    @Column(name = "domain_contact_join_mappings", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> domainContactJoinMappings;

    @Column(name = "contact_join_key", length = 100)
    @Builder.Default
    private String contactJoinKey = "id";

    /**
     * How to resolve nameservers from the hosts table.
     * Example: {"domainColumn": "nameserver", "hostColumn": "host_name", "delimiter": ","}
     */
    @Column(name = "host_join_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> hostJoinConfig;

    // ==================== Custom Table Mappings ====================

    /**
     * Custom table mappings allow additional tables (beyond the 6 standard RDAP types)
     * to be included in the mapping configuration. Each entry defines a table name,
     * a join configuration to connect it to the primary query, and how its columns
     * map to either standard RDAP fields or custom named values.
     *
     * Structure: List of maps, each containing:
     *   - tableName: the database table name (e.g. "greendn")
     *   - label: display label (e.g. "Green Domains")
     *   - description: optional description
     *   - joinConfig: how this table connects to the primary RDAP data:
     *       - joinType: which RDAP object this table relates to (e.g. "domain")
     *       - enabled: whether this join is active
     *       - joinConditions: list of condition objects, each with:
     *           - sourceColumn: column in the custom table (e.g. "domain")
     *           - targetColumns: list of columns from the primary table to compose the
     *                            match value (e.g. ["d_name", "sld"])
     *           - separator: string to join targetColumns with (e.g. ".")
     *             Result: greendn.domain = CONCAT(dn.d_name, '.', dn.sld)
     *   - columnMappings: list of column mapping objects, each with:
     *       - sourceColumn: column name in the custom table (e.g. "CMP_Ename")
     *       - mappingType: "standard" or "custom"
     *       - targetType: for "standard" — which object type (domain, contact, etc.)
     *       - targetField: for "standard" — which standard field (e.g. "organization")
     *       - customAlias: for "custom" — a user-defined alias name (e.g. "green")
     *       - customDescription: optional description for custom mappings
     */
    @Column(name = "custom_table_mappings", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Map<String, Object>> customTableMappings;

    // ==================== Domain Name Composition ====================

    /**
     * Column in the domain table that holds the TLD/zone suffix.
     * When set, the full domain name (ldhName) is composed as: ldhName + separator + suffixColumn.
     * Example: d_name="example-legal-private", sld="com" → "example-legal-private.com"
     */
    @Column(name = "domain_name_suffix_column", length = 100)
    private String domainNameSuffixColumn;

    /**
     * Separator between the domain name and the suffix. Defaults to "." if not set.
     */
    @Column(name = "domain_name_separator", length = 10)
    @Builder.Default
    private String domainNameSeparator = ".";

    public String getEffectiveDomainNameSeparator() {
        return domainNameSeparator != null && !domainNameSeparator.isEmpty() ? domainNameSeparator : ".";
    }

    // ==================== Schema Discovery ====================

    @Column(name = "discovered_schema", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> discoveredSchema;

    @Column(name = "last_introspected_at")
    private LocalDateTime lastIntrospectedAt;

    // ==================== Audit ====================

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==================== Helpers ====================

    public boolean isExternal() {
        return "EXTERNAL".equalsIgnoreCase(dbType);
    }

    public String getEffectiveDomainsTable() {
        return domainsTable != null && !domainsTable.isBlank() ? domainsTable : "rdap_domains";
    }

    public String getEffectiveContactsTable() {
        return contactsTable != null && !contactsTable.isBlank() ? contactsTable : "contact";
    }

    public String getEffectiveHostsTable() {
        return hostsTable != null && !hostsTable.isBlank() ? hostsTable : "host";
    }

    public String getEffectiveContactJoinKey() {
        return contactJoinKey != null && !contactJoinKey.isBlank() ? contactJoinKey : "id";
    }
}
