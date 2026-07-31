/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import com.jaddar.dataholder.entity.FileAttachment;
import com.jaddar.dataholder.entity.RequestAuditLog;
import com.jaddar.dataholder.repository.FileAttachmentRepository;
import com.jaddar.dataholder.repository.RequestAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service for scanning uploaded files for security threats.
 * Implements multiple layers of defense:
 * 1. File extension whitelist
 * 2. MIME type validation and cross-checking
 * 3. Magic byte verification
 * 4. Path traversal detection
 * 5. Content heuristics (embedded scripts, macros)
 * 6. Size limits
 *
 * All threats are logged into the unified RequestAuditLog.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileSecurityService {

    private final RequestAuditLogRepository auditLogRepository;
    private final FileAttachmentRepository fileAttachmentRepository;

    // Maximum file size: 25MB
    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024;

    // Allowed file extensions and their expected MIME types
    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.ofEntries(
        Map.entry("pdf",  Set.of("application/pdf")),
        Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/zip")),
        Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/zip")),
        Map.entry("txt",  Set.of("text/plain", "text/csv", "application/octet-stream")),
        Map.entry("csv",  Set.of("text/csv", "text/plain", "application/octet-stream")),
        Map.entry("json", Set.of("application/json", "text/json", "text/plain", "application/octet-stream")),
        Map.entry("jpeg", Set.of("image/jpeg")),
        Map.entry("jpg",  Set.of("image/jpeg")),
        Map.entry("png",  Set.of("image/png"))
    );

    // Magic bytes for file type verification
    private static final Map<String, byte[][]> MAGIC_BYTES = new HashMap<>() {{
        put("pdf",  new byte[][]{{0x25, 0x50, 0x44, 0x46}}); // %PDF
        put("png",  new byte[][]{{(byte)0x89, 0x50, 0x4E, 0x47}}); // .PNG
        put("jpeg", new byte[][]{{(byte)0xFF, (byte)0xD8, (byte)0xFF}});
        put("jpg",  new byte[][]{{(byte)0xFF, (byte)0xD8, (byte)0xFF}});
        put("docx", new byte[][]{{0x50, 0x4B, 0x03, 0x04}}); // PK (ZIP)
        put("xlsx", new byte[][]{{0x50, 0x4B, 0x03, 0x04}}); // PK (ZIP)
    }};

    // Patterns for detecting path traversal
    private static final Pattern PATH_TRAVERSAL = Pattern.compile(
        "(\\.\\./|\\.\\.\\\\|%2e%2e|%252e%252e|%c0%ae)", Pattern.CASE_INSENSITIVE);

    // Patterns for detecting embedded scripts
    private static final Pattern SCRIPT_PATTERNS = Pattern.compile(
        "(<script|javascript:|vbscript:|on\\w+\\s*=|eval\\(|document\\.|window\\.|" +
        "\\bexec\\b|\\bshell\\b|/bin/|cmd\\.exe|powershell)", Pattern.CASE_INSENSITIVE);

    /**
     * Result of a file security scan
     */
    public record ScanResult(
        boolean safe,
        FileAttachment.ScanStatus status,
        String details,
        String threatType,              // e.g. "MALICIOUS_FILE", "PATH_TRAVERSAL"
        RequestAuditLog.Severity severity
    ) {
        public static ScanResult clean() {
            return new ScanResult(true, FileAttachment.ScanStatus.CLEAN, "All security checks passed", null, null);
        }

        public static ScanResult threat(FileAttachment.ScanStatus status, String details,
                                         String threatType, RequestAuditLog.Severity severity) {
            return new ScanResult(false, status, details, threatType, severity);
        }
    }

    /**
     * Perform a comprehensive security scan on an uploaded file.
     */
    public ScanResult scanFile(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return ScanResult.threat(FileAttachment.ScanStatus.MALICIOUS,
                "File has no filename",
                "SUSPICIOUS_CONTENT", RequestAuditLog.Severity.MEDIUM);
        }

        // 1. Path traversal check
        if (PATH_TRAVERSAL.matcher(originalFilename).find()) {
            return ScanResult.threat(FileAttachment.ScanStatus.MALICIOUS,
                "Path traversal attempt detected in filename: " + originalFilename,
                "PATH_TRAVERSAL", RequestAuditLog.Severity.CRITICAL);
        }

        // Check for null bytes in filename
        if (originalFilename.contains("\0")) {
            return ScanResult.threat(FileAttachment.ScanStatus.MALICIOUS,
                "Null byte injection detected in filename",
                "PATH_TRAVERSAL", RequestAuditLog.Severity.CRITICAL);
        }

        // 2. File extension whitelist
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_TYPES.containsKey(extension)) {
            return ScanResult.threat(FileAttachment.ScanStatus.MALICIOUS,
                "File type not allowed: ." + extension + ". Allowed types: PDF, DOCX, XLSX, TXT, JPEG, PNG",
                "DISALLOWED_FILE_TYPE", RequestAuditLog.Severity.HIGH);
        }

        // 3. File size check
        if (file.getSize() > MAX_FILE_SIZE) {
            return ScanResult.threat(FileAttachment.ScanStatus.MALICIOUS,
                "File exceeds maximum size of 25MB. Size: " + formatSize(file.getSize()),
                "OVERSIZED_FILE", RequestAuditLog.Severity.MEDIUM);
        }

        // 4. MIME type validation
        String contentType = file.getContentType();
        Set<String> allowedMimes = ALLOWED_TYPES.get(extension);
        if (contentType != null && !allowedMimes.contains(contentType.toLowerCase())) {
            return ScanResult.threat(FileAttachment.ScanStatus.MALICIOUS,
                "MIME type mismatch: expected one of " + allowedMimes + 
                " for ." + extension + " but got " + contentType,
                "FILE_EXTENSION_MISMATCH", RequestAuditLog.Severity.HIGH);
        }

        // 5. Magic byte verification
        try {
            byte[] header = new byte[16];
            try (InputStream is = file.getInputStream()) {
                int bytesRead = is.read(header);
                if (bytesRead < 3) {
                    return ScanResult.threat(FileAttachment.ScanStatus.SUSPICIOUS,
                        "File is too small to verify type (only " + bytesRead + " bytes)",
                        "SUSPICIOUS_CONTENT", RequestAuditLog.Severity.MEDIUM);
                }
            }

            byte[][] expectedMagic = MAGIC_BYTES.get(extension);
            if (expectedMagic != null && !extension.equals("txt")) {
                boolean magicMatch = false;
                for (byte[] magic : expectedMagic) {
                    if (startsWith(header, magic)) {
                        magicMatch = true;
                        break;
                    }
                }
                if (!magicMatch) {
                    return ScanResult.threat(FileAttachment.ScanStatus.MALICIOUS,
                        "File magic bytes do not match expected type for ." + extension + 
                        ". Possible polyglot/masquerading file.",
                        "POLYGLOT_FILE", RequestAuditLog.Severity.CRITICAL);
                }
            }
        } catch (Exception e) {
            log.error("Error reading file header for security check", e);
            return ScanResult.threat(FileAttachment.ScanStatus.ERROR,
                "Error reading file for security verification: " + e.getMessage(),
                "SUSPICIOUS_CONTENT", RequestAuditLog.Severity.HIGH);
        }

        // 6. Content heuristics for text-based files
        if (extension.equals("txt") || extension.equals("pdf")) {
            try {
                byte[] content = file.getBytes();
                String textContent;
                if (extension.equals("txt")) {
                    textContent = new String(content, StandardCharsets.UTF_8);
                } else {
                    textContent = new String(content, StandardCharsets.ISO_8859_1);
                }

                if (SCRIPT_PATTERNS.matcher(textContent).find()) {
                    return ScanResult.threat(FileAttachment.ScanStatus.MALICIOUS,
                        "Embedded script or executable content detected in " + extension.toUpperCase() + " file",
                        "EMBEDDED_SCRIPT", RequestAuditLog.Severity.CRITICAL);
                }

                if (extension.equals("pdf")) {
                    if (textContent.contains("/JavaScript") || textContent.contains("/JS ") ||
                        textContent.contains("/Launch") || textContent.contains("/OpenAction")) {
                        return ScanResult.threat(FileAttachment.ScanStatus.MALICIOUS,
                            "PDF contains JavaScript or auto-execute actions",
                            "EMBEDDED_SCRIPT", RequestAuditLog.Severity.CRITICAL);
                    }
                }
            } catch (Exception e) {
                log.warn("Error during content heuristic scan", e);
            }
        }

        // 7. Double extension check (e.g., file.pdf.exe)
        String[] parts = originalFilename.split("\\.");
        if (parts.length > 2) {
            String secondToLast = parts[parts.length - 2].toLowerCase();
            if (Set.of("exe", "bat", "cmd", "ps1", "sh", "vbs", "js", "msi", "dll", "scr")
                    .contains(secondToLast)) {
                return ScanResult.threat(FileAttachment.ScanStatus.MALICIOUS,
                    "Suspicious double extension detected: " + originalFilename,
                    "MALICIOUS_FILE", RequestAuditLog.Severity.CRITICAL);
            }
        }

        return ScanResult.clean();
    }

    /**
     * Log a security threat into the unified audit log.
     */
    public RequestAuditLog logThreat(MultipartFile file, ScanResult result,
                                      HttpServletRequest httpRequest,
                                      String queryType, String queryValue,
                                      String requestorUsername, String requestorEmail,
                                      String requestorSub) {
        String fileHash = computeFileHash(file);

        RequestAuditLog entry = RequestAuditLog.builder()
            .eventType(RequestAuditLog.EventType.SECURITY_THREAT)
            .severity(result.severity())
            .queryType(queryType != null ? queryType : "file_upload")
            .queryValue(queryValue != null ? queryValue : file.getOriginalFilename())
            .requestorUsername(requestorUsername)
            .requestorIp(getClientIp(httpRequest))
            .requestorSub(requestorSub)
            .result("DENIED")
            .resultMessage(result.details())
            .threatType(result.threatType())
            .originalFilename(file.getOriginalFilename())
            .fileType(getFileExtension(file.getOriginalFilename()).toUpperCase())
            .detectedMimeType(file.getContentType())
            .fileSizeBytes(file.getSize())
            .fileHash(fileHash)
            .userAgent(httpRequest.getHeader("User-Agent"))
            .requestHeaders(sanitizeHeaders(httpRequest))
            .build();

        RequestAuditLog saved = auditLogRepository.save(entry);
        log.warn("SECURITY THREAT DETECTED [{}] [severity={}] from IP={}: {}",
            result.threatType(), result.severity(), entry.getRequestorIp(), result.details());

        return saved;
    }

    /**
     * Log a clean file upload into the audit log.
     */
    public void logCleanUpload(MultipartFile file, HttpServletRequest httpRequest,
                                String queryType, String queryValue,
                                String requestorUsername, String requestorSub) {
        RequestAuditLog entry = RequestAuditLog.builder()
            .eventType(RequestAuditLog.EventType.FILE_UPLOAD)
            .severity(RequestAuditLog.Severity.INFO)
            .queryType(queryType != null ? queryType : "file_upload")
            .queryValue(queryValue != null ? queryValue : file.getOriginalFilename())
            .requestorUsername(requestorUsername)
            .requestorIp(getClientIp(httpRequest))
            .requestorSub(requestorSub)
            .result("SUCCESS")
            .resultMessage("File uploaded and passed security scan")
            .originalFilename(file.getOriginalFilename())
            .fileType(getFileExtension(file.getOriginalFilename()).toUpperCase())
            .detectedMimeType(file.getContentType())
            .fileSizeBytes(file.getSize())
            .fileHash(computeFileHash(file))
            .build();

        auditLogRepository.save(entry);
    }

    /**
     * Compute SHA-256 hash of file content
     */
    public String computeFileHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("Failed to compute file hash", e);
            return "unknown";
        }
    }

    public String computeFileHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("Failed to compute file hash", e);
            return "unknown";
        }
    }

    /**
     * Determine the FileType enum from a file extension
     */
    public FileAttachment.FileType resolveFileType(String filename) {
        String ext = getFileExtension(filename).toLowerCase();
        return switch (ext) {
            case "pdf" -> FileAttachment.FileType.PDF;
            case "docx" -> FileAttachment.FileType.DOCX;
            case "xlsx" -> FileAttachment.FileType.XLSX;
            case "txt" -> FileAttachment.FileType.TXT;
            case "jpeg", "jpg" -> FileAttachment.FileType.JPEG;
            case "png" -> FileAttachment.FileType.PNG;
            default -> null;
        };
    }

    // ==================== Helper Methods ====================

    private String getFileExtension(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private String sanitizeHeaders(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        Enumeration<String> headerNames = request.getHeaderNames();
        Set<String> sensitiveHeaders = Set.of("authorization", "cookie", "set-cookie");
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!sensitiveHeaders.contains(name.toLowerCase())) {
                sb.append(name).append(": ").append(request.getHeader(name)).append("\n");
            }
        }
        String result = sb.toString();
        return result.length() > 4000 ? result.substring(0, 4000) : result;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ==================== Convenience Methods for Controllers ====================

    /**
     * Scan a single file and throw SecurityRejectedException if malicious.
     * Call this at the top of any controller method that accepts a MultipartFile.
     * The threat is automatically logged to the audit trail.
     *
     * @param file         the uploaded file
     * @param httpRequest  the HTTP request (for IP, user-agent logging)
     * @param context      description of where the upload came from (e.g. "CSV import", "RDAP file attachment")
     * @throws SecurityRejectedException if the file fails any security check
     */
    public void scanOrReject(MultipartFile file, HttpServletRequest httpRequest, String context) {
        ScanResult result = scanFile(file);
        if (!result.safe()) {
            logThreat(file, result, httpRequest, context, file.getOriginalFilename(), null, null, null);
            throw new SecurityRejectedException(result);
        }
        log.info("File security scan PASSED for '{}' ({}, {}) [context={}]",
                file.getOriginalFilename(), formatSize(file.getSize()), file.getContentType(), context);
    }

    /**
     * Scan multiple files and throw SecurityRejectedException on the first malicious one.
     */
    public void scanAllOrReject(MultipartFile[] files, HttpServletRequest httpRequest, String context) {
        if (files == null) return;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            scanOrReject(file, httpRequest, context);
        }
    }

    /**
     * Exception thrown when a file fails security scanning.
     * Controllers should catch this and return a 400 response with the threat details.
     */
    public static class SecurityRejectedException extends RuntimeException {
        private final ScanResult scanResult;

        public SecurityRejectedException(ScanResult scanResult) {
            super("Security threat detected: " + scanResult.details());
            this.scanResult = scanResult;
        }

        public ScanResult getScanResult() { return scanResult; }
        public String getThreatType() { return scanResult.threatType(); }
        public String getSeverity() { return scanResult.severity() != null ? scanResult.severity().name() : "UNKNOWN"; }
        public String getDetails() { return scanResult.details(); }
    }
}
