package org.simplejavamail.smtpconnectionpool.jakarta;

import jakarta.mail.MessagingException;
import jakarta.mail.Provider;
import jakarta.mail.Session;

/** Programmatic branch of the hybrid physical-transport selection model. */
@FunctionalInterface
public interface SmtpDelegateProviderResolver {
    /**
     * Resolves the physical transport provider for one facade connection attempt.
     *
     * @param session Session that owns the facade and its pools
     * @param delegateProtocol configured physical protocol
     * @return a non-null physical transport provider
     * @throws MessagingException when no suitable provider can be selected
     */
    Provider resolve(Session session, String delegateProtocol) throws MessagingException;
}
