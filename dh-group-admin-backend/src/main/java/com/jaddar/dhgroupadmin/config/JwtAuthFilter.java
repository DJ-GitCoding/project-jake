/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * JWT authentication filter for the DHG Admin API.
 * Protects /api/admin/** endpoints — requires a valid Bearer token.
 * Passes through /api/auth/**, /api/external/**, and /api/public/** unauthenticated.
 */
@Component
@Order(2)
@Slf4j
public class JwtAuthFilter implements Filter {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final long refreshExpirationMs;

    /** Paths that do NOT require authentication */
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/auth/",
            "/api/external/",
            "/api/public/"
    );

    public JwtAuthFilter(
            @Value("${dhg.jwt.secret}") String secret,
            @Value("${dhg.jwt.expiration-ms:28800000}") long expirationMs,
            @Value("${dhg.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(requireStrongSecret(secret).getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getRequestURI();
        // Strip context path
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty()) {
            path = path.substring(contextPath.length());
        }

        // Allow public paths and OPTIONS (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublicPath(path)) {
            chain.doFilter(req, res);
            return;
        }

        // Only protect /api/admin/** paths
        if (!path.startsWith("/api/admin")) {
            chain.doFilter(req, res);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, 401, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            if (Boolean.TRUE.equals(claims.get("refresh", Boolean.class))) {
                sendError(response, 401, "Invalid token");
                return;
            }

            Integer userType = claims.get("type", Integer.class);
            request.setAttribute("userId", claims.get("userId", Long.class));
            request.setAttribute("userEmail", claims.getSubject());
            request.setAttribute("userType", userType);

            if (requiresMaster(request.getMethod(), path) && (userType == null || userType != 1)) {
                sendError(response, 403, "This action requires master administrator access");
                return;
            }

            chain.doFilter(req, res);
        } catch (ExpiredJwtException e) {
            sendError(response, 401, "Token expired");
        } catch (JwtException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            sendError(response, 401, "Invalid token");
        }
    }

    // ==================== Token Generation ====================

    public String generateAccessToken(Long userId, String email, Integer userType) {
        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("type", userType)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(Long userId, String email) {
        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("refresh", true)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // ==================== Helpers ====================

    private boolean requiresMaster(String method, String path) {
        boolean mutating = !"GET".equalsIgnoreCase(method) && !"OPTIONS".equalsIgnoreCase(method);
        if (path.matches("/api/admin/data-holders/\\d+/(reveal|regenerate)-credentials")) return true;
        if (path.matches("/api/admin/requestor-groups/\\d+/(reveal|regenerate)-credentials")) return true;
        if ("DELETE".equalsIgnoreCase(method)
                && path.matches("/api/admin/(users|data-holders|data-holder-groups)/\\d+")) return true;
        if (mutating && path.startsWith("/api/admin/instances")) return true;
        return false;
    }

    private boolean isPublicPath(String path) {
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        // Exact match for login/auth root
        return "/api/auth".equals(path);
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"error\":\"" + message + "\"}");
    }

    private static String requireStrongSecret(String secret) {
        // HMAC-SHA256 requires at least 32 bytes.
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "dhg.jwt.secret must be at least 32 bytes of high-entropy material; "
                    + "refusing to start with a short/weak signing key.");
        }
        return secret;
    }
}
