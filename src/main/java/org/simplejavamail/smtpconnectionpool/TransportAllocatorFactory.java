package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.bbottema.clusteredobjectpool.core.api.AllocatorFactory;
import org.bbottema.genericobjectpool.Allocator;
import org.jetbrains.annotations.NotNull;

class TransportAllocatorFactory implements AllocatorFactory<Session, SessionTransport> {
	@Override
	@NotNull
	public Allocator<SessionTransport> create(@NotNull final Session session) {
		return new TransportAllocator(session);
	}
}