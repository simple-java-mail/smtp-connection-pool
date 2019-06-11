package org.simplejavamail.smtpconnectionpool;

import org.bbottema.clusterstormpot.core.ResourceClusters;
import org.bbottema.clusterstormpot.util.SimpleDelegatingPoolable;
import org.jetbrains.annotations.NotNull;

import javax.mail.Session;
import javax.mail.Transport;
import java.util.UUID;

@SuppressWarnings("WeakerAccess")
public class SmtpConnectionPoolClustered extends ResourceClusters<UUID, Session, SimpleDelegatingPoolable<Transport>> {
    public SmtpConnectionPoolClustered(@NotNull final SmtpClusterConfig smtpClusterConfig) {
        super(smtpClusterConfig.getConfigBuilder().build());
    }
}