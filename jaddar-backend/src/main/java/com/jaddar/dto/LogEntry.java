/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jaddar.enums.LogCategory;
import com.jaddar.enums.LogLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * A single log entry. Mirrors the Python {@code models.logging.LogEntry} pydantic model.
 *
 * <p>JSON wire names preserved exactly (snake_case).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {

    @JsonProperty("id")
    private String id;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("level")
    private LogLevel level;

    @JsonProperty("category")
    private LogCategory category;

    @JsonProperty("message")
    private String message;

    @JsonProperty("details")
    private Map<String, Object> details;

    @JsonProperty("user_sub")
    private String userSub;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("ip_address")
    private String ipAddress;

    @JsonProperty("request_path")
    private String requestPath;

    @JsonProperty("request_method")
    private String requestMethod;

    @JsonProperty("response_status")
    private Integer responseStatus;

    @JsonProperty("duration_ms")
    private Double durationMs;
}
