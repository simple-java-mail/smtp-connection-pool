package org.simplejavamail.smtpconnectionpool.jakarta;

import jakarta.mail.MessagingException;

final class TransportAllocationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    TransportAllocationException(final String message, final MessagingException cause) {
        super(message, cause);
    }

    MessagingException getMessagingCause() {
        return (MessagingException) getCause();
    }
}
