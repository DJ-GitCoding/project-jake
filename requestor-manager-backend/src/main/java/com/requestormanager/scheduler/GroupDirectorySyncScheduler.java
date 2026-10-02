/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.scheduler;

import com.requestormanager.service.GroupDirectoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps the local mirror of the central repository current, without anyone pressing refresh. */
@Component
@RequiredArgsConstructor
@Slf4j
public class GroupDirectorySyncScheduler {

    private final GroupDirectoryService directoryService;

    @Scheduled(fixedDelayString = "${registry.directory.sync-interval-ms:900000}",
               initialDelayString = "${registry.directory.sync-initial-delay-ms:60000}")
    public void syncDirectory() {
        if (!directoryService.isEnabled()) return;
        try {
            directoryService.refresh(false);
            int touched = directoryService.syncToLocal();
            if (touched > 0) {
                log.info("Central repository sync updated {} data holder group(s)", touched);
            }
        } catch (Exception e) {
            log.error("Central repository sync failed: {}", e.getMessage(), e);
        }
    }
}
