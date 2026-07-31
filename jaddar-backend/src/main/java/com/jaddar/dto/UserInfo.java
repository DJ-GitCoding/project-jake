/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * User information from OIDC. Ported from {@code backend/models/auth.py :: UserInfo}.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserInfo {

    private String sub;
    private String name;
    private String email;
    private String picture;
}
