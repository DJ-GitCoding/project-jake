/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * RDAP SecureDNS/DS data entity
 */
@Entity
@Table(name = "rdap_secure_dns", indexes = {
    @Index(name = "idx_rdap_secure_dns_entity", columnList = "rdap_entity_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RdapSecureDns {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The RDAP entity (domain) this DS data belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rdap_entity_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private RdapEntity rdapEntity;

    /**
     * Key tag
     */
    @Column(name = "key_tag")
    private Integer keyTag;

    /**
     * Algorithm number
     */
    @Column(name = "algorithm")
    private Integer algorithm;

    /**
     * Digest type
     */
    @Column(name = "digest_type")
    private Integer digestType;

    /**
     * Digest value
     */
    @Column(name = "digest")
    private String digest;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}