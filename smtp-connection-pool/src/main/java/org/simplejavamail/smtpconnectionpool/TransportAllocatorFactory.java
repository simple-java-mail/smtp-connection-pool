package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.api.AllocatorFactory;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey;
import org.bbottema.genericobjectpool.Allocator;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

class TransportAllocatorFactory<ClusterKey> implements AllocatorFactory<ClusterKey, Session, SessionTransport> {
	private final TransportCancellationSupport cancellationSupport;
	private final TransportConnectionObserver connectionObserver;

	TransportAllocatorFactory() {
		this(transport -> Optional.empty());
	}

	TransportAllocatorFactory(final TransportCancellationSupport cancellationSupport) {
		this(cancellationSupport, TransportConnectionObserver.NONE);
	}

	TransportAllocatorFactory(final TransportCancellationSupport cancellationSupport, final TransportConnectionObserver connectionObserver) {
		this.cancellationSupport = cancellationSupport;
		this.connectionObserver = connectionObserver;
	}

	@Override
	@NotNull
	public Allocator<SessionTransport> create(@NotNull final ResourceKey<ClusterKey, Session> resourceKey) {
		return new TransportAllocator(resourceKey.getPoolKey(), cancellationSupport, connectionObserver);
	}
}
