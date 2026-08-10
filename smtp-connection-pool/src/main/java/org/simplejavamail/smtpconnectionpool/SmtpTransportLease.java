package org.simplejavamail.smtpconnectionpool;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.mail.Session;
import org.bbottema.genericobjectpool.PoolableObject;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

/**
 * An exclusive claim on one pooled SMTP transport.
 *
 * <p>A lease must end exactly once: call {@link #release()} after a healthy use so the transport can be reused,
 * or {@link #invalidate()} after a connection-level failure so it is closed and removed from the pool.</p>
 */
public final class SmtpTransportLease implements AutoCloseable {

    /** Terminal lifecycle states for an exclusive claim. */
    public enum State {
        /** The caller still owns the physical transport exclusively. */
        ACTIVE,
        /** The physical transport was returned to the pool as reusable. */
        RELEASED,
        /** The physical transport was removed from the pool as unusable or uncertain. */
        INVALIDATED
    }

    @NotNull private final PoolableObject<SessionTransport> claimedTransport;
    @NotNull private final AtomicReference<State> state = new AtomicReference<>(State.ACTIVE);

    /** Wraps an already claimed pool object as an exclusive SMTP lease. */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "A lease deliberately owns the claimed pool handle until its terminal outcome.")
    public SmtpTransportLease(@NotNull final PoolableObject<SessionTransport> claimedTransport) {
        this.claimedTransport = claimedTransport;
    }

    /** Returns the Session/Transport pair owned by this lease. */
    @NotNull
    public SessionTransport getSessionTransport() {
        return claimedTransport.getAllocatedObject();
    }

    /** Returns the Jakarta Mail Session associated with the physical transport. */
    @NotNull
    public Session getSession() {
        return getSessionTransport().getSession();
    }

    /** Returns the physical Jakarta Mail transport owned exclusively by this lease. */
    @NotNull
    public jakarta.mail.Transport getTransport() {
        return getSessionTransport().getTransport();
    }

    /** Returns the current lifecycle state. */
    @NotNull
    public State getState() {
        return state.get();
    }

    /** Returns whether neither terminal operation has run yet. */
    public boolean isActive() {
        return state.get() == State.ACTIVE;
    }

    /**
     * Returns the healthy transport to the pool. Subsequent terminal calls are ignored.
     *
     * @return whether this call ended the lease
     */
    public boolean release() {
        if (state.compareAndSet(State.ACTIVE, State.RELEASED)) {
            try {
                claimedTransport.release();
                return true;
            } catch (RuntimeException releaseFailure) {
                state.set(State.INVALIDATED);
                try {
                    claimedTransport.invalidate();
                } catch (RuntimeException invalidationFailure) {
                    releaseFailure.addSuppressed(invalidationFailure);
                }
                throw releaseFailure;
            }
        }
        return false;
    }

    /**
     * Removes an unhealthy transport from the pool. Subsequent terminal calls are ignored.
     *
     * @return whether this call ended the lease
     */
    public boolean invalidate() {
        if (state.compareAndSet(State.ACTIVE, State.INVALIDATED)) {
            claimedTransport.invalidate();
            return true;
        }
        return false;
    }

    /**
     * A try-with-resources lease is treated as healthy unless the caller invalidates it first.
     */
    @Override
    public void close() {
        release();
    }
}
