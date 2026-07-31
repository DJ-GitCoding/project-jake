/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import com.jaddar.dataholder.entity.FileAttachment;
import com.jaddar.dataholder.entity.FileAutomationRule;
import com.jaddar.dataholder.repository.FileAttachmentRepository;
import com.jaddar.dataholder.repository.FileAutomationRuleRepository;
import com.jaddar.dataholder.service.AccessControlService;
import com.jaddar.dataholder.service.DocumentScanClient;
import com.jaddar.dataholder.service.FileAutomationService;
import com.jaddar.dataholder.service.FileSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/files")
@RequiredArgsConstructor
@Slf4j
public class FileManagementController {

    private final FileSecurityService fileSecurityService;
    private final FileAutomationService fileAutomationService;
    private final FileAttachmentRepository fileAttachmentRepository;
    private final FileAutomationRuleRepository fileAutomationRuleRepository;
    private final AccessControlService accessControlService;
    private final DocumentScanClient documentScanClient;

    @Value("${dataholder.file.upload-dir:./uploads}")
    private String uploadDir;

    // ==================== File Upload & Viewing ====================

    /**
     * Get file metadata for a specific request
     */
    @GetMapping("/request/{requestId}")
    public ResponseEntity<?> getFilesForRequest(@PathVariable String requestId) {
        try {
            UUID uuid = UUID.fromString(requestId);
            List<FileAttachment> files = fileAttachmentRepository
                .findByRequest_RequestIdOrderByUploadedAtDesc(uuid);
            
            List<Map<String, Object>> fileMaps = files.stream().map(f -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("fileId", f.getFileId().toString());
                map.put("originalFilename", f.getOriginalFilename());
                map.put("fileType", f.getFileType().name());
                map.put("mimeType", f.getMimeType());
                map.put("fileSize", f.getFileSize());
                map.put("humanReadableSize", f.getHumanReadableSize());
                map.put("scanStatus", f.getScanStatus().name());
                map.put("scanDetails", f.getScanDetails());
                map.put("uploadedAt", f.getUploadedAt() != null ? f.getUploadedAt().toString() : null);
                map.put("fileHash", f.getFileHash());
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("success", true, "files", fileMaps));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Invalid request ID"));
        }
    }

    /**
     * Download/view a specific file by its fileId
     */
    @GetMapping("/view/{fileId}")
    public ResponseEntity<?> viewFile(@PathVariable String fileId) {
        try {
            UUID uuid = UUID.fromString(fileId);
            FileAttachment file = fileAttachmentRepository.findByFileId(uuid)
                .orElse(null);
            if (file == null) {
                return ResponseEntity.notFound().build();
            }

            // Only serve clean or pending files
            if (file.getScanStatus() == FileAttachment.ScanStatus.MALICIOUS) {
                return ResponseEntity.status(403).body(Map.of(
                    "error", "This file has been flagged as malicious and cannot be viewed"));
            }

            Path filePath = Paths.get(uploadDir, file.getStoragePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "File not found on disk"));
            }

