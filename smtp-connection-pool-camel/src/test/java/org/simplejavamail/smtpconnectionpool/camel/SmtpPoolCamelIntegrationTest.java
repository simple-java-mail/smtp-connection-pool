package org.simplejavamail.smtpconnectionpool.camel;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.URLName;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmtpPoolCamelIntegrationTest {
    @BeforeEach
    void resetFixture() {
        FakeTransport.instances.set(0);
        FakeTransport.connections.set(0);
        FakeTransport.sends.set(0);
        FakeTransport.closes.set(0);
        FakeTransport.disconnectOnSend = false;
    }

    @Test
    void failedPooledRouteInvalidatesItsPhysicalTransport() throws Exception {
        final CamelContext context = new DefaultCamelContext();
        context.start();
        try (ProducerTemplate producer = context.createProducerTemplate()) {
            final String endpoint = "smtppool://mail.example.test:2525"
                    + "?username=sender&password=secret"
                    + "&from=sender%40example.test&to=recipient%40example.test"
                    + "&mail.smtppool.delegate.protocol=test-smtp";

            FakeTransport.disconnectOnSend = true;
            assertThrows(CamelExecutionException.class, () -> producer.sendBody(endpoint, "broken"));
            FakeTransport.disconnectOnSend = false;
            producer.sendBody(endpoint, "healthy");

            assertEquals(2, FakeTransport.instances.get());
            assertEquals(2, FakeTransport.connections.get());
            assertEquals(2, FakeTransport.sends.get());
        } finally {
            context.stop();
        }
        assertTrue(FakeTransport.closes.get() >= 2);
    }

    @Test
    void dedicatedCamelComponentSelectsSmtpPoolAndReusesThePhysicalTransport() throws Exception {
        final CamelContext context = new DefaultCamelContext();
        context.start();
        try (ProducerTemplate producer = context.createProducerTemplate()) {
            final String endpoint = "smtppool://mail.example.test:2525"
                    + "?username=sender&password=secret"
                    + "&from=sender%40example.test&to=recipient%40example.test"
                    + "&mail.smtppool.delegate.protocol=test-smtp";

            producer.sendBody(endpoint, "first");
            producer.sendBody(endpoint, "second");

            assertInstanceOf(SmtpPoolMailComponent.class, context.getComponent("smtppool"));
            assertEquals(1, FakeTransport.instances.get());
            assertEquals(1, FakeTransport.connections.get());
            assertEquals(2, FakeTransport.sends.get());
        } finally {
            context.stop();
        }
        assertTrue(FakeTransport.closes.get() >= 1);
    }

    @Test
    void ordinaryCamelSmtpRouteStillUsesTheOrdinaryComponentAndProvider() throws Exception {
        final CamelContext context = new DefaultCamelContext();
        context.start();
        try (ProducerTemplate producer = context.createProducerTemplate()) {
            final CamelExecutionException failure = assertThrows(CamelExecutionException.class,
                    () -> producer.sendBody("smtp://127.0.0.1:1"
                    + "?username=sender&password=secret"
                    + "&from=sender%40example.test&to=recipient%40example.test"
                    + "&connectionTimeout=100", "ordinary"));

            assertFalse(context.getComponent("smtp") instanceof SmtpPoolMailComponent);
            assertTrue(hasCauseFromPackage(failure, "org.eclipse.angus.mail"));
            assertEquals(0, FakeTransport.instances.get());
        } finally {
            context.stop();
        }
    }

    private static boolean hasCauseFromPackage(final Throwable failure, final String packageName) {
        Throwable current = failure;
        while (current != null) {
            if (current.getClass().getName().startsWith(packageName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static final class FakeTransport extends Transport {
        static final AtomicInteger instances = new AtomicInteger();
        static final AtomicInteger connections = new AtomicInteger();
        static final AtomicInteger sends = new AtomicInteger();
        static final AtomicInteger closes = new AtomicInteger();
        static volatile boolean disconnectOnSend;

        public FakeTransport(final Session session, final URLName urlName) {
            super(session, urlName);
            instances.incrementAndGet();
        }

        @Override
        protected boolean protocolConnect(final String host, final int port, final String user, final String password) {
            connections.incrementAndGet();
            return true;
        }

        @Override
        public void sendMessage(final Message message, final Address[] addresses) throws MessagingException {
            sends.incrementAndGet();
            if (disconnectOnSend) {
                setConnected(false);
                throw new MessagingException("connection lost");
            }
        }

        @Override
        public synchronized void close() throws MessagingException {
            closes.incrementAndGet();
            super.close();
        }
    }
}
