package org.simplejavamail.smtpconnectionpool.demo;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.bbottema.genericobjectpool.ClaimControl;
import org.bbottema.genericobjectpool.ClaimOptions;
import org.simplejavamail.smtpconnectionpool.SmtpClusterConfig;
import org.simplejavamail.smtpconnectionpool.SmtpConnectionPool;
import org.simplejavamail.smtpconnectionpool.SmtpTransportLease;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;

/** A stopped job leaves the connection queue while another job keeps its healthy SMTP lease. */
public final class ClaimCancellationDemo {
    private ClaimCancellationDemo() {
    }

    /** Uses a bounded local SMTP server; no external server, credentials or physical-abort adapter is needed. */
    public static DemoResult run() throws Exception {
        try (CountingSmtpServer server = CountingSmtpServer.start()) {
            final Session session = DemoSupport.newSession(server.getPort());
            final SmtpClusterConfig<Session> configuration = new SmtpClusterConfig<>();
            configuration.getConfigBuilder().defaultMaxPoolSize(1);
            final SmtpConnectionPool pool = new SmtpConnectionPool(configuration);
            final ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                try (SmtpTransportLease currentJob = pool.claimTransport(session)) {
                    cancelWaitingJob(pool, session, worker);
                    if (!currentJob.isActive()) {
                        throw new AssertionError("Cancelling the waiting job revoked someone else's lease");
                    }
                }
                try (SmtpTransportLease nextJob = pool.claimTransport(session)) {
                    final MimeMessage message = DemoSupport.newMessage(session, "After cancelled claim", 1);
                    try {
                        nextJob.getTransport().sendMessage(message, message.getAllRecipients());
                    } catch (Exception failure) {
                        nextJob.invalidate();
                        throw failure;
                    }
                }
            } finally {
                worker.shutdownNow();
                if (!worker.awaitTermination(5, SECONDS)) {
                    throw new AssertionError("Cancelled claim worker did not exit");
                }
                pool.shutDown().get(5, SECONDS);
            }
            return server.verify("Cancelled waiting job; healthy connection reused", 1, 1);
        }
    }

    private static void cancelWaitingJob(final SmtpConnectionPool pool, final Session session, final ExecutorService worker)
            throws Exception {
        final ClaimControl control = new ClaimControl();
        final ClaimOptions options = ClaimOptions.withTimeout(5, SECONDS).withClaimControl(control);
        final CountDownLatch started = new CountDownLatch(1);
        final Future<?> waitingJob = worker.submit(() -> {
            started.countDown();
            try (SmtpTransportLease unexpected = pool.claimTransport(session, options)) {
                throw new AssertionError("A stopped job unexpectedly obtained a connection");
            }
        });
        if (!started.await(2, SECONDS)) {
            throw new AssertionError("Job did not start");
        }
        control.requestCancellation(); // A job scheduler or a user's Stop button would do this.
        try {
            waitingJob.get(2, SECONDS); // Observe real worker completion; do not just cancel its Future.
            throw new AssertionError("Expected cancelled acquisition");
        } catch (ExecutionException failure) {
            if (!(failure.getCause() instanceof CancellationException)) {
                throw failure;
            }
        }
    }

    /** Runs the stopped-job example from an IDE. */
    public static void main(final String[] arguments) throws Exception {
        System.out.println(run());
    }
}
