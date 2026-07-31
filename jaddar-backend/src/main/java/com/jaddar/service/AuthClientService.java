/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.config.JaddarProperties;
import com.jaddar.dto.ClientCredentials;
import com.jaddar.dto.ClientRegistrationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing OpenID Connect clients and token validation.
 * Ported from backend/services/auth_service.py :: AuthService.
 *
 * <p>Token introspection performs LOCAL JWT validation using Keycloak's JWKS
 * (mirroring the Python PyJWKClient flow) rather than calling Keycloak's remote
 * introspection endpoint.</p>
 */
@Service
@Slf4j
public class AuthClientService {

    private final WebClient keycloakWebClient;
    private final ObjectMapper objectMapper;

    /** Internal Keycloak URL incl. realm path, e.g. http://keycloak:8080/realms/master. */
    private final String keycloakUrl;
    /** Public-facing Keycloak URL incl. realm path, e.g. http://localhost:8080/realms/master. */
    private final String publicAuthUrl;

    private final String adminUser;
    private final String adminPassword;

    /** Configured OAuth2 client used for the refresh-token grant (mirrors JADDAR_CLIENT_ID/JADDAR_CLIENT_SECRET). */
    private final String clientId;
    private final String clientSecret;

    // --- cached admin token (mirrors self.admin_token / self.token_expires) ---
    private volatile String adminToken;
    private volatile Instant tokenExpires;

