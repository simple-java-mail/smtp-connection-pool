package org.simplejavamail.smtpconnectionpool.jpmsconsumer;

import jakarta.mail.Session;
import org.simplejavamail.smtpconnectionpool.SmtpClusterConfig;
import org.simplejavamail.smtpconnectionpool.SmtpConnectionPool;
import org.simplejavamail.smtpconnectionpool.jakarta.SmtpPoolProperties;

public final class JpmsPoolConsumer {

	private JpmsPoolConsumer() {
	}

	public static SmtpConnectionPool newPool() {
		return new SmtpConnectionPool(new SmtpClusterConfig<Session>());
	}

	public static String providerProtocol() {
		return SmtpPoolProperties.PROTOCOL;
	}
}
