/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Controlled API error. Throw this for any error that should map to a specific HTTP status with a
 * client-facing message. Handled by {@link GlobalExceptionHandler}, which renders
 * {@code {"error": "<message>"}} — mirroring the FastAPI {@code http_exception_handler}.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public ApiException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
