package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourceClusterAndPoolKey;
import org.bbottema.genericobjectpool.ClaimControl;
import org.bbottema.genericobjectpool.ClaimOptions;
import org.bbottema.genericobjectpool.util.Timeout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmtpClaimCancellationTest {
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final List<ResourceClusters<?, ?, ?>> pools = new ArrayList<>();
    private final Queue<SmtpTransportLease> leases = new ConcurrentLinkedQueue<>();
    private final Queue<AbortableTestTransport> transports = new ConcurrentLinkedQueue<>();
    private final AtomicInteger creations = new AtomicInteger();
    private final Properties properties = new Properties();

    @AfterEach
    void closeResources() throws Exception {
        for (final AbortableTestTransport transport : transports) {
            transport.releaseOperation();
            transport.finishClose.countDown();
        }
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(5, SECONDS));
        for (final SmtpTransportLease lease : leases) {
            lease.invalidate();
        }
        for (final ResourceClusters<?, ?, ?> pool : pools) {
            pool.shutDown().get(5, SECONDS);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECT", "KEYED", "BALANCED"})
    void onlyTheCancelledWaiterLeavesAndTheSameTransportRemainsReusable(final String route) throws Exception {
        final Session session = session(null);
        final SmtpConnectionPool direct = own(new SmtpConnectionPool(configuration(true)));
        final SmtpConnectionPoolClustered<String> clustered = own(new SmtpConnectionPoolClustered<>(configuration(true)));
        final ResourceClusterAndPoolKey<String, Session> key = new ResourceClusterAndPoolKey<>("cluster", session);
        clustered.registerResourcePool(key);
        final SmtpTransportLease held = claim(route, direct, clustered, session, ClaimOptions.withoutTimeout());
        final ClaimControl control = new ClaimControl();
        final AtomicReference<Thread> caller = new AtomicReference<>();
        final Future<?> pending = workers.submit(() -> {
            caller.set(Thread.currentThread());
            return claim(route, direct, clustered, session, options(control));
        });
        awaitPoolWait(caller);
        // An unrelated Session is not cancelled or retired.
        final SmtpTransportLease neighbour = remember(direct.claimTransport(session(null), ClaimOptions.withoutTimeout()));
        control.requestCancellation();
        assertInstanceOf(CancellationException.class, assertThrows(Exception.class, () -> pending.get(2, SECONDS)).getCause());
        assertTrue(neighbour.isActive());
        assertTrue(held.isActive());
        held.release();
        final SmtpTransportLease reused = claim(route, direct, clustered, session, ClaimOptions.withoutTimeout());
        assertSame(held.getTransport(), reused.getTransport());
        assertEquals(0, ((AbortableTestTransport) reused.getTransport()).aborts.get());
    }

    @Test
    void preCancelledAndZeroBudgetClaimsDoNotCreateTransports() throws Exception {
        final Session session = session(null);
        final SmtpConnectionPool pool = own(new SmtpConnectionPool(configuration(true)));
        final ClaimControl control = new ClaimControl();
        control.requestCancellation();
        assertThrows(CancellationException.class, () -> pool.claimTransport(session, options(control)));
        assertThrows(IllegalStateException.class, () -> pool.claimTransport(session, ClaimOptions.withTimeout(0, SECONDS)));
        assertEquals(0, creations.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aSlowTokenCallbackCannotContinueIntoSmtpAfterCancellation(final boolean failToken) throws Exception {
        final CountDownLatch resolving = new CountDownLatch(1);
        final CountDownLatch finish = new CountDownLatch(1);
        properties.put(SmtpConnectionPool.OAUTH2_TOKEN_PROVIDER_PROPERTY, (Supplier<String>) () -> {
            resolving.countDown();
            await(finish);
            if (failToken) {
                throw new IllegalStateException("Credential callback failed");
            }
            return "fixture-access-token";
        });
        final Session session = session(null);
        final SmtpConnectionPool pool = own(new SmtpConnectionPool(configuration(true)));
        final ClaimControl control = new ClaimControl();
        try {
            final Future<?> pending = workers.submit(() -> remember(pool.claimTransport(session, options(control))));
            assertTrue(resolving.await(2, SECONDS));
            control.requestCancellation();
            assertFalse(pending.isDone(), "A user credential callback must actually return before the claim settles");
            finish.countDown();
            assertInstanceOf(CancellationException.class, assertThrows(Exception.class, () -> pending.get(2, SECONDS)).getCause());
            final AbortableTestTransport abandoned = transports.element();
            assertFalse(abandoned.connected);
            assertEquals(1, abandoned.closes.get());
            properties.remove(SmtpConnectionPool.OAUTH2_TOKEN_PROVIDER_PROPERTY);
            assertNotSame(abandoned, remember(pool.claimTransport(session)).getTransport());
        } finally {
            finish.countDown();
        }
    }

    @ParameterizedTest
    @EnumSource(value = AbortableTestTransport.Point.class, names = {"CONNECT", "READINESS"})
    void cooperativePreparationAbortExitsTheOperationAndDisposesBeforeReturning(final AbortableTestTransport.Point point) throws Exception {
        final Session session = session(point == AbortableTestTransport.Point.CONNECT ? point : null);
        final SmtpConnectionPool pool = own(new SmtpConnectionPool(configuration(true)));
        AbortableTestTransport reused = null;
        if (point == AbortableTestTransport.Point.READINESS) {
            final SmtpTransportLease first = remember(pool.claimTransport(session));
            reused = (AbortableTestTransport) first.getTransport();
            first.release();
            reused.blockedAt = point;
        }
        final ClaimControl control = new ClaimControl();
        final Future<?> pending = workers.submit(() -> remember(pool.claimTransport(session, options(control))));
        final AbortableTestTransport target = reused == null ? awaitTransport() : reused;
        assertTrue(target.entered.await(2, SECONDS));
        control.requestCancellation();
        assertInstanceOf(CancellationException.class, assertThrows(Exception.class, () -> pending.get(2, SECONDS)).getCause());
        assertEquals(1, target.aborts.get());
        assertEquals(1, target.closes.get());
        assertEquals(0, pool.countLiveResources());
    }

    @Test
    void reconnectIsCoveredByTheAcquisitionRegistration() throws Exception {
        final Session session = session(null);
        final SmtpConnectionPool pool = own(new SmtpConnectionPool(configuration(true)));
        final SmtpTransportLease first = remember(pool.claimTransport(session));
        final AbortableTestTransport transport = (AbortableTestTransport) first.getTransport();
        first.release();
        transport.connected = false;
        transport.blockedAt = AbortableTestTransport.Point.CONNECT;
        final ClaimControl control = new ClaimControl();
        final Future<?> pending = workers.submit(() -> remember(pool.claimTransport(session, options(control))));
        assertTrue(transport.entered.await(2, SECONDS));
        control.requestCancellation();
        assertInstanceOf(CancellationException.class, assertThrows(Exception.class, () -> pending.get(2, SECONDS)).getCause());
        assertEquals(1, transport.closes.get());
        assertNotSame(transport, remember(pool.claimTransport(session)).getTransport());
    }

    @Test
    void anUnsupportedProviderIsObservedOnlyAfterItsConnectReturns() throws Exception {
        final Session session = session(AbortableTestTransport.Point.CONNECT);
        final SmtpConnectionPool pool = own(new SmtpConnectionPool(configuration(false)));
        final ClaimControl control = new ClaimControl();
        final Future<?> pending = workers.submit(() -> remember(pool.claimTransport(session, options(control))));
        final AbortableTestTransport transport = awaitTransport();
        assertTrue(transport.entered.await(2, SECONDS));
        control.requestCancellation();
        assertFalse(pending.isDone());
        assertEquals(0, transport.aborts.get());
        transport.proceed.countDown();
        assertInstanceOf(CancellationException.class, assertThrows(Exception.class, () -> pending.get(2, SECONDS)).getCause());
        assertEquals(1, transport.closes.get());
    }

    @Test
    void lateConnectedTransportIsDiscardedWhenTheTotalBudgetExpired() throws Exception {
        final Session session = session(AbortableTestTransport.Point.CONNECT);
        final SmtpConnectionPool pool = own(new SmtpConnectionPool(configuration(false)));
        final Future<?> pending = workers.submit(() -> remember(pool.claimTransport(session, ClaimOptions.withTimeout(80, MILLISECONDS))));
        final AbortableTestTransport transport = awaitTransport();
        assertTrue(transport.entered.await(2, SECONDS));
        Thread.sleep(120);
        transport.proceed.countDown();
        assertInstanceOf(IllegalStateException.class, assertThrows(Exception.class, () -> pending.get(2, SECONDS)).getCause());
        assertEquals(1, transport.closes.get());
    }

    @Test
    void cancellingAnEarlierClaimCannotAbortItsLeaseOrTheNextBorrower() throws Exception {
        final Session session = session(null);
        final SmtpConnectionPool pool = own(new SmtpConnectionPool(configuration(true)));
        final ClaimControl control = new ClaimControl();
        final SmtpTransportLease first = remember(pool.claimTransport(session, options(control)));
        final AbortableTestTransport transport = (AbortableTestTransport) first.getTransport();
        control.requestCancellation();
        assertEquals(0, transport.aborts.get());
        assertTrue(first.isActive());
        first.release();
        assertSame(transport, remember(pool.claimTransport(session)).getTransport());
        assertEquals(0, transport.aborts.get());
    }

    @Test
    void aRequestMadeBeforeAbortRegistrationIsImmediatelyObserved() throws Exception {
        final Session session = session(null);
        final Properties original = new Properties();
        original.putAll(properties);
        final ClaimControl control = new ClaimControl();
        final SmtpClusterConfig<Session> configuration = configuration(false);
        configuration.withTransportCancellationSupport(transport -> {
            control.requestCancellation();
            return Optional.of(((AbortableTestTransport) transport)::abort);
        });
        final SmtpConnectionPool pool = own(new SmtpConnectionPool(configuration));
        assertThrows(CancellationException.class, () -> pool.claimTransport(session, options(control)));
        assertEquals(1, transports.element().aborts.get());
        assertEquals(1, transports.element().closes.get());
        assertFalse(transports.element().connected);
        assertEquals(original, session.getProperties());
    }

    @Test
    void cancelledPreparationWaitsForPartialTransportDisposal() throws Exception {
        final Session session = session(AbortableTestTransport.Point.CONNECT);
        final SmtpConnectionPool pool = own(new SmtpConnectionPool(configuration(false)));
        final ClaimControl control = new ClaimControl();
        final Future<?> pending = workers.submit(() -> remember(pool.claimTransport(session, options(control))));
        final AbortableTestTransport transport = awaitTransport();
        assertTrue(transport.entered.await(2, SECONDS));
        transport.blockClose = true;
        control.requestCancellation();
        transport.proceed.countDown();
        assertTrue(transport.closing.await(2, SECONDS));
        assertFalse(pending.isDone());
        transport.finishClose.countDown();
        assertInstanceOf(CancellationException.class, assertThrows(Exception.class, () -> pending.get(2, SECONDS)).getCause());
        assertEquals(1, transport.closes.get());
    }

    private Session session(final AbortableTestTransport.Point point) throws Exception {
        final Session session = mock(Session.class);
        when(session.getProperties()).thenReturn(properties);
        when(session.getTransport()).thenAnswer(ignored -> {
            final AbortableTestTransport transport = new AbortableTestTransport(session);
            transport.blockedAt = point;
            creations.incrementAndGet();
            transports.add(transport);
            return transport;
        });
        return session;
    }

    static <K> SmtpClusterConfig<K> configuration(final boolean capable) {
        final SmtpClusterConfig<K> configuration = new SmtpClusterConfig<>();
        configuration.getConfigBuilder().defaultCorePoolSize(0).defaultMaxPoolSize(1)
                .defaultExpirationPolicy(value -> false).claimTimeout(new Timeout(5, SECONDS));
        if (capable) {
            configuration.withTransportCancellationSupport(transport -> transport instanceof AbortableTestTransport
                    ? Optional.of(((AbortableTestTransport) transport)::abort) : Optional.empty());
        }
        return configuration;
    }

    private SmtpTransportLease claim(final String route, final SmtpConnectionPool direct,
                                     final SmtpConnectionPoolClustered<String> clustered, final Session session, final ClaimOptions options)
            throws Exception {
        if (route.equals("DIRECT")) {
            return remember(direct.claimTransport(session, options));
        }
        return remember(route.equals("KEYED")
                ? clustered.claimTransport(new ResourceClusterAndPoolKey<>("cluster", session), options)
                : clustered.claimTransportFromCluster("cluster", options));
    }

    private <T extends ResourceClusters<?, ?, ?>> T own(final T pool) {
        pools.add(pool);
        return pool;
    }

    private SmtpTransportLease remember(final SmtpTransportLease lease) {
        leases.add(lease);
        return lease;
    }

    private AbortableTestTransport awaitTransport() throws Exception {
        final long deadline = System.nanoTime() + SECONDS.toNanos(2);
        while (transports.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertFalse(transports.isEmpty());
        return transports.element();
    }

    private static void awaitPoolWait(final AtomicReference<Thread> caller) throws Exception {
        final long deadline = System.nanoTime() + SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (caller.get() != null && Arrays.stream(caller.get().getStackTrace())
                    .anyMatch(frame -> frame.getMethodName().equals("awaitAvailability"))) {
                return;
            }
            Thread.sleep(1);
        }
        fail("Claim did not reach pool availability waiting");
    }

    private static ClaimOptions options(final ClaimControl control) {
        return ClaimOptions.withTimeout(5, SECONDS).withClaimControl(control);
    }

    private static void await(final CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
