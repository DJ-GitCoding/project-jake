/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dto;

import lombok.Data;

import java.util.List;

/**
 * Wrapper for Requestor Manager API responses ({success, message, data, errors}).
 * Ported from backend/models/agreement.py :: AgreementServerApiResponse.
 */
@Data
public class AgreementServerApiResponse {

    private boolean success;

    private String message;

    private AgreementsResponse data;

    private List<String> errors;
}
