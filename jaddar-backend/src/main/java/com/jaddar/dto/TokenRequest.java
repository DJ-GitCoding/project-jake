/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request for exchanging an authorization code for tokens. Ported from
 * {@code backend/models/auth.py :: TokenRequest}.
 */
@Data
public class TokenRequest {

    @NotNull
    private String code;

    private String state;
}
