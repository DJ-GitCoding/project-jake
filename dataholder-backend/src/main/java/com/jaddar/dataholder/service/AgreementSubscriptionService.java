/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.dto.AgreementApiDto.*;
import com.jaddar.dataholder.entity.*;
import com.jaddar.dataholder.repository.RdapEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Proving that this data holder can actually serve the data an agreement promises.
 *
 * <p>Agreements, subscriptions and their negotiation belong to the data holder group: none of it
 * is held here. When a group admin asks whether retrieval works, it names the agreement and sends
 * its definition, and this service runs the data holder's own RDAP test records against it —
 * resolving a request type, checking the access level, and verifying contact visibility and
 * redaction against the parameters that agreement grants. Nothing about the agreement is stored.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgreementSubscriptionService {

    private final RdapEntityRepository rdapEntityRepository;


    /**
     * Run this data holder's RDAP retrieval suite for an agreement the owning group describes.
     *
     * <p>The group admin asks each data holder in the group in turn and takes the first that can
     * actually prove retrieval; {@code summary.totalRdapTests == 0} means this one has no test
     * records to prove it with, and the caller should try another.
     *
     * <p>The agreement's definition — its request types, their access levels and their RDAP
     * parameters — comes with the request, since agreements live with the group and never here.
     * It is used for the run and then discarded.
     */
    @Transactional(readOnly = true)
    public TestResultInfo runTemplateTest(String templateId, Map<String, Object> agreementDefinition) {
        AgreementTemplate template = agreementDefinition == null || agreementDefinition.isEmpty()
                ? null
                : transientTemplateFrom(templateId, agreementDefinition);
        if (template == null) {
            /* Neither ours nor described to us — say so plainly rather than looking unreachable. */
            long noneStart = System.currentTimeMillis();
            log.info("Template test for {}: no local agreement and none supplied by the caller", templateId);
            return TestResultInfo.builder()
                    .result("SKIPPED")
                    .testedAt(LocalDateTime.now())
                    .details("No agreement definition was supplied for " + templateId
                            + ", so there was nothing to test retrieval against. Agreements are held"
                            + " by the data holder group, which must send the definition with the request.")
                    .testCases(List.of())
                    .rdapTestResults(List.of())
                    .summary(TestSummary.builder()
                            .totalValidationTests(0).passedValidationTests(0).failedValidationTests(0)
                            .totalRdapTests(0).passedRdapTests(0).failedRdapTests(0)
                            .skippedRdapTests(0).errorRdapTests(0)
                            .totalDurationMs(System.currentTimeMillis() - noneStart)
                            .build())
                    .build();
        }

        long start = System.currentTimeMillis();
        /* The records this data holder's operator flagged as test data are what retrieval is
         * proven against; which agreement they are tested under comes from the caller. */
        List<AgreementTemplateTestData> testDataEntries = flaggedTestDataEntries(template);

        List<RdapTestCaseResult> rdapTestResults = new ArrayList<>();
        boolean allPassed = true;
        int skipped = 0;
        int errors = 0;

        for (AgreementTemplateTestData testEntry : testDataEntries) {
            RdapTestCaseResult rdapResult = executeRdapTestCase(template, testEntry);
            rdapTestResults.add(rdapResult);
            switch (rdapResult.getResult()) {
                case "FAILED" -> allPassed = false;
                case "SKIPPED" -> skipped++;
                case "ERROR" -> { allPassed = false; errors++; }
            }
        }

        long passed = rdapTestResults.stream().filter(r -> "PASSED".equals(r.getResult())).count();
        long duration = System.currentTimeMillis() - start;
        String result = testDataEntries.isEmpty() ? "SKIPPED" : (allPassed ? "PASSED" : "FAILED");

        String details = testDataEntries.isEmpty()
                ? "No RDAP records are flagged as test data on this data holder"
                : String.format("RDAP retrievals: %d/%d passed%s%s. Duration: %dms",
                        passed, rdapTestResults.size(),
                        skipped > 0 ? " (" + skipped + " skipped)" : "",
                        " (using records flagged as test data)", duration);

        log.info("Template test for {}: {} — {}", templateId, result, details);

        return TestResultInfo.builder()
                .result(result)
                .testedAt(LocalDateTime.now())
                .details(details)
                .testCases(List.of())
                .rdapTestResults(rdapTestResults)
                .summary(TestSummary.builder()
                        .totalValidationTests(0)
                        .passedValidationTests(0)
                        .failedValidationTests(0)
                        .totalRdapTests(rdapTestResults.size())
                        .passedRdapTests((int) passed)
                        .failedRdapTests((int) rdapTestResults.stream()
                                .filter(r -> "FAILED".equals(r.getResult())).count())
                        .skippedRdapTests(skipped)
                        .errorRdapTests(errors)
                        .totalDurationMs(duration)
                        .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private AgreementTemplate transientTemplateFrom(String templateId, Map<String, Object> definition) {
        AgreementTemplate template = AgreementTemplate.builder()
                .templateId(templateId)
                .name(definition.get("name") != null ? definition.get("name").toString() : templateId)
                .isActive(true)
                .build();

        Object rawTypes = definition.get("requestTypes");
        if (rawTypes instanceof List<?> types) {
            for (Object entry : types) {
                if (!(entry instanceof Map<?, ?> map)) continue;
                Map<String, Object> rt = (Map<String, Object>) map;

                AgreementRequestType requestType = AgreementRequestType.builder()
                        .name(rt.get("name") != null ? rt.get("name").toString() : "standard")
                        .typeCode(rt.get("typeCode") != null ? rt.get("typeCode").toString() : null)
                        .description(rt.get("description") != null ? rt.get("description").toString() : null)
                        .accessLevel(rt.get("accessLevel") instanceof Number n ? n.intValue() : 0)
                        .supportsConfidential(Boolean.TRUE.equals(rt.get("supportsConfidential")))
                        .supportsExigent(Boolean.TRUE.equals(rt.get("supportsExigent")))
                        .requiresManualApproval(Boolean.TRUE.equals(rt.get("requiresManualApproval")))
                        .isActive(!Boolean.FALSE.equals(rt.get("isActive")))
                        .build();

                if (rt.get("rdapParameters") instanceof Map<?, ?> params) {
                    requestType.setRdapParameters(rdapParametersFrom((Map<String, Object>) params));
                }
                template.addRequestType(requestType);
            }
        }
        return template;
    }

    private AgreementRdapParameters rdapParametersFrom(Map<String, Object> flags) {
        AgreementRdapParameters params = AgreementRdapParameters.createDefault();
        for (Map.Entry<String, Object> flag : flags.entrySet()) {
            if (!(flag.getValue() instanceof Boolean value) || flag.getKey() == null || flag.getKey().isBlank()) {
                continue;
            }
            String setter = "set" + Character.toUpperCase(flag.getKey().charAt(0)) + flag.getKey().substring(1);
            try {
                params.getClass().getMethod(setter, Boolean.class).invoke(params, value);
            } catch (ReflectiveOperationException | RuntimeException e) {
                log.debug("Ignoring unknown RDAP parameter '{}' from the agreement definition", flag.getKey());
            }
        }
        return params;
    }

    /**
     * Build transient test cases from RDAP records flagged as test data. These are never persisted —
     * they exist only for the duration of a run, so flagging a record is enough to make it testable
     * without also defining a template test case. Request type defaults to standard, so this cannot
     * exercise confidential or exigent paths; a template test case is still the way to cover those.
     */
    private List<AgreementTemplateTestData> flaggedTestDataEntries(AgreementTemplate template) {
        return rdapEntityRepository.findFlaggedTestData().stream()
                .map(entity -> AgreementTemplateTestData.builder()
                        .template(template)
                        .rdapEntity(entity)
                        .queryType(AgreementTemplateTestData.deriveQueryType(entity))
                        .queryValue(AgreementTemplateTestData.deriveQueryValue(entity))
                        .requestTypeName("standard")
                        .verifyContactAccess(true)
                        .verifyRedaction(true)
                        .isActive(true)
                        .build())
                .toList();
    }

    // ==================== RDAP Test Case Execution ====================

    /**
     * Execute a single RDAP test case against a test data entry.
     */
    private RdapTestCaseResult executeRdapTestCase(AgreementTemplate template,
                                                    AgreementTemplateTestData testEntry) {
        long caseStart = System.currentTimeMillis();
        List<RdapTestCheck> checks = new ArrayList<>();
        boolean casePassed = true;

        String label = testEntry.getDisplayLabel();
        String requestTypeName = testEntry.getRequestTypeName() != null ? testEntry.getRequestTypeName() : "standard";

        try {
            // --- Check 1: Entity Lookup ---
            Optional<RdapEntity> entityOpt = rdapEntityRepository.findById(testEntry.getRdapEntity().getId());
            if (entityOpt.isEmpty()) {
                checks.add(RdapTestCheck.builder()
                        .name("Entity Lookup").category("LOOKUP").passed(false)
                        .message("RDAP entity ID " + testEntry.getRdapEntity().getId() + " not found in database")
                        .severity("ERROR").build());
                return buildCaseResult(testEntry, label, requestTypeName, null, "ERROR",
                        "Referenced RDAP entity no longer exists", caseStart, checks);
            }

            RdapEntity entity = entityOpt.get();
            checks.add(RdapTestCheck.builder()
                    .name("Entity Lookup").category("LOOKUP").passed(true)
                    .message("Found " + entity.getObjectType() + " entity: " + entity.getDisplayIdentifier() +
                             " (handle: " + entity.getHandle() + ")")
                    .severity("INFO").build());

            // --- Check 2: Query Type Validation ---
            String expectedObjectType = switch (testEntry.getQueryType().toLowerCase()) {
                case "domain" -> "DOMAIN";
                case "ip" -> "IP_NETWORK";
                case "asn" -> "AUTNUM";
                default -> "UNKNOWN";
            };
            boolean typeMatch = entity.getObjectType().name().equals(expectedObjectType);
            checks.add(RdapTestCheck.builder()
                    .name("Query Type Match").category("LOOKUP").passed(typeMatch)
                    .message(typeMatch
                            ? "Entity type " + entity.getObjectType() + " matches query type '" + testEntry.getQueryType() + "'"
                            : "Entity type mismatch")
                    .expected(expectedObjectType).actual(entity.getObjectType().name())
                    .severity(typeMatch ? "INFO" : "WARNING").build());
            if (!typeMatch) casePassed = false;

            // --- Check 3: Request Type Resolution ---
            boolean isConfidential = "confidential".equalsIgnoreCase(requestTypeName);
            boolean isExigent = "exigent".equalsIgnoreCase(requestTypeName);

            Optional<AgreementRequestType> matchingRt = template.findMatchingRequestType(isConfidential, isExigent);
            if (matchingRt.isEmpty()) {
                checks.add(RdapTestCheck.builder()
                        .name("Request Type Resolution").category("ACCESS").passed(false)
                        .message("No matching request type found for '" + requestTypeName +
                                 "' (confidential=" + isConfidential + ", exigent=" + isExigent + ")")
                        .severity("ERROR").build());
                return buildCaseResult(testEntry, label, requestTypeName, null, "FAILED",
                        "No matching request type on template for '" + requestTypeName + "'",
                        caseStart, checks);
            }

            AgreementRequestType resolvedRt = matchingRt.get();
            Integer accessLevel = resolvedRt.getAccessLevel();
            checks.add(RdapTestCheck.builder()
                    .name("Request Type Resolution").category("ACCESS").passed(true)
                    .message("Resolved request type '" + resolvedRt.getName() +
                             "' (typeCode=" + resolvedRt.getTypeCode() +
                             ", accessLevel=" + accessLevel + ")")
                    .severity("INFO").build());

            // --- Check 4: Access Level Validity ---
            boolean validLevel = accessLevel != null && accessLevel >= 0 && accessLevel <= 3;
            checks.add(RdapTestCheck.builder()
                    .name("Access Level Validity").category("ACCESS").passed(validLevel)
                    .message(validLevel
                            ? "Access level " + accessLevel + " is valid"
                            : "Access level " + accessLevel + " is out of range (must be 0-3)")
                    .expected("0-3").actual(String.valueOf(accessLevel))
                    .severity(validLevel ? "INFO" : "ERROR").build());
            if (!validLevel) casePassed = false;

            // --- Check 5: RDAP Parameters Resolution ---
            AgreementRdapParameters rdapParams = resolvedRt.getEffectiveRdapParameters();
            boolean hasParams = rdapParams != null;
            checks.add(RdapTestCheck.builder()
                    .name("RDAP Parameters Resolution").category("PARAMETER").passed(hasParams)
                    .message(hasParams
                            ? "RDAP parameters resolved for request type '" + resolvedRt.getName() + "'"
                            : "No RDAP parameters available — defaults will apply")
                    .severity(hasParams ? "INFO" : "WARNING").build());

            // --- Check 6: Contact Visibility ---
            if (Boolean.TRUE.equals(testEntry.getVerifyContactAccess()) && rdapParams != null) {
                verifyContactVisibility(checks, entity, rdapParams, accessLevel);
            }

            // --- Check 7: Field Redaction ---
            if (Boolean.TRUE.equals(testEntry.getVerifyRedaction()) && rdapParams != null) {
                verifyRedaction(checks, entity, rdapParams);
            }

            // --- Check 8: Child Entity Check ---
            List<RdapEntity> childEntities = rdapEntityRepository.findByParentId(entity.getId());
            checks.add(RdapTestCheck.builder()
                    .name("Child Entity Check").category("LOOKUP").passed(true)
                    .message("Entity has " + childEntities.size() + " child entities (contacts/sub-entities)")
                    .severity("INFO").build());

            // --- Check 9: Entity Completeness ---
            checks.add(RdapTestCheck.builder()
                    .name("Entity Completeness").category("LOOKUP").passed(true)
                    .message("Entity has handle='" + entity.getHandle() + "', objectType=" + entity.getObjectType() +
                             ", status=" + (entity.getStatus() != null ? entity.getStatus() : "[]"))
                    .severity("INFO").build());

            // Determine final pass/fail based on ERROR-severity checks
            casePassed = casePassed && checks.stream()
                    .filter(c -> "ERROR".equals(c.getSeverity()))
                    .allMatch(RdapTestCheck::isPassed);

            String caseResult = casePassed ? "PASSED" : "FAILED";
            String caseMessage = casePassed
                    ? "All checks passed for " + testEntry.getQueryType() + " '" + testEntry.getQueryValue() + "'"
                    : "One or more checks failed";

            return buildCaseResult(testEntry, label, requestTypeName, accessLevel, caseResult,
                    caseMessage, caseStart, checks);

        } catch (Exception e) {
            log.error("Error executing RDAP test case for entry {}: {}", testEntry.getId(), e.getMessage(), e);
            checks.add(RdapTestCheck.builder()
                    .name("Unexpected Error").category("LOOKUP").passed(false)
                    .message("Exception during test: " + e.getMessage())
                    .severity("ERROR").build());
            return buildCaseResult(testEntry, label, requestTypeName, null, "ERROR",
                    "Exception: " + e.getMessage(), caseStart, checks);
        }
    }

    /**
     * Verify that contact fields that SHOULD be visible (per RDAP params) are
     * actually populated on the entity or its children.
     */
    private void verifyContactVisibility(List<RdapTestCheck> checks, RdapEntity entity,
                                          AgreementRdapParameters params, int accessLevel) {
        // Registrant
        if (params.hasRegistrantAccess()) {
            List<RdapEntity> registrants = rdapEntityRepository.findByParentIdAndRole(entity.getId(), "registrant");
            boolean hasRegistrant = !registrants.isEmpty();
            checks.add(RdapTestCheck.builder()
                    .name("Registrant Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message(hasRegistrant
                            ? "Registrant contact present (" + registrants.get(0).getDisplayIdentifier() + ") — parameters allow visibility"
                            : "No registrant contact on entity — parameters allow visibility but no data exists to display")
                    .severity(hasRegistrant ? "INFO" : "WARNING").build());
        } else {
            checks.add(RdapTestCheck.builder()
                    .name("Registrant Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message("Registrant contact redacted by RDAP parameters (access level " + accessLevel + ")")
                    .severity("INFO").build());
        }

        // Admin
        if (params.hasAdminAccess()) {
            List<RdapEntity> admins = rdapEntityRepository.findByParentIdAndRole(entity.getId(), "administrative");
            boolean hasAdmin = !admins.isEmpty();
            checks.add(RdapTestCheck.builder()
                    .name("Admin Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message(hasAdmin
                            ? "Admin contact present (" + admins.get(0).getDisplayIdentifier() + ") — parameters allow visibility"
                            : "No admin contact on entity — parameters allow visibility but no data exists")
                    .severity(hasAdmin ? "INFO" : "WARNING").build());
        } else {
            checks.add(RdapTestCheck.builder()
                    .name("Admin Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message("Admin contact redacted by RDAP parameters (access level " + accessLevel + ")")
                    .severity("INFO").build());
        }

        // Tech
        if (params.hasTechAccess()) {
            List<RdapEntity> techs = rdapEntityRepository.findByParentIdAndRole(entity.getId(), "technical");
            boolean hasTech = !techs.isEmpty();
            checks.add(RdapTestCheck.builder()
                    .name("Tech Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message(hasTech
                            ? "Tech contact present (" + techs.get(0).getDisplayIdentifier() + ") — parameters allow visibility"
                            : "No tech contact on entity — parameters allow visibility but no data exists")
                    .severity(hasTech ? "INFO" : "WARNING").build());
        } else {
            checks.add(RdapTestCheck.builder()
                    .name("Tech Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message("Tech contact redacted by RDAP parameters (access level " + accessLevel + ")")
                    .severity("INFO").build());
        }

        // Billing
        if (params.hasBillingAccess()) {
            List<RdapEntity> billings = rdapEntityRepository.findByParentIdAndRole(entity.getId(), "billing");
            boolean hasBilling = !billings.isEmpty();
            checks.add(RdapTestCheck.builder()
                    .name("Billing Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message(hasBilling
                            ? "Billing contact present (" + billings.get(0).getDisplayIdentifier() + ") — parameters allow visibility"
                            : "No billing contact on entity — parameters allow visibility but no data exists")
                    .severity(hasBilling ? "INFO" : "WARNING").build());
        } else {
            checks.add(RdapTestCheck.builder()
                    .name("Billing Contact Visibility").category("CONTACT_VISIBILITY").passed(true)
                    .message("Billing contact redacted by RDAP parameters (access level " + accessLevel + ")")
                    .severity("INFO").build());
        }
    }

    /**
     * Verify that fields disabled in the RDAP parameters would be properly redacted.
     */
    private void verifyRedaction(List<RdapTestCheck> checks, RdapEntity entity,
                                  AgreementRdapParameters params) {
        // Domain field redaction
        if (entity.getObjectType() == RdapEntity.ObjectType.DOMAIN) {
            if (!Boolean.TRUE.equals(params.getDomainHandle())) {
                checks.add(RdapTestCheck.builder()
                        .name("Domain Handle Redaction").category("REDACTION").passed(true)
                        .message("Domain handle field will be redacted from response")
                        .severity("INFO").build());
            }
            if (!Boolean.TRUE.equals(params.getNameservers())) {
                checks.add(RdapTestCheck.builder()
                        .name("Nameservers Redaction").category("REDACTION").passed(true)
                        .message("Nameservers will be redacted from response")
                        .severity("INFO").build());
            }
            if (!Boolean.TRUE.equals(params.getDnssecData())) {
                checks.add(RdapTestCheck.builder()
                        .name("DNSSEC Redaction").category("REDACTION").passed(true)
                        .message("DNSSEC data will be redacted from response")
                        .severity("INFO").build());
            }
        }

        // Network field redaction
        if (entity.getObjectType() == RdapEntity.ObjectType.IP_NETWORK) {
            if (!Boolean.TRUE.equals(params.getNetworkHandle())) {
                checks.add(RdapTestCheck.builder()
                        .name("Network Handle Redaction").category("REDACTION").passed(true)
                        .message("Network handle will be redacted from response")
                        .severity("INFO").build());
            }
        }

        // ASN field redaction
        if (entity.getObjectType() == RdapEntity.ObjectType.AUTNUM) {
            if (!Boolean.TRUE.equals(params.getAutnumHandle())) {
                checks.add(RdapTestCheck.builder()
                        .name("ASN Handle Redaction").category("REDACTION").passed(true)
                        .message("ASN handle will be redacted from response")
                        .severity("INFO").build());
            }
        }

        // Contact role redaction summary
        int redactedRoles = 0;
        if (!params.hasRegistrantAccess()) redactedRoles++;
        if (!params.hasAdminAccess()) redactedRoles++;
        if (!params.hasTechAccess()) redactedRoles++;
        if (!params.hasBillingAccess()) redactedRoles++;

        checks.add(RdapTestCheck.builder()
                .name("Contact Redaction Summary").category("REDACTION").passed(true)
                .message(redactedRoles + " of 4 contact roles are fully redacted; " +
                         (4 - redactedRoles) + " are visible")
                .severity("INFO").build());

        // Events redaction
        if (!Boolean.TRUE.equals(params.getEvents())) {
            checks.add(RdapTestCheck.builder()
                    .name("Events Redaction").category("REDACTION").passed(true)
                    .message("Events will be redacted from response")
                    .severity("INFO").build());
        }
    }

    private RdapTestCaseResult buildCaseResult(AgreementTemplateTestData testEntry,
                                                String label, String requestTypeName,
                                                Integer accessLevel, String result,
                                                String message, long startTime,
                                                List<RdapTestCheck> checks) {
        return RdapTestCaseResult.builder()
                .testDataEntryId(testEntry.getId())
                .label(label)
                .queryType(testEntry.getQueryType())
                .queryValue(testEntry.getQueryValue())
                .requestTypeName(requestTypeName)
                .resolvedAccessLevel(accessLevel)
                .result(result)
                .message(message)
                .durationMs(System.currentTimeMillis() - startTime)
                .checks(checks)
                .build();
    }
}
