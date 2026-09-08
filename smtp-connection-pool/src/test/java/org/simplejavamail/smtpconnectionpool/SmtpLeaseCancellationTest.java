package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.bbottema.genericobjectpool.PoolableObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpLeaseCancellationTest {
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final List<SmtpTransportLease> leases = new ArrayList<>();
    private final List<AbortableTestTransport> transports = new ArrayList<>();
    private final SmtpConnectionPool pool = new SmtpConnectionPool(SmtpClaimCancellationTest.configuration(true));

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
        pool.shutDown().get(5, SECONDS);
    }

    @ParameterizedTest
    @EnumSource(value = AbortableTestTransport.Point.class, names = {"GREETING", "EHLO", "STARTTLS", "AUTH", "MAIL", "RCPT", "DATA", "FINAL_REPLY"})
    void aCapableProviderCanExitBlockedWorkWithoutTheSendMonitor(final AbortableTestTransport.Point point) throws Exception {
        final SmtpTransportLease lease = claim();
        final AbortableTestTransport transport = (AbortableTestTransport) lease.getTransport();
        transport.blockedAt = point;
        final Future<?> operation = workers.submit(() -> { transport.sendMessage(null, null); return null; });
        assertTrue(transport.entered.await(2, SECONDS));
        assertTrue(lease.getCancellation().get().request());
        assertEquals(SmtpTransportLease.State.INVALIDATED, lease.getState());
        assertFalse(lease.release());
        assertThrows(Exception.class, () -> operation.get(2, SECONDS), "The physical operation must exit, not just a wrapper future");
        lease.getDisposalCompletion().toCompletableFuture().get(2, SECONDS);
        assertEquals(1, transport.closes.get());
        assertEquals(1, transport.aborts.get());
        assertFalse(transport.accepted);
        if (point.ordinal() >= AbortableTestTransport.Point.STARTTLS.ordinal()) {
            assertEquals(1, transport.socketGeneration.get(), "The abort still applies after the fixture replaces its socket");
        }
        assertTrue(claim().isActive(), "A later send can use a healthy transport");
    }

    @Test
    void aDelayedHandleFromAnOldLeaseCannotAbortTheNextBorrower() throws Exception {
        final SmtpTransportLease first = claim();
        final TransportCancellation delayed = first.getCancellation().get();
        final CountDownLatch proceed = new CountDownLatch(1);
        final Future<Boolean> oldCallback = workers.submit(() -> {
            assertTrue(proceed.await(2, SECONDS));
            return delayed.request();
        });
        try {
            first.release();
            final SmtpTransportLease next = pool.claimTransport(first.getSession());
            leases.add(next);
            assertSame(first.getTransport(), next.getTransport());
            proceed.countDown();
            assertFalse(oldCallback.get(2, SECONDS));
            assertTrue(next.isActive());
            next.getTransport().sendMessage(null, null);
            assertTrue(((AbortableTestTransport) next.getTransport()).accepted);
            assertEquals(0, ((AbortableTestTransport) next.getTransport()).aborts.get());
        } finally {
            proceed.countDown();
        }
    }

    @Test
    void releaseAndAbortRaceHasExactlyOneWinner() throws Exception {
        for (int round = 0; round < 60; round++) {
            final SmtpTransportLease lease = claim();
            final AbortableTestTransport transport = (AbortableTestTransport) lease.getTransport();
            final CountDownLatch start = new CountDownLatch(1);
            final Future<Boolean> release = workers.submit(() -> { start.await(); return lease.release(); });
            final Future<Boolean> abort = workers.submit(() -> { start.await(); return lease.getCancellation().get().request(); });
            start.countDown();
            final boolean released = release.get(2, SECONDS);
            assertNotEquals(released, abort.get(2, SECONDS));
            assertEquals(released ? 0 : 1, transport.aborts.get());
            assertEquals(released ? SmtpTransportLease.State.RELEASED : SmtpTransportLease.State.INVALIDATED, lease.getState());
            assertFalse(lease.getCancellation().get().request());
            if (!released) {
                lease.getDisposalCompletion().toCompletableFuture().get(2, SECONDS);
                assertEquals(1, transport.closes.get());
            }
        }
    }

    @Test
    void disposalAcknowledgementWaitsForCleanupAndRetainsFailure() throws Exception {
        final SmtpTransportLease lease = claim();
        final AbortableTestTransport transport = (AbortableTestTransport) lease.getTransport();
        transport.blockClose = true;
        transport.failClose = true;
        assertTrue(lease.getCancellation().get().request());
        final CompletableFuture<Void> disposal = lease.getDisposalCompletion().toCompletableFuture();
        assertTrue(transport.closing.await(2, SECONDS));
        assertFalse(disposal.isDone());
        // Cancelling a detached observer cannot cancel the actual disposal.
        assertTrue(lease.getDisposalCompletion().toCompletableFuture().cancel(true));
        transport.finishClose.countDown();
        final Exception failure = assertThrows(Exception.class, () -> disposal.get(2, SECONDS));
        assertInstanceOf(TransportHandlingException.class, failure.getCause());
        assertEquals(1, transport.closes.get());
    }

    @Test
    void acceptedResultRemainsAcceptedAfterALateRequest() throws Exception {
        final SmtpTransportLease lease = claim();
        final AbortableTestTransport transport = (AbortableTestTransport) lease.getTransport();
        transport.sendMessage(null, null);
        assertTrue(transport.accepted);
        assertTrue(lease.getCancellation().get().request());
        lease.getDisposalCompletion().toCompletableFuture().get(2, SECONDS);
        assertTrue(transport.accepted, "The pool must not rewrite a completed send's result");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anAbortFailureStillInvalidatesAndPreservesTheOriginalFailure() {
        final IllegalStateException primary = new IllegalStateException("abort action failed");
        final IllegalArgumentException secondary = new IllegalArgumentException("invalidation failed");
        final PoolableObject<SessionTransport> claimed = mock(PoolableObject.class);
        when(claimed.getAllocatedObject()).thenReturn(new SessionTransport(mock(Session.class), mock(Transport.class),
                Optional.of(() -> { throw primary; })));
        doThrow(secondary).when(claimed).invalidate();
        final SmtpTransportLease lease = new SmtpTransportLease(claimed);
        assertSame(primary, assertThrows(IllegalStateException.class, () -> lease.getCancellation().get().request()));
        assertArrayEquals(new Throwable[]{secondary}, primary.getSuppressed());
        assertEquals(SmtpTransportLease.State.INVALIDATED, lease.getState());
        assertFalse(lease.release());
        verify(claimed, times(1)).invalidate();
        verify(claimed, never()).release();
    }

    @Test
    @SuppressWarnings("unchecked")
    void unsupportedTransportHasNoPretendCancellationCapability() {
        final PoolableObject<SessionTransport> claimed = mock(PoolableObject.class);
        when(claimed.getAllocatedObject()).thenReturn(new SessionTransport(mock(Session.class), mock(Transport.class)));
        final SmtpTransportLease lease = new SmtpTransportLease(claimed);
        assertFalse(lease.getCancellation().isPresent());
        assertTrue(lease.release());
    }

    private SmtpTransportLease claim() throws Exception {
        final Session session = mock(Session.class);
        when(session.getProperties()).thenReturn(new Properties());
        when(session.getTransport()).thenAnswer(ignored -> {
            final AbortableTestTransport transport = new AbortableTestTransport(session);
            transports.add(transport);
            return transport;
        });
        final SmtpTransportLease lease = pool.claimTransport(session);
        leases.add(lease);
        return lease;
    }
}
