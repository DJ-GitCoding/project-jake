/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.scheduler;

import com.jaddar.dhgroupadmin.entity.AgreementSubscription;
import com.jaddar.dhgroupadmin.entity.AgreementSubscription.SubscriptionStatus;
import com.jaddar.dhgroupadmin.repository.AgreementSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Terminates subscriptions that let a mandatory template change lapse past its deadline. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingChangeDeadlineScheduler {

    private final AgreementSubscriptionRepository subscriptionRepository;

    @Scheduled(fixedDelayString = "${subscription.pending-change.check-interval-ms:300000}",
               initialDelayString = "${subscription.pending-change.initial-delay-ms:60000}")
    @Transactional
    public void terminateLapsedChanges() {
        LocalDateTime now = LocalDateTime.now();
        List<AgreementSubscription> lapsed = subscriptionRepository.findAll().stream()
                .filter(s -> "PROPOSED".equals(s.getPendingChangeStatus()))
                .filter(s -> "FORCED".equals(s.getPendingChangeMode()))
                .filter(s -> s.getPendingChangeDeadline() != null && s.getPendingChangeDeadline().isBefore(now))
                .filter(s -> s.getStatus() != SubscriptionStatus.CANCELLED)
                .toList();

        for (AgreementSubscription sub : lapsed) {
            sub.setStatus(SubscriptionStatus.CANCELLED);
            sub.setStatusMessage("Terminated: a required agreement change was not accepted by "
                    + sub.getPendingChangeDeadline() + ".");
            sub.setStatusChangedAt(now);
            sub.setPendingChangeStatus("EXPIRED");
            sub.setPendingRespondedAt(now);
            subscriptionRepository.save(sub);
            log.warn("Terminated subscription {} — required change not accepted by {}",
                    sub.getRequestId(), sub.getPendingChangeDeadline());
        }
    }
}
