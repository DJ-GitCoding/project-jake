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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Defines a custom parameter that can be associated with a request type.
 * These parameters define additional data fields that requestors must provide
 * when making requests of a given type (e.g., case numbers, reference documents).
 */
@Entity
@Table(name = "request_type_custom_parameters", indexes = {
    @Index(name = "idx_rt_custom_param_request_type", columnList = "request_type_id"),
    @Index(name = "idx_rt_custom_param_name", columnList = "request_type_id, name", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RequestTypeCustomParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_type_id", nullable = false)
    private AgreementRequestType requestType;

    /**
     * Parameter name (used as the field key). Must be unique within a request type.
     * Only letters, numbers, and underscores allowed.
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Data type of the parameter.
     * Supported values: string, integer, float, boolean, date, datetime,
     * file, email, url, enum, text, json
     */
    @Column(name = "data_type", nullable = false, length = 20)
    @Builder.Default
    private String dataType = "string";

    /**
     * Whether this parameter is required when making a request.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean required = false;

    /**
     * Human-readable description of the parameter.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Default value for the parameter (stored as string, parsed by client).
     */
    @Column(name = "default_value")
    private String defaultValue;

    /**
     * Placeholder/hint text for input fields.
     */
    @Column(length = 255)
    private String placeholder;

    /**
     * Comma-separated list of allowed values for enum type parameters.
     */
    @Column(name = "enum_values", columnDefinition = "TEXT")
    private String enumValues;

    /**
     * Regex pattern for validating string/text values.
     */
    @Column(name = "validation_regex", length = 500)
    private String validationRegex;

    /**
     * Minimum value for numeric parameters.
     */
    @Column(name = "min_value")
    private String minValue;

    /**
     * Maximum value for numeric parameters.
     */
    @Column(name = "max_value")
    private String maxValue;

    /**
     * Maximum character length for string/text parameters.
     */
    @Column(name = "max_length")
    private Integer maxLength;

    /**
     * Comma-separated list of allowed file extensions (e.g., ".pdf,.jpg,.png").
     */
    @Column(name = "allowed_file_types", length = 500)
    private String allowedFileTypes;

    /**
     * Maximum file size in megabytes for file type parameters.
     */
    @Column(name = "max_file_size_mb")
    private Integer maxFileSizeMb;

    /**
     * Content-format criteria for {@code file} parameters, evaluated by the document-scanner
     * sidecar when the data holder receives the upload. Free-form JSON so criteria can evolve
     * without a migration; absent/null keys are simply not enforced. Recognised keys:
     * {@code minPages, maxPages, requireTextLayer, allowScanned, minWordCount,
     * requiredTextPatterns[], forbiddenTextPatterns[], requireEmbeddedImage,
     * minEmbeddedImageWidth, minEmbeddedImageHeight, minWidth, minHeight, minDpi,
     * rejectEncrypted, maxOcrPages}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_criteria", columnDefinition = "jsonb")
    private Map<String, Object> fileCriteria;

    /**
     * Display order of the parameter within its request type.
     */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
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
}
