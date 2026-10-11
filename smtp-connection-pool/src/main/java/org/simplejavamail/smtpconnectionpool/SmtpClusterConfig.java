package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.ClusterConfig;
import org.bbottema.clusteredobjectpool.core.ClusterConfig.ClusterConfigBuilder;
import org.bbottema.genericobjectpool.expirypolicies.TimeoutSinceLastAllocationExpirationPolicy;

import java.util.Optional;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.Objects.requireNonNull;

/**
 * Defines a preconfigured {@link ClusterConfig} builder with the following defaults:
 * <ul>
 *      <li>A specialized allocator factory for starting and stopping Transport connections ({@link TransportAllocatorFactory})</li>
 *      <li>Expiration policy of {@value EXPIRY_POLICY_SECONDS} seconds, connections don't last long by default</li>
 *      <li>Core pool size of {@value CORE_POOL_SIZE}, so objects are only created on-demand</li>
 *      <li>Max pool size of {@value MAX_POOL_SIZE}</li>
 * </ul>
 *
 * Configure further using {@code mySmtpClusterConfig.getConfigBuilder().x(a).y(b).z(c);}
 */
@SuppressWarnings("WeakerAccess")
public final class SmtpClusterConfig<ClusterKey> {
    
    static final int CORE_POOL_SIZE = 0;
    static final int MAX_POOL_SIZE = 4;
    static final int EXPIRY_POLICY_SECONDS = 10;

    private TransportCancellationSupport cancellationSupport = transport -> Optional.empty();
    private TransportConnectionObserver connectionObserver = TransportConnectionObserver.NONE;

    private final ClusterConfigBuilder<ClusterKey, Session, SessionTransport> configBuilder = ClusterConfig.<ClusterKey, Session, SessionTransport>builder()
            .allocatorFactory(new TransportAllocatorFactory<ClusterKey>())
            .defaultExpirationPolicy(new TimeoutSinceLastAllocationExpirationPolicy<>(EXPIRY_POLICY_SECONDS, SECONDS))
            .defaultCorePoolSize(CORE_POOL_SIZE)
            .defaultMaxPoolSize(MAX_POOL_SIZE);

    public ClusterConfigBuilder<ClusterKey, Session, SessionTransport> getConfigBuilder() {
        return configBuilder;
    }

    /**
     * Lets the pool interrupt connection or send I/O when your transport provider supports physical abort.
     * No provider is assumed capable by default, and shared Sessions are not modified.
     * Call this before building the pool. It retains a configured connection observer, whichever setter is called first,
     * but replaces a custom allocator factory configured through {@link #getConfigBuilder()}.
     *
     * @param support a thread-safe capability factory that returns empty for unsupported transports
     * @return this configuration
     * @since 4.1.0
     */
    public SmtpClusterConfig<ClusterKey> withTransportCancellationSupport(final TransportCancellationSupport support) {
        cancellationSupport = requireNonNull(support, "support");
        configureAllocatorFactory();
        return this;
    }

    /**
     * Lets you watch connections being opened and decide whether the pool should create more in the background.
     * For example, record connection success/failure counters or pause automatic top-up while your application handles
     * an SMTP outage. The observer does not track email submissions, choose another server or retry messages.
     * <p>
     * Configure it before constructing the pool. Existing cancellation support is retained, whichever setter is called
     * first, but this replaces a custom allocator factory configured through {@link #getConfigBuilder()}.
     * Shared Sessions are not modified.
     *
     * @param observer a thread-safe observer whose callbacks return promptly
     * @return this configuration
     * @see TransportConnectionObserver
     * @since 4.4.0
     */
    public SmtpClusterConfig<ClusterKey> withTransportConnectionObserver(final TransportConnectionObserver observer) {
        connectionObserver = requireNonNull(observer, "observer");
        configureAllocatorFactory();
        return this;
    }

    private void configureAllocatorFactory() {
        configBuilder.allocatorFactory(new TransportAllocatorFactory<>(cancellationSupport, connectionObserver));
    }

    @Override
    public String toString() {
        return "SmtpClusterConfig(configBuilder=" + configBuilder + ")";
    }
}
