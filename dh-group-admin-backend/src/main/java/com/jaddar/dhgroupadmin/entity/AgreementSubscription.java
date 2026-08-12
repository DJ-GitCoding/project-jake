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
import java.util.Optional;

/**
 * Represents an agreement subscription from a requestor group to a data holder template.
 * This is the source-of-truth for subscription lifecycle, owned by the Group Admin app.
 */
@Entity
@Table(name = "agreement_subscriptions", indexes = {
    @Index(name = "idx_ga_sub_request_id", columnList = "request_id"),
    @Index(name = "idx_ga_sub_requestor_group", columnList = "requestor_group_id"),
    @Index(name = "idx_ga_sub_status", columnList = "status"),
    @Index(name = "idx_ga_sub_dataholder", columnList = "dataholder_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgreementSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", unique = true, nullable = false)
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private AgreementTemplate template;

    // ==================== Data Holder Info ====================

    /** Identifies which data holder this subscription is for */
    @Column(name = "dataholder_id", nullable = true)
    private String dataholderId;

    @Column(name = "dataholder_name")
    private String dataholderName;

    @Column(name = "dataholder_url")
    private String dataholderUrl;

    @Column(name = "data_holder_group_id")
    private Long dataHolderGroupId;

    // ==================== Subscription Status ====================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.PENDING;

    @Column(name = "status_message", columnDefinition = "TEXT")
    private String statusMessage;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    // ==================== Requestor Group Information ====================

    @Column(name = "requestor_group_id", nullable = false)
    private String requestorGroupId;

    @Column(name = "requestor_group_name", nullable = false)
    private String requestorGroupName;

    @Column(name = "requestor_group_code", length = 10)
    private String requestorGroupCode;

    @Column(name = "requestor_group_type")
    private String requestorGroupType;

    @Column(name = "requestor_description", columnDefinition = "TEXT")
    private String requestorDescription;

    // ==================== Contact Information ====================

    @Column(name = "requestor_first_name") private String requestorFirstName;
    @Column(name = "requestor_last_name") private String requestorLastName;
    @Column(name = "requestor_organization") private String requestorOrganization;
    @Column(name = "requestor_contact_email") private String requestorContactEmail;
    @Column(name = "requestor_phone") private String requestorPhone;
    @Column(name = "requestor_address") private String requestorAddress;
    @Column(name = "requestor_city") private String requestorCity;
    @Column(name = "requestor_state_province") private String requestorStateProvince;
    @Column(name = "requestor_postal_code") private String requestorPostalCode;
    @Column(name = "requestor_country") private String requestorCountry;

    // ==================== Requestor Agent Information ====================

    @Column(name = "requestor_agent_id") private String requestorAgentId;
    @Column(name = "requestor_agent_url") private String requestorAgentUrl;
    @Column(name = "requestor_agent_callback_url") private String requestorAgentCallbackUrl;

    /**
     * Token introspection URL provided by the requestor group.
     * Data holders use this to validate bearer tokens on RDAP queries.
     */
    @Column(name = "introspection_url", length = 500)
    private String introspectionUrl;

    /**
     * Client ID a data holder authenticates with when calling
     * {@link #introspectionUrl}. RFC 7662 §2.1 requires the introspection
     * endpoint to authenticate its caller, so the URL alone is not usable.
     */
    @Column(name = "introspection_client_id", length = 255)
    private String introspectionClientId;

    /**
     * Client secret paired with {@link #introspectionClientId}. Released only to
     * authenticated data holders, never through the admin UI responses.
     */
    @Column(name = "introspection_client_secret", length = 512)
    private String introspectionClientSecret;

    // ==================== Subscription Details ====================

    @Column(name = "granted_sensitivity_level") private Integer grantedSensitivityLevel;
    @Column(name = "purpose", columnDefinition = "TEXT") private String purpose;
    @Column(name = "additional_terms", columnDefinition = "TEXT") private String additionalTerms;

    // ==================== Testing ====================

    @Column(name = "test_started_at") private LocalDateTime testStartedAt;
    @Column(name = "test_completed_at") private LocalDateTime testCompletedAt;
    @Column(name = "test_result") private String testResult;
    @Column(name = "test_details", columnDefinition = "TEXT") private String testDetails;

    // ==================== Review ====================

    @Column(name = "reviewed_by") private String reviewedBy;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Column(name = "review_notes", columnDefinition = "TEXT") private String reviewNotes;

    // ==================== Validity Period ====================

    @Column(name = "effective_from") private LocalDateTime effectiveFrom;
    @Column(name = "effective_to") private LocalDateTime effectiveTo;
    @Column(name = "request_expires_at") private LocalDateTime requestExpiresAt;

    // ==================== Rate Limits ====================

    @Column(name = "max_queries_per_day") private Integer maxQueriesPerDay;
    @Column(name = "max_queries_per_month") private Integer maxQueriesPerMonth;

    // ==================== Audit Fields ====================

    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "activated_at") private LocalDateTime activatedAt;
    @Column(name = "activated_by") private String activatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        statusChangedAt = LocalDateTime.now();
        if (requestId == null) {
            requestId = "SUB-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
        }
        if (requestExpiresAt == null) {
            requestExpiresAt = LocalDateTime.now().plusDays(30);
        }
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    // ==================== Helper Methods ====================

    @Transient
    public boolean isCurrentlyEffective() {
        if (status != SubscriptionStatus.ACTIVE) return false;
        LocalDateTime now = LocalDateTime.now();
        boolean afterStart = effectiveFrom == null || !now.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || now.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }

    @Transient
    public boolean grantsAccess() { return isCurrentlyEffective(); }

    @Transient
    public Integer getEffectiveAccessLevel(String requestTypeName) {
        if (template == null) return 0;
        return template.getRequestTypeByName(requestTypeName)
                .map(AgreementRequestType::getAccessLevel).orElse(0);
    }

    @Transient
    public Integer getEffectiveAccessLevel(boolean isConfidential, boolean isExigent) {
        if (template == null) return 0;
        return template.findMatchingRequestType(isConfidential, isExigent)
                .map(AgreementRequestType::getAccessLevel).orElse(0);
    }

    @Transient
    public Integer getEffectiveAccessLevel() { return getEffectiveAccessLevel(false, false); }

    @Transient
    public Integer getHighestAccessLevel() {
        return template != null ? template.getHighestAccessLevel() : 0;
    }

    @Transient
    public boolean supportsConfidential() { return template != null && template.supportsConfidential(); }

    @Transient
    public boolean supportsExigent() { return template != null && template.supportsExigent(); }

    @Transient
    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        if (requestorAddress != null) sb.append(requestorAddress);
        if (requestorCity != null) { if (sb.length() > 0) sb.append(", "); sb.append(requestorCity); }
        if (requestorStateProvince != null) { if (sb.length() > 0) sb.append(", "); sb.append(requestorStateProvince); }
        if (requestorPostalCode != null) { if (sb.length() > 0) sb.append(" "); sb.append(requestorPostalCode); }
        if (requestorCountry != null) { if (sb.length() > 0) sb.append(", "); sb.append(requestorCountry); }
        return sb.toString();
    }

    @Transient
    public String getDisplayName() {
        if (template != null) return template.getName() + " - " + requestorGroupName;
        return "Subscription - " + requestorGroupName;
    }

    public enum SubscriptionStatus {
        PENDING, APPROVED, TESTING, ACTIVE, DENIED, SUSPENDED, EXPIRED, CANCELLED
    }
}
