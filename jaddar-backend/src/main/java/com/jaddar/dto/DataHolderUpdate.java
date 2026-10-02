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
 * Request body for updating a data holder.
 * Mirrors pydantic {@code DataHolderUpdate} in {@code models/dataholder.py}.
 *
 * <p>All fields optional (null = not supplied). Manual validation of base_urls /
 * asn_ranges / auth_type happens in {@link com.jaddar.service.DataHolderService}.
 */
@Data
public class DataHolderUpdate {

    private String name;

    private String description;

    @JsonProperty("base_urls")
    private List<String> baseUrls;

    private List<String> tlds;

    @JsonProperty("ip_ranges")
    private List<String> ipRanges;

    @JsonProperty("asn_ranges")
    private List<List<Integer>> asnRanges;

    @JsonProperty("requires_auth")
    private Boolean requiresAuth;

    @JsonProperty("auth_type")
    private String authType;

    @JsonProperty("is_active")
    private Boolean isActive;
}
