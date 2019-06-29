package org.simplejavamail.smtpconnectionpool;

import org.bbottema.genericobjectpool.Allocator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Transport;

import static org.slf4j.LoggerFactory.getLogger;

class TransportAllocator extends Allocator<Transport> {

	private static final Logger LOGGER = getLogger(TransportAllocator.class);

	private final Session session;

	TransportAllocator(final Session session) {
		this.session = session;
	}

	@NotNull
	@Override
	public Transport allocate() {
		LOGGER.trace("opening transport connection...");
		try {
			return session.getTransport();
		} catch (NoSuchProviderException e) {
			throw new RuntimeException("unable to get transport from session", e);
		}
	}
	
	@Override
	public void deallocate(@NotNull Transport transport) {
		LOGGER.trace("closing transport...");
		try {
			transport.close();
		} catch (MessagingException e) {
			throw new RuntimeException("error closing transport connection", e);
		}
	}
}