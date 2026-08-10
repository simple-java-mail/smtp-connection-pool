package org.simplejavamail.smtpconnectionpool.jakarta;

import jakarta.mail.Provider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/** Private connection identity. Its string form deliberately omits all credential material. */
final class ConnectionPoolKey {
    private final ConnectionEndpoint endpoint;
    private final Provider provider;
    private final byte[] credentialFingerprint;
    private final char[] password;
    private final int stableHashCode;
    private volatile boolean credentialMaterialCleared;

    ConnectionPoolKey(final String delegateProtocol, final Provider provider, final String host, final int port,
                      final String user, final String password, final Object credentialIdentity,
                      final byte[] fingerprintKey) throws GeneralSecurityException {
        this.endpoint = new ConnectionEndpoint(delegateProtocol, provider, host, port, user);
        this.provider = provider;
        this.password = password == null ? null : password.toCharArray();
        this.credentialFingerprint = fingerprint(password, credentialIdentity, fingerprintKey);
        this.stableHashCode = 31 * endpoint.hashCode() + Arrays.hashCode(credentialFingerprint);
    }

    String getDelegateProtocol() {
        return endpoint.getDelegateProtocol();
    }

    Provider getProvider() {
        return provider;
    }

    String getHost() {
        return endpoint.getHost();
    }

    int getPort() {
        return endpoint.getPort();
    }

    String getUser() {
        return endpoint.getUser();
    }

    ConnectionEndpoint getEndpoint() {
        return endpoint;
    }

    String copyPassword() {
        return password == null ? null : new String(password);
    }

    void clearCredentialMaterial() {
        credentialMaterialCleared = true;
        if (password != null) {
            Arrays.fill(password, '\0');
        }
        Arrays.fill(credentialFingerprint, (byte) 0);
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

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConnectionPoolKey)) {
            return false;
        }
        final ConnectionPoolKey that = (ConnectionPoolKey) other;
        if (credentialMaterialCleared || that.credentialMaterialCleared) {
            return false;
        }
        return endpoint.equals(that.endpoint) && Arrays.equals(credentialFingerprint, that.credentialFingerprint);
    }

    @Override
    public int hashCode() {
        return stableHashCode;
    }

    @Override
    public String toString() {
        return "ConnectionPoolKey(endpoint=" + endpoint + ", credential=<private>)";
    }
}
