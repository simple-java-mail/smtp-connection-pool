package org.simplejavamail.smtpconnectionpool.demo;

import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;

import java.util.concurrent.TimeUnit;

/** Path 1 reference consumer: high-level email creation and sending with Simple Java Mail. */
public final class SimpleJavaMailDemo {
    private SimpleJavaMailDemo() {
    }

    /** Sends three high-level emails while Simple Java Mail owns direct pool integration internally. */
    public static DemoResult run() throws Exception {
        try (CountingSmtpServer server = CountingSmtpServer.start()) {
            try (Mailer mailer = MailerBuilder.withSMTPServer(CountingSmtpServer.HOST, server.getPort())
                    .withConnectionPoolCoreSize(0)
                    .withConnectionPoolMaxSize(1)
                    .withConnectionPoolExpireAfterMillis(60_000)
                    .withDebugLogging(false)
                    .buildMailer()) {
                for (int number = 1; number <= DemoSupport.MESSAGE_COUNT; number++) {
                    mailer.sendMail(DemoSupport.newSimpleJavaMailEmail(number), false)
                            .get(5, TimeUnit.SECONDS);
                }
            }
            return server.verify("Simple Java Mail", DemoSupport.MESSAGE_COUNT, 1);
        }
    }

    /** Runs this scenario from an IDE. */
    public static void main(final String[] arguments) throws Exception {
        System.out.println(run());
    }
}
