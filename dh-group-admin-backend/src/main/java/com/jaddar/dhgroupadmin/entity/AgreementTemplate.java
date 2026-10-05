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

    @Column(name = "agreement_code", unique = true, length = 64)
    private String agreementCode;

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

    /** Legal text the requestor must accept, section by section, to subscribe. */
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<AgreementLegalSection> legalSections = new ArrayList<>();

    /** Values the requestor must supply when subscribing. */
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<TemplateSubscriptionField> subscriptionFields = new ArrayList<>();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<TemplateUserField> userFields = new ArrayList<>();

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

    /** Whether the template is offered for new subscriptions. */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = false;

    /** How widely the template is advertised, clamped by the owning group's setting. */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20)
    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;

    // ==================== Contact Information ====================

    @Column(name = "use_group_contact")
    @Builder.Default
    private Boolean useGroupContact = true;

    @Column(name = "contact_first_name")
    private String contactFirstName;

    @Column(name = "contact_last_name")
    private String contactLastName;

    @Column(name = "contact_email")
    private String contactEmail;

    /** Optional, on the template as on the group. */
    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contact_address")
    private String contactAddress;

    @Column(name = "contact_city")
    private String contactCity;

    @Column(name = "contact_state_province")
    private String contactStateProvince;

    @Column(name = "contact_postal_code")
    private String contactPostalCode;

    @Column(name = "contact_country")
    private String contactCountry;

    @Transient
    public boolean hasOwnContact() { return Boolean.FALSE.equals(useGroupContact); }

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "data_holder_group_id")
    private Long dataHolderGroupId;

    /** Random suffix on generated ids, since a Private template is reachable by whoever knows its id. */
    private static final java.security.SecureRandom ID_RANDOM = new java.security.SecureRandom();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (templateId == null) {
            byte[] entropy = new byte[9];
            ID_RANDOM.nextBytes(entropy);
            templateId = "TPL-" + System.currentTimeMillis() + "-"
                    + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        }
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    /** Legacy alias for isActive, kept for clients built against the old name. */
    @Transient
    public Boolean getIsPublished() { return isActive; }

    @Transient
    public void setIsPublished(Boolean published) { this.isActive = published; }

    // ==================== Request Type Management ====================

    public void addRequestType(AgreementRequestType requestType) {
        requestTypes.add(requestType);
        requestType.setTemplate(this);
    }

    public void removeRequestType(AgreementRequestType requestType) {
        requestTypes.remove(requestType);
        requestType.setTemplate(null);
    }

    public void addLegalSection(AgreementLegalSection section) {
        legalSections.add(section);
        section.setTemplate(this);
    }

    public void addSubscriptionField(TemplateSubscriptionField field) {
        subscriptionFields.add(field);
        field.setTemplate(this);
    }

    public void addUserField(TemplateUserField field) {
        userFields.add(field);
        field.setTemplate(this);
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
