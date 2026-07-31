/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.time.Duration;

/**
 * RestTemplate for external API calls. When mTLS is enabled it's backed by Apache HttpClient 5
 * with the mTLS {@link SSLContext} (presents RM's client cert, trusts the dev CA); otherwise a
 * plain {@link SimpleClientHttpRequestFactory}.
 *
 * Gotcha: the truststore trusts only the dev CA. Plain-HTTP Keycloak calls are unaffected, but
 * adding public-HTTPS calls here would need a composite truststore (dev CA + JVM defaults).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RestTemplateConfig {

    private final MtlsProperties mtlsProperties;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        if (mtlsProperties.isEnabled()) {
            try {
                SSLContext sslContext = MtlsSslContextFactory.buildJavaxSslContext(mtlsProperties);

                SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);

                PoolingHttpClientConnectionManager connectionManager =
                        PoolingHttpClientConnectionManagerBuilder.create()
                                .setSSLSocketFactory(sslSocketFactory)
                                .build();

                CloseableHttpClient httpClient = HttpClients.custom()
                        .setConnectionManager(connectionManager)
                        .build();

                HttpComponentsClientHttpRequestFactory factory =
                        new HttpComponentsClientHttpRequestFactory(httpClient);
                factory.setConnectTimeout(10000);

                log.info("RestTemplate configured with mTLS (Apache HttpClient 5, client cert presented)");
                return builder
                        .requestFactory(() -> factory)
                        .build();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to build mTLS-enabled RestTemplate", e);
            }
        }

        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    public SimpleClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);
        return factory;
    }
}
