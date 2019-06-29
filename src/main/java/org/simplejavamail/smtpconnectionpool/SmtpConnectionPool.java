package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusteredobjectpool.core.ResourceClusters;
import org.jetbrains.annotations.NotNull;

import javax.mail.Session;
import javax.mail.Transport;

@SuppressWarnings("WeakerAccess")
public class SmtpConnectionPool extends ResourceClusters<Session, Session, Transport> {
    public SmtpConnectionPool(@NotNull final SmtpClusterConfig smtpClusterConfig) {
        super(smtpClusterConfig.getConfigBuilder().build());
    }
}