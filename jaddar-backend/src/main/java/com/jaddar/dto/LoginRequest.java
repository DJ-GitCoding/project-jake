/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Direct (username/password) login request. Backs {@code POST /api/auth/login}, which performs the
 * OAuth2 Resource Owner Password Credentials grant against Keycloak so the user can sign in from the
 * app without being redirected to the Keycloak-hosted login page.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
