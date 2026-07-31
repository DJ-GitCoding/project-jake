/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An admin-defined contact role (e.g. "Account Holder") that extends the fixed
 * set of built-in RDAP roles (registrant, administrative, technical, billing, abuse).
 *
 * <p>Policy imports map each contact group in the source file to a role. Groups that
 * do not match a built-in role must match an active custom role defined here, otherwise
 * the import is blocked. The {@code roleKey} becomes the prefix of the generated RDAP
 * field paths (e.g. {@code accountHolder.fn}), which keeps each role's fields distinct.
 */
@Entity
@Table(name = "custom_contact_role",
       uniqueConstraints = @UniqueConstraint(name = "uk_custom_contact_role_key", columnNames = {"role_key"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomContactRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Normalized, field-path-safe identifier used as the RDAP field-path prefix,
     * e.g. "accountHolder". Unique (case-insensitively enforced by the service).
     */
    @Column(name = "role_key", nullable = false)
    private String roleKey;

    /**
     * Human-readable label shown in the UI and matched against import group names,
     * e.g. "Account Holder".
     */
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
