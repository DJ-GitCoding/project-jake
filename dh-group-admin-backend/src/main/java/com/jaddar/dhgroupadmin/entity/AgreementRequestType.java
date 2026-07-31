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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Represents a type of RDAP request that an agreement template supports.
 * Each request type defines its own access level, RDAP parameters,
 * and whether it supports confidential or exigent disclosures.
 */
@Entity
@Table(name = "agreement_request_types", indexes = {
    @Index(name = "idx_ga_request_type_template", columnList = "template_id"),
    @Index(name = "idx_ga_request_type_name", columnList = "name")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgreementRequestType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private AgreementTemplate template;

    @Column(nullable = false)
    private String name;

    @Column(name = "type_code")
    private Integer typeCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "access_level", nullable = false)
    @Builder.Default
    private Integer accessLevel = 0;

    @Column(name = "supports_confidential", nullable = false)
    @Builder.Default
    private Boolean supportsConfidential = false;

    @Column(name = "supports_exigent", nullable = false)
    @Builder.Default
    private Boolean supportsExigent = false;

    @Column(name = "requires_manual_approval", nullable = false)
    @Builder.Default
    private Boolean requiresManualApproval = true;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "rdap_parameters_id")
    private AgreementRdapParameters rdapParameters;

    @OneToMany(mappedBy = "requestType", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<RequestTypeCustomParameter> customParameters = new ArrayList<>();

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Optional regex pattern that the RDAP query value (e.g. domain name) must match
     * for this request type to accept the request. If the query value does not match,
     * the request is rejected with the configured error message.
     *
     * Example: ".*dbx.*" requires "dbx" to appear somewhere in the query value.
     */
    @Column(name = "query_value_regex", length = 500)
    private String queryValueRegex;

    /**
     * Error message returned to the requestor when the query value fails the regex check.
     * If not set, a default message is used.
     */
    @Column(name = "query_value_regex_error", length = 500)
    private String queryValueRegexError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (rdapParameters == null) {
            initializeRdapParametersForAccessLevel();
        }
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public void initializeRdapParametersForAccessLevel() {
        if (this.accessLevel == null || this.accessLevel == 0) {
            this.rdapParameters = AgreementRdapParameters.createPublicOnly();
        } else {
            this.rdapParameters = AgreementRdapParameters.createDefault();
        }
    }

    @Transient
    public AgreementRdapParameters getEffectiveRdapParameters() {
        if (rdapParameters != null) return rdapParameters;
        return accessLevel != null && accessLevel > 0
                ? AgreementRdapParameters.createDefault()
                : AgreementRdapParameters.createPublicOnly();
    }

    public void addCustomParameter(RequestTypeCustomParameter param) {
        customParameters.add(param);
        param.setRequestType(this);
    }

    public void removeCustomParameter(RequestTypeCustomParameter param) {
        customParameters.remove(param);
        param.setRequestType(null);
    }

    @Transient
    public boolean matchesRequest(boolean isConfidential, boolean isExigent) {
        if (isConfidential && !Boolean.TRUE.equals(supportsConfidential)) return false;
        if (isExigent && !Boolean.TRUE.equals(supportsExigent)) return false;
        if (!isConfidential && !isExigent) {
            return !Boolean.TRUE.equals(supportsConfidential) && !Boolean.TRUE.equals(supportsExigent);
        }
        return true;
    }
}
