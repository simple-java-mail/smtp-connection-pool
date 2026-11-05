package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Provider;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.URLName;
import org.bbottema.genericobjectpool.ClaimOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.simplejavamail.smtpconnectionpool.SmtpConnectionPool.OAUTH2_TOKEN_PROPERTY;
import static org.simplejavamail.smtpconnectionpool.SmtpConnectionPool.OAUTH2_TOKEN_PROVIDER_PROPERTY;

/** Exercises Jakarta Mail's actual username selection, rather than mocking its connect overload. */
class TransportAllocatorProtocolUsernameTest {
    private static final String ACTIVE_USER = "active@example.test";
    private static final String INACTIVE_USER = "inactive@example.test";
    private static final String TOKEN = "synthetic-current-token";

    @ParameterizedTest
    @CsvSource({"smtp,false", "smtp,true", "smtps,false", "smtps,true"})
    void allocationAndReconnectionUseTheSelectedProtocolsUsername(final String protocol, final boolean suppliedToken) {
        final Properties properties = properties(protocol);
        properties.setProperty("mail." + protocol + ".user", ACTIVE_USER);
        properties.setProperty("mail." + otherProtocol(protocol) + ".user", INACTIVE_USER);
        final AtomicInteger calls = new AtomicInteger();
        if (suppliedToken) {
            properties.put(OAUTH2_TOKEN_PROVIDER_PROPERTY, (Supplier<String>) () -> TOKEN + calls.incrementAndGet());
        } else {
            properties.setProperty(OAUTH2_TOKEN_PROPERTY, TOKEN);
        }
        final Session session = session(properties, protocol);
        final Properties original = (Properties) properties.clone();
        final TransportAllocator allocator = new TransportAllocator(session);

        assertEquals(0, calls.get());
        final SessionTransport allocated = allocator.allocate(ClaimOptions.withoutTimeout().start());
        final RecordingTransport transport = (RecordingTransport) allocated.getTransport();
        assertEquals(ACTIVE_USER, transport.username);
        assertEquals(suppliedToken ? TOKEN + "1" : TOKEN, transport.password);

        allocator.allocateForReuse(allocated);
        assertEquals(suppliedToken ? 1 : 0, calls.get());
        assertEquals(1, transport.connections);

        transport.close();
        allocator.allocateForReuse(allocated, ClaimOptions.withoutTimeout().start());
        assertSame(transport, allocated.getTransport());
        assertEquals(ACTIVE_USER, transport.username);
        assertEquals(suppliedToken ? TOKEN + "2" : TOKEN, transport.password);
        assertEquals(suppliedToken ? 2 : 0, calls.get());
        assertEquals(2, transport.connections);
        assertEquals(original, properties);
        transport.close();
    }

    @Test
    void addressMappingSelectsTheProtocolWhenNoSessionProtocolIsSet() {
        final Properties properties = properties("smtps");
        properties.remove("mail.transport.protocol");
        properties.setProperty("mail.smtps.user", ACTIVE_USER);
        properties.setProperty("mail.smtp.user", INACTIVE_USER);
        final Session session = session(properties, "smtps");
        session.setProtocolForAddress("rfc822", "smtps");

        final RecordingTransport transport = (RecordingTransport) new TransportAllocator(session).allocate().getTransport();

        assertEquals("smtps", transport.getURLName().getProtocol());
        assertEquals(ACTIVE_USER, transport.username);
        transport.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"smtp", "smtps"})
    void absentProtocolUsernameRetainsTheMailWideFallback(final String protocol) {
        final Properties properties = properties(protocol);
        properties.setProperty("mail.user", ACTIVE_USER);
        properties.setProperty("mail." + otherProtocol(protocol) + ".user", INACTIVE_USER);
        final Session session = session(properties, protocol);

        final RecordingTransport transport = (RecordingTransport) new TransportAllocator(session).allocate().getTransport();

        assertEquals(ACTIVE_USER, transport.username);
        assertEquals(TOKEN, transport.password);
        transport.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"smtp", "smtps"})
    void inheritedProtocolUsernameIsVisibleWithoutMaterializingIt(final String protocol) {
        final Properties defaults = new Properties();
        defaults.setProperty("mail." + protocol + ".user", ACTIVE_USER);
        final Properties properties = new Properties(defaults);
        properties.putAll(properties(protocol));
        properties.setProperty("mail." + otherProtocol(protocol) + ".user", INACTIVE_USER);
        final Session session = session(properties, protocol);
        final Properties original = (Properties) properties.clone();

        final RecordingTransport transport = (RecordingTransport) new TransportAllocator(session).allocate().getTransport();

        assertEquals(ACTIVE_USER, transport.username);
        assertEquals(original, properties);
        assertFalse(properties.containsKey("mail." + protocol + ".user"));
        transport.close();
    }

    @Test
    void selectedProviderUrlRetainsItsOrdinaryUsernamePrecedence() {
        final Properties properties = properties("smtps");
        properties.setProperty("test.provider.url.user", ACTIVE_USER);
        properties.setProperty("mail.smtps.user", "session@example.test");
        properties.setProperty("mail.smtp.user", INACTIVE_USER);
        final Session session = session(properties, "smtps");

        final RecordingTransport transport = (RecordingTransport) new TransportAllocator(session).allocate().getTransport();

        assertEquals(ACTIVE_USER, transport.username);
        assertEquals(TOKEN, transport.password);
        transport.close();
    }

    private static Properties properties(final String protocol) {
        final Properties properties = new Properties();
        properties.setProperty("mail.transport.protocol", protocol);
        properties.setProperty(OAUTH2_TOKEN_PROPERTY, TOKEN);
        return properties;
    }

    private static String otherProtocol(final String protocol) {
        return "smtp".equals(protocol) ? "smtps" : "smtp";
    }

    private static Session session(final Properties properties, final String protocol) {
        final Session session = Session.getInstance(properties);
        try {
            session.setProvider(new Provider(Provider.Type.TRANSPORT, protocol, RecordingTransport.class.getName(), "test", "1"));
        } catch (jakarta.mail.NoSuchProviderException failure) {
            throw new AssertionError(failure);
        }
        return session;
    }

    public static final class RecordingTransport extends Transport {
        private String username;
        private String password;
        private int connections;

        public RecordingTransport(final Session session, final URLName url) {
            super(session, new URLName(url.getProtocol(), url.getHost(), url.getPort(), url.getFile(),
                    session.getProperty("test.provider.url.user"), null));
        }

        @Override
        protected boolean protocolConnect(final String host, final int port, final String user, final String token) {
            username = user;
            password = token;
            connections++;
            return true;
        }

        @Override
        public void sendMessage(final Message message, final Address[] recipients) {
            throw new UnsupportedOperationException("This provider records authentication only");
        }

        @Override
        public synchronized void close() {
            setConnected(false);
        }
    }
}
