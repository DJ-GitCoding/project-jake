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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents an active agreement obtained from a data holder.
 * This is the requestor-manager's local copy of an approved subscription.
 * 
 * Note: Request types are NOT stored here. They are fetched live from the
 * dataholder's published template endpoint to ensure they always reflect
 * the current configuration.
 */
@Entity
@Table(name = "data_holder_agreements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataHolderAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The agreement ID from the dataholder's system
     */
    @Column(name = "external_agreement_id", nullable = false)
    private String externalAgreementId;

    /**
     * The requestor group that holds this agreement
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requestor_group_id", nullable = false)
    private RequestorGroup requestorGroup;

    /**
     * The data holder group this agreement is with
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_holder_group_id", nullable = false)
    private DataHolderGroup dataHolderGroup;

    /**
     * The original subscription request that led to this agreement
     */
    @OneToOne(fetch = FetchType.LAZY, mappedBy = "dataHolderAgreement")
    private SubscriptionRequest subscriptionRequest;

    // ==================== Agreement Details ====================

    /**
     * Name of the agreement (from template)
     */
    @Column(nullable = false)
    private String name;

    /**
     * Description of what this agreement covers
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * The template ID this agreement was created from
     */
    @Column(name = "template_id")
    private String templateId;

    /**
     * Access level granted (0-3)
     */
    @Column(name = "access_level", nullable = false)
    @Builder.Default
    private Integer accessLevel = 0;

    /**
     * Terms and conditions text
     */
    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

    /**
     * Data usage policy text
     */
    @Column(name = "data_usage_policy", columnDefinition = "TEXT")
    private String dataUsagePolicy;

    /**
     * Maximum queries allowed per day (if limited)
     */
    @Column(name = "max_queries_per_day")
    private Integer maxQueriesPerDay;

    /**
     * Maximum queries allowed per month (if limited)
     */
    @Column(name = "max_queries_per_month")
    private Integer maxQueriesPerMonth;

    // ==================== Status ====================

    /**
     * Whether this agreement is currently active
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Current status from the dataholder
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AgreementStatus status = AgreementStatus.ACTIVE;

    /**
     * When the agreement becomes effective
     */
    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    /**
     * When the agreement expires (null = no expiration)
     */
    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

    /**
     * Last time we verified this agreement with the dataholder
     */
    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    // ==================== Audit Fields ====================

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Check if the agreement is currently effective
     */
    @Transient
    public boolean isCurrentlyEffective() {
        if (!isActive || status != AgreementStatus.ACTIVE) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean afterStart = effectiveFrom == null || !now.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || now.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }

    public enum AgreementStatus {
        ACTIVE,
        SUSPENDED,
        EXPIRED,
        TERMINATED
    }
}