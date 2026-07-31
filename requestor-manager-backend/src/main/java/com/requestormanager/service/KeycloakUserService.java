/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.service;

import com.requestormanager.dto.UserDto;
import com.requestormanager.enums.UserType;
import com.requestormanager.exception.CustomExceptions.*;
import com.requestormanager.repository.RequestorGroupRepository;
import com.requestormanager.security.KeycloakAuthService.KeycloakUser;
import com.requestormanager.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakUserService {

    @Value("${keycloak.admin-url:http://keycloak:8080/admin/realms/master}")
    private String keycloakAdminUrl;

    @Value("${keycloak.token-url:http://keycloak:8080/realms/master/protocol/openid-connect/token}")
    private String keycloakTokenUrl;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Value("${keycloak.admin-username:#{null}}")
    private String adminUsername;

    @Value("${keycloak.admin-password:#{null}}")
    private String adminPassword;

    private final SecurityUtils securityUtils;
    private final RequestorGroupRepository requestorGroupRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get the hierarchy level for a UserType (lower = more privileged).
     * Used to enforce "cannot manage same level or above" rule.
     */
    private int getUserTypeLevel(UserType type) {
        if (type == null) return 99;
        switch (type) {
            case JADDAR_MASTER_ADMIN: return 1;
            case GROUP_ADMIN: return 2;
            case REQUESTOR_GROUP_ADMIN: return 3;
            case REQUESTOR_GROUP_USER: return 4;
            default: return 99;
        }
    }

    /**
     * Check if the current user's type is strictly higher privilege than the target user's type.
     * Returns true if currentUser can manage targetType (i.e., current level < target level).
     */
    private boolean canManageUserType(UserType currentType, UserType targetType) {
        return getUserTypeLevel(currentType) < getUserTypeLevel(targetType);
    }

    /**
     * Get admin access token for Keycloak Admin API
     */
    private String getAdminToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "password");
            body.add("client_id", "admin-cli");
            body.add("username", adminUsername);
            body.add("password", adminPassword);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    keycloakTokenUrl,
                    request,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }

            throw new RuntimeException("Failed to get admin token");
        } catch (Exception e) {
            log.error("Failed to get Keycloak admin token: {}", e.getMessage());
            throw new RuntimeException("Failed to authenticate with Keycloak admin", e);
        }
    }

    /**
     * Create a new user in Keycloak
     */
    public UserDto.UserResponse createUser(UserDto.CreateRequest request) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        validateUserCreationPermissions(currentUser, request);

        String adminToken = getAdminToken();
        HttpHeaders headers = createAuthHeaders(adminToken);

        // Check if user already exists - throw specific exception with details
        UserDto.UserResponse existingUser = findUserByEmail(adminToken, request.getEmail());
        if (existingUser != null) {
            List<String> groupNames = request.getAllGroupNames();
            String targetGroup = groupNames.isEmpty() ? null : groupNames.get(0);
            throw new UserAlreadyExistsException(existingUser, targetGroup);
        }

        // Build Keycloak user representation
        Map<String, Object> keycloakUser = new HashMap<>();
        keycloakUser.put("username", request.getEmail());
        keycloakUser.put("email", request.getEmail());
        keycloakUser.put("firstName", request.getFirstName());
        keycloakUser.put("lastName", request.getLastName());
        keycloakUser.put("enabled", true);
        keycloakUser.put("emailVerified", true);

        // Set password
        Map<String, Object> credential = new HashMap<>();
        credential.put("type", "password");
        credential.put("value", request.getPassword());
        credential.put("temporary", false);
        keycloakUser.put("credentials", List.of(credential));

        try {
            HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(keycloakUser, headers);
            ResponseEntity<Void> response = restTemplate.postForEntity(
                    keycloakAdminUrl + "/users",
                    httpRequest,
                    Void.class
            );

            if (response.getStatusCode() == HttpStatus.CREATED) {
                // Get the created user
                String userId = extractUserIdFromLocation(response.getHeaders().getLocation());
                
                // Assign role based on type
                assignRoleToUser(adminToken, userId, request.getType());
                
                // Add to all specified groups
                List<String> groupNames = request.getAllGroupNames();
                for (String groupName : groupNames) {
                    addUserToGroup(adminToken, userId, groupName);
                }

                log.info("User created in Keycloak: {} with groups {} by {}", 
                        request.getEmail(), groupNames, currentUser.getEmail());
                return getUserById(userId);
            }
        } catch (HttpClientErrorException e) {
            log.error("Failed to create user in Keycloak: {}", e.getResponseBodyAsString(), e);
            throw new BadRequestException("Unable to create the user account. Please verify the details and try again.");
        }

        throw new RuntimeException("Failed to create user in Keycloak");
    }

    /**
     * Check if a user exists by email and return their info with group details
     */
    public UserDto.UserExistsResponse checkUserExists(String email, String targetGroupName) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        String adminToken = getAdminToken();

        UserDto.UserResponse existingUser = findUserByEmail(adminToken, email);
        
        if (existingUser == null) {
            return UserDto.UserExistsResponse.builder()
                    .userExists(false)
                    .targetGroup(targetGroupName)
                    .message("User does not exist. You can create a new user with this email.")
                    .build();
        }

        List<String> currentGroups = existingUser.getGroups();
        boolean alreadyInTargetGroup = currentGroups != null && 
                currentGroups.stream().anyMatch(g -> g.equalsIgnoreCase(targetGroupName));

        String message;
        if (alreadyInTargetGroup) {
            message = String.format("User '%s' already exists and is already a member of group '%s'.",
                    existingUser.getDisplayName(), targetGroupName);
        } else {
            message = String.format("User '%s' already exists in group(s): %s. Would you like to grant them access to '%s' as well?",
                    existingUser.getDisplayName(),
                    currentGroups != null ? String.join(", ", currentGroups) : "none",
                    targetGroupName);
        }

        return UserDto.UserExistsResponse.builder()
                .userExists(true)
                .existingUser(existingUser)
                .currentGroups(currentGroups)
                .targetGroup(targetGroupName)
                .alreadyInTargetGroup(alreadyInTargetGroup)
                .message(message)
                .build();
    }

    /**
     * Add an existing user to a group
     */
    public UserDto.UserResponse addExistingUserToGroup(String userId, String groupName) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        
        // Validate permissions - must be group_admin+ or requestor_group_admin of target group
        if (!currentUser.getUserType().canManageGroups()) {
            if (currentUser.getUserType().canManageGroupContent()) {
                // Requestor group admins can only add to their own groups
                if (currentUser.getGroups() == null || !currentUser.getGroups().contains(groupName)) {
                    throw new AccessDeniedException("You can only add users to your own group");
                }
            } else {
                throw new AccessDeniedException("You don't have permission to add users to groups");
            }
        }

        String adminToken = getAdminToken();

        // Verify user exists
        Map<String, Object> existingUser = fetchUserById(adminToken, userId);
        if (existingUser == null) {
            throw new ResourceNotFoundException("User", "id", userId);
        }

        // Enforce level check: cannot add-to-group a user at same or higher level
        UserDto.UserResponse targetUserResponse = mapToUserResponse(existingUser, adminToken);
        if (!canManageUserType(currentUser.getUserType(), targetUserResponse.getType())) {
            throw new AccessDeniedException("You cannot manage users at your access level or above");
        }

        // Check if already in group
        List<String> currentGroups = fetchUserGroups(adminToken, userId);
        if (currentGroups.stream().anyMatch(g -> g.equalsIgnoreCase(groupName))) {
            throw new BadRequestException("User is already a member of group: " + groupName);
        }

        // Add to group
        addUserToGroup(adminToken, userId, groupName);

        log.info("User {} added to group {} by {}", userId, groupName, currentUser.getEmail());
        return getUserById(userId);
    }

    /**
     * Find user by email and return full user response (or null if not found)
     */
    @SuppressWarnings("unchecked")
    private UserDto.UserResponse findUserByEmail(String adminToken, String email) {
        HttpHeaders headers = createAuthHeaders(adminToken);
        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    keycloakAdminUrl + "/users?email=" + email + "&exact=true",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    List.class
            );
            if (response.getBody() != null && !response.getBody().isEmpty()) {
                Map<String, Object> userData = (Map<String, Object>) response.getBody().get(0);
                return mapToUserResponse(userData, adminToken);
            }
        } catch (Exception e) {
            log.error("Error finding user by email: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Public lookup used by the password recovery flow.
     * Returns the Keycloak user (id/email/name) or null if no such user exists.
     */
    public UserDto.UserResponse lookupUserByEmail(String email) {
        return findUserByEmail(getAdminToken(), email);
    }

    /**
     * Reset a user's Keycloak password by their Keycloak id.
     * Used by the password recovery flow after a reset token has been validated.
     */
    public void resetPasswordById(String userId, String newPassword) {
        resetUserPassword(getAdminToken(), userId, newPassword);
    }

    /**
     * Custom exception for user already exists scenario
     */
    public static class UserAlreadyExistsException extends RuntimeException {
        private final UserDto.UserResponse existingUser;
        private final String targetGroup;

        public UserAlreadyExistsException(UserDto.UserResponse existingUser, String targetGroup) {
            super("User already exists: " + existingUser.getEmail());
            this.existingUser = existingUser;
            this.targetGroup = targetGroup;
        }
        
        public UserDto.UserResponse getExistingUser() {
            return existingUser;
        }
        
        public String getTargetGroup() {
            return targetGroup;
        }
    }

    /**
     * Get all users from Keycloak.
     * Users can see all members of their groups regardless of level,
     * but CRUD operations are restricted by level (enforced in create/update/delete).
     */
    public List<UserDto.UserResponse> getAllUsers() {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        String adminToken = getAdminToken();

        List<Map<String, Object>> keycloakUsers = fetchAllUsers(adminToken);

        if (currentUser.getUserType().canManageGroups()) {
            // Group admins and above see all users
            return keycloakUsers.stream()
                    .map(u -> mapToUserResponse(u, adminToken))
                    .collect(Collectors.toList());
        } else {
            // Regular users see only users in their groups
            List<String> userGroups = currentUser.getGroups();
            if (userGroups == null || userGroups.isEmpty()) {
                return List.of();
            }

            return keycloakUsers.stream()
                    .map(u -> mapToUserResponse(u, adminToken))
                    .filter(u -> {
                        // Always show self
                        if (u.getId().equals(currentUser.getSub())) return true;
                        // Show all users in same group(s) regardless of level
                        return u.getGroups() != null && 
                                u.getGroups().stream().anyMatch(userGroups::contains);
                    })
                    .collect(Collectors.toList());
        }
    }

    /**
     * Get user by Keycloak ID
     */
    public UserDto.UserResponse getUserById(String userId) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        String adminToken = getAdminToken();

        Map<String, Object> keycloakUser = fetchUserById(adminToken, userId);
        if (keycloakUser == null) {
            throw new ResourceNotFoundException("User", "id", userId);
        }

        UserDto.UserResponse response = mapToUserResponse(keycloakUser, adminToken);
        validateUserAccessPermission(currentUser, response);

        return response;
    }

    /**
     * Get current user info
     */
    public UserDto.UserResponse getCurrentUserInfo() {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        
        return UserDto.UserResponse.builder()
                .id(currentUser.getSub())
                .firstName(currentUser.getFirstName())
                .lastName(currentUser.getLastName())
                .email(currentUser.getEmail())
                .type(currentUser.getUserType())
                .groups(currentUser.getGroups())
                .roles(currentUser.getRealmRoles())
                .active(true)
                .build();
    }

    /**
     * Get users by group name.
     * Shows all members regardless of level; CRUD restrictions enforced elsewhere.
     */
    public List<UserDto.UserResponse> getUsersByGroup(String groupName) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        
        // Validate access
        if (!currentUser.getUserType().canManageGroups()) {
            if (currentUser.getGroups() == null || !currentUser.getGroups().contains(groupName)) {
                throw new AccessDeniedException("You can only view users from your own groups");
            }
        }

        String adminToken = getAdminToken();
        String groupId = getGroupIdByName(adminToken, groupName);
        if (groupId == null) {
            throw new ResourceNotFoundException("Group", "name", groupName);
        }

        List<Map<String, Object>> members = fetchGroupMembers(adminToken, groupId);
        return members.stream()
                .map(u -> mapToUserResponse(u, adminToken))
                .collect(Collectors.toList());
    }

    /**
     * Update user in Keycloak
     */
    public UserDto.UserResponse updateUser(String userId, UserDto.UpdateRequest request) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        String adminToken = getAdminToken();

        Map<String, Object> existingUser = fetchUserById(adminToken, userId);
        if (existingUser == null) {
            throw new ResourceNotFoundException("User", "id", userId);
        }

        UserDto.UserResponse existingResponse = mapToUserResponse(existingUser, adminToken);
        validateUserUpdatePermission(currentUser, existingResponse, request);

        // Update user fields
        HttpHeaders headers = createAuthHeaders(adminToken);
        Map<String, Object> updates = new HashMap<>();
        
        if (request.getFirstName() != null) {
            updates.put("firstName", request.getFirstName());
        }
        if (request.getLastName() != null) {
            updates.put("lastName", request.getLastName());
        }
        if (request.getEmail() != null && !request.getEmail().equals(existingUser.get("email"))) {
            if (userExistsByEmail(adminToken, request.getEmail())) {
                throw new ResourceAlreadyExistsException("User", "email", request.getEmail());
            }
            updates.put("email", request.getEmail());
            updates.put("username", request.getEmail());
        }
        if (request.getActive() != null) {
            updates.put("enabled", request.getActive());
        }

        if (!updates.isEmpty()) {
            // Merge with existing user data
            existingUser.putAll(updates);
            
            try {
                HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(existingUser, headers);
                restTemplate.put(keycloakAdminUrl + "/users/" + userId, httpRequest);
            } catch (HttpClientErrorException e) {
                log.error("Failed to update user in Keycloak: {}", e.getResponseBodyAsString(), e);
                throw new BadRequestException("Unable to update the user account. Please verify the details and try again.");
            }
        }

        // Update password if provided
        if (request.getPassword() != null) {
            resetUserPassword(adminToken, userId, request.getPassword());
        }

        // Update role if type changed
        if (request.getType() != null && request.getType() != existingResponse.getType()) {
            updateUserRole(adminToken, userId, existingResponse.getType(), request.getType());
        }

        // Update group memberships if specified
        if (request.hasGroupUpdate()) {
            List<String> newGroups = request.getAllGroupNames();
            updateUserGroups(adminToken, userId, newGroups);
        }

        log.info("User updated in Keycloak: {} by {}", userId, currentUser.getEmail());
        return getUserById(userId);
    }

    /**
     * Delete user from Keycloak.
     * Users cannot delete users at their own access level or above.
     */
    public void deleteUser(String userId) {
        KeycloakUser currentUser = securityUtils.requireCurrentUser();
        
        if (!currentUser.getUserType().canManageGroups()) {
            throw new AccessDeniedException("Only Group Admin and above can delete users");
        }

        // Cannot delete self
        if (currentUser.getSub().equals(userId)) {
            throw new BadRequestException("You cannot delete your own account");
        }

        String adminToken = getAdminToken();
        Map<String, Object> existingUser = fetchUserById(adminToken, userId);
        if (existingUser == null) {
            throw new ResourceNotFoundException("User", "id", userId);
        }

        UserDto.UserResponse userResponse = mapToUserResponse(existingUser, adminToken);
        
        // Enforce level check: cannot delete users at same or higher level
        if (!canManageUserType(currentUser.getUserType(), userResponse.getType())) {
            throw new AccessDeniedException("You cannot delete users at your access level or above");
        }

        HttpHeaders headers = createAuthHeaders(adminToken);
        try {
            restTemplate.exchange(
                    keycloakAdminUrl + "/users/" + userId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class
            );
            log.info("User deleted from Keycloak: {} by {}", userId, currentUser.getEmail());
        } catch (HttpClientErrorException e) {
            log.error("Failed to delete user from Keycloak: {}", e.getResponseBodyAsString(), e);
            throw new BadRequestException("Unable to delete the user account. Please try again.");
        }
    }

    // ========== Helper Methods ==========

    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private boolean userExistsByEmail(String adminToken, String email) {
        HttpHeaders headers = createAuthHeaders(adminToken);
        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    keycloakAdminUrl + "/users?email=" + email + "&exact=true",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    List.class
            );
            return response.getBody() != null && !response.getBody().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchAllUsers(String adminToken) {
        HttpHeaders headers = createAuthHeaders(adminToken);
        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    keycloakAdminUrl + "/users?max=1000",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    List.class
            );
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.error("Failed to fetch users from Keycloak: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUserById(String adminToken, String userId) {
        HttpHeaders headers = createAuthHeaders(adminToken);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    keycloakAdminUrl + "/users/" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch user from Keycloak: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchGroupMembers(String adminToken, String groupId) {
        HttpHeaders headers = createAuthHeaders(adminToken);
        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    keycloakAdminUrl + "/groups/" + groupId + "/members",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    List.class
            );
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.error("Failed to fetch group members from Keycloak: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchUserGroups(String adminToken, String userId) {
        HttpHeaders headers = createAuthHeaders(adminToken);
        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    keycloakAdminUrl + "/users/" + userId + "/groups",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    List.class
            );
            if (response.getBody() != null) {
                List<Map<String, Object>> groups = (List<Map<String, Object>>) response.getBody();
                return groups.stream()
                        .map(g -> (String) g.get("name"))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("Failed to fetch user groups: {}", e.getMessage());
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchUserRoles(String adminToken, String userId) {
        HttpHeaders headers = createAuthHeaders(adminToken);
        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    keycloakAdminUrl + "/users/" + userId + "/role-mappings/realm",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    List.class
            );
            if (response.getBody() != null) {
                List<Map<String, Object>> roles = (List<Map<String, Object>>) response.getBody();
                return roles.stream()
                        .map(r -> (String) r.get("name"))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("Failed to fetch user roles: {}", e.getMessage());
        }
        return List.of();
    }

    private String getGroupIdByName(String adminToken, String groupName) {
        HttpHeaders headers = createAuthHeaders(adminToken);
        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    keycloakAdminUrl + "/groups?search=" + groupName,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    List.class
            );
            if (response.getBody() != null) {
                for (Object obj : response.getBody()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> group = (Map<String, Object>) obj;
                    // Exact name match
                    if (groupName.equals(group.get("name"))) {
                        return (String) group.get("id");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get group ID for '{}': {}", groupName, e.getMessage());
        }
        return null;
    }

    private void assignRoleToUser(String adminToken, String userId, UserType type) {
        String roleName = type.getKeycloakRoleName();
        HttpHeaders headers = createAuthHeaders(adminToken);

        // Get role
        try {
            ResponseEntity<Map> roleResponse = restTemplate.exchange(
                    keycloakAdminUrl + "/roles/" + roleName,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            if (roleResponse.getBody() != null) {
                HttpEntity<List<Map>> request = new HttpEntity<>(List.of(roleResponse.getBody()), headers);
                restTemplate.postForEntity(
                        keycloakAdminUrl + "/users/" + userId + "/role-mappings/realm",
                        request,
                        Void.class
                );
            }
        } catch (Exception e) {
            log.error("Failed to assign role to user: {}", e.getMessage());
        }
    }

    private void addUserToGroup(String adminToken, String userId, String groupName) {
        String groupId = getGroupIdByName(adminToken, groupName);
        if (groupId == null) {
            throw new BadRequestException("Group not found: " + groupName);
        }

        HttpHeaders headers = createAuthHeaders(adminToken);
        try {
            restTemplate.put(
                    keycloakAdminUrl + "/users/" + userId + "/groups/" + groupId,
                    new HttpEntity<>(headers)
            );
            log.info("Successfully added user {} to group {}", userId, groupName);
        } catch (Exception e) {
            log.error("Failed to add user {} to group {}: {}", userId, groupName, e.getMessage());
            throw new BadRequestException("Failed to add user to group: " + groupName);
        }
    }

    private void resetUserPassword(String adminToken, String userId, String newPassword) {
        HttpHeaders headers = createAuthHeaders(adminToken);
        Map<String, Object> credential = new HashMap<>();
        credential.put("type", "password");
        credential.put("value", newPassword);
        credential.put("temporary", false);

        try {
            restTemplate.put(
                    keycloakAdminUrl + "/users/" + userId + "/reset-password",
                    new HttpEntity<>(credential, headers)
            );
        } catch (Exception e) {
            log.error("Failed to reset password: {}", e.getMessage());
            throw new BadRequestException("Failed to reset password");
        }
    }

    private void updateUserRole(String adminToken, String userId, UserType oldType, UserType newType) {
        HttpHeaders headers = createAuthHeaders(adminToken);
        
        // Remove old role
        String oldRoleName = oldType.getKeycloakRoleName();
        try {
            ResponseEntity<Map> roleResponse = restTemplate.exchange(
                    keycloakAdminUrl + "/roles/" + oldRoleName,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );
            if (roleResponse.getBody() != null) {
                restTemplate.exchange(
                        keycloakAdminUrl + "/users/" + userId + "/role-mappings/realm",
                        HttpMethod.DELETE,
                        new HttpEntity<>(List.of(roleResponse.getBody()), headers),
                        Void.class
                );
            }
        } catch (Exception e) {
            log.warn("Failed to remove old role: {}", e.getMessage());
        }

        // Add new role
        assignRoleToUser(adminToken, userId, newType);
    }

    private void updateUserGroups(String adminToken, String userId, List<String> newGroupNames) {
        HttpHeaders headers = createAuthHeaders(adminToken);
        
        // Get current groups
        List<String> currentGroups = fetchUserGroups(adminToken, userId);
        
        // Remove groups that are no longer in the list
        for (String groupName : currentGroups) {
            if (!newGroupNames.contains(groupName)) {
                String groupId = getGroupIdByName(adminToken, groupName);
                if (groupId != null) {
                    try {
                        restTemplate.exchange(
                                keycloakAdminUrl + "/users/" + userId + "/groups/" + groupId,
                                HttpMethod.DELETE,
                                new HttpEntity<>(headers),
                                Void.class
                        );
                        log.debug("Removed user {} from group {}", userId, groupName);
                    } catch (Exception e) {
                        log.warn("Failed to remove user from group {}: {}", groupName, e.getMessage());
                    }
                }
            }
        }

        // Add new groups
        for (String groupName : newGroupNames) {
            if (!currentGroups.contains(groupName)) {
                addUserToGroup(adminToken, userId, groupName);
                log.debug("Added user {} to group {}", userId, groupName);
            }
        }
    }

    private String extractUserIdFromLocation(java.net.URI location) {
        if (location != null) {
            String path = location.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        }
        return null;
    }

    private UserDto.UserResponse mapToUserResponse(Map<String, Object> keycloakUser, String adminToken) {
        String userId = (String) keycloakUser.get("id");
        List<String> groups = fetchUserGroups(adminToken, userId);
        List<String> roles = fetchUserRoles(adminToken, userId);
        UserType userType = UserType.fromKeycloakRoles(roles);

        return UserDto.UserResponse.builder()
                .id(userId)
                .firstName((String) keycloakUser.get("firstName"))
                .lastName((String) keycloakUser.get("lastName"))
                .email((String) keycloakUser.get("email"))
                .type(userType)
                .groups(groups)
                .roles(roles)
                .active((Boolean) keycloakUser.getOrDefault("enabled", true))
                .createdAt(null) // Keycloak doesn't expose this easily
                .build();
    }

    // ========== Validation Methods ==========

    private void validateUserCreationPermissions(KeycloakUser currentUser, UserDto.CreateRequest request) {
        UserType currentType = currentUser.getUserType();
        UserType requestedType = request.getType();

        // No one can create JADDAR_MASTER_ADMIN
        if (requestedType == UserType.JADDAR_MASTER_ADMIN) {
            throw new BadRequestException("Cannot create Master Admin users through the API");
        }

        // REQUESTOR_GROUP_USER cannot create anyone
        if (currentType == UserType.REQUESTOR_GROUP_USER) {
            throw new AccessDeniedException("You don't have permission to create users");
        }

        // Enforce: can only create users strictly below own level
        if (!canManageUserType(currentType, requestedType)) {
            throw new AccessDeniedException("You can only create users below your access level");
        }

        // JADDAR_MASTER_ADMIN can create GROUP_ADMIN, REQUESTOR_GROUP_ADMIN, REQUESTOR_GROUP_USER
        if (currentType == UserType.JADDAR_MASTER_ADMIN) {
            return;
        }

        // GROUP_ADMIN can create REQUESTOR_GROUP_ADMIN and REQUESTOR_GROUP_USER
        if (currentType == UserType.GROUP_ADMIN) {
            return;
        }

        // REQUESTOR_GROUP_ADMIN can only create REQUESTOR_GROUP_USER in their own groups
        if (currentType == UserType.REQUESTOR_GROUP_ADMIN) {
            if (requestedType != UserType.REQUESTOR_GROUP_USER) {
                throw new AccessDeniedException("Requestor Group Admin can only create Requestor Group Users");
            }
            // Must create in their own group(s)
            List<String> requestedGroups = request.getAllGroupNames();
            if (requestedGroups.isEmpty()) {
                throw new AccessDeniedException("Requestor Group Admin must assign users to a group");
            }
            if (currentUser.getGroups() == null || currentUser.getGroups().isEmpty()) {
                throw new AccessDeniedException("You don't belong to any group");
            }
            // Check all requested groups are in current user's groups
            for (String groupName : requestedGroups) {
                if (!currentUser.getGroups().contains(groupName)) {
                    throw new AccessDeniedException("Requestor Group Admin can only create users in their own groups. You don't have access to group: " + groupName);
                }
            }
            return;
        }

        throw new AccessDeniedException("You don't have permission to create users");
    }

    private void validateUserAccessPermission(KeycloakUser currentUser, UserDto.UserResponse targetUser) {
        // Users can always see themselves
        if (currentUser.getSub().equals(targetUser.getId())) {
            return;
        }

        if (currentUser.getUserType().canManageGroups()) {
            return; // Group admin and above can see all users
        }

        if (currentUser.getUserType().canManageGroupContent()) {
            if (currentUser.getGroups() != null && targetUser.getGroups() != null) {
                boolean sameGroup = currentUser.getGroups().stream()
                        .anyMatch(g -> targetUser.getGroups().contains(g));
                if (sameGroup) {
                    return;
                }
            }
        }

        throw new AccessDeniedException("You don't have permission to access this user");
    }

    private void validateUserUpdatePermission(KeycloakUser currentUser, UserDto.UserResponse targetUser, 
                                               UserDto.UpdateRequest request) {
        UserType currentType = currentUser.getUserType();
        UserType targetType = targetUser.getType();
        boolean isSelf = currentUser.getSub().equals(targetUser.getId());

        // JADDAR_MASTER_ADMIN can edit themselves and anyone else
        if (currentType == UserType.JADDAR_MASTER_ADMIN) {
            // Cannot promote anyone to Master Admin through the API
            if (request.getType() == UserType.JADDAR_MASTER_ADMIN && !isSelf) {
                throw new BadRequestException("Cannot promote users to Master Admin through the API");
            }
            return;
        }

        // GROUP_ADMIN: cannot edit themselves or Master Admin, can edit everyone else
        if (currentType == UserType.GROUP_ADMIN) {
            if (isSelf) {
                throw new AccessDeniedException("Group Admins cannot edit their own account");
            }
            if (targetType == UserType.JADDAR_MASTER_ADMIN) {
                throw new AccessDeniedException("Group Admins cannot edit Master Admin users");
            }
            if (targetType == UserType.GROUP_ADMIN) {
                throw new AccessDeniedException("Group Admins cannot edit other Group Admin users");
            }
            // Cannot promote to same or higher level
            if (request.getType() != null && !canManageUserType(currentType, request.getType())) {
                throw new AccessDeniedException("You cannot promote a user to your access level or above");
            }
            return;
        }

        // REQUESTOR_GROUP_ADMIN: cannot edit themselves, can only edit REQUESTOR_GROUP_USER in own groups
        if (currentType == UserType.REQUESTOR_GROUP_ADMIN) {
            if (isSelf) {
                throw new AccessDeniedException("Requestor Group Admins cannot edit their own account");
            }
            if (targetType != UserType.REQUESTOR_GROUP_USER) {
                throw new AccessDeniedException("Requestor Group Admin can only update Requestor Group Users");
            }
            // Must be in same group
            if (currentUser.getGroups() == null || targetUser.getGroups() == null) {
                throw new AccessDeniedException("You can only update users in your own groups");
            }
            boolean sameGroup = currentUser.getGroups().stream()
                    .anyMatch(g -> targetUser.getGroups().contains(g));
            if (!sameGroup) {
                throw new AccessDeniedException("You can only update users in your own groups");
            }
            // Cannot change their role
            if (request.getType() != null && request.getType() != UserType.REQUESTOR_GROUP_USER) {
                throw new AccessDeniedException("Requestor Group Admin cannot change user roles");
            }
            return;
        }

        // REQUESTOR_GROUP_USER cannot update anyone
        throw new AccessDeniedException("You don't have permission to update users");
    }
    /**
     * Create a group in Keycloak
     */
    public void createKeycloakGroup(String groupName) {
        String adminToken = getAdminToken();
        HttpHeaders headers = createAuthHeaders(adminToken);
        
        // Check if group already exists
        String existingGroupId = getGroupIdByName(adminToken, groupName);
        if (existingGroupId != null) {
            log.info("Keycloak group already exists: {}", groupName);
            return;
        }
        
        Map<String, String> body = Map.of("name", groupName);
        
        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(
                    keycloakAdminUrl + "/groups",
                    new HttpEntity<>(body, headers),
                    Void.class
            );
            if (response.getStatusCode() == HttpStatus.CREATED) {
                log.info("Created group in Keycloak: {}", groupName);
            }
        } catch (HttpClientErrorException e) {
            log.error("Failed to create group {} in Keycloak: {}", groupName, e.getResponseBodyAsString());
            throw new BadRequestException("Failed to create group in Keycloak: " + groupName);
        }
    }

    /**
     * Rename a group in Keycloak
     */
    public void renameKeycloakGroup(String oldName, String newName) {
        String adminToken = getAdminToken();
        HttpHeaders headers = createAuthHeaders(adminToken);
        
        String groupId = getGroupIdByName(adminToken, oldName);
        if (groupId == null) {
            log.warn("Keycloak group not found for rename: {}. Creating new group: {}", oldName, newName);
            createKeycloakGroup(newName);
            return;
        }
        
        Map<String, String> body = Map.of("name", newName);
        
        try {
            restTemplate.put(
                    keycloakAdminUrl + "/groups/" + groupId,
                    new HttpEntity<>(body, headers)
            );
            log.info("Renamed Keycloak group from {} to {}", oldName, newName);
        } catch (HttpClientErrorException e) {
            log.error("Failed to rename group {} to {} in Keycloak: {}", oldName, newName, e.getResponseBodyAsString());
            throw new BadRequestException("Failed to rename group in Keycloak");
        }
    }

    /**
     * Delete a group from Keycloak
     */
    public void deleteKeycloakGroup(String groupName) {
        String adminToken = getAdminToken();
        HttpHeaders headers = createAuthHeaders(adminToken);
        
        String groupId = getGroupIdByName(adminToken, groupName);
        if (groupId == null) {
            log.warn("Keycloak group not found for deletion: {}", groupName);
            return;
        }
        
        try {
            restTemplate.exchange(
                    keycloakAdminUrl + "/groups/" + groupId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class
            );
            log.info("Deleted group from Keycloak: {}", groupName);
        } catch (HttpClientErrorException e) {
            log.error("Failed to delete group {} from Keycloak: {}", groupName, e.getResponseBodyAsString());
            throw new BadRequestException("Failed to delete group from Keycloak: " + groupName);
        }
    }

    /**
     * Sync all RequestorGroups from database to Keycloak (creates missing groups)
     * Call this on application startup or periodically
     */
    public void syncRequestorGroupsToKeycloak() {
        String adminToken = getAdminToken();
        
        List<String> dbGroupNames = requestorGroupRepository.findAll().stream()
                .map(rg -> rg.getName())
                .collect(Collectors.toList());
        
        int created = 0;
        for (String groupName : dbGroupNames) {
            String groupId = getGroupIdByName(adminToken, groupName);
            if (groupId == null) {
                try {
                    createKeycloakGroup(groupName);
                    created++;
                } catch (Exception e) {
                    log.error("Failed to sync group {} to Keycloak: {}", groupName, e.getMessage());
                }
            }
        }
        
        if (created > 0) {
            log.info("Synced {} missing RequestorGroups to Keycloak", created);
        } else {
            log.debug("All RequestorGroups already exist in Keycloak");
        }
    }
}