/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Stores ALL RDAP requests - both auto-processed and those requiring manual review.
 * 
 * This entity tracks:
 * - Auto-approved requests (autoApproved=true, status=APPROVED)
 * - Auto-denied requests (autoApproved=true, status=DENIED)  
 * - Pending requests requiring manual review (autoApproved=false, status=PENDING)
 * - Manually approved/denied requests (autoApproved=false, status=APPROVED/DENIED)
 */
@Entity
@Table(name = "pending_requests", indexes = {
    @Index(name = "idx_pending_requests_status", columnList = "status"),
    @Index(name = "idx_pending_requests_query_type", columnList = "query_type"),
    @Index(name = "idx_pending_requests_requestor_sub", columnList = "requestor_sub"),
    @Index(name = "idx_pending_requests_created_at", columnList = "created_at"),
    @Index(name = "idx_pending_requests_auto_approved", columnList = "auto_approved")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingRequest {

    public enum Status {
        PENDING,    // Awaiting manual review
        APPROVED,   // Approved (auto or manual)
        DENIED,     // Denied (auto or manual)
        CANCELLED   // Cancelled by the requestor before review
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier for this request (used in polling URLs)
     */
    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    /**
     * Type of RDAP query: domain, ip, asn
     */
    @Column(name = "query_type", nullable = false, length = 100)
    private String queryType;

    /**
     * The queried value (domain name, IP address, or ASN)
     */
    @Column(name = "query_value", nullable = false)
    private String queryValue;

    /**
     * Access level requested/granted
     */
    @Column(name = "requested_access_level", nullable = false)
    private Integer requestedAccessLevel;

    /**
     * Keycloak subject ID of the requestor
     */
    @Column(name = "requestor_sub")
    private String requestorSub;

    /**
     * Username of the requestor
     */
    @Column(name = "requestor_username")
    private String requestorUsername;

    /**
     * Email of the requestor
     */
    @Column(name = "requestor_email")
    private String requestorEmail;

    /**
     * IP address of the requestor
     */
    @Column(name = "requestor_ip")
    private String requestorIp;

    /**
     * Keycloak groups the requestor belongs to
     */
    @Column(name = "requestor_groups", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] requestorGroups;

    /**
     * Agreement names used for this request
     */
    @Column(name = "agreement_names", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] agreementNames;

    /**
     * Current status of the request
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    // ==================== AUTO-PROCESSING FIELDS ====================

    /**
     * Whether this request was auto-processed (true) or requires/required manual review (false)
     */
    @Column(name = "auto_approved", nullable = false)
    @Builder.Default
    private Boolean autoApproved = false;

    /**
     * The wait time (in ms) that was applied to this request
     */
    @Column(name = "wait_time_applied_ms")
    private Long waitTimeAppliedMs;

    /**
     * Response time in milliseconds (from request to response)
     */
    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    /**
     * Denial reason (from control or manual review)
     */
    @Column(name = "denial_reason")
    private String denialReason;

    // ==================== DISCLOSURE FLAGS ====================

    /**
     * Confidential disclosure: do not notify the registrant of the inquiry.
     */
    @Column(name = "confidential", nullable = false)
    @Builder.Default
    private boolean confidential = false;

    /**
     * Exigent disclosure: bypass the requirement for manual verification.
     */
    @Column(name = "exigent", nullable = false)
    @Builder.Default
    private boolean exigent = false;

    /**
     * JAKE compliance flag.
     */
    @Column(name = "jake_compliance", nullable = false)
    @Builder.Default
    private boolean jakeCompliance = false;

    // ==================== REVIEW FIELDS ====================

    /**
     * Admin notes for manual review
     */
    @Column(name = "admin_notes")
    private String adminNotes;

    /**
     * Who reviewed/approved/denied this request (username or "SYSTEM" for auto)
     */
    @Column(name = "reviewed_by")
    private String reviewedBy;

    /**
     * When the request was reviewed
     */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * The RDAP response data (stored for approved requests)
     */
    @Column(name = "response_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> responseData;

    /**
     * Custom parameters passed with the RDAP request (e.g. file references, extra fields)
     */
    @Column(name = "custom_params", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> customParams;

    /**
     * When this pending request expires (for polling)
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Files attached to this request
     */
    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<FileAttachment> fileAttachments;

    @PrePersist
    protected void onCreate() {
        if (requestId == null) {
            requestId = UUID.randomUUID();
        }
        if (status == null) {
            status = Status.PENDING;
        }
        if (autoApproved == null) {
            autoApproved = false;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Helper to check if this was an auto-approved request
     */
    public boolean isAutoProcessed() {
        return Boolean.TRUE.equals(autoApproved);
    }

    /**
     * Helper to check if this is still pending review
     */
    public boolean isPending() {
        return status == Status.PENDING;
    }

    /**
     * Helper to check if this request has expired
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
}