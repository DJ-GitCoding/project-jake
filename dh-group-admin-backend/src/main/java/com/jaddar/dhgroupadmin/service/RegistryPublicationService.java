/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.service;

import com.jaddar.dhgroupadmin.entity.AgreementLegalSection;
import com.jaddar.dhgroupadmin.entity.AgreementRequestType;
import com.jaddar.dhgroupadmin.entity.AgreementTemplate;
import com.jaddar.dhgroupadmin.entity.DataHolderGroup;
import com.jaddar.dhgroupadmin.entity.RegistryPublicationSettings;
import com.jaddar.dhgroupadmin.entity.RequestTypeCustomParameter;
import com.jaddar.dhgroupadmin.entity.RequestTypeKind;
import com.jaddar.dhgroupadmin.entity.TemplateSubscriptionField;
import com.jaddar.dhgroupadmin.entity.Visibility;
import com.jaddar.dhgroupadmin.repository.RegistryPublicationSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Announces this group admin to a common repository, the central node a Requestor Manager pings when it goes looking for data holder groups. */
@Slf4j
@Service
public class RegistryPublicationService {
    /** Where the repository takes announcements, relative to the configured base URL. */
    private static final String ANNOUNCE_PATH = "/api/public/group-registries/announce";

    private final RegistryPublicationSettingsRepository settingsRepository;
    private final TemplateVisibilityService visibilityService;
    private final ResponseMapper mapper;
    private final RestTemplate restTemplate;
    private final AuditService audit;

    public RegistryPublicationService(RegistryPublicationSettingsRepository settingsRepository,
                                      TemplateVisibilityService visibilityService,
                                      ResponseMapper mapper,
                                      AuditService audit,
                                      @Value("${registry.publication.timeout-seconds:10}") long timeoutSeconds) {
        this.settingsRepository = settingsRepository;
        this.visibilityService = visibilityService;
        this.mapper = mapper;
        this.audit = audit;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeoutSeconds * 1000);
        factory.setReadTimeout((int) timeoutSeconds * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** The single settings row, created on first access. */
    @Transactional
    public RegistryPublicationSettings getSettings() {
        return settingsRepository.findById(1L).orElseGet(() -> {
            RegistryPublicationSettings fresh = new RegistryPublicationSettings();
            fresh.setId(1L);
            fresh.setEnabled(false);
            return settingsRepository.save(fresh);
        });
    }

    /** Updates the publication settings. */
    @Transactional
    public RegistryPublicationSettings updateSettings(RegistryPublicationSettings incoming, String actor) {
        RegistryPublicationSettings settings = getSettings();

        String url = normalizeUrl(incoming.getRegistryUrl());
        String code = trimToNull(incoming.getRegistryCode());
        boolean wantEnabled = incoming.getEnabled() != null
                ? incoming.getEnabled() : Boolean.TRUE.equals(settings.getEnabled());

        if (wantEnabled) {
            if (url == null) {
                throw new IllegalArgumentException(
                        "Enter the common repository URL before turning publication on — for example "
                        + "https://registry.example.org/dataholder-registry");
            }
            if (code == null) {
                throw new IllegalArgumentException(
                        "Enter the code this registry should be listed under.");
            }
            if (normalizeUrl(incoming.getPublicBaseUrl()) == null) {
                throw new IllegalArgumentException(
                        "Enter the public API base URL a requestor manager should call — for example "
                        + "https://groups.example.org/dh-group-admin");
            }
        }

        boolean identityChanged = !Objects.equals(url, settings.getRegistryUrl())
                || !Objects.equals(code, settings.getRegistryCode());

        settings.setEnabled(wantEnabled);
        settings.setRegistryUrl(url);
        settings.setRegistryCode(code);
        settings.setRegistryName(trimToNull(incoming.getRegistryName()));
        settings.setRegistryDescription(trimToNull(incoming.getRegistryDescription()));
        settings.setPublicBaseUrl(normalizeUrl(incoming.getPublicBaseUrl()));
        settings.setContactEmail(trimToNull(incoming.getContactEmail()));
        settings.setUpdatedBy(actor);

        if (identityChanged) {
            settings.setAnnounceToken(null);
            settings.setRegistrationStatus("NOT_REGISTERED");
            settings.setLastAnnounceStatus(null);
            settings.setLastAnnounceMessage(null);
        }

        RegistryPublicationSettings saved = settingsRepository.save(settings);
        audit.logCrud("UPDATE", "REGISTRY_PUBLICATION", "1",
                saved.getRegistryCode() != null ? saved.getRegistryCode() : "common-repository", actor,
                (wantEnabled ? "Enabled" : "Disabled") + " publication to the common repository"
                + (url != null ? " at " + url : ""));
        return saved;
    }

    /** Sends this deployment's entry to the repository and records the outcome. */
    @Transactional
    public RegistryPublicationSettings announce(boolean manual) {
        RegistryPublicationSettings settings = getSettings();
        if (!settings.isReady()) {
            settings.setLastAnnounceStatus("FAILURE");
            settings.setLastAnnounceMessage(
                    "Publication is turned off, or the repository URL, group code or public base URL is missing.");
            settings.setLastAnnouncedAt(Instant.now());
            return settingsRepository.save(settings);
        }

        List<Map<String, Object>> groups = globalGroupPayload();
        int templateCount = countTemplates(groups);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", settings.getRegistryCode());
        body.put("name", settings.getRegistryName() != null
                ? settings.getRegistryName() : settings.getRegistryCode());
        body.put("description", settings.getRegistryDescription());
        body.put("baseUrl", settings.getPublicBaseUrl());
        body.put("contactEmail", settings.getContactEmail());
        body.put("dataHolderGroups", groups);

        String url = settings.getRegistryUrl() + ANNOUNCE_PATH;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (settings.getAnnounceToken() != null && !settings.getAnnounceToken().isBlank()) {
                headers.setBearerAuth(settings.getAnnounceToken());
            }

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>)
                    restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);

