package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.MessagingException;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.bbottema.genericobjectpool.AllocationContext;
import org.bbottema.genericobjectpool.Allocator;
import org.bbottema.genericobjectpool.CancellationRegistration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.simplejavamail.smtpconnectionpool.SmtpConnectionPool.OAUTH2_TOKEN_PROPERTY;
import static org.simplejavamail.smtpconnectionpool.SmtpConnectionPool.OAUTH2_TOKEN_PROVIDER_PROPERTY;
import static org.slf4j.LoggerFactory.getLogger;

/** Owns a new transport until preparation succeeds; the generic pool owns reuse and eventual disposal afterwards. */
class TransportAllocator extends Allocator<SessionTransport> {

	private static final Logger LOGGER = getLogger(TransportAllocator.class);

	@NotNull private final Session session;
	private final TransportCancellationSupport cancellationSupport;
	private final TransportConnectionObserver connectionObserver;

	TransportAllocator(@NotNull final Session session) {
		this(session, transport -> Optional.empty());
	}

	TransportAllocator(final Session session, final TransportCancellationSupport cancellationSupport) {
		this(session, cancellationSupport, TransportConnectionObserver.NONE);
	}

	TransportAllocator(final Session session, final TransportCancellationSupport cancellationSupport, final TransportConnectionObserver connectionObserver) {
		this.session = session;
		this.cancellationSupport = cancellationSupport;
		this.connectionObserver = connectionObserver;
	}

	@Override
	public boolean isBackgroundAllocationAllowed() {
		return connectionObserver.isBackgroundConnectionAllowed(session);
	}

	@NotNull
	@Override
	public SessionTransport allocate() {
		return prepareNewTransport(null);
	}

	/** Registers cooperative abort before any connection work, not after a lease has already been produced. */
	@NotNull
	@Override
	public SessionTransport allocate(final AllocationContext context) {
		return prepareNewTransport(context);
	}

	private SessionTransport prepareNewTransport(final AllocationContext context) {
		checkCancellation(context);
		final Transport transport = obtainTransport();
		try {
			checkCancellation(context);
			final Optional<Runnable> abortAction = requireNonNull(cancellationSupport.createAbortAction(transport), "abortAction");
			final SessionTransport prepared = new SessionTransport(session, transport, abortAction);
			try (CancellationRegistration ignored = registerAbort(prepared, context)) {
				connectTransport(transport, context);
				checkCancellation(context);
				return prepared;
			}
		} catch (RuntimeException | Error preparationFailure) {
			closeAfterFailedPreparation(transport, preparationFailure);
			throw preparationFailure;
		}
	}

	private Transport obtainTransport() {
		try {
			return session.getTransport();
		} catch (NoSuchProviderException failure) {
			throw new TransportHandlingException("Unable to obtain an SMTP transport from the configured Session", failure);
		}
	}

	@Override
	public void allocateForReuse(final SessionTransport sessionTransport) {
		prepareForReuse(sessionTransport, null);
	}

	/** The generic pool retains ownership and disposes the existing transport if reuse fails or cancellation wins. */
	@Override
	public void allocateForReuse(final SessionTransport sessionTransport, final AllocationContext context) {
		prepareForReuse(sessionTransport, context);
	}

	private void prepareForReuse(final SessionTransport prepared, final AllocationContext context) {
		try (CancellationRegistration ignored = registerAbort(prepared, context)) {
			if (canContinue(context) && !prepared.getTransport().isConnected() && canContinue(context)) {
				connectTransport(prepared.getTransport(), context);
			}
			checkCancellation(context);
		}
	}

	private static CancellationRegistration registerAbort(final SessionTransport prepared, final AllocationContext context) {
		if (context == null || !prepared.getAbortAction().isPresent()) {
			return () -> { };
		}
		return context.onCancellation(prepared.getAbortAction().get());
	}

	private void connectTransport(final Transport transport, final AllocationContext context) {
		if (!canContinue(context)) {
			return;
		}
		final String oauth2Token = resolveOAuth2Token();
		if (!canContinue(context)) {
			return;
		}
		LOGGER.trace("opening transport connection...");
		final TransportConnectionObserver.Attempt observation = startObservation(transport);
		try {
			if (oauth2Token != null) {
				// OAuth2 uses the explicit credential route; Session's Authenticator remains unchanged.
				transport.connect(session.getProperties().getProperty("mail.smtp.user"), oauth2Token);
			} else {
				transport.connect();
			}
		} catch (MessagingException failure) {
			notifyObservation(() -> reportConnectionFailure(observation, failure, context));
			throw new SmtpConnectionException(failure);
		} catch (RuntimeException | Error failure) {
			notifyObservation(() -> reportConnectionFailure(observation, failure, context));
			throw failure;
		}
		notifyObservation(isAbandoned(context) ? observation::abandoned : observation::connected);
	}

	private static void reportConnectionFailure(final TransportConnectionObserver.Attempt observation, final Throwable failure,
			final AllocationContext context) {
		if (isAbandoned(context)) {
			observation.abandoned();
		} else {
			observation.failed(failure);
		}
	}

	private static boolean isAbandoned(final AllocationContext context) {
		return Thread.currentThread().isInterrupted() || context != null && (context.isCancellationRequested() || context.isTimedOut());
	}

	private TransportConnectionObserver.Attempt startObservation(final Transport transport) {
		try {
			return requireNonNull(connectionObserver.connectionStarting(session, transport), "connection observation");
		} catch (RuntimeException failure) {
			LOGGER.warn("Connection observation could not start; leaving the connection result unchanged", failure);
			return TransportConnectionObserver.Attempt.NONE;
		}
	}

	private static void notifyObservation(final Runnable notification) {
		try {
			notification.run();
		} catch (RuntimeException failure) {
			LOGGER.warn("Connection observation failed; leaving the connection result unchanged", failure);
		}
	}

	private static boolean canContinue(final AllocationContext context) {
		checkCancellation(context);
		// Returning the prepared object lets the generic pool discard it and retain its normal timeout result.
		return context == null || !context.isTimedOut();
	}

	private static void checkCancellation(final AllocationContext context) {
		if (context != null) {
			context.throwIfCancellationRequested();
		}
	}

	private String resolveOAuth2Token() {
		final Properties properties = session.getProperties();
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
		} catch (RuntimeException failure) {
			throw new TransportHandlingException("The OAuth2 token provider failed while obtaining an access token", failure);
		}
		if (!(providedToken instanceof String) || ((String) providedToken).trim().isEmpty()) {
			throw new TransportHandlingException("The OAuth2 token provider returned a blank access token", null);
		}
		return (String) providedToken;
	}

	private static void closeAfterFailedPreparation(final Transport transport, final Throwable primaryFailure) {
		try {
			transport.close();
		} catch (MessagingException | RuntimeException | Error cleanupFailure) {
			if (cleanupFailure != primaryFailure) {
				primaryFailure.addSuppressed(cleanupFailure);
			}
		}
	}

	@Override
	public void deallocate(final SessionTransport sessionTransport) {
		LOGGER.trace("closing transport...");
		try {
			sessionTransport.getTransport().close();
		} catch (MessagingException failure) {
			throw new TransportHandlingException("Error while disposing the SMTP transport", failure);
		}
	}
}
