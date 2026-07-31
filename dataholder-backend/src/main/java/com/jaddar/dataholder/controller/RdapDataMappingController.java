/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import com.jaddar.dataholder.entity.RdapDataMapping;
import com.jaddar.dataholder.repository.RdapDataMappingRepository;
import com.jaddar.dataholder.service.ExternalDatabaseService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/rdap-data-mappings")
@RequiredArgsConstructor
@Slf4j
public class RdapDataMappingController {

    private final RdapDataMappingRepository mappingRepository;
    private final ExternalDatabaseService externalDbService;

    // ==================== CRUD ====================

    @GetMapping
    public ResponseEntity<?> getAllMappings() {
        List<RdapDataMapping> mappings = mappingRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(Map.of("success", true, "mappings", mappings));
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActiveMappings() {
        List<RdapDataMapping> active = mappingRepository.findByIsActiveTrue();
        if (!active.isEmpty()) {
            return ResponseEntity.ok(Map.of("success", true, "mappings", active, "usingDefaults", false));
        }
        return ResponseEntity.ok(Map.of("success", true, "mappings", List.of(), "usingDefaults", true));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMapping(@PathVariable Long id) {
        Optional<RdapDataMapping> mapping = mappingRepository.findById(id);
        if (mapping.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Mapping not found"));
        }
        return ResponseEntity.ok(Map.of("success", true, "mapping", mapping.get()));
    }

    @PostMapping
    public ResponseEntity<?> createMapping(@RequestBody MappingRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Name is required"));
        }
        if (mappingRepository.existsByName(request.getName().trim())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "A mapping with this name already exists"));
        }

        RdapDataMapping mapping = buildMappingFromRequest(request);
        mapping = mappingRepository.save(mapping);
        log.info("RDAP data mapping created: {}", mapping.getName());
        return ResponseEntity.ok(Map.of("success", true, "mapping", mapping));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMapping(@PathVariable Long id, @RequestBody MappingRequest request) {
        Optional<RdapDataMapping> mappingOpt = mappingRepository.findById(id);
        if (mappingOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Mapping not found"));
        }

        RdapDataMapping mapping = mappingOpt.get();
        updateMappingFromRequest(mapping, request, id);
        mapping = mappingRepository.save(mapping);
        log.info("RDAP data mapping updated: {}", mapping.getName());
        return ResponseEntity.ok(Map.of("success", true, "mapping", mapping));
    }

    @PostMapping("/{id}/toggle-active")
    public ResponseEntity<?> toggleActive(@PathVariable Long id) {
        Optional<RdapDataMapping> mappingOpt = mappingRepository.findById(id);
        if (mappingOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Mapping not found"));
        }

        RdapDataMapping mapping = mappingOpt.get();

        if (!mapping.getIsActive()) {
            // Allow multiple active mappings — no need to deactivate others
            mapping.setIsActive(true);
            log.info("RDAP data mapping activated: {}", mapping.getName());
        } else {
            mapping.setIsActive(false);
            log.info("RDAP data mapping deactivated: {} (using defaults now)", mapping.getName());
        }

        mapping = mappingRepository.save(mapping);
        return ResponseEntity.ok(Map.of("success", true, "mapping", mapping));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMapping(@PathVariable Long id) {
        Optional<RdapDataMapping> mappingOpt = mappingRepository.findById(id);
        if (mappingOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Mapping not found"));
        }

        RdapDataMapping mapping = mappingOpt.get();
        if (mapping.getIsActive()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Cannot delete an active mapping. Deactivate it first."));
        }

        mappingRepository.delete(mapping);
        log.info("RDAP data mapping deleted: {}", mapping.getName());
        return ResponseEntity.ok(Map.of("success", true, "message", "Mapping deleted successfully"));
    }

    // ==================== DEFAULTS & FIELD DEFS ====================

    @GetMapping("/defaults")
    public ResponseEntity<?> getDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();

        defaults.put("tables", Map.of(
            "domains", "rdap_domains",
            "ips", "rdap_ips",
            "asns", "rdap_asns",
            "entities", "rdap_entities",
            "contacts", "contact",
            "hosts", "host"
        ));

        defaults.put("domainColumns", buildDefaultColumns("domain"));
        defaults.put("ipColumns", buildDefaultColumns("ip"));
        defaults.put("asnColumns", buildDefaultColumns("asn"));
        defaults.put("entityColumns", buildDefaultColumns("entity"));
        defaults.put("contactColumns", buildDefaultColumns("contact"));
        defaults.put("hostColumns", buildDefaultColumns("host"));

        // Contact roles for join mapping
        defaults.put("contactRoles", List.of(
            Map.of("role", "registrant", "description", "Registrant contact"),
            Map.of("role", "admin", "description", "Administrative contact"),
            Map.of("role", "tech", "description", "Technical contact"),
            Map.of("role", "billing", "description", "Billing contact"),
            Map.of("role", "abuse", "description", "Abuse contact"),
            Map.of("role", "registrar", "description", "Registrar contact")
        ));

        return ResponseEntity.ok(Map.of("success", true, "defaults", defaults));
    }

