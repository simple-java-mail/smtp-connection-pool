package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.bbottema.genericobjectpool.ClaimControl;
import org.bbottema.genericobjectpool.ClaimOptions;
import org.bbottema.genericobjectpool.AllocationContext;
import org.bbottema.genericobjectpool.util.Timeout;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import static java.util.concurrent.TimeUnit.SECONDS;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConnectionObservationTest {
    @Test
    void contextAwareObserverDistinguishesClaimsAndBackgroundConnections() throws Exception {
        final Session session = mock(Session.class);
        final Transport transport = mock(Transport.class);
        when(session.getProperties()).thenReturn(new Properties());
        when(session.getTransport()).thenReturn(transport);
        final List<AllocationContext> contexts = new ArrayList<>();
        final TransportConnectionObserver observer = new TransportConnectionObserver() {
            @Override public Attempt connectionStarting(final Session selected, final Transport connection, final AllocationContext context) {
                contexts.add(context);
                return Attempt.NONE;
            }
        };
        final TransportAllocator allocator = new TransportAllocator(session, ignored -> Optional.empty(), observer);
        final AllocationContext first = ClaimOptions.withoutTimeout().start();
        final AllocationContext second = ClaimOptions.withoutTimeout().start();
        allocator.allocate(first.limitedTo(new Timeout(30, SECONDS)));
        allocator.allocate(second);
        allocator.allocate();
        assertSame(first.getClaimIdentity(), contexts.get(0).getClaimIdentity());
        assertSame(second.getClaimIdentity(), contexts.get(1).getClaimIdentity());
        assertNotSame(contexts.get(0).getClaimIdentity(), contexts.get(1).getClaimIdentity());
        assertNull(contexts.get(2));
    }

    @Test
    void successAndFailureAreReportedExactlyOnceAndFailureKeepsItsIdentity() throws Exception {
        final Session session = mock(Session.class);
        final Transport transport = mock(Transport.class);
        when(session.getProperties()).thenReturn(new Properties());
        when(session.getTransport()).thenReturn(transport);
        final List<Object> events = new ArrayList<>();
        final TransportConnectionObserver observer = observer(events);
        final TransportAllocator allocator = new TransportAllocator(session, ignored -> Optional.empty(), observer);
        allocator.allocate();
        assertEquals(Arrays.asList("start", "connected"), events);
        events.clear();
        final MessagingException failure = new MessagingException("connection failed");
        doThrow(failure).when(transport).connect();
        assertSame(failure, assertThrows(SmtpConnectionException.class, allocator::allocate).getCause());
        assertEquals(Arrays.asList("start", failure), events);
        verify(transport).close();
    }

    @Test
    void runtimeFailureStillTerminatesTheObservationWithoutInventingSmtpEvidence() throws Exception {
        final Session session = mock(Session.class);
        final Transport transport = mock(Transport.class);
        when(session.getProperties()).thenReturn(new Properties());
        when(session.getTransport()).thenReturn(transport);
        final List<Object> events = new ArrayList<>();
        final IllegalStateException failure = new IllegalStateException("provider configuration");
        doThrow(failure).when(transport).connect();
        final TransportAllocator allocator = new TransportAllocator(session, ignored -> Optional.empty(), observer(events));
        assertSame(failure, assertThrows(IllegalStateException.class, allocator::allocate));
        assertEquals(Arrays.asList("start", failure), events);
    }

    @Test
    void cancellationDoesNotReportAConnectionOutage() throws Exception {
        final Session session = mock(Session.class);
        final Transport transport = mock(Transport.class);
        when(session.getProperties()).thenReturn(new Properties());
        when(session.getTransport()).thenReturn(transport);
        final ClaimControl control = new ClaimControl();
        doAnswer(invocation -> {
            control.requestCancellation();
            throw new MessagingException("socket closed by cancellation");
        }).when(transport).connect();
        final List<Object> events = new ArrayList<>();
        final TransportAllocator allocator = new TransportAllocator(session, ignored -> Optional.empty(), observer(events));
        assertThrows(SmtpConnectionException.class, () -> allocator.allocate(ClaimOptions.withoutTimeout().withClaimControl(control).start()));
        assertEquals(Arrays.asList("start", "abandoned"), events);
        verify(transport).close();
    }

    @Test
    void backgroundGateDoesNotDisableExplicitAllocation() throws Exception {
        final Session session = mock(Session.class);
        final Transport transport = mock(Transport.class);
        when(session.getProperties()).thenReturn(new Properties());
        when(session.getTransport()).thenReturn(transport);
        final TransportAllocator allocator = new TransportAllocator(session, ignored -> Optional.empty(), new TransportConnectionObserver() {
            @Override public boolean isBackgroundConnectionAllowed(final Session selected) { return false; }
        });
        assertFalse(allocator.isBackgroundAllocationAllowed());
        allocator.allocate();
        verify(transport).connect();
    }

    private static TransportConnectionObserver observer(final List<Object> events) {
        return new TransportConnectionObserver() {
            @Override public Attempt connectionStarting(final Session session, final Transport transport) {
                events.add("start");
                return new Attempt() {
                    @Override public void connected() { events.add("connected"); }
                    @Override public void failed(final Throwable failure) { events.add(failure); }
                    @Override public void abandoned() { events.add("abandoned"); }
                };
            }
        };
    }
}
