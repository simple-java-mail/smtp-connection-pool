package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourceClusterAndPoolKey;
import org.bbottema.genericobjectpool.ClaimControl;
import org.bbottema.genericobjectpool.ClaimOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.simplejavamail.smtpconnectionpool.PoolAuthenticationRegistrationTest.Fixture;
import org.simplejavamail.smtpconnectionpool.PoolAuthenticationRegistrationTest.RecordingTransport;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.simplejavamail.smtpconnectionpool.PoolAuthenticationRegistrationTest.settings;
import static org.simplejavamail.smtpconnectionpool.SmtpConnectionPool.OAUTH2_TOKEN_PROPERTY;

/** Concurrent claims, eager creation and retirement keep authentication attached to the actual registration. */
@Timeout(20)
class PoolAuthenticationOwnershipTest {
    enum Stop { CANCELLATION, TIMEOUT }

    @Test
    void theSameSessionCanHaveIndependentAuthenticationInDifferentClusters() throws Exception {
        final Fixture fixture = new Fixture("smtp", null);
        final AtomicInteger staleCalls = fixture.installStaleAliases();
        final SmtpConnectionPoolClustered<String> pool = new SmtpConnectionPoolClustered<>(settings());
        final ResourceClusterAndPoolKey<String, Session> first = new ResourceClusterAndPoolKey<>("first", fixture.session);
        final ResourceClusterAndPoolKey<String, Session> second = new ResourceClusterAndPoolKey<>("second", fixture.session);
        try {
            pool.registerResourcePool(first, () -> "first-token");
            pool.registerResourcePool(second, () -> "second-token");
            try (SmtpTransportLease a = pool.claimTransport(first); SmtpTransportLease b = pool.claimTransport(second)) {
                assertEquals("first-token", ((RecordingTransport) a.getTransport()).password);
                assertEquals("second-token", ((RecordingTransport) b.getTransport()).password);
                assertNotSame(a.getTransport(), b.getTransport());
                assertEquals(2, pool.countLiveResources());
            }
            assertEquals(0, staleCalls.get());
        } finally {
            pool.shutDown().get(3, SECONDS);
        }
    }

    @Test
    void aRetiredSelectionCannotBorrowTheReplacementRegistrationOrInvokeItsCredentials() throws Exception {
        final Fixture fixture = new Fixture("smtp", null);
        final SmtpConnectionPoolClustered<String> pool = new SmtpConnectionPoolClustered<>(settings());
        final ResourceClusterAndPoolKey<String, Session> key = new ResourceClusterAndPoolKey<>("cluster", fixture.session);
        final AtomicInteger oldCalls = new AtomicInteger();
        final AtomicInteger newCalls = new AtomicInteger();
        SmtpTransportLease original = null;
        try {
            pool.registerResourcePool(key, () -> "old-" + oldCalls.incrementAndGet());
            final SmtpTransportSelection originalSelection = pool.selectTransport(key);
            original = originalSelection.claimTransport();
            final RecordingTransport oldTransport = (RecordingTransport) original.getTransport();
            final Future<Void> shutdown = pool.shutdownPool(fixture.session);
            assertFalse(shutdown.isDone());

            pool.registerResourcePool(key, () -> "new-" + newCalls.incrementAndGet());
            try (SmtpTransportLease replacement = pool.claimTransport(key)) {
                final RecordingTransport newTransport = (RecordingTransport) replacement.getTransport();
                assertEquals("new-1", newTransport.password);
                assertNotSame(oldTransport, newTransport);
                assertThrows(IllegalStateException.class, originalSelection::claimTransport);
                assertSame(fixture.session, originalSelection.getSession());
                assertEquals(1, oldCalls.get());
                assertEquals(1, newCalls.get());
                original.close();
                original = null;
                shutdown.get(3, SECONDS);
                assertFalse(oldTransport.isConnected());
                assertTrue(newTransport.isConnected());
            }
        } finally {
            if (original != null) {
                original.invalidate();
            }
            pool.shutDown().get(3, SECONDS);
        }
    }