            Resource resource = new FileSystemResource(filePath);
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "inline; filename=\"" + file.getOriginalFilename() + "\"")
                .header("X-File-Type", file.getFileType().name())
                .header("X-Scan-Status", file.getScanStatus().name())
                .body(resource);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file ID"));
        }
    }

    /**
     * Get a file's metadata only (no content)
     */
    @GetMapping("/metadata/{fileId}")
    public ResponseEntity<?> getFileMetadata(@PathVariable String fileId) {
        try {
            UUID uuid = UUID.fromString(fileId);
            return fileAttachmentRepository.findByFileId(uuid)
                .map(f -> {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    meta.put("fileId", f.getFileId().toString());
                    meta.put("originalFilename", f.getOriginalFilename());
                    meta.put("fileType", f.getFileType().name());
                    meta.put("mimeType", f.getMimeType());
                    meta.put("fileSize", f.getFileSize());
                    meta.put("humanReadableSize", f.getHumanReadableSize());
                    meta.put("scanStatus", f.getScanStatus().name());
                    meta.put("scanDetails", f.getScanDetails());
                    meta.put("uploadedAt", f.getUploadedAt() != null ? f.getUploadedAt().toString() : null);
                    meta.put("fileHash", f.getFileHash());
                    return ResponseEntity.ok(Map.of("success", true, "file", meta));
                })
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid file ID"));
        }
    }

    // ==================== File Automation Rules ====================

    @GetMapping("/automation-rules")
    public ResponseEntity<?> getAutomationRules(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "priority") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (page == null) {
            // Backward-compatible: no pagination params -> full list.
            List<FileAutomationRule> rules = fileAutomationService.getAllRules();
            return ResponseEntity.ok(Map.of("success", true, "rules", rules));
        }
        Sort sort = Sort.by("desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 500), sort);
        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;
        Page<FileAutomationRule> pageResult = fileAutomationRuleRepository.searchAll(searchTerm, pageable);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", pageResult.getContent());
        body.put("totalElements", pageResult.getTotalElements());
        body.put("totalPages", pageResult.getTotalPages());
        body.put("page", pageResult.getNumber());
        body.put("size", pageResult.getSize());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/automation-rules/{id}")
    public ResponseEntity<?> getAutomationRule(@PathVariable Long id) {
        return fileAutomationService.getRule(id)
            .map(rule -> ResponseEntity.ok(Map.of("success", true, "rule", rule)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/automation-rules")
    public ResponseEntity<?> createAutomationRule(@RequestBody FileAutomationRule rule) {
        try {
            FileAutomationRule created = fileAutomationService.createRule(rule);
            return ResponseEntity.ok(Map.of("success", true, "rule", created));
        } catch (Exception e) {
            log.error("Failed to create automation rule", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Failed to create the automation rule. Please check your input and try again."));
        }
    }

    @PutMapping("/automation-rules/{id}")
    public ResponseEntity<?> updateAutomationRule(@PathVariable Long id, @RequestBody FileAutomationRule rule) {
        try {
            FileAutomationRule updated = fileAutomationService.updateRule(id, rule);
            return ResponseEntity.ok(Map.of("success", true, "rule", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/automation-rules/{id}")
    public ResponseEntity<?> deleteAutomationRule(@PathVariable Long id) {
        try {
            fileAutomationService.deleteRule(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Rule deleted"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/automation-rules/{id}/toggle")
    public ResponseEntity<?> toggleAutomationRule(@PathVariable Long id) {
        try {
            FileAutomationRule toggled = fileAutomationService.toggleRule(id);
            return ResponseEntity.ok(Map.of("success", true, "rule", toggled));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== Direct File Upload (with security scanning) ====================

    /**
     * Upload a file directly to the data holder.
     * The file is scanned for security threats before being stored.
     * This endpoint is for requestor apps that communicate directly with the data holder
     * rather than going through the intermediary Python backend.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "requestorGroupCode", required = false) String requestorGroupCode,
            @RequestParam(value = "requestTypeCode", required = false) Integer requestTypeCode,
            @RequestParam(value = "paramName", required = false) String paramName,
            HttpServletRequest httpRequest) {
        try {
            // Security scan — rejects and logs if malicious
            fileSecurityService.scanOrReject(file, httpRequest, "Direct file upload");

            /* Format scan — validate the document against the criteria the data holder group
             * admin authored on this request type's file parameter. Criteria are resolved live
             * from the group admin, so there is no local copy to keep in sync. */
            Map<String, Object> criteria =
                    accessControlService.resolveFileCriteria(requestorGroupCode, requestTypeCode, paramName);
            DocumentScanClient.ScanVerdict verdict = documentScanClient.scan(file, criteria);
            if (!verdict.passed()) {
                log.warn("File {} rejected by format scan for param '{}': {}",
                        file.getOriginalFilename(), paramName, verdict.failures());
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "The uploaded file does not meet the required format for \""
                                + (paramName != null ? paramName : "this attachment") + "\".",
                        "failures", verdict.failures()));
            }

            // File passed scanning — store it
            String fileHash = fileSecurityService.computeFileHash(file);
            FileAttachment.FileType fileType = fileSecurityService.resolveFileType(file.getOriginalFilename());
            if (fileType == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "error", "Unsupported file type. Allowed: PDF, DOCX, XLSX, TXT, JPEG, PNG"));
            }

            UUID fileId = UUID.randomUUID();
            String ext = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.'));
            String storedName = fileId + ext.toLowerCase();
            Path storagePath = Paths.get(uploadDir, storedName);
            Files.createDirectories(storagePath.getParent());
            Files.write(storagePath, file.getBytes());

            FileAttachment attachment = FileAttachment.builder()
                    .fileId(fileId)
                    .originalFilename(file.getOriginalFilename())
                    .storedFilename(storedName)
                    .fileType(fileType)
                    .mimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .fileSize(file.getSize())
                    .fileHash(fileHash)
                    .storagePath(storedName)
                    .scanStatus(FileAttachment.ScanStatus.CLEAN)
                    .scanDetails(verdict.skipped()
                            ? "Passed security checks; format not verified (" + verdict.details() + ")"
                            : "Passed security and format checks — " + verdict.details())
                    .scannedAt(LocalDateTime.now())
                    .build();
            fileAttachmentRepository.save(attachment);

            log.info("File uploaded and stored: {} -> {} ({} bytes, hash={})",
                    file.getOriginalFilename(), storedName, file.getSize(), fileHash.substring(0, 16));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "fileId", fileId.toString(),
                    "originalFilename", file.getOriginalFilename(),
                    "fileType", fileType.name(),
                    "mimeType", attachment.getMimeType(),
                    "fileSize", file.getSize(),
                    "fileHash", fileHash,
                    "scanStatus", "CLEAN"
            ));
        } catch (FileSecurityService.SecurityRejectedException e) {
            log.warn("SECURITY: Direct upload blocked - {}", e.getDetails());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "File rejected: " + e.getDetails(),
                    "securityThreat", true,
                    "threatType", e.getThreatType(),
                    "severity", e.getSeverity(),
                    "filename", file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown"
            ));
        } catch (Exception e) {
            log.error("File upload failed", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "The file could not be uploaded. Please try again or contact support."));
        }
    }
}
