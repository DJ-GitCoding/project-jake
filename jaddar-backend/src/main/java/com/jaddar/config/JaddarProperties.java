/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Strongly-typed binding for the {@code jaddar.*} configuration (mirrors the FastAPI env vars).
 * Bound from {@code application.yml}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jaddar")
public class JaddarProperties {

    /** Internal Keycloak realm URL (used by the backend to talk to Keycloak). */
    private String keycloakAuthUrl;

    /** Public-facing Keycloak realm URL (used by browsers). */
    private String publicAuthUrl;

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String webOrigin;
    private String adminEmail;
    private String requestorAgentId;

    /** Per-IP rate limit (requests per minute). */
    private int rateLimitRpm = 240;

    /** Downstream service base URLs. */
    private String requestorManagerUrl;
    private String dataholderUrl;
    private String dhGroupAdminUrl;

    /**
     * Comma-separated list of public data holder URLs that map to the internal
     * {@link #dataholderUrl} for backend-to-backend calls. Configured per deployment.
     */
    private String dataholderPublicUrls;

    /** Comma-separated list of allowed CORS origins. */
    private String corsAllowedOrigins;

    /** Frontend base URL (used for password-reset links). */
    private String frontendUrl;

    @NestedConfigurationProperty
    private PasswordReset passwordReset = new PasswordReset();

    @NestedConfigurationProperty
    private Rdrs rdrs = new Rdrs();

    @Getter
    @Setter
    public static class PasswordReset {
        /** Password reset token expiry, in minutes. */
        private int expiryMinutes = 60;
    }

    /** ICANN Registration Data Request Service integration. */
    @Getter
    @Setter
    public static class Rdrs {

        /** Turns the RDRS endpoints off entirely when false. */
        private boolean enabled = true;

        /** Base URL of the sidecar that drives ICANN's browser-only login. */
        private String sidecarUrl;

        /** Shared secret presented to the sidecar as X-Rdrs-Secret. */
        private String sidecarSecret;

        /**
         * ICANN's public, unauthenticated app config. Read at runtime rather than
         * hardcoding the API host and Okta client id, both of which ICANN can rotate.
         */
        private String configUrl = "https://rdrs.icann.org/api/config";

        /** How long a cached copy of that config stays fresh, in seconds. */
        private long configTtlSeconds = 3600;

        /**
         * Upper bound on how long a cached RDRS session is kept, in seconds. The real
         * expiry comes from the Okta token's own {@code exp}; this only caps it.
         */
        private long sessionMaxSeconds = 3600;

        @NestedConfigurationProperty
        private Mail mail = new Mail();
    }

    @Getter
    @Setter
    public static class Mail {

        private boolean enabled = true;

        private String domain;

        private String apiBaseUrl = "https://api.mailgun.net";

        private String apiKey;

        private String webhookSigningKey;

        private long pollIntervalSeconds = 120;

        private int pollWindowHours = 24;
    }
}
