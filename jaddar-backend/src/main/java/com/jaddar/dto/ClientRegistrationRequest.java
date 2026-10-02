/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Model for OpenID Connect client registration requests.
 * Ported from backend/models/auth.py :: ClientRegistrationRequest.
 */
@Data
public class ClientRegistrationRequest {

    private String name;

    private String description;

    @NotNull
    @JsonProperty("redirect_uris")
    private List<String> redirectUris = new ArrayList<>();

    @JsonProperty("web_origins")
    private List<String> webOrigins = new ArrayList<>();

    /** web, native, or service. */
    @JsonProperty("application_type")
    private String applicationType = "web";

    @JsonProperty("grant_types")
    private List<String> grantTypes = new ArrayList<>(Arrays.asList("authorization_code", "refresh_token"));
}
