package com.ratelimiter.core;

import com.ratelimiter.entity.RateLimitRule;
import com.ratelimiter.store.InMemoryCounterStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowRateLimiterTest {

    private SlidingWindowRateLimiter rateLimiter;
    private RateLimitRule rule;

    @BeforeEach
    void setUp() {
        InMemoryCounterStore store = new InMemoryCounterStore();
        rateLimiter = new SlidingWindowRateLimiter(store);

        rule = new RateLimitRule();
        rule.setRuleName("sliding-test");
        rule.setMaxRequests(10);
        rule.setWindowSeconds(60);
    }

    @Test
    void shouldAllowRequestsWithinLimit() {
        for (int i = 0; i < 10; i++) {
            ThrottleDecision decision = rateLimiter.tryConsume("sw-key", rule);
            assertTrue(decision.allowed(), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void shouldRejectRequestsOverLimit() {
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryConsume("sw-reject-key", rule);
        }

        ThrottleDecision decision = rateLimiter.tryConsume("sw-reject-key", rule);
        assertFalse(decision.allowed());
    }

    @Test
    void shouldReturnCorrectAlgorithm() {
        assertEquals(RateLimitAlgorithm.SLIDING_WINDOW, rateLimiter.algorithm());
    }

    @Test
    void shouldIsolateKeys() {
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryConsume("sw-key-a", rule);
        }

        ThrottleDecision decision = rateLimiter.tryConsume("sw-key-b", rule);
        assertTrue(decision.allowed());
    }

    @Test
    void shouldTrackRemainingRequests() {
        ThrottleDecision decision = rateLimiter.tryConsume("sw-remaining", rule);
        assertTrue(decision.allowed());
        assertTrue(decision.remaining() >= 8); // At least 8 remaining after 1 request (with sliding window approximation)
    }
}
