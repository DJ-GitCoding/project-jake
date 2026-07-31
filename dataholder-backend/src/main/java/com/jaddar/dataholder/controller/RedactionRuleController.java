/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import com.jaddar.dataholder.entity.RedactionRule;
import com.jaddar.dataholder.entity.RedactionRule.RedactionBehavior;
import com.jaddar.dataholder.service.RedactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/redaction-rules")
@RequiredArgsConstructor
@Slf4j
public class RedactionRuleController {

    private final RedactionService redactionService;

    /**
     * Get all redaction rules (the 32-row matrix).
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllRules() {
        try {
            List<RedactionRule> rules = redactionService.getAllRules();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "rules", rules.stream().map(this::ruleToMap).collect(Collectors.toList()),
                    "total", rules.size(),
                    "behaviors", Arrays.stream(RedactionBehavior.values())
                            .map(b -> Map.of(
                                    "value", b.name(),
                                    "label", formatBehaviorLabel(b),
                                    "description", getBehaviorDescription(b)
                            ))
                            .collect(Collectors.toList())
            ));
        } catch (Exception e) {
            log.error("Failed to get redaction rules", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "The request could not be completed. Please try again or contact support."
            ));
        }
    }

    /**
     * Get a single rule by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getRule(@PathVariable Long id) {
        try {
            return redactionService.getRuleById(id)
                    .map(rule -> ResponseEntity.ok(Map.of(
                            "success", (Object) true,
                            "rule", ruleToMap(rule)
                    )))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Failed to get rule {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "The request could not be completed. Please try again or contact support."
            ));
        }
    }

    /**
     * Update an existing rule (change behavior or active status).
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateRule(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            RedactionRule updates = RedactionRule.builder().build();

            if (request.containsKey("redactionBehavior")) {
                updates.setRedactionBehavior(RedactionBehavior.valueOf((String) request.get("redactionBehavior")));
            }
            if (request.containsKey("isActive")) {
                updates.setIsActive((Boolean) request.get("isActive"));
            }

            RedactionRule updated = redactionService.updateRule(id, updates);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Rule updated successfully",
                    "rule", ruleToMap(updated)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to update rule {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "The request could not be completed. Please try again or contact support."
            ));
        }
    }

    /**
     * Toggle rule active status.
     */
    @PostMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleRule(@PathVariable Long id) {
        try {
            redactionService.toggleRuleActive(id);
            return redactionService.getRuleById(id)
                    .map(rule -> ResponseEntity.ok(Map.of(
                            "success", (Object) true,
                            "message", "Rule toggled successfully",
                            "rule", ruleToMap(rule)
                    )))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Failed to toggle rule {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "The request could not be completed. Please try again or contact support."
            ));
        }
    }

    /**
     * Bulk update behaviors for multiple rules.
     */
    @PostMapping("/bulk-update")
    public ResponseEntity<Map<String, Object>> bulkUpdate(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Number> ruleIds = (List<Number>) request.get("ruleIds");
            String behaviorStr = (String) request.get("redactionBehavior");

            if (ruleIds == null || ruleIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "ruleIds is required"
                ));
            }

            RedactionBehavior behavior = RedactionBehavior.valueOf(behaviorStr);
            List<Long> ids = ruleIds.stream().map(Number::longValue).collect(Collectors.toList());

            redactionService.bulkUpdateBehavior(ids, behavior);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Updated " + ids.size() + " rules"
            ));
        } catch (Exception e) {
            log.error("Failed to bulk update rules", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "The request could not be completed. Please try again or contact support."
            ));
        }
    }

    /**
     * Initialize the default 32-row matrix.
     */
    @PostMapping("/initialize")
    public ResponseEntity<Map<String, Object>> initializeRules() {
        try {
            redactionService.initializeDefaultRules();
            List<RedactionRule> rules = redactionService.getAllRules();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Default rules initialized",
                    "count", rules.size()
            ));
        } catch (Exception e) {
            log.error("Failed to initialize rules", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "The request could not be completed. Please try again or contact support."
            ));
        }
    }

    /**
     * Refresh the rule cache.
     */
    @PostMapping("/refresh-cache")
    public ResponseEntity<Map<String, Object>> refreshCache() {
        try {
            redactionService.refreshRuleCache();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Rule cache refreshed"
            ));
        } catch (Exception e) {
            log.error("Failed to refresh cache", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "The request could not be completed. Please try again or contact support."
            ));
        }
    }

    /**
     * Get metadata for dropdowns.
     */
    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getMetadata() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "behaviors", Arrays.stream(RedactionBehavior.values())
                        .map(b -> Map.of(
                                "value", b.name(),
                                "label", formatBehaviorLabel(b),
                                "description", getBehaviorDescription(b)
                        ))
                        .collect(Collectors.toList()),
                "levels", List.of(
                        Map.of("value", 0, "label", "Level 0 - Public"),
                        Map.of("value", 1, "label", "Level 1 - Basic"),
                        Map.of("value", 2, "label", "Level 2 - Enhanced"),
                        Map.of("value", 3, "label", "Level 3 - Full")
                )
        ));
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> ruleToMap(RedactionRule rule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rule.getId());
        map.put("accessLevel", rule.getAccessLevel());
        map.put("sensitivityLevel", rule.getSensitivityLevel());
        map.put("emptyValue", rule.getEmptyValue());
        map.put("redactionBehavior", rule.getRedactionBehavior().name());
        map.put("isActive", rule.getIsActive());
        map.put("createdAt", rule.getCreatedAt() != null ? rule.getCreatedAt().toString() : null);
        map.put("updatedAt", rule.getUpdatedAt() != null ? rule.getUpdatedAt().toString() : null);
        return map;
    }

    private String formatBehaviorLabel(RedactionBehavior behavior) {
        return switch (behavior) {
            case FULL -> "Return Full Value";
            case EMPTY -> "Return Nothing";
            case REDACTED -> "Return as REDACTED";
        };
    }

    private String getBehaviorDescription(RedactionBehavior behavior) {
        return switch (behavior) {
            case FULL -> "Return the actual data value to the requester";
            case EMPTY -> "Return nothing — the field is omitted from the response";
            case REDACTED -> "Return the literal string \"REDACTED\" in place of the actual value";
        };
    }
}
