/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.config;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Transparent AES-256-GCM encryption at rest for sensitive string columns, currently the announce token on RegistryPublicationSettings, which is what... */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    private static final Logger log = LoggerFactory.getLogger(EncryptedStringConverter.class);

    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8;

    private static final Base64.Encoder B64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_URL_DEC = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Populated by KeyHolder during Spring context startup. */
    private static volatile String configuredKeySource;

    private volatile SecretKey cachedKey;

    /** Spring component whose sole job is to lift the resolved key property (env or yaml, with relaxed binding) into the static holder so the non-managed... */
    @Component
    static class KeyHolder {
        KeyHolder(@Value("${dhg.encryption-key:${dhg.jwt.secret:}}") String key) {
            if (key != null && !key.isBlank()) {
                EncryptedStringConverter.configuredKeySource = key;
            }
        }

        @PostConstruct
        void ready() {
            if (configuredKeySource == null || configuredKeySource.isBlank()) {
                log.warn("EncryptedStringConverter: no dhg.encryption-key or "
                        + "dhg.jwt.secret resolved; will fall back to environment variables "
                        + "at convert time.");
            }
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }
        if (attribute.startsWith(PREFIX)) {
            return attribute;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] combined = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            int ctLen = combined.length - GCM_TAG_LENGTH_BYTES;
            byte[] ciphertext = new byte[ctLen];
            byte[] tag = new byte[GCM_TAG_LENGTH_BYTES];
            System.arraycopy(combined, 0, ciphertext, 0, ctLen);
            System.arraycopy(combined, ctLen, tag, 0, GCM_TAG_LENGTH_BYTES);
            return PREFIX
                    + B64_URL.encodeToString(iv) + ":"
                    + B64_URL.encodeToString(tag) + ":"
                    + B64_URL.encodeToString(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt sensitive attribute for storage", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || !dbData.startsWith(PREFIX)) {
            return dbData;
        }
        try {
            String[] parts = dbData.split(":", 5);
            if (parts.length != 5) {
                log.warn("EncryptedStringConverter: malformed ciphertext (unexpected segment count); returning raw value.");
                return dbData;
            }
            byte[] iv = B64_URL_DEC.decode(parts[2]);
            byte[] tag = B64_URL_DEC.decode(parts[3]);
            byte[] ciphertext = B64_URL_DEC.decode(parts[4]);
            byte[] combined = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(combined), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("EncryptedStringConverter: failed to decrypt sensitive attribute; returning raw stored value. Cause: {}",
                    e.getMessage());
            return dbData;
        }
    }

    private SecretKey key() {
        SecretKey local = cachedKey;
        if (local != null) {
            return local;
        }
        String source = configuredKeySource;
        if (source == null || source.isBlank()) {
            source = System.getenv("DHG_ENCRYPTION_KEY");
        }
        if (source == null || source.isBlank()) {
            source = System.getenv("DHG_JWT_SECRET");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalStateException(
                    "No encryption key available: set DHG_ENCRYPTION_KEY or "
                            + "DHG_JWT_SECRET (dhg.encryption-key / dhg.jwt.secret).");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            local = new SecretKeySpec(digest, "AES");
            cachedKey = local;
            return local;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES key from configured secret", e);
        }
    }
}