    @Test
    void aConcurrentImplicitClaimCannotPickUpAuthenticationFromAnUnreservedExplicitRegistration() throws Exception {
        final Fixture fixture = new Fixture("smtp", null);
        fixture.properties.setProperty(OAUTH2_TOKEN_PROPERTY, "legacy-token");
        final SmtpConnectionPoolClustered<String> pool = new SmtpConnectionPoolClustered<>(settings());
        final ResourceClusterAndPoolKey<String, Session> actualKey = new ResourceClusterAndPoolKey<>("cluster", fixture.session);
        final CountDownLatch capturingKey = new CountDownLatch(1);
        final CountDownLatch continueRegistration = new CountDownLatch(1);
        final AtomicInteger explicitCalls = new AtomicInteger();
        final ResourceKey<String, Session> delayedKey = new ResourceKey<String, Session>() {
            @Override public String getClusterKey() { return "cluster"; }
            @Override public Session getPoolKey() {
                capturingKey.countDown();
                await(continueRegistration);
                return fixture.session;
            }
        };
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            final Future<?> registration = worker.submit(() -> pool.registerResourcePool(delayedKey,
                    () -> { explicitCalls.incrementAndGet(); return "explicit-token"; }));
            assertTrue(capturingKey.await(3, SECONDS));
            try (SmtpTransportLease lease = pool.claimTransport(actualKey)) {
                assertEquals("legacy-token", ((RecordingTransport) lease.getTransport()).password);
                continueRegistration.countDown();
                final ExecutionException duplicate = assertThrows(ExecutionException.class, () -> registration.get(3, SECONDS));
                assertInstanceOf(IllegalArgumentException.class, duplicate.getCause());
                assertEquals(0, explicitCalls.get());
            }
        } finally {
            continueRegistration.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(3, SECONDS));
            pool.shutDown().get(3, SECONDS);
        }
    }

    @Test
    void theRegistrationCapturesClusterAndSessionOnceWithoutRetainingAMutableKey() throws Exception {
        final Fixture original = new Fixture("smtp", null);
        final Fixture changed = new Fixture("smtp", null);
        final AtomicReference<Session> selected = new AtomicReference<>(original.session);
        final AtomicReference<String> cluster = new AtomicReference<>("original");
        final AtomicInteger keyReads = new AtomicInteger();
        final AtomicInteger clusterReads = new AtomicInteger();
        final ResourceKey<String, Session> mutableKey = new ResourceKey<String, Session>() {
            @Override public String getClusterKey() { clusterReads.incrementAndGet(); return cluster.get(); }
            @Override public Session getPoolKey() { keyReads.incrementAndGet(); return selected.get(); }
        };
        final SmtpConnectionPoolClustered<String> pool = new SmtpConnectionPoolClustered<>(settings());
        try {
            pool.registerResourcePool(mutableKey, () -> "original-token");
            selected.set(changed.session);
            cluster.set("changed");
            try (SmtpTransportLease lease = pool.claimTransport(new ResourceClusterAndPoolKey<>("original", original.session))) {
                assertSame(original.session, lease.getSession());
                assertEquals("original-token", ((RecordingTransport) lease.getTransport()).password);
            }
            assertEquals(1, keyReads.get());
            assertEquals(1, clusterReads.get());
            assertTrue(changed.created.isEmpty());
        } finally {
            pool.shutDown().get(3, SECONDS);
        }
    }

    @Test
    void anEagerCredentialCallbackDoesNotHoldTheRegistryLockWhileAnotherPoolRegisters() throws Exception {
        final Fixture first = new Fixture("smtp", null);
        final Fixture second = new Fixture("smtp", null);
        final SmtpClusterConfig<String> configuration = settings();
        configuration.getConfigBuilder().defaultCorePoolSize(1);
        final SmtpConnectionPoolClustered<String> pool = new SmtpConnectionPoolClustered<>(configuration);
        final ResourceClusterAndPoolKey<String, Session> firstKey = new ResourceClusterAndPoolKey<>("cluster", first.session);
        final ResourceClusterAndPoolKey<String, Session> secondKey = new ResourceClusterAndPoolKey<>("cluster", second.session);
        final CountDownLatch authenticated = new CountDownLatch(1);
        final ExecutorService otherRegistration = Executors.newSingleThreadExecutor();
        try {
            pool.registerResourcePool(firstKey, () -> {
                try {
                    otherRegistration.submit(() -> pool.registerResourcePool(secondKey, null)).get(3, SECONDS);
                } catch (Exception failure) {
                    throw new IllegalStateException("Another registration could not finish during the credential callback", failure);
                }
                authenticated.countDown();
                return "eager-token";
            });
            assertTrue(authenticated.await(4, SECONDS));
            try (SmtpTransportLease lease = pool.claimTransport(firstKey)) {
                assertEquals("eager-token", ((RecordingTransport) lease.getTransport()).password);
            }
            assertTrue(pool.isPoolRegistered(secondKey));
        } finally {
            otherRegistration.shutdownNow();
            assertTrue(otherRegistration.awaitTermination(3, SECONDS));
            pool.shutDown().get(3, SECONDS);
        }
    }

    @ParameterizedTest
    @EnumSource(Stop.class)
    void aStopDuringCredentialResolutionDiscardsThePreparedTransportBeforeConnecting(final Stop stop) throws Exception {
        final Fixture fixture = new Fixture("smtp", null);
        final AtomicInteger staleCalls = fixture.installStaleAliases();
        final SmtpConnectionPoolClustered<String> pool = new SmtpConnectionPoolClustered<>(settings());
        final ResourceClusterAndPoolKey<String, Session> key = new ResourceClusterAndPoolKey<>("cluster", fixture.session);
        final CountDownLatch resolving = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final ClaimControl cancellation = new ClaimControl();
        final ClaimOptions options = stop == Stop.CANCELLATION
                ? ClaimOptions.withTimeout(5, SECONDS).withClaimControl(cancellation)
                : ClaimOptions.withTimeout(80, MILLISECONDS);
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            pool.registerResourcePool(key, () -> {
                resolving.countDown();
                await(release);
                return "late-token";
            });
            final Future<SmtpTransportLease> claim = worker.submit(() -> pool.claimTransport(key, options));
            assertTrue(resolving.await(3, SECONDS));
            if (stop == Stop.CANCELLATION) {
                cancellation.requestCancellation();
            } else {
                Thread.sleep(120);
            }
            assertFalse(claim.isDone(), "An arbitrary application callback must return cooperatively");
            release.countDown();
            final ExecutionException stopped = assertThrows(ExecutionException.class, () -> claim.get(3, SECONDS));
            if (stop == Stop.CANCELLATION) {
                assertInstanceOf(CancellationException.class, stopped.getCause());
            } else {
                assertInstanceOf(IllegalStateException.class, stopped.getCause());
            }
            assertEquals(1, fixture.created.size());
            assertEquals(0, fixture.created.get(0).connections.get());
            assertEquals(1, fixture.created.get(0).closes.get());
            assertEquals(0, staleCalls.get());
            assertEquals(0, pool.countLiveResources());
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(3, SECONDS));
            pool.shutDown().get(3, SECONDS);
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(5, SECONDS)) {
                throw new IllegalStateException("Fixture latch timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Fixture interrupted", interrupted);
        }
    }
}
