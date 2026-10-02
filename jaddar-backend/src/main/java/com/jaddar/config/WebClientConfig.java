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

    public WebClientConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    private WebClient.Builder baseBuilder(int timeoutSeconds, boolean followRedirects) {
        HttpClient httpClient = HttpClient.create()
                .followRedirect(followRedirects)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds * 1000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS)));

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

    /** Requestor Manager service. */
    @Bean("requestorManagerWebClient")
    public WebClient requestorManagerWebClient(JaddarProperties props) {
        return baseBuilder(30, true)
                .baseUrl(props.getRequestorManagerUrl())
                .build();
    }

    /** Data Holder service. */
    @Bean("dataholderWebClient")
    public WebClient dataholderWebClient(JaddarProperties props) {
        return baseBuilder(30, true)
                .baseUrl(props.getDataholderUrl())
                .build();
    }

    /** Plain client for arbitrary absolute RDAP URLs (follows redirects, 30s timeout). */
    @Bean("rdapWebClient")
    public WebClient rdapWebClient() {
        return baseBuilder(30, true).build();
    }

    /**
     * ICANN's RDRS API. Like {@link #rdapWebClient()} this talks to a public internet
     * host, so it relies on the composite trust store (dev CA + JVM default CAs). No
     * baseUrl — the API host comes from ICANN's own /api/config at runtime.
     *
     * <p>Redirects are NOT followed: {@code /users/authenticate} answers with the
     * session cookie on the first response, and following a redirect would drop it.
     */
    @Bean("rdrsWebClient")
    public WebClient rdrsWebClient() {
        return baseBuilder(30, false).build();
    }

    /**
     * The RDRS sidecar. A full ICANN login is a multi-hop browser journey and may park
     * on an MFA prompt, so this needs a far longer timeout than any other client here.
     */
    @Bean("rdrsSidecarWebClient")
    public WebClient rdrsSidecarWebClient(JaddarProperties props) {
        WebClient.Builder builder = baseBuilder(360, true);
        String sidecarUrl = props.getRdrs().getSidecarUrl();
        // Deployments without the sidecar leave this blank.
        if (sidecarUrl != null && !sidecarUrl.isBlank()) {
            builder.baseUrl(sidecarUrl);
        }
        return builder.build();
    }
}
