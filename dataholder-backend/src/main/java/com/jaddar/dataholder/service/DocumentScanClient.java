/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client for the document-scanner sidecar, which validates an uploaded attachment against the
 * format criteria authored on a request type's file parameter.
 *
 * <p>Fails <em>open</em> on transport errors: if the scanner is unreachable the upload is allowed
 * through and flagged, rather than blocking a legitimate request on a sidecar outage. Criteria
 * violations reported by the scanner are hard failures.
 */
@Service
@Slf4j
public class DocumentScanClient {

    private final WebClient webClient = WebClient.builder()
            // OCR of a large scan can return a sizeable JSON payload (text sample + image list).
            .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
            .build();
    private final ObjectMapper objectMapper;

    @Value("${dataholder.document-scanner.url:http://document-scanner:9600}")
    private String scannerUrl;

    @Value("${dataholder.document-scanner.secret:}")
    private String scannerSecret;

    @Value("${dataholder.document-scanner.enabled:true}")
    private boolean enabled;

    @Value("${dataholder.document-scanner.timeout-seconds:120}")
    private long timeoutSeconds;

    public DocumentScanClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Outcome of a format scan. {@code skipped} means no verdict was reached (disabled/unreachable). */
    public record ScanVerdict(boolean passed, boolean skipped, List<String> failures, String details) {
        public static ScanVerdict skipped(String reason) {
            return new ScanVerdict(true, true, List.of(), reason);
        }
    }

    /**
     * Validate a file against the given criteria.
     *
     * @param criteria admin-authored criteria; null/empty means nothing to enforce
     */
    @SuppressWarnings("unchecked")
    public ScanVerdict scan(MultipartFile file, Map<String, Object> criteria) {
        if (!enabled) return ScanVerdict.skipped("Document scanning disabled");
        if (criteria == null || criteria.isEmpty()) return ScanVerdict.skipped("No format criteria configured");

        try {
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("file", file.getResource());
            body.part("criteria", objectMapper.writeValueAsString(criteria));

            Map<String, Object> response = webClient.post()
                    .uri(scannerUrl + "/scan")
                    .header("X-Scanner-Secret", scannerSecret)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body.build()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(timeoutSeconds));

            if (response == null) {
                return ScanVerdict.skipped("Scanner returned no response");
            }

            String verdict = String.valueOf(response.get("verdict"));
            List<String> messages = new ArrayList<>();
            Object failures = response.get("failures");
            if (failures instanceof List) {
                for (Object f : (List<?>) failures) {
                    if (f instanceof Map) {
                        Object msg = ((Map<String, Object>) f).get("message");
                        if (msg != null) messages.add(msg.toString());
                    }
                }
            }

            if ("PASS".equals(verdict)) {
                return new ScanVerdict(true, false, List.of(), buildDetails(response));
            }
            if ("FAIL".equals(verdict)) {
                return new ScanVerdict(false, false, messages, buildDetails(response));
            }
            // ERROR from the scanner — treat as unverified rather than a content violation.
            return ScanVerdict.skipped("Scanner error: " + response.getOrDefault("error", "unknown"));

        } catch (Exception e) {
            // Fail open: a sidecar outage must not block legitimate submissions.
            log.error("Document scan failed for {}: {}", file.getOriginalFilename(), e.getMessage());
            return ScanVerdict.skipped("Scanner unavailable: " + e.getMessage());
        }
    }

    private String buildDetails(Map<String, Object> r) {
        return String.format("format-scan type=%s pages=%s words=%s ocr=%s images=%s",
                r.get("fileType"), r.get("pageCount"), r.get("wordCount"), r.get("ocrUsed"),
                r.get("images") instanceof List ? ((List<?>) r.get("images")).size() : 0);
    }
}
