package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;
import org.bbottema.clusteredobjectpool.core.api.AllocatorFactory;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourceClusterAndPoolKey;
import org.bbottema.genericobjectpool.Allocator;
import org.bbottema.genericobjectpool.PoolableObject;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;

import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class SmtpConnectionPoolTestBase<PoolType extends ResourceClusters<ClusterKey, Session, Transport>, ClusterKey> {
	
	private static final Map<String, Session> poolKeys = new HashMap<>();
	
	PoolType clusters;
	
	@Before
	public void setup() {
		clusters = initClusters();
	}
	
	abstract PoolType initClusters();
	
	@SuppressWarnings("SameParameterValue")
	String claimAndRelease(ClusterKey clusterKey) throws InterruptedException {
		final PoolableObject<Transport> poolable = clusters.claimResourceFromPool(new ResourceClusterAndPoolKey<>(clusterKey, createSessionPoolKeyForString("server_A")));
		requireNonNull(poolable).release();
		return poolable.getAllocatedObject().toString(); // returns the mocked testable string
	}
	
	@SuppressWarnings("SameParameterValue")
	String claimAndNoRelease(ClusterKey clusterKey) throws InterruptedException {
		ResourceClusterAndPoolKey<ClusterKey, Session> resourceKey = new ResourceClusterAndPoolKey<>(clusterKey, createSessionPoolKeyForString("server_A"));
		return requireNonNull(clusters.claimResourceFromPool(resourceKey)).getAllocatedObject().toString();
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
		PoolableObject<Transport> poolable = clusters.claimResourceFromCluster(clusterKey);
		requireNonNull(poolable).release();
		return poolable.getAllocatedObject().toString();
	}
	
	String claimAndNoReleaseResource(ClusterKey clusterKey) throws InterruptedException {
		return requireNonNull(clusters.claimResourceFromCluster(clusterKey)).getAllocatedObject().toString();
	}
	
	static class DummyAllocatorFactory implements AllocatorFactory<Session, Transport> {
		@NotNull
		@Override
		public Allocator<Transport> create(@NotNull Session serverInfo) {
			return new DummyAllocator(serverInfo);
		}
	}
	
	private static class DummyAllocator extends Allocator<Transport> {
		private final String serverInfo;
		private int counter = 0;
		
		DummyAllocator(Session serverInfo) {
			this.serverInfo = serverInfo.toString();
		}
		
		@NotNull
		@Override
		public Transport allocate() {
			final Transport s = mock(Transport.class);
			when(s.toString()).thenReturn(format("connection%s%d", serverInfo.substring(serverInfo.indexOf('_')), ++counter));
			return s;
		}
		
		@Override
		public void deallocate(Transport transport) {
		
		}
	}
}
