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
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks a provisioned data holder Docker instance.
 * Created when the master admin spins up a new DH from the Jareg UI.
 *
 * An instance may be associated with multiple data holder groups.
 */
@Entity
@Table(name = "data_holder_instances", indexes = {
    @Index(name = "idx_dhi_subdomain", columnList = "subdomain", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DataHolderInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subdomain", unique = true, nullable = false, length = 30)
    private String subdomain;

    @Column(name = "name", nullable = false)
    private String name;

    /** The DH ID registered in the data_holders table, if linked. */
    @Column(name = "dataholder_id")
    private String dataholderId;

    /** Legacy single-group field. Kept for backward compatibility. */
    @Column(name = "data_holder_group_id")
    private Long dataHolderGroupId;

    /**
     * The set of groups this instance is associated with.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "data_holder_instance_group_memberships",
        joinColumns = @JoinColumn(name = "instance_id"),
        inverseJoinColumns = @JoinColumn(name = "data_holder_group_id")
    )
    @Builder.Default
    private Set<DataHolderGroup> dataHolderGroups = new HashSet<>();

    @Column(name = "backend_port")
    private Integer backendPort;

    @Column(name = "frontend_port")
    private Integer frontendPort;

    @Column(name = "backend_container")
    private String backendContainer;

    @Column(name = "frontend_container")
    private String frontendContainer;

    @Column(name = "database_name")
    private String databaseName;

    @Column(name = "admin_username")
    @Builder.Default
    private String adminUsername = "admin";

    @Column(name = "admin_password")
    private String adminPassword;

    /** running, stopped, destroyed */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "running";

    @Column(name = "url")
    private String url;

    @Column(name = "backend_image")
    private String backendImage;

    @Column(name = "frontend_image")
    private String frontendImage;

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
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== Multi-group helpers ====================

    public Set<Long> getAllGroupIds() {
        Set<Long> ids = new HashSet<>();
        if (dataHolderGroups != null) {
            dataHolderGroups.forEach(g -> ids.add(g.getId()));
        }
        if (ids.isEmpty() && dataHolderGroupId != null) {
            ids.add(dataHolderGroupId);
        }
        return ids;
    }

    public boolean belongsToGroup(Long groupId) {
        return getAllGroupIds().contains(groupId);
    }
}
