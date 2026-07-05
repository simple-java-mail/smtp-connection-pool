package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;

public class SmtpConnectionPoolClustered<ClusterKey> extends ResourceClusters<ClusterKey, Session, SessionTransport> {
    public SmtpConnectionPoolClustered(final SmtpClusterConfig<ClusterKey> smtpClusterConfig) {
        super(smtpClusterConfig.getConfigBuilder().build());
    }
}
