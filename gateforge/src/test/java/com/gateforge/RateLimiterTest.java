package com.gateforge;

import com.gateforge.ratelimit.RateLimiter;
import com.gateforge.routing.GatewayProperties;
import com.gateforge.routing.RateLimitConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private RateLimiter newRateLimiter(int maxRequests, int windowSeconds) {
        RateLimitConfig config = new RateLimitConfig();
        config.setMaxRequests(maxRequests);
        config.setWindowSeconds(windowSeconds);
        GatewayProperties properties = new GatewayProperties();
        properties.setRateLimit(config);
        return new RateLimiter(properties);
    }

    @Test
    void allowsRequestsUpToLimit_thenBlocks() {
        RateLimiter rateLimiter = newRateLimiter(5, 10);
        String client = "test-user";

        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.isAllowed(client), "Request " + (i + 1) + " should be allowed");
        }

        assertFalse(rateLimiter.isAllowed(client), "6th request should be blocked");
    }

    @Test
    void differentClients_haveIndependentLimits() {
        RateLimiter rateLimiter = newRateLimiter(5, 10);

        for (int i = 0; i < 5; i++) {
            rateLimiter.isAllowed("client-A");
        }

        assertTrue(rateLimiter.isAllowed("client-B"));
    }
}
