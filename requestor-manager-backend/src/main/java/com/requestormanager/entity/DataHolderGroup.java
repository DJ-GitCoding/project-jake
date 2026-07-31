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
 * Represents a registered Data Holder Group that the Requestor Manager can subscribe to.
 * The baseUrl points to the DH Group Admin application (not individual data holders).
 * Templates and subscriptions are managed centrally by the DH Group Admin.
 */
@Entity
@Table(name = "data_holder_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataHolderGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier code for the data holder group (e.g., "ARIN-GRP", "RIPE-GRP")
     */
    @Column(nullable = false, unique = true)
    private String code;

    /**
     * Display name of the data holder group
     */
    @Column(nullable = false)
    private String name;

    /**
     * Description of the data holder group
     */
    @Column(length = 1000)
    private String description;

    /**
     * Base URL of the DH Group Admin API (e.g., "http://dh-group-admin-backend:8083/dh-group-admin")
     */
    @Column(nullable = false)
    private String baseUrl;

    /**
     * Whether this data holder group is currently active/enabled
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Last time we successfully contacted this data holder group
     */
    @Column
    private LocalDateTime lastContactAt;

    /**
     * Last health check status
     */
    @Column
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;

    /**
     * Contact email for the data holder group administrator
     */
    @Column
    private String contactEmail;

    /**
     * Notes or additional information
     */
    @Column(length = 2000)
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Health status of the data holder group connection
     */
    public enum HealthStatus {
        HEALTHY,
        UNHEALTHY,
        UNKNOWN
    }

    /**
     * Get the full URL for the external API.
     * The DH Group Admin public endpoints are at /api/external/
     */
    public String getExternalApiUrl() {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/api/external";
    }
}
