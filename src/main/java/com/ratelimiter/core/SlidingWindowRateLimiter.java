package com.ratelimiter.core;

import com.ratelimiter.entity.RateLimitRule;
import com.ratelimiter.store.CounterStore;
import com.ratelimiter.store.SlidingWindowState;
import org.springframework.stereotype.Component;

/**
 * Sliding Window Counter algorithm implementation.
 *
 * Uses a weighted average of the current and previous window counts
 * to approximate a true sliding window with O(1) space per key.
 *
 * Example: rule says 100 req/min, we're 20 seconds into the current minute.
 *   - Previous minute had 80 requests.
 *   - Current minute has 30 requests so far.
 *   - Overlap fraction: (60 - 20) / 60 = 0.667
 *   - Effective count: 30 + 80 * 0.667 = 83.3 → 83
 *   - Since 83 < 100, the request is allowed.
 *
 * This is more accurate than fixed window (no boundary burst) at the
 * cost of being an approximation. For most use cases, the approximation
 * error is negligible.
 */
@Component
public class SlidingWindowRateLimiter implements RateLimiterStrategy {

    private final CounterStore counterStore;

    public SlidingWindowRateLimiter(CounterStore counterStore) {
        this.counterStore = counterStore;
    }

    @Override
    public RateLimitAlgorithm algorithm() {
        return RateLimitAlgorithm.SLIDING_WINDOW;
    }

    @Override
    public ThrottleDecision tryConsume(String key, RateLimitRule rule) {
        long windowMs = rule.getWindowSeconds() * 1000L;
        long maxRequests = rule.getMaxRequests();

        SlidingWindowState window = counterStore.getOrCreateWindow(key);
        SlidingWindowState.WindowResult result = window.tryRecord(maxRequests, windowMs);

        if (result.allowed()) {
            return ThrottleDecision.allowed(
                    key,
                    maxRequests,
                    result.remaining(),
                    result.resetAtEpochMs(),
                    rule.getRuleName()
            );
        } else {
            return ThrottleDecision.rejected(
                    key,
                    maxRequests,
                    result.retryAfterMs(),
                    result.resetAtEpochMs(),
                    rule.getRuleName()
            );
        }
    }
}
