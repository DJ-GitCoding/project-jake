/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.controller;

import com.requestormanager.dto.ApiResponse;
import com.requestormanager.dto.AuthDto;
import com.requestormanager.dto.UserDto;
import com.requestormanager.entity.PasswordResetToken;
import com.requestormanager.repository.PasswordResetTokenRepository;
import com.requestormanager.security.KeycloakAuthService;
import com.requestormanager.security.KeycloakAuthenticationToken;
import com.requestormanager.service.EmailService;
import com.requestormanager.service.KeycloakUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication endpoints - delegates to Keycloak")
@Slf4j
public class AuthController {
    
    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakUserService keycloakUserService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final RestTemplate restTemplate;

    @Value("${keycloak.token-url:http://keycloak:8080/realms/master/protocol/openid-connect/token}")
    private String keycloakTokenUrl;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.password-reset.expiry-minutes:60}")
    private long resetExpiryMinutes;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    @PostMapping("/login")
    @Operation(summary = "Login via Keycloak", description = "Authenticate user with Keycloak and get tokens")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Login successful",
            content = @Content(schema = @Schema(implementation = AuthResponseWrapper.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    public ResponseEntity<ApiResponse<AuthDto.AuthResponse>> login(
            @Valid @RequestBody AuthDto.LoginRequest request) {
        try {
            // Request token from Keycloak using password grant
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "password");
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("username", request.getEmail());
            body.add("password", request.getPassword());

            HttpEntity<MultiValueMap<String, String>> keycloakRequest = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    keycloakTokenUrl,
                    keycloakRequest,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> tokenResponse = response.getBody();
                String accessToken = (String) tokenResponse.get("access_token");
                String refreshToken = (String) tokenResponse.get("refresh_token");
                Integer expiresIn = (Integer) tokenResponse.get("expires_in");

                // Decode token to get user info
                KeycloakAuthService.KeycloakUser keycloakUser = keycloakAuthService.decodeToken(accessToken);

                if (keycloakUser == null) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(ApiResponse.error("Failed to decode token"));
                }

                // Build user response with role name (not enum name)
                AuthDto.UserResponse userResponse = AuthDto.UserResponse.builder()
                        .id(keycloakUser.getSub())
                        .email(keycloakUser.getEmail())
                        .firstName(keycloakUser.getFirstName())
                        .lastName(keycloakUser.getLastName())
                        .type(keycloakUser.getUserType().getKeycloakRoleName()) // Use role name, not enum name
                        .groups(keycloakUser.getGroups())
                        .roles(keycloakUser.getRealmRoles()) // Include actual Keycloak roles
                        .build();

                AuthDto.AuthResponse authResponse = AuthDto.AuthResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .tokenType("Bearer")
                        .expiresIn(expiresIn)
                        .user(userResponse)
                        .build();

                log.info("User logged in via Keycloak: {} with role {}", 
                        keycloakUser.getUsername(), keycloakUser.getUserType().getKeycloakRoleName());
                return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid credentials"));

        } catch (Exception e) {
            log.error("Login failed for {}: {}", request.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid credentials"));
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Get new access token using refresh token")
    public ResponseEntity<ApiResponse<AuthDto.AuthResponse>> refresh(
            @RequestBody AuthDto.RefreshRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "refresh_token");
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("refresh_token", request.getRefreshToken());

            HttpEntity<MultiValueMap<String, String>> keycloakRequest = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    keycloakTokenUrl,
                    keycloakRequest,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> tokenResponse = response.getBody();
                String accessToken = (String) tokenResponse.get("access_token");
                String refreshToken = (String) tokenResponse.get("refresh_token");
                Integer expiresIn = (Integer) tokenResponse.get("expires_in");

                KeycloakAuthService.KeycloakUser keycloakUser = keycloakAuthService.decodeToken(accessToken);

                AuthDto.UserResponse userResponse = null;
                if (keycloakUser != null) {
                    userResponse = AuthDto.UserResponse.builder()
                            .id(keycloakUser.getSub())
                            .email(keycloakUser.getEmail())
                            .firstName(keycloakUser.getFirstName())
                            .lastName(keycloakUser.getLastName())
                            .type(keycloakUser.getUserType().getKeycloakRoleName())
                            .groups(keycloakUser.getGroups())
                            .roles(keycloakUser.getRealmRoles())
                            .build();
                }

                AuthDto.AuthResponse authResponse = AuthDto.AuthResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .tokenType("Bearer")
                        .expiresIn(expiresIn)
                        .user(userResponse)
                        .build();

                return ResponseEntity.ok(ApiResponse.success("Token refreshed", authResponse));
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid refresh token"));

        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Token refresh failed"));
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Get information about the currently authenticated user")
    public ResponseEntity<ApiResponse<AuthDto.UserResponse>> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication instanceof KeycloakAuthenticationToken) {
            KeycloakAuthService.KeycloakUser keycloakUser = 
                    ((KeycloakAuthenticationToken) authentication).getKeycloakUser();

            AuthDto.UserResponse userResponse = AuthDto.UserResponse.builder()
                    .id(keycloakUser.getSub())
                    .email(keycloakUser.getEmail())
                    .firstName(keycloakUser.getFirstName())
                    .lastName(keycloakUser.getLastName())
                    .type(keycloakUser.getUserType().getKeycloakRoleName())
                    .groups(keycloakUser.getGroups())
                    .roles(keycloakUser.getRealmRoles())
                    .build();

            return ResponseEntity.ok(ApiResponse.success("User retrieved", userResponse));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Not authenticated"));
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate token", description = "Check if a token is valid")
    public ResponseEntity<ApiResponse<Boolean>> validateToken(@RequestBody AuthDto.ValidateRequest request) {
        KeycloakAuthService.KeycloakUser user = keycloakAuthService.validateToken(request.getToken());
        
        if (user != null && user.isActive()) {
            return ResponseEntity.ok(ApiResponse.success("Token is valid", true));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Token is invalid", false));
    }
    
    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset",
            description = "Emails a reset link if the account exists. Always returns success to avoid account enumeration.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody AuthDto.ForgotPasswordRequest request) {
        ApiResponse<Void> generic = ApiResponse.success(
                "If an account with that email exists, a reset link has been sent.");
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase();
        if (email == null || email.isBlank()) {
            return ResponseEntity.ok(generic);
        }

        try {
            UserDto.UserResponse user = keycloakUserService.lookupUserByEmail(email);
            if (user == null || user.getId() == null) {
                log.info("Password reset requested for unknown email: {}", email);
                return ResponseEntity.ok(generic);
            }

            passwordResetTokenRepository.invalidateOutstanding(email, LocalDateTime.now());

            String rawToken = generateRawToken();
            PasswordResetToken token = PasswordResetToken.builder()
                    .email(email)
                    .keycloakUserId(user.getId())
                    .tokenHash(sha256Hex(rawToken))
                    .expiresAt(LocalDateTime.now().plusMinutes(resetExpiryMinutes))
                    .build();
            passwordResetTokenRepository.save(token);

            String displayName = buildDisplayName(user);
            emailService.sendPasswordReset(user.getEmail() != null ? user.getEmail() : email,
                    displayName, buildResetLink(rawToken));
        } catch (Exception e) {
            // Never leak failures back to the caller; just log.
            log.error("Error handling forgot-password for {}: {}", email, e.getMessage());
        }
        return ResponseEntity.ok(generic);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Complete password reset",
            description = "Validates the emailed token and sets a new Keycloak password.")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody AuthDto.ResetPasswordRequest request) {
        if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Password must be at least 8 characters"));
        }

        Optional<PasswordResetToken> tokenOpt =
                passwordResetTokenRepository.findByTokenHash(sha256Hex(request.getToken()));
        if (tokenOpt.isEmpty() || !tokenOpt.get().isUsable()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid or expired reset link"));
        }

        PasswordResetToken token = tokenOpt.get();
        try {
            keycloakUserService.resetPasswordById(token.getKeycloakUserId(), request.getNewPassword());
        } catch (Exception e) {
            log.error("Failed to reset Keycloak password for {}: {}", token.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Could not reset password. Please try again."));
        }

        token.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);

        log.info("Password reset completed for {}", token.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Your password has been reset. You can now log in."));
    }

    // ==================== Password reset helpers ====================

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }

    private String buildResetLink(String rawToken) {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + "/reset-password?token=" + rawToken;
    }

    private static String buildDisplayName(UserDto.UserResponse user) {
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        String name = (first + " " + last).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }

    // Schema wrapper for Swagger
    @Schema(name = "AuthApiResponse", description = "API response with authentication data")
    private static class AuthResponseWrapper extends ApiResponse<AuthDto.AuthResponse> {}
}
