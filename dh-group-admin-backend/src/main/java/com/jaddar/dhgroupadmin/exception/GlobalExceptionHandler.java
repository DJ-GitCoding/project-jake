/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global error handler for the DH Group Admin backend.
 *
 * <p>Renders every handled error as JSON {@code {"success": false, "error": "<message>"}} with the
 * appropriate HTTP status, matching the error-body shape already returned by AdminController /
 * AuthController / ExternalController so the React frontend contract is preserved.
 *
 * <p>Domain messages ({@link IllegalArgumentException} / {@link IllegalStateException}) are echoed
 * because the services in this codebase throw clean, user-actionable messages. Everything else
 * (data-access failures, unexpected exceptions) is logged with the exception server-side and returned
 * as a generic message so backend internals never leak to the client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Domain validation / state errors carry clean, user-actionable messages -> 400. */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleDomain(RuntimeException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage() != null ? ex.getMessage() : "Invalid request");
    }

    /** Bean-validation failures on @Valid request bodies -> 400 with a field->message summary. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining(", "));
        if (message.isEmpty()) {
            message = "Validation failed";
        }
        return body(HttpStatus.BAD_REQUEST, message);
    }

    /** Constraint violations (unique keys, FKs, not-null) -> 409 GENERIC, never echo DB text. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Data integrity violation", ex);
        return body(HttpStatus.CONFLICT, "The request conflicts with existing data. Please review and try again.");
    }

    /** Any other data-access failure -> 500 GENERIC, never echo SQL/driver text. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(DataAccessException ex) {
        log.error("Data access error", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred. Please try again or contact support.");
    }

    /** Anything else -> 500 GENERIC, logged with the exception. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private static String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("success", false, "error", message != null ? message : ""));
    }
}
