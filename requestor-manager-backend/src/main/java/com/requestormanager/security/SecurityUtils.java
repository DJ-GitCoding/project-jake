/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.security;

import com.requestormanager.enums.UserType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Utility class for accessing the current authenticated user
 */
@Component
public class SecurityUtils {

    /**
     * Get the current authenticated KeycloakUser
     */
    public Optional<KeycloakAuthService.KeycloakUser> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication instanceof KeycloakAuthenticationToken) {
            return Optional.of(((KeycloakAuthenticationToken) authentication).getKeycloakUser());
        }
        
        return Optional.empty();
    }

    /**
     * Get the current user's Keycloak sub (ID)
     */
    public Optional<String> getCurrentUserId() {
        return getCurrentUser().map(KeycloakAuthService.KeycloakUser::getSub);
    }

    /**
     * Get the current user's email
     */
    public Optional<String> getCurrentUserEmail() {
        return getCurrentUser().map(KeycloakAuthService.KeycloakUser::getEmail);
    }

    /**
     * Get the current user's username
     */
    public Optional<String> getCurrentUsername() {
        return getCurrentUser().map(KeycloakAuthService.KeycloakUser::getUsername);
    }

    /**
     * Get the current user's type
     */
    public Optional<UserType> getCurrentUserType() {
        return getCurrentUser().map(KeycloakAuthService.KeycloakUser::getUserType);
    }

    /**
     * Get the current user's groups
     */
    public List<String> getCurrentUserGroups() {
        return getCurrentUser()
                .map(KeycloakAuthService.KeycloakUser::getGroups)
                .orElse(List.of());
    }

    /**
     * Check if current user has a specific role
     */
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role) || 
                              a.getAuthority().equals(role));
    }

    /**
     * Check if current user is MASTER
     */
    public boolean isMaster() {
        return getCurrentUserType()
                .map(type -> type == UserType.JADDAR_MASTER_ADMIN)
                .orElse(false);
    }

    /**
     * Check if current user is ADMIN or MASTER
     */
    public boolean isAdmin() {
        return getCurrentUserType()
                .map(type -> type == UserType.GROUP_ADMIN || type == UserType.JADDAR_MASTER_ADMIN)
                .orElse(false);
    }

    /**
     * Check if current user is REQUESTOR_GROUP_ADMIN or higher
     */
    public boolean isGroupAdmin() {
        return getCurrentUserType()
                .map(type -> type == UserType.REQUESTOR_GROUP_ADMIN || 
                            type == UserType.GROUP_ADMIN || 
                            type == UserType.JADDAR_MASTER_ADMIN)
                .orElse(false);
    }

    /**
     * Check if current user belongs to a specific group
     */
    public boolean isInGroup(String groupName) {
        return getCurrentUserGroups().stream()
                .anyMatch(g -> g.equalsIgnoreCase(groupName));
    }

    /**
     * Get the KeycloakUser or throw exception if not authenticated
     */
    public KeycloakAuthService.KeycloakUser requireCurrentUser() {
        return getCurrentUser()
                .orElseThrow(() -> new RuntimeException("User not authenticated"));
    }
}