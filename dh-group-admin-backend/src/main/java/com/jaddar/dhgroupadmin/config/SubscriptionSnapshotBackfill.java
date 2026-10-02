/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.config;

import com.jaddar.dhgroupadmin.entity.AgreementSubscription;
import com.jaddar.dhgroupadmin.repository.AgreementSubscriptionRepository;
import com.jaddar.dhgroupadmin.service.ResponseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Pins every subscription that predates the template snapshot to its template's current state.
 * Runs in Java rather than SQL because the snapshot must match ResponseMapper.snapshotTemplate
 * exactly — the data holder reads rdapParameters out of it to filter RDAP fields, so a
 * hand-built copy that drifts would silently change what those subscribers receive.
 */
@Component
@RequiredArgsConstructor
@Order(100)
@Slf4j
public class SubscriptionSnapshotBackfill implements CommandLineRunner {

    private final AgreementSubscriptionRepository subscriptionRepository;
    private final ResponseMapper mapper;

    @Override
    @Transactional
    public void run(String... args) {
        List<AgreementSubscription> missing = subscriptionRepository.findAll().stream()
                .filter(s -> s.getTemplateSnapshot() == null && s.getTemplate() != null)
                .toList();

        if (missing.isEmpty()) return;

        for (AgreementSubscription sub : missing) {
            sub.setTemplateSnapshot(mapper.snapshotTemplate(sub.getTemplate()));
        }
        subscriptionRepository.saveAll(missing);
        log.info("Pinned {} subscription(s) to their template's current state", missing.size());
    }
}
