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

/**
 * Model for creating a new RDAP request tracking entry.
 * Ported from backend/models/rdap_request.py :: RdapRequestCreate.
 */
@Data
public class RdapRequestCreate {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("query_type")
    private String queryType;

    @JsonProperty("query_value")
    private String queryValue;

    @JsonProperty("agreements_used")
    private List<String> agreementsUsed;

    @JsonProperty("access_level_requested")
    private Integer accessLevelRequested;

    @JsonProperty("data_holder_id")
    private String dataHolderId;

    @JsonProperty("data_holder_name")
    private String dataHolderName;

    @JsonProperty("expires_at")
    private Instant expiresAt;

    @JsonProperty("jake_compliance")
    private boolean jakeCompliance = false;

    private boolean confidential = false;

    private boolean exigent = false;
}
