package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.*;
import org.bbottema.genericobjectpool.Allocator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.function.Supplier;

import static org.simplejavamail.smtpconnectionpool.SmtpConnectionPool.OAUTH2_TOKEN_PROPERTY;
import static org.simplejavamail.smtpconnectionpool.SmtpConnectionPool.OAUTH2_TOKEN_PROVIDER_PROPERTY;
import static org.slf4j.LoggerFactory.getLogger;

class TransportAllocator extends Allocator<SessionTransport> {

	private static final Logger LOGGER = getLogger(TransportAllocator.class);

	@NotNull private final Session session;

	TransportAllocator(@NotNull final Session session) {
		this.session = session;
	}

	@NotNull
	@Override
	public SessionTransport allocate() {
		LOGGER.trace("opening transport connection...");
		try {
			Transport transport = session.getTransport();
			connectTransport(transport);
			return new SessionTransport(session, transport);
		} catch (NoSuchProviderException e) {
			throw new TransportHandlingException("Unable to obtain an SMTP transport from the configured Session", e);
		}
	}

	@Override
	public void allocateForReuse(SessionTransport sessionTransport) {
		if (!sessionTransport.getTransport().isConnected()) {
			connectTransport(sessionTransport.getTransport());
		}
	}

	private void connectTransport(Transport transport) {
		try {
			final String oauth2Token = resolveOAuth2Token();
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
			throw new TransportHandlingException("Error while opening the configured SMTP transport", e);
		}
	}

	private String resolveOAuth2Token() {
		final java.util.Properties properties = session.getProperties();
		final Object provider = properties.get(OAUTH2_TOKEN_PROVIDER_PROPERTY);
		if (provider == null) {
			return (String) properties.getOrDefault(OAUTH2_TOKEN_PROPERTY, null);
		}
		if (!(provider instanceof Supplier)) {
			throw new TransportHandlingException("The configured OAuth2 token provider is not a Supplier", null);
		}

		final Object providedToken;
		try {
			providedToken = ((Supplier<?>) provider).get();
		} catch (RuntimeException e) {
			throw new TransportHandlingException("The OAuth2 token provider failed while obtaining an access token", e);
		}
		if (!(providedToken instanceof String) || ((String) providedToken).trim().isEmpty()) {
			throw new TransportHandlingException("The OAuth2 token provider returned a blank access token", null);
		}
		return (String) providedToken;
	}

	@Override
	public void deallocate(SessionTransport sessionTransport) {
		LOGGER.trace("closing transport...");
		try {
			sessionTransport.getTransport().close();
		} catch (MessagingException e) {
			LOGGER.debug("Ignoring failure while closing SMTP transport; connection was already unusable.", e);
		}
	}
}
