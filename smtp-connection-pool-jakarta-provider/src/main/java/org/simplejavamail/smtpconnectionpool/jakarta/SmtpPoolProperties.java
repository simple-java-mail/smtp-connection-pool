package org.simplejavamail.smtpconnectionpool.jakarta;

import jakarta.mail.Provider;
import jakarta.mail.Session;

/** Configuration keys and type-safe helpers for the {@code smtppool} provider. */
public final class SmtpPoolProperties {

    /** Public Jakarta Mail protocol exposed by {@link PooledTransportProvider}. */
    public static final String PROTOCOL = "smtppool";
    /** Physical protocol used when no explicit delegate protocol is configured. */
    public static final String DEFAULT_DELEGATE_PROTOCOL = "smtp";

    /** String property naming the physical Jakarta Mail transport protocol. */
    public static final String DELEGATE_PROTOCOL = "mail.smtppool.delegate.protocol";
    /** Object property containing a concrete physical {@link Provider}. */
    public static final String DELEGATE_PROVIDER = "mail.smtppool.delegate.provider";
    /** Object property containing an {@link SmtpDelegateProviderResolver}. */
    public static final String DELEGATE_PROVIDER_RESOLVER = "mail.smtppool.delegate.provider-resolver";
    /** Optional credential generation/version identity used in addition to the effective password or token. */
    public static final String CREDENTIAL_IDENTITY = "mail.smtppool.credential.identity";
    /** Object property containing the Session-scoped {@link SmtpPoolManager}. */
    public static final String MANAGER = "mail.smtppool.manager";
    static final String REGISTRY_SHUTDOWN = "mail.smtppool.registry.shutdown";

    /** Minimum number of physical transports retained per connection identity. */
    public static final String CORE_POOL_SIZE = "mail.smtppool.pool.core-size";
    /** Maximum number of physical transports leased concurrently per connection identity. */
    public static final String MAX_POOL_SIZE = "mail.smtppool.pool.max-size";
    /** Maximum wait in milliseconds for an exclusive lease. */
    public static final String CLAIM_TIMEOUT_MILLIS = "mail.smtppool.pool.claim-timeout-millis";
    /** Expiration threshold in milliseconds measured since an available transport's last claim. */
    public static final String EXPIRATION_MILLIS = "mail.smtppool.pool.expiration-millis";

    /** Default core pool size. */
    public static final int DEFAULT_CORE_POOL_SIZE = 0;
    /** Default maximum pool size. */
    public static final int DEFAULT_MAX_POOL_SIZE = 4;
    /** Default exclusive-claim timeout in milliseconds. */
    public static final long DEFAULT_CLAIM_TIMEOUT_MILLIS = 30_000L;
    /** Default available-transport expiration threshold in milliseconds. */
    public static final long DEFAULT_EXPIRATION_MILLIS = 10_000L;

    private SmtpPoolProperties() {
    }

    /** Selects one concrete physical provider for the supplied Session. */
    public static void setDelegateProvider(final Session session, final Provider provider) {
        session.getProperties().put(DELEGATE_PROVIDER, provider);
    }

    /** Selects a runtime resolver for physical providers; this takes precedence over a concrete provider. */
    public static void setDelegateProviderResolver(final Session session,
                                                   final SmtpDelegateProviderResolver resolver) {
        session.getProperties().put(DELEGATE_PROVIDER_RESOLVER, resolver);
    }

    /**
     * Installs a container-owned manager before the Session is first used with {@code smtppool}.
     *
     * @throws IllegalArgumentException if the manager owns a different Session
     */
    public static void setManager(final Session session, final SmtpPoolManager manager) {
        if (session == null || manager == null) {
            throw new IllegalArgumentException("Session and manager must not be null");
        }
        if (manager.getSession() != session) {
            throw new IllegalArgumentException("The SmtpPoolManager belongs to a different Session");
        }
        if (manager.isShuttingDown()) {
            throw new IllegalStateException("Cannot install an SmtpPoolManager that is shutting down");
        }
        synchronized (session.getProperties()) {
            if (manager.isShuttingDown()) {
                throw new IllegalStateException("Cannot install an SmtpPoolManager that is shutting down");
            }
            if (Boolean.TRUE.equals(session.getProperties().get(REGISTRY_SHUTDOWN))) {
                throw new IllegalStateException("The Session's previous smtppool lifecycle must complete and be restarted first");
            }
            final Object existing = session.getProperties().get(MANAGER);
            if (existing != null && existing != manager) {
                throw new IllegalStateException("The Session already has a different smtppool manager");
            }
            session.getProperties().put(MANAGER, manager);
        }
    }
}
