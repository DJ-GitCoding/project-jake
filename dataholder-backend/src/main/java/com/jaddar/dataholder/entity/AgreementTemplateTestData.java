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
import java.time.LocalDateTime;

/**
 * Links an {@link AgreementTemplate} to an {@link RdapEntity} that should be
 * queried during the subscription testing phase.
 *
 * The data holder admin configures a list of these entries on a template.
 * When a subscription reaches the TESTING status and {@code run-test} is invoked,
 * each entry is looked up, queried through the RDAP pipeline using the template's
 * access levels and RDAP parameters, and the results are reported back in detail.
 *
 * Each entry can optionally specify which request type to test with (standard,
 * confidential, exigent) so that all template capabilities are exercised.
 */
@Entity
@Table(name = "agreement_template_test_data", indexes = {
    @Index(name = "idx_test_data_template", columnList = "template_id"),
    @Index(name = "idx_test_data_rdap_entity", columnList = "rdap_entity_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementTemplateTestData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The template this test data entry belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private AgreementTemplate template;

    /**
     * The RDAP entity to query during testing.
     * Must be a top-level entity (DOMAIN, IP_NETWORK, or AUTNUM).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rdap_entity_id", nullable = false)
    private RdapEntity rdapEntity;

    /**
     * The query type to use when looking up this entity during testing.
     * Must be one of: "domain", "ip", "asn".
     * Derived from the RdapEntity's objectType if not explicitly set.
     */
    @Column(name = "query_type", nullable = false, length = 20)
    private String queryType;

    /**
     * The query value to use when looking up this entity (e.g., "example.com", "192.0.2.0", "AS64496").
     * Derived from the RdapEntity if not explicitly set.
     */
    @Column(name = "query_value", nullable = false)
    private String queryValue;

    /**
     * Which request type name to test with (e.g., "standard", "confidential", "exigent").
     * If null, defaults to "standard".
     */
    @Column(name = "request_type_name", length = 50)
    @Builder.Default
    private String requestTypeName = "standard";

    /**
     * Human-readable label for this test entry (shown in UI during testing).
     * If null, auto-generated from queryType + queryValue.
     */
    @Column(name = "label")
    private String label;

    /**
     * Optional description of what this test case validates
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Whether to verify that sensitive contact fields are properly visible
     * (i.e. that the access level grants access to contact data).
     * When true, the test checks that registrant/admin/tech fields are present
     * in the response if the template's RDAP parameters allow them.
     */
    @Column(name = "verify_contact_access")
    @Builder.Default
    private Boolean verifyContactAccess = true;

    /**
     * Whether to verify that redacted fields are NOT present in the response
     * (i.e. that fields disabled in RDAP parameters are properly filtered out).
     */
    @Column(name = "verify_redaction")
    @Builder.Default
    private Boolean verifyRedaction = true;

    /**
     * Display order for UI presentation
     */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    /**
     * Whether this test entry is active
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    // ==================== Audit Fields ====================

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (requestTypeName == null) {
            requestTypeName = "standard";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== Helper Methods ====================

    /**
     * Get a display label for this test entry
     */
    @Transient
    public String getDisplayLabel() {
        if (label != null && !label.isBlank()) {
            return label;
        }
        return queryType.toUpperCase() + " lookup: " + queryValue +
               (!"standard".equalsIgnoreCase(requestTypeName) ? " (" + requestTypeName + ")" : "");
    }

    /**
     * Derive query type from an RdapEntity's object type
     */
    public static String deriveQueryType(RdapEntity entity) {
        if (entity == null || entity.getObjectType() == null) return "domain";
        return switch (entity.getObjectType()) {
            case DOMAIN -> "domain";
            case IP_NETWORK -> "ip";
            case AUTNUM -> "asn";
            default -> "domain";
        };
    }

    /**
     * Derive query value from an RdapEntity
     */
    public static String deriveQueryValue(RdapEntity entity) {
        if (entity == null) return "";
        return switch (entity.getObjectType()) {
            case DOMAIN -> entity.getLdhName() != null ? entity.getLdhName() : entity.getHandle();
            case IP_NETWORK -> entity.getStartAddress() != null ? entity.getStartAddress() : entity.getHandle();
            case AUTNUM -> entity.getStartAutnum() != null ? "AS" + entity.getStartAutnum() : entity.getHandle();
            default -> entity.getHandle();
        };
    }
}