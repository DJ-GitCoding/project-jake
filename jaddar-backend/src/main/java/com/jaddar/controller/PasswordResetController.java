/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.controller;

import com.jaddar.dto.ForgotPasswordRequest;
import com.jaddar.dto.PasswordResetResponse;
import com.jaddar.dto.ResetPasswordRequest;
import com.jaddar.service.PasswordResetService;
import com.jaddar.service.PasswordResetService.PasswordResetResponseData;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service password recovery endpoints.
 *
 * Ported from backend/routers/password_reset_routes.py.
 *
 * These routes are PUBLIC (no auth). The shared SecurityConfig permits
 * {@code /api/password-reset/**}, so the controller is mounted there (the Python
 * router used the prefix {@code /api/auth}; the public path is governed by the
 * migration contract's SecurityConfig permit-list instead).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/forgot-password")
    public PasswordResetResponse forgotPassword(@RequestBody ForgotPasswordRequest request) {
        PasswordResetResponseData result = passwordResetService.forgotPassword(request.getEmail());
        return new PasswordResetResponse(result.success(), result.message());
    }

    @PostMapping("/reset-password")
    public PasswordResetResponse resetPassword(@RequestBody ResetPasswordRequest request) {
        PasswordResetResponseData result =
                passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return new PasswordResetResponse(result.success(), result.message());
    }
}