    // --- lazily-initialized JWKS-backed decoder + openid config cache ---
    private volatile JwtDecoder jwtDecoder;
    private volatile Map<String, Object> openidConfigCache;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public AuthClientService(
            @Qualifier("keycloakWebClient") WebClient keycloakWebClient,
            ObjectMapper objectMapper,
            JaddarProperties properties,
            @Value("${KEYCLOAK_ADMIN:admin}") String adminUser,
            @Value("${KEYCLOAK_ADMIN_PASSWORD:}") String adminPassword) {
        this.keycloakWebClient = keycloakWebClient;
        this.objectMapper = objectMapper;
        this.keycloakUrl = properties.getKeycloakAuthUrl();
        this.publicAuthUrl = properties.getPublicAuthUrl();
        this.clientId = properties.getClientId();
        this.clientSecret = properties.getClientSecret();
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("KEYCLOAK_ADMIN_PASSWORD not set — admin operations will fail");
        }
    }

    /** Exposed for the /health endpoint (mirrors auth_service.public_auth_url). */
    public String getPublicAuthUrl() {
        return publicAuthUrl;
    }

    private String realmRoot(String urlWithRealm) {
        return urlWithRealm.replace("/realms/master", "");
    }

    // ------------------------------------------------------------------
    // Admin token
    // ------------------------------------------------------------------

    /** Get or refresh the Keycloak admin token (mirrors _get_admin_token). */
    @SuppressWarnings("unchecked")
    public synchronized String getAdminToken() {
        if (adminToken != null && tokenExpires != null && Instant.now().isBefore(tokenExpires)) {
            return adminToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", "admin-cli");
        form.add("username", adminUser);
        form.add("password", adminPassword);
        form.add("grant_type", "password");

        Map<String, Object> data = keycloakWebClient.post()
                .uri(keycloakUrl + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (data == null) {
            throw new IllegalStateException("Empty response obtaining Keycloak admin token");
        }

        this.adminToken = (String) data.get("access_token");
        int expiresIn = ((Number) data.get("expires_in")).intValue();
        // 30 second buffer, matching the Python implementation
        this.tokenExpires = Instant.now().plusSeconds(expiresIn - 30L);
        return this.adminToken;
    }

    // ------------------------------------------------------------------
    // JWKS / token introspection
    // ------------------------------------------------------------------

    /** Get or initialize the JWKS-backed decoder for token verification (mirrors _get_jwks_client). */
    private JwtDecoder getJwtDecoder() {
        if (jwtDecoder == null) {
            synchronized (this) {
                if (jwtDecoder == null) {
                    Map<String, Object> config = getOpenidConfiguration();
                    Object jwksUriObj = config.get("jwks_uri");
                    if (jwksUriObj == null) {
                        throw new IllegalStateException("No jwks_uri found in OpenID configuration");
                    }
                    String jwksUri = jwksUriObj.toString();

                    // Use the internal Keycloak URL for JWKS (mirror the Python string replacements).
                    String internalJwksUri = jwksUri.replace(realmRoot(publicAuthUrl), realmRoot(keycloakUrl));
                    internalJwksUri = internalJwksUri.replace("http://localhost:8080", realmRoot(keycloakUrl));

                    log.info("Initializing JWKS decoder with URI: {}", internalJwksUri);
                    // verify_aud=False, verify_iss=False, verify_exp=True (default in Nimbus).
                    this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(internalJwksUri).build();
                }
            }
        }
        return jwtDecoder;
    }

    /**
     * Introspect a token to validate it and extract claims via local JWT validation
     * using Keycloak's JWKS (mirrors introspect_token).
     *
     * @return the token claims if valid; {@code null} if invalid for non-JWT reasons.
     * @throws JwtException if the token is expired or otherwise invalid (mirrors the
     *         Python re-raise of ExpiredSignatureError / InvalidTokenError so the
     *         controller can branch on it).
     */
    public Map<String, Object> introspectToken(String token) {
        try {
            Jwt jwt = getJwtDecoder().decode(token);
            log.debug("Token introspection successful, sub={}", jwt.getSubject());
            return new HashMap<>(jwt.getClaims());
        } catch (JwtException e) {
            // Mirrors Python: ExpiredSignatureError / InvalidTokenError are re-raised.
            log.info("Token introspection: invalid token - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // Mirrors Python: any other exception returns None.
            log.error("Token introspection error: {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Client management
    // ------------------------------------------------------------------

    /** Create a new OpenID Connect client in Keycloak (mirrors create_client). */
    public ClientCredentials createClient(ClientRegistrationRequest request) {
        String token = getAdminToken();

        String clientId = "client_" + tokenUrlSafe(16);
        String clientSecret = tokenUrlSafe(32);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("application_type", request.getApplicationType());
        attributes.put("created_by", "api");
        attributes.put("created_at", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        Map<String, Object> clientConfig = new HashMap<>();
        clientConfig.put("clientId", clientId);
        clientConfig.put("name", request.getName());
        clientConfig.put("description", request.getDescription());
        clientConfig.put("enabled", true);
        clientConfig.put("clientAuthenticatorType", "client-secret");
        clientConfig.put("secret", clientSecret);
        clientConfig.put("redirectUris", request.getRedirectUris());
        clientConfig.put("webOrigins", request.getWebOrigins());
        clientConfig.put("publicClient", false);
        clientConfig.put("protocol", "openid-connect");
        clientConfig.put("standardFlowEnabled", true);
        clientConfig.put("directAccessGrantsEnabled", true);
        clientConfig.put("serviceAccountsEnabled", "service".equals(request.getApplicationType()));
        clientConfig.put("authorizationServicesEnabled", false);
        clientConfig.put("attributes", attributes);

        keycloakWebClient.post()
                .uri(realmRoot(keycloakUrl) + "/admin/realms/master/clients")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(clientConfig)
                .retrieve()
                .toBodilessEntity()
                .block();

        return ClientCredentials.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .name(request.getName())
                .description(request.getDescription())
                .redirectUris(request.getRedirectUris())
                .webOrigins(request.getWebOrigins())
                .createdAt(Instant.now())
                .metadataUrl(publicAuthUrl + "/.well-known/openid-configuration")
                .build();
    }

    /** Get the OpenID Connect discovery document with public URLs (mirrors get_openid_configuration). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOpenidConfiguration() {
        if (openidConfigCache != null) {
            return openidConfigCache;
        }

        Map<String, Object> config = keycloakWebClient.get()
                .uri(keycloakUrl + "/.well-known/openid-configuration")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (config == null) {
            throw new IllegalStateException("Empty OpenID configuration response");
        }

        String internalBase = realmRoot(keycloakUrl);
        String publicBase = realmRoot(publicAuthUrl);

        try {
            String configStr = objectMapper.writeValueAsString(config);
            configStr = configStr.replace(internalBase, publicBase);
            configStr = configStr.replace("http://localhost:8080", publicBase);
            config = objectMapper.readValue(configStr, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to rewrite OpenID configuration URLs", e);
        }

        this.openidConfigCache = config;
        return config;
    }

    /** Validate client credentials via client_credentials grant (mirrors validate_client). */
    public boolean validateClient(String clientId, String clientSecret) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "client_credentials");

        try {
            return Boolean.TRUE.equals(keycloakWebClient.post()
                    .uri(keycloakUrl + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .exchangeToMono(resp -> resp.releaseBody()
                            .thenReturn(resp.statusCode().value() == 200))
                    .block());
        } catch (Exception e) {
            log.warn("Client validation error for {}: {}", clientId, e.getMessage());
            return false;
        }
    }

    /** Revoke/delete a client by its clientId (mirrors revoke_client). */
    @SuppressWarnings("unchecked")
    public boolean revokeClient(String clientId) {
        String token = getAdminToken();

        // First, get the client's internal ID by clientId.
        List<Map<String, Object>> clients = keycloakWebClient.get()
                .uri(realmRoot(keycloakUrl) + "/admin/realms/master/clients?clientId={clientId}", clientId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (clients == null || clients.isEmpty()) {
            return false;
        }

        String internalId = (String) clients.get(0).get("id");

        try {
            Integer status = keycloakWebClient.delete()
                    .uri(realmRoot(keycloakUrl) + "/admin/realms/master/clients/" + internalId)
                    .header("Authorization", "Bearer " + token)
                    .exchangeToMono(resp -> resp.releaseBody().thenReturn(resp.statusCode().value()))
                    .block();
            return status != null && status == 204;
        } catch (WebClientResponseException e) {
            return e.getStatusCode().value() == 204;
        }
    }

    // ------------------------------------------------------------------
    // Refresh token grant
    // ------------------------------------------------------------------

    /** Thrown when Keycloak rejects a refresh request (non-200); the controller maps this to 401. */
    public static class TokenRefreshException extends RuntimeException {
        public TokenRefreshException(String message) {
            super(message);
        }
    }

    /**
     * Exchange a refresh token for new tokens using the configured client credentials
     * (mirrors the inline httpx call in the Python refresh_token route).
     *
     * @return the raw token response map from Keycloak.
     * @throws TokenRefreshException if Keycloak returns a non-200 status.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> refreshToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);

        ResponsePair pair = keycloakWebClient.post()
                .uri(keycloakUrl + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .exchangeToMono(resp -> resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new ResponsePair(resp.statusCode().value(), b)))
                .block();

        if (pair == null) {
            throw new TokenRefreshException("empty_response");
        }
        if (pair.status() != 200) {
            String snippet = pair.body() != null
                    ? pair.body().substring(0, Math.min(500, pair.body().length())) : null;
            log.warn("Token refresh failed: {} - {}", pair.status(), snippet);
            throw new TokenRefreshException("keycloak_error:" + pair.status());
        }

        try {
            return objectMapper.readValue(pair.body(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse refresh token response", e);
        }
    }

    /** Tiny carrier for status + raw body from a WebClient exchange. */
    private record ResponsePair(int status, String body) { }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** URL-safe token of approximately the given byte count (mirrors secrets.token_urlsafe). */
    private static String tokenUrlSafe(int nbytes) {
        byte[] buf = new byte[nbytes];
        SECURE_RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
