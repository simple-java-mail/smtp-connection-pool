package org.simplejavamail.smtpconnectionpool;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.mail.MessagingException;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.bbottema.genericobjectpool.Allocator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

class TransportAllocator extends Allocator<Transport> {

	private static final Logger LOGGER = getLogger(TransportAllocator.class);

	private static final String OAUTH2_TOKEN_PROPERTY = "mail.imaps.sasl.mechanisms.oauth2.oauthToken";

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
			connectTransport(transport, session);
			return transport;
		} catch (NoSuchProviderException e) {
			throw new TransportHandlingException("unable to get transport from session:\n\t" + session.getProperties(), e);
		} catch (MessagingException e) {
			throw new TransportHandlingException("Error when trying to open connection to the server, session:\n\t" + session.getProperties(), e);
		}
	}

	private static void connectTransport(Transport transport, Session session) throws MessagingException {
		if (!session.getProperties().containsKey(OAUTH2_TOKEN_PROPERTY)) {
			transport.connect();
		} else {
			/*
			 * To connect using OAuth2 authentication, we need to connect slightly differently as we can't use only Session properties and the traditional Authenticator class for
			 * providing password. Instead, <em>mail.smtp.auth</em> is set to {@code false} and the OAuth2 authenticator should take over, but this is only triggered succesfully if we
			 * provide an empty non-null password, which is only possible using the alternative {@link Transport#connect(String, String)}.
			 */
			transport.connect(session.getProperties().getProperty("mail.smtp.user"), "");
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