package org.simplejavamail.smtpconnectionpool;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.bbottema.clusterstormpot.core.ClusterConfig;
import org.bbottema.clusterstormpot.core.ClusterConfig.ClusterConfigBuilder;
import org.bbottema.clusterstormpot.core.ClusterConfig.SizingMode;
import org.bbottema.clusterstormpot.util.SimpleDelegatingPoolable;
import stormpot.TimeExpiration;

import javax.mail.Session;
import javax.mail.Transport;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Defines a preconfigured {@link ClusterConfig} builder with the following defaults:
 * <ul>
 *      <li>A specialized allocator factory for starting and stopping Transport connections ({@link PoolableTransportAllocatorFactory})</li>
 *      <li>{@link SizingMode#ON_DEMAND} for lazy allocation and short lived connections</li>
 *      <li>Expiration policy of {@value EXPIRY_POLICY_SECONDS} seconds, connections don't last long by default</li>
 *      <li>Max pool size of {@value MAX_POOL_SIZE}</li>
 * </ul>
 *
 * Configure further using {@code mySmtpClusterConfig.getConfigBuilder().x(a).y(b).z(c);}
 */
@Getter
@ToString
@NoArgsConstructor
@SuppressWarnings("WeakerAccess")
public final class SmtpClusterConfig {

    private static final int MAX_POOL_SIZE = 4;
    private static final int EXPIRY_POLICY_SECONDS = 10;

    private final ClusterConfigBuilder<Session, SimpleDelegatingPoolable<Transport>> configBuilder = ClusterConfig.<Session, SimpleDelegatingPoolable<Transport>>builder()
            .allocatorFactory(new PoolableTransportAllocatorFactory())
            .sizingMode(SizingMode.ON_DEMAND)
            .defaultExpirationPolicy(new TimeExpiration<SimpleDelegatingPoolable<Transport>>(EXPIRY_POLICY_SECONDS, SECONDS))
            .defaultMaxPoolSize(MAX_POOL_SIZE);
}