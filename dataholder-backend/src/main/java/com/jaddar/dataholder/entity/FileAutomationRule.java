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
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * User-defined automation rules for handling files attached to RDAP requests.
 * 
 * Rules are evaluated in priority order when a file is received with a request.
 * Each rule specifies conditions (file type, size, etc.) and actions (auto-approve,
 * auto-deny, flag for review, etc.)
 */
@Entity
@Table(name = "file_automation_rules", indexes = {
    @Index(name = "idx_file_auto_rule_enabled", columnList = "enabled"),
    @Index(name = "idx_file_auto_rule_priority", columnList = "priority")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileAutomationRule {

    public enum Action {
        AUTO_APPROVE,     // Auto-approve the request
        AUTO_DENY,        // Auto-deny the request
        FLAG_FOR_REVIEW,  // Flag for manual review with a note
        REQUIRE_SCAN,     // Require deep security scan before proceeding
        QUARANTINE        // Quarantine the file and hold the request
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable name for this rule
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Description of what this rule does
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Whether this rule is active
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Priority order (lower = higher priority). Rules are evaluated top-down.
     */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 100;

    // ==================== CONDITIONS ====================

    /**
     * File types this rule applies to (comma-separated: PDF,DOCX,XLSX,TXT,JPEG,PNG)
     * Null or empty = matches all file types
     */
    @Column(name = "file_types", length = 100)
    private String fileTypes;

    /**
     * Maximum file size in bytes. If file exceeds this, the rule triggers.
     * Null = no size condition
     */
    @Column(name = "max_file_size_bytes")
    private Long maxFileSizeBytes;

    /**
     * Minimum file size in bytes. If file is smaller than this, the rule triggers.
     * Null = no minimum size condition
     */
    @Column(name = "min_file_size_bytes")
    private Long minFileSizeBytes;

    /**
     * Filename pattern to match (supports * wildcards)
     * Null = matches all filenames
     */
    @Column(name = "filename_pattern", length = 200)
    private String filenamePattern;

    /**
     * RDAP query types this rule applies to (comma-separated: domain,ip,asn)
     * Null or empty = matches all query types
     */
    @Column(name = "query_types", length = 50)
    private String queryTypes;

    /**
     * Only apply to requests from specific requestor groups (comma-separated)
     * Null = matches all groups
     */
    @Column(name = "requestor_groups", length = 500)
    private String requestorGroups;

    // ==================== ACTION ====================

    /**
     * What action to take when conditions are met
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    @Builder.Default
    private Action action = Action.FLAG_FOR_REVIEW;

    /**
     * Reason/message to include with the action (e.g., denial reason)
     */
    @Column(name = "action_message", length = 1000)
    private String actionMessage;

    /**
     * Additional action configuration as JSON
     */
    @Column(name = "action_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> actionConfig;

    // ==================== METADATA ====================

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * How many times this rule has been triggered
     */
    @Column(name = "trigger_count", nullable = false)
    @Builder.Default
    private Long triggerCount = 0L;

    /**
     * Last time this rule was triggered
     */
    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (enabled == null) enabled = true;
        if (priority == null) priority = 100;
        if (triggerCount == null) triggerCount = 0L;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Increment the trigger count
     */
    public void recordTrigger() {
        triggerCount++;
        lastTriggeredAt = LocalDateTime.now();
    }
}
