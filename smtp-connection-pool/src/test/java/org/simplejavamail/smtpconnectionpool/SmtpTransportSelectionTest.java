package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourceClusterAndPoolKey;
import org.bbottema.genericobjectpool.ClaimControl;
import org.bbottema.genericobjectpool.ClaimOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmtpTransportSelectionTest {

    private final SmtpConnectionPoolClustered<String> pool = new SmtpConnectionPoolClustered<>(SmtpClaimCancellationTest.configuration(true));
    private final AtomicInteger creations = new AtomicInteger();

    @AfterEach
    void close() throws Exception {
        pool.shutDown().get(5, SECONDS);
    }

    @Test
    void selectsSessionsWithoutCreatingTransportsAndClaimsInAnyOrder() throws Exception {
        final Session first = registerSession();
        final Session second = registerSession();
        final SmtpTransportSelection one = pool.selectTransportFromCluster("cluster");
        final SmtpTransportSelection two = pool.selectTransportFromCluster("cluster", ClaimOptions.withoutTimeout());
        assertSame(first, one.getSession());
        assertSame(second, two.getSession());
        assertEquals(0, creations.get());
        try (SmtpTransportLease lease = two.claimTransport(ClaimOptions.withoutTimeout())) {
            assertSame(second, lease.getSession());
        }
        try (SmtpTransportLease lease = one.claimTransport()) {
            assertSame(first, lease.getSession());
        }
        assertSame(first, pool.selectTransportFromCluster("cluster").getSession());
        assertEquals(2, creations.get());
    }

    @Test
    void cancellationAndTimeoutDoNotPoisonTheSelectionOrReplaceALease() throws Exception {
        final Session session = registerSession();
        final SmtpTransportSelection selected = pool.selectTransport(new ResourceClusterAndPoolKey<>("cluster", session));
        final ClaimControl control = new ClaimControl();
        control.requestCancellation();
        final ClaimOptions cancelled = ClaimOptions.withoutTimeout().withClaimControl(control);
        assertThrows(CancellationException.class, () -> selected.claimTransport(cancelled));
        assertThrows(CancellationException.class, () -> pool.selectTransportFromCluster("cluster", cancelled));
        assertEquals(0, creations.get());
        final SmtpTransportLease held = selected.claimTransport();
        try {
            assertThrows(IllegalStateException.class, () -> selected.claimTransport(ClaimOptions.withTimeout(20, MILLISECONDS)));
        } finally {
            held.release();
        }
        try (SmtpTransportLease reused = selected.claimTransport()) {
            assertSame(held.getTransport(), reused.getTransport());
            assertNotSame(held, reused);
        }
        assertEquals(1, creations.get());
    }

    @Test
    void retiredSelectionCannotClaimFromReplacementRegistration() throws Exception {
        final Session session = registerSession();
        final ResourceClusterAndPoolKey<String, Session> key = new ResourceClusterAndPoolKey<>("cluster", session);
        final SmtpTransportSelection old = pool.selectTransport(key, ClaimOptions.withoutTimeout());
        pool.shutdownPool(session).get(5, SECONDS);
        pool.registerResourcePool(key);
        assertThrows(IllegalStateException.class, old::claimTransport);
        assertEquals(0, creations.get());
        try (SmtpTransportLease current = pool.selectTransport(key).claimTransport()) {
            assertSame(session, current.getSession());
        }
    }

    @Test
    void absentAndTimedOutSelectionsDoNotRegisterOrCreateTransports() {
        final Session session = Session.getInstance(new Properties());
        assertThrows(IllegalStateException.class, () -> pool.selectTransport(new ResourceClusterAndPoolKey<>("missing", session)));
        assertThrows(IllegalStateException.class, () -> pool.selectTransportFromCluster("missing", ClaimOptions.withTimeout(0, SECONDS)));
        assertEquals(0, creations.get());
        assertEquals(0, pool.countLiveResources());
    }

    private Session registerSession() throws Exception {
        final Session session = mock(Session.class);
        when(session.getProperties()).thenReturn(new Properties());
        when(session.getTransport()).thenAnswer(ignored -> {
            creations.incrementAndGet();
            return new AbortableTestTransport(session);
        });
        pool.registerResourcePool(new ResourceClusterAndPoolKey<>("cluster", session));
        return session;
    }
}
