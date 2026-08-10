package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.simplejavamail.smtpconnectionpool.SmtpConnectionPool.OAUTH2_TOKEN_PROPERTY;
import static org.simplejavamail.smtpconnectionpool.SmtpConnectionPool.OAUTH2_TOKEN_PROVIDER_PROPERTY;

public class TransportAllocatorTest {

	@Test
	public void allocateShouldResolveOAuth2TokenProviderAtConnectionTime()
			throws Exception {
		final Properties properties = oauth2Properties();
		final AtomicInteger calls = new AtomicInteger();
		properties.put(OAUTH2_TOKEN_PROVIDER_PROPERTY, (Supplier<String>) () -> "token-" + calls.incrementAndGet());
		final Session session = session(properties);
		final Transport transport = mock(Transport.class);
		when(session.getTransport()).thenReturn(transport);

		new TransportAllocator(session).allocate();

		assertEquals(1, calls.get());
		verify(transport).connect("user@example.com", "token-1");
	}

	@Test
	public void allocateForReuseShouldResolveProviderOnlyWhenDisconnected()
			throws Exception {
		final Properties properties = oauth2Properties();
		final AtomicInteger calls = new AtomicInteger();
		properties.put(OAUTH2_TOKEN_PROVIDER_PROPERTY, (Supplier<String>) () -> "token-" + calls.incrementAndGet());
		final Session session = session(properties);
		final Transport disconnected = mock(Transport.class);
		when(disconnected.isConnected()).thenReturn(false);
		final Transport connected = mock(Transport.class);
		when(connected.isConnected()).thenReturn(true);
		final TransportAllocator allocator = new TransportAllocator(session);

		allocator.allocateForReuse(new SessionTransport(session, disconnected));
		allocator.allocateForReuse(new SessionTransport(session, connected));

		assertEquals(1, calls.get());
		verify(disconnected).connect("user@example.com", "token-1");
		verify(connected, never()).connect("user@example.com", "token-1");
	}

	@Test
	public void allocateShouldRetainFixedOAuth2TokenFallback()
			throws Exception {
		final Properties properties = oauth2Properties();
		properties.setProperty(OAUTH2_TOKEN_PROPERTY, "fixed-token");
		final Session session = session(properties);
		final Transport transport = mock(Transport.class);
		when(session.getTransport()).thenReturn(transport);

		new TransportAllocator(session).allocate();

		verify(transport).connect("user@example.com", "fixed-token");
	}

	@Test
	public void allocateShouldRejectBlankProviderTokens()
			throws Exception {
		final Properties properties = oauth2Properties();
		properties.put(OAUTH2_TOKEN_PROVIDER_PROPERTY, (Supplier<String>) () -> "  ");
		final Session session = session(properties);
		when(session.getTransport()).thenReturn(mock(Transport.class));

		final TransportHandlingException error = assertThrows(TransportHandlingException.class,
				() -> new TransportAllocator(session).allocate());

		assertEquals("The OAuth2 token provider returned a blank access token", error.getMessage());
	}

	@Test
	public void allocateShouldPreserveProviderFailureCause()
			throws Exception {
		final Properties properties = oauth2Properties();
		final IllegalStateException cause = new IllegalStateException("refresh failed");
		properties.put(OAUTH2_TOKEN_PROVIDER_PROPERTY, (Supplier<String>) () -> {
			throw cause;
		});
		final Session session = session(properties);
		when(session.getTransport()).thenReturn(mock(Transport.class));

		final TransportHandlingException error = assertThrows(TransportHandlingException.class,
				() -> new TransportAllocator(session).allocate());

		assertSame(cause, error.getCause());
		assertEquals("The OAuth2 token provider failed while obtaining an access token", error.getMessage());
	}

	@Test
	public void concurrentAllocationsShouldResolveProviderIndependently()
			throws Exception {
		final int allocationCount = 8;
		final Properties properties = oauth2Properties();
		final AtomicInteger calls = new AtomicInteger();
		properties.put(OAUTH2_TOKEN_PROVIDER_PROPERTY, (Supplier<String>) () -> "token-" + calls.incrementAndGet());
		final Session session = session(properties);
		when(session.getTransport()).thenAnswer(invocation -> mock(Transport.class));
		final TransportAllocator allocator = new TransportAllocator(session);
		final ExecutorService executor = Executors.newFixedThreadPool(allocationCount);
		final List<Callable<SessionTransport>> allocations = new ArrayList<>();
		for (int i = 0; i < allocationCount; i++) {
			allocations.add(allocator::allocate);
		}

		try {
			final List<Future<SessionTransport>> results = executor.invokeAll(allocations);
			for (Future<SessionTransport> result : results) {
				result.get();
			}
		} finally {
			executor.shutdownNow();
		}

		assertEquals(allocationCount, calls.get());
	}

	@Test
	public void deallocateShouldIgnoreTransportCloseFailures()
			throws Exception {
		final Session session = mock(Session.class);
		final Transport transport = mock(Transport.class);
		doThrow(new MessagingException("connection already closed")).when(transport).close();

		new TransportAllocator(session).deallocate(new SessionTransport(session, transport));

		verify(transport).close();
	}

	private static Properties oauth2Properties() {
		final Properties properties = new Properties();
		properties.setProperty("mail.smtp.user", "user@example.com");
		return properties;
	}

	private static Session session(Properties properties) {
		final Session session = mock(Session.class);
		when(session.getProperties()).thenReturn(properties);
		return session;
	}
}
