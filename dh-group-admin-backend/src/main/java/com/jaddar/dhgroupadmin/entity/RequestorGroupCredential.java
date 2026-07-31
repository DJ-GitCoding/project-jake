/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Credentials issued to a requestor group upon acceptance.
 * The requestor group uses these to authenticate against the external API.
 */
@Entity
@Table(name = "requestor_group_credentials", indexes = {
    @Index(name = "idx_rgcred_client_id", columnList = "client_id"),
    @Index(name = "idx_rgcred_group_id", columnList = "requestor_group_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RequestorGroupCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requestor_group_id", nullable = false)
    private Long requestorGroupId;

    @Column(name = "requestor_group_code")
    private String requestorGroupCode;

    @Column(name = "client_id", unique = true, nullable = false)
    private String clientId;

    @Column(name = "client_secret", nullable = false)
    private String clientSecret;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    /**
     * Generate a new credential pair for a requestor group
     */
    public static RequestorGroupCredential generate(Long groupId, String groupCode) {
        return RequestorGroupCredential.builder()
                .requestorGroupId(groupId)
                .requestorGroupCode(groupCode)
                .clientId("rg-" + groupCode.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8))
                .clientSecret(UUID.randomUUID().toString())
                .isActive(true)
                .build();
    }
}
