/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Model for client credentials returned after registration.
 * Ported from backend/models/auth.py :: ClientCredentials.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientCredentials {

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("client_secret")
    private String clientSecret;

    private String name;

    private String description;

    @Builder.Default
    @JsonProperty("redirect_uris")
    private List<String> redirectUris = new ArrayList<>();

    @Builder.Default
    @JsonProperty("web_origins")
    private List<String> webOrigins = new ArrayList<>();

    /** Defaults to the registration time (UTC), mirroring datetime.utcnow(). */
    @Builder.Default
    @JsonProperty("created_at")
    private Instant createdAt = Instant.now();

    @JsonProperty("metadata_url")
    private String metadataUrl;
}
