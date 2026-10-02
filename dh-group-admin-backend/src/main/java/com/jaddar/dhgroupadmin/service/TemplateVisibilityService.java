/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.service;

import com.jaddar.dhgroupadmin.entity.AgreementTemplate;
import com.jaddar.dhgroupadmin.entity.DataHolderGroup;
import com.jaddar.dhgroupadmin.entity.Visibility;
import com.jaddar.dhgroupadmin.repository.AgreementTemplateRepository;
import com.jaddar.dhgroupadmin.repository.DataHolderGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The single place that decides how far a template is advertised. */
@Service
@RequiredArgsConstructor
public class TemplateVisibilityService {
    private final AgreementTemplateRepository templateRepository;
    private final DataHolderGroupRepository groupRepository;

    /** The template's setting clamped by its group's. */
    public Visibility effectiveVisibility(AgreementTemplate template, Map<Long, DataHolderGroup> groups) {
        Visibility own = template.getVisibility() != null ? template.getVisibility() : Visibility.PUBLIC;
        Long groupId = template.getDataHolderGroupId();
        if (groupId == null) {
            return Visibility.min(own, Visibility.PUBLIC);
        }
        DataHolderGroup group = groups.get(groupId);
        if (group == null) {
            return Visibility.min(own, Visibility.PUBLIC);
        }
        if (!Boolean.TRUE.equals(group.getIsActive())) {
            return Visibility.PRIVATE;
        }
        return Visibility.min(own, group.getVisibility());
    }

    /** Convenience overload that loads the group itself; prefer the map form in loops. */
    @Transactional(readOnly = true)
    public Visibility effectiveVisibility(AgreementTemplate template) {
        return effectiveVisibility(template, groupsById());
    }

    /** Active templates that anyone looking this group admin up should see, effective visibility PUBLIC or GLOBAL. */
    @Transactional(readOnly = true)
    public List<AgreementTemplate> listedTemplates() {
        Map<Long, DataHolderGroup> groups = groupsById();
        return templateRepository.findByIsActiveTrue().stream()
                .filter(t -> effectiveVisibility(t, groups).atLeast(Visibility.PUBLIC))
                .toList();
    }

    /** Active templates whose effective visibility is GLOBAL, for the common repository. */
    @Transactional(readOnly = true)
    public List<AgreementTemplate> globallyPublishedTemplates() {
        Map<Long, DataHolderGroup> groups = groupsById();
        return templateRepository.findByIsActiveTrue().stream()
                .filter(t -> effectiveVisibility(t, groups) == Visibility.GLOBAL)
                .toList();
    }

    /** Why a template is not as widely advertised as it asks to be, or null when its own setting is being honoured. */
    public String clampReason(AgreementTemplate template, Map<Long, DataHolderGroup> groups) {
        Visibility own = template.getVisibility() != null ? template.getVisibility() : Visibility.PUBLIC;
        Visibility effective = effectiveVisibility(template, groups);
        if (effective == own) return null;
        if (template.getDataHolderGroupId() == null) {
            return "Templates without a data holder group cannot be published to the common repository.";
        }
        DataHolderGroup group = groups.get(template.getDataHolderGroupId());
        if (group == null) {
            return "The data holder group this template belongs to no longer exists.";
        }
        if (!Boolean.TRUE.equals(group.getIsActive())) {
            return "The data holder group \"" + group.getName() + "\" is disabled, so this template is not listed.";
        }
        return "The data holder group \"" + group.getName() + "\" is set to "
                + label(group.getVisibility()) + ", which caps this template at " + label(effective) + ".";
    }

    /** Groups keyed by id, so a listing pass reads them once. */
    @Transactional(readOnly = true)
    public Map<Long, DataHolderGroup> groupsById() {
        Map<Long, DataHolderGroup> byId = new HashMap<>();
        for (DataHolderGroup group : groupRepository.findAll()) {
            byId.put(group.getId(), group);
        }
        return byId;
    }

    private static String label(Visibility visibility) {
        return switch (visibility == null ? Visibility.PUBLIC : visibility) {
            case PRIVATE -> "Private";
            case PUBLIC -> "Public";
            case GLOBAL -> "Globally published";
        };
    }
}
