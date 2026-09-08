package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.URLName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;

/** A cooperative provider fixture, not an SMTP implementation or a claim about Angus capabilities. */
final class AbortableTestTransport extends Transport {
    enum Point { CONNECT, READINESS, GREETING, EHLO, STARTTLS, AUTH, MAIL, RCPT, DATA, FINAL_REPLY }

    final AtomicInteger closes = new AtomicInteger();
    final AtomicInteger aborts = new AtomicInteger();
    final AtomicInteger socketGeneration = new AtomicInteger();
    final AtomicBoolean aborted = new AtomicBoolean();
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch proceed = new CountDownLatch(1);
    private final AtomicReference<CountDownLatch> socketWait = new AtomicReference<>(proceed);
    final CountDownLatch closing = new CountDownLatch(1);
    final CountDownLatch finishClose = new CountDownLatch(1);
    volatile Point blockedAt;
    volatile boolean connected;
    volatile boolean accepted;
    volatile boolean blockClose;
    volatile boolean failClose;

    AbortableTestTransport(final Session session) {
        super(session, new URLName("fixture", "localhost", 25, null, null, null));
    }

    @Override
    public synchronized void connect() throws MessagingException {
        pauseAt(Point.CONNECT);
        connected = true;
    }

    @Override
    public synchronized void connect(final String user, final String password) throws MessagingException {
        connect();
    }

    @Override
    public synchronized boolean isConnected() {
        try {
            pauseAt(Point.READINESS);
            return connected;
        } catch (MessagingException abortedProbe) {
            return false;
        }
    }

    @Override
    public synchronized void sendMessage(final Message message, final Address[] addresses) throws MessagingException {
        for (final Point point : Point.values()) {
            if (point != Point.CONNECT && point != Point.READINESS) {
                if (point == Point.STARTTLS) {
                    socketGeneration.incrementAndGet();
                    final CountDownLatch replacement = new CountDownLatch(1);
                    socketWait.set(replacement);
                    if (aborted.get()) {
                        replacement.countDown();
                    }
                }
                pauseAt(point);
            }
        }
        accepted = true;
    }

    /** Deliberately independent of connect/send's monitor and latched before the first connection. */
    void abort() {
        aborts.incrementAndGet();
        aborted.set(true);
        releaseOperation();
    }

    void releaseOperation() {
        socketWait.get().countDown();
        proceed.countDown();
    }

    private void pauseAt(final Point point) throws MessagingException {
        if (point == blockedAt) {
            entered.countDown();
            await(socketWait.get());
        }
        if (aborted.get()) {
            throw new MessagingException("Fixture connection aborted");
        }
    }

    @Override
    public synchronized void close() throws MessagingException {
        closes.incrementAndGet();
        connected = false;
        closing.countDown();
        if (blockClose) {
            await(finishClose);
        }
        if (failClose) {
            throw new MessagingException("Fixture disposal failed");
        }
    }

    private static void await(final CountDownLatch latch) throws MessagingException {
        try {
            if (!latch.await(5, SECONDS)) {
                throw new MessagingException("Test gate was not released");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new MessagingException("Test operation interrupted", interrupted);
        }
    }
}