            Map<String, Object> payload = response.getBody();
            if (payload == null) {
                throw new IllegalStateException("The common repository returned an empty response.");
            }

            Object issued = payload.get("announceToken");
            if (issued instanceof String token && !token.isBlank()) {
                settings.setAnnounceToken(token);
            }

            String status = payload.get("status") instanceof String st ? st : "PENDING";
            settings.setRegistrationStatus(status);
            settings.setLastAnnounceStatus("SUCCESS");
            settings.setLastAnnounceMessage(describe(status, groups.size(), templateCount,
                    payload.get("message") instanceof String m ? m : null));
            settings.setLastAnnouncedAt(Instant.now());
            settings.setPublishedGroupCount(groups.size());
            settings.setPublishedTemplateCount(templateCount);

            if (manual) {
                log.info("Announced to the common repository at {} — {} group(s), {} template(s), status {}",
                        url, groups.size(), templateCount, status);
                audit.logCrud("ANNOUNCE", "REGISTRY_PUBLICATION", "1", settings.getRegistryCode(),
                        settings.getUpdatedBy() != null ? settings.getUpdatedBy() : "admin",
                        "Announced " + groups.size() + " data holder group(s) carrying "
                        + templateCount + " globally published template(s); repository says " + status);
            }
            return settingsRepository.save(settings);

        } catch (Exception e) {
            log.warn("Failed to announce to the common repository at {}: {}", url, e.toString());
            settings.setLastAnnounceStatus("FAILURE");
            settings.setLastAnnounceMessage(friendlyFailure(e));
            settings.setLastAnnouncedAt(Instant.now());
            return settingsRepository.save(settings);
        }
    }

    /** This registry's data holder groups as the repository stores them: one entry per GLOBAL group, each carrying its globally published templates. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> globalGroupPayload() {
        Map<Long, DataHolderGroup> groupsById = visibilityService.groupsById();

        Map<Long, List<Map<String, Object>>> templatesByGroup = new LinkedHashMap<>();
        for (AgreementTemplate template : visibilityService.globallyPublishedTemplates()) {
            Long groupId = template.getDataHolderGroupId();
            if (groupId == null) continue;
            templatesByGroup.computeIfAbsent(groupId, k -> new ArrayList<>())
                    .add(toTemplateRecord(template, groupsById.get(groupId)));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        groupsById.values().stream()
                .filter(group -> Boolean.TRUE.equals(group.getIsActive()))
                .filter(group -> group.getVisibility() == Visibility.GLOBAL)
                .sorted(java.util.Comparator.comparing(DataHolderGroup::getName,
                        java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
                .forEach(group -> {
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("groupId", group.getId());
                    record.put("name", group.getName());
                    record.put("description", group.getDescription());
                    record.put("templates", templatesByGroup.getOrDefault(group.getId(), List.of()));
                    out.add(record);
                });
        return out;
    }

    /** One template, reduced to what the repository publishes. */
    private Map<String, Object> toTemplateRecord(AgreementTemplate template, DataHolderGroup group) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("templateId", template.getTemplateId());
        record.put("agreementCode", template.getAgreementCode());
        record.put("name", template.getName());
        record.put("shortDescription", template.getShortDescription());
        record.put("description", template.getDescription());
        record.put("requiredGroupTypes", template.getRequiredGroupTypes());
        record.put("highestAccessLevel", template.getHighestAccessLevel());
        record.put("supportsConfidential", template.supportsConfidential());
        record.put("supportsExigent", template.supportsExigent());
        record.put("disclosureMode", template.getDisclosureMode());
        record.put("maxQueriesPerDay", template.getMaxQueriesPerDay());
        record.put("maxQueriesPerMonth", template.getMaxQueriesPerMonth());
        record.put("contact", mapper.contactFor(template, group));
        record.put("requestTypes", template.getActiveRequestTypes().stream()
                .map(this::toRequestTypeRecord).toList());
        record.put("subscriptionFields", template.getSubscriptionFields().stream()
                .map(this::toSubscriptionFieldRecord).toList());
        record.put("legalSectionTitles", template.getLegalSections().stream()
                .map(AgreementLegalSection::getTitle)
                .filter(java.util.Objects::nonNull)
                .toList());
        record.put("updatedAt", template.getUpdatedAt() != null ? template.getUpdatedAt().toString() : null);
        return record;
    }

    /** One active request type, with the parameters a requestor would have to supply. */
    private Map<String, Object> toRequestTypeRecord(AgreementRequestType rt) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", rt.getName());
        record.put("typeCode", rt.getTypeCode());
        record.put("description", rt.getDescription());
        record.put("kind", rt.getKind() != null ? rt.getKind().name() : RequestTypeKind.RDAP.name());
        record.put("accessLevel", rt.getAccessLevel());
        record.put("supportsConfidential", rt.getSupportsConfidential());
        record.put("supportsExigent", rt.getSupportsExigent());
        record.put("requiresManualApproval", rt.getRequiresManualApproval());
        record.put("queryValueRegex", rt.getQueryValueRegex());
        record.put("sortOrder", rt.getSortOrder());
        record.put("customParameters", rt.getCustomParameters().stream()
                .map(this::toCustomParameterRecord).toList());
        return record;
    }

    /** One custom parameter, as a requestor would have to fill it in. */
    private Map<String, Object> toCustomParameterRecord(RequestTypeCustomParameter p) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", p.getName());
        record.put("dataType", p.getDataType());
        record.put("required", p.getRequired());
        record.put("description", p.getDescription());
        record.put("placeholder", p.getPlaceholder());
        record.put("enumValues", p.getEnumValues());
        record.put("validationRegex", p.getValidationRegex());
        record.put("minValue", p.getMinValue());
        record.put("maxValue", p.getMaxValue());
        record.put("maxLength", p.getMaxLength());
        record.put("allowedFileTypes", p.getAllowedFileTypes());
        record.put("maxFileSizeMb", p.getMaxFileSizeMb());
        record.put("sortOrder", p.getSortOrder());
        return record;
    }

    /** One value a requestor must supply when subscribing. */
    private Map<String, Object> toSubscriptionFieldRecord(TemplateSubscriptionField f) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("name", f.getName());
        record.put("dataType", f.getDataType());
        record.put("required", f.getRequired());
        record.put("description", f.getDescription());
        record.put("sortOrder", f.getSortOrder());
        return record;
    }

    /** Templates across every group in a payload. */
    public static int countTemplates(List<Map<String, Object>> groups) {
        int total = 0;
        for (Map<String, Object> group : groups) {
            if (group.get("templates") instanceof List<?> templates) total += templates.size();
        }
        return total;
    }

    /** How the last announcement should read in the UI. */
    private String describe(String status, int groupCount, int templateCount, String repositoryMessage) {
        String templatePart = "Published "
                + groupCount + (groupCount == 1 ? " data holder group" : " data holder groups")
                + " carrying "
                + templateCount + (templateCount == 1 ? " template." : " templates.");
        String statusPart = switch (status == null ? "" : status.toUpperCase()) {
            case "APPROVED" -> "Listed in the common repository.";
            case "PENDING" -> "Recorded — waiting for the repository's administrators to approve the listing.";
            case "REJECTED" -> "The repository's administrators have rejected this listing.";
            case "SUSPENDED" -> "The repository's administrators have suspended this listing.";
            default -> "The repository reports status " + status + ".";
        };
        return repositoryMessage != null && !repositoryMessage.isBlank()
                ? templatePart + " " + statusPart + " (" + repositoryMessage + ")"
                : templatePart + " " + statusPart;
    }

    /** Turns a failed announcement into something an admin can act on. */
    private String friendlyFailure(Exception e) {
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (detail.length() > 300) detail = detail.substring(0, 300) + "…";
        if (e instanceof org.springframework.web.client.HttpClientErrorException.Unauthorized) {
            return "The common repository rejected our announce token. Clear the repository URL and save "
                    + "again to register afresh, or ask its administrators to reset the entry.";
        }
        if (e instanceof org.springframework.web.client.HttpClientErrorException.Conflict) {
            return "Another deployment already claims this registry code at that repository. "
                    + "Choose a different code.";
        }
        if (e instanceof org.springframework.web.client.HttpClientErrorException.NotFound) {
            return "The common repository URL does not serve the announcement endpoint. It should be "
                    + "the repository application's base URL — the part before /registry — not the feed "
                    + "URL or the address of its admin UI. (" + detail + ")";
        }
        if (e instanceof org.springframework.web.client.ResourceAccessException) {
            return "Could not reach the common repository at that URL. This is called by the backend, "
                    + "not by your browser, so the URL has to resolve from the server: a hostname the "
                    + "backend can see, and the repository API's port rather than the admin UI's. In the "
                    + "default compose stack that is http://dataholder-registry-backend:8084/dataholder-registry. ("
                    + detail + ")";
        }
        return "Could not reach the common repository. Check the URL is right and the host is reachable. ("
                + detail + ")";
    }

    private static String normalizeUrl(String url) {
        String trimmed = trimToNull(url);
        if (trimmed == null) return null;
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("URLs must start with http:// or https://");
        }
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Visibility levels, for the settings screen's explanatory copy. */
    public static List<String> visibilityLevels() {
        return List.of(Visibility.PRIVATE.name(), Visibility.PUBLIC.name(), Visibility.GLOBAL.name());
    }
}
