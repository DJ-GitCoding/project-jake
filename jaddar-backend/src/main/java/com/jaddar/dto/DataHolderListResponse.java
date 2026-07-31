/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response model for listing data holders.
 * Mirrors pydantic {@code DataHolderListResponse}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataHolderListResponse {

    @JsonProperty("data_holders")
    private List<DataHolderResponse> dataHolders;

    private long total;

    @JsonProperty("active_count")
    private long activeCount;
}
