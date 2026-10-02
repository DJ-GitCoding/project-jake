/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Marks RDAP data records as test data or real data.
 * This allows filtering and identifying test records in the system.
 */
@Entity
@Table(name = "test_data_flags")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TestDataFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * If true, the associated RDAP data is test data.
     * If false, it is considered real/production data.
     */
    @Column(name = "is_test_data", nullable = false)
    @Builder.Default
    private Boolean isTestData = false;

    /**
     * Optional description or reason for the test data flag
     */
    @Column(name = "description")
    private String description;

    /**
     * Optional label for grouping test data (e.g., "load-test", "integration-test", "demo")
     */
    @Column(name = "test_label")
    private String testLabel;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isTestData == null) isTestData = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}