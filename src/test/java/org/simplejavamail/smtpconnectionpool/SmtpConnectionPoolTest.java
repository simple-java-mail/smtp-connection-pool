package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourcePoolKey;
import org.junit.jupiter.api.Test;

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
