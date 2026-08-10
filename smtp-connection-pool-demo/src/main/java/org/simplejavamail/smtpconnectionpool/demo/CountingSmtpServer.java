package org.simplejavamail.smtpconnectionpool.demo;

import org.subethamail.smtp.server.SMTPServer;
import org.subethamail.smtp.server.Session;
import org.subethamail.smtp.server.SessionHandler;
import org.subethamail.wiser.Wiser;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class CountingSmtpServer implements AutoCloseable {
    static final String HOST = "127.0.0.1";
    private static final long QUIESCENCE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);

    private final AtomicInteger physicalConnections = new AtomicInteger();
    private final Set<Session> activeConnections =
            Collections.newSetFromMap(new ConcurrentHashMap<Session, Boolean>());
    private final Wiser wiser;

    private CountingSmtpServer() throws IOException {
        final SMTPServer.Builder server = SMTPServer.port(0)
                .bindAddress(InetAddress.getByName(HOST))
                .sessionHandler(new SessionHandler() {
                    @Override
                    public SessionAcceptance accept(final Session session) {
                        physicalConnections.incrementAndGet();
                        activeConnections.add(session);
                        return SessionAcceptance.success();
                    }

                    @Override
                    public void onSessionEnd(final Session session) {
                        activeConnections.remove(session);
                    }
                });
        wiser = Wiser.create(server);
        wiser.start();
    }

    static CountingSmtpServer start() throws IOException {
        return new CountingSmtpServer();
    }

    int getPort() {
        return wiser.getServer().getPortAllocated();
    }

    void dropActiveConnections() throws IOException, InterruptedException {
        IOException failure = null;
        for (Session session : new ArrayList<Session>(activeConnections)) {
            try {
                session.closeSocket();
            } catch (IOException closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        awaitNoActiveConnections();
        if (failure != null) {
            throw failure;
        }
    }

    DemoResult verify(final String scenario, final int expectedMessages, final int expectedConnections)
            throws InterruptedException {
        awaitNoActiveConnections();
        final DemoResult result = new DemoResult(scenario, wiser.getMessages().size(),
                physicalConnections.get(), activeConnections.size());
        if (result.getDeliveredMessages() != expectedMessages
                || result.getPhysicalConnections() != expectedConnections
                || result.getActiveConnectionsAfterShutdown() != 0) {
            throw new AssertionError("Unexpected demo outcome: " + result);
        }
        return result;
    }

    private void awaitNoActiveConnections() throws InterruptedException {
        final long deadline = System.nanoTime() + QUIESCENCE_TIMEOUT_NANOS;
        while (!activeConnections.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
    }

    @Override
    public void close() {
        wiser.stop();
    }
}
