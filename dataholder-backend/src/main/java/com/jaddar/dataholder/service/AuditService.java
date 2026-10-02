/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.entity.RequestAuditLog;
import com.jaddar.dataholder.entity.RequestAuditLog.EventType;
import com.jaddar.dataholder.entity.RequestAuditLog.Severity;
import com.jaddar.dataholder.repository.RequestAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Central activity/audit logger. Persists rows into {@code request_audit_log} (the same table used
 * for RDAP-query auditing) so login, user, rule, mapping, policy, review, and config changes all
 * surface in the Audit Logs admin UI.
 *
 * <p>The table is RDAP-shaped ({@code query_type}/{@code query_value} are NOT NULL), so for non-RDAP
 * events we repurpose them as {@code (entityType, entityName)}. Persistence failures are swallowed —
 * auditing must never break the business operation being audited.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final RequestAuditLogRepository auditLogRepository;

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILURE = "FAILURE";
    public static final String RESULT_DENIED  = "DENIED";

    /** Core writer. */
    public void log(EventType eventType, Severity severity, String entityType, String entityName,
                    String performedBy, String ip, String result, String details) {
        try {
            auditLogRepository.save(RequestAuditLog.builder()
                    .eventType(eventType != null ? eventType : EventType.CONFIG_CHANGED)
                    .severity(severity != null ? severity : Severity.INFO)
                    .queryType(clamp(nz(entityType, "-"), 100))
                    .queryValue(clamp(nz(entityName, "-"), 255))
                    .requestorUsername(performedBy)
                    .requestorIp(ip != null ? ip : currentIp())
                    .result(clamp(nz(result, RESULT_SUCCESS), 50))
                    .resultMessage(clamp(details, 2000))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to persist audit event {} ({}): {}", eventType, entityName, e.getMessage());
        }
    }

    /** Authentication events (LOGIN, LOGOUT, LOGIN_FAILED). */
    public void logAuth(EventType eventType, String email, String result, String details) {
        Severity sev = eventType == EventType.LOGIN_FAILED ? Severity.MEDIUM : Severity.INFO;
        log(eventType, sev, "AUTH", nz(email, "-"), email, null, result, details);
    }

    /** Best-effort client IP from the current request (X-Forwarded-For aware). */
    private String currentIp() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                String xff = req.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
                return req.getRemoteAddr();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    private static String nz(String s, String d) {
        return (s == null || s.isBlank()) ? d : s;
    }

    private static String clamp(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
