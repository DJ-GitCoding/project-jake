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
 * Join entity: a user can be a member of multiple data holder groups.
 * Master users (type=1) implicitly have access to all groups, but this table
 * tracks explicit memberships for Admin and User type accounts.
 */
@Entity
@Table(name = "user_group_memberships",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "data_holder_group_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserGroupMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "data_holder_group_id", nullable = false)
    private Long dataHolderGroupId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
