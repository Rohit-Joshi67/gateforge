package com.gateforge;

import com.gateforge.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void allowsRequestsUpToLimit_thenBlocks() {
        RateLimiter rateLimiter = new RateLimiter();
        String client = "test-user";

        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.isAllowed(client), "Request " + (i + 1) + " should be allowed");
        }

        assertFalse(rateLimiter.isAllowed(client), "6th request should be blocked");
    }

    @Test
    void differentClients_haveIndependentLimits() {
        RateLimiter rateLimiter = new RateLimiter();

        for (int i = 0; i < 5; i++) {
            rateLimiter.isAllowed("client-A");
        }

        // client-B should be unaffected by client-A's usage
        assertTrue(rateLimiter.isAllowed("client-B"));
    }
}