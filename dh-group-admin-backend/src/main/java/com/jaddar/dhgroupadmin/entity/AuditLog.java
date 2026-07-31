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
 * Audit log entry for tracking all system events:
 * CRUD operations, API calls, authentication, credential actions, etc.
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_entity_type", columnList = "entity_type"),
    @Index(name = "idx_audit_performed_by", columnList = "performed_by"),
    @Index(name = "idx_audit_created_at", columnList = "created_at"),
    @Index(name = "idx_audit_category", columnList = "category")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Category of action: AUTH, CRUD, API, CREDENTIAL, WORKFLOW, SYSTEM
     */
    @Column(name = "category", nullable = false, length = 32)
    private String category;

    /**
     * The action performed: LOGIN, LOGIN_FAILED, CREATE, UPDATE, DELETE,
     * PUBLISH, UNPUBLISH, APPROVE, DENY, ACTIVATE, SUSPEND, REACTIVATE,
     * START_TEST, RECORD_TEST, REVEAL_CREDENTIALS, REGENERATE_CREDENTIALS,
     * REGISTER, ACCEPT, PASSWORD_CHANGE, INITIATE_SUBSCRIPTION, etc.
     */
    @Column(name = "action", nullable = false, length = 64)
    private String action;

    /**
     * The entity type being acted upon: USER, TEMPLATE, SUBSCRIPTION,
     * DATA_HOLDER, REQUESTOR_GROUP, CREDENTIAL, SYSTEM
     */
    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    /**
     * The ID of the entity (DB id or business ID like dataholderId, requestId, etc.)
     */
    @Column(name = "entity_id", length = 255)
    private String entityId;

    /**
     * Human-readable name/label of the entity (e.g., template name, user email)
     */
    @Column(name = "entity_name", length = 255)
    private String entityName;

    /**
     * Who performed the action (email, username, "SYSTEM", "EXTERNAL", etc.)
     */
    @Column(name = "performed_by", nullable = false, length = 255)
    private String performedBy;

    /**
     * Source of the action: ADMIN_UI, EXTERNAL_API, SYSTEM, AUTH
     */
    @Column(name = "source", length = 32)
    @Builder.Default
    private String source = "ADMIN_UI";

    /**
     * Result: SUCCESS, FAILURE, DENIED
     */
    @Column(name = "result", nullable = false, length = 16)
    @Builder.Default
    private String result = "SUCCESS";

    /**
     * Additional details/notes about the action
     */
    @Column(name = "details", length = 4000)
    private String details;

    /**
     * IP address of the requester (if available)
     */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
