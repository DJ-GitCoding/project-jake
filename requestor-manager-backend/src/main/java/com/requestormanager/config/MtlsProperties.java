/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * mTLS config. When enabled, adds a client-auth TLS listener on {@code mtls.port}
 * alongside the plain HTTP port, and outbound calls present a client cert.
 * When disabled, everything runs plain HTTP (local dev escape hatch).
 */
@Component
@ConfigurationProperties(prefix = "mtls")
@Data
public class MtlsProperties {

    private boolean enabled = false;

    /** Client-auth TLS listener port. */
    private int port = 8443;

    private String keyStore;
    private String keyStorePassword;
    private String keyStoreType = "PKCS12";

    private String trustStore;
    private String trustStorePassword;
    private String trustStoreType = "PKCS12";

    /** Plain-HTTP port to mTLS port map for rewriting DB-stored peer URLs, e.g. "8081:8481,8082:8482". */
    private String portMap = "";
}
