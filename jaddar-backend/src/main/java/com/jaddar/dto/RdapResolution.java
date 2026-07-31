/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of resolving a query to one or more RDAP base URLs.
 * Mirrors the dict returned by the Python IANABootstrapService.resolve_* methods:
 *   base_urls, source ("iana"|"custom"), data_holder_id, data_holder_name,
 *   requires_auth, auth_type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RdapResolution {

    private List<String> baseUrls;

    /** "iana" or "custom". */
    private String source;

    private Long dataHolderId;

    private String dataHolderName;

    private boolean requiresAuth;

    private String authType;
}
