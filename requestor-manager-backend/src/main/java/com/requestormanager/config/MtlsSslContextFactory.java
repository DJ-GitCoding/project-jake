/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.requestormanager.config;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;

/**
 * Builds mTLS SSL material from the configured keystore (our identity) and truststore (trusted CA).
 * Produces a javax {@link javax.net.ssl.SSLContext} for the Apache HttpClient (no Netty flavour;
 * this service uses Apache HttpClient rather than reactor-netty).
 */
public final class MtlsSslContextFactory {

    private MtlsSslContextFactory() {}

    public static KeyManagerFactory keyManagerFactory(MtlsProperties p) throws Exception {
        KeyStore ks = KeyStore.getInstance(p.getKeyStoreType());
        try (InputStream in = Files.newInputStream(Paths.get(p.getKeyStore()))) {
            ks.load(in, pw(p.getKeyStorePassword()));
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, pw(p.getKeyStorePassword()));
        return kmf;
    }

    public static TrustManagerFactory trustManagerFactory(MtlsProperties p) throws Exception {
        KeyStore ts = KeyStore.getInstance(p.getTrustStoreType());
        try (InputStream in = Files.newInputStream(Paths.get(p.getTrustStore()))) {
            ts.load(in, pw(p.getTrustStorePassword()));
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        return tmf;
    }

    /** javax SSLContext (client + server capable) for JDK/Apache HTTP clients. */
    public static SSLContext buildJavaxSslContext(MtlsProperties p) throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(keyManagerFactory(p).getKeyManagers(), trustManagerFactory(p).getTrustManagers(), null);
        return ctx;
    }

    private static char[] pw(String s) {
        return s == null ? new char[0] : s.toCharArray();
    }
}
