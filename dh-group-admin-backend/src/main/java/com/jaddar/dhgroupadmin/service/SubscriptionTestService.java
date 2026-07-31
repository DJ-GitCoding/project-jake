/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.service;

import com.jaddar.dhgroupadmin.config.MtlsEndpoints;
import com.jaddar.dhgroupadmin.entity.AgreementRequestType;
import com.jaddar.dhgroupadmin.entity.AgreementSubscription;
import com.jaddar.dhgroupadmin.entity.AgreementSubscription.SubscriptionStatus;
import com.jaddar.dhgroupadmin.entity.DataHolder;
import com.jaddar.dhgroupadmin.repository.AgreementSubscriptionRepository;
import com.jaddar.dhgroupadmin.repository.DataHolderRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes the subscription test that gates activation.
 *
 * <p>The test answers one question: once this subscription goes active, will the requestor
 * actually be able to retrieve data through it? That is checked in two phases —
 *
 * <ol>
 *   <li><b>Resolution</b> — the subscription carries everything the data holder needs to
 *       authorize and answer a query from this requestor (group code, published template with
 *       usable request types, access levels, routing), and the lookup payload the data holder
 *       reads at query time is complete.</li>
 *   <li><b>Retrieval</b> — the data holder is asked to run its own test suite for this
 *       subscription, which performs real RDAP lookups against the template's test data. The
 *       group admin holds no RDAP data itself, so this is the only phase that proves retrieval
 *       end to end; its verdict is authoritative.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionTestService {

    private static final String RESULT_PASSED = "PASSED";
    private static final String RESULT_FAILED = "FAILED";

    /*
     * Requestor-facing failure text. These reach an external party's screen, so they say what went
     * wrong and what to do next without naming hosts, identifiers or exception detail. The technical
     * cause travels alongside as internalDetail and is only logged or audited.
     */
    private static final String MSG_DH_SETUP_INCOMPLETE =
            "This subscription is not fully set up on the data holder's side yet. "
            + "Please contact the data holder to complete the setup.";
    private static final String MSG_DH_NOT_READY =
            "The data holder is not ready to test this subscription yet. "
            + "This is usually temporary — please try again shortly.";
    private static final String MSG_DH_UNREACHABLE =
            "The data holder could not be reached to verify data retrieval. "
            + "This is usually temporary — please try again shortly.";
    private static final String MSG_DH_CANNOT_TEST =
            "The data holder could not run the retrieval test for this subscription right now. "
            + "Please try again, and contact the data holder if the problem continues.";
    private static final String MSG_DH_RETRIEVAL_FAILED =
            "The data holder ran its retrieval tests and one or more did not pass. "
            + "Please contact the data holder, who can review the full diagnostic for this request.";
    private static final String MSG_DH_CHECK_FAILED =
            "This check did not pass at the data holder.";
    private static final String MSG_NO_DATA_HOLDERS =
            "No active data holder is available in this data holder group to answer your queries. "
            + "Please contact the data holder group administrator.";
    private static final String MSG_NO_TEST_DATA =
            "Data retrieval could not be verified because no data holder in this group has test data "
            + "set up for this agreement. Please ask the data holder group administrator to configure "
            + "test data so the subscription can be verified.";

    private final AgreementSubscriptionRepository subscriptionRepository;
    private final DataHolderRepository dataHolderRepository;
    private final ResponseMapper mapper;
    private final WebClient.Builder webClientBuilder;
    private final MtlsEndpoints mtlsEndpoints;

    @Value("${subscription.test.data-holder-timeout-seconds:15}")
    private long dataHolderTimeoutSeconds;

    /**
     * A single check. {@code errorMessage} is shown to the requestor, so it must stay free of
     * internal identifiers, hostnames and exception text; {@code internalDetail} carries the
     * technical cause and is only ever logged or audited, never serialized to the API.
     */
    @Getter
    @Builder
    public static class TestCheck {
        private final String name;
        private final String description;
        private final boolean passed;
        private final String errorMessage;
        private final String internalDetail;
    }

    /** {@code details} is requestor-facing; {@code internalDetails} is for logs and audit only. */
    @Getter
    @Builder
    public static class TestReport {
        private final String result;
        private final LocalDateTime testedAt;
        private final String details;
        private final String internalDetails;
        private final List<TestCheck> checks;
        private final List<Map<String, Object>> rdapTestResults;
        private final Map<String, Object> summary;
    }

    /**
     * Run the full test for a subscription and persist its verdict.
     *
     * @throws IllegalArgumentException when no subscription matches the request ID
     * @throws IllegalStateException    when the subscription is not in TESTING
     */
    @Transactional
    public TestReport runTest(String requestId) {
        AgreementSubscription sub = subscriptionRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + requestId));

        if (sub.getStatus() != SubscriptionStatus.TESTING) {
            throw new IllegalStateException(
                    "Subscription must be in TESTING status to run tests. Current status: " + sub.getStatus());
        }

        long startedAt = System.currentTimeMillis();
        sub.setTestStartedAt(LocalDateTime.now());

        List<TestCheck> checks = new ArrayList<>();
        checks.add(checkRequestorIdentity(sub));
        checks.add(checkRequestorContact(sub));
        checks.add(checkTemplate(sub));
        checks.add(checkRequestTypes(sub));
        checks.add(checkAccessLevels(sub));
        checks.add(checkDataHolderGroup(sub));
        checks.add(checkEffectiveWindow(sub));
        checks.add(checkSubscriptionRetrieval(sub));

        DataHolderTestOutcome dhOutcome = runDataHolderRetrievalTest(sub);
        checks.add(dhOutcome.check());
        checks.addAll(dhOutcome.dataHolderChecks());

        long passed = checks.stream().filter(TestCheck::isPassed).count();
        boolean allPassed = passed == checks.size();
        String result = allPassed ? RESULT_PASSED : RESULT_FAILED;
        long durationMs = System.currentTimeMillis() - startedAt;

        String details = buildDetails(checks, passed, dhOutcome);
        String internalDetails = internalDiagnostic(checks);

        sub.setTestResult(result);
        sub.setTestCompletedAt(LocalDateTime.now());
        sub.setTestDetails(details);
        subscriptionRepository.save(sub);

        log.info("Subscription test completed for {}: {} — {}", requestId, result, details);
        if (!allPassed) {
            // Technical causes stay here; the requestor only ever sees the sanitized messages.
            log.warn("Subscription test failures for {}: {}", requestId, internalDetails);
        }

        return TestReport.builder()
                .result(result)
                .testedAt(sub.getTestCompletedAt())
                .details(details)
                .internalDetails(internalDetails)
                .checks(checks)
                .rdapTestResults(dhOutcome.rdapTestResults())
                .summary(buildSummary(checks, (int) passed, dhOutcome, durationMs))
                .build();
    }

    // ==================== Phase 1: Resolution ====================

    private TestCheck checkRequestorIdentity(AgreementSubscription sub) {
        List<String> missing = new ArrayList<>();
        if (isBlank(sub.getRequestorGroupId())) missing.add("group ID");
        if (isBlank(sub.getRequestorGroupName())) missing.add("group name");
        // The data holder resolves a query to this subscription by group code.
        if (isBlank(sub.getRequestorGroupCode())) missing.add("group code");

        return TestCheck.builder()
                .name("Requestor Group Identity")
                .description("Verify the requestor group carries the identifiers the data holder authorizes against")
                .passed(missing.isEmpty())
                .errorMessage(missing.isEmpty() ? null
                        : "Your requestor group registration is incomplete, so the data holder cannot identify "
                          + "your queries. Please contact your requestor manager administrator to complete it.")
                .internalDetail(missing.isEmpty() ? null : "Missing requestor " + String.join(", ", missing))
                .build();
    }

    private TestCheck checkRequestorContact(AgreementSubscription sub) {
        List<String> missing = new ArrayList<>();
        if (isBlank(sub.getRequestorFirstName()) || isBlank(sub.getRequestorLastName())) missing.add("name");
        if (isBlank(sub.getRequestorContactEmail())) missing.add("contact email");

        return TestCheck.builder()
                .name("Requestor Contact Details")
                .description("Verify the requestor contact details recorded on the subscription are complete")
                .passed(missing.isEmpty())
                .errorMessage(missing.isEmpty() ? null
                        : "The contact details on this subscription are incomplete. Please add your name and "
                          + "contact email to your requestor group profile and submit the subscription again.")
                .internalDetail(missing.isEmpty() ? null : "Missing requestor " + String.join(", ", missing))
                .build();
    }

    private TestCheck checkTemplate(AgreementSubscription sub) {
        boolean present = sub.getTemplate() != null;
        boolean published = present && Boolean.TRUE.equals(sub.getTemplate().getIsPublished());

        return TestCheck.builder()
                .name("Agreement Template")
                .description("Verify the subscribed template still exists and is published")
                .passed(published)
                .errorMessage(published ? null
                        : "The agreement behind this subscription is no longer available. Please contact the "
                          + "data holder group administrator, or subscribe to a currently published agreement.")
                .internalDetail(!present ? "Subscription has no agreement template"
                        : (!published ? "Template '" + sub.getTemplate().getName() + "' is not published" : null))
                .build();
    }

    private TestCheck checkRequestTypes(AgreementSubscription sub) {
        List<AgreementRequestType> active = activeRequestTypes(sub);

        return TestCheck.builder()
                .name("Request Types Available")
                .description("Verify the template exposes at least one active request type the requestor can query with")
                .passed(!active.isEmpty())
                .errorMessage(active.isEmpty()
                        ? "This agreement has no query types enabled, so no requests could be made under it. "
                          + "Please contact the data holder group administrator."
                        : null)
                .internalDetail(active.isEmpty() ? "Template has no active request types" : null)
                .build();
    }

    private TestCheck checkAccessLevels(AgreementSubscription sub) {
        List<AgreementRequestType> active = activeRequestTypes(sub);
        List<String> invalid = new ArrayList<>();
        for (AgreementRequestType rt : active) {
            Integer level = rt.getAccessLevel();
            if (level == null || level < 0 || level > 3) {
                invalid.add(rt.getName() + "=" + level);
            }
        }

        Integer effective = sub.getEffectiveAccessLevel();
        boolean effectiveValid = effective != null && effective >= 0 && effective <= 3;

        String error = null;
        if (!invalid.isEmpty()) {
            error = "Invalid access level on request type(s): " + String.join(", ", invalid);
        } else if (!effectiveValid) {
            error = "Subscription resolves to an unusable effective access level: " + effective;
        }

        return TestCheck.builder()
                .name("Access Levels")
                .description("Verify request type and effective access levels are within the permitted range (0-3)")
                .passed(error == null)
                .errorMessage(error == null ? null
                        : "The access levels on this agreement are not configured correctly, so the data "
                          + "holder could not decide what to return. Please contact the data holder group administrator.")
                .internalDetail(error)
                .build();
    }

    /**
     * A subscription is to a data holder group, not to one data holder, so this only requires the
     * group to exist and still have an active member able to answer the requestor.
     */
    private TestCheck checkDataHolderGroup(AgreementSubscription sub) {
        Long groupId = resolveDataHolderGroupId(sub);
        int activeMembers = groupId == null ? 0 : candidateDataHolders(sub).size();

        String internal = groupId == null
                ? "Neither the subscribed template nor the subscription resolves to a data holder group"
                : (activeMembers == 0 ? "Data holder group " + groupId + " has no active members" : null);

        return TestCheck.builder()
                .name("Data Holder Group")
                .description("Verify the subscription's data holder group has an active data holder to answer queries")
                .passed(internal == null)
                .errorMessage(internal == null ? null : MSG_NO_DATA_HOLDERS)
                .internalDetail(internal)
                .build();
    }

    private TestCheck checkEffectiveWindow(AgreementSubscription sub) {
        LocalDateTime end = sub.getEffectiveTo();
        boolean usable = end == null || end.isAfter(LocalDateTime.now());

        return TestCheck.builder()
                .name("Effective Window")
                .description("Verify the subscription would not be expired the moment it is activated")
                .passed(usable)
                .errorMessage(usable ? null
                        : "This subscription's validity period has already ended, so it cannot be activated. "
                          + "Please contact the data holder group administrator to renew it.")
                .internalDetail(usable ? null : "Subscription effectiveTo already passed: " + end)
                .build();
    }

    /**
     * Read the subscription back through the same mapper the data holder consumes at query time and
     * confirm the payload carries everything needed to serve the requestor.
     */
    private TestCheck checkSubscriptionRetrieval(AgreementSubscription sub) {
        List<String> missing = new ArrayList<>();
        try {
            Map<String, Object> payload = mapper.toSubscriptionResponse(sub);

            if (isBlank(asString(payload.get("requestId")))) missing.add("requestId");
            if (isBlank(asString(payload.get("requestorGroupCode")))) missing.add("requestorGroupCode");
            if (isBlank(asString(payload.get("templateId")))) missing.add("templateId");
            if (payload.get("effectiveAccessLevel") == null) missing.add("effectiveAccessLevel");

            Object requestTypes = payload.get("requestTypes");
            if (!(requestTypes instanceof List<?> list) || list.isEmpty()) {
                missing.add("requestTypes");
            }
        } catch (Exception e) {
            return TestCheck.builder()
                    .name("Subscription Retrieval")
                    .description("Verify the data holder can read this subscription's details for the requestor")
                    .passed(false)
                    .errorMessage("This subscription's details could not be read back. "
                                  + "Please contact the data holder, who can review the diagnostic for this request.")
                    .internalDetail("Response mapping threw: " + e)
                    .build();
        }

        return TestCheck.builder()
                .name("Subscription Retrieval")
                .description("Verify the data holder can read this subscription's details for the requestor")
                .passed(missing.isEmpty())
                .errorMessage(missing.isEmpty() ? null
                        : "This subscription is missing information the data holder needs to answer your queries. "
                          + "Please contact the data holder to complete the setup.")
                .internalDetail(missing.isEmpty() ? null
                        : "Mapped subscription payload missing: " + String.join(", ", missing))
                .build();
    }

    // ==================== Phase 2: Retrieval ====================

    private record DataHolderTestOutcome(TestCheck check,
                                          List<TestCheck> dataHolderChecks,
                                          List<Map<String, Object>> rdapTestResults,
                                          Map<String, Object> summary) {
    }

    /**
     * Find a data holder in the group that holds test data for this template and use it to verify
     * retrieval. Subscriptions are not pinned to one data holder, so every active member of the
     * group is a candidate; a member with no test data cannot prove anything, so the search moves
     * on. Any member that does hold test data is authoritative for the verdict.
     */
    @SuppressWarnings("unchecked")
    private DataHolderTestOutcome runDataHolderRetrievalTest(AgreementSubscription sub) {
        String name = "Data Holder Retrieval Test";
        String description = "Verify a data holder in the group can serve RDAP data for this subscription";

        String templateId = sub.getTemplate() != null ? sub.getTemplate().getTemplateId() : null;
        if (isBlank(templateId)) {
            return failedOutcome(name, description, MSG_DH_SETUP_INCOMPLETE,
                    "Subscription has no template, so no template-scoped test could be run");
        }

        Long groupId = resolveDataHolderGroupId(sub);
        List<DataHolder> candidates = candidateDataHolders(sub);
        if (candidates.isEmpty()) {
            return failedOutcome(name, description, MSG_NO_DATA_HOLDERS,
                    "No active data holders found for data holder group " + groupId
                            + " (resolved from template " + templateId + ")");
        }

        List<String> attemptLog = new ArrayList<>();
        // Distinguishes "nobody answered" from "everybody answered but none holds test data".
        int answered = 0;

        for (DataHolder candidate : candidates) {
            if (isBlank(candidate.getUrl())) {
                attemptLog.add(candidate.getDataholderId() + ": no URL configured");
                continue;
            }

            String url = mtlsEndpoints.resolve(candidate.getUrl())
                    + "/api/agreements/external/templates/" + templateId + "/run-test";

            Map<String, Object> response;
            try {
                response = webClientBuilder.build()
                        .post()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .block(Duration.ofSeconds(dataHolderTimeoutSeconds));
            } catch (WebClientResponseException e) {
                attemptLog.add(candidate.getDataholderId() + ": HTTP " + e.getStatusCode().value()
                        + " from " + url + " — " + e.getResponseBodyAsString());
                continue;
            } catch (Exception e) {
                attemptLog.add(candidate.getDataholderId() + ": call to " + url + " failed — " + e);
                continue;
            }

            if (response == null || !(response.get("testResult") instanceof Map)) {
                attemptLog.add(candidate.getDataholderId() + ": no testResult in response from " + url);
                continue;
            }

            answered++;
            Map<String, Object> candidateResult = (Map<String, Object>) response.get("testResult");
            Map<String, Object> candidateSummary = candidateResult.get("summary") instanceof Map
                    ? (Map<String, Object>) candidateResult.get("summary")
                    : Map.of();

            // No test data here — this data holder cannot prove retrieval, so try the next.
            if (intFrom(candidateSummary.get("totalRdapTests")) == 0) {
                attemptLog.add(candidate.getDataholderId() + ": no active test data for template " + templateId);
                continue;
            }

            return buildOutcomeFrom(name, description, candidate, candidateResult, candidateSummary);
        }

        return failedOutcome(name, description,
                answered == 0 ? MSG_DH_UNREACHABLE : MSG_NO_TEST_DATA,
                "No data holder in group " + groupId
                        + " could verify retrieval for template " + templateId
                        + " (" + answered + " of " + candidates.size() + " responded)"
                        + ". Attempts: " + String.join("; ", attemptLog));
    }

    /**
     * The group to test against is the one that owns the subscribed template. A subscription is
     * never pinned to a single data holder, so the template's group is the authoritative source.
     */
    private Long resolveDataHolderGroupId(AgreementSubscription sub) {
        if (sub.getTemplate() != null && sub.getTemplate().getDataHolderGroupId() != null) {
            return sub.getTemplate().getDataHolderGroupId();
        }
        return sub.getDataHolderGroupId();
    }

    /** Every active data holder in the template's group is a candidate for running the test. */
    private List<DataHolder> candidateDataHolders(AgreementSubscription sub) {
        Long groupId = resolveDataHolderGroupId(sub);
        return groupId == null ? List.of() : dataHolderRepository.findActiveByGroupMembership(groupId);
    }

    @SuppressWarnings("unchecked")
    private DataHolderTestOutcome buildOutcomeFrom(String name, String description, DataHolder candidate,
                                                    Map<String, Object> testResult,
                                                    Map<String, Object> dhSummary) {
        String dhResult = asString(testResult.get("result"));
        boolean dhPassed = RESULT_PASSED.equalsIgnoreCase(dhResult);
        String dhDetails = asString(testResult.get("details"));

        /*
         * The data holder's own wording can name internal entities and IDs, so only its structure
         * (which check, pass/fail) is forwarded — the free text stays in the internal detail.
         */
        List<TestCheck> dhChecks = new ArrayList<>();
        if (testResult.get("testCases") instanceof List<?> cases) {
            for (Object caseObj : cases) {
                if (caseObj instanceof Map<?, ?> tc) {
                    boolean casePassed = Boolean.TRUE.equals(tc.get("passed"));
                    dhChecks.add(TestCheck.builder()
                            .name("Data Holder — " + asString(tc.get("name")))
                            .description(asString(tc.get("description")))
                            .passed(casePassed)
                            .errorMessage(casePassed ? null : MSG_DH_CHECK_FAILED)
                            .internalDetail(casePassed ? null : asString(tc.get("errorMessage")))
                            .build());
                }
            }
        }

        List<Map<String, Object>> rdapResults = new ArrayList<>();
        if (testResult.get("rdapTestResults") instanceof List<?> rdapList) {
            for (Object entry : rdapList) {
                if (entry instanceof Map<?, ?> m) {
                    rdapResults.add(sanitizeRdapResult((Map<String, Object>) m));
                }
            }
        }

        TestCheck check = TestCheck.builder()
                .name(name)
                .description(description)
                .passed(dhPassed)
                .errorMessage(dhPassed ? null : MSG_DH_RETRIEVAL_FAILED)
                .internalDetail(dhPassed ? null
                        : "Data holder " + candidate.getDataholderId() + " reported "
                          + (isBlank(dhResult) ? "no result" : dhResult)
                          + (isBlank(dhDetails) ? "" : ": " + dhDetails))
                .build();

        return new DataHolderTestOutcome(check, dhChecks, rdapResults, dhSummary);
    }

    private DataHolderTestOutcome failedOutcome(String name, String description,
                                                 String userMessage, String internalDetail) {
        return new DataHolderTestOutcome(
                TestCheck.builder().name(name).description(description).passed(false)
                        .errorMessage(userMessage).internalDetail(internalDetail).build(),
                List.of(), List.of(), Map.of());
    }

    /**
     * Keep the shape of a data holder RDAP test case — which checks ran and how they scored — while
     * dropping the free-text messages, which can name entities and internal IDs.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeRdapResult(Map<String, Object> raw) {
        Map<String, Object> safe = new LinkedHashMap<>();
        for (String key : List.of("label", "requestTypeName", "queryType", "result", "durationMs")) {
            if (raw.containsKey(key)) safe.put(key, raw.get(key));
        }

        if (raw.get("checks") instanceof List<?> checks) {
            List<Map<String, Object>> safeChecks = new ArrayList<>();
            for (Object entry : checks) {
                if (entry instanceof Map<?, ?> c) {
                    Map<String, Object> safeCheck = new LinkedHashMap<>();
                    safeCheck.put("name", c.get("name"));
                    safeCheck.put("category", c.get("category"));
                    safeCheck.put("passed", c.get("passed"));
                    safeCheck.put("severity", c.get("severity"));
                    safeChecks.add(safeCheck);
                }
            }
            safe.put("checks", safeChecks);
        }
        return safe;
    }

    // ==================== Reporting ====================

    /**
     * The one-line summary shown in the requestor's result banner. Names which checks failed so the
     * reader knows where to look, and leaves the "what to do" wording to the individual checks.
     */
    private String buildDetails(List<TestCheck> checks, long passed, DataHolderTestOutcome dhOutcome) {
        int total = checks.size();
        int rdapRun = intFrom(dhOutcome.summary().get("totalRdapTests"));

        if (passed == total) {
            return rdapRun > 0
                    ? String.format("All %d checks passed, including %d data retrieval test%s at the data holder.",
                            total, rdapRun, rdapRun == 1 ? "" : "s")
                    : String.format("All %d checks passed.", total);
        }

        List<String> failed = checks.stream()
                .filter(c -> !c.isPassed())
                .map(TestCheck::getName)
                .toList();

        return String.format("%d of %d checks did not pass: %s. See the checks below for what to do next.",
                failed.size(), total, String.join(", ", failed));
    }

    private Map<String, Object> buildSummary(List<TestCheck> checks, int passed,
                                              DataHolderTestOutcome dhOutcome, long durationMs) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalValidationTests", checks.size());
        summary.put("passedValidationTests", passed);
        summary.put("failedValidationTests", checks.size() - passed);
        summary.put("totalRdapTests", intFrom(dhOutcome.summary().get("totalRdapTests")));
        summary.put("passedRdapTests", intFrom(dhOutcome.summary().get("passedRdapTests")));
        summary.put("failedRdapTests", intFrom(dhOutcome.summary().get("failedRdapTests")));
        summary.put("skippedRdapTests", intFrom(dhOutcome.summary().get("skippedRdapTests")));
        summary.put("errorRdapTests", intFrom(dhOutcome.summary().get("errorRdapTests")));
        summary.put("totalDurationMs", durationMs);
        return summary;
    }

    // ==================== Helpers ====================

    private List<AgreementRequestType> activeRequestTypes(AgreementSubscription sub) {
        return sub.getTemplate() == null ? List.of() : sub.getTemplate().getActiveRequestTypes();
    }

    /** Operator-facing summary of why checks failed — for logs and audit only. */
    private String internalDiagnostic(List<TestCheck> checks) {
        return checks.stream()
                .filter(c -> !c.isPassed())
                .map(c -> c.getName() + ": "
                        + (isBlank(c.getInternalDetail()) ? c.getErrorMessage() : c.getInternalDetail()))
                .collect(java.util.stream.Collectors.joining("; "));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static int intFrom(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
