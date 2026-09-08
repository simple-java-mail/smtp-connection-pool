package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.api.AllocatorFactory;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey;
import org.bbottema.genericobjectpool.Allocator;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

class TransportAllocatorFactory<ClusterKey> implements AllocatorFactory<ClusterKey, Session, SessionTransport> {
	private final TransportCancellationSupport cancellationSupport;

	TransportAllocatorFactory() {
		this(transport -> Optional.empty());
	}

	TransportAllocatorFactory(final TransportCancellationSupport cancellationSupport) {
		this.cancellationSupport = cancellationSupport;
	}

	@Override
	@NotNull
	public Allocator<SessionTransport> create(@NotNull final ResourceKey<ClusterKey, Session> resourceKey) {
		return new TransportAllocator(resourceKey.getPoolKey(), cancellationSupport);
	}
}
