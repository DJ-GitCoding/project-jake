/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementTemplate {
    private Long id;
    private String templateId; // Unique identifier for external reference
    private String name;
    private String description;
    private String shortDescription;

    /**
     * Request types this template supports (standard, confidential, exigent, etc.)
     * Each request type defines its own access level and RDAP parameters.
     */
    @Builder.Default
    private List<AgreementRequestType> requestTypes = new ArrayList<>();

    /**
     * RDAP data entries to use during the subscription testing phase.
     * The data holder admin configures these so that when a new subscription
     * reaches the TESTING state, the system can run real RDAP queries against
     * known data and verify access levels / redaction work correctly.
     */
    @Builder.Default
    private List<AgreementTemplateTestData> testDataEntries = new ArrayList<>();
    private String requiredGroupTypes; // Comma-separated: "law-enforcement,government"
    private String termsAndConditions;
    private String dataUsagePolicy;
    private Integer maxQueriesPerDay;
    private Integer maxQueriesPerMonth;

    /** Whether the template is offered for new subscriptions. */
    @Builder.Default
    private Boolean isActive = false;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (templateId == null) {
            templateId = "TPL-" + System.currentTimeMillis();
        }
    }
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Legacy alias for isActive, kept for clients built against the old name. */

    public Boolean getIsPublished() { return isActive; }


    public void setIsPublished(Boolean published) { this.isActive = published; }

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
    public Optional<AgreementRequestType> getRequestTypeByName(String name) {
        return requestTypes.stream()
                .filter(rt -> rt.getName().equalsIgnoreCase(name))
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .findFirst();
    }

    /**
     * Find the matching request type for the given query characteristics
     */
    public Optional<AgreementRequestType> findMatchingRequestType(boolean isConfidential, boolean isExigent) {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .filter(rt -> rt.matchesRequest(isConfidential, isExigent))
                .findFirst();
    }

    /**
     * Get all active request types
     */
    public List<AgreementRequestType> getActiveRequestTypes() {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .toList();
    }

    /**
     * Get the highest access level across all active request types
     */
    public Integer getHighestAccessLevel() {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .map(AgreementRequestType::getAccessLevel)
                .max(Integer::compareTo)
                .orElse(0);
    }

    /**
     * Check if this template supports confidential requests
     */
    public boolean supportsConfidential() {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .anyMatch(rt -> Boolean.TRUE.equals(rt.getSupportsConfidential()));
    }

    /**
     * Check if this template supports exigent requests
     */
    public boolean supportsExigent() {
        return requestTypes.stream()
                .filter(rt -> Boolean.TRUE.equals(rt.getIsActive()))
                .anyMatch(rt -> Boolean.TRUE.equals(rt.getSupportsExigent()));
    }

    /**
     * Get effective RDAP parameters for a specific request type name.
     * Falls back to standard, then first active, then public-only defaults.
     */
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
    public List<AgreementTemplateTestData> getActiveTestDataEntries() {
        return testDataEntries.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
                .toList();
    }

    /**
     * Check if this template has any test data configured
     */
    public boolean hasTestData() {
        return !getActiveTestDataEntries().isEmpty();
    }
}