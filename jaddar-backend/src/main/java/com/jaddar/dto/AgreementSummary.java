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
import java.util.ArrayList;
import java.util.List;

/**
 * Summary of an agreement from the Requestor Manager.
 * Ported from backend/models/agreement.py :: AgreementSummary.
 */
@Data
public class AgreementSummary {

    private int id;

    private String name;

    private String description;

    @JsonProperty("accessLevel")
    private Integer accessLevel;

    @JsonProperty("requestorGroupId")
    private int requestorGroupId;

    @JsonProperty("requestorGroupName")
    private String requestorGroupName;

    @JsonProperty("requestorGroupCode")
    private String requestorGroupCode;

    @JsonProperty("dataHolderGroupCode")
    private String dataHolderGroupCode;

    @JsonProperty("dataHolderGroupName")
    private String dataHolderGroupName;

    @JsonProperty("templateId")
    private String templateId;

    @JsonProperty("requestTypes")
    private List<RequestTypeSummary> requestTypes = new ArrayList<>();

    private String status;

    @JsonProperty("sourceType")
    private String sourceType;

    @JsonProperty("effectiveFrom")
    private Instant effectiveFrom;

    @JsonProperty("effectiveTo")
    private Instant effectiveTo;

    @JsonProperty("createdAt")
    private Instant createdAt;
}
