package org.simplejavamail.smtpconnectionpool.jakarta;

import jakarta.mail.Provider;

/** Jakarta Mail provider descriptor used by both metadata discovery mechanisms. */
public final class PooledTransportProvider extends Provider {
    /** Creates the descriptor for the {@code smtppool} transport protocol. */
    public PooledTransportProvider() {
        super(Type.TRANSPORT, SmtpPoolProperties.PROTOCOL, PooledTransport.class.getName(),
                "Simple Java Mail", implementationVersion());
    }

    private static String implementationVersion() {
        final Package providerPackage = PooledTransportProvider.class.getPackage();
        final String version = providerPackage == null ? null : providerPackage.getImplementationVersion();
        return version == null ? "development" : version;
    }
}
