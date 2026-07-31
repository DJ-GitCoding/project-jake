/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTOs for RDAP request management and audit logging.
 */
public class RdapDto {

    // ==================== Pending Request DTO ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingRequestDto {
        private String requestId;
        private String queryType;
        private String queryValue;
        private Integer requestedAccessLevel;
        private String requestorSub;
        private String requestorUsername;
        private String requestorEmail;
        private List<String> requestorGroups;
        private List<String> agreementNames;
        private String status;
        private String adminNotes;
        private String denialReason;

        // Disclosure flags
        private boolean confidential;
        private boolean exigent;
        private boolean jakeCompliance;

        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private LocalDateTime reviewedAt;
        private String reviewedBy;

        /** RDAP response data — populated for approved requests */
        private Map<String, Object> responseData;

        /** Custom parameters passed with the RDAP request (file references, extra fields) */
        private Map<String, Object> customParams;

        /** File attachments metadata */
        private List<FileAttachmentDto> fileAttachments;
    }

    // ==================== File Attachment DTO ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FileAttachmentDto {
        private String fileId;
        private String originalFilename;
        private String fileType;
        private String mimeType;
        private Long fileSize;
        private String humanReadableSize;
        private String scanStatus;
        private String scanDetails;
        private String uploadedAt;
        private String fileHash;
    }

    // ==================== Audit Log DTO ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AuditLogDto {
        private Long id;
        private String eventType;      // RDAP_QUERY, LOGIN, LOGOUT, SECURITY_THREAT, etc.
        private String severity;       // INFO, LOW, MEDIUM, HIGH, CRITICAL
        private String queryType;
        private String queryValue;
        private String requestorUsername;
        private String requestorIp;
        private List<String> agreementNames;
        private Integer accessLevelRequested;
        private Integer accessLevelGranted;
        private String result;
        private String resultMessage;

        // Disclosure flags
        private boolean confidential;
        private boolean exigent;
        private boolean jakeCompliance;

        private LocalDateTime requestTimestamp;
        private Integer responseTimeMs;

        // Security / file fields
        private String threatType;
        private String originalFilename;
        private String fileType;
        private String detectedMimeType;
        private Long fileSizeBytes;
        private String fileHash;
        private String userAgent;
    }

    // ==================== Review Request DTO ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewRequest {
        /** Action to take: "approve" or "deny" */
        private String action;

        /** Optional admin notes */
        private String adminNotes;

        /** Optional override for granted access level (approve only) */
        private Integer grantedAccessLevel;

        /** Reason for denial — returned to the requestor in the RDAP response if not empty */
        private String denialReason;
    }
}