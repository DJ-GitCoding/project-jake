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
import java.util.Optional;

/**
 * Represents an agreement subscription from a requestor group to a data holder template.
 * This entity handles the full lifecycle from request to active agreement:
 * - PENDING: Initial request awaiting review
 * - APPROVED: Approved by data holder, ready for testing
 * - TESTING: Currently being tested
 * - ACTIVE: Fully active subscription granting access
 * - DENIED: Request was rejected
 * - SUSPENDED: Temporarily suspended
 * - EXPIRED: Subscription has expired
 * - CANCELLED: Cancelled by requestor
 *
 * Access level and RDAP parameters are resolved at query time through
 * the template's {@link AgreementRequestType} entries, based on whether
 * the query is standard, confidential, or exigent.
 */
@Entity
@Table(name = "agreement_subscriptions", indexes = {
    @Index(name = "idx_subscription_request_id", columnList = "request_id"),
    @Index(name = "idx_subscription_requestor_group", columnList = "requestor_group_id"),
    @Index(name = "idx_subscription_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique request ID for external reference and tracking
     */
    @Column(name = "request_id", unique = true, nullable = false)
    private String requestId;

    /**
     * Reference to the template being subscribed to.
     * Access levels and RDAP parameters are inherited from the template's request types.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private AgreementTemplate template;

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

    /**
     * Short code identifying the requestor group in RDAP query parameters.
     * Sent during subscription initiation and stored for access control lookups.
     */
    @Column(name = "requestor_group_code", length = 10)
    private String requestorGroupCode;

    @Column(name = "requestor_group_type")
    private String requestorGroupType;

    @Column(name = "requestor_description", columnDefinition = "TEXT")
    private String requestorDescription;

    // ==================== Contact Information ====================

    @Column(name = "requestor_first_name")
    private String requestorFirstName;

    @Column(name = "requestor_last_name")
    private String requestorLastName;

    @Column(name = "requestor_organization")
    private String requestorOrganization;

    @Column(name = "requestor_contact_email")
    private String requestorContactEmail;

    @Column(name = "requestor_phone")
    private String requestorPhone;

    @Column(name = "requestor_address")
    private String requestorAddress;

    @Column(name = "requestor_city")
    private String requestorCity;

    @Column(name = "requestor_state_province")
    private String requestorStateProvince;

    @Column(name = "requestor_postal_code")
    private String requestorPostalCode;

    @Column(name = "requestor_country")
    private String requestorCountry;

    // ==================== Requestor Agent Information ====================

    @Column(name = "requestor_agent_id")
    private String requestorAgentId;

    @Column(name = "requestor_agent_url")
    private String requestorAgentUrl;

    @Column(name = "requestor_agent_callback_url")
    private String requestorAgentCallbackUrl;

    // ==================== Subscription Details ====================

    /**
     * Maximum sensitivity level granted (0-3)
     */
    @Column(name = "granted_sensitivity_level")
    private Integer grantedSensitivityLevel;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "additional_terms", columnDefinition = "TEXT")
    private String additionalTerms;

    /**
     * Token introspection URL provided by the requestor group.
     * Used as a fallback when no dedicated IntrospectionCredential exists
     * for this subscription. Data holder uses this URL to validate bearer
     * tokens on incoming RDAP queries from this requestor group.
     */
    @Column(name = "introspection_url", length = 500)
    private String introspectionUrl;

    // ==================== Testing ====================

    @Column(name = "test_started_at")
    private LocalDateTime testStartedAt;

    @Column(name = "test_completed_at")
    private LocalDateTime testCompletedAt;

    @Column(name = "test_result")
    private String testResult;

    @Column(name = "test_details", columnDefinition = "TEXT")
    private String testDetails;

    // ==================== Review ====================

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    // ==================== Validity Period ====================

    /**
     * When the subscription becomes effective (for ACTIVE subscriptions)
     */
    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    /**
     * When the subscription expires (null = no expiration)
     */
    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    /**
     * When the request expires if not processed
     */
    @Column(name = "request_expires_at")
    private LocalDateTime requestExpiresAt;

    // ==================== Rate Limits ====================

    @Column(name = "max_queries_per_day")
    private Integer maxQueriesPerDay;

    @Column(name = "max_queries_per_month")
    private Integer maxQueriesPerMonth;

    // ==================== Audit Fields ====================

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "activated_by")
    private String activatedBy;

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
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== Helper Methods ====================

    /**
     * Check if this subscription is currently active and effective
     */
    @Transient
    public boolean isCurrentlyEffective() {
        if (status != SubscriptionStatus.ACTIVE) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean afterStart = effectiveFrom == null || !now.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || now.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }

    /**
     * Check if this subscription grants access (is active and effective)
     */
    @Transient
    public boolean grantsAccess() {
        return isCurrentlyEffective();
    }

    /**
     * Get the effective access level for a specific request type.
     *
     * @param requestTypeName the name of the request type (e.g., "standard", "confidential", "exigent")
     * @return the access level for the given request type, or 0 if not found
     */
    @Transient
    public Integer getEffectiveAccessLevel(String requestTypeName) {
        if (template == null) {
            return 0;
        }
        return template.getRequestTypeByName(requestTypeName)
                .map(AgreementRequestType::getAccessLevel)
                .orElse(0);
    }

    /**
     * Get the effective access level based on query characteristics.
     *
     * @param isConfidential whether the query is a confidential disclosure request
     * @param isExigent whether the query is an exigent disclosure request
     * @return the access level for the matching request type, or 0 if no match
     */
    @Transient
    public Integer getEffectiveAccessLevel(boolean isConfidential, boolean isExigent) {
        if (template == null) {
            return 0;
        }
        return template.findMatchingRequestType(isConfidential, isExigent)
                .map(AgreementRequestType::getAccessLevel)
                .orElse(0);
    }

    /**
     * Get the effective access level for standard (non-confidential, non-exigent) requests.
     * This is the default/backward-compatible method.
     */
    @Transient
    public Integer getEffectiveAccessLevel() {
        return getEffectiveAccessLevel(false, false);
    }

    /**
     * Get the highest access level across all request types in the template
     */
    @Transient
    public Integer getHighestAccessLevel() {
        if (template == null) {
            return 0;
        }
        return template.getHighestAccessLevel();
    }

    /**
     * Check if this subscription supports confidential requests
     */
    @Transient
    public boolean supportsConfidential() {
        return template != null && template.supportsConfidential();
    }

    /**
     * Check if this subscription supports exigent requests
     */
    @Transient
    public boolean supportsExigent() {
        return template != null && template.supportsExigent();
    }

    /**
     * Find the matching request type on the template for the given query
     */
    @Transient
    public Optional<AgreementRequestType> findMatchingRequestType(boolean isConfidential, boolean isExigent) {
        if (template == null) {
            return Optional.empty();
        }
        return template.findMatchingRequestType(isConfidential, isExigent);
    }

    /**
     * Get effective RDAP parameters for a specific request type
     */
    @Transient
    public AgreementRdapParameters getEffectiveRdapParameters(String requestTypeName) {
        if (template != null) {
            return template.getEffectiveRdapParameters(requestTypeName);
        }
        return AgreementRdapParameters.createPublicOnly();
    }

    /**
     * Get effective RDAP parameters based on query characteristics
     */
    @Transient
    public AgreementRdapParameters getEffectiveRdapParameters(boolean isConfidential, boolean isExigent) {
        if (template == null) {
            return AgreementRdapParameters.createPublicOnly();
        }
        return template.findMatchingRequestType(isConfidential, isExigent)
                .map(AgreementRequestType::getEffectiveRdapParameters)
                .orElse(AgreementRdapParameters.createPublicOnly());
    }

    /**
     * Get effective RDAP parameters for standard requests (backward compatibility)
     */
    @Transient
    public AgreementRdapParameters getEffectiveRdapParameters() {
        return getEffectiveRdapParameters(false, false);
    }

    /**
     * Get formatted full address
     */
    @Transient
    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        if (requestorAddress != null) sb.append(requestorAddress);
        if (requestorCity != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(requestorCity);
        }
        if (requestorStateProvince != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(requestorStateProvince);
        }
        if (requestorPostalCode != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(requestorPostalCode);
        }
        if (requestorCountry != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(requestorCountry);
        }
        return sb.toString();
    }

    /**
     * Get a display name for this subscription
     */
    @Transient
    public String getDisplayName() {
        if (template != null) {
            return template.getName() + " - " + requestorGroupName;
        }
        return "Subscription - " + requestorGroupName;
    }

    /**
     * Subscription status enum
     */
    public enum SubscriptionStatus {
        PENDING,    // Initial state, awaiting review
        APPROVED,   // Approved, ready for testing
        TESTING,    // Test in progress
        ACTIVE,     // Fully active subscription granting access
        DENIED,     // Request was rejected
        SUSPENDED,  // Temporarily suspended
        EXPIRED,    // Subscription has expired
        CANCELLED   // Cancelled by requestor
    }
}