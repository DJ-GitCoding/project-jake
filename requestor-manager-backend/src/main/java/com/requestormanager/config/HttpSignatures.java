/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.config;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Message signatures over HTTP, in the shape of RFC 9421.
 *
 * <p>A peer signs each request with a private key it generated and never sends; we verify
 * with the public key it registered. Nothing secret crosses the wire, so a leaked stored
 * value is worthless to an attacker, and the signature survives a proxy terminating TLS --
 * neither of which is true of a shared secret or of client certificates.
 *
 * <p>The signature covers the method, path, a digest of the body and a creation time, so a
 * captured request cannot be replayed against a different path, with a different body, or
 * after the freshness window closes.
 *
 * <p>Ed25519 deliberately: fixed-size keys, no curve, padding or hash parameters for either
 * side to disagree about.
 */
public final class HttpSignatures {

    public static final String ALGORITHM = "Ed25519";
    public static final String SIGNATURE_INPUT_HEADER = "Signature-Input";
    public static final String SIGNATURE_HEADER = "Signature";
    public static final String CONTENT_DIGEST_HEADER = "Content-Digest";

    /** How far apart the clocks may be before a signature is considered stale. */
    public static final long DEFAULT_MAX_SKEW_SECONDS = 300;

    private static final Base64.Encoder B64 = Base64.getEncoder();
    private static final Base64.Decoder B64_DEC = Base64.getDecoder();

    private HttpSignatures() {}

    /** A newly generated peer identity; only the public half is ever shared. */
    public record Keys(String publicKeyBase64, String privateKeyBase64) {}

    public static Keys generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
        KeyPair pair = generator.generateKeyPair();
        return new Keys(
                B64.encodeToString(pair.getPublic().getEncoded()),
                B64.encodeToString(pair.getPrivate().getEncoded()));
    }

    public static PublicKey publicKey(String base64) throws Exception {
        return KeyFactory.getInstance(ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(B64_DEC.decode(base64.trim())));
    }

    public static PrivateKey privateKey(String base64) throws Exception {
        return KeyFactory.getInstance(ALGORITHM)
                .generatePrivate(new PKCS8EncodedKeySpec(B64_DEC.decode(base64.trim())));
    }

    /** {@code sha-256=:base64:} over the exact bytes sent, per RFC 9530. */
    public static String contentDigest(byte[] body) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body);
        return "sha-256=:" + B64.encodeToString(digest) + ":";
    }

    /**
     * The exact bytes both sides sign over. Any disagreement here fails verification, so the
     * construction is deliberately rigid: fixed component order, lowercase names, one per
     * line, no optional whitespace.
     */
    static String signatureBase(String method, String path, String contentDigest, long created, String keyId) {
        List<String> covered = List.of("\"@method\"", "\"@path\"", "\"content-digest\"");
        String params = "(" + String.join(" ", covered) + ");created=" + created + ";keyid=\"" + keyId + "\"";
        return "\"@method\": " + method.toUpperCase(java.util.Locale.ROOT) + "\n"
             + "\"@path\": " + path + "\n"
             + "\"content-digest\": " + contentDigest + "\n"
             + "\"@signature-params\": " + params;
    }

    /** The {@code Signature-Input} value matching {@link #signatureBase}. */
    public static String signatureInput(long created, String keyId) {
        return "sig1=(\"@method\" \"@path\" \"content-digest\");created=" + created + ";keyid=\"" + keyId + "\"";
    }

    /** Signs a request, returning the {@code Signature} header value. */
    public static String sign(String method, String path, byte[] body, long created,
                              String keyId, String privateKeyBase64) throws Exception {
        String base = signatureBase(method, path, contentDigest(body), created, keyId);
        Signature signer = Signature.getInstance(ALGORITHM);
        signer.initSign(privateKey(privateKeyBase64));
        signer.update(base.getBytes(StandardCharsets.UTF_8));
        return "sig1=:" + B64.encodeToString(signer.sign()) + ":";
    }

    /** Why a signature was rejected, for logs and audit entries. */
    public enum Result { VALID, MISSING, MALFORMED, STALE, BAD_DIGEST, BAD_SIGNATURE }

    /**
     * Verifies a received request against the peer's registered public key.
     *
     * <p>The digest is recomputed from the body actually received, so altering the body
     * invalidates the signature even though the body itself is not signed directly.
     */
    public static Result verify(String method, String path, byte[] body,
                                String signatureInput, String signature,
                                String contentDigestHeader,
                                String publicKeyBase64, long maxSkewSeconds) {
        if (signatureInput == null || signature == null || publicKeyBase64 == null) {
            return Result.MISSING;
        }
        try {
            long created = parseCreated(signatureInput);
            String keyId = parseKeyId(signatureInput);
            if (created <= 0 || keyId == null) return Result.MALFORMED;

            long age = Math.abs(Instant.now().getEpochSecond() - created);
            if (age > maxSkewSeconds) return Result.STALE;

            String expectedDigest = contentDigest(body);
            if (contentDigestHeader == null || !constantTimeEquals(expectedDigest, contentDigestHeader.trim())) {
                return Result.BAD_DIGEST;
            }

            String base = signatureBase(method, path, expectedDigest, created, keyId);
            byte[] raw = B64_DEC.decode(stripSignature(signature));

            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey(publicKeyBase64));
            verifier.update(base.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(raw) ? Result.VALID : Result.BAD_SIGNATURE;
        } catch (Exception e) {
            return Result.MALFORMED;
        }
    }

    static long parseCreated(String signatureInput) {
        int i = signatureInput.indexOf("created=");
        if (i < 0) return -1;
        int end = i + 8;
        while (end < signatureInput.length() && Character.isDigit(signatureInput.charAt(end))) end++;
        try {
            return Long.parseLong(signatureInput.substring(i + 8, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String parseKeyId(String signatureInput) {
        int i = signatureInput.indexOf("keyid=\"");
        if (i < 0) return null;
        int end = signatureInput.indexOf('"', i + 7);
        return end < 0 ? null : signatureInput.substring(i + 7, end);
    }

    private static String stripSignature(String value) {
        String v = value.trim();
        int colon = v.indexOf(":");
        int last = v.lastIndexOf(":");
        return (colon >= 0 && last > colon) ? v.substring(colon + 1, last) : v;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
