package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;

public class SmtpConnectionPool extends ResourceClusters<Session, Session, Transport> {
    public SmtpConnectionPool(final SmtpClusterConfig smtpClusterConfig) {
        super(smtpClusterConfig.getConfigBuilder().build());
    }
}