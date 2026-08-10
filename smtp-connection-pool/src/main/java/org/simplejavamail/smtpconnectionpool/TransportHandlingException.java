package org.simplejavamail.smtpconnectionpool;

@SuppressWarnings("WeakerAccess")
public class TransportHandlingException extends RuntimeException {
	/** Preserves the generated serial form of the published 3.1.0 class. */
	private static final long serialVersionUID = 3444648245448996656L;

	TransportHandlingException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
