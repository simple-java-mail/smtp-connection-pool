package org.simplejavamail.smtpconnectionpool.jakarta;

import jakarta.mail.Provider;

import java.util.Locale;
import java.util.Objects;

/** Credential-independent identity of one physical SMTP endpoint and provider implementation. */
final class ConnectionEndpoint {
    private final String delegateProtocol;
    private final String providerIdentity;
    private final String host;
    private final int port;
    private final String user;

    ConnectionEndpoint(final String delegateProtocol, final Provider provider, final String host, final int port,
                       final String user) {
        this.delegateProtocol = normalize(delegateProtocol);
        this.providerIdentity = providerIdentity(provider);
        this.host = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        this.port = port;
        this.user = user;
    }

    String getDelegateProtocol() {
        return delegateProtocol;
    }

    String getHost() {
        return host.isEmpty() ? null : host;
    }

    int getPort() {
        return port;
    }

    String getUser() {
        return user;
    }

    private static String providerIdentity(final Provider provider) {
        return normalize(provider.getProtocol()) + '|' + provider.getClassName() + '|' +
                String.valueOf(provider.getVendor()) + '|' + String.valueOf(provider.getVersion());
    }

    private static String normalize(final String protocol) {
        return protocol == null ? "" : protocol.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConnectionEndpoint)) {
            return false;
        }
        final ConnectionEndpoint that = (ConnectionEndpoint) other;
        return port == that.port && delegateProtocol.equals(that.delegateProtocol) &&
                providerIdentity.equals(that.providerIdentity) && host.equals(that.host) &&
                Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegateProtocol, providerIdentity, host, port, user);
    }

    @Override
    public String toString() {
        return "ConnectionEndpoint(protocol=" + delegateProtocol + ", provider=" + providerIdentity +
                ", host=" + host + ", port=" + port + ", user=" + user + ')';
    }
}
