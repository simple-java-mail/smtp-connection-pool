package org.simplejavamail.smtpconnectionpool.jakarta;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Provider;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.URLName;
import jakarta.mail.event.TransportEvent;
import jakarta.mail.event.TransportListener;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PooledTransportIntegrationTest {
    private Session session;

    @BeforeEach
    void resetFixture() {
        FakeTransport.reset();
        final Properties properties = new Properties();
        properties.setProperty(SmtpPoolProperties.DELEGATE_PROTOCOL, "test-smtp");
        properties.setProperty("mail.test-smtp.port", "2525");
        session = Session.getInstance(properties);
        session.addProvider(fakeProvider());
    }

    @AfterEach
    void shutdownPool() throws Exception {
        SmtpPoolRegistry.shutdownNow(session).get(5, TimeUnit.SECONDS);
    }

    @Test
    void metadataDiscoveryAndPlainJakartaLifecycleReuseOnePhysicalTransport() throws Exception {
        final Transport first = session.getTransport(SmtpPoolProperties.PROTOCOL);
        assertInstanceOf(PooledTransport.class, first);
        first.connect("mail.example.test", 2525, "sender", "secret");
        first.sendMessage(message(), recipients());
        first.close();

        final Transport second = session.getTransport(SmtpPoolProperties.PROTOCOL);
        second.connect("mail.example.test", 2525, "sender", "secret");
        second.sendMessage(message(), recipients());
        second.close();

        assertEquals(1, FakeTransport.instances.get());
        assertEquals(1, FakeTransport.connections.get());
        assertEquals(2, FakeTransport.sends.get());
        assertEquals("mail.example.test", FakeTransport.lastHost);
        assertEquals(2525, FakeTransport.lastPort);
        assertEquals("sender", FakeTransport.lastUser);
        assertEquals("secret", FakeTransport.lastPassword);
        assertEquals(0, SmtpPoolRegistry.getOrCreate(session).getActiveLeaseCount());
    }

    @Test
    void serviceLoaderDescriptorPublishesTheProviderObject() {
        boolean found = false;
        for (Provider provider : ServiceLoader.load(Provider.class)) {
            if (provider instanceof PooledTransportProvider) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void oneWrapperCanConnectAgainAfterClose() throws Exception {
        final Transport transport = session.getTransport(SmtpPoolProperties.PROTOCOL);
        transport.connect("mail.example.test", 2525, "sender", "secret");
        transport.close();
        transport.connect("mail.example.test", 2525, "sender", "secret");
        transport.close();

        assertEquals(1, FakeTransport.instances.get());
        assertEquals(1, FakeTransport.connections.get());
    }

    @Test
    void credentialRotationCannotBorrowAnOlderAuthenticatedConnection() throws Exception {
        sendOnce("credential-a");
        sendOnce("credential-b");

        assertEquals(2, FakeTransport.instances.get());
        assertEquals(2, FakeTransport.connections.get());
    }

    @Test
    void aBrokenPhysicalConnectionIsInvalidated() throws Exception {
        FakeTransport.failure = Failure.DISCONNECT;
        final Transport broken = session.getTransport(SmtpPoolProperties.PROTOCOL);
        final CountDownLatch notDelivered = new CountDownLatch(1);
        broken.addTransportListener(new TransportListener() {
            @Override
            public void messageDelivered(final TransportEvent event) {
            }

            @Override
            public void messageNotDelivered(final TransportEvent event) {
                notDelivered.countDown();
            }

            @Override
            public void messagePartiallyDelivered(final TransportEvent event) {
            }
        });
        broken.connect("mail.example.test", 2525, "sender", "secret");
        assertThrows(MessagingException.class, () -> broken.sendMessage(message(), recipients()));
        assertTrue(notDelivered.await(5, TimeUnit.SECONDS));
        broken.close();

        FakeTransport.failure = Failure.NONE;
        sendOnce("secret");

        assertEquals(2, FakeTransport.instances.get());
        assertEquals(2, FakeTransport.connections.get());
        SmtpPoolRegistry.shutdown(session).get(5, TimeUnit.SECONDS);
        assertTrue(FakeTransport.closes.get() >= 2);
    }

    @Test
    void partialDeliveryKeepsAConnectedDelegateReusableAndForwardsTheEvent() throws Exception {
        FakeTransport.failure = Failure.PARTIAL;
        final Transport transport = session.getTransport(SmtpPoolProperties.PROTOCOL);
        final CountDownLatch partialEvent = new CountDownLatch(1);
        transport.addTransportListener(new TransportListener() {
            @Override
            public void messageDelivered(final TransportEvent event) {
            }

            @Override
            public void messageNotDelivered(final TransportEvent event) {
            }

            @Override
            public void messagePartiallyDelivered(final TransportEvent event) {
                partialEvent.countDown();
            }
        });
        transport.connect("mail.example.test", 2525, "sender", "secret");
        assertThrows(SendFailedException.class, () -> transport.sendMessage(message(), recipients()));
        assertTrue(partialEvent.await(5, TimeUnit.SECONDS));
        transport.close();

        FakeTransport.failure = Failure.NONE;
        sendOnce("secret");
        assertEquals(1, FakeTransport.instances.get());
    }

    @Test
    void deliveredListenersStayWithTheirFacadeAndNeverLeakToAnotherBorrower() throws Exception {
        final Transport first = session.getTransport(SmtpPoolProperties.PROTOCOL);
        final CountDownLatch delivered = new CountDownLatch(1);
        final AtomicInteger firstFacadeEvents = new AtomicInteger();
        first.addTransportListener(new TransportListener() {
            @Override
            public void messageDelivered(final TransportEvent event) {
                firstFacadeEvents.incrementAndGet();
                delivered.countDown();
            }

            @Override
            public void messageNotDelivered(final TransportEvent event) {
            }

            @Override
            public void messagePartiallyDelivered(final TransportEvent event) {
            }
        });
        first.connect("mail.example.test", 2525, "sender", "secret");
        first.sendMessage(message(), recipients());
        assertTrue(delivered.await(5, TimeUnit.SECONDS));
        first.close();

        sendOnce("secret");

        assertEquals(1, firstFacadeEvents.get());
        assertEquals(1, FakeTransport.instances.get());
    }

    @Test
    void recursiveDelegateSelectionFailsWithoutAllocating() throws Exception {
        session.getProperties().setProperty(SmtpPoolProperties.DELEGATE_PROTOCOL, SmtpPoolProperties.PROTOCOL);
        final Transport transport = session.getTransport(SmtpPoolProperties.PROTOCOL);

        final MessagingException failure = assertThrows(MessagingException.class,
                () -> transport.connect("mail.example.test", 2525, "sender", "secret"));

        assertTrue(failure.getMessage().contains("cannot use itself"));
        assertEquals(0, FakeTransport.instances.get());
    }

    @Test
    void programmaticResolverUsesTheSamePoolWithoutRequiringARegisteredAlias() throws Exception {
        session.getProperties().setProperty(SmtpPoolProperties.DELEGATE_PROTOCOL, "runtime-selected");
        SmtpPoolProperties.setDelegateProviderResolver(session, (mailSession, protocol) -> fakeProvider());

        sendOnce("secret");
        sendOnce("secret");

        assertEquals(1, FakeTransport.instances.get());
        assertEquals(2, FakeTransport.sends.get());
    }

    @Test
    void resolvedProviderIdentitySeparatesImplementationsSharingAProtocol() throws Exception {
        SmtpPoolProperties.setDelegateProvider(session, fakeProvider());
        sendOnce("secret");

        SmtpPoolProperties.setDelegateProvider(session, new Provider(Provider.Type.TRANSPORT, "test-smtp",
                SecondFakeTransport.class.getName(), "tests", "1"));
        sendOnce("secret");

        assertEquals(1, FakeTransport.instances.get());
        assertEquals(1, SecondFakeTransport.instances.get());
    }

    @Test
    void failedIdleReconnectIsInvalidatedWithoutLosingThePoolEntry() throws Exception {
        sendOnce("secret");
        FakeTransport.disconnectLast();
        FakeTransport.failure = Failure.CONNECT;

        final Transport reconnecting = session.getTransport(SmtpPoolProperties.PROTOCOL);
        assertThrows(MessagingException.class,
                () -> reconnecting.connect("mail.example.test", 2525, "sender", "secret"));

        FakeTransport.failure = Failure.NONE;
        sendOnce("secret");
        assertEquals(2, FakeTransport.instances.get());
    }

    @Test
    void sessionShutdownRejectsNewClaimsUntilExplicitRestart() throws Exception {
        final Transport active = session.getTransport(SmtpPoolProperties.PROTOCOL);
        active.connect("mail.example.test", 2525, "sender", "secret");
        final Future<?> shutdown = SmtpPoolRegistry.shutdown(session);

        final Transport rejected = session.getTransport(SmtpPoolProperties.PROTOCOL);
        assertThrows(MessagingException.class,
                () -> rejected.connect("mail.example.test", 2525, "sender", "secret"));
        active.close();
        shutdown.get(5, TimeUnit.SECONDS);

        SmtpPoolRegistry.restart(session);
        sendOnce("secret");
        assertEquals(2, FakeTransport.instances.get());
    }

    @Test
    void poolCapacityTimesOutASecondExclusiveClaimAndRecoversAfterRelease() throws Exception {
        session.getProperties().setProperty(SmtpPoolProperties.MAX_POOL_SIZE, "1");
        session.getProperties().setProperty(SmtpPoolProperties.CLAIM_TIMEOUT_MILLIS, "75");
        final Transport first = session.getTransport(SmtpPoolProperties.PROTOCOL);
        first.connect("mail.example.test", 2525, "sender", "secret");

        final Transport second = session.getTransport(SmtpPoolProperties.PROTOCOL);
        final MessagingException timeout = assertThrows(MessagingException.class,
                () -> second.connect("mail.example.test", 2525, "sender", "secret"));
        assertTrue(timeout.getMessage().contains("Timed out"));

        first.close();
        sendOnce("secret");
        assertEquals(1, FakeTransport.instances.get());
    }

    @Test
    void interruptedPoolClaimPreservesTheWaiterInterruptFlag() throws Exception {
        session.getProperties().setProperty(SmtpPoolProperties.MAX_POOL_SIZE, "1");
        session.getProperties().setProperty(SmtpPoolProperties.CLAIM_TIMEOUT_MILLIS, "5000");
        final Transport first = session.getTransport(SmtpPoolProperties.PROTOCOL);
        first.connect("mail.example.test", 2525, "sender", "secret");

        final CountDownLatch started = new CountDownLatch(1);
        final AtomicBoolean interruptedFlag = new AtomicBoolean();
        final AtomicReference<Throwable> result = new AtomicReference<Throwable>();
        final Thread waiter = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Transport second = session.getTransport(SmtpPoolProperties.PROTOCOL);
                    started.countDown();
                    second.connect("mail.example.test", 2525, "sender", "secret");
                    result.set(new AssertionError("The blocked claim unexpectedly succeeded"));
                } catch (Throwable failure) {
                    interruptedFlag.set(Thread.currentThread().isInterrupted());
                    result.set(failure);
                }
            }
        }, "smtppool-interrupted-claim-test");
        waiter.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        Thread.sleep(100L);
        waiter.interrupt();
        waiter.join(5000L);

        assertFalse(waiter.isAlive());
        assertInstanceOf(MessagingException.class, result.get());
        assertTrue(interruptedFlag.get());
        assertInstanceOf(InterruptedException.class, result.get().getCause());
        first.close();
    }

    @Test
    void springJavaMailSenderSelectsTheFacadeForBulkAndSeparateSends() {
        final JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setSession(session);
        sender.setProtocol(SmtpPoolProperties.PROTOCOL);
        sender.setHost("mail.example.test");
        sender.setPort(2525);
        sender.setUsername("sender");
        sender.setPassword("secret");

        sender.send(message(), message());
        sender.send(message());

        assertEquals(1, FakeTransport.instances.get());
        assertEquals(1, FakeTransport.connections.get());
        assertEquals(3, FakeTransport.sends.get());
    }

    @Test
    void completedShutdownDoesNotRetainItsSession() throws Exception {
        final ReferenceQueue<Session> collectedSessions = new ReferenceQueue<Session>();
        final WeakReference<Session> reference = createAndShutdownIsolatedSession(collectedSessions);

        for (int attempt = 0; attempt < 30 && reference.get() != null; attempt++) {
            System.gc();
            System.runFinalization();
            if (collectedSessions.remove(100L) != null) {
                break;
            }
        }

        assertEquals(null, reference.get());
    }

    private static WeakReference<Session> createAndShutdownIsolatedSession(
            final ReferenceQueue<Session> collectedSessions) throws Exception {
        final Properties properties = new Properties();
        properties.setProperty(SmtpPoolProperties.DELEGATE_PROTOCOL, "test-smtp");
        final Session isolated = Session.getInstance(properties);
        isolated.addProvider(fakeProvider());
        final Transport transport = isolated.getTransport(SmtpPoolProperties.PROTOCOL);
        transport.connect("mail.example.test", 2525, "sender", "secret");
        transport.close();
        SmtpPoolRegistry.shutdown(isolated).get(5, TimeUnit.SECONDS);
        FakeTransport.lastInstance = null;
        return new WeakReference<Session>(isolated, collectedSessions);
    }

    private void sendOnce(final String password) throws Exception {
        final Transport transport = session.getTransport(SmtpPoolProperties.PROTOCOL);
        transport.connect("mail.example.test", 2525, "sender", password);
        transport.sendMessage(message(), recipients());
        transport.close();
    }

    private MimeMessage message() {
        final MimeMessage message = new MimeMessage(session);
        try {
            message.setFrom(new InternetAddress("sender@example.test"));
            message.setRecipients(Message.RecipientType.TO, recipients());
            message.setSubject("pool test");
            message.setText("hello");
        } catch (MessagingException failure) {
            throw new AssertionError(failure);
        }
        return message;
    }

    private static Address[] recipients() {
        try {
            return new Address[]{new InternetAddress("recipient@example.test")};
        } catch (jakarta.mail.internet.AddressException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Provider fakeProvider() {
        return new Provider(Provider.Type.TRANSPORT, "test-smtp", FakeTransport.class.getName(), "tests", "1");
    }

    private enum Failure {
        NONE,
        PARTIAL,
        DISCONNECT,
        CONNECT
    }

    public static final class FakeTransport extends Transport {
        static final AtomicInteger instances = new AtomicInteger();
        static final AtomicInteger connections = new AtomicInteger();
        static final AtomicInteger sends = new AtomicInteger();
        static final AtomicInteger closes = new AtomicInteger();
        static volatile Failure failure = Failure.NONE;
        static volatile FakeTransport lastInstance;
        static volatile String lastHost;
        static volatile int lastPort;
        static volatile String lastUser;
        static volatile String lastPassword;

        public FakeTransport(final Session session, final URLName urlName) {
            super(session, urlName);
            instances.incrementAndGet();
            lastInstance = this;
        }

        static void reset() {
            instances.set(0);
            connections.set(0);
            sends.set(0);
            closes.set(0);
            failure = Failure.NONE;
            lastInstance = null;
            lastHost = null;
            lastPort = -1;
            lastUser = null;
            lastPassword = null;
            SecondFakeTransport.instances.set(0);
        }

        static void disconnectLast() {
            lastInstance.setConnected(false);
        }

        @Override
        protected boolean protocolConnect(final String host, final int port, final String user, final String password)
                throws MessagingException {
            connections.incrementAndGet();
            lastHost = host;
            lastPort = port;
            lastUser = user;
            lastPassword = password;
            if (failure == Failure.CONNECT) {
                throw new MessagingException("connect failed");
            }
            return true;
        }

        @Override
        public void sendMessage(final Message message, final Address[] addresses) throws MessagingException {
            sends.incrementAndGet();
            if (failure == Failure.PARTIAL) {
                throw new SendFailedException("partial", null,
                        new Address[]{addresses[0]}, new Address[]{addresses[0]}, null);
            }
            if (failure == Failure.DISCONNECT) {
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

    public static final class SecondFakeTransport extends Transport {
        static final AtomicInteger instances = new AtomicInteger();

        public SecondFakeTransport(final Session session, final URLName urlName) {
            super(session, urlName);
            instances.incrementAndGet();
        }

        @Override
        protected boolean protocolConnect(final String host, final int port, final String user, final String password) {
            return true;
        }

        @Override
        public void sendMessage(final Message message, final Address[] addresses) {
        }
    }
}
