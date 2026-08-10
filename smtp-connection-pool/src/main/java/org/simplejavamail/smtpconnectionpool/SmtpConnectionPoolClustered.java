package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey;
import org.bbottema.genericobjectpool.PoolableObject;

public class SmtpConnectionPoolClustered<ClusterKey> extends ResourceClusters<ClusterKey, Session, SessionTransport> {
    public SmtpConnectionPoolClustered(final SmtpClusterConfig<ClusterKey> smtpClusterConfig) {
        super(smtpClusterConfig.getConfigBuilder().build());
    }

    /**
     * Claims one transport exclusively from the addressed pool.
     */
    public SmtpTransportLease claimTransport(final ResourceKey<ClusterKey, Session> resourceKey) throws InterruptedException {
        return leaseOrThrow(claimResourceFromPool(resourceKey));
    }

    /**
     * Claims one transport exclusively using this pool's cluster load-balancing strategy.
     */
    public SmtpTransportLease claimTransportFromCluster(final ClusterKey clusterKey) throws InterruptedException {
        return leaseOrThrow(claimResourceFromCluster(clusterKey));
    }

    private static SmtpTransportLease leaseOrThrow(final PoolableObject<SessionTransport> claimed) {
        if (claimed == null) {
            throw new IllegalStateException("Timed out waiting for an available SMTP transport");
        }
        return new SmtpTransportLease(claimed);
    }
}
