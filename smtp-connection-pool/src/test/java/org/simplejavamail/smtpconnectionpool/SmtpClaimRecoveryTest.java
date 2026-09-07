package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.URLName;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourceClusterAndPoolKey;
import org.bbottema.genericobjectpool.util.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmtpClaimRecoveryTest {
    @ParameterizedTest
    @CsvSource({"0,DIRECT", "1,DIRECT", "0,KEYED", "1,KEYED", "0,BALANCED", "1,BALANCED"})
    void invalidationWakesAnAlreadyWaitingTransportClaim(final int coreSize, final String route) throws Exception {
        final Properties properties = new Properties();
        final Session session = mock(Session.class);
        when(session.getProperties()).thenReturn(properties);
        when(session.getTransport()).thenAnswer(ignored -> new TestTransport(session));
        final ResourceClusters<?, Session, SessionTransport> pool;
        final Callable<SmtpTransportLease> claim;
        if (route.equals("DIRECT")) {
            final SmtpConnectionPool direct = new SmtpConnectionPool(configuration(coreSize));
            pool = direct;
            claim = () -> direct.claimTransport(session);
        } else {
            final SmtpConnectionPoolClustered<String> clustered = new SmtpConnectionPoolClustered<>(configuration(coreSize));
            final ResourceClusterAndPoolKey<String, Session> key = new ResourceClusterAndPoolKey<>("cluster", session);
            clustered.registerResourcePool(key);
            pool = clustered;
            claim = route.equals("BALANCED") ? () -> clustered.claimTransportFromCluster("cluster") : () -> clustered.claimTransport(key);
        }
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final AtomicReference<Thread> waitingThread = new AtomicReference<>();
        final AtomicReference<SmtpTransportLease> replacement = new AtomicReference<>();
        final SmtpTransportLease original = claim.call();
        try {
            final Future<SmtpTransportLease> waiting = executor.submit(() -> {
                waitingThread.set(Thread.currentThread());
                final SmtpTransportLease lease = claim.call();
                replacement.set(lease);
                return lease;
            });
            awaitBlockedClaim(waitingThread);
            original.getTransport().close();
            original.invalidate();

            final SmtpTransportLease recovered = waiting.get(2, SECONDS);
            assertNotSame(original.getTransport(), recovered.getTransport());
            assertTrue(recovered.getTransport().isConnected());
            assertEquals(1, pool.countLiveResources());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, SECONDS));
            original.invalidate();
            if (replacement.get() != null) {
                replacement.get().close();
            }
            pool.shutDown().get(3, SECONDS);
        }
    }

    private static <K> SmtpClusterConfig<K> configuration(final int coreSize) {
        final SmtpClusterConfig<K> configuration = new SmtpClusterConfig<>();
        configuration.getConfigBuilder().defaultCorePoolSize(coreSize).defaultMaxPoolSize(1)
                .defaultExpirationPolicy(ignored -> false).claimTimeout(new Timeout(500, MILLISECONDS));
        return configuration;
    }

    private static void awaitBlockedClaim(final AtomicReference<Thread> thread) throws InterruptedException {
        final long deadline = System.nanoTime() + SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            final Thread worker = thread.get();
            if (worker != null && worker.getState() == Thread.State.TIMED_WAITING) {
                for (StackTraceElement frame : worker.getStackTrace()) {
                    if (frame.getClassName().equals("org.bbottema.genericobjectpool.GenericObjectPool")
                            && frame.getMethodName().equals("waitForAvailableObjectOrTimeout")) {
                        return;
                    }
                }
            }
            Thread.sleep(1);
        }
        fail("The transport claimant did not reach the underlying pool's condition wait");
    }

    private static final class TestTransport extends Transport {
        private TestTransport(final Session session) {
            super(session, new URLName("smtp", "localhost", 25, null, null, null));
        }

        @Override
        protected boolean protocolConnect(final String host, final int port, final String user, final String password) {
            return true;
        }

        @Override
        public void sendMessage(final Message message, final Address[] recipients) {
        }
    }
}
