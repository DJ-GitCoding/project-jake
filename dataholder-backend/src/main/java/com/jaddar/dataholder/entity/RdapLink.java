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
 * RDAP Link entity for storing links/references to related resources
 */
@Entity
@Table(name = "rdap_links", indexes = {
    @Index(name = "idx_rdap_links_entity", columnList = "rdap_entity_id"),
    @Index(name = "idx_rdap_links_rel", columnList = "rel")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RdapLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The RDAP entity this link belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rdap_entity_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "links"})
    private RdapEntity rdapEntity;

    /**
     * The target URL
     */
    @Column(name = "href", nullable = false)
    private String href;

    /**
     * Link relation type (self, related, alternate, etc.)
     */
    @Column(name = "rel")
    private String rel;

    /**
     * Media type of the target resource
     */
    @Column(name = "media_type")
    private String mediaType;

    /**
     * Human-readable title
     */
    @Column(name = "title")
    private String title;

    /**
     * Value (context-dependent)
     */
    @Column(name = "link_value")
    private String value;

    /**
     * Language tag for the target
     */
    @Column(name = "hreflang")
    private String hreflang;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}