package org.simplejavamail.smtpconnectionpool;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import org.bbottema.genericobjectpool.AllocationContext;
import org.jetbrains.annotations.Nullable;

/**
 * Lets you track connection attempts and temporarily pause the pool's automatic connection top-up.
 * Use it for connection metrics or to let your application's outage-handling logic decide when spare connections
 * should be created again. It observes opening a connection, not sending an email.
 * <p>
 * The pool still borrows only from the selected registration. This observer neither chooses another SMTP server
 * nor retries messages or schedules health checks. Pausing background creation does not stop callers from borrowing
 * healthy connections or explicitly claiming new ones.
 * <p>
 * Callbacks can run on claiming threads or the maintenance worker. Keep them thread-safe and quick; no pool
 * bookkeeping lock is held while they run. Runtime exceptions from observation callbacks are logged and ignored,
 * leaving the connection result unchanged. A runtime exception from the background-permission check instead
 * postpones automatic top-up. Never retain the supplied transport or expose credentials from its Session or URL.
 *
 * @since 4.4.0
 */
public interface TransportConnectionObserver {
    /** Leaves connection setup unobserved and automatic top-up enabled. */
    TransportConnectionObserver NONE = new TransportConnectionObserver() { };

    /**
     * Lets your application pause creation of spare connections for this Session.
     * Returning {@code false} postpones automatic top-up without an allocation failure or retained capacity reservation.
     * Existing healthy connections and explicit claims are unaffected; an allocation already in progress may finish.
     * The maintenance worker checks again on a later pass, so no separate resume call is needed when this returns true again.
     *
     * @param session the SMTP configuration whose background connections are being considered
     * @return whether maintenance may open another connection; the default is {@code true}
     */
    default boolean isBackgroundConnectionAllowed(final Session session) {
        return true;
    }

    /**
     * Gives you a callback object for this one attempt to open a connection.
     * It is called after credentials have been obtained, immediately before the provider's connect call.
     * Returning a separate {@link Attempt} for each call lets overlapping connections keep their own observation state.
     * Each returned object receives one terminal callback: connected, failed or abandoned. The pool keeps ownership
     * of the transport throughout; the callback must not connect, send through or close it.
     *
     * @param session the SMTP configuration used by this attempt
     * @param transport the provider transport about to be connected; do not retain it
     * @return a non-null observation for this attempt; the default does nothing
     */
    default Attempt connectionStarting(final Session session, final Transport transport) {
        return Attempt.NONE;
    }

    /**
     * Associates physical connection setup with the claim that requested it. The context's opaque identity survives
     * narrowed budgets, so integrations can distinguish overlapping callers without thread-local state. Background
     * allocation and legacy uncontrolled claims have no context. The default invokes the original two-argument
     * callback, preserving existing observers. Do not retain the Session, transport or context after settlement.
     *
     * @param session the SMTP configuration used by this attempt
     * @param transport the provider transport about to be connected; do not retain it
     * @param context the active claim, or {@code null} when setup is not part of a context-aware claim
     * @return a non-null observation for this attempt
     * @see #connectionStarting(Session, Transport)
     * @since 4.4.0
     */
    default Attempt connectionStarting(final Session session, final Transport transport, @Nullable final AllocationContext context) {
        return connectionStarting(session, transport);
    }

    /** Receives the result of one connection attempt, not the result of an email submission. */
    interface Attempt {
        /** Ignores this attempt's result. */
        Attempt NONE = new Attempt() { };

        /** The provider established a usable connection without the claim being stopped during setup. */
        default void connected() { }

        /**
         * The provider could not open the connection. The failure may describe availability, authentication or TLS;
         * it does not automatically justify trying another server.
         *
         * @param failure the original connection failure, before wrapping for the claiming caller
         */
        default void failed(final Throwable failure) { }

        /** The claim was cancelled, interrupted or timed out during setup; do not count this alone as an SMTP outage. */
        default void abandoned() { }
    }
}
