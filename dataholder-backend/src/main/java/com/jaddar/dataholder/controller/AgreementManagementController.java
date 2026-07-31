/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import com.jaddar.dataholder.dto.AgreementApiDto.TestResultInfo;
import com.jaddar.dataholder.entity.AgreementRequestType;
import com.jaddar.dataholder.entity.AgreementRdapParameters;
import com.jaddar.dataholder.entity.AgreementSubscription;
import com.jaddar.dataholder.entity.AgreementSubscription.SubscriptionStatus;
import com.jaddar.dataholder.entity.AgreementStatusLog;
import com.jaddar.dataholder.entity.AgreementTemplate;
import com.jaddar.dataholder.entity.AgreementTemplateTestData;
import com.jaddar.dataholder.entity.RdapEntity;
import com.jaddar.dataholder.repository.AgreementSubscriptionRepository;
import com.jaddar.dataholder.repository.AgreementStatusLogRepository;
import com.jaddar.dataholder.repository.AgreementTemplateRepository;
import com.jaddar.dataholder.repository.AgreementTemplateTestDataRepository;
import com.jaddar.dataholder.repository.RdapEntityRepository;
import com.jaddar.dataholder.service.AgreementSubscriptionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal API for managing agreement templates and subscriptions.
 * Used by the admin UI.
 */
