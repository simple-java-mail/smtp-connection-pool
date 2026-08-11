package org.simplejavamail.smtpconnectionpool.demo;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.simplejavamail.batch.BatchTransportExecutor;

/** Path 2: standalone batch orchestration without Simple Java Mail's EmailBuilder or Mailer facade. */
public final class BatchModuleDemo {
    private static final String CLUSTER = "transactional";

    private BatchModuleDemo() {
    }

    /** Sends three Jakarta Mail messages through the public callback API and one physical connection. */
    public static DemoResult run() throws Exception {
        try (CountingSmtpServer server = CountingSmtpServer.start()) {
            final Session session = DemoSupport.newSession(server.getPort());
            try (BatchTransportExecutor<String> batch = BatchTransportExecutor.<String>builder()
                    .withCorePoolSize(0)
                    .withMaxPoolSize(1)
                    .withExpireAfterMillis(60_000)
                    .build()) {
                batch.registerSession(CLUSTER, session);
                for (int number = 1; number <= DemoSupport.MESSAGE_COUNT; number++) {
                    final int messageNumber = number;
                    batch.execute(CLUSTER, (selectedSession, transport) -> {
                        final MimeMessage message = DemoSupport.newMessage(
                                selectedSession, "Batch module", messageNumber);
                        transport.sendMessage(message, message.getAllRecipients());
                        return null;
                    });
                }
            }
            return server.verify("Batch module", DemoSupport.MESSAGE_COUNT, 1);
        }
    }

    /** Runs this scenario from an IDE. */
    public static void main(final String[] arguments) throws Exception {
        System.out.println(run());
    }
}
