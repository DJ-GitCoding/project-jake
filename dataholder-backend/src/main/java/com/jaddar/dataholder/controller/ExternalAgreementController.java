/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import com.jaddar.dataholder.dto.AgreementApiDto.*;
import com.jaddar.dataholder.service.AgreementSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * What a data holder group asks of this data holder.
 *
 * <p>Agreements, subscriptions and their negotiation belong to the group admin: it holds them,
 * and a requestor subscribes there rather than here. All that is left for this side is proving,
 * on request, that the data can actually be retrieved — the group admin names the agreement and
 * sends its definition, and this data holder runs its own RDAP test records against it.
 */
@RestController
@RequestMapping("/api/agreements/external")
@RequiredArgsConstructor
@Slf4j
public class ExternalAgreementController {

    private final AgreementSubscriptionService subscriptionService;

    /**
     * Run this data holder's RDAP retrieval suite for an agreement the group admin describes.
     * Called on each data holder in a group to find one that can prove retrieval.
     */
    @PostMapping("/templates/{templateId}/run-test")
    public ResponseEntity<TestExecutionResponse> runTemplateTest(
            @PathVariable String templateId,
            @RequestBody(required = false) Map<String, Object> agreementDefinition) {
        log.info("External request to run retrieval tests for agreement: {}", templateId);

        try {
            /* Agreements belong to the data holder group that owns them, so the caller sends the
             * agreement's definition and we test our own records against that. */
            TestResultInfo testResult = subscriptionService.runTemplateTest(templateId, agreementDefinition);

            return ResponseEntity.ok(TestExecutionResponse.builder()
                    .success(true)
                    .requestId(templateId)
                    .testResult(testResult)
                    .message("Template test execution completed")
                    .build());
        } catch (Exception e) {
            log.error("Error running retrieval test for agreement {}", templateId, e);
            return ResponseEntity.internalServerError().body(TestExecutionResponse.builder()
                    .success(false)
                    .requestId(templateId)
                    .message("An internal error occurred while running the test. Please try again or contact support.")
                    .build());
        }
    }

    /** Health check for the data holder group admin. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "dataholder-retrieval-test-api",
                "timestamp", System.currentTimeMillis()
        ));
    }
}
