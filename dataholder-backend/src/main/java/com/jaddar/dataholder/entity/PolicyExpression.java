/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "policy_expressions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyExpression {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    /**
     * JSON array of scope conditions that determine when this policy applies.
     * Each condition specifies an RDAP field, an operator, and a value.
     * All conditions must match (AND logic) for the policy to apply.
     */
    @Column(name = "scope_conditions", columnDefinition = "TEXT")
    private String scopeConditions;

    /**
     * Optional note returned to the requestor in RDAP responses when not empty.
     * Maximum 255 characters.
     */
    @Column(name = "note_to_requestor", length = 255)
    private String noteToRequestor;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    // Per-field sensitivity level mappings
    @OneToMany(mappedBy = "policyExpression", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<PolicyRedactionRule> redactionRules = new ArrayList<>();

    @Column(name = "created_at")
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

    public void addRedactionRule(PolicyRedactionRule rule) {
        redactionRules.add(rule);
        rule.setPolicyExpression(this);
    }

    public void removeRedactionRule(PolicyRedactionRule rule) {
        redactionRules.remove(rule);
        rule.setPolicyExpression(null);
    }

    public void setRedactionRules(List<PolicyRedactionRule> rules) {
        this.redactionRules.clear();
        if (rules != null) {
            for (PolicyRedactionRule rule : rules) {
                addRedactionRule(rule);
            }
        }
    }
}
