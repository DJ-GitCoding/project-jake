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
import java.util.ArrayList;
import java.util.List;

/**
 * RDAP Nameserver entity for storing nameserver information
 */
@Entity
@Table(name = "rdap_nameservers", indexes = {
    @Index(name = "idx_rdap_nameservers_entity", columnList = "rdap_entity_id"),
    @Index(name = "idx_rdap_nameservers_ldh", columnList = "ldh_name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RdapNameserver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The RDAP entity (domain) this nameserver belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rdap_entity_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "nameservers"})
    private RdapEntity rdapEntity;

    /**
     * Handle/ID
     */
    @Column(name = "handle")
    private String handle;

    /**
     * LDH name of the nameserver
     */
    @Column(name = "ldh_name", nullable = false)
    private String ldhName;

    /**
     * Unicode name
     */
    @Column(name = "unicode_name")
    private String unicodeName;

    /**
     * IPv4 addresses
     */
    @ElementCollection
    @CollectionTable(name = "rdap_nameserver_ipv4", joinColumns = @JoinColumn(name = "rdap_nameserver_id"))
    @Column(name = "ipv4_address")
    @Builder.Default
    private List<String> ipv4Addresses = new ArrayList<>();

    /**
     * IPv6 addresses
     */
    @ElementCollection
    @CollectionTable(name = "rdap_nameserver_ipv6", joinColumns = @JoinColumn(name = "rdap_nameserver_id"))
    @Column(name = "ipv6_address")
    @Builder.Default
    private List<String> ipv6Addresses = new ArrayList<>();

    /**
     * Status values
     */
    @ElementCollection
    @CollectionTable(name = "rdap_nameserver_status", joinColumns = @JoinColumn(name = "rdap_nameserver_id"))
    @Column(name = "status")
    @Builder.Default
    private List<String> status = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (ipv4Addresses == null) ipv4Addresses = new ArrayList<>();
        if (ipv6Addresses == null) ipv6Addresses = new ArrayList<>();
        if (status == null) status = new ArrayList<>();
    }

    public void addIpv4Address(String ip) {
        if (ipv4Addresses == null) ipv4Addresses = new ArrayList<>();
        ipv4Addresses.add(ip);
    }

    public void addIpv6Address(String ip) {
        if (ipv6Addresses == null) ipv6Addresses = new ArrayList<>();
        ipv6Addresses.add(ip);
    }
}