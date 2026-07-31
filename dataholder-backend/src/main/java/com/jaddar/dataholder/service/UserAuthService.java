/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.entity.DataHolderUser;
import com.jaddar.dataholder.repository.DataHolderUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAuthService {

    private final DataHolderUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${dataholder.jwt.secret}")
    private String jwtSecret;

    @Value("${dataholder.jwt.expiration:86400000}")
    private long jwtExpiration;

    @Value("${dataholder.jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    @Value("${dataholder.admin.username:admin}")
    private String defaultAdminUsername;

    @Value("${dataholder.admin.password}")
    private String defaultAdminPassword;

    @Value("${dataholder.admin.email:admin@dataholder.local}")
    private String defaultAdminEmail;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "dataholder.jwt.secret must be at least 32 bytes of high-entropy material; refusing to start.");
        }
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        createDefaultAdminUser();
    }

    @Transactional
    public void createDefaultAdminUser() {
        if (defaultAdminPassword == null || defaultAdminPassword.isBlank()) {
            throw new IllegalStateException(
                "dataholder.admin.password (env ADMIN_PASSWORD) must be set to seed the admin user.");
        }
        if (!userRepository.existsByUsername(defaultAdminUsername)) {
            DataHolderUser admin = DataHolderUser.builder()
                    .username(defaultAdminUsername)
                    .password(passwordEncoder.encode(defaultAdminPassword))
                    .email(defaultAdminEmail)
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

    @Transactional
    public AuthResult authenticate(String username, String password) {
        Optional<DataHolderUser> userOpt = userRepository.findByUsernameAndIsActiveTrue(username);

        if (userOpt.isEmpty()) {
            log.warn("Authentication failed: user not found - {}", username);
            return AuthResult.failure("Invalid username or password");
        }

        DataHolderUser user = userOpt.get();

        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("Authentication failed: invalid password for user - {}", username);
            return AuthResult.failure("Invalid username or password");
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user);

        log.info("User authenticated successfully: {} (type: {})", username, user.getType());

        return AuthResult.success(accessToken, refreshToken, user);
    }

    public AuthResult refreshToken(String refreshToken) {
        try {
            Claims claims = parseToken(refreshToken);

            if (!"refresh".equals(claims.get("type"))) {
                return AuthResult.failure("Invalid refresh token");
            }

            String username = claims.getSubject();
            Optional<DataHolderUser> userOpt = userRepository.findByUsernameAndIsActiveTrue(username);

            if (userOpt.isEmpty()) {
                return AuthResult.failure("User not found or inactive");
            }

            DataHolderUser user = userOpt.get();
            String newAccessToken = generateAccessToken(user);
            String newRefreshToken = generateRefreshToken(user);

            return AuthResult.success(newAccessToken, newRefreshToken, user);
        } catch (Exception e) {
            log.error("Token refresh failed", e);
            return AuthResult.failure("Invalid or expired refresh token");
        }
    }

    public Optional<TokenUserInfo> validateToken(String token) {
        try {
            Claims claims = parseToken(token);

            if (!"access".equals(claims.get("userType"))) {
                return Optional.empty();
            }

            return Optional.of(TokenUserInfo.builder()
                    .userId(claims.get("userId", Long.class))
                    .username(claims.getSubject())
                    .email(claims.get("email", String.class))
                    .fullName(claims.get("fullName", String.class))
                    .firstName(claims.get("firstName", String.class))
                    .lastName(claims.get("lastName", String.class))
                    .role(claims.get("role", String.class))
                    .type(claims.get("type", String.class))
                    .build());
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<DataHolderUser> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<DataHolderUser> getUserByUsername(String username) {
        return userRepository.findByUsernameAndIsActiveTrue(username);
    }

    public List<DataHolderUser> getAllActiveUsers() {
        return userRepository.findByIsActiveTrue();
    }

    @Transactional
    public DataHolderUser createUser(String username, String password, String email,
                                     String firstName, String lastName, String type, String createdBy) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        DataHolderUser user = DataHolderUser.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .type(type != null ? type : "ASSISTANT_ADMIN")
                .role("ADMIN")
                .isActive(true)
                .createdBy(createdBy)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public DataHolderUser updateUser(Long userId, String email, String firstName, String lastName,
                                     String type, Boolean isActive) {
        DataHolderUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (email != null) user.setEmail(email);
        if (firstName != null) user.setFirstName(firstName);
        if (lastName != null) user.setLastName(lastName);
        if (type != null) user.setType(type);
        if (isActive != null) user.setIsActive(isActive);

        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        DataHolderUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        DataHolderUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private String generateAccessToken(DataHolderUser user) {
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
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .setIssuer("dataholder")
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // Inner classes
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    public static class AuthResult {
        private boolean success;
        private String accessToken;
        private String refreshToken;
        private String error;
        private UserInfo user;

        public static AuthResult success(String accessToken, String refreshToken, DataHolderUser user) {
            return AuthResult.builder()
                    .success(true)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .user(UserInfo.fromEntity(user))
                    .build();
        }

        public static AuthResult failure(String error) {
            return AuthResult.builder()
                    .success(false)
                    .error(error)
                    .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String fullName;
        private String firstName;
        private String lastName;
        private String role;
        private String type;

        public static UserInfo fromEntity(DataHolderUser user) {
            return UserInfo.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .role(user.getRole())
                    .type(user.getType())
                    .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class TokenUserInfo {
        private Long userId;
        private String username;
        private String email;
        private String fullName;
        private String firstName;
        private String lastName;
        private String role;
        private String type;
    }
}
