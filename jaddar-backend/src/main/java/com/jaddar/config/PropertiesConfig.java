/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@link JaddarProperties} binding. Kept separate so the properties class itself stays a
 * plain POJO (no component scanning surprises).
 */
@Configuration
@EnableConfigurationProperties(JaddarProperties.class)
public class PropertiesConfig {
}
