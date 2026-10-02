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
 * Agreements grouped by RequestorGroup.
 * Ported from backend/models/agreement.py :: GroupAgreements.
 */
@Data
public class GroupAgreements {

    @JsonProperty("groupId")
    private int groupId;

    @JsonProperty("groupName")
    private String groupName;

    private List<AgreementSummary> agreements = new ArrayList<>();
}
