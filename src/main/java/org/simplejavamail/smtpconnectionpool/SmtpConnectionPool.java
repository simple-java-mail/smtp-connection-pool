package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusterstormpot.core.ClusterConfig;
import org.bbottema.clusterstormpot.core.ResourceClusters;
import org.bbottema.clusterstormpot.core.api.AllocatorFactory;
import org.bbottema.clusterstormpot.core.api.CyclingStrategy;
import org.bbottema.clusterstormpot.util.SimpleDelegatingPoolable;
import org.jetbrains.annotations.NotNull;
import stormpot.Expiration;

import javax.mail.Session;
import javax.mail.Transport;

public class SmtpConnectionPool extends ResourceClusters<Session, Session, SimpleDelegatingPoolable<Transport>> {
    public SmtpConnectionPool(@NotNull final ClusterConfig<Session, SimpleDelegatingPoolable<Transport>> clusterConfig) {
        super(clusterConfig);
    }
}