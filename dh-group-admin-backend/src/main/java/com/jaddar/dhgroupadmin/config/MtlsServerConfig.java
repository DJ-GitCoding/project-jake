/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

package com.jaddar.dhgroupadmin.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds a client-auth TLS listener on {@code mtls.port} for inter-service calls, alongside the
 * main plain-HTTP port used by browser/edge traffic. Active only when {@code mtls.enabled=true}.
 */
@Configuration
@ConditionalOnProperty(name = "mtls.enabled", havingValue = "true")
public class MtlsServerConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> mtlsConnectorCustomizer(MtlsProperties props) {
        return factory -> factory.addAdditionalTomcatConnectors(buildConnector(props));
    }

    private Connector buildConnector(MtlsProperties props) {
        Connector connector = new Connector(Http11NioProtocol.class.getName());
        connector.setPort(props.getPort());
        connector.setScheme("https");
        connector.setSecure(true);

        Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
        protocol.setSSLEnabled(true);

        SSLHostConfig sslHostConfig = new SSLHostConfig();
        sslHostConfig.setCertificateVerification("required"); // client cert mandatory (mTLS)
        sslHostConfig.setProtocols("TLSv1.2+TLSv1.3");

        SSLHostConfigCertificate certificate =
                new SSLHostConfigCertificate(sslHostConfig, SSLHostConfigCertificate.Type.UNDEFINED);
        certificate.setCertificateKeystoreFile(props.getKeyStore());
        certificate.setCertificateKeystorePassword(props.getKeyStorePassword());
        certificate.setCertificateKeystoreType(props.getKeyStoreType());
        sslHostConfig.addCertificate(certificate);

        sslHostConfig.setTruststoreFile(props.getTrustStore());
        sslHostConfig.setTruststorePassword(props.getTrustStorePassword());
        sslHostConfig.setTruststoreType(props.getTrustStoreType());

        connector.addSslHostConfig(sslHostConfig);
        return connector;
    }
}
