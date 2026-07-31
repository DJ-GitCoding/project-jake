/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiting filter.
 * Applies stricter limits to auth endpoints (login) to mitigate brute-force.
 */
@Component
@Order(0)
@Slf4j
public class RateLimitFilter implements Filter {

    private final int generalRpm;
    private final int loginRpm;

    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${rate-limit.requests-per-minute:60}") int generalRpm,
            @Value("${rate-limit.login-attempts-per-minute:10}") int loginRpm) {
        this.generalRpm = generalRpm;
        this.loginRpm = loginRpm;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String ip = getClientIp(request);

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty()) {
            path = path.substring(contextPath.length());
        }

        // Stricter limit for login endpoint
        if (path.startsWith("/api/auth/login") && "POST".equalsIgnoreCase(request.getMethod())) {
            Bucket loginBucket = loginBuckets.computeIfAbsent(ip, k -> createBucket(loginRpm));
            if (!loginBucket.tryConsume(1)) {
                log.warn("Login rate limit exceeded for IP: {}", ip);
                sendRateLimitError(response);
                return;
            }
        }

        // General rate limit for all requests
        Bucket generalBucket = generalBuckets.computeIfAbsent(ip, k -> createBucket(generalRpm));
        if (!generalBucket.tryConsume(1)) {
            log.warn("General rate limit exceeded for IP: {}", ip);
            sendRateLimitError(response);
            return;
        }

        chain.doFilter(req, res);
    }

    private Bucket createBucket(int tokensPerMinute) {
        Bandwidth limit = Bandwidth.classic(tokensPerMinute, Refill.greedy(tokensPerMinute, Duration.ofMinutes(1)));
        return Bucket4j.builder().addLimit(limit).build();
    }

    private void sendRateLimitError(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setHeader("Retry-After", "60");
        response.getWriter().write("{\"success\":false,\"error\":\"Too many requests. Please try again later.\"}");
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
