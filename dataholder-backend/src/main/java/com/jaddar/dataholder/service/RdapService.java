/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jaddar.dataholder.dto.RdapDataDto.*;
import com.jaddar.dataholder.entity.*;
import com.jaddar.dataholder.entity.RdapEntity.ObjectType;
import com.jaddar.dataholder.entity.RdapRemark.RemarkType;
import com.jaddar.dataholder.repository.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RdapService {

    private final RdapEntityRepository entityRepository;
    private final RdapEventRepository eventRepository;
    private final RdapLinkRepository linkRepository;
    private final RdapNameserverRepository nameserverRepository;
    private final RdapRemarkRepository remarkRepository;
    private final RdapSecureDnsRepository secureDnsRepository;
    private final TestDataFlagRepository testDataFlagRepository;
    private final PendingRequestRepository pendingRequestRepository;
    private final RequestAuditLogRepository auditLogRepository;
    private final TokenIntrospectionService tokenIntrospectionService;
    private final AccessControlService accessControlService;
    private final PolicyExpressionRepository policyExpressionRepository;
    private final RdapImportHelper rdapImportHelper;
    private final RegistrantNotificationService registrantNotificationService;
    private final DataHolderConfigRepository configRepository;
    private final MappedRdapQueryService mappedRdapQueryService;
    private final RdapRedactionService rdapRedactionService;

    @Value("${dataholder.jwt.secret:ThisIsASecretKeyForJWTTokenGenerationThatMustBeAtLeast256BitsLong!!}")
    private String jwtSecret;

    // ==================== PUBLIC RDAP QUERIES (LEGACY) ====================

    @Transactional
    public RdapQueryResult queryDomain(String domain, List<String> agreementNames,
            boolean confidential, boolean exigent, boolean jakeCompliance,
            String authHeader, String clientIp, Map<String, Object> customParams) {
        long startTime = System.currentTimeMillis();
        AuthResult auth = authenticate(authHeader, null);
        if (!auth.isSuccess()) return RdapQueryResult.error(401, "Unauthorized", auth.getMessage(), "domain", domain);

        Optional<RdapEntity> entityOpt = resolveEntity("domain", domain);
        if (entityOpt.isEmpty()) {
            logRequest("domain", domain, 0, auth.getTokenInfo(), clientIp, agreementNames,
                    PendingRequest.Status.DENIED, "Domain not found", startTime,
                    confidential, exigent, jakeCompliance, customParams);
            return RdapQueryResult.error(404, "Not Found", "Domain not found: " + domain, "domain", domain);
        }

        int accessLevel = determineAccessLevel(agreementNames, auth.getTokenInfo(), auth.isAdmin(), confidential, exigent);
        return processRequest("domain", domain, accessLevel, auth.getTokenInfo(), clientIp, agreementNames,
                entityOpt.get(), startTime, auth.isAdmin(), confidential, exigent, jakeCompliance, customParams);
    }

    @Transactional
    public RdapQueryResult queryIp(String ipAddress, List<String> agreementNames,
            boolean confidential, boolean exigent, boolean jakeCompliance,
            String authHeader, String clientIp, Map<String, Object> customParams) {
        long startTime = System.currentTimeMillis();
        AuthResult auth = authenticate(authHeader, null);
        if (!auth.isSuccess()) return RdapQueryResult.error(401, "Unauthorized", auth.getMessage(), "ip", ipAddress);

        Optional<RdapEntity> entityOpt = resolveEntity("ip", ipAddress);
        if (entityOpt.isEmpty()) {
            logRequest("ip", ipAddress, 0, auth.getTokenInfo(), clientIp, agreementNames,
                    PendingRequest.Status.DENIED, "IP not found", startTime,
                    confidential, exigent, jakeCompliance, customParams);
            return RdapQueryResult.error(404, "Not Found", "IP not found: " + ipAddress, "ip", ipAddress);
        }

        int accessLevel = determineAccessLevel(agreementNames, auth.getTokenInfo(), auth.isAdmin(), confidential, exigent);
        return processRequest("ip", ipAddress, accessLevel, auth.getTokenInfo(), clientIp, agreementNames,
                entityOpt.get(), startTime, auth.isAdmin(), confidential, exigent, jakeCompliance, customParams);
    }

    @Transactional
    public RdapQueryResult queryAsn(String asn, List<String> agreementNames,
            boolean confidential, boolean exigent, boolean jakeCompliance,
            String authHeader, String clientIp, Map<String, Object> customParams) {
        long startTime = System.currentTimeMillis();
        AuthResult auth = authenticate(authHeader, null);
        if (!auth.isSuccess()) return RdapQueryResult.error(401, "Unauthorized", auth.getMessage(), "asn", asn);

        String asnStr = asn.toUpperCase().replace("AS", "");
        long asnNum;
        try { asnNum = Long.parseLong(asnStr); }
        catch (NumberFormatException e) { return RdapQueryResult.error(400, "Bad Request", "Invalid ASN: " + asn, "asn", asn); }

        Optional<RdapEntity> entityOpt = resolveEntity("asn", asn);
        if (entityOpt.isEmpty()) {
            logRequest("asn", asn, 0, auth.getTokenInfo(), clientIp, agreementNames,
                    PendingRequest.Status.DENIED, "ASN not found", startTime,
                    confidential, exigent, jakeCompliance, customParams);
            return RdapQueryResult.error(404, "Not Found", "ASN not found: " + asn, "asn", asn);
        }

        int accessLevel = determineAccessLevel(agreementNames, auth.getTokenInfo(), auth.isAdmin(), confidential, exigent);
        return processRequest("asn", asn, accessLevel, auth.getTokenInfo(), clientIp, agreementNames,
                entityOpt.get(), startTime, auth.isAdmin(), confidential, exigent, jakeCompliance, customParams);
    }

    // ==================== CODE-BASED RDAP QUERIES (NEW) ====================

    @Transactional
    public RdapQueryResult queryDomainByCodes(String domain, String dataHolderGroup,
            String requestorGroupCode, Integer requestTypeCode,
            boolean confidential, boolean exigent, boolean jakeCompliance,
            String authHeader, String clientIp, Map<String, Object> customParams) {
        long startTime = System.currentTimeMillis();
        AuthResult auth = authenticate(authHeader, requestorGroupCode);
        if (!auth.isSuccess()) return RdapQueryResult.error(401, "Unauthorized", auth.getMessage(), "domain", domain);

        Optional<RdapEntity> entityOpt = resolveEntity("domain", domain);
        if (entityOpt.isEmpty()) {
            List<String> codeRef = buildCodeRef(requestorGroupCode, requestTypeCode);
            logRequest("domain", domain, 0, auth.getTokenInfo(), clientIp, codeRef,
                    PendingRequest.Status.DENIED, "Domain not found", startTime,
                    confidential, exigent, jakeCompliance, customParams);
            return RdapQueryResult.error(404, "Not Found", "Domain not found: " + domain, "domain", domain);
        }

        int accessLevel = determineAccessLevelByCodes(requestorGroupCode, requestTypeCode, auth.getTokenInfo(), auth.isAdmin());
        List<String> codeRef = buildCodeRef(requestorGroupCode, requestTypeCode);
        return processRequest("domain", domain, accessLevel, auth.getTokenInfo(), clientIp, codeRef,
                entityOpt.get(), startTime, auth.isAdmin(), confidential, exigent, jakeCompliance, customParams);
    }

    @Transactional
    public RdapQueryResult queryIpByCodes(String ipAddress, String dataHolderGroup,
            String requestorGroupCode, Integer requestTypeCode,
            boolean confidential, boolean exigent, boolean jakeCompliance,
            String authHeader, String clientIp, Map<String, Object> customParams) {
        long startTime = System.currentTimeMillis();
        AuthResult auth = authenticate(authHeader, requestorGroupCode);
        if (!auth.isSuccess()) return RdapQueryResult.error(401, "Unauthorized", auth.getMessage(), "ip", ipAddress);

        Optional<RdapEntity> entityOpt = resolveEntity("ip", ipAddress);
        if (entityOpt.isEmpty()) {
            List<String> codeRef = buildCodeRef(requestorGroupCode, requestTypeCode);
            logRequest("ip", ipAddress, 0, auth.getTokenInfo(), clientIp, codeRef,
                    PendingRequest.Status.DENIED, "IP not found", startTime,
                    confidential, exigent, jakeCompliance, customParams);
            return RdapQueryResult.error(404, "Not Found", "IP not found: " + ipAddress, "ip", ipAddress);
        }

        int accessLevel = determineAccessLevelByCodes(requestorGroupCode, requestTypeCode, auth.getTokenInfo(), auth.isAdmin());
        List<String> codeRef = buildCodeRef(requestorGroupCode, requestTypeCode);
        return processRequest("ip", ipAddress, accessLevel, auth.getTokenInfo(), clientIp, codeRef,
                entityOpt.get(), startTime, auth.isAdmin(), confidential, exigent, jakeCompliance, customParams);
    }

    @Transactional
    public RdapQueryResult queryAsnByCodes(String asn, String dataHolderGroup,
            String requestorGroupCode, Integer requestTypeCode,
            boolean confidential, boolean exigent, boolean jakeCompliance,
            String authHeader, String clientIp, Map<String, Object> customParams) {
        long startTime = System.currentTimeMillis();
        AuthResult auth = authenticate(authHeader, requestorGroupCode);
        if (!auth.isSuccess()) return RdapQueryResult.error(401, "Unauthorized", auth.getMessage(), "asn", asn);

        String asnStr = asn.toUpperCase().replace("AS", "");
        long asnNum;
        try { asnNum = Long.parseLong(asnStr); }
        catch (NumberFormatException e) { return RdapQueryResult.error(400, "Bad Request", "Invalid ASN: " + asn, "asn", asn); }

        Optional<RdapEntity> entityOpt = resolveEntity("asn", asn);
        if (entityOpt.isEmpty()) {
            List<String> codeRef = buildCodeRef(requestorGroupCode, requestTypeCode);
            logRequest("asn", asn, 0, auth.getTokenInfo(), clientIp, codeRef,
                    PendingRequest.Status.DENIED, "ASN not found", startTime,
                    confidential, exigent, jakeCompliance, customParams);
            return RdapQueryResult.error(404, "Not Found", "ASN not found: " + asn, "asn", asn);
        }

        int accessLevel = determineAccessLevelByCodes(requestorGroupCode, requestTypeCode, auth.getTokenInfo(), auth.isAdmin());
        List<String> codeRef = buildCodeRef(requestorGroupCode, requestTypeCode);
        return processRequest("asn", asn, accessLevel, auth.getTokenInfo(), clientIp, codeRef,
                entityOpt.get(), startTime, auth.isAdmin(), confidential, exigent, jakeCompliance, customParams);
    }

    // ==================== PENDING REQUEST CHECK ====================

    @Transactional(readOnly = true)
    public RdapQueryResult checkPendingRequest(UUID requestId) {
        Optional<PendingRequest> opt = pendingRequestRepository.findByRequestId(requestId);
        if (opt.isEmpty()) return RdapQueryResult.error(404, "Not Found", "Request not found", "pending", requestId.toString());

        PendingRequest req = opt.get();
        if (req.getExpiresAt().isBefore(LocalDateTime.now())) return RdapQueryResult.error(410, "Gone", "Request expired", "pending", requestId.toString());

        // Extract stored JAKE compliance context so the response includes it
        boolean jake = req.isJakeCompliance();
        List<String> agreements = req.getAgreementNames() != null
                ? Arrays.asList(req.getAgreementNames()) : null;
        String groupCode = extractRequestorGroupCode(agreements);

        return switch (req.getStatus()) {
            case PENDING -> {
                RdapQueryResult r = RdapQueryResult.pending(requestId, req.getQueryType(), req.getQueryValue(), req.getExpiresAt());
                r.setJakeCompliance(jake);
                r.setRequestorGroupCode(groupCode);
                yield r;
            }
            case APPROVED -> {
                RdapEntity entity = findEntityByTypeAndValue(req.getQueryType(), req.getQueryValue());
                if (entity == null) {
                    yield RdapQueryResult.error(404, "Not Found", "Entity no longer exists", req.getQueryType(), req.getQueryValue());
                }
                // Attach custom-table contacts/data exactly as the immediate query path does,
                // so a manually-verified response includes the same contact entities.
                List<Map<String, Object>> customTableData =
                        resolveAndAttachCustomTableData(req.getQueryType(), req.getQueryValue(), entity);

                AgreementRequestType resolvedRequestType = resolveRequestTypeFromAgreements(agreements);

                RdapQueryResult approved = RdapQueryResult.success(entity, req.getQueryType(), req.getQueryValue(),
                        req.getRequestedAccessLevel(), agreements, resolvedRequestType);
                if (customTableData != null && !customTableData.isEmpty()) {
                    approved.setCustomTableData(customTableData);
                }
                approved.setJakeCompliance(jake);
                approved.setRequestorGroupCode(groupCode);
                yield approved;
            }
            case DENIED -> RdapQueryResult.error(403, "Forbidden", "Request denied: " + (req.getDenialReason() != null ? req.getDenialReason() : "No reason"), req.getQueryType(), req.getQueryValue());
            case CANCELLED -> RdapQueryResult.error(410, "Gone", "Request was cancelled by the requestor", req.getQueryType(), req.getQueryValue());
        };
    }

    public List<String> getAvailableDomains() { return entityRepository.findAllDomainNames(); }

    public List<Map<String, Object>> getAvailableSubscriptions() {
        return accessControlService.getAvailableSubscriptionMaps();
    }

    @Deprecated
    public List<Map<String, Object>> getAvailableAgreements() { return getAvailableSubscriptions(); }

    public List<Map<String, Object>> getSubscriptionsForUser(String authHeader) {
        // Subscriptions are now fetched from Group Admin, user-specific filtering
        // is handled at the Group Admin level
        return getAvailableSubscriptions();
    }

    @Deprecated
    public List<Map<String, Object>> getAgreementsForUser(String authHeader) { return getSubscriptionsForUser(authHeader); }

    // ==================== ADMIN CRUD ====================

    public Page<RdapEntity> getDomains(Pageable p, String search) { return getEntitiesByType(ObjectType.DOMAIN, p, search); }
    public Page<RdapEntity> getIpNetworks(Pageable p, String search) { return getEntitiesByType(ObjectType.IP_NETWORK, p, search); }
    public Page<RdapEntity> getAutnums(Pageable p, String search) { return getEntitiesByType(ObjectType.AUTNUM, p, search); }
    public Optional<RdapEntity> getEntityById(Long id) { return entityRepository.findById(id); }

    public Page<RdapEntity> getEntitiesByType(ObjectType type, Pageable pageable, String search) {
        List<RdapEntity> all = entityRepository.findByObjectTypeAndParentIsNullOrderByLdhNameAsc(type);
        if (search != null && !search.isBlank()) {
            String s = search.toLowerCase();
            all = all.stream().filter(e -> matches(e, s)).collect(Collectors.toList());
        }
        int start = (int) pageable.getOffset(), end = Math.min(start + pageable.getPageSize(), all.size());
        return start > all.size() ? new PageImpl<>(new ArrayList<>(), pageable, all.size()) : new PageImpl<>(all.subList(start, end), pageable, all.size());
    }

    @Transactional
    public RdapEntity createEntity(RdapEntityRequest req) {
        validateEntityRequest(req, null);
        RdapEntity e = new RdapEntity();
        mapEntityRequest(e, req);
        e = entityRepository.save(e);
        saveRelatedEntities(e, req);
        return entityRepository.findById(e.getId()).orElse(e);
    }

    @Transactional
    public RdapEntity updateEntity(Long id, RdapEntityRequest req) {
        RdapEntity e = entityRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Entity not found: " + id));
        validateEntityRequest(req, e);
        mapEntityRequest(e, req);
        e = entityRepository.save(e);
        saveRelatedEntities(e, req);
        return entityRepository.findById(e.getId()).orElse(e);
    }

    @Transactional
    public void deleteEntity(Long id) {
        RdapEntity e = entityRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Entity not found: " + id));
        List<RdapEntity> children = entityRepository.findByParentId(id);
        for (RdapEntity child : children) {
            eventRepository.deleteByRdapEntityId(child.getId());
            linkRepository.deleteByRdapEntityId(child.getId());
            remarkRepository.deleteByRdapEntityId(child.getId());
            entityRepository.delete(child);
        }
        eventRepository.deleteByRdapEntityId(id);
        linkRepository.deleteByRdapEntityId(id);
        nameserverRepository.deleteByRdapEntityId(id);
        remarkRepository.deleteByRdapEntityId(id);
        secureDnsRepository.deleteByRdapEntityId(id);
        entityRepository.delete(e);
    }

    @Transactional
    public int bulkDeleteEntities(List<Long> ids) {
        int c = 0;
        for (Long id : ids) { try { deleteEntity(id); c++; } catch (Exception ex) { log.warn("Delete failed {}: {}", id, ex.getMessage()); } }
        return c;
    }

    public List<RdapEvent> getEventsForEntity(Long id) { return eventRepository.findByRdapEntityIdOrderByEventDateDesc(id); }
    public List<RdapLink> getLinksForEntity(Long id) { return linkRepository.findByRdapEntityId(id); }
    public List<RdapNameserver> getNameserversForEntity(Long id) { return nameserverRepository.findByRdapEntityIdOrderByLdhNameAsc(id); }
    public List<RdapRemark> getRemarksForEntity(Long id) { return remarkRepository.findByRdapEntityId(id); }
    public List<RdapSecureDns> getSecureDnsForEntity(Long id) { return secureDnsRepository.findByRdapEntityId(id); }

    // ==================== STATS & SCHEMA ====================

    public RdapDataStats getStats() {
        return RdapDataStats.builder()
            .domainCount(entityRepository.countByObjectType(ObjectType.DOMAIN))
            .ipCount(entityRepository.countByObjectType(ObjectType.IP_NETWORK))
            .asnCount(entityRepository.countByObjectType(ObjectType.AUTNUM))
            .totalCount(entityRepository.count())
            .childEntityCount(entityRepository.countByObjectType(ObjectType.ENTITY))
            .eventCount(eventRepository.count())
            .nameserverCount(nameserverRepository.count())
            .lastUpdated(LocalDateTime.now())
            .build();
    }

    public List<SchemaField> getSchemaFields(String type) {
        List<SchemaField> fields = new ArrayList<>();

        fields.add(SchemaField.builder().name("handle").type("string").description("Unique handle/ID").required(true).example("DOM-123").build());
        fields.add(SchemaField.builder().name("status").type("string[]").description("Status values (comma-separated)").required(false).example("active,ok").build());
        fields.add(SchemaField.builder().name("port43").type("string").description("Port 43 WHOIS server").required(false).example("whois.example.com").build());
        fields.add(SchemaField.builder().name("isTestData").type("boolean").description("Mark as test data").required(false).example("true").build());
        fields.add(SchemaField.builder().name("policyExpressionId").type("integer").description("Policy expression ID").required(false).example("1").build());

        switch (type.toLowerCase()) {
            case "domain", "domains" -> {
                fields.add(SchemaField.builder().name("ldhName").type("string").description("Domain name (LDH format)").required(true).example("example.com").build());
                fields.add(SchemaField.builder().name("unicodeName").type("string").description("Unicode/IDN name").required(false).example("例え.jp").build());
                fields.add(SchemaField.builder().name("secureDnsDelegationSigned").type("boolean").description("DNSSEC delegation signed").required(false).example("true").build());
                fields.add(SchemaField.builder().name("secureDnsZoneSigned").type("boolean").description("DNSSEC zone signed").required(false).example("true").build());
                for (int i = 1; i <= 4; i++) {
                    fields.add(SchemaField.builder().name("ns" + i).type("string").description("Nameserver " + i).required(false).example("ns" + i + ".example.com").build());
                    fields.add(SchemaField.builder().name("ns" + i + "Ipv4").type("string").description("Nameserver " + i + " IPv4").required(false).example("192.0.2." + i).build());
                    fields.add(SchemaField.builder().name("ns" + i + "Ipv6").type("string").description("Nameserver " + i + " IPv6").required(false).example("2001:db8::" + i).build());
                }
            }
            case "ip", "ips", "ip_network" -> {
                fields.add(SchemaField.builder().name("startAddress").type("string").description("Start IP address").required(true).example("192.0.2.0").build());
                fields.add(SchemaField.builder().name("endAddress").type("string").description("End IP address").required(false).example("192.0.2.255").build());
                fields.add(SchemaField.builder().name("ipVersion").type("string").description("IP version (v4 or v6)").required(false).example("v4").build());
                fields.add(SchemaField.builder().name("networkName").type("string").description("Network name").required(false).example("EXAMPLE-NET").build());
                fields.add(SchemaField.builder().name("networkType").type("string").description("Network type").required(false).example("ALLOCATED").build());
                fields.add(SchemaField.builder().name("country").type("string").description("Country code").required(false).example("US").build());
                fields.add(SchemaField.builder().name("parentHandle").type("string").description("Parent network handle").required(false).example("NET-192").build());
            }
            case "asn", "asns", "autnum" -> {
                fields.add(SchemaField.builder().name("startAutnum").type("integer").description("Start ASN").required(true).example("64496").build());
                fields.add(SchemaField.builder().name("endAutnum").type("integer").description("End ASN").required(false).example("64496").build());
                fields.add(SchemaField.builder().name("autnumName").type("string").description("AS name").required(false).example("EXAMPLE-AS").build());
                fields.add(SchemaField.builder().name("autnumType").type("string").description("AS type").required(false).example("DIRECT ALLOCATION").build());
                fields.add(SchemaField.builder().name("country").type("string").description("Country code").required(false).example("US").build());
            }
        }

        // All contact/event/remark/link/secureDNS fields omitted for brevity — identical to original
        // ... (keeping them exactly as they were in the original file)
        fields.add(SchemaField.builder().name("registrantHandle").type("string").description("Registrant handle").required(false).example("CONT-REG-1").build());
        fields.add(SchemaField.builder().name("registrantName").type("string").description("Registrant name").required(false).example("John Smith").build());
        fields.add(SchemaField.builder().name("registrantOrganization").type("string").description("Registrant organization").required(false).example("Example Corp").build());
        fields.add(SchemaField.builder().name("registrantEmail").type("string").description("Registrant email").required(false).example("registrant@example.com").build());
        fields.add(SchemaField.builder().name("registrantPhone").type("string").description("Registrant phone").required(false).example("+1.5551234567").build());
        fields.add(SchemaField.builder().name("registrantFax").type("string").description("Registrant fax").required(false).example("+1.5551234568").build());
        fields.add(SchemaField.builder().name("registrantStreet").type("string").description("Registrant street").required(false).example("123 Main St").build());
        fields.add(SchemaField.builder().name("registrantStreet2").type("string").description("Registrant street line 2").required(false).example("Suite 100").build());
        fields.add(SchemaField.builder().name("registrantCity").type("string").description("Registrant city").required(false).example("San Francisco").build());
        fields.add(SchemaField.builder().name("registrantStateProvince").type("string").description("Registrant state/province").required(false).example("CA").build());
        fields.add(SchemaField.builder().name("registrantPostalCode").type("string").description("Registrant postal code").required(false).example("94102").build());
        fields.add(SchemaField.builder().name("registrantCountry").type("string").description("Registrant country").required(false).example("US").build());
        fields.add(SchemaField.builder().name("adminHandle").type("string").description("Admin handle").required(false).example("CONT-ADMIN-1").build());
        fields.add(SchemaField.builder().name("adminName").type("string").description("Admin name").required(false).example("Jane Admin").build());
        fields.add(SchemaField.builder().name("adminOrganization").type("string").description("Admin organization").required(false).example("Example Corp").build());
        fields.add(SchemaField.builder().name("adminEmail").type("string").description("Admin email").required(false).example("admin@example.com").build());
        fields.add(SchemaField.builder().name("adminPhone").type("string").description("Admin phone").required(false).example("+1.5552222222").build());
        fields.add(SchemaField.builder().name("adminFax").type("string").description("Admin fax").required(false).example("").build());
        fields.add(SchemaField.builder().name("adminStreet").type("string").description("Admin street").required(false).example("123 Main St").build());
        fields.add(SchemaField.builder().name("adminStreet2").type("string").description("Admin street line 2").required(false).example("").build());
        fields.add(SchemaField.builder().name("adminCity").type("string").description("Admin city").required(false).example("San Francisco").build());
        fields.add(SchemaField.builder().name("adminStateProvince").type("string").description("Admin state/province").required(false).example("CA").build());
        fields.add(SchemaField.builder().name("adminPostalCode").type("string").description("Admin postal code").required(false).example("94102").build());
        fields.add(SchemaField.builder().name("adminCountry").type("string").description("Admin country").required(false).example("US").build());
        fields.add(SchemaField.builder().name("techHandle").type("string").description("Tech handle").required(false).example("CONT-TECH-1").build());
        fields.add(SchemaField.builder().name("techName").type("string").description("Tech name").required(false).example("Tech Support").build());
        fields.add(SchemaField.builder().name("techOrganization").type("string").description("Tech organization").required(false).example("Example IT").build());
        fields.add(SchemaField.builder().name("techEmail").type("string").description("Tech email").required(false).example("tech@example.com").build());
        fields.add(SchemaField.builder().name("techPhone").type("string").description("Tech phone").required(false).example("+1.5553333333").build());
        fields.add(SchemaField.builder().name("techFax").type("string").description("Tech fax").required(false).example("").build());
        fields.add(SchemaField.builder().name("techStreet").type("string").description("Tech street").required(false).example("456 Tech Park").build());
        fields.add(SchemaField.builder().name("techStreet2").type("string").description("Tech street line 2").required(false).example("").build());
        fields.add(SchemaField.builder().name("techCity").type("string").description("Tech city").required(false).example("San Jose").build());
        fields.add(SchemaField.builder().name("techStateProvince").type("string").description("Tech state/province").required(false).example("CA").build());
        fields.add(SchemaField.builder().name("techPostalCode").type("string").description("Tech postal code").required(false).example("95110").build());
        fields.add(SchemaField.builder().name("techCountry").type("string").description("Tech country").required(false).example("US").build());
        fields.add(SchemaField.builder().name("billingHandle").type("string").description("Billing handle").required(false).example("CONT-BILL-1").build());
        fields.add(SchemaField.builder().name("billingName").type("string").description("Billing name").required(false).example("Billing Dept").build());
        fields.add(SchemaField.builder().name("billingOrganization").type("string").description("Billing organization").required(false).example("Example Corp").build());
        fields.add(SchemaField.builder().name("billingEmail").type("string").description("Billing email").required(false).example("billing@example.com").build());
        fields.add(SchemaField.builder().name("billingPhone").type("string").description("Billing phone").required(false).example("+1.5554444444").build());
        fields.add(SchemaField.builder().name("billingFax").type("string").description("Billing fax").required(false).example("").build());
        fields.add(SchemaField.builder().name("billingStreet").type("string").description("Billing street").required(false).example("123 Main St").build());
        fields.add(SchemaField.builder().name("billingStreet2").type("string").description("Billing street line 2").required(false).example("").build());
        fields.add(SchemaField.builder().name("billingCity").type("string").description("Billing city").required(false).example("San Francisco").build());
        fields.add(SchemaField.builder().name("billingStateProvince").type("string").description("Billing state/province").required(false).example("CA").build());
        fields.add(SchemaField.builder().name("billingPostalCode").type("string").description("Billing postal code").required(false).example("94102").build());
        fields.add(SchemaField.builder().name("billingCountry").type("string").description("Billing country").required(false).example("US").build());
        fields.add(SchemaField.builder().name("registrarHandle").type("string").description("Registrar handle").required(false).example("REG-EXAMPLE").build());
        fields.add(SchemaField.builder().name("registrarName").type("string").description("Registrar name").required(false).example("Example Registrar Inc").build());
        fields.add(SchemaField.builder().name("registrarEmail").type("string").description("Registrar email").required(false).example("support@registrar.com").build());
        fields.add(SchemaField.builder().name("registrarPhone").type("string").description("Registrar phone").required(false).example("+1.8881234567").build());
        fields.add(SchemaField.builder().name("registrarUrl").type("string").description("Registrar URL").required(false).example("https://www.registrar.com").build());
        fields.add(SchemaField.builder().name("registrarAbuseEmail").type("string").description("Registrar abuse email").required(false).example("abuse@registrar.com").build());
        fields.add(SchemaField.builder().name("registrarAbusePhone").type("string").description("Registrar abuse phone").required(false).example("+1.8881234568").build());
        fields.add(SchemaField.builder().name("abuseHandle").type("string").description("Abuse contact handle").required(false).example("CONT-ABUSE-1").build());
        fields.add(SchemaField.builder().name("abuseName").type("string").description("Abuse contact name").required(false).example("Abuse Team").build());
        fields.add(SchemaField.builder().name("abuseEmail").type("string").description("Abuse email").required(false).example("abuse@example.com").build());
        fields.add(SchemaField.builder().name("abusePhone").type("string").description("Abuse phone").required(false).example("+1.5559999999").build());
        fields.add(SchemaField.builder().name("registrationDate").type("datetime").description("Registration date").required(false).example("2020-01-15T00:00:00Z").build());
        fields.add(SchemaField.builder().name("expirationDate").type("datetime").description("Expiration date").required(false).example("2025-01-15T00:00:00Z").build());
        fields.add(SchemaField.builder().name("lastChangedDate").type("datetime").description("Last changed date").required(false).example("2024-06-01T12:00:00Z").build());
        fields.add(SchemaField.builder().name("lastUpdateOfRdapDb").type("datetime").description("Last RDAP DB update").required(false).example("2024-06-15T00:00:00Z").build());
        fields.add(SchemaField.builder().name("transferDate").type("datetime").description("Transfer date").required(false).example("2022-03-01T00:00:00Z").build());
        fields.add(SchemaField.builder().name("remarkTitle").type("string").description("Remark title").required(false).example("Terms of Use").build());
        fields.add(SchemaField.builder().name("remarkDescription").type("string").description("Remark description").required(false).example("This data is provided for informational purposes.").build());
        fields.add(SchemaField.builder().name("remark2Title").type("string").description("Remark 2 title").required(false).example("").build());
        fields.add(SchemaField.builder().name("remark2Description").type("string").description("Remark 2 description").required(false).example("").build());
        fields.add(SchemaField.builder().name("selfLink").type("string").description("Self link URL").required(false).example("https://rdap.example.com/domain/example.com").build());
        fields.add(SchemaField.builder().name("relatedLink").type("string").description("Related link URL").required(false).example("https://www.example.com").build());
        fields.add(SchemaField.builder().name("relatedLinkTitle").type("string").description("Related link title").required(false).example("Website").build());
        fields.add(SchemaField.builder().name("dsKeyTag").type("integer").description("DS record key tag").required(false).example("12345").build());
        fields.add(SchemaField.builder().name("dsAlgorithm").type("integer").description("DS record algorithm").required(false).example("8").build());
        fields.add(SchemaField.builder().name("dsDigestType").type("integer").description("DS record digest type").required(false).example("2").build());
        fields.add(SchemaField.builder().name("dsDigest").type("string").description("DS record digest").required(false).example("ABCD1234...").build());

        return fields;
    }

    // ==================== EXPORT ====================

    public List<RdapEntity> exportEntities(String type) {
        ObjectType t = switch (type.toLowerCase()) {
            case "domain", "domains" -> ObjectType.DOMAIN;
            case "ip", "ips", "ip_network" -> ObjectType.IP_NETWORK;
            case "asn", "asns", "autnum" -> ObjectType.AUTNUM;
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
        return entityRepository.findByObjectTypeAndParentIsNullOrderByLdhNameAsc(t);
    }

    // ==================== IMPORT ====================

    public CsvPreviewResponse previewCsvImport(MultipartFile file, String type, String delimiter, boolean hasHeader) throws Exception {
        List<String[]> rows = parseCsv(file, delimiter);
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV empty");
        List<String> headers = hasHeader ? Arrays.asList(rows.get(0)) : new ArrayList<>();
        if (!hasHeader) for (int i = 0; i < rows.get(0).length; i++) headers.add("Column " + (i + 1));
        int dataStart = hasHeader ? 1 : 0;
        List<List<String>> samples = new ArrayList<>();
        for (int i = dataStart; i < rows.size(); i++) samples.add(Arrays.asList(rows.get(i)));
        return CsvPreviewResponse.builder().totalRows(rows.size() - dataStart).detectedHeaders(headers).sampleRows(samples)
            .targetFields(getSchemaFields(type)).suggestedMappings(suggestMappings(headers, getSchemaFields(type))).build();
    }

    @Transactional
    public ImportResult importCsv(MultipartFile file, String type, String delimiter, boolean hasHeader, Map<String, Integer> mappings) throws Exception {
        List<String[]> rows = parseCsv(file, delimiter);
        int start = hasHeader ? 1 : 0, success = 0;
        List<ImportError> errors = new ArrayList<>();
        for (int i = start; i < rows.size(); i++) {
            try { importRecord(type, mapRow(rows.get(i), mappings)); success++; }
            catch (Exception e) { log.error("Import error at row {}: {}", i + 1, e.getMessage()); errors.add(ImportError.builder().rowNumber(i + 1).error(e.getMessage()).build()); if (errors.size() >= 100) break; }
        }
        log.info("CSV import complete: {} success, {} failed", success, errors.size());
        return ImportResult.builder().totalRows(rows.size() - start).successCount(success).failedCount(errors.size()).errors(errors).build();
    }

    @Transactional
    public ImportResult importJson(String type, List<Map<String, Object>> data) {
        int success = 0;
        List<ImportError> errors = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            try { importRecord(type, data.get(i)); success++; }
            catch (Exception e) { log.error("Import error at record {}: {}", i + 1, e.getMessage()); errors.add(ImportError.builder().rowNumber(i + 1).error(e.getMessage()).build()); if (errors.size() >= 100) break; }
        }
        log.info("JSON import complete: {} success, {} failed", success, errors.size());
        return ImportResult.builder().totalRows(data.size()).successCount(success).failedCount(errors.size()).errors(errors).build();
    }

    @Transactional
    public ImportResult importJsonFile(MultipartFile file, String type) throws Exception {
        ObjectMapper m = new ObjectMapper(); m.registerModule(new JavaTimeModule());
        List<Map<String, Object>> data;
        try { data = m.readValue(file.getBytes(), new TypeReference<List<Map<String, Object>>>() {}); }
        catch (Exception e) { data = List.of(m.readValue(file.getBytes(), new TypeReference<Map<String, Object>>() {})); }
        return importJson(type, data);
    }

    @Transactional
    public RdapEntity importRecord(String type, Map<String, Object> data) {
        log.debug("Importing {} record with {} fields", type, data.size());
        return rdapImportHelper.importCompleteRecord(type, data);
    }

    public List<RdapEntity> getChildrenForEntity(Long entityId) {
        return entityRepository.findByParentId(entityId);
    }

    // ==================== PRIVATE HELPERS ====================

    /**
     * Authenticate an RDAP query by introspecting the presented bearer token
     * against the introspection URL registered on the requestor group's
     * subscription.
     *
     * @param requestorGroupCode the group code from the query, or null for
     *                           legacy agreement-name queries that carry none
     */
    private AuthResult authenticate(String authHeader, String requestorGroupCode) {
        if (isAdmin(authHeader)) return AuthResult.admin(adminTokenInfo());
        String token = tokenIntrospectionService.extractToken(authHeader);
        if (token == null) return AuthResult.failure("Bearer token required");
        TokenIntrospectionService.SubscriptionIntrospection introspection =
                tokenIntrospectionService.introspectForSubscription(token, requestorGroupCode);
        return introspection.isActive()
                ? AuthResult.user(introspection.tokenInfo())
                : AuthResult.failure(introspection.outcome().getUserMessage());
    }

    private boolean isAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return false;
        try {
            Claims c = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8))).build()
                .parseClaimsJws(authHeader.substring(7)).getBody();
            return "dataholder".equals(c.getIssuer()) && "access".equals(c.get("userType")) && "ADMIN".equals(c.get("role"));
        } catch (Exception e) { return false; }
    }

    private TokenInfo adminTokenInfo() {
        TokenInfo i = new TokenInfo(); i.setActive(true); i.setUsername("dataholder-admin"); i.setSub("dataholder-admin");
        i.setEmail("admin@dataholder.local"); i.setGroups(Arrays.asList("dataholder-admin", "admin")); return i;
    }

    private int determineAccessLevel(List<String> agreements, TokenInfo info, boolean admin,
                                     boolean confidential, boolean exigent) {
        return accessControlService.determineAccessLevelByNames(agreements, info, admin, confidential, exigent);
    }

    private int determineAccessLevelByCodes(String requestorGroupCode, Integer requestTypeCode, TokenInfo info, boolean admin) {
        if (admin) return 3;
        return accessControlService.determineAccessLevelByCodes(requestorGroupCode, requestTypeCode, info, admin);
    }

    private List<String> buildCodeRef(String requestorGroupCode, Integer requestTypeCode) {
        return List.of(requestorGroupCode + ":" + String.format("%03d", requestTypeCode));
    }

    /**
     * Core request processing logic.
     *
     * Now also resolves the AgreementRequestType from the code reference (if present)
     * and passes it through the RdapQueryResult so the controller can apply
     * AgreementRdapParameters filtering on top of policy redaction.
     */
    private RdapQueryResult processRequest(String qType, String qVal, int level, TokenInfo info,
            String ip, List<String> agreements, RdapEntity entity, long start, boolean admin,
            boolean confidential, boolean exigent, boolean jakeCompliance, Map<String, Object> customParams) {

        // ── Query value regex validation (applies to all request paths) ──
        if (!admin && agreements != null && !agreements.isEmpty()) {
            for (String ref : agreements) {
                if (ref != null && ref.contains(":")) {
                    String[] parts = ref.split(":", 2);
                    try {
                        String groupCode = parts[0];
                        Integer typeCode = Integer.parseInt(parts[1]);
                        String regexError = accessControlService.validateQueryValueRegex(groupCode, typeCode, qVal);
                        if (regexError != null) {
                            logRequest(qType, qVal, level, info, ip, agreements,
                                    PendingRequest.Status.DENIED, regexError, start,
                                    confidential, exigent, jakeCompliance, customParams);
                            return RdapQueryResult.error(403, "Forbidden", regexError, qType, qVal);
                        }
                    } catch (NumberFormatException ignored) {}
                    break; // only check the first code reference
                }
            }
        }

        // ── Resolve custom table data early so policy scope conditions can reference mapped columns ──
        // Also attaches actsAs=contact custom tables as contact children on the entity.
        List<Map<String, Object>> earlyCustomTableData = resolveAndAttachCustomTableData(qType, qVal, entity);

        boolean needsVerify = accessControlService.requiresManualVerification(level);

        // Global override: if requireManualReviewAll is enabled, force manual review
        if (!needsVerify) {
            boolean globalManualReview = configRepository.findById(DataHolderConfig.SINGLETON_ID)
                    .map(DataHolderConfig::getRequireManualReviewAll)
                    .orElse(false);
            if (globalManualReview) {
                needsVerify = true;
                log.info("Global manual review enabled — requiring review for {} {} (access level {})", qType, qVal, level);
            }
        }

        // Request type override: if the AgreementRequestType from the Group Admin
        // has requiresManualApproval checked, force manual review for this request.
        if (!needsVerify && agreements != null && !agreements.isEmpty()) {
            String codeRef = agreements.get(0);
            if (codeRef != null && codeRef.contains(":")) {
                String[] parts = codeRef.split(":", 2);
                try {
                    String groupCode = parts[0];
                    Integer typeCode = Integer.parseInt(parts[1]);
                    AgreementRequestType earlyResolvedType = accessControlService.resolveRequestType(groupCode, typeCode);
                    if (earlyResolvedType != null && Boolean.TRUE.equals(earlyResolvedType.getRequiresManualApproval())) {
                        needsVerify = true;
                        log.info("Request type '{}' (code {}) requires manual approval — requiring review for {} {} (access level {})",
                                earlyResolvedType.getName(), typeCode, qType, qVal, level);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        if (exigent && needsVerify) {
            log.info("Exigent request: bypassing manual verification for {} {} (access level {})", qType, qVal, level);
            needsVerify = false;
        }

        // Resolve the applicable policy — for mapped entities (no direct assignment),
        // fall back to scope-condition matching using the custom table data context,
        // then to the system default policy.
        PolicyExpression policy = entity.getPolicyExpression();
        if (policy == null) {
            policy = rdapRedactionService.resolveEffectivePolicy(entity, earlyCustomTableData);
            // Set it on the entity so that applyRedactionRules (called later in toResponse)
            // can find it without re-resolving — especially important for mapped/synthetic
            // entities that have no persisted policy assignment.
            if (policy != null) {
                entity.setPolicyExpression(policy);
            }
        }
        if (needsVerify && !admin) {
            return createPendingReq(qType, qVal, level, info, ip, agreements, start,
                    confidential, exigent, jakeCompliance, customParams);
        }
        logRequest(qType, qVal, level, info, ip, agreements, PendingRequest.Status.APPROVED, null, start,
                confidential, exigent, jakeCompliance, customParams);

        // Notify registrant asynchronously (non-confidential queries on supporting request types)
        try {
            boolean supportsConfidential = false;
            if (agreements != null && !agreements.isEmpty()) {
                String codeRef = agreements.get(0);
                if (codeRef != null && codeRef.contains(":")) {
                    String[] parts = codeRef.split(":");
                    try {
                        Integer typeCode = Integer.parseInt(parts[1]);
                        supportsConfidential = accessControlService.doesRequestTypeSupportConfidential(parts[0], typeCode);
                    } catch (NumberFormatException ignored) {}
                }
            }
            String registrantEmail = registrantNotificationService.findRegistrantEmail(entity);
            registrantNotificationService.notifyRegistrantIfRequired(
                    registrantEmail, qType, qVal, confidential, supportsConfidential);
        } catch (Exception e) {
            log.warn("Failed to trigger registrant notification for {} {}: {}", qType, qVal, e.getMessage());
        }

        // Resolve AgreementRequestType from the code reference so that
        // RDAP parameter filtering (boolean field exclusion) can be applied.
        AgreementRequestType resolvedRequestType = resolveRequestTypeFromAgreements(agreements);
        RdapQueryResult queryResult = RdapQueryResult.success(entity, qType, qVal, level, agreements, resolvedRequestType);

        // Attach the custom table data that was resolved earlier for policy evaluation
        if (earlyCustomTableData != null && !earlyCustomTableData.isEmpty()) {
            queryResult.setCustomTableData(earlyCustomTableData);
        }

        return queryResult;
    }

    private RdapQueryResult createPendingReq(String qType, String qVal, int level, TokenInfo info,
            String ip, List<String> agreements, long start,
            boolean confidential, boolean exigent, boolean jakeCompliance, Map<String, Object> customParams) {
        PendingRequest r = PendingRequest.builder()
            .requestId(UUID.randomUUID()).queryType(qType).queryValue(qVal).requestedAccessLevel(level)
            .requestorSub(info.getSub()).requestorUsername(info.getUsername()).requestorEmail(info.getEmail()).requestorIp(ip)
            .requestorGroups(info.getGroups() != null ? info.getGroups().toArray(new String[0]) : null)
            .agreementNames(agreements != null ? agreements.toArray(new String[0]) : null)
            .status(PendingRequest.Status.PENDING).autoApproved(false)
            .confidential(confidential).exigent(exigent).jakeCompliance(jakeCompliance)
            .customParams(customParams)
            .responseTimeMs((int)(System.currentTimeMillis() - start))
            .expiresAt(LocalDateTime.now().plusHours(24)).build();
        pendingRequestRepository.save(r);
        RdapQueryResult result = RdapQueryResult.pending(r.getRequestId(), qType, qVal, r.getExpiresAt());
        result.setJakeCompliance(jakeCompliance);
        result.setRequestorGroupCode(extractRequestorGroupCode(agreements));
        return result;
    }

    /**
     * Extract the requestor group code from the first code-style agreement reference.
     * Agreement references of the form "GROUP-CODE:TYPE_CODE" contain the requestor
     * group code before the colon. Plain agreement names are returned as-is.
     */
    private String extractRequestorGroupCode(List<String> agreements) {
        if (agreements == null || agreements.isEmpty()) return null;
        String first = agreements.get(0);
        if (first == null) return null;
        if (first.contains(":")) return first.split(":", 2)[0];
        return first;
    }

    /**
     * Resolve the AgreementRequestType from a list of agreement/code references.
     * The first reference of the form "GROUP-CODE:TYPE_CODE" is used to look up the
     * request type (and its RDAP parameters) from the Group Admin. Returns null if
     * no code reference is present or no matching request type is found.
     *
     * Centralizing this here guarantees that the immediate (auto-approved) query path
     * and the manual-approval poll path ({@link #checkPendingRequest}) resolve the
     * request type identically, so the same request type always yields the same RDAP
     * parameter filtering regardless of whether the request was auto-approved or
     * manually approved.
     */
    private AgreementRequestType resolveRequestTypeFromAgreements(List<String> agreements) {
        if (agreements == null || agreements.isEmpty()) return null;
        String codeRef = agreements.get(0);
        if (codeRef == null || !codeRef.contains(":")) return null;
        String[] parts = codeRef.split(":", 2);
        try {
            String groupCode = parts[0];
            Integer typeCode = Integer.parseInt(parts[1]);
            AgreementRequestType resolved = accessControlService.resolveRequestType(groupCode, typeCode);
            if (resolved != null) {
                log.debug("Resolved AgreementRequestType '{}' (code {}) for RDAP parameter filtering",
                        resolved.getName(), typeCode);
            }
            return resolved;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void logRequest(String qType, String qVal, int level, TokenInfo info, String ip,
            List<String> agreements, PendingRequest.Status status,
            String reason, long start,
            boolean confidential, boolean exigent, boolean jakeCompliance,
            Map<String, Object> customParams) {
        PendingRequest r = PendingRequest.builder()
            .requestId(UUID.randomUUID()).queryType(qType).queryValue(qVal).requestedAccessLevel(level)
            .requestorSub(info != null ? info.getSub() : null)
            .requestorUsername(info != null ? info.getUsername() : null)
            .requestorEmail(info != null ? info.getEmail() : null).requestorIp(ip)
            .requestorGroups(info != null && info.getGroups() != null ? info.getGroups().toArray(new String[0]) : null)
            .agreementNames(agreements != null ? agreements.toArray(new String[0]) : null)
            .status(status).autoApproved(true)
            .confidential(confidential).exigent(exigent).jakeCompliance(jakeCompliance)
            .customParams(customParams)
            .responseTimeMs((int)(System.currentTimeMillis() - start))
            .denialReason(reason).reviewedBy("SYSTEM").reviewedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusHours(24)).build();
        pendingRequestRepository.save(r);
        auditLogRepository.save(RequestAuditLog.builder().queryType(qType).queryValue(qVal)
            .requestorSub(info != null ? info.getSub() : null)
            .requestorUsername(info != null ? info.getUsername() : null).requestorIp(ip)
            .agreementNames(agreements != null ? agreements.toArray(new String[0]) : null)
            .accessLevelRequested(level)
            .accessLevelGranted(status == PendingRequest.Status.APPROVED ? level : 0)
            .result(status == PendingRequest.Status.APPROVED ? "success" : "denied").resultMessage(reason)
            .confidential(confidential).exigent(exigent).jakeCompliance(jakeCompliance)
            .responseTimeMs((int)(System.currentTimeMillis() - start)).build());
    }

    private boolean matches(RdapEntity e, String s) {
        return (e.getLdhName() != null && e.getLdhName().toLowerCase().contains(s)) ||
               (e.getHandle() != null && e.getHandle().toLowerCase().contains(s)) ||
               (e.getContactName() != null && e.getContactName().toLowerCase().contains(s)) ||
               (e.getOrganization() != null && e.getOrganization().toLowerCase().contains(s)) ||
               (e.getStartAddress() != null && e.getStartAddress().contains(s)) ||
               (e.getStartAutnum() != null && e.getStartAutnum().toString().contains(s));
    }

    private void validateEntityRequest(RdapEntityRequest r, RdapEntity existing) {
        if (r.getObjectType() == null) throw new IllegalArgumentException("objectType required");
        if (r.getHandle() == null || r.getHandle().isBlank()) throw new IllegalArgumentException("handle required");
        Optional<RdapEntity> dup = entityRepository.findByHandleAndObjectType(r.getHandle(), r.getObjectType());
        if (dup.isPresent() && (existing == null || !dup.get().getId().equals(existing.getId()))) throw new IllegalArgumentException("Handle exists: " + r.getHandle());
        switch (r.getObjectType()) {
            case DOMAIN -> { if (r.getLdhName() == null || r.getLdhName().isBlank()) throw new IllegalArgumentException("ldhName required");
                Optional<RdapEntity> d = entityRepository.findDomainByLdhName(r.getLdhName());
                if (d.isPresent() && (existing == null || !d.get().getId().equals(existing.getId()))) throw new IllegalArgumentException("Domain exists: " + r.getLdhName()); }
            case IP_NETWORK -> { if (r.getStartAddress() == null || r.getStartAddress().isBlank()) throw new IllegalArgumentException("startAddress required"); }
            case AUTNUM -> { if (r.getStartAutnum() == null) throw new IllegalArgumentException("startAutnum required"); }
            default -> {}
        }
    }

    private void mapEntityRequest(RdapEntity e, RdapEntityRequest r) {
        e.setObjectType(r.getObjectType()); e.setObjectClassName(switch(r.getObjectType()) { case DOMAIN -> "domain"; case IP_NETWORK -> "ip network"; case AUTNUM -> "autnum"; case ENTITY -> "entity"; case NAMESERVER -> "nameserver"; });
        e.setHandle(r.getHandle()); e.setLdhName(r.getLdhName()); e.setUnicodeName(r.getUnicodeName());
        e.setStatus(r.getStatus() != null ? r.getStatus() : new ArrayList<>()); e.setPort43(r.getPort43());
        e.setSecureDnsDelegationSigned(r.getSecureDnsDelegationSigned()); e.setSecureDnsZoneSigned(r.getSecureDnsZoneSigned());
        e.setStartAddress(r.getStartAddress()); e.setEndAddress(r.getEndAddress()); e.setIpVersion(r.getIpVersion());
        e.setNetworkName(r.getNetworkName()); e.setNetworkType(r.getNetworkType()); e.setParentHandle(r.getParentHandle()); e.setCountry(r.getCountry());
        e.setStartAutnum(r.getStartAutnum()); e.setEndAutnum(r.getEndAutnum()); e.setAutnumName(r.getAutnumName()); e.setAutnumType(r.getAutnumType());
        e.setContactName(r.getContactName()); e.setOrganization(r.getOrganization()); e.setEmail(r.getEmail()); e.setPhone(r.getPhone()); e.setFax(r.getFax());
        e.setAddressStreet1(r.getAddressStreet1()); e.setAddressStreet2(r.getAddressStreet2()); e.setAddressCity(r.getAddressCity());
        e.setAddressState(r.getAddressState()); e.setAddressPostalCode(r.getAddressPostalCode()); e.setAddressCountry(r.getAddressCountry());
        e.setRoles(r.getRoles() != null ? r.getRoles() : new ArrayList<>()); e.setPublicIds(r.getPublicIds() != null ? r.getPublicIds() : new ArrayList<>());
        if (r.getIsTestData() != null && r.getIsTestData()) {
            TestDataFlag f = e.getTestDataFlag(); if (f == null) f = new TestDataFlag(); f.setIsTestData(true);
            e.setTestDataFlag(testDataFlagRepository.save(f));
        } else if (r.getIsTestData() != null) e.setTestDataFlag(null);
        if (r.getPolicyExpressionId() != null) {
            policyExpressionRepository.findById(r.getPolicyExpressionId()).ifPresent(e::setPolicyExpression);
        } else if (r.getClearPolicyExpression() != null && r.getClearPolicyExpression()) {
            e.setPolicyExpression(null);
        }
    }

    private void saveRelatedEntities(RdapEntity e, RdapEntityRequest r) {
        if (r.getEvents() != null) { eventRepository.deleteByRdapEntityId(e.getId()); for (RdapEventRequest er : r.getEvents()) { RdapEvent ev = new RdapEvent(); ev.setRdapEntity(e); ev.setEventAction(er.getEventAction()); ev.setEventActionCustom(er.getEventActionCustom()); ev.setEventDate(er.getEventDate()); ev.setEventActor(er.getEventActor()); eventRepository.save(ev); }}
        if (r.getLinks() != null) { linkRepository.deleteByRdapEntityId(e.getId()); for (RdapLinkRequest lr : r.getLinks()) { RdapLink l = new RdapLink(); l.setRdapEntity(e); l.setHref(lr.getHref()); l.setRel(lr.getRel()); l.setMediaType(lr.getMediaType()); l.setTitle(lr.getTitle()); l.setValue(lr.getValue()); l.setHreflang(lr.getHreflang()); linkRepository.save(l); }}
        if (r.getNameservers() != null) { nameserverRepository.deleteByRdapEntityId(e.getId()); for (RdapNameserverRequest nr : r.getNameservers()) { RdapNameserver n = new RdapNameserver(); n.setRdapEntity(e); n.setHandle(nr.getHandle()); n.setLdhName(nr.getLdhName()); n.setUnicodeName(nr.getUnicodeName()); n.setIpv4Addresses(nr.getIpv4Addresses() != null ? nr.getIpv4Addresses() : new ArrayList<>()); n.setIpv6Addresses(nr.getIpv6Addresses() != null ? nr.getIpv6Addresses() : new ArrayList<>()); n.setStatus(nr.getStatus() != null ? nr.getStatus() : new ArrayList<>()); nameserverRepository.save(n); }}
        if (r.getRemarks() != null) { remarkRepository.deleteByRdapEntityId(e.getId()); for (RdapRemarkRequest rr : r.getRemarks()) { RdapRemark rm = new RdapRemark(); rm.setRdapEntity(e); rm.setRemarkType(rr.getRemarkType() != null ? rr.getRemarkType() : RemarkType.REMARK); rm.setTitle(rr.getTitle()); rm.setDescription(rr.getDescription() != null ? rr.getDescription() : new ArrayList<>()); rm.setRemarkTypeValue(rr.getRemarkTypeValue()); remarkRepository.save(rm); }}
        if (r.getSecureDnsRecords() != null) { secureDnsRepository.deleteByRdapEntityId(e.getId()); for (RdapSecureDnsRequest dr : r.getSecureDnsRecords()) { RdapSecureDns d = new RdapSecureDns(); d.setRdapEntity(e); d.setKeyTag(dr.getKeyTag()); d.setAlgorithm(dr.getAlgorithm()); d.setDigestType(dr.getDigestType()); d.setDigest(dr.getDigest()); secureDnsRepository.save(d); }}
        if (r.getChildEntities() != null) { e.getChildren().clear(); entityRepository.save(e); for (RdapEntityRequest cr : r.getChildEntities()) { RdapEntity c = new RdapEntity(); mapEntityRequest(c, cr); c.setParent(e); entityRepository.save(c); }}
    }


    /**
     * Resolve an RDAP entity by type and value, checking all active mappings first,
     * then falling back to the local JPA repository.
     */
    private Optional<RdapEntity> resolveEntity(String type, String value) {
        // Check if there are any active mappings
        List<RdapDataMapping> activeMappings = mappedRdapQueryService.getActiveMappings();
        for (RdapDataMapping mapping : activeMappings) {
            log.debug("Active mapping '{}' found — querying mapped database for {} '{}'",
                    mapping.getName(), type, value);

            Optional<RdapEntity> mapped = switch (type.toLowerCase()) {
                case "domain" -> mappedRdapQueryService.queryDomainWithSld(mapping, value);
                // IP and ASN not yet implemented for external mapping — fall through to local
                default -> Optional.empty();
            };

            if (mapped.isPresent()) {
                log.info("Resolved {} '{}' from mapped database '{}'", type, value, mapping.getName());
                return mapped;
            }
            log.debug("Not found in mapped database '{}', trying next mapping", mapping.getName());
        }

        // Fall back to local JPA repository
        return switch (type.toLowerCase()) {
            case "domain" -> entityRepository.findDomainByLdhName(value);
            case "ip" -> {
                Optional<RdapEntity> ip = entityRepository.findIpNetworkByStartAddress(value);
                if (ip.isEmpty()) {
                    List<RdapEntity> containing = entityRepository.findIpNetworksContainingAddress(value);
                    yield containing.isEmpty() ? Optional.empty() : Optional.of(containing.get(0));
                }
                yield ip;
            }
            case "asn" -> {
                try {
                    String asnStr = value.toUpperCase().replace("AS", "");
                    long asnNum = Long.parseLong(asnStr);
                    Optional<RdapEntity> asn = entityRepository.findByAutnum(asnNum);
                    if (asn.isEmpty()) asn = entityRepository.findAsnByHandle("AS" + asnNum);
                    yield asn;
                } catch (NumberFormatException e) {
                    yield Optional.empty();
                }
            }
            default -> Optional.empty();
        };
    }

    /**
     * Get events for an entity — if it's a mapped entity (negative ID), build events
     * from the mapped database instead of querying the local event repository.
     */
    public List<RdapEvent> getEventsForMappedOrLocal(Long entityId, RdapEntity entity) {
        if (entityId != null && entityId < 0) {
            // This is a mapped entity — build events from timestamps on the entity itself
            List<RdapEvent> events = new ArrayList<>();
            if (entity.getCreatedAt() != null) {
                RdapEvent ev = new RdapEvent();
                ev.setEventAction(RdapEvent.EventAction.REGISTRATION);
                ev.setEventDate(entity.getCreatedAt());
                events.add(ev);
            }
            if (entity.getUpdatedAt() != null) {
                RdapEvent ev = new RdapEvent();
                ev.setEventAction(RdapEvent.EventAction.LAST_CHANGED);
                ev.setEventDate(entity.getUpdatedAt());
                events.add(ev);
            }
            return events;
        }
        return eventRepository.findByRdapEntityIdOrderByEventDateDesc(entityId);
    }

    private RdapEntity findEntityByTypeAndValue(String type, String value) {
        return resolveEntity(type, value).orElse(null);
    }

    /**
     * Resolve custom-table data for a domain and attach actsAs=contact custom
     * tables as contact children on the entity. Returns the non-contact custom
     * table rows as customTableData (or null if none). This mirrors the resolution
     * performed during the immediate query path and must also run on the approval
     * (post-manual-verification) path, otherwise manually-verified responses lose
     * their custom-mapped contacts.
     */
    private List<Map<String, Object>> resolveAndAttachCustomTableData(String qType, String qVal, RdapEntity entity) {
        if (entity == null || !"domain".equalsIgnoreCase(qType)) return null;
        List<Map<String, Object>> customTableData = null;
        try {
            List<RdapDataMapping> activeMappings = mappedRdapQueryService.getActiveMappings();
            for (RdapDataMapping activeMap : activeMappings) {
                List<Map<String, Object>> customData = mappedRdapQueryService.resolveCustomTableData(activeMap, qVal, entity);
                if (customData != null && !customData.isEmpty()) {
                    if (customTableData == null) {
                        customTableData = new ArrayList<>(customData);
                    } else {
                        customTableData.addAll(customData);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Custom table data resolution failed for {} {}: {}", qType, qVal, e.getMessage());
        }
        return customTableData;
    }

    // subscriptionToMap removed — Group Admin returns maps directly

    // ==================== JAKE COMPLIANCE ====================

    public Map<String, Object> buildJakeComplianceBlock(String requestorGroupCode) {
        Map<String, Object> jake = new LinkedHashMap<>();

        // Fetch real group memberships and templates from the Group Admin
        List<Map<String, Object>> groupMemberships = accessControlService.getGroupMemberships();
        List<Map<String, Object>> allTemplates = accessControlService.getTemplates();
        List<Map<String, Object>> allSubs = accessControlService.getAvailableSubscriptionMaps();

        List<Map<String, Object>> dataHolderGroups = new ArrayList<>();
        for (Map<String, Object> gm : groupMemberships) {
            Map<String, Object> group = new LinkedHashMap<>();
            Object groupId = gm.get("groupId");
            String groupIdStr = groupId != null ? groupId.toString() : null;
            group.put("groupId", groupId);
            group.put("groupName", gm.get("groupName") != null ? gm.get("groupName") : "Unknown");
            group.put("description", gm.get("description"));
            group.put("isActive", gm.get("isActive"));

            // Templates belonging to this group
            List<Map<String, Object>> groupTemplates = new ArrayList<>();
            for (Map<String, Object> tpl : allTemplates) {
                Object tplGroupId = tpl.get("dataHolderGroupId");
                String tplGroupIdStr = tplGroupId != null ? tplGroupId.toString() : null;
                if (groupIdStr != null && groupIdStr.equals(tplGroupIdStr)) {
                    // Disclosure mode controls how this template's info is exposed in the
                    // discovery block: open = as-is, hashed = all string values hashed,
                    // omit = excluded entirely. It does NOT affect the requestor's own
                    // agreements view or RDAP authorization (those use other endpoints).
                    String disclosureMode = tpl.get("disclosureMode") != null
                            ? tpl.get("disclosureMode").toString().trim().toLowerCase()
                            : DISCLOSURE_OPEN;
                    if (DISCLOSURE_OMIT.equals(disclosureMode)) {
                        continue;
                    }

                    Map<String, Object> tplSummary = new LinkedHashMap<>();
                    tplSummary.put("templateId", tpl.get("templateId"));
                    tplSummary.put("name", tpl.get("name"));
                    tplSummary.put("shortDescription", tpl.get("shortDescription"));
                    tplSummary.put("description", tpl.get("description"));
                    tplSummary.put("defaultAccessLevel", tpl.get("defaultAccessLevel"));
                    tplSummary.put("highestAccessLevel", tpl.get("highestAccessLevel"));
                    tplSummary.put("supportsConfidential", tpl.get("supportsConfidential"));
                    tplSummary.put("supportsExigent", tpl.get("supportsExigent"));
                    tplSummary.put("isPublished", tpl.get("isPublished"));

                    // Request types with access levels and parameters
                    tplSummary.put("requestTypes", extractRequestTypes(tpl.get("requestTypes")));

                    if (DISCLOSURE_HASHED.equals(disclosureMode)) {
                        tplSummary = hashStringValues(tplSummary);
                    }
                    // Keep the mode visible so consumers know whether values are hashed.
                    tplSummary.put("disclosureMode", disclosureMode);
                    groupTemplates.add(tplSummary);
                }
            }
            group.put("templates", groupTemplates);
            dataHolderGroups.add(group);
        }
        jake.put("dataHolderGroups", dataHolderGroups);

        // Active subscriptions summary
        List<Map<String, Object>> subscriptionSummaries = new ArrayList<>();
        for (Map<String, Object> sub : allSubs) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("subscriptionName", sub.get("requestorGroupName"));
            summary.put("requestorGroupCode", sub.get("requestorGroupCode"));
            summary.put("templateName", sub.get("templateName"));
            summary.put("status", sub.get("status"));
            summary.put("effectiveAccessLevel", sub.get("effectiveAccessLevel"));
            summary.put("dataHolderGroupId", sub.get("dataHolderGroupId"));
            summary.put("requestTypes", extractRequestTypes(sub.get("requestTypes")));
            subscriptionSummaries.add(summary);
        }
        jake.put("subscriptions", subscriptionSummaries);

        if (requestorGroupCode != null && !requestorGroupCode.isBlank()) {
            jake.put("subscription", findSubscriptionForRequestorGroup(requestorGroupCode));
        } else {
            jake.put("subscription", null);
        }

        return jake;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRequestTypes(Object requestTypes) {
        if (!(requestTypes instanceof List)) return List.of();
        List<Map<String, Object>> rtSummaries = new ArrayList<>();
        for (Object rtObj : (List<?>) requestTypes) {
            if (!(rtObj instanceof Map)) continue;
            Map<String, Object> rt = (Map<String, Object>) rtObj;
            Map<String, Object> rtSummary = new LinkedHashMap<>();
            rtSummary.put("name", rt.get("name"));
            rtSummary.put("typeCode", rt.get("typeCode"));
            rtSummary.put("description", rt.get("description"));
            rtSummary.put("accessLevel", rt.get("accessLevel"));
            rtSummary.put("supportsConfidential", rt.get("supportsConfidential"));
            rtSummary.put("supportsExigent", rt.get("supportsExigent"));
            rtSummary.put("requiresManualApproval", rt.get("requiresManualApproval"));
            rtSummary.put("rdapParameters", rt.get("rdapParameters"));
            rtSummary.put("customParameters", rt.get("customParameters"));
            rtSummaries.add(rtSummary);
        }
        return rtSummaries;
    }

    // ==================== Disclosure mode (jakeCompliance discovery block) ====================

    private static final String DISCLOSURE_OPEN = "open";
    private static final String DISCLOSURE_HASHED = "hashed";
    private static final String DISCLOSURE_OMIT = "omit";

    /** SHA-256 hex of the given value; used for the "hashed" disclosure mode. */
    private static String hashValue(String value) {
        if (value == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Deep copy of a map with every String value (recursively) replaced by its SHA-256 hash. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> hashStringValues(Map<String, Object> map) {
        return (Map<String, Object>) hashNode(map);
    }

    private Object hashNode(Object node) {
        if (node instanceof Map<?, ?> in) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : in.entrySet()) {
                Object v = e.getValue();
                String key = String.valueOf(e.getKey());
                if (v instanceof String s) {
                    out.put(key, hashValue(s));
                } else if (v instanceof Map || v instanceof List) {
                    out.put(key, hashNode(v));
                } else {
                    out.put(key, v);
                }
            }
            return out;
        }
        if (node instanceof List<?> in) {
            List<Object> out = new ArrayList<>(in.size());
            for (Object item : in) {
                if (item instanceof String s) {
                    out.add(hashValue(s));
                } else if (item instanceof Map || item instanceof List) {
                    out.add(hashNode(item));
                } else {
                    out.add(item);
                }
            }
            return out;
        }
        return node;
    }

    private Map<String, Object> findSubscriptionForRequestorGroup(String requestorGroupCode) {
        List<Map<String, Object>> subscriptions = accessControlService.getAvailableSubscriptionMaps();

        Map<String, Object> match = null;
        for (Map<String, Object> sub : subscriptions) {
            String subCode = (String) sub.get("requestorGroupCode");
            String groupName = (String) sub.get("requestorGroupName");
            String groupId = (String) sub.get("requestorGroupId");
            if (subCode != null && subCode.equalsIgnoreCase(requestorGroupCode)) { match = sub; break; }
            if (groupName != null && groupId != null) {
                String composed = groupName + "-" + groupId;
                if (composed.equalsIgnoreCase(requestorGroupCode)) { match = sub; break; }
            }
        }

        if (match == null) {
            log.debug("No active subscription found for requestor group code: {}", requestorGroupCode);
            return null;
        }

        Map<String, Object> subscription = new LinkedHashMap<>();
        subscription.put("requestorGroupCode", requestorGroupCode);
        subscription.put("requestorGroupName", match.get("requestorGroupName"));
        subscription.put("subscriptionActive", true);
        subscription.put("agreementName", match.get("templateName"));
        subscription.put("accessLevel", match.get("effectiveAccessLevel"));
        subscription.put("templateId", match.get("templateId"));
        subscription.put("templateName", match.get("templateName"));

        // Request types come from the Group Admin response
        Object requestTypes = match.get("requestTypes");
        if (requestTypes != null) {
            subscription.put("requestTypes", requestTypes);
        }
        return subscription;
    }

    // buildRequestTypesForSubscription removed — Group Admin provides request types directly

    private String str(Map<String, Object> d, String k) { Object v = d.get(k); return v != null && !v.toString().trim().isEmpty() ? v.toString().trim() : null; }
    private String str(Map<String, Object> d, String k, String def) { String v = str(d, k); return v != null ? v : def; }
    private Boolean bool(Map<String, Object> d, String k) { Object v = d.get(k); if (v == null) return null; if (v instanceof Boolean) return (Boolean)v; String s = v.toString().toLowerCase().trim(); return "true".equals(s) || "yes".equals(s) || "1".equals(s); }
    private Long lng(Map<String, Object> d, String k) { Object v = d.get(k); if (v == null) return null; if (v instanceof Number) return ((Number)v).longValue(); try { String s = v.toString().trim(); if (s.toUpperCase().startsWith("AS")) s = s.substring(2); return Long.parseLong(s); } catch (NumberFormatException e) { return null; }}

    private List<String> strList(Object v) {
        if (v == null) return new ArrayList<>();
        if (v instanceof List) return new ArrayList<>(((List<?>)v).stream().filter(Objects::nonNull).map(Object::toString).toList());
        if (v instanceof String[]) return new ArrayList<>(Arrays.asList((String[])v));
        if (v instanceof String) { String s = (String)v; return s.isBlank() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",\\s*"))); }
        return new ArrayList<>(List.of(v.toString()));
    }

    private List<String[]> parseCsv(MultipartFile file, String delim) throws Exception {
        List<String[]> rows = new ArrayList<>();
        for (String line : new String(file.getBytes(), StandardCharsets.UTF_8).split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            List<String> fields = new ArrayList<>(); StringBuilder f = new StringBuilder(); boolean inQ = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"') { if (inQ && i + 1 < line.length() && line.charAt(i + 1) == '"') { f.append('"'); i++; } else inQ = !inQ; }
                else if (c == delim.charAt(0) && !inQ) { fields.add(f.toString().trim()); f = new StringBuilder(); }
                else f.append(c);
            }
            fields.add(f.toString().trim());
            rows.add(fields.toArray(new String[0]));
        }
        return rows;
    }

    private Map<String, Integer> suggestMappings(List<String> headers, List<SchemaField> fields) {
        Map<String, Integer> m = new HashMap<>();
        for (SchemaField f : fields) {
            String fn = f.getName().toLowerCase().replaceAll("[_\\-\\s]", "");
            for (int i = 0; i < headers.size(); i++) {
                String h = headers.get(i).toLowerCase().replaceAll("[_\\-\\s]", "");
                if (h.equals(fn) || h.contains(fn) || fn.contains(h)) { m.put(f.getName(), i); break; }
            }
        }
        return m;
    }

    private Map<String, Object> mapRow(String[] row, Map<String, Integer> mappings) {
        Map<String, Object> d = new HashMap<>();
        for (Map.Entry<String, Integer> e : mappings.entrySet()) {
            int i = e.getValue();
            if (i >= 0 && i < row.length && row[i] != null && !row[i].trim().isEmpty()) d.put(e.getKey(), row[i].trim());
        }
        return d;
    }

    // ==================== INNER CLASSES ====================

    private static class AuthResult {
        private final boolean success, admin; private final TokenInfo tokenInfo; private final String message;
        private AuthResult(boolean s, boolean a, TokenInfo t, String m) { success = s; admin = a; tokenInfo = t; message = m; }
        static AuthResult admin(TokenInfo t) { return new AuthResult(true, true, t, null); }
        static AuthResult user(TokenInfo t) { return new AuthResult(true, false, t, null); }
        static AuthResult failure(String m) { return new AuthResult(false, false, null, m); }
        boolean isSuccess() { return success; } boolean isAdmin() { return admin; } TokenInfo getTokenInfo() { return tokenInfo; } String getMessage() { return message; }
    }

    public static class RdapQueryResult {
        private final boolean success, pending; private final RdapEntity entity; private final String queryType, queryValue, errorTitle, errorMessage;
        private final int accessLevel; private final List<String> agreementNames; private final Integer errorCode; private final UUID requestId; private final LocalDateTime expiresAt;
        private final AgreementRequestType resolvedRequestType;
        private List<Map<String, Object>> customTableData;
        private boolean jakeCompliance;
        private String requestorGroupCode;

        private RdapQueryResult(boolean s, RdapEntity e, String qt, String qv, int al, List<String> an, Integer ec, String et, String em, UUID ri, LocalDateTime ea, boolean p, AgreementRequestType rrt) {
            success = s; entity = e; queryType = qt; queryValue = qv; accessLevel = al; agreementNames = an; errorCode = ec; errorTitle = et; errorMessage = em; requestId = ri; expiresAt = ea; pending = p; resolvedRequestType = rrt;
        }
        public static RdapQueryResult success(RdapEntity e, String qt, String qv, int al, List<String> an, AgreementRequestType rrt) { return new RdapQueryResult(true, e, qt, qv, al, an, null, null, null, null, null, false, rrt); }
        public static RdapQueryResult error(int ec, String et, String em, String qt, String qv) { return new RdapQueryResult(false, null, qt, qv, 0, null, ec, et, em, null, null, false, null); }
        public static RdapQueryResult pending(UUID ri, String qt, String qv, LocalDateTime ea) { return new RdapQueryResult(true, null, qt, qv, 0, null, null, null, null, ri, ea, true, null); }
        public boolean isSuccess() { return success; } public RdapEntity getEntity() { return entity; } public String getQueryType() { return queryType; } public String getQueryValue() { return queryValue; }
        public int getAccessLevel() { return accessLevel; } public List<String> getAgreementNames() { return agreementNames; } public Integer getErrorCode() { return errorCode; }
        public String getErrorTitle() { return errorTitle; } public String getErrorMessage() { return errorMessage; } public UUID getRequestId() { return requestId; }
        public LocalDateTime getExpiresAt() { return expiresAt; } public boolean isPending() { return pending; }
        public AgreementRequestType getResolvedRequestType() { return resolvedRequestType; }
        public List<Map<String, Object>> getCustomTableData() { return customTableData; }
        public void setCustomTableData(List<Map<String, Object>> data) { this.customTableData = data; }
        public boolean isJakeCompliance() { return jakeCompliance; }
        public void setJakeCompliance(boolean jc) { this.jakeCompliance = jc; }
        public String getRequestorGroupCode() { return requestorGroupCode; }
        public void setRequestorGroupCode(String rgc) { this.requestorGroupCode = rgc; }
    }
}