package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.api.AllocatorFactory;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/** Carries authentication through the existing registration factory while retaining the original cluster and Session identity. */
final class SmtpPoolRegistration<K> implements ResourceKey<K, Session> {
    private final K clusterKey;
    private final Session session;
    private final OAuth2TokenSource tokenSource;

    private SmtpPoolRegistration(final ResourceKey<K, Session> key, @Nullable final Supplier<String> provider) {
        clusterKey = key.getClusterKey();
        session = requireNonNull(key.getPoolKey(), "session");
        tokenSource = OAuth2TokenSource.explicit(provider);
    }

    static <K> SmtpPoolRegistration<K> capture(final ResourceKey<K, Session> key,
            @Nullable final Supplier<String> provider, final AllocatorFactory<K, Session, SessionTransport> allocatorFactory) {
        requireNonNull(key, "key");
        if (!(allocatorFactory instanceof TransportAllocatorFactory)) {
            throw new IllegalStateException("Explicit SMTP authentication requires the built-in transport allocator");
        }
        return new SmtpPoolRegistration<>(key, provider);
    }

    @Override public K getClusterKey() {
        return clusterKey;
    }

    @Override public Session getPoolKey() {
        return session;
    }

    OAuth2TokenSource getTokenSource() {
        return tokenSource;
    }
}
