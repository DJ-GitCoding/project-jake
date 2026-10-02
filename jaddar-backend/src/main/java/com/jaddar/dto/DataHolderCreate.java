/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Request body for creating a data holder.
 * Mirrors pydantic {@code DataHolderCreate} in {@code models/dataholder.py}.
 *
 * <p>Field-level constraints expressed as jakarta.validation; the cross-field /
 * value constraints (base_urls scheme, asn_ranges pairs, ip_ranges CIDR, auth_type)
 * are enforced manually in {@link com.jaddar.service.DataHolderService}.
 */
@Data
public class DataHolderCreate {

    @NotNull
    @Size(min = 1, max = 255)
    private String name;

    private String description;

    @NotNull
    @NotEmpty
    @JsonProperty("base_urls")
    private List<String> baseUrls;

    private List<String> tlds = new ArrayList<>();

    @JsonProperty("ip_ranges")
    private List<String> ipRanges = new ArrayList<>();

    @JsonProperty("asn_ranges")
    private List<List<Integer>> asnRanges = new ArrayList<>();

    @JsonProperty("requires_auth")
    private boolean requiresAuth = false;

    @JsonProperty("auth_type")
    private String authType = "none";

    @JsonProperty("is_active")
    private boolean isActive = true;
}
