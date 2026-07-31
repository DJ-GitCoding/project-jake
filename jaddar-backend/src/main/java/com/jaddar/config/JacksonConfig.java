/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

/**
 * Registers the {@link LenientInstantDeserializer} on the application's primary {@code ObjectMapper}
 * (used by Spring MVC for request bodies and, via {@link WebClientConfig}, by the outbound WebClients
 * when decoding responses from the other JADDAR services).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer lenientInstantCustomizer() {
        return builder -> builder.deserializerByType(Instant.class, new LenientInstantDeserializer());
    }
}
