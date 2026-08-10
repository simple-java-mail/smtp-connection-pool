package org.simplejavamail.smtpconnectionpool.camel;

import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.apache.camel.component.mail.DefaultJavaMailSender;
import org.simplejavamail.smtpconnectionpool.jakarta.SmtpPoolProperties;

/**
 * Camel sender that preserves Camel's original protocol as the physical delegate and selects {@code smtppool}
 * for the Jakarta Mail facade.
 */
public final class SmtpPoolJavaMailSender extends DefaultJavaMailSender {
    private final SessionTracker sessionTracker;
    private String delegateProtocol = SmtpPoolProperties.DEFAULT_DELEGATE_PROTOCOL;
    private boolean externallySuppliedSession;

    /** Creates a sender for programmatic Camel configuration. */
    public SmtpPoolJavaMailSender() {
        this(SessionTracker.NONE);
    }

    SmtpPoolJavaMailSender(final SessionTracker sessionTracker) {
        this.sessionTracker = sessionTracker;
        super.setProtocol(SmtpPoolProperties.PROTOCOL);
    }

    @Override
    public void setProtocol(final String protocol) {
        if (protocol != null && !protocol.trim().isEmpty() &&
                !SmtpPoolProperties.PROTOCOL.equalsIgnoreCase(protocol)) {
            delegateProtocol = protocol.trim();
        }
        super.setProtocol(SmtpPoolProperties.PROTOCOL);
    }

    /** Returns the physical protocol retained from Camel's endpoint configuration. */
    public String getDelegateProtocol() {
        return delegateProtocol;
    }

    @Override
    public void setSession(final Session session) {
        externallySuppliedSession = session != null;
        super.setSession(session);
    }

    @Override
    public Session getSession() {
        final Session session = super.getSession();
        final String configured = session.getProperty(SmtpPoolProperties.DELEGATE_PROTOCOL);
        if (configured == null || configured.trim().isEmpty()) {
            session.getProperties().setProperty(SmtpPoolProperties.DELEGATE_PROTOCOL, delegateProtocol);
        }
        sessionTracker.track(session, !externallySuppliedSession);
        return session;
    }

    @Override
    protected Transport getTransport(final Session session) throws NoSuchProviderException {
        return session.getTransport(SmtpPoolProperties.PROTOCOL);
    }
}
