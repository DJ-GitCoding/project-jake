/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.controller;

import com.jaddar.dhgroupadmin.entity.User;
import com.jaddar.dhgroupadmin.entity.DataHolderGroup;
import com.jaddar.dhgroupadmin.entity.PasswordResetToken;
import com.jaddar.dhgroupadmin.entity.UserGroupMembership;
import com.jaddar.dhgroupadmin.repository.UserRepository;
import com.jaddar.dhgroupadmin.repository.DataHolderGroupRepository;
import com.jaddar.dhgroupadmin.repository.PasswordResetTokenRepository;
import com.jaddar.dhgroupadmin.repository.UserGroupMembershipRepository;
import com.jaddar.dhgroupadmin.service.AuditService;
import com.jaddar.dhgroupadmin.service.EmailService;
import com.jaddar.dhgroupadmin.config.JwtAuthFilter;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final DataHolderGroupRepository dataHolderGroupRepository;
    private final UserGroupMembershipRepository userGroupMembershipRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuditService audit;
    private final EmailService emailService;
    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.frontend-url:http://localhost:3003}")
    private String frontendUrl;

    @Value("${app.password-reset.expiry-minutes:60}")
    private long resetExpiryMinutes;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request.getEmail() == null || request.getPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Email and password are required"));
        }

        var userOpt = userRepository.findByEmailAndIsActiveTrue(request.getEmail().trim().toLowerCase());
        if (userOpt.isEmpty()) {
            log.warn("Login attempt for unknown or inactive email: {}", request.getEmail());
            audit.logAuth("LOGIN_FAILED", request.getEmail(), AuditService.RESULT_FAILURE,
                    "Unknown or inactive email");
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Invalid email or password"));
        }

        User user = userOpt.get();

        if (!verifyAndMigratePassword(user, request.getPassword())) {
            log.warn("Login attempt with wrong password for: {}", request.getEmail());
            audit.logAuth("LOGIN_FAILED", request.getEmail(), AuditService.RESULT_FAILURE,
                    "Wrong password");
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Invalid email or password"));
        }

        // Update last login
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("User logged in: {} (type {})", user.getEmail(), user.getType());
        audit.logAuth("LOGIN", user.getEmail(), AuditService.RESULT_SUCCESS,
                "Logged in as " + user.getTypeLabel());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "user", toUserResponse(user),
                "token", jwtAuthFilter.generateAccessToken(user.getId(), user.getEmail(), user.getType()),
                "refreshToken", jwtAuthFilter.generateRefreshToken(user.getId(), user.getEmail())
        ));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        var userOpt = userRepository.findById(request.getUserId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();

        if (!verifyAndMigratePassword(user, request.getCurrentPassword())) {
            audit.logAuth("PASSWORD_CHANGE_FAILED", user.getEmail(), AuditService.RESULT_FAILURE,
                    "Current password incorrect");
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Current password is incorrect"));
        }

        user.setPassword(hashPassword(request.getNewPassword()));
        userRepository.save(user);

        audit.logAuth("PASSWORD_CHANGE", user.getEmail(), AuditService.RESULT_SUCCESS,
                "Password changed");

        return ResponseEntity.ok(Map.of("success", true, "message", "Password updated"));
    }

    /**
     * Begin a password reset. Always returns a generic success response so the
     * endpoint cannot be used to enumerate registered emails.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        Map<String, Object> generic = Map.of(
                "success", true,
                "message", "If an account with that email exists, a reset link has been sent.");

        if (email == null || email.isBlank()) {
            return ResponseEntity.ok(generic);
        }
        String normalized = email.trim().toLowerCase();
        var userOpt = userRepository.findByEmailAndIsActiveTrue(normalized);
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for unknown/inactive email: {}", normalized);
            return ResponseEntity.ok(generic);
        }

        User user = userOpt.get();
        passwordResetTokenRepository.invalidateOutstanding(user.getId(), LocalDateTime.now());

        String rawToken = generateRawToken();
        PasswordResetToken token = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(sha256Hex(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(resetExpiryMinutes))
                .build();
        passwordResetTokenRepository.save(token);

        String link = buildResetLink(rawToken);
        try {
            emailService.sendPasswordReset(user.getEmail(), user.getFullName(), link);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
        }
        audit.logAuth("PASSWORD_RESET_REQUESTED", user.getEmail(), AuditService.RESULT_SUCCESS,
                "Reset link issued");
        return ResponseEntity.ok(generic);
    }

    /**
     * Complete a password reset using a token from the emailed link.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        if (request.getToken() == null || request.getToken().isBlank()
                || request.getNewPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Token and new password are required"));
        }
        if (request.getNewPassword().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Password must be at least 8 characters"));
        }

        var tokenOpt = passwordResetTokenRepository.findByTokenHash(sha256Hex(request.getToken()));
        if (tokenOpt.isEmpty() || !tokenOpt.get().isUsable()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid or expired reset link"));
        }

        PasswordResetToken token = tokenOpt.get();
        var userOpt = userRepository.findById(token.getUserId());
        if (userOpt.isEmpty() || !Boolean.TRUE.equals(userOpt.get().getIsActive())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid or expired reset link"));
        }

        User user = userOpt.get();
        user.setPassword(hashPassword(request.getNewPassword()));
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);

        audit.logAuth("PASSWORD_RESET", user.getEmail(), AuditService.RESULT_SUCCESS,
                "Password reset via emailed token");
        return ResponseEntity.ok(Map.of("success", true, "message", "Your password has been reset. You can now log in."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Missing refresh token"));
        }
        try {
            var claims = jwtAuthFilter.parseToken(refreshToken);
            if (!Boolean.TRUE.equals(claims.get("refresh", Boolean.class))) {
                return ResponseEntity.status(401).body(Map.of("success", false, "error", "Not a refresh token"));
            }
            Long userId = claims.get("userId", Long.class);
            String email = claims.getSubject();
            var userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty() || !Boolean.TRUE.equals(userOpt.get().getIsActive())) {
                return ResponseEntity.status(401).body(Map.of("success", false, "error", "User not found or inactive"));
            }
            User user = userOpt.get();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "token", jwtAuthFilter.generateAccessToken(user.getId(), user.getEmail(), user.getType()),
                    "refreshToken", jwtAuthFilter.generateRefreshToken(user.getId(), user.getEmail())
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Invalid or expired refresh token"));
        }
    }

    private Map<String, Object> toUserResponse(User user) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", user.getId());
        r.put("firstName", user.getFirstName());
        r.put("lastName", user.getLastName());
        r.put("email", user.getEmail());
        r.put("type", user.getType());
        r.put("typeLabel", user.getTypeLabel());
        r.put("isActive", user.getIsActive());
        r.put("createdAt", user.getCreatedAt().toString());
        r.put("updatedAt", user.getUpdatedAt().toString());

        // Include group memberships
        List<Map<String, Object>> groups;
        if (user.getType() == 1) {
            // Master user sees all groups
            groups = dataHolderGroupRepository.findAll().stream().map(g -> {
                Map<String, Object> gm = new HashMap<>();
                gm.put("id", g.getId());
                gm.put("name", g.getName());
                gm.put("isActive", g.getIsActive());
                return gm;
            }).toList();
        } else {
            var memberships = userGroupMembershipRepository.findByUserId(user.getId());
            groups = memberships.stream()
                    .map(m -> dataHolderGroupRepository.findById(m.getDataHolderGroupId()).orElse(null))
                    .filter(g -> g != null)
                    .map(g -> {
                        Map<String, Object> gm = new HashMap<>();
                        gm.put("id", g.getId());
                        gm.put("name", g.getName());
                        gm.put("isActive", g.getIsActive());
                        return gm;
                    }).toList();
        }
        r.put("groups", groups);

        return r;
    }

    private static final PasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    /**
     * Verify password against stored hash.
     * Supports both BCrypt (new) and legacy SHA-256 hashes.
     * If a legacy SHA-256 hash matches, it is transparently upgraded to BCrypt.
     */
    public boolean verifyAndMigratePassword(User user, String rawPassword) {
        String stored = user.getPassword();
        // BCrypt hashes start with "$2a$", "$2b$", or "$2y$"
        if (stored.startsWith("$2")) {
            return BCRYPT.matches(rawPassword, stored);
        }
        // Legacy SHA-256 fallback
        String sha256 = legacySha256(rawPassword);
        if (stored.equals(sha256)) {
            // Migrate to BCrypt on successful login
            user.setPassword(BCRYPT.encode(rawPassword));
            userRepository.save(user);
            log.info("Migrated password hash from SHA-256 to BCrypt for user: {}", user.getEmail());
            return true;
        }
        return false;
    }

    public static String hashPassword(String password) {
        return BCRYPT.encode(password);
    }

    // ==================== Password reset helpers ====================

    /** Generate a 256-bit URL-safe random token (raw value, only ever sent by email). */
    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex digest used to store / look up reset tokens. */
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

    /** Legacy SHA-256 hash for migration only — do not use for new passwords */
    private static String legacySha256(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data
    public static class ChangePasswordRequest {
        private Long userId;
        private String currentPassword;
        private String newPassword;
    }

    @Data
    public static class ResetPasswordRequest {
        private String token;
        private String newPassword;
    }
}
