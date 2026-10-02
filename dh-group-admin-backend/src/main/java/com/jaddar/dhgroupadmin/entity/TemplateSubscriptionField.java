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

/**
 * A value the data holder group requires a requestor to supply when subscribing to a template.
 * Mirrors RequestTypeCustomParameter, which does the same job at query time.
 */
@Entity
@Table(name = "template_subscription_fields", indexes = {
    @Index(name = "idx_subscription_field_template", columnList = "template_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TemplateSubscriptionField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private AgreementTemplate template;

    /** Key the submitted value is stored under. Unique within the template. */
    @Column(nullable = false, length = 100)
    private String name;

    /** One of: string, text, url, number, date, select, checkbox. */
    @Column(name = "data_type", nullable = false, length = 20)
    @Builder.Default
    private String dataType = "string";

    @Column(nullable = false)
    @Builder.Default
    private Boolean required = false;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * For the url type this holds the link the group publishes for the requestor to review;
     * for the other types it pre-fills the input.
     */
    @Column(name = "default_value")
    private String defaultValue;

    @Column(length = 255)
    private String placeholder;

    /** Comma-separated options for the select type. */
    @Column(name = "enum_values", columnDefinition = "TEXT")
    private String enumValues;

    @Column(name = "validation_regex", length = 500)
    private String validationRegex;

    @Column(name = "min_value")
    private String minValue;

    @Column(name = "max_value")
    private String maxValue;

    @Column(name = "max_length")
    private Integer maxLength;

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
}
