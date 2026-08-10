package org.simplejavamail.smtpconnectionpool.jakarta;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.URLName;
import jakarta.mail.event.TransportEvent;
import org.simplejavamail.smtpconnectionpool.SmtpTransportLease;

/**
 * Standard Jakarta Mail lifecycle facade over one exclusive pooled lease generation at a time.
 */
public final class PooledTransport extends Transport {
    private Generation generation;

    /** Creates an unconnected facade transport for Jakarta Mail provider discovery. */
    public PooledTransport(final Session session, final URLName urlName) {
        super(session, urlName);
    }

    @Override
    protected synchronized boolean protocolConnect(final String host, final int port, final String user,
                                                   final String password) throws MessagingException {
        terminateStaleGeneration();
        final SmtpPoolManager manager;
        try {
            manager = SmtpPoolRegistry.getOrCreate(session);
        } catch (RuntimeException failure) {
            throw new MessagingException("Unable to initialize the smtppool manager", failure);
        }

        final SmtpTransportLease lease;
        try {
            lease = manager.claim(host, port, user, password);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new MessagingException("Interrupted while waiting for a pooled SMTP transport", interrupted);
        }

        if (!lease.getTransport().isConnected()) {
            manager.invalidate(lease);
            return false;
        }
        generation = new Generation(manager, lease);
        return true;
    }

    @Override
    public synchronized void sendMessage(final Message message, final Address[] addresses) throws MessagingException {
        final Generation current = generation;
        if (current == null || !super.isConnected() || !current.lease.isActive() ||
                !current.lease.getTransport().isConnected()) {
            if (current != null) {
                current.reusable = false;
            }
            setConnected(false);
            throw new MessagingException("Not connected");
        }

        try {
            current.lease.getTransport().sendMessage(message, addresses);
            notifyTransportListeners(TransportEvent.MESSAGE_DELIVERED, addresses, null, null, message);
        } catch (SendFailedException deliveryFailure) {
            current.reusable = current.lease.getTransport().isConnected();
            final Address[] sent = deliveryFailure.getValidSentAddresses();
            final int event = sent != null && sent.length > 0
                    ? TransportEvent.MESSAGE_PARTIALLY_DELIVERED
                    : TransportEvent.MESSAGE_NOT_DELIVERED;
            notifyTransportListeners(event, sent, deliveryFailure.getValidUnsentAddresses(),
                    deliveryFailure.getInvalidAddresses(), message);
            throw deliveryFailure;
        } catch (MessagingException transportFailure) {
            current.reusable = false;
            notifyTransportListeners(TransportEvent.MESSAGE_NOT_DELIVERED, null, addresses, null, message);
            throw transportFailure;
        } catch (RuntimeException unexpectedFailure) {
            current.reusable = false;
            notifyTransportListeners(TransportEvent.MESSAGE_NOT_DELIVERED, null, addresses, null, message);
            throw unexpectedFailure;
        }
    }

    @Override
    public synchronized boolean isConnected() {
        final Generation current = generation;
        final boolean connected = current != null && current.lease.isActive() &&
                current.lease.getTransport().isConnected() && super.isConnected();
        if (!connected && current != null) {
            current.reusable = false;
            setConnected(false);
        }
        return connected;
    }

    @Override
    public synchronized void close() throws MessagingException {
        final Generation current = generation;
        if (current == null) {
            return;
        }
        generation = null;

        MessagingException failure = null;
        try {
            if (current.reusable && current.lease.isActive() && current.lease.getTransport().isConnected()) {
                current.manager.release(current.lease);
            } else {
                current.manager.invalidate(current.lease);
            }
        } catch (RuntimeException poolFailure) {
            failure = new MessagingException("Unable to terminate the pooled SMTP lease", poolFailure);
        } finally {
            try {
                super.close();
            } catch (MessagingException closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void terminateStaleGeneration() throws MessagingException {
        if (generation != null) {
            try {
                generation.manager.invalidate(generation.lease);
            } catch (RuntimeException failure) {
                throw new MessagingException("Unable to invalidate the previous smtppool lease generation", failure);
            } finally {
                generation = null;
                setConnected(false);
            }
        }
    }

    private static final class Generation {
        private final SmtpPoolManager manager;
        private final SmtpTransportLease lease;
        private boolean reusable = true;

        private Generation(final SmtpPoolManager manager, final SmtpTransportLease lease) {
            this.manager = manager;
            this.lease = lease;
        }
    }
}
