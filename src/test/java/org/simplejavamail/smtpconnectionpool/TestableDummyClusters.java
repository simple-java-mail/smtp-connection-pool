package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusterstormpot.core.api.AllocatorFactory;
import org.bbottema.clusterstormpot.cyclingstrategies.RoundRobinCyclingStrategy;
import org.bbottema.clusterstormpot.util.SimpleDelegatingPoolable;
import org.jetbrains.annotations.NotNull;
import stormpot.Expiration;

import javax.mail.Session;
import javax.mail.Transport;

class TestableDummyClusters extends SmtpConnectionPoolClustered {
    TestableDummyClusters(@NotNull final AllocatorFactory<Session, SimpleDelegatingPoolable<Transport>> allocatorFactory,
                          @NotNull final Expiration<SimpleDelegatingPoolable<Transport>> expirationPolicy,
                          int maxPoolSize) {
        super(allocatorFactory, new RoundRobinCyclingStrategy(), expirationPolicy, maxPoolSize);
    }
}