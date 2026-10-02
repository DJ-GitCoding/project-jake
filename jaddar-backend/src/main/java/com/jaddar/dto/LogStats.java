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
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Aggregated log statistics.
 * Mirrors the Python {@code models.logging.LogStats} pydantic model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogStats {

    @JsonProperty("total_entries")
    private int totalEntries;

    @JsonProperty("entries_by_level")
    private Map<String, Integer> entriesByLevel;

    @JsonProperty("entries_by_category")
    private Map<String, Integer> entriesByCategory;

    @JsonProperty("recent_errors")
    private int recentErrors;

    @JsonProperty("introspection_count")
    private int introspectionCount;

    @JsonProperty("unique_users")
    private int uniqueUsers;

    @JsonProperty("unique_clients")
    private int uniqueClients;
}
