package org.simplejavamail.smtpconnectionpool;

/**
 * Secondary control for aborting a physical transport while its exclusive lease is still active.
 * This is separate from cancelling acquisition with a generic-pool ClaimControl.
 *
 * @since 4.1.0
 */
@FunctionalInterface
public interface TransportCancellation {

    /**
     * Atomically invalidates this lease before requesting provider abort. A racing healthy release cannot reuse it.
     * Repeated requests and requests through a handle retained from an ended lease do nothing.
     * This does not mean a message was unsent: observe the sending operation's result and await disposal separately.
     * If the provider action throws, the lease still remains invalidated and the failure is propagated.
     *
     * @return whether this request ended the active lease
     */
    boolean request();
}
