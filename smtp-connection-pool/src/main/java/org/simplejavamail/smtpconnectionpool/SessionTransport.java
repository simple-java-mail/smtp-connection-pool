package org.simplejavamail.smtpconnectionpool;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class SessionTransport {
    @NotNull private final Session session;
    @NotNull private final Transport transport;
    private final Optional<Runnable> abortAction;

    public SessionTransport(@NotNull final Session session, @NotNull final Transport transport) {
        this(session, transport, Optional.empty());
    }

    SessionTransport(final Session session, final Transport transport, final Optional<Runnable> abortAction) {
        this.session = session;
        this.transport = transport;
        this.abortAction = abortAction;
    }

    Optional<Runnable> getAbortAction() {
        return abortAction;
    }

    @NotNull
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The Session handle is the public purpose of this value object.")
    public Session getSession() {
        return session;
    }

    @NotNull
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The Transport handle is the public purpose of this value object.")
    public Transport getTransport() {
        return transport;
    }
}
