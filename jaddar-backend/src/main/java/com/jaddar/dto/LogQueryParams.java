/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.jaddar.enums.LogCategory;
import com.jaddar.enums.LogLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Filtering + pagination parameters for log queries.
 * Mirrors the Python {@code models.logging.LogQueryParams} pydantic model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogQueryParams {

    private LogLevel level;
    private LogCategory category;
    private Instant startDate;
    private Instant endDate;
    private String userSub;
    private String clientId;
    private String search;

    @Builder.Default
    private int limit = 100;

    @Builder.Default
    private int offset = 0;
}
