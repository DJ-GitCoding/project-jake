/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.config;

import com.jaddar.dataholder.entity.RequestAuditLog.EventType;
import com.jaddar.dataholder.entity.RequestAuditLog.Severity;
import com.jaddar.dataholder.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Records every state-changing admin request into the audit log, so login and all CRUD/config
 * activity surface in the Audit Logs UI without instrumenting each of the ~100 write endpoints by
 * hand (and future endpoints are covered automatically). Login is handled explicitly in
 * UserAuthController — it has no auth header and needs the email + pass/fail semantics — so it is
 * excluded here to avoid double-logging.
 */
@Component
@RequiredArgsConstructor
public class AuditInterceptor implements HandlerInterceptor {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditService auditService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // Only audit controller invocations, not static resources / error forwards.
        if (!(handler instanceof HandlerMethod)) return;

        String method = request.getMethod();
        String path = request.getRequestURI();
        if (shouldSkip(method, path)) return;

        int status = response.getStatus();
        String result = status < 400
                ? AuditService.RESULT_SUCCESS
                : (status == 401 || status == 403 ? AuditService.RESULT_DENIED : AuditService.RESULT_FAILURE);
        Severity severity = status >= 500 ? Severity.HIGH : (status >= 400 ? Severity.MEDIUM : Severity.INFO);

        String details = method + " " + path + " -> " + status;
        auditService.log(deriveEventType(method, path), severity, method, path, currentActor(), null, result, details);
    }

    private boolean shouldSkip(String method, String path) {
        if (!MUTATING.contains(method)) return true;
        String p = path.toLowerCase();
        // Public / service-to-service and login (login is audited explicitly in the controller).
        if (p.startsWith("/api/agreements/external")) return true;
        if (p.startsWith("/api/oauth")) return true;
        if (p.equals("/api/auth/login") || p.equals("/api/auth/refresh") || p.equals("/api/auth/validate")
                || p.equals("/api/auth/forgot-password") || p.equals("/api/auth/reset-password")) return true;
        // Non-mutating utility POSTs (previews, connection tests, introspection, suggestions, pings).
        if (p.endsWith("/preview") || p.endsWith("/live-preview")) return true;
        if (p.endsWith("/introspect")) return true;
        if (p.endsWith("/test") || p.endsWith("/test-connection")) return true;
        if (p.endsWith("/suggest-mappings")) return true;
        if (p.endsWith("/ping") || p.endsWith("/status-callback")) return true;
        if (p.contains("/files/view") || p.endsWith("/files/upload")) return true; // upload logged by FileSecurityService
        return false;
    }

    private EventType deriveEventType(String method, String path) {
        String p = path.toLowerCase();
        if (p.contains("/auth/users")) {
            return switch (method) {
                case "POST" -> EventType.USER_CREATED;
                case "DELETE" -> EventType.USER_DELETED;
                default -> EventType.USER_UPDATED;
            };
        }
        if (p.endsWith("/logout")) return EventType.LOGOUT;
        if (p.contains("change-password")) return EventType.CONFIG_CHANGED;
        if (p.contains("rule")) { // redaction-rules + file automation-rules
            if ("DELETE".equals(method)) return EventType.RULE_DELETED;
            if ("POST".equals(method)
                    && !p.contains("toggle") && !p.contains("bulk")
                    && !p.contains("initialize") && !p.contains("refresh-cache")) return EventType.RULE_CREATED;
            return EventType.RULE_UPDATED;
        }
        if (p.contains("/review")
                || (p.contains("/subscriptions/") && (p.endsWith("/approve") || p.endsWith("/deny")
                    || p.endsWith("/activate") || p.endsWith("/suspend") || p.endsWith("/reactivate")
                    || p.endsWith("/start-test") || p.endsWith("/run-test")))) {
            return EventType.REQUEST_REVIEWED;
        }
        return EventType.CONFIG_CHANGED;
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            return auth.getName();
        }
        return null;
    }
}
