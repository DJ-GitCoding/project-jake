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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "rdap_ips")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RdapIp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String handle;

    @Column(name = "start_address", nullable = false)
    private String startAddress;

    @Column(name = "end_address")
    private String endAddress;

    @Column(name = "ip_version")
    private String ipVersion;

    private String name;

    private String country;

    @Column(name = "registration_date")
    private LocalDateTime registrationDate;

    @Column(name = "last_changed_date")
    private LocalDateTime lastChangedDate;

    @Column(name = "rdap_data_level_0", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> rdapDataLevel0;

    @Column(name = "rdap_data_level_1", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> rdapDataLevel1;

    @Column(name = "rdap_data_level_2", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> rdapDataLevel2;

    @Column(name = "rdap_data_level_3", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> rdapDataLevel3;

    /**
     * Optional test data flag.
     * When set and isTestData=true, marks this IP as test data.
     * Nullable - defaults to real data if not set.
     */
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "test_data_flag_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private TestDataFlag testDataFlag;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Get RDAP data for the specified access level
     */
    public Map<String, Object> getRdapDataForLevel(int level) {
        return switch (level) {
            case 0 -> rdapDataLevel0;
            case 1 -> rdapDataLevel1;
            case 2 -> rdapDataLevel2;
            case 3 -> rdapDataLevel3;
            default -> rdapDataLevel0;
        };
    }
}