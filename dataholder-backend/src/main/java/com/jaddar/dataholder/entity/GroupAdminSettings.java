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

/**
 * Stores connection settings for a Data Holder Group Admin.
 * Multiple active records may exist — one per group admin the data holder has joined.
 */
@Entity
@Table(name = "group_admin_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GroupAdminSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable label for this connection, e.g. "Registry Group Admin" */
    @Column(name = "name")
    private String name;

    /** Base URL of the DH Group Admin API, e.g. http://dh-group-admin-backend:8083/dh-group-admin */
    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    /** Client ID issued by the Group Admin upon acceptance */
    @Column(name = "client_id", nullable = false)
    private String clientId;

    /** Client secret issued by the Group Admin upon acceptance */
    @Column(name = "client_secret", nullable = false)
    private String clientSecret;

    /** Whether this connection is enabled */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /** Last time a successful connection was made */
    @Column(name = "last_connected_at")
    private LocalDateTime lastConnectedAt;

    /** Last connection error message (null if healthy) */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

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
}
