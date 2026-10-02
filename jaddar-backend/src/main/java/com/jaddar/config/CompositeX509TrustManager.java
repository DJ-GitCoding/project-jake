/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.config;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link X509TrustManager} that trusts a peer if ANY delegate does. Composing our dev-CA
 * truststore with the JVM default lets one client reach both internal peers and public
 * endpoints (RDAP, Keycloak).
 */
public class CompositeX509TrustManager implements X509TrustManager {

    private final List<X509TrustManager> delegates;

    public CompositeX509TrustManager(X509TrustManager... managers) {
        this.delegates = new ArrayList<>();
        for (X509TrustManager m : managers) {
            if (m != null) this.delegates.add(m);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        CertificateException last = null;
        for (X509TrustManager m : delegates) {
            try {
                m.checkServerTrusted(chain, authType);
                return; // trusted by at least one delegate
            } catch (CertificateException e) {
                last = e;
            }
        }
        throw last != null ? last : new CertificateException("No trust manager trusts the server certificate");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        CertificateException last = null;
        for (X509TrustManager m : delegates) {
            try {
                m.checkClientTrusted(chain, authType);
                return;
            } catch (CertificateException e) {
                last = e;
            }
        }
        throw last != null ? last : new CertificateException("No trust manager trusts the client certificate");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        List<X509Certificate> issuers = new ArrayList<>();
        for (X509TrustManager m : delegates) {
            for (X509Certificate c : m.getAcceptedIssuers()) {
                issuers.add(c);
            }
        }
        return issuers.toArray(new X509Certificate[0]);
    }
}
