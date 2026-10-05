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
import com.jaddar.dhgroupadmin.entity.RequestTypeCustomParameter;
import com.jaddar.dhgroupadmin.entity.TemplateSubscriptionField;
import com.jaddar.dhgroupadmin.entity.TemplateUserField;
import com.jaddar.dhgroupadmin.entity.Visibility;
import com.jaddar.dhgroupadmin.repository.AgreementTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateCopyService {

    private final AgreementTemplateRepository templateRepository;
    private final TemplateVisibilityService visibilityService;
    private final ResponseMapper mapper;
    private final RequestTypeCodeGenerator typeCodeGenerator;
    private final AgreementCodeGenerator agreementCodeGenerator;

    /** Everything {@code groupId} may start a copy from, its own work included. */
    @Transactional(readOnly = true)
    public List<AgreementTemplate> copyableFor(Long groupId) {
        Map<Long, DataHolderGroup> groups = visibilityService.groupsById();
        return templateRepository.findAll().stream()
                .filter(template -> isCopyable(template, groupId, groups))
                .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                        a.getName() == null ? "" : a.getName(),
                        b.getName() == null ? "" : b.getName()))
                .toList();
    }

    /** Whether {@code targetGroupId} may copy this template. */
    @Transactional(readOnly = true)
    public boolean canCopy(AgreementTemplate source, Long targetGroupId) {
        return isCopyable(source, targetGroupId, visibilityService.groupsById());
    }

    private boolean isCopyable(AgreementTemplate template, Long targetGroupId,
                               Map<Long, DataHolderGroup> groups) {
        if (template == null || targetGroupId == null) return false;

        // A group's own agreements are its own business, published or not.
        if (targetGroupId.equals(template.getDataHolderGroupId())) return true;

        // Someone else's is only offered for reuse while it is actually being offered.
        if (!Boolean.TRUE.equals(template.getIsActive())) return false;
        return visibilityService.effectiveVisibility(template, groups).atLeast(Visibility.PUBLIC);
    }

    /**
     * Copy {@code source} into {@code targetGroupId}. The caller has already established that the
     * group may copy it and that the caller administers that group.
     */
    @Transactional
    public AgreementTemplate copy(AgreementTemplate source, Long targetGroupId, String newName, String createdBy) {
        AgreementTemplate copy = AgreementTemplate.builder()
                .name(newName != null && !newName.isBlank() ? newName.trim() : source.getName() + " (copy)")
                .shortDescription(source.getShortDescription())
                .description(source.getDescription())
                .requiredGroupTypes(source.getRequiredGroupTypes())
                .disclosureMode(source.getDisclosureMode())
                .maxQueriesPerDay(source.getMaxQueriesPerDay())
                .maxQueriesPerMonth(source.getMaxQueriesPerMonth())
                .dataHolderGroupId(targetGroupId)
                .createdBy(createdBy)
                /* The copy answers to its new group: its contact, and nothing advertised until
                 * whoever took it has read it through. */
                .useGroupContact(true)
                .visibility(Visibility.PRIVATE)
                .isActive(false)
                .build();

        for (AgreementLegalSection section : source.getLegalSections()) {
            copy.addLegalSection(AgreementLegalSection.builder()
                    .refKey(section.getRefKey())
                    .title(section.getTitle())
                    .body(section.getBody())
                    .clauses(copyClauses(section.getClauses()))
                    .sortOrder(section.getSortOrder())
                    .build());
        }

        for (TemplateSubscriptionField field : source.getSubscriptionFields()) {
            copy.addSubscriptionField(TemplateSubscriptionField.builder()
                    .name(field.getName())
                    .dataType(field.getDataType())
                    .required(field.getRequired())
                    .description(field.getDescription())
                    .defaultValue(field.getDefaultValue())
                    .placeholder(field.getPlaceholder())
                    .enumValues(field.getEnumValues())
                    .validationRegex(field.getValidationRegex())
                    .minValue(field.getMinValue())
                    .maxValue(field.getMaxValue())
                    .maxLength(field.getMaxLength())
                    .sortOrder(field.getSortOrder())
                    .build());
        }

        for (TemplateUserField field : source.getUserFields()) {
            copy.addUserField(TemplateUserField.builder()
                    .key(field.getKey())
                    .label(field.getLabel())
                    .description(field.getDescription())
                    .standard(field.getStandard())
                    .sortOrder(field.getSortOrder())
                    .build());
        }

        /* Codes identify a request type across the whole deployment, so the copy mints its own
         * rather than answering to the source's. */
        Set<String> reserved = new HashSet<>();
        for (AgreementRequestType rt : source.getRequestTypes()) {
            String typeCode = typeCodeGenerator.generate(source.getTemplateId(), rt.getName(), reserved);
            reserved.add(typeCode);

            AgreementRequestType copiedType = AgreementRequestType.builder()
                    .name(rt.getName())
                    .typeCode(typeCode)
                    .kind(rt.getKind())
                    .rdrsDefaults(rt.getRdrsDefaults() == null ? null : new LinkedHashMap<>(rt.getRdrsDefaults()))
                    .description(rt.getDescription())
                    .accessLevel(rt.getAccessLevel())
                    .supportsConfidential(rt.getSupportsConfidential())
                    .supportsExigent(rt.getSupportsExigent())
                    .requiresManualApproval(rt.getRequiresManualApproval())
                    .queryValueRegex(rt.getQueryValueRegex())
                    .queryValueRegexError(rt.getQueryValueRegexError())
                    .sortOrder(rt.getSortOrder())
                    .isActive(rt.getIsActive())
                    .build();

            if (rt.getRdapParameters() != null) {
                // Round-tripped through the mapper so every field travels without listing them twice.
                copiedType.setRdapParameters(
                        mapper.buildRdapParameters(mapper.toRdapParametersMap(rt.getRdapParameters())));
            }

            for (RequestTypeCustomParameter param : rt.getCustomParameters()) {
                copiedType.addCustomParameter(RequestTypeCustomParameter.builder()
                        .name(param.getName())
                        .dataType(param.getDataType())
                        .required(param.getRequired())
                        .description(param.getDescription())
                        .defaultValue(param.getDefaultValue())
                        .placeholder(param.getPlaceholder())
                        .enumValues(param.getEnumValues())
                        .validationRegex(param.getValidationRegex())
                        .minValue(param.getMinValue())
                        .maxValue(param.getMaxValue())
                        .maxLength(param.getMaxLength())
                        .allowedFileTypes(param.getAllowedFileTypes())
                        .maxFileSizeMb(param.getMaxFileSizeMb())
                        .fileCriteria(param.getFileCriteria() == null
                                ? null : new LinkedHashMap<>(param.getFileCriteria()))
                        .sortOrder(param.getSortOrder())
                        .build());
            }

            copy.addRequestType(copiedType);
        }

        AgreementTemplate saved = templateRepository.save(copy);
        saved.setAgreementCode(agreementCodeGenerator.generate(saved.getTemplateId(), saved.getName()));
        saved = templateRepository.save(saved);

        log.info("Copied template {} into data holder group {} as {}",
                source.getTemplateId(), targetGroupId, saved.getTemplateId());
        return saved;
    }

    /** Clauses are stored as JSON, so the copy takes its own maps rather than sharing them. */
    private List<Map<String, Object>> copyClauses(List<Map<String, Object>> clauses) {
        if (clauses == null) return null;
        List<Map<String, Object>> copied = new ArrayList<>();
        for (Map<String, Object> clause : clauses) {
            copied.add(clause == null ? null : new LinkedHashMap<>(clause));
        }
        return copied;
    }
}
