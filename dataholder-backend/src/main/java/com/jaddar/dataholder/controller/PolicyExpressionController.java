/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import com.jaddar.dataholder.dto.PolicyExpressionDto.*;
import com.jaddar.dataholder.service.PolicyExpressionService;
import com.jaddar.dataholder.service.PolicyImportService;
import com.jaddar.dataholder.service.FileSecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/policy-expressions")
@RequiredArgsConstructor
@Slf4j
public class PolicyExpressionController {

    private final PolicyExpressionService policyExpressionService;
    private final PolicyImportService policyImportService;
    private final FileSecurityService fileSecurityService;

    // ==================== CRUD Operations ====================

    /**
     * Get all policy expressions
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllPolicyExpressions(
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        if (page == null) {
            // Backward-compatible: no pagination params -> full list.
            List<PolicyExpressionResponse> policies = activeOnly
                    ? policyExpressionService.getActivePolicyExpressions()
                    : policyExpressionService.getAllPolicyExpressions();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "policyExpressions", policies,
                    "total", policies.size()
            ));
        }

        // activeOnly is an alias for active=true when the explicit active filter is absent.
        Boolean activeFilter = active != null ? active : (activeOnly ? Boolean.TRUE : null);
        Sort sort = Sort.by("asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 500), sort);
        Page<PolicyExpressionResponse> pageResult = policyExpressionService.getPolicyExpressions(search, activeFilter, pageable);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", pageResult.getContent());
        body.put("totalElements", pageResult.getTotalElements());
        body.put("totalPages", pageResult.getTotalPages());
        body.put("page", pageResult.getNumber());
        body.put("size", pageResult.getSize());
        return ResponseEntity.ok(body);
    }

    /**
     * Get a specific policy expression by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPolicyExpression(@PathVariable Long id) {
        return policyExpressionService.getPolicyExpression(id)
                .map(policy -> ResponseEntity.ok(Map.<String, Object>of(
                        "success", true,
                        "policyExpression", policy
                )))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "success", false,
                                "error", "Policy expression not found"
                        )));
    }

    /**
     * Get the default active policy expression
     */
    @GetMapping("/default")
    public ResponseEntity<Map<String, Object>> getDefaultPolicy() {
        return policyExpressionService.getDefaultPolicy()
                .map(policy -> ResponseEntity.ok(Map.<String, Object>of(
                        "success", true,
                        "policyExpression", policy
                )))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "success", false,
                                "error", "No default policy expression found"
                        )));
    }

    /**
     * Create a new policy expression
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createPolicyExpression(
            @RequestBody PolicyExpressionRequest request) {
        try {
            PolicyExpressionResponse created = policyExpressionService.createPolicyExpression(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "policyExpression", created,
                    "message", "Policy expression created successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to create policy expression", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to create the policy expression. Please try again or contact support."
            ));
        }
    }

    /**
     * Update an existing policy expression
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updatePolicyExpression(
            @PathVariable Long id,
            @RequestBody PolicyExpressionRequest request) {
        try {
            PolicyExpressionResponse updated = policyExpressionService.updatePolicyExpression(id, request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "policyExpression", updated,
                    "message", "Policy expression updated successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to update policy expression", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to update the policy expression. Please try again or contact support."
            ));
        }
    }

    /**
     * Delete a policy expression
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deletePolicyExpression(@PathVariable Long id) {
        try {
            policyExpressionService.deletePolicyExpression(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Policy expression deleted successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to delete policy expression", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to delete the policy expression. Please try again or contact support."
            ));
        }
    }

    // ==================== Utility Operations ====================

    /**
     * Set a policy expression as the default
     */
    @PostMapping("/{id}/set-default")
    public ResponseEntity<Map<String, Object>> setAsDefault(@PathVariable Long id) {
        try {
            PolicyExpressionResponse updated = policyExpressionService.setAsDefault(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "policyExpression", updated,
                    "message", "Policy expression set as default"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to set policy expression as default", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to set the policy expression as default. Please try again or contact support."
            ));
        }
    }

    /**
     * Toggle active status of a policy expression
     */
    @PostMapping("/{id}/toggle-active")
    public ResponseEntity<Map<String, Object>> toggleActive(@PathVariable Long id) {
        try {
            PolicyExpressionResponse updated = policyExpressionService.toggleActive(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "policyExpression", updated,
                    "message", "Policy expression active status toggled"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to toggle policy expression active status", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to toggle the active status. Please try again or contact support."
            ));
        }
    }



    /**
     * Get available enum values for legal and protected status
     * Useful for populating dropdowns in the UI
     */
    @GetMapping("/enum-values")
    public ResponseEntity<Map<String, Object>> getEnumValues() {
        PolicyEnumValues enumValues = policyExpressionService.getEnumValues();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "enumValues", enumValues
        ));
    }

    // ==================== JSON Import ====================

    /**
     * Preview what a JSON policy file would produce without persisting.
     * Accepts one or more files via multipart upload.
     */
    @PostMapping("/import/preview")
    public ResponseEntity<Map<String, Object>> previewImport(
            @RequestParam("files") MultipartFile[] files,
            HttpServletRequest httpRequest) {
        try {
            fileSecurityService.scanAllOrReject(files, httpRequest, "Policy import preview");
            var previews = policyImportService.previewImportMultiple(files);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "previews", previews,
                    "total", previews.size()
            ));
        } catch (FileSecurityService.SecurityRejectedException e) {
            log.warn("SECURITY: Policy preview blocked - {}", e.getDetails());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "error", "File rejected: " + e.getDetails(),
                    "securityThreat", true, "threatType", e.getThreatType(), "severity", e.getSeverity()));
        } catch (Exception e) {
            log.error("Failed to preview import", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to preview the import. Please try again or contact support."
            ));
        }
    }

    /**
     * Import one or more JSON policy files.
     * Each file is scanned for security threats before being parsed and persisted.
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importPolicies(
            @RequestParam("files") MultipartFile[] files,
            HttpServletRequest httpRequest) {
        try {
            fileSecurityService.scanAllOrReject(files, httpRequest, "Policy import");
            var results = policyImportService.importFiles(files);

            long successCount = results.stream().filter(PolicyImportService.ImportFileResult::success).count();
            long failureCount = results.size() - successCount;

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "results", results,
                    "imported", successCount,
                    "failed", failureCount,
                    "total", results.size()
            ));
        } catch (FileSecurityService.SecurityRejectedException e) {
            log.warn("SECURITY: Policy import blocked - {}", e.getDetails());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "error", "File rejected: " + e.getDetails(),
                    "securityThreat", true, "threatType", e.getThreatType(), "severity", e.getSeverity()));
        } catch (Exception e) {
            log.error("Failed to import policies", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to import the policies. Please try again or contact support."
            ));
        }
    }
}