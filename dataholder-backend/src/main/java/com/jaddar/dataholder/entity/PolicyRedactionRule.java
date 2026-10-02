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
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Maps a data element (field) to a sensitivity level within a policy expression.
 *
 * The sensitivity level is used together with the request type's access level
 * to look up the redaction behavior in the global redaction rules matrix.
 *
 * For example, if a policy says "vcardArray.email" has sensitivityLevel=2
 * and the requester's access level is 1, the matrix row for
 * (accessLevel=1, sensitivityLevel=2, isEmpty=false) determines whether
 * the value is returned as FULL, REDACTED, or EMPTY.
 */
@Entity
@Table(name = "policy_redaction_rules",
       uniqueConstraints = @UniqueConstraint(columnNames = {"policy_expression_id", "object_type", "field_path"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyRedactionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_expression_id", nullable = false)
    @JsonIgnore
    private PolicyExpression policyExpression;

    /**
     * The RDAP object type this rule applies to
     * e.g., "domain", "entity", "nameserver", "ip", "autnum"
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false)
    private RdapObjectType objectType;

    /**
     * The field path within the RDAP object (dot notation for nested fields)
     * e.g., "handle", "ldhName", "vcardArray.email", "vcardArray.fn"
     */
    @Column(name = "field_path", nullable = false)
    private String fieldPath;

    /**
     * Human-readable display name for this field
     */
    @Column(name = "field_display_name")
    private String fieldDisplayName;

    /**
     * The sensitivity level assigned to this data element (0-3).
     * Used with the requester's access level to look up behavior
     * in the global redaction rules matrix.
     *
     * 0 = Public, 1 = Basic, 2 = Enhanced, 3 = Full
     */
    @Column(name = "sensitivity_level", nullable = false)
    private Integer sensitivityLevel = 0;

    /**
     * The validation level from the imported policy (0-3).
     * 0 = None (V0), 1 = Syntactical (V1), 2 = Operational (V2), 3 = Identification (V3).
     * NULL means not specified / not imported.
     */
    @Column(name = "validation_level")
    private Integer validationLevel;

    /**
     * Optional description/notes about this rule
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Order for display (lower = first)
     */
    @Column(name = "rule_order")
    private Integer ruleOrder = 0;

    /**
     * Whether this rule is enabled
     */
    @Column(name = "is_enabled")
    private Boolean isEnabled = true;

    /**
     * RDAP object types
     */
    public enum RdapObjectType {
        DOMAIN("Domain"),
        ENTITY("Entity"),
        NAMESERVER("Nameserver"),
        IP_NETWORK("IP Network"),
        AUTNUM("AS Number"),
        ALL("All Objects");

        private final String displayName;

        RdapObjectType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static RdapObjectType fromString(String value) {
            if (value == null) return ALL;
            for (RdapObjectType type : values()) {
                if (type.name().equalsIgnoreCase(value) ||
                    type.displayName.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            return ALL;
        }
    }
}
