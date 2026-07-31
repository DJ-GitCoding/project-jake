/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for the token introspection endpoint.
 * Ported from backend/models/auth.py :: TokenIntrospectionRequest.
 *
 * The client credentials are passed via HTTP Basic Auth header,
 * not in the request body (per OAuth2 standard).
 */
@Data
public class TokenIntrospectionRequest {

    /** The token to introspect (required). */
    @NotNull
    private String token;

    /** Hint about the type of token (access_token or refresh_token). */
    @JsonProperty("token_type_hint")
    private String tokenTypeHint;
}
