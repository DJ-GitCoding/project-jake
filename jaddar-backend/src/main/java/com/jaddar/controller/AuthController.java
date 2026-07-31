/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaddar.config.JaddarProperties;
import com.jaddar.dto.LoginRequest;
import com.jaddar.dto.TokenRequest;
import com.jaddar.dto.TokenResponse;
import com.jaddar.dto.UserInfo;
import com.jaddar.exception.ApiException;
import com.jaddar.security.KeycloakAuthUtil;
import com.jaddar.service.LoggingService;
import com.jaddar.service.OpenIdService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Core authentication + health endpoints. Port of the corresponding handlers in
 * {@code backend/main.py}.
 *
 * <p>Owns: {@code GET /}, {@code GET /api/auth/config}, {@code POST /api/auth/token},
 * {@code GET /api/auth/userinfo}, {@code GET /api/protected}, {@code POST /api/auth/logout},
 * {@code GET /api/auth/openid-configuration}, {@code GET /.well-known/openid-configuration}.
 *
 * <p>Client registration/validation/revocation and introspection are owned by the AUTH module agent.
 */
@Slf4j
@RestController
public class AuthController {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JaddarProperties props;
    private final OpenIdService openIdService;
    private final WebClient keycloakWebClient;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<LoggingService> loggingServiceProvider;

    public AuthController(JaddarProperties props,
                          OpenIdService openIdService,
                          @Qualifier("keycloakWebClient") WebClient keycloakWebClient,
                          ObjectMapper objectMapper,
                          ObjectProvider<LoggingService> loggingServiceProvider) {
        this.props = props;
        this.openIdService = openIdService;
        this.keycloakWebClient = keycloakWebClient;
        this.objectMapper = objectMapper;
        this.loggingServiceProvider = loggingServiceProvider;
    }

    // ==================== Health ====================

