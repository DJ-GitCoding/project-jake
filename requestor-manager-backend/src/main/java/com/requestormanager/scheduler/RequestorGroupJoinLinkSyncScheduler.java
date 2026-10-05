/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.scheduler;

import com.requestormanager.service.RequestorGroupJoinLinkService;
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
 * Keeps every active introspection client's {@code joining_url} claim in line with its requestor
 * group's joining link, repairing any change Keycloak did not accept when the link was edited, and
 * covering clients provisioned before the group had one.
 *
 * <p>Runs shortly after startup and hourly thereafter, offset from the credential sync. Disable
 * with {@code subscription.join-link-sync.enabled=false}; tune with {@code .cron}.
 */
@Component
@ConditionalOnProperty(name = "subscription.join-link-sync.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RequestorGroupJoinLinkSyncScheduler {

    private final RequestorGroupJoinLinkService joinLinkService;
    private final TaskScheduler taskScheduler;

    @Value("${subscription.join-link-sync.startup-delay-seconds:90}")
    private long startupDelaySeconds;

    @Scheduled(cron = "${subscription.join-link-sync.cron:0 45 * * * *}")
    public void reconcileJoinLinks() {
        run("scheduled");
    }

    /** Deferred for the same reason as the credential sync: Keycloak may not be up yet. */
    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        taskScheduler.schedule(() -> run("startup"),
                Instant.now().plus(startupDelaySeconds, ChronoUnit.SECONDS));
    }

    private void run(String trigger) {
        try {
            int synced = joinLinkService.reconcileAll();
            log.debug("Joining-link sync ({}) confirmed {} introspection client(s)", trigger, synced);
        } catch (Exception e) {
            // Swallow so a Keycloak outage doesn't kill the scheduler; the next run retries.
            log.error("Joining-link sync ({}) failed: {}", trigger, e.getMessage(), e);
        }
    }
}
