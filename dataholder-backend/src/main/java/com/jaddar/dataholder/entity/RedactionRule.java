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

import java.time.LocalDateTime;

/**
 * Defines redaction rules as a matrix of conditions.
 *
 * Each rule is triggered by a unique combination of:
 *   - Request Type's Access Level (0-3)
 *   - Policy's Sensitivity Level (0-3)
 *   - Whether the data element's value is empty or not
 *
 * This yields 4 × 4 × 2 = 32 possible rule rows.
 *
 * The behavior column specifies what to return when the condition matches:
 *   - FULL:     return the actual value
 *   - EMPTY:    return nothing (omit the field)
 *   - REDACTED: return the string "REDACTED"
 */
@Entity
@Table(name = "redaction_rules",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_redaction_rule_combo",
                columnNames = {"access_level", "sensitivity_level", "value_is_empty"}
        ),
        indexes = {
                @Index(name = "idx_redaction_rules_active", columnList = "is_active")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedactionRule {

    /**
     * What the system returns when this rule matches.
     */
    public enum RedactionBehavior {
        FULL,      // Return the actual value
        EMPTY,     // Return nothing / omit the field
        REDACTED   // Return the literal string "REDACTED"
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The request type's access level (0-3).
     */
    @Column(name = "access_level", nullable = false)
    private Integer accessLevel;

    /**
     * The policy's sensitivity level for the data (0-3).
     */
    @Column(name = "sensitivity_level", nullable = false)
    private Integer sensitivityLevel;

    /**
     * Whether this rule applies when the data element's value is empty (true) or populated (false).
     */
    @Column(name = "value_is_empty", nullable = false)
    private Boolean emptyValue;

    /**
     * What to do when this combination is encountered.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "redaction_behavior", nullable = false)
    @Builder.Default
    private RedactionBehavior redactionBehavior = RedactionBehavior.FULL;

    /**
     * Whether this rule is currently active.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
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
     * Determine the output for a given value based on this rule's behavior.
     * Returns null when the behavior is EMPTY (caller should omit the field).
     */
    public Object getOutputValue(Object actualValue) {
        return switch (redactionBehavior) {
            case FULL -> actualValue;
            case REDACTED -> "REDACTED";
            case EMPTY -> null;
        };
    }
}
