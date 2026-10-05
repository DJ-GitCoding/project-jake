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
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "template_user_fields", indexes = {
    @Index(name = "idx_user_field_template", columnList = "template_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemplateUserField {

    public static final Map<String, String> STANDARD = standardFields();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private AgreementTemplate template;

    @Column(name = "field_key", nullable = false, length = 100)
    private String key;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean standard = false;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    private static Map<String, String> standardFields() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("first_name", "First Name");
        m.put("last_name", "Last Name");
        m.put("email", "Email");
        m.put("phone", "Phone Number");
        m.put("street_address", "Street Address");
        m.put("city", "City");
        m.put("state_province", "State / Province");
        m.put("postal_code", "Postal Code");
        m.put("country", "Country");
        return java.util.Collections.unmodifiableMap(m);
    }

    /** The key a custom field is identified by: its label in snake case. */
    public static String keyFor(String label) {
        String key = label.trim().toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return key.length() > 100 ? key.substring(0, 100) : key;
    }
}
