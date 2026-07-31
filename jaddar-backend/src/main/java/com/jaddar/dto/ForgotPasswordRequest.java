/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import lombok.Data;

/**
 * Request body for POST /api/password-reset/forgot-password.
 *
 * Ported from the pydantic {@code ForgotPasswordRequest} in
 * backend/routers/password_reset_routes.py.
 */
@Data
public class ForgotPasswordRequest {
    private String email;
}
