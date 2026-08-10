package org.simplejavamail.smtpconnectionpool.demo;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import org.simplejavamail.smtpconnectionpool.jakarta.SmtpPoolProperties;
import org.simplejavamail.smtpconnectionpool.jakarta.SmtpPoolRegistry;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Path 3: plain Jakarta Mail using the discoverable {@code smtppool} transport. */
public final class JakartaMailDemo {
    private JakartaMailDemo() {
    }

    /** Sends three messages through separate pooled transports backed by one physical connection. */
    public static DemoResult run() throws Exception {
        try (CountingSmtpServer server = CountingSmtpServer.start()) {
            final Properties properties = DemoSupport.smtpProperties(server.getPort());
            properties.setProperty(SmtpPoolProperties.DELEGATE_PROTOCOL, "smtp");
            properties.setProperty(SmtpPoolProperties.MAX_POOL_SIZE, "1");
            properties.setProperty(SmtpPoolProperties.EXPIRATION_MILLIS, "60000");
            final Session session = Session.getInstance(properties);
            try {
                for (int number = 1; number <= DemoSupport.MESSAGE_COUNT; number++) {
                    final MimeMessage message = DemoSupport.newMessage(session, "Jakarta Mail", number);
                    try (Transport transport = session.getTransport(SmtpPoolProperties.PROTOCOL)) {
                        transport.connect(CountingSmtpServer.HOST, server.getPort(), null, null);
                        transport.sendMessage(message, message.getAllRecipients());
                    }
                }
            } finally {
                SmtpPoolRegistry.shutdown(session).get(5, TimeUnit.SECONDS);
            }
            return server.verify("Jakarta Mail smtppool provider", DemoSupport.MESSAGE_COUNT, 1);
        }
    }

    /** Runs this scenario from an IDE. */
    public static void main(final String[] arguments) throws Exception {
        System.out.println(run());
    }
}
