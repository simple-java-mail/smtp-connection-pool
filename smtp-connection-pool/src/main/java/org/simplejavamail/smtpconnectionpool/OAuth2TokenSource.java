package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import org.jetbrains.annotations.Nullable;

import java.util.Properties;
import java.util.function.Supplier;

import static org.simplejavamail.smtpconnectionpool.SmtpConnectionPool.OAUTH2_TOKEN_PROPERTY;
import static org.simplejavamail.smtpconnectionpool.SmtpConnectionPool.OAUTH2_TOKEN_PROVIDER_PROPERTY;

/** The allocator's credential choice, captured with its registration rather than rediscovered through another registration. */
final class OAuth2TokenSource {
    private static final OAuth2TokenSource LEGACY_PROPERTIES = new OAuth2TokenSource(true, null);

    private final boolean legacyProperties;
    @Nullable private final Supplier<String> provider;

    private OAuth2TokenSource(final boolean legacyProperties, @Nullable final Supplier<String> provider) {
        this.legacyProperties = legacyProperties;
        this.provider = provider;
    }

    static OAuth2TokenSource legacyProperties() {
        return LEGACY_PROPERTIES;
    }

    static OAuth2TokenSource explicit(@Nullable final Supplier<String> provider) {
        return new OAuth2TokenSource(false, provider);
    }

    @Nullable String resolve(final Session session) {
        if (!legacyProperties) {
            return provider == null ? null : resolveProvider(provider);
        }
        final Properties properties = session.getProperties();
        final Object configuredProvider = properties.get(OAUTH2_TOKEN_PROVIDER_PROPERTY);
        if (configuredProvider == null) {
            return (String) properties.getOrDefault(OAUTH2_TOKEN_PROPERTY, null);
        }
        if (!(configuredProvider instanceof Supplier)) {
            throw new TransportHandlingException("The configured OAuth2 token provider is not a Supplier", null);
        }
        return resolveProvider((Supplier<?>) configuredProvider);
    }

    private static String resolveProvider(final Supplier<?> provider) {
        final Object token;
        try {
            token = provider.get();
        } catch (RuntimeException failure) {
            throw new TransportHandlingException("The OAuth2 token provider failed while obtaining an access token", failure);
        }
        if (!(token instanceof String) || ((String) token).trim().isEmpty()) {
            throw new TransportHandlingException("The OAuth2 token provider returned a blank access token", null);
        }
        return (String) token;
    }
}
