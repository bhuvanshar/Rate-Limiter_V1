package com.ratelimiter.concurrency;

import com.ratelimiter.core.RateLimitAlgorithm;
import com.ratelimiter.core.ThrottleDecision;
import com.ratelimiter.core.TokenBucketRateLimiter;
import com.ratelimiter.entity.RateLimitRule;
import com.ratelimiter.store.InMemoryCounterStore;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests to verify thread safety of the rate limiter.
 *
 * These tests launch many threads that simultaneously hammer the same
 * rate limit key to verify:
 * 1. No more than the allowed number of requests pass.
 * 2. No race conditions or CAS failures cause incorrect counts.
 * 3. The system handles contention gracefully.
 */
class RateLimiterConcurrencyTest {

    @Test
    void shouldEnforceLimitUnderHighConcurrency() throws Exception {
        InMemoryCounterStore store = new InMemoryCounterStore();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(store);

        RateLimitRule rule = new RateLimitRule();
        rule.setRuleName("concurrency-test");
        rule.setMaxRequests(100);
        rule.setWindowSeconds(3600); // 1 hour — no refill during test
        rule.setBurstCapacity(100);

        int numThreads = 200;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // All threads start at once
                    ThrottleDecision decision = limiter.tryConsume("concurrent-key", rule);
                    if (decision.allowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all threads simultaneously
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not complete in time");

        executor.shutdown();

        // Exactly 100 should be allowed, 100 rejected
        assertEquals(100, allowedCount.get(),
                "Exactly 100 requests should be allowed (got " + allowedCount.get() + ")");
        assertEquals(100, rejectedCount.get(),
                "Exactly 100 requests should be rejected (got " + rejectedCount.get() + ")");
        assertEquals(200, allowedCount.get() + rejectedCount.get(),
                "Total should be 200");
    }

    @Test
    void shouldHandleMultipleKeysConcurrently() throws Exception {
        InMemoryCounterStore store = new InMemoryCounterStore();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(store);

        RateLimitRule rule = new RateLimitRule();
        rule.setRuleName("multi-key-test");
        rule.setMaxRequests(50);
        rule.setWindowSeconds(3600);
        rule.setBurstCapacity(50);

        int numKeys = 10;
        int requestsPerKey = 100;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        ConcurrentHashMap<String, AtomicInteger> allowedPerKey = new ConcurrentHashMap<>();

        for (int k = 0; k < numKeys; k++) {
            String key = "key-" + k;
            allowedPerKey.put(key, new AtomicInteger(0));
        }

        CountDownLatch doneLatch = new CountDownLatch(numKeys * requestsPerKey);

        for (int k = 0; k < numKeys; k++) {
            String key = "key-" + k;
            for (int r = 0; r < requestsPerKey; r++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        ThrottleDecision decision = limiter.tryConsume(key, rule);
                        if (decision.allowed()) {
                            allowedPerKey.get(key).incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Each key should have exactly 50 allowed requests
        for (int k = 0; k < numKeys; k++) {
            String key = "key-" + k;
            assertEquals(50, allowedPerKey.get(key).get(),
                    "Key " + key + " should have exactly 50 allowed requests");
        }
    }

    @Test
    void shouldMeasureOverheadPerRequest() throws Exception {
        InMemoryCounterStore store = new InMemoryCounterStore();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(store);

        RateLimitRule rule = new RateLimitRule();
        rule.setRuleName("perf-test");
        rule.setMaxRequests(1_000_000);
        rule.setWindowSeconds(3600);
        rule.setBurstCapacity(1_000_000);

        // Warm up
        for (int i = 0; i < 1000; i++) {
            limiter.tryConsume("perf-key-" + (i % 100), rule);
        }

        // Measure
        int iterations = 100_000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            limiter.tryConsume("perf-key-" + (i % 1000), rule);
        }
        long elapsed = System.nanoTime() - start;
        double avgNanos = (double) elapsed / iterations;
        double avgMicros = avgNanos / 1000.0;

        System.out.printf("Average overhead per request: %.2f ns (%.2f μs)%n", avgNanos, avgMicros);

        // Assert overhead is well under 5ms (our budget)
        assertTrue(avgMicros < 5000, // 5ms in microseconds
                "Average overhead " + avgMicros + "μs exceeds 5ms budget");

        // In practice, we expect <10μs on modern hardware
        assertTrue(avgMicros < 100,
                "Average overhead " + avgMicros + "μs is higher than expected (<100μs)");
    }

    @Test
    void shouldHandleEvictionDuringActiveTraffic() throws Exception {
        InMemoryCounterStore store = new InMemoryCounterStore();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(store);

        RateLimitRule rule = new RateLimitRule();
        rule.setRuleName("eviction-test");
        rule.setMaxRequests(100);
        rule.setWindowSeconds(3600);
        rule.setBurstCapacity(100);

        // Pre-populate with entries
        for (int i = 0; i < 1000; i++) {
            limiter.tryConsume("evict-key-" + i, rule);
        }
        assertEquals(1000, store.size());

        // Run eviction concurrently with new traffic
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        // Thread 1: evict with 0ms idle time (evict everything)
        executor.submit(() -> {
            try {
                start.await();
                store.evictExpired(0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        // Thread 2: send new traffic
        executor.submit(() -> {
            try {
                start.await();
                for (int i = 0; i < 100; i++) {
                    limiter.tryConsume("new-key-" + i, rule);
                }
            } catch (Exception e) {
                fail("Exception during concurrent traffic: " + e.getMessage());
            } finally {
                done.countDown();
            }
        });

        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // New keys should still work
        ThrottleDecision decision = limiter.tryConsume("post-evict-key", rule);
        assertTrue(decision.allowed(), "New key should work after eviction");
    }
}
