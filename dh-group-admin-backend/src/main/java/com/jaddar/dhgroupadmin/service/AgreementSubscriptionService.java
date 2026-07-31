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
    private final com.jaddar.dhgroupadmin.config.MtlsEndpoints mtlsEndpoints;

    private static final List<SubscriptionStatus> BLOCKING_STATUSES = Arrays.asList(
            SubscriptionStatus.PENDING, SubscriptionStatus.APPROVED,
            SubscriptionStatus.TESTING, SubscriptionStatus.ACTIVE
    );

    // ==================== Template Queries ====================

    public List<AgreementTemplate> getPublishedTemplates() {
        return templateRepository.findByIsPublishedTrue();
    }

    public Optional<AgreementTemplate> getPublishedTemplate(String templateId) {
        return templateRepository.findByTemplateId(templateId)
                .filter(AgreementTemplate::getIsPublished);
    }

    // ==================== Subscription Initiation ====================

    @Transactional
    public AgreementSubscription initiateSubscription(AgreementSubscription subscription) {
        log.info("Processing subscription request from: {} for dataholder: {}",
                subscription.getRequestorGroupName(), subscription.getDataholderId());

        // Validate template
        AgreementTemplate template = subscription.getTemplate();
        if (template == null || !template.getIsPublished()) {
            throw new IllegalArgumentException("Template not found or not available");
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
        notifyDataHolder(subscription, previousStatus);

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
        notifyDataHolder(subscription, previousStatus);

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
        notifyDataHolder(subscription, previousStatus);

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
        notifyDataHolder(subscription, previousStatus);

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
        notifyDataHolder(subscription, previousStatus);

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
        notifyDataHolder(subscription, previousStatus);

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
        notifyDataHolder(subscription, previousStatus);

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
        notifyDataHolder(subscription, previousStatus);

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

    /** Notify the data holder of a subscription status change via its callback URL. */
    private void notifyDataHolder(AgreementSubscription subscription, String previousStatus) {
        if (subscription.getDataholderUrl() == null) {
            log.debug("No dataholder URL for subscription {}, skipping notification", subscription.getRequestId());
            return;
        }

        try {
            var payload = java.util.Map.of(
                    "requestId", subscription.getRequestId(),
                    "previousStatus", previousStatus != null ? previousStatus : "",
                    "newStatus", subscription.getStatus().name(),
                    "message", subscription.getStatusMessage() != null ? subscription.getStatusMessage() : "",
                    "introspectionUrl", subscription.getIntrospectionUrl() != null ? subscription.getIntrospectionUrl() : "",
                    "changedAt", LocalDateTime.now().toString()
            );

            // Route the callback over the data holder's mTLS listener when enabled.
            String callbackUrl = mtlsEndpoints.resolve(subscription.getDataholderUrl())
                    + "/api/agreements/external/status-callback";

            webClientBuilder.build()
                    .post()
                    .uri(callbackUrl)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .subscribe(
                            result -> log.info("Dataholder notification sent for subscription {}",
                                    subscription.getRequestId()),
                            error -> log.error("Failed to notify dataholder for {}: {}",
                                    subscription.getRequestId(), error.getMessage())
                    );
        } catch (Exception e) {
            log.error("Error notifying dataholder for {}: {}", subscription.getRequestId(), e.getMessage());
        }
    }
}
