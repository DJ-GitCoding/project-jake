/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.scheduler;

import com.jaddar.dhgroupadmin.entity.RegistryPublicationSettings;
import com.jaddar.dhgroupadmin.service.RegistryPublicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps this deployment's entry in the common repository current, the globally published templates change as admins edit them, and the repository has... */
@Component
@RequiredArgsConstructor
@Slf4j
public class RegistryAnnounceScheduler {
    private final RegistryPublicationService publicationService;

    @Scheduled(fixedDelayString = "${registry.publication.interval-ms:900000}",
               initialDelayString = "${registry.publication.initial-delay-ms:120000}")
    public void announce() {
        RegistryPublicationSettings settings = publicationService.getSettings();
        if (!settings.isReady()) return;

        RegistryPublicationSettings result = publicationService.announce(false);
        if ("FAILURE".equals(result.getLastAnnounceStatus())) {
            log.warn("Scheduled announcement to the common repository failed: {}",
                    result.getLastAnnounceMessage());
        }
    }
}
