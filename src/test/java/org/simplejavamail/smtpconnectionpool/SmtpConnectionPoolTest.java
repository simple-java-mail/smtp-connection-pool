package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusterstormpot.core.ClusterConfig;
import org.bbottema.clusterstormpot.core.ResourceClusters;
import org.bbottema.clusterstormpot.core.api.ResourceKey.CyclingPoolKey;
import org.bbottema.clusterstormpot.util.SimpleDelegatingPoolable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.mail.Session;
import javax.mail.Transport;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Session.class)
@SuppressWarnings("SameParameterValue")
public class SmtpConnectionPoolTest extends SmtpConnectionPoolTestBase<Session> {
	
	public ResourceClusters<Session, Session, SimpleDelegatingPoolable<Transport>> initClusters() {
		SmtpClusterConfig smtpClusterConfig = new SmtpClusterConfig();
		smtpClusterConfig.getConfigBuilder()
				.allocatorFactory(new DummyAllocatorFactory())
				.sizingMode(ClusterConfig.SizingMode.AUTO_MAX);
		return new SmtpConnectionPool(smtpClusterConfig);
	}
	
	@Test
	public void testRoundRobinDummyClusters() {
		clusters.registerResourcePool(new CyclingPoolKey<>(createSessionPoolKeyForString("server_A")));
		clusters.registerResourcePool(new CyclingPoolKey<>(createSessionPoolKeyForString("server_B")));
		clusters.registerResourcePool(new CyclingPoolKey<>(createSessionPoolKeyForString("server_C")));
		clusters.registerResourcePool(new CyclingPoolKey<>(createSessionPoolKeyForString("server_D")));
		
		// FIXME perform tests
	}
}