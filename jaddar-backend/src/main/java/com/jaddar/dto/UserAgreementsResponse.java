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

import java.util.ArrayList;
import java.util.List;

/**
 * Response for the frontend — the user's available agreements.
 * Ported from backend/models/agreement.py :: UserAgreementsResponse.
 *
 * <p>{@code keycloak_groups} keeps the Python snake_case wire name (no pydantic alias
 * was set on it), so it is mapped with {@code @JsonProperty("keycloak_groups")}.
 */
@Data
public class UserAgreementsResponse {

    private List<AgreementSummary> agreements = new ArrayList<>();

    private List<GroupAgreements> groups = new ArrayList<>();

    @JsonProperty("keycloak_groups")
    private List<String> keycloakGroups = new ArrayList<>();

    private List<String> warnings;
}
