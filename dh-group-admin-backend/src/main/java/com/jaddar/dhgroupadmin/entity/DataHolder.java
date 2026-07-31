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
 * Represents a registered data holder in the group admin system.
 * Data holders register themselves so they can pull templates and subscriptions.
 *
 * A data holder may belong to MULTIPLE data holder groups simultaneously,
 * each with its own agreement templates and subscriptions.
 */
@Entity
@Table(name = "data_holders", indexes = {
    @Index(name = "idx_dh_dataholder_id", columnList = "dataholder_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DataHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataholder_id", unique = true, nullable = false)
    private String dataholderId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "url")
    private String url;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "callback_url")
    private String callbackUrl;

    /**
     * Legacy single-group field. Kept for backward compatibility and migration.
     * New code should use the {@link #dataHolderGroups} relationship instead.
     * On read, if groups is empty but this is set, it will be used as a fallback.
     */
    @Column(name = "data_holder_group_id")
    private Long dataHolderGroupId;

    /**
     * The set of groups this data holder belongs to.
     * A data holder can be a member of multiple DHGs, each with its own templates.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "data_holder_group_memberships",
        joinColumns = @JoinColumn(name = "data_holder_id"),
        inverseJoinColumns = @JoinColumn(name = "data_holder_group_id")
    )
    @Builder.Default
    private Set<DataHolderGroup> dataHolderGroups = new HashSet<>();

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_ping_at")
    private LocalDateTime lastPingAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (dataholderId == null) {
            dataholderId = "DH-" + System.currentTimeMillis();
        }
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    // ==================== Multi-group helpers ====================

    /**
     * Get all group IDs this data holder belongs to.
     * Falls back to the legacy single dataHolderGroupId if the join table is empty.
     */
    public Set<Long> getAllGroupIds() {
        Set<Long> ids = new HashSet<>();
        if (dataHolderGroups != null) {
            dataHolderGroups.forEach(g -> ids.add(g.getId()));
        }
        // Backward compat: include legacy field if not already present
        if (ids.isEmpty() && dataHolderGroupId != null) {
            ids.add(dataHolderGroupId);
        }
        return ids;
    }

    /**
     * Check if this data holder belongs to a specific group.
     */
    public boolean belongsToGroup(Long groupId) {
        return getAllGroupIds().contains(groupId);
    }

    /**
     * Add a group membership.
     */
    public void addGroup(DataHolderGroup group) {
        if (dataHolderGroups == null) dataHolderGroups = new HashSet<>();
        dataHolderGroups.add(group);
        // Keep legacy field in sync with the first/primary group
        if (dataHolderGroupId == null) {
            dataHolderGroupId = group.getId();
        }
    }

    /**
     * Remove a group membership.
     */
    public void removeGroup(DataHolderGroup group) {
        if (dataHolderGroups != null) {
            dataHolderGroups.remove(group);
        }
        // Update legacy field
        if (group.getId().equals(dataHolderGroupId)) {
            dataHolderGroupId = dataHolderGroups != null && !dataHolderGroups.isEmpty()
                    ? dataHolderGroups.iterator().next().getId()
                    : null;
        }
    }
}
