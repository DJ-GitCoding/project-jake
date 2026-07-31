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
 * Logs all status changes for agreement subscriptions.
 * Provides audit trail for subscription lifecycle.
 */
@Entity
@Table(name = "agreement_status_logs", indexes = {
    @Index(name = "idx_ga_status_log_subscription", columnList = "subscription_id"),
    @Index(name = "idx_ga_status_log_created_at", columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgreementStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private AgreementSubscription subscription;

    @Column(name = "previous_status") private String previousStatus;
    @Column(name = "new_status", nullable = false) private String newStatus;
    @Column(name = "changed_by") private String changedBy;
    @Column(name = "change_reason", columnDefinition = "TEXT") private String changeReason;
    @Column(name = "source") private String source;
    @Column(name = "ip_address") private String ipAddress;
    @Column(name = "metadata", columnDefinition = "TEXT") private String metadata;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
