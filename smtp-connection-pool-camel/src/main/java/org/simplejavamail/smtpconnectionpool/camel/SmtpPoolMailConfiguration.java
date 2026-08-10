package org.simplejavamail.smtpconnectionpool.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mail.JavaMailSender;
import org.apache.camel.component.mail.MailConfiguration;

import java.net.URI;
import java.net.URISyntaxException;

/** Mail configuration that creates pooled senders for normal and dynamic Camel producer paths. */
public class SmtpPoolMailConfiguration extends MailConfiguration {
    /** Camel endpoint scheme that delegates physically to {@code smtp}. */
    public static final String CAMEL_PROTOCOL = "smtppool";
    /** Camel endpoint scheme that delegates physically to {@code smtps}. */
    public static final String CAMEL_SECURE_PROTOCOL = "smtppools";

    private transient SessionTracker sessionTracker = SessionTracker.NONE;

    /** Creates a pooled-mail configuration for Camel's default context injection path. */
    public SmtpPoolMailConfiguration() {
        super();
    }

    /** Creates a pooled-mail configuration associated with the supplied Camel context. */
    public SmtpPoolMailConfiguration(final CamelContext context) {
        super(context);
    }

    void setSessionTracker(final SessionTracker sessionTracker) {
        this.sessionTracker = sessionTracker == null ? SessionTracker.NONE : sessionTracker;
    }

    @Override
    public void configure(final URI uri) {
        super.configure(delegateUri(uri));
    }

    @Override
    protected JavaMailSender createJavaMailSender(final CamelContext context) {
        return new SmtpPoolJavaMailSender(sessionTracker);
    }

    private static URI delegateUri(final URI uri) {
        final String scheme = uri.getScheme();
        final String delegate;
        if (CAMEL_PROTOCOL.equalsIgnoreCase(scheme)) {
            delegate = "smtp";
        } else if (CAMEL_SECURE_PROTOCOL.equalsIgnoreCase(scheme)) {
            delegate = "smtps";
        } else {
            return uri;
        }
        try {
            return new URI(delegate, uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(),
                    uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException failure) {
            throw new IllegalArgumentException("Unable to map the Camel pooled-mail URI", failure);
        }
    }
}
