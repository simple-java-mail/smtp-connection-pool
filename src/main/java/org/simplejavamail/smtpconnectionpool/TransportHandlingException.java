package org.simplejavamail.smtpconnectionpool;

@SuppressWarnings("WeakerAccess")
public class TransportHandlingException extends RuntimeException {
	TransportHandlingException(String msg, Throwable cause) {
		super(msg, cause);
	}
}