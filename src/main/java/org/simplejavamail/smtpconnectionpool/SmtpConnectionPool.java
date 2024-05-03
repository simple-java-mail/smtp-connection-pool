package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;

public class SmtpConnectionPool extends ResourceClusters<Session, Session, SessionTransport> {

    /**
     * When using OAuth2 authentication, there is no default Session property to configure the value, so we'll state
     * here that we will be looking for a property named {@value}.
     */
    public static final String OAUTH2_TOKEN_PROPERTY = "smtp.connection.pool.transport.allocator.oauth2token";

    public SmtpConnectionPool(final SmtpClusterConfig smtpClusterConfig) {
        super(smtpClusterConfig.getConfigBuilder().build());
    }
}