/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;

import java.util.Locale;

/**
 * Resolves the path Spring MVC will actually route a request on, so that filters
 * which gate access by path prefix agree with the controller mapping.
 *
 * <p>{@link HttpServletRequest#getRequestURI()} is the raw request line: percent-encoding
 * is not decoded and matrix parameters ({@code ;key=value}) are kept. Spring MVC routes on
 * a decoded path with matrix parameters removed. Comparing the raw form against a prefix
 * therefore lets {@code /api/%61dmin/users} or {@code /api;x/admin/users} slip past a
 * {@code startsWith("/api/admin")} check while still reaching the admin controller.
 *
 * <p>This helper produces the same view MVC uses, and refuses anything that cannot be
 * normalised unambiguously (encoded slashes, encoded percent signs, dot segments, control
 * characters, malformed encoding). Callers must reject a request when it returns
 * {@code null} rather than fall through to a permissive default.
 */
public final class RequestPaths {

    private RequestPaths() {
    }

    /**
     * @return the decoded, context-path-relative, matrix-parameter-free path, always
     *         starting with {@code /}; or {@code null} when the request path is malformed
     *         or contains constructs whose routing outcome is ambiguous.
     */
    public static String normalized(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        String lower = uri.toLowerCase(Locale.ROOT);
        if (uri.indexOf('\\') >= 0
                || lower.contains("%2f")   // encoded slash
                || lower.contains("%5c")   // encoded backslash
                || lower.contains("%25")   // encoded percent (double encoding)
                || lower.contains("%00")) { // encoded NUL
            return null;
        }

        RequestPath parsed;
        try {
            parsed = RequestPath.parse(uri, request.getContextPath());
        } catch (RuntimeException e) {
            return null;
        }

        StringBuilder out = new StringBuilder(uri.length());
        for (PathContainer.Element element : parsed.pathWithinApplication().elements()) {
            if (element instanceof PathContainer.PathSegment segment) {
                String value = segment.valueToMatch();
                if (value.isEmpty() || ".".equals(value) || "..".equals(value)) {
                    return null;
                }
                for (int i = 0; i < value.length(); i++) {
                    char c = value.charAt(i);
                    if (c < 0x20 || c == 0x7f || c == '/' || c == '\\') {
                        return null;
                    }
                }
                out.append(value);
            } else {
                out.append('/');
            }
        }

        String path = out.toString();
        if (path.isEmpty()) {
            return "/";
        }
        if (path.charAt(0) != '/' || path.contains("//")) {
            return null;
        }
        return path;
    }
}
