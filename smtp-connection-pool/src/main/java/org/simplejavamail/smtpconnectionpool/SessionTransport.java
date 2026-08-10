package org.simplejavamail.smtpconnectionpool;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;

public class SessionTransport {
    @NotNull private final jakarta.mail.Session session;
    @NotNull private final jakarta.mail.Transport transport;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "This value object deliberately retains the pooled Session and Transport handles.")
    public SessionTransport(@NotNull final jakarta.mail.Session session,
                            @NotNull final jakarta.mail.Transport transport) {
        this.session = session;
        this.transport = transport;
    }

    @NotNull
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The Session handle is the public purpose of this value object.")
    public jakarta.mail.Session getSession() {
        return session;
    }

    @NotNull
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The Transport handle is the public purpose of this value object.")
    public jakarta.mail.Transport getTransport() {
        return transport;
    }
}
