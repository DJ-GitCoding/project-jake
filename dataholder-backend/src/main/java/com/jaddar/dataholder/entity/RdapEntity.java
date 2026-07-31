/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified RDAP Entity that stores structured RDAP data for domains, IPs, and ASNs.
 * This replaces the JSONB-based storage with explicitly modeled fields that can
 * have redaction rules applied to them.
 */
@Entity
@Table(name = "rdap_entities", indexes = {
    @Index(name = "idx_rdap_entities_object_type", columnList = "object_type"),
    @Index(name = "idx_rdap_entities_handle", columnList = "handle"),
    @Index(name = "idx_rdap_entities_ldh_name", columnList = "ldh_name"),
    @Index(name = "idx_rdap_entities_object_class", columnList = "object_class_name"),
    @Index(name = "idx_rdap_entities_policy_expression", columnList = "policy_expression_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RdapEntity {

    /**
     * Type of RDAP object
     */
    public enum ObjectType {
        DOMAIN,
        IP_NETWORK,
        AUTNUM,
        ENTITY,
        NAMESERVER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==================== Common RDAP Fields ====================

    /**
     * The type of RDAP object (domain, ip, autnum, entity, nameserver)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false)
    private ObjectType objectType;

    /**
     * RDAP objectClassName field
     */
    @Column(name = "object_class_name", nullable = false)
    private String objectClassName;

    /**
     * Unique handle/registry ID for this object
     */
    @Column(nullable = false)
    private String handle;

    /**
     * LDH (letters, digits, hyphens) name - used for domains and nameservers
     */
    @Column(name = "ldh_name")
    private String ldhName;

    /**
     * Unicode name (if different from LDH name)
     */
    @Column(name = "unicode_name")
    private String unicodeName;

    /**
     * Status values (active, inactive, locked, etc.)
     */
    @ElementCollection
    @CollectionTable(name = "rdap_entity_status", joinColumns = @JoinColumn(name = "rdap_entity_id"))
    @Column(name = "status")
    @Builder.Default
    private List<String> status = new ArrayList<>();

    /**
     * Port 43 WHOIS server
     */
    @Column(name = "port43")
    private String port43;

    // ==================== Domain-Specific Fields ====================

    /**
     * Whether DNSSEC is enabled (domains only)
     */
    @Column(name = "secure_dns_delegation_signed")
    private Boolean secureDnsDelegationSigned;

    /**
     * Zone signed flag
     */
    @Column(name = "secure_dns_zone_signed")
    private Boolean secureDnsZoneSigned;

    // ==================== IP Network-Specific Fields ====================

    /**
     * Start IP address (for IP networks)
     */
    @Column(name = "start_address")
    private String startAddress;

    /**
     * End IP address (for IP networks)
     */
    @Column(name = "end_address")
    private String endAddress;

    /**
     * IP version (v4 or v6)
     */
    @Column(name = "ip_version")
    private String ipVersion;

    /**
     * Network name
     */
    @Column(name = "network_name")
    private String networkName;

    /**
     * Network type (DIRECT ALLOCATION, REALLOCATION, etc.)
     */
    @Column(name = "network_type")
    private String networkType;

    /**
     * Parent handle for hierarchical networks
     */
    @Column(name = "parent_handle")
    private String parentHandle;

    /**
     * Country code
     */
    @Column(name = "country")
    private String country;

    // ==================== ASN-Specific Fields ====================

    /**
     * Start autonomous system number
     */
    @Column(name = "start_autnum")
    private Long startAutnum;

    /**
     * End autonomous system number (for ranges)
     */
    @Column(name = "end_autnum")
    private Long endAutnum;

    /**
     * ASN name
     */
    @Column(name = "autnum_name")
    private String autnumName;

    /**
     * ASN type
     */
    @Column(name = "autnum_type")
    private String autnumType;

    // ==================== Entity (Contact) Fields ====================

    /**
     * Full name (from vCard fn)
     */
    @Column(name = "contact_name")
    private String contactName;

    /**
     * Organization name (from vCard org)
     */
    @Column(name = "organization")
    private String organization;

    /**
     * Email address
     */
    @Column(name = "email")
    private String email;

    /**
     * Phone number
     */
    @Column(name = "phone")
    private String phone;

    /**
     * Fax number
     */
    @Column(name = "fax")
    private String fax;

    /**
     * Street address line 1
     */
    @Column(name = "address_street1")
    private String addressStreet1;

    /**
     * Street address line 2
     */
    @Column(name = "address_street2")
    private String addressStreet2;

    /**
     * City
     */
    @Column(name = "address_city")
    private String addressCity;

    /**
     * State/Province
     */
    @Column(name = "address_state")
    private String addressState;

    /**
     * Postal code
     */
    @Column(name = "address_postal_code")
    private String addressPostalCode;

    /**
     * Country code
     */
    @Column(name = "address_country")
    private String addressCountry;

    /**
     * Roles (registrant, admin, tech, billing, abuse, etc.)
     */
    @ElementCollection
    @CollectionTable(name = "rdap_entity_roles", joinColumns = @JoinColumn(name = "rdap_entity_id"))
    @Column(name = "role")
    @Builder.Default
    private List<String> roles = new ArrayList<>();

    /**
     * Public IDs (e.g., IANA registrar ID)
     */
    @ElementCollection
    @CollectionTable(name = "rdap_entity_public_ids", joinColumns = @JoinColumn(name = "rdap_entity_id"))
    @Column(name = "public_id")
    @Builder.Default
    private List<String> publicIds = new ArrayList<>();

    // ==================== Relationships ====================

    /**
     * Parent entity (for hierarchical relationships)
     * E.g., a domain has entities (contacts), an entity can have sub-entities
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "children", "parent"})
    private RdapEntity parent;

    /**
     * Child entities (contacts, nameservers, etc.)
     */
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "parent"})
    @Builder.Default
    private List<RdapEntity> children = new ArrayList<>();

    /**
     * Non-persisted raw custom columns from a mapped/custom contact row that are
     * not standard RDAP contact fields (e.g. "disclose"). Carried so that policy
     * scope conditions can reference these custom fields during selection. Not
     * stored in the database and not emitted in the RDAP response.
     */
    @Transient
    @Builder.Default
    @JsonIgnore
    private Map<String, Object> customAttributes = new HashMap<>();

    /**
     * Reference to the original domain record (for migration/linking)
     */
    @Column(name = "source_domain_id")
    private Long sourceDomainId;

    /**
     * Reference to the original IP record (for migration/linking)
     */
    @Column(name = "source_ip_id")
    private Long sourceIpId;

    /**
     * Reference to the original ASN record (for migration/linking)
     */
    @Column(name = "source_asn_id")
    private Long sourceAsnId;

    /**
     * Optional test data flag
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_data_flag_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private TestDataFlag testDataFlag;

    /**
     * Optional policy expression for redaction rules.
     * When set, the policy's redaction rules are applied to RDAP responses.
     * Nullable - defaults to system default policy if not set.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "policy_expression_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "redactionRules"})
    private PolicyExpression policyExpression;

    // ==================== Timestamps ====================

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = new ArrayList<>();
        if (roles == null) roles = new ArrayList<>();
        if (publicIds == null) publicIds = new ArrayList<>();
        if (children == null) children = new ArrayList<>();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== Helper Methods ====================

    public void addChild(RdapEntity child) {
        children.add(child);
        child.setParent(this);
    }

    public void removeChild(RdapEntity child) {
        children.remove(child);
        child.setParent(null);
    }

    public void addStatus(String statusValue) {
        if (status == null) status = new ArrayList<>();
        status.add(statusValue);
    }

    public void addRole(String role) {
        if (roles == null) roles = new ArrayList<>();
        roles.add(role);
    }

    /**
     * Get the display identifier for this entity
     */
    public String getDisplayIdentifier() {
        if (ldhName != null) return ldhName;
        if (contactName != null) return contactName;
        if (organization != null) return organization;
        if (startAddress != null && endAddress != null) return startAddress + " - " + endAddress;
        if (startAutnum != null) return "AS" + startAutnum;
        return handle;
    }
}