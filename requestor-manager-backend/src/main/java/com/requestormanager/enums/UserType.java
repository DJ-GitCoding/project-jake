/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.enums;

/**
 * User type/role hierarchy for Jaddar and Requestor Manager.
 * 
 * Hierarchy (highest to lowest privilege):
 * 1. JADDAR_MASTER_ADMIN - Full system control (Keycloak, all groups, all users, all agreements, all RDAP)
 * 2. GROUP_ADMIN - Can create/manage requestor groups, designate requestor_group_admin
 * 3. REQUESTOR_GROUP_ADMIN - Can create users and agreements within assigned groups
 * 4. REQUESTOR_GROUP_USER - Can view/use agreement data and make RDAP requests
 * 
 * Each role inherits all capabilities of roles below it.
 */
public enum UserType {
    JADDAR_MASTER_ADMIN(1, "jaddar_master_admin", "Master Admin"),
    GROUP_ADMIN(2, "group_admin", "Group Admin"),
    REQUESTOR_GROUP_ADMIN(3, "requestor_group_admin", "Requestor Admin"),
    REQUESTOR_GROUP_USER(4, "requestor_group_user", "Requestor User");

    private final int level;
    private final String keycloakRoleName;
    private final String displayName;

    UserType(int level, String keycloakRoleName, String displayName) {
        this.level = level;
        this.keycloakRoleName = keycloakRoleName;
        this.displayName = displayName;
    }

    public int getLevel() {
        return level;
    }

    public String getKeycloakRoleName() {
        return keycloakRoleName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if this user type has at least the specified privilege level.
     * Lower level number = higher privilege.
     */
    public boolean hasAtLeastPrivilege(UserType required) {
        return this.level <= required.level;
    }

    /**
     * Check if this user type is the master admin.
     */
    public boolean isMasterAdmin() {
        return this == JADDAR_MASTER_ADMIN;
    }

    /**
     * Check if this user type can manage groups (level 2+).
     */
    public boolean canManageGroups() {
        return this.level <= GROUP_ADMIN.level;
    }

    /**
     * Check if this user type can manage users/agreements in groups (level 3+).
     */
    public boolean canManageGroupContent() {
        return this.level <= REQUESTOR_GROUP_ADMIN.level;
    }

    /**
     * Get UserType from Keycloak role name.
     * Supports both old role names (for backwards compatibility) and new role names.
     */
    public static UserType fromKeycloakRole(String roleName) {
        if (roleName == null) {
            return REQUESTOR_GROUP_USER;
        }

        // Check new role names
        for (UserType type : values()) {
            if (type.keycloakRoleName.equalsIgnoreCase(roleName)) {
                return type;
            }
        }

        // Backwards compatibility with old role names
        return switch (roleName.toLowerCase()) {
            case "agreement_master", "master" -> JADDAR_MASTER_ADMIN;
            case "agreement_admin", "admin" -> JADDAR_MASTER_ADMIN; // Promote old admin to master
            default -> REQUESTOR_GROUP_USER;
        };
    }

    /**
     * Determine the highest privilege UserType from a list of Keycloak roles.
     */
    public static UserType fromKeycloakRoles(java.util.List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return REQUESTOR_GROUP_USER;
        }

        UserType highest = REQUESTOR_GROUP_USER;
        for (String role : roles) {
            UserType type = fromKeycloakRole(role);
            if (type.level < highest.level) {
                highest = type;
            }
        }
        return highest;
    }
}
