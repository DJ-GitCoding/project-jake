/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.dto.RdapDto;
import com.jaddar.dataholder.dto.RdapDto.*;
import com.jaddar.dataholder.entity.*;
import com.jaddar.dataholder.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PendingRequestService {

    private final PendingRequestRepository pendingRequestRepository;
    private final RdapDomainRepository domainRepository;
    private final RdapIpRepository ipRepository;
    private final RdapAsnRepository asnRepository;
    private final RdapEntityRepository rdapEntityRepository;
    private final RegistrantNotificationService registrantNotificationService;
    private final AccessControlService accessControlService;
    private final MappedRdapQueryService mappedRdapQueryService;
    private final RdapRedactionService rdapRedactionService;
    private final PolicyExpressionRepository policyExpressionRepository;

    /**
     * Get all requests (pending, approved, denied) with optional status filter
     */
    public List<PendingRequestDto> getAllRequests(String statusFilter) {
        List<PendingRequest> requests;
        
        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                PendingRequest.Status status = PendingRequest.Status.valueOf(statusFilter.toUpperCase());
                requests = pendingRequestRepository.findByStatusOrderByCreatedAtDesc(status);
            } catch (IllegalArgumentException e) {
                requests = pendingRequestRepository.findAllByOrderByCreatedAtDesc();
            }
        } else {
            requests = pendingRequestRepository.findAllByOrderByCreatedAtDesc();
        }
        
        return requests.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get a page of requests with an optional status filter and free-text search.
     * Server-side paginated variant of {@link #getAllRequests(String)}.
     */
    public Page<PendingRequestDto> getAllRequests(String statusFilter, String search, Pageable pageable) {
        PendingRequest.Status status = null;
        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
            try {
                status = PendingRequest.Status.valueOf(statusFilter.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Unknown status -> no status filtering
            }
        }
        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;
        return pendingRequestRepository.searchAll(status, searchTerm, pageable).map(this::toDto);
    }

    /**
     * Get all pending requests (non-expired only)
     */
    public List<PendingRequestDto> getPendingRequests() {
        return pendingRequestRepository.findByStatusOrderByCreatedAtDesc(PendingRequest.Status.PENDING)
                .stream()
                .filter(r -> r.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific pending request
     */
    public Optional<PendingRequestDto> getPendingRequest(UUID requestId) {
        return pendingRequestRepository.findByRequestId(requestId)
                .map(this::toDto);
    }

    /**
     * Review (approve/deny) a pending request.
     * On approval, sends a registrant notification if applicable.
     */
    @Transactional
    public PendingRequestDto reviewRequest(UUID requestId, String action, 
                                            String adminNotes, Integer grantedAccessLevel,
                                            String reviewedBy, String denialReason) {
        PendingRequest request = pendingRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        if (request.getStatus() != PendingRequest.Status.PENDING) {
            throw new IllegalStateException("Request has already been reviewed");
        }

        if (request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Request has expired");
        }

        request.setReviewedBy(reviewedBy);
        request.setReviewedAt(LocalDateTime.now());
        request.setAdminNotes(adminNotes);

        if ("approve".equalsIgnoreCase(action)) {
            request.setStatus(PendingRequest.Status.APPROVED);
            
            int level = grantedAccessLevel != null ? grantedAccessLevel : request.getRequestedAccessLevel();
            Map<String, Object> responseData = getResponseData(
                    request.getQueryType(), 
                    request.getQueryValue(), 
                    level);
            request.setResponseData(responseData);
            
            log.info("Approved request {} with access level {}", requestId, level);

            // Trigger registrant notification (async, fire-and-forget)
            triggerRegistrantNotification(request);

        } else if ("deny".equalsIgnoreCase(action)) {
            request.setStatus(PendingRequest.Status.DENIED);
            if (denialReason != null && !denialReason.isBlank()) {
                request.setDenialReason(denialReason);
            }
            log.info("Denied request {}", requestId);
        } else {
            throw new IllegalArgumentException("Invalid action: " + action);
        }

        pendingRequestRepository.save(request);
        return toDto(request);
    }

    /**
     * Trigger registrant notification for an approved request.
     * Extracts the requestor group code and type code from the stored agreement names
     * (format: "GROUP-CODE:TYPE_CODE") to determine if the request type supports
     * the confidential flag.
     */
    private void triggerRegistrantNotification(PendingRequest request) {
        try {
            RdapEntity entity = findEntityByTypeAndValue(request.getQueryType(), request.getQueryValue());
            if (entity == null) {
                log.debug("No entity found for {} {} — skipping registrant notification",
                        request.getQueryType(), request.getQueryValue());
                return;
            }

            boolean supportsConfidential = false;
            if (request.getAgreementNames() != null) {
                for (String codeRef : request.getAgreementNames()) {
                    if (codeRef != null && codeRef.contains(":")) {
                        String[] parts = codeRef.split(":");
                        try {
                            Integer typeCode = Integer.parseInt(parts[1]);
                            supportsConfidential = accessControlService
                                    .doesRequestTypeSupportConfidential(parts[0], typeCode);
                        } catch (NumberFormatException ignored) {}
                        break;
                    }
                }
            }

            String registrantEmail = registrantNotificationService.findRegistrantEmail(entity);
            registrantNotificationService.notifyRegistrantIfRequired(
                    registrantEmail,
                    request.getQueryType(),
                    request.getQueryValue(),
                    request.isConfidential(),
                    supportsConfidential);

        } catch (Exception e) {
            log.warn("Failed to trigger registrant notification for request {}: {}",
                    request.getRequestId(), e.getMessage());
        }
    }

    /**
     * Find an RDAP entity by query type and value.
     * Checks active mapped databases first, then falls back to local repositories.
     */
    private RdapEntity findEntityByTypeAndValue(String type, String value) {
        // Check mapped databases first
        List<RdapDataMapping> activeMappings = mappedRdapQueryService.getActiveMappings();
        for (RdapDataMapping mapping : activeMappings) {
            Optional<RdapEntity> mapped = switch (type.toLowerCase()) {
                case "domain" -> mappedRdapQueryService.queryDomainWithSld(mapping, value);
                default -> Optional.empty();
            };
            if (mapped.isPresent()) {
                RdapEntity entity = mapped.get();
                // Resolve contacts from custom tables (actsAs=contact)
                try {
                    List<Map<String, Object>> customData = mappedRdapQueryService.resolveCustomTableData(mapping, value, entity);
                    // customData resolution adds children to the entity automatically
                } catch (Exception e) {
                    log.warn("Failed to resolve custom table data for {} {}: {}", type, value, e.getMessage());
                }
                return entity;
            }
        }

        // Fall back to local repositories
        return switch (type.toLowerCase()) {
            case "domain" -> rdapEntityRepository.findDomainByLdhName(value).orElse(null);
            case "ip" -> rdapEntityRepository.findIpNetworkByStartAddress(value).orElse(null);
            case "asn" -> {
                try {
                    yield rdapEntityRepository.findByAutnum(
                            Long.parseLong(value.toUpperCase().replace("AS", ""))).orElse(null);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    /**
     * Get response data for a query.
     * Checks mapped databases first (with contacts and custom table data),
     * then falls back to local repositories.
     * Applies policy-based redaction to the result.
     */
    private Map<String, Object> getResponseData(String queryType, String queryValue, int accessLevel) {
        // Try mapped databases first
        List<RdapDataMapping> activeMappings = mappedRdapQueryService.getActiveMappings();
        for (RdapDataMapping mapping : activeMappings) {
            Optional<RdapEntity> mapped = switch (queryType.toLowerCase()) {
                case "domain" -> mappedRdapQueryService.queryDomainWithSld(mapping, queryValue);
                default -> Optional.empty();
            };

            if (mapped.isPresent()) {
                RdapEntity entity = mapped.get();

                // Resolve contacts and custom table data
                try {
                    mappedRdapQueryService.resolveCustomTableData(mapping, queryValue, entity);
                } catch (Exception e) {
                    log.warn("Failed to resolve custom table data for approved request {} {}: {}",
                            queryType, queryValue, e.getMessage());
                }

                // Build RDAP response map similar to how toResponse does it in the controller
                Map<String, Object> response = buildMappedResponseData(entity, accessLevel);

                // Resolve and set policy before redaction
                PolicyExpression policy = rdapRedactionService.resolveEffectivePolicy(entity, null);
                if (policy != null) {
                    entity.setPolicyExpression(policy);
                }

                // Apply policy-based redaction
                return rdapRedactionService.applyRedactionRules(response, entity, accessLevel);
            }
        }

        // Fall back to local repositories
        switch (queryType.toLowerCase()) {
            case "domain":
                return domainRepository.findByLdhNameIgnoreCase(queryValue)
                        .map(d -> d.getRdapDataForLevel(accessLevel))
                        .orElse(Collections.emptyMap());
            case "ip":
                return ipRepository.findByIpAddress(queryValue)
                        .map(ip -> ip.getRdapDataForLevel(accessLevel))
                        .orElse(Collections.emptyMap());
            case "asn":
                String asnStr = queryValue.toUpperCase().replace("AS", "");
                try {
                    int asnNum = Integer.parseInt(asnStr);
                    return asnRepository.findByAutnum(asnNum)
                            .map(asn -> asn.getRdapDataForLevel(accessLevel))
                            .orElse(Collections.emptyMap());
                } catch (NumberFormatException e) {
                    return Collections.emptyMap();
                }
            default:
                return Collections.emptyMap();
        }
    }

    /**
     * Build a basic RDAP response map from a mapped entity, including contacts and events.
     * Mirrors the structure built by RdapController.toResponse() for mapped entities.
     */
    private Map<String, Object> buildMappedResponseData(RdapEntity entity, int accessLevel) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rdapConformance", List.of("rdap_level_0", "icann_rdap_response_profile_0",
                "icann_rdap_technical_implementation_guide_0"));
        r.put("objectClassName", entity.getObjectClassName());
        if (entity.getHandle() != null) r.put("handle", entity.getHandle());
        if (entity.getLdhName() != null) r.put("ldhName", entity.getLdhName());
        if (entity.getUnicodeName() != null) r.put("unicodeName", entity.getUnicodeName());
        if (entity.getStatus() != null && !entity.getStatus().isEmpty()) {
            r.put("status", entity.getStatus());
        }

        // Events from mapped entity timestamps
        List<Map<String, Object>> events = new ArrayList<>();
        if (entity.getCreatedAt() != null) {
            events.add(Map.of("eventAction", "registration",
                    "eventDate", entity.getCreatedAt().toString()));
        }
        if (entity.getUpdatedAt() != null) {
            events.add(Map.of("eventAction", "last changed",
                    "eventDate", entity.getUpdatedAt().toString()));
        }
        if (!events.isEmpty()) r.put("events", events);

        // SecureDNS
        if (Boolean.TRUE.equals(entity.getSecureDnsDelegationSigned())) {
            r.put("secureDNS", Map.of("delegationSigned", true));
        }

        // Child entities (contacts and nameservers)
        if (entity.getChildren() != null && !entity.getChildren().isEmpty()) {
            // Nameservers
            List<Map<String, Object>> nameservers = entity.getChildren().stream()
                    .filter(c -> c.getObjectType() == RdapEntity.ObjectType.NAMESERVER)
                    .map(ns -> {
                        Map<String, Object> nsMap = new LinkedHashMap<>();
                        nsMap.put("objectClassName", "nameserver");
                        if (ns.getHandle() != null) nsMap.put("handle", ns.getHandle());
                        if (ns.getLdhName() != null) nsMap.put("ldhName", ns.getLdhName());
                        return nsMap;
                    })
                    .collect(Collectors.toList());
            if (!nameservers.isEmpty()) r.put("nameservers", nameservers);

            // Contact entities with vCard
            List<Map<String, Object>> contacts = entity.getChildren().stream()
                    .filter(c -> c.getObjectType() == RdapEntity.ObjectType.ENTITY)
                    .map(this::childToMap)
                    .collect(Collectors.toList());
            if (!contacts.isEmpty()) r.put("entities", contacts);
        }

        r.put("accessLevel", accessLevel);
        return r;
    }

    /**
     * Convert a child entity to a response map with vCard data.
     * Mirrors RdapController.childToMap() for consistency.
     */
    private Map<String, Object> childToMap(RdapEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("objectClassName", c.getObjectClassName() != null ? c.getObjectClassName() : "entity");
        if (c.getHandle() != null) m.put("handle", c.getHandle());

        if (c.getObjectType() == RdapEntity.ObjectType.ENTITY) {
            // Build vCard
            if (c.getContactName() != null || c.getOrganization() != null || c.getEmail() != null ||
                c.getPhone() != null || c.getFax() != null || c.getAddressStreet1() != null) {
                m.put("vcardArray", buildVcard(c));
            }
            if (c.getRoles() != null && !c.getRoles().isEmpty()) m.put("roles", c.getRoles());
        }

        if (c.getStatus() != null && !c.getStatus().isEmpty()) m.put("status", c.getStatus());

        return m;
    }

    /**
     * Build a vCard array for a contact entity.
     * Mirrors RdapController.buildVcard() for consistency.
     */
    private List<Object> buildVcard(RdapEntity e) {
        List<Object> properties = new ArrayList<>();
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

        return List.of("vcard", properties);
    }

    /**
     * Update an existing request (for editing approved/denied requests)
     */
    @Transactional
    public PendingRequestDto updateRequest(UUID requestId, String newStatus, 
                                            String adminNotes, Integer accessLevel,
                                            String updatedBy) {
        return updateRequest(requestId, newStatus, adminNotes, accessLevel, updatedBy, null);
    }

    @Transactional
    public PendingRequestDto updateRequest(UUID requestId, String newStatus, 
                                            String adminNotes, Integer accessLevel,
                                            String updatedBy, String denialReason) {
        PendingRequest request = pendingRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        if (newStatus != null && !newStatus.isBlank()) {
            try {
                PendingRequest.Status status = PendingRequest.Status.valueOf(newStatus.toUpperCase());
                PendingRequest.Status previousStatus = request.getStatus();
                request.setStatus(status);
                
                if (status == PendingRequest.Status.APPROVED) {
                    int level = accessLevel != null ? accessLevel : request.getRequestedAccessLevel();
                    Map<String, Object> responseData = getResponseData(
                            request.getQueryType(), 
                            request.getQueryValue(), 
                            level);
                    request.setResponseData(responseData);

                    // Also notify on status change to approved (e.g. re-review)
                    if (previousStatus != PendingRequest.Status.APPROVED) {
                        triggerRegistrantNotification(request);
                    }
                }

                if (status == PendingRequest.Status.DENIED && denialReason != null && !denialReason.isBlank()) {
                    request.setDenialReason(denialReason);
                }
                
                request.setReviewedAt(LocalDateTime.now());
                request.setReviewedBy(updatedBy);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status: " + newStatus);
            }
        }

        if (adminNotes != null) {
            request.setAdminNotes(adminNotes);
        }

        pendingRequestRepository.save(request);
        log.info("Updated request {} to status {} by {}", requestId, request.getStatus(), updatedBy);
        return toDto(request);
    }

    /**
     * Cancel a pending request (requestor-initiated).
     * Only requests with PENDING status can be cancelled.
     */
    @Transactional
    public PendingRequestDto cancelRequest(UUID requestId, String cancelledBy, String reason) {
        PendingRequest request = pendingRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        if (request.getStatus() != PendingRequest.Status.PENDING) {
            throw new IllegalStateException("Only pending requests can be cancelled");
        }

        request.setStatus(PendingRequest.Status.CANCELLED);
        request.setReviewedBy(cancelledBy);
        request.setReviewedAt(LocalDateTime.now());

        String notes = "Cancelled by requestor";
        if (reason != null && !reason.isBlank()) {
            notes = "Cancelled by requestor: " + reason;
            request.setDenialReason(reason);
        }
        request.setAdminNotes(notes);

        pendingRequestRepository.save(request);
        log.info("Cancelled request {} by {}{}", requestId, cancelledBy,
                reason != null && !reason.isBlank() ? " (reason: " + reason + ")" : "");
        return toDto(request);
    }

    /**
     * Delete a request
     */
    @Transactional
    public void deleteRequest(UUID requestId) {
        PendingRequest request = pendingRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
        pendingRequestRepository.delete(request);
        log.info("Deleted request {}", requestId);
    }

    /**
     * Get request statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long pending = pendingRequestRepository.findByStatus(PendingRequest.Status.PENDING)
                .stream()
                .filter(r -> r.getExpiresAt().isAfter(LocalDateTime.now()))
                .count();
        
        long approved = pendingRequestRepository.findByStatus(PendingRequest.Status.APPROVED).size();
        long denied = pendingRequestRepository.findByStatus(PendingRequest.Status.DENIED).size();
        long cancelled = pendingRequestRepository.findByStatus(PendingRequest.Status.CANCELLED).size();
        
        stats.put("pending", pending);
        stats.put("approved", approved);
        stats.put("denied", denied);
        stats.put("cancelled", cancelled);
        stats.put("total", pending + approved + denied + cancelled);
        
        return stats;
    }

    /**
     * Get requests by requestor email
     */
    public List<PendingRequestDto> getRequestsByEmail(String email, String statusFilter, int limit) {
        List<PendingRequest> requests;
        
        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                PendingRequest.Status status = PendingRequest.Status.valueOf(statusFilter.toUpperCase());
                requests = pendingRequestRepository.findByRequestorEmailAndStatusOrderByCreatedAtDesc(email, status);
            } catch (IllegalArgumentException e) {
                requests = pendingRequestRepository.findByRequestorEmailOrderByCreatedAtDesc(email);
            }
        } else {
            requests = pendingRequestRepository.findByRequestorEmailOrderByCreatedAtDesc(email);
        }
        
        return requests.stream()
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get requests by requestor subject (Keycloak sub)
     */
    public List<PendingRequestDto> getRequestsBySub(String sub, String statusFilter, int limit) {
        List<PendingRequest> requests;
        
        if (statusFilter != null && !statusFilter.isBlank()) {
            try {
                PendingRequest.Status status = PendingRequest.Status.valueOf(statusFilter.toUpperCase());
                requests = pendingRequestRepository.findByRequestorSubAndStatusOrderByCreatedAtDesc(sub, status);
            } catch (IllegalArgumentException e) {
                requests = pendingRequestRepository.findByRequestorSubOrderByCreatedAtDesc(sub);
            }
        } else {
            requests = pendingRequestRepository.findByRequestorSubOrderByCreatedAtDesc(sub);
        }
        
        return requests.stream()
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get request counts by email
     */
    public Map<String, Long> getRequestCountsByEmail(String email) {
        List<PendingRequest> allRequests = pendingRequestRepository.findByRequestorEmailOrderByCreatedAtDesc(email);
        
        Map<String, Long> counts = new HashMap<>();
        counts.put("total", (long) allRequests.size());
        counts.put("pending", allRequests.stream().filter(r -> r.getStatus() == PendingRequest.Status.PENDING).count());
        counts.put("approved", allRequests.stream().filter(r -> r.getStatus() == PendingRequest.Status.APPROVED).count());
        counts.put("denied", allRequests.stream().filter(r -> r.getStatus() == PendingRequest.Status.DENIED).count());
        counts.put("cancelled", allRequests.stream().filter(r -> r.getStatus() == PendingRequest.Status.CANCELLED).count());
        
        return counts;
    }

    /**
     * Convert PendingRequest entity to DTO.
     * Includes response data for approved requests and disclosure flags.
     */
    private PendingRequestDto toDto(PendingRequest request) {
        PendingRequestDto dto = new PendingRequestDto();
        dto.setRequestId(request.getRequestId().toString());
        dto.setQueryType(request.getQueryType());
        dto.setQueryValue(request.getQueryValue());
        dto.setRequestedAccessLevel(request.getRequestedAccessLevel());
        dto.setRequestorSub(request.getRequestorSub());
        dto.setRequestorUsername(request.getRequestorUsername());
        dto.setRequestorEmail(request.getRequestorEmail());
        dto.setRequestorGroups(request.getRequestorGroups() != null 
                ? Arrays.asList(request.getRequestorGroups()) : null);
        dto.setAgreementNames(request.getAgreementNames() != null 
                ? Arrays.asList(request.getAgreementNames()) : null);
        dto.setStatus(request.getStatus().name());
        dto.setAdminNotes(request.getAdminNotes());
        dto.setDenialReason(request.getDenialReason());
        dto.setConfidential(request.isConfidential());
        dto.setExigent(request.isExigent());
        dto.setJakeCompliance(request.isJakeCompliance());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setExpiresAt(request.getExpiresAt());
        dto.setReviewedAt(request.getReviewedAt());
        dto.setReviewedBy(request.getReviewedBy());
        
        if (request.getStatus() == PendingRequest.Status.APPROVED && request.getResponseData() != null) {
            dto.setResponseData(request.getResponseData());
        }

        // Map custom parameters (file references, etc.)
        if (request.getCustomParams() != null && !request.getCustomParams().isEmpty()) {
            dto.setCustomParams(request.getCustomParams());
        }

        // Map file attachments if present
        if (request.getFileAttachments() != null && !request.getFileAttachments().isEmpty()) {
            dto.setFileAttachments(request.getFileAttachments().stream()
                .map(f -> RdapDto.FileAttachmentDto.builder()
                    .fileId(f.getFileId() != null ? f.getFileId().toString() : null)
                    .originalFilename(f.getOriginalFilename())
                    .fileType(f.getFileType() != null ? f.getFileType().name() : null)
                    .mimeType(f.getMimeType())
                    .fileSize(f.getFileSize())
                    .humanReadableSize(f.getHumanReadableSize())
                    .scanStatus(f.getScanStatus() != null ? f.getScanStatus().name() : null)
                    .scanDetails(f.getScanDetails())
                    .uploadedAt(f.getUploadedAt() != null ? f.getUploadedAt().toString() : null)
                    .fileHash(f.getFileHash())
                    .build())
                .collect(Collectors.toList()));
        }
        
        return dto;
    }
}