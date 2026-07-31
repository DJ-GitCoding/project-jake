/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Stateless OAuth2 resource-server security. Keycloak access tokens are validated against the realm
 * JWKS (configured via {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} in
 * application.yml). Mirrors the FastAPI behaviour: signature + expiry verified, issuer/audience NOT
 * verified (internal vs public issuer differ).
 *
 * <p>The public (no-auth) paths mirror the migration contract / the FastAPI routes that did not
 * require a token. Everything else needs a valid bearer token.
 *
 * <p>The custom filters (security headers, rate limit, request logging) are registered as
 * {@code @Component} {@link org.springframework.web.filter.OncePerRequestFilter}s and picked up by
 * Spring Boot's servlet filter chain automatically (ordered via {@code @Order}); they run for all
 * requests including the security chain.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Paths that do NOT require authentication. */
    private static final String[] PUBLIC_PATHS = {
            "/",
            "/api/auth/config",
            "/api/auth/token",
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/openid-configuration",
            "/.well-known/openid-configuration",
            // Password recovery (paths match the FastAPI router + the frontend axios calls)
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            // Auth-client endpoints that are public / self-authenticating in FastAPI
            "/api/auth/introspect",
            "/api/auth/refresh",
            "/api/auth/health",
            "/actuator/**"
    };

    private final JaddarProperties props;

    public SecurityConfig(JaddarProperties props) {
        this.props = props;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Allow CORS preflight without auth.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        String clientId = props.getClientId();
        OAuth2TokenValidator<Jwt> audience = jwt -> {
            List<String> aud = jwt.getAudience();
            boolean ok = clientId.equals(jwt.getClaimAsString("azp"))
                    || (aud != null && aud.contains(clientId));
            return ok ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "Token was not issued for this application", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), audience));
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = Arrays.stream(props.getCorsAllowedOrigins().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("*"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
