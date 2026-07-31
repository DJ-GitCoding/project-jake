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
 * Request body for POST /api/password-reset/reset-password.
 *
 * Ported from the pydantic {@code ResetPasswordRequest} in
 * backend/routers/password_reset_routes.py. The JSON field {@code newPassword}
 * is already camelCase in the Python pydantic model, so no alias is needed.
 */
@Data
public class ResetPasswordRequest {
    private String token;
    private String newPassword;
}
