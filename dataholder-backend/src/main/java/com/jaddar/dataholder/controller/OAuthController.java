/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.controller;

import com.jaddar.dataholder.service.KeycloakTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OAuth Controller for third-party token operations via Keycloak.
 * Used for testing RDAP queries with external OAuth tokens.
 */
@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
@Slf4j
public class OAuthController {

    private final KeycloakTokenService keycloakTokenService;

    /**
     * Get OAuth/Keycloak configuration for the frontend
     */
    @GetMapping("/config")
    public ResponseEntity<?> getOAuthConfig() {
        return ResponseEntity.ok(keycloakTokenService.getOAuthConfig());
    }

    /**
     * Get token using username/password from Keycloak (for testing third-party access)
     */
    @PostMapping("/token/password")
    public ResponseEntity<?> getTokenByPassword(@RequestBody PasswordTokenRequest request) {
        try {
            var response = keycloakTokenService.getTokenByPassword(
                request.getUsername(),
                request.getPassword()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Keycloak token request failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "authentication_failed",
                "message", "Unable to authenticate with the identity provider. Please verify your credentials and try again."
            ));
        }
    }

    /**
     * Introspect/validate a Keycloak token
     */
    @PostMapping("/introspect")
    public ResponseEntity<?> introspectToken(@RequestBody IntrospectRequest request) {
        try {
            var info = keycloakTokenService.introspectToken(request.getToken());
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            log.error("Token introspection failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "introspection_failed",
                "message", "Unable to validate the provided token."
            ));
        }
    }

    @lombok.Data
    public static class PasswordTokenRequest {
        private String username;
        private String password;
    }

    @lombok.Data
    public static class IntrospectRequest {
        private String token;
    }
}
