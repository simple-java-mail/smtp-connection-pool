package org.simplejavamail.smtpconnectionpool.demo;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.simplejavamail.smtpconnectionpool.jakarta.SmtpPoolProperties;
import org.simplejavamail.smtpconnectionpool.jakarta.SmtpPoolRegistry;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Path 3: Spring's JavaMailSender selecting the standard {@code smtppool} transport. */
public final class SpringDemo {
    private SpringDemo() {
    }

    /** Sends three Spring messages over one physical connection and shuts down the Session pool. */
    public static DemoResult run() throws Exception {
        try (CountingSmtpServer server = CountingSmtpServer.start()) {
            final Properties properties = DemoSupport.smtpProperties(server.getPort());
            properties.setProperty(SmtpPoolProperties.DELEGATE_PROTOCOL, "smtp");
            properties.setProperty(SmtpPoolProperties.MAX_POOL_SIZE, "1");
            properties.setProperty(SmtpPoolProperties.EXPIRATION_MILLIS, "60000");

            final JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(CountingSmtpServer.HOST);
            sender.setPort(server.getPort());
            sender.setProtocol(SmtpPoolProperties.PROTOCOL);
            sender.setJavaMailProperties(properties);
            final Session session = sender.getSession();
            try {
                for (int number = 1; number <= DemoSupport.MESSAGE_COUNT; number++) {
                    final MimeMessage message = sender.createMimeMessage();
                    DemoSupport.populateMessage(message, "Spring", number);
                    sender.send(message);
                }
            } finally {
                SmtpPoolRegistry.shutdown(session).get(5, TimeUnit.SECONDS);
            }
            return server.verify("Spring JavaMailSender", DemoSupport.MESSAGE_COUNT, 1);
        }
    }

    /** Runs this scenario from an IDE. */
    public static void main(final String[] arguments) throws Exception {
        System.out.println(run());
    }
}
