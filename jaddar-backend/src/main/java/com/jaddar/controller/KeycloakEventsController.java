/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.controller;

import com.jaddar.exception.ApiException;
import com.jaddar.security.KeycloakAuthUtil;
import com.jaddar.service.KeycloakEventsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for accessing Keycloak events via the Keycloak Admin API.
 *
 * <p>Faithful port of {@code backend/routers/keycloak_events.py}
 * (FastAPI router prefix {@code /api/admin/keycloak}).
 *
 * <p>Every endpoint except {@code /health} required admin in the Python module
 * ({@code Depends(require_admin)}); this is enforced here via
 * {@link KeycloakAuthUtil#isAdmin(Jwt)}. {@code /health} performs no auth check beyond the
 * valid-bearer-token requirement imposed by SecurityConfig.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/keycloak")
@RequiredArgsConstructor
public class KeycloakEventsController {

    private final KeycloakEventsService keycloakEventsService;

    private void requireAdmin(Jwt jwt) {
        if (!KeycloakAuthUtil.isAdmin(jwt)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin privileges required");
        }
    }

    /** GET /api/admin/keycloak/events — Get Keycloak login events. Admin required. */
    @GetMapping("/events")
    public ResponseEntity<Map<String, Object>> getKeycloakEvents(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "client", required = false) String client,
            @RequestParam(value = "user", required = false) String user,
            @RequestParam(value = "ip_address", required = false) String ipAddress,
            @RequestParam(value = "date_from", required = false) String dateFrom,
            @RequestParam(value = "date_to", required = false) String dateTo,
            @RequestParam(value = "first", defaultValue = "0") int first,
            @RequestParam(value = "max", defaultValue = "100") int max,
            @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        validateFirst(first);
        validateMax(max);
        return ResponseEntity.ok(keycloakEventsService.getKeycloakEvents(
                type, client, user, ipAddress, dateFrom, dateTo, first, max,
                KeycloakAuthUtil.sub(jwt)));
    }

    /** GET /api/admin/keycloak/events/introspection — Token introspection events. Admin required. */
    @GetMapping("/events/introspection")
    public ResponseEntity<Map<String, Object>> getIntrospectionEvents(
            @RequestParam(value = "client", required = false) String client,
            @RequestParam(value = "ip_address", required = false) String ipAddress,
            @RequestParam(value = "date_from", required = false) String dateFrom,
            @RequestParam(value = "date_to", required = false) String dateTo,
            @RequestParam(value = "include_errors", defaultValue = "true") boolean includeErrors,
            @RequestParam(value = "first", defaultValue = "0") int first,
            @RequestParam(value = "max", defaultValue = "100") int max,
            @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        validateFirst(first);
        validateMax(max);
        return ResponseEntity.ok(keycloakEventsService.getIntrospectionEvents(
                client, ipAddress, dateFrom, dateTo, includeErrors, first, max));
    }

    /** GET /api/admin/keycloak/events/logins — Login events (success + error). Admin required. */
    @GetMapping("/events/logins")
    public ResponseEntity<Map<String, Object>> getLoginEvents(
            @RequestParam(value = "client", required = false) String client,
            @RequestParam(value = "user", required = false) String user,
            @RequestParam(value = "ip_address", required = false) String ipAddress,
            @RequestParam(value = "include_errors", defaultValue = "true") boolean includeErrors,
            @RequestParam(value = "first", defaultValue = "0") int first,
            @RequestParam(value = "max", defaultValue = "100") int max,
            @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        validateFirst(first);
        validateMax(max);
        return ResponseEntity.ok(keycloakEventsService.getLoginEvents(
                client, user, ipAddress, includeErrors, first, max));
    }

    /** GET /api/admin/keycloak/events/types — Available event types. Admin required. */
    @GetMapping("/events/types")
    public ResponseEntity<Map<String, Object>> getEventTypes(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return ResponseEntity.ok(keycloakEventsService.getEventTypes());
    }

    /** GET /api/admin/keycloak/admin-events — Admin events (config changes). Admin required. */
    @GetMapping("/admin-events")
    public ResponseEntity<Map<String, Object>> getAdminEvents(
            @RequestParam(value = "operation_type", required = false) String operationType,
            @RequestParam(value = "resource_type", required = false) String resourceType,
            @RequestParam(value = "resource_path", required = false) String resourcePath,
            @RequestParam(value = "auth_realm", required = false) String authRealm,
            @RequestParam(value = "auth_client", required = false) String authClient,
            @RequestParam(value = "auth_user", required = false) String authUser,
            @RequestParam(value = "date_from", required = false) String dateFrom,
            @RequestParam(value = "date_to", required = false) String dateTo,
            @RequestParam(value = "first", defaultValue = "0") int first,
            @RequestParam(value = "max", defaultValue = "100") int max,
            @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        validateFirst(first);
        validateMax(max);
        return ResponseEntity.ok(keycloakEventsService.getAdminEvents(
                operationType, resourceType, resourcePath, authRealm, authClient, authUser,
                dateFrom, dateTo, first, max));
    }

    /** DELETE /api/admin/keycloak/events — Clear all Keycloak login events. Admin required. */
    @DeleteMapping("/events")
    public ResponseEntity<Map<String, Object>> clearKeycloakEvents(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return ResponseEntity.ok(keycloakEventsService.clearKeycloakEvents(KeycloakAuthUtil.sub(jwt)));
    }

    /** GET /api/admin/keycloak/events/config — Current events configuration. Admin required. */
    @GetMapping("/events/config")
    public ResponseEntity<Map<String, Object>> getEventsConfig(@AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return ResponseEntity.ok(keycloakEventsService.getEventsConfig());
    }

    /**
     * PUT /api/admin/keycloak/events/config — Update events configuration. Admin required.
     *
     * <p>{@code events_enabled} is a required query param in the Python module
     * ({@code Query(...)}); the others have defaults.
     */
    @PutMapping("/events/config")
    public ResponseEntity<Map<String, Object>> updateEventsConfig(
            @RequestParam(value = "events_enabled") boolean eventsEnabled,
            @RequestParam(value = "events_expiration", defaultValue = "604800") int eventsExpiration,
            @RequestParam(value = "admin_events_enabled", defaultValue = "false") boolean adminEventsEnabled,
            @RequestParam(value = "admin_events_details", defaultValue = "false") boolean adminEventsDetails,
            @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return ResponseEntity.ok(keycloakEventsService.updateEventsConfig(
                eventsEnabled, eventsExpiration, adminEventsEnabled, adminEventsDetails));
    }

    /** GET /api/admin/keycloak/health — Keycloak connectivity / admin API check. No admin check. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> keycloakHealthCheck() {
        return ResponseEntity.ok(keycloakEventsService.keycloakHealthCheck());
    }

    // --- query param constraint validation (mirrors FastAPI Query(ge=..., le=...)) ---

    private void validateFirst(int first) {
        if (first < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Input should be greater than or equal to 0");
        }
    }

    private void validateMax(int max) {
        if (max < 1) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Input should be greater than or equal to 1");
        }
        if (max > 1000) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Input should be less than or equal to 1000");
        }
    }
}
