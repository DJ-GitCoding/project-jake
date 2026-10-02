/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "dataholder_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataHolderUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 100)
    private String email;

    @Column(name = "full_name", length = 200)
    private String fullName;

    /**
     * User type: MASTER or ASSISTANT_ADMIN
     * MASTER has full access to everything including user management.
     * ASSISTANT_ADMIN has access to all features except user management.
     */
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String type = "ASSISTANT_ADMIN";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "ADMIN"; // Kept for backward compatibility

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    /**
     * Derives full name from first + last name when available.
     */
    @PrePersist
    @PreUpdate
    public void deriveFullName() {
        if (firstName != null || lastName != null) {
            String fn = firstName != null ? firstName.trim() : "";
            String ln = lastName != null ? lastName.trim() : "";
            this.fullName = (fn + " " + ln).trim();
        }
    }

    public boolean isMaster() {
        return "MASTER".equals(type);
    }

    public boolean isAssistantAdmin() {
        return "ASSISTANT_ADMIN".equals(type);
    }
}
