package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.bbottema.genericobjectpool.PoolableObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpTransportLeaseTest {
    @Test
    void releaseIsAnExactlyOnceTerminalOutcome() {
        final PoolableObject<SessionTransport> claimed = claimedTransport();
        final SmtpTransportLease lease = new SmtpTransportLease(claimed);

        assertTrue(lease.release());
        assertFalse(lease.release());
        assertFalse(lease.invalidate());
        assertEquals(SmtpTransportLease.State.RELEASED, lease.getState());
        verify(claimed, times(1)).release();
    }

    @Test
    void invalidateIsAnExactlyOnceTerminalOutcome() {
        final PoolableObject<SessionTransport> claimed = claimedTransport();
        final SmtpTransportLease lease = new SmtpTransportLease(claimed);

        assertTrue(lease.invalidate());
        lease.close();
        assertEquals(SmtpTransportLease.State.INVALIDATED, lease.getState());
        verify(claimed, times(1)).invalidate();
    }

    @Test
    void failedReleaseFallsBackToInvalidation() {
        final PoolableObject<SessionTransport> claimed = claimedTransport();
        final SmtpTransportLease lease = new SmtpTransportLease(claimed);
        doThrow(new IllegalStateException("release failed")).when(claimed).release();

        assertThrows(IllegalStateException.class, lease::release);

        assertEquals(SmtpTransportLease.State.INVALIDATED, lease.getState());
        verify(claimed, times(1)).invalidate();
    }

    @SuppressWarnings("unchecked")
    private static PoolableObject<SessionTransport> claimedTransport() {
        final PoolableObject<SessionTransport> claimed = mock(PoolableObject.class);
        when(claimed.getAllocatedObject()).thenReturn(new SessionTransport(mock(Session.class), mock(Transport.class)));
        return claimed;
    }
}
