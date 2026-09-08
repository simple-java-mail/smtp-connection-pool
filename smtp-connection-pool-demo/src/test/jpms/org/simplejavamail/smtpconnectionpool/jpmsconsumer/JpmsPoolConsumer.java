package org.simplejavamail.smtpconnectionpool.jpmsconsumer;

import jakarta.mail.Session;
import org.bbottema.genericobjectpool.ClaimControl;
import org.bbottema.genericobjectpool.ClaimOptions;
import org.simplejavamail.smtpconnectionpool.SmtpTransportLease;
import org.simplejavamail.smtpconnectionpool.TransportCancellation;
import org.simplejavamail.smtpconnectionpool.TransportCancellationSupport;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.simplejavamail.batch.BatchTransportExecutor;
import org.simplejavamail.smtpconnectionpool.SmtpClusterConfig;
import org.simplejavamail.smtpconnectionpool.SmtpConnectionPool;
import org.simplejavamail.smtpconnectionpool.jakarta.SmtpPoolProperties;

public final class JpmsPoolConsumer {

	private JpmsPoolConsumer() {
	}

	public static SmtpConnectionPool newPool() {
		return new SmtpConnectionPool(new SmtpClusterConfig<Session>());
	}

	public static BatchTransportExecutor<String> newBatchExecutor() {
		return BatchTransportExecutor.<String>builder().withMaxPoolSize(1).build();
	}

	public static String providerProtocol() {
		return SmtpPoolProperties.PROTOCOL;
	}

	/** Proves the opt-in API is consumable without implementation-package access on the module path. */
	public static CompletionStage<Void> cancellationApi(final Session session, final TransportCancellationSupport support)
			throws InterruptedException {
		final SmtpClusterConfig<Session> configuration = new SmtpClusterConfig<Session>().withTransportCancellationSupport(support);
		final SmtpConnectionPool pool = new SmtpConnectionPool(configuration);
		final ClaimControl control = new ClaimControl();
		final SmtpTransportLease lease = pool.claimTransport(session,
				ClaimOptions.withTimeout(1, TimeUnit.SECONDS).withClaimControl(control));
		final Optional<TransportCancellation> cancellation = lease.getCancellation();
		cancellation.ifPresent(TransportCancellation::request);
		lease.invalidate();
		pool.shutDown();
		return lease.getDisposalCompletion();
	}
}
