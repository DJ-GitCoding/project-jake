/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jaddar.entity.RdapRequestHistory;
import com.jaddar.enums.RequestStatus;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response model for an RDAP request.
 * Ported from backend/models/rdap_request.py :: RdapRequestResponse.
 *
 * NOTE: jakeCompliance/confidential/exigent are NOT columns on the entity (per the
 * migration contract) — they are DTO-only fields echoed from the request flow and
 * default to false.
 */
@Data
public class RdapRequestResponse {

    private Long id;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("user_sub")
    private String userSub;

    @JsonProperty("user_email")
    private String userEmail;

    @JsonProperty("query_type")
    private String queryType;

    @JsonProperty("query_value")
    private String queryValue;

    private RequestStatus status;

    @JsonProperty("agreements_used")
    private List<String> agreementsUsed;

    @JsonProperty("access_level_requested")
    private Integer accessLevelRequested;

    @JsonProperty("access_level_granted")
    private Integer accessLevelGranted;

    @JsonProperty("rdap_data")
    private Map<String, Object> rdapData;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("data_holder_id")
    private String dataHolderId;

    @JsonProperty("data_holder_name")
    private String dataHolderName;

    @JsonProperty("jake_compliance")
    private boolean jakeCompliance = false;

    private boolean confidential = false;

    private boolean exigent = false;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    @JsonProperty("resolved_at")
    private Instant resolvedAt;

    @JsonProperty("expires_at")
    private Instant expiresAt;

    /** Build a response DTO from the persisted entity (mirrors pydantic from_attributes). */
    public static RdapRequestResponse fromEntity(RdapRequestHistory e) {
        RdapRequestResponse r = new RdapRequestResponse();
        r.setId(e.getId());
        r.setRequestId(e.getRequestId());
        r.setUserSub(e.getUserSub());
        r.setUserEmail(e.getUserEmail());
        r.setQueryType(e.getQueryType());
        r.setQueryValue(e.getQueryValue());
        r.setStatus(e.getStatus());
        r.setAgreementsUsed(e.getAgreementsUsed());
        r.setAccessLevelRequested(e.getAccessLevelRequested());
        r.setAccessLevelGranted(e.getAccessLevelGranted());
        r.setRdapData(e.getRdapData());
        r.setErrorMessage(e.getErrorMessage());
        r.setDataHolderId(e.getDataHolderId());
        r.setDataHolderName(e.getDataHolderName());
        r.setCreatedAt(e.getCreatedAt());
        r.setUpdatedAt(e.getUpdatedAt());
        r.setResolvedAt(e.getResolvedAt());
        r.setExpiresAt(e.getExpiresAt());
        return r;
    }
}
