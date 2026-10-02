/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.service;

import com.jaddar.dhgroupadmin.repository.AgreementRequestTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Mints the system-managed type code on an AgreementRequestType: the first 16 hex characters
 * of sha256("<template ref>:<request type name>:<random salt>").
 */
@Component
@RequiredArgsConstructor
public class RequestTypeCodeGenerator {

    public static final int CODE_LENGTH = 16;

    public static final Pattern CODE_PATTERN = Pattern.compile("^[0-9a-f]{" + CODE_LENGTH + "}$");

    private static final int MAX_ATTEMPTS = 100;

    private final AgreementRequestTypeRepository requestTypeRepository;

    /** True when the code has the shape this generator produces. */
    public static boolean isValid(String code) {
        return code != null && CODE_PATTERN.matcher(code).matches();
    }

    /** Mint a code that no persisted request type already holds. */
    public String generate(String templateRef, String name) {
        return generate(templateRef, name, Set.of());
    }

    /**
     * Mint a code that neither a persisted request type nor {@code reserved} already holds.
     * Codes handed out earlier in an unflushed transaction are not yet visible to the repository.
     */
    public String generate(String templateRef, String name, Set<String> reserved) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = mint(templateRef, name, UUID.randomUUID().toString());
            if (!reserved.contains(code) && !requestTypeRepository.existsByTypeCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException(
                "Could not mint a unique request type code after " + MAX_ATTEMPTS + " attempts");
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
