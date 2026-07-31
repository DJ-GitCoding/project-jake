/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.entity;

import com.jaddar.enums.RequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Local tracking of a user's RDAP request — mirrors state from the data holder for quick access
 * and history. Ported from {@code models.database.RdapRequestHistory}.
 */
@Getter
@Setter
@Entity
@Table(name = "rdap_request_history")
public class RdapRequestHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", unique = true, nullable = false, length = 100)
    private String requestId;

    @Column(name = "user_sub", nullable = false, length = 255)
    private String userSub;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "query_type", nullable = false, length = 50)
    private String queryType;

    @Column(name = "query_value", nullable = false, length = 255)
    private String queryValue;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "requeststatus", nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "agreements_used", columnDefinition = "jsonb")
    private List<String> agreementsUsed;

    @Column(name = "access_level_requested")
    private Integer accessLevelRequested;

    @Column(name = "access_level_granted")
    private Integer accessLevelGranted;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rdap_data", columnDefinition = "jsonb")
    private Map<String, Object> rdapData;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "data_holder_id", length = 100)
    private String dataHolderId;

    @Column(name = "data_holder_name", length = 255)
    private String dataHolderName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
