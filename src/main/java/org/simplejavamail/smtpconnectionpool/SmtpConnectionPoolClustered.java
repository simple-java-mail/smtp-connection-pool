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
import java.util.UUID;

public class SmtpConnectionPoolClustered extends ResourceClusters<UUID, Session, SimpleDelegatingPoolable<Transport>> {
    public SmtpConnectionPoolClustered(@NotNull final ClusterConfig<Session, SimpleDelegatingPoolable<Transport>> clusterConfig) {
        super(clusterConfig);
    }
}