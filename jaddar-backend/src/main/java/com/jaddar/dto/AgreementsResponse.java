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
 * Response from the Requestor Manager external API ("data" payload).
 * Ported from backend/models/agreement.py :: AgreementsResponse.
 */
@Data
public class AgreementsResponse {

    @JsonProperty("userSubject")
    private String userSubject;

    @JsonProperty("userEmail")
    private String userEmail;

    private List<GroupAgreements> groups = new ArrayList<>();

    private List<AgreementSummary> agreements = new ArrayList<>();

    private List<String> warnings;
}
