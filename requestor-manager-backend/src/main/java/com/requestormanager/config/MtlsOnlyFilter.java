/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Internal-only paths are refused unless the request arrived on the mTLS listener.
 * Enforced only when {@code mtls.enabled=true}; dual-use (browser-facing) paths are not listed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MtlsOnlyFilter extends OncePerRequestFilter {

    private final MtlsProperties props;

    /** Servlet paths (context path already stripped) that must arrive over mTLS. */
    private static final String[] PROTECTED_PREFIXES = {
            "/api/v1/external/",
            "/api/v1/subscriptions/callback"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (props.isEnabled() && isProtected(request) && request.getLocalPort() != props.getPort()) {
            log.warn("Refusing non-mTLS request to protected path {} on port {}",
                    request.getRequestURI(), request.getLocalPort());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "This endpoint requires mTLS");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isProtected(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String uri = request.getRequestURI();
        for (String prefix : PROTECTED_PREFIXES) {
            if ((servletPath != null && servletPath.startsWith(prefix)) || (uri != null && uri.contains(prefix))) {
                return true;
            }
        }
        return false;
    }
}
