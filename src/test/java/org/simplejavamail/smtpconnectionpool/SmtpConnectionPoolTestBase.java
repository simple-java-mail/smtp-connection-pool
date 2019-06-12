package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusterstormpot.core.ResourceClusters;
import org.bbottema.clusterstormpot.core.api.AllocatorFactory;
import org.bbottema.clusterstormpot.core.api.ResourceKey.ResourceClusterAndPoolKey;
import org.bbottema.clusterstormpot.util.SimpleDelegatingPoolable;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import stormpot.Allocator;
import stormpot.Slot;

import javax.mail.Session;
import javax.mail.Transport;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class SmtpConnectionPoolTestBase<ClusterKey> {
	
	private static Map<String, Session> poolKeys = new HashMap<>();
	
	ResourceClusters<ClusterKey, Session, SimpleDelegatingPoolable<Transport>> clusters;
	
	@Before
	public void setup() {
		clusters = initClusters();
	}
	
	abstract ResourceClusters<ClusterKey, Session, SimpleDelegatingPoolable<Transport>> initClusters();
	
	@SuppressWarnings("SameParameterValue")
	String claimAndRelease(ClusterKey clusterKey) throws InterruptedException {
		final SimpleDelegatingPoolable<Transport> poolable = clusters.claimResourceFromPool(new ResourceClusterAndPoolKey<>(clusterKey, createSessionPoolKeyForString("server_A")));
		poolable.release();
		return poolable.getDelegate().toString(); // returns the mocked testable string
	}
	
	@SuppressWarnings("SameParameterValue")
	String claimAndNoRelease(ClusterKey clusterKey) throws InterruptedException {
		return clusters.claimResourceFromPool(new ResourceClusterAndPoolKey<>(clusterKey, createSessionPoolKeyForString("server_A"))).getDelegate().toString();
	}
	
	@NotNull
	static Session createSessionPoolKeyForString(String poolKey) {
		if (!poolKeys.containsKey(poolKey)) {
			Session mock = mock(Session.class);
			when(mock.toString()).thenReturn(poolKey);
			poolKeys.put(poolKey, mock);
		}
		return poolKeys.get(poolKey);
	}
	
	@SuppressWarnings("SameParameterValue")
	String claimAndReleaseResource(ClusterKey clusterKey) throws InterruptedException {
		SimpleDelegatingPoolable<Transport> poolable = clusters.claimResourceFromCluster(clusterKey);
		poolable.release();
		return poolable.getDelegate().toString();
	}
	
	String claimAndNoReleaseResource(ClusterKey clusterKey) throws InterruptedException {
		return clusters.claimResourceFromCluster(clusterKey).getDelegate().toString();
	}
	
	protected static class DummyAllocatorFactory implements AllocatorFactory<Session, SimpleDelegatingPoolable<Transport>> {
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
