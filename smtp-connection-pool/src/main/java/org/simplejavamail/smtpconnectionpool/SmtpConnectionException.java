package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.MessagingException;

/**
 * The pool tried to open an SMTP connection, but the provider could not connect.
 * The original {@link MessagingException} is retained as the cause, so your application can distinguish, for example,
 * an unreachable server from rejected credentials or a certificate problem. These failures need not have the same remedy.
 * <p>
 * This exception concerns connection setup, not waiting for pool capacity, obtaining credentials, selecting a provider
 * or sending message content. It does not decide whether another destination should be tried or an email retried.
 * Existing callers can still catch its parent, {@link TransportHandlingException}.
 *
 * @since 4.4.0
 */
public final class SmtpConnectionException extends TransportHandlingException {
    private static final long serialVersionUID = 1L;

    SmtpConnectionException(final MessagingException cause) {
        super("Error while opening the configured SMTP transport", cause);
    }
}
