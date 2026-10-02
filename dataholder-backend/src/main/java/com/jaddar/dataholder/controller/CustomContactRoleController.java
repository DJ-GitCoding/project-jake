/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import com.jaddar.dataholder.entity.CustomContactRole;
import com.jaddar.dataholder.repository.CustomContactRoleRepository;
import com.jaddar.dataholder.service.CustomContactRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin CRUD for custom contact roles. Secured by the {@code /api/admin/**}
 * authentication rule in SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/custom-roles")
@RequiredArgsConstructor
@Slf4j
public class CustomContactRoleController {

    private final CustomContactRoleService service;
    private final CustomContactRoleRepository repository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "displayName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (page == null) {
            // Backward-compatible: no pagination params -> full list.
            List<CustomContactRole> roles = service.getAll();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "customRoles", roles,
                    "total", roles.size()
            ));
        }
        Sort sort = Sort.by("desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 500), sort);
        String searchTerm = (search != null && !search.isBlank()) ? search.trim() : null;
        Page<CustomContactRole> pageResult = repository.searchAll(searchTerm, pageable);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", pageResult.getContent());
        body.put("totalElements", pageResult.getTotalElements());
        body.put("totalPages", pageResult.getTotalPages());
        body.put("page", pageResult.getNumber());
        body.put("size", pageResult.getSize());
        return ResponseEntity.ok(body);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            CustomContactRole created = service.create(
                    asString(body.get("displayName")),
                    asString(body.get("description")));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "customRole", created,
                    "message", "Custom role created successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create custom role", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "error", "Failed to create the custom role. Please try again or contact support."));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            CustomContactRole updated = service.update(
                    id,
                    asString(body.get("displayName")),
                    asString(body.get("description")),
                    body.containsKey("isActive") ? (Boolean) body.get("isActive") : null);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "customRole", updated,
                    "message", "Custom role updated successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update custom role", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "error", "Failed to update the custom role. Please try again or contact support."));
        }
    }

    @PostMapping("/{id}/toggle-active")
    public ResponseEntity<Map<String, Object>> toggleActive(@PathVariable Long id) {
        try {
            CustomContactRole updated = service.toggleActive(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "customRole", updated,
                    "message", "Custom role active status toggled"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to toggle custom role", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "error", "Failed to toggle the custom role. Please try again or contact support."));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Custom role deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete custom role", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "error", "Failed to delete the custom role. Please try again or contact support."));
        }
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
