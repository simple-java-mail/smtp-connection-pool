package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TransportAllocatorTest {

	@Test
	public void deallocateShouldIgnoreTransportCloseFailures()
			throws Exception {
		final Session session = mock(Session.class);
		final Transport transport = mock(Transport.class);
		doThrow(new MessagingException("connection already closed")).when(transport).close();

		new TransportAllocator(session).deallocate(new SessionTransport(session, transport));

		verify(transport).close();
	}
}
