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
import com.jaddar.dataholder.entity.RdapRemark.RemarkType;
import com.jaddar.dataholder.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Helper service for importing RDAP data with full support for child entities.
 * Handles import of:
 * - Main entities (domains, IPs, ASNs)
 * - Child entities (contacts: registrant, admin, tech, billing, abuse, registrar)
 * - Events (registration, expiration, last changed, transfer, etc.)
 * - Nameservers with IP addresses
 * - Remarks and notices
 * - Links
 * - SecureDNS/DNSSEC data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RdapImportHelper {

    private final RdapEntityRepository entityRepository;
    private final RdapEventRepository eventRepository;
    private final RdapLinkRepository linkRepository;
    private final RdapNameserverRepository nameserverRepository;
    private final RdapRemarkRepository remarkRepository;
    private final RdapSecureDnsRepository secureDnsRepository;
    private final TestDataFlagRepository testDataFlagRepository;
    private final PolicyExpressionRepository policyExpressionRepository;

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
        DateTimeFormatter.ISO_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );

    /**
     * Import a complete RDAP record with all related entities from a flat data map.
     * This handles CSV imports where child entities are represented as prefixed fields.
     */
    @Transactional
    public RdapEntity importCompleteRecord(String type, Map<String, Object> data) {
        // Import main entity
        RdapEntity mainEntity = importMainEntity(type, data);
        
        // Import child entities (contacts)
        importContacts(mainEntity, data);
        
        // Import events
        importEvents(mainEntity, data);
        
        // Import nameservers (for domains)
        if (mainEntity.getObjectType() == ObjectType.DOMAIN) {
            importNameservers(mainEntity, data);
        }
        
        // Import remarks
        importRemarks(mainEntity, data);
        
        // Import links
        importLinks(mainEntity, data);
        
        // Import SecureDNS
        if (mainEntity.getObjectType() == ObjectType.DOMAIN) {
            importSecureDns(mainEntity, data);
        }
        
        return entityRepository.findById(mainEntity.getId()).orElse(mainEntity);
    }

    /**
     * Import the main entity (domain, IP, or ASN)
     */
    private RdapEntity importMainEntity(String type, Map<String, Object> data) {
        return switch (type.toLowerCase()) {
            case "domain", "domains" -> importDomain(data);
            case "ip", "ips", "ip_network" -> importIp(data);
            case "asn", "asns", "autnum" -> importAsn(data);
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }

    private RdapEntity importDomain(Map<String, Object> data) {
        String ldhName = str(data, "ldhName");
        if (ldhName == null) throw new IllegalArgumentException("ldhName is required for domain import");
        
        String handle = str(data, "handle");
        if (handle == null) {
            handle = "DOM-" + ldhName.toUpperCase().replace(".", "-");
        }
        
        RdapEntity entity = entityRepository.findDomainByLdhName(ldhName).orElse(new RdapEntity());
        
        entity.setObjectType(ObjectType.DOMAIN);
        entity.setObjectClassName("domain");
        entity.setLdhName(ldhName);
        entity.setHandle(handle);
        entity.setUnicodeName(str(data, "unicodeName"));
        entity.setStatus(strList(data.get("status")));
        entity.setPort43(str(data, "port43"));
        
        // DNSSEC flags
        Boolean dnssec = bool(data, "secureDnsDelegationSigned");
        if (dnssec == null) dnssec = bool(data, "dnssecEnabled");
        entity.setSecureDnsDelegationSigned(dnssec);
        entity.setSecureDnsZoneSigned(bool(data, "secureDnsZoneSigned"));
        
        handleFlags(entity, data);
        
        log.info("Importing domain: {} (handle: {})", ldhName, handle);
        return entityRepository.save(entity);
    }

    private RdapEntity importIp(Map<String, Object> data) {
        String handle = str(data, "handle");
        if (handle == null) throw new IllegalArgumentException("handle is required for IP import");
        
        String startAddress = str(data, "startAddress");
        if (startAddress == null) throw new IllegalArgumentException("startAddress is required for IP import");
        
        RdapEntity entity = entityRepository.findByHandleAndObjectType(handle, ObjectType.IP_NETWORK).orElse(new RdapEntity());
        
        entity.setObjectType(ObjectType.IP_NETWORK);
        entity.setObjectClassName("ip network");
        entity.setHandle(handle);
        entity.setStartAddress(startAddress);
        entity.setEndAddress(str(data, "endAddress"));
        entity.setIpVersion(str(data, "ipVersion", "v4"));
        entity.setNetworkName(str(data, "networkName") != null ? str(data, "networkName") : str(data, "name"));
        entity.setNetworkType(str(data, "networkType"));
        entity.setCountry(str(data, "country"));
        entity.setParentHandle(str(data, "parentHandle"));
        entity.setStatus(strList(data.get("status")));
        
        handleFlags(entity, data);
        
        log.info("Importing IP: {} ({})", handle, startAddress);
        return entityRepository.save(entity);
    }

    private RdapEntity importAsn(Map<String, Object> data) {
        String handle = str(data, "handle");
        if (handle == null) throw new IllegalArgumentException("handle is required for ASN import");
        
        Long startAutnum = lng(data, "startAutnum");
        if (startAutnum == null) throw new IllegalArgumentException("startAutnum is required for ASN import");
        
        RdapEntity entity = entityRepository.findByHandleAndObjectType(handle, ObjectType.AUTNUM).orElse(new RdapEntity());
        
        entity.setObjectType(ObjectType.AUTNUM);
        entity.setObjectClassName("autnum");
        entity.setHandle(handle);
        entity.setStartAutnum(startAutnum);
        
        Long endAutnum = lng(data, "endAutnum");
        entity.setEndAutnum(endAutnum != null ? endAutnum : startAutnum);
        
        entity.setAutnumName(str(data, "autnumName") != null ? str(data, "autnumName") : str(data, "name"));
        entity.setAutnumType(str(data, "autnumType"));
        entity.setCountry(str(data, "country"));
        entity.setStatus(strList(data.get("status")));
        
        handleFlags(entity, data);
        
        log.info("Importing ASN: {} (AS{})", handle, startAutnum);
        return entityRepository.save(entity);
    }

    /**
     * Import contact entities (registrant, admin, tech, billing, abuse, registrar)
     */
    private void importContacts(RdapEntity parent, Map<String, Object> data) {
        // Define contact role prefixes
        List<String> contactRoles = List.of("registrant", "admin", "tech", "billing", "abuse", "registrar");
        
        for (String role : contactRoles) {
            // Check if this contact has any data
            String handle = str(data, role + "Handle");
            String name = str(data, role + "Name");
            String email = str(data, role + "Email");
            String org = str(data, role + "Organization");
            String phone = str(data, role + "Phone");
            
            // Skip if no contact data for this role
            if (handle == null && name == null && email == null && org == null && phone == null) {
                continue;
            }
            
            // Generate handle if not provided
            if (handle == null || handle.isBlank()) {
                handle = "CONT-" + role.toUpperCase() + "-" + parent.getHandle();
            }
            
            // Check for existing contact with same parent and role
            Optional<RdapEntity> existingContact = entityRepository.findFirstByParentIdAndRole(parent.getId(), role);
            RdapEntity contact = existingContact.orElse(new RdapEntity());
            
            contact.setObjectType(ObjectType.ENTITY);
            contact.setObjectClassName("entity");
            contact.setHandle(handle);
            contact.setParent(parent);
            
            // Set roles
            List<String> roles = new ArrayList<>();
            roles.add(role);
            contact.setRoles(roles);
            
            // Contact name
            contact.setContactName(name);
            
            // Organization
            contact.setOrganization(org);
            
            // Email
            contact.setEmail(email);
            
            // Phone
            contact.setPhone(phone);
            
            // Fax
            contact.setFax(str(data, role + "Fax"));
            
            // Address fields
            contact.setAddressStreet1(str(data, role + "Street"));
            contact.setAddressStreet2(str(data, role + "Street2"));
            contact.setAddressCity(str(data, role + "City"));
            contact.setAddressState(str(data, role + "StateProvince"));
            contact.setAddressPostalCode(str(data, role + "PostalCode"));
            contact.setAddressCountry(str(data, role + "Country"));
            
            // Status
            contact.setStatus(strList(data.get(role + "Status")));
            
            // Special handling for registrar
            if ("registrar".equals(role)) {
                String abuseEmail = str(data, "registrarAbuseEmail");
                String abusePhone = str(data, "registrarAbusePhone");
                String url = str(data, "registrarUrl");
                
                // Store abuse contact info in remarks or separate entity
                if (abuseEmail != null || abusePhone != null) {
                    // Create abuse contact as child of registrar
                    RdapEntity savedContact = entityRepository.save(contact);
                    createRegistrarAbuseContact(savedContact, abuseEmail, abusePhone, data);
                    continue;
                }
                
                // Store URL as a link
                if (url != null) {
                    RdapEntity savedContact = entityRepository.save(contact);
                    RdapLink link = new RdapLink();
                    link.setRdapEntity(savedContact);
                    link.setHref(url);
                    link.setRel("related");
                    link.setTitle("Registrar Website");
                    linkRepository.save(link);
                    continue;
                }
            }
            
            entityRepository.save(contact);
            log.debug("Imported {} contact for {}: {}", role, parent.getHandle(), handle);
        }
    }

    /**
     * Create registrar abuse contact
     */
    private void createRegistrarAbuseContact(RdapEntity registrar, String abuseEmail, String abusePhone, Map<String, Object> data) {
        String abuseHandle = "CONT-ABUSE-" + registrar.getHandle();
        
        Optional<RdapEntity> existingAbuse = entityRepository.findFirstByParentIdAndRole(registrar.getId(), "abuse");
        RdapEntity abuseContact = existingAbuse.orElse(new RdapEntity());
        
        abuseContact.setObjectType(ObjectType.ENTITY);
        abuseContact.setObjectClassName("entity");
        abuseContact.setHandle(abuseHandle);
        abuseContact.setParent(registrar);
        abuseContact.setRoles(List.of("abuse"));
        abuseContact.setEmail(abuseEmail);
        abuseContact.setPhone(abusePhone);
        abuseContact.setContactName(str(data, "registrarAbuseName") != null ? str(data, "registrarAbuseName") : "Abuse Contact");
        
        entityRepository.save(abuseContact);
        log.debug("Imported abuse contact for registrar {}: {}", registrar.getHandle(), abuseHandle);
    }

    /**
     * Import events (registration, expiration, last changed, transfer, etc.)
     */
    private void importEvents(RdapEntity entity, Map<String, Object> data) {
        // Delete existing events for this entity
        eventRepository.deleteByRdapEntityId(entity.getId());
        
        // Event mappings: field name -> EventAction
        Map<String, EventAction> eventMappings = new LinkedHashMap<>();
        eventMappings.put("registrationDate", EventAction.REGISTRATION);
        eventMappings.put("expirationDate", EventAction.EXPIRATION);
        eventMappings.put("lastChangedDate", EventAction.LAST_CHANGED);
        eventMappings.put("lastUpdateOfRdapDb", EventAction.LAST_UPDATE_OF_RDAP_DATABASE);
        eventMappings.put("transferDate", EventAction.TRANSFER);
        eventMappings.put("deletionDate", EventAction.DELETION);
        eventMappings.put("reregistrationDate", EventAction.REREGISTRATION);
        eventMappings.put("lockedDate", EventAction.LOCKED);
        eventMappings.put("unlockedDate", EventAction.UNLOCKED);
        eventMappings.put("registrarExpirationDate", EventAction.REGISTRAR_EXPIRATION);
        
        for (Map.Entry<String, EventAction> entry : eventMappings.entrySet()) {
            LocalDateTime eventDate = parseDateTime(data.get(entry.getKey()));
            if (eventDate != null) {
                RdapEvent event = RdapEvent.builder()
                    .rdapEntity(entity)
                    .eventAction(entry.getValue())
                    .eventDate(eventDate)
                    .eventActor(str(data, entry.getKey() + "Actor"))
                    .build();
                eventRepository.save(event);
                log.debug("Imported event {} for {}: {}", entry.getValue(), entity.getHandle(), eventDate);
            }
        }
    }

    /**
     * Import nameservers (for domains)
     */
    private void importNameservers(RdapEntity domain, Map<String, Object> data) {
        // Delete existing nameservers for this entity
        nameserverRepository.deleteByRdapEntityId(domain.getId());
        
        // Support up to 10 nameservers (ns1 through ns10)
        for (int i = 1; i <= 10; i++) {
            String nsName = str(data, "ns" + i);
            if (nsName == null || nsName.isBlank()) {
                continue;
            }
            
            RdapNameserver ns = RdapNameserver.builder()
                .rdapEntity(domain)
                .ldhName(nsName)
                .handle(str(data, "ns" + i + "Handle"))
                .unicodeName(str(data, "ns" + i + "Unicode"))
                .ipv4Addresses(new ArrayList<>())
                .ipv6Addresses(new ArrayList<>())
                .status(new ArrayList<>())
                .build();
            
            // IPv4 address(es)
            String ipv4 = str(data, "ns" + i + "Ipv4");
            if (ipv4 != null && !ipv4.isBlank()) {
                if (ipv4.contains(",")) {
                    ns.setIpv4Addresses(Arrays.asList(ipv4.split(",\\s*")));
                } else {
                    ns.getIpv4Addresses().add(ipv4);
                }
            }
            
            // IPv6 address(es)
            String ipv6 = str(data, "ns" + i + "Ipv6");
            if (ipv6 != null && !ipv6.isBlank()) {
                if (ipv6.contains(",")) {
                    ns.setIpv6Addresses(Arrays.asList(ipv6.split(",\\s*")));
                } else {
                    ns.getIpv6Addresses().add(ipv6);
                }
            }
            
            // Status
            String status = str(data, "ns" + i + "Status");
            if (status != null && !status.isBlank()) {
                ns.setStatus(Arrays.asList(status.split(",\\s*")));
            }
            
            nameserverRepository.save(ns);
            log.debug("Imported nameserver {} for domain {}", nsName, domain.getLdhName());
        }
        
        // Also support "nameservers" as a comma-separated list
        String nameserversList = str(data, "nameservers");
        if (nameserversList != null && !nameserversList.isBlank()) {
            String[] nsNames = nameserversList.split(",\\s*");
            for (String nsName : nsNames) {
                if (nsName.isBlank()) continue;
                
                // Check if this nameserver was already imported
                boolean exists = nameserverRepository.findByRdapEntityIdOrderByLdhNameAsc(domain.getId())
                    .stream().anyMatch(ns -> nsName.equalsIgnoreCase(ns.getLdhName()));
                
                if (!exists) {
                    RdapNameserver ns = RdapNameserver.builder()
                        .rdapEntity(domain)
                        .ldhName(nsName.trim())
                        .ipv4Addresses(new ArrayList<>())
                        .ipv6Addresses(new ArrayList<>())
                        .status(new ArrayList<>())
                        .build();
                    nameserverRepository.save(ns);
                }
            }
        }
    }

    /**
     * Import remarks
     */
    private void importRemarks(RdapEntity entity, Map<String, Object> data) {
        // Delete existing remarks for this entity
        remarkRepository.deleteByRdapEntityId(entity.getId());
        
        // Support up to 5 remarks
        for (int i = 1; i <= 5; i++) {
            String suffix = i == 1 ? "" : String.valueOf(i);
            String title = str(data, "remark" + suffix + "Title");
            String description = str(data, "remark" + suffix + "Description");
            
            if ((title == null || title.isBlank()) && (description == null || description.isBlank())) {
                continue;
            }
            
            RdapRemark remark = RdapRemark.builder()
                .rdapEntity(entity)
                .remarkType(RemarkType.REMARK)
                .title(title)
                .description(description != null ? List.of(description.split("\\n")) : new ArrayList<>())
                .remarkTypeValue(str(data, "remark" + suffix + "Type"))
                .build();
            
            remarkRepository.save(remark);
            log.debug("Imported remark for {}: {}", entity.getHandle(), title);
        }
        
        // Support notices separately
        for (int i = 1; i <= 5; i++) {
            String suffix = i == 1 ? "" : String.valueOf(i);
            String title = str(data, "notice" + suffix + "Title");
            String description = str(data, "notice" + suffix + "Description");
            
            if ((title == null || title.isBlank()) && (description == null || description.isBlank())) {
                continue;
            }
            
            RdapRemark notice = RdapRemark.builder()
                .rdapEntity(entity)
                .remarkType(RemarkType.NOTICE)
                .title(title)
                .description(description != null ? List.of(description.split("\\n")) : new ArrayList<>())
                .remarkTypeValue(str(data, "notice" + suffix + "Type"))
                .build();
            
            remarkRepository.save(notice);
            log.debug("Imported notice for {}: {}", entity.getHandle(), title);
        }
    }

    /**
     * Import links
     */
    private void importLinks(RdapEntity entity, Map<String, Object> data) {
        // Delete existing links for this entity
        linkRepository.deleteByRdapEntityId(entity.getId());
        
        // Self link
        String selfLink = str(data, "selfLink");
        if (selfLink != null && !selfLink.isBlank()) {
            RdapLink link = RdapLink.builder()
                .rdapEntity(entity)
                .href(selfLink)
                .rel("self")
                .mediaType("application/rdap+json")
                .build();
            linkRepository.save(link);
        }
        
        // Related link(s) - support up to 5
        for (int i = 1; i <= 5; i++) {
            String suffix = i == 1 ? "" : String.valueOf(i);
            String href = str(data, "relatedLink" + suffix);
            
            if (href == null || href.isBlank()) {
                continue;
            }
            
            RdapLink link = RdapLink.builder()
                .rdapEntity(entity)
                .href(href)
                .rel("related")
                .title(str(data, "relatedLink" + suffix + "Title"))
                .mediaType(str(data, "relatedLink" + suffix + "Type"))
                .build();
            linkRepository.save(link);
            log.debug("Imported related link for {}: {}", entity.getHandle(), href);
        }
    }

    /**
     * Import SecureDNS/DNSSEC data
     */
    private void importSecureDns(RdapEntity domain, Map<String, Object> data) {
        // Delete existing DS records for this entity
        secureDnsRepository.deleteByRdapEntityId(domain.getId());
        
        // Support up to 4 DS records
        for (int i = 1; i <= 4; i++) {
            String suffix = i == 1 ? "" : String.valueOf(i);
            Integer keyTag = intVal(data, "dsKeyTag" + suffix);
            Integer algorithm = intVal(data, "dsAlgorithm" + suffix);
            Integer digestType = intVal(data, "dsDigestType" + suffix);
            String digest = str(data, "dsDigest" + suffix);
            
            // Also support alternative naming
            if (keyTag == null) keyTag = intVal(data, "ds" + i + "KeyTag");
            if (algorithm == null) algorithm = intVal(data, "ds" + i + "Algorithm");
            if (digestType == null) digestType = intVal(data, "ds" + i + "DigestType");
            if (digest == null) digest = str(data, "ds" + i + "Digest");
            
            if (keyTag == null && algorithm == null && digest == null) {
                continue;
            }
            
            RdapSecureDns ds = RdapSecureDns.builder()
                .rdapEntity(domain)
                .keyTag(keyTag)
                .algorithm(algorithm)
                .digestType(digestType)
                .digest(digest)
                .build();
            
            secureDnsRepository.save(ds);
            log.debug("Imported DS record for {}: keyTag={}", domain.getLdhName(), keyTag);
        }
    }

    /**
     * Handle common flags (test data, pending request control, policy expression)
     */
    private void handleFlags(RdapEntity entity, Map<String, Object> data) {
        // Test data flag
        Boolean isTestData = bool(data, "isTestData");
        if (isTestData != null && isTestData) {
            TestDataFlag flag = entity.getTestDataFlag();
            if (flag == null) {
                flag = new TestDataFlag();
            }
            flag.setIsTestData(true);
            entity.setTestDataFlag(testDataFlagRepository.save(flag));
        }

        // Policy expression
        Long policyId = lng(data, "policyExpressionId");
        if (policyId != null) {
            policyExpressionRepository.findById(policyId).ifPresent(entity::setPolicyExpression);
        }
    }

    // ==================== UTILITY METHODS ====================

    private String str(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null && !value.toString().trim().isEmpty() ? value.toString().trim() : null;
    }

    private String str(Map<String, Object> data, String key, String defaultValue) {
        String value = str(data, key);
        return value != null ? value : defaultValue;
    }

    private Boolean bool(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        String s = value.toString().toLowerCase().trim();
        return "true".equals(s) || "yes".equals(s) || "1".equals(s);
    }

    private Long lng(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            String s = value.toString().trim();
            if (s.toUpperCase().startsWith("AS")) s = s.substring(2);
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intVal(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> strList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List) {
            return new ArrayList<>(((List<?>) value).stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList());
        }
        if (value instanceof String[]) {
            return new ArrayList<>(Arrays.asList((String[]) value));
        }
        if (value instanceof String) {
            String s = (String) value;
            return s.isBlank() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",\\s*")));
        }
        return new ArrayList<>(List.of(value.toString()));
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime) return (LocalDateTime) value;
        
        String s = value.toString().trim();
        if (s.isEmpty()) return null;
        
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(s, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }
        
        // Try parsing date-only formats and adding midnight time
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
            return date.atStartOfDay();
        } catch (DateTimeParseException ignored) {
            log.warn("Could not parse date: {}", s);
            return null;
        }
    }
}