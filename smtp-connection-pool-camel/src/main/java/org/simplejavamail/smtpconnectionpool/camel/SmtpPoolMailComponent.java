package org.simplejavamail.smtpconnectionpool.camel;

import jakarta.mail.Session;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.mail.MailComponent;
import org.apache.camel.component.mail.MailComponentConfigurer;
import org.apache.camel.component.mail.MailConfiguration;
import org.apache.camel.component.mail.MailEndpoint;
import org.apache.camel.component.mail.MailEndpointConfigurer;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.simplejavamail.smtpconnectionpool.jakarta.SmtpPoolRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Optional Camel component registered as {@code smtppool:} and {@code smtppools:}. It changes only transport selection;
 * the Jakarta provider owns all pooling behavior.
 */
@SuppressWarnings("this-escape")
public class SmtpPoolMailComponent extends MailComponent {
    private final Set<Session> ownedSessions =
            Collections.newSetFromMap(new ConcurrentHashMap<Session, Boolean>());
    private final SessionTracker sessionTracker = new OwnedSessionTracker(ownedSessions);

    /** Creates a component for Camel's default context injection path. */
    public SmtpPoolMailComponent() {
        super();
        installConfiguration(new SmtpPoolMailConfiguration());
    }

    /** Creates a component associated with the supplied Camel context. */
    public SmtpPoolMailComponent(final CamelContext context) {
        super(context);
        installConfiguration(new SmtpPoolMailConfiguration(context));
    }

    /** Creates a component with a preconfigured pooled-mail configuration. */
    public SmtpPoolMailComponent(final SmtpPoolMailConfiguration configuration) {
        super();
        installConfiguration(configuration);
    }

    @Override
    public SmtpPoolMailConfiguration getConfiguration() {
        return (SmtpPoolMailConfiguration) super.getConfiguration();
    }

    @Override
    public void setConfiguration(final MailConfiguration configuration) {
        if (!(configuration instanceof SmtpPoolMailConfiguration)) {
            throw new IllegalArgumentException("SmtpPoolMailComponent requires SmtpPoolMailConfiguration");
        }
        installConfiguration((SmtpPoolMailConfiguration) configuration);
    }

    /**
     * Camel's generated configurer lookup is keyed by component scheme. Reuse camel-mail's generated configurer for
     * this adapter's two new schemes so all ordinary mail endpoint options keep working.
     */
    @Override
    protected void setProperties(final Endpoint endpoint, final Map<String, Object> parameters) throws Exception {
        if (endpoint instanceof MailEndpoint) {
            consumeConfiguredProperties(new MailEndpointConfigurer(), endpoint, parameters);
        }
        super.setProperties(endpoint, parameters);
    }

    @Override
    protected void setProperties(final Object bean, final Map<String, Object> parameters) throws Exception {
        if (bean == this) {
            consumeConfiguredProperties(new MailComponentConfigurer(), bean, parameters);
        }
        super.setProperties(bean, parameters);
    }

    @Override
    protected void doStop() throws Exception {
        Exception stopFailure = null;
        try {
            super.doStop();
        } catch (Exception failure) {
            stopFailure = failure;
        } finally {
            for (Session session : new ArrayList<Session>(ownedSessions)) {
                try {
                    SmtpPoolRegistry.shutdown(session).get(30, TimeUnit.SECONDS);
                } catch (TimeoutException timeout) {
                    SmtpPoolRegistry.shutdownNow(session);
                    if (stopFailure == null) {
                        stopFailure = timeout;
                    } else {
                        stopFailure.addSuppressed(timeout);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    SmtpPoolRegistry.shutdownNow(session);
                    if (stopFailure == null) {
                        stopFailure = interrupted;
                    } else {
                        stopFailure.addSuppressed(interrupted);
                    }
                } catch (Exception failure) {
                    if (stopFailure == null) {
                        stopFailure = failure;
                    } else {
                        stopFailure.addSuppressed(failure);
                    }
                }
            }
            ownedSessions.clear();
        }
        if (stopFailure != null) {
            throw stopFailure;
        }
    }

    private void installConfiguration(final SmtpPoolMailConfiguration configuration) {
        configuration.setSessionTracker(sessionTracker);
        super.setConfiguration(configuration);
    }

    private void consumeConfiguredProperties(final GeneratedPropertyConfigurer configurer, final Object target,
                                             final Map<String, Object> parameters) {
        final Iterator<Map.Entry<String, Object>> entries = parameters.entrySet().iterator();
        while (entries.hasNext()) {
            final Map.Entry<String, Object> entry = entries.next();
            if (configurer.configure(getCamelContext(), target, entry.getKey(), entry.getValue(), false)) {
                entries.remove();
            }
        }
    }

    private static final class OwnedSessionTracker implements SessionTracker {
        private final Set<Session> ownedSessions;

        private OwnedSessionTracker(final Set<Session> ownedSessions) {
            this.ownedSessions = ownedSessions;
        }

        @Override
        public void track(final Session session, final boolean owned) {
            if (owned) {
                ownedSessions.add(session);
            }
        }
    }
}
