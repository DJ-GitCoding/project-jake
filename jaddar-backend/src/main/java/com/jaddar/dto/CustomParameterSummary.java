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

/**
 * Summary of a custom parameter defined on a request type.
 * Ported from backend/models/agreement.py :: CustomParameterSummary.
 *
 * <p>The {@code @JsonProperty} camelCase aliases match the Requestor Manager wire
 * format (pydantic {@code alias=}); Jackson serializes/deserializes using these names.
 */
@Data
public class CustomParameterSummary {

    private String name;

    @JsonProperty("dataType")
    private String dataType = "string";

    private boolean required = false;

    private String description;

    @JsonProperty("defaultValue")
    private String defaultValue;

    private String placeholder;

    @JsonProperty("enumValues")
    private String enumValues;

    @JsonProperty("validationRegex")
    private String validationRegex;

    @JsonProperty("minValue")
    private String minValue;

    @JsonProperty("maxValue")
    private String maxValue;

    @JsonProperty("maxLength")
    private Integer maxLength;

    @JsonProperty("allowedFileTypes")
    private String allowedFileTypes;

    @JsonProperty("maxFileSizeMb")
    private Integer maxFileSizeMb;

    @JsonProperty("sortOrder")
    private int sortOrder = 0;
}
