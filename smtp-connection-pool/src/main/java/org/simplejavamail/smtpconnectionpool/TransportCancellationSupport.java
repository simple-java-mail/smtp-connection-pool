package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Transport;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Optional bridge to a provider that can abort its own physical connection without waiting behind connect or send.
 * Configure it explicitly through {@link SmtpClusterConfig#withTransportCancellationSupport(TransportCancellationSupport)}.
 * The pool does not install socket factories or modify the caller's Session.
 *
 * @since 4.1.0
 */
@FunctionalInterface
public interface TransportCancellationSupport {

    /**
     * Creates a connection-owned abort action before its first connect, or returns empty for an unsupported transport.
     * The action must be quick, thread-safe and latched: it must also prevent a future connect and cover replacement
     * sockets during reconnect or TLS negotiation. A synchronized {@code Transport.close()} is not sufficient.
     * The pool fences the action to the active acquisition or exclusive lease; the provider owns physical operation exit.
     *
     * @param transport the exact provider-selected transport, not connected yet
     * @return the abort action, or an empty capability; never null
     */
    @NotNull
    Optional<Runnable> createAbortAction(@NotNull Transport transport);
}
