/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for the token introspection endpoint (RFC 7662 compliant).
 * Ported from backend/models/auth.py :: TokenIntrospectionResponse.
 *
 * The {@code active} field is REQUIRED and indicates whether the token is
 * currently active. All other fields are OPTIONAL and omitted when null
 * (pydantic's default for Optional fields with value None).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenIntrospectionResponse {

    /** Whether the token is active (always serialized). */
    private boolean active;

    /** Token expiration timestamp (Unix epoch). */
    private Long exp;

    /** Token issued-at timestamp (Unix epoch). */
    private Long iat;

    /** Subject (user identifier). */
    private String sub;

    /** Client that requested the token. */
    @JsonProperty("client_id")
    private String clientId;

    /** Space-separated list of scopes. */
    private String scope;

    /** Type of token (e.g., Bearer). */
    @JsonProperty("token_type")
    private String tokenType;
}
