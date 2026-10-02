/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.controller;

import com.requestormanager.dto.ApiResponse;
import com.requestormanager.dto.PagedResponse;
import com.requestormanager.dto.RequestorGroupDto;
import com.requestormanager.service.RequestorGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/requestor-groups")
@RequiredArgsConstructor
@Tag(name = "Requestor Groups", description = "Requestor group management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class RequestorGroupController {
    
    private final RequestorGroupService requestorGroupService;
    
    @PostMapping
    @Operation(summary = "Create requestor group", description = "Create a new requestor group (Master and Admin only)")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Requestor group created successfully",
            content = @Content(schema = @Schema(implementation = RequestorGroupResponseWrapper.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    public ResponseEntity<ApiResponse<RequestorGroupDto.RequestorGroupResponse>> createRequestorGroup(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Requestor group creation request",
                required = true,
                content = @Content(schema = @Schema(implementation = RequestorGroupDto.CreateRequest.class))
            )
            @Valid @RequestBody RequestorGroupDto.CreateRequest request) {
        RequestorGroupDto.RequestorGroupResponse response = requestorGroupService.createRequestorGroup(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Requestor group created successfully", response));
    }
    
    @GetMapping("/introspection-default")
    @Operation(summary = "Get the environment default introspection URL",
               description = "Returns this deployment's Keycloak token-introspection endpoint, used to "
                           + "pre-fill a new requestor group's introspection URL.")
    public ResponseEntity<ApiResponse<String>> getDefaultIntrospectionUrl() {
        return ResponseEntity.ok(
                ApiResponse.success("Default introspection URL", requestorGroupService.getDefaultIntrospectionUrl()));
    }

    @GetMapping
    @Operation(summary = "Get all requestor groups",
               description = "Get all requestor groups based on current user's permissions. " +
                           "Pass 'page' to receive a paginated PagedResponse (with optional 'search' " +
                           "over name/code/description); omit 'page' for the full list.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Requestor groups retrieved successfully",
            content = @Content(schema = @Schema(implementation = RequestorGroupListResponseWrapper.class))
        )
    })
    public ResponseEntity<ApiResponse<?>> getAllRequestorGroups(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        if (page == null) {
            // Backward-compatible: no pagination params -> full list.
            List<RequestorGroupDto.RequestorGroupResponse> groups = requestorGroupService.getAllRequestorGroups();
            return ResponseEntity.ok(ApiResponse.success(groups));
        }
        Sort sort = Sort.by("desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 500), sort);
        PagedResponse<RequestorGroupDto.RequestorGroupResponse> paged = PagedResponse.of(
                requestorGroupService.getAllRequestorGroups(search, pageable));
        return ResponseEntity.ok(ApiResponse.success(paged));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get requestor group by ID", description = "Get a specific requestor group by ID")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Requestor group retrieved successfully",
            content = @Content(schema = @Schema(implementation = RequestorGroupResponseWrapper.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Requestor group not found",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    public ResponseEntity<ApiResponse<RequestorGroupDto.RequestorGroupResponse>> getRequestorGroupById(
            @PathVariable Long id) {
        RequestorGroupDto.RequestorGroupResponse group = requestorGroupService.getRequestorGroupById(id);
        return ResponseEntity.ok(ApiResponse.success(group));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update requestor group", description = "Update an existing requestor group (Master and Admin only)")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Requestor group updated successfully",
            content = @Content(schema = @Schema(implementation = RequestorGroupResponseWrapper.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Requestor group not found",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    public ResponseEntity<ApiResponse<RequestorGroupDto.RequestorGroupResponse>> updateRequestorGroup(
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Requestor group update request",
                required = true,
                content = @Content(schema = @Schema(implementation = RequestorGroupDto.UpdateRequest.class))
            )
            @Valid @RequestBody RequestorGroupDto.UpdateRequest request) {
        RequestorGroupDto.RequestorGroupResponse response = requestorGroupService.updateRequestorGroup(id, request);
        return ResponseEntity.ok(ApiResponse.success("Requestor group updated successfully", response));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete requestor group", description = "Delete a requestor group (Master and Admin only)")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Requestor group deleted successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Requestor group not found",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    public ResponseEntity<ApiResponse<Void>> deleteRequestorGroup(@PathVariable Long id) {
        requestorGroupService.deleteRequestorGroup(id);
        return ResponseEntity.ok(ApiResponse.success("Requestor group deleted successfully"));
    }
    
    // ===== Schema wrapper classes for Swagger documentation =====
    
    @Schema(name = "RequestorGroupApiResponse", description = "API response with single requestor group")
    private static class RequestorGroupResponseWrapper extends ApiResponse<RequestorGroupDto.RequestorGroupResponse> {
        @Schema(description = "Requestor group data")
        private RequestorGroupDto.RequestorGroupResponse data;
    }
    
    @Schema(name = "RequestorGroupListApiResponse", description = "API response with list of requestor groups")
    private static class RequestorGroupListResponseWrapper extends ApiResponse<List<RequestorGroupDto.RequestorGroupResponse>> {
        @Schema(description = "List of requestor groups")
        private List<RequestorGroupDto.RequestorGroupResponse> data;
    }
}