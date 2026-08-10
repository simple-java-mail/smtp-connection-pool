package org.simplejavamail.smtpconnectionpool.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DemoScenariosTest {
    @Test
    void directPoolReusesOneConnection() throws Exception {
        assertSuccessfulReuse(DirectPoolDemo.runReuse());
    }

    @Test
    void directPoolInvalidatesAndReplacesFailedConnection() throws Exception {
        final DemoResult result = DirectPoolDemo.runInvalidation();
        assertEquals(2, result.getDeliveredMessages());
        assertEquals(2, result.getPhysicalConnections());
        assertEquals(0, result.getActiveConnectionsAfterShutdown());
    }

    @Test
    void simpleJavaMailReusesOneConnection() throws Exception {
        assertSuccessfulReuse(SimpleJavaMailDemo.run());
    }

    @Test
    void jakartaMailProviderReusesOneConnection() throws Exception {
        assertSuccessfulReuse(JakartaMailDemo.run());
    }

    @Test
    void springReusesOneConnection() throws Exception {
        assertSuccessfulReuse(SpringDemo.run());
    }

    @Test
    void camelReusesOneConnection() throws Exception {
        assertSuccessfulReuse(CamelDemo.run());
    }

    private static void assertSuccessfulReuse(final DemoResult result) {
        assertEquals(DemoSupport.MESSAGE_COUNT, result.getDeliveredMessages());
        assertEquals(1, result.getPhysicalConnections());
        assertEquals(0, result.getActiveConnectionsAfterShutdown());
    }
}
