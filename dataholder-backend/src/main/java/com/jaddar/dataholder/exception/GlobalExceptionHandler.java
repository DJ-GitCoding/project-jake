/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.exception;

import com.jaddar.dataholder.service.FileSecurityService.SecurityRejectedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global fallback error handler for any exception that escapes a controller's own
 * try/catch. Renders every handled error as JSON {@code {"success": false, "error": "<message>"}}
 * — the dominant error-body shape used by this backend's controllers — so the React
 * frontend contract is preserved.
 *
 * <p>Rules:
 * <ul>
 *   <li>{@link IllegalArgumentException}/{@link IllegalStateException} carry hand-written domain
 *       messages in this codebase, so their message is echoed (400).</li>
 *   <li>Spring data-access exceptions carry raw SQL / constraint text and are NEVER echoed —
 *       they return a generic message and the real cause is logged server-side.</li>
 *   <li>The catch-all returns a generic message and logs the exception at ERROR.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Domain-level bad input — message is a safe hand-written string in this codebase. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Domain-level illegal state — message is a safe hand-written string in this codebase. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Bean-validation failures on @Valid request bodies — defaultMessage is safe. */
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

    /**
     * Constraint violations (unique/foreign-key/not-null). Carries raw SQL/constraint text —
     * never echoed. Logged with the exception.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Data integrity violation", ex);
        return body(HttpStatus.CONFLICT,
                "The operation conflicts with existing data. Please check your input and try again.");
    }

    /**
     * Any other data-access failure (connection, query, mapping). Carries raw JDBC/SQL text —
     * never echoed. Logged with the exception.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(DataAccessException ex) {
        log.error("Data access error", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                "A database error occurred. Please try again later or contact support.");
    }

    /**
     * File security rejection that escaped a controller catch. The scan details are NOT echoed;
     * only the safe threat category/severity are exposed, matching the local-catch shape.
     */
    @ExceptionHandler(SecurityRejectedException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityRejected(SecurityRejectedException ex) {
        log.warn("SECURITY: file rejected (threatType={}, severity={})", ex.getThreatType(), ex.getSeverity());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", false);
        map.put("error", "File rejected by the security scan.");
        map.put("securityThreat", true);
        map.put("threatType", ex.getThreatType());
        map.put("severity", ex.getSeverity());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(map);
    }

    /** Anything else -> 500 with a generic message; full detail is logged. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again or contact support.");
    }

    private static String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", false);
        map.put("error", message != null ? message : "");
        return ResponseEntity.status(status).body(map);
    }
}
