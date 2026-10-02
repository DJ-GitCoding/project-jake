/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.service;

import com.jaddar.dhgroupadmin.entity.*;
import com.jaddar.dhgroupadmin.entity.AgreementSubscription.SubscriptionStatus;
import com.jaddar.dhgroupadmin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgreementSubscriptionService {

    private final AgreementTemplateRepository templateRepository;
    private final AgreementSubscriptionRepository subscriptionRepository;
    private final AgreementStatusLogRepository statusLogRepository;
    private final DataHolderRepository dataHolderRepository;
    private final WebClient.Builder webClientBuilder;

    private static final List<SubscriptionStatus> BLOCKING_STATUSES = Arrays.asList(
            SubscriptionStatus.PENDING, SubscriptionStatus.APPROVED,
            SubscriptionStatus.TESTING, SubscriptionStatus.ACTIVE
    );

    // ==================== Template Queries ====================

    public List<AgreementTemplate> getActiveTemplates() {
        return templateRepository.findByIsActiveTrue();
    }

    public Optional<AgreementTemplate> getActiveTemplate(String templateId) {
        return templateRepository.findByTemplateId(templateId)
                .filter(t -> Boolean.TRUE.equals(t.getIsActive()));
    }

    // ==================== Subscription Initiation ====================

    @Transactional
    public AgreementSubscription initiateSubscription(AgreementSubscription subscription) {
        log.info("Processing subscription request from: {} for dataholder: {}",
                subscription.getRequestorGroupName(), subscription.getDataholderId());

        // Validate template
        AgreementTemplate template = subscription.getTemplate();
        if (template == null || !Boolean.TRUE.equals(template.getIsActive())) {
            throw new IllegalArgumentException("This agreement is no longer accepting new subscriptions.");
        }
        if (template.getActiveRequestTypes().isEmpty()) {
            throw new IllegalArgumentException("Template has no active request types configured");
        }

        // Check blocking subscription for same group + dataholder
        if (subscriptionRepository.hasBlockingSubscription(
                subscription.getRequestorGroupId(), subscription.getDataholderId())) {
            throw new IllegalStateException(
                    "This requestor group already has a pending/active subscription for this data holder");
        }

        subscription.setStatus(SubscriptionStatus.PENDING);
        subscription.setStatusMessage("Subscription request received and pending review");
        subscription.setMaxQueriesPerDay(template.getMaxQueriesPerDay());
        subscription.setMaxQueriesPerMonth(template.getMaxQueriesPerMonth());

        subscription = subscriptionRepository.save(subscription);
        logStatusChange(subscription, null, "PENDING", "SYSTEM",
                "Subscription request received", "GROUP_ADMIN");

        log.info("Subscription created: {} for group {} at dataholder {}",
                subscription.getRequestId(), subscription.getRequestorGroupName(),
                subscription.getDataholderId());

        return subscription;
    }

    // ==================== Status Query ====================

    public Optional<AgreementSubscription> getSubscriptionByRequestId(String requestId) {
        return subscriptionRepository.findByRequestId(requestId);
    }

    // ==================== Approval Workflow ====================

    @Transactional
    public AgreementSubscription approveSubscription(Long id, String reviewedBy, String notes) {
        AgreementSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (subscription.getStatus() != SubscriptionStatus.PENDING) {
            throw new IllegalStateException("Subscription is not in PENDING status");
        }

        String previousStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.APPROVED);
        subscription.setReviewedBy(reviewedBy);
        subscription.setReviewedAt(LocalDateTime.now());
        subscription.setReviewNotes(notes);
        subscription.setStatusMessage("Subscription approved, ready for testing");
        subscription.setStatusChangedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);
        logStatusChange(subscription, previousStatus, "APPROVED", reviewedBy, notes, "GROUP_ADMIN");

        log.info("Subscription {} approved by {}", subscription.getRequestId(), reviewedBy);
        return subscription;
    }

    @Transactional
    public AgreementSubscription denySubscription(Long id, String reviewedBy, String reason) {
        AgreementSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (subscription.getStatus() != SubscriptionStatus.PENDING) {
            throw new IllegalStateException("Subscription is not in PENDING status");
        }

        String previousStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.DENIED);
        subscription.setReviewedBy(reviewedBy);
        subscription.setReviewedAt(LocalDateTime.now());
        subscription.setReviewNotes(reason);
        subscription.setStatusMessage("Subscription denied: " + reason);
        subscription.setStatusChangedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);
        logStatusChange(subscription, previousStatus, "DENIED", reviewedBy, reason, "GROUP_ADMIN");

        log.info("Subscription {} denied by {}: {}", subscription.getRequestId(), reviewedBy, reason);
        return subscription;
    }

    // ==================== Testing Workflow ====================

    @Transactional
    public AgreementSubscription startTesting(Long id, String initiatedBy) {
        AgreementSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (subscription.getStatus() != SubscriptionStatus.APPROVED) {
            throw new IllegalStateException("Subscription must be APPROVED to start testing");
        }

        String previousStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.TESTING);
        subscription.setTestStartedAt(LocalDateTime.now());
        subscription.setStatusMessage("Testing in progress");
        subscription.setStatusChangedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);
        logStatusChange(subscription, previousStatus, "TESTING", initiatedBy, "Testing initiated", "GROUP_ADMIN");

        log.info("Testing started for subscription {}", subscription.getRequestId());
        return subscription;
    }

    @Transactional
    public AgreementSubscription startTestingByRequestId(String requestId, String initiatedBy) {
        AgreementSubscription subscription = subscriptionRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (subscription.getStatus() != SubscriptionStatus.APPROVED) {
            throw new IllegalStateException("Subscription must be APPROVED to start testing");
        }

        String previousStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.TESTING);
        subscription.setTestStartedAt(LocalDateTime.now());
        subscription.setStatusMessage("Testing in progress");
        subscription.setStatusChangedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);
        logStatusChange(subscription, previousStatus, "TESTING", initiatedBy, "Testing initiated", "GROUP_ADMIN");

        return subscription;
    }

    @Transactional
    public AgreementSubscription recordTestResult(Long id, String result, String details) {
        AgreementSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (subscription.getStatus() != SubscriptionStatus.TESTING) {
            throw new IllegalStateException("Subscription must be in TESTING status");
        }

        subscription.setTestResult(result);
        subscription.setTestCompletedAt(LocalDateTime.now());
        subscription.setTestDetails(details);

        subscription = subscriptionRepository.save(subscription);
        log.info("Test result recorded for subscription {}: {}", subscription.getRequestId(), result);
        return subscription;
    }

    // ==================== Activation ====================

    @Transactional
    public AgreementSubscription activateSubscription(Long id, String activatedBy) {
        AgreementSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (subscription.getStatus() != SubscriptionStatus.TESTING) {
            throw new IllegalStateException("Subscription must be in TESTING status to activate");
        }

        if (!"PASSED".equals(subscription.getTestResult())) {
            throw new IllegalStateException("Tests must pass before activation");
        }

        String previousStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setEffectiveFrom(LocalDateTime.now());
        subscription.setActivatedAt(LocalDateTime.now());
        subscription.setActivatedBy(activatedBy);
        subscription.setStatusMessage("Subscription activated successfully");
        subscription.setStatusChangedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);
        logStatusChange(subscription, previousStatus, "ACTIVE", activatedBy, "Subscription activated", "GROUP_ADMIN");

        log.info("Subscription {} activated by {}", subscription.getRequestId(), activatedBy);
        return subscription;
    }

    @Transactional
    public AgreementSubscription activateByRequestId(String requestId, String activatedBy) {
        AgreementSubscription subscription = subscriptionRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (subscription.getStatus() != SubscriptionStatus.TESTING) {
            throw new IllegalStateException("Subscription must be in TESTING status to activate");
        }
        if (!"PASSED".equals(subscription.getTestResult())) {
            throw new IllegalStateException("Tests must pass before activation");
        }

        String previousStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setEffectiveFrom(LocalDateTime.now());
        subscription.setActivatedAt(LocalDateTime.now());
        subscription.setActivatedBy(activatedBy);
        subscription.setStatusMessage("Subscription activated successfully");
        subscription.setStatusChangedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);
        logStatusChange(subscription, previousStatus, "ACTIVE", activatedBy, "Subscription activated", "GROUP_ADMIN");

        return subscription;
    }

    // ==================== Suspend / Reactivate ====================

    @Transactional
    public AgreementSubscription suspendSubscription(Long id, String suspendedBy, String reason) {
        AgreementSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE subscriptions can be suspended");
        }

        String previousStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.SUSPENDED);
        subscription.setStatusMessage("Subscription suspended: " + reason);
        subscription.setStatusChangedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);
        logStatusChange(subscription, previousStatus, "SUSPENDED", suspendedBy, reason, "GROUP_ADMIN");

        log.info("Subscription {} suspended by {}: {}", subscription.getRequestId(), suspendedBy, reason);
        return subscription;
    }

    @Transactional
    public AgreementSubscription reactivateSubscription(Long id, String reactivatedBy) {
        AgreementSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (subscription.getStatus() != SubscriptionStatus.SUSPENDED) {
            throw new IllegalStateException("Only SUSPENDED subscriptions can be reactivated");
        }

        String previousStatus = subscription.getStatus().name();
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStatusMessage("Subscription reactivated");
        subscription.setStatusChangedAt(LocalDateTime.now());

        subscription = subscriptionRepository.save(subscription);
        logStatusChange(subscription, previousStatus, "ACTIVE", reactivatedBy, "Subscription reactivated", "GROUP_ADMIN");

        log.info("Subscription {} reactivated by {}", subscription.getRequestId(), reactivatedBy);
        return subscription;
    }

    // ==================== Query Methods ====================

    public List<AgreementSubscription> getActiveSubscriptions() {
        return subscriptionRepository.findActiveAndEffective(LocalDateTime.now());
    }

    public List<AgreementSubscription> getActiveSubscriptionsForGroup(String requestorGroupId) {
        return subscriptionRepository.findActiveAndEffectiveByRequestorGroup(requestorGroupId, LocalDateTime.now());
    }

    public List<AgreementSubscription> getActiveSubscriptionsForDataholder(String dataholderId) {
        return subscriptionRepository.findActiveAndEffectiveByDataholder(dataholderId, LocalDateTime.now());
    }

    // ==================== Helper Methods ====================

    private void logStatusChange(AgreementSubscription subscription, String previousStatus,
                                 String newStatus, String changedBy, String reason, String source) {
        log.info("Status change for {}: {} -> {} by {} ({})",
                subscription.getRequestId(), previousStatus, newStatus, changedBy, reason);

        AgreementStatusLog statusLog = AgreementStatusLog.builder()
                .subscription(subscription)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedBy(changedBy)
                .changeReason(reason)
                .source(source)
                .build();
        statusLogRepository.save(statusLog);
    }

}
