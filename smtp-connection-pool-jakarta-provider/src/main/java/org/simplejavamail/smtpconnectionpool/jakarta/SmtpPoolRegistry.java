package org.simplejavamail.smtpconnectionpool.jakarta;

import jakarta.mail.Session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Future;

/** Session-scoped manager lookup and explicit application/container shutdown hooks. */
public final class SmtpPoolRegistry {
    private static final Set<SmtpPoolManager> MANAGERS =
            Collections.newSetFromMap(new WeakHashMap<SmtpPoolManager, Boolean>());

    private SmtpPoolRegistry() {
    }

    /** Returns the existing Session-owned manager or creates and registers one atomically. */
    public static SmtpPoolManager getOrCreate(final Session session) {
        synchronized (session.getProperties()) {
            if (Boolean.TRUE.equals(session.getProperties().get(SmtpPoolProperties.REGISTRY_SHUTDOWN))) {
                throw new IllegalStateException("The smtppool registry for this Session has been shut down");
            }
            final Object existing = session.getProperties().get(SmtpPoolProperties.MANAGER);
            if (existing != null) {
                if (!(existing instanceof SmtpPoolManager)) {
                    throw new IllegalStateException(SmtpPoolProperties.MANAGER + " must contain an SmtpPoolManager");
                }
                final SmtpPoolManager manager = (SmtpPoolManager) existing;
                if (manager.getSession() != session) {
                    throw new IllegalStateException("The configured SmtpPoolManager belongs to a different Session");
                }
                return manager;
            }

            final SmtpPoolManager manager = new SmtpPoolManager(session);
            session.getProperties().put(SmtpPoolProperties.MANAGER, manager);
            synchronized (MANAGERS) {
                MANAGERS.add(manager);
            }
            return manager;
        }
    }

    /** Starts graceful shutdown for one Session and returns a completion handle. */
    public static Future<?> shutdown(final Session session) {
        final SmtpPoolManager manager;
        synchronized (session.getProperties()) {
            session.getProperties().put(SmtpPoolProperties.REGISTRY_SHUTDOWN, Boolean.TRUE);
            final Object existing = session.getProperties().remove(SmtpPoolProperties.MANAGER);
            manager = existing instanceof SmtpPoolManager ? (SmtpPoolManager) existing : null;
        }
        return manager == null ? CompletedFuture.INSTANCE : manager.shutdown();
    }

    /** Invalidates active leases, starts shutdown for one Session, and returns a completion handle. */
    public static Future<?> shutdownNow(final Session session) {
        final SmtpPoolManager manager;
        synchronized (session.getProperties()) {
            session.getProperties().put(SmtpPoolProperties.REGISTRY_SHUTDOWN, Boolean.TRUE);
            final Object existing = session.getProperties().remove(SmtpPoolProperties.MANAGER);
            manager = existing instanceof SmtpPoolManager ? (SmtpPoolManager) existing : null;
        }
        return manager == null ? CompletedFuture.INSTANCE : manager.shutdownNow();
    }

    /**
     * Starts graceful shutdown for every registry-created manager currently known to this class loader.
     * This process/container hook returns immediately; use {@link #shutdown(Session)} when completion must be awaited.
     */
    public static void shutdownAll() {
        final ArrayList<SmtpPoolManager> snapshot;
        synchronized (MANAGERS) {
            snapshot = new ArrayList<SmtpPoolManager>(MANAGERS);
            MANAGERS.clear();
        }
        for (SmtpPoolManager manager : snapshot) {
            final Session session = manager.getSession();
            synchronized (session.getProperties()) {
                session.getProperties().put(SmtpPoolProperties.REGISTRY_SHUTDOWN, Boolean.TRUE);
                if (session.getProperties().get(SmtpPoolProperties.MANAGER) == manager) {
                    session.getProperties().remove(SmtpPoolProperties.MANAGER);
                }
            }
            manager.shutdown();
        }
    }

    /** Allows a deliberately reconfigured Session to create a fresh manager after its previous shutdown completed. */
    public static void restart(final Session session) {
        synchronized (session.getProperties()) {
            if (session.getProperties().containsKey(SmtpPoolProperties.MANAGER)) {
                throw new IllegalStateException("Cannot restart smtppool while a manager is still registered");
            }
            session.getProperties().remove(SmtpPoolProperties.REGISTRY_SHUTDOWN);
        }
    }

    private enum CompletedFuture implements Future<Object> {
        INSTANCE;

        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(final long timeout, final java.util.concurrent.TimeUnit unit) {
            return null;
        }
    }
}
