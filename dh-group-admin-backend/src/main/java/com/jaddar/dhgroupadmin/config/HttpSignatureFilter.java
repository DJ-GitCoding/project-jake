/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.config;

import com.jaddar.dhgroupadmin.repository.DataHolderCredentialRepository;
import com.jaddar.dhgroupadmin.repository.SubscriptionCredentialRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Verifies peer request signatures before a controller sees the request.
 *
 * <p>Verification has to happen here rather than in a controller because it covers a digest
 * of the raw body, which Spring has already consumed by the time a handler runs. The body is
 * read once into memory and replayed to the rest of the chain.
 *
 * <p>A verified caller is published as request attributes, so handlers ask who is calling
 * instead of trusting a path or body value.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HttpSignatureFilter extends OncePerRequestFilter {

    /** Set once a signature verifies; absent means the caller is unidentified. */
    public static final String ATTR_KEY_ID = "peer.keyId";
    public static final String ATTR_SUBSCRIPTION = "peer.requestId";
    public static final String ATTR_DATAHOLDER = "peer.dataholderId";

    private static final String PROTECTED_PREFIX = "/api/external/";
    /** Bodies above this are refused rather than buffered; peer payloads are small. */
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    private final SubscriptionCredentialRepository subscriptionCredentials;
    private final DataHolderCredentialRepository dataHolderCredentials;

    @Value("${peer.signatures.required:false}")
    private boolean signaturesRequired;

    @Value("${peer.signatures.max-skew-seconds:300}")
    private long maxSkewSeconds;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!isProtected(request)) {
            chain.doFilter(request, response);
            return;
        }

        byte[] body = readBody(request);
        if (body == null) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body too large");
            return;
        }
        HttpServletRequest replayable = new CachedBodyRequest(request, body);

        String signatureInput = request.getHeader(HttpSignatures.SIGNATURE_INPUT_HEADER);
        String signature = request.getHeader(HttpSignatures.SIGNATURE_HEADER);
        String digest = request.getHeader(HttpSignatures.CONTENT_DIGEST_HEADER);

        if (signatureInput == null || signature == null) {
            if (signaturesRequired) {
                log.warn("Refusing unsigned request to {}", request.getRequestURI());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "A request signature is required");
                return;
            }
            chain.doFilter(replayable, response);
            return;
        }

        String keyId = HttpSignatures.parseKeyId(signatureInput);
        if (keyId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Malformed signature");
            return;
        }

        /* The key id names the credential row, which carries the peer's public key. */
        var subscription = subscriptionCredentials.findByClientIdAndIsActiveTrue(keyId);
        var dataHolder = subscription.isPresent()
                ? java.util.Optional.<com.jaddar.dhgroupadmin.entity.DataHolderCredential>empty()
                : dataHolderCredentials.findByClientIdAndIsActiveTrue(keyId);

        String publicKey = subscription.map(c -> c.getPublicKey())
                .orElseGet(() -> dataHolder.map(c -> c.getPublicKey()).orElse(null));

        if (publicKey == null || publicKey.isBlank()) {
            log.warn("No registered public key for key id {}", keyId);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unknown or unregistered key");
            return;
        }

        HttpSignatures.Result result = HttpSignatures.verify(
                request.getMethod(), request.getRequestURI(), body,
                signatureInput, signature, digest, publicKey, maxSkewSeconds);

        if (result != HttpSignatures.Result.VALID) {
            log.warn("Signature rejected for key id {} on {}: {}", keyId, request.getRequestURI(), result);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Signature verification failed: " + result);
            return;
        }

        request.setAttribute(ATTR_KEY_ID, keyId);
        subscription.ifPresent(c -> {
            request.setAttribute(ATTR_SUBSCRIPTION, c.getRequestId());
            c.setLastUsedAt(LocalDateTime.now());
            subscriptionCredentials.save(c);
        });
        dataHolder.ifPresent(c -> {
            request.setAttribute(ATTR_DATAHOLDER, c.getDataholderId());
            c.setLastUsedAt(LocalDateTime.now());
            dataHolderCredentials.save(c);
        });

        chain.doFilter(replayable, response);
    }

    private boolean isProtected(HttpServletRequest request) {
        String path = RequestPaths.normalized(request);
        return path != null && path.startsWith(PROTECTED_PREFIX);
    }

    /** Returns null when the body exceeds what we are willing to buffer. */
    private byte[] readBody(HttpServletRequest request) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        int total = 0;
        try (var in = request.getInputStream()) {
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) return null;
                out.write(chunk, 0, read);
            }
        }
        return out.toByteArray();
    }

    /** Replays the already-consumed body to the rest of the chain. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return source.read(); }
                @Override public boolean isFinished() { return source.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream()));
        }
    }
}
