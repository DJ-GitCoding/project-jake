/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jaddar.entity.UserRdapSettings;
import lombok.Data;

import java.time.Instant;

/**
 * Response model for user RDAP settings.
 * Ported from backend/models/rdap_request.py :: UserRdapSettingsResponse.
 */
@Data
public class UserRdapSettingsResponse {

    @JsonProperty("user_sub")
    private String userSub;

    @JsonProperty("user_email")
    private String userEmail;

    @JsonProperty("default_poll_interval_ms")
    private int defaultPollIntervalMs;

    @JsonProperty("auto_poll_enabled")
    private boolean autoPollEnabled;

    @JsonProperty("notify_on_approval")
    private boolean notifyOnApproval;

    @JsonProperty("notify_on_denial")
    private boolean notifyOnDenial;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    public static UserRdapSettingsResponse fromEntity(UserRdapSettings s) {
        UserRdapSettingsResponse r = new UserRdapSettingsResponse();
        r.setUserSub(s.getUserSub());
        r.setUserEmail(s.getUserEmail());
        r.setDefaultPollIntervalMs(s.getDefaultPollIntervalMs() != null ? s.getDefaultPollIntervalMs() : 30000);
        r.setAutoPollEnabled(s.getAutoPollEnabled() != null ? s.getAutoPollEnabled() : true);
        r.setNotifyOnApproval(s.getNotifyOnApproval() != null ? s.getNotifyOnApproval() : true);
        r.setNotifyOnDenial(s.getNotifyOnDenial() != null ? s.getNotifyOnDenial() : true);
        r.setCreatedAt(s.getCreatedAt());
        r.setUpdatedAt(s.getUpdatedAt());
        return r;
    }
}
