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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Single-row global configuration for the data holder application.
 * Always has exactly one row (id=1), created automatically on first access.
 */
@Entity
@Table(name = "dataholder_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataHolderConfig {

    @Id
    private Long id;

    /**
     * When true, ALL incoming RDAP requests require manual review
     * regardless of policy expressions, access levels, or per-entity controls.
     * Exigent requests still bypass this if allowed by the exigent override.
     */
    @Column(name = "require_manual_review_all", nullable = false)
    @Builder.Default
    private Boolean requireManualReviewAll = false;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * Get the singleton config instance, creating it if necessary.
     */
    public static final Long SINGLETON_ID = 1L;
}
