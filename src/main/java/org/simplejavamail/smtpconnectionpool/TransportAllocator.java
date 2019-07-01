package org.simplejavamail.smtpconnectionpool;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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

	@NotNull private final Session session;

	TransportAllocator(@NotNull final Session session) {
		this.session = session;
	}

	@NotNull
	@Override
	@SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE", justification = "generated code by se.eris Maven plugin")
	public Transport allocate() {
		LOGGER.trace("opening transport connection...");
		try {
			Transport transport = session.getTransport();
			transport.connect();
			return transport;
		} catch (NoSuchProviderException e) {
			throw new TransportHandlingException("unable to get transport from session:\n\t" + session, e);
		} catch (MessagingException e) {
			throw new TransportHandlingException("Error when trying to open connection to the server, session:\n\t" + session, e);
		}
	}
	
	@Override
	public void deallocate(Transport transport) {
		LOGGER.trace("closing transport...");
		try {
			transport.close();
		} catch (MessagingException e) {
			throw new TransportHandlingException("error closing transport connection", e);
		}
	}
}