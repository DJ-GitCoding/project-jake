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
 * Credentials this group admin issues to a requestor manager for one subscription.
 *
 * <p>The subscription is the relationship, so the credential is scoped to it: presenting
 * it proves the caller is the requestor manager that owns this subscription, and nothing
 * more. Revoking one subscription's access never affects another.
 *
 * <p>Mirrors {@link DataHolderCredential}: the secret is shown once at generation and only
 * its BCrypt hash is stored.
 */
@Entity
@Table(name = "subscription_credentials", indexes = {
    @Index(name = "idx_subcred_client_id", columnList = "client_id"),
    @Index(name = "idx_subcred_request_id", columnList = "request_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubscriptionCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The subscription these credentials authorize. */
    @Column(name = "request_id", nullable = false)
    private String requestId;

    /** Denormalized for display in the admin UI and audit entries. */
    @Column(name = "requestor_group_name")
    private String requestorGroupName;

    @Column(name = "client_id", unique = true, nullable = false)
    private String clientId;

    @Column(name = "client_secret", nullable = false)
    private String clientSecret;

    /** When true, clientSecret holds the BCrypt hash; plaintext exists only at generation time. */
    @Column(name = "secret_hashed")
    @Builder.Default
    private Boolean secretHashed = false;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * The peer's Ed25519 public key, which it generated and registered with us. Requests are
     * verified against it; nothing secret is stored here, so this value leaking costs nothing.
     */
    @Column(name = "public_key", length = 500)
    private String publicKey;

    @Column(name = "public_key_registered_at")
    private LocalDateTime publicKeyRegisteredAt;

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
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** New credential pair; secret is plaintext until {@link #hashSecretForStorage()} is called. */
    public static SubscriptionCredential generate(String requestId, String requestorGroupName) {
        return SubscriptionCredential.builder()
                .requestId(requestId)
                .requestorGroupName(requestorGroupName)
                .clientId("sub-" + UUID.randomUUID().toString().substring(0, 8)
                        + "-" + UUID.randomUUID().toString().substring(0, 8))
                .clientSecret(UUID.randomUUID().toString())
                .secretHashed(false)
                .isActive(true)
                .build();
    }

    /** Hashes the secret for storage and returns the plaintext for one-time display. */
    public String hashSecretForStorage() {
        if (Boolean.TRUE.equals(secretHashed)) {
            throw new IllegalStateException("Secret is already hashed");
        }
        String plaintext = this.clientSecret;
        this.clientSecret = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(plaintext);
        this.secretHashed = true;
        return plaintext;
    }

    public boolean verifySecret(String rawSecret) {
        if (Boolean.TRUE.equals(secretHashed)) {
            return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(rawSecret, this.clientSecret);
        }
        /* Plaintext comparison when the stored secret is not a BCrypt hash. */
        return this.clientSecret.equals(rawSecret);
    }
}
