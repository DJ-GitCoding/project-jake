/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jaddar.enums.RequestStatus;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Model for updating an RDAP request.
 * Ported from backend/models/rdap_request.py :: RdapRequestUpdate.
 */
@Data
public class RdapRequestUpdate {

    private RequestStatus status;

    @JsonProperty("access_level_granted")
    private Integer accessLevelGranted;

    @JsonProperty("rdap_data")
    private Map<String, Object> rdapData;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("resolved_at")
    private Instant resolvedAt;
}
