package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.ResourcePoolSelection;
import org.bbottema.genericobjectpool.ClaimOptions;
import org.jetbrains.annotations.NotNull;

import static org.simplejavamail.smtpconnectionpool.SmtpTransportLease.leaseOrThrow;

/**
 * A selected SMTP destination, without a borrowed connection. See
 * {@link SmtpConnectionPoolClustered#selectTransportFromCluster(Object)} for when to select before borrowing.
 * Holding this selection neither keeps the pool alive nor reserves capacity.
 * There is nothing to close; only the eventual {@link SmtpTransportLease} needs release or invalidation.
 */
public final class SmtpTransportSelection {

    private final ResourcePoolSelection<Session, SessionTransport> selected;

    SmtpTransportSelection(final ResourcePoolSelection<Session, SessionTransport> selected) {
        this.selected = selected;
    }

    /** Returns the selected pool's Session without creating or connecting a transport. */
    @NotNull
    public Session getSession() {
        return selected.getPoolKey();
    }

    /**
     * Borrows from the original registration using its configured timeout, without running cluster selection again.
     * If that registration has retired, the claim fails even if another pool now uses the same Session.
     */
    @NotNull
    public SmtpTransportLease claimTransport() throws InterruptedException {
        return leaseOrThrow(selected.claim());
    }

    /**
     * Cancellable acquisition from the original registration. The acquisition budget starts now and is capped by
     * the cluster timeout; selecting and intervening application work do not consume it. Supply a remaining timeout
     * here to keep everything within one application deadline. Timeout throws {@link IllegalStateException},
     * cancellation throws {@link java.util.concurrent.CancellationException}; no failover is attempted.
     */
    @NotNull
    public SmtpTransportLease claimTransport(@NotNull final ClaimOptions options) throws InterruptedException {
        return leaseOrThrow(selected.claim(options));
    }

}