    // ==================== DATABASE INTROSPECTION ====================

    /**
     * Test connection to an external database (before saving).
     */
    @PostMapping("/test-connection")
    public ResponseEntity<?> testConnection(@RequestBody ConnectionTestRequest request) {
        Map<String, Object> result = externalDbService.testConnection(
            request.getJdbcUrl(), request.getUsername(), request.getPassword(), request.getDriver()
        );
        return ResponseEntity.ok(result);
    }

    /**
     * Introspect a database schema. Can introspect either:
     * - An external DB using provided connection details
     * - The local DB (if no connection details are provided)
     */
    @PostMapping("/introspect")
    public ResponseEntity<?> introspectSchema(@RequestBody IntrospectRequest request) {
        Map<String, Object> schema;
        if (request.getJdbcUrl() != null && !request.getJdbcUrl().isBlank()) {
            schema = externalDbService.introspectSchema(
                request.getJdbcUrl(), request.getUsername(), request.getPassword(),
                request.getDriver(), request.getSchemaFilter()
            );
        } else {
            schema = externalDbService.introspectLocalSchema();
        }
        return ResponseEntity.ok(Map.of("success", !schema.containsKey("error"), "schema", schema));
    }

    /**
     * Introspect and save the discovered schema into an existing mapping.
     */
    @PostMapping("/{id}/introspect")
    public ResponseEntity<?> introspectAndSave(@PathVariable Long id) {
        try {
            Map<String, Object> schema = externalDbService.introspectAndSave(id);
            return ResponseEntity.ok(Map.of("success", !schema.containsKey("error"), "schema", schema));
        } catch (Exception e) {
            log.error("Introspect-and-save failed for mapping {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Could not introspect and save the schema for this mapping. Please verify the connection details."));
        }
    }

    // ==================== AUTO-SUGGEST ====================

    /**
     * Given a list of external column names and a target object type,
     * suggest which internal fields they should map to.
     */
    @PostMapping("/suggest-mappings")
    public ResponseEntity<?> suggestMappings(@RequestBody SuggestRequest request) {
        Map<String, String> suggestions = externalDbService.suggestColumnMappings(
            request.getExternalColumns(), request.getObjectType()
        );
        return ResponseEntity.ok(Map.of("success", true, "suggestions", suggestions));
    }

    // ==================== PREVIEW ====================

    /**
     * Preview data from a saved mapping to verify it works.
     */
    @PostMapping("/{id}/preview")
    public ResponseEntity<?> previewData(@PathVariable Long id,
                                          @RequestParam(defaultValue = "domain") String objectType,
                                          @RequestParam(defaultValue = "5") int limit) {
        Optional<RdapDataMapping> mappingOpt = mappingRepository.findById(id);
        if (mappingOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Mapping not found"));
        }

        Map<String, Object> preview = externalDbService.previewMappedData(mappingOpt.get(), objectType, limit);
        return ResponseEntity.ok(preview);
    }

    /**
     * Live preview using the current (unsaved) form state.
     * Uses the saved mapping's connection details but overlays the provided column mappings.
     */
    @PostMapping("/{id}/live-preview")
    public ResponseEntity<?> livePreviewData(@PathVariable Long id,
                                              @RequestParam(defaultValue = "domain") String objectType,
                                              @RequestParam(defaultValue = "5") int limit,
                                              @RequestBody MappingRequest formState) {
        Optional<RdapDataMapping> mappingOpt = mappingRepository.findById(id);
        if (mappingOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Mapping not found"));
        }

        // Clone the saved mapping and overlay the form state's tables + column mappings
        RdapDataMapping mapping = mappingOpt.get();
        if (formState.getDomainsTable() != null) mapping.setDomainsTable(formState.getDomainsTable());
        if (formState.getContactsTable() != null) mapping.setContactsTable(formState.getContactsTable());
        if (formState.getHostsTable() != null) mapping.setHostsTable(formState.getHostsTable());
        if (formState.getIpsTable() != null) mapping.setIpsTable(formState.getIpsTable());
        if (formState.getAsnsTable() != null) mapping.setAsnsTable(formState.getAsnsTable());
        if (formState.getEntitiesTable() != null) mapping.setEntitiesTable(formState.getEntitiesTable());
        if (formState.getDomainColumnMappings() != null) mapping.setDomainColumnMappings(formState.getDomainColumnMappings());
        if (formState.getContactColumnMappings() != null) mapping.setContactColumnMappings(formState.getContactColumnMappings());
        if (formState.getHostColumnMappings() != null) mapping.setHostColumnMappings(formState.getHostColumnMappings());
        if (formState.getIpColumnMappings() != null) mapping.setIpColumnMappings(formState.getIpColumnMappings());
        if (formState.getAsnColumnMappings() != null) mapping.setAsnColumnMappings(formState.getAsnColumnMappings());
        if (formState.getEntityColumnMappings() != null) mapping.setEntityColumnMappings(formState.getEntityColumnMappings());
        if (formState.getDomainNameSuffixColumn() != null) mapping.setDomainNameSuffixColumn(formState.getDomainNameSuffixColumn());
        if (formState.getDomainNameSeparator() != null) mapping.setDomainNameSeparator(formState.getDomainNameSeparator());
        if (formState.getCustomTableMappings() != null) mapping.setCustomTableMappings(formState.getCustomTableMappings());

        Map<String, Object> preview = externalDbService.previewMappedData(mapping, objectType, limit);
        return ResponseEntity.ok(preview);
    }

    // ==================== DATA BROWSING ====================

    /**
     * List all tables in the mapped database.
     */
    @GetMapping("/{id}/tables")
    public ResponseEntity<?> listTables(@PathVariable Long id) {
        Optional<RdapDataMapping> mappingOpt = mappingRepository.findById(id);
        if (mappingOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Mapping not found"));
        }
        Map<String, Object> result = externalDbService.listTables(mappingOpt.get());
        return ResponseEntity.ok(result);
    }

    /**
     * Browse data from a specific table with pagination, search, and sort.
     */
    @GetMapping("/{id}/browse/{tableName}")
    public ResponseEntity<?> browseTable(
            @PathVariable Long id,
            @PathVariable String tableName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(defaultValue = "asc") String sortDir) {
        Optional<RdapDataMapping> mappingOpt = mappingRepository.findById(id);
        if (mappingOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Mapping not found"));
        }
        Map<String, Object> result = externalDbService.browseTable(
                mappingOpt.get(), tableName, page, pageSize, search, sortColumn, sortDir);
        return ResponseEntity.ok(result);
    }

    // ==================== HELPERS ====================

    private RdapDataMapping buildMappingFromRequest(MappingRequest request) {
        return RdapDataMapping.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .isActive(false)
                .dbType(request.getDbType() != null ? request.getDbType() : "LOCAL")
                .externalJdbcUrl(request.getExternalJdbcUrl())
                .externalDbUsername(request.getExternalDbUsername())
                .externalDbPassword(request.getExternalDbPassword())
                .externalDbDriver(request.getExternalDbDriver())
                .externalDbSchema(request.getExternalDbSchema())
                .columnNameCaseSensitive(request.getColumnNameCaseSensitive() != null ? request.getColumnNameCaseSensitive() : false)
                .domainsTable(request.getDomainsTable())
                .ipsTable(request.getIpsTable())
                .asnsTable(request.getAsnsTable())
                .entitiesTable(request.getEntitiesTable())
                .contactsTable(request.getContactsTable())
                .hostsTable(request.getHostsTable())
                .domainColumnMappings(request.getDomainColumnMappings())
                .ipColumnMappings(request.getIpColumnMappings())
                .asnColumnMappings(request.getAsnColumnMappings())
                .entityColumnMappings(request.getEntityColumnMappings())
                .contactColumnMappings(request.getContactColumnMappings())
                .hostColumnMappings(request.getHostColumnMappings())
                .domainContactJoinMappings(request.getDomainContactJoinMappings())
                .contactJoinKey(request.getContactJoinKey())
                .hostJoinConfig(request.getHostJoinConfig())
                .domainNameSuffixColumn(request.getDomainNameSuffixColumn())
                .domainNameSeparator(request.getDomainNameSeparator())
                .customTableMappings(request.getCustomTableMappings())
                .build();
    }

    private void updateMappingFromRequest(RdapDataMapping mapping, MappingRequest request, Long id) {
        if (request.getName() != null) {
            if (mappingRepository.existsByNameAndIdNot(request.getName().trim(), id)) {
                throw new IllegalArgumentException("A mapping with this name already exists");
            }
            mapping.setName(request.getName().trim());
        }
        if (request.getDescription() != null) mapping.setDescription(request.getDescription());
        if (request.getDbType() != null) mapping.setDbType(request.getDbType());
        if (request.getExternalJdbcUrl() != null) mapping.setExternalJdbcUrl(request.getExternalJdbcUrl());
        if (request.getExternalDbUsername() != null) mapping.setExternalDbUsername(request.getExternalDbUsername());
        if (request.getExternalDbPassword() != null) mapping.setExternalDbPassword(request.getExternalDbPassword());
        if (request.getExternalDbDriver() != null) mapping.setExternalDbDriver(request.getExternalDbDriver());
        if (request.getExternalDbSchema() != null) mapping.setExternalDbSchema(request.getExternalDbSchema());
        if (request.getColumnNameCaseSensitive() != null) mapping.setColumnNameCaseSensitive(request.getColumnNameCaseSensitive());
        if (request.getDomainsTable() != null) mapping.setDomainsTable(request.getDomainsTable());
        if (request.getIpsTable() != null) mapping.setIpsTable(request.getIpsTable());
        if (request.getAsnsTable() != null) mapping.setAsnsTable(request.getAsnsTable());
        if (request.getEntitiesTable() != null) mapping.setEntitiesTable(request.getEntitiesTable());
        if (request.getContactsTable() != null) mapping.setContactsTable(request.getContactsTable());
        if (request.getHostsTable() != null) mapping.setHostsTable(request.getHostsTable());
        if (request.getDomainColumnMappings() != null) mapping.setDomainColumnMappings(request.getDomainColumnMappings());
        if (request.getIpColumnMappings() != null) mapping.setIpColumnMappings(request.getIpColumnMappings());
        if (request.getAsnColumnMappings() != null) mapping.setAsnColumnMappings(request.getAsnColumnMappings());
        if (request.getEntityColumnMappings() != null) mapping.setEntityColumnMappings(request.getEntityColumnMappings());
        if (request.getContactColumnMappings() != null) mapping.setContactColumnMappings(request.getContactColumnMappings());
        if (request.getHostColumnMappings() != null) mapping.setHostColumnMappings(request.getHostColumnMappings());
        if (request.getDomainContactJoinMappings() != null) mapping.setDomainContactJoinMappings(request.getDomainContactJoinMappings());
        if (request.getContactJoinKey() != null) mapping.setContactJoinKey(request.getContactJoinKey());
        if (request.getHostJoinConfig() != null) mapping.setHostJoinConfig(request.getHostJoinConfig());
        if (request.getDomainNameSuffixColumn() != null) mapping.setDomainNameSuffixColumn(request.getDomainNameSuffixColumn());
        if (request.getDomainNameSeparator() != null) mapping.setDomainNameSeparator(request.getDomainNameSeparator());
        if (request.getCustomTableMappings() != null) mapping.setCustomTableMappings(request.getCustomTableMappings());
    }

    private List<Map<String, String>> buildDefaultColumns(String type) {
        List<Map<String, String>> columns = new ArrayList<>();

        switch (type) {
            case "domain" -> {
                columns.add(Map.of("field", "ldhName", "column", "ldh_name", "description", "Domain name (LDH format)"));
                columns.add(Map.of("field", "handle", "column", "handle", "description", "Registry object ID (ROID)"));
                columns.add(Map.of("field", "status", "column", "status", "description", "EPP status codes"));
                columns.add(Map.of("field", "registrationDate", "column", "registration_date", "description", "Registration/application date"));
                columns.add(Map.of("field", "expirationDate", "column", "expiration_date", "description", "Expiration date"));
                columns.add(Map.of("field", "lastChangedDate", "column", "last_changed_date", "description", "Last update date"));
                columns.add(Map.of("field", "nameservers", "column", "nameservers", "description", "Nameservers (comma-separated or JSON)"));
                columns.add(Map.of("field", "dnssecEnabled", "column", "dnssec_enabled", "description", "DNSSEC delegation signed data"));
                columns.add(Map.of("field", "registrantContact", "column", "r_contact", "description", "Registrant contact FK"));
                columns.add(Map.of("field", "adminContact", "column", "a_contact", "description", "Admin contact FK"));
                columns.add(Map.of("field", "techContact", "column", "t_contact", "description", "Tech contact FK"));
                columns.add(Map.of("field", "billingContact", "column", "b_contact", "description", "Billing contact FK"));
                columns.add(Map.of("field", "registrarId", "column", "registrar_id", "description", "Registrar identifier"));
                columns.add(Map.of("field", "sld", "column", "sld", "description", "Second-level domain / zone"));
            }
            case "contact" -> {
                columns.add(Map.of("field", "handle", "column", "id", "description", "Contact handle/ID"));
                columns.add(Map.of("field", "name", "column", "ename", "description", "Contact name (English)"));
                columns.add(Map.of("field", "localName", "column", "cname", "description", "Contact name (local script)"));
                columns.add(Map.of("field", "organization", "column", "cmp_ename", "description", "Organization name (English)"));
                columns.add(Map.of("field", "localOrganization", "column", "cmp_cname", "description", "Organization name (local script)"));
                columns.add(Map.of("field", "email", "column", "email", "description", "Email address"));
                columns.add(Map.of("field", "phone", "column", "tel", "description", "Telephone number"));
                columns.add(Map.of("field", "fax", "column", "fax", "description", "Fax number"));
                columns.add(Map.of("field", "street1", "column", "eaddr1", "description", "Street address line 1 (English)"));
                columns.add(Map.of("field", "street2", "column", "eaddr2", "description", "Street address line 2 (English)"));
                columns.add(Map.of("field", "street3", "column", "eaddr3", "description", "Street address line 3 (English)"));
                columns.add(Map.of("field", "city", "column", "ecity", "description", "City (English)"));
                columns.add(Map.of("field", "state", "column", "estate", "description", "State/Province (English)"));
                columns.add(Map.of("field", "postalCode", "column", "postal", "description", "Postal code"));
                columns.add(Map.of("field", "country", "column", "country", "description", "Country code"));
                columns.add(Map.of("field", "localStreet1", "column", "caddr", "description", "Street address (local script)"));
                columns.add(Map.of("field", "localStreet2", "column", "caddr2", "description", "Street line 2 (local script)"));
                columns.add(Map.of("field", "localCity", "column", "ccity", "description", "City (local script)"));
                columns.add(Map.of("field", "localState", "column", "cstate", "description", "State (local script)"));
                columns.add(Map.of("field", "localPostalCode", "column", "cpostal", "description", "Postal code (local)"));
                columns.add(Map.of("field", "localCountry", "column", "ccountry", "description", "Country (local)"));
                columns.add(Map.of("field", "disclose", "column", "disclose", "description", "Disclosure flags"));
                columns.add(Map.of("field", "createdDate", "column", "create_date", "description", "Created timestamp"));
                columns.add(Map.of("field", "updatedDate", "column", "update_date", "description", "Updated timestamp"));
            }
            case "host" -> {
                columns.add(Map.of("field", "handle", "column", "roid", "description", "Host registry object ID"));
                columns.add(Map.of("field", "hostName", "column", "host_name", "description", "Fully qualified host name"));
                columns.add(Map.of("field", "ipv4", "column", "ip_addr4", "description", "IPv4 address(es)"));
                columns.add(Map.of("field", "ipv6", "column", "ip_addr6", "description", "IPv6 address(es)"));
                columns.add(Map.of("field", "createdDate", "column", "create_date", "description", "Created timestamp"));
                columns.add(Map.of("field", "updatedDate", "column", "update_date", "description", "Updated timestamp"));
            }
            default -> {
                // Common columns for ip, asn, entity
                columns.add(Map.of("field", "id", "column", "id", "description", "Primary key"));
                columns.add(Map.of("field", "handle", "column", "handle", "description", "Registry handle"));
                columns.add(Map.of("field", "status", "column", "status", "description", "Status array"));
                columns.add(Map.of("field", "registrationDate", "column", "registration_date", "description", "Registration date"));
                columns.add(Map.of("field", "lastChangedDate", "column", "last_changed_date", "description", "Last changed date"));
                if ("ip".equals(type)) {
                    columns.add(Map.of("field", "startAddress", "column", "start_address", "description", "Start IP address"));
                    columns.add(Map.of("field", "endAddress", "column", "end_address", "description", "End IP address"));
                    columns.add(Map.of("field", "ipVersion", "column", "ip_version", "description", "IP version (v4/v6)"));
                    columns.add(Map.of("field", "name", "column", "name", "description", "Network name"));
                    columns.add(Map.of("field", "country", "column", "country", "description", "Country code"));
                } else if ("asn".equals(type)) {
                    columns.add(Map.of("field", "startAutnum", "column", "start_autnum", "description", "Start AS number"));
                    columns.add(Map.of("field", "endAutnum", "column", "end_autnum", "description", "End AS number"));
                    columns.add(Map.of("field", "name", "column", "name", "description", "AS name"));
                    columns.add(Map.of("field", "country", "column", "country", "description", "Country code"));
                }
            }
        }

        return columns;
    }

    // ==================== REQUEST DTOs ====================

    @Data
    public static class MappingRequest {
        private String name;
        private String description;
        private String dbType;
        private String externalJdbcUrl;
        private String externalDbUsername;
        private String externalDbPassword;
        private String externalDbDriver;
        private String externalDbSchema;
        private Boolean columnNameCaseSensitive;
        private String domainsTable;
        private String ipsTable;
        private String asnsTable;
        private String entitiesTable;
        private String contactsTable;
        private String hostsTable;
        private Map<String, String> domainColumnMappings;
        private Map<String, String> ipColumnMappings;
        private Map<String, String> asnColumnMappings;
        private Map<String, String> entityColumnMappings;
        private Map<String, String> contactColumnMappings;
        private Map<String, String> hostColumnMappings;
        private Map<String, String> domainContactJoinMappings;
        private String contactJoinKey;
        private Map<String, String> hostJoinConfig;
        private String domainNameSuffixColumn;
        private String domainNameSeparator;
        private List<Map<String, Object>> customTableMappings;
    }

    @Data
    public static class ConnectionTestRequest {
        private String jdbcUrl;
        private String username;
        private String password;
        private String driver;
    }

    @Data
    public static class IntrospectRequest {
        private String jdbcUrl;
        private String username;
        private String password;
        private String driver;
        private String schemaFilter;
    }

    @Data
    public static class SuggestRequest {
        private List<String> externalColumns;
        private String objectType;
    }
}
