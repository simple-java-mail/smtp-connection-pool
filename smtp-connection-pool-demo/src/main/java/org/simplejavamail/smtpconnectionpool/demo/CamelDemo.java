package org.simplejavamail.smtpconnectionpool.demo;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;

/** Path 3: Camel Mail selecting the dedicated {@code smtppool:} adapter scheme. */
public final class CamelDemo {
    private CamelDemo() {
    }

    /** Sends three Camel messages over one physical connection; stopping Camel owns pool shutdown. */
    public static DemoResult run() throws Exception {
        try (CountingSmtpServer server = CountingSmtpServer.start()) {
            final CamelContext context = new DefaultCamelContext();
            context.start();
            try (ProducerTemplate producer = context.createProducerTemplate()) {
                final String endpoint = "smtppool://" + CountingSmtpServer.HOST + ":" + server.getPort()
                        + "?from=sender%40example.test&to=recipient%40example.test"
                        + "&mail.smtppool.pool.max-size=1"
                        + "&mail.smtppool.pool.expiration-millis=60000";
                for (int number = 1; number <= DemoSupport.MESSAGE_COUNT; number++) {
                    producer.sendBody(endpoint, "Camel connection pooling demo message " + number);
                }
            } finally {
                context.stop();
            }
            return server.verify("Camel smtppool adapter", DemoSupport.MESSAGE_COUNT, 1);
        }
    }

    /** Runs this scenario from an IDE. */
    public static void main(final String[] arguments) throws Exception {
        System.out.println(run());
    }
}
