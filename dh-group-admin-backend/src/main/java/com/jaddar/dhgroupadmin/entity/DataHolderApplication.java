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

/**
 * Represents a pending application from an external organization wanting to join
 * a Data Holder Group. Once approved by an admin, a DataHolder is created from
 * the application data and added to the specified group.
 */
@Entity
@Table(name = "data_holder_applications", indexes = {
    @Index(name = "idx_dha_status", columnList = "status"),
    @Index(name = "idx_dha_group_id", columnList = "data_holder_group_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DataHolderApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== Target Group ==========
    @Column(name = "data_holder_group_id", nullable = false)
    private Long dataHolderGroupId;

    // ========== Organization Info ==========
    @Column(name = "organization_name", nullable = false)
    private String organizationName;

    @Column(name = "organization_address", columnDefinition = "TEXT")
    private String organizationAddress;

    @Column(name = "organization_phone")
    private String organizationPhone;

    // ========== Admin Contact ==========
    @Column(name = "contact_full_name", nullable = false)
    private String contactFullName;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contact_title")
    private String contactTitle;

    // ========== RDAP Server URLs (comma-separated or JSON array) ==========
    @Column(name = "rdap_server_urls", columnDefinition = "TEXT")
    private String rdapServerUrls;

    // ========== Supported TLDs (comma-separated, e.g. ".com,.net,.bbq") ==========
    @Column(name = "supported_tlds", columnDefinition = "TEXT")
    private String supportedTlds;

    // ========== Optional notes from applicant ==========
    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes;

    // ========== Application Status ==========
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** If approved, the ID of the DataHolder that was created */
    @Column(name = "created_data_holder_id")
    private Long createdDataHolderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum ApplicationStatus {
        PENDING,
        APPROVED,
        DENIED
    }
}
