/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.entity.RdapEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

/**
 * Sends asynchronous notifications to registrants when their registration data
 * is queried via RDAP.
 *
 * Notification rules:
 * - Only triggered for request types that support the confidential flag
 * - If confidential=true, notification is SUPPRESSED (requestor asked for confidential disclosure)
 * - If confidential=false, the registrant is notified that an inquiry was made
 * - Notification is fire-and-forget (async) — never delays the RDAP response
 *
 * The notification is sent via HTTP POST (form-encoded) to a configurable PHP mail endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrantNotificationService {

    private final RestTemplate restTemplate;

    @Value("${dataholder.notification.mail-endpoint:}")
    private String mailEndpoint;

    @Value("${dataholder.notification.enabled:true}")
    private boolean notificationsEnabled;

    @Value("${dataholder.notification.from-email:noreply@dataholder.local}")
    private String fromEmail;

    @Value("${dataholder.notification.from-name:RDAP Data Holder}")
    private String fromName;

    /**
     * Send a registrant notification if conditions are met.
     * 
     * NOTE: registrantEmail must be resolved by the caller while still within the
     * Hibernate session (i.e. on the request thread), because lazy-loaded
     * collections on the entity will not be accessible from this async thread.
     *
     * @param registrantEmail      pre-resolved registrant email (may be null)
     * @param queryType            "domain", "ip", or "asn"
     * @param queryValue           the queried value (e.g. "example.com")
     * @param confidential         whether the requestor flagged this as confidential
     * @param requestTypeSupportsConfidential whether the resolved request type supports the confidential flag
     */
    @Async
    public void notifyRegistrantIfRequired(
            String registrantEmail,
            String queryType,
            String queryValue,
            boolean confidential,
            boolean requestTypeSupportsConfidential) {

        if (!requestTypeSupportsConfidential) {
            log.debug("Request type does not support confidential flag — skipping registrant notification for {} {}",
                    queryType, queryValue);
            return;
        }

        if (confidential) {
            log.info("Confidential disclosure requested — suppressing registrant notification for {} {}",
                    queryType, queryValue);
            return;
        }

        if (!notificationsEnabled) {
            log.debug("Registrant notifications disabled — skipping for {} {}", queryType, queryValue);
            return;
        }

        if (mailEndpoint == null || mailEndpoint.isBlank()) {
            log.warn("No mail endpoint configured (dataholder.notification.mail-endpoint) — cannot notify registrant for {} {}",
                    queryType, queryValue);
            return;
        }

        if (registrantEmail == null || registrantEmail.isBlank()) {
            log.info("No registrant email found for {} {} — skipping notification", queryType, queryValue);
            return;
        }

        try {
            sendNotification(registrantEmail, queryType, queryValue);
            log.info("Registrant notification sent to {} for {} {}", registrantEmail, queryType, queryValue);
        } catch (Exception e) {
            log.error("Failed to send registrant notification for {} {}: {}", queryType, queryValue, e.getMessage());
        }
    }

    /**
     * Find the registrant's email address from the entity or its child entities.
     * Looks for a child entity with the "registrant" role, falls back to the parent entity's email.
     */
    public String findRegistrantEmail(RdapEntity entity) {
        // Check child entities for one with "registrant" role
        if (entity.getChildren() != null) {
            for (RdapEntity child : entity.getChildren()) {
                if (child.getRoles() != null && child.getRoles().contains("registrant")) {
                    if (child.getEmail() != null && !child.getEmail().isBlank()) {
                        return child.getEmail();
                    }
                }
            }
        }

        // Fallback: use the parent entity's email if present
        if (entity.getEmail() != null && !entity.getEmail().isBlank()) {
            return entity.getEmail();
        }

        return null;
    }

    /**
     * POST the notification to the PHP mail endpoint using form-encoded data.
     * Form-encoded avoids mod_security blocks that commonly reject application/json.
     */
    private void sendNotification(String recipientEmail, String queryType, String queryValue) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("to", recipientEmail);
        formData.add("from_email", fromEmail);
        formData.add("from_name", fromName);
        formData.add("subject", "RDAP Inquiry Notification — " + formatObjectLabel(queryType) + ": " + queryValue);
        formData.add("body", buildEmailBody(queryType, queryValue));
        formData.add("query_type", queryType);
        formData.add("query_value", queryValue);
        formData.add("timestamp", LocalDateTime.now().toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                mailEndpoint,
                HttpMethod.POST,
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("Mail endpoint returned {}: {}", response.getStatusCode(), response.getBody());
        } else {
            log.info("Mail endpoint returned {}: {}", response.getStatusCode(), response.getBody());
        }
    }

    private String buildEmailBody(String queryType, String queryValue) {
        String objectLabel = formatObjectLabel(queryType);
        return "Dear Registrant,\n\n" +
                "This is to inform you that an inquiry was made regarding your " +
                objectLabel + ": " + queryValue + ".\n\n" +
                "This notification is provided as a courtesy to keep you informed " +
                "of access to your registration data.\n\n" +
                "If you have questions about this inquiry, please contact " +
                "your registrar or the data holder.\n\n" +
                "Regards,\n" +
                fromName;
    }

    private String formatObjectLabel(String queryType) {
        return switch (queryType.toLowerCase()) {
            case "domain" -> "domain name";
            case "ip" -> "IP network";
            case "asn" -> "AS number";
            default -> "registration object";
        };
    }
}