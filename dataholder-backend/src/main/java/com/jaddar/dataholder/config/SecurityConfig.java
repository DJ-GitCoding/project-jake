/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
    private String allowedOrigins;

    @Value("${dataholder.jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "dataholder.jwt.secret must be at least 32 bytes of high-entropy material; refusing to start.");
        }
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/", "/health", "/api/health").permitAll()
                .requestMatchers("/api/agreements/external/**").permitAll()
                // Auth endpoints - public for login/refresh/validate
                .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/validate").permitAll()
                // Password recovery - public
                .requestMatchers("/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                // OAuth endpoints for third-party apps (Keycloak token exchange)
                .requestMatchers("/api/oauth/**").permitAll()
                // RDAP endpoints - authentication handled by token introspection
                .requestMatchers("/api/rdap/**", "/domain/**", "/ip/**", "/autnum/**").permitAll()
                // File view endpoint - public for inline preview in admin UI iframes
                .requestMatchers("/api/admin/files/view/**").permitAll()
                // File upload endpoint - public so requestor backends can forward files
                .requestMatchers("/api/admin/files/upload").permitAll()
                // Admin endpoints require JWT authentication
                .requestMatchers("/api/admin/**").authenticated()
                // User management requires authentication
                .requestMatchers("/api/auth/me", "/api/auth/change-password", "/api/auth/logout").authenticated()
                .requestMatchers("/api/auth/users", "/api/auth/users/**").authenticated()
                // Everything else
                .anyRequest().permitAll()
            )
            // Add JWT filter
            .addFilterBefore(new JwtAuthenticationFilter(jwtSecret), UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOrigins(origins);
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * JWT Authentication Filter
     */
    public static class JwtAuthenticationFilter extends OncePerRequestFilter {

        private final SecretKey signingKey;

        public JwtAuthenticationFilter(String jwtSecret) {
            this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                        FilterChain filterChain) throws ServletException, IOException {
            
            String authHeader = request.getHeader("Authorization");
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                try {
                    Claims claims = Jwts.parserBuilder()
                            .setSigningKey(signingKey)
                            .build()
                            .parseClaimsJws(token)
                            .getBody();

                    // Only accept access tokens, not refresh tokens
                    // Check 'userType' claim (new tokens) or 'type' claim (legacy tokens)
                    String tokenType = (String) claims.get("userType");
                    String legacyType = (String) claims.get("type");
                    boolean isAccessToken = "access".equals(tokenType) || "access".equals(legacyType);
                    boolean isRefreshToken = "refresh".equals(legacyType);
                    
                    if (isAccessToken && !isRefreshToken) {
                        String role = (String) claims.get("role");
                        UsernamePasswordAuthenticationToken authentication = 
                            new UsernamePasswordAuthenticationToken(
                                claims.getSubject(),
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + (role != null ? role : "USER")))
                            );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (Exception e) {
                    // Invalid token - continue without authentication
                    SecurityContextHolder.clearContext();
                }
            }
            
            filterChain.doFilter(request, response);
        }
    }
}