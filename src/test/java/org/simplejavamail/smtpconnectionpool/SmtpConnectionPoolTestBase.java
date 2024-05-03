package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.bbottema.clusteredobjectpool.core.ResourceClusters;
import org.bbottema.clusteredobjectpool.core.api.AllocatorFactory;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourceClusterAndPoolKey;
import org.bbottema.genericobjectpool.Allocator;
import org.bbottema.genericobjectpool.PoolableObject;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class SmtpConnectionPoolTestBase<PoolType extends ResourceClusters<ClusterKey, Session, SessionTransport>, ClusterKey> {
	
	private static final Map<String, Session> poolKeys = new HashMap<>();
	
	PoolType clusters;
	
	@BeforeEach
	public void setup() {
		clusters = initClusters();
	}
	
	abstract PoolType initClusters();
	
	@SuppressWarnings("SameParameterValue")
	String claimAndRelease(ClusterKey clusterKey) throws InterruptedException {
		final PoolableObject<SessionTransport> poolable = clusters.claimResourceFromPool(new ResourceClusterAndPoolKey<>(clusterKey, createSessionPoolKeyForString("server_A")));
		requireNonNull(poolable).release();
		return poolable.getAllocatedObject().getTransport().toString(); // returns the mocked testable string
	}
	
	@SuppressWarnings("SameParameterValue")
	String claimAndNoRelease(ClusterKey clusterKey) throws InterruptedException {
		ResourceClusterAndPoolKey<ClusterKey, Session> resourceKey = new ResourceClusterAndPoolKey<>(clusterKey, createSessionPoolKeyForString("server_A"));
		return requireNonNull(clusters.claimResourceFromPool(resourceKey)).getAllocatedObject().getTransport().toString();
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
		PoolableObject<SessionTransport> poolable = clusters.claimResourceFromCluster(clusterKey);
		requireNonNull(poolable).release();
		return poolable.getAllocatedObject().getTransport().toString();
	}
	
	String claimAndNoReleaseResource(ClusterKey clusterKey) throws InterruptedException {
		return requireNonNull(clusters.claimResourceFromCluster(clusterKey)).getAllocatedObject().getTransport().toString();
	}
	
	static class DummyAllocatorFactory implements AllocatorFactory<Session, SessionTransport> {
		@NotNull
		@Override
		public Allocator<SessionTransport> create(@NotNull Session serverInfo) {
			return new DummyAllocator(serverInfo);
		}
	}
	
	private static class DummyAllocator extends Allocator<SessionTransport> {
		private final String serverInfo;
		private int counter = 0;
		
		DummyAllocator(Session serverInfo) {
			this.serverInfo = serverInfo.toString();
		}
		
		@NotNull
		@Override
		public SessionTransport allocate() {
			final Transport transport = mock(Transport.class);
			when(transport.toString()).thenReturn(format("connection%s%d", serverInfo.substring(serverInfo.indexOf('_')), ++counter));
			return new SessionTransport(mock(Session.class), transport);
		}
		
		@Override
		public void deallocate(SessionTransport transport) {
		
		}
	}
}