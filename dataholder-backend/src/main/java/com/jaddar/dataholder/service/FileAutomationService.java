/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.entity.FileAttachment;
import com.jaddar.dataholder.entity.FileAutomationRule;
import com.jaddar.dataholder.repository.FileAutomationRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing and evaluating file automation rules.
 * Rules are evaluated in priority order and the first matching rule's action is applied.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileAutomationService {

    private final FileAutomationRuleRepository ruleRepository;

    /**
     * Result of evaluating a file against automation rules
     */
    public record EvaluationResult(
        boolean matched,
        FileAutomationRule matchedRule,
        FileAutomationRule.Action action,
        String message
    ) {
        public static EvaluationResult noMatch() {
            return new EvaluationResult(false, null, null, "No automation rules matched");
        }

        public static EvaluationResult match(FileAutomationRule rule) {
            return new EvaluationResult(true, rule, rule.getAction(), rule.getActionMessage());
        }
    }

    /**
     * Evaluate a file against all active automation rules (in priority order).
     * Returns the first matching rule's action.
     */
    @Transactional
    public EvaluationResult evaluate(FileAttachment file, String queryType, String[] requestorGroups) {
        List<FileAutomationRule> rules = ruleRepository.findByEnabledTrueOrderByPriorityAsc();

        for (FileAutomationRule rule : rules) {
            if (matchesRule(rule, file, queryType, requestorGroups)) {
                rule.recordTrigger();
                ruleRepository.save(rule);
                log.info("File automation rule '{}' (id={}) triggered for file '{}': action={}",
                    rule.getName(), rule.getId(), file.getOriginalFilename(), rule.getAction());
                return EvaluationResult.match(rule);
            }
        }

        return EvaluationResult.noMatch();
    }

    /**
     * Check if a specific rule matches the given file and context
     */
    private boolean matchesRule(FileAutomationRule rule, FileAttachment file,
                                 String queryType, String[] requestorGroups) {
        // Check file type condition
        if (rule.getFileTypes() != null && !rule.getFileTypes().isBlank()) {
            Set<String> allowedTypes = Arrays.stream(rule.getFileTypes().split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
            if (!allowedTypes.contains(file.getFileType().name())) {
                return false;
            }
        }

        // Check max file size condition
        if (rule.getMaxFileSizeBytes() != null && file.getFileSize() > rule.getMaxFileSizeBytes()) {
            // This rule has a max size and the file exceeds it - rule matches if this is the intent
            // (The rule triggers WHEN file is over the limit)
        } else if (rule.getMaxFileSizeBytes() != null) {
            // File is within limit, so this size-based rule doesn't trigger
            return false;
        }

        // Check min file size condition
        if (rule.getMinFileSizeBytes() != null && file.getFileSize() < rule.getMinFileSizeBytes()) {
            // File is under minimum - rule triggers
        } else if (rule.getMinFileSizeBytes() != null) {
            return false;
        }

        // Check filename pattern
        if (rule.getFilenamePattern() != null && !rule.getFilenamePattern().isBlank()) {
            String pattern = rule.getFilenamePattern()
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
            if (!file.getOriginalFilename().matches("(?i)" + pattern)) {
                return false;
            }
        }

        // Check query type condition
        if (rule.getQueryTypes() != null && !rule.getQueryTypes().isBlank()) {
            Set<String> allowedQueryTypes = Arrays.stream(rule.getQueryTypes().split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
            if (queryType != null && !allowedQueryTypes.contains(queryType.toLowerCase())) {
                return false;
            }
        }

        // Check requestor groups condition
        if (rule.getRequestorGroups() != null && !rule.getRequestorGroups().isBlank()) {
            Set<String> allowedGroups = Arrays.stream(rule.getRequestorGroups().split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
            if (requestorGroups == null || requestorGroups.length == 0) {
                return false;
            }
            boolean groupMatch = Arrays.stream(requestorGroups)
                .anyMatch(allowedGroups::contains);
            if (!groupMatch) {
                return false;
            }
        }

        // All conditions passed (or no conditions set = match all)
        return true;
    }

    // ==================== CRUD Operations ====================

    public List<FileAutomationRule> getAllRules() {
        return ruleRepository.findAllByOrderByPriorityAsc();
    }

    public Optional<FileAutomationRule> getRule(Long id) {
        return ruleRepository.findById(id);
    }

    @Transactional
    public FileAutomationRule createRule(FileAutomationRule rule) {
        return ruleRepository.save(rule);
    }

    @Transactional
    public FileAutomationRule updateRule(Long id, FileAutomationRule updates) {
        FileAutomationRule existing = ruleRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));

        existing.setName(updates.getName());
        existing.setDescription(updates.getDescription());
        existing.setEnabled(updates.getEnabled());
        existing.setPriority(updates.getPriority());
        existing.setFileTypes(updates.getFileTypes());
        existing.setMaxFileSizeBytes(updates.getMaxFileSizeBytes());
        existing.setMinFileSizeBytes(updates.getMinFileSizeBytes());
        existing.setFilenamePattern(updates.getFilenamePattern());
        existing.setQueryTypes(updates.getQueryTypes());
        existing.setRequestorGroups(updates.getRequestorGroups());
        existing.setAction(updates.getAction());
        existing.setActionMessage(updates.getActionMessage());
        existing.setActionConfig(updates.getActionConfig());
        existing.setUpdatedBy(updates.getUpdatedBy());

        return ruleRepository.save(existing);
    }

    @Transactional
    public void deleteRule(Long id) {
        if (!ruleRepository.existsById(id)) {
            throw new IllegalArgumentException("Rule not found: " + id);
        }
        ruleRepository.deleteById(id);
    }

    @Transactional
    public FileAutomationRule toggleRule(Long id) {
        FileAutomationRule rule = ruleRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));
        rule.setEnabled(!rule.getEnabled());
        return ruleRepository.save(rule);
    }
}
