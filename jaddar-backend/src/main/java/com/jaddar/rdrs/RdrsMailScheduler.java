/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.rdrs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RdrsMailScheduler {

    private final RdrsMailIngestService ingest;

    // Pulls newly stored mail from Mailgun, which discards it after a few days.
    @Scheduled(
            initialDelayString = "${jaddar.rdrs.mail.poll-interval-seconds:120}",
            fixedDelayString = "${jaddar.rdrs.mail.poll-interval-seconds:120}",
            timeUnit = TimeUnit.SECONDS)
    public void pollInbox() {
        if (!ingest.isConfigured()) return;
        int stored = ingest.poll();
        if (stored > 0) {
            log.info("RDRS mail poll captured {} new message(s)", stored);
        }
    }

    // Applies the configured retention; no-op while retention is indefinite.
    @Scheduled(cron = "${jaddar.rdrs.mail.purge-cron:0 30 3 * * *}")
    public void purge() {
        if (!ingest.isConfigured()) return;
        ingest.purgeExpired();
    }
}
