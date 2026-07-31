/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Agreement templates that the dataholder advertises as available.
 * These are agreements the dataholder is willing to enter into,
 * but are not yet associated with specific requestor groups.
 *
 * Access level and RDAP parameters are now defined per request type
 * via the {@link AgreementRequestType} child entity.
 */
@Entity
@Table(name = "agreement_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", unique = true, nullable = false)
    private String templateId; // Unique identifier for external reference

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "short_description", length = 25)
    private String shortDescription;

    /**
     * Request types this template supports (standard, confidential, exigent, etc.)
     * Each request type defines its own access level and RDAP parameters.
     */
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<AgreementRequestType> requestTypes = new ArrayList<>();

    /**
     * RDAP data entries to use during the subscription testing phase.
     * The data holder admin configures these so that when a new subscription
     * reaches the TESTING state, the system can run real RDAP queries against
     * known data and verify access levels / redaction work correctly.
     */
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<AgreementTemplateTestData> testDataEntries = new ArrayList<>();

    @Column(name = "required_group_types")
    private String requiredGroupTypes; // Comma-separated: "law-enforcement,government"

    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

    @Column(name = "data_usage_policy", columnDefinition = "TEXT")
    private String dataUsagePolicy;

    @Column(name = "max_queries_per_day")
    private Integer maxQueriesPerDay;

    @Column(name = "max_queries_per_month")
    private Integer maxQueriesPerMonth;

    @Column(name = "is_published")
    @Builder.Default
    private Boolean isPublished = false; // Only published templates are visible to Requestor Manager

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (templateId == null) {
            templateId = "TPL-" + System.currentTimeMillis();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== Request Type Management ====================

    /**
     * Add a request type to this template
     */
    public void addRequestType(AgreementRequestType requestType) {
        requestTypes.add(requestType);
        requestType.setTemplate(this);
    }

    /**
     * Remove a request type from this template
     */
    public void removeRequestType(AgreementRequestType requestType) {
        requestTypes.remove(requestType);
        requestType.setTemplate(null);
    }

    /**
     * Find a request type by name
     */
    @Transient
    public Optional<AgreementRequestType> getRequestTypeByName(String name) {
        return requestTypes.stream()
                .filter(rt -> rt.getName().equalsIgnoreCase(name))
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .findFirst();
    }

    /**
     * Find the matching request type for the given query characteristics
     */
    @Transient
    public Optional<AgreementRequestType> findMatchingRequestType(boolean isConfidential, boolean isExigent) {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .filter(rt -> rt.matchesRequest(isConfidential, isExigent))
                .findFirst();
    }

    /**
     * Get all active request types
     */
    @Transient
    public List<AgreementRequestType> getActiveRequestTypes() {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .toList();
    }

    /**
     * Get the highest access level across all active request types
     */
    @Transient
    public Integer getHighestAccessLevel() {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .map(AgreementRequestType::getAccessLevel)
                .max(Integer::compareTo)
                .orElse(0);
    }

    /**
     * Get the default (standard) request type's access level.
     * Falls back to the first active request type, then 0.
     */
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

    /**
     * Check if this template supports confidential requests
     */
    @Transient
    public boolean supportsConfidential() {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .anyMatch(rt -> Boolean.TRUE.equals(rt.getSupportsConfidential()));
    }

    /**
     * Check if this template supports exigent requests
     */
    @Transient
    public boolean supportsExigent() {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .anyMatch(rt -> Boolean.TRUE.equals(rt.getSupportsExigent()));
    }

    /**
     * Get effective RDAP parameters for a specific request type name.
     * Falls back to standard, then first active, then public-only defaults.
     */
    @Transient
    public AgreementRdapParameters getEffectiveRdapParameters(String requestTypeName) {
        Optional<AgreementRequestType> rt = getRequestTypeByName(requestTypeName);
        if (rt.isPresent()) {
            return rt.get().getEffectiveRdapParameters();
        }
        // Fallback to standard
        rt = getRequestTypeByName("standard");
        if (rt.isPresent()) {
            return rt.get().getEffectiveRdapParameters();
        }
        // Fallback to first active
        return requestTypes.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .findFirst()
                .map(AgreementRequestType::getEffectiveRdapParameters)
                .orElse(AgreementRdapParameters.createPublicOnly());
    }

    /**
     * Get effective RDAP parameters for standard requests (backward compatibility)
     */
    @Transient
    public AgreementRdapParameters getEffectiveRdapParameters() {
        return getEffectiveRdapParameters("standard");
    }

    // ==================== Test Data Management ====================

    /**
     * Add a test data entry to this template
     */
    public void addTestDataEntry(AgreementTemplateTestData entry) {
        testDataEntries.add(entry);
        entry.setTemplate(this);
    }

    /**
     * Remove a test data entry from this template
     */
    public void removeTestDataEntry(AgreementTemplateTestData entry) {
        testDataEntries.remove(entry);
        entry.setTemplate(null);
    }

    /**
     * Get all active test data entries
     */
    @Transient
    public List<AgreementTemplateTestData> getActiveTestDataEntries() {
        return testDataEntries.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                .toList();
    }

    /**
     * Check if this template has any test data configured
     */
    @Transient
    public boolean hasTestData() {
        return !getActiveTestDataEntries().isEmpty();
    }
}