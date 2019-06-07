package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusterstormpot.core.ResourceClusters;
import org.bbottema.clusterstormpot.core.api.AllocatorFactory;
import org.bbottema.clusterstormpot.core.api.CyclingStrategy;
import org.bbottema.clusterstormpot.util.SimpleDelegatingPoolable;
import org.jetbrains.annotations.NotNull;
import stormpot.Expiration;

import javax.mail.Session;
import javax.mail.Transport;

public class SmtpConnectionPool extends ResourceClusters<Session, Session, SimpleDelegatingPoolable<Transport>> {
    public SmtpConnectionPool(@NotNull final CyclingStrategy clusterStrategy,
                              @NotNull final Expiration<SimpleDelegatingPoolable<Transport>> expirationPolicy,
                              final int defaultMaxPoolSize) {
        super(new SmtpClusterConfig(expirationPolicy, defaultMaxPoolSize), clusterStrategy);
    }

    public SmtpConnectionPool(@NotNull final AllocatorFactory<Session, SimpleDelegatingPoolable<Transport>> allocatorFactory,
                              @NotNull final CyclingStrategy clusterStrategy,
                              @NotNull final Expiration<SimpleDelegatingPoolable<Transport>> expirationPolicy,
                              final int defaultMaxPoolSize) {
        super(new SmtpClusterConfig(allocatorFactory, expirationPolicy, defaultMaxPoolSize), clusterStrategy);
    }
}