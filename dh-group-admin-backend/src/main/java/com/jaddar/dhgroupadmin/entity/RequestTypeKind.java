/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.entity;

/**
 * What a request type actually does when a requestor runs it.
 *
 * <p>{@link #RDAP} is the original behaviour and the default for every pre-existing
 * row: the requestor runs an RDAP query against a data holder, governed by the
 * request type's access level and RDAP parameters.
 *
 * <p>{@link #RDRS} instead submits a registration data request to ICANN's
 * Registration Data Request Service using the requestor's own ICANN credentials.
 * It never reaches a data holder, so access level, RDAP parameters, confidential
 * and exigent flags and the query-value regex are all meaningless for it.
 */
public enum RequestTypeKind {
    RDAP,
    RDRS
}
