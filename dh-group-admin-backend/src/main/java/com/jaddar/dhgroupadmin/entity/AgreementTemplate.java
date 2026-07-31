/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Agreement templates managed centrally by the Data Holder Group Admin.
 * Data holders retrieve these templates to know what agreements they can offer.
 */
@Entity
@Table(name = "agreement_templates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgreementTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", unique = true, nullable = false)
    private String templateId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "short_description", length = 25)
    private String shortDescription;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<AgreementRequestType> requestTypes = new ArrayList<>();

    @Column(name = "required_group_types")
    private String requiredGroupTypes;

    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

    @Column(name = "data_usage_policy", columnDefinition = "TEXT")
    private String dataUsagePolicy;

    /**
     * Controls how field data is disclosed for this template.
     * One of: "open" (shown as-is), "hashed" (shown hashed), "omit" (excluded).
     */
    @Column(name = "disclosure_mode")
    @Builder.Default
    private String disclosureMode = "open";

    @Column(name = "max_queries_per_day")
    private Integer maxQueriesPerDay;

    @Column(name = "max_queries_per_month")
    private Integer maxQueriesPerMonth;

    @Column(name = "is_published")
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "data_holder_group_id")
    private Long dataHolderGroupId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (templateId == null) {
            templateId = "TPL-" + System.currentTimeMillis();
        }
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    // ==================== Request Type Management ====================

    public void addRequestType(AgreementRequestType requestType) {
        requestTypes.add(requestType);
        requestType.setTemplate(this);
    }

    public void removeRequestType(AgreementRequestType requestType) {
        requestTypes.remove(requestType);
        requestType.setTemplate(null);
    }

    @Transient
    public Optional<AgreementRequestType> getRequestTypeByName(String name) {
        return requestTypes.stream()
                .filter(rt -> rt.getName().equalsIgnoreCase(name))
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .findFirst();
    }

    @Transient
    public Optional<AgreementRequestType> findMatchingRequestType(boolean isConfidential, boolean isExigent) {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .filter(rt -> rt.matchesRequest(isConfidential, isExigent))
                .findFirst();
    }

    @Transient
    public List<AgreementRequestType> getActiveRequestTypes() {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .toList();
    }

    @Transient
    public Integer getHighestAccessLevel() {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .map(AgreementRequestType::getAccessLevel)
                .max(Integer::compareTo)
                .orElse(0);
    }

    @Transient
    public Integer getDefaultAccessLevel() {
        return getRequestTypeByName("standard")
                .map(AgreementRequestType::getAccessLevel)
                .orElseGet(() -> requestTypes.stream()
                        .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                        .map(AgreementRequestType::getAccessLevel)
                        .findFirst()
                        .orElse(0));
    }

    @Transient
    public boolean supportsConfidential() {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .anyMatch(rt -> Boolean.TRUE.equals(rt.getSupportsConfidential()));
    }

    @Transient
    public boolean supportsExigent() {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .anyMatch(rt -> Boolean.TRUE.equals(rt.getSupportsExigent()));
    }

    @Transient
    public AgreementRdapParameters getEffectiveRdapParameters(String requestTypeName) {
        Optional<AgreementRequestType> rt = getRequestTypeByName(requestTypeName);
        if (rt.isPresent()) return rt.get().getEffectiveRdapParameters();
        rt = getRequestTypeByName("standard");
        if (rt.isPresent()) return rt.get().getEffectiveRdapParameters();
        return requestTypes.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .findFirst()
                .map(AgreementRequestType::getEffectiveRdapParameters)
                .orElse(AgreementRdapParameters.createPublicOnly());
    }
}
