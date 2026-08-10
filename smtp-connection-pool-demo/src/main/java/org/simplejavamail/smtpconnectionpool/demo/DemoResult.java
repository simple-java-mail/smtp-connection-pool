package org.simplejavamail.smtpconnectionpool.demo;

/** Observable result of one executable SMTP pooling demonstration. */
public final class DemoResult {
    private final String scenario;
    private final int deliveredMessages;
    private final int physicalConnections;
    private final int activeConnectionsAfterShutdown;

    DemoResult(final String scenario, final int deliveredMessages, final int physicalConnections,
               final int activeConnectionsAfterShutdown) {
        this.scenario = scenario;
        this.deliveredMessages = deliveredMessages;
        this.physicalConnections = physicalConnections;
        this.activeConnectionsAfterShutdown = activeConnectionsAfterShutdown;
    }

    /** Returns the human-readable scenario name. */
    public String getScenario() {
        return scenario;
    }

    /** Returns how many messages reached the dummy SMTP server. */
    public int getDeliveredMessages() {
        return deliveredMessages;
    }

    /** Returns how many physical SMTP connections the dummy server accepted. */
    public int getPhysicalConnections() {
        return physicalConnections;
    }

    /** Returns the number of physical connections left open after owner shutdown. */
    public int getActiveConnectionsAfterShutdown() {
        return activeConnectionsAfterShutdown;
    }

    @Override
    public String toString() {
        return scenario + ": delivered=" + deliveredMessages
                + ", physical connections=" + physicalConnections
                + ", active after shutdown=" + activeConnectionsAfterShutdown;
    }
}
