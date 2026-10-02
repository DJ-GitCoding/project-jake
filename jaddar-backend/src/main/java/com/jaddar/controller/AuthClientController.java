/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.controller;

import com.jaddar.dto.TokenIntrospectionRequest;
import com.jaddar.dto.TokenIntrospectionResponse;
import com.jaddar.exception.ApiException;
import com.jaddar.service.AuthClientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authentication / token management routes.
 * Ported from backend/routers/auth_routes.py (prefix /api/auth).
 *
 * <p>Builds ONLY the endpoints that auth_routes.py adds on top of the foundation's
 * AuthController: POST /introspect, POST /refresh, GET /health.</p>
 *
 * <p>Client authentication for /introspect uses HTTP Basic Auth (client_id:client_secret),
 * validated against Keycloak — matching authenticate_client() in the Python router.</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthClientController {

    private final AuthClientService authClientService;

    /** Extract client IP from request (mirrors get_client_ip). */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }

    /**
     * Authenticate an OAuth2 client using HTTP Basic Auth (mirrors authenticate_client).
     * The client_id is the username, client_secret is the password.
     *
     * @return the authenticated client_id.
     * @throws ApiException 401 if credentials are missing or invalid.
     */
    private String authenticateClient(HttpServletRequest request, String ipAddress) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            log.warn("Missing/invalid Basic auth header from IP: {}", ipAddress);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid client credentials");
        }

        String clientId;
        String clientSecret;
        try {
            String decoded = new String(
                    Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            int idx = decoded.indexOf(':');
            if (idx < 0) {
                throw new IllegalArgumentException("No ':' separator");
            }
            clientId = decoded.substring(0, idx);
            clientSecret = decoded.substring(idx + 1);
        } catch (IllegalArgumentException e) {
            log.warn("Malformed Basic auth header from IP: {}", ipAddress);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid client credentials");
        }

        log.info("Authenticating client: {} from IP: {}", clientId, ipAddress);

        boolean isValid = authClientService.validateClient(clientId, clientSecret);
        if (!isValid) {
            log.warn("Invalid client credentials for client: {} from IP: {}", clientId, ipAddress);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid client credentials");
        }

        log.info("Client authenticated successfully: {}", clientId);
        return clientId;
    }

    // ------------------------------------------------------------------
    // POST /api/auth/introspect  (RFC 7662)
    // ------------------------------------------------------------------

    /**
     * Token Introspection Endpoint (RFC 7662).
     * Validates a token and returns its active status and metadata.
     * Requires HTTP Basic Auth with client_id:client_secret.
     */
    @PostMapping("/introspect")
    public TokenIntrospectionResponse introspectToken(
            @Valid @RequestBody TokenIntrospectionRequest requestBody,
            HttpServletRequest request) {

        String ipAddress = getClientIp(request);
        String clientId = authenticateClient(request, ipAddress);
        String token = requestBody.getToken();

        log.info("Token introspection requested by client: {} from IP: {}", clientId, ipAddress);

        try {
            Map<String, Object> tokenData = authClientService.introspectToken(token);

            if (tokenData == null) {
                log.info("Token introspection: token is not active");
                return TokenIntrospectionResponse.builder().active(false).build();
            }

            // Check if token is expired.
            Long exp = asLong(tokenData.get("exp"));
            if (exp != null && exp < Instant.now().getEpochSecond()) {
                log.info("Token introspection: token is expired");
                return TokenIntrospectionResponse.builder().active(false).exp(exp).build();
            }

            String tokenSub = asString(tokenData.get("sub"));
            log.info("Token introspection: token is active, sub={}", tokenSub);

            String azp = asString(tokenData.get("azp"));
            String clientIdClaim = azp != null ? azp : asString(tokenData.get("client_id"));

            return TokenIntrospectionResponse.builder()
                    .active(true)
                    .exp(asLong(tokenData.get("exp")))
                    .iat(asLong(tokenData.get("iat")))
                    .sub(tokenSub)
                    .clientId(clientIdClaim)
                    .scope(asString(tokenData.get("scope")))
                    .tokenType("Bearer")
                    .build();

        } catch (JwtException e) {
            // Mirrors Python: ExpiredSignatureError / InvalidTokenError → {"active": false}.
            log.info("Token introspection: invalid/expired token - {}", e.getMessage());
            return TokenIntrospectionResponse.builder().active(false).build();
        } catch (Exception e) {
            log.error("Token introspection error: {}", e.getMessage());
            return TokenIntrospectionResponse.builder().active(false).build();
        }
    }

    // ------------------------------------------------------------------
    // POST /api/auth/refresh
    // ------------------------------------------------------------------

    /**
     * Refresh Access Token Endpoint.
     * Uses the refresh token to obtain a new access token (mirrors refresh_token).
     */
    @PostMapping("/refresh")
    public Map<String, Object> refreshToken(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        String ipAddress = getClientIp(request);

        try {
            String refreshToken = body != null ? asString(body.get("refresh_token")) : null;

            if (refreshToken == null || refreshToken.isBlank()) {
                log.warn("Token refresh failed: no refresh token provided from IP: {}", ipAddress);
                throw new ApiException(HttpStatus.BAD_REQUEST, "Refresh token is required");
            }

            log.info("Attempting token refresh from IP: {}", ipAddress);

            Map<String, Object> tokenData = authClientService.refreshToken(refreshToken);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("access_token", tokenData.get("access_token"));
            out.put("id_token", tokenData.get("id_token"));
            out.put("refresh_token", tokenData.get("refresh_token"));
            out.put("expires_in", tokenData.get("expires_in"));
            out.put("token_type", tokenData.getOrDefault("token_type", "Bearer"));
            return out;

        } catch (ApiException e) {
            throw e;
        } catch (AuthClientService.TokenRefreshException e) {
            log.warn("Token refresh failed from IP: {} - {}", ipAddress, e.getMessage());
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Failed to refresh token. Please log in again.");
        } catch (Exception e) {
            log.error("Token refresh error from IP {}: {}", ipAddress, e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal server error during token refresh");
        }
    }

    // ------------------------------------------------------------------
    // GET /api/auth/health
    // ------------------------------------------------------------------

    /** Health check endpoint for the auth service (mirrors auth_health_check). */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> authHealthCheck() {
        try {
            Map<String, Object> config = authClientService.getOpenidConfiguration();
            boolean hasConfig = config != null && config.get("issuer") != null
                    && !config.get("issuer").toString().isBlank();

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", hasConfig ? "healthy" : "degraded");
            out.put("service", "Authentication");
            out.put("keycloak_connected", hasConfig);
            out.put("metadata_url",
                    authClientService.getPublicAuthUrl() + "/.well-known/openid-configuration");
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            log.error("Auth health check failed", e);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "unhealthy");
            out.put("service", "Authentication");
            out.put("error", "Authentication service health check failed");
            return ResponseEntity.ok(out);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof Instant i) {
            return i.getEpochSecond();
        }
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
