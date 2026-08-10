package org.simplejavamail.smtpconnectionpool.jakarta;

import jakarta.mail.Provider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/** Private connection identity. Its string form deliberately omits all credential material. */
final class ConnectionPoolKey {
    private final String delegateProtocol;
    private final Provider provider;
    private final String providerIdentity;
    private final String host;
    private final int port;
    private final String user;
    private final byte[] credentialFingerprint;
    private final char[] password;

    ConnectionPoolKey(final String delegateProtocol, final Provider provider, final String host, final int port,
                      final String user, final String password, final Object credentialIdentity,
                      final byte[] fingerprintKey) throws GeneralSecurityException {
        this.delegateProtocol = normalize(delegateProtocol);
        this.provider = provider;
        this.providerIdentity = providerIdentity(provider);
        this.host = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        this.port = port;
        this.user = user;
        this.password = password == null ? null : password.toCharArray();
        this.credentialFingerprint = fingerprint(password, credentialIdentity, fingerprintKey);
    }

    String getDelegateProtocol() {
        return delegateProtocol;
    }

    Provider getProvider() {
        return provider;
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

    String copyPassword() {
        return password == null ? null : new String(password);
    }

    void clearPassword() {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    private static byte[] fingerprint(final String password, final Object identity, final byte[] fingerprintKey)
            throws GeneralSecurityException {
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(fingerprintKey, "HmacSHA256"));
        mac.update(identity == null ? new byte[]{0} : identity.toString().getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 0);
        if (password != null) {
            final ByteBuffer bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
            final byte[] encoded = new byte[bytes.remaining()];
            bytes.get(encoded);
            mac.update(encoded);
            Arrays.fill(encoded, (byte) 0);
        }
        return mac.doFinal();
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
        if (!(other instanceof ConnectionPoolKey)) {
            return false;
        }
        final ConnectionPoolKey that = (ConnectionPoolKey) other;
        return port == that.port && delegateProtocol.equals(that.delegateProtocol) &&
                providerIdentity.equals(that.providerIdentity) && host.equals(that.host) &&
                Objects.equals(user, that.user) && Arrays.equals(credentialFingerprint, that.credentialFingerprint);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(delegateProtocol, providerIdentity, host, port, user);
        result = 31 * result + Arrays.hashCode(credentialFingerprint);
        return result;
    }

    @Override
    public String toString() {
        return "ConnectionPoolKey(protocol=" + delegateProtocol + ", provider=" + providerIdentity +
                ", host=" + host + ", port=" + port + ", user=" + user + ", credential=<private>)";
    }
}
