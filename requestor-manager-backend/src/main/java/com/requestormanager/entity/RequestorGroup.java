/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "requestor_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestorGroup {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Short code used to identify this requestor group in RDAP query parameters.
     * Format: ≤10 characters, letters/digits/hyphens only, cannot begin or end with a hyphen.
     * Example: "LEA-US", "REG-001"
     * Must be unique across all requestor groups.
     */
    @Column(name = "code", length = 10, unique = true)
    private String code;
    
    @Column(columnDefinition = "TEXT")
    private String description;

    // ==================== Default Contact Information ====================
    
    @Column(name = "default_organization")
    private String defaultOrganization;

    @Column(name = "default_contact_email")
    private String defaultContactEmail;

    @Column(name = "default_contact_phone")
    private String defaultContactPhone;

    @Column(name = "default_address")
    private String defaultAddress;

    @Column(name = "default_city")
    private String defaultCity;

    @Column(name = "default_state_province")
    private String defaultStateProvince;

    @Column(name = "default_postal_code")
    private String defaultPostalCode;

    @Column(name = "default_country")
    private String defaultCountry;

    /**
     * Group type (e.g., "law-enforcement", "registrar", "government")
     */
    @Column(name = "group_type")
    private String groupType;

    /**
     * Default token introspection URL for this requestor group.
     * Pre-filled into subscription requests so data holders know where
     * to validate bearer tokens from this group's members.
     */
    @Column(name = "default_introspection_url", length = 500)
    private String defaultIntrospectionUrl;

    // ==================== Timestamps and Audit ====================
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by_keycloak_id")
    private String createdByKeycloakId;
    
    @Column(name = "created_by_email")
    private String createdByEmail;
    
    @Column(name = "created_by_name")
    private String createdByName;

    // ==================== Relationships ====================
    
    @OneToMany(mappedBy = "requestorGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SubscriptionRequest> subscriptionRequests = new ArrayList<>();

    @OneToMany(mappedBy = "requestorGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DataHolderAgreement> dataHolderAgreements = new ArrayList<>();
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}