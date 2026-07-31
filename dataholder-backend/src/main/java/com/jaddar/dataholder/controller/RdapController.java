/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import com.jaddar.dataholder.dto.RdapDataDto.*;
import com.jaddar.dataholder.dto.RdapDto.PendingRequestDto;
import com.jaddar.dataholder.entity.*;
import com.jaddar.dataholder.entity.RdapEntity.ObjectType;
import com.jaddar.dataholder.service.PendingRequestService;
import com.jaddar.dataholder.service.RdapRedactionService;
import com.jaddar.dataholder.service.RdapService;
import com.jaddar.dataholder.service.RdapService.RdapQueryResult;
import com.jaddar.dataholder.service.MappedRdapQueryService;
import com.jaddar.dataholder.service.TokenIntrospectionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class RdapController {

    private final RdapService rdapService;
    private final PendingRequestService pendingRequestService;
    private final TokenIntrospectionService tokenIntrospectionService;
    private final RdapRedactionService rdapRedactionService;
    private final MappedRdapQueryService mappedRdapQueryService;
    private final com.jaddar.dataholder.service.ExternalDatabaseService externalDatabaseService;
    private final com.jaddar.dataholder.repository.RdapDataMappingRepository mappingRepository;
    private final com.jaddar.dataholder.service.FileSecurityService fileSecurityService;

    private static final MediaType RDAP_JSON = MediaType.parseMediaType("application/rdap+json");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    // ==================== PUBLIC RDAP QUERIES ====================

    @GetMapping(value = "/domain/{domain}", produces = "application/rdap+json")
    public ResponseEntity<?> queryDomain(
            @PathVariable String domain,
            @RequestParam(required = false) String agreements,
            @RequestParam(required = false) String dataHolderGroup,
            @RequestParam(required = false) String requestorGroup,
            @RequestParam(required = false) Integer requestType,
            @RequestParam(required = false, defaultValue = "false") boolean jakeCompliance,
            @RequestParam(required = false, defaultValue = "false") boolean confidential,
            @RequestParam(required = false, defaultValue = "false") boolean exigent,
            @RequestParam(required = false) String customParams,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        Map<String, Object> parsedCustomParams = parseCustomParams(customParams);
        RdapQueryResult result;
        if (requestorGroup != null && requestType != null) {
            result = rdapService.queryDomainByCodes(
                domain, dataHolderGroup, requestorGroup, requestType,
                confidential, exigent, jakeCompliance,
                authHeader, getClientIp(request), parsedCustomParams);
        } else {
            result = rdapService.queryDomain(
                domain, parseAgreements(agreements),
                confidential, exigent, jakeCompliance,
                authHeader, getClientIp(request), parsedCustomParams);
        }
        result.setJakeCompliance(jakeCompliance);
        result.setRequestorGroupCode(requestorGroup);
        return toResponse(result);
    }

    @GetMapping(value = "/ip/{ip}", produces = "application/rdap+json")
    public ResponseEntity<?> queryIp(
            @PathVariable String ip,
            @RequestParam(required = false) String agreements,
            @RequestParam(required = false) String dataHolderGroup,
            @RequestParam(required = false) String requestorGroup,
            @RequestParam(required = false) Integer requestType,
            @RequestParam(required = false, defaultValue = "false") boolean jakeCompliance,
            @RequestParam(required = false, defaultValue = "false") boolean confidential,
            @RequestParam(required = false, defaultValue = "false") boolean exigent,
            @RequestParam(required = false) String customParams,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        Map<String, Object> parsedCustomParams = parseCustomParams(customParams);
        RdapQueryResult result;
        if (requestorGroup != null && requestType != null) {
            result = rdapService.queryIpByCodes(
                ip, dataHolderGroup, requestorGroup, requestType,
                confidential, exigent, jakeCompliance,
                authHeader, getClientIp(request), parsedCustomParams);
        } else {
            result = rdapService.queryIp(
                ip, parseAgreements(agreements),
                confidential, exigent, jakeCompliance,
                authHeader, getClientIp(request), parsedCustomParams);
        }
        result.setJakeCompliance(jakeCompliance);
        result.setRequestorGroupCode(requestorGroup);
        return toResponse(result);
    }

    @GetMapping(value = "/autnum/{asn}", produces = "application/rdap+json")
    public ResponseEntity<?> queryAsn(
            @PathVariable String asn,
            @RequestParam(required = false) String agreements,
            @RequestParam(required = false) String dataHolderGroup,
            @RequestParam(required = false) String requestorGroup,
            @RequestParam(required = false) Integer requestType,
            @RequestParam(required = false, defaultValue = "false") boolean jakeCompliance,
            @RequestParam(required = false, defaultValue = "false") boolean confidential,
            @RequestParam(required = false, defaultValue = "false") boolean exigent,
            @RequestParam(required = false) String customParams,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        Map<String, Object> parsedCustomParams = parseCustomParams(customParams);
        RdapQueryResult result;
        if (requestorGroup != null && requestType != null) {
            result = rdapService.queryAsnByCodes(
                asn, dataHolderGroup, requestorGroup, requestType,
                confidential, exigent, jakeCompliance,
                authHeader, getClientIp(request), parsedCustomParams);
        } else {
            result = rdapService.queryAsn(
                asn, parseAgreements(agreements),
                confidential, exigent, jakeCompliance,
                authHeader, getClientIp(request), parsedCustomParams);
        }
        result.setJakeCompliance(jakeCompliance);
        result.setRequestorGroupCode(requestorGroup);
        return toResponse(result);
    }

    @GetMapping(value = "/api/rdap/domain/{domain}", produces = "application/json")
    public ResponseEntity<?> apiQueryDomain(
            @PathVariable String domain,
            @RequestParam(required = false) String agreements,
            @RequestParam(required = false) String dataHolderGroup,
            @RequestParam(required = false) String requestorGroup,
            @RequestParam(required = false) Integer requestType,
            @RequestParam(required = false, defaultValue = "false") boolean jakeCompliance,
            @RequestParam(required = false, defaultValue = "false") boolean confidential,
            @RequestParam(required = false, defaultValue = "false") boolean exigent,
            @RequestParam(required = false) String customParams,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        return queryDomain(domain, agreements, dataHolderGroup, requestorGroup, requestType,
                jakeCompliance, confidential, exigent, customParams, authHeader, request);
    }

    @GetMapping(value = "/api/rdap/ip/{ip}", produces = "application/json")
    public ResponseEntity<?> apiQueryIp(
            @PathVariable String ip,
            @RequestParam(required = false) String agreements,
            @RequestParam(required = false) String dataHolderGroup,
            @RequestParam(required = false) String requestorGroup,
            @RequestParam(required = false) Integer requestType,
            @RequestParam(required = false, defaultValue = "false") boolean jakeCompliance,
            @RequestParam(required = false, defaultValue = "false") boolean confidential,
            @RequestParam(required = false, defaultValue = "false") boolean exigent,
            @RequestParam(required = false) String customParams,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        return queryIp(ip, agreements, dataHolderGroup, requestorGroup, requestType,
                jakeCompliance, confidential, exigent, customParams, authHeader, request);
    }

    @GetMapping(value = "/api/rdap/autnum/{asn}", produces = "application/json")
    public ResponseEntity<?> apiQueryAsn(
            @PathVariable String asn,
            @RequestParam(required = false) String agreements,
            @RequestParam(required = false) String dataHolderGroup,
            @RequestParam(required = false) String requestorGroup,
            @RequestParam(required = false) Integer requestType,
            @RequestParam(required = false, defaultValue = "false") boolean jakeCompliance,
            @RequestParam(required = false, defaultValue = "false") boolean confidential,
            @RequestParam(required = false, defaultValue = "false") boolean exigent,
            @RequestParam(required = false) String customParams,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        return queryAsn(asn, agreements, dataHolderGroup, requestorGroup, requestType,
                jakeCompliance, confidential, exigent, customParams, authHeader, request);
    }

    @GetMapping(value = "/api/rdap/status/{requestId}", produces = "application/json")
    public ResponseEntity<?> checkRequestStatus(@PathVariable String requestId) {
        try { return toResponse(rdapService.checkPendingRequest(UUID.fromString(requestId))); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid request ID")); }
    }

    @GetMapping(value = "/api/rdap/domains", produces = "application/json")
    public ResponseEntity<List<String>> getAvailableDomains() { return ResponseEntity.ok(rdapService.getAvailableDomains()); }

    @GetMapping(value = "/api/rdap/agreements", produces = "application/json")
    public ResponseEntity<List<Map<String, Object>>> getAvailableAgreements() { return ResponseEntity.ok(rdapService.getAvailableAgreements()); }

    @GetMapping(value = "/api/rdap/my-agreements", produces = "application/json")
    public ResponseEntity<List<Map<String, Object>>> getMyAgreements(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ResponseEntity.ok(rdapService.getAgreementsForUser(authHeader));
    }

    /**
     * Returns the data holder's JAKE compliance data:
     * group memberships, published templates with request types, active subscriptions.
     */
    @GetMapping(value = "/api/rdap/jake-compliance", produces = "application/json")
    public ResponseEntity<Map<String, Object>> getJakeCompliance() {
        return ResponseEntity.ok(rdapService.buildJakeComplianceBlock(null));
    }

    // ==================== MAPPED DATA VIEWING ====================

    /**
     * Get the active mapping info for the frontend to know whether to show the mapped tab.
     * Returns all active mappings so the frontend can display and select between them.
     */
    @GetMapping("/api/admin/rdap/mapped/status")
    public ResponseEntity<?> getMappedDataStatus() {
        List<RdapDataMapping> activeMappings = mappedRdapQueryService.getActiveMappings();
        if (!activeMappings.isEmpty()) {
            List<Map<String, Object>> mappingInfos = activeMappings.stream().map(m -> {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("mappingId", m.getId());
                info.put("mappingName", m.getName());
                info.put("dbType", m.getDbType());
                info.put("domainsTable", m.getEffectiveDomainsTable());
                info.put("contactsTable", m.getEffectiveContactsTable());
                info.put("hostsTable", m.getEffectiveHostsTable());
                return info;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "active", true,
                "mappings", mappingInfos
            ));
        }
        return ResponseEntity.ok(Map.of("active", false, "mappings", List.of()));
    }

    /**
     * Get mapped domain data with pagination and search.
     * Returns domain records from a specific active mapped database translated to our field names.
     * If mappingId is not provided, uses the first active mapping.
     */
    @GetMapping("/api/admin/rdap/mapped/domains")
    public ResponseEntity<?> getMappedDomains(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long mappingId) {
        RdapDataMapping mapping;
        if (mappingId != null) {
            Optional<RdapDataMapping> opt = mappingRepository.findById(mappingId);
            if (opt.isEmpty() || !opt.get().getIsActive()) {
                return ResponseEntity.ok(Map.of("success", false, "error", "Mapping not found or not active"));
            }
            mapping = opt.get();
        } else {
            List<RdapDataMapping> activeMappings = mappedRdapQueryService.getActiveMappings();
            if (activeMappings.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", false, "error", "No active mapping"));
            }
            mapping = activeMappings.get(0);
        }

        try {
            String table = mapping.getEffectiveDomainsTable();
            Map<String, Object> browseResult = externalDatabaseService.browseTable(
                    mapping, table, page, size, search, null, "asc");

            if (!Boolean.TRUE.equals(browseResult.get("success"))) {
                return ResponseEntity.ok(Map.of("success", false, "error", browseResult.get("error")));
            }

            // Translate raw rows to our field names using the column mappings
            Map<String, String> colMap = mapping.getDomainColumnMappings();
            if (colMap == null) colMap = new HashMap<>();

            // Build reverse map: externalCol -> internalField
            Map<String, String> reverseMap = new HashMap<>();
            colMap.forEach((internal, external) -> reverseMap.put(external, internal));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawRows = (List<Map<String, Object>>) browseResult.get("rows");

            List<Map<String, Object>> domains = new ArrayList<>();
            String suffixCol = mapping.getDomainNameSuffixColumn();
            String sep = mapping.getEffectiveDomainNameSeparator();

            for (Map<String, Object> raw : rawRows) {
                Map<String, Object> d = new LinkedHashMap<>();
                raw.forEach((col, val) -> {
                    String mapped = reverseMap.getOrDefault(col, col);
                    d.put(mapped, val);
                });

                // Compose full ldhName from name + suffix if configured
                if (suffixCol != null && !suffixCol.isBlank()) {
                    Object nameVal = d.get("ldhName");
                    Object suffixVal = raw.get(suffixCol);
                    if (nameVal != null && suffixVal != null && !suffixVal.toString().isBlank()) {
                        d.put("ldhName", nameVal + sep + suffixVal);
                    }
                }

                // Add contact role references if join mappings exist
                Map<String, String> joinMap = mapping.getDomainContactJoinMappings();
                if (joinMap != null) {
                    for (Map.Entry<String, String> entry : joinMap.entrySet()) {
                        Object fkVal = raw.get(entry.getValue());
                        if (fkVal != null) d.put(entry.getKey() + "Contact", fkVal);
                    }
                }
                domains.add(d);
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "domains", domains,
                "page", browseResult.get("page"),
                "pageSize", browseResult.get("pageSize"),
                "totalRows", browseResult.get("totalRows"),
                "totalPages", browseResult.get("totalPages"),
                "mappingName", mapping.getName()
            ));
        } catch (Exception e) {
            log.error("Failed to query mapped domains", e);
            return ResponseEntity.ok(Map.of("success", false, "error", "Could not load mapped domain data. Please verify the mapping and connection details."));
        }
    }

    private String getColStr(java.sql.ResultSet rs, String col) {
        try {
            String v = rs.getString(col);
            return v != null ? v.trim() : null;
        } catch (Exception e) { return null; }
    }

    // ==================== ADMIN CRUD - DOMAINS ====================

    @GetMapping("/api/admin/rdap/domains")
    public ResponseEntity<Page<RdapEntity>> getDomains(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ldhName") String sortBy, @RequestParam(defaultValue = "asc") String sortDir, @RequestParam(required = false) String search) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        return ResponseEntity.ok(rdapService.getDomains(PageRequest.of(page, size, sort), search));
    }

    @PostMapping("/api/admin/rdap/domains")
    public ResponseEntity<?> createDomain(@RequestBody RdapEntityRequest request) {
        try { request.setObjectType(ObjectType.DOMAIN); return ResponseEntity.ok(Map.of("success", true, "message", "Domain created", "entity", rdapService.createEntity(request))); }
        catch (Exception e) { log.error("Create domain failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    @PutMapping("/api/admin/rdap/domains/{id}")
    public ResponseEntity<?> updateDomain(@PathVariable Long id, @RequestBody RdapEntityRequest request) {
        try { request.setObjectType(ObjectType.DOMAIN); return ResponseEntity.ok(Map.of("success", true, "message", "Domain updated", "entity", rdapService.updateEntity(id, request))); }
        catch (Exception e) { log.error("Update domain failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    @DeleteMapping("/api/admin/rdap/domains/{id}")
    public ResponseEntity<?> deleteDomain(@PathVariable Long id) {
        try { rdapService.deleteEntity(id); return ResponseEntity.ok(Map.of("success", true, "message", "Domain deleted")); }
        catch (Exception e) { log.error("Request failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    @DeleteMapping("/api/admin/rdap/domains/bulk")
    public ResponseEntity<?> bulkDeleteDomains(@RequestBody List<Long> ids) {
        try { int c = rdapService.bulkDeleteEntities(ids); return ResponseEntity.ok(Map.of("success", true, "message", "Deleted " + c + " domains", "deletedCount", c)); }
        catch (Exception e) { log.error("Request failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    // ==================== ADMIN CRUD - IPs ====================

    @GetMapping("/api/admin/rdap/ips")
    public ResponseEntity<Page<RdapEntity>> getIps(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "handle") String sortBy, @RequestParam(defaultValue = "asc") String sortDir, @RequestParam(required = false) String search) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        return ResponseEntity.ok(rdapService.getIpNetworks(PageRequest.of(page, size, sort), search));
    }

    @PostMapping("/api/admin/rdap/ips")
    public ResponseEntity<?> createIp(@RequestBody RdapEntityRequest request) {
        try { request.setObjectType(ObjectType.IP_NETWORK); return ResponseEntity.ok(Map.of("success", true, "message", "IP created", "entity", rdapService.createEntity(request))); }
        catch (Exception e) { log.error("Create IP failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    @PutMapping("/api/admin/rdap/ips/{id}")
    public ResponseEntity<?> updateIp(@PathVariable Long id, @RequestBody RdapEntityRequest request) {
        try { request.setObjectType(ObjectType.IP_NETWORK); return ResponseEntity.ok(Map.of("success", true, "message", "IP updated", "entity", rdapService.updateEntity(id, request))); }
        catch (Exception e) { log.error("Update IP failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    @DeleteMapping("/api/admin/rdap/ips/{id}")
    public ResponseEntity<?> deleteIp(@PathVariable Long id) {
        try { rdapService.deleteEntity(id); return ResponseEntity.ok(Map.of("success", true, "message", "IP deleted")); }
        catch (Exception e) { log.error("Request failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    @DeleteMapping("/api/admin/rdap/ips/bulk")
    public ResponseEntity<?> bulkDeleteIps(@RequestBody List<Long> ids) {
        try { int c = rdapService.bulkDeleteEntities(ids); return ResponseEntity.ok(Map.of("success", true, "message", "Deleted " + c + " IPs", "deletedCount", c)); }
        catch (Exception e) { log.error("Request failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    // ==================== ADMIN CRUD - ASNs ====================

    @GetMapping("/api/admin/rdap/asns")
    public ResponseEntity<Page<RdapEntity>> getAsns(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "handle") String sortBy, @RequestParam(defaultValue = "asc") String sortDir, @RequestParam(required = false) String search) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        return ResponseEntity.ok(rdapService.getAutnums(PageRequest.of(page, size, sort), search));
    }

    @PostMapping("/api/admin/rdap/asns")
    public ResponseEntity<?> createAsn(@RequestBody RdapEntityRequest request) {
        try { request.setObjectType(ObjectType.AUTNUM); return ResponseEntity.ok(Map.of("success", true, "message", "ASN created", "entity", rdapService.createEntity(request))); }
        catch (Exception e) { log.error("Create ASN failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    @PutMapping("/api/admin/rdap/asns/{id}")
    public ResponseEntity<?> updateAsn(@PathVariable Long id, @RequestBody RdapEntityRequest request) {
        try { request.setObjectType(ObjectType.AUTNUM); return ResponseEntity.ok(Map.of("success", true, "message", "ASN updated", "entity", rdapService.updateEntity(id, request))); }
        catch (Exception e) { log.error("Update ASN failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    @DeleteMapping("/api/admin/rdap/asns/{id}")
    public ResponseEntity<?> deleteAsn(@PathVariable Long id) {
        try { rdapService.deleteEntity(id); return ResponseEntity.ok(Map.of("success", true, "message", "ASN deleted")); }
        catch (Exception e) { log.error("Request failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    @DeleteMapping("/api/admin/rdap/asns/bulk")
    public ResponseEntity<?> bulkDeleteAsns(@RequestBody List<Long> ids) {
        try { int c = rdapService.bulkDeleteEntities(ids); return ResponseEntity.ok(Map.of("success", true, "message", "Deleted " + c + " ASNs", "deletedCount", c)); }
        catch (Exception e) { log.error("Request failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    // ==================== ADMIN - ENTITIES ====================

    @GetMapping("/api/admin/rdap/entities/{id}")
    public ResponseEntity<?> getEntityById(@PathVariable Long id) {
        return rdapService.getEntityById(id).map(e -> ResponseEntity.ok(Map.of(
                "success", true,
                "entity", e,
                "children", rdapService.getChildrenForEntity(id),
                "events", rdapService.getEventsForEntity(id),
                "links", rdapService.getLinksForEntity(id),
                "nameservers", rdapService.getNameserversForEntity(id),
                "remarks", rdapService.getRemarksForEntity(id),
                "secureDns", rdapService.getSecureDnsForEntity(id))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/admin/rdap/entities/{id}/events")
    public ResponseEntity<?> getEntityEvents(@PathVariable Long id) { return ResponseEntity.ok(Map.of("success", true, "events", rdapService.getEventsForEntity(id))); }

    @GetMapping("/api/admin/rdap/entities/{id}/links")
    public ResponseEntity<?> getEntityLinks(@PathVariable Long id) { return ResponseEntity.ok(Map.of("success", true, "links", rdapService.getLinksForEntity(id))); }

    @GetMapping("/api/admin/rdap/entities/{id}/nameservers")
    public ResponseEntity<?> getEntityNameservers(@PathVariable Long id) { return ResponseEntity.ok(Map.of("success", true, "nameservers", rdapService.getNameserversForEntity(id))); }

    @GetMapping("/api/admin/rdap/entities/{id}/remarks")
    public ResponseEntity<?> getEntityRemarks(@PathVariable Long id) { return ResponseEntity.ok(Map.of("success", true, "remarks", rdapService.getRemarksForEntity(id))); }

    @GetMapping("/api/admin/rdap/entities/{id}/secure-dns")
    public ResponseEntity<?> getEntitySecureDns(@PathVariable Long id) { return ResponseEntity.ok(Map.of("success", true, "secureDns", rdapService.getSecureDnsForEntity(id))); }

    // ==================== ADMIN - STATS & SCHEMA ====================

    @GetMapping("/api/admin/rdap/stats")
    public ResponseEntity<?> getStats() { return ResponseEntity.ok(rdapService.getStats()); }

    @GetMapping("/api/admin/rdap/schema/{type}")
    public ResponseEntity<?> getSchema(@PathVariable String type) { return ResponseEntity.ok(Map.of("success", true, "type", type, "fields", rdapService.getSchemaFields(type))); }

    // ==================== ADMIN - EXPORT ====================

    @GetMapping("/api/admin/rdap/export/{type}")
    public ResponseEntity<?> exportData(@PathVariable String type) {
        try {
            List<RdapEntity> d = rdapService.exportEntities(type); return ResponseEntity.ok(Map.of("success", true, "type", type, "count", d.size(), "data", d));
        } catch (Exception e) { log.error("Request failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    // ==================== ADMIN - IMPORT ====================

    @PostMapping("/api/admin/rdap/{type}/import/csv/preview")
    public ResponseEntity<?> previewCsvImport(@PathVariable String type, @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = ",") String delimiter, @RequestParam(defaultValue = "true") boolean hasHeader,
            HttpServletRequest httpRequest) {
        try {
            fileSecurityService.scanOrReject(file, httpRequest, "CSV preview import");
            log.info("CSV preview for type: {}", type);
            return ResponseEntity.ok(Map.of("success", true, "preview", rdapService.previewCsvImport(file, type, delimiter, hasHeader)));
        } catch (com.jaddar.dataholder.service.FileSecurityService.SecurityRejectedException e) {
            log.warn("SECURITY: CSV preview blocked - {}", e.getDetails());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "File rejected: " + e.getDetails(),
                    "securityThreat", true, "threatType", e.getThreatType(), "severity", e.getSeverity()));
        } catch (Exception e) { log.error("CSV preview failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    @PostMapping("/api/admin/rdap/{type}/import/csv")
    public ResponseEntity<?> importCsv(@PathVariable String type, @RequestParam("file") MultipartFile file, @RequestParam(defaultValue = ",") String delimiter,
            @RequestParam(defaultValue = "true") boolean hasHeader, @RequestParam Map<String, String> allParams,
            HttpServletRequest httpRequest) {
        try {
            fileSecurityService.scanOrReject(file, httpRequest, "CSV import");
            Map<String, Integer> mappings = new HashMap<>();
            for (Map.Entry<String, String> e : allParams.entrySet()) {
                if (e.getKey().startsWith("col_")) { try { mappings.put(e.getKey().substring(4), Integer.parseInt(e.getValue())); } catch (NumberFormatException ignored) {} }
            }
            log.info("CSV import for type: {}, mappings: {}", type, mappings);
            return ResponseEntity.ok(Map.of("success", true, "result", rdapService.importCsv(file, type, delimiter, hasHeader, mappings)));
        } catch (com.jaddar.dataholder.service.FileSecurityService.SecurityRejectedException e) {
            log.warn("SECURITY: CSV import blocked - {}", e.getDetails());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "File rejected: " + e.getDetails(),
                    "securityThreat", true, "threatType", e.getThreatType(), "severity", e.getSeverity()));
        } catch (Exception e) { log.error("CSV import failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    @PostMapping("/api/admin/rdap/{type}/import/json")
    public ResponseEntity<?> importJson(@PathVariable String type, @RequestBody JsonImportRequest request) {
        try { log.info("JSON import for type: {}, records: {}", type, request.getData().size()); return ResponseEntity.ok(Map.of("success", true, "result", rdapService.importJson(type, request.getData()))); }
        catch (Exception e) { log.error("JSON import failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    @PostMapping("/api/admin/rdap/{type}/import/json/file")
    public ResponseEntity<?> importJsonFile(@PathVariable String type, @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {
        try {
            fileSecurityService.scanOrReject(file, httpRequest, "JSON file import");
            log.info("JSON file import for type: {}", type);
            return ResponseEntity.ok(Map.of("success", true, "result", rdapService.importJsonFile(file, type)));
        } catch (com.jaddar.dataholder.service.FileSecurityService.SecurityRejectedException e) {
            log.warn("SECURITY: JSON file import blocked - {}", e.getDetails());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "File rejected: " + e.getDetails(),
                    "securityThreat", true, "threatType", e.getThreatType(), "severity", e.getSeverity()));
        } catch (Exception e) { log.error("JSON file import failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    // ==================== USER REQUESTS ====================

    @GetMapping("/api/rdap/my-requests")
    public ResponseEntity<?> getMyRequests(@RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Requestor-Email", required = false) String reqEmail, @RequestHeader(value = "X-Requestor-Sub", required = false) String reqSub,
            @RequestParam(required = false) String email, @RequestParam(required = false) String status, @RequestParam(defaultValue = "50") int limit) {
        String token = tokenIntrospectionService.extractToken(authHeader);
        if (token == null) return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        TokenInfo info = tokenIntrospectionService.introspectToken(token);
        if (info == null || !info.isActive()) return ResponseEntity.status(401).body(Map.of("success", false, "error", "Invalid token"));
        String fEmail = info.getEmail();
        String fSub = info.getSub();
        try {
            List<PendingRequestDto> reqs = fEmail != null && !fEmail.isBlank() ? pendingRequestService.getRequestsByEmail(fEmail, status, limit) : pendingRequestService.getRequestsBySub(fSub, status, limit);
            return ResponseEntity.ok(Map.of("success", true, "requests", reqs, "total", reqs.size()));
        } catch (Exception e) { log.error("Request failed", e); return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    @PostMapping("/api/rdap/my-requests/{requestId}/cancel")
    public ResponseEntity<?> cancelMyRequest(
            @PathVariable String requestId,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Requestor-Email", required = false) String reqEmail,
            @RequestHeader(value = "X-Requestor-Sub", required = false) String reqSub) {
        String token = tokenIntrospectionService.extractToken(authHeader);
        if (token == null) return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        TokenInfo info = tokenIntrospectionService.introspectToken(token);
        if (info == null || !info.isActive()) return ResponseEntity.status(401).body(Map.of("success", false, "error", "Invalid token"));
        String cancelledBy = info.getEmail() != null ? info.getEmail() : info.getSub();
        String reason = (body != null) ? body.get("reason") : null;
        try {
            UUID uuid = UUID.fromString(requestId);
            // Verify the request belongs to the caller.
            Optional<PendingRequestDto> existing = pendingRequestService.getPendingRequest(uuid);
            if (existing.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("success", false, "error", "Request not found"));
            }
            String callerEmail = info.getEmail();
            String callerSub = info.getSub();
            PendingRequestDto req = existing.get();
            boolean ownsRequest = (callerEmail != null && callerEmail.equals(req.getRequestorEmail()))
                    || (callerSub != null && callerSub.equals(req.getRequestorSub()));
            if (!ownsRequest) {
                return ResponseEntity.status(403).body(Map.of("success", false, "error", "You can only cancel your own requests"));
            }
            PendingRequestDto result = pendingRequestService.cancelRequest(uuid, cancelledBy, reason);
            return ResponseEntity.ok(Map.of("success", true, "message", "Request cancelled", "request", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/api/rdap/my-requests/count")
    public ResponseEntity<?> getMyRequestCount(@RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Requestor-Email", required = false) String reqEmail) {
        String token = tokenIntrospectionService.extractToken(authHeader);
        if (token == null) return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        TokenInfo info = tokenIntrospectionService.introspectToken(token);
        if (info == null || !info.isActive()) return ResponseEntity.status(401).body(Map.of("success", false, "error", "Invalid token"));
        String emailAddr = info.getEmail(); // token identity only (IDOR fix)
        try { return ResponseEntity.ok(Map.of("success", true, "email", emailAddr, "counts", pendingRequestService.getRequestCountsByEmail(emailAddr))); }
        catch (Exception e) { log.error("Request failed", e); return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "The request could not be completed. Please check your input and try again.")); }
    }

    // ==================== RESPONSE BUILDER - RFC 9083 COMPLIANT ====================

    private ResponseEntity<?> toResponse(RdapQueryResult result) {
        boolean jakeCompliance = result.isJakeCompliance();
        String requestorGroupCode = result.getRequestorGroupCode();

        if (result.isPending()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("status", "pending");
            r.put("requestId", result.getRequestId().toString());
            r.put("message", "Request requires verification.");
            r.put("pollUrl", "/api/rdap/status/" + result.getRequestId());
            r.put("expiresAt", result.getExpiresAt().toString());
            r.put("queryType", result.getQueryType());
            r.put("queryValue", result.getQueryValue());
            if (jakeCompliance) { r.put("jakeCompliance", buildJakeComplianceBlock(requestorGroupCode)); }
            return ResponseEntity.accepted().contentType(RDAP_JSON).body(r);
        }

        if (!result.isSuccess()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("errorCode", result.getErrorCode());
            r.put("title", result.getErrorTitle());
            r.put("description", List.of(result.getErrorMessage()));
            r.put("queryType", result.getQueryType());
            r.put("queryValue", result.getQueryValue());
            r.put("timestamp", LocalDateTime.now().atOffset(ZoneOffset.UTC).toString());
            if (jakeCompliance) { r.put("jakeCompliance", buildJakeComplianceBlock(requestorGroupCode)); }
            return ResponseEntity.status(result.getErrorCode()).contentType(RDAP_JSON).body(r);
        }

        RdapEntity e = result.getEntity();
        Map<String, Object> r = new LinkedHashMap<>();

        r.put("rdapConformance", List.of("rdap_level_0", "icann_rdap_response_profile_0", "icann_rdap_technical_implementation_guide_0"));
        r.put("objectClassName", e.getObjectClassName());
        r.put("handle", e.getHandle());

        addEntityFields(r, e);

        Long entityId = e.getId();
        boolean isMapped = entityId != null && entityId < 0;

        // Events
        List<RdapEvent> events = isMapped
                ? rdapService.getEventsForMappedOrLocal(entityId, e)
                : rdapService.getEventsForEntity(entityId);
        if (events != null && !events.isEmpty()) { r.put("events", buildEventsArray(events)); }

        // Nameservers (for domains)
        if (e.getObjectType() == ObjectType.DOMAIN) {
            if (isMapped) {
                // Mapped entities carry nameservers as children with ObjectType.NAMESERVER
                List<RdapEntity> nsChildren = e.getChildren() != null
                        ? e.getChildren().stream()
                            .filter(c -> c.getObjectType() == ObjectType.NAMESERVER)
                            .collect(Collectors.toList())
                        : List.of();
                if (!nsChildren.isEmpty()) {
                    r.put("nameservers", nsChildren.stream().map(ns -> {
                        Map<String, Object> nsMap = new LinkedHashMap<>();
                        nsMap.put("objectClassName", "nameserver");
                        if (ns.getHandle() != null) nsMap.put("handle", ns.getHandle());
                        if (ns.getLdhName() != null) nsMap.put("ldhName", ns.getLdhName());
                        // IP addresses stored in startAddress/endAddress
                        Map<String, Object> ipAddresses = new LinkedHashMap<>();
                        if (ns.getStartAddress() != null) ipAddresses.put("v4", List.of(ns.getStartAddress()));
                        if (ns.getEndAddress() != null) ipAddresses.put("v6", List.of(ns.getEndAddress()));
                        if (!ipAddresses.isEmpty()) nsMap.put("ipAddresses", ipAddresses);
                        return nsMap;
                    }).collect(Collectors.toList()));
                }
            } else {
                List<RdapNameserver> nameservers = rdapService.getNameserversForEntity(entityId);
                if (nameservers != null && !nameservers.isEmpty()) { r.put("nameservers", buildNameserversArray(nameservers)); }
            }
        }

        // Remarks
        if (!isMapped) {
            List<RdapRemark> remarks = rdapService.getRemarksForEntity(entityId);
            if (remarks != null && !remarks.isEmpty()) {
                List<Map<String, Object>> remarksList = new ArrayList<>();
                List<Map<String, Object>> noticesList = new ArrayList<>();
                for (RdapRemark remark : remarks) {
                    Map<String, Object> remarkMap = buildRemarkMap(remark);
                    if (remark.getRemarkType() == RdapRemark.RemarkType.NOTICE) { noticesList.add(remarkMap); }
                    else { remarksList.add(remarkMap); }
                }
                if (!remarksList.isEmpty()) r.put("remarks", remarksList);
                if (!noticesList.isEmpty()) r.put("notices", noticesList);
            }
        }

        // Links
        if (!isMapped) {
            List<RdapLink> links = rdapService.getLinksForEntity(entityId);
            if (links != null && !links.isEmpty()) { r.put("links", buildLinksArray(links)); }
        }

        // SecureDNS
        if (e.getObjectType() == ObjectType.DOMAIN) {
            if (isMapped) {
                // For mapped entities, build secureDNS from the entity's boolean flags
                if (Boolean.TRUE.equals(e.getSecureDnsDelegationSigned())) {
                    Map<String, Object> secureDnsMap = new LinkedHashMap<>();
                    secureDnsMap.put("delegationSigned", true);
                    r.put("secureDNS", secureDnsMap);
                }
            } else {
                List<RdapSecureDns> secureDnsRecords = rdapService.getSecureDnsForEntity(entityId);
                Map<String, Object> secureDnsMap = buildSecureDnsObject(e, secureDnsRecords);
                if (secureDnsMap != null && !secureDnsMap.isEmpty()) { r.put("secureDNS", secureDnsMap); }
            }
        }

        // Child entities (contacts)
        if (isMapped) {
            // Mapped entities carry contact children directly (filter out nameservers)
            List<RdapEntity> contactChildren = e.getChildren() != null
                    ? e.getChildren().stream()
                        .filter(c -> c.getObjectType() == ObjectType.ENTITY)
                        .collect(Collectors.toList())
                    : List.of();
            if (!contactChildren.isEmpty()) {
                r.put("entities", orderContactsByRole(contactChildren).stream().map(this::childToMap).collect(Collectors.toList()));
            }
        } else {
            List<RdapEntity> children = rdapService.getChildrenForEntity(entityId);
            if (children != null && !children.isEmpty()) {
                r.put("entities", buildEntitiesArray(children));
            } else if (e.getChildren() != null && !e.getChildren().isEmpty()) {
                r.put("entities", orderContactsByRole(e.getChildren()).stream().map(this::childToMap).collect(Collectors.toList()));
            }
        }

        r.put("accessLevel", result.getAccessLevel());
        if (result.getAgreementNames() != null && !result.getAgreementNames().isEmpty()) { r.put("agreementNames", result.getAgreementNames()); }
        r.put("timestamp", LocalDateTime.now().atOffset(ZoneOffset.UTC).toString());

        // Custom table data (from custom table mappings with active joins)
        if (result.getCustomTableData() != null && !result.getCustomTableData().isEmpty()) {
            r.put("customTableData", result.getCustomTableData());
        }

        if (jakeCompliance) { r.put("jakeCompliance", buildJakeComplianceBlock(requestorGroupCode)); }

        // Phase 1: Policy-based redaction (access level + redaction rules)
        Map<String, Object> redactedResponse = rdapRedactionService.applyRedactionRules(r, e, result.getAccessLevel());

        // Phase 2: Request-type parameter filtering (AgreementRdapParameters booleans)
        AgreementRequestType resolvedRequestType = result.getResolvedRequestType();
        if (resolvedRequestType != null) {
            AgreementRdapParameters params = resolvedRequestType.getEffectiveRdapParameters();
            if (params != null) {
                log.info("PARAM FILTER: Applying filtering for request type '{}'. adminEntity={}, adminName={}, adminAddress={}, adminEmail={}, adminPhone={}",
                        resolvedRequestType.getName(),
                        params.getAdminEntity(), params.getAdminName(), params.getAdminAddress(),
                        params.getAdminEmail(), params.getAdminPhone());
                applyRdapParameterFiltering(redactedResponse, params, e.getObjectType());
            }
        } else {
            log.debug("PARAM FILTER: No resolved request type — skipping RDAP parameter filtering. agreements={}",
                    result.getAgreementNames());
        }

        return ResponseEntity.ok().contentType(RDAP_JSON).body(redactedResponse);
    }

    // ==================== RDAP PARAMETER FILTERING ====================

    /**
     * Apply AgreementRdapParameters boolean filtering on top of the already-redacted response.
     * For each boolean that is false, the corresponding field/section is removed from the response.
     *
     * This runs AFTER policy-based redaction, so it acts as an additional restrictive layer:
     * even if the policy allows a field, the request type parameters can still exclude it.
     */
    @SuppressWarnings("unchecked")
    private void applyRdapParameterFiltering(Map<String, Object> response, AgreementRdapParameters params, ObjectType objectType) {
        log.debug("PARAM FILTER: Applying AgreementRdapParameters filtering for objectType={}", objectType);

        // --- Top-level domain fields ---
        if (objectType == ObjectType.DOMAIN) {
            if (!Boolean.TRUE.equals(params.getDomainHandle())) response.remove("handle");
            if (!Boolean.TRUE.equals(params.getDomainName())) { response.remove("ldhName"); response.remove("unicodeName"); }
            if (!Boolean.TRUE.equals(params.getDomainStatus())) response.remove("status");
            if (!Boolean.TRUE.equals(params.getDomainPort43())) response.remove("port43");
            if (!Boolean.TRUE.equals(params.getNameservers())) response.remove("nameservers");
            if (!Boolean.TRUE.equals(params.getDnssecData())) response.remove("secureDNS");
        }

        // --- Top-level IP network fields ---
        if (objectType == ObjectType.IP_NETWORK) {
            if (!Boolean.TRUE.equals(params.getNetworkHandle())) response.remove("handle");
            if (!Boolean.TRUE.equals(params.getNetworkName())) response.remove("name");
            if (!Boolean.TRUE.equals(params.getNetworkType())) response.remove("type");
            if (!Boolean.TRUE.equals(params.getNetworkStartAddress())) response.remove("startAddress");
            if (!Boolean.TRUE.equals(params.getNetworkEndAddress())) response.remove("endAddress");
            if (!Boolean.TRUE.equals(params.getNetworkIpVersion())) response.remove("ipVersion");
            if (!Boolean.TRUE.equals(params.getNetworkParentHandle())) response.remove("parentHandle");
            if (!Boolean.TRUE.equals(params.getNetworkCountry())) response.remove("country");
        }

        // --- Top-level ASN fields ---
        if (objectType == ObjectType.AUTNUM) {
            if (!Boolean.TRUE.equals(params.getAutnumHandle())) response.remove("handle");
            if (!Boolean.TRUE.equals(params.getAutnumStart())) response.remove("startAutnum");
            if (!Boolean.TRUE.equals(params.getAutnumEnd())) response.remove("endAutnum");
            if (!Boolean.TRUE.equals(params.getAutnumName())) response.remove("name");
            if (!Boolean.TRUE.equals(params.getAutnumType())) response.remove("type");
            if (!Boolean.TRUE.equals(params.getAutnumCountry())) response.remove("country");
        }

        // --- Events ---
        if (!Boolean.TRUE.equals(params.getEvents())) {
            response.remove("events");
        } else {
            filterEvents(response, params);
        }

        // --- Links, notices, remarks ---
        if (!Boolean.TRUE.equals(params.getLinks())) response.remove("links");
        if (!Boolean.TRUE.equals(params.getNotices())) response.remove("notices");
        if (!Boolean.TRUE.equals(params.getRemarks())) response.remove("remarks");

        // --- Nameserver sub-fields (if nameservers still present) ---
        if (Boolean.TRUE.equals(params.getNameservers()) && response.containsKey("nameservers")) {
            filterNameserverFields(response, params);
        }

        // --- SecureDNS sub-fields (if secureDNS still present) ---
        if (Boolean.TRUE.equals(params.getDnssecData()) && response.containsKey("secureDNS")) {
            filterSecureDnsFields(response, params);
        }

        // --- Child entities (contacts) ---
        filterChildEntities(response, params);
    }

    /**
     * Filter individual event types based on parameter booleans.
     */
    @SuppressWarnings("unchecked")
    private void filterEvents(Map<String, Object> response, AgreementRdapParameters params) {
        Object eventsRaw = response.get("events");
        if (eventsRaw == null) return;

        // Unwrap if annotated
        if (eventsRaw instanceof Map && ((Map<?, ?>) eventsRaw).containsKey("value")) {
            eventsRaw = ((Map<?, ?>) eventsRaw).get("value");
        }
        if (!(eventsRaw instanceof List)) return;

        List<Object> events = (List<Object>) eventsRaw;
        events.removeIf(evt -> {
            if (!(evt instanceof Map)) return false;
            Map<String, Object> eventMap = (Map<String, Object>) evt;
            String action = extractStringValue(eventMap.get("eventAction"));
            if (action == null) return false;

            return switch (action.toLowerCase().replace(" ", "").replace("_", "")) {
                case "registration" -> !Boolean.TRUE.equals(params.getEventRegistration());
                case "expiration" -> !Boolean.TRUE.equals(params.getEventExpiration());
                case "lastchanged" -> !Boolean.TRUE.equals(params.getEventLastChanged());
                case "lastupdateofrdapdatabase" -> !Boolean.TRUE.equals(params.getEventLastUpdateOfRdapDb());
                case "transfer" -> !Boolean.TRUE.equals(params.getEventTransfer());
                default -> false;
            };
        });

        if (events.isEmpty()) response.remove("events");
    }

    /**
     * Filter nameserver sub-fields (handle, name, IP addresses, status).
     */
    @SuppressWarnings("unchecked")
    private void filterNameserverFields(Map<String, Object> response, AgreementRdapParameters params) {
        Object nsRaw = response.get("nameservers");
        if (nsRaw == null) return;
        if (nsRaw instanceof Map && ((Map<?, ?>) nsRaw).containsKey("value")) {
            nsRaw = ((Map<?, ?>) nsRaw).get("value");
        }
        if (!(nsRaw instanceof List)) return;

        for (Object nsObj : (List<Object>) nsRaw) {
            if (!(nsObj instanceof Map)) continue;
            Map<String, Object> ns = (Map<String, Object>) nsObj;
            if (!Boolean.TRUE.equals(params.getNameserverHandle())) ns.remove("handle");
            if (!Boolean.TRUE.equals(params.getNameserverName())) { ns.remove("ldhName"); ns.remove("unicodeName"); }
            if (!Boolean.TRUE.equals(params.getNameserverIpAddresses())) ns.remove("ipAddresses");
            if (!Boolean.TRUE.equals(params.getNameserverStatus())) ns.remove("status");
        }
    }

    /**
     * Filter secureDNS sub-fields.
     */
    @SuppressWarnings("unchecked")
    private void filterSecureDnsFields(Map<String, Object> response, AgreementRdapParameters params) {
        Object dnsRaw = response.get("secureDNS");
        if (dnsRaw == null) return;
        if (dnsRaw instanceof Map && ((Map<?, ?>) dnsRaw).containsKey("value")) {
            dnsRaw = ((Map<?, ?>) dnsRaw).get("value");
        }
        if (!(dnsRaw instanceof Map)) return;

        Map<String, Object> dns = (Map<String, Object>) dnsRaw;
        if (!Boolean.TRUE.equals(params.getDnssecDelegationSigned())) dns.remove("delegationSigned");
        if (!Boolean.TRUE.equals(params.getDnssecDsData())) dns.remove("dsData");
        if (!Boolean.TRUE.equals(params.getDnssecKeyData())) dns.remove("keyData");

        // If nothing remains, remove the whole block
        dns.remove("zoneSigned"); // always remove zoneSigned if dnssecData is controlled
        if (dns.isEmpty()) response.remove("secureDNS");
    }

    /**
     * Filter child entities (contacts) based on role.
     * Removes entire contact entities whose role is disabled, and filters
     * individual vCard properties within allowed entities.
     */
    @SuppressWarnings("unchecked")
    private void filterChildEntities(Map<String, Object> response, AgreementRdapParameters params) {
        Object entitiesRaw = response.get("entities");
        if (entitiesRaw == null) return;

        if (entitiesRaw instanceof Map && ((Map<?, ?>) entitiesRaw).containsKey("value")) {
            entitiesRaw = ((Map<?, ?>) entitiesRaw).get("value");
        }
        if (!(entitiesRaw instanceof List)) return;

        List<Object> entities = (List<Object>) entitiesRaw;

        entities.removeIf(entityObj -> {
            if (!(entityObj instanceof Map)) return false;
            Map<String, Object> entity = (Map<String, Object>) entityObj;

            List<String> roles = detectAllContactRoles(entity);
            if (roles.isEmpty()) return false;

            return roles.stream().noneMatch(r -> isRoleEntityEnabled(r, params));
        });

        // For remaining entities, filter individual vCard fields. A field/handle is
        // kept if ANY of the contact's still-enabled roles permits it, so a contact
        // retained via one of its roles is not over-redacted by another.
        for (Object entityObj : entities) {
            if (!(entityObj instanceof Map)) continue;
            Map<String, Object> entity = (Map<String, Object>) entityObj;
            List<String> enabledRoles = detectAllContactRoles(entity).stream()
                    .filter(r -> isRoleEntityEnabled(r, params))
                    .collect(Collectors.toList());
            if (!enabledRoles.isEmpty()) {
                filterContactVcardFields(entity, enabledRoles, params);
                filterContactHandle(entity, enabledRoles, params);
            }
        }

        if (entities.isEmpty()) response.remove("entities");
    }

    /**
     * Detect the primary contact role from an entity map.
     */
    private List<String> detectAllContactRoles(Map<String, Object> entity) {
        Object rolesRaw = entity.get("roles");
        if (rolesRaw instanceof Map && ((Map<?, ?>) rolesRaw).containsKey("value")) {
            rolesRaw = ((Map<?, ?>) rolesRaw).get("value");
        }
        if (!(rolesRaw instanceof List)) return List.of();

        List<String> normalized = new ArrayList<>();
        for (Object roleObj : (List<?>) rolesRaw) {
            if (roleObj == null) continue;
            String norm = normalizeContactRole(roleObj.toString().toLowerCase().trim());
            if (norm != null && !normalized.contains(norm)) normalized.add(norm);
        }
        normalized.sort(Comparator.comparingInt(RdapController::normalizedRoleRank));
        return normalized;
    }

    private static int normalizedRoleRank(String role) {
        return switch (role) {
            case "registrant" -> 0;
            case "administrative" -> 1;
            case "technical" -> 2;
            case "billing" -> 3;
            case "registrar" -> 4;
            case "abuse" -> 5;
            default -> Integer.MAX_VALUE;
        };
    }

    /**
     * Whether the contact entity for the given normalized role is enabled by the
     * agreement's RDAP parameters. Unknown/ungated roles are treated as enabled.
     */
    private boolean isRoleEntityEnabled(String role, AgreementRdapParameters params) {
        return switch (role) {
            case "registrant" -> Boolean.TRUE.equals(params.getRegistrantEntity());
            case "administrative" -> Boolean.TRUE.equals(params.getAdminEntity());
            case "technical" -> Boolean.TRUE.equals(params.getTechEntity());
            case "billing" -> Boolean.TRUE.equals(params.getBillingEntity());
            case "registrar" -> Boolean.TRUE.equals(params.getRegistrarEntity());
            case "abuse" -> Boolean.TRUE.equals(params.getRegistrarAbuseContact());
            default -> true;
        };
    }

    /**
     * Normalize role strings to canonical form.
     * Handles both short ("admin", "tech") and long ("administrative", "technical") RDAP role names.
     */
    private String normalizeContactRole(String role) {
        return switch (role) {
            case "registrant" -> "registrant";
            case "admin", "administrative" -> "administrative";
            case "tech", "technical" -> "technical";
            case "billing" -> "billing";
            case "registrar", "sponsor" -> "registrar";
            case "abuse" -> "abuse";
            default -> null;
        };
    }

    /**
     * Remove the handle from a contact entity if the parameter says so.
     */
    private void filterContactHandle(Map<String, Object> entity, List<String> roles, AgreementRdapParameters params) {
        boolean keepHandle = roles.isEmpty() || roles.stream().anyMatch(r -> isHandleAllowed(r, params));
        if (!keepHandle) entity.remove("handle");
    }

    private boolean isHandleAllowed(String role, AgreementRdapParameters params) {
        return switch (role) {
            case "registrant" -> Boolean.TRUE.equals(params.getRegistrantHandle());
            case "administrative" -> Boolean.TRUE.equals(params.getAdminHandle());
            case "technical" -> Boolean.TRUE.equals(params.getTechHandle());
            case "billing" -> Boolean.TRUE.equals(params.getBillingHandle());
            case "registrar" -> Boolean.TRUE.equals(params.getRegistrarHandle());
            default -> true;
        };
    }

    /**
     * Filter individual vCard properties within a contact entity based on role and parameters.
     */
    @SuppressWarnings("unchecked")
    private void filterContactVcardFields(Map<String, Object> entity, List<String> roles, AgreementRdapParameters params) {
        Object vcardRaw = entity.get("vcardArray");
        if (vcardRaw == null) return;
        if (vcardRaw instanceof Map && ((Map<?, ?>) vcardRaw).containsKey("value")) {
            vcardRaw = ((Map<?, ?>) vcardRaw).get("value");
        }
        if (!(vcardRaw instanceof List)) return;

        List<Object> vcard = (List<Object>) vcardRaw;
        if (vcard.size() < 2 || !(vcard.get(1) instanceof List)) return;

        List<Object> properties = (List<Object>) vcard.get(1);

        properties.removeIf(prop -> {
            if (!(prop instanceof List)) return false;
            List<Object> propList = (List<Object>) prop;
            if (propList.size() < 4) return false;

            String propName = propList.get(0) instanceof String ? ((String) propList.get(0)).toLowerCase() : "";
            if ("version".equals(propName)) return false;

            // Keep the field if any of the contact's enabled roles permits it.
            return roles.stream().noneMatch(r -> isVcardPropertyAllowed(propName, propList, r, params));
        });
    }

    /**
     * Check whether a specific vCard property is allowed for the given contact role.
     */
    @SuppressWarnings("unchecked")
    private boolean isVcardPropertyAllowed(String propName, List<Object> propList, String role, AgreementRdapParameters params) {
        return switch (role) {
            case "registrant" -> switch (propName) {
                case "fn" -> Boolean.TRUE.equals(params.getRegistrantName());
                case "org" -> Boolean.TRUE.equals(params.getRegistrantOrganization());
                case "email" -> Boolean.TRUE.equals(params.getRegistrantEmail());
                case "tel" -> isTelAllowed(propList, params.getRegistrantPhone(), params.getRegistrantFax());
                case "adr" -> Boolean.TRUE.equals(params.getRegistrantAddress());
                default -> true;
            };
            case "administrative" -> switch (propName) {
                case "fn" -> Boolean.TRUE.equals(params.getAdminName());
                case "org" -> Boolean.TRUE.equals(params.getAdminOrganization());
                case "email" -> Boolean.TRUE.equals(params.getAdminEmail());
                case "tel" -> isTelAllowed(propList, params.getAdminPhone(), params.getAdminFax());
                case "adr" -> Boolean.TRUE.equals(params.getAdminAddress());
                default -> true;
            };
            case "technical" -> switch (propName) {
                case "fn" -> Boolean.TRUE.equals(params.getTechName());
                case "org" -> Boolean.TRUE.equals(params.getTechOrganization());
                case "email" -> Boolean.TRUE.equals(params.getTechEmail());
                case "tel" -> isTelAllowed(propList, params.getTechPhone(), params.getTechFax());
                case "adr" -> Boolean.TRUE.equals(params.getTechAddress());
                default -> true;
            };
            case "billing" -> switch (propName) {
                case "fn" -> Boolean.TRUE.equals(params.getBillingName());
                case "org" -> Boolean.TRUE.equals(params.getBillingOrganization());
                case "email" -> Boolean.TRUE.equals(params.getBillingEmail());
                case "tel" -> isTelAllowed(propList, params.getBillingPhone(), params.getBillingFax());
                case "adr" -> Boolean.TRUE.equals(params.getBillingAddress());
                default -> true;
            };
            case "registrar" -> switch (propName) {
                case "fn" -> Boolean.TRUE.equals(params.getRegistrarName());
                case "email" -> Boolean.TRUE.equals(params.getRegistrarEmail());
                case "tel" -> Boolean.TRUE.equals(params.getRegistrarPhone());
                default -> true;
            };
            default -> true;
        };
    }

    /**
     * Check if a tel vCard property is allowed, distinguishing voice vs fax.
     * In jCard, the tel property's params (index 1) may contain {"type": "fax"} or {"type": "voice"}.
     */
    private boolean isTelAllowed(List<Object> propList, Boolean phoneAllowed, Boolean faxAllowed) {
        // If we can positively identify this as fax, use faxAllowed; otherwise phoneAllowed
        if (isFaxProperty(propList)) {
            return Boolean.TRUE.equals(faxAllowed);
        }
        return Boolean.TRUE.equals(phoneAllowed);
    }

    /**
     * Determine if a tel vCard property represents a fax number.
     * Checks the type parameter for any variation of "fax" (case-insensitive).
     * Handles annotation wrappers, string values, and list values.
     */
    @SuppressWarnings("unchecked")
    private boolean isFaxProperty(List<Object> propList) {
        if (propList.size() < 2) return false;

        Object paramsObj = propList.get(1);

        // Unwrap annotation wrapper on params
        if (paramsObj instanceof Map && ((Map<?, ?>) paramsObj).containsKey("value")
                && ((Map<?, ?>) paramsObj).containsKey("requiredAccessLevel")) {
            paramsObj = ((Map<?, ?>) paramsObj).get("value");
        }
        if (!(paramsObj instanceof Map)) return false;

        Map<String, Object> telParams = (Map<String, Object>) paramsObj;

        // Check all param keys — "type" is standard, but also check "TYPE", etc.
        for (Map.Entry<String, Object> entry : telParams.entrySet()) {
            if (!"type".equalsIgnoreCase(entry.getKey())) continue;

            Object typeVal = entry.getValue();

            // Unwrap annotated type value
            if (typeVal instanceof Map && ((Map<?, ?>) typeVal).containsKey("value")) {
                typeVal = ((Map<?, ?>) typeVal).get("value");
            }

            if (containsFax(typeVal)) return true;
        }

        // Also check the URI value (index 3) for "fax:" prefix as a fallback
        if (propList.size() >= 4) {
            Object val = propList.get(3);
            if (val instanceof Map && ((Map<?, ?>) val).containsKey("value")) {
                val = ((Map<?, ?>) val).get("value");
            }
            if (val instanceof String && ((String) val).toLowerCase().startsWith("fax:")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a value (string, list, or other) contains any fax indicator.
     */
    private boolean containsFax(Object value) {
        if (value == null) return false;

        if (value instanceof String) {
            // Could be "fax", "fax,voice", "voice,fax", etc.
            for (String part : ((String) value).split("[,;\\s]+")) {
                if (part.trim().equalsIgnoreCase("fax")) return true;
            }
            return false;
        }

        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null && containsFax(item)) return true;
            }
            return false;
        }

        return value.toString().equalsIgnoreCase("fax");
    }

    /**
     * Extract a string value, unwrapping annotation wrappers if present.
     */
    private String extractStringValue(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            if (map.containsKey("value")) {
                Object val = map.get("value");
                return val instanceof String ? (String) val : (val != null ? val.toString() : null);
            }
        }
        return obj.toString();
    }

    // ==================== JAKE COMPLIANCE ====================

    private Map<String, Object> buildJakeComplianceBlock(String requestorGroupCode) {
        return rdapService.buildJakeComplianceBlock(requestorGroupCode);
    }

    // ==================== RESPONSE BUILDING HELPERS ====================

    private List<Map<String, Object>> buildEventsArray(List<RdapEvent> events) {
        List<Map<String, Object>> eventsArray = new ArrayList<>();
        for (RdapEvent event : events) {
            Map<String, Object> eventMap = new LinkedHashMap<>();
            String actionName = event.getEventActionCustom();
            if (actionName == null || actionName.isBlank()) {
                actionName = event.getEventAction().name().toLowerCase().replace('_', ' ');
            }
            eventMap.put("eventAction", actionName);
            if (event.getEventDate() != null) { eventMap.put("eventDate", event.getEventDate().atOffset(ZoneOffset.UTC).format(ISO_FORMATTER)); }
            if (event.getEventActor() != null && !event.getEventActor().isBlank()) { eventMap.put("eventActor", event.getEventActor()); }
            eventsArray.add(eventMap);
        }
        return eventsArray;
    }

    private List<Map<String, Object>> buildNameserversArray(List<RdapNameserver> nameservers) {
        List<Map<String, Object>> nsArray = new ArrayList<>();
        for (RdapNameserver ns : nameservers) {
            Map<String, Object> nsMap = new LinkedHashMap<>();
            nsMap.put("objectClassName", "nameserver");
            if (ns.getHandle() != null) nsMap.put("handle", ns.getHandle());
            nsMap.put("ldhName", ns.getLdhName());
            if (ns.getUnicodeName() != null && !ns.getUnicodeName().isBlank()) nsMap.put("unicodeName", ns.getUnicodeName());
            if ((ns.getIpv4Addresses() != null && !ns.getIpv4Addresses().isEmpty()) ||
                (ns.getIpv6Addresses() != null && !ns.getIpv6Addresses().isEmpty())) {
                Map<String, Object> ipAddresses = new LinkedHashMap<>();
                if (ns.getIpv4Addresses() != null && !ns.getIpv4Addresses().isEmpty()) ipAddresses.put("v4", ns.getIpv4Addresses());
                if (ns.getIpv6Addresses() != null && !ns.getIpv6Addresses().isEmpty()) ipAddresses.put("v6", ns.getIpv6Addresses());
                nsMap.put("ipAddresses", ipAddresses);
            }
            if (ns.getStatus() != null && !ns.getStatus().isEmpty()) nsMap.put("status", ns.getStatus());
            nsArray.add(nsMap);
        }
        return nsArray;
    }

    private Map<String, Object> buildRemarkMap(RdapRemark remark) {
        Map<String, Object> remarkMap = new LinkedHashMap<>();
        if (remark.getTitle() != null && !remark.getTitle().isBlank()) remarkMap.put("title", remark.getTitle());
        if (remark.getDescription() != null && !remark.getDescription().isEmpty()) remarkMap.put("description", remark.getDescription());
        if (remark.getRemarkTypeValue() != null && !remark.getRemarkTypeValue().isBlank()) remarkMap.put("type", remark.getRemarkTypeValue());
        return remarkMap;
    }

    private List<Map<String, Object>> buildLinksArray(List<RdapLink> links) {
        List<Map<String, Object>> linksArray = new ArrayList<>();
        for (RdapLink link : links) {
            Map<String, Object> linkMap = new LinkedHashMap<>();
            if (link.getValue() != null && !link.getValue().isBlank()) linkMap.put("value", link.getValue());
            if (link.getRel() != null && !link.getRel().isBlank()) linkMap.put("rel", link.getRel());
            linkMap.put("href", link.getHref());
            if (link.getMediaType() != null && !link.getMediaType().isBlank()) linkMap.put("type", link.getMediaType());
            if (link.getTitle() != null && !link.getTitle().isBlank()) linkMap.put("title", link.getTitle());
            if (link.getHreflang() != null && !link.getHreflang().isBlank()) linkMap.put("hreflang", link.getHreflang());
            linksArray.add(linkMap);
        }
        return linksArray;
    }

    private Map<String, Object> buildSecureDnsObject(RdapEntity entity, List<RdapSecureDns> dsRecords) {
        Map<String, Object> secureDns = new LinkedHashMap<>();
        if (entity.getSecureDnsDelegationSigned() != null) secureDns.put("delegationSigned", entity.getSecureDnsDelegationSigned());
        if (entity.getSecureDnsZoneSigned() != null) secureDns.put("zoneSigned", entity.getSecureDnsZoneSigned());
        if (dsRecords != null && !dsRecords.isEmpty()) {
            List<Map<String, Object>> dsDataArray = new ArrayList<>();
            for (RdapSecureDns ds : dsRecords) {
                Map<String, Object> dsMap = new LinkedHashMap<>();
                if (ds.getKeyTag() != null) dsMap.put("keyTag", ds.getKeyTag());
                if (ds.getAlgorithm() != null) dsMap.put("algorithm", ds.getAlgorithm());
                if (ds.getDigestType() != null) dsMap.put("digestType", ds.getDigestType());
                if (ds.getDigest() != null && !ds.getDigest().isBlank()) dsMap.put("digest", ds.getDigest());
                if (!dsMap.isEmpty()) dsDataArray.add(dsMap);
            }
            if (!dsDataArray.isEmpty()) secureDns.put("dsData", dsDataArray);
        }
        return secureDns;
    }

    private List<Map<String, Object>> buildEntitiesArray(List<RdapEntity> children) {
        List<Map<String, Object>> entitiesArray = new ArrayList<>();
        for (RdapEntity child : orderContactsByRole(children)) { entitiesArray.add(childToMap(child)); }
        return entitiesArray;
    }

    /**
     * Order contact entities by RDAP role precedence: registrant, admin, tech,
     * billing, then any others. A contact with multiple roles ranks by its
     * highest-precedence (lowest-ranked) role. The sort is stable, so contacts
     * sharing a rank keep their original relative order.
     */
    private List<RdapEntity> orderContactsByRole(List<RdapEntity> contacts) {
        if (contacts == null) return List.of();
        List<RdapEntity> ordered = new ArrayList<>(contacts);
        ordered.sort(Comparator.comparingInt(this::contactRoleRank));
        return ordered;
    }

    private int contactRoleRank(RdapEntity c) {
        int rank = Integer.MAX_VALUE;
        if (c.getRoles() == null) return rank;
        for (String role : c.getRoles()) {
            if (role == null) continue;
            int idx = switch (role.toLowerCase()) {
                case "registrant" -> 0;
                case "admin", "administrative" -> 1;
                case "tech", "technical" -> 2;
                case "billing" -> 3;
                default -> Integer.MAX_VALUE;
            };
            if (idx < rank) rank = idx;
        }
        return rank;
    }

    private void addEntityFields(Map<String, Object> r, RdapEntity e) {
        switch (e.getObjectType()) {
            case DOMAIN -> {
                if (e.getLdhName() != null) r.put("ldhName", e.getLdhName());
                if (e.getUnicodeName() != null) r.put("unicodeName", e.getUnicodeName());
            }
            case IP_NETWORK -> {
                if (e.getStartAddress() != null) r.put("startAddress", e.getStartAddress());
                if (e.getEndAddress() != null) r.put("endAddress", e.getEndAddress());
                if (e.getIpVersion() != null) r.put("ipVersion", e.getIpVersion());
                if (e.getNetworkName() != null) r.put("name", e.getNetworkName());
                if (e.getNetworkType() != null) r.put("type", e.getNetworkType());
                if (e.getCountry() != null) r.put("country", e.getCountry());
                if (e.getParentHandle() != null) r.put("parentHandle", e.getParentHandle());
            }
            case AUTNUM -> {
                if (e.getStartAutnum() != null) r.put("startAutnum", e.getStartAutnum());
                if (e.getEndAutnum() != null) r.put("endAutnum", e.getEndAutnum());
                if (e.getAutnumName() != null) r.put("name", e.getAutnumName());
                if (e.getAutnumType() != null) r.put("type", e.getAutnumType());
                if (e.getCountry() != null) r.put("country", e.getCountry());
            }
            case ENTITY -> {
                if (e.getContactName() != null || e.getOrganization() != null || e.getEmail() != null || e.getPhone() != null) {
                    r.put("vcardArray", buildVcard(e));
                }
                if (e.getRoles() != null && !e.getRoles().isEmpty()) r.put("roles", e.getRoles());
                if (e.getPublicIds() != null && !e.getPublicIds().isEmpty()) r.put("publicIds", buildPublicIds(e.getPublicIds()));
            }
            case NAMESERVER -> {
                if (e.getLdhName() != null) r.put("ldhName", e.getLdhName());
                if (e.getUnicodeName() != null) r.put("unicodeName", e.getUnicodeName());
            }
        }
        if (e.getStatus() != null && !e.getStatus().isEmpty()) r.put("status", e.getStatus());
        if (e.getPort43() != null) r.put("port43", e.getPort43());
    }

    private Map<String, Object> childToMap(RdapEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("objectClassName", c.getObjectClassName() != null ? c.getObjectClassName() : "entity");
        m.put("handle", c.getHandle());
        if (c.getObjectType() == ObjectType.ENTITY) {
            if (c.getContactName() != null || c.getOrganization() != null || c.getEmail() != null ||
                c.getPhone() != null || c.getFax() != null || c.getAddressStreet1() != null) {
                m.put("vcardArray", buildVcard(c));
            }
            if (c.getRoles() != null && !c.getRoles().isEmpty()) m.put("roles", c.getRoles());
            if (c.getPublicIds() != null && !c.getPublicIds().isEmpty()) m.put("publicIds", buildPublicIds(c.getPublicIds()));
        }
        if (c.getStatus() != null && !c.getStatus().isEmpty()) m.put("status", c.getStatus());
        List<RdapEvent> childEvents = rdapService.getEventsForEntity(c.getId());
        if (childEvents != null && !childEvents.isEmpty()) m.put("events", buildEventsArray(childEvents));
        List<RdapLink> childLinks = rdapService.getLinksForEntity(c.getId());
        if (childLinks != null && !childLinks.isEmpty()) m.put("links", buildLinksArray(childLinks));
        List<RdapRemark> childRemarks = rdapService.getRemarksForEntity(c.getId());
        if (childRemarks != null && !childRemarks.isEmpty()) {
            List<Map<String, Object>> remarksList = childRemarks.stream()
                .filter(r -> r.getRemarkType() == RdapRemark.RemarkType.REMARK)
                .map(this::buildRemarkMap).collect(Collectors.toList());
            if (!remarksList.isEmpty()) m.put("remarks", remarksList);
        }
        return m;
    }

    private List<Map<String, Object>> buildPublicIds(List<String> publicIds) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String id : publicIds) {
            Map<String, Object> publicId = new LinkedHashMap<>();
            if (id.contains(":")) { String[] parts = id.split(":", 2); publicId.put("type", parts[0]); publicId.put("identifier", parts[1]); }
            else { publicId.put("type", "IANA Registrar ID"); publicId.put("identifier", id); }
            result.add(publicId);
        }
        return result;
    }

    private List<Object> buildVcard(RdapEntity e) {
        List<Object> vcard = new ArrayList<>();
        vcard.add("vcard");
        List<List<Object>> properties = new ArrayList<>();

        properties.add(new ArrayList<>(List.of("version", new LinkedHashMap<>(), "text", "4.0")));

        if (e.getContactName() != null && !e.getContactName().isBlank())
            properties.add(new ArrayList<>(List.of("fn", new LinkedHashMap<>(), "text", e.getContactName())));

        if (e.getOrganization() != null && !e.getOrganization().isBlank())
            properties.add(new ArrayList<>(List.of("org", new LinkedHashMap<>(), "text", e.getOrganization())));

        if (e.getEmail() != null && !e.getEmail().isBlank())
            properties.add(new ArrayList<>(List.of("email", new LinkedHashMap<>(), "text", e.getEmail())));

        if (e.getPhone() != null && !e.getPhone().isBlank()) {
            Map<String, Object> telParams = new LinkedHashMap<>();
            telParams.put("type", "voice");
            properties.add(new ArrayList<>(List.of("tel", telParams, "uri",
                    e.getPhone().startsWith("tel:") ? e.getPhone() : "tel:" + e.getPhone())));
        }

        if (e.getFax() != null && !e.getFax().isBlank()) {
            Map<String, Object> faxParams = new LinkedHashMap<>();
            faxParams.put("type", "fax");
            properties.add(new ArrayList<>(List.of("tel", faxParams, "uri",
                    e.getFax().startsWith("tel:") ? e.getFax() : "tel:" + e.getFax())));
        }

        if (e.getAddressStreet1() != null || e.getAddressCity() != null || e.getAddressCountry() != null) {
            List<String> addressComponents = List.of(
                "",
                e.getAddressStreet2() != null ? e.getAddressStreet2() : "",
                e.getAddressStreet1() != null ? e.getAddressStreet1() : "",
                e.getAddressCity() != null ? e.getAddressCity() : "",
                e.getAddressState() != null ? e.getAddressState() : "",
                e.getAddressPostalCode() != null ? e.getAddressPostalCode() : "",
                e.getAddressCountry() != null ? e.getAddressCountry() : ""
            );
            properties.add(new ArrayList<>(List.of("adr", new LinkedHashMap<>(), "text", addressComponents)));
        }

        vcard.add(properties);
        return vcard;
    }

    private List<String> parseAgreements(String a) {
        return a == null || a.isBlank() ? Collections.emptyList() :
            Arrays.stream(a.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseCustomParams(String customParams) {
        if (customParams == null || customParams.isBlank()) return null;
        try {
            Object parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(customParams, Object.class);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
            log.warn("customParams is not a JSON object: {}", customParams);
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse customParams: {}", e.getMessage());
            return null;
        }
    }

    private String getClientIp(HttpServletRequest r) {
        String f = r.getHeader("X-Forwarded-For");
        return f != null && !f.isEmpty() ? f.split(",")[0].trim() : r.getRemoteAddr();
    }
}