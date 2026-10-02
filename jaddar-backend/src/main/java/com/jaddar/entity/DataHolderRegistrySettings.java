/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Deployment-wide settings for the external Data Holder Registry; one row, id 1. */
@Getter
@Setter
@Entity
@Table(name = "data_holder_registry_settings")
public class DataHolderRegistrySettings {

    @Id
    @Column(name = "id")
    private Long id = 1L;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Column(name = "registry_url", length = 1024)
    private String registryUrl;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    /** "SUCCESS" or "FAILURE" for the last fetch attempt. */
    @Column(name = "last_sync_status", length = 32)
    private String lastSyncStatus;

    @Column(name = "last_sync_message", length = 1024)
    private String lastSyncMessage;

    @Column(name = "holder_count")
    private Integer holderCount;

    @Column(name = "feed_publication", length = 64)
    private String feedPublication;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
