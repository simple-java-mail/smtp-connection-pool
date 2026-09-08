package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourcePoolKey;
import org.bbottema.genericobjectpool.PoolableObject;
import org.bbottema.genericobjectpool.ClaimOptions;

import java.util.function.Supplier;

public class SmtpConnectionPool extends ResourceClusters<Session, Session, SessionTransport> {

    /**
     * When using OAuth2 authentication, there is no default Session property to configure the value, so we'll state
     * here that we will be looking for a property named {@value}.
     */
    public static final String OAUTH2_TOKEN_PROPERTY = "smtp.connection.pool.transport.allocator.oauth2token";

    /**
     * Optional {@link Supplier} of current OAuth2 access tokens. The allocator resolves this supplier immediately before
     * opening or reconnecting a physical SMTP transport. Already-connected transports are reused without resolving it.
     * <p>
     * The supplier must be thread-safe and return a nonblank {@link String}. It is responsible for token caching and refresh.
     * When this property is absent, {@link #OAUTH2_TOKEN_PROPERTY} remains the fixed-token fallback.
     */
    public static final String OAUTH2_TOKEN_PROVIDER_PROPERTY = "smtp.connection.pool.transport.allocator.oauth2tokenprovider";

    public SmtpConnectionPool(final SmtpClusterConfig<Session> smtpClusterConfig) {
        super(smtpClusterConfig.getConfigBuilder().build());
    }

    /**
     * Claims one transport exclusively for the supplied Session.
     */
    public SmtpTransportLease claimTransport(final Session session) throws InterruptedException {
        final PoolableObject<SessionTransport> claimed = claimResourceFromPool(new ResourcePoolKey<>(session));
        if (claimed == null) {
            throw new IllegalStateException("Timed out waiting for an available SMTP transport");
        }
        return new SmtpTransportLease(claimed);
    }

    /**
     * Claims with optional pending-acquisition cancellation and one total budget, also limited by the configured
     * cluster timeout. Cancellation throws {@link java.util.concurrent.CancellationException}; timeout throws
     * {@link IllegalStateException}, as with the original route. A running provider or credential callback must
     * return cooperatively unless explicit transport cancellation support can abort it.
     * Acquisition control ends at handoff; it cannot revoke the returned lease.
     *
     * @since 4.1.0
     */
    public SmtpTransportLease claimTransport(final Session session, final ClaimOptions options) throws InterruptedException {
        final PoolableObject<SessionTransport> claimed = claimResourceFromPool(new ResourcePoolKey<>(session), options);
        if (claimed == null) {
            throw new IllegalStateException("Timed out waiting for an available SMTP transport");
        }
        return new SmtpTransportLease(claimed);
    }
}
