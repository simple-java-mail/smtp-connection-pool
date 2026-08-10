package org.simplejavamail.smtpconnectionpool.camel;

import jakarta.mail.Session;

interface SessionTracker {
    SessionTracker NONE = new SessionTracker() {
        @Override
        public void track(final Session session, final boolean owned) {
        }
    };

    void track(Session session, boolean owned);
}
