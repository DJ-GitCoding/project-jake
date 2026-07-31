/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.config;

import com.jaddar.service.LoggingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Times each request and logs API requests. Port of {@code main.py :: log_requests}.
 *
 * <p>For paths starting with {@code /api/} (and not ending with {@code /health}) it calls the
 * optional {@link LoggingService} (built by the logging module agent) via an {@link ObjectProvider}
 * so its absence is tolerated. Also emits an Slf4j line for every request.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final ObjectProvider<LoggingService> loggingServiceProvider;

    public RequestLoggingFilter(ObjectProvider<LoggingService> loggingServiceProvider) {
        this.loggingServiceProvider = loggingServiceProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        long start = System.nanoTime();
        String clientIp = resolveClientIp(request);
        try {
            chain.doFilter(request, response);
        } finally {
            double durationMs = (System.nanoTime() - start) / 1_000_000.0;
            String path = request.getRequestURI();
            String method = request.getMethod();
            int status = response.getStatus();

            log.info("{} {} -> {} ({} ms) ip={}", method, path, status,
                    String.format("%.2f", durationMs), clientIp);

            if (path != null && path.startsWith("/api/") && !path.endsWith("/health")) {
                LoggingService loggingService = loggingServiceProvider.getIfAvailable();
                if (loggingService != null) {
                    try {
                        loggingService.logApiRequest(method, path, status, clientIp,
                                Math.round(durationMs * 100.0) / 100.0);
                    } catch (Exception e) {
                        log.warn("LoggingService.logApiRequest failed: {}", e.toString());
                    }
                }
            }
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }
}
