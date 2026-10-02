/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.rdrs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Wire types for the RDRS integration.
 *
 * <p>The {@code command} shape mirrors ICANN's own client exactly — field names and
 * values are what their API expects, including that several vocabularies travel as
 * display strings rather than ids. RDRS publishes no schema, so these are recovered
 * from ICANN's client and can change without notice.
 */
public final class RdrsDto {

    private RdrsDto() {}

    // ─── Session ────────────────────────────────────────────────────

    /** Credentials posted by the requestor to open an RDRS session. */
    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    /** A multi-factor code answering a challenge raised during login. */
    @Data
    public static class MfaRequest {
        private String challengeId;
        private String code;
    }

    /** What the sidecar answers with. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SidecarResponse {
        /** "ok", "mfa_required" or "error". */
        private String status;
        private String error;
        /** On an error: "invalid_credentials", "rate_limited", or absent. */
        private String code;
        private String accessToken;
        private String idToken;
        private Long expiresAt;
        private String appId;
        private String restBackendUrl;
        private String challengeId;
        private String prompt;
    }

    /** Session state handed back to the browser. Never carries the RDRS cookie. */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SessionResponse {
        /** "active", "mfa_required" or "none". */
        private String status;
        private Long expiresAt;
        private String challengeId;
        private String prompt;
        private Map<String, Object> profile;
    }

    // ─── Lookup ─────────────────────────────────────────────────────

    /**
     * Answer from {@code GET /requests/lookup/{domain}} — the only source of
     * lookupRecordId, registrarName and ianaId, none of which the requestor may type.
     * {@code result} gates submission: only SUCCESS may be submitted.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LookupResponse {
        private String result;
        private String registrar;
        private String ianaId;
        private String lookupRecordId;
    }

    // ─── Create ─────────────────────────────────────────────────────

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Address {
        private String address1;
        private String address2;
        private String city;
        private String state;
        private boolean noState;
        private String zip;
        private boolean noZip;
        private String countryCode;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContactInformation {
        private String phone;
        private Address address;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class User {
        private String name;
        private String email;
        private ContactInformation contactInformation;
    }

    /**
     * The JSON blob sent as the {@code command} multipart part.
     *
     * <p>{@code lookupRecordId}, {@code registrarName} and {@code ianaId} are filled in
     * server-side from the lookup result and are deliberately ignored if a client sends
     * them.
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Command {
        private User user;
        private String organization;
        private String category;
        private String categoryOtherDescription;
        private Boolean requestedConfidentiality;
        private String domainSubject;
        private String requestPriority;
        private String expeditedPriorityReason;
        private List<String> requestedDataElements;

        // Server-filled from the lookup — never trusted from the client.
        private String lookupRecordId;
        private String registrarName;
        private String ianaId;

        private List<String> processedCountries;
        private String issueDescription;
        private Boolean lawEnforcementRequestForData;
        private String lawEnforcementRequestDeadLine;
        private Boolean assertingLegalBasis;
        private String legalBasisReason;
        private String legalBasisOtherInformation;
        private String partyRepresentation;
        private Boolean agreeDataIsCorrect;
        private Boolean agreeDataProcessTransferCompliance;
    }

    /** Outcome of a submission, as reported back to the browser. */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SubmitResponse {
        private boolean success;
        private String message;
        private String registrarName;
        private String ianaId;
    }
}
