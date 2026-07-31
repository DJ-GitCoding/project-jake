/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.config.JaddarProperties;
import com.jaddar.entity.PasswordResetToken;
import com.jaddar.exception.ApiException;
import com.jaddar.repository.PasswordResetTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Self-service password recovery for the main Jaddar app.
 *
 * Ported from backend/routers/password_reset_routes.py.
 *
 * Users authenticate through Keycloak, so this flow generates its own single-use
 * reset token, emails a link via SendGrid, and applies the new password through
 * the Keycloak Admin REST API once the token is validated.
 */
@Slf4j
@Service
public class PasswordResetService {

    private static final String GENERIC_MESSAGE =
            "If an account with that email exists, a reset link has been sent.";
    private static final Duration ADMIN_TIMEOUT = Duration.ofSeconds(30);
    private static final TypeReference<List<Map<String, Object>>> USER_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final WebClient keycloakWebClient;
    private final ObjectMapper objectMapper;
    private final JaddarProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    private final String adminUser;
    private final String adminPassword;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                EmailService emailService,
                                @Qualifier("keycloakWebClient") WebClient keycloakWebClient,
                                ObjectMapper objectMapper,
                                JaddarProperties properties,
                                @Value("${KEYCLOAK_ADMIN:admin}") String adminUser,
                                @Value("${KEYCLOAK_ADMIN_PASSWORD:}") String adminPassword) {
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.keycloakWebClient = keycloakWebClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
    }

    private static String hashToken(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every JVM.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Equivalent of Python's {@code secrets.token_urlsafe(32)}: 32 random bytes, base64url, no padding. */
    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Keycloak realm base URL (keycloakAuthUrl, e.g. {root}/realms/master). */
    private String realmBaseUrl() {
        return properties.getKeycloakAuthUrl();
    }

    /** Keycloak base URL without the /realms/master suffix, for Admin API calls. */
    private String adminBaseUrl() {
        return realmBaseUrl().replace("/realms/master", "");
    }

    private String getAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", adminUser);
        form.add("password", adminPassword);

        String body = keycloakWebClient.post()
                .uri(realmBaseUrl() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block(ADMIN_TIMEOUT);
        try {
            Map<String, Object> data = objectMapper.readValue(body, MAP_TYPE);
            Object accessToken = data.get("access_token");
            if (accessToken == null) {
                throw new IllegalStateException("No access_token in Keycloak admin token response");
            }
            return accessToken.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to obtain Keycloak admin token: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> findKeycloakUserByEmail(String email) {
        String token = getAdminToken();
        String body = keycloakWebClient.get()
                .uri(adminBaseUrl() + "/admin/realms/master/users?email={email}&exact=true", email)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(String.class)
                .block(ADMIN_TIMEOUT);
        try {
            List<Map<String, Object>> users = objectMapper.readValue(body, USER_LIST_TYPE);
            return (users != null && !users.isEmpty()) ? users.get(0) : null;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Keycloak users response: " + e.getMessage(), e);
        }
    }

    private void resetKeycloakPassword(String userId, String newPassword) {
        String token = getAdminToken();
        Map<String, Object> payload = Map.of(
                "type", "password",
                "value", newPassword,
                "temporary", false);
        keycloakWebClient.put()
                .uri(adminBaseUrl() + "/admin/realms/master/users/{userId}/reset-password", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .block(ADMIN_TIMEOUT);
    }

    /**
     * Begin a password reset. Always returns the generic success response to avoid
     * account enumeration.
     */
    @Transactional
    public PasswordResetResponseData forgotPassword(String rawEmail) {
        String email = (rawEmail == null ? "" : rawEmail).strip().toLowerCase();
        if (email.isEmpty()) {
            return generic();
        }

        try {
            Map<String, Object> user = findKeycloakUserByEmail(email);
            if (user == null || user.get("id") == null) {
                log.info("Password reset requested for unknown email: {}", email);
                return generic();
            }

            // Invalidate any outstanding tokens for this email before issuing a new one.
            // The shared repository only exposes findByTokenHash + standard JpaRepository
            // CRUD, so filter findAll() in-memory rather than redefining the repository.
            Instant now = Instant.now();
            List<PasswordResetToken> outstanding = tokenRepository.findAll().stream()
                    .filter(t -> email.equals(t.getEmail()) && t.getUsedAt() == null)
                    .toList();
            for (PasswordResetToken existing : outstanding) {
                existing.setUsedAt(now);
            }
            tokenRepository.saveAll(outstanding);

            String rawToken = generateRawToken();
            PasswordResetToken prt = new PasswordResetToken();
            prt.setKeycloakUserId(user.get("id").toString());
            prt.setEmail(email);
            prt.setTokenHash(hashToken(rawToken));
            prt.setExpiresAt(now.plus(properties.getPasswordReset().getExpiryMinutes(), ChronoUnit.MINUTES));
            tokenRepository.save(prt);

            String displayName = buildDisplayName(user, email);
            String frontendUrl = stripTrailingSlash(properties.getFrontendUrl());
            String resetLink = frontendUrl + "/reset-password?token=" + rawToken;
            Object userEmail = user.get("email");
            String toEmail = (userEmail != null && !userEmail.toString().isEmpty())
                    ? userEmail.toString() : email;
            emailService.sendPasswordReset(toEmail, displayName, resetLink);
        } catch (Exception e) {
            // never leak failures to the caller
            log.error("Error handling forgot-password for {}: {}", email, e.getMessage());
        }

        return generic();
    }

    /**
     * Complete a password reset using a token from the emailed link.
     */
    @Transactional
    public PasswordResetResponseData resetPassword(String token, String newPassword) {
        if (token == null || token.isEmpty()
                || newPassword == null || newPassword.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Token and a new password of at least 8 characters are required");
        }

        Optional<PasswordResetToken> found = tokenRepository.findByTokenHash(hashToken(token));
        PasswordResetToken prt = found.orElse(null);
        if (prt == null || prt.getUsedAt() != null
                || prt.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired reset link");
        }

        try {
            resetKeycloakPassword(prt.getKeycloakUserId(), newPassword);
        } catch (Exception e) {
            log.error("Failed to reset Keycloak password for {}: {}", prt.getEmail(), e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not reset password. Please try again.");
        }

        prt.setUsedAt(Instant.now());
        tokenRepository.save(prt);

        log.info("Password reset completed for {}", prt.getEmail());
        return new PasswordResetResponseData(true, "Your password has been reset. You can now log in.");
    }

    private static String buildDisplayName(Map<String, Object> user, String email) {
        Object first = user.get("firstName");
        Object last = user.get("lastName");
        StringBuilder sb = new StringBuilder();
        if (first != null && !first.toString().isEmpty()) {
            sb.append(first);
        }
        if (last != null && !last.toString().isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(last);
        }
        return sb.length() > 0 ? sb.toString() : email;
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static PasswordResetResponseData generic() {
        return new PasswordResetResponseData(true, GENERIC_MESSAGE);
    }

    /** Internal carrier so the controller can map to the wire DTO without leaking entities. */
    public record PasswordResetResponseData(boolean success, String message) {
    }
}
