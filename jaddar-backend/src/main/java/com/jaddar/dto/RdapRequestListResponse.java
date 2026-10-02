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

import java.util.List;

/**
 * Response model for a list of RDAP requests.
 * Ported from backend/models/rdap_request.py :: RdapRequestListResponse.
 */
@Data
public class RdapRequestListResponse {

    private List<RdapRequestResponse> requests;

    private int total;

    @JsonProperty("pending_count")
    private int pendingCount;

    @JsonProperty("approved_count")
    private int approvedCount;

    @JsonProperty("denied_count")
    private int deniedCount;

    @JsonProperty("error_count")
    private int errorCount;

    @JsonProperty("cancelled_count")
    private int cancelledCount = 0;
}
