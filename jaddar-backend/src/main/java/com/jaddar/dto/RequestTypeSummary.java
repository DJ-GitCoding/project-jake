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
 * Summary of a request type from an agreement/template.
 * Ported from backend/models/agreement.py :: RequestTypeSummary.
 */
@Data
public class RequestTypeSummary {

    private String name;

    @JsonProperty("typeCode")
    private Integer typeCode;

    private String description;

    @JsonProperty("accessLevel")
    private int accessLevel = 0;

    @JsonProperty("supportsConfidential")
    private boolean supportsConfidential = false;

    @JsonProperty("supportsExigent")
    private boolean supportsExigent = false;

    @JsonProperty("customParameters")
    private List<CustomParameterSummary> customParameters = new ArrayList<>();
}
