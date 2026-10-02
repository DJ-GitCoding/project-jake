/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Request model for updating user RDAP settings.
 * Ported from backend/models/rdap_request.py :: UserRdapSettingsRequest.
 */
@Data
public class UserRdapSettingsRequest {

    @Min(0)
    @Max(3600000) // Max 1 hour
    @JsonProperty("default_poll_interval_ms")
    private Integer defaultPollIntervalMs;

    @JsonProperty("auto_poll_enabled")
    private Boolean autoPollEnabled;

    @JsonProperty("notify_on_approval")
    private Boolean notifyOnApproval;

    @JsonProperty("notify_on_denial")
    private Boolean notifyOnDenial;
}
