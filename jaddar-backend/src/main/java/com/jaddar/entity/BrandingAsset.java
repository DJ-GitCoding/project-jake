/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Single-row store for the deployment's custom logo.
 *
 * <p>Always id={@link #SINGLETON_ID}: uploading replaces the row, removing deletes it,
 * and an absent row means "fall back to the built-in logo". The bytes live in the
 * database rather than on disk so no persistent volume is needed and the logo survives
 * container replacement.
 */
@Entity
@Table(name = "branding_asset")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BrandingAsset {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /** Original upload filename, kept for display in the admin UI only. */
    @Column(name = "filename", length = 255)
    private String filename;

    /** Validated image MIME type, echoed back as the Content-Type when serving. */
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "data", nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "size_bytes", nullable = false)
    private Integer sizeBytes;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
