package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusterstormpot.core.api.AllocatorFactory;
import org.bbottema.clusterstormpot.core.api.ResourceKey.ResourceClusterKey;
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
import java.util.UUID;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(javax.mail.Session.class)
@SuppressWarnings("SameParameterValue")
public class SmtpConnectionPoolClusteredTest {
	
	private static final UUID keyCluster1 = UUID.randomUUID();
	private static final UUID keyCluster2 = UUID.randomUUID();
	private static final int MAX_POOL_SIZE = 4;
	
	private TestableSmtpConnectionPoolClustered clusters;
	
	@Before
	public void setupSummyClusters() {
		clusters = new TestableSmtpConnectionPoolClustered(new DummyAllocatorFactory(), new TimeExpiration<SimpleDelegatingPoolable<Transport>>(10, SECONDS), MAX_POOL_SIZE);
	}
	
	@Test
	public void testRoundRobinDummyClusters() throws InterruptedException {
		clusters.registerResourcePool(new ResourceClusterKey<>(keyCluster1, createSessionPoolKeyForString("server_A")));
		clusters.registerResourcePool(new ResourceClusterKey<>(keyCluster1, createSessionPoolKeyForString("server_B")));
		clusters.registerResourcePool(new ResourceClusterKey<>(keyCluster2, createSessionPoolKeyForString("server_C")));
		clusters.registerResourcePool(new ResourceClusterKey<>(keyCluster2, createSessionPoolKeyForString("server_D")));
		
		// first claim on a few specific servers
		SimpleDelegatingPoolable<Transport> connectionA1 = clusters.claimResource(new ResourceClusterKey<>(keyCluster1, createSessionPoolKeyForString("server_A")));
		assertThat(connectionA1.getDelegate().toString()).isEqualTo("connection_A1");
		assertThat(claimAndNoRelease(keyCluster1)).isEqualTo("connection_A2");
		assertThat(claimAndRelease(keyCluster1)).isEqualTo("connection_A3");
		
		// now claim on clusters
		// cluster 1
		assertThat(claimAndReleaseResource(keyCluster1)).isEqualTo("connection_A3");
		assertThat(claimAndNoReleaseResource(keyCluster1)).isEqualTo("connection_B1");
		assertThat(claimAndNoReleaseResource(keyCluster1)).isEqualTo("connection_A3");
		assertThat(claimAndNoReleaseResource(keyCluster1)).isEqualTo("connection_B2");
		assertThat(claimAndReleaseResource(keyCluster1)).isEqualTo("connection_A4");
		connectionA1.release();
		assertThat(claimAndNoReleaseResource(keyCluster1)).isEqualTo("connection_B3");
		assertThat(claimAndNoReleaseResource(keyCluster1)).isEqualTo("connection_A4");
		assertThat(claimAndNoReleaseResource(keyCluster1)).isEqualTo("connection_B4");
		assertThat(claimAndNoReleaseResource(keyCluster1)).isEqualTo("connection_A1");
		// cluster 2
		assertThat(claimAndNoReleaseResource(keyCluster2)).isEqualTo("connection_C1");
		assertThat(claimAndNoReleaseResource(keyCluster2)).isEqualTo("connection_D1");
		assertThat(claimAndNoReleaseResource(keyCluster2)).isEqualTo("connection_C2");
		assertThat(claimAndNoReleaseResource(keyCluster2)).isEqualTo("connection_D2");
		assertThat(claimAndNoReleaseResource(keyCluster2)).isEqualTo("connection_C3");
		assertThat(claimAndNoReleaseResource(keyCluster2)).isEqualTo("connection_D3");
		assertThat(claimAndNoReleaseResource(keyCluster2)).isEqualTo("connection_C4");
	}
	
	private String claimAndRelease(UUID keyCluster) throws InterruptedException {
		final SimpleDelegatingPoolable<Transport> poolable = clusters.claimResource(new ResourceClusterKey<>(keyCluster, createSessionPoolKeyForString("server_A")));
		poolable.release();
		return poolable.getDelegate().toString(); // returns the mocked testable string
	}
	
	private String claimAndNoRelease(UUID keyCluster) throws InterruptedException {
		return clusters.claimResource(new ResourceClusterKey<>(keyCluster, createSessionPoolKeyForString("server_A"))).getDelegate().toString();
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

	private String claimAndReleaseResource(UUID keyCluster) throws InterruptedException {
		SimpleDelegatingPoolable<Transport> poolable = clusters.claimResource(keyCluster);
		poolable.release();
		return poolable.getDelegate().toString();
	}
	
	private String claimAndNoReleaseResource(UUID keyCluster) throws InterruptedException {
		return clusters.claimResource(keyCluster).getDelegate().toString();
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