package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusteredobjectpool.core.api.AllocatorFactory;
import org.bbottema.genericobjectpool.Allocator;
import org.jetbrains.annotations.NotNull;

import javax.mail.Session;
import javax.mail.Transport;

class TransportAllocatorFactory implements AllocatorFactory<Session, Transport> {
	@Override
	@NotNull
	public Allocator<Transport> create(@NotNull final Session session) {
		return new TransportAllocator(session);
	}
}