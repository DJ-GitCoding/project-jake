/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.rdrs;

import lombok.Getter;

/**
 * An RDRS failure that is safe to show a requestor.
 *
 * <p>The message is user-facing by contract: it must say what went wrong and what to
 * do about it, without leaking ICANN's internals, our own stack, or anything from the
 * credentials that were used. Anything diagnostic belongs in the log, not in here.
 */
@Getter
public class RdrsException extends RuntimeException {

    /**
     * Marks a failure of the requestor's ICANN RDRS credentials rather than their Jaddar
     * session. It must never be reported as 401: the BFF proxy reads that as our own session
     * dying and signs the user out of Jaddar entirely.
     */
    public static final String SIGN_IN_REQUIRED = "RDRS_SIGN_IN_REQUIRED";

    /**
     * Marks credentials ICANN refused. Distinct from {@link #SIGN_IN_REQUIRED}: the
     * requestor is already at the sign-in step and needs to correct what they typed.
     */
    public static final String SIGN_IN_REJECTED = "RDRS_SIGN_IN_REJECTED";

    private final int status;

    private final String code;

    public RdrsException(String message, int status) {
        this(message, status, null);
    }

    public RdrsException(String message, int status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
