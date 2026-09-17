package org.simplejavamail.smtpconnectionpool.jakarta;

import org.bbottema.genericobjectpool.ExpirationPolicy;
import org.bbottema.genericobjectpool.PoolableObject;
import org.bbottema.genericobjectpool.expirypolicies.TimeoutSinceLastAllocationExpirationPolicy;
import org.junit.jupiter.api.Test;
import org.simplejavamail.smtpconnectionpool.SessionTransport;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmtpPoolManagerExpirationTest {

    @Test
    void equalThresholdsRetainTheCreationRuleAndSeparateExpiryState() {
        final ExpirationPolicy<SessionTransport> policy = SmtpPoolManager.expirationPolicy(50L, 50L);
        final PoolableObject<SessionTransport> transport = transportAged(50L, 0L);

        assertTrue(policy.hasExpired(transport), "Only creation age has reached the shared threshold");
        assertEquals(2, transport.getExpiriesMs().size(), "Both rules must keep their own expiry state");
    }

    @Test
    void theIdleRuleStillExpiresBeforeTheCreationThreshold() {
        final ExpirationPolicy<SessionTransport> policy = SmtpPoolManager.expirationPolicy(50L, 60_000L);
        assertTrue(policy.hasExpired(transportAged(100L, 50L)));
    }

    @Test
    void neitherRuleExpiresBeforeItsThreshold() {
        final ExpirationPolicy<SessionTransport> policy = SmtpPoolManager.expirationPolicy(50L, 100L);
        assertFalse(policy.hasExpired(transportAged(99L, 49L)));
    }

    @Test
    void zeroCreationThresholdPreservesTheSingleIdlePolicy() {
        final ExpirationPolicy<SessionTransport> policy = SmtpPoolManager.expirationPolicy(50L, 0L);
        assertInstanceOf(TimeoutSinceLastAllocationExpirationPolicy.class, policy);
        assertFalse(policy.hasExpired(transportAged(60_000L, 49L)));
        assertTrue(policy.hasExpired(transportAged(60_000L, 50L)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static PoolableObject<SessionTransport> transportAged(final long creationAge, final long lastClaimAge) {
        final PoolableObject<SessionTransport> transport = mock(PoolableObject.class);
        final Map<ExpirationPolicy, Long> expiryState = new HashMap<ExpirationPolicy, Long>();
        when(transport.ageMs()).thenReturn(creationAge);
        when(transport.allocationAgeMs()).thenReturn(lastClaimAge);
        when(transport.getExpiriesMs()).thenReturn(expiryState);
        return transport;
    }
}
