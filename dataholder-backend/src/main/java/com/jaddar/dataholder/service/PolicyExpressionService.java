/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.dto.PolicyExpressionDto.*;
import com.jaddar.dataholder.entity.PolicyExpression;
import com.jaddar.dataholder.entity.PolicyRedactionRule;
import com.jaddar.dataholder.entity.PolicyRedactionRule.RdapObjectType;
import com.jaddar.dataholder.repository.CustomContactRoleRepository;
import com.jaddar.dataholder.repository.PolicyExpressionRepository;
import com.jaddar.dataholder.repository.PolicyRedactionRuleRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyExpressionService {

    private final PolicyExpressionRepository repository;
    private final PolicyRedactionRuleRepository redactionRuleRepository;
    private final CustomContactRoleRepository customContactRoleRepository;
    private final EntityManager entityManager;

    public List<PolicyExpressionResponse> getAllPolicyExpressions() {
        return repository.findAllByOrderByIdDesc().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public List<PolicyExpressionResponse> getActivePolicyExpressions() {
        return repository.findByIsActiveTrue().stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Server-side paginated variant with free-text search and an optional active filter
     * (null = all, true = active only, false = inactive only).
     */
    public Page<PolicyExpressionResponse> getPolicyExpressions(String search, Boolean active, Pageable pageable) {
        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;
        return repository.searchAll(searchTerm, active, pageable).map(this::toResponse);
    }

    public Optional<PolicyExpressionResponse> getPolicyExpression(Long id) {
        return repository.findById(id).map(this::toResponse);
    }

    public Optional<PolicyExpressionResponse> getPolicyExpressionByName(String name) {
        return repository.findByNameIgnoreCase(name).map(this::toResponse);
    }

    public Optional<PolicyExpressionResponse> getDefaultPolicy() {
        return repository.findFirstByIsActiveTrueAndIsDefaultTrue()
                .or(() -> repository.findFirstByIsActiveTrue())
                .map(this::toResponse);
    }

    public Optional<PolicyExpression> getDefaultPolicyEntity() {
        return repository.findFirstByIsActiveTrueAndIsDefaultTrue()
                .or(() -> repository.findFirstByIsActiveTrue());
    }

    @Transactional
    public PolicyExpressionResponse createPolicyExpression(PolicyExpressionRequest request) {
        if (repository.existsByNameIgnoreCase(request.getName())) {
            throw new IllegalArgumentException("A policy expression with name '" + request.getName() + "' already exists");
        }

        PolicyExpression entity = new PolicyExpression();
        applyRequest(entity, request);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            unsetExistingDefaults();
        }

        PolicyExpression saved = repository.save(entity);

        if (request.getRedactionRules() != null && !request.getRedactionRules().isEmpty()) {
            saveRedactionRules(saved, request.getRedactionRules());
        }

        log.info("Created policy expression: {} (id={})", saved.getName(), saved.getId());
        return toResponse(repository.findById(saved.getId()).orElse(saved));
    }

    @Transactional
    public PolicyExpressionResponse updatePolicyExpression(Long id, PolicyExpressionRequest request) {
        PolicyExpression entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Policy expression not found: " + id));

        if (request.getName() != null &&
            repository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new IllegalArgumentException("A policy expression with name '" + request.getName() + "' already exists");
        }

        applyRequest(entity, request);

        if (Boolean.TRUE.equals(request.getIsDefault()) && !Boolean.TRUE.equals(entity.getIsDefault())) {
            unsetExistingDefaults();
        }

        if (request.getRedactionRules() != null) {
            mergeRedactionRules(entity, request.getRedactionRules());
        }

        PolicyExpression saved = repository.save(entity);
        log.info("Updated policy expression: {} (id={})", saved.getName(), saved.getId());
        return toResponse(saved);
    }

    private void mergeRedactionRules(PolicyExpression entity, List<RedactionRuleRequest> requestedRules) {
        List<PolicyRedactionRule> existingRules = entity.getRedactionRules();

        java.util.Map<String, PolicyRedactionRule> existingByKey = new java.util.HashMap<>();
        for (PolicyRedactionRule rule : existingRules) {
            String key = rule.getObjectType().name() + "::" + rule.getFieldPath();
            existingByKey.put(key, rule);
        }

        java.util.Set<String> requestedKeys = new java.util.HashSet<>();

        int order = 0;
        for (RedactionRuleRequest ruleRequest : requestedRules) {
            RdapObjectType objectType = RdapObjectType.fromString(ruleRequest.getObjectType());
            String fieldPath = ruleRequest.getFieldPath();
            String key = objectType.name() + "::" + fieldPath;
            requestedKeys.add(key);

            PolicyRedactionRule existingRule = existingByKey.get(key);

            if (existingRule != null) {
                existingRule.setFieldDisplayName(ruleRequest.getFieldDisplayName());
                existingRule.setSensitivityLevel(ruleRequest.getSensitivityLevel() != null ? ruleRequest.getSensitivityLevel() : 0);
                existingRule.setValidationLevel(ruleRequest.getValidationLevel());
                existingRule.setDescription(ruleRequest.getDescription());
                existingRule.setRuleOrder(order++);
                existingRule.setIsEnabled(ruleRequest.getIsEnabled() != null ? ruleRequest.getIsEnabled() : true);
            } else {
                PolicyRedactionRule newRule = createRuleFromRequest(ruleRequest);
                newRule.setRuleOrder(order++);
                entity.addRedactionRule(newRule);
            }
        }

        existingRules.removeIf(rule -> {
            String key = rule.getObjectType().name() + "::" + rule.getFieldPath();
            return !requestedKeys.contains(key);
        });
    }

    @Transactional
    public void deletePolicyExpression(Long id) {
        PolicyExpression entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Policy expression not found: " + id));
        repository.delete(entity);
        log.info("Deleted policy expression: {} (id={})", entity.getName(), id);
    }

    @Transactional
    public PolicyExpressionResponse setAsDefault(Long id) {
        PolicyExpression entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Policy expression not found: " + id));
        unsetExistingDefaults();
        entity.setIsDefault(true);
        PolicyExpression saved = repository.save(entity);
        log.info("Set policy expression as default: {} (id={})", saved.getName(), saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public PolicyExpressionResponse toggleActive(Long id) {
        PolicyExpression entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Policy expression not found: " + id));
        entity.setIsActive(!Boolean.TRUE.equals(entity.getIsActive()));
        PolicyExpression saved = repository.save(entity);
        log.info("Toggled policy expression active status: {} (id={}, active={})",
                saved.getName(), saved.getId(), saved.getIsActive());
        return toResponse(saved);
    }

    public PolicyEnumValues getEnumValues() {
        List<String[]> customRoles = customContactRoleRepository.findByIsActiveTrue().stream()
                .map(r -> new String[]{r.getRoleKey(), r.getDisplayName()})
                .collect(Collectors.toList());
        return PolicyEnumValues.getAll(customRoles);
    }

    // ==================== Helper Methods ====================

    private void unsetExistingDefaults() {
        repository.findByIsActiveTrue().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsDefault()))
                .forEach(p -> { p.setIsDefault(false); repository.save(p); });
        repository.flush();
    }

    private void applyRequest(PolicyExpression entity, PolicyExpressionRequest request) {
        if (request.getName() != null) entity.setName(request.getName());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getScopeConditions() != null) entity.setScopeConditions(request.getScopeConditions());
        if (request.getNoteToRequestor() != null) entity.setNoteToRequestor(request.getNoteToRequestor());
        if (request.getIsActive() != null) entity.setIsActive(request.getIsActive());
        if (request.getIsDefault() != null) entity.setIsDefault(request.getIsDefault());
    }

    private void saveRedactionRules(PolicyExpression policy, List<RedactionRuleRequest> rules) {
        List<PolicyRedactionRule> entities = new ArrayList<>();
        int order = 0;
        for (RedactionRuleRequest ruleRequest : rules) {
            PolicyRedactionRule rule = createRuleFromRequest(ruleRequest);
            rule.setPolicyExpression(policy);
            if (rule.getRuleOrder() == null || rule.getRuleOrder() == 0) {
                rule.setRuleOrder(order++);
            }
            entities.add(rule);
        }
        redactionRuleRepository.saveAll(entities);
    }

    private PolicyRedactionRule createRuleFromRequest(RedactionRuleRequest request) {
        PolicyRedactionRule rule = new PolicyRedactionRule();
        if (request.getObjectType() != null) rule.setObjectType(RdapObjectType.fromString(request.getObjectType()));
        if (request.getFieldPath() != null) rule.setFieldPath(request.getFieldPath());
        if (request.getFieldDisplayName() != null) rule.setFieldDisplayName(request.getFieldDisplayName());
        rule.setSensitivityLevel(request.getSensitivityLevel() != null ? request.getSensitivityLevel() : 0);
        rule.setValidationLevel(request.getValidationLevel());
        if (request.getDescription() != null) rule.setDescription(request.getDescription());
        if (request.getRuleOrder() != null) rule.setRuleOrder(request.getRuleOrder());
        if (request.getIsEnabled() != null) rule.setIsEnabled(request.getIsEnabled());
        return rule;
    }

    private PolicyExpressionResponse toResponse(PolicyExpression entity) {
        List<RedactionRuleResponse> rules = entity.getRedactionRules() != null
                ? entity.getRedactionRules().stream().map(this::toRedactionRuleResponse).collect(Collectors.toList())
                : new ArrayList<>();

        return PolicyExpressionResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .scopeConditions(entity.getScopeConditions())
                .noteToRequestor(entity.getNoteToRequestor())
                .isActive(entity.getIsActive())
                .isDefault(entity.getIsDefault())
                .redactionRules(rules)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private RedactionRuleResponse toRedactionRuleResponse(PolicyRedactionRule entity) {
        return RedactionRuleResponse.builder()
                .id(entity.getId())
                .objectType(entity.getObjectType().name())
                .objectTypeDisplay(entity.getObjectType().getDisplayName())
                .fieldPath(entity.getFieldPath())
                .fieldDisplayName(entity.getFieldDisplayName())
                .sensitivityLevel(entity.getSensitivityLevel())
                .validationLevel(entity.getValidationLevel())
                .description(entity.getDescription())
                .ruleOrder(entity.getRuleOrder())
                .isEnabled(entity.getIsEnabled())
                .build();
    }
}