@RestController
@RequestMapping("/api/admin/agreement-management")
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AgreementManagementController {

    private final AgreementTemplateRepository templateRepository;
    private final AgreementSubscriptionRepository subscriptionRepository;
    private final AgreementStatusLogRepository statusLogRepository;
    private final AgreementSubscriptionService subscriptionService;
    private final AgreementTemplateTestDataRepository testDataRepository;
    private final RdapEntityRepository rdapEntityRepository;

    // ==================== Agreement Templates ====================

    @GetMapping("/templates")
    public ResponseEntity<List<Map<String, Object>>> getAllTemplates() {
        List<AgreementTemplate> templates = templateRepository.findAll();
        List<Map<String, Object>> result = templates.stream()
                .map(this::toTemplateResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/templates/{id}")
    public ResponseEntity<?> getTemplate(@PathVariable Long id) {
        return templateRepository.findById(id)
                .map(template -> ResponseEntity.ok(toTemplateResponse(template)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/templates")
    public ResponseEntity<?> createTemplate(@RequestBody CreateTemplateRequest request) {
        log.info("Creating agreement template: {}", request.getName());

        AgreementTemplate template = AgreementTemplate.builder()
                .name(request.getName())
                .shortDescription(request.getShortDescription())
                .description(request.getDescription())
                .requiredGroupTypes(request.getRequiredGroupTypes())
                .termsAndConditions(request.getTermsAndConditions())
                .dataUsagePolicy(request.getDataUsagePolicy())
                .maxQueriesPerDay(request.getMaxQueriesPerDay())
                .maxQueriesPerMonth(request.getMaxQueriesPerMonth())
                .isPublished(request.getIsPublished() != null ? request.getIsPublished() : false)
                .createdBy(request.getCreatedBy())
                .build();

        if (request.getRequestTypes() != null && !request.getRequestTypes().isEmpty()) {
            for (RequestTypeRequest rtReq : request.getRequestTypes()) {
                AgreementRequestType rt = AgreementRequestType.builder()
                        .name(rtReq.getName())
                        .typeCode(rtReq.getTypeCode())
                        .description(rtReq.getDescription())
                        .accessLevel(rtReq.getAccessLevel() != null ? rtReq.getAccessLevel() : 0)
                        .supportsConfidential(rtReq.getSupportsConfidential() != null ?
                                rtReq.getSupportsConfidential() : false)
                        .supportsExigent(rtReq.getSupportsExigent() != null ?
                                rtReq.getSupportsExigent() : false)
                        .requiresManualApproval(rtReq.getRequiresManualApproval() != null ?
                                rtReq.getRequiresManualApproval() : true)
                        .sortOrder(rtReq.getSortOrder() != null ? rtReq.getSortOrder() : 0)
                        .isActive(rtReq.getIsActive() != null ? rtReq.getIsActive() : true)
                        .build();
                if (rtReq.getRdapParameters() != null) {
                    rt.setRdapParameters(buildRdapParameters(rtReq.getRdapParameters()));
                }
                template.addRequestType(rt);
            }
        }

        template = templateRepository.save(template);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Template created successfully",
                "template", toTemplateResponse(template)
        ));
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<?> updateTemplate(@PathVariable Long id, @RequestBody UpdateTemplateRequest request) {
        log.info("Updating template {}", id);
        return templateRepository.findById(id)
                .map(template -> {
                    if (request.getName() != null) template.setName(request.getName());
                    if (request.getShortDescription() != null) template.setShortDescription(request.getShortDescription());
                    if (request.getDescription() != null) template.setDescription(request.getDescription());
                    if (request.getRequiredGroupTypes() != null) template.setRequiredGroupTypes(request.getRequiredGroupTypes());
                    if (request.getTermsAndConditions() != null) template.setTermsAndConditions(request.getTermsAndConditions());
                    if (request.getDataUsagePolicy() != null) template.setDataUsagePolicy(request.getDataUsagePolicy());
                    if (request.getMaxQueriesPerDay() != null) template.setMaxQueriesPerDay(request.getMaxQueriesPerDay());
                    if (request.getMaxQueriesPerMonth() != null) template.setMaxQueriesPerMonth(request.getMaxQueriesPerMonth());
                    if (request.getIsPublished() != null) template.setIsPublished(request.getIsPublished());

                    if (request.getRequestTypes() != null) {
                        template.getRequestTypes().clear();
                        templateRepository.saveAndFlush(template);
                        for (RequestTypeRequest rtReq : request.getRequestTypes()) {
                            AgreementRequestType rt = AgreementRequestType.builder()
                                    .name(rtReq.getName())
                                    .typeCode(rtReq.getTypeCode())
                                    .description(rtReq.getDescription())
                                    .accessLevel(rtReq.getAccessLevel() != null ? rtReq.getAccessLevel() : 0)
                                    .supportsConfidential(rtReq.getSupportsConfidential() != null ?
                                            rtReq.getSupportsConfidential() : false)
                                    .supportsExigent(rtReq.getSupportsExigent() != null ?
                                            rtReq.getSupportsExigent() : false)
                                    .requiresManualApproval(rtReq.getRequiresManualApproval() != null ?
                                            rtReq.getRequiresManualApproval() : true)
                                    .sortOrder(rtReq.getSortOrder() != null ? rtReq.getSortOrder() : 0)
                                    .isActive(rtReq.getIsActive() != null ? rtReq.getIsActive() : true)
                                    .build();
                            if (rtReq.getRdapParameters() != null) {
                                rt.setRdapParameters(buildRdapParameters(rtReq.getRdapParameters()));
                            }
                            template.addRequestType(rt);
                        }
                    }

                    templateRepository.save(template);
                    return ResponseEntity.ok(Map.of("success", true, "template", toTemplateResponse(template)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<?> deleteTemplate(@PathVariable Long id) {
        if (!templateRepository.existsById(id)) return ResponseEntity.notFound().build();
        List<AgreementSubscription> activeSubscriptions = subscriptionRepository.findActiveByTemplateId(id);
        if (!activeSubscriptions.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Cannot delete template with active subscriptions"));
        }
        templateRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Template deleted"));
    }

    @PostMapping("/templates/{id}/publish")
    public ResponseEntity<?> togglePublish(@PathVariable Long id) {
        return templateRepository.findById(id)
                .map(template -> {
                    template.setIsPublished(!template.getIsPublished());
                    templateRepository.save(template);
                    return ResponseEntity.ok(Map.of("success", true, "isPublished", template.getIsPublished()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Request Type Management ====================

    @GetMapping("/templates/{templateId}/request-types")
    public ResponseEntity<?> getRequestTypes(@PathVariable Long templateId) {
        return templateRepository.findById(templateId)
                .map(template -> ResponseEntity.ok(template.getRequestTypes().stream().map(this::toRequestTypeResponse).toList()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/templates/{templateId}/request-types")
    public ResponseEntity<?> addRequestType(@PathVariable Long templateId, @RequestBody RequestTypeRequest request) {
        return templateRepository.findById(templateId)
                .map(template -> {
                    AgreementRequestType rt = AgreementRequestType.builder()
                            .name(request.getName()).typeCode(request.getTypeCode()).description(request.getDescription())
                            .accessLevel(request.getAccessLevel() != null ? request.getAccessLevel() : 0)
                            .supportsConfidential(request.getSupportsConfidential() != null ? request.getSupportsConfidential() : false)
                            .supportsExigent(request.getSupportsExigent() != null ? request.getSupportsExigent() : false)
                            .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                            .build();
                    if (request.getRdapParameters() != null) rt.setRdapParameters(buildRdapParameters(request.getRdapParameters()));
                    template.addRequestType(rt);
                    templateRepository.save(template);
                    return ResponseEntity.ok(Map.of("success", true, "message", "Request type added", "requestType", toRequestTypeResponse(rt)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/templates/{templateId}/request-types/{requestTypeId}")
    public ResponseEntity<?> updateRequestType(@PathVariable Long templateId, @PathVariable Long requestTypeId, @RequestBody RequestTypeRequest request) {
        return templateRepository.findById(templateId)
                .map(template -> {
                    AgreementRequestType rt = template.getRequestTypes().stream().filter(r -> r.getId().equals(requestTypeId)).findFirst().orElse(null);
                    if (rt == null) return ResponseEntity.notFound().build();
                    if (request.getName() != null) rt.setName(request.getName());
                    if (request.getTypeCode() != null) rt.setTypeCode(request.getTypeCode());
                    if (request.getDescription() != null) rt.setDescription(request.getDescription());
                    if (request.getAccessLevel() != null) rt.setAccessLevel(request.getAccessLevel());
                    if (request.getSupportsConfidential() != null) rt.setSupportsConfidential(request.getSupportsConfidential());
                    if (request.getSupportsExigent() != null) rt.setSupportsExigent(request.getSupportsExigent());
                    if (request.getSortOrder() != null) rt.setSortOrder(request.getSortOrder());
                    if (request.getIsActive() != null) rt.setIsActive(request.getIsActive());
                    if (request.getRdapParameters() != null) rt.setRdapParameters(buildRdapParameters(request.getRdapParameters()));
                    templateRepository.save(template);
                    return ResponseEntity.ok(Map.of("success", true, "requestType", toRequestTypeResponse(rt)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/templates/{templateId}/request-types/{requestTypeId}")
    public ResponseEntity<?> deleteRequestType(@PathVariable Long templateId, @PathVariable Long requestTypeId) {
        return templateRepository.findById(templateId)
                .map(template -> {
                    boolean removed = template.getRequestTypes().removeIf(rt -> rt.getId().equals(requestTypeId));
                    if (!removed) return ResponseEntity.notFound().build();
                    templateRepository.save(template);
                    return ResponseEntity.ok(Map.of("success", true, "message", "Request type deleted"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Template Test Data ====================

    @GetMapping("/templates/{templateId}/test-data")
    public ResponseEntity<?> getTestData(@PathVariable Long templateId) {
        return templateRepository.findById(templateId)
                .map(template -> {
                    List<AgreementTemplateTestData> entries = testDataRepository.findByTemplateIdOrderBySortOrderAscIdAsc(templateId);
                    return ResponseEntity.ok(entries.stream().map(this::toTestDataResponse).toList());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/templates/{templateId}/test-data")
    public ResponseEntity<?> addTestData(@PathVariable Long templateId, @RequestBody TestDataRequest request) {
        return templateRepository.findById(templateId)
                .map(template -> {
                    RdapEntity rdapEntity = rdapEntityRepository.findById(request.getRdapEntityId()).orElse(null);
                    if (rdapEntity == null) return ResponseEntity.badRequest().body(Map.of("success", false, "error", "RDAP entity not found: " + request.getRdapEntityId()));
                    if (rdapEntity.getParent() != null) return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Only top-level RDAP entities can be used as test data"));
                    if (testDataRepository.existsByTemplateIdAndRdapEntityId(templateId, rdapEntity.getId()))
                        return ResponseEntity.badRequest().body(Map.of("success", false, "error", "This RDAP entity is already linked to this template"));

                    String queryType = request.getQueryType() != null ? request.getQueryType() : AgreementTemplateTestData.deriveQueryType(rdapEntity);
                    String queryValue = request.getQueryValue() != null ? request.getQueryValue() : AgreementTemplateTestData.deriveQueryValue(rdapEntity);

                    AgreementTemplateTestData entry = AgreementTemplateTestData.builder()
                            .template(template).rdapEntity(rdapEntity).queryType(queryType).queryValue(queryValue)
                            .requestTypeName(request.getRequestTypeName() != null ? request.getRequestTypeName() : "standard")
                            .label(request.getLabel()).description(request.getDescription())
                            .verifyContactAccess(request.getVerifyContactAccess() != null ? request.getVerifyContactAccess() : true)
                            .verifyRedaction(request.getVerifyRedaction() != null ? request.getVerifyRedaction() : true)
                            .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                            .build();
                    entry = testDataRepository.save(entry);
                    log.info("Added test data entry {} to template {} (entity: {} {})", entry.getId(), template.getTemplateId(), rdapEntity.getObjectType(), rdapEntity.getDisplayIdentifier());
                    return ResponseEntity.ok(Map.of("success", true, "message", "Test data entry added", "testData", toTestDataResponse(entry)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/templates/{templateId}/test-data/{entryId}")
    public ResponseEntity<?> updateTestData(@PathVariable Long templateId, @PathVariable Long entryId, @RequestBody TestDataRequest request) {
        AgreementTemplateTestData entry = testDataRepository.findById(entryId).orElse(null);
        if (entry == null || !entry.getTemplate().getId().equals(templateId)) return ResponseEntity.notFound().build();

        if (request.getRequestTypeName() != null) entry.setRequestTypeName(request.getRequestTypeName());
        if (request.getLabel() != null) entry.setLabel(request.getLabel());
        if (request.getDescription() != null) entry.setDescription(request.getDescription());
        if (request.getVerifyContactAccess() != null) entry.setVerifyContactAccess(request.getVerifyContactAccess());
        if (request.getVerifyRedaction() != null) entry.setVerifyRedaction(request.getVerifyRedaction());
        if (request.getSortOrder() != null) entry.setSortOrder(request.getSortOrder());
        if (request.getIsActive() != null) entry.setIsActive(request.getIsActive());
        if (request.getQueryType() != null) entry.setQueryType(request.getQueryType());
        if (request.getQueryValue() != null) entry.setQueryValue(request.getQueryValue());

        if (request.getRdapEntityId() != null && !request.getRdapEntityId().equals(entry.getRdapEntity().getId())) {
            RdapEntity newEntity = rdapEntityRepository.findById(request.getRdapEntityId()).orElse(null);
            if (newEntity == null) return ResponseEntity.badRequest().body(Map.of("success", false, "error", "RDAP entity not found"));
            if (newEntity.getParent() != null) return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Only top-level entities allowed"));
            entry.setRdapEntity(newEntity);
            if (request.getQueryType() == null) entry.setQueryType(AgreementTemplateTestData.deriveQueryType(newEntity));
            if (request.getQueryValue() == null) entry.setQueryValue(AgreementTemplateTestData.deriveQueryValue(newEntity));
        }

        testDataRepository.save(entry);
        return ResponseEntity.ok(Map.of("success", true, "testData", toTestDataResponse(entry)));
    }

    @DeleteMapping("/templates/{templateId}/test-data/{entryId}")
    public ResponseEntity<?> deleteTestData(@PathVariable Long templateId, @PathVariable Long entryId) {
        AgreementTemplateTestData entry = testDataRepository.findById(entryId).orElse(null);
        if (entry == null || !entry.getTemplate().getId().equals(templateId)) return ResponseEntity.notFound().build();
        testDataRepository.delete(entry);
        log.info("Deleted test data entry {} from template {}", entryId, templateId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Test data entry deleted"));
    }

    @PostMapping("/templates/{templateId}/test-data/bulk")
    public ResponseEntity<?> bulkAddTestData(@PathVariable Long templateId, @RequestBody BulkTestDataRequest request) {
        return templateRepository.findById(templateId)
                .map(template -> {
                    int added = 0, skipped = 0;
                    List<String> errors = new ArrayList<>();
                    for (Long entityId : request.getRdapEntityIds()) {
                        try {
                            if (testDataRepository.existsByTemplateIdAndRdapEntityId(templateId, entityId)) { skipped++; continue; }
                            RdapEntity entity = rdapEntityRepository.findById(entityId).orElse(null);
                            if (entity == null || entity.getParent() != null) { errors.add("Entity " + entityId + " not found or is child"); continue; }
                            AgreementTemplateTestData entry = AgreementTemplateTestData.builder()
                                    .template(template).rdapEntity(entity)
                                    .queryType(AgreementTemplateTestData.deriveQueryType(entity))
                                    .queryValue(AgreementTemplateTestData.deriveQueryValue(entity))
                                    .requestTypeName(request.getRequestTypeName() != null ? request.getRequestTypeName() : "standard")
                                    .verifyContactAccess(true).verifyRedaction(true).sortOrder(added).isActive(true).build();
                            testDataRepository.save(entry);
                            added++;
                        } catch (Exception e) { log.error("Failed to add test data for entity {}", entityId, e); errors.add("Entity " + entityId + ": could not be added"); }
                    }
                    return ResponseEntity.ok(Map.of("success", true, "added", added, "skipped", skipped, "errors", errors));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Subscriptions ====================

    @GetMapping("/subscriptions")
    public ResponseEntity<List<Map<String, Object>>> getAllSubscriptions() {
        return ResponseEntity.ok(subscriptionRepository.findAll().stream().map(this::toSubscriptionResponse).toList());
    }

    @GetMapping("/subscriptions/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingSubscriptions() {
        return ResponseEntity.ok(subscriptionRepository.findPendingSubscriptions().stream().map(this::toSubscriptionResponse).toList());
    }

    @GetMapping("/subscriptions/active")
    public ResponseEntity<List<Map<String, Object>>> getActiveSubscriptions() {
        return ResponseEntity.ok(subscriptionRepository.findAllActive().stream().map(this::toSubscriptionResponse).toList());
    }

    @GetMapping("/subscriptions/{id}")
    public ResponseEntity<?> getSubscription(@PathVariable Long id) {
        return subscriptionRepository.findById(id)
                .map(subscription -> {
                    Map<String, Object> response = toSubscriptionResponse(subscription);
                    List<AgreementStatusLog> logs = statusLogRepository.findBySubscriptionIdOrderByCreatedAtDesc(id);
                    response.put("statusHistory", logs.stream().map(this::toStatusLogResponse).toList());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/subscriptions/{id}/approve")
    public ResponseEntity<?> approveSubscription(@PathVariable Long id, @RequestBody ReviewRequest request) {
        try {
            AgreementSubscription result = subscriptionService.approveSubscription(id, request.getReviewedBy(), request.getNotes());
            return ResponseEntity.ok(Map.of("success", true, "message", "Subscription approved", "subscription", toSubscriptionResponse(result)));
        } catch (Exception e) { log.error("Subscription action failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please try again or contact support.")); }
    }

    @PostMapping("/subscriptions/{id}/deny")
    public ResponseEntity<?> denySubscription(@PathVariable Long id, @RequestBody ReviewRequest request) {
        try {
            AgreementSubscription result = subscriptionService.denySubscription(id, request.getReviewedBy(), request.getNotes());
            return ResponseEntity.ok(Map.of("success", true, "message", "Subscription denied", "subscription", toSubscriptionResponse(result)));
        } catch (Exception e) { log.error("Subscription action failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please try again or contact support.")); }
    }

    @PostMapping("/subscriptions/{id}/start-test")
    public ResponseEntity<?> startTest(@PathVariable Long id, @RequestBody ActionRequest request) {
        try {
            AgreementSubscription result = subscriptionService.startTesting(id, request.getInitiatedBy());
            return ResponseEntity.ok(Map.of("success", true, "message", "Testing started", "subscription", toSubscriptionResponse(result)));
        } catch (Exception e) { log.error("Subscription action failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please try again or contact support.")); }
    }

    @PostMapping("/subscriptions/{id}/run-test")
    public ResponseEntity<?> runTest(@PathVariable Long id) {
        try {
            TestResultInfo result = subscriptionService.runSubscriptionTest(id);
            return ResponseEntity.ok(Map.of("success", true, "testResult", result));
        } catch (Exception e) { log.error("Subscription action failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please try again or contact support.")); }
    }

    @PostMapping("/subscriptions/{id}/activate")
    public ResponseEntity<?> activateSubscription(@PathVariable Long id, @RequestBody ActionRequest request) {
        try {
            AgreementSubscription result = subscriptionService.activateSubscription(id, request.getInitiatedBy());
            return ResponseEntity.ok(Map.of("success", true, "message", "Subscription activated", "subscription", toSubscriptionResponse(result)));
        } catch (Exception e) { log.error("Subscription action failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please try again or contact support.")); }
    }

    @PostMapping("/subscriptions/{id}/suspend")
    public ResponseEntity<?> suspendSubscription(@PathVariable Long id, @RequestBody ReviewRequest request) {
        try {
            AgreementSubscription result = subscriptionService.suspendSubscription(id, request.getReviewedBy(), request.getNotes());
            return ResponseEntity.ok(Map.of("success", true, "message", "Subscription suspended", "subscription", toSubscriptionResponse(result)));
        } catch (Exception e) { log.error("Subscription action failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please try again or contact support.")); }
    }

    @PostMapping("/subscriptions/{id}/reactivate")
    public ResponseEntity<?> reactivateSubscription(@PathVariable Long id, @RequestBody ActionRequest request) {
        try {
            AgreementSubscription result = subscriptionService.reactivateSubscription(id, request.getInitiatedBy());
            return ResponseEntity.ok(Map.of("success", true, "message", "Subscription reactivated", "subscription", toSubscriptionResponse(result)));
        } catch (Exception e) { log.error("Subscription action failed", e); return ResponseEntity.badRequest().body(Map.of("success", false, "error", "The request could not be completed. Please try again or contact support.")); }
    }

    // ==================== Stats ====================

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTemplates", templateRepository.count());
        stats.put("publishedTemplates", templateRepository.findByIsPublishedTrue().size());
        stats.put("totalSubscriptions", subscriptionRepository.count());
        stats.put("pendingSubscriptions", subscriptionRepository.countByStatus(SubscriptionStatus.PENDING));
        stats.put("approvedSubscriptions", subscriptionRepository.countByStatus(SubscriptionStatus.APPROVED));
        stats.put("testingSubscriptions", subscriptionRepository.countByStatus(SubscriptionStatus.TESTING));
        stats.put("activeSubscriptions", subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE));
        stats.put("suspendedSubscriptions", subscriptionRepository.countByStatus(SubscriptionStatus.SUSPENDED));
        stats.put("deniedSubscriptions", subscriptionRepository.countByStatus(SubscriptionStatus.DENIED));
        return ResponseEntity.ok(stats);
    }
    // ==================== Helper Methods ====================

    private Map<String, Object> toTemplateResponse(AgreementTemplate template) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", template.getId());
        result.put("templateId", template.getTemplateId());
        result.put("name", template.getName());
        result.put("shortDescription", template.getShortDescription());
        result.put("description", template.getDescription());
        result.put("requiredGroupTypes", template.getRequiredGroupTypes());
        result.put("termsAndConditions", template.getTermsAndConditions());
        result.put("dataUsagePolicy", template.getDataUsagePolicy());
        result.put("maxQueriesPerDay", template.getMaxQueriesPerDay());
        result.put("maxQueriesPerMonth", template.getMaxQueriesPerMonth());
        result.put("isPublished", template.getIsPublished());
        result.put("createdAt", template.getCreatedAt());
        result.put("updatedAt", template.getUpdatedAt());
        result.put("createdBy", template.getCreatedBy());

        result.put("requestTypes", template.getRequestTypes().stream().map(this::toRequestTypeResponse).toList());

        List<Map<String, Object>> testData = template.getTestDataEntries() != null
                ? template.getTestDataEntries().stream().map(this::toTestDataResponse).toList()
                : List.of();
        result.put("testData", testData);
        result.put("testDataCount", testData.size());

        result.put("defaultAccessLevel", template.getDefaultAccessLevel());
        result.put("highestAccessLevel", template.getHighestAccessLevel());
        result.put("supportsConfidential", template.supportsConfidential());
        result.put("supportsExigent", template.supportsExigent());
        return result;
    }

    private Map<String, Object> toRequestTypeResponse(AgreementRequestType rt) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", rt.getId());
        result.put("name", rt.getName());
        result.put("typeCode", rt.getTypeCode());
        result.put("description", rt.getDescription());
        result.put("accessLevel", rt.getAccessLevel());
        result.put("supportsConfidential", rt.getSupportsConfidential());
        result.put("supportsExigent", rt.getSupportsExigent());
        result.put("requiresManualApproval", rt.getRequiresManualApproval());
        result.put("sortOrder", rt.getSortOrder());
        result.put("isActive", rt.getIsActive());
        result.put("createdAt", rt.getCreatedAt());
        result.put("updatedAt", rt.getUpdatedAt());
        if (rt.getRdapParameters() != null) result.put("rdapParameters", toRdapParametersMap(rt.getRdapParameters()));
        return result;
    }

    private Map<String, Object> toTestDataResponse(AgreementTemplateTestData entry) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", entry.getId());
        result.put("queryType", entry.getQueryType());
        result.put("queryValue", entry.getQueryValue());
        result.put("requestTypeName", entry.getRequestTypeName());
        result.put("label", entry.getLabel());
        result.put("displayLabel", entry.getDisplayLabel());
        result.put("description", entry.getDescription());
        result.put("verifyContactAccess", entry.getVerifyContactAccess());
        result.put("verifyRedaction", entry.getVerifyRedaction());
        result.put("sortOrder", entry.getSortOrder());
        result.put("isActive", entry.getIsActive());
        result.put("createdAt", entry.getCreatedAt());
        result.put("updatedAt", entry.getUpdatedAt());
        if (entry.getRdapEntity() != null) {
            result.put("rdapEntityId", entry.getRdapEntity().getId());
            result.put("rdapEntityHandle", entry.getRdapEntity().getHandle());
            result.put("rdapEntityObjectType", entry.getRdapEntity().getObjectType().name());
            result.put("rdapEntityDisplayIdentifier", entry.getRdapEntity().getDisplayIdentifier());
        }
        return result;
    }

    private Map<String, Object> toSubscriptionResponse(AgreementSubscription subscription) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", subscription.getId());
        result.put("requestId", subscription.getRequestId());
        result.put("status", subscription.getStatus().name());
        result.put("statusMessage", subscription.getStatusMessage());
        result.put("statusChangedAt", subscription.getStatusChangedAt());
        result.put("requestorGroupId", subscription.getRequestorGroupId());
        result.put("requestorGroupName", subscription.getRequestorGroupName());
        result.put("requestorGroupCode", subscription.getRequestorGroupCode());
        result.put("requestorGroupType", subscription.getRequestorGroupType());
        result.put("requestorDescription", subscription.getRequestorDescription());
        result.put("requestorFirstName", subscription.getRequestorFirstName());
        result.put("requestorLastName", subscription.getRequestorLastName());
        result.put("requestorOrganization", subscription.getRequestorOrganization());
        result.put("requestorContactEmail", subscription.getRequestorContactEmail());
        result.put("requestorPhone", subscription.getRequestorPhone());
        result.put("requestorAddress", subscription.getRequestorAddress());
        result.put("requestorCity", subscription.getRequestorCity());
        result.put("requestorStateProvince", subscription.getRequestorStateProvince());
        result.put("requestorPostalCode", subscription.getRequestorPostalCode());
        result.put("requestorCountry", subscription.getRequestorCountry());
        result.put("formattedAddress", subscription.getFormattedAddress());
        result.put("requestorAgentId", subscription.getRequestorAgentId());
        result.put("callbackUrl", subscription.getRequestorAgentCallbackUrl());
        result.put("effectiveAccessLevel", subscription.getEffectiveAccessLevel());
        result.put("highestAccessLevel", subscription.getHighestAccessLevel());
        result.put("supportsConfidential", subscription.supportsConfidential());
        result.put("supportsExigent", subscription.supportsExigent());
        result.put("purpose", subscription.getPurpose());
        result.put("additionalTerms", subscription.getAdditionalTerms());
        result.put("introspectionUrl", subscription.getIntrospectionUrl());
        result.put("testResult", subscription.getTestResult());
        result.put("testDetails", subscription.getTestDetails());
        result.put("testStartedAt", subscription.getTestStartedAt());
        result.put("testCompletedAt", subscription.getTestCompletedAt());
        result.put("reviewedBy", subscription.getReviewedBy());
        result.put("reviewedAt", subscription.getReviewedAt());
        result.put("reviewNotes", subscription.getReviewNotes());
        result.put("effectiveFrom", subscription.getEffectiveFrom());
        result.put("effectiveTo", subscription.getEffectiveTo());
        result.put("activatedAt", subscription.getActivatedAt());
        result.put("activatedBy", subscription.getActivatedBy());
        result.put("maxQueriesPerDay", subscription.getMaxQueriesPerDay());
        result.put("maxQueriesPerMonth", subscription.getMaxQueriesPerMonth());
        result.put("createdAt", subscription.getCreatedAt());
        result.put("updatedAt", subscription.getUpdatedAt());
        result.put("requestExpiresAt", subscription.getRequestExpiresAt());

        if (subscription.getTemplate() != null) {
            result.put("templateId", subscription.getTemplate().getTemplateId());
            result.put("templateName", subscription.getTemplate().getName());
            result.put("templateDescription", subscription.getTemplate().getDescription());
        }

        return result;
    }

    private Map<String, Object> toStatusLogResponse(AgreementStatusLog log) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", log.getId());
        result.put("previousStatus", log.getPreviousStatus());
        result.put("newStatus", log.getNewStatus());
        result.put("changedBy", log.getChangedBy());
        result.put("changeReason", log.getChangeReason());
        result.put("source", log.getSource());
        result.put("createdAt", log.getCreatedAt());
        return result;
    }

    private Map<String, Boolean> toRdapParametersMap(AgreementRdapParameters p) {
        Map<String, Boolean> m = new HashMap<>();
        m.put("domainHandle", p.getDomainHandle()); m.put("domainName", p.getDomainName());
        m.put("domainStatus", p.getDomainStatus()); m.put("domainPort43", p.getDomainPort43());
        m.put("domainPublicIds", p.getDomainPublicIds());
        m.put("nameservers", p.getNameservers()); m.put("nameserverHandle", p.getNameserverHandle());
        m.put("nameserverName", p.getNameserverName()); m.put("nameserverIpAddresses", p.getNameserverIpAddresses());
        m.put("nameserverStatus", p.getNameserverStatus());
        m.put("events", p.getEvents()); m.put("eventRegistration", p.getEventRegistration());
        m.put("eventExpiration", p.getEventExpiration()); m.put("eventLastChanged", p.getEventLastChanged());
        m.put("eventLastUpdateOfRdapDb", p.getEventLastUpdateOfRdapDb()); m.put("eventTransfer", p.getEventTransfer());
        m.put("registrantEntity", p.getRegistrantEntity()); m.put("registrantHandle", p.getRegistrantHandle());
        m.put("registrantName", p.getRegistrantName()); m.put("registrantOrganization", p.getRegistrantOrganization());
        m.put("registrantEmail", p.getRegistrantEmail()); m.put("registrantPhone", p.getRegistrantPhone());
        m.put("registrantFax", p.getRegistrantFax()); m.put("registrantAddress", p.getRegistrantAddress());
        m.put("registrantStreet", p.getRegistrantStreet()); m.put("registrantCity", p.getRegistrantCity());
        m.put("registrantStateProvince", p.getRegistrantStateProvince()); m.put("registrantPostalCode", p.getRegistrantPostalCode());
        m.put("registrantCountry", p.getRegistrantCountry());
        m.put("adminEntity", p.getAdminEntity()); m.put("adminHandle", p.getAdminHandle());
        m.put("adminName", p.getAdminName()); m.put("adminOrganization", p.getAdminOrganization());
        m.put("adminEmail", p.getAdminEmail()); m.put("adminPhone", p.getAdminPhone());
        m.put("adminFax", p.getAdminFax()); m.put("adminAddress", p.getAdminAddress());
        m.put("adminStreet", p.getAdminStreet()); m.put("adminCity", p.getAdminCity());
        m.put("adminStateProvince", p.getAdminStateProvince()); m.put("adminPostalCode", p.getAdminPostalCode());
        m.put("adminCountry", p.getAdminCountry());
        m.put("techEntity", p.getTechEntity()); m.put("techHandle", p.getTechHandle());
        m.put("techName", p.getTechName()); m.put("techOrganization", p.getTechOrganization());
        m.put("techEmail", p.getTechEmail()); m.put("techPhone", p.getTechPhone());
        m.put("techFax", p.getTechFax()); m.put("techAddress", p.getTechAddress());
        m.put("techStreet", p.getTechStreet()); m.put("techCity", p.getTechCity());
        m.put("techStateProvince", p.getTechStateProvince()); m.put("techPostalCode", p.getTechPostalCode());
        m.put("techCountry", p.getTechCountry());
        m.put("billingEntity", p.getBillingEntity()); m.put("billingHandle", p.getBillingHandle());
        m.put("billingName", p.getBillingName()); m.put("billingOrganization", p.getBillingOrganization());
        m.put("billingEmail", p.getBillingEmail()); m.put("billingPhone", p.getBillingPhone());
        m.put("billingFax", p.getBillingFax()); m.put("billingAddress", p.getBillingAddress());
        m.put("billingStreet", p.getBillingStreet()); m.put("billingCity", p.getBillingCity());
        m.put("billingStateProvince", p.getBillingStateProvince()); m.put("billingPostalCode", p.getBillingPostalCode());
        m.put("billingCountry", p.getBillingCountry());
        m.put("registrarEntity", p.getRegistrarEntity()); m.put("registrarHandle", p.getRegistrarHandle());
        m.put("registrarName", p.getRegistrarName()); m.put("registrarEmail", p.getRegistrarEmail());
        m.put("registrarPhone", p.getRegistrarPhone()); m.put("registrarUrl", p.getRegistrarUrl());
        m.put("registrarAbuseContact", p.getRegistrarAbuseContact());
        m.put("dnssecData", p.getDnssecData()); m.put("dnssecDelegationSigned", p.getDnssecDelegationSigned());
        m.put("dnssecDsData", p.getDnssecDsData()); m.put("dnssecKeyData", p.getDnssecKeyData());
        m.put("networkHandle", p.getNetworkHandle()); m.put("networkName", p.getNetworkName());
        m.put("networkType", p.getNetworkType()); m.put("networkStartAddress", p.getNetworkStartAddress());
        m.put("networkEndAddress", p.getNetworkEndAddress()); m.put("networkIpVersion", p.getNetworkIpVersion());
        m.put("networkParentHandle", p.getNetworkParentHandle()); m.put("networkCidr", p.getNetworkCidr());
        m.put("networkCountry", p.getNetworkCountry());
        m.put("autnumHandle", p.getAutnumHandle()); m.put("autnumStart", p.getAutnumStart());
        m.put("autnumEnd", p.getAutnumEnd()); m.put("autnumName", p.getAutnumName());
        m.put("autnumType", p.getAutnumType()); m.put("autnumCountry", p.getAutnumCountry());
        m.put("links", p.getLinks()); m.put("notices", p.getNotices()); m.put("remarks", p.getRemarks());
        return m;
    }

    private AgreementRdapParameters buildRdapParameters(Map<String, Boolean> p) {
        return AgreementRdapParameters.builder()
                .domainHandle(p.getOrDefault("domainHandle", true)).domainName(p.getOrDefault("domainName", true))
                .domainStatus(p.getOrDefault("domainStatus", true)).domainPort43(p.getOrDefault("domainPort43", true))
                .domainPublicIds(p.getOrDefault("domainPublicIds", true))
                .nameservers(p.getOrDefault("nameservers", true)).nameserverHandle(p.getOrDefault("nameserverHandle", true))
                .nameserverName(p.getOrDefault("nameserverName", true)).nameserverIpAddresses(p.getOrDefault("nameserverIpAddresses", true))
                .nameserverStatus(p.getOrDefault("nameserverStatus", true))
                .events(p.getOrDefault("events", true)).eventRegistration(p.getOrDefault("eventRegistration", true))
                .eventExpiration(p.getOrDefault("eventExpiration", true)).eventLastChanged(p.getOrDefault("eventLastChanged", true))
                .eventLastUpdateOfRdapDb(p.getOrDefault("eventLastUpdateOfRdapDb", true)).eventTransfer(p.getOrDefault("eventTransfer", true))
                .registrantEntity(p.getOrDefault("registrantEntity", true)).registrantHandle(p.getOrDefault("registrantHandle", true))
                .registrantName(p.getOrDefault("registrantName", true)).registrantOrganization(p.getOrDefault("registrantOrganization", true))
                .registrantEmail(p.getOrDefault("registrantEmail", true)).registrantPhone(p.getOrDefault("registrantPhone", true))
                .registrantFax(p.getOrDefault("registrantFax", true)).registrantAddress(p.getOrDefault("registrantAddress", true))
                .registrantStreet(p.getOrDefault("registrantStreet", true)).registrantCity(p.getOrDefault("registrantCity", true))
                .registrantStateProvince(p.getOrDefault("registrantStateProvince", true)).registrantPostalCode(p.getOrDefault("registrantPostalCode", true))
                .registrantCountry(p.getOrDefault("registrantCountry", true))
                .adminEntity(p.getOrDefault("adminEntity", true)).adminHandle(p.getOrDefault("adminHandle", true))
                .adminName(p.getOrDefault("adminName", true)).adminOrganization(p.getOrDefault("adminOrganization", true))
                .adminEmail(p.getOrDefault("adminEmail", true)).adminPhone(p.getOrDefault("adminPhone", true))
                .adminFax(p.getOrDefault("adminFax", true)).adminAddress(p.getOrDefault("adminAddress", true))
                .adminStreet(p.getOrDefault("adminStreet", true)).adminCity(p.getOrDefault("adminCity", true))
                .adminStateProvince(p.getOrDefault("adminStateProvince", true)).adminPostalCode(p.getOrDefault("adminPostalCode", true))
                .adminCountry(p.getOrDefault("adminCountry", true))
                .techEntity(p.getOrDefault("techEntity", true)).techHandle(p.getOrDefault("techHandle", true))
                .techName(p.getOrDefault("techName", true)).techOrganization(p.getOrDefault("techOrganization", true))
                .techEmail(p.getOrDefault("techEmail", true)).techPhone(p.getOrDefault("techPhone", true))
                .techFax(p.getOrDefault("techFax", true)).techAddress(p.getOrDefault("techAddress", true))
                .techStreet(p.getOrDefault("techStreet", true)).techCity(p.getOrDefault("techCity", true))
                .techStateProvince(p.getOrDefault("techStateProvince", true)).techPostalCode(p.getOrDefault("techPostalCode", true))
                .techCountry(p.getOrDefault("techCountry", true))
                .billingEntity(p.getOrDefault("billingEntity", true)).billingHandle(p.getOrDefault("billingHandle", true))
                .billingName(p.getOrDefault("billingName", true)).billingOrganization(p.getOrDefault("billingOrganization", true))
                .billingEmail(p.getOrDefault("billingEmail", true)).billingPhone(p.getOrDefault("billingPhone", true))
                .billingFax(p.getOrDefault("billingFax", true)).billingAddress(p.getOrDefault("billingAddress", true))
                .billingStreet(p.getOrDefault("billingStreet", true)).billingCity(p.getOrDefault("billingCity", true))
                .billingStateProvince(p.getOrDefault("billingStateProvince", true)).billingPostalCode(p.getOrDefault("billingPostalCode", true))
                .billingCountry(p.getOrDefault("billingCountry", true))
                .registrarEntity(p.getOrDefault("registrarEntity", true)).registrarHandle(p.getOrDefault("registrarHandle", true))
                .registrarName(p.getOrDefault("registrarName", true)).registrarEmail(p.getOrDefault("registrarEmail", true))
                .registrarPhone(p.getOrDefault("registrarPhone", true)).registrarUrl(p.getOrDefault("registrarUrl", true))
                .registrarAbuseContact(p.getOrDefault("registrarAbuseContact", true))
                .dnssecData(p.getOrDefault("dnssecData", true)).dnssecDelegationSigned(p.getOrDefault("dnssecDelegationSigned", true))
                .dnssecDsData(p.getOrDefault("dnssecDsData", true)).dnssecKeyData(p.getOrDefault("dnssecKeyData", true))
                .networkHandle(p.getOrDefault("networkHandle", true)).networkName(p.getOrDefault("networkName", true))
                .networkType(p.getOrDefault("networkType", true)).networkStartAddress(p.getOrDefault("networkStartAddress", true))
                .networkEndAddress(p.getOrDefault("networkEndAddress", true)).networkIpVersion(p.getOrDefault("networkIpVersion", true))
                .networkParentHandle(p.getOrDefault("networkParentHandle", true)).networkCidr(p.getOrDefault("networkCidr", true))
                .networkCountry(p.getOrDefault("networkCountry", true))
                .autnumHandle(p.getOrDefault("autnumHandle", true)).autnumStart(p.getOrDefault("autnumStart", true))
                .autnumEnd(p.getOrDefault("autnumEnd", true)).autnumName(p.getOrDefault("autnumName", true))
                .autnumType(p.getOrDefault("autnumType", true)).autnumCountry(p.getOrDefault("autnumCountry", true))
                .links(p.getOrDefault("links", true)).notices(p.getOrDefault("notices", true))
                .remarks(p.getOrDefault("remarks", true))
                .build();
    }

    // ==================== Request DTOs ====================

    @Data
    public static class CreateTemplateRequest {
        private String name;
        private String shortDescription;
        private String description;
        private String requiredGroupTypes;
        private String termsAndConditions;
        private String dataUsagePolicy;
        private Integer maxQueriesPerDay;
        private Integer maxQueriesPerMonth;
        private Boolean isPublished;
        private String createdBy;
        private List<RequestTypeRequest> requestTypes;
    }

    @Data
    public static class UpdateTemplateRequest {
        private String name;
        private String shortDescription;
        private String description;
        private String requiredGroupTypes;
        private String termsAndConditions;
        private String dataUsagePolicy;
        private Integer maxQueriesPerDay;
        private Integer maxQueriesPerMonth;
        private Boolean isPublished;
        private List<RequestTypeRequest> requestTypes;
    }

    @Data
    public static class RequestTypeRequest {
        private String name;
        private Integer typeCode;
        private String description;
        private Integer accessLevel;
        private Boolean supportsConfidential;
        private Boolean supportsExigent;
        private Boolean requiresManualApproval;
        private Integer sortOrder;
        private Boolean isActive;
        private Map<String, Boolean> rdapParameters;
    }

    @Data
    public static class TestDataRequest {
        private Long rdapEntityId;
        private String queryType;
        private String queryValue;
        private String requestTypeName;
        private String label;
        private String description;
        private Boolean verifyContactAccess;
        private Boolean verifyRedaction;
        private Integer sortOrder;
        private Boolean isActive;
    }

    @Data
    public static class BulkTestDataRequest {
        private List<Long> rdapEntityIds;
        private String requestTypeName;
    }

    @Data
    public static class ReviewRequest {
        private String reviewedBy;
        private String notes;
    }

    @Data
    public static class ActionRequest {
        private String initiatedBy;
        private String notes;
    }
}