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
 * Credentials issued to a data holder upon acceptance into the group.
 * The data holder uses these to authenticate against the external API.
 */
@Entity
@Table(name = "dataholder_credentials", indexes = {
    @Index(name = "idx_dhcred_client_id", columnList = "client_id"),
    @Index(name = "idx_dhcred_dataholder", columnList = "dataholder_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DataHolderCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataholder_id", nullable = false)
    private String dataholderId;

    @Column(name = "dataholder_name")
    private String dataholderName;

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

    /** New credential pair; secret is plaintext until {@link #hashSecretForStorage()} is called. */
    public static DataHolderCredential generate(String dataholderId, String dataholderName) {
        return DataHolderCredential.builder()
                .dataholderId(dataholderId)
                .dataholderName(dataholderName)
                .clientId("dh-" + dataholderId.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8))
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
