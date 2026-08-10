package org.simplejavamail.smtpconnectionpool.jakarta;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.bbottema.clusteredobjectpool.core.api.AllocatorFactory;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey;
import org.bbottema.genericobjectpool.Allocator;
import org.simplejavamail.smtpconnectionpool.SessionTransport;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class ProviderTransportAllocatorFactory
        implements AllocatorFactory<String, ConnectionPoolKey, SessionTransport> {
    private final Session session;
    private final ConcurrentMap<Transport, MessagingException> reuseFailures =
            new ConcurrentHashMap<Transport, MessagingException>();

    ProviderTransportAllocatorFactory(final Session session) {
        this.session = session;
    }

    @Override
    public Allocator<SessionTransport> create(
            final ResourceKey<String, ConnectionPoolKey> resourceKey) {
        return new ProviderTransportAllocator(session, resourceKey.getPoolKey(), reuseFailures);
    }

    MessagingException takeReuseFailure(final SessionTransport sessionTransport) {
        return reuseFailures.remove(sessionTransport.getTransport());
    }

    private static final class ProviderTransportAllocator extends Allocator<SessionTransport> {
        private final Session session;
        private final ConnectionPoolKey key;
        private final ConcurrentMap<Transport, MessagingException> reuseFailures;

        private ProviderTransportAllocator(final Session session, final ConnectionPoolKey key,
                                           final ConcurrentMap<Transport, MessagingException> reuseFailures) {
            this.session = session;
            this.key = key;
            this.reuseFailures = reuseFailures;
        }

        @Override
        public SessionTransport allocate() {
            Transport transport = null;
            try {
                transport = session.getTransport(key.getProvider());
                connect(transport);
                return new SessionTransport(session, transport);
            } catch (MessagingException failure) {
                closeAfterFailedConnect(transport);
                throw new TransportAllocationException("Unable to open the selected physical SMTP transport", failure);
            }
        }

        @Override
        public void allocateForReuse(final SessionTransport sessionTransport) {
            if (!sessionTransport.getTransport().isConnected()) {
                try {
                    connect(sessionTransport.getTransport());
                    reuseFailures.remove(sessionTransport.getTransport());
                } catch (MessagingException failure) {
                    // GenericObjectPool removes an available object from its queue before invoking this callback. Returning
                    // the disconnected object lets the manager claim and invalidate it deterministically instead of losing
                    // the entry when this callback throws.
                    reuseFailures.put(sessionTransport.getTransport(), failure);
                }
            }
        }

        private void connect(final Transport transport) throws MessagingException {
            transport.connect(key.getHost(), key.getPort(), key.getUser(), key.copyPassword());
        }

        @Override
        public void deallocate(final SessionTransport sessionTransport) {
            reuseFailures.remove(sessionTransport.getTransport());
            closeAfterFailedConnect(sessionTransport.getTransport());
        }

        private static void closeAfterFailedConnect(final Transport transport) {
            if (transport != null) {
                try {
                    transport.close();
                } catch (MessagingException ignored) {
                    // The connection is already unusable and is leaving the pool.
                }
            }
        }
    }
}
