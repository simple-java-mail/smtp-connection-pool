package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;
import org.bbottema.clusteredobjectpool.core.ResourcePoolSelection;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey;
import org.bbottema.genericobjectpool.ClaimOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.simplejavamail.smtpconnectionpool.SmtpTransportLease.leaseOrThrow;

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

    /**
     * Selects which registered SMTP destination will supply a connection, using the cluster's load balancer without
     * borrowing or opening a connection yet.
     *
     * <p>Use this when your application needs to inspect {@link SmtpTransportSelection#getSession() the selected Session}
     * before doing other work. For example, an application that limits how many emails it sends through each SMTP server
     * can identify the selected server, wait until its own rate limiter permits another send, and only then borrow a connection.
     * The pool itself does not implement rate limiting or manage the application's intervening work.</p>
     *
     * <p>When ready, call {@link SmtpTransportSelection#claimTransport()} to borrow from that same registration.
     * Selection does not reserve capacity. If you are ready to borrow immediately, use
     * {@link #claimTransportFromCluster(Object)} to select and acquire in one call.</p>
     *
     * <p>Selection requires an already registered pool and uses the cluster timeout.</p>
     */
    public SmtpTransportSelection selectTransportFromCluster(final ClusterKey clusterKey) throws InterruptedException {
        return selectionOrThrow(selectPoolFromCluster(clusterKey));
    }

    /**
     * Lists the SMTP destinations your application can consider, without opening or borrowing a connection.
     * For example, you can inspect each selected Session and decide whether to try another server after a connection
     * failure. This method does not try the servers or resend emails for you.
     * <p>
     * The immutable list contains each current registration once. The first destination follows normal cluster
     * selection; alternatives use the same load-balancing strategy without advancing other callers' shared position.
     * Later registrations do not enter this list, and retiring a registration makes its later claims fail rather
     * than silently use a replacement.
     * <p>
     * When ready, use {@link SmtpTransportSelection#claimTransport(ClaimOptions)} on a selected destination.
     * Each claim has its own timeout budget, capped by the cluster timeout; supply the remaining application budget
     * if selection and acquisition must share a deadline. A selection reserves no capacity and needs no closing.
     * Waiting for a busy pool does not itself establish that its SMTP server is unavailable.
     *
     * @param clusterKey the cluster whose registered SMTP destinations should be considered
     * @param options cancellation and timeout controls for selection, not for subsequent connection claims
     * @return the current destinations in selection order
     * @throws IllegalStateException if the selection times out or the cluster has no registered pool
     * @throws InterruptedException if selection is interrupted
     * @see ResourceClusters#selectPoolCandidates(Object, ClaimOptions)
     * @since 4.4.0
     */
    public List<SmtpTransportSelection> selectTransportCandidates(final ClusterKey clusterKey, final ClaimOptions options)
            throws InterruptedException {
        final List<ResourcePoolSelection<Session, SessionTransport>> candidates = selectPoolCandidates(clusterKey, options);
        if (candidates == null) {
            throw new IllegalStateException("Timed out selecting SMTP destinations; no connection was borrowed");
        }
        final List<SmtpTransportSelection> selections = new ArrayList<>();
        for (final ResourcePoolSelection<Session, SessionTransport> candidate : candidates) {
            selections.add(new SmtpTransportSelection(candidate));
        }
        return Collections.unmodifiableList(selections);
    }

    /**
     * Lists the SMTP destinations your application can consider, using the cluster's normal timeout.
     * See {@link #selectTransportCandidates(Object, ClaimOptions)} for selection and borrowing semantics.
     */
    public List<SmtpTransportSelection> selectTransportCandidates(final ClusterKey clusterKey) throws InterruptedException {
        return selectTransportCandidates(clusterKey, ClaimOptions.withoutTimeout());
    }

    /**
     * Cancellable, time-bounded counterpart of {@link #selectTransportFromCluster(Object)}. A timeout throws
     * {@link IllegalStateException}; cancellation throws {@link java.util.concurrent.CancellationException}.
     * The subsequent {@link SmtpTransportSelection#claimTransport(ClaimOptions)} starts its own acquisition budget.
     */
    public SmtpTransportSelection selectTransportFromCluster(final ClusterKey clusterKey, final ClaimOptions options)
            throws InterruptedException {
        return selectionOrThrow(selectPoolFromCluster(clusterKey, options));
    }

    /**
     * Addressed counterpart of {@link #selectTransportFromCluster(Object)}: selects an already registered Session without
     * borrowing a connection or advancing the cluster's load balancer. If you are ready to borrow immediately, use
     * {@link #claimTransport(ResourceKey)} instead.
     */
    public SmtpTransportSelection selectTransport(final ResourceKey<ClusterKey, Session> resourceKey) throws InterruptedException {
        return selectionOrThrow(selectPool(resourceKey));
    }

    /** Cancellable, time-bounded addressed counterpart of {@link #selectTransportFromCluster(Object, ClaimOptions)}. */
    public SmtpTransportSelection selectTransport(final ResourceKey<ClusterKey, Session> resourceKey, final ClaimOptions options)
            throws InterruptedException {
        return selectionOrThrow(selectPool(resourceKey, options));
    }

    private static SmtpTransportSelection selectionOrThrow(final ResourcePoolSelection<Session, SessionTransport> selected) {
        if (selected == null) {
            throw new IllegalStateException("Timed out selecting an SMTP destination; no connection was borrowed");
        }
        return new SmtpTransportSelection(selected);
    }
}
