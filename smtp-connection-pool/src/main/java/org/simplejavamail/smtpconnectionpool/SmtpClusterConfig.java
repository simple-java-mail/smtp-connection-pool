package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.ClusterConfig;
import org.bbottema.clusteredobjectpool.core.ClusterConfig.ClusterConfigBuilder;
import org.bbottema.genericobjectpool.expirypolicies.TimeoutSinceLastAllocationExpirationPolicy;

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

    private final ClusterConfigBuilder<ClusterKey, Session, SessionTransport> configBuilder = ClusterConfig.<ClusterKey, Session, SessionTransport>builder()
            .allocatorFactory(new TransportAllocatorFactory<ClusterKey>())
            .defaultExpirationPolicy(new TimeoutSinceLastAllocationExpirationPolicy<>(EXPIRY_POLICY_SECONDS, SECONDS))
            .defaultCorePoolSize(CORE_POOL_SIZE)
            .defaultMaxPoolSize(MAX_POOL_SIZE);

    public ClusterConfigBuilder<ClusterKey, Session, SessionTransport> getConfigBuilder() {
        return configBuilder;
    }

    /**
     * Enables provider-specific physical abort for transports allocated by this configuration. No provider is assumed
     * capable by default. Call this before building the pool; it replaces the allocator factory, just like configuring
     * a factory through {@link #getConfigBuilder()}. Shared Sessions are not modified.
     *
     * @param support a thread-safe capability factory that returns empty for unsupported transports
     * @return this configuration
     * @since 4.1.0
     */
    public SmtpClusterConfig<ClusterKey> withTransportCancellationSupport(final TransportCancellationSupport support) {
        configBuilder.allocatorFactory(new TransportAllocatorFactory<>(requireNonNull(support, "support")));
        return this;
    }

    @Override
    public String toString() {
        return "SmtpClusterConfig(configBuilder=" + configBuilder + ")";
    }
}
