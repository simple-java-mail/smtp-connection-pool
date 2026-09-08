package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey;
import org.bbottema.genericobjectpool.PoolableObject;
import org.bbottema.genericobjectpool.ClaimOptions;

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

    /**
     * Addressed-pool counterpart of {@link SmtpConnectionPool#claimTransport(Session, ClaimOptions)}, with the same
     * cancellation, timeout and cooperative-provider contract. Existing cluster selection and Session identity stay unchanged.
     *
     * @since 4.1.0
     */
    public SmtpTransportLease claimTransport(final ResourceKey<ClusterKey, Session> resourceKey, final ClaimOptions options)
            throws InterruptedException {
        return leaseOrThrow(claimResourceFromPool(resourceKey, options));
    }

    /**
     * Cluster-selected counterpart of {@link #claimTransport(ResourceKey, ClaimOptions)}. The same running budget
     * includes strategy selection, registration, waiting and connection preparation; no failover is added.
     *
     * @since 4.1.0
     */
    public SmtpTransportLease claimTransportFromCluster(final ClusterKey clusterKey, final ClaimOptions options)
            throws InterruptedException {
        return leaseOrThrow(claimResourceFromCluster(clusterKey, options));
    }

    private static SmtpTransportLease leaseOrThrow(final PoolableObject<SessionTransport> claimed) {
        if (claimed == null) {
            throw new IllegalStateException("Timed out waiting for an available SMTP transport");
        }
        return new SmtpTransportLease(claimed);
    }
}
