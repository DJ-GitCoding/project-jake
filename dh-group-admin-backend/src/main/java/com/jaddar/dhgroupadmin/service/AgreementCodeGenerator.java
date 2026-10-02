/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.service;

import com.jaddar.dhgroupadmin.repository.AgreementTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Mints the system-managed code on an AgreementTemplate: the first 16 hex characters of
 * sha256("&lt;template ref&gt;:&lt;template name&gt;:&lt;random salt&gt;").
 *
 * <p>The agreement's counterpart to {@link RequestTypeCodeGenerator}. A data holder names
 * the agreements it honours for a query by this code, so what travels is an opaque handle
 * rather than the template's own id or name.
 */
@Component
@RequiredArgsConstructor
public class AgreementCodeGenerator {

    public static final int CODE_LENGTH = 16;

    public static final Pattern CODE_PATTERN = Pattern.compile("^[0-9a-f]{" + CODE_LENGTH + "}$");

    private static final int MAX_ATTEMPTS = 100;

    private final AgreementTemplateRepository templateRepository;

    /** True when the code has the shape this generator produces. */
    public static boolean isValid(String code) {
        return code != null && CODE_PATTERN.matcher(code).matches();
    }

    /** Mint a code that no persisted template already holds. */
    public String generate(String templateRef, String name) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = mint(templateRef, name, UUID.randomUUID().toString());
            if (!templateRepository.existsByAgreementCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException(
                "Could not mint a unique agreement code after " + MAX_ATTEMPTS + " attempts");
    }

    /** The digest itself, without the uniqueness check. */
    static String mint(String templateRef, String name, String salt) {
        String seed = (templateRef != null ? templateRef : "")
                + ':' + (name != null ? name : "")
                + ':' + salt;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, CODE_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
