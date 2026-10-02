/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jaddar.enums.QueryType;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of an RDAP query, returned directly as JSON in several routes.
 * Ported from backend/services/rdap_service.py :: RDAPResponse (pydantic model).
 *
 * Field wire-names mirror the pydantic model (snake_case where applicable).
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class RdapResponse {

    private String query;

    @JsonProperty("query_type")
    private QueryType queryType;

    @JsonProperty("raw_data")
    private Map<String, Object> rawData = new HashMap<>();

    @JsonProperty("parsed_data")
    private Map<String, Object> parsedData = new HashMap<>();

    private Instant timestamp = Instant.now();

    private boolean success = true;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("agreement_ids")
    private List<Integer> agreementIds = new ArrayList<>();

    private String source;

    @JsonProperty("rdap_server")
    private String rdapServer;
}
