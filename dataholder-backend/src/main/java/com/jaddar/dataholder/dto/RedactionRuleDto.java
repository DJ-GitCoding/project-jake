/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.dto;

import com.jaddar.dataholder.entity.RedactionRule;
import com.jaddar.dataholder.entity.RedactionRule.RedactionBehavior;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * DTO for RedactionRule entity (matrix-based model).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedactionRuleDto {

    private Long id;
    private Integer accessLevel;
    private Integer sensitivityLevel;
    private Boolean emptyValue;
    private String redactionBehavior;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Display labels
    private String accessLevelLabel;
    private String sensitivityLevelLabel;
    private String emptyValueLabel;
    private String redactionBehaviorLabel;

    /**
     * Create DTO from entity.
     */
    public static RedactionRuleDto fromEntity(RedactionRule rule) {
        return RedactionRuleDto.builder()
                .id(rule.getId())
                .accessLevel(rule.getAccessLevel())
                .sensitivityLevel(rule.getSensitivityLevel())
                .emptyValue(rule.getEmptyValue())
                .redactionBehavior(rule.getRedactionBehavior().name())
                .isActive(rule.getIsActive())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .accessLevelLabel(getLevelLabel(rule.getAccessLevel()))
                .sensitivityLevelLabel(getLevelLabel(rule.getSensitivityLevel()))
                .emptyValueLabel(rule.getEmptyValue() ? "Empty" : "Has Value")
                .redactionBehaviorLabel(getBehaviorLabel(rule.getRedactionBehavior()))
                .build();
    }

    /**
     * Convert DTO to entity.
     */
    public RedactionRule toEntity() {
        return RedactionRule.builder()
                .id(id)
                .accessLevel(accessLevel)
                .sensitivityLevel(sensitivityLevel)
                .emptyValue(emptyValue)
                .redactionBehavior(redactionBehavior != null ? RedactionBehavior.valueOf(redactionBehavior) : RedactionBehavior.FULL)
                .isActive(isActive)
                .build();
    }

    private static String getLevelLabel(Integer level) {
        if (level == null) return "Unknown";
        return switch (level) {
            case 0 -> "Level 0 - Public";
            case 1 -> "Level 1 - Basic";
            case 2 -> "Level 2 - Enhanced";
            case 3 -> "Level 3 - Full";
            default -> "Level " + level;
        };
    }

    private static String getBehaviorLabel(RedactionBehavior behavior) {
        if (behavior == null) return "Unknown";
        return switch (behavior) {
            case FULL -> "Return Full Value";
            case EMPTY -> "Return Nothing";
            case REDACTED -> "Return as REDACTED";
        };
    }
}
