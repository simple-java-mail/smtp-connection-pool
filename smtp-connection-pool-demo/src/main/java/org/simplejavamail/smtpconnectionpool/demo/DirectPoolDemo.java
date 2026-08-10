package org.simplejavamail.smtpconnectionpool.demo;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.simplejavamail.smtpconnectionpool.SmtpClusterConfig;
import org.simplejavamail.smtpconnectionpool.SmtpConnectionPool;
import org.simplejavamail.smtpconnectionpool.SmtpTransportLease;

import java.util.concurrent.TimeUnit;

/** Path 1: direct use of the transport lease API, including explicit invalidation on failure. */
public final class DirectPoolDemo {
    private DirectPoolDemo() {
    }

    /** Sends three messages over one physical connection and shuts the pool down explicitly. */
    public static DemoResult runReuse() throws Exception {
        try (CountingSmtpServer server = CountingSmtpServer.start()) {
            final Session session = DemoSupport.newSession(server.getPort());
            final SmtpConnectionPool pool = newPool();
            try {
                for (int number = 1; number <= DemoSupport.MESSAGE_COUNT; number++) {
                    send(pool, session, DemoSupport.newMessage(session, "Direct pool", number));
                }
            } finally {
                pool.shutDown().get(5, TimeUnit.SECONDS);
            }
            return server.verify("Direct pool", DemoSupport.MESSAGE_COUNT, 1);
        }
    }

    /** Proves that a failed physical connection is invalidated and replaced for the next message. */
    public static DemoResult runInvalidation() throws Exception {
        try (CountingSmtpServer server = CountingSmtpServer.start()) {
            final Session session = DemoSupport.newSession(server.getPort());
            final SmtpConnectionPool pool = newPool();
            try {
                send(pool, session, DemoSupport.newMessage(session, "Before failure", 1));

                final MimeMessage failedMessage = DemoSupport.newMessage(session, "Forced failure", 2);
                try (SmtpTransportLease lease = pool.claimTransport(session)) {
                    server.dropActiveConnections();
                    try {
                        lease.getTransport().sendMessage(failedMessage, failedMessage.getAllRecipients());
                        throw new AssertionError("The forced connection failure unexpectedly delivered a message");
                    } catch (MessagingException expectedFailure) {
                        lease.invalidate();
                    }
                }

                send(pool, session, DemoSupport.newMessage(session, "After recovery", 3));
            } finally {
                pool.shutDown().get(5, TimeUnit.SECONDS);
            }
            return server.verify("Direct invalidation and recovery", 2, 2);
        }
    }

    private static SmtpConnectionPool newPool() {
        final SmtpClusterConfig<Session> configuration = new SmtpClusterConfig<Session>();
        configuration.getConfigBuilder().defaultMaxPoolSize(1);
        return new SmtpConnectionPool(configuration);
    }

    private static void send(final SmtpConnectionPool pool, final Session session, final MimeMessage message)
            throws MessagingException, InterruptedException {
        try (SmtpTransportLease lease = pool.claimTransport(session)) {
            try {
                lease.getTransport().sendMessage(message, message.getAllRecipients());
            } catch (MessagingException | RuntimeException failure) {
                lease.invalidate();
                throw failure;
            }
        }
    }

    /** Runs both direct-pool scenarios from an IDE. */
    public static void main(final String[] arguments) throws Exception {
        System.out.println(runReuse());
        System.out.println(runInvalidation());
    }
}
