package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusteredobjectpool.core.ResourceClusters;

import javax.mail.Session;
import javax.mail.Transport;
import java.util.UUID;

public class SmtpConnectionPoolClustered extends ResourceClusters<UUID, Session, Transport> {
    public SmtpConnectionPoolClustered(final SmtpClusterConfig smtpClusterConfig) {
        super(smtpClusterConfig.getConfigBuilder().build());
    }
}