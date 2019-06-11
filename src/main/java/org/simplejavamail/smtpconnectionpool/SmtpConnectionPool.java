package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusterstormpot.core.ResourceClusters;
import org.bbottema.clusterstormpot.util.SimpleDelegatingPoolable;
import org.jetbrains.annotations.NotNull;

import javax.mail.Session;
import javax.mail.Transport;

@SuppressWarnings("WeakerAccess")
public class SmtpConnectionPool extends ResourceClusters<Session, Session, SimpleDelegatingPoolable<Transport>> {
    public SmtpConnectionPool(@NotNull final SmtpClusterConfig smtpClusterConfig) {
        super(smtpClusterConfig.getConfigBuilder().build());
    }
}