/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/** Named {@link WebClient} beans for outbound HTTP. Inject by name with {@code @Qualifier}. */
@Configuration
public class WebClientConfig {

    /** Generous default outbound body buffer (RDAP/discovery payloads can be large). */
    private static final int MAX_IN_MEMORY_BYTES = 16 * 1024 * 1024; // 16 MB

    private final ObjectMapper objectMapper;
    private final MtlsProperties mtlsProperties;
    private final MtlsEndpoints mtlsEndpoints;

    /** Client cert + composite trust (dev CA and JVM default CAs), built when mTLS is enabled; null otherwise. */
    private final io.netty.handler.ssl.SslContext clientSslContext;

    public WebClientConfig(ObjectMapper objectMapper, MtlsProperties mtlsProperties, MtlsEndpoints mtlsEndpoints) {
        this.objectMapper = objectMapper;
        this.mtlsProperties = mtlsProperties;
        this.mtlsEndpoints = mtlsEndpoints;
        if (mtlsProperties.isEnabled()) {
            try {
                this.clientSslContext =
                        MtlsSslContextFactory.buildNettyClientSslContextWithSystemTrust(mtlsProperties);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to configure mTLS for outbound WebClients", e);
            }
        } else {
            this.clientSslContext = null;
        }
    }

    /** Builds a WebClient.Builder, decorating the netty client with the mTLS cert when enabled. */
    private WebClient.Builder baseBuilder(int timeoutSeconds, boolean followRedirects) {
        HttpClient httpClient = HttpClient.create()
                .followRedirect(followRedirects)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds * 1000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS)));

        if (clientSslContext != null) {
            httpClient = httpClient.secure(spec -> spec.sslContext(clientSslContext));
        }

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> {
                    c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BYTES);
                    c.defaultCodecs().jackson2JsonDecoder(
                            new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON));
                    c.defaultCodecs().jackson2JsonEncoder(
                            new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON));
                });
    }

    /**
     * Keycloak client. No baseUrl set on purpose — the auth flow calls absolute URLs
     * (token endpoint, JWKS, discovery) that are rewritten between internal/public hostnames.
     */
    @Bean("keycloakWebClient")
    public WebClient keycloakWebClient() {
        return baseBuilder(30, true).build();
    }

    /** Requestor Manager service (reached over its mTLS listener when mTLS is enabled). */
    @Bean("requestorManagerWebClient")
    public WebClient requestorManagerWebClient(JaddarProperties props) {
        return baseBuilder(30, true)
                .baseUrl(mtlsEndpoints.resolve(props.getRequestorManagerUrl()))
                .build();
    }

    /** Data Holder service (reached over its mTLS listener when mTLS is enabled). */
    @Bean("dataholderWebClient")
    public WebClient dataholderWebClient(JaddarProperties props) {
        return baseBuilder(30, true)
                .baseUrl(mtlsEndpoints.resolve(props.getDataholderUrl()))
                .build();
    }

    /** Plain client for arbitrary absolute RDAP URLs (follows redirects, 30s timeout). */
    @Bean("rdapWebClient")
    public WebClient rdapWebClient() {
        return baseBuilder(30, true).build();
    }
}
