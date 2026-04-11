package com.ratelimiter.core;

import com.ratelimiter.entity.RateLimitRule;
import com.ratelimiter.store.InMemoryCounterStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter rateLimiter;
    private RateLimitRule rule;

    @BeforeEach
    void setUp() {
        InMemoryCounterStore store = new InMemoryCounterStore();
        rateLimiter = new TokenBucketRateLimiter(store);

        rule = new RateLimitRule();
        rule.setRuleName("test-rule");
        rule.setMaxRequests(10);
        rule.setWindowSeconds(60);
        rule.setBurstCapacity(10);
    }

    @Test
    void shouldAllowRequestsWithinLimit() {
        for (int i = 0; i < 10; i++) {
            ThrottleDecision decision = rateLimiter.tryConsume("test-key", rule);
            assertTrue(decision.allowed(), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void shouldRejectRequestsOverLimit() {
        // Exhaust all tokens
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryConsume("test-key", rule);
        }

        // Next request should be rejected
        ThrottleDecision decision = rateLimiter.tryConsume("test-key", rule);
        assertFalse(decision.allowed());
        assertTrue(decision.retryAfterMs() > 0);
    }

    @Test
    void shouldTrackRemainingTokens() {
        ThrottleDecision decision = rateLimiter.tryConsume("test-key", rule);
        assertTrue(decision.allowed());
        assertEquals(9, decision.remaining()); // 10 - 1

        decision = rateLimiter.tryConsume("test-key", rule);
        assertTrue(decision.allowed());
        assertEquals(8, decision.remaining()); // 10 - 2
    }

    @Test
    void shouldIsolateKeysByNamespace() {
        // Exhaust key1
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryConsume("key1", rule);
        }

        // key2 should still work
        ThrottleDecision decision = rateLimiter.tryConsume("key2", rule);
        assertTrue(decision.allowed());
    }

    @Test
    void shouldRefillTokensOverTime() throws InterruptedException {
        // Use a small window for faster test
        rule.setMaxRequests(10);
        rule.setWindowSeconds(1); // 10 tokens per second

        // Exhaust all tokens
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryConsume("refill-key", rule);
        }

        // Wait for some refill
        Thread.sleep(200); // Should refill ~2 tokens

        ThrottleDecision decision = rateLimiter.tryConsume("refill-key", rule);
        assertTrue(decision.allowed(), "Should have refilled at least 1 token after 200ms");
    }

    @Test
    void shouldReturnCorrectAlgorithm() {
        assertEquals(RateLimitAlgorithm.TOKEN_BUCKET, rateLimiter.algorithm());
    }

    @Test
    void shouldUseDefaultBurstCapacity() {
        rule.setBurstCapacity(null); // Should fall back to maxRequests
        assertEquals(10, rule.effectiveBurstCapacity());

        ThrottleDecision decision = rateLimiter.tryConsume("burst-key", rule);
        assertTrue(decision.allowed());
    }

    @Test
    void shouldRespectCustomBurstCapacity() {
        rule.setBurstCapacity(5); // Lower burst than max

        // Should only allow 5 bursts
        for (int i = 0; i < 5; i++) {
            ThrottleDecision decision = rateLimiter.tryConsume("burst-limited-key", rule);
            assertTrue(decision.allowed(), "Request " + (i + 1) + " should be allowed");
        }

        ThrottleDecision decision = rateLimiter.tryConsume("burst-limited-key", rule);
        assertFalse(decision.allowed(), "6th request should be rejected with burst capacity 5");
    }
}
