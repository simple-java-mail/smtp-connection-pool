package org.simplejavamail.smtpconnectionpool;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.mail.*;
import lombok.val;
import org.bbottema.genericobjectpool.Allocator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import static org.simplejavamail.smtpconnectionpool.SmtpConnectionPool.OAUTH2_TOKEN_PROPERTY;
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
			connectTransport(transport);
			return transport;
		} catch (NoSuchProviderException e) {
			throw new TransportHandlingException("unable to get transport from session:\n\t" + session.getProperties(), e);
		}
	}

	@Override
	public void allocateForReuse(Transport transport) {
		if (!transport.isConnected()) {
			connectTransport(transport);
		}
	}

	private void connectTransport(Transport transport) {
		try {
			val oauth2Token = (String) session.getProperties().getOrDefault(OAUTH2_TOKEN_PROPERTY, null);
			if (oauth2Token != null) {
				/*
				 * To connect using OAuth2 authentication, we need to connect slightly differently as we can't use only Session properties and the traditional Authenticator class for
				 * providing password. Instead, <em>mail.smtp.auth</em> is set to {@code false} and the OAuth2 authenticator should take over, but this is only triggered succesfully if we
				 * provide an empty non-null password, which is only possible using the alternative {@link Transport#connect(String, String)}.
				 */
				transport.connect(session.getProperties().getProperty("mail.smtp.user"), oauth2Token);
			} else {
				transport.connect();
			}
		} catch (MessagingException e) {
			throw new TransportHandlingException("Error when trying to open connection to the server, session:\n\t" + session.getProperties(), e);
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