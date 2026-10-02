/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.scheduler;

import com.requestormanager.service.KeycloakUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recreates requestor groups missing from Keycloak. RM is the source of truth: this job only
 * adds, never deletes, so a Keycloak-side deletion is corrected as drift rather than propagated.
 * Runs hourly by default; see {@code keycloak.group-sync.enabled}/{@code .cron}.
 */
@Component
@ConditionalOnProperty(name = "keycloak.group-sync.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RequestorGroupSyncScheduler {

    private final KeycloakUserService keycloakUserService;

    @Scheduled(cron = "${keycloak.group-sync.cron:0 0 * * * *}")
    public void reconcileRequestorGroups() {
        try {
            log.debug("Running scheduled requestor-group → Keycloak reconciliation");
            keycloakUserService.syncRequestorGroupsToKeycloak();
        } catch (Exception e) {
            // Swallow so a transient Keycloak outage doesn't kill the scheduler; next run retries.
            log.error("Requestor-group Keycloak reconciliation failed: {}", e.getMessage(), e);
        }
    }
}
