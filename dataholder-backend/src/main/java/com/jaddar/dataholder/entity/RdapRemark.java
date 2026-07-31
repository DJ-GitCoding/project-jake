/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RDAP Remark/Notice entity for storing descriptive text
 */
@Entity
@Table(name = "rdap_remarks", indexes = {
    @Index(name = "idx_rdap_remarks_entity", columnList = "rdap_entity_id"),
    @Index(name = "idx_rdap_remarks_type", columnList = "remark_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RdapRemark {

    public enum RemarkType {
        REMARK,
        NOTICE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The RDAP entity this remark belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rdap_entity_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "remarks"})
    private RdapEntity rdapEntity;

    /**
     * Type: remark or notice
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "remark_type", nullable = false)
    @Builder.Default
    private RemarkType remarkType = RemarkType.REMARK;

    /**
     * Title/heading of the remark
     */
    @Column(name = "title")
    private String title;

    /**
     * Description text lines
     */
    @ElementCollection
    @CollectionTable(name = "rdap_remark_descriptions", joinColumns = @JoinColumn(name = "rdap_remark_id"))
    @Column(name = "description")
    @Builder.Default
    private List<String> description = new ArrayList<>();

    /**
     * Remark type classification (object truncated due to authorization, etc.)
     */
    @Column(name = "remark_type_value")
    private String remarkTypeValue;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (description == null) description = new ArrayList<>();
        if (remarkType == null) remarkType = RemarkType.REMARK;
    }

    public void addDescription(String line) {
        if (description == null) description = new ArrayList<>();
        description.add(line);
    }
}