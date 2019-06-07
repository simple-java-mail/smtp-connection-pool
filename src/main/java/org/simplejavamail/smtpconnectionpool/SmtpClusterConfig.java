package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusterstormpot.core.ClusterConfig;
import org.bbottema.clusterstormpot.core.api.AllocatorFactory;
import org.bbottema.clusterstormpot.util.SimpleDelegatingPoolable;
import org.jetbrains.annotations.NotNull;
import stormpot.Expiration;

import javax.mail.Session;
import javax.mail.Transport;

class SmtpClusterConfig extends ClusterConfig<Session, SimpleDelegatingPoolable<Transport>> {
    SmtpClusterConfig(@NotNull final Expiration<SimpleDelegatingPoolable<Transport>> defaultExpirationPolicy, int defaultMaxPoolSize) {
        super(new PoolableTransportAllocatorFactory(), defaultExpirationPolicy, defaultMaxPoolSize);
    }

    SmtpClusterConfig(AllocatorFactory<Session, SimpleDelegatingPoolable<Transport>> allocatorFactory, Expiration<SimpleDelegatingPoolable<Transport>> expirationPolicy, int defaultMaxPoolSize) {
        super(allocatorFactory, expirationPolicy, defaultMaxPoolSize);
    }
}