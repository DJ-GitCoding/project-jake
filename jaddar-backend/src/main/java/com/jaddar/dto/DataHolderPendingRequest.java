/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Model for pending request data from the data holder.
 * Ported from backend/models/rdap_request.py :: DataHolderPendingRequest.
 * Wire names (aliases) preserved exactly.
 */
@Data
public class DataHolderPendingRequest {

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("queryType")
    private String queryType;

    @JsonProperty("queryValue")
    private String queryValue;

    @JsonProperty("status")
    private String status;

    @JsonProperty("requestedAccessLevel")
    private Integer requestedAccessLevel;

    @JsonProperty("requestorSub")
    private String requestorSub;

    @JsonProperty("requestorEmail")
    private String requestorEmail;

    @JsonProperty("requestorUsername")
    private String requestorUsername;

    @JsonProperty("agreementNames")
    private List<String> agreementNames;

    @JsonProperty("adminNotes")
    private String adminNotes;

    @JsonProperty("denialReason")
    private String denialReason;

    @JsonProperty("jakeCompliance")
    private boolean jakeCompliance = false;

    @JsonProperty("confidential")
    private boolean confidential = false;

    @JsonProperty("exigent")
    private boolean exigent = false;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("expiresAt")
    private Instant expiresAt;

    @JsonProperty("reviewedAt")
    private Instant reviewedAt;

    @JsonProperty("responseData")
    private Map<String, Object> responseData;
}
