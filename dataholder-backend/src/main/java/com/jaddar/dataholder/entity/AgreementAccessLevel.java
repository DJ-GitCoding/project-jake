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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "agreement_access_levels")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgreementAccessLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agreement_id", nullable = false, unique = true)
    private Integer agreementId;

    @Column(name = "agreement_name", nullable = false, unique = true)
    private String agreementName;

    @Column(name = "access_level", nullable = false)
    private Integer accessLevel;

    @Column(name = "allowed_groups", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] allowedGroups;

    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "is_active")
    private Boolean isActive = true;

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
     * Check if a user with the given groups is allowed to use this agreement
     */
    public boolean isAllowedForGroups(List<String> userGroups) {
        // If no group restrictions, allow all
        if (allowedGroups == null || allowedGroups.length == 0) {
            return true;
        }
        
        // If user has no groups, deny
        if (userGroups == null || userGroups.isEmpty()) {
            return false;
        }
        
        // Check if any user group matches allowed groups (case-insensitive)
        for (String allowedGroup : allowedGroups) {
            for (String userGroup : userGroups) {
                if (userGroup.equalsIgnoreCase(allowedGroup)) {
                    return true;
                }
            }
        }
        
        return false;
    }
}