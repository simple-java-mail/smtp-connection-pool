package org.simplejavamail.smtpconnectionpool.jakarta;

import jakarta.mail.MessagingException;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Provider;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.bbottema.clusteredobjectpool.core.ClusterConfig;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourceClusterAndPoolKey;
import org.bbottema.genericobjectpool.PoolableObject;
import org.bbottema.genericobjectpool.expirypolicies.TimeoutSinceLastAllocationExpirationPolicy;
import org.bbottema.genericobjectpool.util.Timeout;
import org.simplejavamail.smtpconnectionpool.SessionTransport;
import org.simplejavamail.smtpconnectionpool.SmtpConnectionPool;
import org.simplejavamail.smtpconnectionpool.SmtpTransportLease;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Owns all physical connection pools for one Jakarta Mail {@link Session}.
 * New claims are rejected as soon as shutdown starts; graceful shutdown waits for active leases, while
 * {@link #shutdownNow()} invalidates them first.
 */
public final class SmtpPoolManager {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String SESSION_CLUSTER = "smtppool-session";

    private final Session session;
    private final ProviderTransportAllocatorFactory allocatorFactory;
    private final ResourceClusters<String, ConnectionPoolKey, SessionTransport> pools;
    private final Set<ConnectionPoolKey> knownKeys =
            Collections.newSetFromMap(new IdentityHashMap<ConnectionPoolKey, Boolean>());
    private final Map<ConnectionEndpoint, ConnectionPoolKey> currentKeysByEndpoint =
            new HashMap<ConnectionEndpoint, ConnectionPoolKey>();
    private final Set<SmtpTransportLease> activeLeases =
            Collections.newSetFromMap(new ConcurrentHashMap<SmtpTransportLease, Boolean>());
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final byte[] credentialFingerprintKey = new byte[32];
    private final Object registrationLock = new Object();
    private volatile Future<?> shutdownFuture;

    /** Creates a manager whose sizing, timeout, and delegate settings are read from the supplied Session. */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "A manager intentionally owns and scopes all pools to the supplied Jakarta Mail Session.")
    public SmtpPoolManager(final Session session) {
        if (session == null) {
            throw new IllegalArgumentException("Session must not be null");
        }
        this.session = session;
        SECURE_RANDOM.nextBytes(credentialFingerprintKey);

        final int coreSize = intProperty(SmtpPoolProperties.CORE_POOL_SIZE,
                SmtpPoolProperties.DEFAULT_CORE_POOL_SIZE);
        final int maxSize = intProperty(SmtpPoolProperties.MAX_POOL_SIZE,
                SmtpPoolProperties.DEFAULT_MAX_POOL_SIZE);
        final long claimTimeout = longProperty(SmtpPoolProperties.CLAIM_TIMEOUT_MILLIS,
                SmtpPoolProperties.DEFAULT_CLAIM_TIMEOUT_MILLIS);
        final long expiration = longProperty(SmtpPoolProperties.EXPIRATION_MILLIS,
                SmtpPoolProperties.DEFAULT_EXPIRATION_MILLIS);
        if (coreSize < 0 || maxSize < 1 || coreSize > maxSize || claimTimeout < 0 || expiration < 0) {
            throw new IllegalArgumentException("Invalid smtppool sizing or timeout configuration");
        }

        allocatorFactory = new ProviderTransportAllocatorFactory(session);
        final ClusterConfig<String, ConnectionPoolKey, SessionTransport> config =
                ClusterConfig.<String, ConnectionPoolKey, SessionTransport>builder()
                        .allocatorFactory(allocatorFactory)
                        .defaultExpirationPolicy(new TimeoutSinceLastAllocationExpirationPolicy<SessionTransport>(
                                expiration, TimeUnit.MILLISECONDS))
                        .defaultCorePoolSize(coreSize)
                        .defaultMaxPoolSize(maxSize)
                        .claimTimeout(new Timeout(claimTimeout, TimeUnit.MILLISECONDS))
                        .build();
        pools = new ResourceClusters<String, ConnectionPoolKey, SessionTransport>(config);
    }

    Session getSession() {
        return session;
    }

    SmtpTransportLease claim(final String host, final int port, final String user, final String suppliedPassword)
            throws MessagingException, InterruptedException {
        if (shuttingDown.get()) {
            throw new MessagingException("The smtppool manager is shutting down");
        }

        final String requestedProtocol = delegateProtocol();
        final Provider delegateProvider = resolveDelegateProvider(requestedProtocol);
        rejectRecursiveDelegate(requestedProtocol, delegateProvider);
        final String effectiveHost = firstNonBlank(host, session.getProperty("mail." + requestedProtocol + ".host"),
                session.getProperty("mail.host"));
        final String effectiveUser = firstNonBlank(user, session.getProperty("mail." + requestedProtocol + ".user"),
                session.getProperty("mail.user"));
        final int effectivePort = effectivePort(requestedProtocol, port);
        final String effectivePassword = resolvePassword(requestedProtocol, effectiveHost, effectivePort,
                effectiveUser, suppliedPassword);
        final Object credentialIdentity = session.getProperties().get(SmtpPoolProperties.CREDENTIAL_IDENTITY);

        final ConnectionPoolKey candidateKey;
        try {
            candidateKey = new ConnectionPoolKey(requestedProtocol, delegateProvider, effectiveHost, effectivePort,
                    effectiveUser, effectivePassword, credentialIdentity, credentialFingerprintKey);
        } catch (GeneralSecurityException failure) {
            throw new MessagingException("Unable to construct the private SMTP credential identity", failure);
        }

        final ConnectionPoolKey key = selectPoolKey(candidateKey);
        final ResourceClusterAndPoolKey<String, ConnectionPoolKey> resourceKey =
                new ResourceClusterAndPoolKey<String, ConnectionPoolKey>(SESSION_CLUSTER, key);

        final PoolableObject<SessionTransport> claimed;
        try {
            claimed = pools.claimResourceFromPool(resourceKey);
        } catch (TransportAllocationException failure) {
            throw failure.getMessagingCause();
        } catch (IllegalStateException shutdownRace) {
            throw new MessagingException("The smtppool manager shut down while claiming a transport", shutdownRace);
        } catch (RuntimeException poolFailure) {
            throw new MessagingException("Unable to claim a pooled SMTP transport", poolFailure);
        }
        if (claimed == null) {
            throw new MessagingException("Timed out waiting for an available pooled SMTP transport");
        }

        final SmtpTransportLease lease = new SmtpTransportLease(claimed);
        final MessagingException reconnectFailure = allocatorFactory.takeReuseFailure(lease.getSessionTransport());
        if (reconnectFailure != null || !lease.getTransport().isConnected()) {
            lease.invalidate();
            if (reconnectFailure != null) {
                throw reconnectFailure;
            }
            throw new MessagingException("The selected physical SMTP transport disconnected while being claimed");
        }
        activeLeases.add(lease);
        if (shuttingDown.get()) {
            invalidate(lease);
            throw new MessagingException("The smtppool manager started shutting down while claiming a transport");
        }
        return lease;
    }

    void release(final SmtpTransportLease lease) {
        activeLeases.remove(lease);
        lease.release();
    }

    void invalidate(final SmtpTransportLease lease) {
        activeLeases.remove(lease);
        lease.invalidate();
    }

    /** Returns whether this manager has stopped accepting new claims. */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    /** Returns the current number of allocated physical transport objects across all connection identities. */
    public int getLiveTransportCount() {
        return pools.countLiveResources();
    }

    /** Returns the current number of exclusive leases that have not yet been released or invalidated. */
    public int getActiveLeaseCount() {
        return activeLeases.size();
    }

    int getCurrentPoolCountForTesting() {
        synchronized (registrationLock) {
            return currentKeysByEndpoint.size();
        }
    }

    int getRetainedCredentialKeyCountForTesting() {
        synchronized (registrationLock) {
            return knownKeys.size();
        }
    }

    /**
     * Stops accepting claims and lets existing claims return before their physical transports are closed.
     * The returned handle completes only after allocator deallocation has finished. Repeated lifecycle calls return
     * the same handle while this manager is shutting down.
     */
    public Future<?> shutdown() {
        return beginShutdown(false);
    }

    /**
     * Stops accepting claims and invalidates all currently active leases before closing the remaining pool.
     * This also escalates an in-progress graceful shutdown and returns its existing completion handle.
     */
    public Future<?> shutdownNow() {
        return beginShutdown(true);
    }

    private ConnectionPoolKey selectPoolKey(final ConnectionPoolKey candidate) throws MessagingException {
        synchronized (registrationLock) {
            if (shuttingDown.get()) {
                candidate.clearCredentialMaterial();
                throw new MessagingException("The smtppool manager shut down while resolving connection credentials");
            }

            final ConnectionEndpoint endpoint = candidate.getEndpoint();
            final ConnectionPoolKey current = currentKeysByEndpoint.get(endpoint);
            if (candidate.equals(current)) {
                candidate.clearCredentialMaterial();
                return current;
            }

            final ResourceClusterAndPoolKey<String, ConnectionPoolKey> resourceKey =
                    new ResourceClusterAndPoolKey<String, ConnectionPoolKey>(SESSION_CLUSTER, candidate);
            try {
                pools.registerResourcePool(resourceKey);
            } catch (RuntimeException registrationFailure) {
                candidate.clearCredentialMaterial();
                throw new MessagingException("Unable to register a physical SMTP connection pool", registrationFailure);
            }
            currentKeysByEndpoint.put(endpoint, candidate);
            knownKeys.add(candidate);

            if (current != null) {
                retireCredentialGeneration(current, pools.shutdownPool(current));
            }
            return candidate;
        }
    }

    private void retireCredentialGeneration(final ConnectionPoolKey retiredKey, final Future<?> retirement) {
        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    retirement.get();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException ignored) {
                    // The owning manager's shutdown handle reports pool cleanup failures to lifecycle callers.
                } finally {
                    synchronized (registrationLock) {
                        knownKeys.remove(retiredKey);
                        retiredKey.clearCredentialMaterial();
                    }
                }
            }
        });
    }

    private Future<?> beginShutdown(final boolean force) {
        final Future<?> result;
        synchronized (registrationLock) {
            if (!shuttingDown.get()) {
                shuttingDown.set(true);
                markSessionShuttingDown();
                shutdownFuture = cleanupAfter(pools.shutDown());
            }
            result = shutdownFuture;
        }
        if (force) {
            for (SmtpTransportLease lease : new ArrayList<SmtpTransportLease>(activeLeases)) {
                invalidate(lease);
            }
        }
        return result;
    }

    private Provider resolveDelegateProvider(final String protocol) throws MessagingException {
        final Object resolver = session.getProperties().get(SmtpPoolProperties.DELEGATE_PROVIDER_RESOLVER);
        if (resolver != null) {
            if (!(resolver instanceof SmtpDelegateProviderResolver)) {
                throw new MessagingException(SmtpPoolProperties.DELEGATE_PROVIDER_RESOLVER +
                        " must contain an SmtpDelegateProviderResolver");
            }
            final Provider resolved = ((SmtpDelegateProviderResolver) resolver).resolve(session, protocol);
            if (resolved == null) {
                throw new NoSuchProviderException("The smtppool delegate resolver returned no provider for " + protocol);
            }
            return resolved;
        }

        final Object configuredProvider = session.getProperties().get(SmtpPoolProperties.DELEGATE_PROVIDER);
        if (configuredProvider != null) {
            if (!(configuredProvider instanceof Provider)) {
                throw new MessagingException(SmtpPoolProperties.DELEGATE_PROVIDER + " must contain a jakarta.mail.Provider");
            }
            return (Provider) configuredProvider;
        }
        return session.getProvider(protocol);
    }

    private static void rejectRecursiveDelegate(final String requestedProtocol, final Provider provider)
            throws MessagingException {
        final String protocol = provider.getProtocol();
        final String className = provider.getClassName();
        if (SmtpPoolProperties.PROTOCOL.equalsIgnoreCase(requestedProtocol) ||
                SmtpPoolProperties.PROTOCOL.equalsIgnoreCase(protocol) ||
                PooledTransport.class.getName().equals(className) ||
                PooledTransportProvider.class.getName().equals(className)) {
            throw new MessagingException("smtppool cannot use itself as its physical delegate");
        }
        if (provider.getType() != Provider.Type.TRANSPORT) {
            throw new MessagingException("The selected smtppool delegate is not a Transport provider");
        }
    }

    private String delegateProtocol() {
        final String configured = session.getProperty(SmtpPoolProperties.DELEGATE_PROTOCOL);
        return configured == null || configured.trim().isEmpty()
                ? SmtpPoolProperties.DEFAULT_DELEGATE_PROTOCOL
                : configured.trim().toLowerCase(Locale.ROOT);
    }

    private int effectivePort(final String protocol, final int suppliedPort) throws MessagingException {
        if (suppliedPort >= 0) {
            return suppliedPort;
        }
        final String configured = session.getProperty("mail." + protocol + ".port");
        if (configured != null && !configured.trim().isEmpty()) {
            try {
                return Integer.parseInt(configured.trim());
            } catch (NumberFormatException failure) {
                throw new MessagingException("Invalid delegate port for protocol " + protocol, failure);
            }
        }
        if ("smtp".equals(protocol)) {
            return 25;
        }
        if ("smtps".equals(protocol)) {
            return 465;
        }
        return -1;
    }

    private String resolvePassword(final String protocol, final String host, final int port, final String user,
                                   final String suppliedPassword) throws MessagingException {
        if (suppliedPassword != null) {
            return suppliedPassword;
        }

        final Object tokenSupplier = session.getProperties().get(SmtpConnectionPool.OAUTH2_TOKEN_PROVIDER_PROPERTY);
        if (tokenSupplier != null) {
            if (!(tokenSupplier instanceof Supplier)) {
                throw new MessagingException(SmtpConnectionPool.OAUTH2_TOKEN_PROVIDER_PROPERTY + " must contain a Supplier");
            }
            final Object token;
            try {
                token = ((Supplier<?>) tokenSupplier).get();
            } catch (RuntimeException failure) {
                throw new MessagingException("The OAuth2 token provider failed", failure);
            }
            if (!(token instanceof String) || ((String) token).trim().isEmpty()) {
                throw new MessagingException("The OAuth2 token provider returned a blank access token");
            }
            return (String) token;
        }

        final Object fixedToken = session.getProperties().get(SmtpConnectionPool.OAUTH2_TOKEN_PROPERTY);
        if (fixedToken instanceof String) {
            return (String) fixedToken;
        }

        final PasswordAuthentication saved = session.getPasswordAuthentication(
                new URLName(protocol, host, port, null, user, null));
        if (saved != null && (user == null || user.equals(saved.getUserName()))) {
            return saved.getPassword();
        }
        return null;
    }

    private int intProperty(final String name, final int defaultValue) {
        return (int) longProperty(name, defaultValue);
    }

    private long longProperty(final String name, final long defaultValue) {
        final String value = session.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid numeric value for " + name, failure);
        }
    }

    private void clearKnownPasswords() {
        synchronized (registrationLock) {
            for (ConnectionPoolKey key : knownKeys) {
                key.clearCredentialMaterial();
            }
            knownKeys.clear();
            currentKeysByEndpoint.clear();
            java.util.Arrays.fill(credentialFingerprintKey, (byte) 0);
        }
    }

    private Future<?> cleanupAfter(final Future<?> shutdownFuture) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    shutdownFuture.get();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(interrupted);
                } catch (java.util.concurrent.ExecutionException failure) {
                    throw new CompletionException(failure.getCause());
                } finally {
                    clearKnownPasswords();
                    SmtpPoolRegistry.managerShutdownCompleted(SmtpPoolManager.this);
                }
            }
        });
    }

    private void markSessionShuttingDown() {
        synchronized (session.getProperties()) {
            if (session.getProperties().get(SmtpPoolProperties.MANAGER) == this) {
                session.getProperties().put(SmtpPoolProperties.REGISTRY_SHUTDOWN, Boolean.TRUE);
            }
        }
    }

    private static String firstNonBlank(final String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate;
            }
        }
        return null;
    }
}
