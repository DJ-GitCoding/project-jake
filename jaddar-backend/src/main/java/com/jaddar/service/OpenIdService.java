/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.config.JaddarProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenID Connect discovery + endpoint URL rewriting. Port of the discovery/config logic in
 * {@code backend/main.py} ({@code get_openid_configuration}, {@code get_auth_config}, plus the
 * internal/public token endpoint rewriting used by token exchange and logout).
 *
 * <p>The discovery document is fetched once and cached. On fetch failure, a default set of Keycloak
 * endpoints (derived from {@code jaddar.keycloak-auth-url}) is returned WITHOUT being cached (so a
 * later call can retry) — matching the Python behaviour.
 */
@Slf4j
@Service
public class OpenIdService {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JaddarProperties props;
    private final WebClient keycloakWebClient;
    private final ObjectMapper objectMapper;

    /** Internal hostnames that must be replaced with the public base in browser-facing URLs. */
    private final List<String> internalBaseVariations;

    /** Cached discovery doc (empty until first successful fetch). */
    private volatile Map<String, Object> openidConfigCache;

    public OpenIdService(JaddarProperties props,
                         @Qualifier("keycloakWebClient") WebClient keycloakWebClient,
                         ObjectMapper objectMapper) {
        this.props = props;
        this.keycloakWebClient = keycloakWebClient;
        this.objectMapper = objectMapper;
        this.internalBaseVariations = buildInternalBaseVariations(props.getKeycloakAuthUrl());
    }

    /**
     * Internal auth origins to rewrite to the public base: the configured internal
     * Keycloak origin plus the standard docker/localhost service origins.
     */
    private static List<String> buildInternalBaseVariations(String keycloakAuthUrl) {
        List<String> variations = new ArrayList<>();
        String origin = originOf(keycloakAuthUrl);
        if (origin != null && !origin.isBlank()) {
            variations.add(origin);
        }
        if (!variations.contains("http://keycloak:8080")) variations.add("http://keycloak:8080");
        if (!variations.contains("http://localhost:8080")) variations.add("http://localhost:8080");
        return variations;
    }

    /** Extract the scheme://host[:port] origin from a URL, or null if unparseable. */
    private static String originOf(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            java.net.URI u = java.net.URI.create(url);
            if (u.getScheme() == null || u.getHost() == null) return null;
            String origin = u.getScheme() + "://" + u.getHost();
            if (u.getPort() != -1) origin += ":" + u.getPort();
            return origin;
        } catch (Exception e) {
            return null;
        }
    }

    private String openidConfigUrl() {
        return props.getKeycloakAuthUrl() + "/.well-known/openid-configuration";
    }

    /**
     * Fetch (and cache) the OpenID configuration from the auth server. On failure, returns default
     * Keycloak endpoints without caching. Mirrors {@code get_openid_configuration}.
     */
    public Map<String, Object> getOpenidConfiguration() {
        Map<String, Object> cached = openidConfigCache;
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        String url = openidConfigUrl();
        try {
            String body = keycloakWebClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
            Map<String, Object> config = objectMapper.readValue(body, MAP_TYPE);
            openidConfigCache = config;
            log.info("OpenID configuration loaded successfully");
            log.info("Authorization endpoint: {}", config.get("authorization_endpoint"));
            return config;
        } catch (Exception e) {
            log.error("Failed to fetch OpenID configuration from {}: {}", url, e.toString());
            return defaultEndpoints();
        }
    }

    private Map<String, Object> defaultEndpoints() {
        String base = props.getKeycloakAuthUrl();
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("authorization_endpoint", base + "/protocol/openid-connect/auth");
        defaults.put("token_endpoint", base + "/protocol/openid-connect/token");
        defaults.put("userinfo_endpoint", base + "/protocol/openid-connect/userinfo");
        defaults.put("end_session_endpoint", base + "/protocol/openid-connect/logout");
        defaults.put("jwks_uri", base + "/protocol/openid-connect/certs");
        return defaults;
    }

    /** Public Keycloak base (publicAuthUrl with the trailing "/realms/master" stripped). */
    public String publicBase() {
        return props.getPublicAuthUrl().replace("/realms/master", "");
    }

    /**
     * Build the frontend auth config (browser-facing endpoints, with internal hostnames swapped for
     * the public base). Mirrors {@code get_auth_config}.
     */
    public Map<String, Object> buildAuthConfig() {
        Map<String, Object> config = getOpenidConfiguration();
        String publicBase = publicBase();

        String authEndpoint = rewriteToPublic(str(config.get("authorization_endpoint")), publicBase);
        String tokenEndpoint = rewriteToPublic(str(config.get("token_endpoint")), publicBase);
        String userinfoEndpoint = rewriteToPublic(str(config.get("userinfo_endpoint")), publicBase);
        String endSessionEndpoint = rewriteToPublic(str(config.get("end_session_endpoint")), publicBase);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authorization_endpoint", authEndpoint);
        result.put("token_endpoint", tokenEndpoint);
        result.put("userinfo_endpoint", userinfoEndpoint);
        result.put("end_session_endpoint", endSessionEndpoint);
        result.put("client_id", props.getClientId());
        result.put("redirect_uri", props.getRedirectUri());
        result.put("scope", "openid profile email");
        log.info("Returning auth config: {}", result);
        return result;
    }

    /**
     * The INTERNAL token endpoint the backend must use to talk to Keycloak directly. Rewrites the
     * public auth base (and localhost) to the configured internal Keycloak origin, so the discovered
     * endpoint is reachable from inside the network regardless of the deployment's public domain.
     */
    public String internalTokenEndpoint() {
        Map<String, Object> config = getOpenidConfiguration();
        String tokenEndpoint = str(config.get("token_endpoint"));
        if (tokenEndpoint == null || tokenEndpoint.isEmpty()) {
            tokenEndpoint = props.getKeycloakAuthUrl() + "/protocol/openid-connect/token";
        }
        String internalOrigin = originOf(props.getKeycloakAuthUrl());
        if (internalOrigin != null && !internalOrigin.isBlank()) {
            tokenEndpoint = tokenEndpoint.replace("http://localhost:8080", internalOrigin);
            String publicBase = publicBase();
            if (publicBase != null && !publicBase.isBlank()) {
                tokenEndpoint = tokenEndpoint.replace(publicBase, internalOrigin);
            }
        }
        return tokenEndpoint;
    }

    /** The userinfo endpoint (as discovered / defaulted). */
    public String userinfoEndpoint() {
        return str(getOpenidConfiguration().get("userinfo_endpoint"));
    }

    /**
     * The browser-facing end_session endpoint, with the internal keycloak host swapped for the
     * public base. Mirrors the rewrite in {@code logout}.
     */
    public String publicEndSessionEndpoint() {
        Map<String, Object> config = getOpenidConfiguration();
        String endSession = str(config.get("end_session_endpoint"));
        if (endSession == null || endSession.isEmpty()) {
            endSession = props.getKeycloakAuthUrl() + "/protocol/openid-connect/logout";
        }
        return endSession.replace("http://keycloak:8080", publicBase());
    }

    private String rewriteToPublic(String endpoint, String publicBase) {
        if (endpoint == null) {
            return "";
        }
        String out = endpoint;
        for (String internal : internalBaseVariations) {
            out = out.replace(internal, publicBase);
        }
        return out;
    }

    private static String str(Object o) {
        return o != null ? String.valueOf(o) : null;
    }
}
