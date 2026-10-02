/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "request_audit_log", indexes = {
    @Index(name = "idx_audit_log_event_type", columnList = "event_type"),
    @Index(name = "idx_audit_log_severity", columnList = "severity"),
    @Index(name = "idx_audit_log_timestamp", columnList = "request_timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestAuditLog {

    /**
     * Categorises every log entry so a single table serves as the unified audit trail.
     */
    public enum EventType {
        RDAP_QUERY,          // Normal RDAP lookup
        LOGIN,               // User login
        LOGOUT,              // User logout
        LOGIN_FAILED,        // Failed login attempt
        FILE_UPLOAD,         // File received with a request
        SECURITY_THREAT,     // Malicious file / hacking attempt
        RULE_CREATED,        // Automation rule created
        RULE_UPDATED,        // Automation rule updated
        RULE_DELETED,        // Automation rule deleted
        REQUEST_REVIEWED,    // Manual review of a request
        CONFIG_CHANGED,      // System configuration change
        USER_CREATED,        // User account created
        USER_UPDATED,        // User account updated
        USER_DELETED         // User account deleted
    }

    public enum Severity {
        INFO,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==================== EVENT CLASSIFICATION ====================

    /**
     * Type of event being logged.
     * Legacy rows with NULL are treated as RDAP_QUERY.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 25)
    @Builder.Default
    private EventType eventType = EventType.RDAP_QUERY;

    /**
     * Severity level. INFO for normal operations, higher for threats.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 10)
    @Builder.Default
    private Severity severity = Severity.INFO;

    // ==================== RDAP QUERY FIELDS (original) ====================

    @Column(name = "query_type", nullable = false, length = 100)
    private String queryType;

    @Column(name = "query_value", nullable = false)
    private String queryValue;

    @Column(name = "requestor_sub")
    private String requestorSub;

    @Column(name = "requestor_username")
    private String requestorUsername;

    @Column(name = "requestor_ip")
    private String requestorIp;

    @Column(name = "agreement_names", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] agreementNames;

    @Column(name = "access_level_requested")
    private Integer accessLevelRequested;

    @Column(name = "access_level_granted")
    private Integer accessLevelGranted;

    @Column(nullable = false, length = 50)
    private String result;

    @Column(name = "result_message", length = 2000)
    private String resultMessage;

    // ==================== DISCLOSURE FLAGS ====================

    @Column(name = "confidential", nullable = false)
    @Builder.Default
    private boolean confidential = false;

    @Column(name = "exigent", nullable = false)
    @Builder.Default
    private boolean exigent = false;

    @Column(name = "jake_compliance", nullable = false)
    @Builder.Default
    private boolean jakeCompliance = false;

    // ==================== TIMING ====================

    @Column(name = "request_timestamp")
    private LocalDateTime requestTimestamp;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    // ==================== SECURITY / FILE FIELDS ====================

    /**
     * For SECURITY_THREAT events – the specific threat type detected.
     * e.g. "MALICIOUS_FILE", "PATH_TRAVERSAL", "EMBEDDED_SCRIPT"
     */
    @Column(name = "threat_type", length = 40)
    private String threatType;

    /**
     * Original filename for FILE_UPLOAD / SECURITY_THREAT events
     */
    @Column(name = "original_filename")
    private String originalFilename;

    /**
     * File type detected (PDF, DOCX, etc.)
     */
    @Column(name = "file_type", length = 10)
    private String fileType;

    /**
     * Detected MIME type of uploaded file
     */
    @Column(name = "detected_mime_type", length = 100)
    private String detectedMimeType;

    /**
     * File size in bytes
     */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /**
     * SHA-256 hash of the file
     */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    /**
     * User-Agent header (recorded for security events)
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * Sanitised request headers (security events only)
     */
    @Column(name = "request_headers", columnDefinition = "TEXT")
    private String requestHeaders;

    @PrePersist
    protected void onCreate() {
        if (requestTimestamp == null) {
            requestTimestamp = LocalDateTime.now();
        }
        if (eventType == null) {
            eventType = EventType.RDAP_QUERY;
        }
        if (severity == null) {
            severity = Severity.INFO;
        }
    }
}