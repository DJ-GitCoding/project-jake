/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.scheduler;

import com.requestormanager.service.SubscriptionCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Reconciles introspection credentials for active subscriptions against the data holder group.
 *
 * A subscription whose credentials never reached the group admin cannot authenticate any RDAP
 * query — the data holder has no client to introspect with. That state is invisible from the
 * requestor's side and, before this job, needed a manual fix per subscription. This asks the group
 * admin which subscriptions are missing credentials and repairs only those.
 *
 * The job only ever fills gaps: anything the group admin already holds is left alone, and a
 * subscription whose state cannot be determined is skipped rather than guessed at. See
 * {@link SubscriptionCredentialService#reconcileMissingCredentials()} for the full safety rules.
 *
 * <p>Runs once shortly after startup and hourly thereafter, offset from the top of the hour so it
 * does not contend with the requestor-group sync. Disable with
 * {@code subscription.credential-sync.enabled=false}; tune cadence with {@code .cron}, and the
 * startup pass with {@code .run-on-startup} / {@code .startup-delay-seconds}.
 */
@Component
@ConditionalOnProperty(name = "subscription.credential-sync.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SubscriptionCredentialSyncScheduler {

    private final SubscriptionCredentialService credentialService;
    private final TaskScheduler taskScheduler;

    @Value("${subscription.credential-sync.run-on-startup:true}")
    private boolean runOnStartup;

    @Value("${subscription.credential-sync.startup-delay-seconds:60}")
    private long startupDelaySeconds;

    @Scheduled(cron = "${subscription.credential-sync.cron:0 30 * * * *}")
    public void reconcileCredentials() {
        run("scheduled");
    }

    /**
     * Reconcile once shortly after startup, so a stack that comes up with unrepaired subscriptions
     * fixes itself immediately instead of waiting for the next cron tick.
     *
     * Deferred rather than run inline: the whole stack usually starts at once, and the group admin
     * is often not accepting requests yet when this service becomes ready. Reconciliation treats an
     * unreachable group admin as "unknown" and skips — safe, but useless — so the delay is what
     * makes the startup run actually do anything. It is scheduled rather than slept so application
     * startup is never blocked.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        if (!runOnStartup) {
            log.debug("Startup credential reconciliation disabled");
            return;
        }
        log.info("Scheduling startup introspection-credential reconciliation in {}s", startupDelaySeconds);
        taskScheduler.schedule(() -> run("startup"),
                Instant.now().plus(startupDelaySeconds, ChronoUnit.SECONDS));
    }

    private void run(String trigger) {
        try {
            log.debug("Running {} introspection-credential reconciliation", trigger);
            int repaired = credentialService.reconcileMissingCredentials();
            if (repaired > 0) {
                log.info("Credential reconciliation ({}) repaired {} subscription(s)", trigger, repaired);
            }
        } catch (Exception e) {
            // Swallow so a transient group-admin outage doesn't kill the scheduler; next run retries.
            log.error("Introspection-credential reconciliation ({}) failed: {}", trigger, e.getMessage(), e);
        }
    }
}
