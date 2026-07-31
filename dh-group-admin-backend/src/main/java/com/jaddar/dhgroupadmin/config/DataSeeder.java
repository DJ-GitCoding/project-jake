/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.config;

import com.jaddar.dhgroupadmin.controller.AuthController;
import com.jaddar.dhgroupadmin.entity.AgreementRdapParameters;
import com.jaddar.dhgroupadmin.entity.AgreementRequestType;
import com.jaddar.dhgroupadmin.entity.AgreementTemplate;
import com.jaddar.dhgroupadmin.entity.DataHolderCredential;
import com.jaddar.dhgroupadmin.entity.DataHolderGroup;
import com.jaddar.dhgroupadmin.entity.User;
import com.jaddar.dhgroupadmin.repository.AgreementTemplateRepository;
import com.jaddar.dhgroupadmin.repository.DataHolderCredentialRepository;
import com.jaddar.dhgroupadmin.repository.DataHolderGroupRepository;
import com.jaddar.dhgroupadmin.repository.UserRepository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AgreementTemplateRepository templateRepository;
    private final DataHolderGroupRepository dataHolderGroupRepository;
    private final DataHolderCredentialRepository dataHolderCredentialRepository;

    // Running counter so seeded request type codes are globally unique
    // (system-wide), matching the runtime auto-generation.
    private int seedTypeCode = 0;

    @org.springframework.beans.factory.annotation.Value("${admin.default-email:}")
    private String defaultAdminEmail;

    @org.springframework.beans.factory.annotation.Value("${admin.default-password:#{null}}")
    private String defaultAdminPassword;

    @Override
    public void run(String... args) {
        seedUsers();
        seedDataHolderGroups();
        seedTemplates();
        hashLegacyPlaintextClientSecrets();
    }

    /**
     * One-time, idempotent migration that BCrypt-hashes any client secrets still
     * stored as plaintext (secret_hashed = false OR NULL), so no plaintext client
     * secret remains at rest. The data holder's original plaintext keeps
     * authenticating afterward because {@code verifySecret} uses BCrypt matches.
     *
     * <p>Idempotent: only rows flagged as unhashed are touched, and once hashed
     * they are flagged secret_hashed = true so subsequent startups skip them.
     * Data-only — no schema change, so no Flyway ordering concerns.
     */
    private void hashLegacyPlaintextClientSecrets() {
        List<DataHolderCredential> unhashed =
                dataHolderCredentialRepository.findBySecretHashedFalseOrSecretHashedIsNull();
        if (unhashed.isEmpty()) {
            log.info("Client secret migration: no plaintext secrets found, nothing to hash");
            return;
        }

        int migrated = 0;
        int skipped = 0;
        for (DataHolderCredential cred : unhashed) {
            String current = cred.getClientSecret();
            // Safety guard if the stored value already looks like a BCrypt hash.
            if (current != null
                    && (current.startsWith("$2a$") || current.startsWith("$2b$") || current.startsWith("$2y$"))) {
                cred.setSecretHashed(true);
                dataHolderCredentialRepository.save(cred);
                skipped++;
                continue;
            }
            cred.hashSecretForStorage(); // BCrypts plaintext, sets secretHashed = true
            dataHolderCredentialRepository.save(cred);
            migrated++;
        }
        log.info("Client secret migration: hashed {} plaintext secret(s); corrected flag on {} already-hashed row(s)",
                migrated, skipped);
    }

    private void seedDataHolderGroups() {
        if (dataHolderGroupRepository.count() == 0) {
            log.info("No data holder groups found, creating default group...");
            DataHolderGroup defaultGroup = DataHolderGroup.builder()
                    .name("Default Group")
                    .description("Default data holder group created during initial setup")
                    .isActive(true)
                    .build();
            dataHolderGroupRepository.save(defaultGroup);
            log.info("Default data holder group created: '{}'", defaultGroup.getName());
        } else {
            log.info("Data holder groups table already has {} records, skipping seed", dataHolderGroupRepository.count());
        }
    }

    private void seedUsers() {
        if (userRepository.count() == 0) {
            log.info("No users found, creating default admin user...");
            String pw = defaultAdminPassword != null ? defaultAdminPassword : java.util.UUID.randomUUID().toString().substring(0, 12);
            User admin = User.builder()
                    .firstName("Admin")
                    .lastName("User")
                    .email(defaultAdminEmail)
                    .password(AuthController.hashPassword(pw))
                    .type(1)
                    .isActive(true)
                    .build();
            userRepository.save(admin);
            if (defaultAdminPassword != null) {
                log.info("Default admin user created: {}", defaultAdminEmail);
            } else {
                log.warn("Default admin user created: {} — generated password: {} (change this immediately)", defaultAdminEmail, pw);
            }
        } else {
            log.info("Users table already has {} records, skipping seed", userRepository.count());
        }
    }

    private void seedTemplates() {
        if (templateRepository.count() > 0) {
            log.info("Agreement templates table already has {} records, skipping seed", templateRepository.count());
            return;
        }

        log.info("No agreement templates found, seeding defaults...");

        // Look up the default group to associate templates with it
        Long defaultGroupId = dataHolderGroupRepository.findAll().stream()
                .findFirst().map(DataHolderGroup::getId).orElse(null);
        if (defaultGroupId == null) {
            log.warn("No data holder group found — templates will be created without a group assignment");
        }

        // 1. Public Access Agreement
        createTemplate(defaultGroupId,
                "TPL-PUBLIC-ACCESS",
                "Public Access Agreement",
                "Public access",
                "Basic access for authenticated users. Provides Level 1 access to public RDAP information plus basic contact details.",
                null,
                "Standard terms apply. Data may only be used for legitimate purposes.",
                "Data must not be used for mass marketing, spam, or any illegal purpose.",
                1000, 30000, true,
                new RequestTypeSeed("standard", 1, "Standard public access request", 1, false, false, false)
        );

        // 2. Law Enforcement Access Agreement
        createTemplate(defaultGroupId,
                "TPL-LAW-ENFORCEMENT",
                "Law Enforcement Access Agreement",
                "Law enforcement",
                "Full access for verified law enforcement agencies. Provides Level 3 access including all registrant PII.",
                "law-enforcement,police,government",
                "Restricted to verified law enforcement use only. All queries are logged and audited.",
                "Data accessed under this agreement is for law enforcement purposes only. Unauthorized disclosure is prohibited.",
                500, 15000, true,
                new RequestTypeSeed("standard", 1, "Standard law enforcement request", 3, false, false, true),
                new RequestTypeSeed("confidential", 2, "Confidential disclosure request", 3, true, false, true),
                new RequestTypeSeed("exigent", 3, "Exigent/emergency disclosure request", 3, false, true, false)
        );

        // 3. Domain Registrar Access Agreement
        createTemplate(defaultGroupId,
                "TPL-REGISTRAR",
                "Domain Registrar Access Agreement",
                "Registrar access",
                "Enhanced access for ICANN-accredited registrars. Provides Level 2 access.",
                "registrar,registry",
                "For use by ICANN-accredited registrars only.",
                "Data may be used for registrar operations, abuse mitigation, and compliance with ICANN policies.",
                2000, 60000, true,
                new RequestTypeSeed("standard", 1, "Standard registrar access request", 2, false, false, true)
        );

        // 4. Security Research Access Agreement
        createTemplate(defaultGroupId,
                "TPL-SECURITY",
                "Security Research Access Agreement",
                "Security research",
                "Enhanced access for security professionals. Provides Level 2 access for threat intelligence.",
                "security,cert,csirt",
                "For legitimate security research purposes only.",
                "Data may be used for security research, incident response, and threat intelligence. Bulk data extraction is prohibited.",
                500, 15000, true,
                new RequestTypeSeed("standard", 1, "Standard security research request", 2, false, false, true)
        );

        // 5. Brand Protection Access Agreement
        createTemplate(defaultGroupId,
                "TPL-BRAND-PROTECTION",
                "Brand Protection Access Agreement",
                "Brand protection",
                "Enhanced access for brand protection and IP enforcement. Provides Level 2 access.",
                "brand-protection,legal,trademark",
                "For legitimate brand protection and trademark enforcement only.",
                "Data may be used for trademark enforcement, anti-counterfeiting, and brand protection activities.",
                300, 10000, true,
                new RequestTypeSeed("standard", 1, "Standard brand protection request", 2, false, false, true)
        );

        log.info("Seeded {} agreement templates", templateRepository.count());
    }

    private void createTemplate(Long dataHolderGroupId, String templateId, String name, String shortDescription, String description,
                                 String requiredGroupTypes, String termsAndConditions, String dataUsagePolicy,
                                 Integer maxQueriesPerDay, Integer maxQueriesPerMonth,
                                 boolean isPublished,
                                 RequestTypeSeed... requestTypes) {

        if (templateRepository.existsByTemplateId(templateId)) {
            log.info("Template {} already exists, skipping", templateId);
            return;
        }

        AgreementTemplate template = AgreementTemplate.builder()
                .templateId(templateId)
                .name(name)
                .shortDescription(shortDescription)
                .description(description)
                .requiredGroupTypes(requiredGroupTypes)
                .termsAndConditions(termsAndConditions)
                .dataUsagePolicy(dataUsagePolicy)
                .maxQueriesPerDay(maxQueriesPerDay)
                .maxQueriesPerMonth(maxQueriesPerMonth)
                .isPublished(isPublished)
                .createdBy("system")
                .dataHolderGroupId(dataHolderGroupId)
                .build();

        for (int i = 0; i < requestTypes.length; i++) {
            RequestTypeSeed rts = requestTypes[i];
            AgreementRequestType rt = AgreementRequestType.builder()
                    .name(rts.name)
                    .typeCode(++seedTypeCode)
                    .description(rts.description)
                    .accessLevel(rts.accessLevel)
                    .supportsConfidential(rts.supportsConfidential)
                    .supportsExigent(rts.supportsExigent)
                    .requiresManualApproval(rts.requiresManualApproval)
                    .sortOrder(i)
                    .isActive(true)
                    .rdapParameters(rts.accessLevel > 0 ? AgreementRdapParameters.createDefault() : AgreementRdapParameters.createPublicOnly())
                    .build();
            template.addRequestType(rt);
        }

        templateRepository.save(template);
        log.info("Created template: {} ({}) with {} request types", name, templateId, requestTypes.length);
    }

    private record RequestTypeSeed(String name, int typeCode, String description, int accessLevel,
                                    boolean supportsConfidential, boolean supportsExigent, boolean requiresManualApproval) {}
}
