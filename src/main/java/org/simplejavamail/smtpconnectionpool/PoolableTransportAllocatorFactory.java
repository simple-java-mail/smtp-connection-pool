package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusterstormpot.core.AllocatorFactory;
import org.bbottema.clusterstormpot.util.SimpleDelegatingPoolable;
import stormpot.Allocator;

import javax.annotation.Nonnull;
import javax.mail.Session;
import javax.mail.Transport;

public class PoolableTransportAllocatorFactory implements AllocatorFactory<Session, SimpleDelegatingPoolable<Transport>> {
	@Override
	@Nonnull
	public Allocator<SimpleDelegatingPoolable<Transport>> create(@Nonnull final Session session) {
		return new PoolableTransportAllocator(session);
	}
}