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
 * Represents a Data Holder Group — the multi-tenant grouping entity.
 * Data holders join a specific group, and admin users can be members of multiple groups.
 */
@Entity
@Table(name = "data_holder_groups")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DataHolderGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /** How far this group is advertised, and the ceiling on how far its templates can be. */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", length = 20)
    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;

    // ==================== Default Contact Information ====================

    @Column(name = "default_contact_first_name")
    private String defaultContactFirstName;

    @Column(name = "default_contact_last_name")
    private String defaultContactLastName;

    @Column(name = "default_contact_email")
    private String defaultContactEmail;

    /** Optional: an agreement contact is reachable by email; a phone number is a courtesy. */
    @Column(name = "default_contact_phone")
    private String defaultContactPhone;

    @Column(name = "default_address")
    private String defaultAddress;

    @Column(name = "default_city")
    private String defaultCity;

    @Column(name = "default_state_province")
    private String defaultStateProvince;

    @Column(name = "default_postal_code")
    private String defaultPostalCode;

    @Column(name = "default_country")
    private String defaultCountry;

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

    /** Legacy alias for the boolean used before this group gained visibility levels. */
    @Transient
    public Boolean getIsPrivate() { return visibility == Visibility.PRIVATE; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataHolderGroup that = (DataHolderGroup) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }
}