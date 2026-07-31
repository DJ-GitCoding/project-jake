/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import com.jaddar.dataholder.entity.DataHolderUser;
import com.jaddar.dataholder.entity.PasswordResetToken;
import com.jaddar.dataholder.entity.RequestAuditLog.EventType;
import com.jaddar.dataholder.service.AuditService;
import com.jaddar.dataholder.repository.DataHolderUserRepository;
import com.jaddar.dataholder.repository.PasswordResetTokenRepository;
import com.jaddar.dataholder.service.EmailService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class UserAuthController {

    private final DataHolderUserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Value("${app.frontend-url:http://localhost:3001}")
    private String frontendUrl;

    @Value("${app.password-reset.expiry-minutes:60}")
    private long resetExpiryMinutes;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${dataholder.jwt.secret:ThisIsASecretKeyForJWTTokenGenerationThatMustBeAtLeast256BitsLong!!}")
    private String jwtSecret;

    @Value("${dataholder.jwt.expiration:86400000}")
    private long jwtExpiration;

    @Value("${dataholder.admin.username:admin}")
    private String defaultAdminUsername;

    @Value("${dataholder.admin.password:admin123}")
    private String defaultAdminPassword;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        createDefaultAdminUser();
    }

    private void createDefaultAdminUser() {
        if (!userRepository.existsByUsername(defaultAdminUsername)) {
            DataHolderUser admin = DataHolderUser.builder()
                    .username(defaultAdminUsername)
                    .password(passwordEncoder.encode(defaultAdminPassword))
                    .email("admin@dataholder.local")
                    .firstName("System")
                    .lastName("Administrator")
                    .fullName("System Administrator")
                    .type("MASTER")
                    .role("ADMIN")
                    .isActive(true)
                    .createdBy("SYSTEM")
                    .build();
            userRepository.save(admin);
            log.info("Default MASTER admin user created: {}", defaultAdminUsername);
        }
    }

    // ==================== Authentication ====================

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        if (request.getEmail() == null || request.getPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Email and password are required"
            ));
        }

        Optional<DataHolderUser> userOpt = userRepository.findByEmailIgnoreCaseAndIsActiveTrue(request.getEmail().trim());

        if (userOpt.isEmpty()) {
            log.warn("Login failed: user not found - {}", request.getEmail());
            auditService.logAuth(EventType.LOGIN_FAILED, request.getEmail(),
                    AuditService.RESULT_DENIED, "User not found");
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "error", "Invalid email or password"
            ));
        }

        DataHolderUser user = userOpt.get();

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed: invalid password for email - {}", request.getEmail());
            auditService.logAuth(EventType.LOGIN_FAILED, request.getEmail(),
                    AuditService.RESULT_DENIED, "Invalid password");
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "error", "Invalid email or password"
            ));
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = generateToken(user);
        String refreshToken = generateRefreshToken(user);

        log.info("User logged in successfully: {} (type: {})", user.getUsername(), user.getType());
        auditService.logAuth(EventType.LOGIN, request.getEmail(),
                AuditService.RESULT_SUCCESS, "User logged in (type: " + user.getType() + ")");

        return ResponseEntity.ok(Map.of(
            "success", true,
            "accessToken", accessToken,
            "refreshToken", refreshToken,
            "expiresIn", jwtExpiration / 1000,
            "user", buildUserInfo(user)
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        if (request.getRefreshToken() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Refresh token is required"
            ));
        }

        try {
            var claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(request.getRefreshToken())
                    .getBody();

            if (!"refresh".equals(claims.get("type"))) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "error", "Invalid refresh token"
                ));
            }

            String username = claims.getSubject();
            Optional<DataHolderUser> userOpt = userRepository.findByUsernameAndIsActiveTrue(username);

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "error", "User not found or inactive"
                ));
            }

            DataHolderUser user = userOpt.get();
            String newAccessToken = generateToken(user);
            String newRefreshToken = generateRefreshToken(user);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "accessToken", newAccessToken,
                "refreshToken", newRefreshToken,
                "expiresIn", jwtExpiration / 1000,
                "user", buildUserInfo(user)
            ));
        } catch (Exception e) {
            log.error("Token refresh failed", e);
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "error", "Invalid or expired refresh token"
            ));
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody ValidateRequest request) {
        if (request.getToken() == null) {
            return ResponseEntity.ok(Map.of("valid", false, "error", "Token is required"));
        }

        try {
            var claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(request.getToken())
                    .getBody();

            return ResponseEntity.ok(Map.of(
                "valid", true,
                "user", Map.of(
                    "userId", claims.get("userId"),
                    "username", claims.getSubject(),
                    "email", claims.get("email") != null ? claims.get("email") : "",
                    "fullName", claims.get("fullName") != null ? claims.get("fullName") : "",
                    "firstName", claims.get("firstName") != null ? claims.get("firstName") : "",
                    "lastName", claims.get("lastName") != null ? claims.get("lastName") : "",
                    "role", claims.get("role"),
                    "type", claims.get("type") != null ? claims.get("type") : "ASSISTANT_ADMIN"
                )
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("valid", false, "error", "Invalid or expired token"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "error", "Authorization header required"
            ));
        }

        try {
            String token = authHeader.substring(7);
            var claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return ResponseEntity.ok(Map.of(
                "success", true,
                "user", Map.of(
                    "userId", claims.get("userId"),
                    "username", claims.getSubject(),
                    "email", claims.get("email") != null ? claims.get("email") : "",
                    "fullName", claims.get("fullName") != null ? claims.get("fullName") : "",
                    "firstName", claims.get("firstName") != null ? claims.get("firstName") : "",
                    "lastName", claims.get("lastName") != null ? claims.get("lastName") : "",
                    "role", claims.get("role"),
                    "type", claims.get("type") != null ? claims.get("type") : "ASSISTANT_ADMIN"
                )
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "error", "Invalid or expired token"
            ));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ChangePasswordRequest request) {
        DataHolderUser currentUser = extractUserFromToken(authHeader);
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Unauthorized"));
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), currentUser.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Current password is incorrect"));
        }

        currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(currentUser);

        return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully"));
    }

    // ==================== Password recovery ====================

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
        Optional<DataHolderUser> userOpt = userRepository.findByEmail(normalized);
        if (userOpt.isEmpty() || !Boolean.TRUE.equals(userOpt.get().getIsActive())) {
            log.info("Password reset requested for unknown/inactive email: {}", normalized);
            return ResponseEntity.ok(generic);
        }

        DataHolderUser user = userOpt.get();
        passwordResetTokenRepository.invalidateOutstanding(user.getId(), LocalDateTime.now());

        String rawToken = generateRawToken();
        PasswordResetToken token = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(sha256Hex(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(resetExpiryMinutes))
                .build();
        passwordResetTokenRepository.save(token);

        String displayName = user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName() : user.getUsername();
        try {
            emailService.sendPasswordReset(user.getEmail(), displayName, buildResetLink(rawToken));
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
        }
        return ResponseEntity.ok(generic);
    }

    /**
     * Complete a password reset using a token from the emailed link.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        if (request.getToken() == null || request.getToken().isBlank() || request.getNewPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Token and new password are required"));
        }
        if (request.getNewPassword().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Password must be at least 8 characters"));
        }

        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByTokenHash(sha256Hex(request.getToken()));
        if (tokenOpt.isEmpty() || !tokenOpt.get().isUsable()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid or expired reset link"));
        }

        PasswordResetToken token = tokenOpt.get();
        Optional<DataHolderUser> userOpt = userRepository.findById(token.getUserId());
        if (userOpt.isEmpty() || !Boolean.TRUE.equals(userOpt.get().getIsActive())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid or expired reset link"));
        }

        DataHolderUser user = userOpt.get();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);

        log.info("Password reset completed for user: {}", user.getUsername());
        return ResponseEntity.ok(Map.of("success", true, "message", "Your password has been reset. You can now log in."));
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

    // ==================== User Management (MASTER only) ====================

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        DataHolderUser currentUser = extractUserFromToken(authHeader);
        if (currentUser == null || !currentUser.isMaster()) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Only MASTER accounts can manage users"));
        }

        if (page == null) {
            // Backward-compatible: no pagination params -> full list.
            List<Map<String, Object>> users = userRepository.findAll().stream()
                    .map(this::buildUserInfo)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("success", true, "users", users));
        }

        Sort sort = Sort.by("desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 500), sort);
        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;
        Page<DataHolderUser> pageResult = userRepository.searchAll(searchTerm, pageable);
        List<Map<String, Object>> content = pageResult.getContent().stream()
                .map(this::buildUserInfo)
                .collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("totalElements", pageResult.getTotalElements());
        body.put("totalPages", pageResult.getTotalPages());
        body.put("page", pageResult.getNumber());
        body.put("size", pageResult.getSize());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id) {
        DataHolderUser currentUser = extractUserFromToken(authHeader);
        if (currentUser == null || !currentUser.isMaster()) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Only MASTER accounts can manage users"));
        }

        Optional<DataHolderUser> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "User not found"));
        }

        return ResponseEntity.ok(Map.of("success", true, "user", buildUserInfo(userOpt.get())));
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody CreateUserRequest request) {
        DataHolderUser currentUser = extractUserFromToken(authHeader);
        if (currentUser == null || !currentUser.isMaster()) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Only MASTER accounts can create users"));
        }

        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Username is required"));
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Password is required"));
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Email is required"));
        }

        if (userRepository.existsByUsername(request.getUsername().trim())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Username already exists"));
        }
        if (userRepository.existsByEmail(request.getEmail().trim())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Email already exists"));
        }

        String type = request.getType() != null ? request.getType() : "ASSISTANT_ADMIN";
        if (!type.equals("MASTER") && !type.equals("ASSISTANT_ADMIN")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Type must be MASTER or ASSISTANT_ADMIN"));
        }

        DataHolderUser newUser = DataHolderUser.builder()
                .username(request.getUsername().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail().trim())
                .type(type)
                .role("ADMIN")
                .isActive(true)
                .createdBy(currentUser.getUsername())
                .build();

        newUser = userRepository.save(newUser);
        log.info("User created by {}: {} (type: {})", currentUser.getUsername(), newUser.getUsername(), type);

        return ResponseEntity.ok(Map.of("success", true, "user", buildUserInfo(newUser)));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request) {
        DataHolderUser currentUser = extractUserFromToken(authHeader);
        if (currentUser == null || !currentUser.isMaster()) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Only MASTER accounts can update users"));
        }

        Optional<DataHolderUser> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "User not found"));
        }

        DataHolderUser user = userOpt.get();

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getEmail() != null) {
            if (!request.getEmail().equals(user.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Email already exists"));
            }
            user.setEmail(request.getEmail());
        }
        if (request.getType() != null) {
            if (!request.getType().equals("MASTER") && !request.getType().equals("ASSISTANT_ADMIN")) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Type must be MASTER or ASSISTANT_ADMIN"));
            }
            user.setType(request.getType());
        }
        if (request.getIsActive() != null) user.setIsActive(request.getIsActive());
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        user = userRepository.save(user);
        log.info("User updated by {}: {}", currentUser.getUsername(), user.getUsername());

        return ResponseEntity.ok(Map.of("success", true, "user", buildUserInfo(user)));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id) {
        DataHolderUser currentUser = extractUserFromToken(authHeader);
        if (currentUser == null || !currentUser.isMaster()) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Only MASTER accounts can delete users"));
        }

        if (currentUser.getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Cannot delete your own account"));
        }

        Optional<DataHolderUser> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "User not found"));
        }

        userRepository.delete(userOpt.get());
        log.info("User deleted by {}: {}", currentUser.getUsername(), userOpt.get().getUsername());

        return ResponseEntity.ok(Map.of("success", true, "message", "User deleted successfully"));
    }

    // ==================== Helpers ====================

    private DataHolderUser extractUserFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        try {
            String token = authHeader.substring(7);
            var claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            String username = claims.getSubject();
            return userRepository.findByUsernameAndIsActiveTrue(username).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> buildUserInfo(DataHolderUser user) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("firstName", user.getFirstName());
        info.put("lastName", user.getLastName());
        info.put("fullName", user.getFullName());
        info.put("email", user.getEmail());
        info.put("type", user.getType());
        info.put("role", user.getRole());
        info.put("isActive", user.getIsActive());
        info.put("lastLogin", user.getLastLogin());
        info.put("createdAt", user.getCreatedAt());
        info.put("updatedAt", user.getUpdatedAt());
        info.put("createdBy", user.getCreatedBy());
        return info;
    }

    private String generateToken(DataHolderUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("fullName", user.getFullName());
        claims.put("firstName", user.getFirstName());
        claims.put("lastName", user.getLastName());
        claims.put("role", user.getRole());
        claims.put("type", user.getType());
        claims.put("userType", "access");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .setIssuer("dataholder")
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private String generateRefreshToken(DataHolderUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("type", "refresh");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + (jwtExpiration * 7)))
                .setIssuer("dataholder")
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // ==================== Request DTOs ====================

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data
    public static class RefreshRequest {
        private String refreshToken;
    }

    @Data
    public static class ValidateRequest {
        private String token;
    }

    @Data
    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;
    }

    @Data
    public static class ResetPasswordRequest {
        private String token;
        private String newPassword;
    }

    @Data
    public static class CreateUserRequest {
        private String username;
        private String password;
        private String firstName;
        private String lastName;
        private String email;
        private String type; // MASTER or ASSISTANT_ADMIN
    }

    @Data
    public static class UpdateUserRequest {
        private String firstName;
        private String lastName;
        private String email;
        private String type;
        private Boolean isActive;
        private String password; // optional - only set if changing
    }
}
