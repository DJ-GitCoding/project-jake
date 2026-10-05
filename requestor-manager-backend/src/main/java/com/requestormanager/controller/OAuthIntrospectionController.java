/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.controller;

import com.requestormanager.entity.SubscriptionCredential;
import com.requestormanager.service.RequestorIntrospectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/external/oauth/token")
@RequiredArgsConstructor
@Tag(name = "Token Introspection", description = "RFC 7662 introspection for data holders, with requestor member information")
public class OAuthIntrospectionController {

    private final RequestorIntrospectionService introspectionService;

    @PostMapping(value = "/introspect", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Introspect a requestor member's token",
            description = "Reports whether the token is active and, for a member, the member information the "
                    + "subscription's agreement requires (user_fields) and what the member lacks (missing_user_fields).")
    public ResponseEntity<?> introspect(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                        @RequestParam MultiValueMap<String, String> form) {
        Optional<SubscriptionCredential> client = introspectionService.authenticateClient(authorization);
        if (client.isEmpty()) return invalidClient();
        String token = form.getFirst("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_request",
                    "error_description", "The token parameter is required"));
        }
        return ResponseEntity.ok(introspectionService.introspect(client.get(), token));
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Obtain a client-credentials token for a subscription's introspection client")
    public ResponseEntity<?> token(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                   @RequestParam MultiValueMap<String, String> form) {
        Optional<SubscriptionCredential> client = introspectionService.authenticateClient(authorization);
        if (client.isEmpty()) return invalidClient();
        if (!"client_credentials".equals(form.getFirst("grant_type"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type",
                    "error_description", "Only the client_credentials grant is supported here"));
        }
        return introspectionService.clientCredentialsToken(client.get());
    }

    /** RFC 6749 §5.2: an unauthenticated client gets 401 with a challenge. */
    private static ResponseEntity<?> invalidClient() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"requestor-manager\"")
                .body(Map.of("error", "invalid_client"));
    }
}
