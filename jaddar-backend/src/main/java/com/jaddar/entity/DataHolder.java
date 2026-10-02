/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;

/**
 * Custom RDAP data holder configuration. Stores data holders that serve RDAP data for specific
 * TLDs, IP ranges, or ASN ranges (IANA RDAP bootstrap format, RFC 9224).
 *
 * <p>Ported from {@code models.database.DataHolder} including the {@code serves_*} helpers.
 */
@Getter
@Setter
@Entity
@Table(name = "data_holders")
public class DataHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "base_urls", columnDefinition = "jsonb", nullable = false)
    private List<String> baseUrls;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tlds", columnDefinition = "jsonb")
    private List<String> tlds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ip_ranges", columnDefinition = "jsonb")
    private List<String> ipRanges;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "asn_ranges", columnDefinition = "jsonb")
    private List<List<Integer>> asnRanges;

    @Column(name = "requires_auth", nullable = false)
    private Boolean requiresAuth = false;

    @Column(name = "auth_type", nullable = false, length = 50)
    private String authType = "bearer";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ==================== Behaviour helpers (port of Python serves_*) ====================

    /** Check if this data holder serves a given TLD (case-insensitive). */
    @Transient
    public boolean servesTld(String tld) {
        if (tlds == null || tlds.isEmpty() || tld == null) {
            return false;
        }
        String target = tld.toLowerCase();
        for (String t : tlds) {
            if (t != null && t.toLowerCase().equals(target)) {
                return true;
            }
        }
        return false;
    }

    /** Check if this data holder serves a given IP address (matched against the CIDR ranges). */
    @Transient
    public boolean servesIp(String ipAddress) {
        if (ipRanges == null || ipRanges.isEmpty() || ipAddress == null) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            for (String cidr : ipRanges) {
                if (cidr != null && cidrContains(cidr, addr)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Invalid IP -> not served (mirrors Python's ValueError swallow).
            return false;
        }
        return false;
    }

    /** Check if this data holder serves a given ASN (matched against the [start, end] pairs). */
    @Transient
    public boolean servesAsn(int asnNumber) {
        if (asnRanges == null || asnRanges.isEmpty()) {
            return false;
        }
        for (List<Integer> pair : asnRanges) {
            if (pair != null && pair.size() == 2 && pair.get(0) != null && pair.get(1) != null) {
                if (pair.get(0) <= asnNumber && asnNumber <= pair.get(1)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Test whether a CIDR range (e.g. "192.0.2.0/24" or "2001:db8::/32") contains the given address.
     * Pure-Java implementation that handles both IPv4 and IPv6, mirroring Python's
     * {@code ipaddress.ip_address(...) in ipaddress.ip_network(cidr, strict=False)}.
     */
    private static boolean cidrContains(String cidr, InetAddress addr) {
        try {
            String[] parts = cidr.split("/");
            InetAddress network = InetAddress.getByName(parts[0].trim());
            byte[] netBytes = network.getAddress();
            byte[] addrBytes = addr.getAddress();
            if (netBytes.length != addrBytes.length) {
                return false; // Different address families (IPv4 vs IPv6).
            }
            int prefix = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : netBytes.length * 8;
            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (netBytes[i] != addrBytes[i]) {
                    return false;
                }
            }
            if (remainingBits > 0) {
                int mask = (0xFF00 >> remainingBits) & 0xFF;
                if ((netBytes[fullBytes] & mask) != (addrBytes[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
