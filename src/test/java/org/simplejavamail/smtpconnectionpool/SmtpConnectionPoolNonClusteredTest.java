package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusterstormpot.core.ClusterConfig;
import org.bbottema.clusterstormpot.core.api.AllocatorFactory;
import org.bbottema.clusterstormpot.core.api.ResourceKey.CyclingPoolKey;
import org.bbottema.clusterstormpot.util.SimpleDelegatingPoolable;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import stormpot.Allocator;
import stormpot.Slot;
import stormpot.TimeExpiration;

import javax.mail.Session;
import javax.mail.Transport;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Session.class)
@SuppressWarnings("SameParameterValue")
public class SmtpConnectionPoolNonClusteredTest {
	
	private static final int MAX_POOL_SIZE = 4;
	
	private SmtpConnectionPool clusters;
	
	@Before
	public void setupSummyClusters() {
		clusters = new SmtpConnectionPool(ClusterConfig.<Session, SimpleDelegatingPoolable<Transport>>builder()
				.allocatorFactory(new DummyAllocatorFactory())
				.defaultExpirationPolicy(new TimeExpiration<SimpleDelegatingPoolable<Transport>>(10, SECONDS))
				.defaultMaxPoolSize(MAX_POOL_SIZE)
				.build());
	}
	
	@Test
	public void testRoundRobinDummyClusters() {
		clusters.registerResourcePool(new CyclingPoolKey<>(createSessionPoolKeyForString("server_A")));
		clusters.registerResourcePool(new CyclingPoolKey<>(createSessionPoolKeyForString("server_B")));
		clusters.registerResourcePool(new CyclingPoolKey<>(createSessionPoolKeyForString("server_C")));
		clusters.registerResourcePool(new CyclingPoolKey<>(createSessionPoolKeyForString("server_D")));
		
		// FIXME perform tests
	}

	private static Map<String, Session> poolKeys = new HashMap<>();

	@NotNull
	private Session createSessionPoolKeyForString(String poolKey) {
		if (!poolKeys.containsKey(poolKey)) {
			Session mock = mock(Session.class);
			when(mock.toString()).thenReturn(poolKey);
			poolKeys.put(poolKey, mock);
		}
		return poolKeys.get(poolKey);
	}

	private static class DummyAllocatorFactory implements AllocatorFactory<Session, SimpleDelegatingPoolable<Transport>> {
		@NotNull
		@Override
		public Allocator<SimpleDelegatingPoolable<Transport>> create(@NotNull Session serverInfo) {
			return new DummyAllocator(serverInfo);
		}
	}
	
	private static class DummyAllocator implements Allocator<SimpleDelegatingPoolable<Transport>> {
		private final String serverInfo;
		private int counter = 0;
		
		DummyAllocator(Session serverInfo) {
			this.serverInfo = serverInfo.toString();
		}
		
		@Override
		public SimpleDelegatingPoolable<Transport> allocate(Slot slot) {
			final Transport s = mock(Transport.class);
			when(s.toString()).thenReturn(format("connection%s%d", serverInfo.substring(serverInfo.indexOf('_')), ++counter));
			return new SimpleDelegatingPoolable<>(slot, s);
		}
		
		@Override
		public void deallocate(SimpleDelegatingPoolable<Transport> poolable) {
		
		}
	}
}