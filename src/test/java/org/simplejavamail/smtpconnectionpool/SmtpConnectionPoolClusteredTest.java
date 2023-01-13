package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Transport;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourceClusterAndPoolKey;
import org.bbottema.genericobjectpool.PoolableObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.UUID;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(PowerMockRunner.class)
@PrepareForTest(jakarta.mail.Session.class)
@SuppressWarnings("SameParameterValue")
public class SmtpConnectionPoolClusteredTest extends SmtpConnectionPoolTestBase<SmtpConnectionPoolClustered, UUID> {
	
	private static final UUID keyCluster1 = UUID.randomUUID();
	private static final UUID keyCluster2 = UUID.randomUUID();
	
	public SmtpConnectionPoolClustered initClusters() {
		SmtpClusterConfig smtpClusterConfig = new SmtpClusterConfig();
		smtpClusterConfig.getConfigBuilder()
				.allocatorFactory(new DummyAllocatorFactory())
				.defaultCorePoolSize(SmtpClusterConfig.MAX_POOL_SIZE);
		return new SmtpConnectionPoolClustered(smtpClusterConfig);
	}
	
	@Test
	public void testRoundRobinDummyClusters() throws InterruptedException {
		clusters.registerResourcePool(new ResourceClusterAndPoolKey<>(keyCluster1, createSessionPoolKeyForString("server_A")));
		clusters.registerResourcePool(new ResourceClusterAndPoolKey<>(keyCluster1, createSessionPoolKeyForString("server_B")));
		clusters.registerResourcePool(new ResourceClusterAndPoolKey<>(keyCluster2, createSessionPoolKeyForString("server_C")));
		clusters.registerResourcePool(new ResourceClusterAndPoolKey<>(keyCluster2, createSessionPoolKeyForString("server_D")));
		
		// first claim on a few specific servers
		PoolableObject<SessionTransport> connectionA1 = clusters.claimResourceFromPool(new ResourceClusterAndPoolKey<>(keyCluster1, createSessionPoolKeyForString("server_A")));
		assertThat(requireNonNull(connectionA1).getAllocatedObject().getTransport().toString()).isEqualTo("connection_A1");
		assertThat(claimAndNoRelease(keyCluster1)).isEqualTo("connection_A2");
		assertThat(claimAndRelease(keyCluster1)).isEqualTo("connection_A3");
		
		// now claim on clusters
		// cluster 1
		assertThat(claimAndReleaseResource(keyCluster1)).isEqualTo("connection_A4"); // A4 has been waiting longer than A3 now
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
}