/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

import java.time.LocalDateTime;

/**
 * Tracks subscription requests sent from this Requestor Manager to external data holders.
 * This represents the requestor-manager's view of the subscription process.
 */
@Entity
@Table(name = "subscription_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Internal unique identifier for this subscription request
     */
    @Column(name = "internal_request_id", unique = true, nullable = false)
    private String internalRequestId;

    /**
     * The request ID returned by the dataholder (for status checks)
     */
    @Column(name = "external_request_id")
    private String externalRequestId;

    /**
     * Credentials the data holder group issued for this subscription. They identify this
     * requestor manager to that group admin for calls about this subscription only, so
     * revoking one subscription never affects another.
     */
    @Column(name = "dhg_client_id")
    private String dhgClientId;

    /**
     * Signing keys for this subscription's calls to its data holder group. The private half
     * never leaves this service; the public half is registered with the group admin, so a
     * compromise of their database cannot be used to impersonate us.
     */
    @Column(name = "dhg_public_key", length = 500)
    private String dhgPublicKey;

    /** Encrypted at rest (AES-256-GCM) by {@link com.requestormanager.config.EncryptedStringConverter}. */
    @Column(name = "dhg_private_key", length = 2000)
    @Convert(converter = com.requestormanager.config.EncryptedStringConverter.class)
    private String dhgPrivateKey;

    /** Encrypted at rest (AES-256-GCM) by {@link com.requestormanager.config.EncryptedStringConverter}. */
    @Column(name = "dhg_client_secret", length = 2000)
    @Convert(converter = com.requestormanager.config.EncryptedStringConverter.class)
    private String dhgClientSecret;

    /**
     * The requestor group making this subscription request
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requestor_group_id", nullable = false)
    private RequestorGroup requestorGroup;

    /**
     * The data holder group being subscribed to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_holder_group_id", nullable = false)
    private DataHolderGroup dataHolderGroup;

    /**
     * Template ID from the dataholder being requested
     */
    @Column(name = "template_id", nullable = false)
    private String templateId;

    /**
     * Template name (cached for display purposes)
     */
    @Column(name = "template_name")
    private String templateName;

    // ==================== Requestor Contact Information ====================

    /**
     * First name of the person making the request. Nullable at the DB level.
     */
    @Column(name = "requestor_first_name")
    private String requestorFirstName;

    /**
     * Last name of the person making the request. Nullable at the DB level.
     */
    @Column(name = "requestor_last_name")
    private String requestorLastName;

    /**
     * Organization name
     */
    @Column(name = "requestor_organization")
    private String requestorOrganization;

    /**
     * Contact email
     */
    @Column(name = "requestor_email", nullable = false)
    private String requestorEmail;

    /**
     * Contact phone number
     */
    @Column(name = "requestor_phone")
    private String requestorPhone;

    /**
     * Street address
     */
    @Column(name = "requestor_address")
    private String requestorAddress;

    /**
     * City
     */
    @Column(name = "requestor_city")
    private String requestorCity;

    /**
     * State or Province
     */
    @Column(name = "requestor_state_province")
    private String requestorStateProvince;

    /**
     * Postal/ZIP code
     */
    @Column(name = "requestor_postal_code")
    private String requestorPostalCode;

    /**
     * Country
     */
    @Column(name = "requestor_country")
    private String requestorCountry;

    // ==================== Request Details ====================

    /**
     * Token introspection URL that data holders should use to validate
     * bearer tokens from this requestor group's members.
     * Provided by the requestor group during subscription creation.
     */
    @Column(name = "introspection_url", length = 500)
    private String introspectionUrl;

    /** What the requestor entered for the template's group-defined subscription fields. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "subscription_field_values", columnDefinition = "jsonb")
    private Map<String, Object> subscriptionFieldValues;

    /** PROPOSED while a template change from the group admin awaits this requestor's answer. */
    @Column(name = "pending_change_status", length = 20)
    private String pendingChangeStatus;

    @Column(name = "pending_proposed_at")
    private LocalDateTime pendingProposedAt;

    /** FORCED (must accept by the deadline) or OPTIONAL (may decline). */
    @Column(name = "pending_change_mode", length = 20)
    private String pendingChangeMode;

    @Column(name = "pending_change_deadline")
    private LocalDateTime pendingChangeDeadline;

    /**
     * The terms this subscription actually runs under: as at the initial subscription, or as at
     * the last change the requestor accepted. This is what the requestor reviews.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> templateSnapshot;

    /** Which legal sections were accepted, and when. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accepted_terms", columnDefinition = "jsonb")
    private List<Map<String, Object>> acceptedTerms;

    /** The template state being proposed, shown to the requestor before they answer. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_template_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> pendingTemplateSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_field_mapping", columnDefinition = "jsonb")
    private Map<String, String> userFieldMapping;

    /** Ids of the legal sections the requestor accepted when subscribing. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accepted_legal_section_ids", columnDefinition = "jsonb")
    private List<Long> acceptedLegalSectionIds;

    // ==================== Status Tracking ====================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.DRAFT;

    /**
     * Message from the dataholder (e.g., decline reason)
     */
    @Column(name = "status_message", columnDefinition = "TEXT")
    private String statusMessage;

    /**
     * When the status was last changed
     */
    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    /**
     * URL to check status at the dataholder
     */
    @Column(name = "status_check_url")
    private String statusCheckUrl;

    /**
     * The dataholder's agent ID (for reference)
     */
    @Column(name = "dataholder_agent_id")
    private String dataholderAgentId;

    // ==================== Approval Details ====================

    /**
     * If approved, the granted access level
     */

    /**
     * Reference to the created DataHolderAgreement (if approved)
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_holder_agreement_id")
    private DataHolderAgreement dataHolderAgreement;

    // ==================== Audit Fields ====================

    /**
     * Keycloak ID of user who created this request
     */
    @Column(name = "created_by_keycloak_id")
    private String createdByKeycloakId;

    @Column(name = "created_by_email")
    private String createdByEmail;

    @Column(name = "created_by_name")
    private String createdByName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * When the request was submitted to the dataholder
     */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /**
     * Request expiration time
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        if (internalRequestId == null) {
            internalRequestId = "SUB-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
        }
        statusChangedAt = LocalDateTime.now();
    }

    /**
     * Subscription request status from the requestor-manager's perspective
     */
    public enum SubscriptionStatus {
        DRAFT,              // Created but not yet submitted
        SUBMITTED,          // Sent to dataholder, awaiting response
        PENDING_REVIEW,     // Dataholder is reviewing
        APPROVED,           // Approved by dataholder, pending testing
        TESTING,            // Testing in progress at dataholder
        ACTIVE,             // Fully active agreement
        DECLINED,           // Rejected by dataholder
        SUSPENDED,          // Temporarily suspended
        CANCELLED,          // Cancelled by requestor
        EXPIRED             // Request expired before completion
    }
}