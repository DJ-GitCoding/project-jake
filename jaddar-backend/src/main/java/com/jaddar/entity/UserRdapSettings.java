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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * User-specific settings for RDAP requests. Ported from {@code models.database.UserRdapSettings}.
 */
@Getter
@Setter
@Entity
@Table(name = "user_rdap_settings")
public class UserRdapSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_sub", unique = true, nullable = false, length = 255)
    private String userSub;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "default_poll_interval_ms", nullable = false)
    private Integer defaultPollIntervalMs = 30000;

    @Column(name = "auto_poll_enabled", nullable = false)
    private Boolean autoPollEnabled = true;

    @Column(name = "notify_on_approval", nullable = false)
    private Boolean notifyOnApproval = true;

    @Column(name = "notify_on_denial", nullable = false)
    private Boolean notifyOnDenial = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
