/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jaddar.entity.DataHolder;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Response model for a data holder.
 * Mirrors pydantic {@code DataHolderResponse} (from_attributes=True).
 */
@Data
public class DataHolderResponse {

    private Long id;
    private String name;
    private String description;

    @JsonProperty("base_urls")
    private List<String> baseUrls;

    private List<String> tlds = new ArrayList<>();

    @JsonProperty("ip_ranges")
    private List<String> ipRanges = new ArrayList<>();

    @JsonProperty("asn_ranges")
    private List<List<Integer>> asnRanges = new ArrayList<>();

    @JsonProperty("requires_auth")
    private boolean requiresAuth = false;

    @JsonProperty("auth_type")
    private String authType = "none";

    @JsonProperty("is_active")
    private boolean isActive = true;

    @JsonProperty("created_by")
    private String createdBy;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    /** Build a response DTO from the shared {@link DataHolder} entity. */
    public static DataHolderResponse fromEntity(DataHolder h) {
        DataHolderResponse r = new DataHolderResponse();
        r.setId(h.getId());
        r.setName(h.getName());
        r.setDescription(h.getDescription());
        r.setBaseUrls(h.getBaseUrls());
        r.setTlds(h.getTlds() != null ? h.getTlds() : new ArrayList<>());
        r.setIpRanges(h.getIpRanges() != null ? h.getIpRanges() : new ArrayList<>());
        r.setAsnRanges(h.getAsnRanges() != null ? h.getAsnRanges() : new ArrayList<>());
        r.setRequiresAuth(Boolean.TRUE.equals(h.getRequiresAuth()));
        r.setAuthType(h.getAuthType());
        r.setActive(Boolean.TRUE.equals(h.getIsActive()));
        r.setCreatedBy(h.getCreatedBy());
        r.setCreatedAt(h.getCreatedAt());
        r.setUpdatedAt(h.getUpdatedAt());
        return r;
    }
}
