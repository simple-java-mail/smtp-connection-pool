package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;

import java.util.UUID;

public class SmtpConnectionPoolClustered extends ResourceClusters<UUID, Session, SessionTransport> {
    public SmtpConnectionPoolClustered(final SmtpClusterConfig smtpClusterConfig) {
        super(smtpClusterConfig.getConfigBuilder().build());
    }
}