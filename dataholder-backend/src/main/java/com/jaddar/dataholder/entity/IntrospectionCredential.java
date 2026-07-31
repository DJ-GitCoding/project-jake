/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Stores introspection credentials received from the agreement server
 * when a subscription is activated. Each record contains the client_id
 * and client_secret that this data holder should use when introspecting
 * bearer tokens on incoming RDAP queries from the associated requestor group.
 */
@Entity
@Table(name = "introspection_credentials",
       uniqueConstraints = @UniqueConstraint(columnNames = "request_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntrospectionCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The subscription request ID (our internal reference)
     */
    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    /**
     * Reference to the agreement subscription (if still exists)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private AgreementSubscription subscription;

    /**
     * The Keycloak client ID to use for introspection
     */
    @Column(name = "client_id", nullable = false)
    private String clientId;

    /**
     * The Keycloak client secret
     */
    @Column(name = "client_secret", nullable = false)
    private String clientSecret;

    /**
     * The token introspection endpoint URL
     */
    @Column(name = "introspection_url", nullable = false)
    private String introspectionUrl;

    /**
     * The token endpoint URL (optional, for client_credentials grants)
     */
    @Column(name = "token_url")
    private String tokenUrl;

    /**
     * The requestor group name these credentials are associated with
     */
    @Column(name = "requestor_group_name")
    private String requestorGroupName;

    /**
     * The requestor group code
     */
    @Column(name = "requestor_group_code")
    private String requestorGroupCode;

    /**
     * Whether this credential is currently active
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * When this credential was last used for introspection
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /**
     * Number of times this credential has been used for introspection
     */
    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Long usageCount = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Increment usage counter and update last used timestamp
     */
    public void recordUsage() {
        this.usageCount++;
        this.lastUsedAt = LocalDateTime.now();
    }
}