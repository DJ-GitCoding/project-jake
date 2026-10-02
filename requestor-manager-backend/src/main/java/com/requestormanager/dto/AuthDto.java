/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class AuthDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Login request")
    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Schema(description = "User email", example = "admin@example.com")
        private String email;

        @NotBlank(message = "Password is required")
        @Schema(description = "User password")
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Authentication response")
    public static class AuthResponse {
        @Schema(description = "JWT access token")
        private String accessToken;

        @Schema(description = "JWT refresh token")
        private String refreshToken;

        @Schema(description = "Token type", example = "Bearer")
        private String tokenType;

        @Schema(description = "Token expiration time in seconds")
        private Integer expiresIn;

        @Schema(description = "Authenticated user information")
        private UserResponse user;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "User information from authentication")
    public static class UserResponse {
        @Schema(description = "User ID (Keycloak sub)")
        private String id;

        @Schema(description = "User email")
        private String email;

        @Schema(description = "User first name")
        private String firstName;

        @Schema(description = "User last name")
        private String lastName;

        @Schema(description = "User type/role name (e.g., jaddar_master_admin, group_admin)")
        private String type;

        @Schema(description = "User's Keycloak groups")
        private List<String> groups;

        @Schema(description = "User's Keycloak realm roles")
        private List<String> roles;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Token refresh request")
    public static class RefreshRequest {
        @NotBlank(message = "Refresh token is required")
        @Schema(description = "JWT refresh token")
        private String refreshToken;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Token validation request")
    public static class ValidateRequest {
        @NotBlank(message = "Token is required")
        @Schema(description = "JWT token to validate")
        private String token;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Forgot password request")
    public static class ForgotPasswordRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Schema(description = "Account email", example = "user@example.com")
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Reset password request")
    public static class ResetPasswordRequest {
        @NotBlank(message = "Token is required")
        @Schema(description = "Reset token from the emailed link")
        private String token;

        @NotBlank(message = "New password is required")
        @Schema(description = "New password")
        private String newPassword;
    }
}
