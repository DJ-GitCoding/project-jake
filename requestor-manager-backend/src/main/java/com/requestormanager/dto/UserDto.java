/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.dto;

import com.requestormanager.enums.UserType;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserDto {
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "UserCreateRequest", description = "Create user request")
    public static class CreateRequest {
        
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        @Schema(description = "User's first name", example = "John")
        private String firstName;
        
        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        @Schema(description = "User's last name", example = "Doe")
        private String lastName;
        
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Schema(description = "User's email address", example = "john.doe@example.com")
        private String email;
        
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Schema(description = "User's password", example = "SecurePass123!")
        private String password;
        
        @NotNull(message = "User type is required")
        @Schema(description = "User type/role", example = "REQUESTOR_GROUP_USER")
        private UserType type;
        
        @Schema(description = "Single Keycloak group name (legacy support)", example = "ICANN")
        @JsonAlias({"groupName"})
        private String requestorGroupName;
        
        @Schema(description = "List of Keycloak group names to add user to", example = "[\"ICANN\", \"ARIN\"]")
        @JsonAlias({"groupNames"})
        private List<String> requestorGroupNames;
        
        /**
         * Get all group names (combines single and list fields for backwards compatibility)
         */
        public List<String> getAllGroupNames() {
            List<String> allGroups = new ArrayList<>();
            if (requestorGroupNames != null && !requestorGroupNames.isEmpty()) {
                allGroups.addAll(requestorGroupNames);
            }
            if (requestorGroupName != null && !requestorGroupName.isEmpty() && !allGroups.contains(requestorGroupName)) {
                allGroups.add(requestorGroupName);
            }
            return allGroups;
        }
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "UserUpdateRequest", description = "Update user request")
    public static class UpdateRequest {
        
        @Size(max = 100, message = "First name must not exceed 100 characters")
        @Schema(description = "User's first name", example = "John")
        private String firstName;
        
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        @Schema(description = "User's last name", example = "Doe")
        private String lastName;
        
        @Email(message = "Invalid email format")
        @Schema(description = "User's email address", example = "john.doe@example.com")
        private String email;
        
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Schema(description = "New password", example = "NewSecurePass123!")
        private String password;
        
        @Schema(description = "User type/role", example = "REQUESTOR_GROUP_ADMIN")
        private UserType type;
        
        @Schema(description = "Single Keycloak group name (legacy support)", example = "ICANN")
        @JsonAlias({"requestorGroupName"})
        private String groupName;
        
        @Schema(description = "List of Keycloak group names", example = "[\"ICANN\", \"ARIN\"]")
        @JsonAlias({"requestorGroupNames"})
        private List<String> groupNames;
        
        @Schema(description = "Whether the user is active", example = "true")
        private Boolean active;
        
        /**
         * Get all group names (combines single and list fields for backwards compatibility)
         */
        public List<String> getAllGroupNames() {
            List<String> allGroups = new ArrayList<>();
            if (groupNames != null && !groupNames.isEmpty()) {
                allGroups.addAll(groupNames);
            }
            if (groupName != null && !groupName.isEmpty() && !allGroups.contains(groupName)) {
                allGroups.add(groupName);
            }
            return allGroups;
        }
        
        /**
         * Check if group update was requested
         */
        public boolean hasGroupUpdate() {
            return (groupNames != null) || (groupName != null);
        }
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "UserResponse", description = "User response")
    public static class UserResponse {
        
        @Schema(description = "Keycloak user ID (UUID)")
        private String id;
        
        @Schema(description = "User's first name")
        private String firstName;
        
        @Schema(description = "User's last name")
        private String lastName;
        
        @Schema(description = "User's email address")
        private String email;
        
        @Schema(description = "User type/role")
        private UserType type;
        
        @Schema(description = "Keycloak groups the user belongs to")
        private List<String> groups;
        
        @Schema(description = "Keycloak realm roles assigned to user")
        private List<String> roles;
        
        @Schema(description = "Whether the user is active/enabled")
        private Boolean active;
        
        @Schema(description = "Creation timestamp")
        private LocalDateTime createdAt;
        
        /**
         * Get display name
         */
        public String getDisplayName() {
            if (firstName != null || lastName != null) {
                return ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
            }
            return email;
        }
        
        /**
         * Get primary group (first group in list)
         */
        public String getPrimaryGroup() {
            return (groups != null && !groups.isEmpty()) ? groups.get(0) : null;
        }
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "UserExistsResponse", description = "Response when user already exists in another group")
    public static class UserExistsResponse {
        
        @Schema(description = "Indicates user already exists")
        private boolean userExists;
        
        @Schema(description = "The existing user's details")
        private UserResponse existingUser;
        
        @Schema(description = "Groups the user currently belongs to")
        private List<String> currentGroups;
        
        @Schema(description = "The group the admin is trying to add the user to")
        private String targetGroup;
        
        @Schema(description = "Message explaining the situation")
        private String message;
        
        @Schema(description = "Whether the user is already in the target group")
        private boolean alreadyInTargetGroup;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "AddUserToGroupRequest", description = "Request to add existing user to a group")
    public static class AddUserToGroupRequest {
        
        @NotBlank(message = "User ID is required")
        @Schema(description = "Keycloak user ID", example = "cde63753-434a-468b-94d6-7d806968d3cd")
        private String userId;
        
        @NotBlank(message = "Group name is required")
        @Schema(description = "Group name to add user to", example = "ICANN")
        private String groupName;
    }
    
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "CheckUserRequest", description = "Request to check if user exists by email")
    public static class CheckUserRequest {
        
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Schema(description = "Email to check", example = "john.doe@example.com")
        private String email;
        
        @Schema(description = "Target group name", example = "ICANN")
        private String targetGroupName;
    }
}