/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.Arrays;

@Configuration
@Slf4j
public class WebConfig {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3003}")
    private String allowedOrigins;

    private final MtlsProperties mtlsProperties;

    public WebConfig(MtlsProperties mtlsProperties) {
        this.mtlsProperties = mtlsProperties;
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .forEach(config::addAllowedOrigin);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    /** Shared WebClient builder for outbound service calls, presenting the mTLS client cert when enabled. */
    @Bean
    public WebClient.Builder webClientBuilder() {
        WebClient.Builder builder = WebClient.builder();
        if (mtlsProperties.isEnabled()) {
            try {
                io.netty.handler.ssl.SslContext sslContext =
                        MtlsSslContextFactory.buildNettyClientSslContext(mtlsProperties);
                HttpClient httpClient = HttpClient.create().secure(spec -> spec.sslContext(sslContext));
                builder.clientConnector(new ReactorClientHttpConnector(httpClient));
                log.info("mTLS enabled: WebClient will present a client certificate on outbound calls");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure mTLS for WebClient", e);
            }
        }
        return builder;
    }
}
