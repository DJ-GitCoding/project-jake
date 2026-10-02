/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "rdrs_mail_settings")
public class RdrsMailSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "last_polled_at")
    private Instant lastPolledAt;

    @Column(name = "last_poll_status", length = 32)
    private String lastPollStatus;

    @Column(name = "last_poll_message", length = 1024)
    private String lastPollMessage;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
