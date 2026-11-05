package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Address;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Provider;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.URLName;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourceClusterAndPoolKey;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourcePoolKey;
import org.bbottema.genericobjectpool.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.simplejavamail.smtpconnectionpool.SmtpConnectionPool.OAUTH2_TOKEN_PROPERTY;
import static org.simplejavamail.smtpconnectionpool.SmtpConnectionPool.OAUTH2_TOKEN_PROVIDER_PROPERTY;

/** Exercises registration through both public facades and Jakarta Mail's real Service.connect implementation. */
@org.junit.jupiter.api.Timeout(20)
class PoolAuthenticationRegistrationTest {
    static final String USER = "selected@example.test";
    enum Route { DIRECT, CLUSTERED }

    @ParameterizedTest
    @CsvSource({"DIRECT,smtp,false", "DIRECT,smtps,false", "DIRECT,smtp,true", "DIRECT,smtps,true",
            "CLUSTERED,smtp,false", "CLUSTERED,smtps,false", "CLUSTERED,smtp,true", "CLUSTERED,smtps,true"})
    void explicitProviderUsesTheSelectedSessionWithoutLegacyAliasesAndRefreshesOnlyForAConnection(
            final Route route, final String protocol, final boolean customSizing) throws Exception {
        final Fixture fixture = new Fixture(protocol, null);
        final AtomicInteger staleCalls = fixture.installStaleAliases();
        final Properties original = (Properties) fixture.properties.clone();
        final AtomicInteger calls = new AtomicInteger();
        try (Harness pool = new Harness(route, fixture.session)) {
            pool.register(() -> "current-" + calls.incrementAndGet(), customSizing);
            assertEquals(0, calls.get());
            final RecordingTransport first;
            try (SmtpTransportLease lease = pool.claim()) {
                assertSame(fixture.session, lease.getSession());
                first = (RecordingTransport) lease.getTransport();
                assertEquals(USER, first.username);
                assertEquals("current-1", first.password);
            }
            try (SmtpTransportLease lease = pool.claim()) {
                assertSame(first, lease.getTransport());
                assertEquals(1, calls.get());
                first.close();
            }
            try (SmtpTransportLease lease = pool.claim()) {
                assertSame(first, lease.getTransport());
                assertEquals("current-2", first.password);
                assertEquals(2, calls.get());
            }
        }
        assertEquals(0, staleCalls.get());
        assertEquals(original, fixture.properties);
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void explicitNullRetainsOrdinarySessionAuthenticationWithoutReadingStaleAliases(final Route route) throws Exception {
        final AtomicInteger authentications = new AtomicInteger();
        final Authenticator authenticator = new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication() {
                authentications.incrementAndGet();
                return new PasswordAuthentication(USER, "ordinary-password");
            }
        };
        final Fixture fixture = new Fixture("smtp", authenticator);
        fixture.requiresPassword = true;
        final AtomicInteger staleCalls = fixture.installStaleAliases();
        final Properties original = (Properties) fixture.properties.clone();
        try (Harness pool = new Harness(route, fixture.session)) {
            pool.register(null, false);
            try (SmtpTransportLease lease = pool.claim()) {
                assertEquals("ordinary-password", ((RecordingTransport) lease.getTransport()).password);
            }
        }
        assertEquals(1, authentications.get());
        assertEquals(0, staleCalls.get());
        assertEquals(original, fixture.properties);
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void legacyRegistrationKeepsTheExistingPropertyBasedProviderAndFixedFallback(final Route route) throws Exception {
        final Fixture fixture = new Fixture("smtp", null);
        final AtomicInteger calls = new AtomicInteger();
        fixture.properties.setProperty(OAUTH2_TOKEN_PROPERTY, "legacy-fixed");
        fixture.properties.put(OAUTH2_TOKEN_PROVIDER_PROPERTY, (Supplier<String>) () -> "legacy-" + calls.incrementAndGet());
        try (Harness pool = new Harness(route, fixture.session)) {
            pool.registerLegacy();
            try (SmtpTransportLease lease = pool.claim()) {
                assertEquals("legacy-1", ((RecordingTransport) lease.getTransport()).password);
            }
        }
        assertEquals(1, calls.get());
        fixture.properties.remove(OAUTH2_TOKEN_PROVIDER_PROPERTY);
        try (Harness pool = new Harness(route, fixture.session)) {
            try (SmtpTransportLease lease = pool.claim()) {
                assertEquals("legacy-fixed", ((RecordingTransport) lease.getTransport()).password);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"DIRECT,NULL", "DIRECT,EMPTY", "DIRECT,SPACE", "CLUSTERED,NULL", "CLUSTERED,EMPTY", "CLUSTERED,SPACE"})
    void anInvalidExplicitTokenFailsBeforeProviderConnectionWithoutFallback(final Route route, final String value) throws Exception {
        final Fixture fixture = new Fixture("smtp", null);
        final AtomicInteger staleCalls = fixture.installStaleAliases();
        try (Harness pool = new Harness(route, fixture.session)) {
            pool.register(() -> value.equals("NULL") ? null : value.equals("EMPTY") ? "" : "  ", false);
            final TransportHandlingException failure = assertThrows(TransportHandlingException.class, pool::claim);
            assertEquals("The OAuth2 token provider returned a blank access token", failure.getMessage());
            assertEquals(1, fixture.created.size());
            assertEquals(0, fixture.created.get(0).connections.get());
            assertEquals(1, fixture.created.get(0).closes.get());
            assertEquals(0, staleCalls.get());
        }
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void aThrowingSupplierRetainsItsCauseAndSuppressedTransportCleanup(final Route route) throws Exception {
        final Fixture fixture = new Fixture("smtp", null);
        final IllegalStateException sourceFailure = new IllegalStateException("token service unavailable");
        fixture.closeFailure = new MessagingException("synthetic close failure");
        try (Harness pool = new Harness(route, fixture.session)) {
            pool.register(() -> { throw sourceFailure; }, false);
            final TransportHandlingException failure = assertThrows(TransportHandlingException.class, pool::claim);
            assertSame(sourceFailure, failure.getCause());
            assertArrayEquals(new Throwable[]{fixture.closeFailure}, failure.getSuppressed());
            assertEquals(0, fixture.created.get(0).connections.get());
            assertEquals(1, fixture.created.get(0).closes.get());
        }
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    void duplicateAndInvalidRegistrationCannotReplaceAnAcceptedChoice(final Route route) throws Exception {
        final Fixture fixture = new Fixture("smtp", null);
        final AtomicInteger replacementCalls = new AtomicInteger();
        try (Harness pool = new Harness(route, fixture.session)) {
            assertThrows(IllegalArgumentException.class, () -> pool.registerSized(() -> "rejected", 2, 1));
            assertFalse(pool.isRegistered());
            pool.register(() -> "accepted", false);
            assertThrows(IllegalArgumentException.class,
                    () -> pool.register(() -> { replacementCalls.incrementAndGet(); return "replacement"; }, false));
            try (SmtpTransportLease lease = pool.claim()) {
                assertEquals("accepted", ((RecordingTransport) lease.getTransport()).password);
            }
        }
        assertEquals(0, replacementCalls.get());
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void builtInObserverAndCancellationConfigurationKeepTheExplicitBinding(final boolean cancellationFirst) throws Exception {
        final Fixture fixture = new Fixture("smtp", null);
        final AtomicInteger attempts = new AtomicInteger();
        final TransportConnectionObserver observer = new TransportConnectionObserver() {
            @Override public Attempt connectionStarting(final Session session, final Transport transport) {
                assertSame(fixture.session, session);
                attempts.incrementAndGet();
                return Attempt.NONE;
            }
        };
        final SmtpClusterConfig<Session> settings = settings();
        if (cancellationFirst) {
            settings.withTransportCancellationSupport(transport -> Optional.of(() -> { }))
                    .withTransportConnectionObserver(observer);
        } else {
            settings.withTransportConnectionObserver(observer)
                    .withTransportCancellationSupport(transport -> Optional.of(() -> { }));
        }
        final SmtpConnectionPool pool = new SmtpConnectionPool(settings);
        try {
            pool.registerResourcePool(new ResourcePoolKey<>(fixture.session), () -> "current");
            try (SmtpTransportLease lease = pool.claimTransport(fixture.session)) {
                assertTrue(lease.getCancellation().isPresent());
                assertEquals("current", ((RecordingTransport) lease.getTransport()).password);
            }
            assertEquals(1, attempts.get());
        } finally {
            pool.shutDown().get(3, SECONDS);
        }
    }

    @Test
    void aCustomAllocatorCannotSilentlyIgnoreTheNewBindingButItsLegacyPathRemainsAvailable() throws Exception {
        final Fixture fixture = new Fixture("smtp", null);
        fixture.properties.setProperty(OAUTH2_TOKEN_PROPERTY, "legacy");
        final SmtpClusterConfig<Session> settings = settings();
        settings.getConfigBuilder().allocatorFactory(key -> new TransportAllocator(key.getPoolKey()));
        final SmtpConnectionPool pool = new SmtpConnectionPool(settings);
        final ResourcePoolKey<Session> key = new ResourcePoolKey<>(fixture.session);
        try {
            assertThrows(IllegalStateException.class, () -> pool.registerResourcePool(key, () -> "explicit"));
            assertFalse(pool.isPoolRegistered(key));
            pool.registerResourcePool(key);
            try (SmtpTransportLease lease = pool.claimTransport(fixture.session)) {
                assertEquals("legacy", ((RecordingTransport) lease.getTransport()).password);
            }
        } finally {
            pool.shutDown().get(3, SECONDS);
        }
    }

    @Test
    void registrationAndClaimDoNotRenderOrCompareTheApplicationSupplier() throws Exception {
        final Fixture fixture = new Fixture("smtp", null);
        final AtomicInteger calls = new AtomicInteger();
        final Supplier<String> provider = new Supplier<String>() {
            @Override public String get() {
                calls.incrementAndGet();
                return "synthetic-explicit-token";
            }
            @Override public boolean equals(final Object other) {
                throw new AssertionError("Supplier.equals must not be invoked");
            }
            @Override public int hashCode() {
                throw new AssertionError("Supplier.hashCode must not be invoked");
            }
            @Override public String toString() {
                throw new AssertionError("Supplier.toString must not be invoked");
            }
        };
        try (Harness pool = new Harness(Route.DIRECT, fixture.session)) {
            pool.register(provider, false);
            assertThrows(IllegalArgumentException.class, () -> pool.register(provider, false));
            assertEquals(0, calls.get());
            try (SmtpTransportLease lease = pool.claim()) {
                assertEquals("synthetic-explicit-token", ((RecordingTransport) lease.getTransport()).password);
            }
            assertEquals(1, calls.get());
        }
    }

    static <K> SmtpClusterConfig<K> settings() {
        final SmtpClusterConfig<K> settings = new SmtpClusterConfig<>();
        settings.getConfigBuilder().defaultCorePoolSize(0).defaultMaxPoolSize(1)
                .defaultExpirationPolicy(ignored -> false).claimTimeout(new Timeout(2, SECONDS));
        return settings;
    }

    static final class Harness implements AutoCloseable {
        final SmtpConnectionPool direct;
        final SmtpConnectionPoolClustered<String> clustered;
        final ResourcePoolKey<Session> directKey;
        final ResourceClusterAndPoolKey<String, Session> clusteredKey;
        final ResourceClusters<?, Session, SessionTransport> manager;

        Harness(final Route route, final Session session) {
            direct = route == Route.DIRECT ? new SmtpConnectionPool(settings()) : null;
            clustered = route == Route.CLUSTERED ? new SmtpConnectionPoolClustered<>(settings()) : null;
            directKey = new ResourcePoolKey<>(session);
            clusteredKey = new ResourceClusterAndPoolKey<>("cluster", session);
            manager = direct != null ? direct : clustered;
        }

        void register(final Supplier<String> provider, final boolean customSizing) {
            if (customSizing) {
                registerSized(provider, 0, 1);
            } else if (direct != null) {
                direct.registerResourcePool(directKey, provider);
            } else {
                clustered.registerResourcePool(clusteredKey, provider);
            }
        }

        void registerSized(final Supplier<String> provider, final int core, final int max) {
            if (direct != null) {
                direct.registerResourcePool(directKey, ignored -> false, core, max, provider);
            } else {
                clustered.registerResourcePool(clusteredKey, ignored -> false, core, max, provider);
            }
        }

        void registerLegacy() {
            if (direct != null) {
                direct.registerResourcePool(directKey);
            } else {
                clustered.registerResourcePool(clusteredKey);
            }
        }

        boolean isRegistered() {
            return direct != null ? direct.isPoolRegistered(directKey) : clustered.isPoolRegistered(clusteredKey);
        }

        SmtpTransportLease claim() throws InterruptedException {
            return direct != null ? direct.claimTransport(directKey.getPoolKey()) : clustered.claimTransport(clusteredKey);
        }

        @Override public void close() throws Exception {
            manager.shutDown().get(3, SECONDS);
        }
    }

    static final class Fixture {
        final Properties properties = new Properties();
        final Session session;
        final List<RecordingTransport> created = new CopyOnWriteArrayList<>();
        volatile boolean requiresPassword;
        volatile MessagingException closeFailure;

        Fixture(final String protocol, final Authenticator authenticator) throws Exception {
            properties.setProperty("mail.transport.protocol", protocol);
            properties.setProperty("mail." + protocol + ".host", "localhost");
            properties.setProperty("mail." + protocol + ".user", USER);
            properties.setProperty("mail." + (protocol.equals("smtp") ? "smtps" : "smtp") + ".user", "inactive@example.test");
            properties.put("test.fixture", this);
            session = Session.getInstance(properties, authenticator);
            session.setProvider(new Provider(Provider.Type.TRANSPORT, protocol, RecordingTransport.class.getName(), "test", "1"));
        }

        AtomicInteger installStaleAliases() {
            final AtomicInteger calls = new AtomicInteger();
            properties.setProperty(OAUTH2_TOKEN_PROPERTY, "stale-token");
            properties.put(OAUTH2_TOKEN_PROVIDER_PROPERTY, (Supplier<String>) () -> {
                calls.incrementAndGet();
                throw new IllegalStateException("obsolete callback must not run");
            });
            return calls;
        }
    }

    public static final class RecordingTransport extends Transport {
        final Fixture fixture;
        final AtomicInteger connections = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        volatile String username;
        volatile String password;

        public RecordingTransport(final Session session, final URLName url) {
            super(session, url);
            fixture = (Fixture) session.getProperties().get("test.fixture");
            fixture.created.add(this);
        }

        @Override protected boolean protocolConnect(final String host, final int port, final String user, final String suppliedPassword) {
            connections.incrementAndGet();
            username = user;
            password = suppliedPassword;
            return !fixture.requiresPassword || suppliedPassword != null;
        }

        @Override public void sendMessage(final Message message, final Address[] recipients) {
            throw new UnsupportedOperationException("Fixture records authentication only");
        }

        @Override public synchronized void close() throws MessagingException {
            closes.incrementAndGet();
            setConnected(false);
            if (fixture.closeFailure != null) {
                throw fixture.closeFailure;
            }
        }
    }
}
