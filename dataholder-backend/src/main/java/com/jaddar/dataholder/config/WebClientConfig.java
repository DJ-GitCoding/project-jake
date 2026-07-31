/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dataholder.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@Slf4j
public class WebClientConfig {

    private final MtlsProperties mtlsProperties;

    public WebClientConfig(MtlsProperties mtlsProperties) {
        this.mtlsProperties = mtlsProperties;
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(10));

        if (mtlsProperties.isEnabled()) {
            try {
                io.netty.handler.ssl.SslContext sslContext =
                        MtlsSslContextFactory.buildNettyClientSslContext(mtlsProperties);
                httpClient = httpClient.secure(spec -> spec.sslContext(sslContext));
                log.info("mTLS enabled: WebClient will present a client certificate on outbound calls");
            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure mTLS for WebClient", e);
            }
        }

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
