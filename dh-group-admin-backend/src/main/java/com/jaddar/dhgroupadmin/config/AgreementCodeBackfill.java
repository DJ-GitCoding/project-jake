/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.config;

import com.jaddar.dhgroupadmin.entity.AgreementTemplate;
import com.jaddar.dhgroupadmin.repository.AgreementTemplateRepository;
import com.jaddar.dhgroupadmin.service.AgreementCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class AgreementCodeBackfill implements CommandLineRunner {

    private final AgreementTemplateRepository templateRepository;
    private final AgreementCodeGenerator codeGenerator;

    @Override
    public void run(String... args) {
        try {
            List<AgreementTemplate> missing = templateRepository.findByAgreementCodeIsNull();
            if (missing.isEmpty()) return;

            for (AgreementTemplate template : missing) {
                template.setAgreementCode(
                        codeGenerator.generate(template.getTemplateId(), template.getName()));
            }
            templateRepository.saveAll(missing);
            log.info("Minted agreement codes for {} template(s) that had none", missing.size());
        } catch (Exception e) {
            // Never block startup on a best-effort backfill; the next boot retries.
            log.warn("Agreement code backfill skipped: {}", e.getMessage());
        }
    }
}
