/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * File upload security scanning and storage for RDAP request attachments.
 *
 * Ports the file-handling helpers from backend/routers/rdap_routes.py:
 *   _scan_file_security, _process_and_store_file, plus the constants
 *   (allowed types, magic bytes, script patterns, etc.).
 */
@Slf4j
@Service
public class RdapFileService {

    private static final Set<String> ALLOWED_FILE_EXTENSIONS =
            Set.of(".pdf", ".docx", ".xlsx", ".txt", ".jpeg", ".jpg", ".png");

    private static final Map<String, Set<String>> ALLOWED_MIME_TYPES = Map.of(
            ".pdf", Set.of("application/pdf"),
            ".docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip"),
            ".xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip"),
            ".txt", Set.of("text/plain", "text/csv", "application/octet-stream"),
            ".jpeg", Set.of("image/jpeg"),
            ".jpg", Set.of("image/jpeg"),
            ".png", Set.of("image/png")
    );

    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024; // 25 MB

    private static final Map<String, String> MIME_TO_CONTENT_TYPE = Map.of(
            ".pdf", "application/pdf",
            ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ".txt", "text/plain",
            ".jpeg", "image/jpeg",
            ".jpg", "image/jpeg",
            ".png", "image/png"
    );

    private static final Map<String, byte[][]> MAGIC_BYTES = Map.of(
            ".pdf", new byte[][]{"%PDF".getBytes(StandardCharsets.ISO_8859_1)},
            ".png", new byte[][]{new byte[]{(byte) 0x89, 'P', 'N', 'G'}},
            ".jpeg", new byte[][]{new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}},
            ".jpg", new byte[][]{new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}},
            ".docx", new byte[][]{new byte[]{'P', 'K', 0x03, 0x04}},
            ".xlsx", new byte[][]{new byte[]{'P', 'K', 0x03, 0x04}}
    );

    private static final Pattern SCRIPT_PATTERNS = Pattern.compile(
            "(<script|javascript:|vbscript:|on\\w+\\s*=|eval\\(|document\\.|window\\.|"
                    + "\\bexec\\b|\\bshell\\b|/bin/|cmd\\.exe|powershell)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
            "(\\.\\./|\\.\\.\\\\|%2e%2e|%252e%252e|%c0%ae)", Pattern.CASE_INSENSITIVE);

    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "ps1", "sh", "vbs", "js", "msi", "dll", "scr", "com", "pif");

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    @Value("${rdap.file-upload-dir:rdap_uploads}")
    private String uploadDir;

    private Path uploadPath;

    public RdapFileService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() throws IOException {
        this.uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);
    }

    /** Result of a security scan (mirrors the Python dict). */
    public record ScanResult(boolean safe, String threatType, String severity, String detail) {
        static ScanResult ok() {
            return new ScanResult(true, null, null, null);
        }

        static ScanResult fail(String threatType, String severity, String detail) {
            return new ScanResult(false, threatType, severity, detail);
        }
    }

    public ScanResult scanFileSecurity(String filename, byte[] content, String claimedMime) {
        if (filename == null || filename.isEmpty()) {
            return ScanResult.fail("SUSPICIOUS_CONTENT", "MEDIUM", "File has no filename");
        }
        // 1. Path traversal
        if (PATH_TRAVERSAL_PATTERN.matcher(filename).find()) {
            return ScanResult.fail("PATH_TRAVERSAL", "CRITICAL", "Path traversal attempt in filename: " + filename);
        }
        // 2. Null bytes
        if (filename.indexOf('\0') >= 0) {
            return ScanResult.fail("PATH_TRAVERSAL", "CRITICAL", "Null byte injection in filename");
        }
        // 3. Extension whitelist
        String ext = getFileExtension(filename);
        if (!ALLOWED_FILE_EXTENSIONS.contains(ext)) {
            return ScanResult.fail("DISALLOWED_FILE_TYPE", "HIGH",
                    "File type '" + ext + "' not allowed. Allowed: PDF, DOCX, XLSX, TXT, JPEG, PNG");
        }
        // 4. Size
        if (content.length > MAX_FILE_SIZE) {
            double mb = content.length / (1024.0 * 1024.0);
            return ScanResult.fail("OVERSIZED_FILE", "MEDIUM",
                    String.format("File is %.1fMB, exceeds 25MB limit", mb));
        }
        if (content.length == 0) {
            return ScanResult.fail("SUSPICIOUS_CONTENT", "MEDIUM", "File is empty (0 bytes)");
        }
        // 5. MIME type cross-check
        Set<String> allowedMimes = ALLOWED_MIME_TYPES.getOrDefault(ext, Set.of());
        if (claimedMime != null && !claimedMime.isEmpty() && !allowedMimes.isEmpty()
                && !allowedMimes.contains(claimedMime.toLowerCase())) {
            return ScanResult.fail("FILE_EXTENSION_MISMATCH", "HIGH",
                    "MIME type '" + claimedMime + "' does not match extension '" + ext + "'. Expected: " + allowedMimes);
        }
        // 6. Magic byte verification
        byte[][] expectedMagic = MAGIC_BYTES.get(ext);
        if (expectedMagic != null && !ext.equals(".txt")) {
            if (content.length < 4) {
                return ScanResult.fail("SUSPICIOUS_CONTENT", "MEDIUM",
                        "File too small (" + content.length + " bytes) to verify type");
            }
            boolean matched = false;
            for (byte[] magic : expectedMagic) {
                if (startsWith(content, magic)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return ScanResult.fail("POLYGLOT_FILE", "CRITICAL",
                        "Magic bytes don't match '" + ext + "'. File may be masquerading as another type.");
            }
        }
        // 7. Double-extension check
        String[] parts = filename.split("\\.");
        if (parts.length > 2) {
            String secondToLast = parts[parts.length - 2].toLowerCase();
            if (DANGEROUS_EXTENSIONS.contains(secondToLast)) {
                return ScanResult.fail("MALICIOUS_FILE", "CRITICAL", "Suspicious double extension: " + filename);
            }
        }
        // 8. Content heuristics for text-readable files
        if (ext.equals(".txt") || ext.equals(".pdf")) {
            String text = new String(content,
                    ext.equals(".txt") ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
            if (SCRIPT_PATTERNS.matcher(text).find()) {
                return ScanResult.fail("EMBEDDED_SCRIPT", "CRITICAL",
                        "Embedded script or executable content detected in " + ext.substring(1).toUpperCase() + " file");
            }
        }
        // 9. PDF-specific dangerous actions
        if (ext.equals(".pdf")) {
            String pdfText = new String(content, StandardCharsets.ISO_8859_1);
            for (String pattern : List.of("/JavaScript", "/JS ", "/Launch", "/OpenAction", "/AA ", "/RichMedia")) {
                if (pdfText.contains(pattern)) {
                    return ScanResult.fail("EMBEDDED_SCRIPT", "CRITICAL",
                            "PDF contains dangerous action: " + pattern.trim());
                }
            }
        }
        return ScanResult.ok();
    }

    /**
     * Scan a file and store it if clean. Returns metadata on success.
     * Throws ApiException(400) with the threat detail if malicious.
     */
    public Map<String, Object> processAndStoreFile(String filename, byte[] content, String claimedMime) {
        ScanResult scan = scanFileSecurity(filename, content, claimedMime);
        if (!scan.safe()) {
            String fileHash = sha256(content);
            log.warn("SECURITY THREAT BLOCKED | type={} severity={} file={} size={} hash={}... detail={}",
                    scan.threatType(), scan.severity(), filename, content.length,
                    fileHash.substring(0, Math.min(16, fileHash.length())), scan.detail());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("error", "SECURITY_THREAT_DETECTED");
            detail.put("threat_type", scan.threatType());
            detail.put("severity", scan.severity());
            detail.put("message", scan.detail());
            detail.put("filename", filename);
            detail.put("request_denied", true);
            throw new ApiException(HttpStatus.BAD_REQUEST, serialize(detail));
        }

        String ext = getFileExtension(filename);
        String fileId = UUID.randomUUID().toString();
        String storedName = fileId + ext;
        String fileHash = sha256(content);
        String fileType = ext.substring(1).toUpperCase().replace("JPG", "JPEG");

        try {
            Files.write(uploadPath.resolve(storedName), content);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file: " + e.getMessage());
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("fileId", fileId);
        meta.put("originalFilename", filename);
        meta.put("storedFilename", storedName);
        meta.put("fileType", fileType);
        meta.put("mimeType", MIME_TO_CONTENT_TYPE.getOrDefault(ext,
                claimedMime != null ? claimedMime : "application/octet-stream"));
        meta.put("fileSize", content.length);
        meta.put("fileHash", fileHash);
        meta.put("scanStatus", "CLEAN");
        meta.put("uploadedAt", Instant.now().toString());

        try {
            Files.writeString(uploadPath.resolve(fileId + ".json"), serialize(meta));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file metadata: " + e.getMessage());
        }

        log.info("File passed security scan and stored: {} -> {} ({} bytes)", filename, storedName, content.length);
        return meta;
    }

    public Map<String, Object> readMeta(String fileId) {
        Path metaPath = uploadPath.resolve(fileId + ".json");
        if (!Files.exists(metaPath)) {
            return null;
        }
        try {
            return objectMapper.readValue(Files.readString(metaPath), MAP_TYPE);
        } catch (IOException e) {
            return null;
        }
    }

    public Path resolveStored(String storedFilename) {
        return uploadPath.resolve(storedFilename);
    }

    public byte[] readStored(String storedFilename) throws IOException {
        return Files.readAllBytes(uploadPath.resolve(storedFilename));
    }

    // ==================== helpers ====================

    public static String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOfRange(content, 0, magic.length), magic);
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String serialize(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    public Map<String, Object> emptyMeta() {
        return new HashMap<>();
    }
}
