/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.controller;

import com.requestormanager.dto.ApiResponse;
import com.requestormanager.dto.UserDto;
import com.requestormanager.service.KeycloakUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users", description = "User management endpoints - manages Keycloak users")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {
    
    private final KeycloakUserService userService;
    
    @PostMapping
    @Operation(summary = "Create user", description = "Create a new user in Keycloak. If user already exists, returns 409 with user details and option to add to group.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "User created successfully",
            content = @Content(schema = @Schema(implementation = UserResponseWrapper.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "User already exists - returns existing user info",
            content = @Content(schema = @Schema(implementation = UserExistsResponseWrapper.class))
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
    public ResponseEntity<?> createUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "User creation request",
                required = true,
                content = @Content(schema = @Schema(implementation = UserDto.CreateRequest.class))
            )
            @Valid @RequestBody UserDto.CreateRequest request) {
        try {
            UserDto.UserResponse response = userService.createUser(request);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("User created successfully", response));
        } catch (KeycloakUserService.UserAlreadyExistsException e) {
            // Return 409 with existing user info
            UserDto.UserResponse existingUser = e.getExistingUser();
            boolean alreadyInTargetGroup = existingUser.getGroups() != null &&
                    existingUser.getGroups().stream().anyMatch(g -> g.equalsIgnoreCase(e.getTargetGroup()));
            
            UserDto.UserExistsResponse existsResponse = UserDto.UserExistsResponse.builder()
                    .userExists(true)
                    .existingUser(existingUser)
                    .currentGroups(existingUser.getGroups())
                    .targetGroup(e.getTargetGroup())
                    .alreadyInTargetGroup(alreadyInTargetGroup)
                    .message(alreadyInTargetGroup 
                            ? String.format("User '%s' already exists and is already a member of group '%s'.",
                                    existingUser.getDisplayName(), e.getTargetGroup())
                            : String.format("User '%s' already exists in group(s): %s. Would you like to grant them access to '%s' as well?",
                                    existingUser.getDisplayName(),
                                    existingUser.getGroups() != null ? String.join(", ", existingUser.getGroups()) : "none",
                                    e.getTargetGroup()))
                    .build();
            
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ApiResponse.<UserDto.UserExistsResponse>builder()
                            .success(false)
                            .message("User already exists")
                            .data(existsResponse)
                            .build());
        }
    }
    
    @PostMapping("/check")
    @Operation(summary = "Check if user exists", description = "Check if a user with the given email exists and get their group memberships")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Check completed",
            content = @Content(schema = @Schema(implementation = UserExistsResponseWrapper.class))
        )
    })
    public ResponseEntity<ApiResponse<UserDto.UserExistsResponse>> checkUserExists(
            @Valid @RequestBody UserDto.CheckUserRequest request) {
        UserDto.UserExistsResponse response = userService.checkUserExists(
                request.getEmail(), 
                request.getTargetGroupName()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @PostMapping("/add-to-group")
    @Operation(summary = "Add existing user to group", description = "Add an existing user to a new group")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "User added to group successfully",
            content = @Content(schema = @Schema(implementation = UserResponseWrapper.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "User already in group or invalid request",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "User or group not found",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    public ResponseEntity<ApiResponse<UserDto.UserResponse>> addUserToGroup(
            @Valid @RequestBody UserDto.AddUserToGroupRequest request) {
        UserDto.UserResponse response = userService.addExistingUserToGroup(
                request.getUserId(),
                request.getGroupName()
        );
        return ResponseEntity.ok(ApiResponse.success(
                String.format("User added to group '%s' successfully", request.getGroupName()), 
                response
        ));
    }
    
    @GetMapping
    @Operation(summary = "Get all users", description = "Get all users from Keycloak based on current user's permissions")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Users retrieved successfully",
            content = @Content(schema = @Schema(implementation = UserListResponseWrapper.class))
        )
    })
    public ResponseEntity<ApiResponse<List<UserDto.UserResponse>>> getAllUsers() {
        List<UserDto.UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Get a specific user by Keycloak ID")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "User retrieved successfully",
            content = @Content(schema = @Schema(implementation = UserResponseWrapper.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    public ResponseEntity<ApiResponse<UserDto.UserResponse>> getUserById(@PathVariable String id) {
        UserDto.UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    @GetMapping("/group/{groupName}")
    @Operation(summary = "Get users by group", description = "Get all users in a specific Keycloak group")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Users retrieved successfully",
            content = @Content(schema = @Schema(implementation = UserListResponseWrapper.class))
        )
    })
    public ResponseEntity<ApiResponse<List<UserDto.UserResponse>>> getUsersByGroup(
            @PathVariable String groupName) {
        List<UserDto.UserResponse> users = userService.getUsersByGroup(groupName);
        return ResponseEntity.ok(ApiResponse.success(users));
    }
    
    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Get the currently authenticated user's information")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Current user retrieved successfully",
            content = @Content(schema = @Schema(implementation = UserResponseWrapper.class))
        )
    })
    public ResponseEntity<ApiResponse<UserDto.UserResponse>> getCurrentUser() {
        UserDto.UserResponse user = userService.getCurrentUserInfo();
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Update an existing user in Keycloak")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "User updated successfully",
            content = @Content(schema = @Schema(implementation = UserResponseWrapper.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    public ResponseEntity<ApiResponse<UserDto.UserResponse>> updateUser(
            @PathVariable String id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "User update request",
                required = true,
                content = @Content(schema = @Schema(implementation = UserDto.UpdateRequest.class))
            )
            @Valid @RequestBody UserDto.UpdateRequest request) {
        UserDto.UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", response));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Delete a user from Keycloak (Master and Admin only)")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "User deleted successfully",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "User not found",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        )
    })
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully"));
    }
    
    // ===== Schema wrapper classes for Swagger documentation =====
    
    @Schema(name = "UserApiResponse", description = "API response with single user")
    private static class UserResponseWrapper extends ApiResponse<UserDto.UserResponse> {
        @Schema(description = "User data")
        private UserDto.UserResponse data;
    }
    
    @Schema(name = "UserListApiResponse", description = "API response with list of users")
    private static class UserListResponseWrapper extends ApiResponse<List<UserDto.UserResponse>> {
        @Schema(description = "List of users")
        private List<UserDto.UserResponse> data;
    }
    
    @Schema(name = "UserExistsApiResponse", description = "API response when user already exists")
    private static class UserExistsResponseWrapper extends ApiResponse<UserDto.UserExistsResponse> {
        @Schema(description = "User exists data")
        private UserDto.UserExistsResponse data;
    }
}