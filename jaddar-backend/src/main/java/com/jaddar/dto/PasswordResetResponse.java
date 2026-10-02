/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Response body for the password-reset endpoints.
 *
 * Mirrors the Python dict {@code {"success": true, "message": "..."}} returned by
 * forgot_password / reset_password in backend/routers/password_reset_routes.py.
 */
@Data
@AllArgsConstructor
public class PasswordResetResponse {
    private boolean success;
    private String message;
}
