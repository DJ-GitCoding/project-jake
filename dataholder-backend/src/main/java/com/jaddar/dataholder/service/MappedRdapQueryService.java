/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.entity.*;
import com.jaddar.dataholder.entity.RdapEntity.ObjectType;
import com.jaddar.dataholder.entity.RdapEvent.EventAction;
import com.jaddar.dataholder.repository.RdapDataMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Queries an external mapped database and constructs RdapEntity objects
 * that are compatible with the existing RDAP response pipeline.
 *
 * When an active RdapDataMapping exists, this service is used instead of
 * the local rdap_entities JPA repository to resolve domain/IP/ASN lookups.
 *
 * The service:
 * 1. Queries the domain table (e.g. "dn") using mapped column names
 * 2. Joins to the contact table (e.g. "contact") for each contact role
 * 3. Joins to the host table (e.g. "host") for nameservers
 * 4. Assembles everything into RdapEntity objects with children
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MappedRdapQueryService {

    private final DataSource localDataSource;
    private final RdapDataMappingRepository mappingRepository;

    /**
     * Get all currently active mappings.
     */
    public List<RdapDataMapping> getActiveMappings() {
        return mappingRepository.findByIsActiveTrue();
    }

    /**
     * Query a domain by its LDH name from the mapped database.
     */
    public Optional<RdapEntity> queryDomain(RdapDataMapping mapping, String domainName) {
        try {
            Connection conn = getConnection(mapping);
            try {
                String table = mapping.getEffectiveDomainsTable();
                Map<String, String> colMap = mapping.getDomainColumnMappings();
                if (colMap == null) colMap = new HashMap<>();

                // Determine which column holds the domain name — must be mapped
                String nameCol = colMap.get("ldhName");
                if (nameCol == null || nameCol.isBlank()) {
                    log.error("Cannot query domain: 'ldhName' column is not mapped");
                    return Optional.empty();
                }

                String sql = "SELECT * FROM " + q(conn, table) + " WHERE " + q(conn, nameCol) + " = ?";
                log.debug("Mapped domain query: {} [{}]", sql, domainName);

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, domainName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) return Optional.empty();
                        RdapEntity entity = buildDomainEntity(rs, colMap, mapping);
                        // Resolve child contacts
                        resolveContacts(conn, mapping, rs, colMap, entity);
                        // Resolve nameservers
                        resolveNameservers(conn, mapping, rs, colMap, entity);
                        return Optional.of(entity);
                    }
                }
            } finally {
                if (mapping.isExternal()) conn.close();
            }
        } catch (Exception e) {
            log.error("Mapped domain query failed for {}: {}", domainName, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Query a domain by its full LDH name with suffix/zone splitting.
     * If domainNameSuffixColumn is configured, splits the query domain at the separator
     * and queries name column + suffix column separately.
     * Falls back to exact match on the name column alone.
     */
    public Optional<RdapEntity> queryDomainWithSld(RdapDataMapping mapping, String domainName) {
        String suffixCol = mapping.getDomainNameSuffixColumn();

        // If a suffix column is configured and the domain contains the separator, do a split query
        if (suffixCol != null && !suffixCol.isBlank() && domainName.contains(".")) {
            String sep = mapping.getEffectiveDomainNameSeparator();
            int sepIdx = domainName.indexOf(sep);
            String namePart = domainName.substring(0, sepIdx);
            String suffixPart = domainName.substring(sepIdx + sep.length());

            try {
                Connection conn = getConnection(mapping);
                try {
                    String table = mapping.getEffectiveDomainsTable();
                    Map<String, String> colMap = mapping.getDomainColumnMappings();
                    if (colMap == null) colMap = new HashMap<>();

                    String nameCol = colMap.get("ldhName");
                    if (nameCol == null || nameCol.isBlank()) {
                        log.error("Cannot query domain: 'ldhName' column is not mapped");
                        return Optional.empty();
                    }

                    String sql = "SELECT * FROM " + q(conn, table) +
                                 " WHERE " + q(conn, nameCol) + " = ? AND " + q(conn, suffixCol) + " = ?";
                    log.debug("Mapped domain query with suffix: {} [{}, {}]", sql, namePart, suffixPart);

                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, namePart);
                        ps.setString(2, suffixPart);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) return Optional.empty();
                            RdapEntity entity = buildDomainEntity(rs, colMap, mapping);
                            resolveContacts(conn, mapping, rs, colMap, entity);
                            resolveNameservers(conn, mapping, rs, colMap, entity);
                            return Optional.of(entity);
                        }
                    }
                } finally {
                    if (mapping.isExternal()) conn.close();
                }
            } catch (Exception e) {
                log.error("Mapped domain+suffix query failed for {}: {}", domainName, e.getMessage(), e);
            }

            return Optional.empty();
        }

        // No suffix column configured — try exact match on name column
        return queryDomain(mapping, domainName);
    }

    // ==================== ENTITY BUILDERS ====================

    private RdapEntity buildDomainEntity(ResultSet rs, Map<String, String> colMap, RdapDataMapping mapping) throws SQLException {
        RdapEntity e = new RdapEntity();
        e.setObjectType(ObjectType.DOMAIN);
        e.setObjectClassName("domain");

        // Map columns to entity fields
        String nameValue = getStr(rs, colMap.get("ldhName"));

        // Compose full domain name if a suffix column is configured
        String suffixCol = mapping.getDomainNameSuffixColumn();
        if (suffixCol != null && !suffixCol.isBlank()) {
            String suffixValue = getStr(rs, suffixCol);
            if (nameValue != null && suffixValue != null && !suffixValue.isBlank()) {
                String sep = mapping.getEffectiveDomainNameSeparator();
                nameValue = nameValue + sep + suffixValue;
            }
        }

        e.setLdhName(nameValue);
        e.setHandle(getStr(rs, colMap.get("handle")));
        e.setPort43(getStr(rs, colMap.get("port43")));

        // Status
        String statusStr = getStr(rs, colMap.get("status"));
        if (statusStr != null && !statusStr.isBlank()) {
            e.setStatus(new ArrayList<>(Arrays.asList(statusStr.split(",\\s*"))));
        } else {
            e.setStatus(new ArrayList<>());
        }

        // DNSSEC
        String dnssec = getStr(rs, colMap.get("dnssecEnabled"));
        e.setSecureDnsDelegationSigned(dnssec != null && !dnssec.isBlank());

        // Build a synthetic ID (negative to avoid collision with local entities)
        String handle = e.getHandle();
        e.setId(handle != null ? (long) -(handle.hashCode() & 0x7FFFFFFF) : -1L);

        // Roles and public IDs init
        e.setRoles(new ArrayList<>());
        e.setPublicIds(new ArrayList<>());
        e.setChildren(new ArrayList<>());

        // Build events from date columns
        List<RdapEvent> events = new ArrayList<>();
        LocalDateTime regDate = getDateTime(rs, colMap.get("registrationDate"));
        if (regDate != null) {
            RdapEvent ev = new RdapEvent();
            ev.setEventAction(EventAction.REGISTRATION);
            ev.setEventDate(regDate);
            ev.setRdapEntity(e);
            events.add(ev);
        }

        LocalDateTime expDate = getDateTime(rs, colMap.get("expirationDate"));
        if (expDate != null) {
            RdapEvent ev = new RdapEvent();
            ev.setEventAction(EventAction.EXPIRATION);
            ev.setEventDate(expDate);
            ev.setRdapEntity(e);
            events.add(ev);
        }

        LocalDateTime updateDate = getDateTime(rs, colMap.get("lastChangedDate"));
        if (updateDate != null) {
            RdapEvent ev = new RdapEvent();
            ev.setEventAction(EventAction.LAST_CHANGED);
            ev.setEventDate(updateDate);
            ev.setRdapEntity(e);
            events.add(ev);
        }

        // Store events on the entity for the controller to pick up
        // We'll use a transient approach — store in children list as a workaround,
        // or the service layer will provide them separately.
        // For now, we attach them via a holder pattern.
        e.setCreatedAt(regDate);
        e.setUpdatedAt(updateDate);

        return e;
    }

    private RdapEntity buildContactEntity(ResultSet rs, Map<String, String> colMap, String role) throws SQLException {
        RdapEntity c = new RdapEntity();
        c.setObjectType(ObjectType.ENTITY);
        c.setObjectClassName("entity");

        c.setHandle(getStr(rs, colMap.get("handle")));
        c.setContactName(getStr(rs, colMap.get("name")));
        c.setOrganization(getStr(rs, colMap.get("organization")));
        c.setEmail(getStr(rs, colMap.get("email")));
        c.setPhone(getStr(rs, colMap.get("phone")));
        c.setFax(getStr(rs, colMap.get("fax")));
        c.setAddressStreet1(getStr(rs, colMap.get("street1")));
        c.setAddressStreet2(getStr(rs, colMap.get("street2")));
        c.setAddressCity(getStr(rs, colMap.get("city")));
        c.setAddressState(getStr(rs, colMap.get("state")));
        c.setAddressPostalCode(getStr(rs, colMap.get("postalCode")));
        c.setAddressCountry(getStr(rs, colMap.get("country")));

        // Roles
        c.setRoles(new ArrayList<>(List.of(role)));
        c.setStatus(new ArrayList<>());
        c.setPublicIds(new ArrayList<>());
        c.setChildren(new ArrayList<>());

        // Synthetic ID
        String handle = c.getHandle();
        c.setId(handle != null ? (long) -(handle.hashCode() & 0x7FFFFFFF) - role.hashCode() : -2L);

        // Carry non-standard mapped fields (e.g. "disclose") so policy scope
        // conditions can reference them. colMap maps mappedName -> source column;
        // the standard fields above are excluded. Driven entirely by the mapping's
        // column map — nothing hardcoded. Not persisted, not in the RDAP response.
        Set<String> standardFields = Set.of(
                "handle", "name", "organization", "email", "phone", "fax",
                "street1", "street2", "city", "state", "postalCode", "country");
        Map<String, Object> customAttrs = new HashMap<>();
        for (Map.Entry<String, String> e : colMap.entrySet()) {
            String mappedName = e.getKey();
            String sourceCol = e.getValue();
            if (mappedName == null || sourceCol == null || standardFields.contains(mappedName)) continue;
            String val = getStr(rs, sourceCol);
            if (val != null) customAttrs.put(mappedName, val);
        }
        c.setCustomAttributes(customAttrs);

        return c;
    }

    // ==================== JOIN RESOLUTION ====================

    /**
     * Resolve contacts from the contact table for each role defined in the join mappings.
     * The domain row is still open in the ResultSet so we can read FK values.
     */
    private void resolveContacts(Connection conn, RdapDataMapping mapping,
                                  ResultSet domainRs, Map<String, String> domainColMap,
                                  RdapEntity domainEntity) throws SQLException {
        Map<String, String> joinMap = mapping.getDomainContactJoinMappings();
        if (joinMap == null || joinMap.isEmpty()) return;

        String contactTable = mapping.getEffectiveContactsTable();
        String joinKey = mapping.getEffectiveContactJoinKey();
        Map<String, String> contactColMap = mapping.getContactColumnMappings();
        if (contactColMap == null) contactColMap = new HashMap<>();

        // Deduplicate: if multiple roles point to the same contact ID, combine roles
        Map<String, List<String>> contactIdToRoles = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : joinMap.entrySet()) {
            String role = entry.getKey();        // e.g. "registrant"
            String fkColumn = entry.getValue();  // e.g. "r_contact"

            String fkValue = getStr(domainRs, fkColumn);
            if (fkValue != null && !fkValue.isBlank()) {
                contactIdToRoles.computeIfAbsent(fkValue, k -> new ArrayList<>()).add(role);
            }
        }

        // Query each unique contact
        for (Map.Entry<String, List<String>> entry : contactIdToRoles.entrySet()) {
            String contactId = entry.getKey();
            List<String> roles = entry.getValue();

            String sql = "SELECT * FROM " + q(conn, contactTable) +
                         " WHERE " + q(conn, joinKey) + " = ? LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, contactId);
                try (ResultSet crs = ps.executeQuery()) {
                    if (crs.next()) {
                        // Build one entity with all applicable roles
                        RdapEntity contact = buildContactEntity(crs, contactColMap, roles.get(0));
                        contact.setRoles(new ArrayList<>(roles));
                        contact.setParent(domainEntity);
                        domainEntity.getChildren().add(contact);
                    }
                }
            }
        }
    }

    /**
     * Resolve nameservers from the host table.
     * Uses the host join config to determine how domain rows reference hosts.
     */
    private void resolveNameservers(Connection conn, RdapDataMapping mapping,
                                     ResultSet domainRs, Map<String, String> domainColMap,
                                     RdapEntity domainEntity) throws SQLException {
        Map<String, String> hostJoin = mapping.getHostJoinConfig();
        if (hostJoin == null || hostJoin.isEmpty()) {
            // Fallback: check if there's a nameservers column directly
            String nsCol = domainColMap.get("nameservers");
            String nsValue = getStr(domainRs, nsCol);
            if (nsValue == null || nsValue.isBlank()) return;

            // Try to resolve from host table
            resolveNameserversFromList(conn, mapping, nsValue, ",", domainEntity);
            return;
        }

        String domainCol = hostJoin.get("domainColumn");
        String hostCol = hostJoin.get("hostColumn");
        String delimiter = hostJoin.getOrDefault("delimiter", ",");

        if (domainCol == null || hostCol == null) return;

        String nsValue = getStr(domainRs, domainCol);
        if (nsValue == null || nsValue.isBlank()) return;

        resolveNameserversFromList(conn, mapping, nsValue, delimiter, domainEntity);
    }

    private void resolveNameserversFromList(Connection conn, RdapDataMapping mapping,
                                             String nsValue, String delimiter,
                                             RdapEntity domainEntity) throws SQLException {
        String hostTable = mapping.getEffectiveHostsTable();
        Map<String, String> hostColMap = mapping.getHostColumnMappings();
        if (hostColMap == null) hostColMap = new HashMap<>();
        String hostJoinCol = hostColMap.get("hostName");
        Map<String, String> hostJoin = mapping.getHostJoinConfig();
        if (hostJoin != null && hostJoin.get("hostColumn") != null) {
            hostJoinCol = hostJoin.get("hostColumn");
        }

        String[] nsNames = nsValue.split(delimiter.isEmpty() ? "," : delimiter);

        for (String nsName : nsNames) {
            String trimmed = nsName.trim();
            if (trimmed.isEmpty()) continue;

            RdapEntity ns = new RdapEntity();
            ns.setObjectType(ObjectType.NAMESERVER);
            ns.setObjectClassName("nameserver");
            ns.setLdhName(trimmed);

            // Only query the host table if we have a column to join on
            if (hostJoinCol != null && !hostJoinCol.isBlank()) {
                String sql = "SELECT * FROM " + q(conn, hostTable) +
                             " WHERE " + q(conn, hostJoinCol) + " = ? LIMIT 1";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, trimmed);
                    try (ResultSet hrs = ps.executeQuery()) {
                        if (hrs.next()) {
                            ns.setHandle(getStr(hrs, hostColMap.get("handle")));
                            String ipv4 = getStr(hrs, hostColMap.get("ipv4"));
                            String ipv6 = getStr(hrs, hostColMap.get("ipv6"));
                            ns.setStartAddress(ipv4);
                            ns.setEndAddress(ipv6);
                        }
                    }
                }
            }

            if (ns.getHandle() == null) {
                ns.setHandle("NS-" + trimmed.hashCode());
            }

            ns.setId((long) -(trimmed.hashCode() & 0x7FFFFFFF));
            ns.setStatus(new ArrayList<>());
            ns.setRoles(new ArrayList<>());
            ns.setPublicIds(new ArrayList<>());
            ns.setChildren(new ArrayList<>());
            ns.setParent(domainEntity);
            domainEntity.getChildren().add(ns);
        }
    }

    // ==================== CUSTOM TABLE DATA RESOLUTION ====================

    /**
     * Resolve data from custom table mappings for a given domain query.
     * Each custom table defines join conditions that specify how columns in the
     * custom table relate to columns in the primary (domain) table.
     *
     * Join conditions are fully dynamic — each condition pairs a source column in the
     * custom table with one or more target columns from the primary table, optionally
     * concatenated with a separator.
     *
     * Example: greendn.domain = CONCAT(dn.d_name, '.', dn.sld)
     *   → joinConditions: [{ sourceColumn: "domain", targetColumns: ["d_name", "sld"], separator: "." }]
     *
     * @param mapping      the active data mapping
     * @param domainName   the full composed domain name (e.g. "legal-green.test.example")
     * @param domainEntity the resolved domain entity (for FK-based joins)
     * @return list of custom table result maps
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> resolveCustomTableData(RdapDataMapping mapping, String domainName, RdapEntity domainEntity) {
        List<Map<String, Object>> customTableMappings = mapping.getCustomTableMappings();
        if (customTableMappings == null || customTableMappings.isEmpty()) {
            log.debug("Custom table resolution: no custom table mappings configured");
            return List.of();
        }

        log.info("Custom table resolution: resolving for domain '{}' — {} custom table(s) configured",
                domainName, customTableMappings.size());
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            Connection conn = getConnection(mapping);
            try {
                // Pre-fetch the primary domain row so we can look up any column value
                Map<String, String> domainRowValues = fetchDomainRow(conn, mapping, domainName);
                if (domainRowValues == null) {
                    log.warn("Custom table resolution: could not fetch domain row for '{}' — skipping all custom tables", domainName);
                    return List.of();
                }
                log.info("Custom table resolution: domain row fetched — columns: {}", domainRowValues.keySet());

                for (Map<String, Object> ctm : customTableMappings) {
                    String tableName = (String) ctm.get("tableName");
                    String label = (String) ctm.get("label");
                    if (tableName == null || tableName.isBlank()) continue;

                    Map<String, Object> joinConfig = (Map<String, Object>) ctm.get("joinConfig");
                    if (joinConfig == null) {
                        log.debug("Custom table '{}': no joinConfig present, skipping", tableName);
                        continue;
                    }
                    if (!Boolean.TRUE.equals(joinConfig.get("enabled"))) {
                        log.debug("Custom table '{}': join not enabled (enabled={}), skipping", tableName, joinConfig.get("enabled"));
                        continue;
                    }

                    String joinType = (String) joinConfig.getOrDefault("joinType", "domain");
                    if (!"domain".equalsIgnoreCase(joinType)) {
                        log.debug("Custom table '{}': joinType='{}' not yet supported, skipping", tableName, joinType);
                        continue;
                    }

                    List<Map<String, Object>> joinConditions = (List<Map<String, Object>>) joinConfig.get("joinConditions");
                    if (joinConditions == null || joinConditions.isEmpty()) {
                        log.warn("Custom table '{}': join is enabled but no joinConditions defined — skipping", tableName);
                        continue;
                    }

                    log.info("Custom table '{}': processing {} join condition(s)", tableName, joinConditions.size());

                    // Build WHERE clause from join conditions
                    StringBuilder whereClause = new StringBuilder();
                    List<String> params = new ArrayList<>();
                    boolean allValid = true;

                    for (int ci = 0; ci < joinConditions.size(); ci++) {
                        Map<String, Object> cond = joinConditions.get(ci);
                        String sourceColumn = (String) cond.get("sourceColumn");
                        List<String> targetColumns = (List<String>) cond.get("targetColumns");
                        String separator = (String) cond.getOrDefault("separator", "");

                        log.debug("  Condition {}: sourceColumn='{}', targetColumns={}, separator='{}'",
                                ci, sourceColumn, targetColumns, separator);

                        if (sourceColumn == null || sourceColumn.isBlank() ||
                            targetColumns == null || targetColumns.isEmpty()) {
                            log.warn("  Condition {}: sourceColumn or targetColumns missing — aborting", ci);
                            allValid = false;
                            break;
                        }

                        // Compose the target value from primary row columns
                        StringBuilder targetValue = new StringBuilder();
                        for (int ti = 0; ti < targetColumns.size(); ti++) {
                            String col = targetColumns.get(ti);
                            String val = domainRowValues.get(col);
                            if (val == null) {
                                log.warn("  Condition {}: target column '{}' not found in domain row. Available: {}",
                                        ci, col, domainRowValues.keySet());
                                allValid = false;
                                break;
                            }
                            if (ti > 0 && separator != null && !separator.isEmpty()) {
                                targetValue.append(separator);
                            }
                            targetValue.append(val);
                        }
                        if (!allValid) break;

                        if (ci > 0) whereClause.append(" AND ");
                        whereClause.append(q(conn, sourceColumn)).append(" = ?");
                        params.add(targetValue.toString());
                        log.debug("  Condition {}: composed target value = '{}'", ci, targetValue);
                    }

                    if (!allValid || whereClause.isEmpty()) {
                        log.info("Custom table '{}': skipped — join conditions incomplete or domain column values missing", tableName);
                        continue;
                    }

                    // Derive a contact role hint from the domain FK column(s) this table
                    // joined on (e.g. r_contact → registrant, a_contact → administrative).
                    // Used only when a contact row doesn't carry an explicit contact.role.
                    String contactRoleHint = null;
                    for (Map<String, Object> cond : joinConditions) {
                        List<String> tcs = (List<String>) cond.get("targetColumns");
                        if (tcs == null) continue;
                        for (String tc : tcs) {
                            String derived = deriveRoleFromColumn(tc);
                            if (derived != null) { contactRoleHint = derived; break; }
                        }
                        if (contactRoleHint != null) break;
                    }

                    String sql = "SELECT * FROM " + q(conn, tableName) + " WHERE " + whereClause;
                    log.info("Custom table query: {} params={}", sql, params);

                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (int i = 0; i < params.size(); i++) {
                            ps.setString(i + 1, params.get(i));
                        }

                        try (ResultSet rs = ps.executeQuery()) {
                            List<Map<String, Object>> rows = new ArrayList<>();
                            ResultSetMetaData meta = rs.getMetaData();
                            int colCount = meta.getColumnCount();
                            boolean caseSensitive = Boolean.TRUE.equals(mapping.getColumnNameCaseSensitive());

                            while (rs.next()) {
                                // Use case-insensitive map so column mappings match regardless of DB casing
                                Map<String, Object> rawRow = caseSensitive
                                        ? new LinkedHashMap<>()
                                        : new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                                for (int i = 1; i <= colCount; i++) {
                                    rawRow.put(meta.getColumnName(i), rs.getObject(i));
                                }
                                Map<String, Object> mappedRow = applyCustomColumnMappings(rawRow, ctm);
                                log.info("Custom table '{}': mapped row keys: {}", tableName, mappedRow.keySet());
                                rows.add(mappedRow);
                            }

                            if (!rows.isEmpty()) {
                                // Check if this custom table "acts as" a standard RDAP type
                                String actsAs = (String) ctm.get("actsAs");

                                if ("contact".equalsIgnoreCase(actsAs) && domainEntity != null) {
                                    // Integrate as contact entities on the domain
                                    for (Map<String, Object> row : rows) {
                                        RdapEntity contact = buildContactFromCustomRow(row, contactRoleHint);
                                        contact.setParent(domainEntity);
                                        domainEntity.getChildren().add(contact);
                                    }
                                    log.info("Custom table '{}' (actsAs=contact): added {} contact entit{} to domain '{}'",
                                            tableName, rows.size(), rows.size() != 1 ? "ies" : "y", domainName);
                                } else {
                                    // Standard custom table result
                                    Map<String, Object> tableResult = new LinkedHashMap<>();
                                    tableResult.put("tableName", tableName);
                                    tableResult.put("label", label != null ? label : tableName);
                                    tableResult.put("description", ctm.get("description"));
                                    tableResult.put("actsAs", actsAs);
                                    tableResult.put("matchCount", rows.size());
                                    tableResult.put("data", rows);
                                    results.add(tableResult);
                                    log.info("Custom table '{}' returned {} row(s) for domain '{}'",
                                            tableName, rows.size(), domainName);
                                }
                            }
                        }
                    } catch (SQLException e) {
                        log.warn("Custom table query failed for '{}': {}", tableName, e.getMessage());
                    }
                }
            } finally {
                if (mapping.isExternal()) conn.close();
            }
        } catch (Exception e) {
            log.error("Failed to resolve custom table data for domain '{}': {}", domainName, e.getMessage(), e);
        }

        return results;
    }

    /**
     * Fetch the entire domain row as a column→value map so custom table joins
     * can reference any column without knowing in advance which ones are needed.
     */
    private Map<String, String> fetchDomainRow(Connection conn, RdapDataMapping mapping, String domainName) throws SQLException {
        String table = mapping.getEffectiveDomainsTable();
        Map<String, String> colMap = mapping.getDomainColumnMappings();
        if (colMap == null) colMap = new HashMap<>();

        String suffixCol = mapping.getDomainNameSuffixColumn();
        String nameCol = colMap.get("ldhName");
        if (nameCol == null || nameCol.isBlank()) return null;

        String sql;
        List<String> params = new ArrayList<>();

        if (suffixCol != null && !suffixCol.isBlank() && domainName.contains(".")) {
            String sep = mapping.getEffectiveDomainNameSeparator();
            int sepIdx = domainName.indexOf(sep);
            String namePart = domainName.substring(0, sepIdx);
            String suffixPart = domainName.substring(sepIdx + sep.length());
            sql = "SELECT * FROM " + q(conn, table) +
                  " WHERE " + q(conn, nameCol) + " = ? AND " + q(conn, suffixCol) + " = ? LIMIT 1";
            params.add(namePart);
            params.add(suffixPart);
        } else {
            sql = "SELECT * FROM " + q(conn, table) +
                  " WHERE " + q(conn, nameCol) + " = ? LIMIT 1";
            params.add(domainName);
        }

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setString(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // Use case-insensitive map unless the mapping explicitly requires case sensitivity
                    boolean caseSensitive = Boolean.TRUE.equals(mapping.getColumnNameCaseSensitive());
                    Map<String, String> row = caseSensitive
                            ? new LinkedHashMap<>()
                            : new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    ResultSetMetaData meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String val = rs.getString(i);
                        if (val != null) row.put(meta.getColumnName(i), val);
                    }
                    return row;
                }
            }
        }
        return null;
    }

    /**
     * Apply column mappings to a raw row, producing either standard field references or custom aliases.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> applyCustomColumnMappings(Map<String, Object> rawRow, Map<String, Object> ctm) {
        List<Map<String, Object>> columnMappings = (List<Map<String, Object>>) ctm.get("columnMappings");
        if (columnMappings == null || columnMappings.isEmpty()) {
            // No mappings defined — return raw row as-is
            return rawRow;
        }

        Map<String, Object> mapped = new LinkedHashMap<>();
        // Use the same case sensitivity as the rawRow map (TreeMap = case-insensitive)
        Set<String> mappedSourceCols = (rawRow instanceof TreeMap)
                ? new TreeSet<>(String.CASE_INSENSITIVE_ORDER)
                : new HashSet<>();

        for (Map<String, Object> cm : columnMappings) {
            String sourceColumn = (String) cm.get("sourceColumn");
            if (sourceColumn == null || !rawRow.containsKey(sourceColumn)) continue;

            Object value = rawRow.get(sourceColumn);
            String mappingType = (String) cm.getOrDefault("mappingType", "custom");

            if ("standard".equals(mappingType)) {
                String targetType = (String) cm.get("targetType");
                String targetField = (String) cm.get("targetField");
                if (targetType != null && targetField != null) {
                    mapped.put(targetType + "." + targetField, value);
                }
            } else {
                String alias = (String) cm.get("customAlias");
                if (alias != null && !alias.isBlank()) {
                    mapped.put(alias, value);
                } else {
                    mapped.put(sourceColumn, value);
                }
            }
            mappedSourceCols.add(sourceColumn);
        }

        // Include unmapped columns with their original names
        for (Map.Entry<String, Object> entry : rawRow.entrySet()) {
            if (!mappedSourceCols.contains(entry.getKey())) {
                mapped.put(entry.getKey(), entry.getValue());
            }
        }

        return mapped;
    }

    // ==================== ACTS-AS ENTITY BUILDERS ====================

    /**
     * Build a contact RdapEntity from a custom table mapped row.
     * Column mappings with mappingType=standard and targetType=contact produce keys
     * like "contact.organization", "contact.email", etc. This method extracts those
     * and populates the corresponding RdapEntity fields.
     *
     * Any keys that don't start with "contact." are treated as custom/extra data and
     * stored in the entity's remarks for visibility.
     */
    private RdapEntity buildContactFromCustomRow(Map<String, Object> mappedRow, String roleHint) {
        RdapEntity c = new RdapEntity();
        c.setObjectType(ObjectType.ENTITY);
        c.setObjectClassName("entity");

        // Extract standard contact fields from "contact.xxx" keys
        c.setHandle(str(mappedRow.get("contact.handle")));
        c.setContactName(str(mappedRow.get("contact.name")));
        c.setOrganization(str(mappedRow.get("contact.organization")));
        c.setEmail(str(mappedRow.get("contact.email")));
        c.setPhone(str(mappedRow.get("contact.phone")));
        c.setFax(str(mappedRow.get("contact.fax")));
        c.setAddressStreet1(str(mappedRow.get("contact.street1")));
        c.setAddressStreet2(str(mappedRow.get("contact.street2")));
        c.setAddressCity(str(mappedRow.get("contact.city")));
        c.setAddressState(str(mappedRow.get("contact.state")));
        c.setAddressPostalCode(str(mappedRow.get("contact.postalCode")));
        c.setAddressCountry(str(mappedRow.get("contact.country")));

        // Determine role dynamically. Priority:
        //   1. Explicit "contact.role" value mapped from the contact row.
        //   2. Role hint derived from the domain FK column this contact was
        //      joined on (e.g. r_contact → registrant, a_contact → administrative).
        // If neither is known, leave roles empty: the contact is still returned and
        // shown, but is treated as untyped rather than being forced to a role —
        // contacts can be any number and any type, driven by the data, not assumed.
        String role = str(mappedRow.get("contact.role"));
        if (role == null || role.isBlank()) {
            role = roleHint;
        }
        if (role != null && !role.isBlank()) {
            c.setRoles(new ArrayList<>(List.of(role)));
        } else {
            c.setRoles(new ArrayList<>());
        }
        c.setStatus(new ArrayList<>());
        c.setPublicIds(new ArrayList<>());
        c.setChildren(new ArrayList<>());

        // Synthetic ID — keep distinct per contact (handle + role), tolerate a null role
        String handle = c.getHandle();
        long idBase = handle != null ? (long) -(handle.hashCode() & 0x7FFFFFFF) : System.nanoTime();
        c.setId(idBase - (role != null ? role.hashCode() : 0));

        // Carry every field on the row that is NOT one of the standard contact
        // fields extracted above (which are emitted in the RDAP response). This
        // includes extended/mapped contact fields (e.g. "disclose",
        // "localOrganization") and any raw columns — so policy scope conditions can
        // reference them by their mapped name. A leading "contact." prefix is
        // stripped so a scope condition written as "disclose" matches whether the
        // column was mapped as a contact field or left as a raw column. Nothing is
        // hardcoded; this tracks whatever columns each company has mapped. These
        // values are not persisted and are not emitted in the RDAP response.
        Set<String> standardFields = Set.of(
                "handle", "name", "organization", "email", "phone", "fax",
                "street1", "street2", "city", "state", "postalCode", "country");
        Map<String, Object> customAttrs = new HashMap<>();
        for (Map.Entry<String, Object> e : mappedRow.entrySet()) {
            String key = e.getKey();
            if (key == null) continue;
            String fieldName = key.regionMatches(true, 0, "contact.", 0, 8) ? key.substring(8) : key;
            if (standardFields.contains(fieldName)) continue; // already on the entity
            customAttrs.put(fieldName, e.getValue());
        }
        c.setCustomAttributes(customAttrs);

        return c;
    }

    /**
     * Derive a canonical RDAP contact role from a domain FK column name, but ONLY
     * when the name unambiguously names a role (contains "registrant", "registrar",
     * "admin"/"administrative", "tech"/"technical", "billing", or "abuse"). Ambiguous
     * registry-specific column names (e.g. single-letter conventions like a_contact)
     * are intentionally NOT guessed — returning null leaves the contact untyped so it
     * is shown rather than mislabeled and wrongly filtered. Assign such roles
     * explicitly via a mapped contact.role value instead.
     */
    private String deriveRoleFromColumn(String column) {
        if (column == null || column.isBlank()) return null;
        String c = column.toLowerCase();
        if (c.contains("registrant")) return "registrant";
        if (c.contains("registrar")) return "registrar";
        if (c.contains("admin")) return "administrative";
        if (c.contains("tech")) return "technical";
        if (c.contains("billing")) return "billing";
        if (c.contains("abuse")) return "abuse";
        return null;
    }

    /**
     * Safely convert an Object value to a trimmed String, returning null for blanks/nulls.
     */
    private String str(Object val) {
        if (val == null) return null;
        String s = val.toString().trim();
        return s.isEmpty() ? null : s;
    }

    // ==================== EVENT HELPERS ====================

    /**
     * Build events for a mapped domain entity.
     * Since we can't store events directly on a detached entity,
     * we return them separately for the controller to use.
     */
    public List<RdapEvent> buildEventsForDomain(RdapDataMapping mapping, String domainName) {
        List<RdapEvent> events = new ArrayList<>();
        try {
            Connection conn = getConnection(mapping);
            try {
                String table = mapping.getEffectiveDomainsTable();
                Map<String, String> colMap = mapping.getDomainColumnMappings();
                if (colMap == null) colMap = new HashMap<>();
                String nameCol = colMap.get("ldhName");
                if (nameCol == null || nameCol.isBlank()) return events;

                String sql = "SELECT * FROM " + q(conn, table) + " WHERE " + q(conn, nameCol) + " = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, domainName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            LocalDateTime regDate = getDateTime(rs, colMap.get("registrationDate"));
                            if (regDate != null) {
                                RdapEvent ev = new RdapEvent();
                                ev.setEventAction(EventAction.REGISTRATION);
                                ev.setEventDate(regDate);
                                events.add(ev);
                            }

                            LocalDateTime expDate = getDateTime(rs, colMap.get("expirationDate"));
                            if (expDate != null) {
                                RdapEvent ev = new RdapEvent();
                                ev.setEventAction(EventAction.EXPIRATION);
                                ev.setEventDate(expDate);
                                events.add(ev);
                            }

                            LocalDateTime updateDate = getDateTime(rs, colMap.get("lastChangedDate"));
                            if (updateDate != null) {
                                RdapEvent ev = new RdapEvent();
                                ev.setEventAction(EventAction.LAST_CHANGED);
                                ev.setEventDate(updateDate);
                                events.add(ev);
                            }
                        }
                    }
                }
            } finally {
                if (mapping.isExternal()) conn.close();
            }
        } catch (Exception e) {
            log.error("Failed to build events for domain {}: {}", domainName, e.getMessage());
        }
        return events;
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

    private String q(Connection conn, String identifier) throws SQLException {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier cannot be null or blank");
        }
        if (!identifier.matches("^[a-zA-Z_][a-zA-Z0-9_.]*$")) {
            throw new IllegalArgumentException("Invalid identifier: " + identifier);
        }
        String quote = conn.getMetaData().getIdentifierQuoteString();
        if (quote == null || quote.isBlank() || quote.equals(" ")) quote = "\"";
        String escaped = identifier.replace(quote, quote + quote);
        return quote + escaped + quote;
    }

    private String getStr(ResultSet rs, String column) {
        if (column == null) return null;
        try {
            String val = rs.getString(column);
            return (val != null && !val.isBlank()) ? val.trim() : null;
        } catch (SQLException e) {
            // Column doesn't exist in this result set — not an error
            return null;
        }
    }

    private LocalDateTime getDateTime(ResultSet rs, String column) {
        if (column == null) return null;
        try {
            Timestamp ts = rs.getTimestamp(column);
            return ts != null ? ts.toLocalDateTime() : null;
        } catch (SQLException e) {
            return null;
        }
    }
}