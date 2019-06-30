package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusteredobjectpool.core.ResourceClusters;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourcePoolKey;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.mail.Session;
import javax.mail.Transport;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Session.class)
@SuppressWarnings("SameParameterValue")
public class SmtpConnectionPoolTest extends SmtpConnectionPoolTestBase<SmtpConnectionPool, Session> {
	
	public SmtpConnectionPool initClusters() {
		SmtpClusterConfig smtpClusterConfig = new SmtpClusterConfig();
		smtpClusterConfig.getConfigBuilder()
				.allocatorFactory(new DummyAllocatorFactory())
				.defaultCorePoolSize(SmtpClusterConfig.MAX_POOL_SIZE);
		return new SmtpConnectionPool(smtpClusterConfig);
	}
	
	@Test
	public void testRoundRobinDummyClusters() {
		clusters.registerResourcePool(new ResourcePoolKey<>(createSessionPoolKeyForString("server_A")));
		clusters.registerResourcePool(new ResourcePoolKey<>(createSessionPoolKeyForString("server_B")));
		clusters.registerResourcePool(new ResourcePoolKey<>(createSessionPoolKeyForString("server_C")));
		clusters.registerResourcePool(new ResourcePoolKey<>(createSessionPoolKeyForString("server_D")));
		
		// FIXME perform tests
	}
}