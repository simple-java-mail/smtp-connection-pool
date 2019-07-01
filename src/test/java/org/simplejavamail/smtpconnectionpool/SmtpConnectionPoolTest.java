/*
 * Copyright (C) 2019 Benny Bottema (benny@bennybottema.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusteredobjectpool.core.api.ResourceKey.ResourcePoolKey;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.mail.Session;

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