/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.controller;

import com.requestormanager.dto.ApiResponse;
import com.requestormanager.dto.DataHolderGroupDto;
import com.requestormanager.dto.PagedResponse;
import com.requestormanager.entity.GroupDirectorySettings;
import com.requestormanager.security.SecurityUtils;
import com.requestormanager.service.DataHolderGroupClientService;
import com.requestormanager.service.DataHolderGroupService;
import com.requestormanager.service.GroupDirectoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

/**
 * REST controller for managing Data Holder Groups and their external API interactions.
 */
@RestController
@RequestMapping("/api/data-holder-groups")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Data Holder Groups", description = "Manage data holder group registrations and interactions")
public class DataHolderGroupController {

    private final DataHolderGroupService dataHolderGroupService;
    private final DataHolderGroupClientService clientService;
    private final GroupDirectoryService directoryService;
    private final SecurityUtils securityUtils;

    // ========== CRUD Operations ==========

    @GetMapping
    @Operation(summary = "Get all data holder groups",
               description = "Pass 'page' to receive a paginated PagedResponse (with optional 'search' " +
                           "over name/code/description); omit 'page' for the full list. " +
                           "'activeOnly' restricts to active groups in both modes.")
    public ResponseEntity<ApiResponse<?>> getAllDataHolderGroups(
            @RequestParam(required = false, defaultValue = "false") Boolean activeOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (page == null) {
            // Backward-compatible: no pagination params -> full list.
            List<DataHolderGroupDto.Response> dataHolderGroups = activeOnly
                    ? dataHolderGroupService.getActiveDataHolderGroups()
                    : dataHolderGroupService.getAllDataHolderGroups();
            return ResponseEntity.ok(ApiResponse.success(dataHolderGroups));
        }
        Sort sort = Sort.by("desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 500), sort);
        PagedResponse<DataHolderGroupDto.Response> paged = PagedResponse.of(
                dataHolderGroupService.getDataHolderGroups(search, Boolean.TRUE.equals(activeOnly), pageable));
        return ResponseEntity.ok(ApiResponse.success(paged));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active data holder groups only")
    public ResponseEntity<ApiResponse<List<DataHolderGroupDto.Response>>> getActiveDataHolderGroups() {
        return ResponseEntity.ok(ApiResponse.success(dataHolderGroupService.getActiveDataHolderGroups()));
    }

    @GetMapping("/healthy")
    @Operation(summary = "Get healthy data holder groups only")
    public ResponseEntity<ApiResponse<List<DataHolderGroupDto.Response>>> getHealthyDataHolderGroups() {
        return ResponseEntity.ok(ApiResponse.success(dataHolderGroupService.getHealthyDataHolderGroups()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get data holder group by ID")
    public ResponseEntity<ApiResponse<DataHolderGroupDto.Response>> getDataHolderGroup(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(dataHolderGroupService.getDataHolderGroup(id)));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get data holder group by code")
    public ResponseEntity<ApiResponse<DataHolderGroupDto.Response>> getDataHolderGroupByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(dataHolderGroupService.getDataHolderGroupByCode(code)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('jaddar_master_admin', 'group_admin', 'ROLE_jaddar_master_admin', 'ROLE_group_admin')")
    @Operation(summary = "Create a new data holder group")
    public ResponseEntity<ApiResponse<DataHolderGroupDto.Response>> createDataHolderGroup(
            @Valid @RequestBody DataHolderGroupDto.CreateRequest request) {
        DataHolderGroupDto.Response created = dataHolderGroupService.createDataHolderGroup(request);
        return ResponseEntity.ok(ApiResponse.success("Data holder created successfully", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('jaddar_master_admin', 'group_admin', 'ROLE_jaddar_master_admin', 'ROLE_group_admin')")
    @Operation(summary = "Update a data holder group")
    public ResponseEntity<ApiResponse<DataHolderGroupDto.Response>> updateDataHolderGroup(
            @PathVariable Long id,
            @Valid @RequestBody DataHolderGroupDto.UpdateRequest request) {
        DataHolderGroupDto.Response updated = dataHolderGroupService.updateDataHolderGroup(id, request);
        return ResponseEntity.ok(ApiResponse.success("Data holder updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('jaddar_master_admin', 'ROLE_jaddar_master_admin')")
    @Operation(summary = "Delete a data holder group")
    public ResponseEntity<ApiResponse<Void>> deleteDataHolderGroup(@PathVariable Long id) {
        dataHolderGroupService.deleteDataHolderGroup(id);
        return ResponseEntity.ok(ApiResponse.success("Data holder deleted successfully", null));
    }

    @PostMapping("/{id}/toggle-active")
    @PreAuthorize("hasAnyAuthority('jaddar_master_admin', 'group_admin', 'ROLE_jaddar_master_admin', 'ROLE_group_admin')")
    @Operation(summary = "Toggle data holder group active status")
    public ResponseEntity<ApiResponse<DataHolderGroupDto.Response>> toggleActive(@PathVariable Long id) {
        DataHolderGroupDto.Response updated = dataHolderGroupService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success(
                "Data holder " + (updated.getActive() ? "activated" : "deactivated"), 
                updated));
    }

    // ========== Common Repository Directory ==========

    @GetMapping("/directory/settings")
    @Operation(summary = "Common repository settings and the outcome of the last sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDirectorySettings() {
        return ResponseEntity.ok(ApiResponse.success(toDirectorySettings(directoryService.getSettings())));
    }

    @PutMapping("/directory/settings")
    @PreAuthorize("hasAnyAuthority('jaddar_master_admin', 'group_admin', 'ROLE_jaddar_master_admin', 'ROLE_group_admin')")
    @Operation(summary = "Point this deployment at a common repository, or turn it off")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateDirectorySettings(
            @RequestBody DirectorySettingsRequest request) {
        String actor = securityUtils.getCurrentUserEmail().orElse("admin");
        GroupDirectorySettings settings =
                directoryService.updateSettings(request.getEnabled(), request.getRegistryUrl(), actor);
        if (Boolean.TRUE.equals(settings.getEnabled())) {
            settings = directoryService.refresh(true);
            directoryService.syncToLocal();
        }
        return ResponseEntity.ok(ApiResponse.success("Common repository settings saved",
                toDirectorySettings(settings)));
    }

    @PostMapping("/directory/refresh")
    @PreAuthorize("hasAnyAuthority('jaddar_master_admin', 'group_admin', 'ROLE_jaddar_master_admin', 'ROLE_group_admin')")
    @Operation(summary = "Re-fetch the common repository's data holder group feed")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refreshDirectory() {
        GroupDirectorySettings settings = directoryService.refresh(true);
        directoryService.syncToLocal();
        return ResponseEntity.ok(ApiResponse.success(toDirectorySettings(settings)));
    }

    @GetMapping("/directory")
    @Operation(summary = "Data holder group registries published by the common repository",
               description = "Each row carries that registry's data holder groups and their "
                           + "permitted templates, and says whether it is already registered here.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listDirectory() {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("enabled", directoryService.isEnabled());
        body.put("settings", toDirectorySettings(directoryService.getSettings()));
        body.put("registries", directoryService.listForAdmin());
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    private Map<String, Object> toDirectorySettings(GroupDirectorySettings settings) {
        Map<String, Object> r = new java.util.LinkedHashMap<>();
        r.put("enabled", Boolean.TRUE.equals(settings.getEnabled()));
        r.put("registryUrl", settings.getRegistryUrl());
        r.put("lastSyncedAt", settings.getLastSyncedAt());
        r.put("lastSyncStatus", settings.getLastSyncStatus());
        r.put("lastSyncMessage", settings.getLastSyncMessage());
        r.put("registryCount", settings.getRegistryCount());
        r.put("groupCount", settings.getGroupCount());
        r.put("feedPublication", settings.getFeedPublication());
        r.put("updatedBy", settings.getUpdatedBy());
        r.put("updatedAt", settings.getUpdatedAt());
        return r;
    }
    /** Body of the directory settings update. */
    @lombok.Getter
    @lombok.Setter
    public static class DirectorySettingsRequest {
        private Boolean enabled;
        private String registryUrl;
    }

    // ========== Health Check Operations ==========

    @GetMapping("/{id}/health")
    @Operation(summary = "Check health of a specific data holder group")
    public ResponseEntity<ApiResponse<DataHolderGroupDto.HealthResponse>> checkHealth(@PathVariable Long id) {
        DataHolderGroupDto.HealthResponse health = dataHolderGroupService.checkHealth(id);
        return ResponseEntity.ok(ApiResponse.success(health));
    }

    @PostMapping("/health-check-all")
    @PreAuthorize("hasAnyAuthority('jaddar_master_admin', 'group_admin', 'ROLE_jaddar_master_admin', 'ROLE_group_admin')")
    @Operation(summary = "Check health of all active data holder groups")
    public ResponseEntity<ApiResponse<List<DataHolderGroupDto.HealthResponse>>> checkAllHealth() {
        List<DataHolderGroupDto.HealthResponse> results = dataHolderGroupService.checkAllHealth();
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    // ========== Template Operations ==========

    @GetMapping("/{id}/templates")
    @Operation(summary = "Get available templates from a data holder group")
    public ResponseEntity<ApiResponse<List<DataHolderGroupDto.TemplateInfo>>> getTemplates(@PathVariable Long id) {
        List<DataHolderGroupDto.TemplateInfo> templates = dataHolderGroupService.getTemplates(id);
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    @GetMapping("/templates")
    @Operation(summary = "Get all templates from all active data holder groups")
    public ResponseEntity<ApiResponse<List<DataHolderGroupDto.TemplateInfo>>> getAllTemplates() {
        List<DataHolderGroupDto.TemplateInfo> templates = dataHolderGroupService.getAllTemplates();
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    // ========== Agreement Operations ==========

    /** Accept or decline a template change the group admin proposed for one subscription. */
    @PostMapping("/{id}/subscriptions/{requestId}/pending-change")
    @PreAuthorize("hasAnyAuthority('jaddar_master_admin', 'group_admin', 'requestor_group_admin', 'ROLE_jaddar_master_admin', 'ROLE_group_admin', 'ROLE_requestor_group_admin')")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Map<String, Object>>> respondToPendingChange(
            @PathVariable Long id,
            @PathVariable String requestId,
            @RequestParam boolean accept,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> values = body != null && body.get("subscriptionFieldValues") instanceof Map<?, ?> raw
                ? new java.util.LinkedHashMap<String, Object>((Map<String, Object>) raw)
                : null;
        Map<String, Object> result = clientService.respondToPendingChange(id, requestId, accept, values);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/initiate")
    @PreAuthorize("hasAnyAuthority('jaddar_master_admin', 'group_admin', 'requestor_group_admin', 'ROLE_jaddar_master_admin', 'ROLE_group_admin', 'ROLE_requestor_group_admin')")
    @Operation(summary = "Initiate an agreement with a data holder group")
    public ResponseEntity<ApiResponse<DataHolderGroupDto.InitiationResponse>> initiateAgreement(
            @PathVariable Long id,
            @Valid @RequestBody DataHolderGroupDto.InitiationRequest request) {
        DataHolderGroupDto.InitiationResponse response = clientService.initiateAgreement(id, request);
        if (response.getSuccess()) {
            return ResponseEntity.ok(ApiResponse.success("Agreement initiation successful", response));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error(response.getMessage()));
        }
    }

    @GetMapping("/{id}/status/{requestId}")
    @Operation(summary = "Check status of an agreement request")
    public ResponseEntity<ApiResponse<DataHolderGroupDto.StatusResponse>> getAgreementStatus(
            @PathVariable Long id,
            @PathVariable String requestId) {
        clientService.requireSubscriptionAccess(requestId, false);
        return clientService.getAgreementStatus(id, requestId)
                .map(status -> ResponseEntity.ok(ApiResponse.success(status)))
                .orElseGet(() -> ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Agreement request not found")));
    }
}