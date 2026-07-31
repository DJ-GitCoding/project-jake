/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.scheduler;

import com.requestormanager.entity.SubscriptionRequest;
import com.requestormanager.repository.SubscriptionRequestRepository;
import com.requestormanager.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drives the post-approval subscription workflow automatically. Once a data holder has approved a
 * subscription, this advances it APPROVED → TESTING → (run tests) → ACTIVE without any user action —
 * one transition per tick so the frontend's status polling can render each intermediate state
 * (testing shows as "in progress" rather than being skipped).
 *
 * <p>Runs server-side, independent of any open browser session. Disable with
 * {@code subscription.auto-advance.enabled=false}; tune cadence with {@code .interval-ms}.
 *
 * @see SubscriptionService#autoAdvance(Long, String)
 */
@Component
@ConditionalOnProperty(name = "subscription.auto-advance.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SubscriptionAutoAdvanceScheduler {

    private final SubscriptionRequestRepository subscriptionRequestRepository;
    private final SubscriptionService subscriptionService;

    /** System identity recorded as the actor for automated transitions. */
    @Value("${subscription.auto-advance.actor:system@auto-advance}")
    private String systemActor;

    @Scheduled(
            fixedDelayString = "${subscription.auto-advance.interval-ms:10000}",
            initialDelayString = "${subscription.auto-advance.initial-delay-ms:15000}")
    public void advancePendingSubscriptions() {
        List<SubscriptionRequest> pending;
        try {
            // SUBMITTED, PENDING_REVIEW, APPROVED, TESTING — the pre-active states that may advance.
            pending = subscriptionRequestRepository.findPendingRequests();
        } catch (Exception e) {
            log.error("Auto-advance: failed to load pending subscriptions: {}", e.getMessage(), e);
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        log.debug("Auto-advance: evaluating {} pending subscription(s)", pending.size());
        for (SubscriptionRequest sr : pending) {
            Long id = sr.getId();
            try {
                subscriptionService.autoAdvance(id, systemActor);
            } catch (Exception e) {
                // Swallow per-subscription failures so one bad record doesn't stall the batch;
                // the next tick retries.
                log.error("Auto-advance failed for subscription id={}: {}", id, e.getMessage(), e);
            }
        }
    }
}
