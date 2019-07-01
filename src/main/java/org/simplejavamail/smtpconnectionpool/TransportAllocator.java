/*
 * Copyright (C) 2019 Benny Bottema (benny@bennybottema.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.simplejavamail.smtpconnectionpool;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.bbottema.genericobjectpool.Allocator;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Transport;

import static org.slf4j.LoggerFactory.getLogger;

class TransportAllocator extends Allocator<Transport> {

	private static final Logger LOGGER = getLogger(TransportAllocator.class);

	@NotNull private final Session session;

	TransportAllocator(@NotNull final Session session) {
		this.session = session;
	}

	@NotNull
	@Override
	@SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE", justification = "generated code by se.eris Maven plugin")
	public Transport allocate() {
		LOGGER.trace("opening transport connection...");
		try {
			Transport transport = session.getTransport();
			transport.connect();
			return transport;
		} catch (NoSuchProviderException e) {
			throw new TransportHandlingException("unable to get transport from session:\n\t" + session, e);
		} catch (MessagingException e) {
			throw new TransportHandlingException("Error when trying to open connection to the server, session:\n\t" + session, e);
		}
	}
	
	@Override
	public void deallocate(Transport transport) {
		LOGGER.trace("closing transport...");
		try {
			transport.close();
		} catch (MessagingException e) {
			throw new TransportHandlingException("error closing transport connection", e);
		}
	}
}