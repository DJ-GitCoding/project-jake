/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.service;

import com.jaddar.dhgroupadmin.entity.AuditLog;
import com.jaddar.dhgroupadmin.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Centralized audit logging service.
 * All CRUD, auth, API, credential, and workflow events go through here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    // ========== Categories ==========
    public static final String CAT_AUTH       = "AUTH";
    public static final String CAT_CRUD       = "CRUD";
    public static final String CAT_API        = "API";
    public static final String CAT_CREDENTIAL = "CREDENTIAL";
    public static final String CAT_WORKFLOW   = "WORKFLOW";
    public static final String CAT_SYSTEM     = "SYSTEM";

    // ========== Results ==========
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILURE = "FAILURE";
    public static final String RESULT_DENIED  = "DENIED";

    // ========== Sources ==========
    public static final String SRC_ADMIN    = "ADMIN_UI";
    public static final String SRC_EXTERNAL = "EXTERNAL_API";
    public static final String SRC_SYSTEM   = "SYSTEM";
    public static final String SRC_AUTH     = "AUTH";

    /**
     * Log an audit event.
     */
    public AuditLog logEvent(String category, String action, String entityType,
                             String entityId, String entityName,
                             String performedBy, String source,
                             String result, String details) {
        AuditLog entry = AuditLog.builder()
                .category(category)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .entityName(entityName)
                .performedBy(performedBy != null ? performedBy : "SYSTEM")
                .source(source != null ? source : SRC_ADMIN)
                .result(result != null ? result : RESULT_SUCCESS)
                .details(details)
                .build();
        entry = auditLogRepository.save(entry);
        log.debug("Audit: [{}] {} {} {} (by {}) — {}",
                category, action, entityType, entityId, performedBy, details);
        return entry;
    }

    // ========== Convenience methods ==========

    public void logAuth(String action, String email, String result, String details) {
        logEvent(CAT_AUTH, action, "USER", null, email, email, SRC_AUTH, result, details);
    }

    public void logCrud(String action, String entityType, String entityId,
                        String entityName, String performedBy, String details) {
        logEvent(CAT_CRUD, action, entityType, entityId, entityName, performedBy, SRC_ADMIN, RESULT_SUCCESS, details);
    }

    public void logWorkflow(String action, String entityType, String entityId,
                            String entityName, String performedBy, String details) {
        logEvent(CAT_WORKFLOW, action, entityType, entityId, entityName, performedBy, SRC_ADMIN, RESULT_SUCCESS, details);
    }

    public void logCredential(String action, String entityType, String entityId,
                              String entityName, String performedBy, String details) {
        logEvent(CAT_CREDENTIAL, action, entityType, entityId, entityName, performedBy, SRC_ADMIN, RESULT_SUCCESS, details);
    }

    public void logApi(String action, String entityType, String entityId,
                       String entityName, String performedBy, String source, String details) {
        logEvent(CAT_API, action, entityType, entityId, entityName, performedBy, source, RESULT_SUCCESS, details);
    }
}
