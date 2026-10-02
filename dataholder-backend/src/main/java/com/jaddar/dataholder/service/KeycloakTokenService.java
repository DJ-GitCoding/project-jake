/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakTokenService {

    private final WebClient.Builder webClientBuilder;

    @Value("${keycloak.auth-server-url:http://keycloak:8080}")
    private String keycloakUrl;

    @Value("${keycloak.realm:master}")
    private String realm;

    @Value("${keycloak.client-id:${KEYCLOAK_CLIENT_ID:jaddar-client}}")
    private String clientId;

    @Value("${keycloak.client-secret:${KEYCLOAK_CLIENT_SECRET:}}")
    private String clientSecret;

    @Value("${dataholder.oauth.scopes:openid,profile,email,groups}")
    private String scopes;

    private String getRealmUrl() {
        return keycloakUrl + "/realms/" + realm;
    }

    private String getTokenEndpoint() {
        return getRealmUrl() + "/protocol/openid-connect/token";
    }

    private String getIntrospectEndpoint() {
        return getRealmUrl() + "/protocol/openid-connect/token/introspect";
    }

    public OAuthConfig getOAuthConfig() {
        String externalKeycloakUrl = keycloakUrl
                .replace("https://keycloak:8443", "http://localhost:8080")
                .replace("keycloak:8080", "localhost:8080");
        String externalRealmUrl = externalKeycloakUrl + "/realms/" + realm;
        
        return OAuthConfig.builder()
                .authorizationEndpoint(externalRealmUrl + "/protocol/openid-connect/auth")
                .tokenEndpoint(externalRealmUrl + "/protocol/openid-connect/token")
                .userInfoEndpoint(externalRealmUrl + "/protocol/openid-connect/userinfo")
                .clientId(clientId)
                .scopes(Arrays.asList(scopes.split(",")))
                .build();
    }

    public TokenResponse getTokenByPassword(String username, String password) {
        log.info("Getting Keycloak token for user: {}", username);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", clientId);
        formData.add("username", username);
        formData.add("password", password);
        formData.add("scope", scopes.replace(",", " "));
        
        if (clientSecret != null && !clientSecret.isEmpty()) {
            formData.add("client_secret", clientSecret);
        }

        return executeTokenRequest(formData);
    }

    public TokenInfo introspectToken(String token) {
        log.info("Introspecting Keycloak token");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("token", token);
        formData.add("client_id", clientId);
        
        if (clientSecret != null && !clientSecret.isEmpty()) {
            formData.add("client_secret", clientSecret);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(getIntrospectEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("Empty response from introspection endpoint");
            }

            Boolean active = (Boolean) response.get("active");
            
            return TokenInfo.builder()
                    .active(active != null && active)
                    .sub((String) response.get("sub"))
                    .username((String) response.get("username"))
                    .preferredUsername((String) response.get("preferred_username"))
                    .email((String) response.get("email"))
                    .name((String) response.get("name"))
                    .groups(getListFromResponse(response, "groups"))
                    .exp(getLongFromResponse(response, "exp"))
                    .iss((String) response.get("iss"))
                    .build();

        } catch (Exception e) {
            log.error("Token introspection failed", e);
            throw new RuntimeException("Token introspection failed: " + e.getMessage());
        }
    }

    private TokenResponse executeTokenRequest(MultiValueMap<String, String> formData) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(getTokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("Empty response from token endpoint");
            }

            String accessToken = (String) response.get("access_token");
            
            TokenInfo userInfo = null;
            if (accessToken != null) {
                try {
                    userInfo = introspectToken(accessToken);
                } catch (Exception e) {
                    log.warn("Could not introspect token: {}", e.getMessage());
                }
            }

            return TokenResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken((String) response.get("refresh_token"))
                    .tokenType((String) response.get("token_type"))
                    .expiresIn(getIntFromResponse(response, "expires_in"))
                    .scope((String) response.get("scope"))
                    .userInfo(userInfo)
                    .build();

        } catch (Exception e) {
            log.error("Token request failed", e);
            throw new RuntimeException("Token request failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getListFromResponse(Map<String, Object> response, String key) {
        Object value = response.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return null;
    }

    private Integer getIntFromResponse(Map<String, Object> response, String key) {
        Object value = response.get(key);
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) return ((Long) value).intValue();
        if (value instanceof Double) return ((Double) value).intValue();
        return null;
    }

    private Long getLongFromResponse(Map<String, Object> response, String key) {
        Object value = response.get(key);
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof Double) return ((Double) value).longValue();
        return null;
    }

    @Data
    @Builder
    public static class OAuthConfig {
        private String authorizationEndpoint;
        private String tokenEndpoint;
        private String userInfoEndpoint;
        private String clientId;
        private List<String> scopes;
    }

    @Data
    @Builder
    public static class TokenResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private Integer expiresIn;
        private String scope;
        private TokenInfo userInfo;
    }

    @Data
    @Builder
    public static class TokenInfo {
        private boolean active;
        private String sub;
        private String username;
        private String preferredUsername;
        private String email;
        private String name;
        private List<String> groups;
        private Long exp;
        private String iss;
    }
}
