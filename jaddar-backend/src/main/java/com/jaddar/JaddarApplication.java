/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * JADDAR main backend — OpenID authentication broker and RDAP query service.
 * Ported from the original FastAPI service (backend/).
 */
@SpringBootApplication
public class JaddarApplication {
    public static void main(String[] args) {
        SpringApplication.run(JaddarApplication.class, args);
    }
}
