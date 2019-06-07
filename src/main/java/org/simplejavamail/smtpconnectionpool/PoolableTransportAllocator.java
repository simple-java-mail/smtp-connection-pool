package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusterstormpot.util.SimpleDelegatingPoolable;
import org.slf4j.Logger;
import stormpot.Allocator;
import stormpot.Slot;

import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Transport;

import static org.slf4j.LoggerFactory.getLogger;

class PoolableTransportAllocator implements Allocator<SimpleDelegatingPoolable<Transport>> {

	private static final Logger LOGGER = getLogger(PoolableTransportAllocator.class);

	private final Session session;

	PoolableTransportAllocator(final Session session) {
		this.session = session;
	}

	@Override
	public SimpleDelegatingPoolable<Transport> allocate(final Slot slot)
			throws NoSuchProviderException {
		LOGGER.trace("opening transport connection...");
		return new SimpleDelegatingPoolable<>(slot, session.getTransport());
	}

	@Override
	public void deallocate(final SimpleDelegatingPoolable<Transport> poolable)
			throws MessagingException {
		LOGGER.trace("closing transport...");
		poolable.getDelegate().close();
	}
}