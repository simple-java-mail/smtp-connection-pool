package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;
import org.bbottema.clusteredobjectpool.core.ClusterConfig;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourcePoolKey;
import org.bbottema.genericobjectpool.PoolableObject;
import org.bbottema.genericobjectpool.ClaimOptions;
import org.bbottema.genericobjectpool.ExpirationPolicy;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class SmtpConnectionPool extends ResourceClusters<Session, Session, SessionTransport> {

    /**
     * When using OAuth2 authentication, there is no default Session property to configure the value, so we'll state
     * here that we will be looking for a property named {@value}.
     * The selected Jakarta Mail provider resolves the username using its ordinary URL and Session settings;
     * the allocator supplies only this token explicitly and leaves unrelated protocol usernames unchanged.
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
     * Registers this Session's pool with an explicit authentication choice and its cluster's default sizing and expiration.
     * A non-null supplier provides the current OAuth2 token when a physical connection opens or reconnects. A null supplier
     * uses ordinary Jakarta Mail authentication. Neither choice reads the legacy pool OAuth token properties or changes the Session.
     * Connected reuse does not call the supplier; configured eager allocation may open connections during registration.
     * <p>
     * Register before the first claim. Duplicate registration fails without replacing the existing pool's authentication.
     * The choice belongs to this registration; retiring it does not change another registration or close an application-owned supplier.
     * A caller-held retired selection, lease or resource may keep its old registration and callback reachable until released.
     * Retired selections cannot claim from a replacement registration; retirement does not promise immediate erasure of caller-held references.
     * Existing registration overloads and implicit claims retain their legacy property-based authentication behavior.
     * <p>
     * Explicit authentication requires the built-in SMTP allocator, including its cancellation and observer configurations.
     * A custom allocator factory must use the existing registration API and manage its own connection behavior.
     *
     * @param key the existing pool key identifying the Session; must not be null or contain a null Session
     * @param oauth2AccessTokenProvider thread-safe supplier of nonblank tokens, or null for ordinary Session authentication;
     *                                 a failed, null or blank result fails preparation without credential fallback
     * @throws IllegalArgumentException if the pool is already registered
     * @throws IllegalStateException if a custom allocator factory cannot honor the explicit choice
     * @since 4.5.0
     */
    public void registerResourcePool(final ResourceKey<Session, Session> key,
            @Nullable final Supplier<String> oauth2AccessTokenProvider) {
        final SmtpPoolRegistration<Session> registration =
                SmtpPoolRegistration.capture(key, oauth2AccessTokenProvider, getClusterConfig().getAllocatorFactory());
        final ClusterConfig<Session, Session, SessionTransport> defaults = getClusterConfig(registration.getClusterKey());
        super.registerResourcePool(registration, defaults.getDefaultExpirationPolicy(),
                defaults.getDefaultCorePoolSize(), defaults.getDefaultMaxPoolSize());
    }

    /**
     * Registers an explicit authentication choice with this pool's own expiration and sizing.
     * Authentication, callback ownership and compatibility follow {@link #registerResourcePool(ResourceKey, Supplier)}.
     * The existing expiration and core/max-size requirements still apply, and a rejected registration does not replace an existing pool.
     *
     * @param key the existing pool key identifying the Session; must not be null or contain a null Session
     * @param expirationPolicy expiration policy for this registration
     * @param coreSize number of connections to keep available, subject to the existing eager-allocation behavior
     * @param maxSize maximum concurrent connections for this registration
     * @param oauth2AccessTokenProvider thread-safe supplier of nonblank tokens, or null for ordinary Session authentication
     * @throws IllegalArgumentException if the pool is already registered or its size configuration is invalid
     * @throws IllegalStateException if a custom allocator factory cannot honor the explicit choice
     * @since 4.5.0
     */
    public void registerResourcePool(final ResourceKey<Session, Session> key,
            final ExpirationPolicy<SessionTransport> expirationPolicy, final int coreSize, final int maxSize,
            @Nullable final Supplier<String> oauth2AccessTokenProvider) {
        final SmtpPoolRegistration<Session> registration =
                SmtpPoolRegistration.capture(key, oauth2AccessTokenProvider, getClusterConfig().getAllocatorFactory());
        super.registerResourcePool(registration, expirationPolicy, coreSize, maxSize);
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
