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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores metadata about files attached to RDAP requests.
 * Actual file content is stored on disk; this entity tracks the reference.
 * 
 * Supported file types: PDF, Word (DOCX), Excel (XLSX), TXT, JPEG, PNG
 */
@Entity
@Table(name = "file_attachments", indexes = {
    @Index(name = "idx_file_attachment_request_id", columnList = "request_id"),
    @Index(name = "idx_file_attachment_file_type", columnList = "file_type"),
    @Index(name = "idx_file_attachment_scan_status", columnList = "scan_status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileAttachment {

    public enum FileType {
        PDF,
        DOCX,
        XLSX,
        TXT,
        JPEG,
        PNG
    }

    public enum ScanStatus {
        PENDING,    // Not yet scanned
        CLEAN,      // Passed all security checks
        SUSPICIOUS, // Flagged but not confirmed malicious
        MALICIOUS,  // Confirmed malicious - request should be denied
        ERROR       // Scan failed
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique file identifier for retrieval
     */
    @Column(name = "file_id", nullable = false, unique = true)
    private UUID fileId;

    /**
     * Reference to the pending request this file belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private PendingRequest request;

    /**
     * Original filename as uploaded
     */
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    /**
     * Sanitized filename stored on disk
     */
    @Column(name = "stored_filename", nullable = false)
    private String storedFilename;

    /**
     * Detected file type
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private FileType fileType;

    /**
     * Detected MIME type
     */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /**
     * File size in bytes
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * SHA-256 hash of file content
     */
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    /**
     * Path to stored file on disk (relative to upload root)
     */
    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    /**
     * Security scan status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 15)
    @Builder.Default
    private ScanStatus scanStatus = ScanStatus.PENDING;

    /**
     * Details from the security scan
     */
    @Column(name = "scan_details", length = 2000)
    private String scanDetails;

    /**
     * When the file was uploaded
     */
    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    /**
     * When the file was last scanned
     */
    @Column(name = "scanned_at")
    private LocalDateTime scannedAt;

    @PrePersist
    protected void onCreate() {
        if (fileId == null) {
            fileId = UUID.randomUUID();
        }
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }

    /**
     * Human-readable file size
     */
    public String getHumanReadableSize() {
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        return String.format("%.1f MB", fileSize / (1024.0 * 1024));
    }
}
