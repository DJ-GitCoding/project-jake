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
 * A requestor group that has registered (or is pending registration) with this
 * Data Holder Group Admin.  Acceptance grants the group credentials to
 * interact with the Group Admin's external API.
 */
@Entity
@Table(name = "requestor_groups", indexes = {
    @Index(name = "idx_rg_code", columnList = "code"),
    @Index(name = "idx_rg_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RequestorGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display name of the requestor group */
    @Column(nullable = false)
    private String name;

    /** Short unique code (e.g. "LEA-US", "REG-001") */
    @Column(unique = true, nullable = false, length = 40)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Group type (e.g. "law-enforcement", "registrar", "security", "brand-protection") */
    @Column(name = "group_type")
    private String groupType;

    /** Primary contact name */
    @Column(name = "contact_name")
    private String contactName;

    /** Primary contact email */
    @Column(name = "contact_email")
    private String contactEmail;

    /** Primary contact phone */
    @Column(name = "contact_phone")
    private String contactPhone;

    /** Organization name */
    @Column(name = "organization")
    private String organization;

    /** Address fields */
    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "state_province")
    private String stateProvince;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "country")
    private String country;

    /** Reason for requesting access */
    @Column(name = "reason_for_access", columnDefinition = "TEXT")
    private String reasonForAccess;

    @Column(name = "data_holder_group_id")
    private Long dataHolderGroupId;

    /** Registration status */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RegistrationStatus status = RegistrationStatus.PENDING;

    /** Admin review notes */
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = false;

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

    public enum RegistrationStatus {
        PENDING,
        ACCEPTED,
        DENIED,
        SUSPENDED
    }
}