    /** Health check. Mirrors {@code root()}. */
    @GetMapping("/")
    public Map<String, Object> root() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "healthy");
        out.put("service", "Python Backend with Keycloak OpenID and RDAP");
        return out;
    }

    // ==================== Auth config ====================

    /** Frontend auth configuration. Mirrors {@code get_auth_config}. */
    @GetMapping("/api/auth/config")
    public Map<String, Object> getAuthConfig() {
        return openIdService.buildAuthConfig();
    }

    // ==================== Token exchange ====================

    /** Exchange an authorization code for tokens. Mirrors {@code exchange_code_for_token}. */
    @PostMapping("/api/auth/token")
    public TokenResponse exchangeCodeForToken(@Valid @RequestBody TokenRequest request,
                                              HttpServletRequest httpRequest) {
        String tokenEndpoint = openIdService.internalTokenEndpoint();
        log.info("Exchanging code for token at: {}", tokenEndpoint);

        String clientIp = clientIp(httpRequest);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", request.getCode());
        form.add("redirect_uri", props.getRedirectUri());
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());

        try {
            String body = keycloakWebClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);

            TokenResponse response = objectMapper.readValue(body, TokenResponse.class);

            logAuthEvent("token_exchange", true, null, props.getClientId(), clientIp,
                    Map.of("grant_type", "authorization_code"));

            return response;
        } catch (Exception e) {
            log.error("Token exchange failed: {}", e.toString());
            logAuthEvent("token_exchange", false, null, props.getClientId(), clientIp,
                    Map.of("error", e.toString()));
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to exchange code for token");
        }
    }

    // ==================== Direct login (password grant) ====================

    /**
     * Direct username/password login via the OAuth2 Resource Owner Password Credentials grant.
     * Lets users sign in from the app itself instead of being redirected to the Keycloak-hosted
     * login page. Returns the same token set as {@link #exchangeCodeForToken}.
     */
    @PostMapping("/api/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest) {
        String tokenEndpoint = openIdService.internalTokenEndpoint();
        log.info("Direct password login at: {}", tokenEndpoint);

        String clientIp = clientIp(httpRequest);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        form.add("username", request.getUsername());
        form.add("password", request.getPassword());
        form.add("scope", "openid profile email");

        try {
            String body = keycloakWebClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(TIMEOUT);

            TokenResponse response = objectMapper.readValue(body, TokenResponse.class);

            logAuthEvent("login", true, null, props.getClientId(), clientIp,
                    Map.of("grant_type", "password"));

            return response;
        } catch (Exception e) {
            log.warn("Direct password login failed for user '{}': {}",
                    request.getUsername(), e.toString());
            logAuthEvent("login", false, null, props.getClientId(), clientIp,
                    Map.of("error", e.toString()));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password");
        }
    }

    // ==================== Userinfo ====================

    /** Current user info from token claims, falling back to the userinfo endpoint. Mirrors {@code get_user_info}. */
    @GetMapping("/api/auth/userinfo")
    public UserInfo getUserInfo(@AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        UserInfo info = new UserInfo();
        info.setSub(KeycloakAuthUtil.sub(jwt));
        info.setName(KeycloakAuthUtil.name(jwt));
        info.setEmail(KeycloakAuthUtil.email(jwt));
        info.setPicture(jwt.getClaimAsString("picture"));

        // If minimal info, fetch from the userinfo endpoint using the raw bearer token.
        if ((info.getName() == null || info.getName().isEmpty())
                && (info.getEmail() == null || info.getEmail().isEmpty())) {
            String token = bearerToken(httpRequest);
            String userinfoEndpoint = openIdService.userinfoEndpoint();
            if (token != null && userinfoEndpoint != null && !userinfoEndpoint.isEmpty()) {
                try {
                    String body = keycloakWebClient.get()
                            .uri(userinfoEndpoint)
                            .header("Authorization", "Bearer " + token)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(TIMEOUT);
                    Map<String, Object> fetched = objectMapper.readValue(body, MAP_TYPE);
                    if (fetched.get("sub") != null) {
                        info.setSub(String.valueOf(fetched.get("sub")));
                    }
                    if (fetched.get("name") != null) {
                        info.setName(String.valueOf(fetched.get("name")));
                    }
                    if (fetched.get("email") != null) {
                        info.setEmail(String.valueOf(fetched.get("email")));
                    }
                    if (fetched.get("picture") != null) {
                        info.setPicture(String.valueOf(fetched.get("picture")));
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch userinfo: {}", e.toString());
                }
            }
        }

        return info;
    }

    // ==================== Protected example ====================

    /** Example protected endpoint. Mirrors {@code protected_route}. */
    @GetMapping("/api/protected")
    public Map<String, Object> protectedRoute(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "This is a protected endpoint");
        out.put("user_sub", KeycloakAuthUtil.sub(jwt));
        out.put("token_claims", jwt.getClaims());
        return out;
    }

    // ==================== Logout ====================

    /** Returns the Keycloak logout URL. Mirrors {@code logout}. */
    @PostMapping("/api/auth/logout")
    public Map<String, Object> logout(@RequestBody(required = false) Map<String, Object> body,
                                      HttpServletRequest httpRequest) {
        String idToken = body != null && body.get("id_token") != null
                ? String.valueOf(body.get("id_token")) : null;
        String clientIp = clientIp(httpRequest);

        String endSession = openIdService.publicEndSessionEndpoint();

        StringBuilder url = new StringBuilder(endSession)
                .append("?post_logout_redirect_uri=")
                .append(urlEncode(props.getWebOrigin() + "/login"));
        if (idToken != null) {
            url.append("&id_token_hint=").append(urlEncode(idToken));
        }

        logAuthEvent("logout", true, null, null, clientIp, null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("logout_url", url.toString());
        return out;
    }

    // ==================== OpenID discovery ====================

    /** OpenID Connect discovery document. Mirrors {@code get_openid_configuration_endpoint}. */
    @GetMapping("/api/auth/openid-configuration")
    public Map<String, Object> openidConfiguration() {
        return openIdService.getOpenidConfiguration();
    }

    /** Alternative well-known discovery endpoint. Mirrors {@code well_known_openid_configuration}. */
    @GetMapping("/.well-known/openid-configuration")
    public Map<String, Object> wellKnownOpenidConfiguration() {
        return openIdService.getOpenidConfiguration();
    }

    // ==================== Helpers ====================

    private void logAuthEvent(String eventType, boolean success, String userSub, String clientId,
                              String ip, Map<String, Object> details) {
        LoggingService loggingService = loggingServiceProvider.getIfAvailable();
        if (loggingService != null) {
            try {
                loggingService.logAuthEvent(eventType, success, userSub, clientId, ip, details);
            } catch (Exception e) {
                log.warn("logAuthEvent failed: {}", e.toString());
            }
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return null;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
