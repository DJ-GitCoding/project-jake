/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.controller;

import com.jaddar.dhgroupadmin.config.HttpSignatures;
import com.jaddar.dhgroupadmin.entity.*;
import com.jaddar.dhgroupadmin.entity.AgreementSubscription.SubscriptionStatus;
import com.jaddar.dhgroupadmin.repository.*;
import com.jaddar.dhgroupadmin.service.AgreementCodeGenerator;
import com.jaddar.dhgroupadmin.service.TemplateCopyService;
import com.jaddar.dhgroupadmin.service.AgreementSubscriptionService;
import com.jaddar.dhgroupadmin.service.AuditService;
import com.jaddar.dhgroupadmin.service.ProvisioningService;
import com.jaddar.dhgroupadmin.service.RequestTypeCodeGenerator;
import com.jaddar.dhgroupadmin.service.ResponseMapper;
import com.jaddar.dhgroupadmin.service.SubscriptionTestService;
import com.jaddar.dhgroupadmin.service.TemplateVisibilityService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Internal admin API for managing agreement templates, subscriptions, and data holders.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdminController {

    private final AgreementTemplateRepository templateRepository;
    private final AgreementRequestTypeRepository requestTypeRepository;
    private final AgreementSubscriptionRepository subscriptionRepository;
    private final AgreementStatusLogRepository statusLogRepository;
    private final AgreementSubscriptionService subscriptionService;
    private final SubscriptionTestService subscriptionTestService;
    private final DataHolderRepository dataHolderRepository;
    private final DataHolderCredentialRepository credentialRepository;
    private final SubscriptionCredentialRepository subCredentialRepository;
    private final RequestorGroupRepository requestorGroupRepository;
    private final RequestorGroupCredentialRepository rgCredentialRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final DataHolderGroupRepository dataHolderGroupRepository;
    private final DataHolderApplicationRepository dataHolderApplicationRepository;
    private final DataHolderInstanceRepository instanceRepository;
    private final UserGroupMembershipRepository userGroupMembershipRepository;
    private final ResponseMapper mapper;
    private final TemplateVisibilityService visibilityService;
    private final RequestTypeCodeGenerator typeCodeGenerator;
    private final AgreementCodeGenerator agreementCodeGenerator;
    private final TemplateCopyService templateCopyService;
    private final AuditService audit;
    private final ProvisioningService provisioningService;

    // ==================== Caller identity (from verified JWT) ====================
    /* Authorization must derive from JwtAuthFilter's verified token attributes. */

    /** Caller privilege tier from the verified token: 1=master, 2=admin, 3=user. */
    private int callerType(HttpServletRequest http) {
        Object t = http.getAttribute("userType");
        // Absent/unknown → least privilege (never master). The filter already requires a valid token.
        return (t instanceof Integer) ? (Integer) t : 3;
    }

    /**
     * The set of user ids a caller may see or manage.
     *
     * <p>A master (type 1) is unrestricted — indicated by {@code null}. A non-master is limited to
     * members of their own data holder groups, plus themselves, so a group admin never sees users
     * outside their groups. An empty set means "nothing visible" and callers must skip the query.
     */
    private Set<Long> visibleUserIds(HttpServletRequest http) {
        if (callerType(http) == 1) return null; // master: unrestricted
        User caller = callerUser(http);
        if (caller == null) return Set.of();

        List<Long> groupIds = userGroupMembershipRepository.findByUserId(caller.getId())
                .stream().map(UserGroupMembership::getDataHolderGroupId).toList();

        Set<Long> ids = new HashSet<>();
        ids.add(caller.getId()); // always able to see themselves
        if (!groupIds.isEmpty()) {
            userGroupMembershipRepository.findByDataHolderGroupIdIn(groupIds)
                    .forEach(m -> ids.add(m.getUserId()));
        }
        return ids;
    }

    /** The authenticated caller, resolved from the verified token's userId, or null. */
    /** The groups a caller administers, or null for a master, who administers all of them. */
    private Set<Long> callerGroupIds(HttpServletRequest http) {
        if (callerType(http) == 1) return null;
        User caller = callerUser(http);
        if (caller == null) return Set.of();
        return userGroupMembershipRepository.findByUserId(caller.getId())
                .stream().map(UserGroupMembership::getDataHolderGroupId).collect(java.util.stream.Collectors.toSet());
    }

    private User callerUser(HttpServletRequest http) {
        Object u = http.getAttribute("userId");
        if (u instanceof Long id) {
            return userRepository.findById(id).orElse(null);
        }
        return null;
    }

    /** The 404 a non-master gets for anything outside their groups. */
    private static ResponseStatusException outOfScope() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }

    /** Requires the caller to administer the given group; masters always pass. */
    private void requireGroupAccess(HttpServletRequest http, Long dataHolderGroupId) {
        if (!inScope(callerGroupIds(http), dataHolderGroupId)) throw outOfScope();
    }

    /** Requires the caller to administer at least one of the given groups. */
    private void requireAnyGroupAccess(HttpServletRequest http, Set<Long> entityGroupIds) {
        if (!inScopeAny(callerGroupIds(http), entityGroupIds)) throw outOfScope();
    }

    /** Requires a master caller. */
    private void requireMaster(HttpServletRequest http) {
        if (callerType(http) != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This action requires master administrator access");
        }
    }

    /** Whether a group is within the caller's scope. */
    private boolean inScope(Set<Long> callerGroups, Long dataHolderGroupId) {
        return callerGroups == null || (dataHolderGroupId != null && callerGroups.contains(dataHolderGroupId));
    }

    /** Whether any of the given groups is within the caller's scope. */
    private boolean inScopeAny(Set<Long> callerGroups, Set<Long> entityGroupIds) {
        return callerGroups == null
                || (entityGroupIds != null && entityGroupIds.stream().anyMatch(callerGroups::contains));
    }

    /** The groups a subscription belongs to. */
    private Set<Long> subscriptionGroupIds(AgreementSubscription sub) {
        Set<Long> ids = new HashSet<>();
        if (sub.getTemplate() != null && sub.getTemplate().getDataHolderGroupId() != null) {
            ids.add(sub.getTemplate().getDataHolderGroupId());
        }
        if (sub.getDataHolderGroupId() != null) ids.add(sub.getDataHolderGroupId());
        return ids;
    }

    /** Requires the caller to administer the subscription's group. */
    private void requireSubscriptionAccess(HttpServletRequest http, Long subscriptionId) {
        subscriptionRepository.findById(subscriptionId)
                .ifPresent(sub -> requireAnyGroupAccess(http, subscriptionGroupIds(sub)));
    }

    /** The groups a non-master's paged list may cover. */
    private Set<Long> pageScope(Set<Long> callerGroups, Long requestedGroupId) {
        if (requestedGroupId == null) return callerGroups;
        return callerGroups.contains(requestedGroupId) ? Set.of(requestedGroupId) : Set.of();
    }

    /** Validates and trims a data holder URL. */
    private String validDataHolderUrl(String url) {
        String trimmed = url == null ? "" : url.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Data holder URL is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!uri.isAbsolute() || !(scheme.equals("http") || scheme.equals("https"))
                || uri.getHost() == null || uri.getHost().isBlank()
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "Data holder URL must be an absolute http or https URL with a host and no credentials, query or fragment");
        }
        return trimmed;
    }

    // ==================== Pagination helpers ====================
    // Shared by the opt-in server-side pagination on the list endpoints below.
    // All list endpoints keep returning the full List when no 'page' param is supplied.

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        Sort sort = Sort.by("asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), sort);
    }

    private String cleanSearch(String search) {
        return (search != null && !search.isBlank()) ? search.trim() : null;
    }

    /** Blank means "not set" for contact details, so an empty box clears the stored value. */
    private String trimOrNull(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }

    private Map<String, Object> pageResponse(Page<?> pg, List<?> content) {
        Map<String, Object> r = new HashMap<>();
        r.put("content", content);
        r.put("totalElements", pg.getTotalElements());
        r.put("totalPages", pg.getTotalPages());
        r.put("page", pg.getNumber());
        r.put("size", pg.getSize());
        return r;
    }

    // ==================== Templates ====================

    @GetMapping("/templates")
    public ResponseEntity<?> getAllTemplates(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long dataHolderGroupId,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletRequest http) {
        Map<Long, DataHolderGroup> groups = visibilityService.groupsById();
        Set<Long> scope = callerGroupIds(http);
        if (page == null) {
            return ResponseEntity.ok(templateRepository.findAll().stream()
                    .filter(t -> inScope(scope, t.getDataHolderGroupId()))
                    .map(t -> mapper.toTemplateResponse(t, groups)).toList());
        }
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Page<AgreementTemplate> pg;
        if (scope == null) {
            pg = templateRepository.search(cleanSearch(search), dataHolderGroupId, pageable);
        } else {
            Set<Long> in = pageScope(scope, dataHolderGroupId);
            pg = in.isEmpty() ? Page.empty(pageable) : templateRepository.searchInGroups(cleanSearch(search), in, pageable);
        }
        return ResponseEntity.ok(pageResponse(pg, pg.getContent().stream()
                .map(t -> mapper.toTemplateResponse(t, groups)).toList()));
    }

    @GetMapping("/templates/{id}")
    public ResponseEntity<?> getTemplate(@PathVariable Long id, HttpServletRequest http) {
        return templateRepository.findById(id)
                .map(t -> {
                    requireGroupAccess(http, t.getDataHolderGroupId());
                    return ResponseEntity.ok(mapper.toTemplateResponse(t));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/templates")
    public ResponseEntity<?> createTemplate(@RequestBody CreateTemplateRequest request, HttpServletRequest http) {
        requireGroupAccess(http, request.getDataHolderGroupId());
        log.info("Creating agreement template: {}", request.getName());

        AgreementTemplate template = AgreementTemplate.builder()
                .name(request.getName())
                .shortDescription(request.getShortDescription())
                .description(request.getDescription())
                .requiredGroupTypes(request.getRequiredGroupTypes())
                .maxQueriesPerDay(request.getMaxQueriesPerDay())
                .maxQueriesPerMonth(request.getMaxQueriesPerMonth())
                .isActive(request.activeFlag() != null ? request.activeFlag() : false)
                .visibility(Visibility.parse(request.getVisibility(), Visibility.PUBLIC))
                .createdBy(request.getCreatedBy())
                .dataHolderGroupId(request.getDataHolderGroupId())
                .disclosureMode(request.getDisclosureMode() != null ? request.getDisclosureMode() : "open")
                .useGroupContact(request.getUseGroupContact() == null || request.getUseGroupContact())
                .contactFirstName(trimOrNull(request.getContactFirstName()))
                .contactLastName(trimOrNull(request.getContactLastName()))
                .contactEmail(trimOrNull(request.getContactEmail()))
                .contactPhone(trimOrNull(request.getContactPhone()))
                .contactAddress(trimOrNull(request.getContactAddress()))
                .contactCity(trimOrNull(request.getContactCity()))
                .contactStateProvince(trimOrNull(request.getContactStateProvince()))
                .contactPostalCode(trimOrNull(request.getContactPostalCode()))
                .contactCountry(trimOrNull(request.getContactCountry()))
                .build();

        String contactError = validateContact(template);
        if (contactError != null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", contactError));
        }

        replaceLegalSections(template, request.getLegalSections());
        String fieldError = replaceSubscriptionFields(template, request.getSubscriptionFields());
        if (fieldError != null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", fieldError));
        }

        if (request.getRequestTypes() != null) {
            String nameError = validateUniqueTypeNames(request.getRequestTypes());
            if (nameError != null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", nameError));
            }
            List<AgreementRequestType> built = new ArrayList<>();
            for (RequestTypeRequest rtReq : request.getRequestTypes()) {
                built.add(buildRequestType(rtReq));
            }
            assignTypeCodes(template, built);
            built.forEach(template::addRequestType);
        }

        template = templateRepository.save(template);
        /* Seeded from the template id, which only exists once the row is persisted. */
        template.setAgreementCode(
                agreementCodeGenerator.generate(template.getTemplateId(), template.getName()));
        template = templateRepository.save(template);

        audit.logCrud("CREATE", "TEMPLATE", template.getTemplateId(), template.getName(),
                request.getCreatedBy() != null ? request.getCreatedBy() : "admin",
                "Created template with " + (request.getRequestTypes() != null ? request.getRequestTypes().size() : 0) + " request types");

        return ResponseEntity.ok(Map.of("success", true, "message", "Template created", "template", mapper.toTemplateResponse(template)));
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<?> updateTemplate(@PathVariable Long id, @RequestBody UpdateTemplateRequest request,
                                            HttpServletRequest http) {
        return templateRepository.findById(id)
                .map(template -> {
                    requireGroupAccess(http, template.getDataHolderGroupId());
                    if (request.getName() != null) template.setName(request.getName());
                    if (request.getShortDescription() != null) template.setShortDescription(request.getShortDescription());
                    if (request.getDescription() != null) template.setDescription(request.getDescription());
                    if (request.getRequiredGroupTypes() != null) template.setRequiredGroupTypes(request.getRequiredGroupTypes());
                    if (request.getMaxQueriesPerDay() != null) template.setMaxQueriesPerDay(request.getMaxQueriesPerDay());
                    if (request.getMaxQueriesPerMonth() != null) template.setMaxQueriesPerMonth(request.getMaxQueriesPerMonth());
                    if (request.activeFlag() != null) template.setIsActive(request.activeFlag());
                    if (request.getVisibility() != null) {
                        template.setVisibility(Visibility.parse(request.getVisibility(), template.getVisibility()));
                    }
                    if (request.getDataHolderGroupId() != null
                            && !request.getDataHolderGroupId().equals(template.getDataHolderGroupId())) {
                        if (template.getDataHolderGroupId() != null && callerType(http) != 1) {
                            return ResponseEntity.status(403).body(Map.of(
                                    "success", false,
                                    "message", "The data holder group is fixed once the template is saved. Only a master account can change it."));
                        }
                        template.setDataHolderGroupId(request.getDataHolderGroupId());
                    }
                    if (request.getDisclosureMode() != null) template.setDisclosureMode(request.getDisclosureMode());
                    if (template.getAgreementCode() == null || template.getAgreementCode().isBlank()) {
                        template.setAgreementCode(
                                agreementCodeGenerator.generate(template.getTemplateId(), template.getName()));
                    }
                    if (request.getUseGroupContact() != null) template.setUseGroupContact(request.getUseGroupContact());
                    /* The template's own fields are kept even while it shows the group's, so a
                     * contact typed here survives switching the link off and on again. */
                    if (request.getContactFirstName() != null) template.setContactFirstName(trimOrNull(request.getContactFirstName()));
                    if (request.getContactLastName() != null) template.setContactLastName(trimOrNull(request.getContactLastName()));
                    if (request.getContactEmail() != null) template.setContactEmail(trimOrNull(request.getContactEmail()));
                    if (request.getContactPhone() != null) template.setContactPhone(trimOrNull(request.getContactPhone()));
                    if (request.getContactAddress() != null) template.setContactAddress(trimOrNull(request.getContactAddress()));
                    if (request.getContactCity() != null) template.setContactCity(trimOrNull(request.getContactCity()));
                    if (request.getContactStateProvince() != null) template.setContactStateProvince(trimOrNull(request.getContactStateProvince()));
                    if (request.getContactPostalCode() != null) template.setContactPostalCode(trimOrNull(request.getContactPostalCode()));
                    if (request.getContactCountry() != null) template.setContactCountry(trimOrNull(request.getContactCountry()));
                    String contactError = validateContact(template);
                    if (contactError != null) {
                        return ResponseEntity.badRequest().body(Map.of("success", false, "message", contactError));
                    }
                    if (request.getLegalSections() != null) replaceLegalSections(template, request.getLegalSections());
                    if (request.getSubscriptionFields() != null) {
                        String fieldError = replaceSubscriptionFields(template, request.getSubscriptionFields());
                        if (fieldError != null) {
                            return ResponseEntity.badRequest().body(Map.of("success", false, "message", fieldError));
                        }
                    }

                    if (request.getRequestTypes() != null) {
                        String nameError = validateUniqueTypeNames(request.getRequestTypes());
                        if (nameError != null) {
                            return ResponseEntity.badRequest().body(Map.of("success", false, "message", nameError));
                        }
                        template.getRequestTypes().clear();
                        templateRepository.saveAndFlush(template);
                        List<AgreementRequestType> built = new ArrayList<>();
                        for (RequestTypeRequest rtReq : request.getRequestTypes()) {
                            built.add(buildRequestType(rtReq));
                        }
                        assignTypeCodes(template, built);
                        built.forEach(template::addRequestType);
                    }

                    templateRepository.save(template);

                    audit.logCrud("UPDATE", "TEMPLATE", template.getTemplateId(), template.getName(),
                            "admin", "Updated template");

                    return ResponseEntity.ok(Map.of("success", true, "template", mapper.toTemplateResponse(template)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/templates/copyable")
    public ResponseEntity<?> getCopyableTemplates(@RequestParam Long dataHolderGroupId, HttpServletRequest http) {
        requireGroupAccess(http, dataHolderGroupId);
        Map<Long, DataHolderGroup> groups = visibilityService.groupsById();
        List<Map<String, Object>> out = templateCopyService.copyableFor(dataHolderGroupId).stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("templateId", t.getTemplateId());
                    m.put("name", t.getName());
                    m.put("shortDescription", t.getShortDescription());
                    m.put("description", t.getDescription());
                    m.put("isActive", t.getIsActive());
                    m.put("visibility", t.getVisibility() != null ? t.getVisibility().name() : Visibility.PUBLIC.name());
                    m.put("effectiveVisibility", visibilityService.effectiveVisibility(t, groups).name());
                    m.put("dataHolderGroupId", t.getDataHolderGroupId());
                    DataHolderGroup owner = t.getDataHolderGroupId() == null
                            ? null : groups.get(t.getDataHolderGroupId());
                    m.put("dataHolderGroupName", owner == null ? null : owner.getName());
                    m.put("ownGroup", dataHolderGroupId.equals(t.getDataHolderGroupId()));
                    m.put("requestTypeCount", t.getRequestTypes().size());
                    m.put("legalSectionCount", t.getLegalSections().size());
                    m.put("subscriptionFieldCount", t.getSubscriptionFields().size());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping("/templates/{id}/copy")
    public ResponseEntity<?> copyTemplate(@PathVariable Long id, @RequestBody CopyTemplateRequest request,
                                          HttpServletRequest http) {
        AgreementTemplate source = templateRepository.findById(id).orElse(null);
        if (source == null) return ResponseEntity.notFound().build();

        Long targetGroupId = request.getDataHolderGroupId();
        if (targetGroupId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "message", "A data holder group is required to copy an agreement into."));
        }

        Set<Long> callerGroups = callerGroupIds(http);
        if (callerGroups != null && !callerGroups.contains(targetGroupId)) {
            return ResponseEntity.status(403).body(Map.of("success", false,
                    "message", "You can only copy an agreement into a data holder group you administer."));
        }

        if (!templateCopyService.canCopy(source, targetGroupId)) {
            return ResponseEntity.status(403).body(Map.of("success", false,
                    "message", "This agreement is not published widely enough for another data holder group to copy."));
        }

        AgreementTemplate copy = templateCopyService.copy(source, targetGroupId, request.getName(), "admin");

        audit.logCrud("COPY", "TEMPLATE", copy.getTemplateId(), copy.getName(), "admin",
                "Copied from template " + source.getTemplateId()
                        + " (group " + source.getDataHolderGroupId() + ") into group " + targetGroupId);

        return ResponseEntity.ok(Map.of("success", true, "message", "Agreement copied",
                "template", mapper.toTemplateResponse(copy)));
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<?> deleteTemplate(@PathVariable Long id, HttpServletRequest http) {
        var template = templateRepository.findById(id).orElse(null);
        if (template == null) return ResponseEntity.notFound().build();
        requireGroupAccess(http, template.getDataHolderGroupId());
        List<AgreementSubscription> active = subscriptionRepository.findActiveByTemplateId(id);
        if (!active.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Cannot delete template with active subscriptions"));
        }
        String templateId = template != null ? template.getTemplateId() : String.valueOf(id);
        String templateName = template != null ? template.getName() : "Unknown";
        templateRepository.deleteById(id);

        audit.logCrud("DELETE", "TEMPLATE", templateId, templateName, "admin", "Deleted template");

        return ResponseEntity.ok(Map.of("success", true, "message", "Template deleted"));
    }

    /** Flips the template between Active and Inactive, leaving existing subscriptions untouched. */
    @PostMapping("/templates/{id}/publish")
    public ResponseEntity<?> toggleActive(@PathVariable Long id, HttpServletRequest http) {
        return templateRepository.findById(id)
                .map(template -> {
                    requireGroupAccess(http, template.getDataHolderGroupId());
                    boolean nowActive = !Boolean.TRUE.equals(template.getIsActive());
                    template.setIsActive(nowActive);
                    templateRepository.save(template);

                    long liveSubscriptions = subscriptionRepository.findActiveByTemplateId(id).size();
                    audit.logCrud(nowActive ? "ACTIVATE" : "DEACTIVATE", "TEMPLATE",
                            template.getTemplateId(), template.getName(), "admin",
                            (nowActive ? "Activated" : "Deactivated") + " template"
                            + (nowActive || liveSubscriptions == 0 ? ""
                               : " — " + liveSubscriptions + " subscription(s) already in place are unaffected"));

                    Map<String, Object> body = new HashMap<>();
                    body.put("success", true);
                    body.put("isActive", nowActive);
                    body.put("isPublished", nowActive);
                    body.put("activeSubscriptions", liveSubscriptions);
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Sets how widely the template is advertised: PRIVATE, PUBLIC or GLOBAL. */
    @PatchMapping("/templates/{id}/visibility")
    public ResponseEntity<?> setTemplateVisibility(@PathVariable Long id,
                                                   @RequestBody VisibilityRequest request,
                                                   HttpServletRequest http) {
        Visibility requested = Visibility.parse(request.getVisibility(), null);
        if (requested == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "message", "Visibility must be one of PRIVATE, PUBLIC or GLOBAL."));
        }
        return templateRepository.findById(id)
                .map(template -> {
                    requireGroupAccess(http, template.getDataHolderGroupId());
                    template.setVisibility(requested);
                    templateRepository.save(template);

                    var groups = visibilityService.groupsById();
                    String note = visibilityService.clampReason(template, groups);
                    audit.logCrud("VISIBILITY", "TEMPLATE", template.getTemplateId(), template.getName(),
                            "admin", "Set template visibility to " + requested.name()
                            + (note == null ? "" : " (" + note + ")"));

                    Map<String, Object> body = new HashMap<>();
                    body.put("success", true);
                    body.put("visibility", requested.name());
                    body.put("effectiveVisibility",
                            visibilityService.effectiveVisibility(template, groups).name());
                    body.put("visibilityNote", note);
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Request Types ====================

    @GetMapping("/templates/{templateId}/request-types")
    public ResponseEntity<?> getRequestTypes(@PathVariable Long templateId, HttpServletRequest http) {
        return templateRepository.findById(templateId)
                .map(t -> {
                    requireGroupAccess(http, t.getDataHolderGroupId());
                    return ResponseEntity.ok(t.getRequestTypes().stream().map(mapper::toRequestTypeResponse).toList());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Subscribers of a template, each flagged with whether it is still running an older copy of
     * the template and whether a change is already awaiting their answer.
     */
    @GetMapping("/templates/{templateId}/subscribers")
    public ResponseEntity<?> getTemplateSubscribers(@PathVariable Long templateId, HttpServletRequest http) {
        return templateRepository.findById(templateId)
                .map(template -> {
                    requireGroupAccess(http, template.getDataHolderGroupId());
                    Map<String, Object> current = mapper.snapshotTemplate(template);
                    List<Map<String, Object>> rows = subscriptionRepository.findActiveByTemplateId(templateId)
                            .stream()
                            .map(sub -> {
                                Map<String, Object> row = new HashMap<String, Object>();
                                row.put("subscriptionId", sub.getId());
                                row.put("requestId", sub.getRequestId());
                                row.put("requestorGroupName", sub.getRequestorGroupName());
                                row.put("requestorGroupCode", sub.getRequestorGroupCode());
                                row.put("status", sub.getStatus().name());
                                row.put("pendingChangeStatus", sub.getPendingChangeStatus());
                                row.put("pendingProposedAt", sub.getPendingProposedAt());
                                row.put("outOfDate", !snapshotMatches(sub.getTemplateSnapshot(), current));
                                return row;
                            })
                            .toList();
                    return ResponseEntity.ok(Map.of("success", true, "subscribers", rows));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Push the template's current state to chosen subscribers: FORCE applies it at once, PROPOSE
     * leaves it awaiting the requestor's answer and changes nothing until they accept.
     */
    @PostMapping("/templates/{templateId}/publish-changes")
    public ResponseEntity<?> publishTemplateChanges(@PathVariable Long templateId,
                                                    @RequestBody PublishChangesRequest request,
                                                    HttpServletRequest http) {
        return templateRepository.findById(templateId)
                .map(template -> {
                    requireGroupAccess(http, template.getDataHolderGroupId());
                    if (request.getDecisions() == null || request.getDecisions().isEmpty()) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "success", false, "message", "Choose at least one subscriber to publish to."));
                    }
                    boolean anyForced = request.getDecisions().stream()
                            .anyMatch(d -> "FORCE".equalsIgnoreCase(d.getMode()));
                    if (anyForced && request.getRespondByDays() == null) {
                        return ResponseEntity.badRequest().body(Map.of("success", false,
                                "message", "A forced change needs a deadline for subscribers to respond by."));
                    }
                    LocalDateTime deadline = anyForced
                            ? LocalDateTime.now().plusDays(request.getRespondByDays()) : null;

                    Map<String, Object> snapshot = mapper.snapshotTemplate(template);
                    int forced = 0, proposed = 0;

                    for (PublishDecision decision : request.getDecisions()) {
                        AgreementSubscription sub = subscriptionRepository.findById(decision.getSubscriptionId())
                                .orElse(null);
                        if (sub == null || sub.getTemplate() == null
                                || !templateId.equals(sub.getTemplate().getId())) {
                            continue;
                        }
                        boolean force = "FORCE".equalsIgnoreCase(decision.getMode());
                        sub.setPendingTemplateSnapshot(snapshot);
                        sub.setPendingChangeStatus("PROPOSED");
                        sub.setPendingProposedAt(LocalDateTime.now());
                        sub.setPendingRespondedAt(null);
                        sub.setPendingChangeMode(force ? "FORCED" : "OPTIONAL");
                        sub.setPendingChangeDeadline(force ? deadline : null);
                        if (force) forced++; else proposed++;
                        subscriptionRepository.save(sub);
                    }

                    audit.logCrud("UPDATE", "TEMPLATE", template.getTemplateId(), template.getName(), "admin",
                            "Published template changes: " + forced + " forced, " + proposed + " proposed");

                    return ResponseEntity.ok(Map.of("success", true, "forced", forced, "proposed", proposed));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Whether a subscription's pinned copy still matches the template's current state. */
    private boolean snapshotMatches(Map<String, Object> snapshot, Map<String, Object> current) {
        if (snapshot == null) return false;
        return Objects.equals(snapshot.get("legalSections"), current.get("legalSections"))
                && Objects.equals(snapshot.get("subscriptionFields"), current.get("subscriptionFields"))
                && Objects.equals(snapshot.get("requestTypes"), current.get("requestTypes"));
    }

    @Data public static class PublishChangesRequest {
        private List<PublishDecision> decisions;
        /** Days a subscriber has to accept a forced change before the subscription is terminated. */
        private Integer respondByDays;
    }

    @Data public static class PublishDecision {
        private Long subscriptionId;
        /** FORCE or PROPOSE. */
        private String mode;
    }

    @PostMapping("/templates/{templateId}/request-types")
    public ResponseEntity<?> addRequestType(@PathVariable Long templateId, @RequestBody RequestTypeRequest request,
                                            HttpServletRequest http) {
        return templateRepository.findById(templateId)
                .map(template -> {
                    requireGroupAccess(http, template.getDataHolderGroupId());
                    if (requestTypeNameConflicts(template, request.getName(), null)) {
                        return ResponseEntity.badRequest().body(Map.of("success", false,
                                "message", "A request type named '" + request.getName().trim() + "' already exists in this template."));
                    }
                    AgreementRequestType rt = buildRequestType(request);
                    rt.setTypeCode(newTypeCode(template, rt.getName())); // system-generated, globally unique
                    template.addRequestType(rt);
                    templateRepository.save(template);

                    audit.logCrud("CREATE", "REQUEST_TYPE", template.getTemplateId(), request.getName(),
                            "admin", "Added request type '" + request.getName() + "' to template '" + template.getName() + "'");

                    return ResponseEntity.ok(Map.of("success", true, "requestType", mapper.toRequestTypeResponse(rt)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/templates/{templateId}/request-types/{rtId}")
    public ResponseEntity<?> updateRequestType(@PathVariable Long templateId, @PathVariable Long rtId, @RequestBody RequestTypeRequest request,
                                               HttpServletRequest http) {
        return templateRepository.findById(templateId)
                .map(template -> {
                    requireGroupAccess(http, template.getDataHolderGroupId());
                    AgreementRequestType rt = template.getRequestTypes().stream().filter(r -> r.getId().equals(rtId)).findFirst().orElse(null);
                    if (rt == null) return ResponseEntity.notFound().build();
                    if (request.getName() != null) {
                        if (requestTypeNameConflicts(template, request.getName(), rtId)) {
                            return ResponseEntity.badRequest().body(Map.of("success", false,
                                    "message", "A request type named '" + request.getName().trim() + "' already exists in this template."));
                        }
                        rt.setName(request.getName());
                    }
                    // typeCode is system-managed and immutable — never updated from the request.
                    if (request.getDescription() != null) rt.setDescription(request.getDescription());
                    if (request.getKind() != null) rt.setKind(parseKind(request.getKind()));
                    if (request.getAccessLevel() != null) rt.setAccessLevel(request.getAccessLevel());
                    if (request.getSupportsConfidential() != null) rt.setSupportsConfidential(request.getSupportsConfidential());
                    if (request.getSupportsExigent() != null) rt.setSupportsExigent(request.getSupportsExigent());
                    if (request.getRequiresManualApproval() != null) rt.setRequiresManualApproval(request.getRequiresManualApproval());
                    if (request.getSortOrder() != null) rt.setSortOrder(request.getSortOrder());
                    if (request.getIsActive() != null) rt.setIsActive(request.getIsActive());
                    // Regex fields — allow setting to null/blank to clear
                    rt.setQueryValueRegex(request.getQueryValueRegex());
                    rt.setQueryValueRegexError(request.getQueryValueRegexError());
                    if (rt.isRdrs()) {
                        // An RDRS type carries presets instead of RDAP parameters.
                        rt.setRdrsDefaults(request.getRdrsDefaults());
                        rt.setRdapParameters(null);
                    } else {
                        rt.setRdrsDefaults(null);
                        if (request.getRdapParameters() != null) rt.setRdapParameters(mapper.buildRdapParameters(request.getRdapParameters()));
                    }
                    if (request.getCustomParameters() != null) {
                        rt.getCustomParameters().clear();
                        for (CustomParameterRequest cpReq : request.getCustomParameters()) {
                            rt.addCustomParameter(buildCustomParameter(cpReq));
                        }
                    }
                    templateRepository.save(template);

                    audit.logCrud("UPDATE", "REQUEST_TYPE", String.valueOf(rtId), rt.getName(),
                            "admin", "Updated request type on template '" + template.getName() + "'");

                    return ResponseEntity.ok(Map.of("success", true, "requestType", mapper.toRequestTypeResponse(rt)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/templates/{templateId}/request-types/{rtId}")
    public ResponseEntity<?> deleteRequestType(@PathVariable Long templateId, @PathVariable Long rtId,
                                               HttpServletRequest http) {
        return templateRepository.findById(templateId)
                .map(template -> {
                    requireGroupAccess(http, template.getDataHolderGroupId());
                    boolean removed = template.getRequestTypes().removeIf(rt -> rt.getId().equals(rtId));
                    if (!removed) return ResponseEntity.notFound().build();
                    templateRepository.save(template);

                    audit.logCrud("DELETE", "REQUEST_TYPE", String.valueOf(rtId), null,
                            "admin", "Removed request type from template '" + template.getName() + "'");

                    return ResponseEntity.ok(Map.of("success", true, "message", "Request type deleted"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Subscriptions ====================

    @GetMapping("/subscriptions")
    public ResponseEntity<?> getAllSubscriptions(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long dataHolderGroupId,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            HttpServletRequest http) {
        Map<Long, DataHolderGroup> groups = visibilityService.groupsById();
        Set<Long> scope = callerGroupIds(http);
        if (page == null) {
            return ResponseEntity.ok(subscriptionRepository.findAllByOrderByCreatedAtDesc().stream()
                    .filter(s -> inScopeAny(scope, subscriptionGroupIds(s)))
                    .map(s -> mapper.toAdminSubscriptionResponse(s, groups)).toList());
        }
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Page<AgreementSubscription> pg;
        if (scope == null) {
            pg = subscriptionRepository.search(cleanSearch(search), dataHolderGroupId, status, pageable);
        } else {
            Set<Long> in = pageScope(scope, dataHolderGroupId);
            pg = in.isEmpty() ? Page.empty(pageable)
                    : subscriptionRepository.searchInGroups(cleanSearch(search), in, status, pageable);
        }
        return ResponseEntity.ok(pageResponse(pg, pg.getContent().stream()
                .map(s -> mapper.toAdminSubscriptionResponse(s, groups)).toList()));
    }

    /**
     * Issue (or re-issue) the credentials a requestor manager uses for this subscription.
     * The secret is returned once here and only its BCrypt hash is stored, matching the
     * data holder credential flow.
     */
    @PostMapping("/subscriptions/{id}/regenerate-credentials")
    public ResponseEntity<?> regenerateSubscriptionCredentials(@PathVariable Long id, HttpServletRequest http) {
        return subscriptionRepository.findById(id)
                .map(sub -> {
                    requireAnyGroupAccess(http, subscriptionGroupIds(sub));
                    subCredentialRepository.findByRequestId(sub.getRequestId())
                            .forEach(c -> { c.setIsActive(false); subCredentialRepository.save(c); });

                    SubscriptionCredential cred = SubscriptionCredential.generate(
                            sub.getRequestId(), sub.getRequestorGroupName());
                    String plaintextSecret = cred.hashSecretForStorage();
                    cred = subCredentialRepository.save(cred);

                    log.info("Generated subscription credentials for {}: clientId={}",
                            sub.getRequestId(), cred.getClientId());
                    audit.logCredential("GENERATE", "SUBSCRIPTION", sub.getRequestId(),
                            sub.getRequestorGroupName(), "admin",
                            "Generated subscription credentials. clientId=" + cred.getClientId());

                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "clientId", cred.getClientId(),
                            "clientSecret", plaintextSecret,
                            "requestId", sub.getRequestId(),
                            "requestorGroupName", sub.getRequestorGroupName() != null ? sub.getRequestorGroupName() : "",
                            "message", "New credentials generated"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Register the public key a requestor manager signs this subscription's calls with.
     *
     * <p>The value is public by construction, so it is pasted in rather than generated here.
     * It is validated by loading it, so a malformed key is rejected now instead of silently
     * failing every later request.
     */
    @PostMapping("/subscriptions/{id}/public-key")
    public ResponseEntity<?> registerSubscriptionPublicKey(@PathVariable Long id,
                                                            @RequestBody Map<String, String> body,
                                                            HttpServletRequest http) {
        String publicKey = body == null ? null : body.get("publicKey");
        if (publicKey == null || publicKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "A public key is required"));
        }
        try {
            HttpSignatures.publicKey(publicKey.trim());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "error", "That is not a valid Ed25519 public key"));
        }
        return subscriptionRepository.findById(id)
                .map(sub -> {
                    requireAnyGroupAccess(http, subscriptionGroupIds(sub));
                    var credOpt = subCredentialRepository.findByRequestIdAndIsActiveTrue(sub.getRequestId());
                    if (credOpt.isEmpty()) {
                        return ResponseEntity.ok(Map.of("success", false,
                                "error", "Generate credentials for this subscription first."));
                    }
                    SubscriptionCredential cred = credOpt.get();
                    cred.setPublicKey(publicKey.trim());
                    cred.setPublicKeyRegisteredAt(LocalDateTime.now());
                    subCredentialRepository.save(cred);
                    audit.logCredential("REGISTER_KEY", "SUBSCRIPTION", sub.getRequestId(),
                            sub.getRequestorGroupName(), "admin",
                            "Registered a request-signing public key");
                    log.info("Registered signing public key for subscription {}", sub.getRequestId());
                    return ResponseEntity.ok(Map.of("success", true,
                            "requestId", sub.getRequestId(),
                            "message", "Public key registered"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** The clientId in force for a subscription; the secret is never retrievable after minting. */
    @PostMapping("/subscriptions/{id}/reveal-credentials")
    public ResponseEntity<?> revealSubscriptionCredentials(@PathVariable Long id, HttpServletRequest http) {
        return subscriptionRepository.findById(id)
                .map(sub -> {
                    requireAnyGroupAccess(http, subscriptionGroupIds(sub));
                    var credOpt = subCredentialRepository.findByRequestIdAndIsActiveTrue(sub.getRequestId());
                    if (credOpt.isEmpty()) {
                        return ResponseEntity.ok(Map.of("success", false,
                                "error", "No credentials have been generated for this subscription yet."));
                    }
                    SubscriptionCredential cred = credOpt.get();
                    audit.logCredential("VIEW", "SUBSCRIPTION", sub.getRequestId(),
                            sub.getRequestorGroupName(), "admin",
                            "Viewed credential clientId=" + cred.getClientId());
                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "clientId", cred.getClientId(),
                            "requestId", sub.getRequestId(),
                            "requestorGroupName", sub.getRequestorGroupName() != null ? sub.getRequestorGroupName() : "",
                            "lastUsedAt", cred.getLastUsedAt() != null ? cred.getLastUsedAt().toString() : "",
                            "publicKeyRegisteredAt", cred.getPublicKeyRegisteredAt() != null
                                    ? cred.getPublicKeyRegisteredAt().toString() : "",
                            "secretAvailable", false,
                            "message", "For security, the client secret is only shown once when generated. "
                                    + "Use \"Regenerate credentials\" to issue a new secret."));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/subscriptions/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingSubscriptions(HttpServletRequest http) {
        Map<Long, DataHolderGroup> groups = visibilityService.groupsById();
        Set<Long> scope = callerGroupIds(http);
        return ResponseEntity.ok(subscriptionRepository.findPendingSubscriptions().stream()
                .filter(s -> inScopeAny(scope, subscriptionGroupIds(s)))
                .map(s -> mapper.toAdminSubscriptionResponse(s, groups)).toList());
    }

    @GetMapping("/subscriptions/active")
    public ResponseEntity<List<Map<String, Object>>> getActiveSubscriptions(HttpServletRequest http) {
        Map<Long, DataHolderGroup> groups = visibilityService.groupsById();
        Set<Long> scope = callerGroupIds(http);
        return ResponseEntity.ok(subscriptionRepository.findAllActive().stream()
                .filter(s -> inScopeAny(scope, subscriptionGroupIds(s)))
                .map(s -> mapper.toAdminSubscriptionResponse(s, groups)).toList());
    }

    @GetMapping("/subscriptions/{id}")
    public ResponseEntity<?> getSubscription(@PathVariable Long id, HttpServletRequest http) {
        return subscriptionRepository.findById(id)
                .map(subscription -> {
                    requireAnyGroupAccess(http, subscriptionGroupIds(subscription));
                    Map<String, Object> response = mapper.toAdminSubscriptionResponse(subscription);
                    List<AgreementStatusLog> logs = statusLogRepository.findBySubscriptionIdOrderByCreatedAtDesc(id);
                    response.put("statusHistory", logs.stream().map(mapper::toStatusLogResponse).toList());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/subscriptions/{id}/approve")
    public ResponseEntity<?> approveSubscription(@PathVariable Long id, @RequestBody ReviewRequest request,
                                            HttpServletRequest http) {
        requireSubscriptionAccess(http, id);
        try {
            SubscriptionTestService.ApprovalOutcome outcome =
                    subscriptionTestService.approveAndActivate(id, request.getReviewedBy(), request.getNotes());
            AgreementSubscription result = outcome.subscription();

            audit.logWorkflow("APPROVE", "SUBSCRIPTION", result.getRequestId(), result.getRequestorGroupName(),
                    request.getReviewedBy(), "Approved subscription. Notes: " + (request.getNotes() != null ? request.getNotes() : ""));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("subscription", mapper.toAdminSubscriptionResponse(result));
            if (outcome.report() != null) {
                auditTestRun(result, outcome.report(), request.getReviewedBy());
                body.put("testResult", adminTestResult(outcome.report()));
            }
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException | IllegalStateException e) { return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage())); }
    }

    /** Audits a subscription test run with its technical causes. */
    private void auditTestRun(AgreementSubscription subscription, SubscriptionTestService.TestReport report, String actor) {
        audit.logWorkflow("RUN_TEST", "SUBSCRIPTION", subscription.getRequestId(),
                subscription.getRequestorGroupName(), actor,
                "Ran subscription test — " + report.getResult() + ": "
                        + (report.getInternalDetails() == null || report.getInternalDetails().isBlank()
                                ? report.getDetails() : report.getInternalDetails()));
    }

    /** The admin view of a test report: verdict, per-check causes and diagnostics. */
    private Map<String, Object> adminTestResult(SubscriptionTestService.TestReport report) {
        List<Map<String, Object>> checks = report.getChecks().stream()
                .map(c -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", c.getName());
                    entry.put("description", c.getDescription());
                    entry.put("passed", c.isPassed());
                    entry.put("message", c.getErrorMessage());
                    entry.put("detail", c.getInternalDetail());
                    return entry;
                })
                .toList();
        Map<String, Object> testResult = new LinkedHashMap<>();
        testResult.put("result", report.getResult());
        testResult.put("details", report.getDetails());
        testResult.put("checks", checks);
        testResult.put("diagnostics", report.getDiagnostics());
        return testResult;
    }

    @PostMapping("/subscriptions/{id}/deny")
    public ResponseEntity<?> denySubscription(@PathVariable Long id, @RequestBody ReviewRequest request,
                                            HttpServletRequest http) {
        requireSubscriptionAccess(http, id);
        try {
            AgreementSubscription result = subscriptionService.denySubscription(id, request.getReviewedBy(), request.getNotes());

            audit.logWorkflow("DENY", "SUBSCRIPTION", result.getRequestId(), result.getRequestorGroupName(),
                    request.getReviewedBy(), "Denied subscription. Reason: " + (request.getNotes() != null ? request.getNotes() : ""));

            return ResponseEntity.ok(Map.of("success", true, "subscription", mapper.toAdminSubscriptionResponse(result)));
        } catch (IllegalArgumentException | IllegalStateException e) { return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage())); }
    }

    @PostMapping("/subscriptions/{id}/start-test")
    public ResponseEntity<?> startTest(@PathVariable Long id, @RequestBody ActionRequest request,
                                            HttpServletRequest http) {
        requireSubscriptionAccess(http, id);
        try {
            AgreementSubscription result = subscriptionService.startTesting(id, request.getInitiatedBy());

            audit.logWorkflow("START_TEST", "SUBSCRIPTION", result.getRequestId(), result.getRequestorGroupName(),
                    request.getInitiatedBy(), "Started testing");

            return ResponseEntity.ok(Map.of("success", true, "subscription", mapper.toAdminSubscriptionResponse(result)));
        } catch (IllegalArgumentException | IllegalStateException e) { return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage())); }
    }

    /**
     * Admin-triggered run of the subscription test. Mirrors the external run-test, which admins
     * cannot reach directly because it requires the requestor manager's subscription credentials.
     */
    @PostMapping("/subscriptions/{id}/run-test")
    public ResponseEntity<?> runTest(@PathVariable Long id, HttpServletRequest http) {
        AgreementSubscription subscription = subscriptionRepository.findById(id).orElse(null);
        if (subscription == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Subscription not found"));
        }
        requireAnyGroupAccess(http, subscriptionGroupIds(subscription));

        try {
            String actor = http.getAttribute("userEmail") instanceof String email ? email : "admin";
            SubscriptionTestService.TestReport report = subscriptionTestService.runTest(subscription.getRequestId(), actor);
            auditTestRun(subscription, report, actor);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("testResult", adminTestResult(report));
            body.put("subscription", mapper.toAdminSubscriptionResponse(
                    subscriptionRepository.findById(id).orElse(subscription)));
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/subscriptions/{id}/record-test-result")
    public ResponseEntity<?> recordTestResult(@PathVariable Long id, @RequestBody TestResultRequest request,
                                            HttpServletRequest http) {
        requireSubscriptionAccess(http, id);
        try {
            AgreementSubscription result = subscriptionService.recordTestResult(id, request.getResult(), request.getDetails());
            String actor = http.getAttribute("userEmail") instanceof String email ? email : "admin";

            audit.logWorkflow("RECORD_TEST", "SUBSCRIPTION", result.getRequestId(), result.getRequestorGroupName(),
                    actor, "Recorded test result: " + request.getResult());

            if ("PASSED".equalsIgnoreCase(request.getResult())) {
                result = subscriptionService.activateSubscription(id, actor);
                audit.logWorkflow("ACTIVATE", "SUBSCRIPTION", result.getRequestId(), result.getRequestorGroupName(),
                        actor, "Activated subscription after test marked passed");
            }

            return ResponseEntity.ok(Map.of("success", true, "subscription", mapper.toAdminSubscriptionResponse(result)));
        } catch (IllegalArgumentException | IllegalStateException e) { return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage())); }
    }

    @PostMapping("/subscriptions/{id}/activate")
    public ResponseEntity<?> activateSubscription(@PathVariable Long id, @RequestBody ActionRequest request,
                                            HttpServletRequest http) {
        requireSubscriptionAccess(http, id);
        try {
            AgreementSubscription result = subscriptionService.activateSubscription(id, request.getInitiatedBy());

            audit.logWorkflow("ACTIVATE", "SUBSCRIPTION", result.getRequestId(), result.getRequestorGroupName(),
                    request.getInitiatedBy(), "Activated subscription");

            return ResponseEntity.ok(Map.of("success", true, "subscription", mapper.toAdminSubscriptionResponse(result)));
        } catch (IllegalArgumentException | IllegalStateException e) { return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage())); }
    }

    @PostMapping("/subscriptions/{id}/suspend")
    public ResponseEntity<?> suspendSubscription(@PathVariable Long id, @RequestBody ReviewRequest request,
                                            HttpServletRequest http) {
        requireSubscriptionAccess(http, id);
        try {
            AgreementSubscription result = subscriptionService.suspendSubscription(id, request.getReviewedBy(), request.getNotes());

            audit.logWorkflow("SUSPEND", "SUBSCRIPTION", result.getRequestId(), result.getRequestorGroupName(),
                    request.getReviewedBy(), "Suspended. Reason: " + (request.getNotes() != null ? request.getNotes() : ""));

            return ResponseEntity.ok(Map.of("success", true, "subscription", mapper.toAdminSubscriptionResponse(result)));
        } catch (IllegalArgumentException | IllegalStateException e) { return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage())); }
    }

    @PostMapping("/subscriptions/{id}/reactivate")
    public ResponseEntity<?> reactivateSubscription(@PathVariable Long id, @RequestBody ActionRequest request,
                                            HttpServletRequest http) {
        requireSubscriptionAccess(http, id);
        try {
            AgreementSubscription result = subscriptionService.reactivateSubscription(id, request.getInitiatedBy());

            audit.logWorkflow("REACTIVATE", "SUBSCRIPTION", result.getRequestId(), result.getRequestorGroupName(),
                    request.getInitiatedBy(), "Reactivated subscription");

            return ResponseEntity.ok(Map.of("success", true, "subscription", mapper.toAdminSubscriptionResponse(result)));
        } catch (IllegalArgumentException | IllegalStateException e) { return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage())); }
    }

    @PatchMapping("/subscriptions/{id}/introspection-url")
    public ResponseEntity<?> updateIntrospectionUrl(@PathVariable Long id, @RequestBody UpdateIntrospectionUrlRequest request,
                                                    HttpServletRequest http) {
        try {
            AgreementSubscription subscription = subscriptionRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
            requireAnyGroupAccess(http, subscriptionGroupIds(subscription));

            String oldUrl = subscription.getIntrospectionUrl();
            subscription.setIntrospectionUrl(request.getIntrospectionUrl());
            subscription = subscriptionRepository.save(subscription);

            audit.logWorkflow("UPDATE_INTROSPECTION_URL", "SUBSCRIPTION", subscription.getRequestId(),
                    subscription.getRequestorGroupName(), "admin",
                    "Introspection URL updated from '" + (oldUrl != null ? oldUrl : "none") +
                    "' to '" + (request.getIntrospectionUrl() != null ? request.getIntrospectionUrl() : "none") + "'");

            log.info("Introspection URL updated for subscription {} ({})",
                    subscription.getRequestId(), subscription.getRequestorGroupName());

            return ResponseEntity.ok(Map.of("success", true, "subscription", mapper.toAdminSubscriptionResponse(subscription)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== Data Holders ====================

    @GetMapping("/data-holders")
    public ResponseEntity<?> getAllDataHolders(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long dataHolderGroupId,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletRequest http) {
        Set<Long> scope = callerGroupIds(http);
        if (page == null) {
            return ResponseEntity.ok(dataHolderRepository.findAll().stream()
                    .filter(dh -> inScopeAny(scope, dh.getAllGroupIds()))
                    .map(mapper::toDataHolderResponse).toList());
        }
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Page<DataHolder> pg;
        if (scope == null) {
            pg = dataHolderRepository.search(cleanSearch(search), dataHolderGroupId, pageable);
        } else {
            Set<Long> in = pageScope(scope, dataHolderGroupId);
            pg = in.isEmpty() ? Page.empty(pageable) : dataHolderRepository.searchInGroups(cleanSearch(search), in, pageable);
        }
        return ResponseEntity.ok(pageResponse(pg, pg.getContent().stream().map(mapper::toDataHolderResponse).toList()));
    }

    @GetMapping("/data-holders/{id}")
    public ResponseEntity<?> getDataHolder(@PathVariable Long id, HttpServletRequest http) {
        return dataHolderRepository.findById(id)
                .map(dh -> {
                    requireAnyGroupAccess(http, dh.getAllGroupIds());
                    Map<String, Object> response = mapper.toDataHolderResponse(dh);
                    credentialRepository.findByDataholderIdAndIsActiveTrue(dh.getDataholderId())
                            .ifPresent(cred -> {
                                response.put("clientId", cred.getClientId());
                                response.put("hasCredentials", true);
                                response.put("credentialCreatedAt", cred.getCreatedAt());
                            });
                    if (!response.containsKey("hasCredentials")) {
                        response.put("hasCredentials", false);
                    }
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/data-holders")
    public ResponseEntity<?> registerDataHolder(@RequestBody DataHolderRequest request, HttpServletRequest http) {
        requireMaster(http);
        if (dataHolderRepository.existsByDataholderId(request.getDataholderId())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Data holder ID already registered"));
        }
        String url = request.getUrl() != null ? validDataHolderUrl(request.getUrl()) : null;

        // Resolve group IDs: prefer new list, fall back to single legacy field
        java.util.List<Long> groupIds = request.getDataHolderGroupIds();
        if (groupIds == null || groupIds.isEmpty()) {
            groupIds = request.getDataHolderGroupId() != null
                    ? java.util.List.of(request.getDataHolderGroupId())
                    : java.util.List.of();
        }

        DataHolder dh = DataHolder.builder()
                .dataholderId(request.getDataholderId())
                .name(request.getName())
                .url(url)
                .description(request.getDescription())
                .contactEmail(request.getContactEmail())
                .callbackUrl(request.getCallbackUrl())
                .dataHolderGroupId(!groupIds.isEmpty() ? groupIds.get(0) : null)
                .isActive(true)
                .build();

        // Populate the many-to-many group memberships
        Set<DataHolderGroup> groupSet = new HashSet<>();
        for (Long gid : groupIds) {
            dataHolderGroupRepository.findById(gid).ifPresent(groupSet::add);
        }
        dh.setDataHolderGroups(groupSet);

        dh = dataHolderRepository.save(dh);

        /* Auto-generate credentials upon registration. The secret is shown to the
         * admin exactly once here; only its BCrypt hash is persisted. */
        DataHolderCredential cred = DataHolderCredential.generate(dh.getDataholderId(), dh.getName());
        String plaintextSecret = cred.hashSecretForStorage();
        cred = credentialRepository.save(cred);
        log.info("Generated credentials for data holder {}: clientId={}", dh.getDataholderId(), cred.getClientId());

        audit.logCrud("CREATE", "DATA_HOLDER", dh.getDataholderId(), dh.getName(),
                "admin", "Registered data holder in groups: " + dh.getAllGroupIds());
        audit.logCredential("GENERATE", "DATA_HOLDER", dh.getDataholderId(), dh.getName(),
                "admin", "Auto-generated credentials. clientId=" + cred.getClientId());

        Map<String, Object> response = mapper.toDataHolderResponse(dh);
        response.put("clientId", cred.getClientId());
        response.put("clientSecret", plaintextSecret);
        response.put("hasCredentials", true);

        return ResponseEntity.ok(Map.of("success", true, "dataHolder", response));
    }

    @PutMapping("/data-holders/{id}")
    public ResponseEntity<?> updateDataHolder(@PathVariable Long id, @RequestBody DataHolderRequest request,
                                              HttpServletRequest http) {
        requireMaster(http);
        return dataHolderRepository.findById(id)
                .map(dh -> {
                    if (request.getName() != null) dh.setName(request.getName());
                    if (request.getUrl() != null) dh.setUrl(validDataHolderUrl(request.getUrl()));
                    if (request.getDescription() != null) dh.setDescription(request.getDescription());
                    if (request.getContactEmail() != null) dh.setContactEmail(request.getContactEmail());
                    if (request.getCallbackUrl() != null) dh.setCallbackUrl(request.getCallbackUrl());

                    // Handle group membership updates
                    if (request.getDataHolderGroupIds() != null) {
                        // New multi-group update: replace all memberships
                        Set<DataHolderGroup> newGroups = new HashSet<>();
                        for (Long gid : request.getDataHolderGroupIds()) {
                            dataHolderGroupRepository.findById(gid).ifPresent(newGroups::add);
                        }
                        dh.setDataHolderGroups(newGroups);
                        // Sync legacy field with first group
                        dh.setDataHolderGroupId(!newGroups.isEmpty()
                                ? newGroups.iterator().next().getId() : null);
                    } else if (request.getDataHolderGroupId() != null) {
                        // Legacy single-group update (backward compat)
                        dh.setDataHolderGroupId(request.getDataHolderGroupId());
                        dataHolderGroupRepository.findById(request.getDataHolderGroupId())
                                .ifPresent(g -> {
                                    Set<DataHolderGroup> groups = dh.getDataHolderGroups();
                                    if (groups == null) groups = new HashSet<>();
                                    // Only add, don't remove existing memberships for backward compat
                                    groups.add(g);
                                    dh.setDataHolderGroups(groups);
                                });
                    }

                    if (request.getIsActive() != null) {
                        boolean wasActive = dh.getIsActive();
                        dh.setIsActive(request.getIsActive());
                        if (wasActive != request.getIsActive()) {
                            audit.logCrud(request.getIsActive() ? "ENABLE" : "DISABLE", "DATA_HOLDER",
                                    dh.getDataholderId(), dh.getName(), "admin",
                                    (request.getIsActive() ? "Enabled" : "Disabled") + " data holder");
                        }
                    }
                    dataHolderRepository.save(dh);
                    audit.logCrud("UPDATE", "DATA_HOLDER", dh.getDataholderId(), dh.getName(), "admin",
                            "Updated data holder. Groups: " + dh.getAllGroupIds());
                    return ResponseEntity.ok(Map.of("success", true, "dataHolder", mapper.toDataHolderResponse(dh)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/data-holders/{id}")
    public ResponseEntity<?> deleteDataHolder(@PathVariable Long id, HttpServletRequest http) {
        requireMaster(http);
        return dataHolderRepository.findById(id)
                .map(dh -> {
                    credentialRepository.findByDataholderId(dh.getDataholderId())
                            .forEach(c -> { c.setIsActive(false); credentialRepository.save(c); });
                    dataHolderRepository.delete(dh);
                    log.info("Deleted data holder {} ({})", dh.getDataholderId(), dh.getName());
                    audit.logCrud("DELETE", "DATA_HOLDER", dh.getDataholderId(), dh.getName(),
                            "admin", "Deleted data holder and deactivated credentials");
                    return ResponseEntity.ok(Map.of("success", true, "message", "Data holder deleted"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Data Holder Credentials ====================

    @PostMapping("/data-holders/{id}/regenerate-credentials")
    public ResponseEntity<?> regenerateCredentials(@PathVariable Long id, HttpServletRequest http) {
        requireMaster(http);
        return dataHolderRepository.findById(id)
                .map(dh -> {
                    // Deactivate existing credentials
                    credentialRepository.findByDataholderId(dh.getDataholderId())
                            .forEach(c -> { c.setIsActive(false); credentialRepository.save(c); });

                    /* Generate new credentials. The secret is shown once here; only
                     * its BCrypt hash is persisted. */
                    DataHolderCredential cred = DataHolderCredential.generate(dh.getDataholderId(), dh.getName());
                    String plaintextSecret = cred.hashSecretForStorage();
                    cred = credentialRepository.save(cred);
                    log.info("Regenerated credentials for data holder {}: clientId={}", dh.getDataholderId(), cred.getClientId());
                    audit.logCredential("REGENERATE", "DATA_HOLDER", dh.getDataholderId(), dh.getName(),
                            "admin", "Regenerated credentials. New clientId=" + cred.getClientId());
                    return ResponseEntity.ok(Map.of(
                            "success", true, "clientId", cred.getClientId(), "clientSecret", plaintextSecret,
                            "message", "New credentials generated. The secret is only shown once — copy it now."
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/data-holders/{id}/credentials")
    public ResponseEntity<?> getDataHolderCredentials(@PathVariable Long id, HttpServletRequest http) {
        return dataHolderRepository.findById(id)
                .map(dh -> {
                    requireAnyGroupAccess(http, dh.getAllGroupIds());
                    var creds = credentialRepository.findByDataholderId(dh.getDataholderId());
                    var result = creds.stream().map(c -> Map.of(
                            "id", (Object) c.getId(), "clientId", c.getClientId(), "isActive", c.getIsActive(),
                            "lastUsedAt", c.getLastUsedAt() != null ? c.getLastUsedAt().toString() : "",
                            "createdAt", c.getCreatedAt().toString()
                    )).toList();
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Returns the active credential's clientId only. Secrets are BCrypt-hashed at rest and shown
     * once at generation time; obtaining a usable secret requires regenerating the credentials.
     */
    @PostMapping("/data-holders/{id}/reveal-credentials")
    public ResponseEntity<?> revealCredentials(@PathVariable Long id, HttpServletRequest http) {
        return dataHolderRepository.findById(id)
                .map(dh -> {
                    requireAnyGroupAccess(http, dh.getAllGroupIds());
                    var credOpt = credentialRepository.findByDataholderIdAndIsActiveTrue(dh.getDataholderId());
                    if (credOpt.isEmpty()) {
                        return ResponseEntity.ok(Map.of("success", false, "error", "No active credentials found. Register or regenerate credentials first."));
                    }
                    DataHolderCredential cred = credOpt.get();
                    log.info("Admin viewed credential clientId for data holder {}: clientId={}", dh.getDataholderId(), cred.getClientId());
                    audit.logCredential("VIEW", "DATA_HOLDER", dh.getDataholderId(), dh.getName(),
                            "admin", "Viewed credential clientId=" + cred.getClientId());
                    return ResponseEntity.ok(Map.of(
                            "success", true, "clientId", cred.getClientId(),
                            "dataholderName", dh.getName(), "dataholderId", dh.getDataholderId(),
                            "secretAvailable", false,
                            "message", "For security, the client secret is only shown once when generated. " +
                                    "Use \"Regenerate credentials\" to issue a new secret."
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Data Holder Applications ====================

    @GetMapping("/data-holder-applications")
    public ResponseEntity<?> getAllApplications(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long dataHolderGroupId,
            @RequestParam(required = false) DataHolderApplication.ApplicationStatus status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            HttpServletRequest http) {
        Set<Long> scope = callerGroupIds(http);
        if (page == null) {
            return ResponseEntity.ok(dataHolderApplicationRepository.findAll().stream()
                    .filter(a -> inScope(scope, a.getDataHolderGroupId()))
                    .map(this::toApplicationResponse).toList());
        }
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Page<DataHolderApplication> pg;
        if (scope == null) {
            pg = dataHolderApplicationRepository.search(cleanSearch(search), dataHolderGroupId, status, pageable);
        } else {
            Set<Long> in = pageScope(scope, dataHolderGroupId);
            pg = in.isEmpty() ? Page.empty(pageable)
                    : dataHolderApplicationRepository.searchInGroups(cleanSearch(search), in, status, pageable);
        }
        return ResponseEntity.ok(pageResponse(pg, pg.getContent().stream().map(this::toApplicationResponse).toList()));
    }

    @GetMapping("/data-holder-applications/pending")
    public ResponseEntity<?> getPendingApplications(HttpServletRequest http) {
        Set<Long> scope = callerGroupIds(http);
        return ResponseEntity.ok(dataHolderApplicationRepository
                .findByStatus(DataHolderApplication.ApplicationStatus.PENDING).stream()
                .filter(a -> inScope(scope, a.getDataHolderGroupId()))
                .map(this::toApplicationResponse).toList());
    }

    @GetMapping("/data-holder-applications/{id}")
    public ResponseEntity<?> getApplication(@PathVariable Long id, HttpServletRequest http) {
        return dataHolderApplicationRepository.findById(id)
                .map(a -> {
                    requireGroupAccess(http, a.getDataHolderGroupId());
                    return ResponseEntity.ok(toApplicationResponse(a));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Approve an application: creates a DataHolder from the application data,
     * auto-generates credentials, and marks the application as APPROVED.
     */
    @PostMapping("/data-holder-applications/{id}/approve")
    public ResponseEntity<?> approveApplication(@PathVariable Long id,
                                                 @RequestBody(required = false) ReviewRequest request,
                                                 HttpServletRequest http) {
        return dataHolderApplicationRepository.findById(id)
                .map(app -> {
                    requireGroupAccess(http, app.getDataHolderGroupId());
                    if (app.getStatus() != DataHolderApplication.ApplicationStatus.PENDING) {
                        return ResponseEntity.badRequest().body(Map.of("success", false,
                                "error", "Application is not in PENDING status"));
                    }

                    // Create the DataHolder from application data
                    DataHolder dh = DataHolder.builder()
                            .name(app.getOrganizationName())
                            .url(app.getRdapServerUrls()) // primary RDAP URL
                            .description("RDAP Servers: " + (app.getRdapServerUrls() != null ? app.getRdapServerUrls() : "N/A")
                                    + "\nTLDs: " + (app.getSupportedTlds() != null ? app.getSupportedTlds() : "N/A")
                                    + "\nPhone: " + (app.getOrganizationPhone() != null ? app.getOrganizationPhone() : "N/A")
                                    + "\nAddress: " + (app.getOrganizationAddress() != null ? app.getOrganizationAddress() : "N/A"))
                            .contactEmail(app.getContactEmail())
                            .dataHolderGroupId(app.getDataHolderGroupId())
                            .isActive(true)
                            .build();

                    // Also populate the many-to-many relationship
                    Set<DataHolderGroup> groups = new HashSet<>();
                    dataHolderGroupRepository.findById(app.getDataHolderGroupId()).ifPresent(groups::add);
                    dh.setDataHolderGroups(groups);

                    dh = dataHolderRepository.save(dh);

                    // Auto-generate credentials
                    DataHolderCredential cred = DataHolderCredential.generate(dh.getDataholderId(), dh.getName());
                    cred = credentialRepository.save(cred);

                    // Update application status
                    app.setStatus(DataHolderApplication.ApplicationStatus.APPROVED);
                    app.setReviewNotes(request != null ? request.getNotes() : null);
                    app.setReviewedBy(request != null ? request.getReviewedBy() : "admin");
                    app.setReviewedAt(java.time.LocalDateTime.now());
                    app.setCreatedDataHolderId(dh.getId());
                    dataHolderApplicationRepository.save(app);

                    audit.logCrud("APPROVE", "DATA_HOLDER_APPLICATION", String.valueOf(app.getId()),
                            app.getOrganizationName(), request != null ? request.getReviewedBy() : "admin",
                            "Approved application and created data holder " + dh.getDataholderId());

                    Map<String, Object> response = mapper.toDataHolderResponse(dh);
                    response.put("clientId", cred.getClientId());
                    response.put("clientSecret", cred.getClientSecret());

                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "message", "Application approved. Data holder created with credentials.",
                            "dataHolder", response,
                            "application", toApplicationResponse(app)
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deny an application.
     */
    @PostMapping("/data-holder-applications/{id}/deny")
    public ResponseEntity<?> denyApplication(@PathVariable Long id,
                                              @RequestBody(required = false) ReviewRequest request,
                                              HttpServletRequest http) {
        return dataHolderApplicationRepository.findById(id)
                .map(app -> {
                    requireGroupAccess(http, app.getDataHolderGroupId());
                    if (app.getStatus() != DataHolderApplication.ApplicationStatus.PENDING) {
                        return ResponseEntity.badRequest().body(Map.of("success", false,
                                "error", "Application is not in PENDING status"));
                    }
                    app.setStatus(DataHolderApplication.ApplicationStatus.DENIED);
                    app.setReviewNotes(request != null ? request.getNotes() : null);
                    app.setReviewedBy(request != null ? request.getReviewedBy() : "admin");
                    app.setReviewedAt(java.time.LocalDateTime.now());
                    dataHolderApplicationRepository.save(app);

                    audit.logCrud("DENY", "DATA_HOLDER_APPLICATION", String.valueOf(app.getId()),
                            app.getOrganizationName(), request != null ? request.getReviewedBy() : "admin",
                            "Denied application" + (request != null && request.getNotes() != null ? ": " + request.getNotes() : ""));

                    return ResponseEntity.ok(Map.of("success", true, "message", "Application denied",
                            "application", toApplicationResponse(app)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/data-holder-applications/{id}")
    public ResponseEntity<?> deleteApplication(@PathVariable Long id, HttpServletRequest http) {
        return dataHolderApplicationRepository.findById(id)
                .map(app -> {
                    requireGroupAccess(http, app.getDataHolderGroupId());
                    dataHolderApplicationRepository.delete(app);
                    audit.logCrud("DELETE", "DATA_HOLDER_APPLICATION", String.valueOf(id),
                            app.getOrganizationName(), "admin", "Deleted application");
                    return ResponseEntity.ok(Map.of("success", true, "message", "Application deleted"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toApplicationResponse(DataHolderApplication a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("dataHolderGroupId", a.getDataHolderGroupId());
        m.put("organizationName", a.getOrganizationName());
        m.put("organizationAddress", a.getOrganizationAddress());
        m.put("organizationPhone", a.getOrganizationPhone());
        m.put("contactFullName", a.getContactFullName());
        m.put("contactEmail", a.getContactEmail());
        m.put("contactPhone", a.getContactPhone());
        m.put("contactTitle", a.getContactTitle());
        m.put("rdapServerUrls", a.getRdapServerUrls());
        m.put("supportedTlds", a.getSupportedTlds());
        m.put("additionalNotes", a.getAdditionalNotes());
        m.put("status", a.getStatus().name());
        m.put("reviewNotes", a.getReviewNotes());
        m.put("reviewedBy", a.getReviewedBy());
        m.put("reviewedAt", a.getReviewedAt() != null ? a.getReviewedAt().toString() : null);
        m.put("createdDataHolderId", a.getCreatedDataHolderId());
        m.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
        m.put("updatedAt", a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null);
        return m;
    }

    // ==================== Data Holder Groups ====================

    @GetMapping("/data-holder-groups")
    public ResponseEntity<?> getAllDataHolderGroups(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletRequest http) {
        Set<Long> scope = callerGroupIds(http);
        if (page == null) {
            return ResponseEntity.ok(dataHolderGroupRepository.findAll().stream()
                    .filter(g -> inScope(scope, g.getId()))
                    .map(this::toGroupResponse).toList());
        }
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Page<DataHolderGroup> pg;
        if (scope == null) {
            pg = dataHolderGroupRepository.search(cleanSearch(search), pageable);
        } else {
            pg = scope.isEmpty() ? Page.empty(pageable) : dataHolderGroupRepository.searchInIds(cleanSearch(search), scope, pageable);
        }
        return ResponseEntity.ok(pageResponse(pg, pg.getContent().stream().map(this::toGroupResponse).toList()));
    }

    @GetMapping("/data-holder-groups/{id}")
    public ResponseEntity<?> getDataHolderGroup(@PathVariable Long id, HttpServletRequest http) {
        requireGroupAccess(http, id);
        return dataHolderGroupRepository.findById(id)
                .map(g -> {
                    Map<String, Object> response = toGroupResponse(g);
                    // Count members via the join table (many-to-many)
                    long joinCount = dataHolderRepository.countByGroupMembership(g.getId());
                    // Fallback: also count legacy single-column memberships
                    long legacyCount = dataHolderRepository.countByDataHolderGroupId(g.getId());
                    response.put("dataHolderCount", Math.max(joinCount, legacyCount));
                    response.put("memberCount", userGroupMembershipRepository.findByDataHolderGroupId(g.getId()).size());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/data-holder-groups")
    public ResponseEntity<?> createDataHolderGroup(@RequestBody DataHolderGroupRequest request, HttpServletRequest http) {
        requireMaster(http);
        if (request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Name is required"));
        }
        if (dataHolderGroupRepository.existsByName(request.getName().trim())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "A group with this name already exists"));
        }
        DataHolderGroup group = DataHolderGroup.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .visibility(request.visibilityOrNull() != null ? request.visibilityOrNull() : Visibility.PUBLIC)
                .defaultContactFirstName(trimOrNull(request.getDefaultContactFirstName()))
                .defaultContactLastName(trimOrNull(request.getDefaultContactLastName()))
                .defaultContactEmail(trimOrNull(request.getDefaultContactEmail()))
                .defaultContactPhone(trimOrNull(request.getDefaultContactPhone()))
                .defaultAddress(trimOrNull(request.getDefaultAddress()))
                .defaultCity(trimOrNull(request.getDefaultCity()))
                .defaultStateProvince(trimOrNull(request.getDefaultStateProvince()))
                .defaultPostalCode(trimOrNull(request.getDefaultPostalCode()))
                .defaultCountry(trimOrNull(request.getDefaultCountry()))
                .build();
        group = dataHolderGroupRepository.save(group);

        audit.logCrud("CREATE", "DATA_HOLDER_GROUP", String.valueOf(group.getId()), group.getName(),
                "admin", "Created data holder group");

        return ResponseEntity.ok(Map.of("success", true, "dataHolderGroup", toGroupResponse(group)));
    }

    @PutMapping("/data-holder-groups/{id}")
    public ResponseEntity<?> updateDataHolderGroup(@PathVariable Long id, @RequestBody DataHolderGroupRequest request,
                                                   HttpServletRequest http) {
        requireGroupAccess(http, id);
        return dataHolderGroupRepository.findById(id)
                .map(group -> {
                    if (request.getName() != null) group.setName(request.getName().trim());
                    if (request.getDescription() != null) group.setDescription(request.getDescription());
                    Visibility requestedVisibility = request.visibilityOrNull();
                    if (requestedVisibility != null && requestedVisibility != group.getVisibility()) {
                        Visibility previous = group.getVisibility();
                        group.setVisibility(requestedVisibility);
                        audit.logCrud("VISIBILITY", "DATA_HOLDER_GROUP", String.valueOf(group.getId()),
                                group.getName(), "admin",
                                "Group visibility " + previous + " -> " + requestedVisibility
                                + "; this caps how far its templates are advertised");
                    }
                    if (request.getIsActive() != null) {
                        boolean wasActive = group.getIsActive();
                        group.setIsActive(request.getIsActive());
                        if (wasActive != request.getIsActive()) {
                            audit.logCrud(request.getIsActive() ? "ENABLE" : "DISABLE", "DATA_HOLDER_GROUP",
                                    String.valueOf(group.getId()), group.getName(), "admin",
                                    (request.getIsActive() ? "Enabled" : "Disabled") + " data holder group");
                        }
                    }
                    /* Contact details reach requestors through every template that inherits them,
                     * so a blank field clears rather than keeps the old value. */
                    if (request.getDefaultContactFirstName() != null) group.setDefaultContactFirstName(trimOrNull(request.getDefaultContactFirstName()));
                    if (request.getDefaultContactLastName() != null) group.setDefaultContactLastName(trimOrNull(request.getDefaultContactLastName()));
                    if (request.getDefaultContactEmail() != null) group.setDefaultContactEmail(trimOrNull(request.getDefaultContactEmail()));
                    if (request.getDefaultContactPhone() != null) group.setDefaultContactPhone(trimOrNull(request.getDefaultContactPhone()));
                    if (request.getDefaultAddress() != null) group.setDefaultAddress(trimOrNull(request.getDefaultAddress()));
                    if (request.getDefaultCity() != null) group.setDefaultCity(trimOrNull(request.getDefaultCity()));
                    if (request.getDefaultStateProvince() != null) group.setDefaultStateProvince(trimOrNull(request.getDefaultStateProvince()));
                    if (request.getDefaultPostalCode() != null) group.setDefaultPostalCode(trimOrNull(request.getDefaultPostalCode()));
                    if (request.getDefaultCountry() != null) group.setDefaultCountry(trimOrNull(request.getDefaultCountry()));
                    dataHolderGroupRepository.save(group);
                    audit.logCrud("UPDATE", "DATA_HOLDER_GROUP", String.valueOf(group.getId()), group.getName(),
                            "admin", "Updated data holder group");
                    return ResponseEntity.ok(Map.of("success", true, "dataHolderGroup", toGroupResponse(group)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/data-holder-groups/{id}")
    public ResponseEntity<?> deleteDataHolderGroup(@PathVariable Long id, HttpServletRequest http) {
        requireMaster(http);
        return dataHolderGroupRepository.findById(id)
                .map(group -> {
                    long dhCount = dataHolderRepository.countByDataHolderGroupId(id);
                    if (dhCount > 0) {
                        return ResponseEntity.badRequest().body(Map.of("success", false,
                                "error", "Cannot delete group with " + dhCount + " data holder(s). Reassign or remove them first."));
                    }
                    userGroupMembershipRepository.deleteByDataHolderGroupId(id);
                    dataHolderGroupRepository.delete(group);
                    audit.logCrud("DELETE", "DATA_HOLDER_GROUP", String.valueOf(id), group.getName(),
                            "admin", "Deleted data holder group and removed all memberships");
                    return ResponseEntity.ok(Map.of("success", true, "message", "Data holder group deleted"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Data Holder Group Memberships ====================

    @GetMapping("/data-holder-groups/{id}/members")
    public ResponseEntity<?> getGroupMembers(@PathVariable Long id, HttpServletRequest http) {
        requireGroupAccess(http, id);
        if (!dataHolderGroupRepository.existsById(id)) return ResponseEntity.notFound().build();
        var memberships = userGroupMembershipRepository.findByDataHolderGroupId(id);
        var members = memberships.stream().map(m -> {
            var user = userRepository.findById(m.getUserId()).orElse(null);
            if (user == null) return null;
            Map<String, Object> r = new HashMap<>();
            r.put("membershipId", m.getId());
            r.put("userId", user.getId());
            r.put("firstName", user.getFirstName());
            r.put("lastName", user.getLastName());
            r.put("email", user.getEmail());
            r.put("type", user.getType());
            r.put("typeLabel", user.getTypeLabel());
            r.put("joinedAt", m.getCreatedAt());
            return r;
        }).filter(r -> r != null).toList();
        return ResponseEntity.ok(members);
    }

    @PostMapping("/data-holder-groups/{id}/members")
    public ResponseEntity<?> addGroupMember(@PathVariable Long id, @RequestBody GroupMemberRequest request,
                                            HttpServletRequest http) {
        requireGroupAccess(http, id);
        if (!dataHolderGroupRepository.existsById(id)) return ResponseEntity.notFound().build();

        // Non-master users cannot add themselves to groups. Caller comes from the verified token.
        Long callerId = callerUser(http) != null ? callerUser(http).getId() : null;
        if (callerId != null && request.getUserId().equals(callerId) && callerType(http) != 1) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "error", "You cannot modify your own data holder group access"));
        }

        Set<Long> visible = visibleUserIds(http);
        var userOpt = userRepository.findById(request.getUserId());
        if (userOpt.isEmpty() || (visible != null && !visible.contains(request.getUserId()))) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "User not found"));
        }
        if (userGroupMembershipRepository.existsByUserIdAndDataHolderGroupId(request.getUserId(), id)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "User is already a member of this group"));
        }
        UserGroupMembership membership = UserGroupMembership.builder()
                .userId(request.getUserId())
                .dataHolderGroupId(id)
                .build();
        userGroupMembershipRepository.save(membership);
        audit.logCrud("ADD_MEMBER", "DATA_HOLDER_GROUP", String.valueOf(id),
                dataHolderGroupRepository.findById(id).map(DataHolderGroup::getName).orElse(""),
                "admin", "Added user " + userOpt.get().getEmail() + " to group");
        return ResponseEntity.ok(Map.of("success", true, "message", "Member added"));
    }

    @DeleteMapping("/data-holder-groups/{groupId}/members/{userId}")
    public ResponseEntity<?> removeGroupMember(@PathVariable Long groupId, @PathVariable Long userId,
                                                HttpServletRequest http) {
        requireGroupAccess(http, groupId);
        if (!dataHolderGroupRepository.existsById(groupId)) return ResponseEntity.notFound().build();

        // Non-master users cannot remove themselves from groups. Caller comes from the verified token.
        Long callerId = callerUser(http) != null ? callerUser(http).getId() : null;
        if (callerId != null && userId.equals(callerId) && callerType(http) != 1) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "error", "You cannot modify your own data holder group access"));
        }

        if (!userGroupMembershipRepository.existsByUserIdAndDataHolderGroupId(userId, groupId)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "User is not a member of this group"));
        }
        userGroupMembershipRepository.deleteByUserIdAndDataHolderGroupId(userId, groupId);
        audit.logCrud("REMOVE_MEMBER", "DATA_HOLDER_GROUP", String.valueOf(groupId),
                dataHolderGroupRepository.findById(groupId).map(DataHolderGroup::getName).orElse(""),
                callerId != null ? "user:" + callerId : "admin",
                "Removed user " + userId + " from group");
        return ResponseEntity.ok(Map.of("success", true, "message", "Member removed"));
    }

    /** Returns the groups a user belongs to (for non-master users) or all groups (for master). */
    @GetMapping("/users/{id}/groups")
    public ResponseEntity<?> getUserGroups(@PathVariable Long id, HttpServletRequest http) {
        Set<Long> visible = visibleUserIds(http);
        if (visible != null && !visible.contains(id)) return ResponseEntity.notFound().build();
        var userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
        User user = userOpt.get();
        if (user.getType() == 1) {
            // Master sees all groups
            return ResponseEntity.ok(dataHolderGroupRepository.findAll().stream().map(this::toGroupResponse).toList());
        }
        var memberships = userGroupMembershipRepository.findByUserId(id);
        var groups = memberships.stream()
                .map(m -> dataHolderGroupRepository.findById(m.getDataHolderGroupId()).orElse(null))
                .filter(g -> g != null)
                .map(this::toGroupResponse)
                .toList();
        return ResponseEntity.ok(groups);
    }

    private Map<String, Object> toGroupResponse(DataHolderGroup g) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", g.getId());
        r.put("name", g.getName());
        r.put("description", g.getDescription());
        r.put("isActive", g.getIsActive());
        r.put("visibility", g.getVisibility() != null ? g.getVisibility().name() : Visibility.PUBLIC.name());
        r.put("isPrivate", g.getIsPrivate());
        r.put("defaultContactFirstName", g.getDefaultContactFirstName());
        r.put("defaultContactLastName", g.getDefaultContactLastName());
        r.put("defaultContactEmail", g.getDefaultContactEmail());
        r.put("defaultContactPhone", g.getDefaultContactPhone());
        r.put("defaultAddress", g.getDefaultAddress());
        r.put("defaultCity", g.getDefaultCity());
        r.put("defaultStateProvince", g.getDefaultStateProvince());
        r.put("defaultPostalCode", g.getDefaultPostalCode());
        r.put("defaultCountry", g.getDefaultCountry());
        r.put("contact", mapper.groupContact(g));
        r.put("createdAt", g.getCreatedAt());
        r.put("updatedAt", g.getUpdatedAt());
        return r;
    }

    // ==================== Stats ====================

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(HttpServletRequest http) {
        Set<Long> scope = callerGroupIds(http);
        if (scope != null) return ResponseEntity.ok(scopedStats(http, scope));
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTemplates", templateRepository.count());
        long activeTemplates = templateRepository.findByIsActiveTrue().size();
        stats.put("activeTemplates", activeTemplates);
        stats.put("publishedTemplates", activeTemplates);
        stats.put("globallyPublishedTemplates", visibilityService.globallyPublishedTemplates().size());
        stats.put("totalSubscriptions", subscriptionRepository.count());
        stats.put("pendingSubscriptions", subscriptionRepository.countByStatus(SubscriptionStatus.PENDING));
        stats.put("approvedSubscriptions", subscriptionRepository.countByStatus(SubscriptionStatus.APPROVED));
        stats.put("testingSubscriptions", subscriptionRepository.countByStatus(SubscriptionStatus.TESTING));
        stats.put("activeSubscriptions", subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE));
        stats.put("suspendedSubscriptions", subscriptionRepository.countByStatus(SubscriptionStatus.SUSPENDED));
        stats.put("deniedSubscriptions", subscriptionRepository.countByStatus(SubscriptionStatus.DENIED));
        stats.put("totalDataHolders", dataHolderRepository.count());
        stats.put("activeDataHolders", dataHolderRepository.findByIsActiveTrue().size());
        stats.put("totalDataHolderGroups", dataHolderGroupRepository.count());
        stats.put("activeDataHolderGroups", dataHolderGroupRepository.findByIsActiveTrue().size());
        stats.put("totalUsers", userRepository.count());
        stats.put("totalInstances", instanceRepository.countByStatus("running") + instanceRepository.countByStatus("stopped"));
        stats.put("runningInstances", instanceRepository.countByStatus("running"));
        stats.put("stoppedInstances", instanceRepository.countByStatus("stopped"));
        return ResponseEntity.ok(stats);
    }

    /** Dashboard statistics limited to the given groups. */
    private Map<String, Object> scopedStats(HttpServletRequest http, Set<Long> scope) {
        Map<String, Object> stats = new HashMap<>();
        List<AgreementTemplate> templates = templateRepository.findAll().stream()
                .filter(t -> inScope(scope, t.getDataHolderGroupId())).toList();
        long activeTemplates = templates.stream().filter(t -> Boolean.TRUE.equals(t.getIsActive())).count();
        stats.put("totalTemplates", (long) templates.size());
        stats.put("activeTemplates", activeTemplates);
        stats.put("publishedTemplates", activeTemplates);
        stats.put("globallyPublishedTemplates", visibilityService.globallyPublishedTemplates().stream()
                .filter(t -> inScope(scope, t.getDataHolderGroupId())).count());
        List<AgreementSubscription> subs = subscriptionRepository.findAll().stream()
                .filter(s -> inScopeAny(scope, subscriptionGroupIds(s))).toList();
        stats.put("totalSubscriptions", (long) subs.size());
        stats.put("pendingSubscriptions", subs.stream().filter(s -> s.getStatus() == SubscriptionStatus.PENDING).count());
        stats.put("approvedSubscriptions", subs.stream().filter(s -> s.getStatus() == SubscriptionStatus.APPROVED).count());
        stats.put("testingSubscriptions", subs.stream().filter(s -> s.getStatus() == SubscriptionStatus.TESTING).count());
        stats.put("activeSubscriptions", subs.stream().filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE).count());
        stats.put("suspendedSubscriptions", subs.stream().filter(s -> s.getStatus() == SubscriptionStatus.SUSPENDED).count());
        stats.put("deniedSubscriptions", subs.stream().filter(s -> s.getStatus() == SubscriptionStatus.DENIED).count());
        List<DataHolder> holders = dataHolderRepository.findAll().stream()
                .filter(dh -> inScopeAny(scope, dh.getAllGroupIds())).toList();
        stats.put("totalDataHolders", (long) holders.size());
        stats.put("activeDataHolders", holders.stream().filter(dh -> Boolean.TRUE.equals(dh.getIsActive())).count());
        List<DataHolderGroup> groups = dataHolderGroupRepository.findAllById(scope);
        stats.put("totalDataHolderGroups", (long) groups.size());
        stats.put("activeDataHolderGroups", groups.stream().filter(g -> Boolean.TRUE.equals(g.getIsActive())).count());
        Set<Long> visible = visibleUserIds(http);
        stats.put("totalUsers", (long) (visible == null ? 0 : visible.size()));
        List<DataHolderInstance> instances = instanceRepository.findAll().stream()
                .filter(i -> inScopeAny(scope, i.getAllGroupIds())).toList();
        long running = instances.stream().filter(i -> "running".equals(i.getStatus())).count();
        long stopped = instances.stream().filter(i -> "stopped".equals(i.getStatus())).count();
        stats.put("totalInstances", running + stopped);
        stats.put("runningInstances", running);
        stats.put("stoppedInstances", stopped);
        return stats;
    }

    // ==================== Users ====================

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            HttpServletRequest http) {
        // Non-master callers only ever see users inside their own data holder groups.
        Set<Long> visible = visibleUserIds(http);

        if (page == null) {
            List<User> users = visible == null
                    ? userRepository.findAll()
                    : (visible.isEmpty() ? List.of() : userRepository.findByIdIn(visible));
            return ResponseEntity.ok(users.stream().map(this::toUserResponse).toList());
        }

        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Page<User> pg;
        if (visible == null) {
            pg = userRepository.search(cleanSearch(search), type, pageable);
        } else if (visible.isEmpty()) {
            pg = Page.empty(pageable);
        } else {
            pg = userRepository.searchByIds(visible, cleanSearch(search), type, pageable);
        }
        return ResponseEntity.ok(pageResponse(pg, pg.getContent().stream().map(this::toUserResponse).toList()));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id, HttpServletRequest http) {
        // Outside the caller's groups → 404 rather than 403, so the endpoint can't be used to
        // probe for the existence of users in other data holder groups.
        Set<Long> visible = visibleUserIds(http);
        if (visible != null && !visible.contains(id)) {
            return ResponseEntity.notFound().build();
        }
        return userRepository.findById(id)
                .map(u -> ResponseEntity.ok(toUserResponse(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody UserRequest request, HttpServletRequest http) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Email already exists"));
        }

        // Determine the caller's permissions from the VERIFIED token (not the request body).
        User caller = callerUser(http);
        int callerType = callerType(http);
        int targetType = request.getType() != null ? request.getType() : 3;

        // Callers can only create users with a lower privilege level (higher type number)
        if (targetType <= callerType) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "error", "You can only create users below your own access level"));
        }

        // Validate group assignments based on caller permissions
        if (request.getGroupIds() != null && targetType != 1) {
            if (callerType == 2 && caller != null) {
                // Admin: can only assign groups they themselves belong to
                var callerGroupIds = userGroupMembershipRepository.findByUserId(caller.getId())
                        .stream().map(UserGroupMembership::getDataHolderGroupId).toList();
                for (Long gid : request.getGroupIds()) {
                    if (!callerGroupIds.contains(gid)) {
                        return ResponseEntity.badRequest().body(Map.of("success", false,
                                "error", "You can only assign groups that you are a member of"));
                    }
                }
            }
            // Master: no restriction on group assignment
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail().trim().toLowerCase())
                .password(AuthController.hashPassword(request.getPassword() != null ? request.getPassword() : "changeme"))
                .type(targetType)
                .isActive(true)
                .build();
        user = userRepository.save(user);

        // Create group memberships for non-master users
        if (user.getType() != 1 && request.getGroupIds() != null) {
            for (Long groupId : request.getGroupIds()) {
                if (dataHolderGroupRepository.existsById(groupId)) {
                    UserGroupMembership membership = UserGroupMembership.builder()
                            .userId(user.getId())
                            .dataHolderGroupId(groupId)
                            .build();
                    userGroupMembershipRepository.save(membership);
                }
            }
        }

        audit.logCrud("CREATE", "USER", String.valueOf(user.getId()), user.getEmail(),
                caller != null ? caller.getEmail() : "admin",
                "Created user: " + user.getFullName() + " (type=" + user.getTypeLabel() + ")"
                        + (request.getGroupIds() != null ? " with " + request.getGroupIds().size() + " group(s)" : ""));
        return ResponseEntity.ok(Map.of("success", true, "user", toUserResponse(user)));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UserRequest request,
                                        HttpServletRequest http) {
        // Determine the caller's permissions from the VERIFIED token (not the request body).
        User caller = callerUser(http);
        int callerType = callerType(http);

        return userRepository.findById(id)
                .map(user -> {
                    boolean isSelfEdit = caller != null && user.getId().equals(caller.getId());
                    boolean isMaster = callerType == 1;

                    /* Master can edit anyone including themselves. A non-master may edit their own
                     * account (self-service — limited to email/password below) or users strictly
                     * below their own level, and only inside their own data holder groups. */
                    if (!isMaster) {
                        Set<Long> visible = visibleUserIds(http);
                        if (visible != null && !visible.contains(user.getId())) {
                            return ResponseEntity.status(404).body(Map.of("success", false,
                                    "error", "User not found"));
                        }
                        if (!isSelfEdit && user.getType() <= callerType) {
                            return ResponseEntity.badRequest().body(Map.of("success", false,
                                    "error", "You can only edit users below your own access level"));
                        }
                    }
                    // Non-master users cannot modify their own group assignments
                    if (isSelfEdit && !isMaster && request.getGroupIds() != null) {
                        return ResponseEntity.badRequest().body(Map.of("success", false,
                                "error", "You cannot modify your own data holder group access"));
                    }
                    // Cannot promote a user to the caller's level or above (only when it changes,
                    // so re-submitting an unchanged type never trips this).
                    if (request.getType() != null && !request.getType().equals(user.getType())
                            && request.getType() <= callerType) {
                        return ResponseEntity.badRequest().body(Map.of("success", false,
                                "error", "You cannot promote a user to your own access level or above"));
                    }

                    /* Self-service scope: a non-master editing their own account may change only
                     * their email and password. Name/type/status stay under admin control. */
                    boolean selfServiceOnly = isSelfEdit && !isMaster;
                    if (selfServiceOnly && request.getIsActive() != null
                            && !request.getIsActive().equals(user.getIsActive())) {
                        return ResponseEntity.badRequest().body(Map.of("success", false,
                                "error", "You cannot change your own account status"));
                    }

                    StringBuilder changes = new StringBuilder();
                    if (!selfServiceOnly && request.getFirstName() != null) { user.setFirstName(request.getFirstName()); changes.append("firstName "); }
                    if (!selfServiceOnly && request.getLastName() != null) { user.setLastName(request.getLastName()); changes.append("lastName "); }
                    if (request.getEmail() != null) {
                        String newEmail = request.getEmail().trim().toLowerCase();
                        if (!newEmail.equals(user.getEmail())) {
                            // An email address may only be changed by its owner or by a master.
                            if (!isSelfEdit && !isMaster) {
                                return ResponseEntity.badRequest().body(Map.of("success", false,
                                        "error", "Only the account owner or a master account can change an email address"));
                            }
                            user.setEmail(newEmail);
                            changes.append("email ");
                        }
                    }
                    if (!selfServiceOnly && request.getType() != null) { user.setType(request.getType()); changes.append("type "); }
                    if (!selfServiceOnly && request.getIsActive() != null) {
                        boolean wasActive = user.getIsActive();
                        user.setIsActive(request.getIsActive());
                        if (wasActive != request.getIsActive()) {
                            changes.append(request.getIsActive() ? "activated " : "deactivated ");
                        }
                    }
                    if (request.getPassword() != null && !request.getPassword().isBlank()) {
                        user.setPassword(AuthController.hashPassword(request.getPassword()));
                        changes.append("password ");
                    }
                    userRepository.save(user);

                    // Sync group memberships for non-master users
                    if (request.getGroupIds() != null && user.getType() != 1) {
                        if (callerType == 2 && caller != null) {
                            // Admin caller: only manage groups within the admin's own scope
                            var callerGroupIds = userGroupMembershipRepository.findByUserId(caller.getId())
                                    .stream().map(UserGroupMembership::getDataHolderGroupId).toList();

                            // Validate: all requested groups must be within caller's scope
                            for (Long gid : request.getGroupIds()) {
                                if (!callerGroupIds.contains(gid)) {
                                    return ResponseEntity.badRequest().body(Map.of("success", false,
                                            "error", "You can only assign groups that you are a member of"));
                                }
                            }

                            // Remove only memberships within the admin's scope, preserve others
                            var existingMemberships = userGroupMembershipRepository.findByUserId(user.getId());
                            for (var m : existingMemberships) {
                                if (callerGroupIds.contains(m.getDataHolderGroupId())) {
                                    userGroupMembershipRepository.deleteByUserIdAndDataHolderGroupId(
                                            user.getId(), m.getDataHolderGroupId());
                                }
                                // Memberships outside caller's scope are untouched
                            }

                            // Add the requested memberships (all within caller's scope, already validated)
                            for (Long groupId : request.getGroupIds()) {
                                if (dataHolderGroupRepository.existsById(groupId)
                                        && !userGroupMembershipRepository.existsByUserIdAndDataHolderGroupId(user.getId(), groupId)) {
                                    UserGroupMembership membership = UserGroupMembership.builder()
                                            .userId(user.getId())
                                            .dataHolderGroupId(groupId)
                                            .build();
                                    userGroupMembershipRepository.save(membership);
                                }
                            }
                        } else {
                            // Master caller: full sync — remove all, recreate
                            var existing = userGroupMembershipRepository.findByUserId(user.getId());
                            for (var m : existing) {
                                userGroupMembershipRepository.deleteByUserIdAndDataHolderGroupId(
                                        user.getId(), m.getDataHolderGroupId());
                            }
                            for (Long groupId : request.getGroupIds()) {
                                if (dataHolderGroupRepository.existsById(groupId)) {
                                    UserGroupMembership membership = UserGroupMembership.builder()
                                            .userId(user.getId())
                                            .dataHolderGroupId(groupId)
                                            .build();
                                    userGroupMembershipRepository.save(membership);
                                }
                            }
                        }
                        changes.append("groups ");
                    }

                    audit.logCrud("UPDATE", "USER", String.valueOf(user.getId()), user.getEmail(),
                            caller != null ? caller.getEmail() : "admin",
                            "Updated user: " + changes.toString().trim());
                    return ResponseEntity.ok(Map.of("success", true, "user", toUserResponse(user)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id, HttpServletRequest http) {
        Set<Long> visible = visibleUserIds(http);
        if (visible != null && !visible.contains(id)) return ResponseEntity.notFound().build();
        var userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();
        User user = userOpt.get();
        if (callerType(http) != 1 && user.getType() <= callerType(http)) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "error", "You can only delete users below your own access level"));
        }
        // Remove all group memberships
        var memberships = userGroupMembershipRepository.findByUserId(id);
        for (var m : memberships) {
            userGroupMembershipRepository.deleteByUserIdAndDataHolderGroupId(id, m.getDataHolderGroupId());
        }
        userRepository.deleteById(id);
        audit.logCrud("DELETE", "USER", String.valueOf(id), user.getEmail(),
                "admin", "Deleted user: " + user.getFullName());
        return ResponseEntity.ok(Map.of("success", true, "message", "User deleted"));
    }

    private Map<String, Object> toUserResponse(User user) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", user.getId());
        r.put("firstName", user.getFirstName());
        r.put("lastName", user.getLastName());
        r.put("email", user.getEmail());
        r.put("type", user.getType());
        r.put("typeLabel", user.getTypeLabel());
        r.put("isActive", user.getIsActive());
        r.put("createdAt", user.getCreatedAt());
        r.put("updatedAt", user.getUpdatedAt());

        // Include group memberships
        if (user.getType() == 1) {
            // Master implicitly has all groups
            r.put("groups", dataHolderGroupRepository.findAll().stream().map(g -> {
                Map<String, Object> gm = new HashMap<>();
                gm.put("id", g.getId());
                gm.put("name", g.getName());
                return gm;
            }).toList());
            r.put("groupIds", dataHolderGroupRepository.findAll().stream().map(DataHolderGroup::getId).toList());
        } else {
            var memberships = userGroupMembershipRepository.findByUserId(user.getId());
            r.put("groups", memberships.stream()
                    .map(m -> dataHolderGroupRepository.findById(m.getDataHolderGroupId()).orElse(null))
                    .filter(g -> g != null)
                    .map(g -> {
                        Map<String, Object> gm = new HashMap<>();
                        gm.put("id", g.getId());
                        gm.put("name", g.getName());
                        return gm;
                    }).toList());
            r.put("groupIds", memberships.stream().map(UserGroupMembership::getDataHolderGroupId).toList());
        }

        return r;
    }

    // ==================== Helper ====================

    /** Rejects duplicate request-type names (case-insensitive, trimmed); returns an error message or null. */
    private String validateUniqueTypeNames(List<RequestTypeRequest> requestTypes) {
        if (requestTypes == null || requestTypes.size() < 2) return null;
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (RequestTypeRequest rt : requestTypes) {
            if (rt.getName() != null) {
                String key = rt.getName().trim().toLowerCase();
                if (!key.isEmpty() && !seen.add(key)) {
                    duplicates.add(rt.getName().trim());
                }
            }
        }
        if (!duplicates.isEmpty()) {
            return "Duplicate request type name(s): " + String.join(", ", duplicates)
                    + ". Each request type must have a unique name within a template.";
        }
        return null;
    }

    /**
     * True if the given name (case-insensitive, trimmed) already belongs to a
     * request type on this template, excluding the request type with excludeRtId.
     */
    private boolean requestTypeNameConflicts(AgreementTemplate template, String name, Long excludeRtId) {
        if (name == null || name.isBlank()) return false;
        String trimmed = name.trim();
        return template.getRequestTypes().stream()
                .filter(r -> excludeRtId == null || !excludeRtId.equals(r.getId()))
                .anyMatch(r -> r.getName() != null && r.getName().trim().equalsIgnoreCase(trimmed));
    }

    /**
     * Give every request type a globally unique hashed type code, keeping a client-supplied one
     * only when it is well-formed and unclaimed.
     */
    private void assignTypeCodes(AgreementTemplate template, List<AgreementRequestType> requestTypes) {
        Set<String> used = new HashSet<>();
        for (AgreementRequestType rt : requestTypes) {
            String supplied = rt.getTypeCode();
            if (RequestTypeCodeGenerator.isValid(supplied)
                    && used.add(supplied)
                    && !requestTypeRepository.existsByTypeCode(supplied)) {
                continue;
            }
            if (supplied != null) {
                log.warn("Discarding supplied type code '{}' on request type '{}' — malformed or already claimed",
                        supplied, rt.getName());
                used.remove(supplied);
            }
            String code = typeCodeGenerator.generate(templateRef(template), rt.getName(), used);
            rt.setTypeCode(code);
            used.add(code);
        }
    }

    /** A fresh globally-unique hashed type code for one new request type. */
    private String newTypeCode(AgreementTemplate template, String name) {
        return typeCodeGenerator.generate(templateRef(template), name);
    }

    /** Seeds the type code digest: the template's business id, else its primary key. */
    private String templateRef(AgreementTemplate template) {
        if (template == null) return "";
        if (template.getTemplateId() != null) return template.getTemplateId();
        return template.getId() != null ? String.valueOf(template.getId()) : "";
    }

    /** Parse the wire value for a request type kind, defaulting to RDAP on anything unrecognised. */
    private RequestTypeKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) return RequestTypeKind.RDAP;
        try {
            return RequestTypeKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown request type kind '{}', defaulting to RDAP", raw);
            return RequestTypeKind.RDAP;
        }
    }

    private AgreementRequestType buildRequestType(RequestTypeRequest req) {
        RequestTypeKind kind = parseKind(req.getKind());
        AgreementRequestType rt = AgreementRequestType.builder()
                .name(req.getName())
                .typeCode(req.getTypeCode())
                .description(req.getDescription())
                .kind(kind)
                .rdrsDefaults(kind == RequestTypeKind.RDRS ? req.getRdrsDefaults() : null)
                .accessLevel(req.getAccessLevel() != null ? req.getAccessLevel() : 0)
                .supportsConfidential(req.getSupportsConfidential() != null ? req.getSupportsConfidential() : false)
                .supportsExigent(req.getSupportsExigent() != null ? req.getSupportsExigent() : false)
                .requiresManualApproval(req.getRequiresManualApproval() != null ? req.getRequiresManualApproval() : true)
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                .isActive(req.getIsActive() != null ? req.getIsActive() : true)
                .queryValueRegex(req.getQueryValueRegex())
                .queryValueRegexError(req.getQueryValueRegexError())
                .build();
        // RDAP parameters are meaningless for an RDRS type, which never reaches a data holder.
        if (req.getRdapParameters() != null && kind != RequestTypeKind.RDRS) {
            rt.setRdapParameters(mapper.buildRdapParameters(req.getRdapParameters()));
        }
        if (req.getCustomParameters() != null) {
            for (CustomParameterRequest cpReq : req.getCustomParameters()) {
                rt.addCustomParameter(buildCustomParameter(cpReq));
            }
        }
        return rt;
    }

    private RequestTypeCustomParameter buildCustomParameter(CustomParameterRequest req) {
        return RequestTypeCustomParameter.builder()
                .name(req.getName())
                .dataType(req.getDataType() != null ? req.getDataType() : "string")
                .required(req.getRequired() != null ? req.getRequired() : false)
                .description(req.getDescription())
                .defaultValue(req.getDefaultValue())
                .placeholder(req.getPlaceholder())
                .enumValues(req.getEnumValues())
                .validationRegex(req.getValidationRegex())
                .minValue(req.getMinValue())
                .maxValue(req.getMaxValue())
                .maxLength(req.getMaxLength())
                .allowedFileTypes(req.getAllowedFileTypes())
                .maxFileSizeMb(req.getMaxFileSizeMb())
                .fileCriteria(req.getFileCriteria())
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                .build();
    }

    // ==================== Request DTOs ====================

    @Data public static class CreateTemplateRequest {
        private String name; private String shortDescription; private String description;
        private String requiredGroupTypes;
        private List<LegalSectionRequest> legalSections;
        private List<SubscriptionFieldRequest> subscriptionFields;
        private Integer maxQueriesPerDay; private Integer maxQueriesPerMonth;
        /** isPublished is the old name for isActive; either may be sent. */
        private Boolean isActive; private Boolean isPublished; private String createdBy;
        private Long dataHolderGroupId;
        /** PRIVATE, PUBLIC or GLOBAL; defaults to PUBLIC. */
        private String visibility;
        private String disclosureMode;
        private List<RequestTypeRequest> requestTypes;

        /** Contact shown to requestors; null useGroupContact means the group's. */
        private Boolean useGroupContact;
        private String contactFirstName; private String contactLastName;
        private String contactEmail; private String contactPhone; private String contactAddress;
        private String contactCity; private String contactStateProvince;
        private String contactPostalCode; private String contactCountry;

        Boolean activeFlag() { return isActive != null ? isActive : isPublished; }
    }

    @Data public static class UpdateTemplateRequest {
        private String name; private String shortDescription; private String description;
        private String requiredGroupTypes;
        private List<LegalSectionRequest> legalSections;
        private List<SubscriptionFieldRequest> subscriptionFields;
        private Integer maxQueriesPerDay; private Integer maxQueriesPerMonth;
        /** isPublished is the old name for isActive; either may be sent. */
        private Boolean isActive; private Boolean isPublished;
        private Long dataHolderGroupId;
        /** PRIVATE, PUBLIC or GLOBAL. */
        private String visibility;
        private String disclosureMode;
        private List<RequestTypeRequest> requestTypes;

        /** Contact shown to requestors; null useGroupContact leaves the current setting. */
        private Boolean useGroupContact;
        private String contactFirstName; private String contactLastName;
        private String contactEmail; private String contactPhone; private String contactAddress;
        private String contactCity; private String contactStateProvince;
        private String contactPostalCode; private String contactCountry;

        Boolean activeFlag() { return isActive != null ? isActive : isPublished; }
    }

    /*
     * A template that answers for its own contact must at least say where to write; a
     * template on the group's contact is the group's problem to keep filled in, and is
     * flagged in the UI rather than blocked here so that a group can be set up in any order.
     */
    private String validateContact(AgreementTemplate template) {
        if (!template.hasOwnContact()) return null;
        if (template.getContactEmail() == null || template.getContactEmail().isBlank()) {
            return "A contact email is required when this agreement uses its own contact information.";
        }
        return null;
    }

    private static final Pattern LEGAL_REFERENCE = Pattern.compile("\\{\\{ref:([A-Za-z0-9_.-]+)\\}\\}");

    /** Replace the template's legal sections with the supplied list, keeping the given order. */
    private void replaceLegalSections(AgreementTemplate template, List<LegalSectionRequest> sections) {
        template.getLegalSections().clear();
        if (sections == null) return;

        List<LegalSectionRequest> accepted = new ArrayList<>();
        for (LegalSectionRequest req : sections) {
            if (req.getTitle() != null && !req.getTitle().isBlank()) accepted.add(req);
        }

        Set<String> usedKeys = new LinkedHashSet<>();
        List<String> sectionKeys = new ArrayList<>();
        List<List<Map<String, Object>>> sectionClauses = new ArrayList<>();
        Map<String, String> refLabels = new HashMap<>();

        for (int i = 0; i < accepted.size(); i++) {
            String sectionKey = uniqueRefKey(accepted.get(i).getRefKey(), "section-" + (i + 1), usedKeys);
            sectionKeys.add(sectionKey);
            refLabels.put(sectionKey, String.valueOf(i + 1));

            List<Map<String, Object>> clauses = normalizeClauses(accepted.get(i).getClauses(), sectionKey, usedKeys);
            sectionClauses.add(clauses);
            for (int j = 0; j < clauses.size(); j++) {
                refLabels.put(String.valueOf(clauses.get(j).get("refKey")), (i + 1) + "." + (j + 1));
            }
        }

        for (int i = 0; i < accepted.size(); i++) {
            LegalSectionRequest req = accepted.get(i);
            List<Map<String, Object>> clauses = sectionClauses.get(i);
            template.addLegalSection(AgreementLegalSection.builder()
                    .refKey(sectionKeys.get(i))
                    .title(req.getTitle().trim())
                    .clauses(clauses.isEmpty() ? null : clauses)
                    .body(clauses.isEmpty()
                            ? resolveLegalReferences(req.getBody(), refLabels)
                            : renderClauseBody(clauses, i + 1, refLabels))
                    .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : i)
                    .build());
        }
    }

    private String uniqueRefKey(String requested, String fallback, Set<String> used) {
        String base = requested != null ? requested.trim().replaceAll("[^A-Za-z0-9_.-]", "") : "";
        if (base.isEmpty()) base = fallback;
        String key = base;
        int suffix = 2;
        while (!used.add(key)) {
            key = base + "-" + suffix;
            suffix++;
        }
        return key;
    }

    private List<Map<String, Object>> normalizeClauses(List<Map<String, Object>> clauses, String sectionKey,
                                                      Set<String> used) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (clauses == null) return normalized;
        for (Map<String, Object> clause : clauses) {
            if (clause == null) continue;
            Object rawText = clause.get("text");
            String text = rawText != null ? rawText.toString() : "";
            if (text.isBlank()) continue;
            Object rawKey = clause.get("refKey");
            String key = uniqueRefKey(rawKey != null ? rawKey.toString() : null,
                    sectionKey + "-c" + (normalized.size() + 1), used);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("refKey", key);
            entry.put("text", text);
            normalized.add(entry);
        }
        return normalized;
    }

    private String renderClauseBody(List<Map<String, Object>> clauses, int sectionNumber,
                                    Map<String, String> refLabels) {
        StringBuilder rendered = new StringBuilder();
        for (int j = 0; j < clauses.size(); j++) {
            if (j > 0) rendered.append("\n\n");
            rendered.append(sectionNumber).append('.').append(j + 1).append(' ')
                    .append(resolveLegalReferences(String.valueOf(clauses.get(j).get("text")), refLabels));
        }
        return rendered.toString();
    }

    private String resolveLegalReferences(String text, Map<String, String> refLabels) {
        if (text == null || text.isEmpty()) return text;
        Matcher matcher = LEGAL_REFERENCE.matcher(text);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String label = refLabels.get(matcher.group(1));
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(label != null ? label : matcher.group(1)));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    /** Replace the template's subscription fields; returns an error message when names collide. */
    private String replaceSubscriptionFields(AgreementTemplate template, List<SubscriptionFieldRequest> fields) {
        template.getSubscriptionFields().clear();
        if (fields == null) return null;
        Set<String> seen = new HashSet<>();
        int order = 0;
        for (SubscriptionFieldRequest req : fields) {
            if (req.getName() == null || req.getName().isBlank()) continue;
            String name = req.getName().trim();
            if (!seen.add(name.toLowerCase())) {
                return "Duplicate subscription field name '" + name + "'. Each field must have a unique name.";
            }
            template.addSubscriptionField(TemplateSubscriptionField.builder()
                    .name(name)
                    .dataType(req.getDataType() != null ? req.getDataType() : "string")
                    .required(req.getRequired() != null ? req.getRequired() : false)
                    .description(req.getDescription())
                    .defaultValue(req.getDefaultValue())
                    .placeholder(req.getPlaceholder())
                    .enumValues(req.getEnumValues())
                    .validationRegex(req.getValidationRegex())
                    .minValue(req.getMinValue())
                    .maxValue(req.getMaxValue())
                    .maxLength(req.getMaxLength())
                    .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : order)
                    .build());
            order++;
        }
        return null;
    }

    @Data public static class LegalSectionRequest {
        private String refKey; private String title; private String body; private Integer sortOrder;
        private List<Map<String, Object>> clauses;
    }

    @Data public static class SubscriptionFieldRequest {
        private String name; private String dataType; private Boolean required;
        private String description; private String defaultValue; private String placeholder;
        private String enumValues; private String validationRegex;
        private String minValue; private String maxValue; private Integer maxLength;
        private Integer sortOrder;
    }

    @Data public static class RequestTypeRequest {
        private String name; private String typeCode; private String description;
        private Integer accessLevel; private Boolean supportsConfidential; private Boolean supportsExigent;
        private Boolean requiresManualApproval;
        private Integer sortOrder; private Boolean isActive; private Map<String, Boolean> rdapParameters;
        private List<CustomParameterRequest> customParameters;
        private String queryValueRegex; private String queryValueRegexError;
        /** "RDAP" (default) or "RDRS". */
        private String kind;
        private Map<String, Object> rdrsDefaults;
    }

    @Data public static class CustomParameterRequest {
        private String name;
        private String dataType;
        private Boolean required;
        private String description;
        private String defaultValue;
        private String placeholder;
        private String enumValues;
        private String validationRegex;
        private String minValue;
        private String maxValue;
        private Integer maxLength;
        private String allowedFileTypes;
        private Integer maxFileSizeMb;
        private Map<String, Object> fileCriteria;
        private Integer sortOrder;
    }

    @Data public static class ReviewRequest {
        private String reviewedBy; private String notes;
    }

    @Data public static class ActionRequest {
        private String initiatedBy; private String notes;
    }

    @Data public static class TestResultRequest {
        private String result; private String details;
    }

    @Data public static class DataHolderRequest {
        private String dataholderId; private String name; private String url;
        private String description; private String contactEmail; private String callbackUrl;
        private Boolean isActive; private Long dataHolderGroupId;
        /** New: list of group IDs for multi-group membership. If provided, takes precedence over dataHolderGroupId. */
        private java.util.List<Long> dataHolderGroupIds;
    }

    @Data public static class DataHolderGroupRequest {
        private String name; private String description; private Boolean isActive;
        /** PRIVATE, PUBLIC or GLOBAL. isPrivate is the old boolean and still accepted. */
        private String visibility; private Boolean isPrivate;

        /** Administrator contact, inherited by every template that does not override it. */
        private String defaultContactFirstName; private String defaultContactLastName;
        private String defaultContactEmail; private String defaultContactPhone;
        private String defaultAddress; private String defaultCity; private String defaultStateProvince;
        private String defaultPostalCode; private String defaultCountry;

        /** The requested level, reading the legacy boolean when no level was sent. */
        Visibility visibilityOrNull() {
            if (visibility != null && !visibility.isBlank()) {
                return Visibility.parse(visibility, null);
            }
            if (isPrivate != null) return isPrivate ? Visibility.PRIVATE : Visibility.PUBLIC;
            return null;
        }
    }

    @Data public static class CopyTemplateRequest {
        /** The group the copy will belong to; it can never be moved afterwards. */
        private Long dataHolderGroupId;
        /** Optional name for the copy; defaults to the source's name with "(copy)". */
        private String name;
    }

    @Data public static class VisibilityRequest {
        private String visibility;
    }

    @Data public static class GroupMemberRequest {
        private Long userId;
        private Long callerUserId;
    }

    @Data public static class UserRequest {
        private String firstName; private String lastName; private String email;
        private String password; private Integer type; private Boolean isActive;
        private List<Long> groupIds;
        private Long callerUserId;
    }

    // ==================== Requestor Groups ====================

    @GetMapping("/requestor-groups")
    public ResponseEntity<?> getAllRequestorGroups(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long dataHolderGroupId,
            @RequestParam(required = false) RequestorGroup.RegistrationStatus status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            HttpServletRequest http) {
        Set<Long> scope = callerGroupIds(http);
        if (page == null) {
            return ResponseEntity.ok(requestorGroupRepository.findAll().stream()
                    .filter(rg -> inScope(scope, rg.getDataHolderGroupId()))
                    .map(this::toRgResponse).toList());
        }
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Page<RequestorGroup> pg;
        if (scope == null) {
            pg = requestorGroupRepository.search(cleanSearch(search), dataHolderGroupId, status, pageable);
        } else {
            Set<Long> in = pageScope(scope, dataHolderGroupId);
            pg = in.isEmpty() ? Page.empty(pageable)
                    : requestorGroupRepository.searchInGroups(cleanSearch(search), in, status, pageable);
        }
        return ResponseEntity.ok(pageResponse(pg, pg.getContent().stream().map(this::toRgResponse).toList()));
    }

    @GetMapping("/requestor-groups/pending")
    public ResponseEntity<?> getPendingRequestorGroups(HttpServletRequest http) {
        Set<Long> scope = callerGroupIds(http);
        return ResponseEntity.ok(requestorGroupRepository
                .findByStatus(RequestorGroup.RegistrationStatus.PENDING).stream()
                .filter(rg -> inScope(scope, rg.getDataHolderGroupId()))
                .map(this::toRgResponse).toList());
    }

    @GetMapping("/requestor-groups/{id}")
    public ResponseEntity<?> getRequestorGroup(@PathVariable Long id, HttpServletRequest http) {
        return requestorGroupRepository.findById(id)
                .map(rg -> {
                    requireGroupAccess(http, rg.getDataHolderGroupId());
                    Map<String, Object> response = toRgResponse(rg);
                    rgCredentialRepository.findByRequestorGroupIdAndIsActiveTrue(rg.getId())
                            .ifPresent(cred -> {
                                response.put("clientId", cred.getClientId());
                                response.put("hasCredentials", true);
                            });
                    if (!response.containsKey("hasCredentials")) response.put("hasCredentials", false);
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/requestor-groups/{id}/accept")
    public ResponseEntity<?> acceptRequestorGroup(@PathVariable Long id, @RequestBody(required = false) ReviewRequest request,
                                                  HttpServletRequest http) {
        return requestorGroupRepository.findById(id)
                .map(rg -> {
                    requireGroupAccess(http, rg.getDataHolderGroupId());
                    rg.setStatus(RequestorGroup.RegistrationStatus.ACCEPTED);
                    rg.setIsActive(true);
                    rg.setReviewedAt(java.time.LocalDateTime.now());
                    if (request != null) {
                        rg.setReviewedBy(request.getReviewedBy());
                        rg.setReviewNotes(request.getNotes());
                    }
                    requestorGroupRepository.save(rg);

                    /* The secret is shown to the admin exactly once here; only its BCrypt
                     * hash is persisted, matching the data holder credential flow. */
                    RequestorGroupCredential cred = RequestorGroupCredential.generate(rg.getId(), rg.getCode());
                    String plaintextSecret = cred.hashSecretForStorage();
                    cred = rgCredentialRepository.save(cred);
                    log.info("Accepted requestor group {} and generated credentials: clientId={}", rg.getCode(), cred.getClientId());

                    audit.logWorkflow("ACCEPT", "REQUESTOR_GROUP", rg.getCode(), rg.getName(),
                            request != null ? request.getReviewedBy() : "admin",
                            "Accepted requestor group and generated credentials");

                    return ResponseEntity.ok(Map.of(
                            "success", true, "requestorGroup", toRgResponse(rg),
                            "clientId", cred.getClientId(), "clientSecret", plaintextSecret
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/requestor-groups/{id}/deny")
    public ResponseEntity<?> denyRequestorGroup(@PathVariable Long id, @RequestBody(required = false) ReviewRequest request,
                                                HttpServletRequest http) {
        return requestorGroupRepository.findById(id)
                .map(rg -> {
                    requireGroupAccess(http, rg.getDataHolderGroupId());
                    rg.setStatus(RequestorGroup.RegistrationStatus.DENIED);
                    rg.setIsActive(false);
                    rg.setReviewedAt(java.time.LocalDateTime.now());
                    if (request != null) {
                        rg.setReviewedBy(request.getReviewedBy());
                        rg.setReviewNotes(request.getNotes());
                    }
                    requestorGroupRepository.save(rg);
                    rgCredentialRepository.findByRequestorGroupId(rg.getId())
                            .forEach(c -> { c.setIsActive(false); rgCredentialRepository.save(c); });

                    audit.logWorkflow("DENY", "REQUESTOR_GROUP", rg.getCode(), rg.getName(),
                            request != null ? request.getReviewedBy() : "admin",
                            "Denied requestor group. Reason: " + (request != null ? request.getNotes() : ""));

                    return ResponseEntity.ok(Map.of("success", true, "message", "Requestor group denied"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/requestor-groups/{id}")
    public ResponseEntity<?> updateRequestorGroup(@PathVariable Long id, @RequestBody RequestorGroupRequest request,
                                                  HttpServletRequest http) {
        return requestorGroupRepository.findById(id)
                .map(rg -> {
                    requireGroupAccess(http, rg.getDataHolderGroupId());
                    if (request.getDataHolderGroupId() != null) requireGroupAccess(http, request.getDataHolderGroupId());
                    if (request.getName() != null) rg.setName(request.getName());
                    if (request.getDescription() != null) rg.setDescription(request.getDescription());
                    if (request.getGroupType() != null) rg.setGroupType(request.getGroupType());
                    if (request.getContactName() != null) rg.setContactName(request.getContactName());
                    if (request.getContactEmail() != null) rg.setContactEmail(request.getContactEmail());
                    if (request.getOrganization() != null) rg.setOrganization(request.getOrganization());
                    if (request.getDataHolderGroupId() != null) rg.setDataHolderGroupId(request.getDataHolderGroupId());
                    if (request.getIsActive() != null) {
                        boolean wasActive = rg.getIsActive();
                        rg.setIsActive(request.getIsActive());
                        if (wasActive != request.getIsActive()) {
                            audit.logCrud(request.getIsActive() ? "ENABLE" : "DISABLE", "REQUESTOR_GROUP",
                                    rg.getCode(), rg.getName(), "admin",
                                    (request.getIsActive() ? "Enabled" : "Disabled") + " requestor group");
                        }
                    }
                    requestorGroupRepository.save(rg);
                    audit.logCrud("UPDATE", "REQUESTOR_GROUP", rg.getCode(), rg.getName(), "admin", "Updated requestor group");
                    return ResponseEntity.ok(Map.of("success", true, "requestorGroup", toRgResponse(rg)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/requestor-groups/{id}")
    public ResponseEntity<?> deleteRequestorGroup(@PathVariable Long id, HttpServletRequest http) {
        return requestorGroupRepository.findById(id)
                .map(rg -> {
                    requireGroupAccess(http, rg.getDataHolderGroupId());
                    rgCredentialRepository.findByRequestorGroupId(rg.getId())
                            .forEach(c -> { c.setIsActive(false); rgCredentialRepository.save(c); });
                    requestorGroupRepository.delete(rg);
                    log.info("Deleted requestor group {} ({})", rg.getCode(), rg.getName());
                    audit.logCrud("DELETE", "REQUESTOR_GROUP", rg.getCode(), rg.getName(),
                            "admin", "Deleted requestor group and deactivated credentials");
                    return ResponseEntity.ok(Map.of("success", true, "message", "Requestor group deleted"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Requestor Group Credentials ====================

    @PostMapping("/requestor-groups/{id}/reveal-credentials")
    public ResponseEntity<?> revealRgCredentials(@PathVariable Long id, HttpServletRequest http) {
        return requestorGroupRepository.findById(id)
                .map(rg -> {
                    requireGroupAccess(http, rg.getDataHolderGroupId());
                    var credOpt = rgCredentialRepository.findByRequestorGroupIdAndIsActiveTrue(rg.getId());
                    if (credOpt.isEmpty()) {
                        return ResponseEntity.ok(Map.of("success", false, "error", "No active credentials found"));
                    }
                    RequestorGroupCredential cred = credOpt.get();
                    log.info("Admin viewed credential clientId for requestor group {}: clientId={}", rg.getCode(), cred.getClientId());
                    audit.logCredential("VIEW", "REQUESTOR_GROUP", rg.getCode(), rg.getName(),
                            "admin", "Viewed credential clientId=" + cred.getClientId());
                    return ResponseEntity.ok(Map.of(
                            "success", true, "clientId", cred.getClientId(),
                            "requestorGroupName", rg.getName(), "requestorGroupCode", rg.getCode(),
                            "secretAvailable", false,
                            "message", "For security, the client secret is only shown once when generated. "
                                    + "Use \"Regenerate credentials\" to issue a new secret."
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/requestor-groups/{id}/regenerate-credentials")
    public ResponseEntity<?> regenerateRgCredentials(@PathVariable Long id, HttpServletRequest http) {
        return requestorGroupRepository.findById(id)
                .map(rg -> {
                    requireGroupAccess(http, rg.getDataHolderGroupId());
                    rgCredentialRepository.findByRequestorGroupId(rg.getId())
                            .forEach(c -> { c.setIsActive(false); rgCredentialRepository.save(c); });
                    RequestorGroupCredential cred = RequestorGroupCredential.generate(rg.getId(), rg.getCode());
                    String plaintextSecret = cred.hashSecretForStorage();
                    cred = rgCredentialRepository.save(cred);
                    log.info("Regenerated credentials for requestor group {}: clientId={}", rg.getCode(), cred.getClientId());
                    audit.logCredential("REGENERATE", "REQUESTOR_GROUP", rg.getCode(), rg.getName(),
                            "admin", "Regenerated credentials. New clientId=" + cred.getClientId());
                    return ResponseEntity.ok(Map.of(
                            "success", true, "clientId", cred.getClientId(), "clientSecret", plaintextSecret,
                            "message", "New credentials generated"
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toRgResponse(RequestorGroup rg) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", rg.getId()); r.put("name", rg.getName()); r.put("code", rg.getCode());
        r.put("description", rg.getDescription()); r.put("groupType", rg.getGroupType());
        r.put("contactName", rg.getContactName()); r.put("contactEmail", rg.getContactEmail());
        r.put("organization", rg.getOrganization()); r.put("reasonForAccess", rg.getReasonForAccess());
        r.put("status", rg.getStatus().name()); r.put("isActive", rg.getIsActive());
        r.put("reviewedBy", rg.getReviewedBy()); r.put("reviewedAt", rg.getReviewedAt());
        r.put("reviewNotes", rg.getReviewNotes()); r.put("createdAt", rg.getCreatedAt());
        r.put("updatedAt", rg.getUpdatedAt()); r.put("dataHolderGroupId", rg.getDataHolderGroupId());
        return r;
    }

    @Data public static class RequestorGroupRequest {
        private String name; private String code; private String description;
        private String groupType; private String contactName; private String contactEmail;
        private String contactPhone; private String organization;
        private String address; private String city; private String stateProvince;
        private String postalCode; private String country;
        private String reasonForAccess; private Boolean isActive; private Long dataHolderGroupId;
    }

    // ==================== Audit Logs ====================

    @GetMapping("/audit-logs")
    public ResponseEntity<?> getAuditLogs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String performedBy,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest http) {
        requireMaster(http); // audit entries span every group

        Page<AuditLog> logs = auditLogRepository.findFiltered(
                category, entityType, action, performedBy, result, search,
                PageRequest.of(page, Math.min(size, 200)));

        Map<String, Object> response = new HashMap<>();
        response.put("content", logs.getContent().stream().map(this::toAuditLogResponse).toList());
        response.put("totalElements", logs.getTotalElements());
        response.put("totalPages", logs.getTotalPages());
        response.put("page", logs.getNumber());
        response.put("size", logs.getSize());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/audit-logs/stats")
    public ResponseEntity<?> getAuditLogStats(HttpServletRequest http) {
        requireMaster(http);
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", auditLogRepository.count());
        stats.put("authEvents", auditLogRepository.countByCategory("AUTH"));
        stats.put("crudEvents", auditLogRepository.countByCategory("CRUD"));
        stats.put("workflowEvents", auditLogRepository.countByCategory("WORKFLOW"));
        stats.put("credentialEvents", auditLogRepository.countByCategory("CREDENTIAL"));
        stats.put("apiEvents", auditLogRepository.countByCategory("API"));
        stats.put("failureCount", auditLogRepository.countByResult("FAILURE"));
        stats.put("last24h", auditLogRepository.countSince(java.time.LocalDateTime.now().minusHours(24)));
        stats.put("actors", auditLogRepository.findDistinctPerformedBy());
        stats.put("actions", auditLogRepository.findDistinctActions());
        return ResponseEntity.ok(stats);
    }

    private Map<String, Object> toAuditLogResponse(AuditLog auditLog) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", auditLog.getId()); r.put("category", auditLog.getCategory());
        r.put("action", auditLog.getAction()); r.put("entityType", auditLog.getEntityType());
        r.put("entityId", auditLog.getEntityId()); r.put("entityName", auditLog.getEntityName());
        r.put("performedBy", auditLog.getPerformedBy()); r.put("source", auditLog.getSource());
        r.put("result", auditLog.getResult()); r.put("details", auditLog.getDetails());
        r.put("ipAddress", auditLog.getIpAddress()); r.put("createdAt", auditLog.getCreatedAt());
        return r;
    }

    @Data public static class UpdateIntrospectionUrlRequest {
        private String introspectionUrl;
    }

    // ==================== Data Holder Instances (Provisioning) ====================

    @GetMapping("/instances")
    public ResponseEntity<?> listInstances(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long dataHolderGroupId,
            HttpServletRequest http) {
        requireMaster(http); // instances are infrastructure, managed only by a master
        List<com.jaddar.dhgroupadmin.entity.DataHolderInstance> all = provisioningService.listInstances();
        if (page == null) {
            return ResponseEntity.ok(all.stream().map(this::toInstanceResponse).toList());
        }
        /* Instances come from the provisioning service (not a JPA Page) and are few, so we
         * filter/search/sort in memory and slice the requested page while keeping the same
         * response shape as the JPA-backed endpoints. */
        String s = cleanSearch(search);
        List<com.jaddar.dhgroupadmin.entity.DataHolderInstance> filtered = all.stream()
                .filter(i -> dataHolderGroupId == null || i.getAllGroupIds().contains(dataHolderGroupId))
                .filter(i -> s == null
                        || (i.getName() != null && i.getName().toLowerCase().contains(s.toLowerCase()))
                        || (i.getSubdomain() != null && i.getSubdomain().toLowerCase().contains(s.toLowerCase()))
                        || (i.getDataholderId() != null && i.getDataholderId().toLowerCase().contains(s.toLowerCase())))
                .sorted(java.util.Comparator.comparing(
                        com.jaddar.dhgroupadmin.entity.DataHolderInstance::getName,
                        java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        int pageSize = Math.min(Math.max(1, size), 200);
        int pageIdx = Math.max(0, page);
        int total = filtered.size();
        int totalPages = (int) Math.ceil((double) total / pageSize);
        int from = Math.min(pageIdx * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> content = filtered.subList(from, to).stream().map(this::toInstanceResponse).toList();
        Map<String, Object> r = new HashMap<>();
        r.put("content", content);
        r.put("totalElements", (long) total);
        r.put("totalPages", totalPages);
        r.put("page", pageIdx);
        r.put("size", pageSize);
        return ResponseEntity.ok(r);
    }

    @GetMapping("/instances/{subdomain}")
    public ResponseEntity<?> getInstance(@PathVariable String subdomain, HttpServletRequest http) {
        requireMaster(http);
        return provisioningService.findBySubdomain(subdomain)
                .map(i -> ResponseEntity.ok(toInstanceResponse(i)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/instances/{subdomain}/live-status")
    public ResponseEntity<?> getInstanceLiveStatus(@PathVariable String subdomain, HttpServletRequest http) {
        requireMaster(http);
        try {
            Map<String, Object> status = provisioningService.getLiveStatus(subdomain);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get live status for instance {}", subdomain, e);
            return ResponseEntity.ok(Map.of("success", false, "error", "Could not retrieve the instance status."));
        }
    }

    @PostMapping("/instances")
    public ResponseEntity<?> provisionInstance(@RequestBody ProvisionInstanceRequest request, HttpServletRequest http) {
        requireMaster(http);
        try {
            var instance = provisioningService.createInstance(
                    request.getName(),
                    request.getSubdomain(),
                    request.getDataholderId(),
                    request.getDataHolderGroupId(),
                    request.getDataHolderGroupIds(),
                    request.getAdminPassword()
            );
            audit.logCrud("CREATE", "INSTANCE", instance.getSubdomain(), instance.getName(),
                    "admin", "Provisioned data holder instance at " + instance.getUrl());
            return ResponseEntity.ok(Map.of("success", true, "instance", toInstanceResponse(instance)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Provisioning failed for subdomain {}", request.getSubdomain(), e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "Provisioning failed. Please try again or contact support."));
        }
    }

    @PostMapping("/instances/{subdomain}/stop")
    public ResponseEntity<?> stopInstance(@PathVariable String subdomain, HttpServletRequest http) {
        requireMaster(http);
        try {
            var instance = provisioningService.stopInstance(subdomain);
            audit.logCrud("STOP", "INSTANCE", subdomain, instance.getName(), "admin", "Stopped instance");
            return ResponseEntity.ok(Map.of("success", true, "instance", toInstanceResponse(instance)));
        } catch (Exception e) {
            log.error("Failed to stop instance {}", subdomain, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Could not stop the instance."));
        }
    }

    @PostMapping("/instances/{subdomain}/start")
    public ResponseEntity<?> startInstance(@PathVariable String subdomain, HttpServletRequest http) {
        requireMaster(http);
        try {
            var instance = provisioningService.startInstance(subdomain);
            audit.logCrud("START", "INSTANCE", subdomain, instance.getName(), "admin", "Started instance");
            return ResponseEntity.ok(Map.of("success", true, "instance", toInstanceResponse(instance)));
        } catch (Exception e) {
            log.error("Failed to start instance {}", subdomain, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Could not start the instance."));
        }
    }

    @DeleteMapping("/instances/{subdomain}")
    public ResponseEntity<?> teardownInstance(@PathVariable String subdomain, HttpServletRequest http) {
        requireMaster(http);
        try {
            var instance = provisioningService.findBySubdomain(subdomain).orElse(null);
            String name = instance != null ? instance.getName() : subdomain;
            provisioningService.teardownInstance(subdomain);
            audit.logCrud("TEARDOWN", "INSTANCE", subdomain, name, "admin", "Full teardown of instance and data");
            return ResponseEntity.ok(Map.of("success", true, "message", "Instance " + subdomain + " torn down"));
        } catch (Exception e) {
            log.error("Failed to tear down instance {}", subdomain, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Could not tear down the instance."));
        }
    }

    @PostMapping("/instances/{subdomain}/update")
    public ResponseEntity<?> updateInstance(@PathVariable String subdomain, @RequestBody(required = false) UpdateInstanceRequest request,
                                            HttpServletRequest http) {
        requireMaster(http);
        try {
            var instance = provisioningService.updateInstance(
                    subdomain,
                    request != null ? request.getBackendImage() : null,
                    request != null ? request.getFrontendImage() : null
            );
            audit.logCrud("UPDATE", "INSTANCE", subdomain, instance.getName(), "admin", "Updated instance containers");
            return ResponseEntity.ok(Map.of("success", true, "instance", toInstanceResponse(instance)));
        } catch (Exception e) {
            log.error("Failed to update instance {}", subdomain, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Could not update the instance."));
        }
    }

    @PostMapping("/instances/update-all")
    public ResponseEntity<?> updateAllInstances(@RequestBody(required = false) UpdateInstanceRequest request,
                                                HttpServletRequest http) {
        requireMaster(http);
        var instances = provisioningService.listInstances().stream()
                .filter(i -> "running".equals(i.getStatus()))
                .toList();
        List<Map<String, Object>> results = new java.util.ArrayList<>();
        for (var inst : instances) {
            try {
                var updated = provisioningService.updateInstance(
                        inst.getSubdomain(),
                        request != null ? request.getBackendImage() : null,
                        request != null ? request.getFrontendImage() : null
                );
                results.add(Map.of("subdomain", inst.getSubdomain(), "success", true));
            } catch (Exception e) {
                log.error("Failed to update instance {} during batch update", inst.getSubdomain(), e);
                results.add(Map.of("subdomain", inst.getSubdomain(), "success", false, "error", "Could not update the instance."));
            }
        }
        audit.logCrud("UPDATE_ALL", "INSTANCE", "all", "All instances",
                "admin", "Batch update of " + instances.size() + " instances");
        return ResponseEntity.ok(Map.of("success", true, "results", results));
    }

    private Map<String, Object> toInstanceResponse(com.jaddar.dhgroupadmin.entity.DataHolderInstance i) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", i.getId());
        r.put("subdomain", i.getSubdomain());
        r.put("name", i.getName());
        r.put("dataholderId", i.getDataholderId());
        r.put("dataHolderGroupId", i.getDataHolderGroupId());
        // Multi-group fields
        r.put("dataHolderGroupIds", i.getAllGroupIds());
        java.util.List<Map<String, Object>> groupList = new java.util.ArrayList<>();
        if (i.getDataHolderGroups() != null) {
            i.getDataHolderGroups().forEach(g -> {
                Map<String, Object> gm = new HashMap<>();
                gm.put("id", g.getId());
                gm.put("name", g.getName());
                groupList.add(gm);
            });
        }
        if (groupList.isEmpty() && i.getDataHolderGroupId() != null) {
            groupList.add(Map.of("id", i.getDataHolderGroupId(), "name", ""));
        }
        r.put("dataHolderGroups", groupList);
        r.put("backendPort", i.getBackendPort());
        r.put("frontendPort", i.getFrontendPort());
        r.put("backendContainer", i.getBackendContainer());
        r.put("frontendContainer", i.getFrontendContainer());
        r.put("databaseName", i.getDatabaseName());
        r.put("adminUsername", i.getAdminUsername());
        // adminPassword intentionally excluded from API responses
        r.put("status", i.getStatus());
        r.put("url", i.getUrl());
        r.put("backendImage", i.getBackendImage());
        r.put("frontendImage", i.getFrontendImage());
        r.put("createdAt", i.getCreatedAt());
        r.put("updatedAt", i.getUpdatedAt());
        return r;
    }

    @Data public static class ProvisionInstanceRequest {
        private String name;
        private String subdomain;
        private String dataholderId;
        private Long dataHolderGroupId;
        /** New: list of group IDs. If provided, takes precedence over dataHolderGroupId. */
        private java.util.List<Long> dataHolderGroupIds;
        private String adminPassword;
    }

    @Data public static class UpdateInstanceRequest {
        private String backendImage;
        private String frontendImage;
    }
}